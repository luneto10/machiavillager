package io.github.luneto10.machiavillager.domain.story;

import java.util.Objects;

/**
 * When and where he comes back after dying: due at a game time, in a dimension, at the spot he fell.
 * Immutable. Pure Java: the Minecraft side turns a due plan into a spawn.
 */
public record RespawnPlan(long dueTick, String dimension, double x, double y, double z) {
    /** Sixty seconds at 20 ticks per second. */
    public static final long RESPAWN_DELAY_TICKS = 1_200L;

    public RespawnPlan {
        Objects.requireNonNull(dimension, "dimension");
    }

    /** The plan for a death at {@code deathTick}: due one delay later, at the spot he died. */
    public static RespawnPlan after(long deathTick, String dimension, double x, double y, double z) {
        return new RespawnPlan(deathTick + RESPAWN_DELAY_TICKS, dimension, x, y, z);
    }

    /** True once the game time has reached the due time. */
    public boolean isDue(long nowTicks) {
        return nowTicks >= dueTick;
    }
}
