import pytest

from machiavillager.domain.text_clean import fix_dashes, speakable, strip_emoji


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("Oi 😊 tudo bem", "Oi tudo bem"),
        ("Ah para você, luneto10 é? 😊", "Ah para você, luneto10 é?"),
        ("Bora! 🔥🔥", "Bora!"),
        ("Tá bom ❤️", "Tá bom"),
        ("Vamos 👍🏽 agora", "Vamos agora"),
        ("1️⃣ item", "1 item"),
    ],
)
def test_emoji_are_removed_and_the_gap_closed(text, expected):
    # Emoji, variation selectors and skin tones go; the words around them stay.
    assert strip_emoji(text) == expected


@pytest.mark.parametrize(
    "text",
    [
        "Família 👨‍👩‍👧 unida",  # ZWJ family sequence is removed in one piece
        "Bandeira 🇧🇷 aqui",  # a flag is two regional indicators
        "Subdivisão 🏴󠁧󠁢󠁳󠁣󠁴󠁿 aqui",  # a tag-sequence flag is removed too
    ],
)
def test_zwj_sequences_and_flags_are_removed(text):
    assert "👨" not in strip_emoji(text)
    assert "🇧" not in strip_emoji(text)
    assert "‍" not in strip_emoji(text)
    assert "\U000e0067" not in strip_emoji(text)
    assert "  " not in strip_emoji(text)


def test_accents_and_punctuation_are_kept():
    text = "São João, você é ótimo; ç, ã, õ: ok?!"

    assert strip_emoji(text) == text


def test_ordinary_line_is_unchanged():
    assert strip_emoji("Valeu, mano!") == "Valeu, mano!"


def test_emoji_only_text_becomes_empty():
    assert strip_emoji("😊🎉") == ""


def test_speakable_drops_lines_that_were_only_emoji():
    assert speakable(["😊", "Valeu, mano! 😊", "🎉🎉", "Oi."]) == ["Valeu, mano!", "Oi."]


def test_speakable_fixes_dashes_and_drops_a_line_that_was_only_a_dash():
    assert speakable(["Oi — tudo bem", "—", "Ok 😊 — valeu"]) == ["Oi, tudo bem", "Ok, valeu"]


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        (
            "Tô largando nada, luneto10, meu chapéu é lendário — só aparece pra quem acredita.",
            "Tô largando nada, luneto10, meu chapéu é lendário, só aparece pra quem acredita.",
        ),
        ("lendário—só", "lendário, só"),
        ("lendário – só", "lendário, só"),
        ("lendário―só", "lendário, só"),
        ("lendário⸺só", "lendário, só"),
        ("— começo", "começo"),
        ("fim —", "fim"),
        ("fim — ", "fim"),
        ("Oi!—tudo", "Oi! tudo"),
        ("Oi! — tudo", "Oi! tudo"),
        ("Oi!—", "Oi!"),
        ("a , b — c", "a, b, c"),
        ("a —, b", "a, b"),
        ("a —— b", "a, b"),
        ("guarda-chuva — ok", "guarda-chuva, ok"),
        ("Vai, {player} — bora.", "Vai, {player}, bora."),
        ("Faltam -1600 — ou 1600-2000", "Faltam -1600, ou 1600-2000"),
    ],
)
def test_dashes_become_commas_or_go(text, expected):
    assert fix_dashes(text) == expected


@pytest.mark.parametrize(
    "text",
    [
        "guarda-chuva e 1600-2000",
        "Faltam -1600 pontos",
        "Valeu, mano!",
        "a , b",
    ],
)
def test_text_without_a_long_dash_is_unchanged(text):
    assert fix_dashes(text) == text
