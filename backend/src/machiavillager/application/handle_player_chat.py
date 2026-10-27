"""Answers one player_chat: builds the prompt, asks the writer, sends the speak action back."""

import logging
from collections.abc import Callable
from typing import Any, Final

from machiavillager.application.conversation_store import ConversationStore
from machiavillager.application.history_budget import fit_history
from machiavillager.application.ports import (
    BridgeUnavailable,
    Feeling,
    GameBridge,
    LineWriter,
)
from machiavillager.application.prompt_builder import (
    ConversationLine,
    ProfanityLevel,
    build_prompt,
)
from machiavillager.application.speak_fit import fit_to_speak
from machiavillager.domain.npc_state import COORDS_PLACEHOLDER, coords_words
from machiavillager.domain.protocol import (
    MAX_TIMEOUT_MS,
    PROTOCOL_VERSION,
    BackendAction,
    ComeToAction,
    ComeToArgs,
    Interrupted,
    NpcSnapshot,
    PlayerChatEvent,
    SpeakAction,
    SpeakArgs,
    Succeeded,
    SuggestRelationshipAction,
    SuggestRelationshipArgs,
    TerminalStatus,
)
from machiavillager.domain.rudeness import kindness_floor, rudeness_floor

logger = logging.getLogger(__name__)

# The mod answers a suggestion at once, so a short wait keeps a silent mod from delaying the speech.
SUGGESTION_TIMEOUT_MS: Final = 5_000
# come_to is instant on the mod side too, so it gets the same short wait.
COME_TIMEOUT_MS: Final = 5_000
NPC_SPEAKER: Final = "Machiavillager"
# Marks a line he was talked over in the middle of, so the next reply knows it was cut short.
CUT_OFF_MARK: Final = " (cut off)"
IRRITATED_MOOD: Final = "irritated"
# The reason the mod shows for each kind floor value: an apology is +1, warm words are +2.
KINDNESS_REASONS: Final = {1: "apology", 2: "kind words"}


def conversation_key(event: PlayerChatEvent) -> str:
    """The thread a chat line belongs to: its conversation, or the player when there is none."""

    # Without a conversation id, each player keeps his own thread.
    if event.conversation_id is not None:
        return event.conversation_id
    return f"player:{event.data.player_uuid}"


def reply_action(
    event: PlayerChatEvent,
    lines: list[str],
    request_id: str,
    *,
    model: str | None = None,
    latency_ms: int | None = None,
) -> SpeakAction:
    """Build the speak action that answers a chat line. The AI reply and the dev echo share it."""

    # Optional fields are left out instead of passed as None: the models are strict, and an
    # explicit None is refused by the contract.
    args_fields: dict[str, Any] = {"lines": lines}
    if event.data.addressed == "private":
        args_fields["to"] = event.data.player_uuid
    action_fields: dict[str, Any] = {}
    if event.conversation_id is not None:
        action_fields["conversation_id"] = event.conversation_id
    # Dev info: only the echo and dev-mode replies carry it; the echo passes neither.
    if model is not None:
        action_fields["model"] = model
    if latency_ms is not None:
        action_fields["latency_ms"] = latency_ms

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


def apply_feeling_floors(text: str, feeling: Feeling | None) -> Feeling | None:
    """Pin the model's feeling to what his words deserve: rude lines drop it, kind ones lift it."""

    # Rude words are checked first and win: a line with an insult never gets the kindness floor.
    rude = rudeness_floor(text)
    if rude < 0:
        # A feeling already at or below the floor is kept, so a harsher model reply is not undone.
        if feeling is not None and feeling.affinity_delta <= rude:
            return feeling
        logger.info("feeling floor applied: %d", rude)
        return Feeling(affinity_delta=rude, reason="rude words")

    # Kind words only lift the feeling: a model value already at or above the floor is kept.
    kind = kindness_floor(text)
    if kind > 0 and (feeling is None or feeling.affinity_delta < kind):
        logger.info("feeling floor applied: %d", kind)
        return Feeling(affinity_delta=kind, reason=KINDNESS_REASONS[kind])
    return feeling


def suggest_action(
    event: PlayerChatEvent,
    feeling: Feeling,
    request_id: str,
) -> SuggestRelationshipAction:
    """Build the suggest_relationship action that carries his feeling about the player who spoke."""

    # Every field is set: this action has no optional fields, and the contract refuses a null.
    return SuggestRelationshipAction(
        v=PROTOCOL_VERSION,
        type="action",
        request_id=request_id,
        action="suggest_relationship",
        args=SuggestRelationshipArgs(
            player=event.data.player_uuid,
            affinity_delta=feeling.affinity_delta,
            reason=feeling.reason,
        ),
        timeout_ms=SUGGESTION_TIMEOUT_MS,
        in_reply_to=event.event_id,
    )


def fill_coords(lines: list[str], npc: NpcSnapshot | None) -> list[str]:
    """Put his real coordinates where the model wrote the placeholder, then fit the lines again."""

    # Without game state there is nothing to fill, so the placeholder is dropped, never spoken.
    coords = coords_words(npc.x, npc.y, npc.z) if npc is not None else ""

    # Filling makes a line longer, so the speak limits are applied again afterwards.
    return fit_to_speak([line.replace(COORDS_PLACEHOLDER, coords) for line in lines])


def come_action(event: PlayerChatEvent, request_id: str) -> ComeToAction:
    """Build the come_to action that sends him to the player who called him."""

    return ComeToAction(
        v=PROTOCOL_VERSION,
        type="action",
        request_id=request_id,
        action="come_to",
        args=ComeToArgs(player=event.data.player_uuid),
        timeout_ms=COME_TIMEOUT_MS,
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
        *,
        show_model: bool = False,
    ) -> None:
        self._writer = writer
        self._bridge = bridge
        self._store = store
        self._read_persona = read_persona
        self._profanity = profanity
        self._new_request_id = new_request_id
        self._clock = clock
        # Dev only: when on, a reply says which model wrote it and how long it took.
        self._show_model = show_model
        self._in_flight: set[str] = set()
        # Lines held during a reply, per player, in arrival order; they join the thread after it.
        self._pending: dict[str, list[PlayerChatEvent]] = {}

    async def __call__(self, event: PlayerChatEvent) -> None:
        player = event.data.player_uuid

        # A spamming player cannot burn the model budget: one reply per player at a time.
        if player in self._in_flight:
            self._park(event)
            return

        # Stored now because no reply is running, so the thread stays in arrival order.
        self._store_line(event)
        self._in_flight.add(player)
        try:
            # Lines held during a reply join the thread in order, then one reply answers the newest.
            current: PlayerChatEvent | None = event
            while current is not None:
                await self._reply(current)
                current = self._take_pending(player)
        finally:
            # If a reply failed, held lines still join the thread, so none is lost or left stale.
            self._take_pending(player)
            self._in_flight.discard(player)

    def _store_line(self, event: PlayerChatEvent) -> None:
        """Add his chat line to its thread the moment it arrives."""

        # Stored once, here, so no reply adds the same line again.
        player_line = ConversationLine(event.data.player_name, event.data.text, from_npc=False)
        self._store.add(conversation_key(event), player_line, self._clock())

    def _park(self, event: PlayerChatEvent) -> None:
        """Hold a line that arrived mid-reply; it joins the thread after his reply, in order."""

        # Not stored yet: storing it now would put it before his reply to the line in flight.
        player = event.data.player_uuid
        self._pending.setdefault(player, []).append(event)
        logger.info("reply queued event_id=%s", event.event_id)

    def _take_pending(self, player: str) -> PlayerChatEvent | None:
        """Store the lines held during the last reply in order; return the newest to answer."""

        # Nothing held means no reply is due and the player is free for the next line.
        parked = self._pending.pop(player, [])
        if not parked:
            return None

        # Each held line joins the thread now, after his reply, so the prompt keeps arrival order.
        for event in parked:
            self._store_line(event)

        # Only the newest held line gets a reply; the older ones are read from the thread.
        for event in parked[:-1]:
            logger.info("reply superseded event_id=%s", event.event_id)
        return parked[-1]

    async def _reply(self, event: PlayerChatEvent) -> None:
        key = conversation_key(event)

        # The thread already ends with his newest line, so the prompt reads it from the store.
        # The store keeps every line; only the newest that fit the token budget reach the model.
        history = fit_history(self._store.recent(key, self._clock()))

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

        reply = await self._writer.write_reply(messages, irritated=irritated)
        lines = fill_coords(reply.lines, npc)

        # Only the count is logged, so a silent reply shows up without any of his words in the log.
        logger.info("reply %s lines=%d", event.event_id, len(reply.lines))

        # Floor reads his newest line only. The dev echo never gets here, so it is never floored.
        feeling = apply_feeling_floors(event.data.text, reply.feeling)

        # The feeling goes first: it is instant, while the speak below can take seconds.
        if feeling is not None:
            await self._suggest(event, feeling)

        # He sets off before he talks, so he does not finish his lines first. Only when the mod's
        # rule said he comes: the model agreeing is not enough.
        if reply.come and npc is not None and npc.if_asked_to_come == "comes":
            await self._send_quick(come_action(event, self._new_request_id()), "come_to")

        # Silence is not a line, so nothing is sent or stored; the player's line is already kept.
        if not lines:
            logger.info("stayed silent event_id=%s", event.event_id)
            return

        # Model and latency are only sent in dev mode; reply_action leaves out a None.
        action = reply_action(
            event,
            lines,
            self._new_request_id(),
            model=reply.model if self._show_model else None,
            latency_ms=reply.latency_ms if self._show_model else None,
        )
        try:
            status = await self._bridge.request(action)
        except BridgeUnavailable:
            logger.info("reply %s not sent: no mod is connected", action.request_id)
            return

        self._remember(key, lines, status)
        logger.info("reply %s finished: %s", action.request_id, status.status)

    async def _suggest(self, event: PlayerChatEvent, feeling: Feeling) -> None:
        """Send the feeling for the player who spoke. Never raises: the speak still goes out."""

        # The reason text is never logged on this side: only the request id and the status.
        await self._send_quick(suggest_action(event, feeling, self._new_request_id()), "feeling")

    async def _send_quick(self, action: BackendAction, label: str) -> None:
        """Send an instant action before the speak. Never raises: the speak still goes out."""

        try:
            status = await self._bridge.request(action)
        except BridgeUnavailable:
            logger.info("%s %s not sent: no mod is connected", label, action.request_id)
            return
        except Exception as error:
            # Type name only: the error text is kept out of the log, as for the other failures.
            logger.warning("%s %s failed: %s", label, action.request_id, type(error).__name__)
            return

        logger.info("%s %s finished: %s", label, action.request_id, status.status)

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
