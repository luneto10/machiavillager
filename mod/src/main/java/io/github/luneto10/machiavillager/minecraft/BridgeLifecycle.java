package io.github.luneto10.machiavillager.minecraft;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.bridge.BridgeClient;
import io.github.luneto10.machiavillager.domain.bridge.BridgeConfig;
import io.github.luneto10.machiavillager.domain.bridge.BridgeState;
import io.github.luneto10.machiavillager.domain.bridge.DeliverLinesAction;
import io.github.luneto10.machiavillager.domain.bridge.ModelLabel;
import io.github.luneto10.machiavillager.domain.bridge.Addressed;
import io.github.luneto10.machiavillager.domain.bridge.NearChatCooldown;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.ChatAddressing;
import io.github.luneto10.machiavillager.domain.bridge.ComeToAction;
import io.github.luneto10.machiavillager.domain.bridge.PlayerChatEvent;
import io.github.luneto10.machiavillager.domain.bridge.PrivateChannel;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.bridge.SuggestRelationshipAction;
import io.github.luneto10.machiavillager.domain.speech.SpeechRules;
import io.github.luneto10.machiavillager.minecraft.bridge.ActionExecutor;
import io.github.luneto10.machiavillager.minecraft.bridge.ModelLine;
import io.github.luneto10.machiavillager.minecraft.bridge.PlayerChatReporter;
import io.github.luneto10.machiavillager.minecraft.bridge.RelationshipNudger;
import io.github.luneto10.machiavillager.minecraft.lines.NpcLines;
import io.github.luneto10.machiavillager.minecraft.npc.NpcSpeech;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Starts the backend bridge with the server and stops it at shutdown. The server is the game-thread executor:
 * {@link MinecraftServer} is an {@link java.util.concurrent.Executor}, so decoded messages wait in its task
 * queue and run on the server thread.
 */
public final class BridgeLifecycle {
    private static final String UNKNOWN_VERSION = "unknown";

    // Set while a server runs; null otherwise, and when the settings were invalid.
    private static volatile BridgeClient client;
    // Runs the actions the backend asks for; set together with the client, null when the bridge is off.
    private static volatile ActionExecutor executor;
    // Read once at server start: when true, only lines that name him are forwarded to the backend.
    private static volatile boolean requireName;
    // Limits overheard lines per player; set with the client, null when the bridge is off.
    private static volatile NearChatCooldown nearCooldown;
    // The model label of the last reply, for the dev boss bar; a fresh one per start, null when the bridge is off.
    private static volatile ModelLine modelLine;
    // Applies suggested affinity changes; fresh per start, null when the bridge is off.
    private static volatile RelationshipNudger nudger;
    // The spoken lines; fresh per start and present whenever a server runs, even with the bridge off.
    private static volatile NpcLines npcLines;

    private BridgeLifecycle() {}

    /** Hooks the bridge into the server start and stop events. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(BridgeLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(BridgeLifecycle::stop);
        // Speech is paced by the server tick, so it advances even while no player is talking.
        ServerTickEvents.END_SERVER_TICK.register(BridgeLifecycle::tickActions);
        // Before the broadcast: a private @m line is cancelled there, so CHAT_MESSAGE never sees it.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(BridgeLifecycle::allowChat);
        ServerMessageEvents.CHAT_MESSAGE.register(BridgeLifecycle::onChat);
    }

    /** The running client, or empty when no server is running or the bridge settings were invalid. */
    public static Optional<BridgeClient> current() {
        return Optional.ofNullable(client);
    }

    /** The nudger of the running server, or empty when the bridge is off. Dev command and executor share it. */
    public static Optional<RelationshipNudger> nudger() {
        return Optional.ofNullable(nudger);
    }

    /** The spoken lines of the running server, or empty when no server is running. Works with the bridge off. */
    public static Optional<NpcLines> lines() {
        return Optional.ofNullable(npcLines);
    }

    /** Dev: the model label the boss bar shows. Back to "no reply yet" when the bridge is off or restarted. */
    public static String aiLineText() {
        ModelLine line = modelLine;
        return line == null ? ModelLabel.NO_REPLY_YET : line.text();
    }

    private static void start(MinecraftServer server) {
        BridgeConfig config;
        try {
            config = BridgeConfig.fromEnvironment(System.getenv());
        } catch (IllegalArgumentException invalid) {
            // Logged, not thrown: a bad bridge setting must never stop the server from starting.
            Machiavillager.LOGGER.error("Bridge off: invalid bridge settings ({})", invalid.getMessage());
            // Lines still work without the bridge: they come from the fallbacks, and nothing is asked.
            npcLines = new NpcLines(server, () -> false, event -> false);
            return;
        }
        BridgeClient started = new BridgeClient(config, modVersion(), server, BridgeLifecycle::onMessage);
        // Read with the other bridge settings, so a restart picks up a changed variable.
        requireName = ChatAddressing.requireName(System.getenv());
        // Built per start too, so a restart starts with no recorded waits and picks up a changed value.
        nearCooldown = new NearChatCooldown(NearChatCooldown.cooldownTicks(System.getenv()));
        // Fresh per start, so a restart shows "no reply yet" again until the next reply.
        ModelLine line = new ModelLine();
        modelLine = line;
        // Built per start too, so a restart starts with no recorded nudges and the same limits.
        RelationshipNudger nudges = new RelationshipNudger(server);
        nudger = nudges;
        // The executor is in place before the socket starts, so the first action finds it.
        // Built once the client exists, so requests go out through it; they are dropped unless the link is up.
        NpcLines lines = new NpcLines(
                server,
                () -> started.state() == BridgeState.CONNECTED,
                event -> sendIfConnected(started, event));
        npcLines = lines;
        executor = new ActionExecutor(
                server, started, SpeechRules.unpromptedCooldown(System.getenv()), line, nudges, lines);
        client = started;
        started.start();
    }

    private static void stop(MinecraftServer server) {
        BridgeClient running = client;
        client = null;
        executor = null;
        nudger = null;
        npcLines = null;
        nearCooldown = null;
        modelLine = null;
        if (running != null) {
            running.stop();
        }
    }

    // Game thread. Speak, relationship suggestions and delivered lines are executed; any other message is only logged.
    private static void onMessage(BridgeMessage message) {
        ActionExecutor actions = executor;
        if (message instanceof SpeakAction speak && actions != null) {
            actions.speak(speak);
            return;
        }
        if (message instanceof SuggestRelationshipAction suggest && actions != null) {
            actions.suggestRelationship(suggest);
            return;
        }
        if (message instanceof DeliverLinesAction deliver && actions != null) {
            actions.deliverLines(deliver);
            return;
        }
        if (message instanceof ComeToAction come && actions != null) {
            actions.comeTo(come);
            return;
        }
        Machiavillager.LOGGER.info("Bridge received {}", message.getClass().getSimpleName());
    }

    // Game thread, every server tick: advances the speech in progress.
    private static void tickActions(MinecraftServer server) {
        ActionExecutor actions = executor;
        if (actions != null) {
            actions.tick();
        }
    }

    // A private @m line is hidden from everyone else: the broadcast is cancelled, so it never interrupts him either.
    // This may run on a filter thread, so only the text is read here; the rest is posted to the server thread.
    private static boolean allowChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
        if (!bound.chatType().is(ChatType.CHAT)) {
            return true;
        }
        Optional<String> body = PrivateChannel.body(message.signedContent());
        if (body.isEmpty()) {
            return true;
        }
        sender.level().getServer().execute(() -> onPrivateChat(sender, body.get()));
        return false;
    }

    // Server thread. The sender always sees the echo; the backend gets the line only while the bridge runs.
    private static void onPrivateChat(ServerPlayer sender, String text) {
        // "@m" alone has nothing to say, and the contract needs at least one character.
        if (text.isEmpty()) {
            return;
        }
        Component name = ModEntities.MACHIAVILLAGER.getDescription();
        NpcSpeech.echoPrivate(sender, name, text);
        BridgeClient running = client;
        if (running == null) {
            return;
        }
        Optional<PlayerChatEvent> event =
                PlayerChatReporter.build(sender.level().getServer(), sender, text, Addressed.PRIVATE);
        if (event.isEmpty()) {
            // shortcut: he is found only while his chunk is loaded; the chunk ticket in #57 keeps him loaded.
            NpcSpeech.notice(sender, Component.empty().append(name).append(" is out of reach right now."));
            return;
        }
        running.send(event.get());
        // Logged by id only: a private line is never written to the log.
        Machiavillager.LOGGER.info("player_chat sent event_id={} (private)", event.get().eventId());
    }

    // Normal chat only: commands such as /say and /me also fire this event, and they must never interrupt him.
    // The chat handler may finish on a filter thread, so the check is posted back to the server thread.
    private static void onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
        ActionExecutor actions = executor;
        if (actions == null || !bound.chatType().is(ChatType.CHAT)) {
            return;
        }
        String text = message.signedContent();
        sender.level().getServer().execute(() -> {
            actions.onPlayerChat(sender);
            reportChat(sender, text);
        });
    }

    // Server thread. A line goes to the backend if the bridge is running, he can hear it, and the addressing rule allows it.
    private static void reportChat(ServerPlayer sender, String text) {
        BridgeClient running = client;
        if (running == null) {
            return;
        }
        Optional<Addressed> addressed = ChatAddressing.decide(text, requireName);
        if (addressed.isEmpty()) {
            return;
        }
        Addressed reason = addressed.get();
        // Built first, so a line he cannot hear never uses up the speaker's near cooldown.
        PlayerChatReporter.build(sender.level().getServer(), sender, text, reason).ifPresent(event -> {
            // Overheard lines wait out the per-player cooldown; a line that names him always passes.
            boolean limited = reason == Addressed.NEAR;
            if (limited && !nearCooldown.tryAcquire(sender.getUUID(), sender.level().getGameTime())) {
                // Logged without the text, so a quiet player's blocked lines still leave a trace.
                Machiavillager.LOGGER.info("player_chat skipped: near cooldown");
                return;
            }
            running.send(event);
            // Logged by id only: the chat text never reaches the log.
            Machiavillager.LOGGER.info("player_chat sent event_id={}", event.eventId());
        });
    }

    // Server thread. True when the event was queued: the link was up when it was checked.
    private static boolean sendIfConnected(BridgeClient bridge, BridgeMessage event) {
        if (bridge.state() != BridgeState.CONNECTED) {
            return false;
        }
        bridge.send(event);
        return true;
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(Machiavillager.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(UNKNOWN_VERSION);
    }
}
