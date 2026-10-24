"""Checks the mod-backend contract: the schema itself, then every example against it.

The mod's Java suite reads the same files, so a rule that drifts on one side fails a test.
"""

import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, FormatChecker

# This file sits in backend/tests/, so two parents up is the repo root that holds shared/.
REPO_ROOT = Path(__file__).resolve().parents[2]
SHARED_DIR = REPO_ROOT / "shared"
SCHEMA_PATH = SHARED_DIR / "protocol.schema.json"
VALID_DIR = SHARED_DIR / "examples" / "valid"
INVALID_DIR = SHARED_DIR / "examples" / "invalid"


def _load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def _validator() -> Draft202012Validator:
    # Without a FormatChecker, "format": "date-time" is only an annotation and is never checked.
    return Draft202012Validator(_load_json(SCHEMA_PATH), format_checker=FormatChecker())


def test_schema_is_valid_draft_2020_12():
    Draft202012Validator.check_schema(_load_json(SCHEMA_PATH))


@pytest.mark.parametrize("example", sorted(VALID_DIR.glob("*.json")), ids=lambda p: p.name)
def test_valid_example_passes_schema(example: Path):
    assert list(_validator().iter_errors(_load_json(example))) == []


@pytest.mark.parametrize("example", sorted(INVALID_DIR.glob("*.json")), ids=lambda p: p.name)
def test_invalid_example_fails_schema(example: Path):
    assert list(_validator().iter_errors(_load_json(example))), f"{example.name} should be rejected"
