"""Band edges for the words that stand in for the NPC's affinity and independence numbers."""

import pytest

from machiavillager.domain.npc_state import affinity_words, independence_words, register_words


@pytest.mark.parametrize(
    ("value", "words"),
    [
        (0, "you despise them"),
        (19, "you despise them"),
        (20, "you distrust them"),
        (39, "you distrust them"),
        (40, "you are neutral toward them"),
        (59, "you are neutral toward them"),
        (60, "you like them"),
        (79, "you like them"),
        (80, "you adore them"),
        (100, "you adore them"),
    ],
)
def test_affinity_words_change_at_each_band_edge(value: int, words: str):
    assert affinity_words(value) == words


@pytest.mark.parametrize(
    ("value", "opening"),
    [
        (0, "Be openly rude to them"),
        (19, "Be openly rude to them"),
        (20, "Be firm and curt"),
        (39, "Be firm and curt"),
        (40, "Take teasing as a joke"),
        (59, "Take teasing as a joke"),
        (60, "Take insults as jokes"),
        (79, "Take insults as jokes"),
        (80, "Treat them like family"),
        (100, "Treat them like family"),
    ],
)
def test_register_words_change_at_each_band_edge(value: int, opening: str):
    assert register_words(value).startswith(opening)


def test_despise_register_is_cold_and_accepts_an_apology_a_little():
    # Pinned in full: the rules for hatred, a direct "do you hate me", and an apology.
    assert register_words(0) == (
        "Be openly rude to them: curse and insult them with strong words and no shame, "
        "refuse favors. "
        "Never call them a friend or a partner; if asked whether you hate them, answer coldly "
        "and honestly. If their last line is an apology or a kind word, stay cold but accept it "
        "a little and curse less."
    )


def test_distrust_register_is_curt_and_accepts_an_apology_grudgingly():
    # Pinned in full: firm but not yet a friend, and an apology is taken grudgingly.
    assert register_words(20) == (
        "Be firm and curt. A jab or a light curse is fine; no heavy insults yet. "
        "Do not call them a friend yet. If their last line is an apology, accept it grudgingly."
    )


@pytest.mark.parametrize(
    ("value", "words"),
    [
        (0, "you still feel like their loyal partner"),
        (24, "you still feel like their loyal partner"),
        (25, "you are getting restless"),
        (49, "you are getting restless"),
        (50, "you think for yourself now"),
        (74, "you think for yourself now"),
        (75, "you answer to no one"),
        (100, "you answer to no one"),
    ],
)
def test_independence_words_change_at_each_band_edge(value: int, words: str):
    assert independence_words(value) == words


@pytest.mark.parametrize("value", [-1, 101])
def test_out_of_range_scores_are_refused(value: int):
    # A bad number must fail loudly, not quietly land in a band.
    with pytest.raises(ValueError):
        affinity_words(value)
    with pytest.raises(ValueError):
        independence_words(value)
