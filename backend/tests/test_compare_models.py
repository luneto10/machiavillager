"""Tests for the speak-validity check in scripts/compare_models.py.

pyproject.toml sets pytest's pythonpath to scripts/, so the import below needs no sys.path edit.
"""

from typing import Any

import pytest
from compare_models import build_body, is_valid_speak, parse_model_pair

from machiavillager.infrastructure.model_params import request_settings


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


def test_azure_gpt6_body_uses_completion_tokens_and_no_reasoning() -> None:
    # Arrange: the same helper the backend uses, for the deployment name the user typed
    params = request_settings("azure", "gpt-6-luna-machi", max_tokens=300, timeout_seconds=60.0)

    # Act
    body = build_body("gpt-6-luna-machi", "oi", params)

    # Assert
    assert "max_tokens" not in body
    assert body["max_completion_tokens"] == 300
    assert body["reasoning_effort"] == "none"


def test_groq_body_keeps_max_tokens() -> None:
    # Arrange
    params = request_settings("groq", "qwen/qwen3.8-27b", max_tokens=300, timeout_seconds=60.0)

    # Act
    body = build_body("qwen/qwen3.8-27b", "oi", params)

    # Assert
    assert body["max_tokens"] == 300
    assert "reasoning_effort" not in body
