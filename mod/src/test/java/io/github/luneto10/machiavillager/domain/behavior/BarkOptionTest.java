package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BarkOptionTest {
    private static final UUID LISTENER = new UUID(0L, 2L);
    private static final UUID COLD = new UUID(0L, 3L);
    private static final UUID WARM = new UUID(0L, 4L);

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

    @Test
    void affinityBelowTheFriendlyEdge_isNotProposed() {
        int cold = Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES - 1;
        WeightedDice<NpcAction> dice = propose(situationWithAffinity(cold));

        assertEquals(0, dice.weightOf(new NpcAction.Bark(LISTENER)));
    }

    @Test
    void affinityAtTheFriendlyEdge_isProposed() {
        WeightedDice<NpcAction> dice = propose(situationWithAffinity(Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES));

        assertTrue(dice.weightOf(new NpcAction.Bark(LISTENER)) > 0);
    }

    @Test
    void mixedListeners_onlyTheWarmOneIsProposed() {
        Situation mixed = new Situation(
                Mood.CALM,
                List.of(
                        new NearbyPlayer(COLD, "Bob", 5, Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES - 1),
                        new NearbyPlayer(WARM, "Alice", 5, Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES)),
                Phase.PARTNER,
                0,
                Set.of(),
                Set.of());

        WeightedDice<NpcAction> dice = propose(mixed);

        assertEquals(0, dice.weightOf(new NpcAction.Bark(COLD)));
        assertTrue(dice.weightOf(new NpcAction.Bark(WARM)) > 0);
    }

    /** Runs the option into a builder pre-seeded with a Wander of weight 1, so build() never fails. */
    private static WeightedDice<NpcAction> propose(Situation situation) {
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        candidates.add(new NpcAction.Wander(), 1);
        new BarkOption().propose(situation, candidates);
        return candidates.build();
    }

    private static Situation situation(Mood mood, double distance) {
        return situationOf(mood, distance, Relationships.DEFAULT_AFFINITY);
    }

    private static Situation situationWithAffinity(int affinity) {
        return situationOf(Mood.CALM, 5, affinity);
    }

    private static Situation situationOf(Mood mood, double distance, int affinity) {
        NearbyPlayer listener = new NearbyPlayer(LISTENER, "Alice", distance, affinity);
        return new Situation(mood, List.of(listener), Phase.PARTNER, 0, Set.of(), Set.of());
    }
}
