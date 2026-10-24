package io.github.luneto10.machiavillager.domain.time;

/**
 * Blocks an action for a number of ticks after it is used. Pure Java: no Minecraft imports.
 * Mutable on purpose: the last-use time changes every time the action fires.
 */
public final class Cooldown {
    private final long durationTicks;
    private boolean used;
    private long lastUseTick;

    /** A cooldown that lasts {@code durationTicks} ticks. Must be at least 1. */
    public Cooldown(long durationTicks) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be >= 1, was " + durationTicks);
        }
        this.durationTicks = durationTicks;
    }

    /** True if it was never used, or the full duration has passed since the last use. */
    public boolean isReady(long nowTicks) {
        // A fresh cooldown has nothing to wait for.
        if (!used) {
            return true;
        }
        return nowTicks - lastUseTick >= durationTicks;
    }

    /** Starts the cooldown now. */
    public void markUsed(long nowTicks) {
        used = true;
        lastUseTick = nowTicks;
    }

    /** Uses the action if it is ready. Returns true when it was ready (and is now started). */
    public boolean tryUse(long nowTicks) {
        // Check first, so a refused use does not restart the wait.
        if (!isReady(nowTicks)) {
            return false;
        }
        markUsed(nowTicks);
        return true;
    }
}
