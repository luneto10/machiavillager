package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

/** Dev only: shows what the brain is doing in the action bar. Does nothing in a normal game. */
final class BrainDebugOverlay {
    private static final boolean ENABLED = FabricLoader.getInstance().isDevelopmentEnvironment();
    private static final double SHOW_RADIUS = 32.0;
    private static final double SHOW_RADIUS_SQR = SHOW_RADIUS * SHOW_RADIUS;
    // The action bar fades after about 2 s, so it is refreshed twice per second.
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;

    private BrainDebugOverlay() {}

    /** Sends the brain state to every player within range of the NPC. */
    static void show(
            PathfinderMob npc, ServerLevel level, Supplier<String> actionText, Mood mood, int ticksUntilNextDecision) {
        if (!ENABLED || npc.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        int seconds = (int) Math.ceil(Math.max(ticksUntilNextDecision, 0) / (double) TICKS_PER_SECOND);
        String text = "[brain] " + actionText.get() + " | mood " + mood + " | next decision in " + seconds + "s";
        Component line = Component.literal(text);
        for (var player : level.players()) {
            if (player.distanceToSqr(npc) <= SHOW_RADIUS_SQR) {
                player.sendOverlayMessage(line);
            }
        }
    }
}
