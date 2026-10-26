"""Dev only: the in-game model switch (dev_model) and the model list the welcome carries.

The switch is handled by api/bridge/events.py with a fake writer, so no network is used. The welcome
test runs the real app with a fake key, which only builds the models and never calls them.
"""

import asyncio
import json
import logging
from datetime import UTC, datetime
from typing import Any

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr, ValidationError
from starlette.datastructures import State

from machiavillager.api.app import create_app
from machiavillager.api.bridge.events import handle_event
from machiavillager.domain.protocol import (
    DevModelData,
    DevModelEvent,
    parse_message,
)
from machiavillager.infrastructure.settings import Settings, get_settings

TOKEN = "test-bridge-token-0123456789"
AUTH_HEADERS = {"Authorization": f"Bearer {TOKEN}"}
HELLO = '{"v": 1, "type": "hello", "mod_version": "0.1.0", "protocol": 1}'
MODEL = "groq:openai/gpt-oss-120b"

PINNED_CHAIN = "groq:qwen/qwen3.8-27b,groq:openai/gpt-oss-120b,nvidia:nvidia/nemotron-3.5-bolt-e4b"


def _handle(event: DevModelEvent, state: State) -> None:
    # The handler is async so the socket loop never waits on it; a sync test just runs it to end.
    asyncio.run(handle_event(event, state))


class FakeWriter:
    """Records each use_model call; raises the chosen error to show a refused switch."""

    def __init__(self, error: Exception | None = None) -> None:
        self._error = error
        self.calls: list[str | None] = []

    def use_model(self, pair: str | None) -> None:
        self.calls.append(pair)
        if self._error is not None:
            raise self._error


def _state(*, dev_endpoints: bool, writer: FakeWriter) -> State:
    state = State()
    state.dev_endpoints = dev_endpoints
    state.dev_echo = False
    state.handle_player_chat = None
    state.llm_writer = writer
    return state


def _dev_model(data: dict[str, Any]) -> DevModelEvent:
    return DevModelEvent(
        v=1,
        type="event",
        event_id="evt_test00000001",
        event="dev_model",
        occurred_at=datetime(2026, 10, 26, 12, 0, tzinfo=UTC),
        data=DevModelData(**data),
    )


def test_set_switches_the_writer_to_that_model():
    writer = FakeWriter()

    _handle(
        _dev_model({"op": "set", "model": MODEL}),
        _state(dev_endpoints=True, writer=writer),
    )

    assert writer.calls == [MODEL]


def test_reset_goes_back_to_the_chain_from_the_environment():
    writer = FakeWriter()

    _handle(_dev_model({"op": "reset"}), _state(dev_endpoints=True, writer=writer))

    assert writer.calls == [None]


def test_dev_model_is_ignored_when_dev_endpoints_are_off(caplog):
    writer = FakeWriter()

    with caplog.at_level(logging.INFO):
        _handle(
            _dev_model({"op": "set", "model": MODEL}),
            _state(dev_endpoints=False, writer=writer),
        )

    assert writer.calls == []
    assert "dev_model ignored: dev endpoints are off" in caplog.text


def test_refused_switch_is_logged_and_nothing_is_raised(caplog):
    writer = FakeWriter(error=ValueError("no key set for provider 'nvidia'"))

    with caplog.at_level(logging.INFO):
        _handle(
            _dev_model({"op": "set", "model": "nvidia:nvidia/nemotron-3.5-bolt-e4b"}),
            _state(dev_endpoints=True, writer=writer),
        )

    assert "dev_model set refused: no key set for provider 'nvidia'" in caplog.text


@pytest.mark.parametrize(
    "data",
    [
        {"op": "set"},
        {"op": "reset", "model": MODEL},
    ],
    ids=["set_without_model", "reset_with_model"],
)
def test_model_must_match_the_op(data: dict[str, Any]):
    message = {
        "v": 1,
        "type": "event",
        "event_id": "evt_test00000001",
        "event": "dev_model",
        "occurred_at": "2026-10-26T12:00:00Z",
        "data": data,
    }

    with pytest.raises(ValidationError):
        parse_message(json.dumps(message))


def _client(*, dev_endpoints: bool) -> TestClient:
    # _env_file=None keeps a developer's real .env out of the test. The fake key only builds models.
    settings = Settings(
        _env_file=None,
        bridge_token=SecretStr(TOKEN),
        dev_endpoints=dev_endpoints,
        groq_api_key="fake-key-for-tests",
        llm_chain=PINNED_CHAIN,
        llm_chain_irritated=PINNED_CHAIN,
    )
    app = create_app(settings)
    app.dependency_overrides[get_settings] = lambda: settings
    return TestClient(app)


def _welcome(client: TestClient) -> dict[str, Any]:
    with client.websocket_connect("/bridge", headers=AUTH_HEADERS) as mod:
        mod.send_text(HELLO)
        return json.loads(mod.receive_text())


def test_welcome_lists_the_models_only_with_dev_endpoints():
    with_dev = _welcome(_client(dev_endpoints=True))
    without_dev = _welcome(_client(dev_endpoints=False))

    assert with_dev["models"] == [
        "groq:qwen/qwen3.8-27b",
        "groq:openai/gpt-oss-120b",
    ]
    assert "models" not in without_dev
