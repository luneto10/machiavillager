"""WebSocket bridge the Minecraft mod connects to.

Wire rules: docs/decisions/0004-bridge-protocol.md.
"""

from machiavillager.api.bridge.connections import ModConnections
from machiavillager.api.bridge.routes import router

__all__ = ["ModConnections", "router"]
