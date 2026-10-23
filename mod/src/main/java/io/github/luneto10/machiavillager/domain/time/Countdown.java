package io.github.luneto10.machiavillager.domain.time;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.random.RandomGenerator;

/**
 * Counts game ticks down to a fire point, then restarts with the next interval. Pure Java: no Minecraft imports.
 * Mutable on purpose: a timer is state that changes every tick.
 */
public final class Countdown {
    private final IntSupplier nextInterval;
    private int remainingTicks;

    private Countdown(IntSupplier nextInterval) {
        this.nextInterval = nextInterval;
        // Starts expired, so the first tick() fires.
        this.remainingTicks = 0;
    }

    /** Fires every {@code ticks} ticks. ticks must be >= 1. */
    public static Countdown every(int ticks) {
        checkAtLeastOne(ticks, "ticks");
        return new Countdown(() -> ticks);
    }

    /** Fires after a random number of ticks in [minTicks, maxTicks], re-rolled each cycle. 1 <= min <= max. */
    public static Countdown randomBetween(int minTicks, int maxTicks, RandomGenerator random) {
        checkAtLeastOne(minTicks, "minTicks");
        if (maxTicks < minTicks) {
            throw new IllegalArgumentException("maxTicks must be >= minTicks, was " + maxTicks + " < " + minTicks);
        }
        Objects.requireNonNull(random, "random");
        return new Countdown(() -> random.nextInt(minTicks, maxTicks + 1));
    }

    /** Advances one tick. Returns true when the countdown reaches zero; it then restarts with the next interval. */
    public boolean tick() {
        remainingTicks--;
        if (remainingTicks > 0) {
            return false;
        }
        remainingTicks = nextInterval.getAsInt();
        return true;
    }

    /** Makes the next tick() fire. */
    public void expireNow() {
        remainingTicks = 0;
    }

    /** Ticks left before it fires (never negative). */
    public int remainingTicks() {
        return remainingTicks;
    }

    private static void checkAtLeastOne(int ticks, String name) {
        if (ticks < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, was " + ticks);
        }
    }
}
