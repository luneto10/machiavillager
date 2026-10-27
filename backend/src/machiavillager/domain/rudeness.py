"""A floor for obvious feelings in a player's words, read from the text alone.

rudeness_floor is the least a rude line may score (0, -1, -2 or -4). kindness_floor is the least a
kind line may score (0, +1 or +2). Both are floors for obvious cases, not classifiers: they only
catch the listed phrases, and they only know Portuguese and English. Anything subtler is left to
the model's own feeling.
"""

import re
import unicodedata
from typing import Final

# Hatred, or telling him to die or vanish: the strongest floor.
SEVERE_PHRASES: Final = (
    "te odeio",
    "odeio voce",
    "se mata",
    "se joga",
    "vai morrer",
    "morre",
    "vai se foder",
    "vai tomar no cu",
    "toma no cu",
    "toma no seu cu",
    "fdp",
    "filho da puta",
    "i hate you",
    "kill yourself",
    "go die",
    "fuck you",
)

# Plain insults and orders to be quiet: merda only counts inside these exact phrases.
INSULT_PHRASES: Final = (
    "cala a boca",
    "cala boca",
    "idiota",
    "burro",
    "lixo",
    "inutil",
    "imbecil",
    "otario",
    "babaca",
    "estupido",
    "mentiroso",
    "fudido",
    "cuzao",
    "desgracado",
    "sua mae",
    "seu merda",
    "voce e uma merda",
    "voce eh uma merda",
    "shut up",
    "stupid",
    "useless",
    "idiot",
    "loser",
    "liar",
    "chupa",
    "chupa meu",
    "chupa meus",
    "vai chupar",
    "bosta",
    "arrombado",
    "corno",
    "vsf",
    "vtnc",
    "tnc",
    "pau no cu",
    "otaria",
    "babacao",
    "cretino",
    "retardado",
    "suck my",
    "dumbass",
    "bastard",
    "moron",
)

# Mild rudeness: a jab, not an insult.
MILD_PHRASES: Final = ("chato", "boring", "feio")

# Affection: the strongest kind floor.
AFFECTION_PHRASES: Final = (
    "te amo",
    "te adoro",
    "i love you",
    "voce e o melhor",
    "you are the best",
)

# Apologies: a small kind floor, so a grudging sorry still counts for something.
APOLOGY_PHRASES: Final = (
    "desculpa",
    "desculpe",
    "me desculpa",
    "me perdoa",
    "perdao",
    "foi mal",
    "sorry",
    "my bad",
    "forgive me",
)

# Anything that is not a letter or digit separates words, so punctuation cannot glue them together.
_NON_WORD: Final = re.compile(r"[^a-z0-9]+")


def _normalize(text: str) -> str:
    """Lowercase, accent-free, with single spaces, padded so phrases match on whole words."""

    # NFKD splits an accented letter into the plain letter and a combining mark; the mark goes.
    decomposed = unicodedata.normalize("NFKD", text)
    unaccented = "".join(ch for ch in decomposed if not unicodedata.combining(ch))

    # Padding with spaces lets a phrase match only whole words: "lixo" never matches "lixeira".
    words = _NON_WORD.sub(" ", unaccented.lower()).split()
    return f" {' '.join(words)} "


def _has_any(padded: str, phrases: tuple[str, ...]) -> bool:
    """True when any phrase appears as whole words in the normalized text."""

    # Each phrase is wrapped in spaces too, so it cannot match inside a longer word.
    return any(f" {phrase} " in padded for phrase in phrases)


def rudeness_floor(text: str) -> int:
    """The least affinity change the player's words deserve: 0, -1, -2 or -4. Never positive."""

    padded = _normalize(text)

    # Checked from the strongest to the mildest, so the most negative match wins.
    if _has_any(padded, SEVERE_PHRASES):
        return -4
    if _has_any(padded, INSULT_PHRASES):
        return -2
    if _has_any(padded, MILD_PHRASES):
        return -1
    return 0


def kindness_floor(text: str) -> int:
    """The least affinity change the player's kind words deserve: 0, +1 or +2. Never negative."""

    padded = _normalize(text)

    # Affection is checked first, so a line that is both affectionate and an apology scores +2.
    if _has_any(padded, AFFECTION_PHRASES):
        return 2
    if _has_any(padded, APOLOGY_PHRASES):
        return 1
    return 0
