package io.github.luneto10.machiavillager.minecraft.bridge;

import io.github.luneto10.machiavillager.domain.behavior.Hearing;
import io.github.luneto10.machiavillager.domain.speech.SpeechQueue;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One speech in progress: who speaks, what is left to say, who (if anyone) it is private to, and whether a
 * player's chat cut it short. Server thread only.
 */
final class ActiveSpeech {
    // Same radius as his voice, so a player who can hear him can also interrupt him.
    private static final double HEARING_RADIUS_SQR = Hearing.DISTANCE * Hearing.DISTANCE;

    private final String requestId;
    private final MachiavillagerEntity npc;
    private final SpeechQueue queue;
    // Present only for private speech: the lines go to this player alone.
    private final Optional<ServerPlayer> recipient;
    private boolean interrupted;

    ActiveSpeech(String requestId, MachiavillagerEntity npc, SpeechQueue queue, Optional<ServerPlayer> recipient) {
        this.requestId = requestId;
        this.npc = npc;
        this.queue = queue;
        this.recipient = recipient;
    }

    String requestId() {
        return requestId;
    }

    MachiavillagerEntity npc() {
        return npc;
    }

    SpeechQueue queue() {
        return queue;
    }

    Optional<ServerPlayer> recipient() {
        return recipient;
    }

    boolean interrupted() {
        return interrupted;
    }

    /** Private speech is not voice, so nearby chat never cuts it short. */
    boolean interruptible() {
        return recipient.isEmpty();
    }

    /** True when the recipient has logged out or reconnected: the same player object is no longer online. */
    boolean recipientLeft(MinecraftServer server) {
        return recipient.map(player -> server.getPlayerList().getPlayer(player.getUUID()) != player).orElse(false);
    }

    /** True when the player stands in the same level and within his hearing range. Farther players never interrupt. */
    boolean hears(ServerPlayer player) {
        return player.level() == npc.level() && player.distanceToSqr(npc) <= HEARING_RADIUS_SQR;
    }

    /** Drops the lines not yet said. The line on screen is still read, then the speech ends as interrupted. */
    void interrupt() {
        interrupted = true;
        queue.interrupt();
    }
}
