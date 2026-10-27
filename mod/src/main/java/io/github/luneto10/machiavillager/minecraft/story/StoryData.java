package io.github.luneto10.machiavillager.minecraft.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.lines.LineKey;
import io.github.luneto10.machiavillager.domain.lines.SavedLinePools;
import io.github.luneto10.machiavillager.domain.story.Personality;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.story.StoryProgress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The story state saved with the world. One per server: there is only one Machiavillager.
 * Holds immutable progress, relationships and the summons opt-outs. A change replaces the whole value and marks
 * the data dirty.
 */
public final class StoryData extends SavedData {
    private static final Codec<StoryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("ambition").forGetter(data -> data.progress.ambition()),
                    Codec.STRING.fieldOf("phase").forGetter(data -> data.progress.phase().name()),
                    Codec.STRING.fieldOf("highest_phase").forGetter(data -> data.progress.highestPhase().name()),
                    Codec.INT.fieldOf("independence").forGetter(data -> data.relationships.independence()),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("affinities")
                            .forGetter(data -> data.relationships.affinities()),
                    // Optional, so saves from before summons still load: nobody has opted out yet.
                    Codec.list(UUIDUtil.STRING_CODEC).optionalFieldOf("summon_opt_out", List.of())
                            .forGetter(data -> List.copyOf(data.summonOptOut)),
                    // Optional, so saves from before the AI wrote lines still load: no pools yet.
                    Codec.unboundedMap(Codec.STRING, Codec.list(Codec.STRING)).optionalFieldOf("line_pools", Map.of())
                            .forGetter(data -> idKeyed(data.linePools)))
            .apply(instance, StoryData::restore));

    private static final SavedDataType<StoryData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Machiavillager.MOD_ID, "story"), StoryData::new, CODEC, null);

    private StoryProgress progress;
    private Relationships relationships;
    private Set<UUID> summonOptOut;
    private Map<LineKey, List<String>> linePools;

    public StoryData() {
        this(StoryProgress.initial(), Relationships.initial(), Set.of(), Map.of());
    }

    private StoryData(
            StoryProgress progress,
            Relationships relationships,
            Set<UUID> summonOptOut,
            Map<LineKey, List<String>> linePools) {
        this.progress = progress;
        this.relationships = relationships;
        this.summonOptOut = summonOptOut;
        this.linePools = linePools;
    }

    /** Loads the story of this server, or creates a fresh one the first time. */
    public static StoryData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The world's personality, derived from the world seed. It is never saved: the same seed always rolls the
     * same personality, so it is the same after every restart. Cheap enough to rebuild on demand.
     */
    public static Personality personality(MinecraftServer server) {
        return Personality.rolled(new Random(server.overworld().getSeed()));
    }

    public StoryProgress progress() {
        return progress;
    }

    public Relationships relationships() {
        return relationships;
    }

    public void setProgress(StoryProgress progress) {
        this.progress = progress;
        setDirty();
    }

    public void setRelationships(Relationships relationships) {
        this.relationships = relationships;
        setDirty();
    }

    /** The saved line pools, one per key. Read-only: change them with {@link #setLinePools}. */
    public Map<LineKey, List<String>> linePools() {
        return linePools;
    }

    /** Replaces every saved line pool with an immutable copy, so later changes to the caller's map do not reach it. */
    public void setLinePools(Map<LineKey, List<String>> pools) {
        linePools = Map.copyOf(pools);
        setDirty();
    }

    /** Whether this player has turned summons off. */
    public boolean isOptedOut(UUID player) {
        return summonOptOut.contains(player);
    }

    /** Turns this player's summons off or on. Replaces the set, so the saved value is never edited in place. */
    public void setOptedOut(UUID player, boolean optedOut) {
        // Copy, change the copy, then swap it in: the old set stays exactly as it was.
        Set<UUID> updated = new HashSet<>(summonOptOut);
        if (optedOut) {
            updated.add(player);
        } else {
            updated.remove(player);
        }
        summonOptOut = Set.copyOf(updated);
        setDirty();
    }

    /** Dev: sets ambition (the ratchet may keep it higher). */
    public void setAmbition(double value, Personality personality) {
        setProgress(progress.withAmbition(value, personality));
    }

    /** Dev: puts him straight in a phase, and resets the ratchet to it. */
    public void setPhase(Phase phase, Personality personality) {
        setProgress(StoryProgress.atPhase(phase, personality));
    }

    /** Dev: sets independence. */
    public void setIndependence(int value) {
        setRelationships(relationships.withIndependence(value));
    }

    private static StoryData restore(
            double ambition,
            String phase,
            String highestPhase,
            int independence,
            Map<UUID, Integer> affinities,
            List<UUID> summonOptOut,
            Map<String, List<String>> linePools) {
        StoryProgress restoredProgress =
                StoryProgress.restored(ambition, Phase.valueOf(phase), Phase.valueOf(highestPhase));
        return new StoryData(
                restoredProgress,
                Relationships.restored(affinities, independence),
                Set.copyOf(summonOptOut),
                parsePools(linePools));
    }

    // Validated by the domain: unknown ids and invalid lines are dropped, so an edited save cannot break the bank.
    private static Map<LineKey, List<String>> parsePools(Map<String, List<String>> saved) {
        return SavedLinePools.parse(saved);
    }

    // The codec stores the pools under their text ids, such as "hint:seeds:bored".
    private static Map<String, List<String>> idKeyed(Map<LineKey, List<String>> pools) {
        Map<String, List<String>> byId = new HashMap<>();
        pools.forEach((key, lines) -> byId.put(key.id(), lines));
        return byId;
    }
}
