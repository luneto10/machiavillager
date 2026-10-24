package io.github.luneto10.machiavillager.domain.meeting;

import java.util.Objects;

/** How long a line stays on screen so the player can read it. */
public final class ReadingTime {
    // 2 s to notice the line.
    static final int BASE_TICKS = 40;
    // About 20 characters per second.
    static final int TICKS_PER_CHAR = 1;

    private ReadingTime() {}

    /** Ticks needed to read this line. A null line is a caller bug, so it throws. */
    public static int ticksFor(String line) {
        // Fail loudly on null instead of guessing a reading time.
        Objects.requireNonNull(line, "line");

        // Base time to notice the line, then one tick per character to read it.
        return BASE_TICKS + line.length() * TICKS_PER_CHAR;
    }
}
