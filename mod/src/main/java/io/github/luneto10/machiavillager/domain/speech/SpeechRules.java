package io.github.luneto10.machiavillager.domain.speech;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Rules that limit how often he speaks. Pure Java: no Minecraft imports. */
public final class SpeechRules {
    /** Environment variable that overrides the unprompted cooldown, in whole seconds. */
    public static final String COOLDOWN_VARIABLE = "MACHIAVILLAGER_SPEECH_COOLDOWN_SECONDS";

    /** Default wait between unprompted speeches: 3 minutes (ADR 0004, decision 9). */
    public static final Duration UNPROMPTED_COOLDOWN_DEFAULT = Duration.ofSeconds(180);

    /** Game ticks per second, the unit the cooldown is counted in. */
    public static final long TICKS_PER_SECOND = 20;

    // Digits only, at most nine: a non-negative integer that cannot overflow a long when parsed.
    private static final Pattern WHOLE_SECONDS = Pattern.compile("[0-9]{1,9}");

    private SpeechRules() {}

    /**
     * The minimum wait between unprompted speeches. In Checkpoint 4 every speak counts as unprompted, because
     * replies to a player arrive only in Checkpoint 5. Invalid or missing values fall back to the default.
     */
    public static Duration unpromptedCooldown(Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        String raw = env.get(COOLDOWN_VARIABLE);
        // Missing or unreadable settings must not stop the server, so they fall back quietly.
        if (raw == null || !WHOLE_SECONDS.matcher(raw.strip()).matches()) {
            return UNPROMPTED_COOLDOWN_DEFAULT;
        }
        return Duration.ofSeconds(Long.parseLong(raw.strip()));
    }

    /** Converts a wait to game ticks. Zero means no limit, so the result is zero. */
    public static long toTicks(Duration wait) {
        Objects.requireNonNull(wait, "wait");
        return wait.toSeconds() * TICKS_PER_SECOND;
    }
}
