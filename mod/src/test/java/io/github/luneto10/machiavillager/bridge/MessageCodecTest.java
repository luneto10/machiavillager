package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.bridge.ActionStatus;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageCodecTest {
    private static final String SPEAK =
            "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_Ab12Cd34\",\"action\":\"speak\","
                    + "\"args\":{\"lines\":[\"hi\"]},\"timeout_ms\":5000}";

    @Test
    void decode_readsSpeakAction() {
        BridgeMessage message = MessageCodec.decode(SPEAK);

        SpeakAction speak = assertInstanceOf(SpeakAction.class, message);
        assertEquals(List.of("hi"), speak.lines());
        assertEquals(Optional.empty(), speak.conversationId());
    }

    @Test
    void decode_readsInterruptedStatus() {
        String json = "{\"v\":1,\"type\":\"action_status\",\"request_id\":\"act_Ab12Cd34\","
                + "\"status\":\"interrupted\",\"spoken\":1,\"of\":3}";

        assertInstanceOf(ActionStatus.Interrupted.class, MessageCodec.decode(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"v\":true,\"type\":\"welcome\",\"protocol\":1}",
        "{\"v\":1.0,\"type\":\"welcome\",\"protocol\":1}",
        "{\"v\":1,\"type\":\"welcome\",\"protocol\":1,\"extra\":1}",
        "{\"v\":1,\"type\":\"welcome\"}",
        "{\"v\":1,\"type\":\"unknown\"}",
        "[]",
        "not json",
    })
    void decode_rejectsStructuralBreaks(String json) {
        assertThrows(MessageCodec.DecodeException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void decode_rejectsExplicitNullOptionalField() {
        String json = SPEAK.replace("\"timeout_ms\":5000", "\"timeout_ms\":5000,\"conversation_id\":null");

        assertThrows(MessageCodec.DecodeException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void decode_rejectsFractionalTimeout() {
        String json = SPEAK.replace("\"timeout_ms\":5000", "\"timeout_ms\":5000.0");

        assertThrows(MessageCodec.DecodeException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void encode_omitsAbsentConversationId() {
        String json = MessageCodec.encode(MessageCodec.decode(SPEAK));

        assertFalse(json.contains("conversation_id"));
        assertTrue(json.contains("\"v\":1"));
    }
}
