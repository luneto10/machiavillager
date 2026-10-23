package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.application.ChooseNextAction;
import io.github.luneto10.machiavillager.domain.Barks;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import io.github.luneto10.machiavillager.domain.time.Countdown;
import io.github.luneto10.machiavillager.minecraft.npc.NpcSpeech;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final Barks IDLE_LINES = new Barks(
            "%s, have you noticed how much the villagers charge for bread?",
            "Psst, %s. Do you trust the village elder? I don't.",
            "%s! Lovely day for a little ambition.",
            "I've been counting emeralds, %s. Not mine. Theirs.");

    private final ChooseNextAction chooser = ChooseNextAction.withDefaultCatalog();
    private NpcAction current = new NpcAction.Wander();
    // The brain runs on the single server thread, so caching the thread-local generator in a field is safe.
    private final Countdown decisionTimer = Countdown.randomBetween(
            MIN_DECISION_TICKS, MAX_DECISION_TICKS, ThreadLocalRandom.current());
    private Mood mood = Mood.CALM;

    /** Called every server tick. Decides again when the timer runs out, then acts on the decision. */
    public void tick(PathfinderMob npc, ServerLevel level) {
        if (decisionTimer.tick()) {
            decide(npc, level);
        }
        // Supplier: the text is only built when the overlay actually shows (dev, every 10 ticks).
        BrainDebugOverlay.show(npc, level, () -> describe(current, level), mood, decisionTimer.remainingTicks());
    }

    private void decide(PathfinderMob npc, ServerLevel level) {
        Situation snapshot = SituationSnapshot.take(npc, level, mood);
        current = chooser.choose(snapshot, ThreadLocalRandom.current());
        Machiavillager.LOGGER.info("Machiavillager decided: {}", describe(current, level));
        act(npc, level);
    }

    /** The player the NPC should walk after, or null when the current decision is not a follow. */
    public Player followTarget(ServerLevel level) {
        return current instanceof NpcAction.Follow follow ? level.getPlayerByUUID(follow.target()) : null;
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
                Player listener = level.getPlayerByUUID(bark.listener());
                if (listener != null) {
                    String line = IDLE_LINES.pick(ThreadLocalRandom.current()).formatted(listener.getPlainTextName());
                    NpcSpeech.say(npc, line);
                }
            }
        }
    }

    private static String describe(NpcAction action, ServerLevel level) {
        return switch (action) {
            case NpcAction.Follow follow -> "Follow(" + nameOf(follow.target(), level) + ")";
            case NpcAction.Wander() -> "Wander";
            case NpcAction.Bark bark -> "Bark(" + nameOf(bark.listener(), level) + ")";
        };
    }

    private static String nameOf(UUID playerId, ServerLevel level) {
        Player player = level.getPlayerByUUID(playerId);
        return player != null ? player.getPlainTextName() : playerId.toString();
    }
}
