from functools import lru_cache
from pathlib import Path

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

from machiavillager.application.prompt_builder import ProfanityLevel

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

    # Ordered provider:model pairs, tried left to right. Qwen is the best character (ADR 0005).
    # Its base URL comes from LLM_BASE_URLS; pairs whose provider has no key are skipped.
    llm_chain: str = (
        "groq:qwen/qwen3.8-27b,groq:openai/gpt-oss-120b,nvidia:nvidia/nemotron-3.5-bolt-e4b"
    )

    # Used when he is irritated: gpt-oss-120b curses harder, so it leads. NVIDIA bolt is the slow
    # last fallback and has its own request budget.
    llm_chain_irritated: str = (
        "groq:openai/gpt-oss-120b,groq:qwen/qwen3.8-27b,nvidia:nvidia/nemotron-3.5-bolt-e4b"
    )

    # Median reply is 0.4 s; a model slower than this is skipped for the next one in the chain.
    llm_timeout_seconds: float = 4.0

    # Caps output tokens per reply. 8 full lines plus the tool-call JSON and gpt-oss reasoning can
    # reach ~700, so 800 only cuts runaways.
    llm_max_tokens: int = 800

    # Per-server choice: mild by default (public repo, other servers did not opt in).
    # The owner's server sets full.
    profanity: ProfanityLevel = "mild"

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
