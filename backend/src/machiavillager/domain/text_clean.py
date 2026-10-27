"""Text clean-up for lines that are read aloud. Pure: stdlib only, no I/O."""

import re
from typing import Final

# Emoji, pictographs, flags, variation selectors, ZWJ, skin tones and keycap marks, as code points.
# Letters, accents, digits and ordinary punctuation are outside every range, so they are kept.
_EMOJI_CHARS: Final = re.compile(
    "["
    "©®"  # copyright and registered signs
    "‼⁉™ℹ"  # double exclamation, interrobang, trade mark, information
    "↔-↙↩↪"  # arrows that render as emoji
    "⌚-⏿"  # watches, hourglasses, media controls
    "Ⓜ"  # circled M
    "▪-◾"  # small squares and triangles
    "☀-➿"  # weather, symbols and dingbats
    "⤴⤵⬅-⬇⬛⬜⭐⭕"  # arrows and stars
    "〰〽㊗㊙"  # wavy dash and CJK emoji marks
    "\U0001f000-\U0001faff"  # every pictograph block, including flags and skin tones
    "︀-️"  # variation selectors
    "‍"  # zero-width joiner that glues emoji sequences
    "⃣"  # combining keycap
    "\U000e0000-\U000e007f"  # tag characters, used by subdivision flags
    "]"
)

# Two or more spaces in a row, which removing an emoji can leave behind.
_RUN_OF_SPACES: Final = re.compile(" {2,}")


def strip_emoji(text: str) -> str:
    """Return the text without emoji, with the gaps they leave closed and the ends trimmed."""

    # Removed first, so a sequence such as a flag or a ZWJ family goes in one pass.
    without_emoji = _EMOJI_CHARS.sub("", text)

    # Closing the gap keeps "ok 😊 done" reading as "ok done" instead of "ok  done".
    return _RUN_OF_SPACES.sub(" ", without_emoji).strip()


# Em dash, en dash, horizontal bar and two-em dash: TTS reads them badly, so they never get spoken.
_DASH: Final = re.compile(r"\s*[—–―⸺]+\s*")

# Punctuation that already separates words, so a dash next to it is dropped or only spaced.
_PUNCTUATION: Final = ".,;:!?…"

# Stray commas and spaces that a removed dash can leave behind.
_SPACE_BEFORE_COMMA: Final = re.compile(r"\s+,")
_RUN_OF_COMMAS: Final = re.compile(r",(?:\s*,)+")


def _is_word_edge(char: str) -> bool:
    """A letter or digit, or a brace from a {player} placeholder, which reads as a word."""

    return char.isalnum() or char in "{}"


def _replace_dash(match: re.Match[str]) -> str:
    """Pick what a dash becomes from the characters on each side of it."""

    text = match.string
    before = text[: match.start()].rstrip()
    after = text[match.end() :].lstrip()

    # At the very start or end there is nothing to join, so the dash goes.
    if not before or not after:
        return ""

    # After punctuation the mark already pauses; keep a space only before a word.
    if before[-1] in _PUNCTUATION:
        return " " if after[0].isalnum() else ""

    if after[0] in _PUNCTUATION:
        return ""

    # Between two words the dash becomes a pause, which TTS reads naturally.
    if _is_word_edge(before[-1]) and _is_word_edge(after[0]):
        return ", "

    return " "


def fix_dashes(text: str) -> str:
    """Return the text with dashes turned into commas or dropped; other text is untouched."""

    # Text without a dash is returned as it is, so ordinary spacing is never rewritten.
    if not _DASH.search(text):
        return text

    replaced = _DASH.sub(_replace_dash, text)
    without_gaps = _SPACE_BEFORE_COMMA.sub(",", replaced)
    without_commas = _RUN_OF_COMMAS.sub(",", without_gaps)

    # Hyphens inside words and minus signs are never matched, so they stay as written.
    return _RUN_OF_SPACES.sub(" ", without_commas).strip()


def speakable(lines: list[str]) -> list[str]:
    """The lines with emoji and dashes fixed for speech; a line that became empty is dropped."""

    # Cleaned once here, so every writer that hands lines to the game applies the same rule.
    cleaned = (fix_dashes(strip_emoji(line)) for line in lines)
    return [line for line in cleaned if line]
