package io.github.luneto10.machiavillager.domain.bridge;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Backend asks the mod to speak a short sequence of lines as one action. The mod paces the lines.
 *
 * @param requestId the action id the status messages will reference
 * @param lines what he says, in order; the schema bounds the count and the length of each line
 * @param timeoutMs how long the mod may take before giving up
 * @param conversationId optional conversation this speech belongs to
 * @param to optional private recipient: the lines reach that player alone, at any distance
 * @param inReplyTo optional player_chat event this speech answers; a reply skips the unprompted-speech limit
 */
public record SpeakAction(
        String requestId,
        List<String> lines,
        int timeoutMs,
        Optional<String> conversationId,
        Optional<UUID> to,
        Optional<String> inReplyTo) implements BridgeMessage {

    /** Null checks only, and a copy of the lines so later changes to the caller's list do not leak in. */
    public SpeakAction {
        Objects.requireNonNull(requestId, "request_id");
        lines = List.copyOf(lines);
        Objects.requireNonNull(conversationId, "conversation_id");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(inReplyTo, "in_reply_to");
    }
}
