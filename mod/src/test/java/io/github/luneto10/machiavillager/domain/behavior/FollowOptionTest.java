package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowOptionTest {
    private static final UUID PLAYER = new UUID(0L, 1L);

    @Test
    void noPlayers_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(new Situation(Mood.CALM, List.of()));

        assertEquals(0.0, dice.chanceOf(new NpcAction.Follow(PLAYER)), 1e-9);
    }

    @Test
    void playerAt300Blocks_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, 300));

        assertEquals(0, dice.weightOf(new NpcAction.Follow(PLAYER)));
    }

    @Test
    void playerWithin16_isProposed() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, 10));

        assertTrue(dice.weightOf(new NpcAction.Follow(PLAYER)) > 0);
    }

    @Test
    void weightDependsOnMood() {
        WeightedDice<NpcAction> calm = propose(situation(Mood.CALM, 5));
        WeightedDice<NpcAction> irritated = propose(situation(Mood.IRRITATED, 5));

        assertEquals(50, calm.weightOf(new NpcAction.Follow(PLAYER)));
        assertEquals(20, irritated.weightOf(new NpcAction.Follow(PLAYER)));
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new FollowOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Mood mood, double distance) {
        return new Situation(mood, List.of(new NearbyPlayer(PLAYER, "Alice", distance)));
    }
}
