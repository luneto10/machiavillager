package io.github.luneto10.machiavillager.domain.bridge;

import java.time.Instant;
import java.util.Objects;

/**
 * The mod asks for lines of one kind at the current phase and mood. The backend may answer with a deliver_lines
 * action; no reply is required here.
 *
 * @param eventId the event id, prefixed evt_
 * @param occurredAt when the mod asked, in UTC
 * @param kind the line kind on the wire: bark, hint or mutter
 * @param phase the story phase on the wire
 * @param mood the mood on the wire
 */
public record LinesNeededEvent(String eventId, Instant occurredAt, String kind, String phase, String mood)
        implements BridgeMessage {

    /** Null checks only: the schema holds the allowed spellings. */
    public LinesNeededEvent {
        Objects.requireNonNull(eventId, "event_id");
        Objects.requireNonNull(occurredAt, "occurred_at");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(mood, "mood");
    }
}
