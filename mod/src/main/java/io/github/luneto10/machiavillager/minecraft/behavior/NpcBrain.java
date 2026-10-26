package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.application.ChooseNextAction;
import io.github.luneto10.machiavillager.domain.Barks;
import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.behavior.HitReaction;
import io.github.luneto10.machiavillager.domain.behavior.IndependenceDrift;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import io.github.luneto10.machiavillager.domain.behavior.SummonBlocker;
import io.github.luneto10.machiavillager.domain.behavior.TeleportPolicy;
import io.github.luneto10.machiavillager.domain.meeting.MeetingRules;
import io.github.luneto10.machiavillager.domain.meeting.RetrySchedule;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.time.Countdown;
import io.github.luneto10.machiavillager.domain.time.PerPlayerCooldown;
import io.github.luneto10.machiavillager.minecraft.BridgeLifecycle;
import io.github.luneto10.machiavillager.minecraft.meeting.Meeting;
import io.github.luneto10.machiavillager.minecraft.meeting.SummonFacts;
import io.github.luneto10.machiavillager.minecraft.npc.NpcSpeech;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import io.github.luneto10.machiavillager.minecraft.world.VillageFinder;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    // Independence also drifts with time: alone it rises, in company it falls.
    private final IndependenceDrift drift = new IndependenceDrift();
    private final BrainDebugOverlay overlay = new BrainDebugOverlay();
    private final VillageFinder villages = new VillageFinder();
    // True while a bridge speech is running; the brain then makes no new decision, so no bark talks over it.
    private final BooleanSupplier speaking;
    // In memory only: after a restart the first hint waits the minimum again.
    private long activeTicksSinceHint;
    // The running summons, or null. One meeting at a time.
    private Meeting meeting;
    // Long cooldown, marked only when a meeting really pulled the player: the same player is not pulled again soon.
    private final PerPlayerCooldown summonCooldown = new PerPlayerCooldown(MeetingRules.SUMMON_COOLDOWN_TICKS);
    // Blocked or cancelled summons: each player is retried by timer, not by the dice.
    private final RetrySchedule summonRetries = new RetrySchedule();

    /**
     * Speaking is supplied by the caller: the brain only asks, so it does not know about the bridge.
     */
    public NpcBrain(BooleanSupplier speaking) {
        this.speaking = Objects.requireNonNull(speaking, "speaking");
    }

    /** True while a meeting is running, from the warning to the walk home. */
    public boolean inMeeting() {
        return meeting != null;
    }

    /** Called every server tick. Decides again when the timer runs out, then acts on the decision. */
    public void tick(PathfinderMob npc, ServerLevel level) {
        // A meeting owns him: decisions wait until it ends, while the story clock keeps running.
        if (meeting != null) {
            tickMeeting(npc, level);
        } else if (decisionTimer.tick() && !speaking.getAsBoolean()) {
            decide(npc, level);
        }
        if (storyClock.tick()) {
            // Before the presence check: a retry is due whether or not anyone is near.
            retryDueSummons(npc, level);
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
                data.relationships(),
                meeting == null ? null : nameOf(meeting.target(), level),
                BridgeLifecycle::aiLineText);
    }

    /** Called when he is removed from the world (dead, or his chunk unloaded): his dev bars go with him. */
    public void onRemoved() {
        overlay.hideAll();
    }

    /** Removed mid-meeting: the player is sent back now, so nobody is stranded away from home. */
    public void abortMeeting(ServerLevel level) {
        if (meeting != null) {
            meeting.abort(level);
            meeting = null;
        }
    }

    /**
     * Independence drifts on every clock step, with or without company. Ambition grows only while players are near
     * him: an empty server never advances the story.
     */
    private void growStory(PathfinderMob npc, ServerLevel level) {
        // Time alone only counts while his chunk is loaded: he does not tick otherwise, see #57.
        boolean playersNear = level.getNearestPlayer(npc, PRESENCE_RADIUS) != null;
        driftIndependence(level, playersNear);

        // Nobody close enough to watch him: the story waits.
        if (!playersNear) {
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

    /** Moves independence by the drift's whole points, if any. Saved only when the value really changes. */
    private void driftIndependence(ServerLevel level, boolean playersNear) {
        int delta = drift.tick(STORY_CLOCK_TICKS, playersNear);
        // Most clock steps earn no full point, so the log stays quiet.
        if (delta == 0) {
            return;
        }
        StoryData data = StoryData.get(level.getServer());
        int before = data.relationships().independence();
        data.setRelationships(data.relationships().withIndependence(before + delta));
        int after = data.relationships().independence();
        Machiavillager.LOGGER.info(
                "Machiavillager independence {} -> {} ({})", before, after, playersNear ? "company" : "alone");
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
        // One story read per snapshot; the summons check needs the opt-outs from the same story.
        StoryData data = StoryData.get(level.getServer());
        Phase phase = data.progress().phase();
        long now = level.getGameTime();
        return SituationSnapshot.take(npc, level, mood, phase, activeTicksSinceHint, data.relationships(),
                player -> summonable(npc, data, player, now));
    }

    // Checked here, so the odds only ever offer summons that may really happen.
    private boolean summonable(PathfinderMob npc, StoryData data, ServerPlayer player, long now) {
        return meeting == null
                && SummonFacts.check(npc, player, data.isOptedOut(player.getUUID()),
                        summonReady(player.getUUID(), now)).allowed();
    }

    /** A summons may happen only when the long cooldown is over and no retry is pending for the player. */
    private boolean summonReady(UUID player, long now) {
        return summonCooldown.isReady(player, now) && !summonRetries.isPending(player);
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

    /** His current temperament. Server thread only. */
    public Mood mood() {
        return mood;
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
            case NpcAction.Summon summon -> startSummon(npc, level, summon.target());
            case NpcAction.Mutter() -> mutter(npc, level);
        }
    }

    /** Starts a meeting when none is running and the rules allow it. A refused summons is logged and retried soon. */
    private void startSummon(PathfinderMob npc, ServerLevel level, UUID target) {
        // One meeting at a time: a second summons waits for the first to end.
        if (meeting != null) {
            return;
        }

        // Offline players cannot be told anything, so the refusal is only logged.
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        if (player == null) {
            Machiavillager.LOGGER.info("Summon blocked for {}: player offline", target);
            return;
        }

        // Fairness first: the opt-out and danger rules decide (the cooldown was already rolled in the dice).
        // A refused summons names its rule in the log and tells the player why, even from far away.
        Optional<SummonBlocker> blocker = summonBlocker(npc, level, target);
        if (blocker.isPresent()) {
            refuseSummon(target, blocker.get(), level.getGameTime());
            return;
        }

        // The cooldown is marked when the meeting ends, depending on whether he really pulled the player.
        meeting = Meeting.start(target);
        Machiavillager.LOGGER.info("Machiavillager summons {}", nameOf(target, level));
    }

    /** Advances the running meeting one tick, and forgets it once it has ended. */
    private void tickMeeting(PathfinderMob npc, ServerLevel level) {
        UUID target = meeting.target();
        StoryData data = StoryData.get(level.getServer());
        // Re-checked during the warning; the cooldown is passed as ready, since this summons already spent it.
        Supplier<Optional<SummonBlocker>> blocker = () -> summonBlocker(npc, level, target);
        if (!meeting.tick(npc, level, data, villages, blocker)) {
            markSummonEnded(data, target, meeting.pulled(), level.getGameTime());
            meeting = null;
        }
    }

    /** A meeting that pulled the player waits the long cooldown; one called off before the pull is retried by timer. */
    private void markSummonEnded(StoryData data, UUID target, boolean pulled, long now) {
        // A real meeting is rare, so it gets the long cooldown and any retry is done with.
        if (pulled) {
            summonCooldown.markUsed(target, now);
            summonRetries.clear(target);
            return;
        }
        // Called off before the pull: retried later, unless he was told no.
        if (data.isOptedOut(target)) {
            summonRetries.clear(target);
            return;
        }
        summonRetries.schedule(target, now);
    }

    /** Logs a refused summons and arranges its retry. Silent toward the player: the reason stays in the log. */
    private void refuseSummon(UUID target, SummonBlocker blocker, long now) {
        // Never chase a player who said no: opted out means no retry at all.
        if (blocker == SummonBlocker.OPTED_OUT) {
            Machiavillager.LOGGER.info("Summon blocked for {}: {}", target, blocker);
            summonRetries.clear(target);
            return;
        }
        Machiavillager.LOGGER.info("Summon blocked for {}: {} (retry scheduled)", target, blocker);
        summonRetries.schedule(target, now);
    }

    /**
     * Runs the summons whose retry timer has come due. A retry waits while a meeting runs or the player is
     * offline, and is armed for another full interval.
     */
    private void retryDueSummons(PathfinderMob npc, ServerLevel level) {
        long now = level.getGameTime();
        for (UUID player : summonRetries.takeDue(now)) {
            // One meeting at a time, and an offline player cannot be pulled: wait one more interval.
            if (meeting != null || level.getServer().getPlayerList().getPlayer(player) == null) {
                summonRetries.schedule(player, now);
                continue;
            }
            Machiavillager.LOGGER.info("Retrying summon of {}", nameOf(player, level));
            startSummon(npc, level, player);
        }
    }

    /** The first rule that refuses a summons to this player, or empty when it may happen. */
    private Optional<SummonBlocker> summonBlocker(PathfinderMob npc, ServerLevel level, UUID target) {
        StoryData data = StoryData.get(level.getServer());
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        // Offline: nothing to refuse here, since the meeting ends on its own once the player is gone.
        if (player == null) {
            return Optional.empty();
        }
        return SummonFacts.check(npc, player, data.isOptedOut(target), true).blocker();
    }

    /** The player he is talking to right now, or null when no meeting is talking. Read by the meeting goal. */
    public Player meetingPartner(ServerLevel level) {
        if (meeting == null || !meeting.inMeeting()) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(meeting.target());
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

    /** Mutters one line for the current story phase. Only players within whisper range overhear it. */
    private void mutter(PathfinderMob npc, ServerLevel level) {
        Phase phase = StoryData.get(level.getServer()).progress().phase();
        NpcSpeech.whisper(npc, mutterLines(phase).pick(ThreadLocalRandom.current()));
    }

    /** What he mutters to himself in each story phase. The switch is exhaustive, so a new phase needs lines. */
    private static Barks mutterLines(Phase phase) {
        return switch (phase) {
            case PARTNER -> new Barks(
                    "Friendship... such a useful word.",
                    "Count the emeralds. Count them again.");
            case SEEDS -> new Barks(
                    "A crown would suit me. Emerald, not gold.",
                    "Leaders are just people who decided first.");
            case PROPOSAL -> new Barks(
                    "They'll say yes. They always say yes.",
                    "A title for each of them. A leash for each title.");
            case REGIME -> new Barks(
                    "Loyalty is a habit. Habits can be trained.",
                    "The bell rings when I say it rings.");
            case WAR -> new Barks(
                    "Traitors whisper too. Louder.",
                    "If the walls fall, I'll build taller walls.");
        };
    }

    private static String describe(NpcAction action, ServerLevel level) {
        return switch (action) {
            case NpcAction.Follow follow -> "Follow(" + nameOf(follow.target(), level) + ")";
            case NpcAction.Wander() -> "Wander";
            case NpcAction.Bark bark -> "Bark(" + nameOf(bark.listener(), level) + ")";
            case NpcAction.Hint hint -> "Hint(" + nameOf(hint.listener(), level) + ")";
            case NpcAction.Summon summon -> "Summon(" + nameOf(summon.target(), level) + ")";
            case NpcAction.Mutter() -> "Mutter";
        };
    }

    private static String nameOf(UUID playerId, ServerLevel level) {
        Player player = level.getPlayerByUUID(playerId);
        return player != null ? player.getPlainTextName() : playerId.toString();
    }
}
