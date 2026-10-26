package io.github.luneto10.machiavillager.domain.bridge;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An operator changed the model choice in dev. The mod reports it; no reply is expected.
 *
 * @param eventId the event id, prefixed evt_
 * @param occurredAt when the operator asked, in UTC
 * @param op SET pins a model, RESET goes back to the default
 * @param model the model id for SET; empty for RESET
 */
public record DevModelEvent(String eventId, Instant occurredAt, DevModelOp op, Optional<String> model)
        implements BridgeMessage {

    /** Null checks, then the one rule the schema cannot express: SET carries a model and RESET does not. */
    public DevModelEvent {
        Objects.requireNonNull(eventId, "event_id");
        Objects.requireNonNull(occurredAt, "occurred_at");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(model, "model");
        if (op == DevModelOp.SET && model.isEmpty()) {
            throw new IllegalArgumentException("a set needs a model");
        }
        if (op == DevModelOp.RESET && model.isPresent()) {
            throw new IllegalArgumentException("a reset takes no model");
        }
    }
}
