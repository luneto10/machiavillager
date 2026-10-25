package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Wire limits and identifier rules shared by every bridge record. Mirrors
 * backend/src/machiavillager/domain/protocol.py, the source of truth. Pure Java: no Minecraft imports.
 */
public final class ProtocolRules {
    /** The only protocol version this mod speaks. */
    public static final int PROTOCOL_VERSION = 1;

    /** Mod version string length, in characters. */
    public static final int MAX_MOD_VERSION_LENGTH = 32;
    /** Lines in one speak action. */
    public static final int MAX_SPEAK_LINES = 8;
    /** Characters in one spoken line. */
    public static final int MAX_LINE_LENGTH = 256;
    /** Smallest timeout the backend may ask for. */
    public static final int MIN_TIMEOUT_MS = 100;
    /** Largest timeout the backend may ask for. */
    public static final int MAX_TIMEOUT_MS = 120_000;
    /** Characters allowed in a rejected or failed reason. */
    public static final int MAX_REASON_LENGTH = 200;
    /** Characters allowed in an error detail. */
    public static final int MAX_DETAIL_LENGTH = 500;
    /** Minecraft player name length. */
    public static final int MAX_PLAYER_NAME_LENGTH = 16;
    /** Characters allowed in a player's chat text. */
    public static final int MAX_CHAT_TEXT_LENGTH = 256;

    // Prefixed ids, so a log line shows what kind of id it is. matches() needs the whole string to
    // fit, which is why these patterns carry no ^ and $ anchors.
    /** Action request id, such as act_Ab12Cd34. */
    public static final Pattern ACTION_ID = Pattern.compile("act_[A-Za-z0-9]{8,32}");
    /** Event id, such as evt_Ab12Cd34. */
    public static final Pattern EVENT_ID = Pattern.compile("evt_[A-Za-z0-9]{8,32}");
    /** Conversation id, such as conv_Ab12Cd34. */
    public static final Pattern CONVERSATION_ID = Pattern.compile("conv_[A-Za-z0-9]{8,32}");
    /** Canonical 8-4-4-4-12 UUID only. java.util.UUID also accepts shorter groups, so the codec checks this first. */
    public static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ProtocolRules() {}

    /** Checks an action request id against its prefixed format. */
    public static String requireActionId(String value) {
        return requireId(ACTION_ID, value, "request_id");
    }

    /** Checks an event id against its prefixed format. */
    public static String requireEventId(String value) {
        return requireId(EVENT_ID, value, "event_id");
    }

    /** Checks an optional conversation id. Absent is fine; present must match the format. */
    public static Optional<String> requireConversationId(Optional<String> value) {
        Objects.requireNonNull(value, "conversation_id");
        // Only a present id has a format to check; an absent one is simply not in use yet.
        value.ifPresent(id -> requireId(CONVERSATION_ID, id, "conversation_id"));
        return value;
    }

    /** Checks a string length in code points, the unit JSON Schema and pydantic count in. */
    public static String requireText(String value, int minLength, int maxLength, String field) {
        Objects.requireNonNull(value, field);
        // Counting UTF-16 units would reject emoji that the contract accepts (two units, one character).
        int length = value.codePointCount(0, value.length());
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be between " + minLength + " and " + maxLength + " characters");
        }
        return value;
    }

    /** Checks an integer falls inside an inclusive range. */
    public static int requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static String requireId(Pattern pattern, String value, String field) {
        Objects.requireNonNull(value, field);
        // The message names the field only: ids come from the wire and should not be echoed back.
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has the wrong format");
        }
        return value;
    }
}
