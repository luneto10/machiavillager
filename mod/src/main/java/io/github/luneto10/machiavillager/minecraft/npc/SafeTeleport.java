package io.github.luneto10.machiavillager.minecraft.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Finds safe spots next to a position, and moves an NPC next to a player the way a tamed wolf catches up. Server side only. */
public final class SafeTeleport {
    // Every candidate within this many blocks of the centre on x and z is checked.
    private static final int SEARCH_RADIUS_XZ = 3;
    // Down to two blocks: a step down onto a lower floor or onto snow still counts as reachable.
    private static final int MIN_DY = -2;
    // One block up is a step he can walk onto; higher is not walkable.
    private static final int MAX_DY = 1;
    // Dog-style catch-up keeps two blocks off the player on at least one axis, so it never lands on them.
    private static final int DOG_MIN_RING = 2;
    private static final double BLOCK_CENTER = 0.5;

    private SafeTeleport() {}

    /**
     * A safe spot around {@code center}, sized for {@code probe}. Every candidate in a square of
     * {@link #SEARCH_RADIUS_XZ} blocks is scanned, at most 7 * 7 * 4 checks, which is cheap for a rare event.
     * Candidates with |dx| and |dz| both below {@code minRing} are skipped: 1 skips only the centre column.
     * Empty when no candidate passes.
     */
    public static Optional<BlockPos> findSpotNear(PathfinderMob probe, BlockPos center, int minRing) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS_XZ; dx <= SEARCH_RADIUS_XZ; dx++) {
            for (int dz = -SEARCH_RADIUS_XZ; dz <= SEARCH_RADIUS_XZ; dz++) {
                // Too close to the centre on both axes would overlap whoever stands there.
                if (Math.abs(dx) < minRing && Math.abs(dz) < minRing) {
                    continue;
                }
                for (int dy = MAX_DY; dy >= MIN_DY; dy--) {
                    candidates.add(center.offset(dx, dy, dz));
                }
            }
        }

        // Shuffled, so repeated summons do not always land on the same tile; seeded from the probe's own random.
        Collections.shuffle(candidates, new Random(probe.getRandom().nextLong()));
        for (BlockPos pos : candidates) {
            if (isSafe(probe, pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /**
     * Where to land next to {@code anchor}: the center of a safe block around it, or, as a last resort, the anchor's
     * exact position while it stands on the ground (entities may overlap). Empty when neither works, e.g. the anchor
     * is in the air or in water. One rule for both directions: the dog teleport (he lands by you) and the summon
     * (you land by him).
     */
    public static Optional<Vec3> landingNear(PathfinderMob probe, Entity anchor, int minRing) {
        // A free tile next to the anchor is best: arriving beside someone, not inside them.
        Optional<BlockPos> spot = findSpotNear(probe, anchor.blockPosition(), minRing);
        if (spot.isPresent()) {
            BlockPos pos = spot.get();
            return Optional.of(new Vec3(pos.getX() + BLOCK_CENTER, pos.getY(), pos.getZ() + BLOCK_CENTER));
        }

        // Last resort: the anchor's exact spot, only on the ground, or the arrival would hang in the air or water.
        if (!anchor.onGround()) {
            return Optional.empty();
        }
        return Optional.of(anchor.position());
    }

    /** Dog-style catch-up: the mob lands next to the target (see {@link #landingNear}). False when it cannot. */
    public static boolean tryTeleportNear(PathfinderMob mob, Player target) {
        // No landing right now: the follow goal simply tries again on a later tick.
        Optional<Vec3> landing = landingNear(mob, target, DOG_MIN_RING);
        if (landing.isEmpty()) {
            return false;
        }

        // Arrive standing still, so he does not keep walking an old path.
        Vec3 at = landing.get();
        mob.snapTo(at.x, at.y, at.z);
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
