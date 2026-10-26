import asyncio
import logging
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

from machiavillager.application.conversation_store import ConversationStore
from machiavillager.application.handle_player_chat import (
    HandlePlayerChat,
    conversation_key,
)
from machiavillager.application.ports import BridgeUnavailable
from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.domain.protocol import (
    PROTOCOL_VERSION,
    Failed,
    Interrupted,
    NpcSnapshot,
    PlayerChatData,
    PlayerChatEvent,
    SpeakAction,
    Succeeded,
)

PLAYER_UUID = "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69"
CONVERSATION_ID = "conv_01HZX8K2M9"
REQUEST_ID = "act_0123456789abcdef"
PERSONA = "Persona text."
NOW = 1000.0


class FakeWriter:
    """Stands in for the LLM. Records each call; can hold until a test releases it."""

    def __init__(self, lines: tuple[str, ...] = ("Hello.",), gate: asyncio.Event | None = None):
        self._lines = list(lines)
        self._gate = gate
        self.calls: list[tuple[list[PromptMessage], bool]] = []

    async def write_reply(self, messages: list[PromptMessage], *, irritated: bool) -> list[str]:
        self.calls.append((messages, irritated))
        if self._gate is not None:
            await self._gate.wait()
        return list(self._lines)


class FakeBridge:
    """Stands in for the mod. Returns one chosen status, or raises one chosen error."""

    def __init__(self, status: Any = None, error: Exception | None = None):
        self._status = status
        self._error = error
        self.actions: list[SpeakAction] = []

    async def request(self, action: SpeakAction) -> Any:
        self.actions.append(action)
        if self._error is not None:
            raise self._error
        return self._status


def _succeeded() -> Succeeded:
    return Succeeded(
        v=PROTOCOL_VERSION,
        type="action_status",
        request_id=REQUEST_ID,
        status="succeeded",
    )


def _interrupted(spoken: int, of: int) -> Interrupted:
    return Interrupted(
        v=PROTOCOL_VERSION,
        type="action_status",
        request_id=REQUEST_ID,
        status="interrupted",
        spoken=spoken,
        of=of,
    )


def _failed() -> Failed:
    return Failed(
        v=PROTOCOL_VERSION,
        type="action_status",
        request_id=REQUEST_ID,
        status="failed",
        reason="speak broke",
    )


def _chat_event(
    *,
    text: str = "hi",
    addressed: str = "near",
    event_id: str = "evt_test00000001",
    conversation_id: str | None = None,
    npc: NpcSnapshot | None = None,
) -> PlayerChatEvent:
    event_fields: dict[str, Any] = {}
    if conversation_id is not None:
        event_fields["conversation_id"] = conversation_id
    data_fields: dict[str, Any] = {}
    if npc is not None:
        data_fields["npc"] = npc
    return PlayerChatEvent(
        v=PROTOCOL_VERSION,
        type="event",
        event_id=event_id,
        event="player_chat",
        occurred_at=datetime(2026, 10, 26, 12, 0, tzinfo=UTC),
        data=PlayerChatData(
            player_uuid=UUID(PLAYER_UUID),
            player_name="Luciano",
            text=text,
            addressed=addressed,
            **data_fields,
        ),
        **event_fields,
    )


def _npc(mood: str) -> NpcSnapshot:
    return NpcSnapshot(
        phase="seeds",
        mood=mood,
        affinity=40,
        independence=60,
        nearby_players=["Luciano"],
    )


def _handler(
    writer: FakeWriter,
    bridge: FakeBridge,
    store: ConversationStore | None = None,
) -> HandlePlayerChat:
    return HandlePlayerChat(
        writer,
        bridge,
        store or ConversationStore(),
        lambda: PERSONA,
        "mild",
        lambda: REQUEST_ID,
        lambda: NOW,
    )


def _run(handler: HandlePlayerChat, *events: PlayerChatEvent) -> None:
    async def handle_all() -> None:
        for event in events:
            await handler(event)

    asyncio.run(handle_all())


def _npc_texts(store: ConversationStore, key: str) -> list[str]:
    return [line.text for line in store.recent(key, NOW) if line.from_npc]


def test_history_reaches_the_prompt_on_the_second_line():
    writer = FakeWriter(("Hello.",))
    handler = _handler(writer, FakeBridge(_succeeded()))

    _run(
        handler,
        _chat_event(text="first line", event_id="evt_test00000001"),
        _chat_event(text="second line", event_id="evt_test00000002"),
    )

    second_messages, _ = writer.calls[1]
    assert [m.content for m in second_messages if m.role == "assistant"] == ["Hello."]
    assert any("first line" in m.content for m in second_messages if m.role == "user")
    assert "second line" in second_messages[-1].content


def test_irritated_mood_asks_for_the_irritated_chain():
    writer = FakeWriter()
    _run(_handler(writer, FakeBridge(_succeeded())), _chat_event(npc=_npc("irritated")))
    _run(
        _handler(writer, FakeBridge(_succeeded())),
        _chat_event(npc=_npc("bored"), event_id="evt_test00000002"),
    )

    assert [irritated for _, irritated in writer.calls] == [True, False]


def test_no_game_state_is_not_irritated():
    writer = FakeWriter()

    _run(_handler(writer, FakeBridge(_succeeded())), _chat_event(npc=None))

    assert writer.calls[0][1] is False


def test_private_chat_answers_only_the_player():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(addressed="private"))

    assert bridge.actions[0].args.to == UUID(PLAYER_UUID)


def test_public_chat_has_no_private_recipient():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(addressed="near"))

    assert bridge.actions[0].args.to is None


def test_reply_points_at_the_event_and_keeps_its_conversation():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(conversation_id=CONVERSATION_ID))

    action = bridge.actions[0]
    assert action.in_reply_to == "evt_test00000001"
    assert action.conversation_id == CONVERSATION_ID
    assert action.args.lines == ["Hello."]


def test_succeeded_reply_is_remembered_in_full():
    store = ConversationStore()
    writer = FakeWriter(("Hello.", "Sit down."))

    _run(_handler(writer, FakeBridge(_succeeded()), store), _chat_event())

    recent = store.recent(f"player:{PLAYER_UUID}", NOW)
    assert [(line.speaker, line.text, line.from_npc) for line in recent] == [
        ("Luciano", "hi", False),
        ("Machiavillager", "Hello.", True),
        ("Machiavillager", "Sit down.", True),
    ]


def test_interrupted_reply_remembers_only_what_was_spoken_marked_cut_off():
    store = ConversationStore()
    writer = FakeWriter(("one", "two", "three"))

    _run(_handler(writer, FakeBridge(_interrupted(spoken=2, of=3)), store), _chat_event())

    assert _npc_texts(store, f"player:{PLAYER_UUID}") == ["one", "two (cut off)"]


def test_interrupted_before_any_line_remembers_nothing_from_him():
    store = ConversationStore()

    _run(_handler(FakeWriter(), FakeBridge(_interrupted(spoken=0, of=1)), store), _chat_event())

    assert _npc_texts(store, f"player:{PLAYER_UUID}") == []


def test_failed_reply_remembers_nothing_from_him():
    store = ConversationStore()

    _run(_handler(FakeWriter(), FakeBridge(_failed()), store), _chat_event())

    assert _npc_texts(store, f"player:{PLAYER_UUID}") == []


def test_no_connected_mod_speaks_nothing_and_does_not_raise():
    store = ConversationStore()

    _run(
        _handler(FakeWriter(), FakeBridge(error=BridgeUnavailable("no mod")), store),
        _chat_event(),
    )

    assert _npc_texts(store, f"player:{PLAYER_UUID}") == []


def test_events_during_a_reply_coalesce_into_one_reply_to_the_newest(caplog):
    gate = asyncio.Event()
    writer = FakeWriter(gate=gate)
    bridge = FakeBridge(_succeeded())
    handler = _handler(writer, bridge)

    async def scenario() -> None:
        first = asyncio.create_task(
            handler(_chat_event(text="first line", event_id="evt_test00000001"))
        )
        # One turn lets the first call reach the writer and hold there.
        await asyncio.sleep(0)
        await handler(_chat_event(text="second line", event_id="evt_test00000002"))
        await handler(_chat_event(text="third line", event_id="evt_test00000003"))
        gate.set()
        await first

    with caplog.at_level(logging.INFO):
        asyncio.run(scenario())

    assert len(writer.calls) == 2
    assert [action.in_reply_to for action in bridge.actions] == [
        "evt_test00000001",
        "evt_test00000003",
    ]
    # Both parked lines reach the newest reply's history, each exactly once.
    user_texts = [m.content for m in writer.calls[1][0] if m.role == "user"]
    assert sum("second line" in text for text in user_texts) == 1
    assert sum("third line" in text for text in user_texts) == 1
    assert "reply queued event_id=evt_test00000002" in caplog.text
    assert "reply superseded event_id=evt_test00000002" in caplog.text


def test_a_finished_reply_frees_the_player_for_the_next_one():
    writer = FakeWriter()
    bridge = FakeBridge(_succeeded())

    _run(
        _handler(writer, bridge),
        _chat_event(event_id="evt_test00000001"),
        _chat_event(event_id="evt_test00000002"),
    )

    assert len(bridge.actions) == 2


def test_conversation_key_is_the_conversation_or_the_player():
    assert conversation_key(_chat_event(conversation_id=CONVERSATION_ID)) == CONVERSATION_ID
    assert conversation_key(_chat_event()) == f"player:{PLAYER_UUID}"
