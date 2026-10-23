package io.github.luneto10.machiavillager.minecraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
                .then(ping());
        // .then(spawn())   <- Checkpoint 2: one line per new subcommand
    }

    // /machiavillager ping
    private static LiteralArgumentBuilder<CommandSourceStack> ping() {
        return Commands.literal("ping").executes(DevCommands::runPing);
    }

    private static int runPing(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("pong"), false);
        return 1;
    }
}
