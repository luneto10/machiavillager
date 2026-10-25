package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowOptionTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final double FAR = 300.0;

    @Test
    void noPlayers_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(new Situation(Mood.CALM, List.of(), Phase.PARTNER, 0, Set.of(), Set.of()));

        assertEquals(0.0, dice.chanceOf(new NpcAction.Follow(PLAYER)), 1e-9);
    }

    @Test
    void playerAt300Blocks_notCared_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, FAR, Set.of()));

        assertEquals(0, dice.weightOf(new NpcAction.Follow(PLAYER)));
    }

    @Test
    void playerAt300Blocks_cared_isProposedWithFarWeight() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, FAR, Set.of(PLAYER)));

        assertEquals(20, dice.weightOf(new NpcAction.Follow(PLAYER)));
    }

    @Test
    void playerWithin32_isProposed() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, 30, Set.of()));

        assertTrue(dice.weightOf(new NpcAction.Follow(PLAYER)) > 0);
    }

    @Test
    void nearPlayer_notCared_isStillProposed() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.IRRITATED, 5, Set.of()));

        assertEquals(20, dice.weightOf(new NpcAction.Follow(PLAYER)));
    }

    @Test
    void weightDependsOnMood() {
        WeightedDice<NpcAction> calm = propose(situation(Mood.CALM, 5, Set.of()));
        WeightedDice<NpcAction> irritated = propose(situation(Mood.IRRITATED, 5, Set.of()));

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

    private static Situation situation(Mood mood, double distance, Set<UUID> caredFor) {
        return new Situation(
                mood,
                List.of(new NearbyPlayer(PLAYER, "Alice", distance)),
                Phase.PARTNER,
                0,
                Set.of(),
                caredFor);
    }
}
