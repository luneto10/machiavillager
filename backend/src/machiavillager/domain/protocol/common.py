"""Shared protocol pieces: limits, id patterns, reusable field types, base models."""

import re
from typing import Annotated, Final, Literal
from uuid import UUID

from pydantic import (
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    StringConstraints,
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
MAX_NEARBY_PLAYERS: Final = 16
# Dev info only: the model that wrote a reply, and the models the backend can switch to.
MAX_MODEL_NAME_LENGTH: Final = 80
MAX_MODELS: Final = 16
MAX_LATENCY_MS: Final = 600_000
# Affinity and independence share the mod's 0..100 scale.
MIN_RELATIONSHIP_SCORE: Final = 0
MAX_RELATIONSHIP_SCORE: Final = 100

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
# Mod's Phase and Mood enums, lowercased.
NpcPhase = Literal["partner", "seeds", "proposal", "regime", "war"]
NpcMood = Literal["calm", "bored", "irritated"]
# Minecraft names use letters, digits and underscore only. The pattern also keeps a name from
# carrying markup into the prompt, where it is written unescaped.
# provider:model, e.g. groq:qwen/qwen3.8-27b. Only the length is checked: ids are free text.
ModelName = Annotated[str, StringConstraints(min_length=1, max_length=MAX_MODEL_NAME_LENGTH)]
PlayerName = Annotated[
    str,
    StringConstraints(min_length=1, max_length=MAX_PLAYER_NAME_LENGTH, pattern=r"^[A-Za-z0-9_]+$"),
]


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


class ClosedModel(BaseModel):
    """Base for every bridge model. Unknown keys are rejected and values are immutable."""

    # extra="forbid" becomes additionalProperties false. strict stops pydantic from coercing
    # "30000" into an int, which the JSON Schema would reject.
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)


class EnvelopeModel(ClosedModel):
    """Base for top-level messages. Every message carries the wire version."""

    v: WireVersion = Field(
        description="Wire version of the message. Always 1. A breaking change bumps it."
    )
