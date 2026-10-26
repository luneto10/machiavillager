package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Only what the records still do: copy their lists and refuse nulls. The wire limits are tested by the contract. */
class ProtocolRecordsTest {
    private static final String ACTION_ID = "act_Ab12Cd34";

    @Test
    void speakAction_copiesLinesSoLaterChangesDoNotLeakIn() {
        List<String> lines = new ArrayList<>(List.of("hi"));
        SpeakAction action = new SpeakAction(ACTION_ID, lines, 5000, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), OptionalInt.empty());

        lines.add("later");

        assertEquals(1, action.lines().size());
    }

    @Test
    void speakAction_refusesNullLines() {
        assertThrows(NullPointerException.class,
                () -> new SpeakAction(ACTION_ID, null, 5000, Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), OptionalInt.empty()));
    }

    @Test
    void devModelEvent_setNeedsAModel() {
        assertThrows(IllegalArgumentException.class,
                () -> new DevModelEvent("evt_0123456789abcdef", Instant.EPOCH, DevModelOp.SET, Optional.empty()));
    }

    @Test
    void devModelEvent_resetTakesNoModel() {
        assertThrows(IllegalArgumentException.class,
                () -> new DevModelEvent("evt_0123456789abcdef", Instant.EPOCH, DevModelOp.RESET, Optional.of("x")));
    }

    @Test
    void welcome_copiesModelsSoLaterChangesDoNotLeakIn() {
        List<String> models = new ArrayList<>(List.of("groq:qwen/qwen3.8-27b"));
        Welcome welcome = new Welcome(models);

        models.add("later");

        assertEquals(1, welcome.models().size());
    }

    @Test
    void errorCode_wireNamesMatchTheContract() {
        assertEquals("bad_message", BridgeError.Code.BAD_MESSAGE.wireName());
        assertEquals("unsupported_protocol", BridgeError.Code.UNSUPPORTED_PROTOCOL.wireName());
    }

    @Test
    void addressed_wireNamesRoundTrip() {
        for (Addressed value : Addressed.values()) {
            assertEquals(value, Addressed.fromWire(value.wireName()));
        }
        assertEquals("butt_in", Addressed.BUTT_IN.wireName());
    }
}
