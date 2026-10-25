package io.github.luneto10.machiavillager.domain.behavior;

/**
 * Turns time into independence: long time alone raises it, time with company lowers it. Pure Java.
 * Mutable on purpose: it counts ticks, like Countdown does.
 */
public final class IndependenceDrift {
    // +1 independence every 10 minutes with nobody near.
    static final long ALONE_TICKS_PER_POINT = 12_000;
    // -1 independence every 20 minutes with someone near: company tames him slowly.
    static final long COMPANY_TICKS_PER_POINT = 24_000;

    private long aloneTicks;
    private long companyTicks;

    /**
     * Adds {@code ticks} to the counter matching {@code playersNear} and returns the whole points to apply now:
     * positive when alone, negative when in company, zero when no full point has been reached yet.
     */
    public int tick(long ticks, boolean playersNear) {
        // The other counter is left as it is: each kind of time accumulates on its own.
        if (playersNear) {
            companyTicks += ticks;
            int points = (int) (companyTicks / COMPANY_TICKS_PER_POINT);
            companyTicks %= COMPANY_TICKS_PER_POINT;
            return -points;
        }
        aloneTicks += ticks;
        int points = (int) (aloneTicks / ALONE_TICKS_PER_POINT);
        aloneTicks %= ALONE_TICKS_PER_POINT;
        return points;
    }
}
