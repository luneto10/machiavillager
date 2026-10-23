package io.github.luneto10.machiavillager.domain.behavior;

import java.util.Objects;
import java.util.UUID;

/** A player close enough to the NPC to matter for a decision. Distance is in blocks. */
public record NearbyPlayer(UUID id, String name, double distance) {
    public NearbyPlayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        // Written as "not >= 0" so that NaN is rejected too.
        if (!(distance >= 0)) {
            throw new IllegalArgumentException("distance must be >= 0, was " + distance);
        }
    }
}
