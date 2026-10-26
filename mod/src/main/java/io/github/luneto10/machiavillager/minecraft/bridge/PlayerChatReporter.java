package io.github.luneto10.machiavillager.minecraft.bridge;

import io.github.luneto10.machiavillager.domain.behavior.Hearing;
import io.github.luneto10.machiavillager.domain.bridge.Addressed;
import io.github.luneto10.machiavillager.domain.bridge.NpcSnapshot;
import io.github.luneto10.machiavillager.domain.bridge.PlayerChatEvent;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerLookup;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the player_chat event for a line that calls him by name, with his live state. Server thread only: it reads
 * the world and the story data.
 */
public final class PlayerChatReporter {
    // The schema allows at most this many names in the snapshot.
    static final int MAX_NEARBY_PLAYERS = 16;

    // He hears as far as his voice carries, so a line he could not hear is never sent.
    private static final double REACH_SQR = Hearing.DISTANCE * Hearing.DISTANCE;
    // Event ids are "evt_" plus 16 hex digits, so 8 random bytes.
    private static final String EVENT_PREFIX = "evt_";
    private static final int ID_BYTES = 8;

    private PlayerChatReporter() {}

    /**
     * The event for this line, or empty when he cannot hear it: he must be loaded, in the speaker's dimension,
     * and the speaker must be within reach. The addressed reason comes from the caller, which decided it.
     */
    public static Optional<PlayerChatEvent> build(
            MinecraftServer server, ServerPlayer sender, String text, Addressed addressed) {
        Optional<MachiavillagerEntity> found = MachiavillagerLookup.find(server);
        if (found.isEmpty() || !(sender.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        MachiavillagerEntity npc = found.get();
        // Distance across dimensions means nothing, so the other dimension is refused first.
        if (npc.level() != level || npc.distanceToSqr(sender) > REACH_SQR) {
            return Optional.empty();
        }

        NpcSnapshot snapshot = snapshot(server, level, npc, sender);
        return Optional.of(new PlayerChatEvent(
                newEventId(),
                Instant.now(),
                sender.getUUID(),
                sender.getPlainTextName(),
                text,
                addressed,
                Optional.empty(),
                Optional.of(snapshot)));
    }

    // Phase and independence come from the story, mood from the brain, affinity toward this speaker.
    private static NpcSnapshot snapshot(
            MinecraftServer server, ServerLevel level, MachiavillagerEntity npc, ServerPlayer sender) {
        StoryData story = StoryData.get(server);
        return new NpcSnapshot(
                story.progress().phase(),
                npc.mood(),
                story.relationships().affinityOf(sender.getUUID()),
                story.relationships().independence(),
                nearbyNames(level, npc));
    }

    // Living, non-spectator players within reach of him. The schema caps the list, so the rest are left out.
    private static List<String> nearbyNames(ServerLevel level, MachiavillagerEntity npc) {
        return level.players().stream()
                .filter(player -> !player.isSpectator() && player.isAlive())
                .filter(player -> player.distanceToSqr(npc) <= REACH_SQR)
                .limit(MAX_NEARBY_PLAYERS)
                .map(ServerPlayer::getPlainTextName)
                .toList();
    }

    // Random rather than sequential: the backend only needs ids to be unique.
    static String newEventId() {
        byte[] bytes = new byte[ID_BYTES];
        ThreadLocalRandom.current().nextBytes(bytes);
        return EVENT_PREFIX + HexFormat.of().formatHex(bytes);
    }
}
