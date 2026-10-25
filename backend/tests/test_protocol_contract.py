"""Checks the mod-backend contract: the Pydantic models, the generated shared files and examples.

The models in machiavillager.domain.protocol are the source of truth. The files in shared/ are
generated from them: the JSON Schema, PROTOCOL.md and the invalid examples. The mod's Java suite
reads the same files, so every valid example must pass parse_message and the schema, and every
invalid example must fail both.
"""

import json
from pathlib import Path
from typing import Any

import pytest
from export_protocol_schema import PROTOCOL_MD_PATH, SCHEMA_PATH, render_protocol_md, render_schema
from generate_invalid_examples import INVALID_DIR, VALID_DIR, compare_dir, generate_invalid_examples
from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError

from machiavillager.domain.protocol import parse_message

VALID_EXAMPLES: list[Path] = sorted(VALID_DIR.glob("*.json"))
INVALID_EXAMPLES: list[Path] = sorted(INVALID_DIR.glob("*.json"))
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


def test_committed_invalid_examples_are_up_to_date():
    problems = compare_dir(INVALID_DIR, generate_invalid_examples(VALID_DIR).files)

    assert not problems, f"{'; '.join(problems)}. {REGENERATE_HINT}"


def test_schema_is_valid_draft_2020_12():
    Draft202012Validator.check_schema(_load(SCHEMA_PATH))


def test_examples_exist():
    # An empty folder would make the parametrized tests below pass without checking anything.
    assert VALID_EXAMPLES, "no valid examples found"
    assert INVALID_EXAMPLES, "no invalid examples found"


@pytest.mark.parametrize("example", VALID_EXAMPLES, ids=lambda path: path.name)
def test_valid_example_passes_parser_and_schema(example: Path):
    raw = example.read_text(encoding="utf-8")

    parse_message(raw)
    assert list(_validator().iter_errors(json.loads(raw))) == []


@pytest.mark.parametrize("example", INVALID_EXAMPLES, ids=lambda path: path.name)
def test_invalid_example_fails_parser_and_schema(example: Path):
    raw = example.read_text(encoding="utf-8")

    with pytest.raises(ValidationError):
        parse_message(raw)
    assert list(_validator().iter_errors(json.loads(raw))), f"{example.name} should be rejected"
