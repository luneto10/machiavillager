package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BridgeConfigTest {
    private static final String SECRET = "s3cret-token-value";

    @Test
    void fromEnvironment_usesDefaultUrlWhenUnset() {
        BridgeConfig config = BridgeConfig.fromEnvironment(Map.of());

        assertEquals(URI.create(BridgeConfig.DEFAULT_URL), config.url());
        assertFalse(config.enabled());
    }

    @Test
    void fromEnvironment_readsUrlAndTokenWhenSet() {
        BridgeConfig config = BridgeConfig.fromEnvironment(Map.of(
                BridgeConfig.URL_VARIABLE, "wss://example.test/bridge",
                BridgeConfig.TOKEN_VARIABLE, SECRET));

        assertEquals(URI.create("wss://example.test/bridge"), config.url());
        assertEquals(SECRET, config.token().orElseThrow());
        assertTrue(config.enabled());
    }

    @Test
    void fromEnvironment_treatsBlankTokenAsAbsent() {
        BridgeConfig config = BridgeConfig.fromEnvironment(Map.of(BridgeConfig.TOKEN_VARIABLE, "   "));

        assertFalse(config.enabled());
        assertFalse(config.token().isPresent());
    }

    @Test
    void toString_neverContainsTheToken() {
        BridgeConfig config = BridgeConfig.fromEnvironment(Map.of(BridgeConfig.TOKEN_VARIABLE, SECRET));

        String text = config.toString();

        assertFalse(text.contains(SECRET));
        assertTrue(text.contains("token=***"));
    }

    @Test
    void toString_saysNoneWhenNoToken() {
        assertTrue(BridgeConfig.fromEnvironment(Map.of()).toString().contains("token=none"));
    }

    @Test
    void constructor_rejectsNonWebSocketScheme() {
        assertThrows(IllegalArgumentException.class, () -> BridgeConfig.fromEnvironment(
                Map.of(BridgeConfig.URL_VARIABLE, "http://example.test/bridge")));
    }
}
