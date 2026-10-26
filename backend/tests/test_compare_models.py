"""Tests for the speak-validity check in scripts/compare_models.py.

pyproject.toml sets pytest's pythonpath to scripts/, so the import below needs no sys.path edit.
"""

from typing import Any

import pytest
from compare_models import is_valid_speak, parse_model_pair


def _reply(tool_name: str, arguments: str) -> dict[str, Any]:
    """Build a minimal chat completion holding one tool call."""

    function = {"name": tool_name, "arguments": arguments}
    return {"choices": [{"message": {"tool_calls": [{"function": function}]}}]}


def test_valid_speak_returns_its_lines() -> None:
    # Arrange
    reply = _reply("speak", '{"lines": ["Hello, traveller."]}')

    # Act
    lines = is_valid_speak(reply)

    # Assert
    assert lines == ["Hello, traveller."]


def test_wrong_tool_name_is_not_a_valid_speak() -> None:
    # Arrange
    reply = _reply("shout", '{"lines": ["Hello, traveller."]}')

    # Act / Assert
    assert is_valid_speak(reply) is None


def test_parse_model_pair_splits_on_first_colon_only() -> None:
    # Arrange
    text = "groq:openai/gpt-oss-20b"

    # Act
    provider, model = parse_model_pair(text)

    # Assert
    assert (provider, model) == ("groq", "openai/gpt-oss-20b")


def test_parse_model_pair_rejects_unknown_provider() -> None:
    # Arrange / Act / Assert
    with pytest.raises(ValueError):
        parse_model_pair("mistral:mistral-small")


def test_arguments_failing_speak_args_are_not_valid() -> None:
    # Arrange: an empty lines list breaks SpeakArgs min_length=1
    reply = _reply("speak", '{"lines": []}')

    # Act / Assert
    assert is_valid_speak(reply) is None
