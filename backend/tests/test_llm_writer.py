import asyncio
import logging

import pytest
from pydantic_ai.exceptions import ModelHTTPError, ModelTimeoutError
from pydantic_ai.messages import ModelMessage, ModelResponse, ToolCallPart
from pydantic_ai.models import Model
from pydantic_ai.models.fallback import FallbackModel
from pydantic_ai.models.function import AgentInfo, FunctionModel

from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.domain.protocol.common import MAX_LINE_LENGTH, MAX_SPEAK_LINES
from machiavillager.infrastructure.llm_writer import (
    CANNED_LINES,
    LlmLineWriter,
    _split_messages,
)
from machiavillager.infrastructure.settings import Settings

PLAYER_LINE = '<player_line from="luneto10">hey machi</player_line>'


def _speak(*lines: str) -> ModelResponse:
    """A model reply that calls the speak tool with these lines."""

    return ModelResponse(
        parts=[ToolCallPart(tool_name="speak", args={"lines": list(lines)})],
        model_name="fake-ok",
    )


def _answers(*lines: str) -> FunctionModel:
    """A fake model that always answers with these lines."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        return _speak(*lines)

    return FunctionModel(reply, model_name="fake-ok")


def _fails_with_429() -> FunctionModel:
    """A fake model whose provider answers HTTP 429, like a rate limit."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        raise ModelHTTPError(status_code=429, model_name="fake-429", body={"error": "rate"})

    return FunctionModel(reply, model_name="fake-429")


def _times_out() -> FunctionModel:
    """A fake model that never answers within the request budget."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        raise ModelTimeoutError(model_name="fake-slow", message="timed out")

    return FunctionModel(reply, model_name="fake-slow")


def _chain(*models: Model) -> FallbackModel:
    return FallbackModel(*models)


def _writer(calm: Model, irritated: Model | None = None) -> LlmLineWriter:
    return LlmLineWriter(calm, irritated or calm, timeout_seconds=4.0)


def _prompt() -> list[PromptMessage]:
    return [
        PromptMessage("system", "Fixed rules."),
        PromptMessage("system", "Persona."),
        PromptMessage("system", "State."),
        PromptMessage("user", PLAYER_LINE),
    ]


def _reply(writer: LlmLineWriter, *, irritated: bool = False) -> list[str]:
    return asyncio.run(writer.write_reply(_prompt(), irritated=irritated))


def _is_one_canned_line(lines: list[str]) -> bool:
    return len(lines) == 1 and lines[0] in CANNED_LINES


def test_first_model_answers_with_its_lines():
    writer = _writer(_chain(_answers("Hello, villager.")))

    assert _reply(writer) == ["Hello, villager."]


def test_provider_error_falls_through_to_the_next_model():
    writer = _writer(_chain(_fails_with_429(), _answers("second")))

    assert _reply(writer) == ["second"]


def test_timeout_falls_through_to_the_next_model():
    writer = _writer(_chain(_times_out(), _answers("second")))

    assert _reply(writer) == ["second"]


def test_every_model_failing_speaks_a_canned_line(caplog):
    writer = _writer(_chain(_fails_with_429(), _times_out()))

    with caplog.at_level(logging.WARNING):
        lines = _reply(writer)

    assert len(lines) == 1
    assert lines[0] in CANNED_LINES
    assert "ModelHTTPError" in caplog.text
    assert "ModelTimeoutError" in caplog.text


def test_overlong_output_is_fitted_before_it_is_returned():
    too_many = [f"line {number}" for number in range(MAX_SPEAK_LINES + 2)]
    too_long = "w" * (MAX_LINE_LENGTH + 10)
    writer = _writer(_chain(_answers(*too_many, too_long)))

    lines = _reply(writer)

    assert len(lines) == MAX_SPEAK_LINES
    assert all(len(line) <= MAX_LINE_LENGTH for line in lines)


def test_output_empty_after_fitting_speaks_a_canned_line():
    writer = _writer(_chain(_answers("   ", "")))

    assert _is_one_canned_line(_reply(writer))


def test_irritated_uses_the_irritated_chain():
    writer = _writer(_chain(_answers("calm")), _chain(_answers("angry")))

    assert _reply(writer, irritated=True) == ["angry"]
    assert _reply(writer, irritated=False) == ["calm"]


def test_success_is_logged_without_the_reply_text(caplog):
    writer = _writer(_chain(_answers("secret reply words")))

    with caplog.at_level(logging.INFO):
        _reply(writer)

    assert "LLM reply from fake-ok" in caplog.text
    assert "secret reply words" not in caplog.text


def test_pair_without_a_key_is_skipped_by_from_settings(caplog):
    settings = Settings(groq_api_key=None, nvidia_api_key="x", _env_file=None)

    with caplog.at_level(logging.INFO):
        writer = LlmLineWriter.from_settings(settings)

    calm = writer._calm_agent.model
    assert [model.model_name for model in calm.models] == ["nvidia/nemotron-3.5-bolt-e4b"]
    assert "groq:qwen/qwen3.8-27b" in caplog.text


def test_gpt_oss_gets_low_reasoning_effort():
    settings = Settings(groq_api_key="x", _env_file=None)

    writer = LlmLineWriter.from_settings(settings)

    gpt_oss = writer._irritated_agent.model.models[0]
    assert gpt_oss.model_name == "openai/gpt-oss-120b"
    assert gpt_oss.settings == {"openai_reasoning_effort": "low"}


def test_no_keys_at_all_speaks_a_canned_line():
    settings = Settings(_env_file=None)

    writer = LlmLineWriter.from_settings(settings)

    assert _is_one_canned_line(_reply(writer))


def test_system_messages_become_instructions_and_last_player_line_the_prompt():
    messages = [
        PromptMessage("system", "Rules."),
        PromptMessage("system", "Persona."),
        PromptMessage("user", "first"),
        PromptMessage("assistant", "his reply"),
        PromptMessage("user", "second"),
    ]

    instructions, history, prompt = _split_messages(messages)

    assert instructions == "Rules.\n\nPersona."
    assert prompt == "second"
    assert len(history) == 2


def test_prompt_without_a_player_line_is_rejected():
    with pytest.raises(ValueError):
        _split_messages([PromptMessage("system", "Rules.")])
