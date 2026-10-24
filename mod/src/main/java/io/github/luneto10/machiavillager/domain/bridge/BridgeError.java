package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Locale;
import java.util.Objects;

/**
 * Either side reports a message it cannot accept. Branch on the code, never on the detail text.
 *
 * @param code the closed set of error codes
 * @param detail a human-readable note, up to 500 characters
 */
public record BridgeError(Code code, String detail) implements BridgeMessage {

    /** Validates the code is present and the detail length is within the limit. */
    public BridgeError {
        Objects.requireNonNull(code, "code");
        detail = ProtocolRules.requireText(detail, 0, ProtocolRules.MAX_DETAIL_LENGTH, "detail");
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
