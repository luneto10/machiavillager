package io.github.luneto10.machiavillager.domain.bridge;

/**
 * Progress of one action request. Each status is its own record, so a failed status cannot exist
 * without a reason and an interrupted one cannot exist without its counts.
 */
public sealed interface ActionStatus extends BridgeMessage
        permits ActionStatus.Accepted, ActionStatus.Running, ActionStatus.Succeeded,
                ActionStatus.Failed, ActionStatus.Rejected, ActionStatus.Interrupted {

    /** The action request this status belongs to. */
    String requestId();

    /** The mod took the request and will try to run it. */
    record Accepted(String requestId) implements ActionStatus {
        /** Validates the request id. */
        public Accepted {
            requestId = ProtocolRules.requireActionId(requestId);
        }
    }

    /** The mod has started performing the action. */
    record Running(String requestId) implements ActionStatus {
        /** Validates the request id. */
        public Running {
            requestId = ProtocolRules.requireActionId(requestId);
        }
    }

    /** The action finished. Nothing else to report. */
    record Succeeded(String requestId) implements ActionStatus {
        /** Validates the request id. */
        public Succeeded {
            requestId = ProtocolRules.requireActionId(requestId);
        }
    }

    /** The action could not be completed. The reason says why, up to 200 characters. */
    record Failed(String requestId, String reason) implements ActionStatus {
        /** Validates the request id and the reason length. */
        public Failed {
            requestId = ProtocolRules.requireActionId(requestId);
            reason = ProtocolRules.requireText(reason, 0, ProtocolRules.MAX_REASON_LENGTH, "reason");
        }
    }

    /** The mod refused the request before starting it. The reason says why, up to 200 characters. */
    record Rejected(String requestId, String reason) implements ActionStatus {
        /** Validates the request id and the reason length. */
        public Rejected {
            requestId = ProtocolRules.requireActionId(requestId);
            reason = ProtocolRules.requireText(reason, 0, ProtocolRules.MAX_REASON_LENGTH, "reason");
        }
    }

    /** The action started but stopped early. Reports how many of the lines were spoken. */
    record Interrupted(String requestId, int spoken, int of) implements ActionStatus {
        /** Validates the request id and both counts. */
        public Interrupted {
            requestId = ProtocolRules.requireActionId(requestId);
            spoken = ProtocolRules.requireRange(spoken, 0, Integer.MAX_VALUE, "spoken");
            of = ProtocolRules.requireRange(of, 1, ProtocolRules.MAX_SPEAK_LINES, "of");
        }
    }
}
