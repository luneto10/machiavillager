package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The @m channel: a chat line that starts with "@m", "@machi" or "@machiavillager" (any case, as a whole word) is a
 * private message to him. Pure Java: it only reads the text, so it is safe off the server thread.
 */
public final class PrivateChannel {
    // Whole word, so "@me" or "@mike" stay public. DOTALL keeps a message with a line break in one piece.
    private static final Pattern PREFIX = Pattern.compile(
            "^\\s*@(?:machiavillager|machi|m)(?=\\s|$)\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private PrivateChannel() {}

    /**
     * The message without the prefix, trimmed, when the line is private; empty when the line is public chat. An
     * empty string means the line was private but had nothing after the prefix.
     */
    public static Optional<String> body(String text) {
        Matcher matcher = PREFIX.matcher(Objects.requireNonNull(text, "text"));
        return matcher.matches() ? Optional.of(matcher.group(1).strip()) : Optional.empty();
    }
}
