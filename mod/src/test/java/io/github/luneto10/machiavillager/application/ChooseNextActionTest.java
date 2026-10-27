package io.github.luneto10.machiavillager.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NearbyPlayer;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChooseNextActionTest {
    private static final long SEED = 42L;
    private static final int ROLLS = 100_000;
    private static final double TOLERANCE = 0.01;
    private static final UUID PLAYER = new UUID(0L, 1L);

    // Weights for one player at 5 blocks, calm, partner phase: Follow, Wander, Bark and Mutter. Hint needs SEEDS.
    private static final int CALM_FOLLOW = 50;
    private static final int CALM_WANDER = 25;
    private static final int CALM_BARK = 20;
    private static final int CALM_MUTTER = 3;
    private static final int CALM_TOTAL = CALM_FOLLOW + CALM_WANDER + CALM_BARK + CALM_MUTTER;

    @Test
    void emptyCatalog_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ChooseNextAction(List.of()));
    }

    @Test
    void noPlayersNearby_alwaysWanders() {
        ChooseNextAction useCase = ChooseNextAction.withDefaultCatalog();
        Situation alone = situation(Mood.CALM, List.of());
        Random random = new Random(SEED);

        for (int i = 0; i < 1000; i++) {
            assertEquals(new NpcAction.Wander(), useCase.choose(alone, random));
        }
    }

    @Test
    void playerNearby_canFollowWanderBarkOrMutter() {
        ChooseNextAction useCase = ChooseNextAction.withDefaultCatalog();
        Situation withPlayer = calmWithPlayerAt(5);
        Random random = new Random(SEED);
        Set<Class<?>> seen = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            seen.add(useCase.choose(withPlayer, random).getClass());
        }

        assertEquals(Set.of(NpcAction.Follow.class, NpcAction.Wander.class,
                NpcAction.Bark.class, NpcAction.Mutter.class), seen);
    }

    @Test
    void calmWithOnePlayerAt5Blocks_frequenciesMatchWeights() {
        Map<Class<?>, Integer> counts = rollCounts(ChooseNextAction.withDefaultCatalog(),
                calmWithPlayerAt(5), new Random(SEED), ROLLS);

        assertEquals((double) CALM_FOLLOW / CALM_TOTAL, fraction(counts, NpcAction.Follow.class), TOLERANCE);
        assertEquals((double) CALM_WANDER / CALM_TOTAL, fraction(counts, NpcAction.Wander.class), TOLERANCE);
        assertEquals((double) CALM_BARK / CALM_TOTAL, fraction(counts, NpcAction.Bark.class), TOLERANCE);
        assertEquals((double) CALM_MUTTER / CALM_TOTAL, fraction(counts, NpcAction.Mutter.class), TOLERANCE);
    }

    @Test
    void sameSeed_sameSequence() {
        ChooseNextAction useCase = ChooseNextAction.withDefaultCatalog();
        Situation withPlayer = calmWithPlayerAt(5);

        List<NpcAction> first = rollSequence(useCase, withPlayer, new Random(SEED), 50);
        List<NpcAction> second = rollSequence(useCase, withPlayer, new Random(SEED), 50);

        assertEquals(first, second);
    }

    @Test
    void boredMood_wandersMoreThanCalm() {
        ChooseNextAction useCase = ChooseNextAction.withDefaultCatalog();
        Situation bored = situation(Mood.BORED, List.of(nearbyAt(5)));

        double boredWander = fraction(rollCounts(useCase, bored, new Random(SEED), ROLLS),
                NpcAction.Wander.class);
        double calmWander = fraction(rollCounts(useCase, calmWithPlayerAt(5), new Random(SEED), ROLLS),
                NpcAction.Wander.class);

        assertTrue(boredWander > calmWander, "bored=" + boredWander + " calm=" + calmWander);
    }

    @Test
    void odds_chancesSumToOne() {
        WeightedDice<NpcAction> odds = ChooseNextAction.withDefaultCatalog().odds(calmWithPlayerAt(5));

        double total = 0;
        for (NpcAction action : odds.outcomes()) {
            total += odds.chanceOf(action);
        }

        assertEquals(1.0, total, 1e-9);
    }

    @Test
    void hint_onlyOnceTheStoryHasStarted() {
        // Hint is possible at SEEDS after the hint gap, and never in the partner phase.
        Situation hintReady = new Situation(Mood.CALM, List.of(nearbyAt(5)), Phase.SEEDS, 24_000, Set.of(), Set.of());
        ChooseNextAction useCase = ChooseNextAction.withDefaultCatalog();

        assertTrue(useCase.odds(hintReady).outcomes().stream().anyMatch(NpcAction.Hint.class::isInstance));
        assertFalse(useCase.odds(calmWithPlayerAt(5)).outcomes().stream()
                .anyMatch(NpcAction.Hint.class::isInstance));
    }

    private static Situation calmWithPlayerAt(double distance) {
        return situation(Mood.CALM, List.of(nearbyAt(distance)));
    }

    // Partner phase, no hint history: the story has not started yet.
    private static Situation situation(Mood mood, List<NearbyPlayer> nearby) {
        return new Situation(mood, nearby, Phase.PARTNER, 0, Set.of(), Set.of());
    }

    private static NearbyPlayer nearbyAt(double distance) {
        return new NearbyPlayer(PLAYER, "Alice", distance);
    }

    private static List<NpcAction> rollSequence(ChooseNextAction useCase, Situation situation,
            Random random, int count) {
        List<NpcAction> actions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            actions.add(useCase.choose(situation, random));
        }
        return actions;
    }

    private static Map<Class<?>, Integer> rollCounts(ChooseNextAction useCase, Situation situation,
            Random random, int rolls) {
        Map<Class<?>, Integer> counts = new HashMap<>();
        for (int i = 0; i < rolls; i++) {
            counts.merge(useCase.choose(situation, random).getClass(), 1, Integer::sum);
        }
        return counts;
    }

    private static double fraction(Map<Class<?>, Integer> counts, Class<?> kind) {
        return counts.getOrDefault(kind, 0) / (double) ROLLS;
    }
}
