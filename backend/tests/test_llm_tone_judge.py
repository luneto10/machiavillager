import asyncio
import logging

from pydantic_ai.exceptions import ModelHTTPError
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from machiavillager.api.app import create_app
from machiavillager.infrastructure.llm_tone_judge import LlmToneJudge
from machiavillager.infrastructure.llm_writer import Pair
from machiavillager.infrastructure.settings import Settings

JUDGE_CHAIN = "groq:fake-ok,nvidia:nvidia/nemotron-3.5-bolt-e4b"


def _judged(tone: str) -> FunctionModel:
    """A fake model that answers with the classify_tone tool and this tone."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        return ModelResponse(
            parts=[ToolCallPart(tool_name="classify_tone", args={"tone": tone})],
            model_name="fake-ok",
        )

    return FunctionModel(reply, model_name="fake-ok")


def _fails_with_429() -> FunctionModel:
    """A fake model whose provider answers HTTP 429, like a rate limit."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        raise ModelHTTPError(status_code=429, model_name="fake-429", body={"error": "rate"})

    return FunctionModel(reply, model_name="fake-429")


def _plain_text() -> FunctionModel:
    """A fake model that answers in plain text, so the classify_tone tool is never called."""

    def reply(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart(content="insult")], model_name="fake-text")

    return FunctionModel(reply, model_name="fake-text")


def _judge(*pairs: Pair) -> LlmToneJudge:
    return LlmToneJudge(list(pairs), timeout_seconds=3.0, max_tokens=60)


def _judge_text(judge: LlmToneJudge, text: str = "vai se foder") -> str | None:
    return asyncio.run(judge.judge(text))


def test_a_good_answer_returns_its_tone():
    judge = _judge(("groq:fake-ok", _judged("insult")))

    assert _judge_text(judge) == "insult"


def test_a_tone_outside_the_list_is_refused_and_falls_through():
    judge = _judge(
        ("groq:fake-ok", _judged("furious")),
        ("groq:fake-second", _judged("apology")),
    )

    assert _judge_text(judge) == "apology"


def test_a_bad_first_model_falls_through_to_the_next(caplog):
    judge = _judge(
        ("groq:fake-429", _fails_with_429()),
        ("groq:fake-ok", _judged("kind")),
    )

    with caplog.at_level(logging.INFO):
        tone = _judge_text(judge)

    assert tone == "kind"
    assert "LLM judge groq:fake-429 failed (ModelHTTPError, status=429), trying the next" in (
        caplog.text
    )
    assert "tone judged: kind by groq:fake-ok" in caplog.text


def test_a_model_without_a_tool_call_falls_through_to_the_next():
    judge = _judge(
        ("groq:fake-text", _plain_text()),
        ("groq:fake-ok", _judged("affection")),
    )

    assert _judge_text(judge) == "affection"


def test_every_model_failing_returns_none_and_logs_once(caplog):
    judge = _judge(
        ("groq:fake-429", _fails_with_429()),
        ("groq:fake-text", _plain_text()),
    )

    with caplog.at_level(logging.WARNING):
        tone = _judge_text(judge)

    assert tone is None
    assert caplog.text.count("LLM judge: every model failed") == 1


def test_the_judged_line_text_never_reaches_the_log(caplog):
    judge = _judge(("groq:fake-ok", _judged("neutral")))

    with caplog.at_level(logging.INFO):
        _judge_text(judge, text="segredo do jogador")

    assert "segredo do jogador" not in caplog.text


def test_provider_without_a_key_is_skipped_by_from_settings(caplog):
    settings = Settings(
        groq_api_key=None,
        nvidia_api_key="x",
        llm_chain_judge=JUDGE_CHAIN,
        _env_file=None,
    )

    with caplog.at_level(logging.INFO):
        judge = LlmToneJudge.from_settings(settings)

    names = [agent.model.model_name for _, agent in judge._routes]
    assert names == ["nvidia/nemotron-3.5-bolt-e4b"]
    assert "LLM judge pairs skipped, no key: groq:fake-ok" in caplog.text


def test_no_keys_at_all_means_no_judge_model():
    judge = LlmToneJudge.from_settings(Settings(_env_file=None))

    assert not judge.has_models
    assert _judge_text(judge) is None


def test_judge_settings_have_the_briefed_defaults():
    settings = Settings(_env_file=None)

    assert settings.llm_judge_timeout_seconds == 3.0
    assert settings.llm_judge_max_tokens == 60


def test_the_default_judge_chain_is_empty_so_each_message_is_one_call():
    settings = Settings(_env_file=None)

    assert settings.llm_chain_judge == ""
    assert not LlmToneJudge.from_settings(settings).has_models


def test_default_app_wiring_gives_the_chat_handler_no_judge():
    # _env_file=None keeps a developer's real .env out of the test. The fake key only builds models.
    settings = Settings(_env_file=None, groq_api_key="fake-key-for-tests")

    app = create_app(settings)

    assert app.state.handle_player_chat is not None
    assert app.state.handle_player_chat._tone_judge is None
