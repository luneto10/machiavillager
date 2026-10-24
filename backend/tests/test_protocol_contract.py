"""Checks the mod-backend contract in both directions: the Pydantic models and the shared schema.

The models in machiavillager.domain.protocol are the source of truth. shared/protocol.schema.json
is generated from them, and the mod's Java suite reads that same file. Every example and every
structural mutation must therefore give the same verdict from parse_message and from the schema.
"""

import copy
import json
from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest
from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError

from machiavillager.domain.protocol import parse_message, protocol_json_schema

# This file sits in backend/tests/, so two parents up is the repo root that holds shared/.
REPO_ROOT = Path(__file__).resolve().parents[2]
SHARED_DIR = REPO_ROOT / "shared"
SCHEMA_PATH = SHARED_DIR / "protocol.schema.json"
VALID_DIR = SHARED_DIR / "examples" / "valid"
INVALID_DIR = SHARED_DIR / "examples" / "invalid"

# A field no message defines. Adding it must be rejected everywhere (extra="forbid").
UNEXPECTED_FIELD = "unexpected_field"

Message = dict[str, Any]
Mutation = Callable[[Message], Message]


def _load_json(path: Path) -> Message:
    return json.loads(path.read_text(encoding="utf-8"))


def _validator() -> Draft202012Validator:
    # Without a FormatChecker, "format": "date-time" is only an annotation and is never checked.
    return Draft202012Validator(_load_json(SCHEMA_PATH), format_checker=FormatChecker())


def _without(message: Message, key: str) -> Message:
    return {name: value for name, value in message.items() if name != key}


def _add_field_to_nested_objects(message: Message) -> Message:
    # Works on a copy so the valid example on disk is never changed.
    mutant = copy.deepcopy(message)
    for value in mutant.values():
        if isinstance(value, dict):
            value[UNEXPECTED_FIELD] = True
    return mutant


# The six structural rules. Each mutant of a valid example must fail the parser and the schema.
MUTATIONS: dict[str, Mutation] = {
    "add_top_level_field": lambda message: {**message, UNEXPECTED_FIELD: True},
    "v_is_2": lambda message: {**message, "v": 2},
    "remove_v": lambda message: _without(message, "v"),
    "remove_type": lambda message: _without(message, "type"),
    "type_not_a_type": lambda message: {**message, "type": "not_a_type"},
    "nested_unexpected_field": _add_field_to_nested_objects,
}


def _mutant_params() -> list[Any]:
    params = []
    for example in sorted(VALID_DIR.glob("*.json")):
        original = _load_json(example)
        for name, mutate in MUTATIONS.items():
            mutant = mutate(original)
            # A mutation can be a no-op, for example a message with no nested object.
            if mutant == original:
                continue
            params.append(pytest.param(mutant, id=f"{example.name}:{name}"))
    return params


def test_committed_schema_is_up_to_date():
    # Same serialization as scripts/export_protocol_schema.py, so the file is byte-for-byte equal.
    expected = json.dumps(protocol_json_schema(), indent=2, sort_keys=False) + "\n"

    assert SCHEMA_PATH.read_text(encoding="utf-8") == expected, (
        "shared/protocol.schema.json is stale. Run: "
        "cd backend; uv run python scripts/export_protocol_schema.py"
    )


def test_schema_is_valid_draft_2020_12():
    Draft202012Validator.check_schema(_load_json(SCHEMA_PATH))


@pytest.mark.parametrize("example", sorted(VALID_DIR.glob("*.json")), ids=lambda p: p.name)
def test_valid_example_passes_parser_and_schema(example: Path):
    raw = example.read_text(encoding="utf-8")

    parse_message(raw)
    assert list(_validator().iter_errors(json.loads(raw))) == []


@pytest.mark.parametrize("example", sorted(INVALID_DIR.glob("*.json")), ids=lambda p: p.name)
def test_invalid_example_fails_parser_and_schema(example: Path):
    raw = example.read_text(encoding="utf-8")

    with pytest.raises(ValidationError):
        parse_message(raw)
    assert list(_validator().iter_errors(json.loads(raw))), f"{example.name} should be rejected"


@pytest.mark.parametrize("mutant", _mutant_params())
def test_structural_mutant_fails_parser_and_schema(mutant: Message):
    raw = json.dumps(mutant)

    with pytest.raises(ValidationError):
        parse_message(raw)
    assert list(_validator().iter_errors(mutant)), "schema accepted a structurally broken message"
