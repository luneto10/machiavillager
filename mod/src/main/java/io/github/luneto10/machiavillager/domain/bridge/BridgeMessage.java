package io.github.luneto10.machiavillager.domain.bridge;

/**
 * Every message that crosses the bridge. The sealed set lets the client switch over message types
 * exhaustively, so a new type that is not handled fails to compile.
 */
public sealed interface BridgeMessage
        permits Hello, Welcome, SpeakAction, SuggestRelationshipAction, DeliverLinesAction, ActionStatus,
                PlayerChatEvent, DevModelEvent, LinesNeededEvent, BridgeError {
}
