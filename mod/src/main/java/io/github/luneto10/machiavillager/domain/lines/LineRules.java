package io.github.luneto10.machiavillager.domain.lines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules a line must pass before it may be cached or spoken: its length, its placeholders, and how many lines
 * one pool keeps. Pure Java: no Minecraft imports.
 */
public final class LineRules {
    /** Most lines one pool keeps; a longer batch is cut to this, in order. */
    public static final int MAX_LINES = 30;
    /** Longest line in characters, so one speech bubble stays readable. */
    public static final int MAX_LENGTH = 160;
    /** A batch with fewer valid lines than this is too thin to be worth caching. */
    public static final int MIN_VALID_LINES = 5;

    private static final String PLAYER = "{player}";
    private static final String X = "{x}";
    private static final String Z = "{z}";
    // Only these three placeholders exist; the group is what fill() looks up.
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(player|x|z)\\}");

    private LineRules() {
    }

    /**
     * True when the line is 1 to 160 characters, not blank, and uses only the placeholders its kind allows. A bark
     * may use {player}; a mutter uses none; a hint must have {x} and {z} and may also have {player}.
     */
    public static boolean isValidLine(LineKind kind, String line) {
        Objects.requireNonNull(kind, "kind");
        // Missing, blank and over-long lines are refused before any brace is read.
        if (line == null || line.isBlank() || line.length() > MAX_LENGTH) {
            return false;
        }

        // Only the placeholders this kind owns are removed; a brace left over is a placeholder the kind may not use.
        String rest = switch (kind) {
            case BARK -> line.replace(PLAYER, "");
            case MUTTER -> line;
            case HINT -> line.replace(PLAYER, "").replace(X, "").replace(Z, "");
        };
        if (rest.contains("{") || rest.contains("}")) {
            return false;
        }

        // A hint without both coordinates cannot tell the player where the village is.
        return kind != LineKind.HINT || (line.contains(X) && line.contains(Z));
    }

    /** The valid lines, in their original order, at most {@link #MAX_LINES}. Invalid and null entries are skipped. */
    public static List<String> validLines(LineKind kind, List<String> lines) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lines, "lines");

        List<String> valid = new ArrayList<>();
        for (String line : lines) {
            // Stops at the cap, so the first valid lines of a long batch are the ones kept.
            if (valid.size() == MAX_LINES) {
                break;
            }
            if (isValidLine(kind, line)) {
                valid.add(line);
            }
        }
        return List.copyOf(valid);
    }

    /** Replaces {player}, {x} and {z} in the line. Other text is left as it is. */
    public static String fill(String line, String player, int x, int z) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(player, "player");

        // One pass over the template, so a player name that looks like a placeholder is not filled again.
        Matcher matcher = PLACEHOLDER.matcher(line);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(valueOf(match.group(1), player, x, z)));
    }

    private static String valueOf(String placeholder, String player, int x, int z) {
        return switch (placeholder) {
            case "player" -> player;
            case "x" -> String.valueOf(x);
            default -> String.valueOf(z);
        };
    }
}
