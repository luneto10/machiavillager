package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.domain.behavior.DeathRules;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.story.RespawnPlan;
import io.github.luneto10.machiavillager.minecraft.ModEntities;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Death is not a reset. A player's killing blow halves their affinity, and he comes back where he fell once
 * {@link RespawnPlan#RESPAWN_DELAY_TICKS} have passed. The pending respawn is saved in {@link StoryData}, so a
 * restart during the delay keeps it. Server thread only.
 */
public final class NpcRespawn {
    // Checked once a second: a 60 s delay does not need a finer clock.
    private static final int CHECK_INTERVAL_TICKS = 20;
    // Sixteen blocks per chunk: a block coordinate shifted right by 4 gives its chunk coordinate.
    private static final int CHUNK_SHIFT = 4;

    private NpcRespawn() {}

    /** Starts the once-per-second check for a due respawn. Registered at mod start, whether or not the bridge runs. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(NpcRespawn::tick);
    }

    /**
     * Called when he dies on the server. A player killer loses half their affinity, written as an observed event.
     * Any other cause (lava, a fall, a mob, /kill) costs nobody anything. Then the respawn is saved.
     */
    public static void onDeath(ServerLevel level, MachiavillagerEntity npc, DamageSource source) {
        MinecraftServer server = level.getServer();
        if (source.getEntity() instanceof Player killer) {
            chargeKiller(server, killer);
        }

        // The game time comes from the overworld: it is saved with the world, so it does not reset on restart.
        long deathTick = server.overworld().getGameTime();
        RespawnPlan plan = RespawnPlan.after(
                deathTick, dimensionId(level), npc.getX(), npc.getY(), npc.getZ());
        StoryData.get(server).scheduleRespawn(plan);
    }

    /** True while he is dead and has not come back yet. */
    public static boolean isPending(MinecraftServer server) {
        return StoryData.get(server).respawn().isPresent();
    }

    /**
     * Brings him back now, whatever the due time. Used by the dev command and by the timed check. Returns true when
     * he is back (or already was), false when no safe spot was found: the plan stays pending and the check retries.
     */
    public static boolean respawnNow(MinecraftServer server) {
        StoryData data = StoryData.get(server);
        Optional<RespawnPlan> plan = data.respawn();
        return plan.isPresent() && respawn(server, data, plan.get());
    }

    // Once a second: only the one saved plan is read, and nothing happens until it is due.
    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        StoryData data = StoryData.get(server);
        Optional<RespawnPlan> plan = data.respawn();
        if (plan.isPresent() && plan.get().isDue(now)) {
            respawn(server, data, plan.get());
        }
    }

    // One Machiavillager at most. If he already exists (for example after /machiavillager spawn), the plan is dropped.
    private static boolean respawn(MinecraftServer server, StoryData data, RespawnPlan plan) {
        if (!MachiavillagerLookup.findAll(server).isEmpty()) {
            data.clearRespawn();
            return true;
        }
        Optional<MachiavillagerEntity> npc = spawnNear(server, plan);
        if (npc.isPresent()) {
            data.clearRespawn();
        }
        return npc.isPresent();
    }

    // Tries the death spot first. If that is unusable (for example in the void), falls back to the overworld spawn.
    private static Optional<MachiavillagerEntity> spawnNear(MinecraftServer server, RespawnPlan plan) {
        Optional<ServerLevel> deathLevel = levelFor(server, plan.dimension());
        if (deathLevel.isPresent()) {
            BlockPos deathSpot = BlockPos.containing(plan.x(), plan.y(), plan.z());
            Optional<MachiavillagerEntity> npc = spawnAt(deathLevel.get(), deathSpot);
            if (npc.isPresent()) {
                return npc;
            }
        }
        ServerLevel overworld = server.overworld();
        return spawnAt(overworld, overworld.getRespawnData().pos());
    }

    // Loads the chunk if needed, then spawns him on a safe block around the centre. Empty when nothing is safe.
    private static Optional<MachiavillagerEntity> spawnAt(ServerLevel level, BlockPos center) {
        // Outside the build height there is no ground to stand on, so the search is skipped.
        if (!level.isInsideBuildHeight(center.getY())) {
            return Optional.empty();
        }
        // The safe-spot search reads the blocks around the centre, so the chunk is loaded for it, even with nobody near.
        level.getChunk(center.getX() >> CHUNK_SHIFT, center.getZ() >> CHUNK_SHIFT);

        // A probe gives the search the walking rules of this entity. It is never added to the level.
        MachiavillagerEntity probe = ModEntities.MACHIAVILLAGER.create(level, EntitySpawnReason.EVENT);
        if (probe == null) {
            return Optional.empty();
        }
        // Ring 0: the death column itself is allowed, so he lands where he fell when that tile is safe.
        return SafeTeleport.findSpotNear(probe, center, 0)
                .map(spot -> ModEntities.MACHIAVILLAGER.spawn(level, spot, EntitySpawnReason.EVENT));
    }

    // The penalty goes straight to the saved relationships: an observed event, so the AI nudge limits do not apply.
    private static void chargeKiller(MinecraftServer server, Player killer) {
        StoryData data = StoryData.get(server);
        Relationships relationships = data.relationships();
        int before = relationships.affinityOf(killer.getUUID());
        int after = DeathRules.affinityAfterKill(before);
        data.setRelationships(relationships.withAffinity(killer.getUUID(), after));
        Machiavillager.LOGGER.info(
                "death penalty player={} before={} after={} reason=killed him provenance=observed",
                killer.getName().getString(), before, after);
    }

    private static Optional<ServerLevel> levelFor(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (dimensionId(level).equals(dimension)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    private static String dimensionId(Level level) {
        return level.dimension().identifier().toString();
    }
}
