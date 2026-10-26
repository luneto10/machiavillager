"""Recent dialogue per conversation, kept in memory. CP7 (#72) saves it per world.

Time is passed in by the caller, so tests control it and this module never reads a clock.
"""

from typing import Final

from machiavillager.application.prompt_builder import ConversationLine

# Only the newest lines reach the prompt, so a long chat cannot grow it without limit. Twenty is
# what Verity keeps: enough to remember what was just said and done, small enough to stay cheap.
MAX_LINES: Final = 20
# Optional: a conversation with no line for this long is over. Off by default, so he does not forget
# a chat because the player went quiet for a minute.
SILENCE_SECONDS: Final = 60.0


class ConversationStore:
    """Keeps the last lines of each conversation; forgets it after a silence only when asked to."""

    def __init__(self, silence_seconds: float | None = None) -> None:
        self._silence_seconds = silence_seconds
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
        if (
            self._silence_seconds is None
            or last_at is None
            or now - last_at <= self._silence_seconds
        ):
            return
        self._lines.pop(key, None)
        self._last_at.pop(key, None)
