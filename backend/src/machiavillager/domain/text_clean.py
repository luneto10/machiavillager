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


def speakable(lines: list[str]) -> list[str]:
    """The lines with emoji removed; a line that was only emoji is dropped."""

    # Cleaned once here, so every writer that hands lines to the game applies the same rule.
    cleaned = (strip_emoji(line) for line in lines)
    return [line for line in cleaned if line]
