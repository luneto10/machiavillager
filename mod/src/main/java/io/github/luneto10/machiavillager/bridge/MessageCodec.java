package io.github.luneto10.machiavillager.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.luneto10.machiavillager.domain.bridge.ActionStatus;
import io.github.luneto10.machiavillager.domain.bridge.BridgeError;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.Hello;
import io.github.luneto10.machiavillager.domain.bridge.PlayerChatEvent;
import io.github.luneto10.machiavillager.domain.bridge.ProtocolRules;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.bridge.Welcome;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * JSON adapter for the bridge protocol. Gson parses and writes; the domain records enforce the limits.
 * Decoding refuses what the contract refuses: unknown or missing fields, a boolean or fraction where a
 * number belongs, explicit nulls, and unknown type, action, status, event or code values.
 */
public final class MessageCodec {

    /** Thrown when a wire message breaks a structural or business rule. */
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
    private static final String CONVERSATION_ID = "conversation_id";

    // The exact key set per message. Any key outside it is a rule violation (additionalProperties false).
    private static final Set<String> HELLO_KEYS = Set.of(VERSION, TYPE, "mod_version", PROTOCOL);
    private static final Set<String> WELCOME_KEYS = Set.of(VERSION, TYPE, PROTOCOL);
    private static final Set<String> SPEAK_KEYS =
            Set.of(VERSION, TYPE, REQUEST_ID, "action", "args", "timeout_ms", CONVERSATION_ID);
    private static final Set<String> SPEAK_ARGS_KEYS = Set.of("lines");
    private static final Set<String> STATUS_KEYS = Set.of(VERSION, TYPE, REQUEST_ID, STATUS);
    private static final Set<String> REASON_KEYS = Set.of(VERSION, TYPE, REQUEST_ID, STATUS, "reason");
    private static final Set<String> INTERRUPTED_KEYS =
            Set.of(VERSION, TYPE, REQUEST_ID, STATUS, "spoken", "of");
    private static final Set<String> EVENT_KEYS =
            Set.of(VERSION, TYPE, "event_id", "event", "occurred_at", "data", CONVERSATION_ID);
    private static final Set<String> PLAYER_CHAT_DATA_KEYS = Set.of("player_uuid", "player_name", "text");
    private static final Set<String> ERROR_KEYS = Set.of(VERSION, TYPE, "code", "detail");

    // Integers are written as plain digits. A fraction or an exponent is refused, as pydantic refuses it.
    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d{1,10}");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MessageCodec() {}

    /** Parses and validates one wire message. Throws {@link DecodeException} when a rule breaks. */
    public static BridgeMessage decode(String json) {
        JsonObject object = parseObject(json);
        // Records throw IllegalArgumentException for business rules; surface them as one error type.
        try {
            return decodeObject(object);
        } catch (IllegalArgumentException e) {
            throw new DecodeException(e.getMessage());
        }
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

    /** Writes one message as JSON. Always writes v and type; omits optional fields that are absent. */
    public static String encode(BridgeMessage message) {
        Objects.requireNonNull(message, "message");
        return GSON.toJson(toJson(message));
    }

    private static JsonObject parseObject(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new DecodeException("message is not valid JSON");
        }
        if (!root.isJsonObject()) {
            throw new DecodeException("message must be a JSON object");
        }
        return root.getAsJsonObject();
    }

    private static BridgeMessage decodeObject(JsonObject object) {
        // Checked before the type: a message from another protocol version is refused whatever its type.
        wireVersion(object, VERSION);
        String type = string(object, TYPE);
        return switch (type) {
            case "hello" -> hello(object);
            case "welcome" -> welcome(object);
            case "action" -> speakAction(object);
            case "action_status" -> actionStatus(object);
            case "event" -> playerChat(object);
            case "error" -> error(object);
            default -> throw new DecodeException("unknown message type");
        };
    }

    private static Hello hello(JsonObject object) {
        requireKeys(object, HELLO_KEYS);
        wireVersion(object, PROTOCOL);
        return new Hello(string(object, "mod_version"));
    }

    private static Welcome welcome(JsonObject object) {
        requireKeys(object, WELCOME_KEYS);
        wireVersion(object, PROTOCOL);
        return new Welcome();
    }

    private static SpeakAction speakAction(JsonObject object) {
        requireKeys(object, SPEAK_KEYS);
        // speak is the only action in this checkpoint; any other name is refused before its args are read.
        if (!"speak".equals(string(object, "action"))) {
            throw new DecodeException("unknown action");
        }
        JsonObject args = object(object, "args");
        requireKeys(args, SPEAK_ARGS_KEYS);
        return new SpeakAction(
                string(object, REQUEST_ID),
                lines(array(args, "lines")),
                integer(object, "timeout_ms"),
                optionalString(object, CONVERSATION_ID));
    }

    private static ActionStatus actionStatus(JsonObject object) {
        return switch (string(object, STATUS)) {
            case "accepted" -> accepted(object);
            case "running" -> running(object);
            case "succeeded" -> succeeded(object);
            case "failed" -> failed(object);
            case "rejected" -> rejected(object);
            case "interrupted" -> interrupted(object);
            default -> throw new DecodeException("unknown action status");
        };
    }

    private static ActionStatus.Accepted accepted(JsonObject object) {
        requireKeys(object, STATUS_KEYS);
        return new ActionStatus.Accepted(string(object, REQUEST_ID));
    }

    private static ActionStatus.Running running(JsonObject object) {
        requireKeys(object, STATUS_KEYS);
        return new ActionStatus.Running(string(object, REQUEST_ID));
    }

    private static ActionStatus.Succeeded succeeded(JsonObject object) {
        requireKeys(object, STATUS_KEYS);
        return new ActionStatus.Succeeded(string(object, REQUEST_ID));
    }

    private static ActionStatus.Failed failed(JsonObject object) {
        requireKeys(object, REASON_KEYS);
        return new ActionStatus.Failed(string(object, REQUEST_ID), string(object, "reason"));
    }

    private static ActionStatus.Rejected rejected(JsonObject object) {
        requireKeys(object, REASON_KEYS);
        return new ActionStatus.Rejected(string(object, REQUEST_ID), string(object, "reason"));
    }

    private static ActionStatus.Interrupted interrupted(JsonObject object) {
        requireKeys(object, INTERRUPTED_KEYS);
        return new ActionStatus.Interrupted(
                string(object, REQUEST_ID), integer(object, "spoken"), integer(object, "of"));
    }

    private static PlayerChatEvent playerChat(JsonObject object) {
        requireKeys(object, EVENT_KEYS);
        if (!"player_chat".equals(string(object, "event"))) {
            throw new DecodeException("unknown event");
        }
        JsonObject data = object(object, "data");
        requireKeys(data, PLAYER_CHAT_DATA_KEYS);
        return new PlayerChatEvent(
                string(object, "event_id"),
                instant(object, "occurred_at"),
                uuid(data, "player_uuid"),
                string(data, "player_name"),
                string(data, "text"),
                optionalString(object, CONVERSATION_ID));
    }

    private static BridgeError error(JsonObject object) {
        requireKeys(object, ERROR_KEYS);
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
            case Welcome welcome -> welcomeJson();
            case SpeakAction speak -> speakJson(speak);
            case ActionStatus status -> statusJson(status);
            case PlayerChatEvent event -> playerChatJson(event);
            case BridgeError error -> errorJson(error);
        };
    }

    private static JsonObject helloJson(Hello hello) {
        JsonObject json = envelope("hello");
        json.addProperty("mod_version", hello.modVersion());
        json.addProperty(PROTOCOL, ProtocolRules.PROTOCOL_VERSION);
        return json;
    }

    private static JsonObject welcomeJson() {
        JsonObject json = envelope("welcome");
        json.addProperty(PROTOCOL, ProtocolRules.PROTOCOL_VERSION);
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
        json.add("args", args);
        json.addProperty("timeout_ms", speak.timeoutMs());
        speak.conversationId().ifPresent(id -> json.addProperty(CONVERSATION_ID, id));
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
        json.addProperty("reason", reason);
        return json;
    }

    private static JsonObject playerChatJson(PlayerChatEvent event) {
        JsonObject json = envelope("event");
        json.addProperty("event_id", event.eventId());
        json.addProperty("event", "player_chat");
        // Instant.toString is ISO-8601 in UTC, which is the date-time format the contract requires.
        json.addProperty("occurred_at", event.occurredAt().toString());
        JsonObject data = new JsonObject();
        data.addProperty("player_uuid", event.playerUuid().toString());
        data.addProperty("player_name", event.playerName());
        data.addProperty("text", event.text());
        json.add("data", data);
        event.conversationId().ifPresent(id -> json.addProperty(CONVERSATION_ID, id));
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

    private static void requireKeys(JsonObject object, Set<String> allowed) {
        // Anything the message type does not define is refused, never silently ignored.
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new DecodeException("unexpected field: " + key);
            }
        }
    }

    private static void wireVersion(JsonObject object, String key) {
        if (integer(object, key) != ProtocolRules.PROTOCOL_VERSION) {
            throw new DecodeException(key + " must be " + ProtocolRules.PROTOCOL_VERSION);
        }
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            throw new DecodeException("missing field: " + key);
        }
        return element;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = required(object, key);
        // An explicit null is a JsonNull, not a string, so it is refused here too.
        if (!isString(element)) {
            throw new DecodeException(key + " must be a non-null string");
        }
        return element.getAsString();
    }

    private static Optional<String> optionalString(JsonObject object, String key) {
        if (!object.has(key)) {
            return Optional.empty();
        }
        return Optional.of(string(object, key));
    }

    private static int integer(JsonObject object, String key) {
        JsonElement element = required(object, key);
        // A boolean is a JSON primitive but not a number, so isNumber() refuses true and false.
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DecodeException(key + " must be an integer");
        }
        String literal = element.getAsString();
        if (!INTEGER_LITERAL.matcher(literal).matches()) {
            throw new DecodeException(key + " must be a whole number");
        }
        // At most ten digits, so parseLong cannot overflow; the int range is checked on top.
        long value = Long.parseLong(literal);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new DecodeException(key + " is out of range");
        }
        return (int) value;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = required(object, key);
        if (!element.isJsonObject()) {
            throw new DecodeException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = required(object, key);
        if (!element.isJsonArray()) {
            throw new DecodeException(key + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static List<String> lines(JsonArray array) {
        List<String> lines = new ArrayList<>();
        for (JsonElement element : array) {
            if (!isString(element)) {
                throw new DecodeException("lines must be strings");
            }
            lines.add(element.getAsString());
        }
        return lines;
    }

    private static Instant instant(JsonObject object, String key) {
        String text = string(object, key);
        try {
            // ISO-8601 with an explicit offset, which is the date-time form the contract requires.
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            throw new DecodeException(key + " must be an ISO-8601 date-time with an offset");
        }
    }

    private static UUID uuid(JsonObject object, String key) {
        String text = string(object, key);
        // Checked before UUID.fromString, which would also accept shorter forms the wire forbids.
        if (!ProtocolRules.CANONICAL_UUID.matcher(text).matches()) {
            throw new DecodeException(key + " must be a canonical UUID");
        }
        return UUID.fromString(text);
    }

    private static boolean isString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }
}
