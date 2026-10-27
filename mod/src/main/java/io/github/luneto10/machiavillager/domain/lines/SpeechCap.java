package io.github.luneto10.machiavillager.domain.lines;

/**
 * Limits unprompted speech across all players: at most one line per minimum gap. Pure Java and mutable: it only
 * remembers when he last spoke. Callers pass nowTicks.
 */
public final class SpeechCap {
    /** Default gap between two unprompted lines: 60 seconds. */
    public static final long DEFAULT_MIN_GAP_TICKS = 1_200;

    private final long minGapTicks;
    private boolean hasSpoken;
    private long lastSpokeTick;

    /** A cap that keeps the given gap, in ticks, between two unprompted lines. */
    public SpeechCap(long minGapTicks) {
        if (minGapTicks < 0) {
            throw new IllegalArgumentException("minGapTicks must not be negative: " + minGapTicks);
        }
        this.minGapTicks = minGapTicks;
    }

    /** A cap with the default gap of 60 seconds. */
    public SpeechCap() {
        this(DEFAULT_MIN_GAP_TICKS);
    }

    /** True when he has not spoken yet, or the gap since his last unprompted line has passed. */
    public boolean canSpeak(long nowTicks) {
        return !hasSpoken || nowTicks - lastSpokeTick >= minGapTicks;
    }

    /** Records an unprompted line. Call it only once the line is really spoken. */
    public void markSpoke(long nowTicks) {
        hasSpoken = true;
        lastSpokeTick = nowTicks;
    }
}
