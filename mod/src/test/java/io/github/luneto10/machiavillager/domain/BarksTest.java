package io.github.luneto10.machiavillager.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BarksTest {
    private static final long SEED = 42L;

    @Test
    void pick_alwaysReturnsOneOfTheLines() {
        Barks barks = new Barks("a", "b", "c");
        Random random = new Random(SEED);

        for (int i = 0; i < 100; i++) {
            assertTrue(barks.lines().contains(barks.pick(random)));
        }
    }

    @Test
    void pick_sameSeed_givesSameSequence() {
        Barks barks = new Barks("a", "b", "c", "d");

        List<String> first = pickMany(barks, new Random(SEED), 20);
        List<String> second = pickMany(barks, new Random(SEED), 20);

        assertEquals(first, second);
    }

    @Test
    void pick_overManyRolls_usesEveryLine() {
        Barks barks = new Barks("a", "b", "c");

        Set<String> seen = new HashSet<>(pickMany(barks, new Random(SEED), 1000));

        assertEquals(Set.of("a", "b", "c"), seen);
    }

    @Test
    void constructor_emptyList_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Barks(List.of()));
    }

    @Test
    void constructor_copiesTheList_soLaterChangesDoNotLeakIn() {
        List<String> original = new ArrayList<>(List.of("a"));
        Barks barks = new Barks(original);

        original.add("sneaky");

        assertEquals(List.of("a"), barks.lines());
        assertThrows(UnsupportedOperationException.class, () -> barks.lines().add("nope"));
    }

    private static List<String> pickMany(Barks barks, Random random, int count) {
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            picks.add(barks.pick(random));
        }
        return picks;
    }
}
