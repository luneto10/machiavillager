"""Per-model request settings, and that each writer sends its own route's settings. No network."""

import asyncio
from collections.abc import Callable

import pytest
from pydantic_ai.messages import ModelMessage, ModelResponse, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.settings import ModelSettings

from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.infrastructure.llm_batch_writer import LlmBatchWriter
from machiavillager.infrastructure.llm_writer import LlmLineWriter, Pair
from machiavillager.infrastructure.model_params import (
    NO_CLIENT_RETRIES,
    request_settings,
    uses_completion_tokens,
)

AZURE_GPT6 = "gpt-6-luna-machi"
REASONING_EXTRA = {"max_completion_tokens": 800, "reasoning_effort": "none"}


def _settings(provider: str, model_id: str) -> ModelSettings:
    """The settings the writers build for one model, with the default cap."""

    return request_settings(
        provider,
        model_id,
        max_tokens=800,
        timeout_seconds=4.0,
        base=NO_CLIENT_RETRIES,
    )


@pytest.mark.parametrize("model_id", [AZURE_GPT6, "gpt-5.4-nano", "o3-mini"])
def test_azure_reasoning_family_takes_completion_tokens_and_no_reasoning(model_id: str):
    settings = _settings("azure", model_id)

    # The family refuses max_tokens, so the cap moves into extra_body.
    assert "max_tokens" not in settings
    assert settings["extra_body"] == REASONING_EXTRA
    assert settings["timeout"] == 4.0
    assert settings["max_retries"] == 0


@pytest.mark.parametrize(
    ("provider", "model_id"),
    [
        ("azure", "DeepSeek-V4-Flash"),
        ("azure", "mistral-small-2503"),
        ("groq", "openai/gpt-oss-120b"),
        ("groq", "gpt-5-not-azure"),
        ("nvidia", "nvidia/nemotron-3.5-bolt-e4b"),
    ],
)
def test_other_models_keep_max_tokens_and_no_reasoning_field(provider: str, model_id: str):
    settings = _settings(provider, model_id)

    assert settings["max_tokens"] == 800
    assert "extra_body" not in settings


def test_family_match_is_by_deployment_prefix_in_any_case():
    assert uses_completion_tokens("azure", "GPT-6-Luna") is True
    assert uses_completion_tokens("azure", "gpt-4o") is False


def test_existing_extra_body_is_kept_and_the_base_is_not_mutated():
    base: ModelSettings = {"extra_body": {"user": "machi"}}

    settings = request_settings("azure", AZURE_GPT6, max_tokens=800, timeout_seconds=4.0, base=base)

    assert settings["extra_body"] == {"user": "machi", **REASONING_EXTRA}
    assert base == {"extra_body": {"user": "machi"}}


def _spy(seen: list[ModelSettings | None], tool_name: str, args: dict) -> FunctionModel:
    """A fake model that records the settings of each request and calls one tool."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(info.model_settings)
        return ModelResponse(parts=[ToolCallPart(tool_name=tool_name, args=args)], model_name="spy")

    return FunctionModel(reply, model_name="spy")


def _assert_azure_route(seen: list[ModelSettings | None], cap: int) -> None:
    assert seen[0] is not None
    assert "max_tokens" not in seen[0]
    assert seen[0]["extra_body"] == {"max_completion_tokens": cap, "reasoning_effort": "none"}


def _assert_groq_route(seen: list[ModelSettings | None], cap: int) -> None:
    assert seen[0] is not None
    assert seen[0]["max_tokens"] == cap
    assert "extra_body" not in seen[0]


def _chat_writer(pairs: list[Pair]) -> LlmLineWriter:
    return LlmLineWriter(pairs, pairs, timeout_seconds=4.0, max_tokens=800)


def _batch_writer(pairs: list[Pair]) -> LlmBatchWriter:
    return LlmBatchWriter(
        pairs,
        timeout_seconds=4.0,
        max_tokens=1500,
        language="en",
        profanity="mild",
        batch_size=15,
        read_persona=lambda: "Persona.",
    )


def _run_chat(writer: LlmLineWriter) -> None:
    messages = [PromptMessage("system", "Rules."), PromptMessage("user", "hey machi")]
    asyncio.run(writer.write_reply(messages, irritated=False))


def _run_batch(writer: LlmBatchWriter) -> None:
    asyncio.run(writer.write_batch("bark", "partner", "calm"))


def test_chat_writer_sends_azure_gpt6_its_own_settings():
    seen: list[ModelSettings | None] = []
    writer = _chat_writer([("azure:" + AZURE_GPT6, _spy(seen, "speak", {"lines": ["Hi."]}))])

    _run_chat(writer)

    _assert_azure_route(seen, cap=800)


def test_batch_writer_sends_azure_gpt6_its_own_settings():
    seen: list[ModelSettings | None] = []
    writer = _batch_writer(
        [("azure:" + AZURE_GPT6, _spy(seen, "deliver_lines", {"lines": ["Hi."]}))]
    )

    _run_batch(writer)

    _assert_azure_route(seen, cap=1500)


@pytest.mark.parametrize(
    ("build", "run", "tool", "args", "cap"),
    [
        pytest.param(_chat_writer, _run_chat, "speak", {"lines": ["Hi."]}, 800, id="chat"),
        pytest.param(
            _batch_writer, _run_batch, "deliver_lines", {"lines": ["Hi."]}, 1500, id="batch"
        ),
    ],
)
def test_groq_route_keeps_max_tokens_in_every_writer(
    build: Callable[[list[Pair]], object],
    run: Callable[[object], None],
    tool: str,
    args: dict,
    cap: int,
):
    seen: list[ModelSettings | None] = []
    writer = build([("groq:openai/gpt-oss-120b", _spy(seen, tool, args))])

    run(writer)

    _assert_groq_route(seen, cap)
