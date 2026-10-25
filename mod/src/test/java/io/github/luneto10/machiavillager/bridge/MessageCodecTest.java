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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageCodecTest {
    private static final String SPEAK =
            "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_Ab12Cd34\",\"action\":\"speak\","
                    + "\"args\":{\"lines\":[\"hi\"]},\"timeout_ms\":5000}";
    private static final UUID PLAYER = UUID.fromString("7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69");
    private static final String EVENT = "evt_01HZX8K2M9";

    @Test
    void decode_readsSpeakAction() {
        BridgeMessage message = MessageCodec.decode(SPEAK);

        SpeakAction speak = assertInstanceOf(SpeakAction.class, message);
        assertEquals(List.of("hi"), speak.lines());
        assertEquals(Optional.empty(), speak.conversationId());
        assertEquals(Optional.empty(), speak.to());
    }

    @Test
    void decode_readsPrivateSpeakAction() {
        String json = SPEAK.replace("\"args\":{", "\"args\":{\"to\":\"" + PLAYER + "\",");

        assertEquals(Optional.of(PLAYER), ((SpeakAction) MessageCodec.decode(json)).to());
    }

    @Test
    void decode_readsInterruptedStatus() {
        String json = "{\"v\":1,\"type\":\"action_status\",\"request_id\":\"act_Ab12Cd34\","
                + "\"status\":\"interrupted\",\"spoken\":1,\"of\":3}";

        assertInstanceOf(ActionStatus.Interrupted.class, MessageCodec.decode(json));
    }

    // Broken messages are covered by the generated examples; these two cover the parse step itself.
    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]"})
    void decode_rejectsTextThatIsNotAnObject(String json) {
        assertThrows(MessageCodec.DecodeException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void encode_omitsAbsentConversationIdAndRecipient() {
        String json = MessageCodec.encode(MessageCodec.decode(SPEAK));

        assertFalse(json.contains("conversation_id"));
        assertFalse(json.contains("\"to\""));
        assertTrue(json.contains("\"v\":1"));
    }

    @Test
    void encode_writesRecipientOnlyForPrivateSpeech() {
        BridgeMessage speak = new SpeakAction("act_Ab12Cd34", List.of("hi"), 5000,
                Optional.empty(), Optional.of(PLAYER), Optional.empty());

        assertTrue(MessageCodec.encode(speak).contains("\"to\":\"" + PLAYER + "\""));
    }

    @Test
    void decode_readsReplyToAnEvent() {
        String json = SPEAK.replace("\"timeout_ms\":5000}", "\"timeout_ms\":5000,\"in_reply_to\":\"" + EVENT + "\"}");

        assertEquals(Optional.of(EVENT), ((SpeakAction) MessageCodec.decode(json)).inReplyTo());
    }

    @Test
    void encode_omitsInReplyToForUnpromptedSpeech() {
        String json = MessageCodec.encode(MessageCodec.decode(SPEAK));

        assertFalse(json.contains("in_reply_to"));
    }

    @Test
    void encode_writesInReplyToOnlyForAReply() {
        BridgeMessage reply = new SpeakAction("act_Ab12Cd34", List.of("hi"), 5000,
                Optional.empty(), Optional.empty(), Optional.of(EVENT));

        assertTrue(MessageCodec.encode(reply).contains("\"in_reply_to\":\"" + EVENT + "\""));
    }
}
