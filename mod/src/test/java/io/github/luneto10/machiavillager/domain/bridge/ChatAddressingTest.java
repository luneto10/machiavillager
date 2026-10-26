package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatAddressingTest {
    private static final String VAR = ChatAddressing.REQUIRE_NAME_VARIABLE;

    @Test
    void nameMention_isNameInBothModes() {
        assertEquals(Optional.of(Addressed.NAME), ChatAddressing.decide("hey machi", false));
        assertEquals(Optional.of(Addressed.NAME), ChatAddressing.decide("hey machi", true));
    }

    @Test
    void noName_isNearWhenNamesAreOptional() {
        assertEquals(Optional.of(Addressed.NEAR), ChatAddressing.decide("nice weather", false));
    }

    @Test
    void noName_isDroppedWhenNamesAreRequired() {
        assertEquals(Optional.empty(), ChatAddressing.decide("nice weather", true));
    }

    @Test
    void decide_rejectsNull() {
        assertThrows(NullPointerException.class, () -> ChatAddressing.decide(null, false));
    }

    @Test
    void requireName_isOffWhenUnset() {
        assertFalse(ChatAddressing.requireName(Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True", " true "})
    void requireName_isOnForTrueInAnyCase(String raw) {
        assertTrue(ChatAddressing.requireName(Map.of(VAR, raw)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "garbage", "", "1", "yes"})
    void requireName_isOffForAnythingElse(String raw) {
        assertFalse(ChatAddressing.requireName(Map.of(VAR, raw)));
    }
}
