package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.time.Cooldown;
import io.github.luneto10.machiavillager.domain.time.PerPlayerCooldown;
import java.util.UUID;

/**
 * What a hit does to him. Hits are rate limited, so spamming punches cannot farm independence.
 * Mutable on purpose: it owns the cooldowns that decide whether a hit counts.
 */
public final class HitReaction {
    static final int AFFINITY_LOSS = 10;
    // 1 min: a flurry of punches is one offense per player.
    static final long AFFINITY_COOLDOWN_TICKS = 1_200;
    static final int INDEPENDENCE_GAIN = 2;
    // 10 min shared by everyone: at most +12 per hour from hits, however many friends punch him.
    static final long INDEPENDENCE_COOLDOWN_TICKS = 12_000;

    private final PerPlayerCooldown affinityCooldown = new PerPlayerCooldown(AFFINITY_COOLDOWN_TICKS);
    private final Cooldown independenceCooldown = new Cooldown(INDEPENDENCE_COOLDOWN_TICKS);

    /** Applies a hit by {@code attacker} and returns the new relationships. Returns the same object when neither change applies. */
    public Relationships apply(Relationships before, UUID attacker, long nowTicks) {
        Relationships after = before;

        // Affinity drops once per attacker per cooldown, so a flurry of punches counts as one offense.
        if (affinityCooldown.tryUse(attacker, nowTicks)) {
            after = after.withAffinity(attacker, before.affinityOf(attacker) - AFFINITY_LOSS);
        }

        // Independence is shared: the cooldown is for him, not for each friend who punches him.
        if (independenceCooldown.tryUse(nowTicks)) {
            after = after.withIndependence(after.independence() + INDEPENDENCE_GAIN);
        }
        return after;
    }
}
