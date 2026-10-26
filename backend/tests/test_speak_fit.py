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
