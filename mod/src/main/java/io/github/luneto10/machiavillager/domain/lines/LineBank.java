package io.github.luneto10.machiavillager.domain.lines;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The cached pools of spoken lines, one per {@link LineKey}. Pure Java and mutable on purpose: it remembers the
 * recent picks for the no-repeat rule, the uses since the last delivery, and when each key was last asked for.
 * It has no clock: callers pass nowTicks.
 */
public final class LineBank {
    /** Most recent picks one key remembers, so none of them repeats right away. */
    public static final int RECENT_LIMIT = 5;
    /** Ticks a key must wait between two asks to the backend: 60 seconds. */
    public static final long ASK_INTERVAL_TICKS = 1_200;

    /** Share of the pool, in percent, that must be used up before a new batch is wanted. */
    private static final int REFRESH_PERCENT = 70;
    private static final int PERCENT = 100;

    private final Map<LineKey, List<String>> pools = new HashMap<>();
    private final Map<LineKey, Integer> uses = new HashMap<>();
    private final Map<LineKey, Deque<String>> recent = new HashMap<>();
    private final Map<LineKey, Long> lastAsked = new HashMap<>();

    /** Starts with the given pools. They are copied, so later changes to the caller's map do not reach the bank. */
    public LineBank(Map<LineKey, List<String>> pools) {
        Objects.requireNonNull(pools, "pools").forEach((key, lines) -> this.pools.put(key, List.copyOf(lines)));
    }

    /**
     * A random line of the key's pool that is not one of its recent picks. Empty when the pool has no lines.
     * Counts as a use, so the pool moves toward a new batch.
     */
    public Optional<String> pick(LineKey key, RandomGenerator random) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(random, "random");

        List<String> pool = pools.getOrDefault(key, List.of());
        if (pool.isEmpty()) {
            return Optional.empty();
        }

        Deque<String> history = recent.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        List<String> candidates = candidatesFor(pool, history);
        String line = candidates.get(random.nextInt(candidates.size()));

        // Remember the pick and count it together, so the no-repeat window and the refresh count stay in step.
        uses.merge(key, 1, Integer::sum);
        history.addLast(line);
        while (history.size() > RECENT_LIMIT) {
            history.pollFirst();
        }
        return Optional.of(line);
    }

    /** True when the key wants a new batch now: its pool is empty or mostly used up, and it was not asked recently. */
    public boolean needsBatch(LineKey key, long nowTicks) {
        Objects.requireNonNull(key, "key");

        // Asking too often floods the backend, so a recent ask answers no.
        Long asked = lastAsked.get(key);
        if (asked != null && nowTicks - asked < ASK_INTERVAL_TICKS) {
            return false;
        }

        // Integer math rounds the threshold down, so a one-line pool counts as due straight after a delivery.
        List<String> pool = pools.getOrDefault(key, List.of());
        int threshold = pool.size() * REFRESH_PERCENT / PERCENT;
        return pool.isEmpty() || uses.getOrDefault(key, 0) >= threshold;
    }

    /** Records that the key was asked for now, so {@link #needsBatch} waits the full interval before asking again. */
    public void markAsked(LineKey key, long nowTicks) {
        Objects.requireNonNull(key, "key");
        lastAsked.put(key, nowTicks);
    }

    /** Replaces the key's pool with a new batch and forgets the uses and the recent picks of the old one. */
    public void deliver(LineKey key, List<String> lines) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(lines, "lines");

        pools.put(key, List.copyOf(lines));
        uses.remove(key);
        recent.remove(key);
    }

    /** True when the key's pool has at least one line. */
    public boolean hasLines(LineKey key) {
        Objects.requireNonNull(key, "key");
        return !pools.getOrDefault(key, List.of()).isEmpty();
    }

    /** An unmodifiable copy of every pool, for saving. Changing the bank later does not change it. */
    public Map<LineKey, List<String>> snapshot() {
        return Map.copyOf(pools);
    }

    // A pool of five or fewer lines can only avoid its last pick; a bigger one avoids the last five.
    private static List<String> candidatesFor(List<String> pool, Deque<String> history) {
        int avoid = pool.size() <= RECENT_LIMIT ? 1 : RECENT_LIMIT;
        List<String> ordered = new ArrayList<>(history);
        List<String> avoided = ordered.subList(Math.max(0, ordered.size() - avoid), ordered.size());

        List<String> candidates = pool.stream().filter(line -> !avoided.contains(line)).toList();
        // A pool made only of the avoided lines has nothing else to offer, so it may repeat.
        return candidates.isEmpty() ? pool : candidates;
    }
}
