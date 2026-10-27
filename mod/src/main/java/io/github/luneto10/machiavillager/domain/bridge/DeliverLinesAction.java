package io.github.luneto10.machiavillager.domain.bridge;

import java.util.List;
import java.util.Objects;

/**
 * Backend sends lines for one kind of moment, such as a hint. The lines may hold placeholders like {player}; the
 * mod does not fill them here.
 *
 * @param requestId the action id the status messages will reference
 * @param kind the line kind on the wire: bark, hint or mutter
 * @param phase the story phase on the wire
 * @param mood the mood on the wire
 * @param lines the lines, one to thirty; copied so the caller cannot change them later
 * @param timeoutMs how long the mod may take before giving up
 */
public record DeliverLinesAction(
        String requestId,
        String kind,
        String phase,
        String mood,
        List<String> lines,
        int timeoutMs) implements BridgeMessage {

    /** Null checks, then a copy of the lines. The schema bounds the line count and the text. */
    public DeliverLinesAction {
        Objects.requireNonNull(requestId, "request_id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(mood, "mood");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
}
