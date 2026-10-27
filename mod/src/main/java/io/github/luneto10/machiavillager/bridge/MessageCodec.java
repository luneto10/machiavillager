package io.github.luneto10.machiavillager.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.bridge.ActionStatus;
import io.github.luneto10.machiavillager.domain.bridge.Addressed;
import io.github.luneto10.machiavillager.domain.bridge.BridgeError;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.DeliverLinesAction;
import io.github.luneto10.machiavillager.domain.bridge.DevModelEvent;
import io.github.luneto10.machiavillager.domain.bridge.DevModelOp;
import io.github.luneto10.machiavillager.domain.bridge.Hello;
import io.github.luneto10.machiavillager.domain.bridge.LinesNeededEvent;
import io.github.luneto10.machiavillager.domain.bridge.NpcSnapshot;
import io.github.luneto10.machiavillager.domain.bridge.PlayerChatEvent;
import io.github.luneto10.machiavillager.domain.bridge.ProtocolRules;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.bridge.SuggestRelationshipAction;
import io.github.luneto10.machiavillager.domain.bridge.Welcome;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * JSON adapter for the bridge protocol. The schema decides whether a message is valid; Gson then maps a valid
 * message onto the domain records. Encoding writes the shapes the schema accepts.
 */
public final class MessageCodec {

    /** Thrown when a wire message breaks a rule. The message names the first broken rule. */
    public static final class DecodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** Creates the exception with a message that names the broken rule. */
        public DecodeException(String message) {
            super(message);
        }
    }

    private static final String VERSION = "v";
    private static final String TYPE = "type";
    private static final String PROTOCOL = "protocol";
    private static final String REQUEST_ID = "request_id";
    private static final String STATUS = "status";
    private static final String REASON = "reason";
    private static final String EVENT_ID = "event_id";
    private static final String CONVERSATION_ID = "conversation_id";
    private static final String IN_REPLY_TO = "in_reply_to";
    private static final String MODEL = "model";
    private static final String MODELS = "models";
    private static final String LATENCY_MS = "latency_ms";

    // Loaded once: the schema is the same for every message. Failing here stops a broken build at startup.
    private static final SchemaValidator SCHEMA = new SchemaValidator();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MessageCodec() {}

    /** Validates one wire message against the schema, then maps it. Throws {@link DecodeException} on a broken rule. */
    public static BridgeMessage decode(String json) {
        List<String> errors = SCHEMA.errors(json);
        if (!errors.isEmpty()) {
            throw new DecodeException(errors.get(0));
        }
        return decodeObject(JsonParser.parseString(json).getAsJsonObject());
    }

    /**
     * The request id of a refused action, read from raw JSON when the full decode failed. Returns the id only when
     * the message is an action with a well-formed id, so the refusal can name it. Never throws.
     */
    public static Optional<String> peekActionRequestId(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            return root.isJsonObject() ? actionRequestId(root.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            // Not JSON at all: nothing to name, so the caller falls back to a bad_message error.
            return Optional.empty();
        }
    }

    /** Writes one message as JSON. Always writes v and type; omits optional fields that are absent. */
    public static String encode(BridgeMessage message) {
        Objects.requireNonNull(message, "message");
        return GSON.toJson(toJson(message));
    }

    private static Optional<String> actionRequestId(JsonObject object) {
        JsonElement type = object.get(TYPE);
        JsonElement requestId = object.get(REQUEST_ID);
        // Missing or non-string fields cannot name an action, so they are not echoed back.
        if (type == null || requestId == null || !isString(type) || !isString(requestId)) {
            return Optional.empty();
        }
        if (!"action".equals(type.getAsString())) {
            return Optional.empty();
        }
        String candidate = requestId.getAsString();
        return ProtocolRules.ACTION_ID.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }

    // Only validated messages reach this point, so every required field is present and well typed.
    private static BridgeMessage decodeObject(JsonObject object) {
        return switch (string(object, TYPE)) {
            case "hello" -> new Hello(string(object, "mod_version"));
            case "welcome" -> new Welcome(welcomeModels(object));
            case "action" -> actionMessage(object);
            case "action_status" -> actionStatus(object);
            case "event" -> event(object);
            case "error" -> error(object);
            default -> throw new DecodeException("unknown message type");
        };
    }

    // Absent means no models: a backend that sends no list still welcomes the mod.
    private static List<String> welcomeModels(JsonObject object) {
        return object.has(MODELS) ? lines(object.getAsJsonArray(MODELS)) : List.of();
    }

    // An action message is one of several kinds; its "action" name decides which record it becomes.
    private static BridgeMessage actionMessage(JsonObject object) {
        return switch (string(object, "action")) {
            case "speak" -> speakAction(object);
            case "suggest_relationship" -> suggestRelationship(object);
            case "deliver_lines" -> deliverLines(object);
            default -> throw new DecodeException("unknown action");
        };
    }

    private static SuggestRelationshipAction suggestRelationship(JsonObject object) {
        JsonObject args = object.getAsJsonObject("args");
        return new SuggestRelationshipAction(
                string(object, REQUEST_ID),
                uuid(args, "player"),
                args.get("affinity_delta").getAsInt(),
                string(args, REASON),
                object.get("timeout_ms").getAsInt(),
                optionalString(object, IN_REPLY_TO));
    }

    // The schema has already checked that lines is an array of strings, so the mapping can trust it.
    private static DeliverLinesAction deliverLines(JsonObject object) {
        JsonObject args = object.getAsJsonObject("args");
        return new DeliverLinesAction(
                string(object, REQUEST_ID),
                string(args, "kind"),
                string(args, "phase"),
                string(args, "mood"),
                lines(args.getAsJsonArray("lines")),
                object.get("timeout_ms").getAsInt());
    }

    private static SpeakAction speakAction(JsonObject object) {
        JsonObject args = object.getAsJsonObject("args");
        return new SpeakAction(
                string(object, REQUEST_ID),
                lines(args.getAsJsonArray("lines")),
                object.get("timeout_ms").getAsInt(),
                optionalString(object, CONVERSATION_ID),
                optionalUuid(args, "to"),
                optionalString(object, IN_REPLY_TO),
                optionalString(object, MODEL),
                optionalInt(object, LATENCY_MS));
    }

    private static ActionStatus actionStatus(JsonObject object) {
        String requestId = string(object, REQUEST_ID);
        return switch (string(object, STATUS)) {
            case "accepted" -> new ActionStatus.Accepted(requestId);
            case "running" -> new ActionStatus.Running(requestId);
            case "succeeded" -> new ActionStatus.Succeeded(requestId);
            case "failed" -> new ActionStatus.Failed(requestId, string(object, REASON));
            case "rejected" -> new ActionStatus.Rejected(requestId, string(object, REASON));
            case "interrupted" -> new ActionStatus.Interrupted(
                    requestId, object.get("spoken").getAsInt(), object.get("of").getAsInt());
            default -> throw new DecodeException("unknown action status");
        };
    }

    // "event" carries several kinds; the event name decides which record the object becomes.
    private static BridgeMessage event(JsonObject object) {
        return switch (string(object, "event")) {
            case "dev_model" -> devModel(object);
            case "lines_needed" -> linesNeeded(object);
            default -> playerChat(object);
        };
    }

    // The mod writes this event; decoding it keeps the contract examples round-tripping.
    private static LinesNeededEvent linesNeeded(JsonObject object) {
        JsonObject data = object.getAsJsonObject("data");
        return new LinesNeededEvent(
                string(object, EVENT_ID),
                OffsetDateTime.parse(string(object, "occurred_at")).toInstant(),
                string(data, "kind"),
                string(data, "phase"),
                string(data, "mood"));
    }

    private static DevModelEvent devModel(JsonObject object) {
        JsonObject data = object.getAsJsonObject("data");
        return new DevModelEvent(
                string(object, EVENT_ID),
                OffsetDateTime.parse(string(object, "occurred_at")).toInstant(),
                DevModelOp.fromWire(string(data, "op")),
                optionalString(data, "model"));
    }

    private static PlayerChatEvent playerChat(JsonObject object) {
        JsonObject data = object.getAsJsonObject("data");
        return new PlayerChatEvent(
                string(object, EVENT_ID),
                OffsetDateTime.parse(string(object, "occurred_at")).toInstant(),
                uuid(data, "player_uuid"),
                string(data, "player_name"),
                string(data, "text"),
                Addressed.fromWire(string(data, "addressed")),
                optionalString(object, CONVERSATION_ID),
                optionalNpc(data));
    }

    // Optional: a player_chat without his state still decodes, with no npc.
    private static Optional<NpcSnapshot> optionalNpc(JsonObject data) {
        if (!data.has("npc")) {
            return Optional.empty();
        }
        JsonObject npc = data.getAsJsonObject("npc");
        // Nearby names are optional in the schema; an absent list means nobody is near him.
        List<String> nearby = npc.has("nearby_players") ? lines(npc.getAsJsonArray("nearby_players")) : List.of();
        return Optional.of(new NpcSnapshot(
                Phase.valueOf(string(npc, "phase").toUpperCase(Locale.ROOT)),
                Mood.valueOf(string(npc, "mood").toUpperCase(Locale.ROOT)),
                npc.get("affinity").getAsInt(),
                npc.get("independence").getAsInt(),
                nearby));
    }

    private static BridgeError error(JsonObject object) {
        return new BridgeError(errorCode(string(object, "code")), string(object, "detail"));
    }

    private static BridgeError.Code errorCode(String wire) {
        for (BridgeError.Code code : BridgeError.Code.values()) {
            if (code.wireName().equals(wire)) {
                return code;
            }
        }
        throw new DecodeException("unknown error code");
    }

    private static JsonObject toJson(BridgeMessage message) {
        return switch (message) {
            case Hello hello -> helloJson(hello);
            case Welcome welcome -> welcomeJson(welcome);
            case SpeakAction speak -> speakJson(speak);
            case SuggestRelationshipAction suggest -> suggestRelationshipJson(suggest);
            case DeliverLinesAction deliver -> deliverLinesJson(deliver);
            case ActionStatus status -> statusJson(status);
            case PlayerChatEvent event -> playerChatJson(event);
            case DevModelEvent event -> devModelJson(event);
            case LinesNeededEvent event -> linesNeededJson(event);
            case BridgeError error -> errorJson(error);
        };
    }

    private static JsonObject helloJson(Hello hello) {
        JsonObject json = envelope("hello");
        json.addProperty("mod_version", hello.modVersion());
        json.addProperty(PROTOCOL, ProtocolRules.PROTOCOL_VERSION);
        return json;
    }

    // The models key is written only when there are models, so a welcome without them keeps its old shape.
    private static JsonObject welcomeJson(Welcome welcome) {
        JsonObject json = envelope("welcome");
        json.addProperty(PROTOCOL, ProtocolRules.PROTOCOL_VERSION);
        if (!welcome.models().isEmpty()) {
            json.add(MODELS, stringArray(welcome.models()));
        }
        return json;
    }

    private static JsonObject speakJson(SpeakAction speak) {
        JsonObject json = envelope("action");
        json.addProperty(REQUEST_ID, speak.requestId());
        json.addProperty("action", "speak");
        JsonArray lines = new JsonArray();
        for (String line : speak.lines()) {
            lines.add(line);
        }
        JsonObject args = new JsonObject();
        args.add("lines", lines);
        // Private speech carries its recipient; normal speech omits the key entirely, never writes null.
        speak.to().ifPresent(player -> args.addProperty("to", player.toString()));
        json.add("args", args);
        json.addProperty("timeout_ms", speak.timeoutMs());
        speak.conversationId().ifPresent(id -> json.addProperty(CONVERSATION_ID, id));
        // Only a reply names the event it answers; an unprompted speech omits the key.
        speak.inReplyTo().ifPresent(id -> json.addProperty(IN_REPLY_TO, id));
        // Dev-only fields: the model that wrote the lines and its latency. Absent ones are left out, never null.
        speak.model().ifPresent(name -> json.addProperty(MODEL, name));
        speak.latencyMs().ifPresent(ms -> json.addProperty(LATENCY_MS, ms));
        return json;
    }

    // The suggestion carries the player and the signed delta; the reason is text the backend wrote, sent as is.
    private static JsonObject suggestRelationshipJson(SuggestRelationshipAction suggest) {
        JsonObject json = envelope("action");
        json.addProperty(REQUEST_ID, suggest.requestId());
        json.addProperty("action", "suggest_relationship");
        JsonObject args = new JsonObject();
        args.addProperty("player", suggest.player().toString());
        args.addProperty("affinity_delta", suggest.affinityDelta());
        args.addProperty(REASON, suggest.reason());
        json.add("args", args);
        json.addProperty("timeout_ms", suggest.timeoutMs());
        // Only a suggestion that answers a chat line names it; an unprompted one omits the key.
        suggest.inReplyTo().ifPresent(id -> json.addProperty(IN_REPLY_TO, id));
        return json;
    }

    // The dev_model event: data.model only for a set, never an explicit null.
    private static JsonObject devModelJson(DevModelEvent event) {
        JsonObject json = envelope("event");
        json.addProperty(EVENT_ID, event.eventId());
        json.addProperty("event", "dev_model");
        json.addProperty("occurred_at", event.occurredAt().toString());
        JsonObject data = new JsonObject();
        data.addProperty("op", event.op().wireName());
        event.model().ifPresent(name -> data.addProperty("model", name));
        json.add("data", data);
        return json;
    }

    // The deliver_lines action: the lines keep their order, and the envelope matches the other actions.
    private static JsonObject deliverLinesJson(DeliverLinesAction deliver) {
        JsonObject json = envelope("action");
        json.addProperty(REQUEST_ID, deliver.requestId());
        json.addProperty("action", "deliver_lines");
        JsonObject args = new JsonObject();
        args.addProperty("kind", deliver.kind());
        args.addProperty("phase", deliver.phase());
        args.addProperty("mood", deliver.mood());
        args.add("lines", stringArray(deliver.lines()));
        json.add("args", args);
        json.addProperty("timeout_ms", deliver.timeoutMs());
        return json;
    }

    // The lines_needed event: kind, phase and mood sit under data, the same shape the dev_model event uses.
    private static JsonObject linesNeededJson(LinesNeededEvent event) {
        JsonObject json = envelope("event");
        json.addProperty(EVENT_ID, event.eventId());
        json.addProperty("event", "lines_needed");
        json.addProperty("occurred_at", event.occurredAt().toString());
        JsonObject data = new JsonObject();
        data.addProperty("kind", event.kind());
        data.addProperty("phase", event.phase());
        data.addProperty("mood", event.mood());
        json.add("data", data);
        return json;
    }

    private static JsonObject statusJson(ActionStatus status) {
        return switch (status) {
            case ActionStatus.Accepted accepted -> statusEnvelope("accepted", accepted.requestId());
            case ActionStatus.Running running -> statusEnvelope("running", running.requestId());
            case ActionStatus.Succeeded succeeded -> statusEnvelope("succeeded", succeeded.requestId());
            case ActionStatus.Failed failed -> withReason("failed", failed.requestId(), failed.reason());
            case ActionStatus.Rejected rejected -> withReason("rejected", rejected.requestId(), rejected.reason());
            case ActionStatus.Interrupted interrupted -> {
                JsonObject json = statusEnvelope("interrupted", interrupted.requestId());
                json.addProperty("spoken", interrupted.spoken());
                json.addProperty("of", interrupted.of());
                yield json;
            }
        };
    }

    private static JsonObject statusEnvelope(String status, String requestId) {
        JsonObject json = envelope("action_status");
        json.addProperty(REQUEST_ID, requestId);
        json.addProperty(STATUS, status);
        return json;
    }

    private static JsonObject withReason(String status, String requestId, String reason) {
        JsonObject json = statusEnvelope(status, requestId);
        json.addProperty(REASON, reason);
        return json;
    }

    private static JsonObject playerChatJson(PlayerChatEvent event) {
        JsonObject json = envelope("event");
        json.addProperty(EVENT_ID, event.eventId());
        json.addProperty("event", "player_chat");
        // Instant.toString is ISO-8601 in UTC, which is the date-time format the contract requires.
        json.addProperty("occurred_at", event.occurredAt().toString());
        JsonObject data = new JsonObject();
        data.addProperty("player_uuid", event.playerUuid().toString());
        data.addProperty("player_name", event.playerName());
        data.addProperty("text", event.text());
        data.addProperty("addressed", event.addressed().wireName());
        // His state is optional: a player_chat sent without it omits the key entirely.
        event.npc().ifPresent(npc -> data.add("npc", npcJson(npc)));
        json.add("data", data);
        event.conversationId().ifPresent(id -> json.addProperty(CONVERSATION_ID, id));
        return json;
    }

    // Phase and mood go out lowercase, the spelling the schema's enums use.
    private static JsonObject npcJson(NpcSnapshot npc) {
        JsonObject json = new JsonObject();
        json.addProperty("phase", npc.phase().name().toLowerCase(Locale.ROOT));
        json.addProperty("mood", npc.mood().name().toLowerCase(Locale.ROOT));
        json.addProperty("affinity", npc.affinity());
        json.addProperty("independence", npc.independence());
        JsonArray names = new JsonArray();
        for (String name : npc.nearbyPlayers()) {
            names.add(name);
        }
        json.add("nearby_players", names);
        return json;
    }

    private static JsonObject errorJson(BridgeError error) {
        JsonObject json = envelope("error");
        json.addProperty("code", error.code().wireName());
        json.addProperty("detail", error.detail());
        return json;
    }

    private static JsonObject envelope(String type) {
        JsonObject json = new JsonObject();
        json.addProperty(VERSION, ProtocolRules.PROTOCOL_VERSION);
        json.addProperty(TYPE, type);
        return json;
    }

    private static String string(JsonObject object, String key) {
        return object.get(key).getAsString();
    }

    // Absent means empty. The schema already refuses an explicit null, so presence is the only check.
    private static Optional<String> optionalString(JsonObject object, String key) {
        return object.has(key) ? Optional.of(string(object, key)) : Optional.empty();
    }

    private static OptionalInt optionalInt(JsonObject object, String key) {
        return object.has(key) ? OptionalInt.of(object.get(key).getAsInt()) : OptionalInt.empty();
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static Optional<UUID> optionalUuid(JsonObject object, String key) {
        return object.has(key) ? Optional.of(UUID.fromString(string(object, key))) : Optional.empty();
    }

    private static UUID uuid(JsonObject object, String key) {
        return UUID.fromString(string(object, key));
    }

    private static List<String> lines(JsonArray array) {
        List<String> lines = new ArrayList<>();
        for (JsonElement element : array) {
            lines.add(element.getAsString());
        }
        return lines;
    }

    private static boolean isString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }
}
