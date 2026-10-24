package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SituationTest {

    @Test
    void copiesList() {
        List<NearbyPlayer> source = new ArrayList<>(List.of(player(5)));
        Situation situation = calm(source);

        source.add(player(7));

        assertEquals(1, situation.nearbyPlayers().size());
        assertThrows(UnsupportedOperationException.class,
                () -> situation.nearbyPlayers().add(player(9)));
    }

    @Test
    void playersWithin_filtersByDistance() {
        NearbyPlayer near = player(5);
        NearbyPlayer edge = player(16);
        Situation situation = calm(List.of(near, edge, player(30)));

        assertEquals(List.of(near, edge), situation.playersWithin(16.0));
    }

    @Test
    void negativeDistance_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new NearbyPlayer(UUID.randomUUID(), "Alice", -1.0));
    }

    // Calm mood, partner phase, no hint history: the story has not started yet.
    private static Situation calm(List<NearbyPlayer> nearby) {
        return new Situation(Mood.CALM, nearby, Phase.PARTNER, 0);
    }

    private static NearbyPlayer player(double distance) {
        return new NearbyPlayer(UUID.randomUUID(), "Alice", distance);
    }
}
