package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.domain.Barks;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The Machiavillager NPC. Act 1 body: follows the nearest player, wanders, talks when clicked. */
public class MachiavillagerEntity extends PathfinderMob {
    private static final double MAX_HEALTH = 40.0;
    private static final double MOVEMENT_SPEED = 0.3;
    private static final double FOLLOW_SPEED = 1.0;   // multiplier of MOVEMENT_SPEED
    private static final double STROLL_SPEED = 0.6;
    private static final float LOOK_DISTANCE = 8.0f;

    private static final Barks CLICK_LINES = new Barks(
            "Need something, partner? I have... opportunities.",
            "Do you know how many emeralds that village is hoarding? I counted.",
            "Friendship is an investment. I expect returns.",
            "Shh. I'm planning. Nothing illegal. Yet.");

    public MachiavillagerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
    }

    // Lower number = higher priority. Goals sharing a flag (MOVE/LOOK) compete; different flags run together.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FollowNearestPlayerGoal(this, FOLLOW_SPEED));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, LOOK_DISTANCE));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    // Right-click. Runs on both sides; NpcSpeech only talks on the server, so the line appears once.
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        NpcSpeech.say(this, CLICK_LINES.pick(ThreadLocalRandom.current()));
        return InteractionResult.SUCCESS;
    }

    // Always show the name tag above his head, like a player. The name comes from lang/en_us.json.
    @Override
    public boolean shouldShowName() {
        return true;
    }

    // He is a character, not a random mob: never despawn.
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
