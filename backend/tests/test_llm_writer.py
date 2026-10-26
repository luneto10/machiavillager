import asyncio
import logging

import pytest
from pydantic_ai.exceptions import ModelHTTPError, ModelTimeoutError
from pydantic_ai.messages import ModelMessage, ModelResponse, ToolCallPart
from pydantic_ai.models import Model
from pydantic_ai.models.fallback import FallbackModel
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.settings import ModelSettings

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
    return asyncio.run(writer.write_reply(_prompt(), irritated=irritated)).lines


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


def test_output_empty_after_fitting_is_silence_not_a_canned_line():
    writer = _writer(_chain(_answers("   ", "")))

    assert _reply(writer) == []


def test_empty_speak_call_is_silence_and_logged_without_text(caplog):
    writer = _writer(_chain(_answers()))

    with caplog.at_level(logging.INFO):
        lines = _reply(writer)

    assert lines == []
    assert "LLM chose silence from fake-ok" in caplog.text


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


def test_speak_tool_description_is_only_the_speak_sentence():
    seen: list[str] = []

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(info.output_tools[0].description)
        return _speak("hi")

    writer = _writer(_chain(FunctionModel(reply, model_name="fake-ok")))

    _reply(writer)

    assert seen == ["Say lines out loud in Minecraft chat."]


def test_requests_send_the_default_max_tokens_cap():
    seen: list[ModelSettings | None] = []

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(info.model_settings)
        return _speak("hi")

    writer = _writer(_chain(FunctionModel(reply, model_name="fake-ok")))

    _reply(writer)

    assert seen[0] is not None
    assert seen[0]["max_tokens"] == 800


def test_max_tokens_setting_reaches_the_writer():
    settings = Settings(groq_api_key="x", llm_max_tokens=123, _env_file=None)

    writer = LlmLineWriter.from_settings(settings)

    assert writer._request_settings["max_tokens"] == 123


def _labelled_writer(calm: Model, irritated: Model | None = None) -> LlmLineWriter:
    # The label map turns the fake's model id into the provider:model form the mod shows.
    return LlmLineWriter(
        calm,
        irritated or calm,
        timeout_seconds=4.0,
        labels={"fake-ok": "groq:fake-ok"},
    )


def test_reply_reports_the_model_and_latency_that_answered():
    writer = _labelled_writer(_chain(_answers("Hello.")))

    reply = asyncio.run(writer.write_reply(_prompt(), irritated=False))

    assert reply.lines == ["Hello."]
    assert reply.model == "groq:fake-ok"
    assert reply.latency_ms is not None
    assert reply.latency_ms >= 0


def test_canned_line_has_no_model_and_no_latency():
    writer = _labelled_writer(_chain(_fails_with_429()))

    reply = asyncio.run(writer.write_reply(_prompt(), irritated=False))

    assert _is_one_canned_line(reply.lines)
    assert reply.model is None
    assert reply.latency_ms is None


def test_available_models_lists_chain_pairs_that_have_a_key_once():
    writer = LlmLineWriter.from_settings(Settings(groq_api_key="x", _env_file=None))

    assert writer.available_models() == [
        "groq:qwen/qwen3.8-27b",
        "groq:openai/gpt-oss-120b",
    ]


def test_use_model_puts_the_pair_first_and_the_chain_after_it():
    settings = Settings(groq_api_key="x", nvidia_api_key="y", _env_file=None)
    writer = LlmLineWriter.from_settings(settings)

    writer.use_model("groq:openai/gpt-oss-120b")

    names = [model.model_name for model in writer._calm_agent.model.models]
    assert names == [
        "openai/gpt-oss-120b",
        "qwen/qwen3.8-27b",
        "openai/gpt-oss-120b",
        "nvidia/nemotron-3.5-bolt-e4b",
    ]


def test_use_model_none_restores_the_chain_from_settings():
    settings = Settings(groq_api_key="x", nvidia_api_key="y", _env_file=None)
    writer = LlmLineWriter.from_settings(settings)
    writer.use_model("groq:openai/gpt-oss-120b")

    writer.use_model(None)

    names = [model.model_name for model in writer._calm_agent.model.models]
    assert names == [
        "qwen/qwen3.8-27b",
        "openai/gpt-oss-120b",
        "nvidia/nemotron-3.5-bolt-e4b",
    ]


def test_use_model_logs_set_and_cleared_without_the_key(caplog):
    settings = Settings(groq_api_key="sekret-key-123", _env_file=None)
    writer = LlmLineWriter.from_settings(settings)

    with caplog.at_level(logging.INFO):
        writer.use_model("groq:openai/gpt-oss-120b")
        writer.use_model(None)

    assert "LLM model override set to groq:openai/gpt-oss-120b" in caplog.text
    assert "LLM model override cleared" in caplog.text
    assert "sekret-key-123" not in caplog.text


def test_use_model_refuses_a_provider_without_a_key_and_hides_the_key():
    settings = Settings(groq_api_key="sekret-key-123", _env_file=None)
    writer = LlmLineWriter.from_settings(settings)

    with pytest.raises(ValueError, match="no key set for provider 'nvidia'") as refused:
        writer.use_model("nvidia:nvidia/nemotron-3.5-bolt-e4b")

    assert "sekret-key-123" not in str(refused.value)


def test_use_model_refuses_an_unknown_provider():
    writer = LlmLineWriter.from_settings(Settings(groq_api_key="x", _env_file=None))

    with pytest.raises(ValueError, match="unknown provider"):
        writer.use_model("openai:gpt-4o")
