package io.github.luneto10.machiavillager.minecraft.world;

import io.github.luneto10.machiavillager.Machiavillager;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;

/**
 * Finds the nearest village for a brain. Villages do not move, so the last result is cached and reused
 * while the NPC stays close to where it was searched. One instance per brain.
 *
 * <p>The search runs on the server thread (world access is not thread-safe) and can pause the tick. It is
 * kept rare, small and cached, and its cost is logged so a slow search shows up in the server log.
 */
public final class VillageFinder {
    private static final int SEARCH_RADIUS_CHUNKS = 32; // ~512 blocks; bigger radius = longer pause on the server thread
    private static final long CACHE_REUSE_DISTANCE = 256; // blocks
    private static final long CACHE_REUSE_DISTANCE_SQR = CACHE_REUSE_DISTANCE * CACHE_REUSE_DISTANCE;

    private BlockPos cachedFrom;
    private Optional<BlockPos> cachedResult = Optional.empty();

    /** The nearest village to {@code from}, or empty when there is none nearby (or outside the overworld). */
    public Optional<BlockPos> nearest(ServerLevel level, BlockPos from) {
        if (level.dimension() != Level.OVERWORLD) {
            return Optional.empty();
        }
        if (cachedFrom != null && isNearCachedOrigin(from)) {
            return cachedResult;
        }
        long start = System.nanoTime();
        BlockPos found = level.findNearestMapStructure(StructureTags.VILLAGE, from, SEARCH_RADIUS_CHUNKS, false);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        Machiavillager.LOGGER.info(
                "Village search took {} ms: {}", millis, found == null ? "none" : found.toShortString());
        cachedFrom = from;
        cachedResult = Optional.ofNullable(found);
        return cachedResult;
    }

    private boolean isNearCachedOrigin(BlockPos from) {
        long dx = from.getX() - cachedFrom.getX();
        long dz = from.getZ() - cachedFrom.getZ();
        return dx * dx + dz * dz <= CACHE_REUSE_DISTANCE_SQR;
    }
}
