package io.github.luneto10.machiavillager.domain.story;

import java.util.Objects;

/**
 * Ambition and the story phase it maps to. Immutable: every change returns a new object.
 * Phases have hysteresis so a value near a threshold does not flip back and forth.
 */
public final class StoryProgress {
    public static final double MIN = 0.0;
    public static final double MAX = 100.0;
    /** Ambition below a phase's entry by more than this drops the NPC back a phase. */
    public static final int HYSTERESIS = 5;
    public static final long TICKS_PER_HOUR = 72_000L;
    /** Growth multiplier once ambition reaches the REGIME entry: diminishing returns near the top. */
    public static final double SLOWDOWN_FACTOR = 0.7;

    private final double ambition;
    private final Phase phase;
    private final Phase highestPhase;

    private StoryProgress(double ambition, Phase phase, Phase highestPhase) {
        this.ambition = ambition;
        this.phase = phase;
        this.highestPhase = highestPhase;
    }

    /** Ambition 0, in the PARTNER phase, never past it. */
    public static StoryProgress initial() {
        return new StoryProgress(MIN, Phase.PARTNER, Phase.PARTNER);
    }

    /** Rebuilds a saved state as-is (no growth, no hysteresis). Rejects values that could not have been saved. */
    public static StoryProgress restored(double ambition, Phase phase, Phase highestPhase) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(highestPhase, "highestPhase");
        if (!(ambition >= MIN && ambition <= MAX)) {
            throw new IllegalArgumentException("ambition out of range: " + ambition);
        }
        if (highestPhase.ordinal() < phase.ordinal()) {
            throw new IllegalArgumentException("highestPhase " + highestPhase + " is below phase " + phase);
        }
        return new StoryProgress(ambition, phase, highestPhase);
    }

    public double ambition() {
        return ambition;
    }

    public Phase phase() {
        return phase;
    }

    public Phase highestPhase() {
        return highestPhase;
    }

    /** Grows ambition by the time players were nearby. Negative time is rejected. */
    public StoryProgress grow(long activeTicks, Personality personality) {
        if (activeTicks < 0) {
            throw new IllegalArgumentException("activeTicks must not be negative: " + activeTicks);
        }
        if (activeTicks == 0) {
            return this;
        }
        double hours = activeTicks / (double) TICKS_PER_HOUR;
        double rate = personality.growthPerHour();
        if (ambition >= personality.entryOf(Phase.REGIME)) {
            rate *= SLOWDOWN_FACTOR;
        }
        return withAmbition(ambition + hours * rate, personality);
    }

    /** Sets ambition (clamped, ratcheted and hysteresis applied) and recomputes the phase. */
    public StoryProgress withAmbition(double value, Personality personality) {
        double clamped = Math.clamp(value, MIN, MAX);
        double floor = personality.entryOf(highestPhase.previous());
        double ratcheted = Math.max(clamped, floor);

        Phase next = phase;
        while (next != Phase.WAR && ratcheted >= personality.entryOf(next.next())) {
            next = next.next();
        }
        while (next != Phase.PARTNER && ratcheted < personality.entryOf(next) - HYSTERESIS) {
            next = next.previous();
        }

        Phase highest = highestPhase.ordinal() >= next.ordinal() ? highestPhase : next;
        return new StoryProgress(ratcheted, next, highest);
    }

    @Override
    public String toString() {
        return "StoryProgress[" + phase + ", ambition " + ambition + ", highest " + highestPhase + "]";
    }
}
