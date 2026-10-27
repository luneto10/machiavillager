package io.github.luneto10.machiavillager.domain.bridge;

import io.github.luneto10.machiavillager.domain.behavior.ComeAnswer;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Objects;

/**
 * His state when a player spoke, so the backend can answer in character. Limits are enforced by the schema.
 *
 * @param phase which stage of his plan he is in
 * @param mood how he feels right now
 * @param affinity how much he likes the speaker, 0 to 100
 * @param independence how far he has broken away from his partner role, 0 to 100
 * @param nearbyPlayers names of the players within reach of him
 * @param dimension the dimension he is in, as a namespaced id such as minecraft:the_nether
 * @param x his block x coordinate
 * @param y his block y coordinate
 * @param z his block z coordinate
 * @param ifAskedToCome what he does if the speaker asks him to come
 */
public record NpcSnapshot(
        Phase phase,
        Mood mood,
        int affinity,
        int independence,
        List<String> nearbyPlayers,
        String dimension,
        int x,
        int y,
        int z,
        ComeAnswer ifAskedToCome) {

    /** Null checks only. The list is copied, so the record cannot change after it is built. */
    public NpcSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(mood, "mood");
        Objects.requireNonNull(nearbyPlayers, "nearby_players");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(ifAskedToCome, "if_asked_to_come");
        nearbyPlayers = List.copyOf(nearbyPlayers);
    }
}
