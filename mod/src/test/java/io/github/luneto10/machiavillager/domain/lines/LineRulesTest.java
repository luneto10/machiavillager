package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineRulesTest {

    @Test
    void bark_acceptsPlayerPlaceholder_andRefusesOtherBraces() {
        assertTrue(LineRules.isValidLine(LineKind.BARK, "{player}, hello there"));
        assertFalse(LineRules.isValidLine(LineKind.BARK, "{x}, hello there"));
        assertFalse(LineRules.isValidLine(LineKind.BARK, "{{player}}"));
    }

    @Test
    void mutter_refusesEveryBrace() {
        assertTrue(LineRules.isValidLine(LineKind.MUTTER, "Plain words."));
        assertFalse(LineRules.isValidLine(LineKind.MUTTER, "{player} mutters"));
    }

    @Test
    void hint_needsBothCoordinates_andMayUsePlayer() {
        assertTrue(LineRules.isValidLine(LineKind.HINT, "{player}: the village at {x}, {z}"));
        assertFalse(LineRules.isValidLine(LineKind.HINT, "the village at {x}"));
        assertFalse(LineRules.isValidLine(LineKind.HINT, "the village at {z}"));
        assertFalse(LineRules.isValidLine(LineKind.HINT, "{x}, {z}, {name}"));
    }

    @Test
    void line_lengthBoundsAreInclusiveAt160() {
        assertTrue(LineRules.isValidLine(LineKind.MUTTER, "a".repeat(LineRules.MAX_LENGTH)));
        assertFalse(LineRules.isValidLine(LineKind.MUTTER, "a".repeat(LineRules.MAX_LENGTH + 1)));
    }

    @Test
    void empty_blank_andNull_linesAreRefused() {
        assertFalse(LineRules.isValidLine(LineKind.BARK, ""));
        assertFalse(LineRules.isValidLine(LineKind.BARK, "   "));
        assertFalse(LineRules.isValidLine(LineKind.BARK, null));
    }

    @Test
    void validLines_keepsValidOnesInOrder() {
        List<String> batch = List.of("ok one", "{x} bad", "ok two", "", "{player} ok three");

        assertEquals(List.of("ok one", "ok two", "{player} ok three"), LineRules.validLines(LineKind.BARK, batch));
    }

    @Test
    void validLines_capsAtMaxLines_keepingTheFirstOnes() {
        List<String> batch = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            batch.add("line " + i);
        }

        List<String> valid = LineRules.validLines(LineKind.MUTTER, batch);

        assertEquals(LineRules.MAX_LINES, valid.size());
        assertEquals(batch.subList(0, LineRules.MAX_LINES), valid);
    }

    @Test
    void fill_replacesPlayerXAndZ() {
        assertEquals("Ana at 12,-7", LineRules.fill("{player} at {x},{z}", "Ana", 12, -7));
    }

    @Test
    void fill_doesNotRefillAPlayerNameThatLooksLikeAPlaceholder() {
        assertEquals("hi {x}", LineRules.fill("hi {player}", "{x}", 1, 2));
    }
}
