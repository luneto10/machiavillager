package io.github.luneto10.machiavillager.minecraft.npc;

import java.util.EnumSet;
import java.util.function.Supplier;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Holds him still during a meeting: no walking, eyes on the partner. The provider names the partner, so this
 * goal only holds still. Works for any PathfinderMob.
 */
public class MeetingGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<Player> partner;

    public MeetingGoal(PathfinderMob mob, Supplier<Player> partner) {
        this.mob = mob;
        this.partner = partner;
        // MOVE silences the walking goals and LOOK the idle look goals, while the meeting runs.
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return partner.get() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return partner.get() != null;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        // Stopped every tick, so no path can pull him away during the talk.
        mob.getNavigation().stop();
        Player target = partner.get();
        if (target != null) {
            mob.getLookControl().setLookAt(target);
        }
    }
}
