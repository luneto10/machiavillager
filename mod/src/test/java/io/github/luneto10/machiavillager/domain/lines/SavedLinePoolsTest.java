package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SavedLinePoolsTest {
    private static final LineKey BARK = new LineKey(LineKind.BARK, Phase.SEEDS, Mood.CALM);

    @Test
    void validPool_loadsAsSaved() {
        Map<LineKey, List<String>> pools = SavedLinePools.parse(Map.of(BARK.id(), List.of("Hi, {player}.")));

        assertEquals(List.of("Hi, {player}."), pools.get(BARK));
    }

    @Test
    void invalidLines_areDroppedAndEmptyPoolsSkipped() {
        // A bark with {x} is a foreign placeholder, and an over-long line is refused by length.
        String tooLong = "a".repeat(LineRules.MAX_LENGTH + 1);
        Map<LineKey, List<String>> pools = SavedLinePools.parse(Map.of(
                BARK.id(), List.of("Hi {x}.", tooLong, "Fine line."),
                "bark:seeds:irritated", List.of("{x}")));

        assertEquals(List.of("Fine line."), pools.get(BARK));
        assertEquals(1, pools.size());
        assertFalse(pools.containsKey(new LineKey(LineKind.BARK, Phase.SEEDS, Mood.IRRITATED)));
    }

    @Test
    void unknownIds_areSkipped() {
        Map<LineKey, List<String>> pools = SavedLinePools.parse(Map.of("gossip:seeds:calm", List.of("Hi.")));

        assertEquals(Map.of(), pools);
    }
}
