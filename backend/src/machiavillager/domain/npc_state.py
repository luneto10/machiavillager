"""Turns the NPC's numbers and labels into words the model reads. Never returns numbers.

The model sees "you distrust them", never "affinity: 35", so it cannot leak the number to a player.
"""

from typing import Final

from machiavillager.domain.protocol.common import (
    MAX_RELATIONSHIP_SCORE,
    MIN_RELATIONSHIP_SCORE,
    NpcMood,
    NpcPhase,
)

# Each band is (highest value it covers, words). Checked in order; together they cover 0..100.
AFFINITY_BANDS: Final[tuple[tuple[int, str], ...]] = (
    (19, "you despise them"),
    (39, "you distrust them"),
    (59, "you are neutral toward them"),
    (79, "you like them"),
    (100, "you adore them"),
)
# How he talks to a player at each affinity band, same edges as AFFINITY_BANDS.
REGISTER_BANDS: Final[tuple[tuple[int, str], ...]] = (
    (
        19,
        "Be openly rude to them: curse and insult them with strong words and no shame, "
        "refuse favors. "
        "Never call them a friend or a partner; if asked whether you hate them, answer coldly "
        "and honestly. If their last line is an apology or a kind word, stay cold but accept it "
        "a little and curse less.",
    ),
    (
        39,
        "Be firm and curt. A jab or a light curse is fine; no heavy insults yet. "
        "Do not call them a friend yet. If their last line is an apology, accept it grudgingly.",
    ),
    (
        59,
        "Take teasing as a joke. If they keep insulting you, say it annoys you and ask them to "
        "stop.",
    ),
    (
        79,
        "Take insults as jokes and tease back lightly. If it keeps going, say it stings and ask "
        "kindly. Never curse at them.",
    ),
    (
        100,
        "Treat them like family: laugh off insults and tease back warmly. If it keeps going, say "
        "it hurts and ask them to stop. Never curse at them.",
    ),
)
INDEPENDENCE_BANDS: Final[tuple[tuple[int, str], ...]] = (
    (24, "you still feel like their loyal partner"),
    (49, "you are getting restless"),
    (74, "you think for yourself now"),
    (100, "you answer to no one"),
)
PHASE_LABELS: Final[dict[NpcPhase, str]] = {
    "partner": "Partner",
    "seeds": "Seeds",
    "proposal": "Proposal",
    "regime": "Regime",
    "war": "War",
}
MOOD_LABELS: Final[dict[NpcMood, str]] = {
    "calm": "calm",
    "bored": "bored",
    "irritated": "irritated",
}


def affinity_words(value: int) -> str:
    """Describe how much he likes one player, in words."""

    # Affinity uses the shared 0..100 scale; anything outside it is a bug upstream, not a band.
    return _band_words(value, AFFINITY_BANDS)


def register_words(value: int) -> str:
    """Tell him how to talk to one player, given his affinity for them."""

    # Same edges as affinity, so the register always matches the word for how he feels.
    return _band_words(value, REGISTER_BANDS)


def independence_words(value: int) -> str:
    """Describe how far he has drifted from his loyal partner role, in words."""

    # Same scale and same lookup as affinity, with its own band wording.
    return _band_words(value, INDEPENDENCE_BANDS)


def phase_words(phase: NpcPhase) -> str:
    """Name the phase of his plan as a short label."""

    # The mapping covers every Literal value; a missing key means the contract grew first.
    return PHASE_LABELS[phase]


def mood_words(mood: NpcMood) -> str:
    """Name his current mood as a short label."""

    # Lowercase on purpose: the state block reads as "Mood: irritated".
    return MOOD_LABELS[mood]


def _band_words(value: int, bands: tuple[tuple[int, str], ...]) -> str:
    """Return the words of the first band whose upper bound covers the value."""

    # Reject out-of-range input before the lookup, so a wrong number never silently gets a band.
    if not MIN_RELATIONSHIP_SCORE <= value <= MAX_RELATIONSHIP_SCORE:
        raise ValueError(f"score must be {MIN_RELATIONSHIP_SCORE}..{MAX_RELATIONSHIP_SCORE}")

    # Bands are ordered by upper bound, so the first match is the right one.
    for upper_bound, words in bands:
        if value <= upper_bound:
            return words

    # Unreachable while the last band ends at MAX_RELATIONSHIP_SCORE; kept so a gap fails loudly.
    raise AssertionError("bands do not cover the score range")
