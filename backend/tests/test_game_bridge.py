import asyncio
import json
import re
from typing import Any

import pytest

from machiavillager.api.bridge import requests
from machiavillager.api.bridge.connections import ModConnections
from machiavillager.api.bridge.ids import new_request_id
from machiavillager.api.bridge.requests import ModGameBridge, PendingRequests
from machiavillager.application.ports import BridgeUnavailable
from machiavillager.domain.protocol import (
    ACTION_ID_PATTERN,
    MIN_TIMEOUT_MS,
    PROTOCOL_VERSION,
    Failed,
    SpeakAction,
    SpeakArgs,
    Succeeded,
    is_terminal,
    parse_message,
)

REQUEST_ID = "act_0123456789abcdef"
OTHER_REQUEST_ID = "act_fedcba9876543210"


class FakeSocket:
    """Stands in for the WebSocket: records the text the backend sends to the mod."""

    def __init__(self) -> None:
        self.sent: list[str] = []

    async def send_text(self, text: str) -> None:
        self.sent.append(text)


def _reply(status: str, request_id: str = REQUEST_ID, **extra: Any) -> Any:
    # Parsed from the wire shape, so the test uses exactly what the mod would send.
    body = {"v": PROTOCOL_VERSION, "type": "action_status", "request_id": request_id}
    return parse_message(json.dumps(body | {"status": status} | extra))


def _speak(timeout_ms: int = MIN_TIMEOUT_MS, request_id: str = REQUEST_ID) -> SpeakAction:
    return SpeakAction(
        v=PROTOCOL_VERSION,
        type="action",
        request_id=request_id,
        action="speak",
        args=SpeakArgs(lines=["Hello."]),
        timeout_ms=timeout_ms,
    )


def test_progress_statuses_do_not_resolve_the_request():
    async def scenario() -> None:
        pending = PendingRequests()
        future = pending.create(REQUEST_ID)

        pending.resolve(_reply("accepted"))
        pending.resolve(_reply("running"))

        assert not future.done()
        assert len(pending) == 1

    asyncio.run(scenario())


def test_terminal_status_resolves_the_request():
    async def scenario() -> None:
        pending = PendingRequests()
        future = pending.create(REQUEST_ID)

        pending.resolve(_reply("succeeded"))

        assert future.result().status == "succeeded"
        assert len(pending) == 0

    asyncio.run(scenario())


def test_status_for_unknown_request_is_ignored():
    async def scenario() -> None:
        pending = PendingRequests()
        future = pending.create(REQUEST_ID)

        pending.resolve(_reply("succeeded", request_id=OTHER_REQUEST_ID))

        assert not future.done()
        assert len(pending) == 1

    asyncio.run(scenario())


def test_cancel_all_fails_every_pending_request_as_disconnected():
    async def scenario() -> None:
        pending = PendingRequests()
        future = pending.create(REQUEST_ID)

        pending.cancel_all("disconnected")

        result = future.result()
        assert isinstance(result, Failed)
        assert result.reason == "disconnected"
        assert result.request_id == REQUEST_ID
        assert len(pending) == 0

    asyncio.run(scenario())


def test_request_times_out_as_failed_and_forgets_the_id(monkeypatch: pytest.MonkeyPatch):
    # No margin here: the test should time out in about 100 ms, not 2 s.
    monkeypatch.setattr(requests, "TIMEOUT_MARGIN_SECONDS", 0.0)
    connections = ModConnections()
    socket = FakeSocket()
    pending = PendingRequests()

    async def scenario() -> Any:
        await connections.take_over(socket)  # type: ignore[arg-type]
        bridge = ModGameBridge(connections, pending)
        return await bridge.request(_speak(timeout_ms=MIN_TIMEOUT_MS))

    result = asyncio.run(scenario())

    assert isinstance(result, Failed)
    assert result.reason == "timeout"
    assert len(pending) == 0
    # The action really went out before the wait started.
    assert json.loads(socket.sent[0])["request_id"] == REQUEST_ID


def test_request_without_a_mod_raises_bridge_unavailable():
    bridge = ModGameBridge(ModConnections(), PendingRequests())

    with pytest.raises(BridgeUnavailable):
        asyncio.run(bridge.request(_speak()))


def test_new_request_id_matches_the_protocol_pattern():
    request_id = new_request_id()

    assert re.fullmatch(ACTION_ID_PATTERN, request_id)


@pytest.mark.parametrize(
    ("status", "terminal"),
    [("accepted", False), ("running", False), ("succeeded", True)],
)
def test_is_terminal_splits_progress_from_answers(status: str, terminal: bool):
    assert is_terminal(_reply(status)) is terminal


def test_succeeded_is_the_concrete_terminal_model():
    assert isinstance(_reply("succeeded"), Succeeded)
