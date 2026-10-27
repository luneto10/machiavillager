package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MutterOptionTest {
    private static final UUID PLAYER = new UUID(0L, 3L);

    @Test
    void nobodyWithin8_proposesNothing() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.BORED, 10));

        assertEquals(0, dice.weightOf(new NpcAction.Mutter()));
    }

    @Test
    void playerAt6_isProposedWithMoodWeight() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.BORED, 6));

        assertEquals(6, dice.weightOf(new NpcAction.Mutter()));
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new MutterOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Mood mood, double distance) {
        return new Situation(
                mood, List.of(new NearbyPlayer(PLAYER, "Alice", distance, Relationships.DEFAULT_AFFINITY)), Phase.PARTNER, 0, Set.of(), Set.of());
    }
}
