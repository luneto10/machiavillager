package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.minecraft.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Finds the Machiavillager in the running server. Server thread only. */
public final class MachiavillagerLookup {

    private MachiavillagerLookup() {}

    /** Every Machiavillager in a loaded chunk, across all levels. Unloaded ones are not found. */
    public static List<MachiavillagerEntity> findAll(MinecraftServer server) {
        List<MachiavillagerEntity> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            found.addAll(level.getEntities(ModEntities.MACHIAVILLAGER, entity -> true));
        }
        return found;
    }

    /** The first loaded Machiavillager, or empty when none is loaded. There is only one in practice. */
    public static Optional<MachiavillagerEntity> find(MinecraftServer server) {
        List<MachiavillagerEntity> all = findAll(server);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }
}
