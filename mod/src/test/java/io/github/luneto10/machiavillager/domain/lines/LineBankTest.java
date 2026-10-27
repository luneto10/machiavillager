package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LineBankTest {
    private static final LineKey KEY = new LineKey(LineKind.BARK, Phase.SEEDS, Mood.CALM);
    private static final long ASK = LineBank.ASK_INTERVAL_TICKS;

    @Test
    void pick_neverRepeatsOneOfTheLastFive_inAPoolOfEight() {
        LineBank bank = new LineBank(Map.of(KEY, eight()));
        Random random = new Random(7);
        Deque<String> window = new ArrayDeque<>();

        for (int i = 0; i < 500; i++) {
            String line = bank.pick(KEY, random).orElseThrow();
            // Once five picks exist, the new one must not be among them.
            assertFalse(window.contains(line), "repeated " + line + " at pick " + i);
            window.addLast(line);
            if (window.size() > LineBank.RECENT_LIMIT) {
                window.pollFirst();
            }
        }
    }

    @Test
    void pick_inASmallPool_neverRepeatsTheLastLine() {
        LineKey small = new LineKey(LineKind.MUTTER, Phase.WAR, Mood.IRRITATED);
        LineBank bank = new LineBank(Map.of(small, List.of("a", "b", "c")));
        Random random = new Random(7);
        String previous = bank.pick(small, random).orElseThrow();

        for (int i = 0; i < 200; i++) {
            String line = bank.pick(small, random).orElseThrow();
            assertFalse(line.equals(previous));
            previous = line;
        }
    }

    @Test
    void pick_inAPoolOfOne_returnsThatLine() {
        LineBank bank = new LineBank(Map.of(KEY, List.of("only")));

        assertEquals(Optional.of("only"), bank.pick(KEY, new Random(7)));
        assertEquals(Optional.of("only"), bank.pick(KEY, new Random(7)));
    }

    @Test
    void pick_inAnEmptyPool_returnsEmpty() {
        LineBank bank = new LineBank(Map.of());

        assertEquals(Optional.empty(), bank.pick(KEY, new Random(7)));
    }

    @Test
    void needsBatch_trueWhenThePoolIsEmpty() {
        assertTrue(new LineBank(Map.of()).needsBatch(KEY, 0));
    }

    @Test
    void needsBatch_turnsTrueExactlyAtSeventyPercentOfTheUses() {
        LineBank bank = new LineBank(Map.of(KEY, eight()));
        Random random = new Random(7);
        // Eight lines: 70% rounds down to 5 uses.
        for (int i = 0; i < 4; i++) {
            bank.pick(KEY, random);
        }
        assertFalse(bank.needsBatch(KEY, 0));

        bank.pick(KEY, random);
        assertTrue(bank.needsBatch(KEY, 0));
    }

    @Test
    void needsBatch_afterAnAsk_waitsTheFullIntervalBeforeTrueAgain() {
        LineBank bank = new LineBank(Map.of());
        bank.markAsked(KEY, 1_000);

        assertFalse(bank.needsBatch(KEY, 1_000 + ASK - 1));
        assertTrue(bank.needsBatch(KEY, 1_000 + ASK));
    }

    @Test
    void deliver_replacesThePool_andResetsTheUses() {
        LineBank bank = new LineBank(Map.of(KEY, eight()));
        Random random = new Random(7);
        for (int i = 0; i < 6; i++) {
            bank.pick(KEY, random);
        }
        assertTrue(bank.needsBatch(KEY, 0));

        bank.deliver(KEY, List.of("new one", "new two", "new three", "new four", "new five",
                "new six", "new seven", "new eight", "new nine", "new ten"));

        assertFalse(bank.needsBatch(KEY, 0));
        assertTrue(bank.hasLines(KEY));
        assertTrue(bank.pick(KEY, random).orElseThrow().startsWith("new "));
    }

    @Test
    void hasLines_isFalseForAnEmptyPool() {
        assertFalse(new LineBank(Map.of()).hasLines(KEY));
    }

    @Test
    void snapshot_isAnUnmodifiableCopy_andTheBankCopiesItsInput() {
        Map<LineKey, List<String>> input = new HashMap<>();
        input.put(KEY, List.of("a", "b"));
        LineBank bank = new LineBank(input);
        input.put(KEY, List.of("changed"));

        Map<LineKey, List<String>> snapshot = bank.snapshot();
        bank.deliver(KEY, List.of("later"));

        assertEquals(List.of("a", "b"), snapshot.get(KEY));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(KEY, List.of()));
    }

    @Test
    void clear_emptiesEveryPool_andCountsOnlyThePoolsWithLines() {
        LineKey other = new LineKey(LineKind.HINT, Phase.SEEDS, Mood.CALM);
        LineKey empty = new LineKey(LineKind.MUTTER, Phase.WAR, Mood.IRRITATED);
        LineBank bank = new LineBank(new HashMap<>(Map.of(KEY, eight(), other, List.of("one"), empty, List.of())));

        assertEquals(2, bank.clear());
        assertEquals(Map.of(), bank.snapshot());
        assertFalse(bank.hasLines(KEY));
        assertEquals(Optional.empty(), bank.pick(KEY, new Random(7)));
    }

    private static List<String> eight() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            lines.add("line " + i);
        }
        return lines;
    }
}
