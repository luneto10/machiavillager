package io.github.luneto10.machiavillager.bridge;

import io.github.luneto10.machiavillager.domain.bridge.BridgeState;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** The words printed by {@code /machiavillager bridge status}. Pure: the caller passes the state and the clock. */
public final class BridgeStatusText {
    /** Wall-clock time shown for when the link came up. */
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String PREFIX = "Bridge: ";
    private static final String UNKNOWN_TIME = "unknown";

    private BridgeStatusText() {}

    /** One line for the given state. Never includes the URL or the token. */
    public static String describe(
            BridgeState state, Optional<Instant> connectedSince, Optional<Instant> nextAttemptAt, Instant now) {
        return PREFIX + switch (state) {
            case OFF -> "OFF (no token set)";
            case CONNECTING -> "CONNECTING...";
            case CONNECTED -> "CONNECTED since " + connectedSince.map(CLOCK::format).orElse(UNKNOWN_TIME);
            case BACKING_OFF -> "BACKING_OFF, next try in " + secondsUntil(nextAttemptAt, now) + " s";
        };
    }

    // A retry that is already due shows 0 s rather than a negative countdown.
    private static long secondsUntil(Optional<Instant> at, Instant now) {
        return at.map(instant -> Math.max(0L, Duration.between(now, instant).toSeconds())).orElse(0L);
    }
}
