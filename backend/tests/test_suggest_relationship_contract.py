from uuid import UUID

import pytest
from generate_invalid_examples import VALID_DIR
from pydantic import ValidationError

from machiavillager.domain.protocol import (
    SpeakAction,
    SuggestRelationshipAction,
    SuggestRelationshipArgs,
    parse_message,
)

PLAYER = "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69"
SUGGESTION_EXAMPLES = (
    "action_suggest_relationship_down.json",
    "action_suggest_relationship_up.json",
)


def _args(affinity_delta: int = -6, reason: str = "threatened him for real") -> dict:
    # Strict models want a UUID object in Python; the JSON examples carry the string form.
    return {"player": UUID(PLAYER), "affinity_delta": affinity_delta, "reason": reason}


@pytest.mark.parametrize("name", SUGGESTION_EXAMPLES)
def test_suggestion_examples_parse_as_suggest_relationship_actions(name: str):
    # The parser picks the class from the action name, so these must not fall through to speak.
    raw = (VALID_DIR / name).read_text(encoding="utf-8")

    assert isinstance(parse_message(raw), SuggestRelationshipAction)


@pytest.mark.parametrize("affinity_delta", [-10, -1, 1, 5, 10])
def test_any_delta_from_minus_ten_to_ten_except_zero_is_accepted(affinity_delta: int):
    args = SuggestRelationshipArgs(**_args(affinity_delta=affinity_delta))

    assert args.affinity_delta == affinity_delta


@pytest.mark.parametrize("affinity_delta", [0, 11, -11])
def test_delta_outside_the_range_or_zero_is_rejected(affinity_delta: int):
    with pytest.raises(ValidationError):
        SuggestRelationshipArgs(**_args(affinity_delta=affinity_delta))


@pytest.mark.parametrize("reason", ["", "x" * 121])
def test_reason_must_be_between_one_and_120_characters(reason: str):
    with pytest.raises(ValidationError):
        SuggestRelationshipArgs(**_args(reason=reason))


def test_reason_of_exactly_120_characters_is_accepted():
    assert len(SuggestRelationshipArgs(**_args(reason="x" * 120)).reason) == 120


def test_speak_still_parses_as_speak_next_to_the_new_action():
    raw = (
        '{"v": 1, "type": "action", "request_id": "act_01HZX8K2M9", "action": "speak", '
        '"args": {"lines": ["Hi."]}, "timeout_ms": 30000}'
    )

    assert isinstance(parse_message(raw), SpeakAction)
