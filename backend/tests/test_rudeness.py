import pytest

from machiavillager.domain.rudeness import kindness_floor, rudeness_floor


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


@pytest.mark.parametrize("text", ["se joga", "toma no cu", "Toma no seu cu!"])
def test_newly_added_severe_phrases_floor_at_minus_four(text):
    # The newer hatred and curse phrases belong to the strongest floor too.
    assert rudeness_floor(text) == -4


@pytest.mark.parametrize(
    "text", ["mentiroso", "Você é um FUDIDO", "cuzao", "desgraçado", "sua mae", "liar"]
)
def test_newly_added_insult_phrases_floor_at_minus_two(text):
    # The newer insults sit on the middle floor, like the rest of the insult list.
    assert rudeness_floor(text) == -2


@pytest.mark.parametrize(
    "text", ["te amo", "Te ADORO!", "I love you", "você é o melhor", "You are the best"]
)
def test_affection_phrases_give_plus_two(text):
    # Warm words are the strongest kind floor.
    assert kindness_floor(text) == 2


@pytest.mark.parametrize(
    "text",
    [
        "desculpa",
        "Desculpe!",
        "me desculpa",
        "me perdoa",
        "perdão",
        "Foi mal",
        "Sorry",
        "my bad",
        "forgive me",
    ],
)
def test_apology_phrases_give_plus_one(text):
    # A sorry is a small lift, even when he is still cold.
    assert kindness_floor(text) == 1


@pytest.mark.parametrize("text", ["te amor", "desculpado", "perdoar", "bom dia", ""])
def test_kindness_needs_whole_words_and_is_zero_otherwise(text):
    # Whole words only, as for rudeness: a longer word that contains a phrase is not kind.
    assert kindness_floor(text) == 0


def test_kindness_takes_the_highest_match():
    # An apology next to warm words scores as the warm words do.
    assert kindness_floor("desculpa, te amo") == 2
