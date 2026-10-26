"""What the backend does with each event the mod reports.

The socket loop and the dev routes both call handle_event, so a simulated chat takes the same path
as a real one.
"""

import asyncio
import logging
from collections.abc import Coroutine
from typing import Any, Final

from starlette.datastructures import State

from machiavillager.api.bridge.ids import new_request_id
from machiavillager.application.handle_player_chat import HandlePlayerChat, reply_action
from machiavillager.application.ports import BridgeUnavailable, GameBridge
from machiavillager.domain.protocol import (
    MAX_LINE_LENGTH,
    MAX_TIMEOUT_MS,
    PlayerChatEvent,
    SpeakAction,
)

logger = logging.getLogger(__name__)

# Marks the echoed line, so a tester can tell the echo from speech the backend really wants.
ECHO_PREFIX: Final = "(echo) "
# The mod paces the line itself, so the longest allowed wait is the safe choice for a test.
ECHO_TIMEOUT_MS: Final = MAX_TIMEOUT_MS
# asyncio keeps only weak references to tasks. This set keeps the reply tasks alive until they end.
_reply_tasks: set[asyncio.Task[None]] = set()


async def handle_event(event: PlayerChatEvent, app_state: State) -> None:
    """Handle one event from the mod. Returns at once, so the socket loop keeps reading."""

    # Ids and the address only: the chat text itself is never logged at INFO.
    logger.info("player_chat event_id=%s addressed=%s", event.event_id, event.data.addressed)

    # The dev echo wins: it is an explicit dev choice, so it never shares the AI reply's path.
    if app_state.dev_echo:
        _spawn(_answer_with_echo(event, app_state.game_bridge))
    elif app_state.handle_player_chat is not None:
        _spawn(_reply_to_chat(app_state.handle_player_chat, event))


def _spawn(work: Coroutine[Any, Any, None]) -> None:
    # The reply waits for the mod's status, and that status arrives on the same socket this loop
    # reads. Awaiting it here would deadlock, so it runs as its own task.
    task = asyncio.create_task(work)
    _reply_tasks.add(task)
    task.add_done_callback(_reply_tasks.discard)


async def _reply_to_chat(handler: HandlePlayerChat, event: PlayerChatEvent) -> None:
    try:
        await handler(event)
    except Exception as error:
        # Only the type name: the error text can hold player words or provider details.
        logger.warning("player chat reply failed: %s", type(error).__name__)


def echo_action(event: PlayerChatEvent) -> SpeakAction:
    """Build the speak action that repeats the player's line back."""

    # Same shape as an AI reply (private stays private, it answers the line), only the text differs.
    line = (ECHO_PREFIX + event.data.text)[:MAX_LINE_LENGTH]
    return reply_action(event, [line], new_request_id())


async def _answer_with_echo(event: PlayerChatEvent, bridge: GameBridge) -> None:
    action = echo_action(event)
    try:
        status = await bridge.request(action)
    except BridgeUnavailable:
        logger.info("dev echo %s not sent: no mod is connected", action.request_id)
        return
    logger.info("dev echo %s finished: %s", action.request_id, status.status)
