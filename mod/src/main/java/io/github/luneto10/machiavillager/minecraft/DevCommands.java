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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
 * Commands under /machiavillager. Spawn and brain are operators only.
 * <ul>
 *   <li>{@code summons on|off}: let him summon you, or not. Open to every player.</li>
 *   <li>{@code spawn}: summon him, or bring him to you if he already exists.</li>
 *   <li>{@code brain next}: force a new decision now (dev).</li>
 *   <li>{@code brain odds}: print the odds of his next decision, one line per NPC (dev).</li>
 *   <li>{@code brain do <action>}: force follow, wander, bark, hint or summon aimed at you (dev).</li>
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
            "hint", NpcAction.Hint::new,
            "summon", NpcAction.Summon::new);

    // Operators only: the spawn and brain subtrees check this themselves, so the root stays open to everyone.
    private static final Predicate<CommandSourceStack> OPERATOR = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private DevCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(root()));
    }

    // The tree: /machiavillager <subcommand>
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        var root = Commands.literal("machiavillager")
                .then(summons())
                .then(spawn());
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            root.then(brain()); // dev only: debugging shortcuts
        }
        return root;
    }

    // /machiavillager spawn
    private static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn").requires(OPERATOR).executes(DevCommands::runSpawn);
    }

    // /machiavillager summons on|off: every player may turn his summons off or on for themselves.
    private static LiteralArgumentBuilder<CommandSourceStack> summons() {
        return Commands.literal("summons")
                .then(Commands.literal("off").executes(context -> runSummons(context, true)))
                .then(Commands.literal("on").executes(context -> runSummons(context, false)));
    }

    // Saves the choice for the player who typed it; the brain checks it before any summons.
    private static int runSummons(CommandContext<CommandSourceStack> context, boolean optOut)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID playerId = source.getPlayerOrException().getUUID();
        StoryData.get(source.getServer()).setOptedOut(playerId, optOut);
        String message = optOut ? "He will not summon you anymore." : "He may summon you again.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
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

    // Runs the same dev action on every loaded Machiavillager and reports how many were affected.
    private static int forEachNpc(
            CommandSourceStack source, Consumer<MachiavillagerEntity> action, String doneMessage) {
        List<MachiavillagerEntity> all = findAll(source);
        all.forEach(action);

        // With no NPC loaded, report that instead of a zero count.
        int count = all.size();
        String message = count == 0 ? "No Machiavillager found." : doneMessage.formatted(count);
        source.sendSuccess(() -> Component.literal(message), false);
        return count;
    }

    // /machiavillager brain (dev only)
    private static LiteralArgumentBuilder<CommandSourceStack> brain() {
        var brain = Commands.literal("brain")
                .requires(OPERATOR)
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
        return forEachNpc(context.getSource(), MachiavillagerEntity::forceDecision,
                "Forced a new decision for %d Machiavillager(s).");
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
        return forEachNpc(source, npc -> npc.forceAction(action),
                "Forced " + name + " on %d Machiavillager(s).");
    }

    // Sets his mood on every loaded Machiavillager. The next decision already uses it.
    private static int runSetMood(CommandContext<CommandSourceStack> context, Mood mood) {
        return forEachNpc(context.getSource(), npc -> npc.setMood(mood),
                "Mood is now " + mood + " for %d Machiavillager(s).");
    }
}
