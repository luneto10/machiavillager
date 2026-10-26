"""Ports the application layer needs from the outside world. Adapters live in api/bridge."""

from dataclasses import dataclass
from typing import Protocol

from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.domain.protocol import BackendAction, TerminalStatus


class BridgeUnavailable(Exception):
    """No mod is connected, so an action cannot be sent right now."""


class GameBridge(Protocol):
    """Sends one action to the game and waits for its final status."""

    async def request(self, action: BackendAction) -> TerminalStatus:
        """Return the terminal status. Raises BridgeUnavailable when no mod is connected."""


@dataclass(frozen=True)
class Feeling:
    """A bounded change of how he feels about the player who spoke, with the reason it happened."""

    # Already clamped by the writer; the contract checks the range again on the way out.
    affinity_delta: int
    reason: str


@dataclass(frozen=True)
class Reply:
    """What the writer produced: the lines, the feeling, and which model wrote them and how long."""

    # Empty lines mean he chose silence. A canned line has no model, so model stays None.
    lines: list[str]
    # Dev info only: "provider:model" of the model that answered, when a model wrote the lines.
    model: str | None = None
    # Dev info only: how long that model took, in milliseconds.
    latency_ms: int | None = None
    # Set only when the writer found a real reason to feel differently about the player.
    feeling: Feeling | None = None


class LineWriter(Protocol):
    """Turns a built prompt into the lines he speaks."""

    async def write_reply(self, messages: list[PromptMessage], *, irritated: bool) -> Reply:
        """Return the reply: 0-8 lines that fit speak, where an empty list means he chose silence.

        Never raises for provider problems.
        """
