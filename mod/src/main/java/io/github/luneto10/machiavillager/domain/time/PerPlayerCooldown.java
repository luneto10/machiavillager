package io.github.luneto10.machiavillager.domain.time;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One {@link Cooldown} per player, all with the same duration. Pure Java: no Minecraft imports.
 * One entry per player, so it stays tiny.
 */
public final class PerPlayerCooldown {
    private final long durationTicks;
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();

    /** Every player gets a cooldown of {@code durationTicks}. Must be at least 1. */
    public PerPlayerCooldown(long durationTicks) {
        // Validates the duration up front, the same way Cooldown does.
        new Cooldown(durationTicks);
        this.durationTicks = durationTicks;
    }

    /** True if this player's cooldown is ready (or they never had one). */
    public boolean isReady(UUID player, long nowTicks) {
        return cooldownOf(player).isReady(nowTicks);
    }

    /** Starts this player's cooldown now. */
    public void markUsed(UUID player, long nowTicks) {
        cooldownOf(player).markUsed(nowTicks);
    }

    /** Uses the action for this player if ready. Returns true when it was ready. */
    public boolean tryUse(UUID player, long nowTicks) {
        return cooldownOf(player).tryUse(nowTicks);
    }

    private Cooldown cooldownOf(UUID player) {
        return cooldowns.computeIfAbsent(Objects.requireNonNull(player, "player"),
                id -> new Cooldown(durationTicks));
    }
}
