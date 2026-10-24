"""The bridge's only route: auth, accept, handshake, take the slot, serve, release."""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect, status
from fastapi.security import HTTPAuthorizationCredentials

from machiavillager.api.bridge.auth import bearer, refusal_reason
from machiavillager.api.bridge.connections import ModConnections
from machiavillager.api.bridge.session import handshake, serve
from machiavillager.infrastructure.settings import Settings, get_settings

logger = logging.getLogger(__name__)

router = APIRouter()


@router.websocket("/bridge")
async def bridge(
    websocket: WebSocket,
    settings: Annotated[Settings, Depends(get_settings)],
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> None:
    # Auth runs before accept: a refused client never gets a session it could use.
    reason = refusal_reason(credentials, settings.bridge_token)
    if reason is not None:
        logger.info("bridge connection refused: %s", reason)
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()
    connections: ModConnections = websocket.app.state.mod_connections
    try:
        # Only a mod that completed the handshake may replace the current one.
        if not await handshake(websocket):
            return
        await connections.take_over(websocket)
        await serve(websocket)
    except WebSocketDisconnect:
        logger.info("bridge connection closed")
    finally:
        connections.release(websocket)
