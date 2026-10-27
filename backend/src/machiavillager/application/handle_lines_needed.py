"""Answers one lines_needed event: writes a batch for its key and sends it as deliver_lines."""

import logging
import time
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass
from typing import Final

from pydantic import ValidationError

from machiavillager.application.ports import BatchWriter, BridgeUnavailable, GameBridge
from machiavillager.domain.protocol import (
    MAX_BATCH_LINES,
    MAX_TIMEOUT_MS,
    PROTOCOL_VERSION,
    DeliverLinesAction,
    DeliverLinesArgs,
    LineKind,
    LinesNeededEvent,
    Succeeded,
)
from machiavillager.domain.protocol.common import NpcMood, NpcPhase

logger = logging.getLogger(__name__)

HOUR_SECONDS: Final = 3600.0

# A batch with fewer valid lines is not sent: a thin pool is not worth a delivery.
MIN_VALID_LINES: Final = 5

# The pool a batch is filed under: one kind, one phase and one mood.
LineKey = tuple[LineKind, NpcPhase, NpcMood]


def fits_kind(kind: LineKind, line: str) -> bool:
    """Whether a line uses only the placeholders its kind allows."""

    # A bark calls one player, so the village coordinates have no place in it.
    if kind == "bark":
        return "{x}" not in line and "{z}" not in line

    # A mutter names nobody, so it carries no braces at all.
    if kind == "mutter":
        return "{" not in line and "}" not in line

    # A hint without its coordinates tells the player nothing useful.
    return "{x}" in line and "{z}" in line


@dataclass(frozen=True)
class _Attempt:
    """When a key was last attempted, and whether that attempt sent any lines."""

    at: float
    produced: bool


class HandleLinesNeeded:
    """Fills the mod's line pools, with a wait per key and a cap on batches per hour."""

    def __init__(
        self,
        writer: BatchWriter,
        bridge: GameBridge,
        new_request_id: Callable[[], str],
        clock: Callable[[], float] = time.monotonic,
        *,
        batches_per_hour: int,
        refresh_seconds: int,
        retry_seconds: int,
    ) -> None:
        self._writer = writer
        self._bridge = bridge
        self._new_request_id = new_request_id
        self._clock = clock
        self._batches_per_hour = batches_per_hour
        self._refresh_seconds = refresh_seconds
        self._retry_seconds = retry_seconds
        self._in_flight: set[LineKey] = set()
        self._last_attempt: dict[LineKey, _Attempt] = {}
        # Start times of the attempts in the last hour, oldest first.
        self._attempts: deque[float] = deque()

    async def __call__(self, event: LinesNeededEvent) -> None:
        key: LineKey = (event.data.kind, event.data.phase, event.data.mood)
        now = self._clock()

        # Attempts older than an hour stop counting toward the cap before it is checked.
        self._forget_old_attempts(now)
        reason = self._skip_reason(key, now)
        if reason is not None:
            logger.info("lines_needed skipped: %s", reason)
            return

        # Recorded before the writer runs, so a second event for this key is skipped while it works.
        self._attempts.append(now)
        self._in_flight.add(key)
        produced = False
        try:
            produced = await self._deliver(key)
        finally:
            self._in_flight.discard(key)
            self._last_attempt[key] = _Attempt(at=now, produced=produced)

    def _forget_old_attempts(self, now: float) -> None:
        """Drop the attempts that are over an hour old."""

        # The deque is in time order, so the old ones are always at the front.
        while self._attempts and now - self._attempts[0] >= HOUR_SECONDS:
            self._attempts.popleft()

    def _skip_reason(self, key: LineKey, now: float) -> str | None:
        """Why this event gets no batch, or None when it may be written."""

        # Two batches for one key at once would fill its pool twice.
        if key in self._in_flight:
            return "in_flight"

        # A key that got lines waits the refresh time; an empty one waits the shorter retry.
        last = self._last_attempt.get(key)
        if last is not None:
            wait = self._refresh_seconds if last.produced else self._retry_seconds
            if now - last.at < wait:
                return "key_cooldown"

        # The cap counts every attempt in the last hour, empty ones too, so the budget holds.
        if len(self._attempts) >= self._batches_per_hour:
            return "hourly_cap"
        return None

    async def _deliver(self, key: LineKey) -> bool:
        """Write one batch and send it. True when the mod was sent lines."""

        kind, phase, mood = key
        raw = await self._writer.write_batch(kind, phase, mood)
        lines = self._checked_lines(key, raw)

        # A thin batch counts as an empty attempt, so the short retry wait applies.
        if len(lines) < MIN_VALID_LINES:
            logger.info("lines_needed dropped: only %d valid lines", len(lines))
            return False

        # Timeout matches the speak action: the mod may take as long as it needs to store the batch.
        action = DeliverLinesAction(
            v=PROTOCOL_VERSION,
            type="action",
            request_id=self._new_request_id(),
            action="deliver_lines",
            args=DeliverLinesArgs(kind=kind, phase=phase, mood=mood, lines=lines),
            timeout_ms=MAX_TIMEOUT_MS,
        )
        try:
            status = await self._bridge.request(action)
        except BridgeUnavailable:
            logger.info("lines %s not sent: no mod is connected", action.request_id)
            return False

        # Only a stored batch counts as sent; any other end leaves the key on the short retry wait.
        if not isinstance(status, Succeeded):
            logger.info("deliver_lines not stored: %s", status.status)
            return False

        # Only the request id and the status are logged, never the lines.
        logger.info("lines %s finished: %s", action.request_id, status.status)
        return True

    def _checked_lines(self, key: LineKey, raw: list[str]) -> list[str]:
        """Keep the lines the contract and their kind accept, at most the batch limit."""

        kind, phase, mood = key

        # Each line is checked alone, so one bad line is dropped and the rest of the batch is kept.
        kept: list[str] = []
        contract_failed = 0
        kind_failed = 0
        for line in raw:
            try:
                DeliverLinesArgs(kind=kind, phase=phase, mood=mood, lines=[line])
            except ValidationError:
                contract_failed += 1
                continue

            # The contract only knows the braces; the kind decides which of them a line may use.
            if not fits_kind(kind, line):
                kind_failed += 1
                continue
            kept.append(line)

        # Each dropped line is counted once, under the first rule it failed.
        dropped = contract_failed + kind_failed
        if dropped:
            logger.info(
                "lines_needed: dropped %d (contract %d, kind rule %d)",
                dropped,
                contract_failed,
                kind_failed,
            )
        return kept[:MAX_BATCH_LINES]
