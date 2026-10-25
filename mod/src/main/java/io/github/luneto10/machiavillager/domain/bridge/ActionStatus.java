package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;

/**
 * Progress of one action request. Each status is its own record, so the shape of each one is fixed by its type.
 * The schema enforces the limits on ids and reasons; the records only check for null.
 */
public sealed interface ActionStatus extends BridgeMessage
        permits ActionStatus.Accepted, ActionStatus.Running, ActionStatus.Succeeded,
                ActionStatus.Failed, ActionStatus.Rejected, ActionStatus.Interrupted {

    /** The action request this status belongs to. */
    String requestId();

    /** The mod took the request and will try to run it. */
    record Accepted(String requestId) implements ActionStatus {
        /** Null check only. */
        public Accepted {
            Objects.requireNonNull(requestId, "request_id");
        }
    }

    /** The mod has started performing the action. */
    record Running(String requestId) implements ActionStatus {
        /** Null check only. */
        public Running {
            Objects.requireNonNull(requestId, "request_id");
        }
    }

    /** The action finished. Nothing else to report. */
    record Succeeded(String requestId) implements ActionStatus {
        /** Null check only. */
        public Succeeded {
            Objects.requireNonNull(requestId, "request_id");
        }
    }

    /** The action could not be completed. The reason says why. */
    record Failed(String requestId, String reason) implements ActionStatus {
        /** Null checks only. */
        public Failed {
            Objects.requireNonNull(requestId, "request_id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The mod refused the request before starting it. The reason says why. */
    record Rejected(String requestId, String reason) implements ActionStatus {
        /** Null checks only. */
        public Rejected {
            Objects.requireNonNull(requestId, "request_id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The action started but stopped early. Reports how many of the lines were spoken. */
    record Interrupted(String requestId, int spoken, int of) implements ActionStatus {
        /** Null check only; the counts are bounded by the schema. */
        public Interrupted {
            Objects.requireNonNull(requestId, "request_id");
        }
    }
}
