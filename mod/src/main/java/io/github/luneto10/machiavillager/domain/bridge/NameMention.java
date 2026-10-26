package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Whether a chat line calls him by name: "machi" or "machiavillager", in any case, as a whole word. Pure Java.
 */
public final class NameMention {
    private static final String SHORT_NAME = "machi";
    private static final String FULL_NAME = "machiavillager";
    // Whole words only, so "machine" does not count as a mention of "machi".
    private static final Pattern MENTION = Pattern.compile(
            "\\b(?:" + FULL_NAME + "|" + SHORT_NAME + ")\\b", Pattern.CASE_INSENSITIVE);

    private NameMention() {}

    /** True when the text mentions him by either name. Throws on null. */
    public static boolean matches(String text) {
        Objects.requireNonNull(text, "text");
        return MENTION.matcher(text).find();
    }
}
