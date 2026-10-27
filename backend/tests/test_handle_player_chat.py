import asyncio
import contextlib
import json
import logging
import re
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

import pytest

from machiavillager.application.conversation_store import ConversationStore
from machiavillager.application.handle_player_chat import (
    HandlePlayerChat,
    conversation_key,
    reply_action,
)
from machiavillager.application.ports import BridgeUnavailable, Feeling, Reply
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
    SuggestRelationshipAction,
)

PLAYER_UUID = "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69"
CONVERSATION_ID = "conv_01HZX8K2M9"
REQUEST_ID = "act_0123456789abcdef"
PERSONA = "Persona text."
NOW = 1000.0


class FakeWriter:
    """Stands in for the LLM. Records each call; can hold until a test releases it."""

    def __init__(
        self,
        lines: tuple[str, ...] = ("Hello.",),
        gate: asyncio.Event | None = None,
        *,
        model: str | None = None,
        latency_ms: int | None = None,
        feeling: Feeling | None = None,
    ):
        self._lines = list(lines)
        self._gate = gate
        self._model = model
        self._latency_ms = latency_ms
        self._feeling = feeling
        self.calls: list[tuple[list[PromptMessage], bool]] = []

    async def write_reply(self, messages: list[PromptMessage], *, irritated: bool) -> Reply:
        self.calls.append((messages, irritated))
        if self._gate is not None:
            await self._gate.wait()
        return Reply(
            lines=list(self._lines),
            model=self._model,
            latency_ms=self._latency_ms,
            feeling=self._feeling,
        )


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


def _dialogue(messages: list[PromptMessage]) -> list[tuple[str, str]]:
    """The dialogue as (role, text): system rules left out, player tags stripped from the text."""

    dialogue: list[tuple[str, str]] = []
    for message in messages:
        if message.role == "system":
            continue
        # Player lines are wrapped in a tag for the model; the test compares the words inside it.
        match = re.fullmatch(
            r'<player_line from="[^"]*">(.*)</player_line>', message.content, re.DOTALL
        )
        text = match.group(1) if match else message.content
        dialogue.append((message.role, text))
    return dialogue


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


def test_silence_sends_nothing_and_keeps_only_the_player_line(caplog):
    store = ConversationStore()
    bridge = FakeBridge(_succeeded())

    with caplog.at_level(logging.INFO):
        _run(
            _handler(FakeWriter(()), bridge, store),
            _chat_event(event_id="evt_test00000001"),
        )

    recent = store.recent(f"player:{PLAYER_UUID}", NOW)
    assert bridge.actions == []
    assert [(line.text, line.from_npc) for line in recent] == [("hi", False)]
    assert "stayed silent event_id=evt_test00000001" in caplog.text


def test_near_chat_tells_the_model_it_was_overheard():
    writer = FakeWriter()

    _run(_handler(writer, FakeBridge(_succeeded())), _chat_event(addressed="near"))

    system_text = "\n".join(m.content for m in writer.calls[0][0] if m.role == "system")
    assert "Overheard" in system_text


def test_name_chat_is_not_marked_as_overheard():
    writer = FakeWriter()

    _run(_handler(writer, FakeBridge(_succeeded())), _chat_event(addressed="name"))

    system_text = "\n".join(m.content for m in writer.calls[0][0] if m.role == "system")
    assert "Overheard" not in system_text


def test_a_line_held_during_a_reply_joins_the_thread_after_that_reply():
    gate = asyncio.Event()
    writer = FakeWriter(gate=gate)
    bridge = FakeBridge(_succeeded())
    handler = _handler(writer, bridge)

    async def scenario() -> None:
        first = asyncio.create_task(handler(_chat_event(text="A", event_id="evt_test00000001")))
        # One turn lets the first call reach the writer and hold there.
        await asyncio.sleep(0)
        await handler(_chat_event(text="B", event_id="evt_test00000002"))
        gate.set()
        await first

    asyncio.run(scenario())

    # His reply to A sits between A and B, so the prompt for B reads in the order it was said.
    assert _dialogue(writer.calls[1][0]) == [
        ("user", "A"),
        ("assistant", "Hello."),
        ("user", "B"),
    ]
    assert [action.in_reply_to for action in bridge.actions] == [
        "evt_test00000001",
        "evt_test00000002",
    ]


def test_held_lines_join_the_thread_in_order_and_one_reply_answers_the_newest(caplog):
    gate = asyncio.Event()
    writer = FakeWriter(gate=gate)
    bridge = FakeBridge(_succeeded())
    handler = _handler(writer, bridge)

    async def scenario() -> None:
        first = asyncio.create_task(handler(_chat_event(text="A", event_id="evt_test00000001")))
        # One turn lets the first call reach the writer and hold there.
        await asyncio.sleep(0)
        await handler(_chat_event(text="B1", event_id="evt_test00000002"))
        await handler(_chat_event(text="B2", event_id="evt_test00000003"))
        gate.set()
        await first

    with caplog.at_level(logging.INFO):
        asyncio.run(scenario())

    assert len(writer.calls) == 2
    assert [action.in_reply_to for action in bridge.actions] == [
        "evt_test00000001",
        "evt_test00000003",
    ]
    # Each held line appears once, in arrival order, after his reply to A.
    assert _dialogue(writer.calls[1][0]) == [
        ("user", "A"),
        ("assistant", "Hello."),
        ("user", "B1"),
        ("user", "B2"),
    ]
    assert "reply queued event_id=evt_test00000002" in caplog.text
    assert "reply queued event_id=evt_test00000003" in caplog.text
    assert "reply superseded event_id=evt_test00000002" in caplog.text
    assert "reply superseded event_id=evt_test00000003" not in caplog.text


def test_a_reply_cut_off_by_a_held_line_is_remembered_cut_off_before_that_line():
    gate = asyncio.Event()
    writer = FakeWriter(("one", "two"), gate=gate)
    handler = _handler(writer, FakeBridge(_interrupted(spoken=1, of=2)))

    async def scenario() -> None:
        first = asyncio.create_task(handler(_chat_event(text="A", event_id="evt_test00000001")))
        # One turn lets the first call reach the writer and hold there.
        await asyncio.sleep(0)
        await handler(_chat_event(text="B", event_id="evt_test00000002"))
        gate.set()
        await first

    asyncio.run(scenario())

    assert _dialogue(writer.calls[1][0]) == [
        ("user", "A"),
        ("assistant", "one (cut off)"),
        ("user", "B"),
    ]


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


def test_reply_action_carries_model_and_latency_only_when_given():
    event = _chat_event()

    plain = reply_action(event, ["Hi."], REQUEST_ID)
    shown = reply_action(event, ["Hi."], REQUEST_ID, model="groq:qwen/qwen3.8-27b", latency_ms=480)

    # A line with no model (canned, or a dev echo) leaves both fields off the wire entirely.
    assert "model" not in json.loads(plain.model_dump_json(exclude_none=True))
    assert shown.model == "groq:qwen/qwen3.8-27b"
    assert shown.latency_ms == 480


def test_model_and_latency_are_sent_only_when_show_model_is_on():
    for show_model in (True, False):
        writer = FakeWriter(model="groq:qwen/qwen3.8-27b", latency_ms=480)
        bridge = FakeBridge(status=_succeeded())
        handler = HandlePlayerChat(
            writer,
            bridge,
            ConversationStore(),
            lambda: PERSONA,
            "mild",
            lambda: REQUEST_ID,
            lambda: NOW,
            show_model=show_model,
        )

        _run(handler, _chat_event())

        action = bridge.actions[0]
        assert action.model == ("groq:qwen/qwen3.8-27b" if show_model else None)
        assert action.latency_ms == (480 if show_model else None)


def test_held_lines_join_the_thread_even_when_the_reply_fails():
    store = ConversationStore()
    gate = asyncio.Event()

    class ExplodingWriter(FakeWriter):
        async def write_reply(self, messages, *, irritated):
            await gate.wait()
            raise RuntimeError("provider down")

    handler = _handler(ExplodingWriter(), FakeBridge(_succeeded()), store)

    async def scenario() -> None:
        first = asyncio.create_task(handler(_chat_event(text="first", event_id="evt_test00000001")))
        await asyncio.sleep(0)
        await handler(_chat_event(text="held", event_id="evt_test00000002"))
        gate.set()
        with contextlib.suppress(RuntimeError):
            await first

    asyncio.run(scenario())

    texts = [line.text for line in store.recent(f"player:{PLAYER_UUID}", NOW)]
    assert texts == ["first", "held"]


def test_a_long_thread_reaches_the_writer_trimmed_and_ends_with_the_newest_line():
    store = ConversationStore()
    writer = FakeWriter(("Hello.",))
    handler = _handler(writer, FakeBridge(_succeeded()), store)
    # Long lines, so the token budget keeps only the newest few of the thread.
    texts = [f"{i:02d} " + "word " * 40 for i in range(30)]
    events = [
        _chat_event(text=text, event_id=f"evt_test{i:08d}") for i, text in enumerate(texts, start=1)
    ]

    _run(handler, *events)

    messages, _ = writer.calls[-1]
    dialogue = _dialogue(messages)
    stored = store.recent(f"player:{PLAYER_UUID}", NOW)
    # The store keeps its 20 lines; only the prompt is trimmed.
    assert len(stored) == 20
    assert len(dialogue) < len(stored)
    assert dialogue[-1] == ("user", texts[-1])


class SuggestionFailsBridge(FakeBridge):
    """A mod that fails the suggest_relationship action with one error and speaks normally."""

    def __init__(self, error: Exception):
        super().__init__(_succeeded())
        self._suggestion_error = error

    async def request(self, action: Any) -> Any:
        if isinstance(action, SuggestRelationshipAction):
            self.actions.append(action)
            raise self._suggestion_error
        return await super().request(action)


FEELING = Feeling(affinity_delta=-6, reason="threatened him for real")


def _suggestions(bridge: FakeBridge) -> list[SuggestRelationshipAction]:
    return [a for a in bridge.actions if isinstance(a, SuggestRelationshipAction)]


def _speaks(bridge: FakeBridge) -> list[SpeakAction]:
    return [a for a in bridge.actions if isinstance(a, SpeakAction)]


def test_feeling_is_sent_as_a_suggestion_before_the_speak():
    bridge = FakeBridge(_succeeded())
    event = _chat_event(event_id="evt_test00000001")

    _run(_handler(FakeWriter(feeling=FEELING), bridge), event)

    assert [type(a) for a in bridge.actions] == [SuggestRelationshipAction, SpeakAction]
    suggestion = bridge.actions[0]
    assert suggestion.action == "suggest_relationship"
    assert str(suggestion.args.player) == PLAYER_UUID
    assert suggestion.args.affinity_delta == -6
    assert suggestion.in_reply_to == "evt_test00000001"


@pytest.mark.parametrize("error", [BridgeUnavailable("no mod"), RuntimeError("socket broke")])
def test_a_failed_suggestion_never_blocks_the_speak(error):
    bridge = SuggestionFailsBridge(error)

    _run(_handler(FakeWriter(feeling=FEELING), bridge), _chat_event())

    assert len(_suggestions(bridge)) == 1
    assert [a.args.lines for a in _speaks(bridge)] == [["Hello."]]


def test_reply_without_a_feeling_sends_only_the_speak():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event())

    assert len(bridge.actions) == 1
    assert isinstance(bridge.actions[0], SpeakAction)


def test_feeling_with_no_lines_sends_only_the_suggestion():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter((), feeling=FEELING), bridge), _chat_event())

    assert [type(a) for a in bridge.actions] == [SuggestRelationshipAction]


def test_the_feeling_reason_is_never_logged_at_info(caplog):
    bridge = FakeBridge(_succeeded())

    with caplog.at_level(logging.INFO):
        _run(_handler(FakeWriter(feeling=FEELING), bridge), _chat_event())

    assert "feeling act_0123456789abcdef finished: succeeded" in caplog.text
    assert "threatened him for real" not in caplog.text


def test_a_rude_line_with_no_model_feeling_sends_the_floor():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(text="te odeio"))

    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [-4]


def test_a_milder_insult_with_no_model_feeling_sends_minus_two():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(text="Você é um LIXO"))

    suggestion = _suggestions(bridge)[0]
    assert suggestion.args.affinity_delta == -2
    assert suggestion.args.reason == "rude words"


def test_a_rude_line_where_the_model_said_plus_two_sends_the_floor():
    bridge = FakeBridge(_succeeded())

    _run(
        _handler(FakeWriter(feeling=Feeling(affinity_delta=2, reason="kind")), bridge),
        _chat_event(text="cala boca"),
    )

    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [-2]


def test_a_model_feeling_more_negative_than_the_floor_stays():
    bridge = FakeBridge(_succeeded())

    _run(
        _handler(FakeWriter(feeling=Feeling(affinity_delta=-8, reason="real anger")), bridge),
        _chat_event(text="lixo"),
    )

    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [-8]


def test_chupa_meus_ovo_with_no_model_feeling_sends_minus_two():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(text="chupa meus ovo"))

    suggestion = _suggestions(bridge)[0]
    assert suggestion.args.affinity_delta == -2
    assert suggestion.args.reason == "rude words"


def test_a_normal_line_sends_no_suggestion():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(text="bom dia, amigo"))

    assert _suggestions(bridge) == []


def test_an_apology_with_no_model_feeling_sends_plus_one():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(text="desculpa"))

    suggestion = _suggestions(bridge)[0]
    assert suggestion.args.affinity_delta == 1
    assert suggestion.args.reason == "apology"


def test_an_apology_the_model_scored_minus_one_becomes_plus_one():
    bridge = FakeBridge(_succeeded())
    writer = FakeWriter(feeling=Feeling(affinity_delta=-1, reason="still grumpy"))

    _run(_handler(writer, bridge), _chat_event(text="desculpa"))

    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [1]


def test_warm_words_with_no_model_feeling_send_plus_two():
    bridge = FakeBridge(_succeeded())

    _run(_handler(FakeWriter(), bridge), _chat_event(text="te amo"))

    suggestion = _suggestions(bridge)[0]
    assert suggestion.args.affinity_delta == 2
    assert suggestion.args.reason == "kind words"


def test_a_model_feeling_above_the_kindness_floor_stays():
    bridge = FakeBridge(_succeeded())
    writer = FakeWriter(feeling=Feeling(affinity_delta=3, reason="touched"))

    _run(_handler(writer, bridge), _chat_event(text="te amo"))

    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [3]


def test_a_chat_message_makes_exactly_one_writer_call():
    writer = FakeWriter(feeling=Feeling(affinity_delta=1, reason="warm"))
    bridge = FakeBridge(_succeeded())

    # One writer call per message: the reply carries the feeling, with no second call.
    _run(_handler(writer, bridge), _chat_event(text="bom dia"))

    assert len(writer.calls) == 1
    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [1]


def test_a_rude_apology_keeps_the_rudeness_floor():
    bridge = FakeBridge(_succeeded())
    writer = FakeWriter(feeling=Feeling(affinity_delta=2, reason="kind"))

    _run(_handler(writer, bridge), _chat_event(text="desculpa seu idiota"))

    assert [a.args.affinity_delta for a in _suggestions(bridge)] == [-2]
