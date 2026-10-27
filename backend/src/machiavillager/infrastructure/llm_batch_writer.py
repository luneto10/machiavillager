"""LLM adapter: writes one batch of lines in one call, through its own provider fallback chain."""

import logging
import time
from collections.abc import Callable
from typing import Final

from pydantic import BaseModel, Field
from pydantic_ai import Agent, ToolOutput
from pydantic_ai.exceptions import ModelAPIError, UnexpectedModelBehavior
from pydantic_ai.models import Model

from machiavillager.application.line_batch_prompt import build_batch_prompt
from machiavillager.application.prompt_builder import ProfanityLevel
from machiavillager.domain.protocol import LineKind
from machiavillager.domain.protocol.common import NpcMood, NpcPhase
from machiavillager.domain.text_clean import speakable
from machiavillager.infrastructure.llm_writer import MODEL_RETRIES, Pair, build_chain
from machiavillager.infrastructure.model_params import settings_for_label
from machiavillager.infrastructure.persona import read_persona
from machiavillager.infrastructure.settings import Settings

logger = logging.getLogger(__name__)

BATCH_TOOL_NAME: Final = "deliver_lines"
BATCH_TOOL_DESCRIPTION: Final = "Deliver a batch of lines he says, for one kind, phase and mood."


class BatchOutput(BaseModel):
    # No docstring on purpose: PydanticAI appends it to the tool description the model sees.

    lines: list[str] = Field(
        min_length=1,
        description=(
            "The batch: one item per line he says out loud. Only the placeholders {player}, {x} "
            "and {z} may use braces; any other brace is refused."
        ),
    )


# A model's agent, built once, with the label it answers under.
BatchRoute = tuple[str, Agent[None, BatchOutput]]


class LlmBatchWriter:
    """Writes one batch with the first model in the batch chain that answers in time."""

    def __init__(
        self,
        pairs: list[Pair],
        *,
        timeout_seconds: float,
        max_tokens: int,
        language: str,
        profanity: ProfanityLevel,
        batch_size: int,
        read_persona: Callable[[], str] = read_persona,
    ) -> None:
        # Built once here, so a batch only pays for its own request.
        self._routes: list[BatchRoute] = [(label, _build_agent(model)) for label, model in pairs]
        self._language = language
        self._profanity = profanity
        self._batch_size = batch_size
        self._read_persona = read_persona

        # Kept so each route builds its own settings, as in chat: the cap depends on the model.
        self._timeout_seconds = timeout_seconds
        self._max_tokens = max_tokens

    @property
    def has_models(self) -> bool:
        """True when at least one batch model has a key, so a batch can be written at all."""

        return bool(self._routes)

    @classmethod
    def from_settings(cls, settings: Settings) -> "LlmBatchWriter":
        """Build the batch chain from settings. Pairs whose provider has no key are skipped."""

        # Only the keys are logged, once, for the pairs that were left out.
        skipped: set[str] = set()
        pairs = build_chain(settings, settings.llm_chain_batch, skipped, [])
        if skipped:
            logger.info("LLM batch pairs skipped, no key: %s", ", ".join(sorted(skipped)))
        return cls(
            pairs,
            timeout_seconds=settings.llm_batch_timeout_seconds,
            max_tokens=settings.llm_batch_max_tokens,
            language=settings.lines_language,
            profanity=settings.profanity,
            batch_size=settings.lines_batch_size,
        )

    async def write_batch(self, kind: LineKind, phase: NpcPhase, mood: NpcMood) -> list[str]:
        """Return the raw lines of one batch. [] when every model fails or none has a key."""

        try:
            return await self._answer(kind, phase, mood)
        except Exception as error:
            # Type name only: the error text can hold provider details.
            logger.warning("LLM batch failed, no lines written: %s", type(error).__name__)
            return []

    async def _answer(self, kind: LineKind, phase: NpcPhase, mood: NpcMood) -> list[str]:
        """Try the batch models in order and return the first batch that answers."""

        # Without a model there is nothing to ask; from_settings already logged the skipped pairs.
        if not self._routes:
            return []

        messages = build_batch_prompt(
            self._read_persona(),
            kind,
            phase,
            mood,
            language=self._language,
            profanity=self._profanity,
            count=self._batch_size,
        )

        # The rules are the system messages; the request is the last message, the user line.
        instructions = "\n\n".join(
            message.content for message in messages if message.role == "system"
        )
        prompt = messages[-1].content

        # Timed from the first attempt, so the latency is the whole wait for the batch.
        started = time.perf_counter()
        for label, agent in self._routes:
            try:
                result = await agent.run(
                    prompt,
                    instructions=instructions,
                    model_settings=settings_for_label(
                        label,
                        max_tokens=self._max_tokens,
                        timeout_seconds=self._timeout_seconds,
                    ),
                )
            except (ModelAPIError, UnexpectedModelBehavior) as error:
                # Type name and HTTP status only: the error text can hold provider details.
                status = getattr(error, "status_code", None)
                logger.info(
                    "LLM batch %s failed (%s, status=%s), trying the next",
                    label,
                    type(error).__name__,
                    status,
                )
                continue

            elapsed_ms = (time.perf_counter() - started) * 1000
            # Emoji are removed here too, so a stored line is never read aloud with one.
            lines = speakable(result.output.lines)

            # The count is logged, never the text: the lines are checked again by the use case.
            logger.info(
                "LLM batch from %s in %d ms, %d lines", label, round(elapsed_ms), len(lines)
            )
            return lines

        logger.warning("LLM batch: every model failed, no lines written")
        return []


def _build_agent(model: Model) -> Agent[None, BatchOutput]:
    """An agent whose only output is the deliver_lines tool."""

    # The tool is the only output, so a plain-text answer is rejected and falls through.
    return Agent(
        model,
        output_type=ToolOutput(
            BatchOutput,
            name=BATCH_TOOL_NAME,
            description=BATCH_TOOL_DESCRIPTION,
        ),
        retries=MODEL_RETRIES,
    )
