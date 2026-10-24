package io.github.luneto10.machiavillager.minecraft.behavior;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NearbyPlayer;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/** Builds a domain {@link Situation} from the live world. Reads Minecraft, returns plain data. */
public final class SituationSnapshot {
    private SituationSnapshot() {}

    /**
     * Every living non-spectator player in the NPC's level, with their distance. The domain options filter by
     * distance themselves. {@code summonable} says which of them may be summoned right now. Server side only.
     */
    public static Situation take(
            PathfinderMob npc,
            ServerLevel level,
            Mood mood,
            Phase phase,
            long ticksSinceHint,
            Predicate<ServerPlayer> summonable) {
        // Far players are kept too: a summons pulls exactly these players in.
        List<NearbyPlayer> nearby = new ArrayList<>();
        Set<UUID> summonableIds = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            nearby.add(new NearbyPlayer(player.getUUID(), player.getPlainTextName(), npc.distanceTo(player)));

            // The domain only ever sees ids, never Minecraft player objects.
            if (summonable.test(player)) {
                summonableIds.add(player.getUUID());
            }
        }
        return new Situation(mood, nearby, phase, ticksSinceHint, summonableIds);
    }
}
