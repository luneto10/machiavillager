package io.github.luneto10.machiavillager.minecraft;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.bridge.BridgeClient;
import io.github.luneto10.machiavillager.domain.bridge.BridgeConfig;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Starts the backend bridge with the server and stops it at shutdown. The server is the game-thread executor:
 * {@link MinecraftServer} is an {@link java.util.concurrent.Executor}, so decoded messages wait in its task
 * queue and run on the server thread.
 */
public final class BridgeLifecycle {
    private static final String UNKNOWN_VERSION = "unknown";

    // Set while a server runs; null otherwise, and when the settings were invalid.
    private static volatile BridgeClient client;

    private BridgeLifecycle() {}

    /** Hooks the bridge into the server start and stop events. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(BridgeLifecycle::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(BridgeLifecycle::stop);
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
        client = started;
        started.start();
    }

    private static void stop(MinecraftServer server) {
        BridgeClient running = client;
        client = null;
        if (running != null) {
            running.stop();
        }
    }

    // Game thread. Actions are only logged until #10 executes them.
    private static void onMessage(BridgeMessage message) {
        Machiavillager.LOGGER.info(
                "Bridge received {} (actions are executed in #10)", message.getClass().getSimpleName());
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(Machiavillager.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(UNKNOWN_VERSION);
    }
}
