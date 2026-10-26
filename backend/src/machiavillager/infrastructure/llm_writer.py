"""LLM adapter: turns a built prompt into speak lines through a provider fallback chain."""

import logging
import random
import time
from typing import Final

import pydantic_ai
from pydantic import BaseModel, Field
from pydantic_ai import Agent, ToolOutput
from pydantic_ai.exceptions import ModelAPIError
from pydantic_ai.messages import (
    ModelMessage,
    ModelRequest,
    ModelResponse,
    TextPart,
    UserPromptPart,
)
from pydantic_ai.models import Model
from pydantic_ai.models.fallback import FallbackModel
from pydantic_ai.models.openai import OpenAIChatModel, OpenAIChatModelSettings
from pydantic_ai.providers.openai import OpenAIProvider
from pydantic_ai.settings import ModelSettings
from pydantic_ai.usage import RunUsage

from machiavillager.application.ports import Reply
from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.application.speak_fit import fit_to_speak
from machiavillager.domain.protocol.common import MAX_MODELS
from machiavillager.infrastructure.settings import (
    LLM_BASE_URLS,
    Settings,
    parse_model_pair,
)

logger = logging.getLogger(__name__)

# The backend reports through its own logs; PydanticAI's first-run terminal banner is noise there.
pydantic_ai.BANNER_ENABLED = False

SPEAK_TOOL_NAME: Final = "speak"
SPEAK_TOOL_DESCRIPTION: Final = "Say lines out loud in Minecraft chat."

# Spoken when no model answers, so he stays in character instead of going silent.
CANNED_LINES: Final = (
    "Hm. Later.",
    "Not now, I'm counting emeralds.",
    "The villagers need me. Ask again.",
    "Wrong question. Try a better one.",
    "I'm busy plotting. Go away.",
)


class SpeakOutput(BaseModel):
    # No docstring on purpose: PydanticAI appends it to the tool description the model sees.

    lines: list[str] = Field(min_length=0)


class NoUsableModelError(Exception):
    """Every pair in the chain was skipped for lack of a key."""


class LlmLineWriter:
    """Writes his reply with the first model in the chain that answers in time."""

    def __init__(
        self,
        calm: Model | None,
        irritated: Model | None,
        *,
        timeout_seconds: float,
        max_tokens: int = 800,
        labels: dict[str, str] | None = None,
        settings: Settings | None = None,
        available: list[str] | None = None,
    ) -> None:
        self._calm_agent = _build_agent(calm)
        self._irritated_agent = _build_agent(irritated)
        # The chains from settings, kept so use_model(None) can put them back.
        self._calm_default = calm
        self._irritated_default = irritated
        # Keys for building an override model; without settings no provider has a key.
        self._settings = settings
        self._available = list(available or [])

        # No client retries: a 429 or timeout should fall through to the next model at once.
        # max_tokens caps a runaway reply's cost and latency.
        self._request_settings = ModelSettings(
            timeout=timeout_seconds,
            max_retries=0,
            max_tokens=max_tokens,
        )
        self._labels = labels or {}

    @property
    def has_models(self) -> bool:
        """True when either chain has at least one model, so a reply can be written at all."""

        return self._calm_agent is not None or self._irritated_agent is not None

    @classmethod
    def from_settings(cls, settings: Settings) -> "LlmLineWriter":
        """Build both chains from settings. Pairs whose provider has no key are skipped."""

        # One set for both chains, so a pair listed twice is reported once.
        skipped: set[str] = set()
        labels: dict[str, str] = {}
        available: list[str] = []
        calm = _build_chain(settings, settings.llm_chain, skipped, labels, available)
        irritated = _build_chain(settings, settings.llm_chain_irritated, skipped, labels, available)

        # One INFO line names what was left out; keys are never printed.
        if skipped:
            logger.info("LLM pairs skipped, no key: %s", ", ".join(sorted(skipped)))
        return cls(
            calm,
            irritated,
            timeout_seconds=settings.llm_timeout_seconds,
            max_tokens=settings.llm_max_tokens,
            labels=labels,
            settings=settings,
            available=available,
        )

    def available_models(self) -> list[str]:
        """Dev info: the provider:model pairs of both chains that have a key, at most 16."""

        return self._available[:MAX_MODELS]

    def use_model(self, pair: str | None) -> None:
        """Dev only: answer with this provider:model first, or back to the chains when None.

        The chain from settings stays as the fallback. Raises ValueError for an unknown provider
        or a provider with no key; the message never holds the key.
        """

        # Swapping the two agent attributes is the whole switch: a reply already running keeps the
        # agent it started with, and the next reply picks up the new one.
        if pair is None:
            self._calm_agent = _build_agent(self._calm_default)
            self._irritated_agent = _build_agent(self._irritated_default)
            logger.info("LLM model override cleared")
            return

        provider, model_id = parse_model_pair(pair)
        key = self._settings.llm_key(provider) if self._settings is not None else None
        if key is None:
            raise ValueError(f"no key set for provider {provider!r}")

        override = _build_model(provider, model_id, key)
        self._labels[model_id] = pair
        self._calm_agent = _build_agent(_with_override(override, self._calm_default))
        self._irritated_agent = _build_agent(_with_override(override, self._irritated_default))
        logger.info("LLM model override set to %s", pair)

    async def write_reply(self, messages: list[PromptMessage], *, irritated: bool) -> Reply:
        """Return the fitted lines with model and latency. No lines means he chose silence.

        Failures speak a canned line, which has no model.
        """

        try:
            return await self._answer(messages, irritated=irritated)
        except Exception as error:
            # Only type names are logged: the error text can hold player words or provider details.
            logger.warning("LLM reply failed, speaking a canned line: %s", _describe(error))
            return Reply(lines=[random.choice(CANNED_LINES)])

    async def _answer(self, messages: list[PromptMessage], *, irritated: bool) -> Reply:
        """Run the chosen agent once and return the fitted lines."""

        agent = self._irritated_agent if irritated else self._calm_agent
        if agent is None:
            raise NoUsableModelError()

        # Rules and state become instructions; the last player line becomes the prompt.
        instructions, history, prompt = _split_messages(messages)

        # Timed around the run only, so the log shows the wait the player actually had.
        started = time.perf_counter()
        result = await agent.run(
            prompt,
            message_history=history,
            instructions=instructions,
            model_settings=self._request_settings,
        )
        elapsed_ms = (time.perf_counter() - started) * 1000

        # The model's lines may be longer or more numerous than the mod accepts, so they are fitted.
        lines = fit_to_speak(result.output.lines)

        # An empty list is a choice to stay quiet, so it is logged as one instead of replaced.
        outcome = "reply" if lines else "chose silence"
        model = self._provider_model(result.response.model_name)
        self._log_answer(outcome, model or "unknown", result.usage, elapsed_ms)
        return Reply(lines=lines, model=model, latency_ms=round(elapsed_ms))

    def _provider_model(self, model_name: str | None) -> str | None:
        """The provider:model label of the model id that answered, or the id when unlabelled."""

        # The response names the model id only; the label adds the provider when it is known.
        if model_name is None:
            return None
        return self._labels.get(model_name, model_name)

    def _log_answer(self, outcome: str, label: str, usage: RunUsage, elapsed_ms: float) -> None:
        """Log which model answered and what it cost. Never logs the text."""

        logger.info(
            "LLM %s from %s in %.0f ms, tokens in=%s out=%s",
            outcome,
            label,
            elapsed_ms,
            usage.input_tokens,
            usage.output_tokens,
        )


def _build_agent(model: Model | None) -> Agent[None, SpeakOutput] | None:
    """An agent whose only output is the speak tool, or None when the chain has no model."""

    # The speak tool is the only output, so a plain-text reply is rejected by the agent.
    if model is None:
        return None
    return Agent(
        model,
        output_type=ToolOutput(
            SpeakOutput,
            name=SPEAK_TOOL_NAME,
            description=SPEAK_TOOL_DESCRIPTION,
        ),
    )


def _build_chain(
    settings: Settings,
    chain_text: str,
    skipped: set[str],
    labels: dict[str, str],
    available: list[str],
) -> FallbackModel | None:
    """One fallback model over the pairs that have a key, or None when none do."""

    models: list[Model] = []
    for item in chain_text.split(","):
        if not item.strip():
            continue
        provider, model_id = parse_model_pair(item.strip())

        # A provider without a key would fail with 401 on every call, so the pair is left out.
        key = settings.llm_key(provider)
        if key is None:
            skipped.add(f"{provider}:{model_id}")
            continue
        models.append(_build_model(provider, model_id, key))
        labels[model_id] = f"{provider}:{model_id}"

        # Listed once even when both chains name it, for the dev model suggestions.
        pair = f"{provider}:{model_id}"
        if pair not in available:
            available.append(pair)

    if not models:
        return None
    return _fallback(models)


def _fallback(models: list[Model]) -> FallbackModel:
    """Try the models in order, moving on when one fails with an API error."""

    # The default fallback_on is ModelAPIError, which covers HTTP errors such as 429 and timeouts
    # (ModelTimeoutError is a ModelAPIError), so those move on to the next model.
    return FallbackModel(*models, fallback_on=(ModelAPIError,))


def _with_override(override: Model, chain: Model | None) -> Model:
    """The override model first, then the chain from settings as the fallback."""

    # A chain is already a FallbackModel, so its models are taken out and kept in order.
    fallbacks: list[Model] = []
    if isinstance(chain, FallbackModel):
        fallbacks = list(chain.models)
    elif chain is not None:
        fallbacks = [chain]
    return _fallback([override, *fallbacks])


def _build_model(provider: str, model_id: str, key: str) -> OpenAIChatModel:
    """One OpenAI-compatible chat model pointed at its provider."""

    client = OpenAIProvider(base_url=LLM_BASE_URLS[provider], api_key=key)

    # gpt-oss reasons before it answers; low effort keeps the reply inside the time budget.
    model_settings = (
        OpenAIChatModelSettings(openai_reasoning_effort="low")
        if model_id.startswith("openai/gpt-oss")
        else None
    )
    return OpenAIChatModel(model_id, provider=client, settings=model_settings)


def _split_messages(messages: list[PromptMessage]) -> tuple[str, list[ModelMessage], str]:
    """Split the prompt into run instructions, earlier dialogue and the last player line."""

    # System messages are rules, persona and state, so they become the instructions in order.
    instructions = "\n\n".join(message.content for message in messages if message.role == "system")
    dialogue = [message for message in messages if message.role != "system"]

    # The last player line is the prompt; his own lines after it are dropped, a run ends on it.
    last_user = max(
        (index for index, message in enumerate(dialogue) if message.role == "user"),
        default=None,
    )
    if last_user is None:
        raise ValueError("prompt has no player line")

    history = [_to_model_message(message) for message in dialogue[:last_user]]
    return instructions, history, dialogue[last_user].content


def _to_model_message(message: PromptMessage) -> ModelMessage:
    """Map a dialogue message to the request or response type PydanticAI expects."""

    # His own lines are responses, so the model reads them as its past words, not as player input.
    if message.role == "assistant":
        return ModelResponse(parts=[TextPart(content=message.content)])
    return ModelRequest(parts=[UserPromptPart(content=message.content)])


def _describe(error: BaseException) -> str:
    """Exception type names only, including the members of a fallback group."""

    # A FallbackExceptionGroup lists one error per model tried; their names show which failed.
    inner = [type(member).__name__ for member in getattr(error, "exceptions", ())]
    name = type(error).__name__
    return f"{name} ({', '.join(inner)})" if inner else name
