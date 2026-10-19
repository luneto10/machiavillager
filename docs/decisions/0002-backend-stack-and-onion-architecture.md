# 0002. Backend stack and onion architecture

Date: 2026-10-18 · Status: accepted

## Decision

- Python **3.13**, **uv**, **FastAPI**, **PydanticAI** (from Checkpoint 4), **Pydantic** models.
- Onion layers `domain → application → infrastructure → api`, dependencies point inward only.
- Enforced by `backend/tests/test_architecture.py` (stdlib `ast`), not by import-linter:
  its Rust extension is blocked by Windows Smart App Control on the dev machine.
- Ruff for lint/format, pytest for tests.
- LLM provider reached through an OpenAI-compatible endpoint configured by env vars,
  so NVIDIA Build ↔ OpenRouter is a config change.

## Why onion here

The valuable logic (personality rules, memory provenance, action allowlists) must outlive
provider churn. Keeping it free of FastAPI/SQLAlchemy/PydanticAI imports makes it fast to test
and safe to swap infrastructure under.

## Guardrails against over-engineering

- No port/Protocol until there is a second implementation or a test double that needs it.
- No empty files "for later". A layer gets a module when it gets real code.
- One deployable service. No microservices.
