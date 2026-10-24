package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HintOptionTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final long MIN = HintOption.MIN_TICKS_BETWEEN_HINTS;
    private static final long MAX = HintOption.MAX_TICKS_BETWEEN_HINTS;

    @Test
    void partnerPhase_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.PARTNER, MAX, 5));

        assertEquals(0, dice.weightOf(new NpcAction.Hint(PLAYER)));
    }

    @Test
    void seedsBeforeMinimum_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.SEEDS, MIN - 1, 5));

        assertEquals(0, dice.weightOf(new NpcAction.Hint(PLAYER)));
    }

    @Test
    void seedsAfterMinimum_normalWeight() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.SEEDS, MIN, 5));

        assertEquals(10, dice.weightOf(new NpcAction.Hint(PLAYER)));
    }

    @Test
    void afterMaximum_overdueWeight() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.SEEDS, MAX, 5));

        assertEquals(1_000, dice.weightOf(new NpcAction.Hint(PLAYER)));
    }

    @Test
    void laterPhase_alsoProposes() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.REGIME, MIN, 5));

        assertTrue(dice.weightOf(new NpcAction.Hint(PLAYER)) > 0);
    }

    @Test
    void noPlayerInHearingRange_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.SEEDS, MAX, 30));

        assertEquals(0, dice.weightOf(new NpcAction.Hint(PLAYER)));
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new HintOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Phase phase, long ticksSinceHint, double distance) {
        return new Situation(Mood.CALM, List.of(new NearbyPlayer(PLAYER, "Alice", distance)), phase, ticksSinceHint);
    }
}
