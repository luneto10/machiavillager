"""Ports the application layer needs from the outside world. Adapters live in api/bridge."""

from typing import Protocol

from machiavillager.domain.protocol import SpeakAction, TerminalStatus


class BridgeUnavailable(Exception):
    """No mod is connected, so an action cannot be sent right now."""


class GameBridge(Protocol):
    """Sends one action to the game and waits for its final status."""

    async def request(self, action: SpeakAction) -> TerminalStatus:
        """Return the terminal status. Raises BridgeUnavailable when no mod is connected."""
