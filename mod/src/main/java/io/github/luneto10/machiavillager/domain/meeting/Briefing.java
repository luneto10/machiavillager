package io.github.luneto10.machiavillager.domain.meeting;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.Objects;

/**
 * What he knows when a summoned meeting starts. Immutable.
 *
 * @param phase the story phase; the status topic depends on it
 * @param knowsVillage whether he has found a village to talk about
 * @param affinity how much he likes the player
 * @param independence how much he needs nobody
 */
public record Briefing(Phase phase, boolean knowsVillage, int affinity, int independence) {
    public Briefing {
        Objects.requireNonNull(phase, "phase");
    }
}
