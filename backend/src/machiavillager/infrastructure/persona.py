"""Reads the persona text from the file that ships inside the package."""

from pathlib import Path
from typing import Final

# parents[1] is the machiavillager package folder; the persona file sits next to the layer folders.
PERSONA_PATH: Final = Path(__file__).resolve().parents[1] / "persona" / "machiavillager.md"


def read_persona() -> str:
    """Return the persona text as the file currently says it."""

    # Read on every call, not cached, so edits apply on the next reply without a restart.
    return PERSONA_PATH.read_text(encoding="utf-8")
