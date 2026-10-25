package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A snapshot of the world at decision time. Immutable.
 *
 * @param mood the NPC's current mood
 * @param nearbyPlayers the players around him, copied into an unmodifiable list
 * @param phase the story phase, which gates some options (such as hints)
 * @param ticksSinceHint active-play ticks since the last hint
 * @param summonable players who may be summoned right now, copied into an unmodifiable set
 * @param caredFor players he cares about (affinity above independence), copied into an unmodifiable set
 */
public record Situation(
        Mood mood,
        List<NearbyPlayer> nearbyPlayers,
        Phase phase,
        long ticksSinceHint,
        Set<UUID> summonable,
        Set<UUID> caredFor) {
    public Situation {
        Objects.requireNonNull(mood, "mood");
        Objects.requireNonNull(nearbyPlayers, "nearbyPlayers");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(summonable, "summonable");
        Objects.requireNonNull(caredFor, "caredFor");
        if (ticksSinceHint < 0) {
            throw new IllegalArgumentException("ticksSinceHint must not be negative: " + ticksSinceHint);
        }
        nearbyPlayers = List.copyOf(nearbyPlayers);
        summonable = Set.copyOf(summonable);
        caredFor = Set.copyOf(caredFor);
    }

    /** Players at most {@code radius} blocks away. The list is unmodifiable. */
    public List<NearbyPlayer> playersWithin(double radius) {
        return nearbyPlayers.stream()
                .filter(player -> player.distance() <= radius)
                .toList();
    }

    /** Whether this player may be summoned right now. */
    public boolean isSummonable(UUID player) {
        // The set is immutable, so a plain lookup is enough.
        return summonable.contains(player);
    }

    /** Whether he cares about this player, which lets him go looking for them from far away. */
    public boolean caresFor(UUID player) {
        return caredFor.contains(player);
    }
}
