package io.github.luneto10.machiavillager.domain.speech;

import io.github.luneto10.machiavillager.domain.meeting.ReadingTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Says a list of lines one at a time, each for as long as it takes to read. Pure Java: no Minecraft imports.
 * Mutable on purpose: the queue shrinks and counts down on every tick.
 *
 * <p>Callers check {@link #finished()} before {@link #tick()}. That order lets a meeting end on the same tick
 * it always did: the closing line is read first, then the caller sees the queue is done.
 */
public final class SpeechQueue {
    private final Deque<String> pending;
    private final int total;
    private int spoken;
    // Ticks left before the next line may start; the line on screen is still being read until this hits zero.
    private int waitTicks;

    /** A queue that will say these lines in order. The list is copied, so later changes to it do not matter. */
    public SpeechQueue(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        this.pending = new ArrayDeque<>(lines);
        this.total = lines.size();
    }

    /**
     * Advances one tick. Returns the next line when its turn has come, empty otherwise. The first line comes
     * on the first tick; each later line waits until the one before it has been read.
     */
    public Optional<String> tick() {
        // Still reading the line on screen: nothing new yet.
        if (waitTicks > 0) {
            waitTicks--;
            return Optional.empty();
        }
        String line = pending.poll();
        if (line == null) {
            return Optional.empty();
        }
        // The reader needs this many ticks before the next line may start.
        waitTicks = ReadingTime.ticksFor(line);
        spoken++;
        return Optional.of(line);
    }

    /** True when every line was said and the last one has been read. */
    public boolean finished() {
        return pending.isEmpty() && waitTicks == 0;
    }

    /**
     * Drops the lines not yet said. The line on screen still gets its reading time, so {@link #finished()}
     * turns true once that time has passed. A caller that only needs the counts may treat it as finished at once.
     */
    public void interrupt() {
        pending.clear();
    }

    /** Lines said so far. */
    public int spoken() {
        return spoken;
    }

    /** Lines the queue was built with. */
    public int total() {
        return total;
    }
}
