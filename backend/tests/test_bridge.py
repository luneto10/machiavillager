import json
import logging
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr
from starlette.websockets import WebSocketDisconnect

from machiavillager.api.app import create_app
from machiavillager.api.bridge import connections, session
from machiavillager.domain.protocol import Error, Welcome, parse_message
from machiavillager.infrastructure.settings import Settings, get_settings

# This file sits in backend/tests/, so two parents up is the repo root that holds shared/.
EXAMPLES_DIR = Path(__file__).resolve().parents[2] / "shared" / "examples" / "valid"
TOKEN = "test-bridge-token-0123456789"
AUTH_HEADERS = {"Authorization": f"Bearer {TOKEN}"}
POLICY_VIOLATION = 1008
BAD_ENVELOPE_JSON = "not json at all"
# The protocol version a future mod would send. The model only accepts 1, so this must be refused.
FUTURE_PROTOCOL = 2


def _example(name: str) -> str:
    return (EXAMPLES_DIR / name).read_text(encoding="utf-8")


def _hello_raw() -> str:
    return _example("hello.json")


def _client(token: str | None) -> TestClient:
    # _env_file=None keeps a developer's real .env out of the tests (e.g. MACHIAVILLAGER_DEV_ECHO).
    settings = Settings(_env_file=None, bridge_token=SecretStr(token) if token else None)
    app = create_app(settings)
    app.dependency_overrides[get_settings] = lambda: settings
    return TestClient(app)


@contextmanager
def _connect_and_handshake(client: TestClient, headers: dict[str, str]) -> Iterator[Any]:
    with client.websocket_connect("/bridge", headers=headers) as ws:
        ws.send_text(_hello_raw())
        assert isinstance(parse_message(ws.receive_text()), Welcome)
        yield ws


def _assert_refused(client: TestClient, headers: dict[str, str]) -> None:
    with (
        pytest.raises(WebSocketDisconnect) as refused,
        client.websocket_connect("/bridge", headers=headers),
    ):
        pass
    assert refused.value.code == POLICY_VIOLATION


def test_welcome_reply_matches_the_contract():
    with _client(TOKEN).websocket_connect("/bridge", headers=AUTH_HEADERS) as ws:
        ws.send_text(_hello_raw())

        reply = parse_message(ws.receive_text())

    assert isinstance(reply, Welcome)
    assert reply.protocol == 1


def test_wrong_token_is_refused():
    _assert_refused(_client(TOKEN), {"Authorization": "Bearer not-the-token"})


def test_missing_header_is_refused():
    _assert_refused(_client(TOKEN), {})


@pytest.mark.parametrize("header", ["Basic dGVzdA==", "Bearer", "Bearer ", TOKEN])
def test_malformed_header_is_refused(header: str):
    _assert_refused(_client(TOKEN), {"Authorization": header})


@pytest.mark.parametrize("configured", [None, ""])
def test_no_token_configured_fails_closed(configured: str | None):
    # Even a header with a plausible token must not get in when the backend has none set.
    _assert_refused(_client(configured), AUTH_HEADERS)


def test_hello_with_unsupported_protocol_gets_error_then_close():
    hello = json.loads(_hello_raw()) | {"protocol": FUTURE_PROTOCOL}
    with _client(TOKEN).websocket_connect("/bridge", headers=AUTH_HEADERS) as ws:
        ws.send_text(json.dumps(hello))

        reply = parse_message(ws.receive_text())
        assert isinstance(reply, Error)
        assert reply.code == "unsupported_protocol"

        with pytest.raises(WebSocketDisconnect):
            ws.receive_text()


def test_first_message_not_hello_gets_bad_message_then_close():
    with _client(TOKEN).websocket_connect("/bridge", headers=AUTH_HEADERS) as ws:
        ws.send_text(_example("status_accepted.json"))

        reply = parse_message(ws.receive_text())
        assert isinstance(reply, Error)
        assert reply.code == "bad_message"

        with pytest.raises(WebSocketDisconnect):
            ws.receive_text()


def test_garbage_after_welcome_is_answered_and_connection_stays_open():
    with _connect_and_handshake(_client(TOKEN), AUTH_HEADERS) as ws:
        ws.send_text(BAD_ENVELOPE_JSON)
        first = parse_message(ws.receive_text())

        # A valid event expects no reply, so the next reply must be for the garbage below.
        ws.send_text(_example("event_player_chat.json"))
        ws.send_text("{}")
        second = parse_message(ws.receive_text())

    assert isinstance(first, Error)
    assert first.code == "bad_message"
    assert isinstance(second, Error)
    assert second.code == "bad_message"


def test_error_detail_is_short_and_does_not_echo_the_payload():
    oversized = json.dumps({"v": 1, "type": "x" * 5000, "secret_note": "keep-me-out"})
    with _connect_and_handshake(_client(TOKEN), AUTH_HEADERS) as ws:
        ws.send_text(oversized)

        reply = parse_message(ws.receive_text())

    assert isinstance(reply, Error)
    assert len(reply.detail) <= 500
    assert "keep-me-out" not in reply.detail


def test_second_connection_replaces_the_first():
    client = _client(TOKEN)
    with client.websocket_connect("/bridge", headers=AUTH_HEADERS) as first:
        first.send_text(_hello_raw())
        first.receive_text()

        with client.websocket_connect("/bridge", headers=AUTH_HEADERS) as second:
            second.send_text(_hello_raw())
            assert isinstance(parse_message(second.receive_text()), Welcome)

            # The first socket was closed by the server with the replaced code.
            with pytest.raises(WebSocketDisconnect) as replaced:
                first.receive_text()
            assert replaced.value.code == connections.NORMAL_CLOSE_CODE


def test_hello_timeout_gets_bad_message_then_close(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(session, "HELLO_TIMEOUT_SECONDS", 0.1)
    with _client(TOKEN).websocket_connect("/bridge", headers=AUTH_HEADERS) as ws:
        # Send nothing. The server should give up and say why before closing.
        reply = parse_message(ws.receive_text())

        assert isinstance(reply, Error)
        assert reply.code == "bad_message"
        with pytest.raises(WebSocketDisconnect):
            ws.receive_text()


def test_token_never_appears_in_logs(caplog: pytest.LogCaptureFixture):
    caplog.set_level(logging.DEBUG)
    # The app logger does not propagate to root (see create_app), so caplog listens on it directly.
    app_logger = logging.getLogger("machiavillager")
    app_logger.addHandler(caplog.handler)
    try:
        client = _client(TOKEN)

        _assert_refused(client, {"Authorization": f"Bearer {TOKEN}x"})
        with _connect_and_handshake(client, AUTH_HEADERS):
            pass
    finally:
        app_logger.removeHandler(caplog.handler)

    # The refusal and the acceptance must both have logged, or the absence check proves nothing.
    assert "bridge connection refused: wrong token" in caplog.text
    assert "bridge out welcome" in caplog.text
    assert TOKEN not in caplog.text
