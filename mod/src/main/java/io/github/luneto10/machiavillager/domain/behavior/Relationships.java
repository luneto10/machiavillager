package io.github.luneto10.machiavillager.domain.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * How much the NPC cares about each player and how independent he is. Values are clamped to [0, 100].
 * Immutable: every change returns a new object. Affinity becomes real in Checkpoint 7 (memory), independence
 * in #32 (ambition).
 */
public final class Relationships {
    public static final int MIN = 0;
    public static final int MAX = 100;
    /** A stranger starts neutral. */
    public static final int DEFAULT_AFFINITY = 50;
    private static final int NO_INDEPENDENCE = 0;

    private final Map<UUID, Integer> affinities;
    private final int independence;

    private Relationships(Map<UUID, Integer> affinities, int independence) {
        this.affinities = Map.copyOf(affinities);
        this.independence = independence;
    }

    /** No players known yet, independence 0. */
    public static Relationships initial() {
        return new Relationships(Map.of(), NO_INDEPENDENCE);
    }

    /** Affinity toward the player, or {@link #DEFAULT_AFFINITY} when unknown. */
    public int affinityOf(UUID player) {
        return affinities.getOrDefault(Objects.requireNonNull(player, "player"), DEFAULT_AFFINITY);
    }

    /** All known affinities, immutable. */
    public Map<UUID, Integer> affinities() {
        return affinities;
    }

    public int independence() {
        return independence;
    }

    /** Rebuilds a saved state. Values are clamped to [0, 100], like the other methods. */
    public static Relationships restored(Map<UUID, Integer> affinities, int independence) {
        Map<UUID, Integer> clamped = new HashMap<>();
        affinities.forEach((player, value) ->
                clamped.put(Objects.requireNonNull(player, "player"), clamp(value)));
        return new Relationships(clamped, clamp(independence));
    }

    /** Returns a new object with the player's affinity set, clamped to [0, 100]. */
    public Relationships withAffinity(UUID player, int value) {
        Map<UUID, Integer> updated = new HashMap<>(affinities);
        updated.put(Objects.requireNonNull(player, "player"), clamp(value));
        return new Relationships(updated, independence);
    }

    /** Returns a new object with independence set, clamped to [0, 100]. */
    public Relationships withIndependence(int value) {
        return new Relationships(affinities, clamp(value));
    }

    /** Companion behavior toward this player is allowed only while affinity > independence. */
    public boolean caresAbout(UUID player) {
        return affinityOf(player) > independence;
    }

    private static int clamp(int value) {
        return Math.clamp(value, MIN, MAX);
    }
}
