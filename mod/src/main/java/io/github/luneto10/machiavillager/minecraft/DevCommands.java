package io.github.luneto10.machiavillager.minecraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.story.StoryProgress;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * Developer commands under /machiavillager. Operators only.
 * <ul>
 *   <li>{@code spawn}: summon him, or bring him to you if he already exists.</li>
 *   <li>{@code brain next}: force a new decision now (dev).</li>
 *   <li>{@code brain odds}: print the odds of his next decision, one line per NPC (dev).</li>
 *   <li>{@code brain do <action>}: force follow, wander, bark or hint aimed at you (dev).</li>
 *   <li>{@code brain set ambition|independence <0-100>}: edit the saved story (dev).</li>
 *   <li>{@code brain set mood <calm|bored|irritated>}: change his mood (dev).</li>
 * </ul>
 */
public final class DevCommands {
    private static final int MIN_DEV_VALUE = 0;
    private static final int MAX_DEV_VALUE = 100;
    private static final String VALUE_ARG = "value";

    // Dev shortcuts: name -> action aimed at the player who typed the command.
    // Adding a new NpcAction? Add one line here to test it from chat.
    private static final Map<String, Function<UUID, NpcAction>> DEV_ACTIONS = Map.of(
            "follow", NpcAction.Follow::new,
            "wander", player -> new NpcAction.Wander(),
            "bark", NpcAction.Bark::new,
            "hint", NpcAction.Hint::new);

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

        // Bring the existing one instead of creating a second.
        List<MachiavillagerEntity> existing = findAll(source);
        if (!existing.isEmpty()) {
            MachiavillagerEntity npc = existing.getFirst();
            npc.teleportTo(source.getLevel(), pos.x, pos.y, pos.z, Set.of(), npc.getYRot(), npc.getXRot(), true);
            source.sendSuccess(() -> Component.literal("Machiavillager already exists. He came to you."), false);
            return 1;
        }

        // First spawn: he arrives where the command was typed.
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
        var brain = Commands.literal("brain")
                .then(Commands.literal("next").executes(DevCommands::runBrainNext))
                .then(Commands.literal("odds").executes(DevCommands::runBrainOdds));

        // One literal per table entry: "brain do follow", "brain do bark", and so on.
        var does = Commands.literal("do");
        DEV_ACTIONS.forEach((name, build) ->
                does.then(Commands.literal(name).executes(context -> runDo(context, name, build))));
        brain.then(does);

        // "set" edits the saved story values, and the mood.
        brain.then(set());
        return brain;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> set() {
        var set = Commands.literal("set")
                .then(Commands.literal("ambition").then(valueArgument(DevCommands::runSetAmbition)))
                .then(Commands.literal("independence").then(valueArgument(DevCommands::runSetIndependence)));

        // One literal per mood, named in lower case: "brain set mood calm".
        var mood = Commands.literal("mood");
        for (Mood value : Mood.values()) {
            mood.then(Commands.literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(context -> runSetMood(context, value)));
        }
        return set.then(mood);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> valueArgument(
            Command<CommandSourceStack> action) {
        return Commands.argument(VALUE_ARG, IntegerArgumentType.integer(MIN_DEV_VALUE, MAX_DEV_VALUE))
                .executes(action);
    }

    // Sets ambition in the saved story. Works even with no NPC spawned.
    private static int runSetAmbition(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        StoryData data = StoryData.get(server);
        data.setAmbition(IntegerArgumentType.getInteger(context, VALUE_ARG), StoryData.personality(server));
        StoryProgress progress = data.progress();
        String message = "Ambition is now " + (int) progress.ambition() + " (" + progress.phase() + ").";
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    // Sets independence in the saved story. Works even with no NPC spawned.
    private static int runSetIndependence(CommandContext<CommandSourceStack> context) {
        StoryData data = StoryData.get(context.getSource().getServer());
        data.setIndependence(IntegerArgumentType.getInteger(context, VALUE_ARG));
        String message = "Independence is now " + data.relationships().independence() + ".";
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
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

    // Prints each Machiavillager's odds, one chat line each, so the dice can be checked by eye.
    private static int runBrainOdds(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<MachiavillagerEntity> all = findAll(source);
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Machiavillager found."), false);
            return 0;
        }
        for (MachiavillagerEntity npc : all) {
            String line = "Odds: " + npc.describeOdds();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    // Forces the action on every Machiavillager, aimed at the player who typed the command.
    private static int runDo(
            CommandContext<CommandSourceStack> context, String name, Function<UUID, NpcAction> build)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        // Fails early from the console: only a player has a target to aim at.
        UUID playerId = source.getPlayerOrException().getUUID();
        NpcAction action = build.apply(playerId);

        // Every Machiavillager gets the same forced action.
        List<MachiavillagerEntity> all = findAll(source);
        all.forEach(npc -> npc.forceAction(action));
        int count = all.size();
        String message = count == 0
                ? "No Machiavillager found."
                : "Forced " + name + " on " + count + " Machiavillager(s).";
        source.sendSuccess(() -> Component.literal(message), false);
        return count;
    }

    // Sets his mood on every loaded Machiavillager. The next decision already uses it.
    private static int runSetMood(CommandContext<CommandSourceStack> context, Mood mood) {
        CommandSourceStack source = context.getSource();
        List<MachiavillagerEntity> all = findAll(source);
        all.forEach(npc -> npc.setMood(mood));
        int count = all.size();
        String message = count == 0
                ? "No Machiavillager found."
                : "Mood is now " + mood + " for " + count + " Machiavillager(s).";
        source.sendSuccess(() -> Component.literal(message), false);
        return count;
    }
}
