# Machiavillager

A chaotic, scheming AI companion for Minecraft Java Edition. He starts as your friend, slowly grows
ambitious, and is absolutely going to corner the emerald market. Every world tells a different story.

> **Status:** Checkpoints 0-4 done. **Checkpoint 5 (first AI conversation) in progress.**
> Game design: [docs/design/emerald-empire.md](docs/design/emerald-empire.md) ·
> Architecture: [docs/architecture.md](docs/architecture.md) ·
> Decisions: [docs/decisions/](docs/decisions/)

## What he does today

| | |
|---|---|
| **Lives on his own** | Every 10-30 s he rolls weighted dice: follow, wander, bark, mutter to himself, drop a hint, summon a player. Mood changes the odds. |
| **Stays with friends** | Follows players he likes from any distance and teleports to them like a tamed wolf. |
| **Has a story** | Ambition grows only while players are near and moves him through Partner → Seeds → Proposal → Regime → War (2-3 h per phase, seeded per world). Independence rises when he is alone and falls with company. Everything is saved with the world. |
| **Remembers how you treat him** | Affinity per player; punching him costs it (rate-limited, no farming). |
| **Points at real villages** | From the Seeds phase on he tells you the real coordinates of the nearest village. |
| **Calls meetings** | Pulls a far player to him with a warning card, talks through an agenda built from his state, then sends them back. Fair rules, per-player opt-out, silent retries. |
| **Talks to the backend** | An authenticated WebSocket to a Python backend that can make him speak (`/dev/speak`). The AI conversation is Checkpoint 5. |

## How it fits together

```
Minecraft 26.3 + Fabric mod (Java 25)
   │  the game decides day to day; the bridge thread never blocks the tick
   │  one authenticated WebSocket, JSON contract in shared/ (ADR 0004)
   ▼
FastAPI backend (Python 3.13, uv)
   ├── LLM through PydanticAI, provider swappable (Checkpoint 5)
   ├── Memory: PostgreSQL (Checkpoint 7)
   ├── Voice: Simple Voice Chat addon (Checkpoint 11)
   └── Dashboard (Checkpoint 10)
```

The AI only **requests** allowlisted actions. The mod validates and executes them on the server
thread. Without the backend the mod plays on its own.

## Quick start

Full setup: [docs/development.md](docs/development.md). You need JDK 25 and [uv](https://docs.astral.sh/uv/).

1. **Secrets.** Copy `.env.example` to `.env` at the repo root (git ignores it). Generate a bridge token:
   ```bash
   cd backend
   uv run python -c "import secrets; print(secrets.token_urlsafe(32))"
   ```
   Put it in `.env` as `MACHIAVILLAGER_BRIDGE_TOKEN=...`. Both the backend and the mod read this file.
2. **Backend.**
   ```bash
   cd backend
   uv sync
   uv run uvicorn machiavillager.api.app:app --reload
   ```
   In IntelliJ (project opened on `mod/`) use the shared run configuration **Backend (uvicorn)**.
3. **Game.** `cd mod && ./gradlew runClient`, open a world, then `/machiavillager spawn` and
   `/machiavillager bridge status` (should say `CONNECTED`).
4. **Make him talk.** With `MACHIAVILLAGER_DEV_ENDPOINTS=true` in `.env`, open
   http://localhost:8000/docs and try `POST /dev/speak`.

Dev-only commands in `runClient`: `/machiavillager brain do <follow|wander|bark|hint|summon|mutter>`,
`brain odds`, `brain next`, `brain set ambition|independence|mood`. Every player can use
`/machiavillager summons on|off`.

## Tests

```bash
cd backend && uv run pytest && uv run ruff check . && uv run ruff format --check .
cd mod && ./gradlew build          # unit tests + ArchitectureTest (onion rules)
python scripts/sync_skills.py --check
```

## Repository layout

| Path | What lives there |
|---|---|
| `mod/` | Fabric mod. `domain` (pure rules) ← `application` ← `minecraft` / `bridge` / `client` |
| `backend/` | FastAPI service. `domain` ← `application` ← `infrastructure` ← `api` |
| `shared/` | Bridge contract: generated `protocol.schema.json` + examples both test suites read |
| `docs/` | Architecture, game design, dev guide, decision records (ADRs) |
| `.agents/skills/`, `.claude/skills/` | Project skills for Codex and Claude Code (kept identical) |
| `.claude/agents/`, `.codex/agents/` | Review agents |

## Working together

- Work is tracked as GitHub milestones (one per checkpoint) and issues with acceptance criteria.
- One branch per issue off `main` (`feat/<issue>-<slug>`), small PRs, CI green, the other dev reviews.
- Changes to the bridge contract are their own PR and update [ADR 0004](docs/decisions/0004-bridge-protocol.md).
- Never commit `.env` or any secret: this repository is public.
- Read [AGENTS.md](AGENTS.md) before your first change: it holds the project rules for humans and coding agents.
