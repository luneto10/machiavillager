"""What the backend does with each event the mod reports.

The socket loop and the dev routes both call handle_event, so a simulated chat takes the same path
as a real one.
"""

import asyncio
import logging
from typing import Any, Final

from starlette.datastructures import State

from machiavillager.api.bridge.ids import new_request_id
from machiavillager.application.ports import BridgeUnavailable, GameBridge
from machiavillager.domain.protocol import (
    MAX_LINE_LENGTH,
    MAX_TIMEOUT_MS,
    PROTOCOL_VERSION,
    PlayerChatEvent,
    SpeakAction,
    SpeakArgs,
)

logger = logging.getLogger(__name__)

# Marks the echoed line, so a tester can tell the echo from speech the backend really wants.
ECHO_PREFIX: Final = "(echo) "
# The mod paces the line itself, so the longest allowed wait is the safe choice for a test.
ECHO_TIMEOUT_MS: Final = MAX_TIMEOUT_MS
# asyncio keeps only weak references to tasks. This set keeps the echo tasks alive until they end.
_echo_tasks: set[asyncio.Task[None]] = set()


async def handle_event(event: PlayerChatEvent, app_state: State) -> None:
    """Handle one event from the mod. Returns at once, so the socket loop keeps reading."""

    # Ids and the address only: the chat text itself is never logged at INFO.
    logger.info("player_chat event_id=%s addressed=%s", event.event_id, event.data.addressed)
    if not app_state.dev_echo:
        return

    # The echo waits for the mod's status, and that status arrives on the same socket this loop
    # reads. Awaiting the echo here would deadlock, so it runs as its own task.
    task = asyncio.create_task(_answer_with_echo(event, app_state.game_bridge))
    _echo_tasks.add(task)
    task.add_done_callback(_echo_tasks.discard)


def echo_action(event: PlayerChatEvent) -> SpeakAction:
    """Build the speak action that repeats the player's line back."""

    line = (ECHO_PREFIX + event.data.text)[:MAX_LINE_LENGTH]

    # Optional fields are left out instead of passed as None: the models are strict, and an
    # explicit None is refused by the contract.
    args_fields: dict[str, Any] = {"lines": [line]}
    if event.data.addressed == "private":
        args_fields["to"] = event.data.player_uuid
    action_fields: dict[str, Any] = {}
    if event.conversation_id is not None:
        action_fields["conversation_id"] = event.conversation_id

    # The echo answers the chat line, so it is a reply: the mod does not count it as unprompted.
    return SpeakAction(
        v=PROTOCOL_VERSION,
        type="action",
        request_id=new_request_id(),
        action="speak",
        args=SpeakArgs(**args_fields),
        timeout_ms=ECHO_TIMEOUT_MS,
        in_reply_to=event.event_id,
        **action_fields,
    )


async def _answer_with_echo(event: PlayerChatEvent, bridge: GameBridge) -> None:
    action = echo_action(event)
    try:
        status = await bridge.request(action)
    except BridgeUnavailable:
        logger.info("dev echo %s not sent: no mod is connected", action.request_id)
        return
    logger.info("dev echo %s finished: %s", action.request_id, status.status)
