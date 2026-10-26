"""Recent dialogue per conversation, kept in memory. CP7 moves it into the database.

Time is passed in by the caller, so tests control it and this module never reads a clock.
"""

from typing import Final

from machiavillager.application.prompt_builder import ConversationLine

# Only the newest lines reach the prompt, so a long chat cannot grow it without limit.
MAX_LINES: Final = 10
# A conversation with no line for this long is over; the next line starts a fresh one.
SILENCE_SECONDS: Final = 60.0


class ConversationStore:
    """Keeps the last lines of each conversation and forgets it after a silence."""

    def __init__(self) -> None:
        self._lines: dict[str, list[ConversationLine]] = {}
        self._last_at: dict[str, float] = {}

    def add(self, key: str, line: ConversationLine, now: float) -> None:
        # An old conversation is cleared first, so a late line never joins a finished thread.
        self._expire(key, now)
        lines = self._lines.setdefault(key, [])
        lines.append(line)
        del lines[:-MAX_LINES]
        self._last_at[key] = now

    def recent(self, key: str, now: float) -> list[ConversationLine]:
        self._expire(key, now)
        return list(self._lines.get(key, []))

    def _expire(self, key: str, now: float) -> None:
        # Silence is measured from the newest line, so an active thread never expires mid-talk.
        last_at = self._last_at.get(key)
        if last_at is None or now - last_at <= SILENCE_SECONDS:
            return
        self._lines.pop(key, None)
        self._last_at.pop(key, None)
