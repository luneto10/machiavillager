import pytest
from generate_invalid_examples import VALID_DIR
from pydantic import ValidationError

from machiavillager.domain.protocol import (
    DeliverLinesAction,
    DeliverLinesArgs,
    LinesNeededData,
    LinesNeededEvent,
    parse_message,
)

LINES_EXAMPLES = (
    ("event_lines_needed.json", LinesNeededEvent),
    ("action_deliver_lines.json", DeliverLinesAction),
)


def _args(lines: list[str]) -> dict:
    # A valid key, so each test changes only the lines it is about.
    return {"kind": "hint", "phase": "seeds", "mood": "bored", "lines": lines}


@pytest.mark.parametrize(("name", "model"), LINES_EXAMPLES)
def test_lines_examples_parse_as_their_message(name: str, model: type):
    # The parser picks the class from the event or action name, so each must land on its own model.
    raw = (VALID_DIR / name).read_text(encoding="utf-8")

    assert isinstance(parse_message(raw), model)


def test_placeholder_other_than_player_x_z_is_rejected():
    # Any other brace would be spoken as is, so the batch is refused before the mod sees it.
    with pytest.raises(ValidationError):
        DeliverLinesArgs(**_args(["Hi {name}."]))


def test_placeholders_player_x_z_are_accepted():
    args = DeliverLinesArgs(**_args(["{player}, {x} {z}."]))

    assert args.lines == ["{player}, {x} {z}."]


def test_empty_lines_list_is_rejected():
    with pytest.raises(ValidationError):
        DeliverLinesArgs(**_args([]))


def test_thirty_one_lines_are_rejected():
    with pytest.raises(ValidationError):
        DeliverLinesArgs(**_args(["Hi."] * 31))


def test_line_of_161_characters_is_rejected():
    with pytest.raises(ValidationError):
        DeliverLinesArgs(**_args(["x" * 161]))


def test_unknown_kind_is_rejected():
    with pytest.raises(ValidationError):
        DeliverLinesArgs(**{**_args(["Hi."]), "kind": "shout"})


def test_lines_needed_with_a_bad_phase_is_rejected():
    with pytest.raises(ValidationError):
        LinesNeededData(kind="hint", phase="nope", mood="calm")
