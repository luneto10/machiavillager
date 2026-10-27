package io.github.luneto10.machiavillager.domain.behavior;

import java.util.Objects;
import java.util.UUID;

/**
 * A player close enough to the NPC to matter for a decision. Distance is in blocks. Affinity is how much he cares
 * about this player, on the same 0 to 100 scale as {@link Relationships}.
 */
public record NearbyPlayer(UUID id, String name, double distance, int affinity) {
    public NearbyPlayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        // Written as "not >= 0" so that NaN is rejected too.
        if (!(distance >= 0)) {
            throw new IllegalArgumentException("distance must be >= 0, was " + distance);
        }
        if (affinity < Relationships.MIN || affinity > Relationships.MAX) {
            throw new IllegalArgumentException("affinity must be 0 to 100, was " + affinity);
        }
    }

    /** True when he is warm enough toward this player to talk to them unprompted. */
    public boolean isFriendly() {
        return affinity >= Relationships.MIN_AFFINITY_FOR_FRIENDLY_LINES;
    }
}
