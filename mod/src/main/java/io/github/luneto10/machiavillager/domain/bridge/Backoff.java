package io.github.luneto10.machiavillager.domain.bridge;

import java.time.Duration;

/**
 * Reconnect delays for the bridge client: 1, 2, 4, 8, 16 seconds, then 30 seconds for every later attempt.
 * Mutable on purpose, like {@link io.github.luneto10.machiavillager.domain.time.Countdown}: one instance
 * lives for one client and is reset after a successful connection.
 */
public final class Backoff {
    /** First delay after a failed connect. */
    public static final Duration INITIAL = Duration.ofSeconds(1);
    /** Longest delay the backoff grows to. */
    public static final Duration MAX = Duration.ofSeconds(30);

    private Duration nextDelay = INITIAL;

    /** Returns this attempt's delay and doubles the next one, capped at {@link #MAX}. */
    public Duration next() {
        Duration current = nextDelay;
        // Capped so a long outage never asks for a delay above MAX.
        Duration doubled = current.multipliedBy(2);
        nextDelay = doubled.compareTo(MAX) > 0 ? MAX : doubled;
        return current;
    }

    /** Starts over at {@link #INITIAL}, after a connection succeeded. */
    public void reset() {
        nextDelay = INITIAL;
    }
}
