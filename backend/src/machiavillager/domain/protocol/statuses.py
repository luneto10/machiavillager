"""action_status messages: how an action progresses and ends.

Holds the ActionStatus union and the terminal-status helpers.
"""

from typing import Annotated, Final, Literal

from pydantic import Field

from machiavillager.domain.protocol.common import MAX_SPEAK_LINES, ActionId, EnvelopeModel, Reason


class _ActionStatusBase(EnvelopeModel):
    """Base for action_status messages. The status field selects the concrete model."""

    type: Literal["action_status"] = Field(description='Message kind. Always "action_status".')
    request_id: ActionId = Field(
        description="The request_id of the action this status is about. Echoed from the action."
    )


class Accepted(_ActionStatusBase):
    """The mod took the request and will try to run it."""

    status: Literal["accepted"] = Field(
        description='Progress. "accepted": the mod took the action and will try to run it.'
    )


class Running(_ActionStatusBase):
    """The mod has started performing the action."""

    status: Literal["running"] = Field(
        description='Progress. "running": the mod has started the action.'
    )


class Succeeded(_ActionStatusBase):
    """The action finished. Nothing else to report."""

    status: Literal["succeeded"] = Field(
        description='Terminal. "succeeded": the action finished. No other fields.'
    )


class Failed(_ActionStatusBase):
    """The action could not be completed. The reason says why."""

    status: Literal["failed"] = Field(
        description='Terminal. "failed": the action could not be completed.'
    )
    reason: Reason = Field(
        description=(
            "Why the action failed, in up to 200 characters. Set by the mod, or by the backend "
            'when the mod disconnects ("disconnected") or does not answer in time ("timeout"). '
            'Example: "disconnected".'
        )
    )


class Rejected(_ActionStatusBase):
    """The mod refused the request before starting it. The reason says why."""

    status: Literal["rejected"] = Field(
        description='Terminal. "rejected": the mod refused the action before starting it.'
    )
    reason: Reason = Field(
        description=(
            "Why the mod refused the action, in up to 200 characters. "
            'Example: "unknown action: run_command".'
        )
    )


class Interrupted(_ActionStatusBase):
    """The action started but stopped early. Reports how many lines were spoken."""

    status: Literal["interrupted"] = Field(
        description=(
            'Terminal. "interrupted": the action started and stopped early, because a player '
            "talked over him."
        )
    )
    spoken: Annotated[int, Field(ge=0)] = Field(
        description="How many lines were spoken before the interruption. 0 or more. Example: 2."
    )
    of: Annotated[int, Field(ge=1, le=MAX_SPEAK_LINES)] = Field(
        description="How many lines the action asked for, between 1 and 8. Example: 5."
    )


# Nested discriminators: "type" selects the message, and action_status then selects the status.
ActionStatus = Annotated[
    Accepted | Running | Succeeded | Failed | Rejected | Interrupted,
    # The title names this group in the generated docs instead of "item 3".
    Field(
        discriminator="status",
        title="ActionStatus",
        description="Mod reports how an action request is going. The status selects the shape.",
    ),
]

# The four statuses that end a request. Accepted and Running are progress, not an answer.
TerminalStatus = Succeeded | Failed | Rejected | Interrupted
TERMINAL_STATUS_TYPES: Final = (Succeeded, Failed, Rejected, Interrupted)


def is_terminal(status: ActionStatus) -> bool:
    """True when the status ends the request. Only terminal statuses may answer a waiter."""

    # isinstance on the concrete classes: a new status must be listed here on purpose.
    return isinstance(status, TERMINAL_STATUS_TYPES)
