package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.bridge.BridgeConfig;
import io.github.luneto10.machiavillager.domain.bridge.BridgeState;
import io.github.luneto10.machiavillager.domain.bridge.Hello;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BridgeClientTest {
    private static final String MOD_VERSION = "0.1.0";

    @Test
    void start_withoutToken_staysOff_andStartsNoBridgeThread() {
        BridgeClient client = offClient();
        try {
            client.start();

            assertEquals(BridgeState.OFF, client.state());
            assertTrue(bridgeThreads().isEmpty(), "no bridge thread may run while the bridge is off");
        } finally {
            client.stop();
        }
    }

    @Test
    void send_whileOff_doesNotThrow() {
        BridgeClient client = offClient();
        try {
            client.start();

            assertDoesNotThrow(() -> client.send(new Hello(MOD_VERSION)));
        } finally {
            client.stop();
        }
    }

    @Test
    void send_afterStop_doesNotThrow() {
        BridgeClient client = offClient();
        client.stop();

        assertDoesNotThrow(() -> client.send(new Hello(MOD_VERSION)));
    }

    // Game executor runs inline, and no token is set, so nothing touches the network.
    private static BridgeClient offClient() {
        BridgeConfig config = BridgeConfig.fromEnvironment(Map.of());
        return new BridgeClient(config, MOD_VERSION, Runnable::run, message -> { });
    }

    private static List<Thread> bridgeThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(BridgeClient.THREAD_NAME))
                .toList();
    }
}
