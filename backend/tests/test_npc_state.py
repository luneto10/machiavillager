"""Band edges for the words that stand in for the NPC's affinity and independence numbers."""

import pytest

from machiavillager.domain.npc_state import affinity_words, independence_words


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
