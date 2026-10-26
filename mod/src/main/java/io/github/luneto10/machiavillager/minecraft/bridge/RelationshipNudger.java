package io.github.luneto10.machiavillager.minecraft.bridge;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies one suggested affinity change to the saved story. The limits live in {@link RelationshipNudges}; this
 * class only checks the player is online and writes the result into {@link StoryData}, the same place the brain
 * writes its hits. Server thread only.
 */
public final class RelationshipNudger {
    /** Refused when the suggested player is not on the server right now. */
    public static final String UNKNOWN_PLAYER = "unknown_player";

    private final MinecraftServer server;
    // One policy per server session, so a restart starts with no recorded nudges.
    private final RelationshipNudges policy = new RelationshipNudges();

    /** Creates the nudger for one server session. */
    public RelationshipNudger(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Applies a suggested change, or refuses it with a reason. The reason is logged, never the chat text.
     * Returns the policy's result, or {@link UNKNOWN_PLAYER} when the player is not online.
     */
    public RelationshipNudges.Result suggest(UUID player, int delta, String reason) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) {
            return new RelationshipNudges.Rejected(UNKNOWN_PLAYER);
        }

        RelationshipNudges.Result result = policy.tryNudge(player, delta, server.getTickCount());
        if (result instanceof RelationshipNudges.Applied applied) {
            // Written through the story, so the brain's overlay and the next save both see the new value.
            StoryData data = StoryData.get(server);
            int current = data.relationships().affinityOf(player);
            data.setRelationships(data.relationships().withAffinity(player, current + applied.delta()));
            Machiavillager.LOGGER.info("relationship nudge player={} applied={} provenance=claimed reason={}",
                    online.getPlainTextName(), applied.delta(), reason);
        } else if (result instanceof RelationshipNudges.Rejected rejected) {
            Machiavillager.LOGGER.info("relationship nudge player={} rejected={}",
                    online.getPlainTextName(), rejected.reason());
        }
        return result;
    }
}
