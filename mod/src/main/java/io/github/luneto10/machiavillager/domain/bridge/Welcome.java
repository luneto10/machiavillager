package io.github.luneto10.machiavillager.domain.bridge;

import java.util.List;
import java.util.Objects;

/**
 * Sent by the backend in answer to hello when the protocol version is supported.
 *
 * @param models the model ids the backend can use, shown by the dev commands; empty when the backend sends none
 */
public record Welcome(List<String> models) implements BridgeMessage {

    /** Null check only, and a copy of the list so later changes to the caller's list do not leak in. */
    public Welcome {
        models = List.copyOf(Objects.requireNonNull(models, "models"));
    }
}
