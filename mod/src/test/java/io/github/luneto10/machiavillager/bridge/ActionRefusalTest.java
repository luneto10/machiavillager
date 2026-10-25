package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.bridge.ProtocolRules;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActionRefusalTest {
    private static final String ID = "act_Ab12Cd34";

    @Test
    void peek_returnsTheIdOfAWellFormedAction_evenWhenTheArgsAreBroken() {
        String json = "{\"v\":1,\"type\":\"action\",\"request_id\":\"" + ID + "\",\"action\":\"dance\"}";

        assertEquals(Optional.of(ID), MessageCodec.peekActionRequestId(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "[]",
        "{\"v\":1,\"type\":\"welcome\",\"request_id\":\"" + ID + "\"}",
        "{\"v\":1,\"type\":\"action\"}",
        "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_x\"}",
        "{\"v\":1,\"type\":\"action\",\"request_id\":42}",
        "{\"v\":1,\"type\":\"action\",\"request_id\":null}",
        "{\"v\":1,\"type\":null,\"request_id\":\"" + ID + "\"}",
    })
    void peek_returnsEmpty_whenTheIdCannotBeTrusted(String json) {
        assertFalse(MessageCodec.peekActionRequestId(json).isPresent());
    }

    @Test
    void peek_nameOnlyTheIdFormat_neverTheRest() {
        String json = "{\"v\":1,\"type\":\"action\",\"request_id\":\"" + ID + "\",\"secret\":\"x\"}";

        assertEquals(ID, MessageCodec.peekActionRequestId(json).orElseThrow());
    }

    @Test
    void reason_isLabelledAsInvalidAction() {
        assertEquals("invalid action: missing field: args", BridgeListener.actionReason("missing field: args"));
    }

    @Test
    void reason_isCutToTheProtocolLimit() {
        String reason = BridgeListener.actionReason("x".repeat(1_000));

        assertTrue(reason.codePointCount(0, reason.length()) <= ProtocolRules.MAX_REASON_LENGTH);
    }
}
