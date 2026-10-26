"""Fit model output to what the mod can speak: trimmed, split at spaces, at most 8 lines."""

from machiavillager.domain.protocol.common import MAX_LINE_LENGTH, MAX_SPEAK_LINES


def fit_to_speak(lines: list[str]) -> list[str]:
    """Return lines the mod accepts: stripped, non-empty, each within the length limit."""

    # Blank lines carry nothing to say, so they go before any splitting or counting.
    stripped = (line.strip() for line in lines)
    fitted = [piece for line in stripped if line for piece in _split_long(line)]

    # The mod rejects more than the limit of lines, so the surplus is dropped, not wrapped.
    return fitted[:MAX_SPEAK_LINES]


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
    """Index to cut at: after the last space within the limit, or at the limit if there is none."""

    # A cut at the space keeps words whole; the space itself is removed by the rstrip and lstrip.
    space = text.rfind(" ", 0, MAX_LINE_LENGTH + 1)
    if space > 0:
        return space

    # No space to break on, so the text is cut mid-word at the limit.
    return MAX_LINE_LENGTH
