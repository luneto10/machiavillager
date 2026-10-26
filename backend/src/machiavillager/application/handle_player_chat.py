"""Answers one player_chat: builds the prompt, asks the writer, sends the speak action back."""

import logging
from collections.abc import Callable
from typing import Any, Final

from machiavillager.application.conversation_store import ConversationStore
from machiavillager.application.ports import BridgeUnavailable, GameBridge, LineWriter
from machiavillager.application.prompt_builder import (
    ConversationLine,
    ProfanityLevel,
    build_prompt,
)
from machiavillager.domain.protocol import (
    MAX_TIMEOUT_MS,
    PROTOCOL_VERSION,
    Interrupted,
    PlayerChatEvent,
    SpeakAction,
    SpeakArgs,
    Succeeded,
    TerminalStatus,
)

logger = logging.getLogger(__name__)

NPC_SPEAKER: Final = "Machiavillager"
# Marks a line he was talked over in the middle of, so the next reply knows it was cut short.
CUT_OFF_MARK: Final = " (cut off)"
IRRITATED_MOOD: Final = "irritated"


def conversation_key(event: PlayerChatEvent) -> str:
    """The thread a chat line belongs to: its conversation, or the player when there is none."""

    # Without a conversation id, each player keeps his own thread.
    if event.conversation_id is not None:
        return event.conversation_id
    return f"player:{event.data.player_uuid}"


def reply_action(event: PlayerChatEvent, lines: list[str], request_id: str) -> SpeakAction:
    """Build the speak action that answers a chat line. The AI reply and the dev echo share it."""

    # Optional fields are left out instead of passed as None: the models are strict, and an
    # explicit None is refused by the contract.
    args_fields: dict[str, Any] = {"lines": lines}
    if event.data.addressed == "private":
        args_fields["to"] = event.data.player_uuid
    action_fields: dict[str, Any] = {}
    if event.conversation_id is not None:
        action_fields["conversation_id"] = event.conversation_id

    # The reply answers the chat line, so the mod does not count it as unprompted speech.
    return SpeakAction(
        v=PROTOCOL_VERSION,
        type="action",
        request_id=request_id,
        action="speak",
        args=SpeakArgs(**args_fields),
        timeout_ms=MAX_TIMEOUT_MS,
        in_reply_to=event.event_id,
        **action_fields,
    )


class HandlePlayerChat:
    """Replies to a player's chat in character, remembering what he actually said."""

    def __init__(
        self,
        writer: LineWriter,
        bridge: GameBridge,
        store: ConversationStore,
        read_persona: Callable[[], str],
        profanity: ProfanityLevel,
        new_request_id: Callable[[], str],
        clock: Callable[[], float],
    ) -> None:
        self._writer = writer
        self._bridge = bridge
        self._store = store
        self._read_persona = read_persona
        self._profanity = profanity
        self._new_request_id = new_request_id
        self._clock = clock
        self._in_flight: set[str] = set()
        # One newest pending event per player; an older pending one is replaced by a newer one.
        self._pending: dict[str, PlayerChatEvent] = {}

    async def __call__(self, event: PlayerChatEvent) -> None:
        player = event.data.player_uuid

        # Every line is kept as it arrives, so the next reply reads the whole thread.
        self._store_line(event)

        # A spamming player cannot burn the model budget: one reply per player at a time.
        if player in self._in_flight:
            self._park(event)
            return

        self._in_flight.add(player)
        try:
            # Lines that arrived during a reply are answered in turn, one reply to the newest.
            current: PlayerChatEvent | None = event
            while current is not None:
                await self._reply(current)
                current = self._pending.pop(player, None)
        finally:
            self._in_flight.discard(player)

    def _store_line(self, event: PlayerChatEvent) -> None:
        """Add his chat line to its thread the moment it arrives."""

        # Stored once, here, so no reply adds the same line again.
        player_line = ConversationLine(event.data.player_name, event.data.text, from_npc=False)
        self._store.add(conversation_key(event), player_line, self._clock())

    def _park(self, event: PlayerChatEvent) -> None:
        """Hold a line that arrived mid-reply; only the newest held line gets a reply."""

        # The replaced line stays in the thread, so the coalesced reply still sees it.
        player = event.data.player_uuid
        older = self._pending.get(player)
        if older is not None:
            logger.info("reply superseded event_id=%s", older.event_id)
        self._pending[player] = event
        logger.info("reply queued event_id=%s", event.event_id)

    async def _reply(self, event: PlayerChatEvent) -> None:
        key = conversation_key(event)

        # The thread already ends with his newest line, so the prompt reads it from the store.
        history = self._store.recent(key, self._clock())

        npc = event.data.npc
        messages = build_prompt(
            self._read_persona(),
            event.data.player_name,
            history,
            npc,
            profanity=self._profanity,
            addressed=event.data.addressed,
        )
        irritated = npc is not None and npc.mood == IRRITATED_MOOD
        lines = await self._writer.write_reply(messages, irritated=irritated)

        # Silence is not a line, so nothing is sent or stored; the player's line is already kept.
        if not lines:
            logger.info("stayed silent event_id=%s", event.event_id)
            return

        action = reply_action(event, lines, self._new_request_id())
        try:
            status = await self._bridge.request(action)
        except BridgeUnavailable:
            logger.info("reply %s not sent: no mod is connected", action.request_id)
            return

        self._remember(key, lines, status)
        logger.info("reply %s finished: %s", action.request_id, status.status)

    def _remember(self, key: str, lines: list[str], status: TerminalStatus) -> None:
        """Store only what he actually got out, so later replies build on what was said."""

        # A completed reply was said in full; a cut-off one only up to the spoken lines.
        if isinstance(status, Succeeded):
            said = list(lines)
        elif isinstance(status, Interrupted):
            said = lines[: status.spoken]
            if not said:
                return
            said[-1] += CUT_OFF_MARK
        else:
            return

        now = self._clock()
        for text in said:
            self._store.add(key, ConversationLine(NPC_SPEAKER, text, from_npc=True), now)
