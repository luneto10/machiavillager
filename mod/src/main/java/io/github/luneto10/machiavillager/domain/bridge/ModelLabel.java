package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The action bar line for the model that answered, for dev. "groq:qwen/qwen3.8-27b" becomes
 * "AI: groq · qwen3.8-27b · 480 ms". A model with no colon is shown as it came.
 */
public final class ModelLabel {
    private static final String PREFIX = "AI: ";
    private static final String SEPARATOR = " · ";
    private static final String MS_SUFFIX = " ms";
    private static final char PROVIDER_SEPARATOR = ':';
    private static final char NAME_SEPARATOR = '/';

    /** Shown on the dev boss bar before any reply has arrived. */
    public static final String NO_REPLY_YET = "AI: no reply yet";
    /** Shown when the last reply was a canned line, which no model wrote. */
    public static final String CANNED_LINE = "AI: canned line";

    private ModelLabel() {}

    /** The full action bar text: the provider, the model name, and the latency when the backend sent one. */
    public static String format(String model, OptionalInt latencyMs) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(latencyMs, "latencyMs");
        String label = PREFIX + shortName(model);
        return latencyMs.isPresent() ? label + SEPARATOR + latencyMs.getAsInt() + MS_SUFFIX : label;
    }

    /** The label for a reply: the model when one wrote it, otherwise the canned-line text. */
    public static String forReply(Optional<String> model, OptionalInt latencyMs) {
        Objects.requireNonNull(model, "model");
        // A canned line has no model, so there is no provider or latency to show.
        return model.isPresent() ? format(model.get(), latencyMs) : CANNED_LINE;
    }

    // Provider first, then the model name after the last slash. A value with no colon is returned as it is.
    private static String shortName(String model) {
        int colon = model.indexOf(PROVIDER_SEPARATOR);
        if (colon < 0) {
            return model;
        }
        String provider = model.substring(0, colon);
        String rest = model.substring(colon + 1);
        String name = rest.substring(rest.lastIndexOf(NAME_SEPARATOR) + 1);
        return provider + SEPARATOR + name;
    }
}
