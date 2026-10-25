package io.github.luneto10.machiavillager.domain.bridge;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend asks the mod to speak a short sequence of lines as one action. The mod paces the lines.
 *
 * @param requestId the action id the status messages will reference
 * @param lines between 1 and 8 lines, each between 1 and 256 characters
 * @param timeoutMs how long the mod may take before giving up, 100 to 120000
 * @param conversationId optional conversation this speech belongs to
 */
public record SpeakAction(String requestId, List<String> lines, int timeoutMs, Optional<String> conversationId)
        implements BridgeMessage {

    /** Validates every field; the copy of the lines stops the caller from changing them later. */
    public SpeakAction {
        requestId = ProtocolRules.requireActionId(requestId);
        lines = requireLines(lines);
        timeoutMs = ProtocolRules.requireRange(
                timeoutMs, ProtocolRules.MIN_TIMEOUT_MS, ProtocolRules.MAX_TIMEOUT_MS, "timeout_ms");
        conversationId = ProtocolRules.requireConversationId(conversationId);
    }

    private static List<String> requireLines(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        // Copy first, so the size and line checks below describe the list the record keeps.
        List<String> copy = List.copyOf(lines);
        if (copy.isEmpty() || copy.size() > ProtocolRules.MAX_SPEAK_LINES) {
            throw new IllegalArgumentException(
                    "lines must hold between 1 and " + ProtocolRules.MAX_SPEAK_LINES + " entries");
        }
        copy.forEach(line -> ProtocolRules.requireText(line, 1, ProtocolRules.MAX_LINE_LENGTH, "line"));
        return copy;
    }
}
