"""Dev-only HTTP endpoints. Mounted only when MACHIAVILLAGER_DEV_ENDPOINTS is true."""

import secrets
from datetime import UTC, datetime
from typing import Annotated, Any, Final
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field, StringConstraints

from machiavillager.api.bridge.events import handle_event
from machiavillager.api.bridge.ids import new_request_id
from machiavillager.application.ports import BridgeUnavailable, GameBridge
from machiavillager.domain.protocol import (
    MAX_CHAT_TEXT_LENGTH,
    MAX_PLAYER_NAME_LENGTH,
    MAX_SPEAK_LINES,
    MAX_TIMEOUT_MS,
    MIN_TIMEOUT_MS,
    PROTOCOL_VERSION,
    ChatAddress,
    ConversationId,
    PlayerChatData,
    PlayerChatEvent,
    PlayerUuid,
    SpeakAction,
    SpeakArgs,
    SpeakLine,
)

# Long enough for a few lines to be paced by the mod, short enough to fail fast while testing.
# The protocol maximum: 8 long lines paced by reading time can take close to two minutes, so a
# shorter default would report a timeout while he is still talking.
DEFAULT_TIMEOUT_MS: Final = MAX_TIMEOUT_MS
# A fixed fake player, so a simulated chat looks the same from one run to the next.
DEV_PLAYER_UUID: Final = UUID("00000000-0000-4000-8000-000000000001")
EVENT_ID_PREFIX: Final = "evt_"
# 8 random bytes give 16 hex characters, inside the 8-32 the protocol pattern allows.
EVENT_ID_BYTES: Final = 8

router = APIRouter(prefix="/dev", tags=["dev"])


class SpeakRequest(BaseModel):
    """Lines for him to say, and how long the mod may take before the request gives up."""

    lines: list[SpeakLine] = Field(min_length=1, max_length=MAX_SPEAK_LINES)
    timeout_ms: int = Field(default=DEFAULT_TIMEOUT_MS, ge=MIN_TIMEOUT_MS, le=MAX_TIMEOUT_MS)


class ChatRequest(BaseModel):
    """A chat line as if a player had typed it. The address says how the mod would have sent it."""

    player_name: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PLAYER_NAME_LENGTH)]
    text: Annotated[str, StringConstraints(min_length=1, max_length=MAX_CHAT_TEXT_LENGTH)]
    addressed: ChatAddress = "name"
    player_uuid: PlayerUuid = DEV_PLAYER_UUID
    conversation_id: ConversationId | None = None


def get_game_bridge(request: Request) -> GameBridge:
    # Looked up per request, so the route uses the one bridge that create_app stored.
    bridge: GameBridge = request.app.state.game_bridge
    return bridge


def new_event_id() -> str:
    # Made here because the mod normally makes it. Same shape, so the contract accepts it.
    return EVENT_ID_PREFIX + secrets.token_hex(EVENT_ID_BYTES)


@router.post(
    "/speak",
    summary="Make him say these lines",
    description=(
        "Dev only. Sends one speak action to the connected mod and waits for its final status. "
        "Answers 503 when no mod is connected."
    ),
    responses={503: {"description": "No mod is connected to the bridge"}},
)
async def speak(
    body: SpeakRequest,
    bridge: Annotated[GameBridge, Depends(get_game_bridge)],
) -> dict[str, Any]:
    # Built here, not by the caller: the id must be unique and the envelope must be valid.
    action = SpeakAction(
        v=PROTOCOL_VERSION,
        type="action",
        request_id=new_request_id(),
        action="speak",
        args=SpeakArgs(lines=body.lines),
        timeout_ms=body.timeout_ms,
    )
    try:
        status = await bridge.request(action)
    except BridgeUnavailable as error:
        raise HTTPException(status_code=503, detail=str(error)) from error

    # exclude_none: the wire rejects explicit nulls, and the reply should look like the contract.
    return status.model_dump(mode="json", exclude_none=True)


@router.post(
    "/chat",
    status_code=202,
    summary="Simulate a player's chat line",
    description=(
        "Dev only. Builds a player_chat event and hands it to the same handler a real mod event "
        "reaches. With MACHIAVILLAGER_DEV_ECHO on, he repeats the line back."
    ),
)
async def chat(body: ChatRequest, request: Request) -> dict[str, str]:
    # Optional fields are left out instead of passed as None: the models are strict.
    event_fields: dict[str, Any] = {}
    if body.conversation_id is not None:
        event_fields["conversation_id"] = body.conversation_id

    event = PlayerChatEvent(
        v=PROTOCOL_VERSION,
        type="event",
        event_id=new_event_id(),
        event="player_chat",
        occurred_at=datetime.now(UTC),
        data=PlayerChatData(
            player_uuid=body.player_uuid,
            player_name=body.player_name,
            text=body.text,
            addressed=body.addressed,
        ),
        **event_fields,
    )
    await handle_event(event, request.app.state)
    return {"event_id": event.event_id}
