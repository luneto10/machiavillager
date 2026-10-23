# Machiavillager

A chaotic, scheming AI companion for Minecraft Java Edition. He talks with you by voice,
remembers who betrayed him, and is absolutely going to corner the emerald market.

> Status: **Checkpoint 1** — mod reacts to the game (lifecycle logs, join greeting, dev command).
> Game design: [docs/design/emerald-empire.md](docs/design/emerald-empire.md).

## How it fits together

```
Minecraft 26.3 + Fabric mod (Java 25)
        │  authenticated WebSocket, JSON actions/events
        ▼
FastAPI backend (Python 3.13, PydanticAI)
        ├── LLM: NVIDIA Build (OpenAI-compatible, swappable)
        ├── Voice: Azure Speech STT, TTS TBD (Checkpoint 11)
        ├── Memory: PostgreSQL (Checkpoint 7)
        └── Dashboard (Checkpoint 10)
```

The LLM only *requests* actions. The mod validates and executes them on the server thread.
See [docs/architecture.md](docs/architecture.md).

## Repository layout

| Path | What lives there |
|---|---|
| `mod/` | Fabric mod (Gradle, Loom) |
| `backend/` | FastAPI service, onion architecture (`domain → application → infrastructure → api`) |
| `docs/` | Architecture, dev guide, decision records |
| `.agents/skills/`, `.claude/skills/` | Project skills for Codex and Claude Code (kept identical) |
| `.claude/agents/`, `.codex/agents/` | Review agents for Claude Code and Codex |

## Quick start

See [docs/development.md](docs/development.md) for the full setup.

```bash
cd backend && uv sync && uv run pytest
cd mod && ./gradlew build        # needs JDK 25
```

## Roadmap

Checkpoints 0–10 are tracked as GitHub milestones. Each one has acceptance criteria and
must be verified before the next starts.
