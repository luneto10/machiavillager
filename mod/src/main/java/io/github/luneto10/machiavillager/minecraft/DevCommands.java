package io.github.luneto10.machiavillager.minecraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.luneto10.machiavillager.bridge.BridgeClient;
import io.github.luneto10.machiavillager.bridge.BridgeStatusText;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges;
import io.github.luneto10.machiavillager.domain.bridge.BridgeState;
import io.github.luneto10.machiavillager.domain.bridge.DevModelEvent;
import io.github.luneto10.machiavillager.domain.bridge.DevModelOp;
import io.github.luneto10.machiavillager.domain.bridge.EventIds;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.story.StoryProgress;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import io.github.luneto10.machiavillager.minecraft.bridge.RelationshipNudger;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerLookup;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * Commands under /machiavillager. Spawn and brain are operators only.
 * <ul>
 *   <li>{@code bridge status}: show the backend link state (operators only).</li>
 *   <li>{@code summons on|off}: let him summon you, or not. Open to every player.</li>
 *   <li>{@code spawn}: summon him, or bring him to you if he already exists.</li>
 *   <li>{@code brain next}: force a new decision now (dev).</li>
 *   <li>{@code brain odds}: print the odds of his next decision, one line per NPC (dev).</li>
 *   <li>{@code brain nudge <-10..10>}: apply an affinity nudge for you, through the same limits as the AI (dev).</li>
 *   <li>{@code brain do <action>}: force follow, wander, bark, hint, summon or mutter aimed at you (dev).</li>
 *   <li>{@code brain set ambition|independence <0-100>}: edit the saved story (dev).</li>
 *   <li>{@code brain set phase <partner|seeds|proposal|regime|war>}: put him in a phase, resetting the ratchet (dev).</li>
 *   <li>{@code brain set mood <calm|bored|irritated>}: change his mood (dev).</li>
 *   <li>{@code ai models}: list the models the backend offered in its welcome (dev, operators).</li>
 *   <li>{@code ai model <name>}: ask the backend to answer with this model; tab completes the list (dev, operators).</li>
 *   <li>{@code ai model reset}: go back to the backend's default model (dev, operators).</li>
 * </ul>
 */
public final class DevCommands {
    private static final int MIN_DEV_VALUE = 0;
    private static final int MAX_DEV_VALUE = 100;
    private static final String VALUE_ARG = "value";
    private static final String NUDGE_ARG = "affinity_delta";
    // Dev nudges stay inside the range the policy accepts, so a typo cannot ask for more than a suggestion could.
    private static final int MIN_NUDGE = -10;
    private static final int MAX_NUDGE = 10;
    private static final String DEV_NUDGE_REASON = "dev command";
    private static final String MODEL_ARG = "name";
    private static final String NO_MODELS = "No model list: the backend is not connected or not in dev mode.";
    private static final String NOT_CONNECTED = "Bridge not connected.";
    private static final String NO_MODEL_NAME = "No model name given.";
    private static final String RESET_FEEDBACK = "Model reset requested.";

    // Dev shortcuts: name -> action aimed at the player who typed the command.
    // Adding a new NpcAction? Add one line here to test it from chat.
    private static final Map<String, Function<UUID, NpcAction>> DEV_ACTIONS = Map.of(
            "follow", NpcAction.Follow::new,
            "wander", player -> new NpcAction.Wander(),
            "bark", NpcAction.Bark::new,
            "hint", NpcAction.Hint::new,
            "summon", NpcAction.Summon::new,
            "mutter", player -> new NpcAction.Mutter());

    // Operators only: the spawn and brain subtrees check this themselves, so the root stays open to everyone.
    private static final Predicate<CommandSourceStack> OPERATOR = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    // Tab completion for "ai model": the models from the last welcome, filtered by what is typed so far.
    private static final SuggestionProvider<CommandSourceStack> MODEL_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(modelList(), builder);

    private DevCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(root()));
    }

    // The tree: /machiavillager <subcommand>
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        var root = Commands.literal("machiavillager")
                .then(summons())
                .then(spawn())
                .then(bridge());
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            root.then(brain()); // dev only: debugging shortcuts
            root.then(ai()); // dev only: pick the model that answers
        }
        return root;
    }

    // /machiavillager ai models, ai model <name>, ai model reset (dev only, operators)
    private static LiteralArgumentBuilder<CommandSourceStack> ai() {
        return Commands.literal("ai")
                .requires(OPERATOR)
                .then(Commands.literal("models").executes(DevCommands::runAiModels))
                .then(Commands.literal("model")
                        // The literal is a sibling of the name argument, so "reset" is never read as a model name.
                        .then(Commands.literal("reset").executes(DevCommands::runAiReset))
                        .then(Commands.argument(MODEL_ARG, StringArgumentType.greedyString())
                                .suggests(MODEL_SUGGESTIONS)
                                .executes(DevCommands::runAiSet)));
    }

    // The model ids from the last welcome; empty when the bridge is not connected.
    private static List<String> modelList() {
        return BridgeLifecycle.current().map(BridgeClient::models).orElse(List.of());
    }

    // Prints one model per line, or says why there are none.
    private static int runAiModels(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> models = modelList();
        if (models.isEmpty()) {
            source.sendSuccess(() -> Component.literal(NO_MODELS), false);
            return 0;
        }
        for (String model : models) {
            source.sendSuccess(() -> Component.literal(model), false);
        }
        return models.size();
    }

    private static int runAiSet(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, MODEL_ARG).trim();
        if (name.isEmpty()) {
            context.getSource().sendFailure(Component.literal(NO_MODEL_NAME));
            return 0;
        }
        String feedback = "Model request sent: " + name + ". The next reply shows who answered.";
        return sendDevModel(context.getSource(), DevModelOp.SET, Optional.of(name), feedback);
    }

    private static int runAiReset(CommandContext<CommandSourceStack> context) {
        return sendDevModel(context.getSource(), DevModelOp.RESET, Optional.empty(), RESET_FEEDBACK);
    }

    // Sends the request only when the link is up; otherwise the operator is told the bridge is not connected.
    private static int sendDevModel(
            CommandSourceStack source, DevModelOp op, Optional<String> model, String feedback) {
        Optional<BridgeClient> connected = BridgeLifecycle.current()
                .filter(client -> client.state() == BridgeState.CONNECTED);
        if (connected.isEmpty()) {
            source.sendFailure(Component.literal(NOT_CONNECTED));
            return 0;
        }
        connected.get().send(new DevModelEvent(EventIds.newEventId(), Instant.now(), op, model));
        source.sendSuccess(() -> Component.literal(feedback), false);
        return 1;
    }

    // /machiavillager spawn
    private static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn").requires(OPERATOR).executes(DevCommands::runSpawn);
    }

    // /machiavillager bridge status: operators only, and never shows the URL or the token.
    private static LiteralArgumentBuilder<CommandSourceStack> bridge() {
        return Commands.literal("bridge")
                .requires(OPERATOR)
                .then(Commands.literal("status").executes(DevCommands::runBridgeStatus));
    }

    private static int runBridgeStatus(CommandContext<CommandSourceStack> context) {
        String message = BridgeLifecycle.current()
                .map(client -> BridgeStatusText.describe(
                        client.state(), client.connectedSince(), client.nextAttemptAt(), Instant.now()))
                .orElse("Bridge: OFF (not started, see the log)");
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
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
        List<MachiavillagerEntity> existing = MachiavillagerLookup.findAll(source.getServer());
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

    // Runs the same dev action on every loaded Machiavillager and reports how many were affected.
    private static int forEachNpc(
            CommandSourceStack source, Consumer<MachiavillagerEntity> action, String doneMessage) {
        List<MachiavillagerEntity> all = MachiavillagerLookup.findAll(source.getServer());
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
        brain.then(nudge());

        // One literal per table entry: "brain do follow", "brain do bark", and so on.
        var does = Commands.literal("do");
        DEV_ACTIONS.forEach((name, build) ->
                does.then(Commands.literal(name).executes(context -> runDo(context, name, build))));
        brain.then(does);

        // "set" edits the saved story values, and the mood.
        brain.then(set());
        return brain;
    }

    // "brain nudge <delta>": runs the same path as an AI suggestion, for the player who typed it.
    private static LiteralArgumentBuilder<CommandSourceStack> nudge() {
        return Commands.literal("nudge")
                .then(Commands.argument(NUDGE_ARG, IntegerArgumentType.integer(MIN_NUDGE, MAX_NUDGE))
                        .executes(DevCommands::runNudge));
    }

    // Applies the nudge through the bridge's nudger, so the caps and the refusal reasons are the real ones.
    private static int runNudge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID playerId = source.getPlayerOrException().getUUID();
        int delta = IntegerArgumentType.getInteger(context, NUDGE_ARG);

        // The nudger exists only while the bridge runs, so the operator is told when it is off.
        Optional<RelationshipNudger> nudger = BridgeLifecycle.nudger();
        if (nudger.isEmpty()) {
            source.sendFailure(Component.literal("Nudge refused: bridge is off."));
            return 0;
        }
        RelationshipNudges.Result result = nudger.get().suggest(playerId, delta, DEV_NUDGE_REASON);
        if (result instanceof RelationshipNudges.Applied applied) {
            source.sendSuccess(() -> Component.literal("Nudge applied: " + applied.delta()), false);
            return 1;
        }
        String reason = ((RelationshipNudges.Rejected) result).reason();
        source.sendFailure(Component.literal("Nudge refused: " + reason));
        return 0;
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

        // One literal per phase, named in lower case: "brain set phase regime".
        var phase = Commands.literal("phase");
        for (Phase value : Phase.values()) {
            phase.then(Commands.literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(context -> runSetPhase(context, value)));
        }
        return set.then(mood).then(phase);
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

    // Puts him straight in a phase and resets the ratchet, so testing can go back. Works even with no NPC spawned.
    private static int runSetPhase(CommandContext<CommandSourceStack> context, Phase phase) {
        MinecraftServer server = context.getSource().getServer();
        StoryData data = StoryData.get(server);
        data.setPhase(phase, StoryData.personality(server));

        // Shows the phase he landed in, with the ambition that put him there.
        StoryProgress progress = data.progress();
        String message = "Phase is now " + progress.phase() + " (ambition " + (int) progress.ambition() + ").";
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
        List<MachiavillagerEntity> all = MachiavillagerLookup.findAll(source.getServer());
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
