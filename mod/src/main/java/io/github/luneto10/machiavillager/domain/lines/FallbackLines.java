package io.github.luneto10.machiavillager.domain.lines;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Hand-written lines for when the backend is down and a pool is still empty. They use the same placeholders as
 * {@link LineRules}: a bark uses only {player}, a mutter none, a hint {player}, {x} and {z}. Pure Java.
 */
public final class FallbackLines {
    private static final Map<Phase, List<String>> BARKS = Map.of(
            Phase.PARTNER, List.of(
                    "{player}, watch the lava down there.",
                    "Good digging today, {player}. Bread is on me.",
                    "{player}, this ore is richer than it looks."),
            Phase.SEEDS, List.of(
                    "{player}, mind the gap by the old mineshaft.",
                    "Quiet night, {player}. Good for mining.",
                    "{player}, I found coal. Want half?"),
            Phase.PROPOSAL, List.of(
                    "{player}, this village would run better with one voice. Mine.",
                    "Between us, {player}: they listen to me now."),
            Phase.REGIME, List.of(
                    "{player}, they do as the bell says. Good.",
                    "The walls are quiet, {player}. Quiet is what I wanted."),
            Phase.WAR, List.of(
                    "{player}, the traitors are still out there. Keep your sword close.",
                    "The night is loud, {player}. Let them come."));

    private static final Map<Phase, List<String>> MUTTERS = Map.of(
            Phase.PARTNER, List.of(
                    "Good stone under this hill.",
                    "Wheat should be ready soon, I think."),
            Phase.SEEDS, List.of(
                    "A crown would suit me. Emerald, not gold.",
                    "Leaders are just people who decided first."),
            Phase.PROPOSAL, List.of(
                    "They'll say yes. They always say yes.",
                    "A title for each of them. A leash for each title."),
            Phase.REGIME, List.of(
                    "Loyalty is a habit. Habits can be trained.",
                    "The bell rings when I say it rings."),
            Phase.WAR, List.of(
                    "Traitors whisper too. Louder.",
                    "If the walls fall, I'll build taller walls."));

    // The partner has no village to name yet, so hints start once he has a reason to talk about one.
    private static final List<String> HINTS = List.of(
            "{player}... have you seen the village near {x}, {z}? So disorganized. Someone should take care of it.",
            "Between us, {player}: the village at {x}, {z} has too many emeralds and too few leaders.",
            "{player}, I walked past {x}, {z}. Lovely village. Wasted on its elder.");

    private FallbackLines() {
    }

    /** A random fallback line for the kind and phase, or empty when none exists (a hint in the partner phase). */
    public static Optional<String> pick(LineKind kind, Phase phase, RandomGenerator random) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(random, "random");

        List<String> lines = linesFor(kind, phase);
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(lines.get(random.nextInt(lines.size())));
    }

    private static List<String> linesFor(LineKind kind, Phase phase) {
        return switch (kind) {
            case BARK -> BARKS.get(phase);
            case MUTTER -> MUTTERS.get(phase);
            case HINT -> phase == Phase.PARTNER ? List.of() : HINTS;
        };
    }
}
