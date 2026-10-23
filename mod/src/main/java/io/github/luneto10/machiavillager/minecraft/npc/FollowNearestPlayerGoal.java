package io.github.luneto10.machiavillager.minecraft.npc;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Walks toward the nearest player and stops at a polite distance. Works for any PathfinderMob. */
public class FollowNearestPlayerGoal extends Goal {
    private static final double START_DISTANCE = 6.0;   // start following when farther than this
    private static final double STOP_DISTANCE = 3.0;    // stop when this close
    private static final double MAX_DISTANCE = 16.0;    // ignore players farther than this
    // Distances compared squared: avoids a square root every tick (same answer).
    private static final double START_DISTANCE_SQR = START_DISTANCE * START_DISTANCE;
    private static final double STOP_DISTANCE_SQR = STOP_DISTANCE * STOP_DISTANCE;
    private static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final PathfinderMob mob;
    private final double speed;
    private Player target;
    private int ticksUntilRepath;

    public FollowNearestPlayerGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player nearest = mob.level().getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), MAX_DISTANCE,
                entity -> !entity.isSpectator());
        if (nearest == null || mob.distanceToSqr(nearest) < START_DISTANCE_SQR) {
            return false;
        }
        target = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!target.isAlive() || target.isSpectator()) {
            return false;
        }
        double distanceSqr = mob.distanceToSqr(target);
        return distanceSqr > STOP_DISTANCE_SQR && distanceSqr < MAX_DISTANCE_SQR;
    }

    @Override
    public void start() {
        ticksUntilRepath = 0;
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(target);
        // Recomputing a path is expensive: do it every few ticks, not every tick.
        if (--ticksUntilRepath <= 0) {
            ticksUntilRepath = adjustedTickDelay(REPATH_INTERVAL_TICKS);
            mob.getNavigation().moveTo(target, speed);
        }
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }
}
