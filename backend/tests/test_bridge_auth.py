from pathlib import Path

from fastapi.security import HTTPAuthorizationCredentials
from fastapi.testclient import TestClient
from pydantic import SecretStr

from machiavillager.api.app import create_app
from machiavillager.api.bridge.auth import refusal_reason
from machiavillager.domain.protocol import Welcome, parse_message
from machiavillager.infrastructure.settings import Settings, get_settings

# This file sits in backend/tests/, so two parents up is the repo root that holds shared/.
HELLO_EXAMPLE = Path(__file__).resolve().parents[2] / "shared" / "examples" / "valid" / "hello.json"
TOKEN = "test-bridge-token-0123456789"


def _creds(token: str) -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)


def test_no_token_configured_refuses_everyone():
    assert refusal_reason(_creds(TOKEN), None) == "no token configured"


def test_empty_configured_token_refuses_everyone():
    assert refusal_reason(_creds(TOKEN), SecretStr("")) == "no token configured"


def test_missing_credentials_are_malformed():
    assert refusal_reason(None, SecretStr(TOKEN)) == "missing or malformed bearer token"


def test_empty_credentials_are_malformed():
    assert refusal_reason(_creds(""), SecretStr(TOKEN)) == "missing or malformed bearer token"


def test_wrong_token_is_refused():
    assert refusal_reason(_creds("not-the-token"), SecretStr(TOKEN)) == "wrong token"


def test_right_token_is_accepted():
    assert refusal_reason(_creds(TOKEN), SecretStr(TOKEN)) is None


def test_lowercase_bearer_scheme_is_accepted_on_websocket():
    # HTTPBearer matches the scheme case-insensitively, so "bearer" must open the bridge too.
    app = create_app()
    settings = Settings(_env_file=None, bridge_token=SecretStr(TOKEN))
    app.dependency_overrides[get_settings] = lambda: settings
    client = TestClient(app)

    with client.websocket_connect("/bridge", headers={"Authorization": f"bearer {TOKEN}"}) as ws:
        ws.send_text((HELLO_EXAMPLE).read_text(encoding="utf-8"))
        assert isinstance(parse_message(ws.receive_text()), Welcome)
