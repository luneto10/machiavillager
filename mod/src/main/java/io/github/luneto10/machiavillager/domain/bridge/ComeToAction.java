package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;
import java.util.UUID;

/**
 * Backend asks him to go to a player who called him. The mod checks the same rule as the snapshot again, so the
 * backend never moves him on its own.
 *
 * @param requestId the action id the status messages will reference
 * @param player the player who asked him to come
 * @param timeoutMs how long the mod may take before giving up
 */
public record ComeToAction(String requestId, UUID player, int timeoutMs) implements BridgeMessage {

    /** Null checks only: the schema checks the id and the timeout. */
    public ComeToAction {
        Objects.requireNonNull(requestId, "request_id");
        Objects.requireNonNull(player, "player");
    }
}
