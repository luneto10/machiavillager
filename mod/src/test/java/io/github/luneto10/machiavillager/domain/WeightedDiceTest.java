package io.github.luneto10.machiavillager.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedDiceTest {
    private static final long SEED = 42L;
    private static final int ROLLS = 100_000;
    private static final double TOLERANCE = 0.01;

    enum Action { FOLLOW, WANDER, SAY, CHAOS }

    @Test
    void build_negativeWeight_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedDice.<Action>builder().add(Action.FOLLOW, -1).build());
    }

    @Test
    void build_allZeroWeights_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedDice.<Action>builder().add(Action.FOLLOW, 0).add(Action.WANDER, 0).build());
    }

    @Test
    void build_noOutcomes_throws() {
        assertThrows(IllegalArgumentException.class, () -> WeightedDice.<Action>builder().build());
    }

    @Test
    void build_duplicateOutcome_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedDice.<Action>builder().add(Action.FOLLOW, 1).add(Action.FOLLOW, 2));
    }

    @Test
    void roll_zeroWeight_isNeverRolled() {
        WeightedDice<Action> dice = WeightedDice.<Action>builder()
                .add(Action.FOLLOW, 10)
                .add(Action.CHAOS, 0)
                .build();
        Random random = new Random(SEED);

        for (int i = 0; i < ROLLS; i++) {
            assertFalse(dice.roll(random) == Action.CHAOS);
        }
    }

    @Test
    void roll_sameSeed_givesSameSequence() {
        WeightedDice<Action> dice = standardDice();

        List<Action> first = rollMany(dice, new Random(SEED), 50);
        List<Action> second = rollMany(dice, new Random(SEED), 50);

        assertEquals(first, second);
    }

    @Test
    void roll_frequencies_matchWeights() {
        WeightedDice<Action> dice = standardDice();
        Map<Action, Integer> counts = new EnumMap<>(Action.class);
        Random random = new Random(SEED);

        for (int i = 0; i < ROLLS; i++) {
            counts.merge(dice.roll(random), 1, Integer::sum);
        }

        for (Action action : Action.values()) {
            double observed = counts.getOrDefault(action, 0) / (double) ROLLS;
            assertEquals(dice.chanceOf(action), observed, TOLERANCE, action.name());
        }
    }

    @Test
    void chanceOf_returnsWeightOverTotal() {
        WeightedDice<Action> dice = WeightedDice.<Action>builder()
                .add(Action.FOLLOW, 50)
                .add(Action.WANDER, 50)
                .build();

        assertEquals(0.5, dice.chanceOf(Action.FOLLOW), 1e-9);
        assertEquals(0.5, dice.chanceOf(Action.WANDER), 1e-9);
        assertEquals(0.0, dice.chanceOf(Action.SAY), 1e-9);
    }

    @Test
    void withWeight_returnsNewDice_andLeavesOriginalUnchanged() {
        WeightedDice<Action> original = standardDice();

        WeightedDice<Action> changed = original.withWeight(Action.FOLLOW, 0);

        assertEquals(50, original.weightOf(Action.FOLLOW));
        assertEquals(0, changed.weightOf(Action.FOLLOW));
        assertFalse(changed == original);
    }

    @Test
    void withWeight_newOutcome_isAppended() {
        WeightedDice<Action> original = WeightedDice.<Action>builder().add(Action.FOLLOW, 1).build();

        WeightedDice<Action> changed = original.withWeight(Action.WANDER, 1);

        assertEquals(List.of(Action.FOLLOW, Action.WANDER), changed.outcomes());
        assertEquals(List.of(Action.FOLLOW), original.outcomes());
    }

    @Test
    void withWeight_toZeroForAll_throws() {
        WeightedDice<Action> dice = WeightedDice.<Action>builder().add(Action.FOLLOW, 1).build();

        assertThrows(IllegalArgumentException.class, () -> dice.withWeight(Action.FOLLOW, 0));
    }

    @Test
    void outcomes_isUnmodifiable() {
        List<Action> outcomes = standardDice().outcomes();

        assertThrows(UnsupportedOperationException.class, () -> outcomes.add(Action.SAY));
    }

    private static WeightedDice<Action> standardDice() {
        return WeightedDice.<Action>builder()
                .add(Action.FOLLOW, 50)
                .add(Action.WANDER, 25)
                .add(Action.SAY, 20)
                .add(Action.CHAOS, 5)
                .build();
    }

    private static List<Action> rollMany(WeightedDice<Action> dice, Random random, int count) {
        List<Action> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rolls.add(dice.roll(random));
        }
        return rolls;
    }
}
