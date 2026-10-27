"""Builds the chat messages for one batch of lines. Pure: no provider types, no I/O."""

from typing import Final

from machiavillager.application.prompt_builder import (
    PROFANITY_RULES,
    ProfanityLevel,
    PromptMessage,
    persona_for_phase,
)
from machiavillager.domain.protocol import LineKind
from machiavillager.domain.protocol.common import NpcMood, NpcPhase

# The rules and the persona carry the whole request, so the user message only asks for it.
BATCH_USER_MESSAGE: Final = "Write the lines."

# What each kind of line is for, and the placeholders it must carry. Braces stay literal text here.
KIND_RULES: Final[dict[LineKind, str]] = {
    "bark": (
        "Kind bark: a quick call to one nearby player. Use ONLY {player}, in most lines; "
        "never {x} or {z}. e.g. 'Hey {player}, careful with that lava.' "
        "Translate the idea into the target language; never copy the example. "
        "Speak TO the player in the second person. Use {player} only as a call or address "
        "(for example at the start followed by a comma, or at the end), never as the subject "
        "of a sentence about them."
    ),
    "hint": (
        "Kind hint: he tells one player about a village he saw. Include {player}, {x} and {z} in "
        "EVERY line; x and z are the village coordinates. Each line tells {player} about a village "
        "he saw and gives where it is, as a sentence that uses the coordinates, e.g. "
        "'{player}, did you see the village at {x}, {z}? "
        "So disorganized, someone should take charge.' "
        "Another shape: 'Between us, {player}: the village at {x}, {z} has too many emeralds "
        "and too few leaders.' "
        "Translate the idea into the target language; never copy the example. "
        "A line without {x} and {z} is wrong. "
        "Write the coordinates as '{x}, {z}'. "
        "Speak TO the player in the second person. Use {player} only as a call or address "
        "(for example at the start followed by a comma, or at the end), never as the subject "
        "of a sentence about them."
    ),
    "mutter": (
        "Kind mutter: said to himself, to no one. Complain or scheme about the game, the world, "
        "his luck or his plans. Never address anyone in the second person: no 'you' or 'your'. "
        "e.g. 'Gravel always falls on the one who digs.' "
        "Translate the idea into the target language; never copy the example."
    ),
}

# The brace rule per kind: a mutter is told no braces at all, so no placeholder name reaches it.
BRACE_RULES: Final[dict[LineKind, str]] = {
    "bark": "The only brace allowed is {player}; write no other braces.",
    "hint": "The only braces allowed are {player}, {x} and {z}; write no other braces.",
    "mutter": "Write no braces at all.",
}

# The tone per mood, so each mood gets its own bite instead of one vague word.
MOOD_RULES: Final[dict[NpcMood, str]] = {
    "calm": (
        "Mood calm: warm and playful. Tease like a friend; no insults and no cursing at the player."
    ),
    "bored": (
        "Mood bored: sarcastic. Poke the player for a reaction, a mild dig, no heavy insults."
    ),
    "irritated": "Mood irritated: fuming. Roast the player by name, heavy insults allowed.",
}


def build_batch_prompt(
    persona: str,
    kind: LineKind,
    phase: NpcPhase,
    mood: NpcMood,
    *,
    language: str,
    profanity: ProfanityLevel,
    count: int,
) -> list[PromptMessage]:
    """Batch rules, the phase's persona and the profanity rule as system text, then the request."""

    # Written as plain text: the model needs exact placeholders, so no format string is used.
    # The brace rule is the one the contract enforces, so the model sees it before it writes.
    rules = (
        f"Write {count} different lines he says out loud, on his own, in the language with tag "
        f"{language}.\n"
        "Each line is at most 120 characters. Spoken words only: no stage directions, no emoji, "
        "no written laughter.\n"
        "Write normal sentences with capital letters and punctuation; the lines are read aloud.\n"
        "Vary the jokes and the openings; no two lines start the same way.\n"
        "At most one line of the whole batch may mention missing pumpkin pie, in passing.\n"
        f"{MOOD_RULES[mood]}\n"
        "The mood sets only the tone. The kind rule below always wins.\n"
        f"{KIND_RULES[kind]}\n"
        f"{BRACE_RULES[kind]}\n"
        "Answer by calling deliver_lines once with the lines array, and nothing else."
    )

    # Same order as the chat prompt: rules first, then persona, then profanity.
    return [
        PromptMessage("system", rules),
        PromptMessage("system", persona_for_phase(persona, phase)),
        PromptMessage("system", PROFANITY_RULES[profanity]),
        PromptMessage("user", BATCH_USER_MESSAGE),
    ]
