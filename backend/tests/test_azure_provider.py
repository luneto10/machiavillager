import logging

import pytest
from pydantic import ValidationError

from machiavillager.infrastructure.llm_writer import build_chain
from machiavillager.infrastructure.settings import Settings, parse_model_pair

AZURE_URL = "https://my-resource.openai.azure.com/openai/v1"
FAKE_KEY = "sekret-key-123"


def test_azure_base_url_is_stored_with_exactly_one_trailing_slash():
    # Arrange / Act: a doubled trailing slash must collapse to one
    settings = Settings(azure_base_url=AZURE_URL + "//", _env_file=None)

    # Assert
    assert settings.azure_base_url == AZURE_URL + "/"


def test_azure_base_url_without_openai_v1_is_rejected():
    # Arrange / Act / Assert: the v1 path is what the OpenAI client expects
    with pytest.raises(ValidationError):
        Settings(azure_base_url="https://my-resource.openai.azure.com/v1", _env_file=None)


def test_azure_base_url_over_plain_http_is_rejected():
    # Arrange / Act / Assert: the key must not travel unencrypted
    with pytest.raises(ValidationError):
        Settings(azure_base_url="http://my-resource.openai.azure.com/openai/v1", _env_file=None)


def test_azure_base_url_error_names_no_key():
    # Arrange: a bad URL next to a key; the error must not echo the key
    with pytest.raises(ValidationError) as error:
        Settings(
            azure_api_key=FAKE_KEY,
            azure_base_url="http://my-resource.openai.azure.com/openai/v1",
            _env_file=None,
        )

    # Assert
    assert FAKE_KEY not in str(error.value)


def test_empty_azure_base_url_counts_as_unset():
    # Arrange / Act / Assert: a blank line in .env does not enable Azure
    assert Settings(azure_base_url="", _env_file=None).azure_base_url is None


def test_parse_model_pair_accepts_azure_with_a_dotted_deployment_name():
    # Arrange / Act
    provider, model = parse_model_pair("azure:gpt-4.1-mini")

    # Assert
    assert (provider, model) == ("azure", "gpt-4.1-mini")


def test_parse_model_pair_still_rejects_an_unknown_provider():
    # Arrange / Act / Assert
    with pytest.raises(ValueError, match="unknown provider"):
        parse_model_pair("mistral:mistral-small")


def test_azure_pair_uses_the_azure_base_url_and_key():
    # Arrange
    settings = Settings(azure_api_key=FAKE_KEY, azure_base_url=AZURE_URL, _env_file=None)

    # Act
    skipped: set[str] = set()
    pairs = build_chain(settings, "azure:my-deploy", skipped, [])

    # Assert: the client is inspected, the key is only compared, never printed
    label, model = pairs[0]
    assert label == "azure:my-deploy"
    assert model.model_name == "my-deploy"
    assert str(model.client.base_url) == AZURE_URL + "/"
    assert model.client.api_key == FAKE_KEY
    assert skipped == set()


def test_azure_pair_without_base_url_is_skipped_and_the_variable_named(caplog):
    # Arrange: a key but no endpoint
    settings = Settings(azure_api_key=FAKE_KEY, _env_file=None)

    # Act
    skipped: set[str] = set()
    with caplog.at_level(logging.INFO):
        pairs = build_chain(settings, "azure:my-deploy", skipped, [])

    # Assert: skipped like a missing key, the variable name is logged, the key is not
    assert pairs == []
    assert skipped == {"azure:my-deploy"}
    assert "MACHIAVILLAGER_AZURE_BASE_URL" in caplog.text
    assert FAKE_KEY not in caplog.text


def test_azure_pair_without_key_is_skipped_even_with_a_base_url():
    # Arrange: an endpoint but no key
    settings = Settings(azure_base_url=AZURE_URL, _env_file=None)

    # Act
    skipped: set[str] = set()
    pairs = build_chain(settings, "azure:my-deploy", skipped, [])

    # Assert
    assert pairs == []
    assert skipped == {"azure:my-deploy"}


def test_llm_base_url_keeps_the_fixed_urls_of_the_public_providers():
    # Arrange: Azure is set, so only the other providers' URLs are checked
    settings = Settings(azure_base_url=AZURE_URL, _env_file=None)

    # Act / Assert
    assert settings.llm_base_url("groq") == "https://api.groq.com/openai/v1"
    assert settings.llm_base_url("nvidia") == "https://integrate.api.nvidia.com/v1"
    assert settings.llm_base_url("xai") == "https://api.x.ai/v1"
    assert settings.llm_base_url("mistral") is None


def test_llm_base_url_for_azure_is_none_until_set():
    # Arrange / Act / Assert
    assert Settings(_env_file=None).llm_base_url("azure") is None


def test_llm_key_reads_the_azure_key():
    # Arrange / Act / Assert
    assert Settings(azure_api_key=FAKE_KEY, _env_file=None).llm_key("azure") == FAKE_KEY
