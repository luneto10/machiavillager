package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.bridge.ActionStatus;
import io.github.luneto10.machiavillager.domain.bridge.Addressed;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.DeliverLinesAction;
import io.github.luneto10.machiavillager.domain.bridge.DevModelEvent;
import io.github.luneto10.machiavillager.domain.bridge.DevModelOp;
import io.github.luneto10.machiavillager.domain.bridge.LinesNeededEvent;
import io.github.luneto10.machiavillager.domain.bridge.NpcSnapshot;
import io.github.luneto10.machiavillager.domain.bridge.Welcome;
import io.github.luneto10.machiavillager.domain.bridge.PlayerChatEvent;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.bridge.SuggestRelationshipAction;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

    private static PlayerChatEvent chatEvent(Optional<NpcSnapshot> npc) {
        return new PlayerChatEvent(EVENT, Instant.parse("2026-10-18T12:00:00Z"), PLAYER, "Luciano", "hi",
                Addressed.NAME, Optional.empty(), npc);
    }

    private static NpcSnapshot snapshot() {
        return new NpcSnapshot(Phase.SEEDS, Mood.BORED, 37, 63, List.of("Luciano", "Ana"));
    }

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
    void encode_writesNpcSnapshotWithLowercaseEnums() {
        String json = MessageCodec.encode(chatEvent(Optional.of(snapshot())));

        assertTrue(json.contains("\"npc\":{\"phase\":\"seeds\",\"mood\":\"bored\",\"affinity\":37,"
                + "\"independence\":63,\"nearby_players\":[\"Luciano\",\"Ana\"]}"));
    }

    @Test
    void encode_omitsNpcWhenAbsent() {
        assertFalse(MessageCodec.encode(chatEvent(Optional.empty())).contains("\"npc\""));
    }

    @Test
    void decode_readsNpcSnapshotBackFromEncodedEvent() {
        PlayerChatEvent event = (PlayerChatEvent) MessageCodec.decode(
                MessageCodec.encode(chatEvent(Optional.of(snapshot()))));

        assertEquals(Optional.of(snapshot()), event.npc());
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
                Optional.empty(), Optional.of(PLAYER), Optional.empty(), Optional.empty(), OptionalInt.empty());

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
    void decode_readsModelAndLatencyOnSpeak() {
        String json = SPEAK.replace("\"timeout_ms\":5000}",
                "\"timeout_ms\":5000,\"model\":\"groq:qwen/qwen3.8-27b\",\"latency_ms\":480}");

        SpeakAction speak = (SpeakAction) MessageCodec.decode(json);

        assertEquals(Optional.of("groq:qwen/qwen3.8-27b"), speak.model());
        assertEquals(OptionalInt.of(480), speak.latencyMs());
    }

    @Test
    void encode_writesModelAndLatencyOnlyWhenPresent() {
        String json = MessageCodec.encode(MessageCodec.decode(SPEAK));

        assertFalse(json.contains("\"model\""));
        assertFalse(json.contains("latency_ms"));
    }

    @Test
    void decode_readsModelsFromWelcome() {
        Welcome welcome = (Welcome) MessageCodec.decode(
                "{\"v\":1,\"type\":\"welcome\",\"protocol\":1,\"models\":[\"groq:openai/gpt-oss-120b\"]}");

        assertEquals(List.of("groq:openai/gpt-oss-120b"), welcome.models());
    }

    @Test
    void decode_welcomeWithoutModelsHasNone() {
        Welcome welcome = (Welcome) MessageCodec.decode("{\"v\":1,\"type\":\"welcome\",\"protocol\":1}");

        assertEquals(List.of(), welcome.models());
    }

    @Test
    void encode_writesDevModelSetWithModelOnly() {
        DevModelEvent set = new DevModelEvent("evt_0123456789abcdef", Instant.parse("2026-10-18T12:00:00Z"),
                DevModelOp.SET, Optional.of("groq:openai/gpt-oss-120b"));

        String json = MessageCodec.encode(set);

        assertTrue(json.contains("\"event\":\"dev_model\""));
        assertTrue(json.contains("\"data\":{\"op\":\"set\",\"model\":\"groq:openai/gpt-oss-120b\"}"));
    }

    @Test
    void encode_writesDevModelResetWithoutModelKey() {
        DevModelEvent reset = new DevModelEvent("evt_0123456789abcdef", Instant.parse("2026-10-18T12:00:05Z"),
                DevModelOp.RESET, Optional.empty());

        String json = MessageCodec.encode(reset);

        assertTrue(json.contains("\"data\":{\"op\":\"reset\"}"));
    }

    @Test
    void decode_readsDevModelEvent() {
        BridgeMessage message = MessageCodec.decode(MessageCodec.encode(new DevModelEvent(
                "evt_0123456789abcdef", Instant.parse("2026-10-18T12:00:00Z"), DevModelOp.RESET, Optional.empty())));

        assertEquals(DevModelOp.RESET, ((DevModelEvent) message).op());
    }

    // The deliver_lines and lines_needed examples as the shared folder writes them.
    private static final String DELIVER_LINES =
            "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_01HZX8K2M9\",\"action\":\"deliver_lines\","
                    + "\"args\":{\"kind\":\"hint\",\"phase\":\"seeds\",\"mood\":\"bored\",\"lines\":"
                    + "[\"{player}, the seeds at {x}, {z} need water.\",\"Check the field at {x}, {z} before dusk.\","
                    + "\"{player}, you are late.\"]},\"timeout_ms\":30000}";
    private static final String LINES_NEEDED =
            "{\"v\":1,\"type\":\"event\",\"event_id\":\"evt_01HZX8K2M9\",\"event\":\"lines_needed\","
                    + "\"occurred_at\":\"2026-10-18T12:00:00Z\",\"data\":{\"kind\":\"hint\",\"phase\":\"seeds\","
                    + "\"mood\":\"bored\"}}";

    @Test
    void decode_readsDeliverLinesExample() {
        DeliverLinesAction deliver = (DeliverLinesAction) MessageCodec.decode(DELIVER_LINES);

        assertEquals("hint", deliver.kind());
        assertEquals(3, deliver.lines().size());
        assertEquals(30000, deliver.timeoutMs());
    }

    @Test
    void decode_rejectsDeliverLinesWithoutLines() {
        String json = "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_01HZX8K2M9\",\"action\":\"deliver_lines\","
                + "\"args\":{\"kind\":\"hint\",\"phase\":\"seeds\",\"mood\":\"bored\"},\"timeout_ms\":30000}";

        assertThrows(MessageCodec.DecodeException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void decode_rejectsDeliverLinesWhenLinesIsNotAnArray() {
        String json = "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_01HZX8K2M9\",\"action\":\"deliver_lines\","
                + "\"args\":{\"kind\":\"hint\",\"phase\":\"seeds\",\"mood\":\"bored\",\"lines\":\"hi\"},"
                + "\"timeout_ms\":30000}";

        assertThrows(MessageCodec.DecodeException.class, () -> MessageCodec.decode(json));
    }

    @Test
    void encode_roundTripsDeliverLinesExample() {
        BridgeMessage decoded = MessageCodec.decode(DELIVER_LINES);

        assertEquals(JsonParser.parseString(DELIVER_LINES), JsonParser.parseString(MessageCodec.encode(decoded)));
    }

    @Test
    void decode_readsLinesNeededEvent() {
        LinesNeededEvent event = (LinesNeededEvent) MessageCodec.decode(LINES_NEEDED);

        assertEquals("hint", event.kind());
        assertEquals("bored", event.mood());
    }

    @Test
    void encode_writesLinesNeededInTheExampleShape() {
        LinesNeededEvent event = new LinesNeededEvent("evt_01HZX8K2M9", Instant.parse("2026-10-18T12:00:00Z"),
                "hint", "seeds", "bored");

        assertEquals(JsonParser.parseString(LINES_NEEDED), JsonParser.parseString(MessageCodec.encode(event)));
    }

    // Both suggestion examples as the backend writes them: a loss of affinity, then a gain.
    private static final String SUGGEST_DOWN =
            "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_01HZX8K2M9\",\"action\":\"suggest_relationship\","
                    + "\"args\":{\"player\":\"7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69\",\"affinity_delta\":-6,"
                    + "\"reason\":\"threatened him for real\"},\"timeout_ms\":30000,\"in_reply_to\":\"evt_01HZX8K2M9\"}";
    private static final String SUGGEST_UP =
            "{\"v\":1,\"type\":\"action\",\"request_id\":\"act_01HZX8K2M9\",\"action\":\"suggest_relationship\","
                    + "\"args\":{\"player\":\"7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69\",\"affinity_delta\":4,"
                    + "\"reason\":\"brought him the iron he asked for\"},\"timeout_ms\":30000,"
                    + "\"in_reply_to\":\"evt_01HZX8K2M9\"}";

    @Test
    void decode_readsSuggestRelationshipDown() {
        SuggestRelationshipAction suggest = (SuggestRelationshipAction) MessageCodec.decode(SUGGEST_DOWN);

        assertEquals(PLAYER, suggest.player());
        assertEquals(-6, suggest.affinityDelta());
        assertEquals("threatened him for real", suggest.reason());
        assertEquals(Optional.of(EVENT), suggest.inReplyTo());
    }

    @Test
    void encode_roundTripsSuggestRelationshipUp() {
        BridgeMessage decoded = MessageCodec.decode(SUGGEST_UP);

        String encoded = MessageCodec.encode(decoded);

        assertTrue(encoded.contains("\"action\":\"suggest_relationship\""));
        assertEquals(decoded, MessageCodec.decode(encoded));
    }

    @Test
    void encode_writesInReplyToOnlyForAReply() {
        BridgeMessage reply = new SpeakAction("act_Ab12Cd34", List.of("hi"), 5000,
                Optional.empty(), Optional.empty(), Optional.of(EVENT), Optional.empty(), OptionalInt.empty());

        assertTrue(MessageCodec.encode(reply).contains("\"in_reply_to\":\"" + EVENT + "\""));
    }
}
