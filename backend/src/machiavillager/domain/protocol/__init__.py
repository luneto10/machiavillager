"""Bridge protocol between the Minecraft mod and the backend.

These models are the source of truth. shared/protocol.schema.json and shared/PROTOCOL.md are
generated from them by scripts/export_protocol_schema.py, and the invalid examples are generated
from the same rules. The mod's Java tests read the shared files, so both sides enforce the same
rules. Design and wire rules: docs/decisions/0004-bridge-protocol.md.

The package is split by message kind: handshake.py, actions.py, statuses.py, events.py and
errors.py hold the models, common.py the shared pieces. New actions go in actions.py and new
events in events.py; then add them to the Message union below.
"""

from typing import Annotated, Any, Final

from pydantic import Field, TypeAdapter

from machiavillager.domain.protocol.actions import SpeakAction, SpeakArgs
from machiavillager.domain.protocol.common import (
    ACTION_ID_PATTERN,
    CANONICAL_UUID_PATTERN,
    CONVERSATION_ID_PATTERN,
    EVENT_ID_PATTERN,
    JSON_SCHEMA_DIALECT,
    MAX_CHAT_TEXT_LENGTH,
    MAX_DETAIL_LENGTH,
    MAX_LATENCY_MS,
    MAX_LINE_LENGTH,
    MAX_MOD_VERSION_LENGTH,
    MAX_MODEL_NAME_LENGTH,
    MAX_MODELS,
    MAX_PLAYER_NAME_LENGTH,
    MAX_REASON_LENGTH,
    MAX_SPEAK_LINES,
    MAX_TIMEOUT_MS,
    MIN_TIMEOUT_MS,
    PROTOCOL_VERSION,
    SCHEMA_DESCRIPTION,
    SCHEMA_TITLE,
    ActionId,
    ChatAddress,
    ConversationId,
    ErrorCode,
    EventId,
    PlayerUuid,
    Reason,
    SpeakLine,
    WireVersion,
)
from machiavillager.domain.protocol.errors import Error
from machiavillager.domain.protocol.events import (
    DevModelData,
    DevModelEvent,
    ModEvent,
    NpcSnapshot,
    PlayerChatData,
    PlayerChatEvent,
)
from machiavillager.domain.protocol.handshake import Hello, Welcome
from machiavillager.domain.protocol.statuses import (
    TERMINAL_STATUS_TYPES,
    Accepted,
    ActionStatus,
    Failed,
    Interrupted,
    Rejected,
    Running,
    Succeeded,
    TerminalStatus,
    is_terminal,
)

__all__ = [
    "ACTION_ID_PATTERN",
    "CANONICAL_UUID_PATTERN",
    "CONVERSATION_ID_PATTERN",
    "DevModelData",
    "DevModelEvent",
    "EVENT_ID_PATTERN",
    "JSON_SCHEMA_DIALECT",
    "MAX_CHAT_TEXT_LENGTH",
    "MAX_DETAIL_LENGTH",
    "MAX_LATENCY_MS",
    "MAX_LINE_LENGTH",
    "MAX_MODEL_NAME_LENGTH",
    "MAX_MODELS",
    "MAX_MOD_VERSION_LENGTH",
    "MAX_PLAYER_NAME_LENGTH",
    "MAX_REASON_LENGTH",
    "MAX_SPEAK_LINES",
    "MAX_TIMEOUT_MS",
    "MESSAGE_ADAPTER",
    "MIN_TIMEOUT_MS",
    "PROTOCOL_VERSION",
    "SCHEMA_DESCRIPTION",
    "SCHEMA_TITLE",
    "TERMINAL_STATUS_TYPES",
    "Accepted",
    "ActionId",
    "ActionStatus",
    "ChatAddress",
    "ConversationId",
    "Error",
    "ErrorCode",
    "EventId",
    "Failed",
    "Hello",
    "Interrupted",
    "Message",
    "ModEvent",
    "NpcSnapshot",
    "PlayerChatData",
    "PlayerChatEvent",
    "PlayerUuid",
    "Reason",
    "Rejected",
    "Running",
    "SpeakAction",
    "SpeakArgs",
    "SpeakLine",
    "Succeeded",
    "TerminalStatus",
    "Welcome",
    "WireVersion",
    "is_terminal",
    "parse_message",
    "protocol_json_schema",
]

Message = Annotated[
    Hello | Welcome | SpeakAction | ActionStatus | ModEvent | Error,
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
