package io.github.luneto10.machiavillager.domain.meeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReadingTimeTest {

    @Test
    void emptyLine_takesOnlyTheBaseTime() {
        assertEquals(40, ReadingTime.ticksFor(""));
    }

    @Test
    void twentyCharacters_addOneTickEach() {
        assertEquals(60, ReadingTime.ticksFor("01234567890123456789"));
    }

    @Test
    void nullLine_throws() {
        assertThrows(NullPointerException.class, () -> ReadingTime.ticksFor(null));
    }
}
