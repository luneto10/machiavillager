package io.github.luneto10.machiavillager.domain.lines;

import java.util.Locale;
import java.util.Optional;

/** The three kinds of spoken line: an idle bark, a hint about a village, and a mutter to himself. */
public enum LineKind {
    BARK,
    HINT,
    MUTTER;

    /** The lowercase name used on the wire and in saved ids, such as "bark". */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The kind whose wire name matches exactly, or empty for an unknown or missing name. */
    public static Optional<LineKind> fromWire(String wire) {
        // A missing name is unknown, not an error: the backend may send anything.
        if (wire == null) {
            return Optional.empty();
        }

        for (LineKind kind : values()) {
            if (kind.wire().equals(wire)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
