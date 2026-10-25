"""Requests the backend sent to the mod and still waits on, and the GameBridge that sends them."""

import asyncio
import logging
import secrets
from typing import Final

from fastapi import WebSocketDisconnect

from machiavillager.api.bridge.connections import ModConnections
from machiavillager.api.bridge.session import send
from machiavillager.application.ports import BridgeUnavailable
from machiavillager.domain.protocol import (
    PROTOCOL_VERSION,
    ActionStatus,
    Failed,
    SpeakAction,
    TerminalStatus,
    is_terminal,
)

logger = logging.getLogger(__name__)

REQUEST_ID_PREFIX: Final = "act_"
# 8 random bytes give 16 hex characters, inside the 8-32 the protocol pattern allows.
REQUEST_ID_BYTES: Final = 8
# Slack past the mod's own deadline, so the mod's timeout report can still arrive first.
TIMEOUT_MARGIN_SECONDS: float = 2.0
MS_PER_SECOND: Final = 1000
REASON_TIMEOUT: Final = "timeout"
REASON_DISCONNECTED: Final = "disconnected"
NO_MOD_MESSAGE: Final = "no mod is connected to the bridge"
# Errors that mean the socket went away while we were sending to it.
SEND_FAILURES: Final = (RuntimeError, OSError, WebSocketDisconnect)


def new_request_id() -> str:
    # token_hex gives a short id made of safe characters, matching the protocol pattern.
    return REQUEST_ID_PREFIX + secrets.token_hex(REQUEST_ID_BYTES)


def _failed(request_id: str, reason: str) -> Failed:
    return Failed(
        v=PROTOCOL_VERSION,
        type="action_status",
        request_id=request_id,
        status="failed",
        reason=reason,
    )


class PendingRequests:
    """Maps each request id to the future that the mod's terminal status will complete."""

    def __init__(self) -> None:
        self._futures: dict[str, asyncio.Future[TerminalStatus]] = {}

    def __len__(self) -> int:
        return len(self._futures)

    def create(self, request_id: str) -> asyncio.Future[TerminalStatus]:
        future: asyncio.Future[TerminalStatus] = asyncio.get_running_loop().create_future()
        self._futures[request_id] = future
        return future

    def discard(self, request_id: str) -> None:
        # Called on timeout so a late reply for that id finds nothing to resolve.
        self._futures.pop(request_id, None)

    def resolve(self, status: ActionStatus) -> None:
        # Progress statuses are not answers: the waiter keeps waiting for the terminal one.
        if not is_terminal(status):
            logger.info("bridge request %s is %s, still waiting", status.request_id, status.status)
            return
        future = self._futures.pop(status.request_id, None)
        if future is None:
            logger.info("bridge status for unknown request %s ignored", status.request_id)
            return
        if not future.done():
            future.set_result(status)

    def cancel_all(self, reason: str) -> None:
        # Every waiter must hear something, or its HTTP call would hang until its own timeout.
        for request_id, future in self._futures.items():
            if not future.done():
                future.set_result(_failed(request_id, reason))
        self._futures.clear()


class ModGameBridge:
    """GameBridge adapter: sends an action over the one mod socket and waits for its result."""

    def __init__(self, connections: ModConnections, pending: PendingRequests) -> None:
        self._connections = connections
        self._pending = pending

    async def request(self, action: SpeakAction) -> TerminalStatus:
        websocket = self._connections.current()
        if websocket is None:
            raise BridgeUnavailable(NO_MOD_MESSAGE)

        # Register before sending: the mod may answer before send() returns.
        future = self._pending.create(action.request_id)
        try:
            await send(websocket, action)
        except SEND_FAILURES as error:
            self._pending.discard(action.request_id)
            raise BridgeUnavailable(NO_MOD_MESSAGE) from error

        wait_seconds = action.timeout_ms / MS_PER_SECOND + TIMEOUT_MARGIN_SECONDS
        try:
            return await asyncio.wait_for(future, timeout=wait_seconds)
        except TimeoutError:
            logger.info("bridge request %s timed out", action.request_id)
            self._pending.discard(action.request_id)
            return _failed(action.request_id, REASON_TIMEOUT)
