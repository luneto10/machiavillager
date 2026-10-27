"""Dispatch: a lines_needed event from the mod reaches the batch handler."""

import asyncio
import logging
import threading
from datetime import UTC, datetime
from pathlib import Path

from fastapi.testclient import TestClient
from pydantic import SecretStr
from starlette.datastructures import State

from machiavillager.api.app import create_app
from machiavillager.api.bridge.events import handle_event
from machiavillager.domain.protocol import PROTOCOL_VERSION, LinesNeededData, LinesNeededEvent
from machiavillager.infrastructure.settings import Settings, get_settings

TOKEN = "test-bridge-token-0123456789"
HELLO = '{"v": 1, "type": "hello", "mod_version": "0.1.0", "protocol": 1}'
# The shared example the mod also tests against, so the socket test reads the real wire shape.
EXAMPLE_EVENT = (
    Path(__file__).resolve().parents[2]
    / "shared"
    / "examples"
    / "valid"
    / "event_lines_needed.json"
)


class FakeLinesHandler:
    """Records each event it is given, like the real handler would receive it."""

    def __init__(self) -> None:
        self.events: list[LinesNeededEvent] = []
        # Set when an event arrives, so a test on another thread can wait for it.
        self.received = threading.Event()

    async def __call__(self, event: LinesNeededEvent) -> None:
        self.events.append(event)
        self.received.set()


def _state(handler: FakeLinesHandler | None) -> State:
    state = State()
    state.dev_endpoints = False
    state.dev_echo = False
    state.handle_player_chat = None
    state.handle_lines_needed = handler
    return state


def _lines_event() -> LinesNeededEvent:
    return LinesNeededEvent(
        v=PROTOCOL_VERSION,
        type="event",
        event_id="evt_test00000001",
        event="lines_needed",
        occurred_at=datetime(2026, 10, 26, 12, 0, tzinfo=UTC),
        data=LinesNeededData(kind="hint", phase="seeds", mood="bored"),
    )


def _handle(event: LinesNeededEvent, state: State) -> None:
    async def run() -> None:
        await handle_event(event, state)
        # The batch runs as its own task, so one turn of the loop lets it start.
        await asyncio.sleep(0)

    asyncio.run(run())


def test_lines_needed_reaches_the_batch_handler(caplog):
    handler = FakeLinesHandler()
    event = _lines_event()

    with caplog.at_level(logging.INFO):
        _handle(event, _state(handler))

    assert handler.events == [event]
    assert "lines_needed event_id=evt_test00000001 kind=hint" in caplog.text


def test_lines_needed_without_a_batch_handler_is_only_logged(caplog):
    with caplog.at_level(logging.INFO):
        _handle(_lines_event(), _state(None))

    assert "lines_needed event_id=evt_test00000001 kind=hint" in caplog.text


def test_lines_needed_sent_over_the_socket_reaches_the_batch_handler():
    handler = FakeLinesHandler()
    # No `with`: the lifespan is skipped, so the handler set below is the one the socket uses.
    settings = Settings(
        _env_file=None, bridge_token=SecretStr(TOKEN), groq_api_key="fake-key-for-tests"
    )
    app = create_app(settings)
    # The bridge route reads its token through get_settings, so the test settings go there too.
    app.dependency_overrides[get_settings] = lambda: settings
    app.state.handle_lines_needed = handler
    client = TestClient(app)

    with client.websocket_connect("/bridge", headers={"Authorization": f"Bearer {TOKEN}"}) as mod:
        mod.send_text(HELLO)
        mod.receive_text()  # the welcome
        mod.send_text(EXAMPLE_EVENT.read_text(encoding="utf-8"))
        # The socket loop hands the event on without waiting for it, so wait briefly for it here.
        assert handler.received.wait(timeout=2.0)

    assert [event.data.kind for event in handler.events] == ["hint"]
