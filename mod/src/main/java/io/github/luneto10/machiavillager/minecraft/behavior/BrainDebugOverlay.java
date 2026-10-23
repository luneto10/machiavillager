package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;

/** Dev only: shows what the brain is doing in the action bar. Each player sees their own relationship values. Does nothing in a normal game. */
final class BrainDebugOverlay {
    private static final boolean ENABLED = FabricLoader.getInstance().isDevelopmentEnvironment();
    // The action bar fades after about 2 s, so it is refreshed twice per second.
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final String CARES_MARK = " ♥";

    private BrainDebugOverlay() {}

    /** Sends the shared brain state, plus each player's own affinity, to every player in the level. */
    static void show(
            PathfinderMob npc,
            ServerLevel level,
            Supplier<String> actionText,
            Mood mood,
            int ticksUntilNextDecision,
            Relationships relationships) {
        if (!ENABLED || npc.tickCount % REFRESH_INTERVAL_TICKS != 0 || level.players().isEmpty()) {
            return;
        }
        int seconds = (int) Math.ceil(Math.max(ticksUntilNextDecision, 0) / (double) TICKS_PER_SECOND);
        // Zero-padded so the centered text keeps the same width while the timer counts down.
        String shared = "[brain] " + actionText.get() + " | " + mood + " | next " + String.format("%02d", seconds) + "s";
        for (Player player : level.players()) {
            player.sendOverlayMessage(Component.literal(shared + ownPart(player, relationships)));
        }
    }

    private static String ownPart(Player player, Relationships relationships) {
        UUID playerId = player.getUUID();
        String part = " | you: affinity " + relationships.affinityOf(playerId)
                + ", independence " + relationships.independence();
        return relationships.caresAbout(playerId) ? part + CARES_MARK : part;
    }
}
