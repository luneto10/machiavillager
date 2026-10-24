package io.github.luneto10.machiavillager.minecraft.meeting;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** The on-screen warning a player gets before a summons pulls them away. Server side only. */
final class MeetingTitles {
    private static final int FADE_IN_TICKS = 0;
    private static final int STAY_TICKS = 25;
    private static final int FADE_OUT_TICKS = 5;
    private static final String TITLE = "Machiavillager is summoning you";

    private MeetingTitles() {}

    /** Shows the title, with the seconds left as the subtitle. Sent once a second. */
    static void countdown(ServerPlayer player, int secondsLeft) {
        // Timing goes first, so the title and subtitle that follow fade on the same schedule every second.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(TITLE)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(String.valueOf(secondsLeft))));
    }
}
