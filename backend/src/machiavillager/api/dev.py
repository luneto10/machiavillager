"""Dev-only HTTP endpoints. Mounted only when MACHIAVILLAGER_DEV_ENDPOINTS is true."""

from typing import Annotated, Any, Final

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from machiavillager.api.bridge.requests import new_request_id
from machiavillager.application.ports import BridgeUnavailable, GameBridge
from machiavillager.domain.protocol import (
    MAX_SPEAK_LINES,
    MAX_TIMEOUT_MS,
    MIN_TIMEOUT_MS,
    PROTOCOL_VERSION,
    SpeakAction,
    SpeakArgs,
    SpeakLine,
)

# Long enough for a few lines to be paced by the mod, short enough to fail fast while testing.
# The protocol maximum: 8 long lines paced by reading time can take close to two minutes, so a
# shorter default would report a timeout while he is still talking.
DEFAULT_TIMEOUT_MS: Final = MAX_TIMEOUT_MS

router = APIRouter(prefix="/dev", tags=["dev"])


class SpeakRequest(BaseModel):
    """Lines for him to say, and how long the mod may take before the request gives up."""

    lines: list[SpeakLine] = Field(min_length=1, max_length=MAX_SPEAK_LINES)
    timeout_ms: int = Field(default=DEFAULT_TIMEOUT_MS, ge=MIN_TIMEOUT_MS, le=MAX_TIMEOUT_MS)


def get_game_bridge(request: Request) -> GameBridge:
    # Looked up per request, so the route uses the one bridge that create_app stored.
    bridge: GameBridge = request.app.state.game_bridge
    return bridge


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
