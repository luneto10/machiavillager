package io.github.luneto10.machiavillager.domain.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PersonalityTest {
    private static final long SAME_SEED = 42L;
    private static final long FIRST_SEED = 1L;
    private static final long SECOND_SEED = 2L;
    private static final int SEED_COUNT = 1000;
    private static final double GROWTH = Personality.DEFAULT_GROWTH_PER_HOUR;
    private static final List<Integer> TOO_FEW = List.of(10, 20);
    private static final List<Integer> NOT_INCREASING = List.of(50, 25, 75, 92);

    @Test
    void rolled_sameSeed_sameResult() {
        Personality first = Personality.rolled(new Random(SAME_SEED));
        Personality second = Personality.rolled(new Random(SAME_SEED));

        assertEquals(first, second);
    }

    @Test
    void rolled_differentSeeds_differ() {
        Personality first = Personality.rolled(new Random(FIRST_SEED));
        Personality second = Personality.rolled(new Random(SECOND_SEED));

        assertNotEquals(first, second);
    }

    @Test
    void rolled_manySeeds_thresholdsValid() {
        for (long seed = 0; seed < SEED_COUNT; seed++) {
            Personality personality = Personality.rolled(new Random(seed));
            List<Integer> thresholds = personality.thresholds();

            assertTrue(personality.growthPerHour() >= Personality.MIN_GROWTH_PER_HOUR);
            assertTrue(personality.growthPerHour() < Personality.MAX_GROWTH_PER_HOUR);
            for (int i = 0; i < thresholds.size(); i++) {
                int value = thresholds.get(i);
                assertTrue(value >= 1 && value <= Personality.MAX_THRESHOLD, "seed " + seed + ": " + value);
                int previous = i == 0 ? 0 : thresholds.get(i - 1);
                assertTrue(value > previous, "seed " + seed + ": not increasing " + thresholds);
                boolean clamped = value == Personality.MAX_THRESHOLD;
                assertTrue(clamped || value - previous >= Personality.MIN_PHASE_WIDTH,
                        "seed " + seed + ": narrow phase " + thresholds);
            }
        }
    }

    @Test
    void constructor_rejectsWrongSize() {
        assertThrows(IllegalArgumentException.class, () -> new Personality(GROWTH, TOO_FEW));
    }

    @Test
    void constructor_rejectsNotIncreasing() {
        assertThrows(IllegalArgumentException.class, () -> new Personality(GROWTH, NOT_INCREASING));
    }
}
