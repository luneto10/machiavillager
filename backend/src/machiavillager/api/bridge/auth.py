"""Bearer-token decision for the mod bridge. Pure logic: no sockets, no I/O."""

import secrets
from typing import Final

from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import SecretStr

# auto_error=False: a missing or malformed header yields None instead of an HTTP 403. That lets
# refusal_reason decide and say why in the log, which a bare 403 would hide.
bearer: Final = HTTPBearer(auto_error=False)

REASON_NO_TOKEN: Final = "no token configured"
REASON_MALFORMED: Final = "missing or malformed bearer token"
REASON_WRONG_TOKEN: Final = "wrong token"


def refusal_reason(
    credentials: HTTPAuthorizationCredentials | None,
    expected: SecretStr | None,
) -> str | None:
    """Return why the connection must be refused, or None when the token is accepted."""

    # Fail closed: with no token configured, the bridge is off for everyone.
    if expected is None or not expected.get_secret_value():
        return REASON_NO_TOKEN

    if credentials is None or not credentials.credentials:
        return REASON_MALFORMED

    # Constant-time comparison, so response timing cannot reveal how much of the token matched.
    presented = credentials.credentials.encode()
    if not secrets.compare_digest(presented, expected.get_secret_value().encode()):
        return REASON_WRONG_TOKEN

    return None
