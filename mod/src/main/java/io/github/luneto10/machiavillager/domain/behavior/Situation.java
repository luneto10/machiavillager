package io.github.luneto10.machiavillager.domain.behavior;

import java.util.List;
import java.util.Objects;

/** A snapshot of the world at decision time. Immutable. */
public record Situation(Mood mood, List<NearbyPlayer> nearbyPlayers) {
    public Situation {
        Objects.requireNonNull(mood, "mood");
        Objects.requireNonNull(nearbyPlayers, "nearbyPlayers");
        nearbyPlayers = List.copyOf(nearbyPlayers);
    }

    /** Players at most {@code radius} blocks away. The list is unmodifiable. */
    public List<NearbyPlayer> playersWithin(double radius) {
        return nearbyPlayers.stream()
                .filter(player -> player.distance() <= radius)
                .toList();
    }
}
