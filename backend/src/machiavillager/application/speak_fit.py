"""Fit model output to what the mod can speak: trimmed, CJK-free, split cleanly, at most 8 lines."""

import re
from typing import Final

from machiavillager.domain.protocol.common import MAX_LINE_LENGTH, MAX_SPEAK_LINES

# Open models sometimes leak Chinese or Japanese into Portuguese, which the player cannot read.
_NON_LATIN_TEXT: Final = re.compile(
    "[\u3000-\u303f\u3040-\u309f\u30a0-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af\uff00-\uffef]"
)

# Removing a character can leave two spaces side by side, so they are collapsed after it.
_DOUBLE_SPACE: Final = re.compile(" {2,}")

# A sentence end is punctuation then a space; the lookahead leaves that space in the rest.
_SENTENCE_END: Final = re.compile(r"[.!?…](?= )")


def fit_to_speak(lines: list[str]) -> list[str]:
    """Return lines the mod accepts: clean, non-empty, each within the length limit."""

    # Lines empty after cleaning carry nothing to say, so they go before splitting or counting.
    cleaned = (_clean(line) for line in lines)
    fitted = [piece for line in cleaned if line for piece in _split_long(line)]

    # The mod rejects more than the limit of lines, so the surplus is dropped, not wrapped.
    return fitted[:MAX_SPEAK_LINES]


def _clean(line: str) -> str:
    """Strip CJK characters and the double spaces they leave behind, then trim the ends."""

    # Removal runs before the trim, so a line made only of CJK ends up empty and is dropped.
    without_cjk = _NON_LATIN_TEXT.sub("", line)

    # Collapsing keeps "em 呃 melhorias" from becoming "em  melhorias" after the removal.
    single_spaced = _DOUBLE_SPACE.sub(" ", without_cjk)
    return single_spaced.strip()


def _split_long(line: str) -> list[str]:
    """Cut one stripped line into pieces no longer than the line limit."""

    # Short lines pass through untouched; only over-long ones need cutting.
    pieces: list[str] = []
    rest = line
    while len(rest) > MAX_LINE_LENGTH:
        cut = _cut_point(rest)
        pieces.append(rest[:cut].rstrip())
        rest = rest[cut:].lstrip()

    # The last piece is within the limit already, so it is kept as it is.
    pieces.append(rest)
    return pieces


def _cut_point(text: str) -> int:
    """Index to cut at: after the last sentence end, else at the last space, else at the limit."""

    # A cut after sentence punctuation keeps each piece ending on its own punctuation.
    window = text[: MAX_LINE_LENGTH + 1]
    sentence_ends = list(_SENTENCE_END.finditer(window))
    if sentence_ends:
        return sentence_ends[-1].end()

    # A cut at the space keeps words whole; the space itself is removed by the rstrip and lstrip.
    space = text.rfind(" ", 0, MAX_LINE_LENGTH + 1)
    if space > 0:
        return space

    # No space to break on, so the text is cut mid-word at the limit.
    return MAX_LINE_LENGTH
