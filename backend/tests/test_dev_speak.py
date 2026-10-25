import json
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from typing import Any

from fastapi.testclient import TestClient
from pydantic import SecretStr

from machiavillager.api.app import create_app
from machiavillager.domain.protocol import (
    PROTOCOL_VERSION,
    SpeakAction,
    parse_message,
)
from machiavillager.infrastructure.settings import Settings, get_settings

TOKEN = "test-bridge-token-0123456789"
AUTH_HEADERS = {"Authorization": f"Bearer {TOKEN}"}
SPEAK_TIMEOUT_MS = 5000
# Generous for a CI box, still short enough that a hang fails instead of stalling forever.
POST_WAIT_SECONDS = 10
HELLO = '{"v": 1, "type": "hello", "mod_version": "0.1.0", "protocol": 1}'


def _client(dev_endpoints: bool) -> TestClient:
    # _env_file=None keeps a developer's real .env out of the tests.
    settings = Settings(
        _env_file=None,
        bridge_token=SecretStr(TOKEN),
        dev_endpoints=dev_endpoints,
    )
    app = create_app(settings)
    app.dependency_overrides[get_settings] = lambda: settings
    return TestClient(app)


def _status_json(request_id: str, status: str, **extra: Any) -> str:
    body = {"v": PROTOCOL_VERSION, "type": "action_status", "request_id": request_id}
    return json.dumps(body | {"status": status} | extra)


@contextmanager
def _connect_fake_mod(client: TestClient) -> Iterator[Any]:
    with client.websocket_connect("/bridge", headers=AUTH_HEADERS) as mod:
        mod.send_text(HELLO)
        mod.receive_text()  # welcome
        yield mod


def _post_speak(client: TestClient, lines: list[str]) -> Any:
    return client.post("/dev/speak", json={"lines": lines, "timeout_ms": SPEAK_TIMEOUT_MS})


def test_endpoint_is_absent_when_dev_endpoints_are_off():
    response = _client(dev_endpoints=False).post("/dev/speak", json={"lines": ["Hi"]})

    assert response.status_code == 404


def test_speak_without_a_connected_mod_answers_503():
    response = _client(dev_endpoints=True).post("/dev/speak", json={"lines": ["Hi"]})

    assert response.status_code == 503
    assert "no mod is connected" in response.json()["detail"]


def test_empty_lines_are_rejected_before_reaching_the_bridge():
    response = _client(dev_endpoints=True).post("/dev/speak", json={"lines": []})

    assert response.status_code == 422


def test_speak_returns_succeeded_when_the_mod_reports_success():
    with (
        _client(dev_endpoints=True) as client,
        _connect_fake_mod(client) as mod,
        ThreadPoolExecutor(max_workers=1) as pool,
    ):
        post = pool.submit(_post_speak, client, ["Hello there."])
        action = parse_message(mod.receive_text())
        assert isinstance(action, SpeakAction)

        # Progress first: the backend must keep waiting until the terminal status.
        for status in ("accepted", "running", "succeeded"):
            mod.send_text(_status_json(action.request_id, status))
        response = post.result(timeout=POST_WAIT_SECONDS)

    assert response.status_code == 200
    assert response.json() == {
        "v": PROTOCOL_VERSION,
        "type": "action_status",
        "request_id": action.request_id,
        "status": "succeeded",
    }


def test_speak_returns_rejected_with_the_mods_reason():
    with (
        _client(dev_endpoints=True) as client,
        _connect_fake_mod(client) as mod,
        ThreadPoolExecutor(max_workers=1) as pool,
    ):
        post = pool.submit(_post_speak, client, ["Hello there."])
        action = parse_message(mod.receive_text())
        mod.send_text(_status_json(action.request_id, "rejected", reason="cooldown"))
        response = post.result(timeout=POST_WAIT_SECONDS)

    assert response.status_code == 200
    assert response.json()["status"] == "rejected"
    assert response.json()["reason"] == "cooldown"


def test_mod_disconnect_fails_the_pending_request():
    with _client(dev_endpoints=True) as client, ThreadPoolExecutor(max_workers=1) as pool:
        with _connect_fake_mod(client) as mod:
            post = pool.submit(_post_speak, client, ["Hello there."])
            parse_message(mod.receive_text())
            # Leaving the with-block closes the mod socket while the request is pending.
        response = post.result(timeout=POST_WAIT_SECONDS)

    assert response.status_code == 200
    assert response.json()["status"] == "failed"
    assert response.json()["reason"] == "disconnected"
