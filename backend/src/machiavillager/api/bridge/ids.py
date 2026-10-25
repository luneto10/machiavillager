"""Ids the backend makes for the bridge.

Kept apart from requests.py: session.py imports events.py, which needs these ids, and requests.py
imports session.py, so putting them in requests.py would make an import cycle.
"""

import secrets
from typing import Final

REQUEST_ID_PREFIX: Final = "act_"
# 8 random bytes give 16 hex characters, inside the 8-32 the protocol pattern allows.
REQUEST_ID_BYTES: Final = 8


def new_request_id() -> str:
    # token_hex gives a short id made of safe characters, matching the protocol pattern.
    return REQUEST_ID_PREFIX + secrets.token_hex(REQUEST_ID_BYTES)
