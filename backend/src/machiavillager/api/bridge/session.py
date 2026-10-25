"""Protocol for one mod socket: the hello handshake, then serving messages until it leaves."""

import asyncio
import json
import logging
from typing import TYPE_CHECKING, Final

from fastapi import WebSocket, status
from pydantic import ValidationError

from machiavillager.api.bridge.events import handle_event
from machiavillager.domain.protocol import (
    MAX_DETAIL_LENGTH,
    PROTOCOL_VERSION,
    Error,
    ErrorCode,
    Hello,
    Message,
    PlayerChatEvent,
    Welcome,
    parse_message,
)

if TYPE_CHECKING:
    # Typing only: requests imports this module at runtime, so a real import here would cycle.
    from machiavillager.api.bridge.requests import PendingRequests

logger = logging.getLogger(__name__)

# Seconds the mod has to send hello after connecting. A module global, so tests can shorten it.
HELLO_TIMEOUT_SECONDS: float = 5.0
# Ids that identify a message in the log. Payloads are never logged: they hold player chat.
LOGGED_ID_FIELDS: Final = ("request_id", "event_id")


def describe(error: ValidationError) -> str:
    # errors() without inputs: a rejected payload is never echoed back to the mod.
    first = error.errors(include_input=False)[0]
    where = ".".join(str(part) for part in first["loc"]) or "message"
    return f"{where}: {first['msg']}"[:MAX_DETAIL_LENGTH]


def is_unsupported_protocol(raw: str) -> bool:
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


def log_message(direction: str, message: Message) -> None:
    parts = [direction, message.type]
    for name in LOGGED_ID_FIELDS:
        value = getattr(message, name, None)
        if value is not None:
            parts.append(f"{name}={value}")
    logger.info("bridge %s", " ".join(parts))


async def send(websocket: WebSocket, message: Message) -> None:
    log_message("out", message)
    # exclude_none: the contract rejects explicit nulls, so absent optional fields must be omitted.
    await websocket.send_text(message.model_dump_json(exclude_none=True))


async def send_error(websocket: WebSocket, code: ErrorCode, detail: str) -> None:
    await send(websocket, Error(v=PROTOCOL_VERSION, type="error", code=code, detail=detail))


async def reject_and_close(websocket: WebSocket, code: ErrorCode, detail: str) -> None:
    await send_error(websocket, code, detail)
    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)


async def handshake(websocket: WebSocket) -> bool:
    """Wait for hello and answer welcome. Returns False when the connection was closed."""

    try:
        raw = await asyncio.wait_for(websocket.receive_text(), timeout=HELLO_TIMEOUT_SECONDS)
    except TimeoutError:
        logger.info("bridge connection closed: no hello in time")
        await reject_and_close(websocket, "bad_message", "no hello received in time")
        return False

    try:
        message = parse_message(raw)
    except ValidationError as error:
        if is_unsupported_protocol(raw):
            await reject_and_close(websocket, "unsupported_protocol", "protocol 1 only")
        else:
            await reject_and_close(websocket, "bad_message", describe(error))
        return False

    if not isinstance(message, Hello):
        await reject_and_close(websocket, "bad_message", "first message must be hello")
        return False

    await send(websocket, Welcome(v=PROTOCOL_VERSION, type="welcome", protocol=PROTOCOL_VERSION))
    return True


async def serve(websocket: WebSocket, pending: "PendingRequests") -> None:
    """Read messages until the mod leaves. A bad message is answered, not fatal."""

    while True:
        raw = await websocket.receive_text()
        try:
            message = parse_message(raw)
        except ValidationError as error:
            await send_error(websocket, "bad_message", describe(error))
            continue
        log_message("in", message)

        # Only action_status answers a request we sent. A chat event goes to the event handler,
        # which never waits, so this loop keeps reading the statuses that follow.
        if message.type == "action_status":
            pending.resolve(message)
        elif isinstance(message, PlayerChatEvent):
            await handle_event(message, websocket.app.state)
