"""The persona file ships with the package and is read from disk on every call."""

from machiavillager.infrastructure.persona import PERSONA_PATH, read_persona


def test_persona_file_exists_next_to_the_package():
    assert PERSONA_PATH.is_file()


def test_read_persona_returns_the_character_text():
    assert "Machiavillager" in read_persona()
