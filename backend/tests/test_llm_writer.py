import asyncio
import logging

import pytest
from pydantic_ai.exceptions import ModelHTTPError, ModelTimeoutError
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models import Model
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.settings import ModelSettings

from machiavillager.application.ports import Feeling
from machiavillager.application.prompt_builder import (
    WHERE_REMINDER,
    ConversationLine,
    PromptMessage,
    build_prompt,
)
from machiavillager.domain.npc_state import register_words
from machiavillager.domain.protocol import NpcSnapshot
from machiavillager.domain.protocol.common import MAX_LINE_LENGTH, MAX_SPEAK_LINES
from machiavillager.infrastructure.llm_writer import (
    CANNED_LINES,
    LlmLineWriter,
    Pair,
    _split_messages,
    _to_feeling,
)
from machiavillager.infrastructure.settings import Settings

PLAYER_LINE = '<player_line from="luneto10">hey machi</player_line>'

# The speak tool requires both feeling fields; a plain reply says nothing happened.
REQUIRED_FEELING = {"feeling_reason": "none", "feeling_delta": 0}

PINNED_CHAIN = "groq:qwen/qwen3.8-27b,groq:openai/gpt-oss-120b,nvidia:nvidia/nemotron-3.5-bolt-e4b"


def _speak(*lines: str) -> ModelResponse:
    """A model reply that calls the speak tool with these lines."""

    return ModelResponse(
        parts=[ToolCallPart(tool_name="speak", args={"lines": list(lines), **REQUIRED_FEELING})],
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


def _plain_text() -> FunctionModel:
    """A fake model that answers in plain text, so the speak tool is never called."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart(content="no tool call here")], model_name="fake-text")

    return FunctionModel(reply, model_name="fake-text")


def _chain(*models: Model) -> list[Pair]:
    """Each model labelled by its own name, which is the label the fakes answer under."""

    return [(model.model_name, model) for model in models]


def _writer(calm: list[Pair], irritated: list[Pair] | None = None) -> LlmLineWriter:
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


def test_bad_answer_without_a_speak_call_falls_through_to_the_next_model(caplog):
    writer = _writer([("groq:fake-text", _plain_text()), ("groq:fake-ok", _answers("second"))])

    with caplog.at_level(logging.INFO):
        reply = asyncio.run(writer.write_reply(_prompt(), irritated=False))

    assert reply.lines == ["second"]
    assert reply.model == "groq:fake-ok"
    assert "LLM groq:fake-text failed (UnexpectedModelBehavior, status=None)" in caplog.text


def test_every_model_failing_speaks_a_canned_line(caplog):
    writer = _writer(_chain(_fails_with_429(), _plain_text(), _times_out()))

    with caplog.at_level(logging.WARNING):
        lines = _reply(writer)

    assert len(lines) == 1
    assert lines[0] in CANNED_LINES
    assert "ModelHTTPError" in caplog.text
    assert "UnexpectedModelBehavior" in caplog.text
    assert "ModelTimeoutError" in caplog.text
    # The error texts (provider body, timeout message) never reach the log.
    assert "rate" not in caplog.text
    assert "timed out" not in caplog.text


def test_an_emoji_is_spoken_without_it():
    writer = _writer(_chain(_answers("Ah para você, luneto10 é? 😊")))

    assert _reply(writer) == ["Ah para você, luneto10 é?"]


def test_an_em_dash_is_spoken_as_a_comma():
    writer = _writer(_chain(_answers("meu chapéu é lendário — só aparece pra quem acredita.")))

    assert _reply(writer) == ["meu chapéu é lendário, só aparece pra quem acredita."]


def test_a_line_that_is_only_emoji_is_dropped():
    writer = _writer(_chain(_answers("😊", "Valeu, mano!")))

    assert _reply(writer) == ["Valeu, mano!"]


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

    names = [agent.model.model_name for _, agent in writer._calm_routes]
    assert names == ["nvidia/nemotron-3.5-bolt-e4b"]
    assert "groq:qwen/qwen3.8-27b" in caplog.text


def test_gpt_oss_gets_low_reasoning_effort():
    settings = Settings(groq_api_key="x", _env_file=None)

    writer = LlmLineWriter.from_settings(settings)

    _, gpt_oss_agent = writer._irritated_routes[0]
    assert gpt_oss_agent.model.model_name == "openai/gpt-oss-120b"
    assert gpt_oss_agent.model.settings == {"openai_reasoning_effort": "low"}


def test_no_keys_at_all_speaks_a_canned_line():
    settings = Settings(_env_file=None)

    writer = LlmLineWriter.from_settings(settings)

    assert not writer.has_models
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


def test_max_tokens_setting_reaches_the_request():
    seen: list[ModelSettings | None] = []

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(info.model_settings)
        return _speak("hi")

    model = FunctionModel(reply, model_name="fake-ok")
    writer = LlmLineWriter([("groq:fake-ok", model)], [], timeout_seconds=4.0, max_tokens=123)

    _reply(writer)

    assert seen[0] is not None
    assert seen[0]["max_tokens"] == 123


def _labelled_writer(calm: Model) -> LlmLineWriter:
    # The pair label is the provider:model form the mod shows.
    return _writer([("groq:fake-ok", calm)])


def test_reply_reports_the_model_and_latency_that_answered():
    writer = _labelled_writer(_answers("Hello."))

    reply = asyncio.run(writer.write_reply(_prompt(), irritated=False))

    assert reply.lines == ["Hello."]
    assert reply.model == "groq:fake-ok"
    assert reply.latency_ms is not None
    assert reply.latency_ms >= 0


def test_canned_line_has_no_model_and_no_latency():
    writer = _labelled_writer(_fails_with_429())

    reply = asyncio.run(writer.write_reply(_prompt(), irritated=False))

    assert _is_one_canned_line(reply.lines)
    assert reply.model is None
    assert reply.latency_ms is None


def test_available_models_lists_chain_pairs_that_have_a_key_once():
    writer = LlmLineWriter.from_settings(
        Settings(
            groq_api_key="x",
            llm_chain=PINNED_CHAIN,
            llm_chain_irritated=PINNED_CHAIN,
            _env_file=None,
        )
    )

    assert writer.available_models() == [
        "groq:qwen/qwen3.8-27b",
        "groq:openai/gpt-oss-120b",
    ]


def test_use_model_puts_the_pair_first_and_the_chain_after_it():
    settings = Settings(
        groq_api_key="x",
        nvidia_api_key="y",
        llm_chain=PINNED_CHAIN,
        llm_chain_irritated=PINNED_CHAIN,
        _env_file=None,
    )
    writer = LlmLineWriter.from_settings(settings)

    writer.use_model("groq:openai/gpt-oss-120b")

    names = [agent.model.model_name for _, agent in writer._calm_routes]
    assert names == [
        "openai/gpt-oss-120b",
        "qwen/qwen3.8-27b",
        "openai/gpt-oss-120b",
        "nvidia/nemotron-3.5-bolt-e4b",
    ]


def test_use_model_none_restores_the_chain_from_settings():
    settings = Settings(
        groq_api_key="x",
        nvidia_api_key="y",
        llm_chain=PINNED_CHAIN,
        llm_chain_irritated=PINNED_CHAIN,
        _env_file=None,
    )
    writer = LlmLineWriter.from_settings(settings)
    writer.use_model("groq:openai/gpt-oss-120b")

    writer.use_model(None)

    names = [agent.model.model_name for _, agent in writer._calm_routes]
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


def test_the_reminder_reaches_the_models_final_user_prompt():
    seen: list[str] = []

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        # The last request's last part is the prompt the agent sends for this turn.
        seen.append(messages[-1].parts[-1].content)
        return _speak("hi")

    npc = NpcSnapshot(
        phase="seeds",
        mood="irritated",
        affinity=70,
        independence=63,
        nearby_players=[],
        dimension="minecraft:overworld",
        x=120,
        y=64,
        z=-340,
        if_asked_to_come="refuses",
    )
    line = ConversationLine("luneto10", "you are useless", from_npc=False)
    prompt = build_prompt("Persona.", "luneto10", [line], npc)
    writer = _writer(_chain(FunctionModel(reply, model_name="fake-ok")))

    asyncio.run(writer.write_reply(prompt, irritated=False))

    assert seen[0].endswith(
        "\n(Reminder, not from the player: " + register_words(70) + " " + WHERE_REMINDER + ")"
    )


def _feeling_model(delta: int, reason: str) -> FunctionModel:
    """A fake model that answers with one line and a feeling about the player."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        # The feeling rides along in the speak call, as the two fields the schema requires.
        args = {"lines": ["Hm."], "feeling_delta": delta, "feeling_reason": reason}
        return ModelResponse(parts=[ToolCallPart(tool_name="speak", args=args)], model_name="fake")

    return FunctionModel(reply, model_name="fake-feel")


def _feeling_from(delta: int, reason: str = "threatened me") -> Feeling | None:
    writer = _writer(_chain(_feeling_model(delta, reason)))

    return asyncio.run(writer.write_reply(_prompt(), irritated=False)).feeling


def test_feeling_comes_back_in_the_reply():
    assert _feeling_from(-6) == Feeling(affinity_delta=-6, reason="threatened me")


def test_to_feeling_clamps_a_gain_to_plus_five():
    assert _to_feeling(9, "rescued me") == Feeling(affinity_delta=5, reason="rescued me")


def test_to_feeling_clamps_a_loss_to_minus_ten():
    assert _to_feeling(-15, "threatened me") == Feeling(affinity_delta=-10, reason="threatened me")


@pytest.mark.parametrize("delta", [9, -15])
def test_a_delta_outside_the_scale_is_rejected_like_a_bad_answer(delta):
    writer = _writer(
        [
            ("groq:fake-feel", _feeling_model(delta, "threatened me")),
            ("groq:fake-ok", _answers("second")),
        ]
    )

    assert _reply(writer) == ["second"]


def test_zero_feeling_is_no_feeling():
    assert _feeling_from(0) is None


def test_long_reason_is_trimmed_to_120_characters():
    feeling = _feeling_from(-3, "r" * 200)

    assert feeling is not None
    assert len(feeling.reason) == 120


def test_blank_reason_is_no_feeling():
    assert _feeling_from(-3, "   ") is None


def test_speak_with_delta_zero_and_none_gives_no_feeling():
    writer = _writer(_chain(_answers("Hello.")))

    assert asyncio.run(writer.write_reply(_prompt(), irritated=False)).feeling is None


def test_none_reason_is_no_feeling_even_with_a_delta():
    assert _to_feeling(-3, "none") is None


def _come_model(come: bool) -> FunctionModel:
    """A fake model that answers with one line and says whether he comes."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        args = {"lines": ["Já vou."], "come": come, **REQUIRED_FEELING}
        return ModelResponse(parts=[ToolCallPart(tool_name="speak", args=args)], model_name="fake")

    return FunctionModel(reply, model_name="fake-come")


@pytest.mark.parametrize("come", [True, False])
def test_come_comes_back_in_the_reply(come):
    writer = _writer(_chain(_come_model(come)))

    assert asyncio.run(writer.write_reply(_prompt(), irritated=False)).come is come


def test_the_speak_tool_requires_the_feeling_fields_before_the_lines():
    seen: list[dict] = []

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(info.output_tools[0].parameters_json_schema)
        return _speak("hi")

    _reply(_writer(_chain(FunctionModel(reply, model_name="fake-ok"))))

    schema = seen[0]
    assert {"feeling_reason", "feeling_delta", "lines"} <= set(schema["required"])
    assert "come" not in schema["required"]
    assert list(schema["properties"])[:3] == ["feeling_reason", "feeling_delta", "lines"]
    assert schema["properties"]["feeling_delta"]["minimum"] == -10
    assert schema["properties"]["feeling_delta"]["maximum"] == 5


def test_a_speak_call_missing_feeling_delta_falls_through_to_the_next_model():
    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        args = {"lines": ["Hm."], "feeling_reason": "none"}
        return ModelResponse(parts=[ToolCallPart(tool_name="speak", args=args)], model_name="fake")

    writer = _writer(
        [
            ("groq:fake-missing", FunctionModel(reply, model_name="fake-missing")),
            ("groq:fake-ok", _answers("second")),
        ]
    )

    assert _reply(writer) == ["second"]
