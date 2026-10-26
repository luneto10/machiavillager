from machiavillager.application.speak_fit import fit_to_speak
from machiavillager.domain.protocol.common import MAX_LINE_LENGTH, MAX_SPEAK_LINES


def test_strips_surrounding_whitespace():
    assert fit_to_speak(["  hello  "]) == ["hello"]


def test_drops_empty_and_blank_lines():
    assert fit_to_speak(["", "   ", "hi"]) == ["hi"]


def test_returns_empty_list_when_nothing_is_left():
    assert fit_to_speak(["", "  "]) == []


def test_keeps_short_lines_unchanged():
    assert fit_to_speak(["one", "two"]) == ["one", "two"]


def test_splits_long_line_at_last_space_before_the_limit():
    # The space at index 200 is the last one that fits within 256 characters.
    line = "a" * 200 + " " + "b" * 100

    assert fit_to_speak([line]) == ["a" * 200, "b" * 100]


def test_splits_after_the_last_sentence_end_within_the_limit():
    # The full stop at index 199 is the last sentence end that fits, so the first piece keeps it.
    line = "a" * 199 + ". " + "b" * 99

    assert fit_to_speak([line]) == ["a" * 199 + ".", "b" * 99]


def test_ignores_sentence_end_beyond_the_limit_and_uses_the_space():
    # The full stop sits past the limit, so the cut falls back to the space at index 200.
    line = "a" * 200 + " " + "b" * 60 + ". " + "c" * 50

    assert fit_to_speak([line]) == ["a" * 200, "b" * 60 + ". " + "c" * 50]


def test_hard_cuts_long_line_without_a_space():
    line = "x" * (MAX_LINE_LENGTH + 44)

    assert fit_to_speak([line]) == ["x" * MAX_LINE_LENGTH, "x" * 44]


def test_every_piece_respects_the_length_limit():
    line = " ".join(["word"] * 200)

    pieces = fit_to_speak([line])

    assert len(pieces) > 1
    assert all(len(piece) <= MAX_LINE_LENGTH for piece in pieces)


def test_keeps_at_most_eight_lines():
    lines = [f"line {number}" for number in range(MAX_SPEAK_LINES + 3)]

    fitted = fit_to_speak(lines)

    assert fitted == lines[:MAX_SPEAK_LINES]


def test_splitting_counts_toward_the_line_limit():
    # One long line becomes several pieces, and the surplus beyond eight is dropped.
    line = "x" * (MAX_LINE_LENGTH * 10)

    fitted = fit_to_speak([line])

    assert len(fitted) == MAX_SPEAK_LINES


def test_strips_cjk_and_collapses_the_double_space_it_leaves():
    # Open models leak Chinese into Portuguese; the removed character leaves no double space.
    assert fit_to_speak(["em…呃, em melhorias"]) == ["em…, em melhorias"]


def test_drops_a_line_made_only_of_cjk():
    assert fit_to_speak(["你好世界", "hi"]) == ["hi"]


def test_leaves_accented_portuguese_and_emoji_untouched():
    text = "não é ação, café! 🎉"

    assert fit_to_speak([text]) == [text]
