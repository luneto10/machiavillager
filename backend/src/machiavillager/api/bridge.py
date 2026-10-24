"""WebSocket endpoint the Minecraft mod connects to.

Wire rules: docs/decisions/0004-bridge-protocol.md.
"""

import asyncio
import json
import logging
import secrets
from typing import Annotated, Final

from fastapi import APIRouter, Depends, Header, WebSocket, WebSocketDisconnect, status
from pydantic import ValidationError

from machiavillager.domain.protocol import (
    MAX_DETAIL_LENGTH,
    PROTOCOL_VERSION,
    Error,
    ErrorCode,
    Hello,
    Message,
    Welcome,
    parse_message,
)
from machiavillager.infrastructure.settings import Settings, get_settings

logger = logging.getLogger(__name__)

router = APIRouter()

# Seconds the mod has to send hello after connecting. A module global, so tests can shorten it.
HELLO_TIMEOUT_SECONDS: float = 5.0
BEARER_SCHEME: Final = "Bearer"
# 1000 is a normal close: the mod did nothing wrong, it was just replaced.
NORMAL_CLOSE_CODE: Final = 1000
REPLACED_REASON: Final = "replaced"
# Ids that identify a message in the log. Payloads are never logged: they hold player chat.
LOGGED_ID_FIELDS: Final = ("request_id", "event_id")


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


def _refusal_reason(authorization: str | None, settings: Settings) -> str | None:
    """Return why the connection must be refused, or None when the token is accepted."""

    expected = settings.bridge_token

    # Fail closed: with no token configured, the bridge is off for everyone.
    if expected is None or not expected.get_secret_value():
        return "no token configured"

    if authorization is None:
        return "missing Authorization header"

    scheme, _, token = authorization.partition(" ")
    if scheme != BEARER_SCHEME or not token:
        return "malformed Authorization header"

    # Constant-time comparison, so response timing cannot reveal how much of the token matched.
    if not secrets.compare_digest(token.encode(), expected.get_secret_value().encode()):
        return "wrong token"

    return None


def _describe(error: ValidationError) -> str:
    # errors() without inputs: a rejected payload is never echoed back to the mod.
    first = error.errors(include_input=False)[0]
    where = ".".join(str(part) for part in first["loc"]) or "message"
    return f"{where}: {first['msg']}"[:MAX_DETAIL_LENGTH]


def _is_unsupported_protocol(raw: str) -> bool:
    # The model only accepts protocol 1, so a newer mod's hello fails parsing. Peek at the raw
    # JSON to report the real cause instead of a generic bad_message.
    try:
        data = json.loads(raw)
    except ValueError:
        return False
    return (
        isinstance(data, dict)
        and data.get("type") == "hello"
        and data.get("protocol") != PROTOCOL_VERSION
    )


def _log_message(direction: str, message: Message) -> None:
    parts = [direction, message.type]
    for name in LOGGED_ID_FIELDS:
        value = getattr(message, name, None)
        if value is not None:
            parts.append(f"{name}={value}")
    logger.info("bridge %s", " ".join(parts))


async def _send(websocket: WebSocket, message: Message) -> None:
    _log_message("out", message)
    # exclude_none: the contract rejects explicit nulls, so absent optional fields must be omitted.
    await websocket.send_text(message.model_dump_json(exclude_none=True))


async def _send_error(websocket: WebSocket, code: ErrorCode, detail: str) -> None:
    await _send(websocket, Error(v=PROTOCOL_VERSION, type="error", code=code, detail=detail))


async def _reject_and_close(websocket: WebSocket, code: ErrorCode, detail: str) -> None:
    await _send_error(websocket, code, detail)
    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)


async def _handshake(websocket: WebSocket) -> bool:
    """Wait for hello and answer welcome. Returns False when the connection was closed."""

    try:
        raw = await asyncio.wait_for(websocket.receive_text(), timeout=HELLO_TIMEOUT_SECONDS)
    except TimeoutError:
        logger.info("bridge connection closed: no hello in time")
        await _reject_and_close(websocket, "bad_message", "no hello received in time")
        return False

    try:
        message = parse_message(raw)
    except ValidationError as error:
        if _is_unsupported_protocol(raw):
            await _reject_and_close(websocket, "unsupported_protocol", "protocol 1 only")
        else:
            await _reject_and_close(websocket, "bad_message", _describe(error))
        return False

    if not isinstance(message, Hello):
        await _reject_and_close(websocket, "bad_message", "first message must be hello")
        return False

    await _send(websocket, Welcome(v=PROTOCOL_VERSION, type="welcome", protocol=PROTOCOL_VERSION))
    return True


async def _serve(websocket: WebSocket) -> None:
    """Read messages until the mod leaves. A bad message is answered, not fatal."""

    while True:
        raw = await websocket.receive_text()
        try:
            message = parse_message(raw)
        except ValidationError as error:
            await _send_error(websocket, "bad_message", _describe(error))
            continue
        _log_message("in", message)


@router.websocket("/bridge")
async def bridge(
    websocket: WebSocket,
    settings: Annotated[Settings, Depends(get_settings)],
    authorization: Annotated[str | None, Header()] = None,
) -> None:
    # Auth runs before accept: a refused client never gets a session it could use.
    reason = _refusal_reason(authorization, settings)
    if reason is not None:
        logger.info("bridge connection refused: %s", reason)
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()
    connections: ModConnections = websocket.app.state.mod_connections
    try:
        # Only a mod that completed the handshake may replace the current one.
        if not await _handshake(websocket):
            return
        await connections.take_over(websocket)
        await _serve(websocket)
    except WebSocketDisconnect:
        logger.info("bridge connection closed")
    finally:
        connections.release(websocket)
