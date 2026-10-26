import json
import time
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any
from uuid import UUID

from fastapi.testclient import TestClient
from pydantic import SecretStr

from machiavillager.api.app import create_app
from machiavillager.api.bridge.ids import new_request_id
from machiavillager.application.conversation_store import ConversationStore
from machiavillager.application.handle_player_chat import HandlePlayerChat
from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.domain.protocol import (
    MAX_LINE_LENGTH,
    PROTOCOL_VERSION,
    SpeakAction,
    parse_message,
)
from machiavillager.infrastructure.settings import Settings, get_settings

TOKEN = "test-bridge-token-0123456789"
AUTH_HEADERS = {"Authorization": f"Bearer {TOKEN}"}
HELLO = '{"v": 1, "type": "hello", "mod_version": "0.1.0", "protocol": 1}'
ECHO_PREFIX = "(echo) "
PLAYER_UUID = "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69"
CONVERSATION_ID = "conv_01HZX8K2M9"


def _client(dev_endpoints: bool, dev_echo: bool) -> TestClient:
    # _env_file=None keeps a developer's real .env out of the tests.
    settings = Settings(
        _env_file=None,
        bridge_token=SecretStr(TOKEN),
        dev_endpoints=dev_endpoints,
        dev_echo=dev_echo,
    )
    app = create_app(settings)
    app.dependency_overrides[get_settings] = lambda: settings
    return TestClient(app)


@contextmanager
def _connect_fake_mod(client: TestClient) -> Iterator[Any]:
    with client.websocket_connect("/bridge", headers=AUTH_HEADERS) as mod:
        mod.send_text(HELLO)
        mod.receive_text()  # welcome
        yield mod


def _status_json(request_id: str, status: str) -> str:
    body = {"v": PROTOCOL_VERSION, "type": "action_status", "request_id": request_id}
    return json.dumps(body | {"status": status})


def _chat(client: TestClient, **fields: Any) -> Any:
    return client.post("/dev/chat", json={"player_name": "Luciano", "text": "hi"} | fields)


def test_chat_endpoint_is_absent_when_dev_endpoints_are_off():
    response = _chat(_client(dev_endpoints=False, dev_echo=False))

    assert response.status_code == 404


def test_unknown_address_is_rejected_before_reaching_the_handler():
    with _client(dev_endpoints=True, dev_echo=False) as client:
        response = _chat(client, addressed="shout")

    assert response.status_code == 422


def test_chat_is_accepted_when_echo_is_off():
    with _client(dev_endpoints=True, dev_echo=False) as client:
        response = _chat(client)

    assert response.status_code == 202
    assert response.json()["event_id"].startswith("evt_")


def test_echo_repeats_a_public_chat_line_to_the_mod():
    with (
        _client(dev_endpoints=True, dev_echo=True) as client,
        _connect_fake_mod(client) as mod,
    ):
        response = _chat(client, text="hi there")
        action = parse_message(mod.receive_text())
        mod.send_text(_status_json(action.request_id, "succeeded"))

    assert response.status_code == 202
    assert isinstance(action, SpeakAction)
    assert action.args.lines == [ECHO_PREFIX + "hi there"]
    # Public chat is spoken normally, so there is no private recipient.
    assert action.args.to is None
    # The echo answers the chat event, so the mod treats it as a reply, not unprompted speech.
    assert action.in_reply_to == response.json()["event_id"]


def test_echo_answers_a_private_chat_privately():
    with (
        _client(dev_endpoints=True, dev_echo=True) as client,
        _connect_fake_mod(client) as mod,
    ):
        _chat(client, text="psst", addressed="private", player_uuid=PLAYER_UUID)
        action = parse_message(mod.receive_text())
        assert isinstance(action, SpeakAction)
        mod.send_text(_status_json(action.request_id, "succeeded"))

    assert action.args.lines[0].startswith(ECHO_PREFIX)
    assert action.args.to == UUID(PLAYER_UUID)


def test_echo_keeps_the_conversation_id_of_the_chat():
    with (
        _client(dev_endpoints=True, dev_echo=True) as client,
        _connect_fake_mod(client) as mod,
    ):
        _chat(client, addressed="conversation", conversation_id=CONVERSATION_ID)
        action = parse_message(mod.receive_text())
        assert isinstance(action, SpeakAction)
        mod.send_text(_status_json(action.request_id, "succeeded"))

    assert action.conversation_id == CONVERSATION_ID


def test_echo_truncates_a_long_line_to_the_protocol_limit():
    with (
        _client(dev_endpoints=True, dev_echo=True) as client,
        _connect_fake_mod(client) as mod,
    ):
        _chat(client, text="x" * MAX_LINE_LENGTH)
        action = parse_message(mod.receive_text())
        assert isinstance(action, SpeakAction)
        mod.send_text(_status_json(action.request_id, "succeeded"))

    assert len(action.args.lines[0]) == MAX_LINE_LENGTH
    assert action.args.lines[0].startswith(ECHO_PREFIX)


class _FixedWriter:
    """Stands in for the LLM: answers with fixed lines, so no network is used."""

    async def write_reply(self, messages: list[PromptMessage], *, irritated: bool) -> list[str]:
        return ["Ah, Luciano.", "Sit down."]


def test_player_chat_gets_the_ai_reply_over_the_real_bridge():
    with (
        _client(dev_endpoints=True, dev_echo=False) as client,
        _connect_fake_mod(client) as mod,
    ):
        # The app's real bridge, a fake writer: the reply path runs end to end without a model.
        client.app.state.handle_player_chat = HandlePlayerChat(
            _FixedWriter(),
            client.app.state.game_bridge,
            ConversationStore(),
            lambda: "Persona.",
            "mild",
            new_request_id,
            time.monotonic,
        )
        response = _chat(client, text="hi there", addressed="private", player_uuid=PLAYER_UUID)
        action = parse_message(mod.receive_text())
        assert isinstance(action, SpeakAction)
        mod.send_text(_status_json(action.request_id, "succeeded"))

    assert response.status_code == 202
    assert action.args.lines == ["Ah, Luciano.", "Sit down."]
    assert action.in_reply_to == response.json()["event_id"]
    assert action.args.to == UUID(PLAYER_UUID)
