package io.github.luneto10.machiavillager.minecraft.meeting;

import io.github.luneto10.machiavillager.domain.behavior.SummonCheck;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Reads the world into the domain's {@link SummonCheck}: where the player is, whether they fight, fly or ride,
 * and whether he is in danger. The rules live in the domain; this class only looks. Server side only.
 */
public final class SummonFacts {
    private SummonFacts() {}

    /**
     * The fairness facts for summoning this player right now. {@code optedOut} and {@code cooldownReady} come
     * from the brain's saved state, so this class never reads the story itself.
     */
    public static SummonCheck check(PathfinderMob npc, ServerPlayer player, boolean optedOut, boolean cooldownReady) {
        // Only the same dimension can be reached with one teleport.
        boolean sameDimension = player.level() == npc.level();

        // Hit recently by a mob or a player means a fight is going on, even if it is quiet this second.
        boolean inCombat = player.getLastHurtByMob() != null || player.getLastHurtByPlayer() != null;

        // Gliding or flying: pulling someone out of the air would be cruel.
        boolean flying = player.isFallFlying() || player.getAbilities().flying;
        boolean inVehicle = player.isPassenger();

        // He never summons while he burns, sinks in lava or is being hit.
        boolean npcInDanger = npc.isOnFire()
                || npc.isInLava()
                || npc.getLastHurtByMob() != null
                || npc.getLastHurtByPlayer() != null;
        return new SummonCheck(sameDimension, inCombat, flying, inVehicle, optedOut, cooldownReady, npcInDanger);
    }
}
