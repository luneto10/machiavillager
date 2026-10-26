package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventIdsTest {
    private static final String SHAPE = "^evt_[0-9a-f]{16}$";

    @Test
    void newEventId_hasThePrefixAndSixteenHexDigits() {
        assertTrue(EventIds.newEventId().matches(SHAPE));
    }

    @Test
    void newEventId_differsBetweenCalls() {
        assertNotEquals(EventIds.newEventId(), EventIds.newEventId());
    }
}
