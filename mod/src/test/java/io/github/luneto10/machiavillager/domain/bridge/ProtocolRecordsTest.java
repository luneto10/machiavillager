package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolRecordsTest {
    private static final String ACTION_ID = "act_Ab12Cd34";
    private static final String EVENT_ID = "evt_Ab12Cd34";
    private static final String CONVERSATION_ID = "conv_Ab12Cd34";
    private static final UUID PLAYER = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void hello_acceptsModVersionAtTheLimit() {
        // Arrange and Act: exactly 32 characters is the largest allowed.
        Hello hello = new Hello("v".repeat(32));

        // Assert
        assertEquals(32, hello.modVersion().length());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 33})
    void hello_rejectsModVersionOutsideOneToThirtyTwo(int length) {
        assertThrows(IllegalArgumentException.class, () -> new Hello("v".repeat(length)));
    }

    @Test
    void lengthIsCountedInCodePoints_notUtf16Units() {
        // 256 emoji are 512 UTF-16 units but 256 characters, so the contract accepts them as one line.
        String emoji = "😀".repeat(256);

        assertEquals(512, new SpeakAction(ACTION_ID, List.of(emoji), 5000, Optional.empty()).lines().get(0).length());
    }

    @Test
    void speakAction_acceptsOneAndEightLines() {
        SpeakAction one = new SpeakAction(ACTION_ID, List.of("hi"), 100, Optional.empty());
        SpeakAction eight = new SpeakAction(ACTION_ID, List.of("a", "b", "c", "d", "e", "f", "g", "h"),
                120_000, Optional.of(CONVERSATION_ID));

        assertEquals(1, one.lines().size());
        assertEquals(8, eight.lines().size());
    }

    @Test
    void speakAction_rejectsNoLinesAndNineLines() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(ACTION_ID, List.of(), 5000, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new SpeakAction(ACTION_ID,
                List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"), 5000, Optional.empty()));
    }

    @Test
    void speakAction_rejectsEmptyAndTooLongLines() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(ACTION_ID, List.of(""), 5000, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(ACTION_ID, List.of("x".repeat(257)), 5000, Optional.empty()));
    }

    @Test
    void speakAction_rejectsTimeoutOutsideRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(ACTION_ID, List.of("hi"), 99, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(ACTION_ID, List.of("hi"), 120_001, Optional.empty()));
    }

    @Test
    void speakAction_copiesLinesSoLaterChangesDoNotLeakIn() {
        var lines = new java.util.ArrayList<>(List.of("hi"));
        SpeakAction action = new SpeakAction(ACTION_ID, lines, 5000, Optional.empty());

        lines.add("later");

        assertEquals(1, action.lines().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"act_short", "act_Ab12Cd34Ef56Gh78Ij90Kl12Mn34Op56Q", "xct_Ab12Cd34", "act_Ab12Cd34\n"})
    void actionId_rejectsWrongFormat(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(id, List.of("hi"), 5000, Optional.empty()));
    }

    @Test
    void conversationId_rejectsWrongFormatWhenPresent() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpeakAction(ACTION_ID, List.of("hi"), 5000, Optional.of("conv_x")));
    }

    @Test
    void failedAndRejected_acceptEmptyReasonAndRejectLongOne() {
        assertEquals("", new ActionStatus.Failed(ACTION_ID, "").reason());
        assertThrows(IllegalArgumentException.class,
                () -> new ActionStatus.Rejected(ACTION_ID, "r".repeat(201)));
    }

    @Test
    void interrupted_requiresSpokenNonNegativeAndOfBetweenOneAndEight() {
        assertThrows(IllegalArgumentException.class, () -> new ActionStatus.Interrupted(ACTION_ID, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new ActionStatus.Interrupted(ACTION_ID, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ActionStatus.Interrupted(ACTION_ID, 0, 9));
    }

    @Test
    void playerChatEvent_acceptsValidAndRejectsBadFields() {
        PlayerChatEvent event = new PlayerChatEvent(EVENT_ID, NOW, PLAYER, "Ana", "hello", Optional.empty());

        assertEquals("Ana", event.playerName());
        assertThrows(IllegalArgumentException.class, () -> new PlayerChatEvent(
                EVENT_ID, NOW, PLAYER, "n".repeat(17), "hello", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PlayerChatEvent(
                EVENT_ID, NOW, PLAYER, "Ana", "", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new PlayerChatEvent(
                "evt_bad", NOW, PLAYER, "Ana", "hello", Optional.empty()));
    }

    @Test
    void bridgeError_rejectsDetailOverFiveHundred() {
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeError(BridgeError.Code.INTERNAL, "d".repeat(501)));
    }

    @Test
    void errorCode_wireNamesMatchTheContract() {
        assertEquals("bad_message", BridgeError.Code.BAD_MESSAGE.wireName());
        assertEquals("unsupported_protocol", BridgeError.Code.UNSUPPORTED_PROTOCOL.wireName());
    }
}
