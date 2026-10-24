"""The one mod connection slot. A newer connection replaces the older one."""

import logging
from typing import Final

from fastapi import WebSocket, WebSocketDisconnect

logger = logging.getLogger(__name__)

# 1000 is a normal close: the mod did nothing wrong, it was just replaced.
NORMAL_CLOSE_CODE: Final = 1000
REPLACED_REASON: Final = "replaced"


class ModConnections:
    """Holds the one mod connection allowed right now. A newer connection replaces the older."""

    def __init__(self) -> None:
        self._current: WebSocket | None = None

    def current(self) -> WebSocket | None:
        return self._current

    async def take_over(self, websocket: WebSocket) -> None:
        previous = self._current
        # Claim the slot before awaiting anything, so two connections never both think they own it.
        self._current = websocket
        if previous is not None:
            logger.info("bridge connection replaces an earlier one")
            await _close_replaced(previous)

    def release(self, websocket: WebSocket) -> None:
        # A replaced socket must not clear the slot its successor now holds.
        if self._current is websocket:
            self._current = None


async def _close_replaced(websocket: WebSocket) -> None:
    try:
        await websocket.close(code=NORMAL_CLOSE_CODE, reason=REPLACED_REASON)
    except (RuntimeError, OSError, WebSocketDisconnect):
        # The old mod may already be gone; that is the outcome we wanted anyway.
        logger.debug("replaced bridge connection was already closed")
