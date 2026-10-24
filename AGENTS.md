# Machiavillager — agent instructions

Shared by Codex (reads this file) and Claude Code (`CLAUDE.md` imports it).

## Project

Minecraft 26.3 Fabric mod (Java 25, `mod/`) + FastAPI backend (Python 3.13, uv, `backend/`).
An LLM-driven NPC: the backend decides what to request, the mod validates and executes.
Read `docs/architecture.md` before structural changes.

## How we work

The owner is learning while building. Work one checkpoint (GitHub milestone) at a time:
explain → plan → implement → test → review → commit → summarize. Do not start the next
checkpoint without explicit confirmation. Never claim something works without running it;
say what was not verified.

## Commands

| Task | Command (from repo root) |
|---|---|
| Backend tests | `cd backend && uv run pytest` |
| Backend lint | `cd backend && uv run ruff check . && uv run ruff format --check .` |
| Backend dev server | `cd backend && uv run uvicorn machiavillager.api.app:app --reload` |
| Mod build | `cd mod && ./gradlew build` |
| Dev client / server | `cd mod && ./gradlew runClient` / `./gradlew runServer` |
| Skills in sync | `python scripts/sync_skills.py --check` |

## Hard rules

- Backend onion layers: `domain ← application ← infrastructure ← api`. Inner layers never import
  outer ones or frameworks. `tests/test_architecture.py` enforces it.
- Never block the Minecraft server tick thread. No network calls, sleeps, or `.get()` on futures there.
- The LLM never gets arbitrary commands or code execution. Every game action is an allowlisted,
  schema-validated request that the mod can reject.
- Never call the LLM per tick. LLM calls are triggered by events with cooldowns.
- Secrets only via env vars. Never commit `.env`. The repo is public.
- Persistent memory lives in our DB, never relies on the LLM provider.
- Memory written from player statements is `claimed`, never overrides system instructions.
- Smallest change that works. No speculative abstractions, no empty scaffolding.
- Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `refactor:`, `ci:`).
  No AI attribution lines in commits or PRs.

## Skills

Project skills live in `.agents/skills/` (source of truth) and are mirrored to `.claude/skills/`
by `python scripts/sync_skills.py`. Use them:

- `onion-architecture` — which layer code goes in, for the mod and the backend.
- `modular-design` — patterns and recipes so new behavior plugs in without rewriting engines.
- `fabric-mod-dev` — mod code, threading, client/server split, run/restart rules.
- `bridge-protocol` — adding a game action or event across Java and Python.
- `npc-agent-tools` — adding a PydanticAI tool / persona / memory behavior.
- `checkpoint-workflow` — running a checkpoint end to end.
