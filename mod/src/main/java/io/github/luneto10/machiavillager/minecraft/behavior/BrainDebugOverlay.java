package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.story.StoryProgress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Dev only: shows what the brain is doing as boss bars at the top of the screen. Three bars: the decision
 * countdown and the story (shared by everyone), and one relationship bar per player. Does nothing in a normal
 * game. Owned by one brain, so each Machiavillager has its own bars.
 */
final class BrainDebugOverlay {
    private static final boolean ENABLED = FabricLoader.getInstance().isDevelopmentEnvironment();
    // Boss bars are cheap, but there is no need to rebuild them every tick.
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final String CARES_MARK = " ♥";
    private static final int AMBITION_SCALE = 100;
    private static final String SEPARATOR = " · ";

    private final ServerBossEvent decisionBar = newBar(BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent storyBar =
            newBar(BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private final Map<UUID, ServerBossEvent> relationshipBars = new HashMap<>();

    /** Updates the bars and their viewers. Only refreshes every few ticks, and only in dev. */
    void show(
            PathfinderMob npc,
            ServerLevel level,
            Supplier<String> actionText,
            Mood mood,
            int ticksUntilNextDecision,
            int maxDecisionTicks,
            StoryProgress progress,
            Relationships relationships) {
        // Dev only, and only on the refresh tick.
        if (!ENABLED || npc.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }

        // The shared bars come first: the decision countdown and the story.
        updateDecisionBar(actionText.get(), mood, ticksUntilNextDecision, maxDecisionTicks);
        updateStoryBar(progress);

        // Sync who sees the shared bars: new players join, players who left are removed.
        syncViewers(level);

        // Each player sees their own relationship bar, so affinity is never shown to the wrong person.
        for (ServerPlayer player : level.players()) {
            updateRelationshipBar(player, relationships);
        }
    }

    /** Removes every bar from every viewer. Called when he is removed from the world. */
    void hideAll() {
        decisionBar.removeAllPlayers();
        storyBar.removeAllPlayers();
        relationshipBars.values().forEach(ServerBossEvent::removeAllPlayers);
        relationshipBars.clear();
    }

    private void updateDecisionBar(String action, Mood mood, int ticksUntilNextDecision, int maxDecisionTicks) {
        int seconds = (int) Math.ceil(Math.max(ticksUntilNextDecision, 0) / (double) TICKS_PER_SECOND);
        // Zero-padded so the text keeps the same width while the timer counts down.
        decisionBar.setName(Component.literal(
                action + SEPARATOR + mood + SEPARATOR + "next " + String.format("%02d", seconds) + "s"));
        decisionBar.setProgress(Math.clamp((float) ticksUntilNextDecision / maxDecisionTicks, 0.0f, 1.0f));
    }

    private void updateStoryBar(StoryProgress progress) {
        // The story bar is colored by phase, so the danger of the story is visible at a glance.
        storyBar.setName(Component.literal(
                progress.phase() + SEPARATOR + "ambition " + (int) progress.ambition()));
        storyBar.setProgress((float) (progress.ambition() / AMBITION_SCALE));
        storyBar.setColor(colorOf(progress.phase()));
    }

    // Exhaustive on purpose: a new phase must pick a bar color before this compiles.
    private static BossEvent.BossBarColor colorOf(Phase phase) {
        return switch (phase) {
            case PARTNER -> BossEvent.BossBarColor.GREEN;
            case SEEDS -> BossEvent.BossBarColor.YELLOW;
            case PROPOSAL -> BossEvent.BossBarColor.PURPLE;
            case REGIME, WAR -> BossEvent.BossBarColor.RED;
        };
    }

    // Adds every player in the level to the shared bars, and removes whoever has left.
    private void syncViewers(ServerLevel level) {
        Set<ServerPlayer> present = new HashSet<>(level.players());
        for (ServerPlayer player : present) {
            decisionBar.addPlayer(player);
            storyBar.addPlayer(player);
        }
        removeAbsent(decisionBar, present);
        removeAbsent(storyBar, present);

        // A relationship bar belongs to one player: drop it once that player is gone.
        Set<UUID> presentIds = new HashSet<>();
        present.forEach(player -> presentIds.add(player.getUUID()));
        relationshipBars.entrySet().removeIf(entry -> {
            if (presentIds.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().removeAllPlayers();
            return true;
        });
    }

    // Iterates a copy: removing a viewer while looping over the live collection would break the loop.
    private static void removeAbsent(ServerBossEvent bar, Set<ServerPlayer> present) {
        List<ServerPlayer> viewers = new ArrayList<>(bar.getPlayers());
        for (ServerPlayer viewer : viewers) {
            if (!present.contains(viewer)) {
                bar.removePlayer(viewer);
            }
        }
    }

    private void updateRelationshipBar(ServerPlayer player, Relationships relationships) {
        UUID playerId = player.getUUID();
        ServerBossEvent bar = relationshipBars.computeIfAbsent(
                playerId, id -> newBar(BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10));
        bar.addPlayer(player);

        int affinity = relationships.affinityOf(playerId);
        boolean cares = relationships.caresAbout(playerId);
        String heart = cares ? CARES_MARK : "";
        bar.setName(Component.literal("You" + SEPARATOR + "affinity " + affinity
                + SEPARATOR + "independence " + relationships.independence() + heart));
        bar.setProgress((float) affinity / Relationships.MAX);
        bar.setColor(cares ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.BLUE);
    }

    private static ServerBossEvent newBar(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        return new ServerBossEvent(UUID.randomUUID(), Component.empty(), color, overlay);
    }
}
