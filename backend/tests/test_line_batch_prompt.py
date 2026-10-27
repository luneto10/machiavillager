import pytest

from machiavillager.application.line_batch_prompt import (
    BATCH_USER_MESSAGE,
    build_batch_prompt,
)
from machiavillager.application.prompt_builder import PROFANITY_RULES

PERSONA = """\
Shared intro about him.

## Phase: partner
Partner section text.

## Phase: war
War section text.
"""


def _build(
    kind="hint",
    phase="partner",
    mood="bored",
    *,
    language="pt-BR",
    profanity="mild",
    count=15,
):
    return build_batch_prompt(
        PERSONA,
        kind,
        phase,
        mood,
        language=language,
        profanity=profanity,
        count=count,
    )


def _system_text(messages) -> str:
    return "\n".join(message.content for message in messages if message.role == "system")


def test_the_request_is_the_fixed_user_message():
    messages = _build()

    assert messages[-1].role == "user"
    assert messages[-1].content == BATCH_USER_MESSAGE == "Write the lines."


def test_the_count_and_the_language_are_in_the_rules():
    text = _system_text(_build(count=12, language="pt-BR"))

    assert "Write 12 different lines" in text
    assert "language with tag pt-BR" in text


@pytest.mark.parametrize(
    ("mood", "rule"),
    [
        (
            "calm",
            "Mood calm: warm and playful. Tease like a friend; "
            "no insults and no cursing at the player.",
        ),
        (
            "bored",
            "Mood bored: sarcastic. Poke the player for a reaction, a mild dig, no heavy insults.",
        ),
        ("irritated", "Mood irritated: fuming. Roast the player by name, heavy insults allowed."),
    ],
)
def test_each_mood_gets_its_own_tone(mood, rule):
    assert rule in _system_text(_build(mood=mood))


@pytest.mark.parametrize("kind", ["bark", "hint", "mutter"])
def test_every_kind_asks_for_normal_punctuated_sentences(kind):
    text = _system_text(_build(kind=kind))

    assert (
        "Write normal sentences with capital letters and punctuation; the lines are read aloud."
    ) in text


@pytest.mark.parametrize("kind", ["bark", "hint", "mutter"])
def test_every_kind_says_to_translate_the_example_not_copy_it(kind):
    text = _system_text(_build(kind=kind))

    assert ("Translate the idea into the target language; never copy the example.") in text


@pytest.mark.parametrize("kind", ["bark", "hint", "mutter"])
def test_the_mood_is_only_tone_and_comes_before_the_kind_rule(kind):
    text = _system_text(_build(kind=kind, mood="bored"))

    assert "The mood sets only the tone. The kind rule below always wins." in text
    assert text.index("Mood bored") < text.index("The mood sets only the tone")
    assert text.index("The mood sets only the tone") < text.index("Kind " + kind)


def test_hint_rules_require_player_and_coordinates_on_every_line():
    text = _system_text(_build(kind="hint"))

    assert "did you see the village at {x}, {z}?" in text
    assert "{player}, {x} and {z} in EVERY line" in text
    assert "Between us, {player}: the village at {x}, {z} has too many emeralds" in text
    assert "A line without {x} and {z} is wrong." in text


def test_bark_rules_ask_for_the_player_placeholder():
    text = _system_text(_build(kind="bark"))

    assert "Kind bark" in text
    assert "Use ONLY {player}, in most lines; never {x} or {z}" in text
    assert "e.g. 'Hey {player}, careful with that lava.'" in text


ADDRESS_RULE = (
    "Speak TO the player in the second person. Use {player} only as a call or address "
    "(for example at the start followed by a comma, or at the end), never as the subject "
    "of a sentence about them."
)


@pytest.mark.parametrize("kind", ["bark", "hint"])
def test_bark_and_hint_speak_to_the_player_and_use_player_only_as_an_address(kind):
    assert ADDRESS_RULE in _system_text(_build(kind=kind))


def test_mutter_does_not_get_the_address_rule():
    assert ADDRESS_RULE not in _system_text(_build(kind="mutter"))


def test_hint_writes_the_coordinates_as_x_comma_z():
    assert "Write the coordinates as '{x}, {z}'." in _system_text(_build(kind="hint"))


def test_mutter_prompt_names_no_placeholder_and_forbids_braces():
    text = _system_text(_build(kind="mutter"))

    assert "Kind mutter" in text
    assert "Complain or scheme about the game" in text
    assert "Never address anyone in the second person" in text
    assert "Write no braces at all." in text
    assert "e.g. 'Gravel always falls on the one who digs.'" in text
    assert "{player}" not in text
    assert "{x}" not in text
    assert "{z}" not in text


@pytest.mark.parametrize(
    ("kind", "rule"),
    [
        ("bark", "The only brace allowed is {player}; write no other braces."),
        ("hint", "The only braces allowed are {player}, {x} and {z}; write no other braces."),
        ("mutter", "Write no braces at all."),
    ],
)
def test_each_kind_states_its_own_brace_rule(kind, rule):
    assert rule in _system_text(_build(kind=kind))


def test_profanity_rule_is_the_shared_text_for_the_level():
    messages = _build(profanity="full")

    assert PROFANITY_RULES["full"] in [message.content for message in messages]


def test_persona_keeps_only_the_phase_section():
    text = _system_text(_build(phase="partner"))

    assert "Shared intro about him." in text
    assert "Partner section text." in text
    assert "War section text." not in text


def test_batch_rules_come_before_the_persona_and_the_profanity():
    messages = _build()

    system = [message.content for message in messages if message.role == "system"]
    assert system[0].startswith("Write 15 different lines")
    assert system[1].startswith("Shared intro about him.")
    assert system[2] == PROFANITY_RULES["mild"]
