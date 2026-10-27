import asyncio
import logging
from datetime import UTC, datetime
from types import SimpleNamespace
from typing import Any

import pytest

from machiavillager.application.handle_lines_needed import (
    MIN_VALID_LINES,
    HandleLinesNeeded,
    fits_kind,
)
from machiavillager.application.ports import BridgeUnavailable
from machiavillager.domain.protocol import (
    MAX_TIMEOUT_MS,
    PROTOCOL_VERSION,
    DeliverLinesAction,
    LinesNeededData,
    LinesNeededEvent,
    Succeeded,
)

REQUEST_ID = "act_0123456789abcdef"
START = 1000.0
ONE_HOUR = 3600.0

# Distinct valid hint lines, exactly the minimum batch size.
HINT_LINES = [f"{{player}}, village {i} is at {{x}}, {{z}}." for i in range(MIN_VALID_LINES)]
# Plain lines, valid for bark and mutter.
PLAIN_LINES = [f"Plain line number {i} here." for i in range(MIN_VALID_LINES)]


class FakeBatchWriter:
    """Stands in for the batch writer: each call takes the next answer, then answers []."""

    def __init__(
        self,
        answers: list[list[str]] | None = None,
        gate: asyncio.Event | None = None,
    ) -> None:
        self._answers = list(answers or [])
        self._gate = gate
        self.calls: list[tuple[str, str, str]] = []

    async def write_batch(self, kind: str, phase: str, mood: str) -> list[str]:
        self.calls.append((kind, phase, mood))
        if self._gate is not None:
            await self._gate.wait()
        if not self._answers:
            return []
        return list(self._answers.pop(0))


class FakeBridge:
    """Stands in for the mod. Records each action; can raise as if no mod were connected."""

    def __init__(self, error: Exception | None = None, status: str | None = None) -> None:
        self._error = error
        self._status = status
        self.actions: list[DeliverLinesAction] = []

    async def request(self, action: DeliverLinesAction) -> Any:
        self.actions.append(action)
        if self._error is not None:
            raise self._error
        if self._status is not None:
            return SimpleNamespace(status=self._status)
        return Succeeded(
            v=PROTOCOL_VERSION,
            type="action_status",
            request_id=action.request_id,
            status="succeeded",
        )


class FakeClock:
    """A clock the test moves by hand, in seconds."""

    def __init__(self, now: float = START) -> None:
        self.now = now

    def __call__(self) -> float:
        return self.now


def _handler(
    writer: FakeBatchWriter,
    bridge: FakeBridge,
    clock: FakeClock,
    *,
    per_hour: int = 6,
) -> HandleLinesNeeded:
    return HandleLinesNeeded(
        writer,
        bridge,
        lambda: REQUEST_ID,
        clock,
        batches_per_hour=per_hour,
        refresh_seconds=1200,
        retry_seconds=120,
    )


def _event(
    kind: str = "hint",
    phase: str = "seeds",
    mood: str = "bored",
    event_id: str = "evt_test00000001",
) -> LinesNeededEvent:
    return LinesNeededEvent(
        v=PROTOCOL_VERSION,
        type="event",
        event_id=event_id,
        event="lines_needed",
        occurred_at=datetime(2026, 10, 26, 12, 0, tzinfo=UTC),
        data=LinesNeededData(kind=kind, phase=phase, mood=mood),
    )


def _run(handler: HandleLinesNeeded, event: LinesNeededEvent) -> None:
    asyncio.run(handler(event))


@pytest.mark.parametrize(
    ("kind", "line"),
    [
        ("hint", "{player}, olha a vila em {x}, {z}."),
        ("hint", "Olha a vila em {x}, {z}, {player}!"),
        ("hint", "Ei, {player}! A vila fica em {x}, {z}."),
        ("bark", "Ei, {player}!"),
        ("bark", "Hey {player}, careful."),
    ],
)
def test_player_used_as_an_address_fits_bark_and_hint(kind, line):
    assert fits_kind(kind, line)


@pytest.mark.parametrize(
    ("kind", "line"),
    [
        ("hint", "{player} viu a vila em {x} {z}"),
        ("bark", "{player} foi pra mina"),
    ],
)
def test_player_as_the_subject_of_a_sentence_fails_bark_and_hint(kind, line):
    assert not fits_kind(kind, line)


@pytest.mark.parametrize(
    "line",
    [
        '"E aí {player}',
        'o dia tá gostoso hoje."',
        '"O que cê vai fazer mais tarde {player}"',
        '"Viu aquele meme novo?"',
    ],
)
def test_broken_split_fragments_are_rejected_for_bark(line):
    assert not fits_kind("bark", line)


@pytest.mark.parametrize(
    "line",
    [
        "Ei {player}, cuidado com a lava.",
        "Gravel sempre cai em quem cava.",
        "Oi tudo bem!",
    ],
)
def test_whole_sentences_are_kept_for_bark(line):
    assert fits_kind("bark", line)


@pytest.mark.parametrize(
    ("kind", "line"),
    [
        ("bark", "Oi tudo bem"),
        ("bark", "Cuidadosamente"),
        ("hint", "“{player}”, a vila fica em {x}, {z}."),
        ("mutter", "Oi tudo bem"),
    ],
)
def test_short_single_word_or_curly_quoted_lines_fail_every_kind(kind, line):
    assert not fits_kind(kind, line)


@pytest.mark.parametrize(
    ("kind", "line"),
    [
        ("bark", "o dia tá gostoso hoje."),
        ("hint", "o dia tá gostoso hoje."),
    ],
)
def test_lowercase_fragment_without_a_placeholder_fails_bark_and_hint(kind, line):
    assert not fits_kind(kind, line)


def test_mutter_may_start_lowercase():
    assert fits_kind("mutter", "gravel sempre cai em quem cava.")


@pytest.mark.parametrize(
    ("kind", "line"),
    [
        ("hint", "A vila fica em {x}, {z}."),
        ("bark", "Hey you there."),
    ],
)
def test_lines_without_player_are_not_checked_for_address(kind, line):
    assert fits_kind(kind, line)


@pytest.mark.parametrize(
    ("kind", "line", "kept"),
    [
        # Bark takes {player} only; a coordinate in a bark is dropped.
        ("bark", "{player}, morning.", True),
        ("bark", "{player}, go to {x}.", False),
        # Mutter has no braces; a named player in a mutter is dropped.
        ("mutter", "Hmm, the rain again.", True),
        ("mutter", "Hmm, {player} again.", False),
        # Hint needs both coordinates; a hint without them is useless.
        ("hint", "{player}, the village is at {x}, {z}.", True),
        ("hint", "{player}, nice day.", False),
    ],
)
def test_each_kind_keeps_only_the_placeholders_it_allows(kind, line, kept):
    writer = FakeBatchWriter([[line] * MIN_VALID_LINES])
    bridge = FakeBridge()

    _run(_handler(writer, bridge, FakeClock()), _event(kind=kind))

    expected = [[line] * MIN_VALID_LINES] if kept else []
    assert [action.args.lines for action in bridge.actions] == expected


def test_four_valid_lines_are_not_delivered_and_wait_the_retry_time(caplog):
    writer = FakeBatchWriter([HINT_LINES[:4], HINT_LINES])
    bridge = FakeBridge()
    clock = FakeClock()
    handler = _handler(writer, bridge, clock)

    with caplog.at_level(logging.INFO):
        _run(handler, _event())
    assert bridge.actions == []
    assert "lines_needed dropped: only 4 valid lines" in caplog.text

    # Counted as empty, so the 120 s retry applies, not the 1200 s refresh.
    clock.now += 100
    _run(handler, _event(event_id="evt_test00000002"))
    assert len(writer.calls) == 1

    clock.now += 21
    _run(handler, _event(event_id="evt_test00000003"))
    assert len(writer.calls) == 2
    assert len(bridge.actions) == 1


def test_five_valid_lines_are_delivered():
    bridge = FakeBridge()

    _run(_handler(FakeBatchWriter([HINT_LINES]), bridge, FakeClock()), _event())

    assert [action.args.lines for action in bridge.actions] == [HINT_LINES]


@pytest.mark.parametrize("reported", ["rejected", "failed"])
def test_a_status_that_is_not_success_retries_after_the_retry_time(reported, caplog):
    writer = FakeBatchWriter([HINT_LINES, HINT_LINES])
    clock = FakeClock()
    handler = _handler(writer, FakeBridge(status=reported), clock)

    with caplog.at_level(logging.INFO):
        _run(handler, _event())
    assert f"deliver_lines not stored: {reported}" in caplog.text

    # Not stored counts as an empty attempt, so the 120 s retry applies, not the 1200 s refresh.
    clock.now += 100
    _run(handler, _event(event_id="evt_test00000002"))
    assert len(writer.calls) == 1

    clock.now += 21
    _run(handler, _event(event_id="evt_test00000003"))
    assert len(writer.calls) == 2


def test_a_succeeded_status_waits_the_long_refresh_time():
    writer = FakeBatchWriter([HINT_LINES, HINT_LINES])
    clock = FakeClock()
    handler = _handler(writer, FakeBridge(), clock)
    _run(handler, _event())

    # Past the retry time but inside the 1200 s refresh, so the key is still skipped.
    clock.now += 121
    _run(handler, _event(event_id="evt_test00000002"))
    assert len(writer.calls) == 1

    clock.now += 1100
    _run(handler, _event(event_id="evt_test00000003"))
    assert len(writer.calls) == 2


def test_the_drop_log_names_the_rule_each_line_failed(caplog):
    writer = FakeBatchWriter(
        [
            [
                "Hi {name}.",
                "{player}, go to {x}.",
                "Fine weather for digging today.",
                "Ok, let us keep going.",
                "Yes, that is a good plan.",
                "Sure thing, my friend.",
            ]
        ]
    )

    with caplog.at_level(logging.INFO):
        _run(_handler(writer, FakeBridge(), FakeClock()), _event(kind="bark"))

    assert "dropped 2 (contract 1, kind rule 1)" in caplog.text


def test_sends_one_action_with_the_key_and_the_lines():
    writer = FakeBatchWriter([HINT_LINES])
    bridge = FakeBridge()

    _run(_handler(writer, bridge, FakeClock()), _event())

    assert writer.calls == [("hint", "seeds", "bored")]
    assert len(bridge.actions) == 1
    action = bridge.actions[0]
    assert action.action == "deliver_lines"
    assert action.request_id == REQUEST_ID
    assert action.timeout_ms == MAX_TIMEOUT_MS
    assert action.args.kind == "hint"
    assert action.args.phase == "seeds"
    assert action.args.mood == "bored"
    assert action.args.lines == HINT_LINES


def test_a_line_with_a_bad_brace_is_dropped_and_the_rest_kept(caplog):
    writer = FakeBatchWriter(
        [
            [
                "Hi {name}.",
                "Good morning, my friends.",
                "Fine weather for digging today.",
                "Ok, let us keep going.",
                "Yes, that is a good plan.",
                "Sure thing, my friend.",
            ]
        ]
    )
    bridge = FakeBridge()

    # Bark takes no coordinates, so the plain line is kept and the bad brace is dropped.
    with caplog.at_level(logging.INFO):
        _run(_handler(writer, bridge, FakeClock()), _event(kind="bark"))

    assert bridge.actions[0].args.lines == [
        "Good morning, my friends.",
        "Fine weather for digging today.",
        "Ok, let us keep going.",
        "Yes, that is a good plan.",
        "Sure thing, my friend.",
    ]
    assert "dropped 1 (contract 1, kind rule 0)" in caplog.text


def test_no_action_when_the_writer_returns_nothing():
    bridge = FakeBridge()

    _run(_handler(FakeBatchWriter([[]]), bridge, FakeClock()), _event())

    assert bridge.actions == []


def test_no_action_when_every_line_fails_the_contract():
    bridge = FakeBridge()

    _run(_handler(FakeBatchWriter([["Hi {name}."]]), bridge, FakeClock()), _event())

    assert bridge.actions == []


def test_same_key_within_the_refresh_time_is_skipped(caplog):
    writer = FakeBatchWriter([HINT_LINES, HINT_LINES])
    clock = FakeClock()
    handler = _handler(writer, FakeBridge(), clock)
    _run(handler, _event())

    clock.now += 100
    with caplog.at_level(logging.INFO):
        _run(handler, _event(event_id="evt_test00000002"))

    assert len(writer.calls) == 1
    assert "lines_needed skipped: key_cooldown" in caplog.text

    # Once the refresh time has passed, the same key may be written again.
    clock.now += 1100
    _run(handler, _event(event_id="evt_test00000003"))
    assert len(writer.calls) == 2


def test_failed_attempt_retries_after_the_retry_time_not_before():
    writer = FakeBatchWriter([[], HINT_LINES])
    bridge = FakeBridge()
    clock = FakeClock()
    handler = _handler(writer, bridge, clock)
    _run(handler, _event())

    # The empty attempt waits the retry time (120 s), not the refresh time (1200 s).
    clock.now += 100
    _run(handler, _event(event_id="evt_test00000002"))
    assert len(writer.calls) == 1

    clock.now += 21
    _run(handler, _event(event_id="evt_test00000003"))
    assert len(writer.calls) == 2
    assert len(bridge.actions) == 1


def test_hourly_cap_blocks_the_seventh_batch_and_releases_after_an_hour(caplog):
    keys = [
        ("bark", "partner", "calm"),
        ("bark", "partner", "bored"),
        ("bark", "partner", "irritated"),
        ("bark", "seeds", "calm"),
        ("bark", "seeds", "bored"),
        ("bark", "seeds", "irritated"),
        ("hint", "seeds", "calm"),
    ]
    writer = FakeBatchWriter([PLAIN_LINES] * len(keys))
    clock = FakeClock()
    handler = _handler(writer, FakeBridge(), clock, per_hour=6)

    # Six batches in six seconds fill the hour's budget.
    for offset, (kind, phase, mood) in enumerate(keys[:6]):
        clock.now = START + offset
        _run(handler, _event(kind, phase, mood, event_id=f"evt_test0000000{offset}"))
    assert len(writer.calls) == 6

    # The seventh key is refused while the first attempt is still inside the hour.
    clock.now = START + 10
    with caplog.at_level(logging.INFO):
        _run(handler, _event(*keys[6], event_id="evt_test00000070"))
    assert len(writer.calls) == 6
    assert "lines_needed skipped: hourly_cap" in caplog.text

    # An hour after the first attempt, that attempt no longer counts and the seventh key goes.
    clock.now = START + ONE_HOUR
    _run(handler, _event(*keys[6], event_id="evt_test00000071"))
    assert len(writer.calls) == 7


def test_same_key_already_being_written_is_skipped(caplog):
    async def scenario() -> FakeBatchWriter:
        gate = asyncio.Event()
        writer = FakeBatchWriter([HINT_LINES], gate=gate)
        handler = _handler(writer, FakeBridge(), FakeClock())

        first = asyncio.create_task(handler(_event()))
        # Let the first call reach the writer and wait there, so its key is in flight.
        await asyncio.sleep(0)
        await handler(_event(event_id="evt_test00000002"))

        gate.set()
        await first
        return writer

    with caplog.at_level(logging.INFO):
        writer = asyncio.run(scenario())

    assert len(writer.calls) == 1
    assert "lines_needed skipped: in_flight" in caplog.text


def test_no_mod_connected_is_logged_and_counts_as_an_empty_attempt(caplog):
    writer = FakeBatchWriter([HINT_LINES, HINT_LINES])
    clock = FakeClock()
    handler = _handler(writer, FakeBridge(error=BridgeUnavailable("no mod")), clock)

    with caplog.at_level(logging.INFO):
        _run(handler, _event())

    assert "not sent: no mod is connected" in caplog.text

    # Counted as empty, so the short retry wait applies, not the refresh wait.
    clock.now += 100
    _run(handler, _event(event_id="evt_test00000002"))
    assert len(writer.calls) == 1

    clock.now += 21
    _run(handler, _event(event_id="evt_test00000003"))
    assert len(writer.calls) == 2
