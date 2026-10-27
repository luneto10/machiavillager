package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineKeyTest {

    @Test
    void id_isLowercaseKindPhaseMood_andRoundTrips() {
        LineKey key = new LineKey(LineKind.HINT, Phase.SEEDS, Mood.BORED);

        assertEquals("hint:seeds:bored", key.id());
        assertEquals(Optional.of(key), LineKey.fromId(key.id()));
    }

    @Test
    void fromId_refusesWrongShapeUnknownParts_andNull() {
        assertTrue(LineKey.fromId("hint:seeds").isEmpty());
        assertTrue(LineKey.fromId("hint:seeds:").isEmpty());
        assertTrue(LineKey.fromId("shout:seeds:bored").isEmpty());
        assertTrue(LineKey.fromId("hint:nowhere:bored").isEmpty());
        assertTrue(LineKey.fromId("hint:seeds:angry").isEmpty());
        assertTrue(LineKey.fromId(null).isEmpty());
    }

    @Test
    void lineKind_wireRoundTrips_andRefusesUnknownNames() {
        for (LineKind kind : LineKind.values()) {
            assertEquals(Optional.of(kind), LineKind.fromWire(kind.wire()));
        }
        assertTrue(LineKind.fromWire("Bark").isEmpty());
        assertTrue(LineKind.fromWire(null).isEmpty());
    }

    @Test
    void constructor_refusesNullPart() {
        assertThrows(NullPointerException.class, () -> new LineKey(null, Phase.PARTNER, Mood.CALM));
    }
}
