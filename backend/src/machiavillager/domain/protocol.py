"""Bridge protocol between the Minecraft mod and the backend.

These models are the source of truth. shared/protocol.schema.json is generated from them by
scripts/export_protocol_schema.py, and the mod's Java tests read that file, so both sides
enforce the same rules. Design and wire rules: docs/decisions/0004-bridge-protocol.md.
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

    v: WireVersion


class Hello(_Envelope):
    """Sent by the mod after connecting. Carries its mod version and the protocol it speaks."""

    type: Literal["hello"]
    mod_version: Annotated[str, StringConstraints(min_length=1, max_length=MAX_MOD_VERSION_LENGTH)]
    protocol: WireVersion


class Welcome(_Envelope):
    """Sent by the backend in answer to hello when the protocol version is supported."""

    type: Literal["welcome"]
    protocol: WireVersion


class SpeakArgs(_Closed):
    """Arguments for speak: between 1 and 8 lines, each between 1 and 256 characters.

    The mod paces the lines itself.
    """

    lines: Annotated[list[SpeakLine], Field(min_length=1, max_length=MAX_SPEAK_LINES)]


class SpeakAction(_Envelope):
    """Backend asks the mod to speak a short sequence of lines as one action."""

    type: Literal["action"]
    request_id: ActionId
    action: Literal["speak"]
    args: SpeakArgs
    timeout_ms: Annotated[int, Field(ge=MIN_TIMEOUT_MS, le=MAX_TIMEOUT_MS)]

    # Not Optional on purpose: an explicit null must be rejected, and the default None still
    # applies when the key is absent.
    conversation_id: ConversationId = None  # type: ignore[assignment]


class _ActionStatusBase(_Envelope):
    """Base for action_status messages. The status field selects the concrete model."""

    type: Literal["action_status"]
    request_id: ActionId


class Accepted(_ActionStatusBase):
    """The mod took the request and will try to run it."""

    status: Literal["accepted"]


class Running(_ActionStatusBase):
    """The mod has started performing the action."""

    status: Literal["running"]


class Succeeded(_ActionStatusBase):
    """The action finished. Nothing else to report."""

    status: Literal["succeeded"]


class Failed(_ActionStatusBase):
    """The action could not be completed. The reason says why."""

    status: Literal["failed"]
    reason: Reason


class Rejected(_ActionStatusBase):
    """The mod refused the request before starting it. The reason says why."""

    status: Literal["rejected"]
    reason: Reason


class Interrupted(_ActionStatusBase):
    """The action started but stopped early. Reports how many lines were spoken."""

    status: Literal["interrupted"]
    spoken: Annotated[int, Field(ge=0)]
    of: Annotated[int, Field(ge=1, le=MAX_SPEAK_LINES)]


class PlayerChatData(_Closed):
    """Payload for player_chat: a player said something in normal chat."""

    player_uuid: PlayerUuid
    player_name: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PLAYER_NAME_LENGTH)]
    text: Annotated[str, StringConstraints(min_length=1, max_length=MAX_CHAT_TEXT_LENGTH)]


class PlayerChatEvent(_Envelope):
    """Mod reports that a player spoke in chat. No reply is expected."""

    type: Literal["event"]
    event_id: EventId
    event: Literal["player_chat"]
    occurred_at: AwareDatetime
    data: PlayerChatData

    # Same reasoning as SpeakAction.conversation_id: explicit null is not allowed.
    conversation_id: ConversationId = None  # type: ignore[assignment]


class Error(_Envelope):
    """Either side reports a message it cannot accept. The code set is closed for branching."""

    type: Literal["error"]
    code: ErrorCode
    detail: Annotated[str, StringConstraints(max_length=MAX_DETAIL_LENGTH)]


# Nested discriminators: "type" selects the message, and action_status then selects the status.
ActionStatus = Annotated[
    Accepted | Running | Succeeded | Failed | Rejected | Interrupted,
    Field(discriminator="status"),
]

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
