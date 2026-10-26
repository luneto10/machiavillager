"""LLM adapter: turns a built prompt into speak lines through a provider fallback chain."""

import logging
import random
import time
from typing import Final

import pydantic_ai
from pydantic import BaseModel, Field
from pydantic_ai import Agent, ToolOutput
from pydantic_ai.exceptions import ModelAPIError, UnexpectedModelBehavior
from pydantic_ai.messages import (
    ModelMessage,
    ModelRequest,
    ModelResponse,
    TextPart,
    UserPromptPart,
)
from pydantic_ai.models import Model
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

# One output retry per model: a bad answer costs one more try, then the next model takes over.
MODEL_RETRIES: Final = 1

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


# A model in a chain, with the provider:model label the mod shows for it.
Pair = tuple[str, Model]
# A model's agent, built once, with the label it answers under.
Route = tuple[str, Agent[None, SpeakOutput]]


class LlmLineWriter:
    """Writes his reply with the first model in the chain that answers in time."""

    def __init__(
        self,
        calm: list[Pair],
        irritated: list[Pair],
        *,
        timeout_seconds: float,
        max_tokens: int = 800,
        settings: Settings | None = None,
        available: list[str] | None = None,
    ) -> None:
        # The pairs from settings are kept so use_model(None) can rebuild the chains.
        self._calm_pairs = list(calm)
        self._irritated_pairs = list(irritated)
        self._calm_routes = _build_routes(self._calm_pairs)
        self._irritated_routes = _build_routes(self._irritated_pairs)

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

    @property
    def has_models(self) -> bool:
        """True when either chain has at least one model, so a reply can be written at all."""

        return bool(self._calm_routes) or bool(self._irritated_routes)

    @classmethod
    def from_settings(cls, settings: Settings) -> "LlmLineWriter":
        """Build both chains from settings. Pairs whose provider has no key are skipped."""

        # One set for both chains, so a pair listed twice is reported once.
        skipped: set[str] = set()
        available: list[str] = []
        calm = _build_chain(settings, settings.llm_chain, skipped, available)
        irritated = _build_chain(settings, settings.llm_chain_irritated, skipped, available)

        # One INFO line names what was left out; keys are never printed.
        if skipped:
            logger.info("LLM pairs skipped, no key: %s", ", ".join(sorted(skipped)))
        return cls(
            calm,
            irritated,
            timeout_seconds=settings.llm_timeout_seconds,
            max_tokens=settings.llm_max_tokens,
            settings=settings,
            available=available,
        )

    def available_models(self) -> list[str]:
        """Dev info: the provider:model pairs of both chains that have a key, at most 16."""

        return self._available[:MAX_MODELS]

    def use_model(self, pair: str | None) -> None:
        """Dev only: answer with this provider:model first, or back to the chains when None.

        The chain from settings stays behind it as the fallback. Raises ValueError for an unknown
        provider or a provider with no key; the message never holds the key.
        """

        # Swapping the route lists is the whole switch: a reply already running keeps the routes
        # it started with, and the next reply picks up the new ones.
        if pair is None:
            self._calm_routes = _build_routes(self._calm_pairs)
            self._irritated_routes = _build_routes(self._irritated_pairs)
            logger.info("LLM model override cleared")
            return

        provider, model_id = parse_model_pair(pair)
        key = self._settings.llm_key(provider) if self._settings is not None else None
        if key is None:
            raise ValueError(f"no key set for provider {provider!r}")

        # The override goes first in both chains; the chains from settings stay behind it.
        override: Pair = (pair, _build_model(provider, model_id, key))
        self._calm_routes = _build_routes([override, *self._calm_pairs])
        self._irritated_routes = _build_routes([override, *self._irritated_pairs])
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
        """Try the chosen chain's models in order and return the first usable answer."""

        routes = self._irritated_routes if irritated else self._calm_routes
        if not routes:
            raise NoUsableModelError()

        # Rules and state become instructions; the last player line becomes the prompt.
        instructions, history, prompt = _split_messages(messages)

        # Timed from the first attempt, so the latency is the whole wait the player sat through.
        started = time.perf_counter()
        errors: list[Exception] = []
        for label, agent in routes:
            try:
                result = await agent.run(
                    prompt,
                    message_history=history,
                    instructions=instructions,
                    model_settings=self._request_settings,
                )
            except (ModelAPIError, UnexpectedModelBehavior) as error:
                # A bad answer from one model does not end the chain; the next model gets its turn.
                errors.append(error)
                # Type name only: the error text can hold player words or provider details.
                logger.info("LLM %s failed (%s), trying the next", label, type(error).__name__)
                continue

            elapsed_ms = (time.perf_counter() - started) * 1000

            # The model's lines may be longer or more numerous than the mod accepts, so fit them.
            lines = fit_to_speak(result.output.lines)

            # An empty list is a choice to stay quiet, so it is logged as one instead of replaced.
            outcome = "reply" if lines else "chose silence"
            self._log_answer(outcome, label, result.usage, elapsed_ms)
            return Reply(lines=lines, model=label, latency_ms=round(elapsed_ms))

        # Every model failed: the collected errors go up so write_reply can name them.
        raise ExceptionGroup("no model in the chain answered", errors)

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


def _build_routes(pairs: list[Pair]) -> list[Route]:
    """One agent per model, built once, each keeping the label it answers under."""

    # Built here, not per reply, so a reply only pays for the request itself.
    return [(label, _build_agent(model)) for label, model in pairs]


def _build_agent(model: Model) -> Agent[None, SpeakOutput]:
    """An agent whose only output is the speak tool."""

    # The speak tool is the only output, so a plain-text reply is rejected by the agent.
    return Agent(
        model,
        output_type=ToolOutput(
            SpeakOutput,
            name=SPEAK_TOOL_NAME,
            description=SPEAK_TOOL_DESCRIPTION,
        ),
        retries=MODEL_RETRIES,
    )


def _build_chain(
    settings: Settings,
    chain_text: str,
    skipped: set[str],
    available: list[str],
) -> list[Pair]:
    """The pairs of a chain that have a key, in order; the rest are reported as skipped."""

    pairs: list[Pair] = []
    for item in chain_text.split(","):
        if not item.strip():
            continue
        provider, model_id = parse_model_pair(item.strip())
        pair = f"{provider}:{model_id}"

        # A provider without a key would fail with 401 on every call, so the pair is left out.
        key = settings.llm_key(provider)
        if key is None:
            skipped.add(pair)
            continue
        pairs.append((pair, _build_model(provider, model_id, key)))

        # Listed once even when both chains name it, for the dev model suggestions.
        if pair not in available:
            available.append(pair)
    return pairs


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
    """Exception type names only, including the members of an exception group."""

    # An ExceptionGroup lists one error per model tried; their names show which failed.
    inner = [type(member).__name__ for member in getattr(error, "exceptions", ())]
    name = type(error).__name__
    return f"{name} ({', '.join(inner)})" if inner else name
