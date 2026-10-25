"""Bridge protocol between the Minecraft mod and the backend.

These models are the source of truth. shared/protocol.schema.json and shared/PROTOCOL.md are
generated from them by scripts/export_protocol_schema.py, and the invalid examples are generated
from the same rules. The mod's Java tests read the shared files, so both sides enforce the same
rules. Design and wire rules: docs/decisions/0004-bridge-protocol.md.
"""

import re
from typing import Annotated, Any, Final, Literal
from uuid import UUID

from pydantic import (
    AwareDatetime,
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    StringConstraints,
    TypeAdapter,
    WithJsonSchema,
)

PROTOCOL_VERSION: Final = 1
JSON_SCHEMA_DIALECT: Final = "https://json-schema.org/draft/2020-12/schema"
SCHEMA_TITLE: Final = "Machiavillager bridge protocol"
SCHEMA_DESCRIPTION: Final = (
    "Messages exchanged between the Minecraft mod and the Python backend over one WebSocket. "
    "Every message is one JSON object with v and type. "
    "Design and wire rules: docs/decisions/0004-bridge-protocol.md."
)

# Limits. The business rules they encode are explained in docs/decisions/0004-bridge-protocol.md.
MAX_MOD_VERSION_LENGTH: Final = 32
MAX_SPEAK_LINES: Final = 8
MAX_LINE_LENGTH: Final = 256
MIN_TIMEOUT_MS: Final = 100
MAX_TIMEOUT_MS: Final = 120_000
MAX_REASON_LENGTH: Final = 200
MAX_DETAIL_LENGTH: Final = 500
MAX_PLAYER_NAME_LENGTH: Final = 16
MAX_CHAT_TEXT_LENGTH: Final = 256

# Identifiers are prefixed so a log line shows what kind of id it is.
ACTION_ID_PATTERN: Final = r"^act_[A-Za-z0-9]{8,32}$"
EVENT_ID_PATTERN: Final = r"^evt_[A-Za-z0-9]{8,32}$"
CONVERSATION_ID_PATTERN: Final = r"^conv_[A-Za-z0-9]{8,32}$"
CANONICAL_UUID_PATTERN: Final = (
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

ActionId = Annotated[str, StringConstraints(pattern=ACTION_ID_PATTERN)]
EventId = Annotated[str, StringConstraints(pattern=EVENT_ID_PATTERN)]
ConversationId = Annotated[str, StringConstraints(pattern=CONVERSATION_ID_PATTERN)]
SpeakLine = Annotated[str, StringConstraints(min_length=1, max_length=MAX_LINE_LENGTH)]
Reason = Annotated[str, StringConstraints(max_length=MAX_REASON_LENGTH)]
ErrorCode = Literal["bad_message", "unsupported_protocol", "unauthorized", "internal"]
# Why the mod forwarded a chat line. "private" is the @m channel; the rest are public chat.
ChatAddress = Literal["name", "conversation", "near", "butt_in", "private"]


def _require_canonical_uuid(value: object) -> object:
    # Pydantic's UUID type also accepts unhyphenated input. The wire only allows 8-4-4-4-12,
    # so the check runs before pydantic parses the value.
    if isinstance(value, str) and re.fullmatch(CANONICAL_UUID_PATTERN, value) is None:
        raise ValueError("must be a canonical 8-4-4-4-12 UUID")
    return value


# The pattern is written into the schema too, so the generated file carries the same rule.
PlayerUuid = Annotated[
    UUID,
    BeforeValidator(_require_canonical_uuid),
    WithJsonSchema({"type": "string", "format": "uuid", "pattern": CANONICAL_UUID_PATTERN}),
]


def _reject_boolean(value: object) -> object:
    # Python treats True == 1, so Literal[1] would accept JSON true. Booleans are refused first.
    if isinstance(value, bool):
        raise ValueError("must be the number 1, not a boolean")
    return value


# Wire version field type. Used for both v and the protocol negotiation field.
WireVersion = Annotated[Literal[PROTOCOL_VERSION], BeforeValidator(_reject_boolean)]


class _Closed(BaseModel):
    """Base for every bridge model. Unknown keys are rejected and values are immutable."""

    # extra="forbid" becomes additionalProperties false. strict stops pydantic from coercing
    # "30000" into an int, which the JSON Schema would reject.
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)


class _Envelope(_Closed):
    """Base for top-level messages. Every message carries the wire version."""

    v: WireVersion = Field(
        description="Wire version of the message. Always 1. A breaking change bumps it."
    )


class Hello(_Envelope):
    """Sent by the mod after connecting. Carries its mod version and the protocol it speaks."""

    type: Literal["hello"] = Field(description='Message kind. Always "hello".')
    mod_version: Annotated[
        str, StringConstraints(min_length=1, max_length=MAX_MOD_VERSION_LENGTH)
    ] = Field(
        description=('Version of the mod, read from its own metadata by the mod. Example: "0.1.0".')
    )
    protocol: WireVersion = Field(
        description="Protocol version the mod speaks. Only 1 is accepted today."
    )


class Welcome(_Envelope):
    """Sent by the backend in answer to hello when the protocol version is supported."""

    type: Literal["welcome"] = Field(description='Message kind. Always "welcome".')
    protocol: WireVersion = Field(
        description="Protocol version the backend agreed to speak. Always 1 today."
    )


class SpeakArgs(_Closed):
    """Arguments for speak: between 1 and 8 lines, each between 1 and 256 characters.

    Without "to", the lines are normal proximity speech. With "to", the lines are private text
    for one player. The mod paces the lines itself.
    """

    lines: Annotated[list[SpeakLine], Field(min_length=1, max_length=MAX_SPEAK_LINES)] = Field(
        description=(
            "What he says, in order. The mod paces the lines itself. Between 1 and 8 lines, each "
            'between 1 and 256 characters. Example: ["Hello, traveller."].'
        )
    )

    # Not Optional on purpose: an explicit null must be rejected, and the default None still
    # applies when the key is absent.
    to: PlayerUuid = Field(
        default=None,
        description=(
            "Private recipient. Set only for speech meant for one player: the lines reach that "
            "player alone, at any distance, and are never spoken aloud. Omit it for normal "
            "speech nearby. Explicit null is rejected. "
            'Example: "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69".'
        ),
    )  # type: ignore[assignment]


class SpeakAction(_Envelope):
    """Backend asks the mod to speak a short sequence of lines as one action."""

    type: Literal["action"] = Field(description='Message kind. Always "action".')
    request_id: ActionId = Field(
        description=(
            "Generated by the backend: act_ followed by 16 random hex characters. Unique per "
            "action; every action_status for this action echoes it. "
            "Example: act_3fa9c01b7e2d4f60."
        )
    )
    action: Literal["speak"] = Field(
        description='Name of the allowlisted action. Only "speak" exists today.'
    )
    args: SpeakArgs = Field(description="Arguments for speak. See SpeakArgs.")
    timeout_ms: Annotated[int, Field(ge=MIN_TIMEOUT_MS, le=MAX_TIMEOUT_MS)] = Field(
        description=(
            "How long the mod may take before the action is reported as failed with reason "
            "timeout, in milliseconds. Between 100 and 120000. Example: 30000."
        )
    )

    # Not Optional on purpose: an explicit null must be rejected, and the default None still
    # applies when the key is absent.
    conversation_id: ConversationId = Field(
        default=None,
        description=(
            "Groups this speech with a conversation that the mod is tracking. Generated by the "
            "mod, see PlayerChatEvent. Omit it when the speech is not part of one. Explicit null "
            "is rejected."
        ),
    )  # type: ignore[assignment]

    # Same reasoning as conversation_id: explicit null is refused, absence means "not a reply".
    in_reply_to: EventId = Field(
        default=None,
        description=(
            "The player_chat event this speech answers. Set it only for a reply, so the mod does "
            "not count the speech as unprompted: replies skip the unprompted-speech cooldown. "
            "Omit it for speech the backend starts on its own. Explicit null is rejected. "
            "Example: evt_01HZX8K2M9."
        ),
    )  # type: ignore[assignment]


class _ActionStatusBase(_Envelope):
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


class PlayerChatData(_Closed):
    """Payload for player_chat: a player said something the mod chose to forward."""

    player_uuid: PlayerUuid = Field(
        description=(
            "Minecraft UUID of the player who spoke, in canonical 8-4-4-4-12 form. Set by the mod. "
            'Example: "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69".'
        )
    )
    player_name: Annotated[
        str, StringConstraints(min_length=1, max_length=MAX_PLAYER_NAME_LENGTH)
    ] = Field(description='The player\'s in-game name, 1 to 16 characters. Example: "Luciano".')
    text: Annotated[str, StringConstraints(min_length=1, max_length=MAX_CHAT_TEXT_LENGTH)] = Field(
        description=(
            "What the player typed, 1 to 256 characters. Set by the mod. Chat text is never "
            'logged at INFO. Example: "hi".'
        )
    )
    addressed: ChatAddress = Field(
        description=(
            "Why the mod forwarded this line. Set by the mod. "
            '"name": the player used his name. '
            '"conversation": the line continues a conversation the mod is tracking. '
            '"near": nearby speech without his name. '
            '"butt_in": the player talked over him while he was speaking. '
            '"private": the @m channel. The text is private and is never voice.'
        )
    )


class PlayerChatEvent(_Envelope):
    """Mod reports that a player spoke. No reply is expected; the backend decides whether to act."""

    type: Literal["event"] = Field(description='Message kind. Always "event".')
    event_id: EventId = Field(
        description=(
            "Generated by the mod: evt_ followed by 8 to 32 letters or digits. Unique per event. "
            "Example: evt_01HZX8K2M9."
        )
    )
    event: Literal["player_chat"] = Field(
        description='Name of the event. Only "player_chat" exists today.'
    )
    occurred_at: AwareDatetime = Field(
        description=(
            "When the mod saw the chat, from the mod's clock, in UTC with the Z suffix. "
            "Example: 2026-10-18T12:00:00Z."
        )
    )
    data: PlayerChatData = Field(description="Who said what. See PlayerChatData.")

    # Same reasoning as SpeakAction.conversation_id: explicit null is not allowed.
    conversation_id: ConversationId = Field(
        default=None,
        description=(
            "Generated by the mod: one id per conversation, new after 60 seconds of silence. "
            "Omit it when the chat is not part of a conversation. Explicit null is rejected. "
            "Example: conv_01HZX8K2M9."
        ),
    )  # type: ignore[assignment]


class Error(_Envelope):
    """Either side reports a message it cannot accept. The code set is closed for branching."""

    type: Literal["error"] = Field(description='Message kind. Always "error".')
    code: ErrorCode = Field(
        description=(
            "Why the message was refused, from a closed set the other side can branch on. "
            "bad_message: could not be parsed or broke a rule. "
            "unsupported_protocol: the protocol version is not supported. "
            "unauthorized: the token was wrong or missing. "
            "internal: the sender failed unexpectedly."
        )
    )
    detail: Annotated[str, StringConstraints(max_length=MAX_DETAIL_LENGTH)] = Field(
        description=(
            "Short human-readable explanation, up to 500 characters. It never repeats the "
            'rejected payload. Example: "action args do not match the speak schema".'
        )
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


Message = Annotated[
    Hello | Welcome | SpeakAction | ActionStatus | PlayerChatEvent | Error,
    Field(discriminator="type"),
]

MESSAGE_ADAPTER: Final[TypeAdapter[Message]] = TypeAdapter(Message)


def parse_message(raw: str | bytes) -> Message:
    """Parse one wire message. Raises pydantic.ValidationError when it breaks a rule."""

    # validate_json parses and checks in one pass, so a rejected message never becomes an object.
    return MESSAGE_ADAPTER.validate_json(raw)


def _drop_openapi_only_keys(node: Any) -> Any:
    """Remove pydantic's OpenAPI-only "discriminator" blocks and null defaults."""

    # JSON Schema 2020-12 has no "discriminator" keyword, and a null default on a string field
    # would mislead readers. oneOf plus Literal fields already enforce the same rules.
    if isinstance(node, dict):
        return {
            key: _drop_openapi_only_keys(value)
            for key, value in node.items()
            if key != "discriminator" and not (key == "default" and value is None)
        }

    if isinstance(node, list):
        return [_drop_openapi_only_keys(item) for item in node]

    return node


def protocol_json_schema() -> dict[str, Any]:
    """Return the JSON Schema for every message, as committed to shared/protocol.schema.json."""

    # The title, description and dialect are set here and not by pydantic, so the file stays
    # stable when models are renamed.
    schema = _drop_openapi_only_keys(MESSAGE_ADAPTER.json_schema())

    return {
        "$schema": JSON_SCHEMA_DIALECT,
        "title": SCHEMA_TITLE,
        "description": SCHEMA_DESCRIPTION,
        **schema,
    }
