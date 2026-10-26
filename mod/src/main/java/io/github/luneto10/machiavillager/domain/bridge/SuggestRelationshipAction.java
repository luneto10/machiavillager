package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Backend suggests a small change to how he feels about a player, after a conversation. The mod decides whether
 * and how much to apply; the backend never sets the value.
 *
 * @param requestId the action id the status messages will reference
 * @param player the player whose affinity the suggestion is about
 * @param affinityDelta the suggested change; the schema allows zero, the mod refuses it
 * @param reason why the backend suggests it; logged, never shown to the player
 * @param timeoutMs how long the mod may take before giving up
 * @param inReplyTo optional player_chat event this suggestion came from
 */
public record SuggestRelationshipAction(
        String requestId,
        UUID player,
        int affinityDelta,
        String reason,
        int timeoutMs,
        Optional<String> inReplyTo) implements BridgeMessage {

    /** Null checks only: the schema bounds the numbers and the text. */
    public SuggestRelationshipAction {
        Objects.requireNonNull(requestId, "request_id");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(inReplyTo, "in_reply_to");
    }
}
