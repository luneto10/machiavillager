"""The tone of one player line, judged by a small model, and the affinity change each tone is worth.

The judge only classifies words; it never writes a reply. TONE_DELTAS is the change a judged tone
moves his feeling by, before the word list floor is applied on top.
"""

from typing import Final, Literal

Tone = Literal["hate", "insult", "jab", "neutral", "kind", "apology", "affection"]

# Hatred is the strongest push down; affection the strongest lift. Neutral changes nothing.
TONE_DELTAS: Final[dict[Tone, int]] = {
    "hate": -5,
    "insult": -2,
    "jab": -1,
    "neutral": 0,
    "kind": 1,
    "apology": 1,
    "affection": 2,
}
