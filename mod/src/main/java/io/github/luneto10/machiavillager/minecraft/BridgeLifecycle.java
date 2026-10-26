package io.github.luneto10.machiavillager.minecraft;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.bridge.BridgeClient;
import io.github.luneto10.machiavillager.domain.bridge.BridgeConfig;
import io.github.luneto10.machiavillager.domain.bridge.ModelLabel;
import io.github.luneto10.machiavillager.domain.bridge.Addressed;
import io.github.luneto10.machiavillager.domain.bridge.NearChatCooldown;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.ChatAddressing;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.speech.SpeechRules;
import io.github.luneto10.machiavillager.minecraft.bridge.ActionExecutor;
import io.github.luneto10.machiavillager.minecraft.bridge.ModelLine;
import io.github.luneto10.machiavillager.minecraft.bridge.PlayerChatReporter;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ChatType;
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

    private BridgeLifecycle() {}

    /** Hooks the bridge into the server start and stop events. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(BridgeLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(BridgeLifecycle::stop);
        // Speech is paced by the server tick, so it advances even while no player is talking.
        ServerTickEvents.END_SERVER_TICK.register(BridgeLifecycle::tickActions);
        ServerMessageEvents.CHAT_MESSAGE.register(BridgeLifecycle::onChat);
    }

    /** The running client, or empty when no server is running or the bridge settings were invalid. */
    public static Optional<BridgeClient> current() {
        return Optional.ofNullable(client);
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
        // The executor is in place before the socket starts, so the first action finds it.
        executor = new ActionExecutor(server, started, SpeechRules.unpromptedCooldown(System.getenv()), line);
        client = started;
        started.start();
    }

    private static void stop(MinecraftServer server) {
        BridgeClient running = client;
        client = null;
        executor = null;
        nearCooldown = null;
        modelLine = null;
        if (running != null) {
            running.stop();
        }
    }

    // Game thread. Speak is executed; any other message is only logged.
    private static void onMessage(BridgeMessage message) {
        ActionExecutor actions = executor;
        if (message instanceof SpeakAction speak && actions != null) {
            actions.speak(speak);
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
                return;
            }
            running.send(event);
            // Logged by id only: the chat text never reaches the log.
            Machiavillager.LOGGER.info("player_chat sent event_id={}", event.eventId());
        });
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(Machiavillager.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(UNKNOWN_VERSION);
    }
}
