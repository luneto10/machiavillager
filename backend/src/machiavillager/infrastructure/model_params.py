"""Per-model request settings: the reply-cap field and reasoning mode each deployment accepts."""

from typing import Final

from pydantic_ai.settings import ModelSettings

from machiavillager.infrastructure.settings import AZURE_PROVIDER

# OpenAI reasoning-family names. Azure deployments are named by the user, so the prefix is the rule.
REASONING_FAMILY_PREFIXES: Final = ("gpt-5", "gpt-6", "o1", "o3", "o4")

# No client retries: a 429 or timeout should fall through to the next model at once.
NO_CLIENT_RETRIES: Final[ModelSettings] = {"max_retries": 0}


def uses_completion_tokens(provider: str, model_id: str) -> bool:
    """True for an Azure reasoning-family deployment, which takes max_completion_tokens."""

    # Only Azure deployments can be named after an OpenAI family; other providers keep max_tokens.
    if provider != AZURE_PROVIDER:
        return False

    # Lowercased, so the match does not depend on how the deployment name was typed.
    return model_id.lower().startswith(REASONING_FAMILY_PREFIXES)


def request_settings(
    provider: str,
    model_id: str,
    *,
    max_tokens: int,
    timeout_seconds: float,
    base: ModelSettings | None = None,
) -> ModelSettings:
    """The settings for one model's request: base, plus its timeout and reply cap."""

    settings: ModelSettings = {**(base or {}), "timeout": timeout_seconds}

    # Every other model takes the cap as max_tokens, the field it has always been sent with.
    if not uses_completion_tokens(provider, model_id):
        settings["max_tokens"] = max_tokens
        return settings

    # The reasoning family refuses max_tokens, and refuses function tools unless reasoning is off.
    existing = (base or {}).get("extra_body")
    extra_body = dict(existing) if isinstance(existing, dict) else {}
    extra_body.update(max_completion_tokens=max_tokens, reasoning_effort="none")
    settings["extra_body"] = extra_body
    return settings


def settings_for_label(label: str, *, max_tokens: int, timeout_seconds: float) -> ModelSettings:
    """The request settings for a provider:model label, as the writers' routes name them."""

    # Split on the first colon only, like parse_model_pair; a label without one is never Azure.
    provider, _, model_id = label.partition(":")
    return request_settings(
        provider,
        model_id,
        max_tokens=max_tokens,
        timeout_seconds=timeout_seconds,
        base=NO_CLIENT_RETRIES,
    )
