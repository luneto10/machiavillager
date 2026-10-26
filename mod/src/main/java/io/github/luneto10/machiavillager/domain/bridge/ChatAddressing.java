package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether a player's chat line is forwarded to the backend, and why. Pure Java: no Minecraft imports.
 */
public final class ChatAddressing {
    /** Environment variable that limits forwarding to lines that use his name. Unset means off. */
    public static final String REQUIRE_NAME_VARIABLE = "MACHIAVILLAGER_REQUIRE_NAME";

    private ChatAddressing() {}

    /**
     * The reason to forward this line, or empty when it must not be forwarded. A line that names him is always
     * forwarded; any other line is forwarded as nearby speech unless names are required.
     */
    public static Optional<Addressed> decide(String text, boolean requireName) {
        Objects.requireNonNull(text, "text");
        if (NameMention.matches(text)) {
            return Optional.of(Addressed.NAME);
        }

        // Without a name, nearby speech still reaches him unless the server asked for names only.
        if (!requireName) {
            return Optional.of(Addressed.NEAR);
        }
        return Optional.empty();
    }

    /** True only when the setting is "true" in any case. Missing or other values keep names optional. */
    public static boolean requireName(Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        String raw = env.get(REQUIRE_NAME_VARIABLE);
        // Only an explicit "true" turns the stricter mode on; anything else keeps the default.
        return raw != null && "true".equalsIgnoreCase(raw.strip());
    }
}
