package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SummonOptionTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final double FAR = 40.0;
    private static final double TOO_CLOSE = 9.0;
    private static final int PARTNER_WEIGHT = 2;
    private static final int REGIME_WEIGHT = 12;

    @Test
    void notSummonable_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.REGIME, FAR, false));

        assertEquals(0, dice.weightOf(new NpcAction.Summon(PLAYER)));
    }

    @Test
    void tooClose_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.REGIME, TOO_CLOSE, true));

        assertEquals(0, dice.weightOf(new NpcAction.Summon(PLAYER)));
    }

    @Test
    void farAndSummonable_isProposed() {
        WeightedDice<NpcAction> dice = propose(situation(Phase.REGIME, FAR, true));

        assertTrue(dice.weightOf(new NpcAction.Summon(PLAYER)) > 0);
    }

    @Test
    void weightGrowsWithPhase() {
        int partner = propose(situation(Phase.PARTNER, FAR, true)).weightOf(new NpcAction.Summon(PLAYER));
        int regime = propose(situation(Phase.REGIME, FAR, true)).weightOf(new NpcAction.Summon(PLAYER));

        assertEquals(PARTNER_WEIGHT, partner);
        assertEquals(REGIME_WEIGHT, regime);
        assertTrue(partner < regime);
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new SummonOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Phase phase, double distance, boolean summonable) {
        Set<UUID> allowed = summonable ? Set.of(PLAYER) : Set.of();
        return new Situation(
                Mood.CALM, List.of(new NearbyPlayer(PLAYER, "Alice", distance)), phase, 0, allowed);
    }
}
