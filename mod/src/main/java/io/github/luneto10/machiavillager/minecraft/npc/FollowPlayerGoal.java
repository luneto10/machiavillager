package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.domain.time.Countdown;
import java.util.EnumSet;
import java.util.function.Supplier;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Walks toward whatever player the provider names, and stops at a polite distance. The provider
 * decides who to follow; this goal only moves. Works for any PathfinderMob.
 */
public class FollowPlayerGoal extends Goal {
    private static final double START_DISTANCE = 6.0;   // start following when farther than this
    private static final double STOP_DISTANCE = 3.0;    // stop when this close
    private static final double MAX_DISTANCE = 16.0;    // give up on players farther than this
    // Distances compared squared: avoids a square root every tick (same answer).
    private static final double START_DISTANCE_SQR = START_DISTANCE * START_DISTANCE;
    private static final double STOP_DISTANCE_SQR = STOP_DISTANCE * STOP_DISTANCE;
    private static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final PathfinderMob mob;
    private final double speed;
    private final Supplier<Player> targetProvider;
    private Player target;
    private final Countdown repathTimer;

    public FollowPlayerGoal(PathfinderMob mob, double speed, Supplier<Player> targetProvider) {
        this.mob = mob;
        this.speed = speed;
        this.targetProvider = targetProvider;
        this.repathTimer = Countdown.every(adjustedTickDelay(REPATH_INTERVAL_TICKS));
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player candidate = targetProvider.get();
        if (candidate == null || candidate.isSpectator() || !candidate.isAlive()
                || mob.distanceToSqr(candidate) < START_DISTANCE_SQR) {
            return false;
        }
        target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Stop if the brain has changed its mind about who to follow.
        if (!target.isAlive() || target.isSpectator() || targetProvider.get() != target) {
            return false;
        }
        double distanceSqr = mob.distanceToSqr(target);
        return distanceSqr > STOP_DISTANCE_SQR && distanceSqr < MAX_DISTANCE_SQR;
    }

    @Override
    public void start() {
        repathTimer.expireNow();
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(target);
        // Recomputing a path is expensive: do it every few ticks, not every tick.
        if (repathTimer.tick()) {
            mob.getNavigation().moveTo(target, speed);
        }
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }
}
