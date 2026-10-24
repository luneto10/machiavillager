package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.bridge.BridgeState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BridgeStatusTextTest {
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void describe_offAndConnecting_matchTheCommandWords() {
        assertEquals("Bridge: OFF (no token set)",
                BridgeStatusText.describe(BridgeState.OFF, Optional.empty(), Optional.empty(), NOW));
        assertEquals("Bridge: CONNECTING...",
                BridgeStatusText.describe(BridgeState.CONNECTING, Optional.empty(), Optional.empty(), NOW));
    }

    @Test
    void describe_connected_showsTheTimeTheLinkCameUp() {
        String line = BridgeStatusText.describe(BridgeState.CONNECTED, Optional.of(NOW), Optional.empty(), NOW);

        assertTrue(line.startsWith("Bridge: CONNECTED since "), line);
        assertTrue(line.matches(".*\\d{2}:\\d{2}:\\d{2}$"), line);
    }

    @Test
    void describe_backingOff_countsDownAndNeverGoesNegative() {
        Instant due = NOW.plusSeconds(4);

        assertEquals("Bridge: BACKING_OFF, next try in 4 s",
                BridgeStatusText.describe(BridgeState.BACKING_OFF, Optional.empty(), Optional.of(due), NOW));
        assertEquals("Bridge: BACKING_OFF, next try in 0 s",
                BridgeStatusText.describe(BridgeState.BACKING_OFF, Optional.empty(), Optional.of(NOW.minusSeconds(3)), NOW));
    }
}
