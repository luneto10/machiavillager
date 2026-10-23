package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NearbyPlayer;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/** Builds a domain {@link Situation} from the live world. Reads Minecraft, returns plain data. */
public final class SituationSnapshot {
    private static final double SNAPSHOT_RADIUS = 32.0;

    private SituationSnapshot() {}

    /** The living, non-spectator players within the snapshot radius of the NPC. Server side only. */
    public static Situation take(PathfinderMob npc, ServerLevel level, Mood mood) {
        List<NearbyPlayer> nearby = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double distance = npc.distanceTo(player);
            if (distance <= SNAPSHOT_RADIUS) {
                nearby.add(new NearbyPlayer(player.getUUID(), player.getPlainTextName(), distance));
            }
        }
        return new Situation(mood, nearby);
    }
}
