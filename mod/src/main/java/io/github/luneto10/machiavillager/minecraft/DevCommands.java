package io.github.luneto10.machiavillager.minecraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;

public class DevCommands {
    public DevCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(root()));
    }

    // The tree: /machiavillager <subcommand>
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("machiavillager")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(ping())
                .then(spawn());
    }

    // /machiavillager ping
    private static LiteralArgumentBuilder<CommandSourceStack> ping() {
        return Commands.literal("ping").executes(DevCommands::runPing);
    }

    private static int runPing(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("pong"), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn").executes(DevCommands::runSpawn);
    }

    private static int runSpawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ModEntities.MACHIAVILLAGER.spawn(source.getLevel(), pos, EntitySpawnReason.COMMAND);
        source.sendSuccess(() -> Component.literal("Machiavillager has arrived. Hide your emeralds."), false);
        return 1;
    }
}
