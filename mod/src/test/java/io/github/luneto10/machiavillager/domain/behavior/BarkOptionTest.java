package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BarkOptionTest {
    private static final UUID LISTENER = new UUID(0L, 2L);

    @Test
    void within24_isProposed() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, 20));

        assertTrue(dice.weightOf(new NpcAction.Bark(LISTENER)) > 0);
    }

    @Test
    void beyond24_isNotProposed() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.CALM, 30));

        assertEquals(0, dice.weightOf(new NpcAction.Bark(LISTENER)));
    }

    @Test
    void irritated_weighsBarkAt35() {
        WeightedDice<NpcAction> dice = propose(situation(Mood.IRRITATED, 5));

        assertEquals(35, dice.weightOf(new NpcAction.Bark(LISTENER)));
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new BarkOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Mood mood, double distance) {
        return new Situation(mood, List.of(new NearbyPlayer(LISTENER, "Alice", distance)), Phase.PARTNER, 0);
    }
}
