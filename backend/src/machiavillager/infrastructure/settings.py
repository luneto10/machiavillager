from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

from machiavillager.application.prompt_builder import ProfanityLevel
from machiavillager.domain.protocol.common import MAX_BATCH_LINES

# parents[4] is the repo root, shared with the mod; parents[3] is backend/, kept as a fallback.
# pydantic-settings lets LATER files win, so the root file is listed last to take precedence.
_REPO_ROOT_ENV = Path(__file__).resolve().parents[4] / ".env"
_BACKEND_ENV = Path(__file__).resolve().parents[3] / ".env"


# All three providers are OpenAI-compatible, so a provider is only a URL and a key.
LLM_BASE_URLS: dict[str, str] = {
    "groq": "https://api.groq.com/openai/v1",
    "nvidia": "https://integrate.api.nvidia.com/v1",
    "xai": "https://api.x.ai/v1",
}


def parse_model_pair(text: str) -> tuple[str, str]:
    """Split "provider:model" into its parts, rejecting providers with no base URL."""

    # Split on the first colon only, since model ids such as "openai/gpt-oss-20b" hold slashes.
    provider, separator, model = text.partition(":")
    if not separator or not model:
        raise ValueError(f"expected provider:model, got {text!r}")

    # Unknown providers have no base URL, so reject them before any request is built.
    if provider not in LLM_BASE_URLS:
        raise ValueError(
            f"unknown provider {provider!r}, expected one of {', '.join(LLM_BASE_URLS)}"
        )
    return provider, model


class Settings(BaseSettings):
    """Runtime configuration: MACHIAVILLAGER_* environment variables or the repo-root .env."""

    model_config = SettingsConfigDict(
        env_prefix="MACHIAVILLAGER_",
        env_file=(str(_BACKEND_ENV), str(_REPO_ROOT_ENV)),
        extra="ignore",
    )

    # SecretStr so the token prints as ********** in reprs, logs and tracebacks.
    bridge_token: SecretStr | None = None

    # Dev only: mounts POST /dev/speak. Off by default, so a normal run exposes no test endpoints.
    dev_endpoints: bool = False

    # Dev only: he repeats each player chat line back, prefixed "(echo) ". Off by default.
    dev_echo: bool = False

    # SecretStr so a key never shows in reprs, logs or tracebacks. None means unset.
    groq_api_key: SecretStr | None = None
    nvidia_api_key: SecretStr | None = None
    xai_api_key: SecretStr | None = None

    # Ordered provider:model pairs, tried left to right. gpt-oss-120b leads: cleaner Portuguese than
    # qwen, and cheaper per call. NVIDIA bolt is second: good Portuguese, and its own request budget
    # keeps him talking when Groq is limited. Qwen is last (ADR 0005).
    # Its base URL comes from LLM_BASE_URLS; pairs whose provider has no key are skipped.
    llm_chain: str = (
        "groq:openai/gpt-oss-120b,nvidia:nvidia/nemotron-3.5-bolt-e4b,groq:qwen/qwen3.8-27b"
    )

    # Used when he is irritated. Same order for now; gpt-oss-120b also curses hardest. NVIDIA bolt
    # is the slow last fallback and has its own request budget.
    llm_chain_irritated: str = (
        "groq:openai/gpt-oss-120b,nvidia:nvidia/nemotron-3.5-bolt-e4b,groq:qwen/qwen3.8-27b"
    )

    # Median reply is 0.4 s; a model slower than this is skipped for the next one in the chain.
    llm_timeout_seconds: float = 4.0

    # Caps output tokens per reply. 8 full lines plus the tool-call JSON and gpt-oss reasoning can
    # reach ~700, so 800 only cuts runaways.
    llm_max_tokens: int = 800

    # Per-server choice: mild by default (public repo, other servers did not opt in).
    # The owner's server sets full.
    profanity: ProfanityLevel = "mild"

    # Line batches: the lines he says without a player, written in one call per batch. NVIDIA leads
    # so the chat keeps Groq's per-minute budget for replies (ADR 0005).
    llm_chain_batch: str = "nvidia:nvidia/nemotron-3.5-bolt-e4b,groq:openai/gpt-oss-120b"

    # A batch is long output, so it gets more time than a chat reply.
    llm_batch_timeout_seconds: float = 20.0

    # A batch of 30 short lines fits well inside this; the cap only stops a runaway.
    llm_batch_max_tokens: int = 1500

    # Language tag of the batch lines (BCP 47 style, e.g. "en" or "pt-BR").
    lines_language: str = Field(default="en", pattern=r"^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?$")

    # Lines per batch the model is asked for; the contract caps a batch at 30.
    lines_batch_size: int = Field(default=15, ge=1, le=MAX_BATCH_LINES)

    # Tone judge: a tiny separate call that classifies the tone of the player's newest line.
    # The small replying model may skip the optional feeling, so the judge does not depend on it.
    llm_chain_judge: str = "nvidia:nvidia/nemotron-3.5-bolt-e4b,groq:openai/gpt-oss-120b"

    # The judge must answer fast; a slower judge is ignored so his speech is never held up.
    llm_judge_timeout_seconds: float = Field(default=3.0, ge=0.5)

    # One tone is a few tokens, so the cap only stops a runaway.
    llm_judge_max_tokens: int = 60

    # Batch calls allowed in any hour, so a busy server cannot drain the provider budget.
    lines_batches_per_hour: int = Field(default=6, ge=1)

    # A key that got a batch is not asked again for this long.
    lines_refresh_seconds: int = Field(default=1200, ge=1)

    # A key whose last batch produced nothing is retried after this shorter wait.
    lines_retry_seconds: int = Field(default=120, ge=1)

    def llm_key(self, provider: str) -> str | None:
        """Secret value of that provider's key, or None when unset or empty."""

        # A dict makes an unknown provider return None instead of reading an unrelated field.
        keys = {
            "groq": self.groq_api_key,
            "nvidia": self.nvidia_api_key,
            "xai": self.xai_api_key,
        }
        secret = keys.get(provider)

        # An empty value in .env counts as unset, so a blank line does not enable a provider.
        if secret is None or not secret.get_secret_value():
            return None
        return secret.get_secret_value()


@lru_cache
def get_settings() -> Settings:
    # Read once per process. Tests replace it through app.dependency_overrides, not env vars.
    return Settings()
