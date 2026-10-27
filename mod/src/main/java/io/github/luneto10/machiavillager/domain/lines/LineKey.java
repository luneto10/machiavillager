package io.github.luneto10.machiavillager.domain.lines;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The key of one pool of lines: which kind of line, in which story phase, in which mood. Its id form, such as
 * "hint:seeds:bored", is the name a saved pool is stored under.
 */
public record LineKey(LineKind kind, Phase phase, Mood mood) {

    private static final String SEPARATOR = ":";
    private static final int PARTS = 3;

    /** All three parts are required: a key without one of them could not name a pool. */
    public LineKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(mood, "mood");
    }

    /** The saved form: kind, phase and mood in lowercase, joined by colons. */
    public String id() {
        return kind.wire() + SEPARATOR + lower(phase.name()) + SEPARATOR + lower(mood.name());
    }

    /** The key whose id matches, or empty when any part is unknown or the shape is wrong. */
    public static Optional<LineKey> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }

        // A limit of -1 keeps trailing empty parts, so "hint:seeds:" is refused instead of silently accepted.
        String[] parts = id.split(SEPARATOR, -1);
        if (parts.length != PARTS) {
            return Optional.empty();
        }

        Optional<LineKind> kind = LineKind.fromWire(parts[0]);
        Optional<Phase> phase = byLowerName(Phase.values(), parts[1]);
        Optional<Mood> mood = byLowerName(Mood.values(), parts[2]);
        if (kind.isEmpty() || phase.isEmpty() || mood.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LineKey(kind.get(), phase.get(), mood.get()));
    }

    private static <E extends Enum<E>> Optional<E> byLowerName(E[] constants, String wanted) {
        return Arrays.stream(constants)
                .filter(constant -> lower(constant.name()).equals(wanted))
                .findFirst();
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
