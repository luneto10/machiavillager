package io.github.luneto10.machiavillager.minecraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** Developer commands under /machiavillager. Operators only. */
public final class DevCommands {
    private DevCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(root()));
    }

    // The tree: /machiavillager <subcommand>
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        var root = Commands.literal("machiavillager")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(spawn());
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            root.then(brain()); // dev only: debugging shortcuts
        }
        return root;
    }

    // /machiavillager spawn
    private static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn").executes(DevCommands::runSpawn);
    }

    // There is only one Machiavillager: if he already exists, he is brought to you instead.
    private static int runSpawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 pos = source.getPosition();
        List<MachiavillagerEntity> existing = findAll(source);
        if (!existing.isEmpty()) {
            MachiavillagerEntity npc = existing.getFirst();
            npc.teleportTo(source.getLevel(), pos.x, pos.y, pos.z, Set.of(), npc.getYRot(), npc.getXRot(), true);
            source.sendSuccess(() -> Component.literal("Machiavillager already exists. He came to you."), false);
            return 1;
        }
        ModEntities.MACHIAVILLAGER.spawn(source.getLevel(), BlockPos.containing(pos), EntitySpawnReason.COMMAND);
        source.sendSuccess(() -> Component.literal("Machiavillager has arrived. Hide your emeralds."), false);
        return 1;
    }

    // Only finds him in loaded chunks.
    private static List<MachiavillagerEntity> findAll(CommandSourceStack source) {
        List<MachiavillagerEntity> found = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            found.addAll(level.getEntities(ModEntities.MACHIAVILLAGER, entity -> true));
        }
        return found;
    }

    // /machiavillager brain (dev only)
    private static LiteralArgumentBuilder<CommandSourceStack> brain() {
        return Commands.literal("brain")
                .then(Commands.literal("next").executes(DevCommands::runBrainNext));
    }

    // Forces every Machiavillager in every loaded level to decide on the next tick.
    private static int runBrainNext(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<MachiavillagerEntity> all = findAll(source);
        all.forEach(MachiavillagerEntity::forceDecision);
        int count = all.size();
        String message = count == 0
                ? "No Machiavillager found."
                : "Forced a new decision for " + count + " Machiavillager(s).";
        source.sendSuccess(() -> Component.literal(message), false);
        return count;
    }
}
