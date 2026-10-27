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

from machiavillager.application.ports import Feeling, Reply
from machiavillager.application.prompt_builder import PromptMessage
from machiavillager.application.speak_fit import fit_to_speak
from machiavillager.domain.protocol.common import MAX_FEELING_REASON_LENGTH, MAX_MODELS
from machiavillager.domain.text_clean import speakable
from machiavillager.infrastructure.model_params import settings_for_label
from machiavillager.infrastructure.settings import (
    AZURE_PROVIDER,
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


# Feeling is harder to earn than to lose: a gain is capped at +5, a loss goes down to -10.
MIN_FEELING_DELTA: Final = -10
MAX_FEELING_DELTA: Final = 5


class SpeakOutput(BaseModel):
    # No docstring on purpose: PydanticAI appends it to the tool description the model sees.

    lines: list[str] = Field(min_length=0)

    # Two flat fields, not one nested object: NVIDIA rejects the $defs a nested model adds, and
    # gpt-oss wrote the object as a string, which Groq answers with a 400.
    feeling_delta: int | None = Field(
        default=None,
        description=(
            "Set it when the player's last line changes how you feel about them: -1 or -2 for a "
            "friendly jab or a rude line, -4 to -8 for hatred or an order to vanish or die, "
            "-9 or -10 "
            "only for a real threat to kill you in the game, "
            "+2 to +5 for a gift, a rescue, loyalty or real affection, +1 or +2 for an "
            "apology or a kind word after a fight. Leave it null for small talk and requests."
        ),
    )
    feeling_reason: str | None = Field(
        default=None,
        description="With feeling_delta: what the player did, a few words. Never shown.",
    )
    come: bool = Field(
        default=False,
        description=(
            "True only when the player's last line asks you to come to them and your state says "
            "you go. False otherwise."
        ),
    )


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

        # Kept so each route builds its own settings: the reply cap and field depend on the model.
        self._timeout_seconds = timeout_seconds
        self._max_tokens = max_tokens

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
        calm = build_chain(settings, settings.llm_chain, skipped, available)
        irritated = build_chain(settings, settings.llm_chain_irritated, skipped, available)

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
        provider or a provider with no key or base URL; the message never holds the key.
        """

        # Swapping the route lists is the whole switch: a reply already running keeps the routes
        # it started with, and the next reply picks up the new ones.
        if pair is None:
            self._calm_routes = _build_routes(self._calm_pairs)
            self._irritated_routes = _build_routes(self._irritated_pairs)
            logger.info("LLM model override cleared")
            return

        provider, model_id = parse_model_pair(pair)
        settings = self._settings
        key = settings.llm_key(provider) if settings is not None else None
        if settings is None or key is None:
            raise ValueError(f"no key set for provider {provider!r}")

        # Azure has no fixed host, so a missing base URL is refused the same way as a missing key.
        base_url = settings.llm_base_url(provider)
        if base_url is None:
            raise ValueError(f"no base URL set for provider {provider!r}")

        # The override goes first in both chains; the chains from settings stay behind it.
        override: Pair = (pair, build_model(model_id, base_url, key))
        self._calm_routes = _build_routes([override, *self._calm_pairs])
        self._irritated_routes = _build_routes([override, *self._irritated_pairs])
        logger.info("LLM model override set to %s", pair)

    def _settings_for(self, label: str) -> ModelSettings:
        """The request settings for one route. The reply cap bounds cost and latency."""

        return settings_for_label(
            label,
            max_tokens=self._max_tokens,
            timeout_seconds=self._timeout_seconds,
        )

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
                    model_settings=self._settings_for(label),
                )
            except (ModelAPIError, UnexpectedModelBehavior) as error:
                # A bad answer from one model does not end the chain; the next model gets its turn.
                # Azure content filters answer 400 (ModelHTTPError): the next model takes over.
                errors.append(error)
                # Type name only: the error text can hold player words or provider details.
                # The HTTP status (429 rate limit, 404 no access) says why; it holds no player text.
                status = getattr(error, "status_code", None)
                logger.info(
                    "LLM %s failed (%s, status=%s), trying the next",
                    label,
                    type(error).__name__,
                    status,
                )
                continue

            elapsed_ms = (time.perf_counter() - started) * 1000

            # Emoji are read aloud as nonsense, so they go first; then the lines are fitted.
            lines = fit_to_speak(speakable(result.output.lines))

            # An empty list is a choice to stay quiet, so it is logged as one instead of replaced.
            outcome = "reply" if lines else "chose silence"
            self._log_answer(outcome, label, result.usage, elapsed_ms)
            feeling = _to_feeling(result.output.feeling_delta, result.output.feeling_reason)
            return Reply(
                lines=lines,
                model=label,
                latency_ms=round(elapsed_ms),
                feeling=feeling,
                come=result.output.come,
            )

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


def _to_feeling(delta: int | None, reason: str | None) -> Feeling | None:
    """Clamp the model's feeling to the writer's range. A zero delta or empty reason means none."""

    if delta is None or reason is None:
        return None

    # Clamped here, before the contract sees it, so a model cannot push past the cap.
    delta = min(max(delta, MIN_FEELING_DELTA), MAX_FEELING_DELTA)

    # Trimmed here too: a long reason is cut, not refused, because the lines still matter.
    reason = reason.strip()[:MAX_FEELING_REASON_LENGTH]
    if delta == 0 or not reason:
        return None
    return Feeling(affinity_delta=delta, reason=reason)


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


def build_chain(
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
        base_url = settings.llm_base_url(provider)
        if key is None or base_url is None:
            skipped.add(pair)

            # Azure's URL is per resource, so a missing one is named here; the key is never shown.
            if provider == AZURE_PROVIDER and base_url is None:
                logger.info("LLM pair %s skipped, MACHIAVILLAGER_AZURE_BASE_URL is not set", pair)
            continue
        pairs.append((pair, build_model(model_id, base_url, key)))

        # Listed once even when both chains name it, for the dev model suggestions.
        if pair not in available:
            available.append(pair)
    return pairs


def build_model(model_id: str, base_url: str, key: str) -> OpenAIChatModel:
    """One OpenAI-compatible chat model pointed at its provider's base URL."""

    client = OpenAIProvider(base_url=base_url, api_key=key)

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
