package io.github.luneto10.machiavillager.minecraft.npc;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Finds safe spots next to a position, and moves an NPC next to a player the way a tamed wolf catches up. Server side only. */
public final class SafeTeleport {
    private static final int MAX_ATTEMPTS = 10;
    private static final int SPREAD_XZ = 3;       // candidate offset in [-3, 3] on x and z
    private static final int SPREAD_Y = 1;        // candidate offset in [-1, 1] on y
    private static final int TOO_CLOSE_XZ = 2;    // |dx| and |dz| both below this would overlap the centre
    private static final double BLOCK_CENTER = 0.5;

    private SafeTeleport() {}

    /**
     * A safe spot 2-3 blocks around {@code center}, sized for {@code probe}. Used both to move him next to you
     * (dog-style catch-up) and you next to him (summon). Empty when no candidate passes.
     */
    public static Optional<BlockPos> findSpotNear(PathfinderMob probe, BlockPos center) {
        // Random candidates, not a fixed grid, so repeated teleports do not always land on the same tile.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int dx = probe.getRandom().nextIntBetweenInclusive(-SPREAD_XZ, SPREAD_XZ);
            int dz = probe.getRandom().nextIntBetweenInclusive(-SPREAD_XZ, SPREAD_XZ);
            int dy = probe.getRandom().nextIntBetweenInclusive(-SPREAD_Y, SPREAD_Y);

            // Too close to the centre on both axes would overlap whoever stands there.
            if (Math.abs(dx) < TOO_CLOSE_XZ && Math.abs(dz) < TOO_CLOSE_XZ) {
                continue;
            }
            BlockPos pos = center.offset(dx, dy, dz);
            if (isSafe(probe, pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /** Teleports the mob to a random safe spot within 2-3 blocks of the target. Returns false if none was found. */
    public static boolean tryTeleportNear(PathfinderMob mob, Player target) {
        Optional<BlockPos> spot = findSpotNear(mob, target.blockPosition());
        if (spot.isEmpty()) {
            return false;
        }
        BlockPos pos = spot.get();
        mob.snapTo(pos.getX() + BLOCK_CENTER, pos.getY(), pos.getZ() + BLOCK_CENTER);
        mob.getNavigation().stop();
        return true;
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
