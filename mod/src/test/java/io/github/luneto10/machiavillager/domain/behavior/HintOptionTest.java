package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HintOptionTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final UUID COLD = new UUID(0L, 3L);
    private static final UUID WARM = new UUID(0L, 4L);
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

    @Test
    void affinityBelowTheFriendlyEdge_isNotProposed() {
        int cold = Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES - 1;
        WeightedDice<NpcAction> dice = propose(situationWithAffinity(cold));

        assertEquals(0, dice.weightOf(new NpcAction.Hint(PLAYER)));
    }

    @Test
    void affinityAtTheFriendlyEdge_isProposed() {
        WeightedDice<NpcAction> dice = propose(situationWithAffinity(Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES));

        assertTrue(dice.weightOf(new NpcAction.Hint(PLAYER)) > 0);
    }

    @Test
    void mixedListeners_onlyTheWarmOneIsProposed() {
        Situation mixed = new Situation(
                Mood.CALM,
                List.of(
                        new NearbyPlayer(COLD, "Bob", 5, Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES - 1),
                        new NearbyPlayer(WARM, "Alice", 5, Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES)),
                Phase.SEEDS,
                MIN,
                Set.of(),
                Set.of());

        WeightedDice<NpcAction> dice = propose(mixed);

        assertEquals(0, dice.weightOf(new NpcAction.Hint(COLD)));
        assertTrue(dice.weightOf(new NpcAction.Hint(WARM)) > 0);
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new HintOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Phase phase, long ticksSinceHint, double distance) {
        return situationOf(phase, ticksSinceHint, distance, Relationships.DEFAULT_AFFINITY);
    }

    private static Situation situationWithAffinity(int affinity) {
        return situationOf(Phase.SEEDS, MAX, 5, affinity);
    }

    private static Situation situationOf(Phase phase, long ticksSinceHint, double distance, int affinity) {
        NearbyPlayer listener = new NearbyPlayer(PLAYER, "Alice", distance, affinity);
        return new Situation(Mood.CALM, List.of(listener), phase, ticksSinceHint, Set.of(), Set.of());
    }
}
