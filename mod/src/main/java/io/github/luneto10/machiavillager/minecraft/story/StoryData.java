package io.github.luneto10.machiavillager.minecraft.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.story.Personality;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.story.StoryProgress;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The story state saved with the world. One per server: there is only one Machiavillager.
 * Holds immutable progress and relationships. A change replaces the whole object and marks the data dirty.
 */
public final class StoryData extends SavedData {
    private static final Codec<StoryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("ambition").forGetter(data -> data.progress.ambition()),
                    Codec.STRING.fieldOf("phase").forGetter(data -> data.progress.phase().name()),
                    Codec.STRING.fieldOf("highest_phase").forGetter(data -> data.progress.highestPhase().name()),
                    Codec.INT.fieldOf("independence").forGetter(data -> data.relationships.independence()),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("affinities")
                            .forGetter(data -> data.relationships.affinities()))
            .apply(instance, StoryData::restore));

    private static final SavedDataType<StoryData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Machiavillager.MOD_ID, "story"), StoryData::new, CODEC, null);

    private StoryProgress progress;
    private Relationships relationships;

    public StoryData() {
        this(StoryProgress.initial(), Relationships.initial());
    }

    private StoryData(StoryProgress progress, Relationships relationships) {
        this.progress = progress;
        this.relationships = relationships;
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

    /** Dev: sets ambition (the ratchet may keep it higher). */
    public void setAmbition(double value, Personality personality) {
        setProgress(progress.withAmbition(value, personality));
    }

    /** Dev: sets independence. */
    public void setIndependence(int value) {
        setRelationships(relationships.withIndependence(value));
    }

    private static StoryData restore(
            double ambition, String phase, String highestPhase, int independence, Map<UUID, Integer> affinities) {
        StoryProgress restoredProgress =
                StoryProgress.restored(ambition, Phase.valueOf(phase), Phase.valueOf(highestPhase));
        return new StoryData(restoredProgress, Relationships.restored(affinities, independence));
    }
}
