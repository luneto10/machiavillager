package io.github.luneto10.machiavillager.domain.bridge;

import io.github.luneto10.machiavillager.domain.speech.SpeechRules;
import io.github.luneto10.machiavillager.domain.time.PerPlayerCooldown;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Limits how often each player's overheard lines reach the backend, so a chatty player cannot burn the token
 * budget. Lines that name him are never limited and never reach this class. Pure Java: no Minecraft imports.
 */
public final class NearChatCooldown {
    /** Environment variable for the wait between forwarded overheard lines per player, in whole seconds. */
    public static final String COOLDOWN_VARIABLE = "MACHIAVILLAGER_NEAR_COOLDOWN_SECONDS";

    /** Default wait between overheard lines from one player: 5 seconds. */
    public static final Duration COOLDOWN_DEFAULT = Duration.ofSeconds(5);

    // Digits only, at most nine: a non-negative integer that cannot overflow a long when converted to ticks.
    private static final Pattern WHOLE_SECONDS = Pattern.compile("[0-9]{1,9}");

    private final long cooldownTicks;
    // Null when there is no limit: PerPlayerCooldown refuses a zero duration, so zero is handled here.
    private final PerPlayerCooldown cooldowns;

    /** A limit of {@code cooldownTicks} per player. Zero means no limit. */
    public NearChatCooldown(long cooldownTicks) {
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must be >= 0, was " + cooldownTicks);
        }
        this.cooldownTicks = cooldownTicks;
        this.cooldowns = cooldownTicks == 0 ? null : new PerPlayerCooldown(cooldownTicks);
    }

    /**
     * Forwards one overheard line from this player if they have no recorded line or the wait has passed.
     * Returns true when it was allowed, and then records this tick; a refused line leaves the record alone.
     */
    public boolean tryAcquire(UUID player, long gameTick) {
        Objects.requireNonNull(player, "player");
        // No limit configured, so every line is allowed and nothing is recorded.
        if (cooldowns == null) {
            return true;
        }
        return cooldowns.tryUse(player, gameTick);
    }

    /**
     * The per-player wait in game ticks. Missing or invalid values fall back to the default; zero means no limit.
     */
    public static long cooldownTicks(Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        String raw = env.get(COOLDOWN_VARIABLE);
        // Missing or unreadable settings must not stop the server, so they fall back quietly.
        if (raw == null || !WHOLE_SECONDS.matcher(raw.strip()).matches()) {
            return SpeechRules.toTicks(COOLDOWN_DEFAULT);
        }
        return SpeechRules.toTicks(Duration.ofSeconds(Long.parseLong(raw.strip())));
    }
}
