package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.application.ChooseNextAction;
import io.github.luneto10.machiavillager.domain.Barks;
import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.behavior.HitReaction;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import io.github.luneto10.machiavillager.domain.behavior.TeleportPolicy;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.time.Countdown;
import io.github.luneto10.machiavillager.domain.time.PerPlayerCooldown;
import io.github.luneto10.machiavillager.minecraft.npc.NpcSpeech;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import io.github.luneto10.machiavillager.minecraft.world.VillageFinder;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;

/**
 * The NPC's decision maker. Asks the decision engine what to do every 10-30 seconds, then carries
 * out the part that touches the body. Server side only.
 */
public final class NpcBrain {
    private static final int MIN_DECISION_TICKS = 200; // 10 s
    private static final int MAX_DECISION_TICKS = 600; // 30 s
    private static final long CATCH_UP_COOLDOWN_TICKS = 100; // 5 s between teleports to the same player
    private static final int STORY_CLOCK_TICKS = 20; // grows once per second
    private static final double PRESENCE_RADIUS = 32.0; // same radius he sees players in
    private static final int PERCENT = 100;

    private static final Barks IDLE_LINES = new Barks(
            "%s, have you noticed how much the villagers charge for bread?",
            "Psst, %s. Do you trust the village elder? I don't.",
            "%s! Lovely day for a little ambition.",
            "I've been counting emeralds, %s. Not mine. Theirs.");

    private static final Barks HINT_LINES = new Barks(
            "%1$s... have you seen the village near %2$d, %3$d? So disorganized. Someone should take care of it.",
            "Between us, %1$s: the village at %2$d, %3$d has too many emeralds and too few leaders.",
            "%1$s, I walked past %2$d, %3$d. Lovely village. Wasted on its elder.");

    private final ChooseNextAction chooser = ChooseNextAction.withDefaultCatalog();
    private NpcAction current = new NpcAction.Wander();
    // The brain runs on the single server thread, so caching the thread-local generator in a field is safe.
    private final Countdown decisionTimer = Countdown.randomBetween(
            MIN_DECISION_TICKS, MAX_DECISION_TICKS, ThreadLocalRandom.current());
    private final Countdown storyClock = Countdown.every(STORY_CLOCK_TICKS);
    private Mood mood = Mood.CALM;
    // One cooldown per friend, so a catch-up teleport is not repeated right away.
    private final PerPlayerCooldown catchUpCooldown = new PerPlayerCooldown(CATCH_UP_COOLDOWN_TICKS);
    // Rate limits hits, so punching him cannot farm independence.
    private final HitReaction hitReaction = new HitReaction();
    private final BrainDebugOverlay overlay = new BrainDebugOverlay();
    private final VillageFinder villages = new VillageFinder();
    // In memory only: after a restart the first hint waits the minimum again.
    private long activeTicksSinceHint;

    /** Called every server tick. Decides again when the timer runs out, then acts on the decision. */
    public void tick(PathfinderMob npc, ServerLevel level) {
        if (decisionTimer.tick()) {
            decide(npc, level);
        }
        if (storyClock.tick()) {
            growStory(npc, level);
        }

        // Supplier: the text is only built when the overlay actually shows (dev, every 10 ticks).
        StoryData data = StoryData.get(level.getServer());
        overlay.show(
                npc,
                level,
                () -> describe(current, level),
                mood,
                decisionTimer.remainingTicks(),
                MAX_DECISION_TICKS,
                data.progress(),
                data.relationships());
    }

    /** Called when he is removed from the world (dead, or his chunk unloaded): his dev bars go with him. */
    public void onRemoved() {
        overlay.hideAll();
    }

    /** Ambition grows only while players are near him: an empty server never advances the story. */
    private void growStory(PathfinderMob npc, ServerLevel level) {
        // Nobody close enough to watch him: the story waits.
        if (level.getNearestPlayer(npc, PRESENCE_RADIUS) == null) {
            return;
        }

        // The hint clock counts the same active time as the story clock.
        activeTicksSinceHint += STORY_CLOCK_TICKS;

        // Remember the phase before growing, so a change can be logged.
        StoryData data = StoryData.get(level.getServer());
        Phase before = data.progress().phase();
        data.setProgress(data.progress().grow(STORY_CLOCK_TICKS, StoryData.personality(level.getServer())));
        Phase after = data.progress().phase();

        // Logged only on a real change, so the log stays quiet otherwise.
        if (before != after) {
            Machiavillager.LOGGER.info("Machiavillager phase: {} -> {}", before, after);
        }
    }

    /** Being hit lowers his affinity for the attacker and raises his independence, within rate limits. */
    public void onHurtBy(ServerLevel level, UUID attacker) {
        StoryData data = StoryData.get(level.getServer());
        data.setRelationships(hitReaction.apply(data.relationships(), attacker, level.getGameTime()));
        Relationships after = data.relationships();
        Machiavillager.LOGGER.info(
                "Machiavillager was hit by {}: affinity {}, independence {}",
                attacker,
                after.affinityOf(attacker),
                after.independence());
    }

    private void decide(PathfinderMob npc, ServerLevel level) {
        current = chooser.choose(snapshot(npc, level), ThreadLocalRandom.current());
        Machiavillager.LOGGER.info("Machiavillager decided: {}", describe(current, level));
        act(npc, level);
    }

    /** Dev: the odds of his next decision, most likely first, e.g. "Follow(Player388) 53% | Wander 26%". */
    public String describeOdds(PathfinderMob npc, ServerLevel level) {
        // The same snapshot decide() uses, so the odds shown are the odds he would really roll.
        WeightedDice<NpcAction> odds = chooser.odds(snapshot(npc, level));

        // Most likely first, so the dominant action reads first.
        return odds.outcomes().stream()
                .sorted(Comparator.<NpcAction>comparingDouble(odds::chanceOf).reversed())
                .map(action -> describe(action, level) + " " + Math.round(odds.chanceOf(action) * PERCENT) + "%")
                .collect(Collectors.joining(" | "));
    }

    /** The world as he sees it right now: the input to every decision. */
    private Situation snapshot(PathfinderMob npc, ServerLevel level) {
        Phase phase = StoryData.get(level.getServer()).progress().phase();
        return SituationSnapshot.take(npc, level, mood, phase, activeTicksSinceHint);
    }

    /**
     * Dev only: makes the action the current decision and acts on it now. It skips the dice, the phase
     * and the cooldowns on purpose, so any action can be tested on demand.
     */
    public void forceAction(PathfinderMob npc, ServerLevel level, NpcAction action) {
        // The forced action becomes the decision the rest of the brain reads, such as the follow goal.
        current = action;
        Machiavillager.LOGGER.info("Machiavillager forced: {}", describe(current, level));

        // Restart the timer so the forced action lasts a full decision interval, not the rest of the old one.
        decisionTimer.restart();

        // Act right away instead of waiting for the next tick.
        act(npc, level);
    }

    /** Dev: changes his mood now. The next decision already uses it. */
    public void setMood(Mood mood) {
        this.mood = Objects.requireNonNull(mood, "mood");
    }

    /** Dev: makes the brain decide again on the next tick. */
    public void decideNow() {
        decisionTimer.expireNow();
    }

    /** The player the NPC should walk after, or null when the current decision is not a follow. */
    public Player followTarget(ServerLevel level) {
        return current instanceof NpcAction.Follow follow ? level.getPlayerByUUID(follow.target()) : null;
    }

    /** True when the follow goal may teleport next to this player now (dog-style catch-up). Starts the cooldown when it returns true. */
    public boolean mayCatchUpTo(Player player, PathfinderMob npc) {
        if (!(current instanceof NpcAction.Follow follow) || !follow.target().equals(player.getUUID())) {
            return false;
        }
        if (player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
            return false;
        }

        // Cooldown first: a teleport to the same friend is not repeated right away.
        long now = npc.level().getGameTime();
        if (!catchUpCooldown.isReady(player.getUUID(), now)) {
            return false;
        }

        // Teleport rules depend on mood, distance and how much he cares about this player.
        boolean allowed = TeleportPolicy.mayCatchUp(
                mood,
                npc.distanceTo(player),
                npc.level() == player.level(),
                StoryData.get(((ServerLevel) npc.level()).getServer())
                        .relationships()
                        .caresAbout(player.getUUID()));

        // Only an allowed teleport starts the cooldown.
        if (allowed) {
            catchUpCooldown.markUsed(player.getUUID(), now);
        }
        return allowed;
    }

    private void act(PathfinderMob npc, ServerLevel level) {
        // Sealed interface + exhaustive switch: adding a new NpcAction makes this fail to compile until it is handled.
        switch (current) {
            case NpcAction.Follow follow -> {
                // Nothing to do here: the FollowPlayerGoal reads followTarget().
            }
            case NpcAction.Wander wander -> {
                // Nothing to do here: the vanilla stroll goal runs whenever MOVE is free.
            }
            case NpcAction.Bark bark -> {
                // A bark needs the listener online; otherwise there is no one to talk to.
                Player listener = level.getPlayerByUUID(bark.listener());
                if (listener != null) {
                    String line = IDLE_LINES.pick(ThreadLocalRandom.current()).formatted(listener.getPlainTextName());
                    NpcSpeech.say(npc, line);
                }
            }
            case NpcAction.Hint hint -> giveHint(npc, level, hint.listener());
        }
    }

    private void giveHint(PathfinderMob npc, ServerLevel level, UUID listenerId) {
        // Reset first, also when nothing is found, so a failed search is not retried for 10 minutes.
        activeTicksSinceHint = 0;

        // The listener may have logged out while the decision was waiting.
        Player listener = level.getPlayerByUUID(listenerId);
        if (listener == null) {
            return;
        }

        // No village in reach means nothing to say; the reset above still applies.
        Optional<BlockPos> village = villages.nearest(level, npc.blockPosition());
        if (village.isEmpty()) {
            Machiavillager.LOGGER.info("No village within reach, no hint.");
            return;
        }

        // Name the player and the village coordinates, so the hint is actionable.
        BlockPos at = village.get();
        String line = HINT_LINES.pick(ThreadLocalRandom.current())
                .formatted(listener.getPlainTextName(), at.getX(), at.getZ());
        NpcSpeech.say(npc, line);
    }

    private static String describe(NpcAction action, ServerLevel level) {
        return switch (action) {
            case NpcAction.Follow follow -> "Follow(" + nameOf(follow.target(), level) + ")";
            case NpcAction.Wander() -> "Wander";
            case NpcAction.Bark bark -> "Bark(" + nameOf(bark.listener(), level) + ")";
            case NpcAction.Hint hint -> "Hint(" + nameOf(hint.listener(), level) + ")";
        };
    }

    private static String nameOf(UUID playerId, ServerLevel level) {
        Player player = level.getPlayerByUUID(playerId);
        return player != null ? player.getPlainTextName() : playerId.toString();
    }
}
