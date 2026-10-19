---
name: onion-architecture
description: Use when adding or moving backend Python code in backend/src/machiavillager — deciding which layer (domain, application, infrastructure, api) a class belongs in, adding a port/adapter, wiring dependencies, or when tests/test_architecture.py fails.
---

# Onion architecture (backend)

Dependencies point **inward only**:

```
api  ─▶  infrastructure  ─▶  application  ─▶  domain
(outer)                                       (inner)
```

`backend/tests/test_architecture.py` fails if a layer imports an outer layer, or if
`domain`/`application` import a framework (fastapi, starlette, sqlalchemy, pydantic_ai, httpx, uvicorn).

## Where does it go?

Ask in order, stop at the first yes:

1. **Is it a game/NPC concept or rule with no I/O?** (Memory, Goal, Relationship, Personality,
   GameAction, provenance rules, "is this action allowed for this personality")
   → `domain/`. Pydantic models and plain functions. No async I/O, no clocks you can't inject.
2. **Is it a workflow that coordinates domain objects and needs outside services?**
   ("handle player chat", "run one agent turn", "extract memories from conversation")
   → `application/`. Write the use case as a function or small class. Declare what it needs as a
   `typing.Protocol` port in `application/ports.py` **only when** a test double or second adapter needs it.
3. **Does it talk to a real technology?** (Postgres, PydanticAI/NVIDIA, Azure Speech, env settings)
   → `infrastructure/`. Implements a port. Keeps vendor types inside; returns domain objects.
4. **Is it HTTP/WebSocket/dashboard or object wiring?** → `api/`. Routes stay thin: parse, call
   use case, serialize. `api/` is the composition root: it builds adapters and injects them.

## Rules of thumb

- Domain objects never hold a DB session, HTTP client, or agent.
- Use cases receive dependencies as arguments (constructor or function params). No global singletons.
- Translate vendor errors to domain/application errors at the infrastructure boundary.
- A PydanticAI `Agent` is infrastructure. The *tool semantics* (what `follow_player` means, validation)
  are domain/application; the tool decorator glue is infrastructure.
- Don't create a layer file until it has real code. Don't add a Protocol with one implementation
  and no test double.

## Testing per layer

| Layer | Test style |
|---|---|
| domain | plain pytest, no fixtures needed |
| application | pytest with tiny in-memory fakes implementing the ports |
| infrastructure | integration tests (real Postgres via docker, or recorded HTTP) — mark slow |
| api | `fastapi.testclient.TestClient(create_app(...))` with fake adapters injected |

## Checklist before finishing

- [ ] `uv run pytest` passes (includes the layer test)
- [ ] `uv run ruff check . && uv run ruff format --check .`
- [ ] New module sits in the layer the decision list above gives
