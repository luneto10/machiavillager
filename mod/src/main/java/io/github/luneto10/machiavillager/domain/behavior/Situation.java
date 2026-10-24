package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of the world at decision time. Immutable.
 *
 * @param mood the NPC's current mood
 * @param nearbyPlayers the players around him, copied into an unmodifiable list
 * @param phase the story phase, which gates some options (such as hints)
 * @param ticksSinceHint active-play ticks since the last hint
 */
public record Situation(Mood mood, List<NearbyPlayer> nearbyPlayers, Phase phase, long ticksSinceHint) {
    public Situation {
        Objects.requireNonNull(mood, "mood");
        Objects.requireNonNull(nearbyPlayers, "nearbyPlayers");
        Objects.requireNonNull(phase, "phase");
        if (ticksSinceHint < 0) {
            throw new IllegalArgumentException("ticksSinceHint must not be negative: " + ticksSinceHint);
        }
        nearbyPlayers = List.copyOf(nearbyPlayers);
    }

    /** Players at most {@code radius} blocks away. The list is unmodifiable. */
    public List<NearbyPlayer> playersWithin(double radius) {
        return nearbyPlayers.stream()
                .filter(player -> player.distance() <= radius)
                .toList();
    }
}
