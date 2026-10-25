package io.github.luneto10.machiavillager.minecraft.bridge;

import io.github.luneto10.machiavillager.domain.behavior.Hearing;
import io.github.luneto10.machiavillager.domain.speech.SpeechQueue;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import net.minecraft.server.level.ServerPlayer;

/** One speech in progress: who speaks, what is left to say, and whether a player's chat cut it short. Server thread only. */
final class ActiveSpeech {
    // Same radius as his voice, so a player who can hear him can also interrupt him.
    private static final double HEARING_RADIUS_SQR = Hearing.DISTANCE * Hearing.DISTANCE;

    private final String requestId;
    private final MachiavillagerEntity npc;
    private final SpeechQueue queue;
    private boolean interrupted;

    ActiveSpeech(String requestId, MachiavillagerEntity npc, SpeechQueue queue) {
        this.requestId = requestId;
        this.npc = npc;
        this.queue = queue;
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

    boolean interrupted() {
        return interrupted;
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
