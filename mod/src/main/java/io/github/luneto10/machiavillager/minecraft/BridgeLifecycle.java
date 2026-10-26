package io.github.luneto10.machiavillager.minecraft;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.bridge.BridgeClient;
import io.github.luneto10.machiavillager.domain.bridge.BridgeConfig;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.NameMention;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.speech.SpeechRules;
import io.github.luneto10.machiavillager.minecraft.bridge.ActionExecutor;
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
        // The executor is in place before the socket starts, so the first action finds it.
        executor = new ActionExecutor(server, started, SpeechRules.unpromptedCooldown(System.getenv()));
        client = started;
        started.start();
    }

    private static void stop(MinecraftServer server) {
        BridgeClient running = client;
        client = null;
        executor = null;
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

    // Server thread. A line that calls him by name goes to the backend, if the bridge is running and he can hear it.
    private static void reportChat(ServerPlayer sender, String text) {
        BridgeClient running = client;
        if (running == null || !NameMention.matches(text)) {
            return;
        }
        PlayerChatReporter.build(sender.level().getServer(), sender, text).ifPresent(event -> {
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
