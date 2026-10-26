"""Trims the recent dialogue sent to the model to a token budget. Pure: no I/O."""

import math
from typing import Final

from machiavillager.application.prompt_builder import ConversationLine

# Rough for Portuguese and English chat; errs on the side of counting more.
ESTIMATED_CHARS_PER_TOKEN: Final = 3
# About 12 average lines; the persona and rules are about 700 on top.
MAX_HISTORY_TOKENS: Final = 450
# Covers the speaker tag and role that wrap each line in the prompt.
LINE_OVERHEAD_TOKENS: Final = 8


def estimate_tokens(text: str) -> int:
    """Rough token count for a piece of text; empty text costs nothing."""

    # Rounding up means a short non-empty line still costs at least one token.
    if not text:
        return 0
    return math.ceil(len(text) / ESTIMATED_CHARS_PER_TOKEN)


def fit_history(
    lines: list[ConversationLine], max_tokens: int = MAX_HISTORY_TOKENS
) -> list[ConversationLine]:
    """Keep the newest lines that fit the budget, in their original order."""

    # Walking from the newest line means the freshest context is the part that survives.
    kept: list[ConversationLine] = []
    used = 0
    for line in reversed(lines):
        cost = _line_tokens(line)
        # The newest line is always kept, even when it alone is over budget.
        if kept and used + cost > max_tokens:
            break
        kept.append(line)
        used += cost
    kept.reverse()

    # A window that opens on his own line reads oddly to the model, so those lead lines go.
    # The last line is never dropped, so the player's newest line always reaches the model.
    while len(kept) > 1 and kept[0].from_npc:
        kept.pop(0)
    return kept


def _line_tokens(line: ConversationLine) -> int:
    """What one line costs in the prompt: its speaker, its text and the wrapper."""

    return estimate_tokens(line.speaker) + estimate_tokens(line.text) + LINE_OVERHEAD_TOKENS
