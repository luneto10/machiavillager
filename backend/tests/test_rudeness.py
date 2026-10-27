import pytest

from machiavillager.domain.rudeness import rudeness_floor


@pytest.mark.parametrize(
    "text",
    [
        "te odeio",
        "Eu TE ODEIO!",
        "odeio voce",
        "se mata",
        "vai morrer",
        "morre",
        "vai se foder",
        "vai tomar no cu",
        "fdp",
        "filho da puta",
        "I hate you",
        "kill yourself",
        "go die",
        "fuck you",
    ],
)
def test_severe_phrases_floor_at_minus_four(text):
    # Hatred and death wishes are the strongest floor.
    assert rudeness_floor(text) == -4


@pytest.mark.parametrize(
    "text",
    [
        "cala a boca",
        "Cala boca!",
        "idiota",
        "burro",
        "Você é um LIXO",
        "voce eh um lixo",
        "inútil",
        "imbecil",
        "otário",
        "babaca",
        "estúpido",
        "seu merda",
        "Você é uma merda",
        "voce eh uma merda",
        "shut up",
        "STUPID",
        "useless",
        "idiot",
        "loser",
    ],
)
def test_insult_phrases_floor_at_minus_two(text):
    # Plain insults hit the middle floor, whatever the accents or case.
    assert rudeness_floor(text) == -2


@pytest.mark.parametrize("text", ["chato", "Boring", "feio!", "você é feio"])
def test_mild_phrases_floor_at_minus_one(text):
    # A jab is a small floor, not an insult.
    assert rudeness_floor(text) == -1


@pytest.mark.parametrize("text", ["lixeira", "burrice", "idiotice", "chatice", "fdpzinho"])
def test_words_containing_a_listed_word_do_not_match(text):
    # Whole words only: a listed word inside a longer word is not rude.
    assert rudeness_floor(text) == 0


@pytest.mark.parametrize("text", ["amo voce", "bom dia", "thanks, friend", ""])
def test_neutral_or_empty_text_is_zero(text):
    # Nothing rude found means no floor at all.
    assert rudeness_floor(text) == 0


def test_mixed_text_takes_the_most_negative_match():
    # A mild word next to an insult, and an insult next to hatred, each give the stronger floor.
    assert rudeness_floor("chato e feio, mas idiota") == -2
    assert rudeness_floor("idiota, te odeio") == -4
