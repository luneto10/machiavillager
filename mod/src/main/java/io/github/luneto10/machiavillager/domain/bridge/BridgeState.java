package io.github.luneto10.machiavillager.domain.bridge;

/** Where the bridge client is in its life cycle. */
public enum BridgeState {
    /** No token is set, or the bridge has been stopped. Nothing is attempted. */
    OFF,
    /** A connection attempt, including the handshake, is in progress. */
    CONNECTING,
    /** The backend answered hello with welcome; actions and events can flow. */
    CONNECTED,
    /** The last attempt failed or the link dropped; the client waits before trying again. */
    BACKING_OFF
}
