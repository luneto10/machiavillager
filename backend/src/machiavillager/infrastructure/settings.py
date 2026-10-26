from functools import lru_cache
from pathlib import Path
from typing import Literal

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

    # Which provider the LLM calls go to; its base URL comes from LLM_BASE_URLS.
    llm_provider: Literal["groq", "nvidia", "xai"] = "groq"

    # Chosen in ADR 0005: best character, no refusals, ~0.4 s on Groq. Preview model, so it may
    # be retired; the fallback list arrives with the agent (#63).
    llm_model: str = "qwen/qwen3.8-27b"

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
