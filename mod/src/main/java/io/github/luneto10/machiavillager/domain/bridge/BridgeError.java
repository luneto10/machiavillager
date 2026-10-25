package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Locale;
import java.util.Objects;

/**
 * Either side reports a message it cannot accept. Branch on the code, never on the detail text.
 *
 * @param code the closed set of error codes
 * @param detail a human-readable note
 */
public record BridgeError(Code code, String detail) implements BridgeMessage {

    /** Null checks only; the detail length is enforced by the schema. */
    public BridgeError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }

    /** The closed set of error codes allowed on the wire. */
    public enum Code {
        BAD_MESSAGE,
        UNSUPPORTED_PROTOCOL,
        UNAUTHORIZED,
        INTERNAL;

        /** The lowercase name used on the wire, such as bad_message. */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
