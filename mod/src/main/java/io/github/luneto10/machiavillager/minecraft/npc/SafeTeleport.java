package io.github.luneto10.machiavillager.minecraft.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Teleports an NPC next to a player, the way a tamed wolf catches up with its owner. Server side only. */
public final class SafeTeleport {
    private static final int MAX_ATTEMPTS = 10;
    private static final int SPREAD_XZ = 3;       // candidate offset in [-3, 3] on x and z
    private static final int SPREAD_Y = 1;        // candidate offset in [-1, 1] on y
    private static final int TOO_CLOSE_XZ = 2;    // |dx| and |dz| both below this would overlap the player
    private static final double BLOCK_CENTER = 0.5;

    private SafeTeleport() {}

    /** Teleports the mob to a random safe spot within 2-3 blocks of the target. Returns false if none was found. */
    public static boolean tryTeleportNear(PathfinderMob mob, Player target) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int dx = mob.getRandom().nextIntBetweenInclusive(-SPREAD_XZ, SPREAD_XZ);
            int dz = mob.getRandom().nextIntBetweenInclusive(-SPREAD_XZ, SPREAD_XZ);
            int dy = mob.getRandom().nextIntBetweenInclusive(-SPREAD_Y, SPREAD_Y);
            if (Math.abs(dx) < TOO_CLOSE_XZ && Math.abs(dz) < TOO_CLOSE_XZ) {
                continue;
            }
            BlockPos pos = target.blockPosition().offset(dx, dy, dz);
            if (isSafe(mob, pos)) {
                mob.snapTo(pos.getX() + BLOCK_CENTER, pos.getY(), pos.getZ() + BLOCK_CENTER);
                mob.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    private static boolean isSafe(PathfinderMob mob, BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(mob, pos) != PathType.WALKABLE) {
            return false;
        }
        if (mob.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
            return false;
        }
        double moveX = pos.getX() + BLOCK_CENTER - mob.getX();
        double moveY = pos.getY() - mob.getY();
        double moveZ = pos.getZ() + BLOCK_CENTER - mob.getZ();
        return mob.level().noCollision(mob, mob.getBoundingBox().move(moveX, moveY, moveZ));
    }
}
