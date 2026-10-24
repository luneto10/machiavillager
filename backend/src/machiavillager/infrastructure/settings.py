from functools import lru_cache
from pathlib import Path

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

# parents[4] is the repo root, shared with the mod; parents[3] is backend/, kept as a fallback.
# pydantic-settings lets LATER files win, so the root file is listed last to take precedence.
_REPO_ROOT_ENV = Path(__file__).resolve().parents[4] / ".env"
_BACKEND_ENV = Path(__file__).resolve().parents[3] / ".env"


class Settings(BaseSettings):
    """Runtime configuration: MACHIAVILLAGER_* environment variables or the repo-root .env."""

    model_config = SettingsConfigDict(
        env_prefix="MACHIAVILLAGER_",
        env_file=(str(_BACKEND_ENV), str(_REPO_ROOT_ENV)),
        extra="ignore",
    )

    # SecretStr so the token prints as ********** in reprs, logs and tracebacks.
    bridge_token: SecretStr | None = None


@lru_cache
def get_settings() -> Settings:
    # Read once per process. Tests replace it through app.dependency_overrides, not env vars.
    return Settings()
