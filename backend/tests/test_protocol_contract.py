"""Checks the mod-backend contract: the Pydantic models, the generated shared files and examples.

The models in machiavillager.domain.protocol are the source of truth. The files in shared/ are
generated from them: the JSON Schema, PROTOCOL.md and the invalid cases in invalid.jsonl. The mod's
Java suite reads the same files, so every valid example must pass parse_message and the schema, and
every invalid case must fail both.
"""

import json
from pathlib import Path
from typing import Any

import pytest
from export_protocol_schema import PROTOCOL_MD_PATH, SCHEMA_PATH, render_protocol_md, render_schema
from generate_invalid_examples import (
    INVALID_JSONL,
    VALID_DIR,
    generate_invalid_examples,
    load_invalid_cases,
    render_invalid_jsonl,
)
from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError

from machiavillager.domain.protocol import parse_message

VALID_EXAMPLES: list[Path] = sorted(VALID_DIR.glob("*.json"))
# A missing file loads as no cases, so the existence test below reports it instead of a crash.
INVALID_CASES: list[tuple[str, dict[str, Any]]] = (
    load_invalid_cases(INVALID_JSONL) if INVALID_JSONL.exists() else []
)
CASE_IDS: list[str] = [name for name, _ in INVALID_CASES]
REGENERATE_HINT = "Run: cd backend; uv run python scripts/export_protocol_schema.py"


def _load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _validator() -> Draft202012Validator:
    # Without a FormatChecker, "format": "date-time" is only an annotation and is never checked.
    return Draft202012Validator(_load(SCHEMA_PATH), format_checker=FormatChecker())


def test_committed_schema_is_up_to_date():
    assert SCHEMA_PATH.read_text(encoding="utf-8") == render_schema(), (
        f"shared/protocol.schema.json is stale. {REGENERATE_HINT}"
    )


def test_committed_protocol_md_is_up_to_date():
    assert PROTOCOL_MD_PATH.read_text(encoding="utf-8") == render_protocol_md(), (
        f"shared/PROTOCOL.md is stale. {REGENERATE_HINT}"
    )


def test_committed_invalid_cases_are_up_to_date():
    # Compares the whole file, so an added, removed or changed case all fail the same way.
    expected = render_invalid_jsonl(generate_invalid_examples(VALID_DIR).cases)
    current = INVALID_JSONL.read_text(encoding="utf-8") if INVALID_JSONL.exists() else ""

    assert current == expected, (
        f"shared/examples/invalid.jsonl is stale or missing. {REGENERATE_HINT}"
    )


def test_schema_is_valid_draft_2020_12():
    Draft202012Validator.check_schema(_load(SCHEMA_PATH))


def test_examples_exist():
    # An empty folder would make the parametrized tests below pass without checking anything.
    assert VALID_EXAMPLES, "no valid examples found"
    assert INVALID_CASES, "no invalid cases found in shared/examples/invalid.jsonl"


def test_invalid_case_names_are_unique():
    # A duplicate name would make two cases share one test id, and one would hide the other.
    assert len(CASE_IDS) == len(set(CASE_IDS)), "duplicate case names in invalid.jsonl"


@pytest.mark.parametrize("example", VALID_EXAMPLES, ids=lambda path: path.name)
def test_valid_example_passes_parser_and_schema(example: Path):
    raw = example.read_text(encoding="utf-8")

    parse_message(raw)
    assert list(_validator().iter_errors(json.loads(raw))) == []


@pytest.mark.parametrize(("name", "message"), INVALID_CASES, ids=CASE_IDS)
def test_invalid_case_fails_parser_and_schema(name: str, message: dict[str, Any]):
    # The parser takes text, so the case is serialized the way the backend would receive it.
    raw = json.dumps(message)

    with pytest.raises(ValidationError):
        parse_message(raw)
    assert list(_validator().iter_errors(message)), f"{name} should be rejected"
