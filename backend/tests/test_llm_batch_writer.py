import asyncio
import logging

from pydantic_ai.exceptions import ModelHTTPError
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from machiavillager.infrastructure.llm_batch_writer import LlmBatchWriter
from machiavillager.infrastructure.llm_writer import Pair
from machiavillager.infrastructure.settings import Settings

PERSONA = "Persona."


def _batch(*lines: str) -> ModelResponse:
    """A model reply that calls the deliver_lines tool with these lines."""

    return ModelResponse(
        parts=[ToolCallPart(tool_name="deliver_lines", args={"lines": list(lines)})],
        model_name="fake-ok",
    )


def _answers(*lines: str) -> FunctionModel:
    """A fake model that always delivers these lines."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        return _batch(*lines)

    return FunctionModel(reply, model_name="fake-ok")


def _fails_with_429() -> FunctionModel:
    """A fake model whose provider answers HTTP 429, like a rate limit."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        raise ModelHTTPError(status_code=429, model_name="fake-429", body={"error": "rate"})

    return FunctionModel(reply, model_name="fake-429")


def _plain_text() -> FunctionModel:
    """A fake model that answers in plain text, so the deliver_lines tool is never called."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart(content="no tool call here")], model_name="fake-text")

    return FunctionModel(reply, model_name="fake-text")


def _writer(*pairs: Pair) -> LlmBatchWriter:
    return LlmBatchWriter(
        list(pairs),
        timeout_seconds=20.0,
        max_tokens=1500,
        language="en",
        profanity="mild",
        batch_size=15,
        read_persona=lambda: PERSONA,
    )


def _write(writer: LlmBatchWriter) -> list[str]:
    return asyncio.run(writer.write_batch("bark", "partner", "calm"))


def test_a_good_answer_returns_its_lines():
    writer = _writer(("groq:fake-ok", _answers("Hey, {player}.", "Come here.")))

    assert _write(writer) == ["Hey, {player}.", "Come here."]


def test_emoji_are_removed_from_batch_lines_and_emoji_only_lines_are_dropped():
    writer = _writer(("groq:fake-ok", _answers("Hey, {player}. 😊", "😊🎉", "Come here.")))

    assert _write(writer) == ["Hey, {player}.", "Come here."]


def test_an_em_dash_is_stored_as_a_comma():
    writer = _writer(("groq:fake-ok", _answers("Vai, {player} — só aparece pra quem acredita.")))

    assert _write(writer) == ["Vai, {player}, só aparece pra quem acredita."]


def test_a_failing_first_model_falls_through_to_the_second(caplog):
    writer = _writer(
        ("groq:fake-429", _fails_with_429()),
        ("groq:fake-ok", _answers("second")),
    )

    with caplog.at_level(logging.INFO):
        lines = _write(writer)

    assert lines == ["second"]
    assert "LLM batch groq:fake-429 failed (ModelHTTPError, status=429), trying the next" in (
        caplog.text
    )
    assert "LLM batch from groq:fake-ok in" in caplog.text
    assert "1 lines" in caplog.text


def test_a_model_without_a_tool_call_falls_through_to_the_second():
    writer = _writer(
        ("groq:fake-text", _plain_text()),
        ("groq:fake-ok", _answers("second")),
    )

    assert _write(writer) == ["second"]


def test_every_model_failing_returns_nothing_and_logs_once(caplog):
    writer = _writer(
        ("groq:fake-429", _fails_with_429()),
        ("groq:fake-text", _plain_text()),
    )

    with caplog.at_level(logging.WARNING):
        lines = _write(writer)

    assert lines == []
    assert caplog.text.count("LLM batch: every model failed") == 1


def test_pair_without_a_key_is_skipped_by_from_settings(caplog):
    settings = Settings(groq_api_key="x", _env_file=None)

    with caplog.at_level(logging.INFO):
        writer = LlmBatchWriter.from_settings(settings)

    names = [agent.model.model_name for _, agent in writer._routes]
    assert names == ["openai/gpt-oss-120b"]
    assert "LLM batch pairs skipped, no key: nvidia:nvidia/nemotron-3.5-bolt-e4b" in caplog.text


def test_no_keys_at_all_means_no_batch_model():
    writer = LlmBatchWriter.from_settings(Settings(_env_file=None))

    assert not writer.has_models
    assert _write(writer) == []
