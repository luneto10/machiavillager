package io.github.luneto10.machiavillager.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * An immutable weighted random picker. Outcomes keep insertion order, so the same seed always gives the same
 * sequence. Pure Java: no Minecraft imports.
 */
public final class WeightedDice<T> {
    private final List<Entry<T>> entries;
    private final int totalWeight;

    private WeightedDice(List<Entry<T>> entries) {
        this.totalWeight = sumWeights(entries);
        this.entries = List.copyOf(entries);
    }

    /** Starts an empty builder. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Collects outcomes with their weights, then builds an immutable dice. */
    public static final class Builder<T> {
        private final List<Entry<T>> entries = new ArrayList<>();

        private Builder() {
        }

        /** Adds an outcome with a weight >= 0. Each outcome may be added only once. */
        public Builder<T> add(T outcome, int weight) {
            Objects.requireNonNull(outcome, "outcome");
            checkWeight(weight);
            if (indexOf(entries, outcome) >= 0) {
                throw new IllegalArgumentException("Duplicate outcome: " + outcome);
            }
            entries.add(new Entry<>(outcome, weight));
            return this;
        }

        /** Builds the dice. At least one weight must be above zero. */
        public WeightedDice<T> build() {
            return new WeightedDice<>(entries);
        }
    }

    /**
     * Rolls one outcome. Weight 0 outcomes are never returned.
     */
    public T roll(RandomGenerator random) {
        // Pick a number in [0, total), then walk the entries; the one whose slice covers it wins.
        int r = random.nextInt(totalWeight);
        for (Entry<T> entry : entries) {
            if (r < entry.weight()) {
                return entry.outcome();
            }
            r -= entry.weight();
        }
        throw new IllegalStateException("Unreachable: roll value always falls inside a weight slice");
    }

    /** Probability of the outcome (weight / total weight), or 0.0 if the outcome is unknown. */
    public double chanceOf(T outcome) {
        return (double) weightOf(outcome) / totalWeight;
    }

    /** Weight of the outcome, or 0 if the outcome is unknown. */
    public int weightOf(T outcome) {
        int index = indexOf(entries, outcome);
        return index < 0 ? 0 : entries.get(index).weight();
    }

    /** Returns a new dice with the outcome's weight replaced, or appended if the outcome is new. */
    public WeightedDice<T> withWeight(T outcome, int weight) {
        Objects.requireNonNull(outcome, "outcome");
        checkWeight(weight);
        List<Entry<T>> updated = new ArrayList<>(entries);
        Entry<T> replacement = new Entry<>(outcome, weight);
        int index = indexOf(updated, outcome);
        if (index >= 0) {
            updated.set(index, replacement);
        } else {
            updated.add(replacement);
        }
        return new WeightedDice<>(updated);
    }

    /** Outcomes in insertion order. The list is unmodifiable. */
    public List<T> outcomes() {
        return entries.stream().map(Entry::outcome).toList();
    }

    private record Entry<T>(T outcome, int weight) {
    }

    private static void checkWeight(int weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("Weight must be >= 0, was " + weight);
        }
    }

    private static <T> int indexOf(List<Entry<T>> entries, T outcome) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).outcome().equals(outcome)) {
                return i;
            }
        }
        return -1;
    }

    private static <T> int sumWeights(List<Entry<T>> entries) {
        int total = 0;
        for (Entry<T> entry : entries) {
            total = Math.addExact(total, entry.weight());
        }
        if (total <= 0) {
            throw new IllegalArgumentException("At least one outcome must have a weight above 0");
        }
        return total;
    }
}
