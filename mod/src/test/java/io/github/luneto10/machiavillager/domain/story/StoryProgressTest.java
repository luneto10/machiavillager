package io.github.luneto10.machiavillager.domain.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoryProgressTest {
    private static final long ONE_MINUTE_TICKS = 1_200L;
    private static final long FOUR_HOURS_TICKS = 4L * StoryProgress.TICKS_PER_HOUR;
    private static final double MIN_PHASE_HOURS = 2.0;
    private static final double MAX_PHASE_HOURS = 3.0;
    private static final double JUST_BELOW_HYSTERESIS = 19.9;
    private static final double NEAR_SEEDS_ENTRY = 24.0;
    private static final double DEEP_BELOW = 21.0;
    private static final double LOW_NEAR_ENTRY = 22.0;
    private static final double ABOVE_MAX = 150.0;
    private static final double BELOW_MIN = -10.0;
    private static final double REGIME_AMBITION = 80.0;
    private static final double FOUR_HOUR_AMBITION = 40.0;

    private static final Personality STANDARD = Personality.standard();

    @Test
    void grow_zeroTicks_noChange() {
        StoryProgress start = StoryProgress.initial();

        assertSame(start, start.grow(0L, STANDARD));
    }

    @Test
    void grow_negativeTicks_throws() {
        StoryProgress start = StoryProgress.initial();

        assertThrows(IllegalArgumentException.class, () -> start.grow(-1L, STANDARD));
    }

    @Test
    void grow_standardRate_eachPhaseTakesTwoToThreeHours() {
        Map<Phase, Double> enteredAtHour = new EnumMap<>(Phase.class);
        enteredAtHour.put(Phase.PARTNER, 0.0);
        StoryProgress progress = StoryProgress.initial();
        long steps = 0;
        while (progress.phase() != Phase.WAR) {
            progress = progress.grow(ONE_MINUTE_TICKS, STANDARD);
            steps++;
            double hours = steps * ONE_MINUTE_TICKS / (double) StoryProgress.TICKS_PER_HOUR;
            enteredAtHour.putIfAbsent(progress.phase(), hours);
        }

        for (Phase phase : new Phase[] {Phase.PARTNER, Phase.SEEDS, Phase.PROPOSAL, Phase.REGIME}) {
            double duration = enteredAtHour.get(phase.next()) - enteredAtHour.get(phase);
            assertTrue(duration >= MIN_PHASE_HOURS && duration <= MAX_PHASE_HOURS,
                    phase + " lasted " + duration + " hours");
        }
    }

    @Test
    void grow_oneSitting_neverSkipsPhases() {
        StoryProgress progress = StoryProgress.initial().grow(FOUR_HOURS_TICKS, STANDARD);

        assertEquals(FOUR_HOUR_AMBITION, progress.ambition());
        assertEquals(Phase.SEEDS, progress.phase());
    }

    @Test
    void withAmbition_nearThreshold_noFlipFlop() {
        double seedsEntry = STANDARD.entryOf(Phase.SEEDS);
        StoryProgress progress = StoryProgress.initial().withAmbition(seedsEntry, STANDARD);
        assertEquals(Phase.SEEDS, progress.phase());

        progress = progress.withAmbition(NEAR_SEEDS_ENTRY, STANDARD);
        assertEquals(Phase.SEEDS, progress.phase());
        progress = progress.withAmbition(DEEP_BELOW, STANDARD);
        assertEquals(Phase.SEEDS, progress.phase());
        progress = progress.withAmbition(LOW_NEAR_ENTRY, STANDARD);
        assertEquals(Phase.SEEDS, progress.phase());

        progress = progress.withAmbition(JUST_BELOW_HYSTERESIS, STANDARD);
        assertEquals(Phase.PARTNER, progress.phase());
        progress = progress.withAmbition(NEAR_SEEDS_ENTRY, STANDARD);
        assertEquals(Phase.PARTNER, progress.phase());
        progress = progress.withAmbition(seedsEntry, STANDARD);
        assertEquals(Phase.SEEDS, progress.phase());
    }

    @Test
    void withAmbition_ratchet_atMostOnePhaseBack() {
        StoryProgress progress = StoryProgress.initial().withAmbition(REGIME_AMBITION, STANDARD);
        assertEquals(Phase.REGIME, progress.phase());
        assertEquals(Phase.REGIME, progress.highestPhase());

        progress = progress.withAmbition(StoryProgress.MIN, STANDARD);

        assertEquals(STANDARD.entryOf(Phase.PROPOSAL), progress.ambition());
        assertEquals(Phase.PROPOSAL, progress.phase());
    }

    @Test
    void withAmbition_clamped() {
        assertEquals(StoryProgress.MAX, StoryProgress.initial().withAmbition(ABOVE_MAX, STANDARD).ambition());
        assertEquals(Phase.WAR, StoryProgress.initial().withAmbition(ABOVE_MAX, STANDARD).phase());
        assertEquals(StoryProgress.MIN, StoryProgress.initial().withAmbition(BELOW_MIN, STANDARD).ambition());
        assertEquals(Phase.PARTNER, StoryProgress.initial().withAmbition(BELOW_MIN, STANDARD).phase());
    }
}
