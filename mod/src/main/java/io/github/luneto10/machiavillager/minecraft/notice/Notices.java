package io.github.luneto10.machiavillager.minecraft.notice;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** Sends notices to players. Players with the mod get the styled card; the rest get short vanilla titles. */
public final class Notices {
    private static final int FADE_IN_TICKS = 0;
    private static final int FADE_OUT_TICKS = 5;
    private static final boolean RESET_TIMES = true;

    private Notices() {}

    /** Registers the payload type. Call once from the common entrypoint, before any player joins. */
    public static void register() {
        // Must be registered on both ends before the server can send it.
        PayloadTypeRegistry.clientboundPlay().register(NoticePayload.TYPE, NoticePayload.CODEC);
    }

    /** Shows a notice to one player. */
    public static void show(ServerPlayer player, NoticePayload notice) {
        // A player with the mod gets the full card; the client does the drawing.
        if (ServerPlayNetworking.canSend(player, NoticePayload.TYPE)) {
            ServerPlayNetworking.send(player, notice);
            return;
        }
        showVanilla(player, notice);
    }

    private static void showVanilla(ServerPlayer player, NoticePayload notice) {
        // Hiding in vanilla is a clear, not a zero-length title.
        if (notice.durationTicks() == NoticePayload.HIDE_DURATION_TICKS) {
            player.connection.send(new ClientboundClearTitlesPacket(RESET_TIMES));
            return;
        }

        // Vanilla titles overflow the screen when long, so only the title and a short body are sent.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                FADE_IN_TICKS, notice.durationTicks(), FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(notice.title())));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(notice.body())));
    }
}
