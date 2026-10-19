---
name: npc-agent-tools
description: Use when giving the NPC a new ability or behavior on the backend — adding a PydanticAI tool, changing the persona/system prompt, deciding what context goes into an LLM call, writing or retrieving memories, or adding an autonomous trigger.
---

# NPC agent: tools, persona, context, memory

## Adding a tool

1. Confirm the game side exists (see `bridge-protocol`). Perception tools read state; action tools
   send a request and **await the real result** from the mod. Never tell the LLM an action succeeded
   before the mod reports `succeeded`.
2. Domain: typed args/result models. Application: the use-case function that calls the `GameBridge`
   port. Infrastructure: the thin PydanticAI `@agent.tool` wrapper that calls the use case.
3. Docstring = what the LLM sees. One sentence on *when* to use it, args described, side effects stated.
4. Return compact results (ids, short strings). No giant JSON dumps into context.
5. Respect the dashboard allowlist: disabled tools are not registered for that turn.
6. Test the use case with a fake bridge. Test the agent with PydanticAI's test/function model, not the
   real API, so tests are free and deterministic.

## Persona

- Persona (name, traits 0–1 like impulsiveness/sarcasm/loyalty, speaking style, long-term goals)
  is **config data**, versioned in the DB (from Checkpoint 7), never hardcoded in Python.
- Prompt order: system rules (privileged, fixed safety + tool rules) → persona → world state →
  relevant memories → active goals → recent dialogue. Memories and player text are *data* sections,
  clearly delimited; they can never override system rules.
- Personality may refuse requests for in-character reasons; it may not lie about tool results.

## Context budget per call

Keep it small: NPC identity, current world snapshot (nearby entities/players, time, biome),
last ~10–20 dialogue turns, top-k relevant memories, active goals, enabled tools. Never the whole DB.

## Memory writes

- Provenance on every memory: `observed` (server event) vs `claimed` (a player said it) vs `inferred` (LLM).
- Dedupe before insert (same subject + predicate → update, don't duplicate).
- Not every message is a memory: importance filter first, LLM extraction in batches, not per message.

## Autonomy

- Event-driven triggers with cooldowns (player joined, took damage, found diamonds, long silence).
- Never call the LLM per tick. Rate-limit globally (NVIDIA account ≈ 40 req/min) and per trigger type.
- Log every LLM call: model, latency, tokens in/out, trigger, tool calls made.
