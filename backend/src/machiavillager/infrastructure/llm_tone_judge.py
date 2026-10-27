"""LLM adapter: classifies the tone of one player line through its own provider fallback chain."""

import logging
import time
from typing import Final

from pydantic import BaseModel
from pydantic_ai import Agent, ToolOutput
from pydantic_ai.exceptions import ModelAPIError, UnexpectedModelBehavior
from pydantic_ai.models import Model

from machiavillager.application.tone_prompt import build_tone_prompt
from machiavillager.domain.tone import Tone
from machiavillager.infrastructure.llm_writer import MODEL_RETRIES, Pair, build_chain
from machiavillager.infrastructure.model_params import settings_for_label
from machiavillager.infrastructure.settings import Settings

logger = logging.getLogger(__name__)

TONE_TOOL_NAME: Final = "classify_tone"
TONE_TOOL_DESCRIPTION: Final = "Name the tone of the player's line: one of the listed tones."


class ToneOutput(BaseModel):
    # No docstring on purpose: PydanticAI appends it to the tool description the model sees.

    tone: Tone


# A model's agent, built once, with the label it answers under.
ToneRoute = tuple[str, Agent[None, ToneOutput]]


class LlmToneJudge:
    """Judges one line's tone with the first model in the judge chain that answers in time."""

    def __init__(
        self,
        pairs: list[Pair],
        *,
        timeout_seconds: float,
        max_tokens: int,
    ) -> None:
        # Built once here, so a judgement only pays for its own request.
        self._routes: list[ToneRoute] = [(label, _build_agent(model)) for label, model in pairs]

        # Kept so each route builds its own settings: the cap depends on the model.
        self._timeout_seconds = timeout_seconds
        self._max_tokens = max_tokens

    @property
    def has_models(self) -> bool:
        """True when at least one judge model has a key, so a tone can be judged at all."""

        return bool(self._routes)

    @classmethod
    def from_settings(cls, settings: Settings) -> "LlmToneJudge":
        """Build the judge chain from settings. Pairs whose provider has no key are skipped."""

        # Only the keys are logged, once, for the pairs that were left out.
        skipped: set[str] = set()
        pairs = build_chain(settings, settings.llm_chain_judge, skipped, [])
        if skipped:
            logger.info("LLM judge pairs skipped, no key: %s", ", ".join(sorted(skipped)))
        return cls(
            pairs,
            timeout_seconds=settings.llm_judge_timeout_seconds,
            max_tokens=settings.llm_judge_max_tokens,
        )

    async def judge(self, text: str) -> Tone | None:
        """Return the tone of the line, or None when every model fails. Never raises."""

        try:
            return await self._answer(text)
        except Exception as error:
            # Type name only: the error text can hold provider details.
            logger.warning("LLM judge failed, no tone: %s", type(error).__name__)
            return None

    async def _answer(self, text: str) -> Tone | None:
        """Try the judge models in order and return the first tone that answers."""

        # Without a model there is nothing to ask; from_settings already logged the skipped pairs.
        if not self._routes:
            return None

        messages = build_tone_prompt(text)

        # The rules are the system message; the player's wrapped line is the prompt.
        instructions = messages[0].content
        prompt = messages[-1].content

        # Timed from the first attempt, so the latency is the whole wait for the judgement.
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
                    "LLM judge %s failed (%s, status=%s), trying the next",
                    label,
                    type(error).__name__,
                    status,
                )
                continue

            elapsed_ms = (time.perf_counter() - started) * 1000

            # Only the tone and the model are logged, never the line.
            logger.info(
                "tone judged: %s by %s in %d ms", result.output.tone, label, round(elapsed_ms)
            )
            return result.output.tone

        logger.warning("LLM judge: every model failed, no tone")
        return None


def _build_agent(model: Model) -> Agent[None, ToneOutput]:
    """An agent whose only output is the classify_tone tool."""

    # The tool is the only output, so a plain-text answer is rejected and falls through.
    return Agent(
        model,
        output_type=ToolOutput(
            ToneOutput,
            name=TONE_TOOL_NAME,
            description=TONE_TOOL_DESCRIPTION,
        ),
        retries=MODEL_RETRIES,
    )
