package io.github.luneto10.machiavillager.domain.story;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The per-world seeded personality: how fast ambition grows and where each phase starts.
 * {@code thresholds.get(i)} is the ambition needed to enter {@code Phase.values()[i + 1]}.
 */
public record Personality(double growthPerHour, List<Integer> thresholds) {
    public static final List<Integer> BASE_THRESHOLDS = List.of(25, 50, 75, 92);
    public static final int THRESHOLD_JITTER = 8;
    public static final int MIN_PHASE_WIDTH = 15;
    public static final int MAX_THRESHOLD = 99;
    public static final double MIN_GROWTH_PER_HOUR = 8.0;
    public static final double MAX_GROWTH_PER_HOUR = 12.0;
    public static final double DEFAULT_GROWTH_PER_HOUR = 10.0;
    static final int THRESHOLD_LOWER_BOUND = 0;
    static final int THRESHOLD_UPPER_BOUND = 100;
    private static final int PHASE_ENTRIES = Phase.values().length - 1;

    public Personality {
        thresholds = List.copyOf(thresholds);
        if (!(growthPerHour > 0)) {
            throw new IllegalArgumentException("growthPerHour must be positive: " + growthPerHour);
        }
        if (thresholds.size() != PHASE_ENTRIES) {
            throw new IllegalArgumentException("expected " + PHASE_ENTRIES + " thresholds, got " + thresholds.size());
        }
        int previous = THRESHOLD_LOWER_BOUND;
        for (int value : thresholds) {
            if (value <= THRESHOLD_LOWER_BOUND || value >= THRESHOLD_UPPER_BOUND) {
                throw new IllegalArgumentException("threshold out of range: " + value);
            }
            if (value <= previous) {
                throw new IllegalArgumentException("thresholds must be strictly increasing: " + thresholds);
            }
            previous = value;
        }
    }

    /** Reference personality: base thresholds and default growth, no randomness. */
    public static Personality standard() {
        return new Personality(DEFAULT_GROWTH_PER_HOUR, BASE_THRESHOLDS);
    }

    /** Rolls growth and thresholds from the seed, keeping every phase at least {@link #MIN_PHASE_WIDTH} wide. */
    public static Personality rolled(RandomGenerator random) {
        double growth = MIN_GROWTH_PER_HOUR + random.nextDouble() * (MAX_GROWTH_PER_HOUR - MIN_GROWTH_PER_HOUR);
        List<Integer> values = new ArrayList<>();
        int previous = THRESHOLD_LOWER_BOUND;
        for (int base : BASE_THRESHOLDS) {
            int jittered = base + random.nextInt(-THRESHOLD_JITTER, THRESHOLD_JITTER + 1);
            int value = Math.min(Math.max(jittered, previous + MIN_PHASE_WIDTH), MAX_THRESHOLD);
            if (value <= previous) {
                value = previous + 1;
            }
            values.add(value);
            previous = value;
        }
        return new Personality(growth, values);
    }

    /** Ambition needed to enter the phase; 0 for {@link Phase#PARTNER}. */
    public int entryOf(Phase phase) {
        return phase == Phase.PARTNER ? 0 : thresholds.get(phase.ordinal() - 1);
    }
}
