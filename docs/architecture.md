# Architecture

## Big picture

```
┌──────────────────────────── Minecraft server (Java 25) ────────────────────────────┐
│  Fabric mod                                                                          │
│   ├── NPC entity, navigation, perception (reads world state)                         │
│   ├── Action executor: validate → schedule on server thread → report result          │
│   ├── Bridge client: WebSocket, request IDs, timeouts, reconnect (off the tick thread)│
│   └── Simple Voice Chat addon: capture player audio, play NPC audio positionally     │
└──────────────────────────────────────┬───────────────────────────────────────────────┘
                                       │ JSON over authenticated WebSocket
┌──────────────────────────────────────▼───────────────────────────────────────────────┐
│  Python backend (FastAPI)                                                            │
│   api ─▶ infrastructure ─▶ application ─▶ domain        (dependencies point inward)  │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

**Golden rule:** the LLM decides *what to request*. The Java server decides *whether it is
allowed* and *executes it*. The LLM never runs arbitrary code or console commands.

## Backend: onion architecture

Each ring may depend only on rings inside it. `tests/test_architecture.py` enforces this.

| Layer | Owns | May import | Examples (future) |
|---|---|---|---|
| `domain` | Core concepts and rules. Pure Python + Pydantic. | nothing in the package | `Memory`, `Goal`, `Relationship`, `GameAction`, `Personality` |
| `application` | Use cases and **ports** (`typing.Protocol`) describing what they need. | `domain` | `HandleChatMessage`, `RunAgentTurn`, `MemoryRepository` port, `GameBridge` port |
| `infrastructure` | **Adapters** implementing ports with real tech. | `application`, `domain` | `PostgresMemoryRepository`, `PydanticAiAgent`, `AzureSpeechToText`, `Settings` |
| `api` | Delivery + composition root (wires adapters into use cases). | everything | FastAPI routes, mod WebSocket, dashboard endpoints |

Why: the interesting logic (personality, memory rules, which actions are allowed) stays
testable without a network, a database, or a model. Swapping NVIDIA for OpenRouter or
Postgres for SQLite only touches `infrastructure`.

Pragmatic limits: no interface until a second implementation or a test double needs it;
no layer gets a file until it has real code.

## Mod: same onion, Minecraft as the outer ring

Same vocabulary as the backend. Packages are created when their first class exists.

| Package | Ring | Rule |
|---|---|---|
| `...machiavillager.domain` | inner | Pure Java rules: acts, ambition meter, weighted dice, factions, Scepter rules, protocol records, validation. No `net.minecraft`/`net.fabricmc` imports, so JUnit tests run in milliseconds. |
| `...machiavillager.application` | inner | Use cases ("plant village hint", "roll next action", "coup") and the ports (interfaces) they need, e.g. `WorldQuery`, `Speaker`. |
| `...machiavillager.minecraft` | outer | Adapters: events, commands, NPC entity, soldiers, building, chat, titles. Implements the ports with real Minecraft code. |
| `...machiavillager.bridge` | outer | WebSocket client to the backend. Own thread; hands work to the server thread via `server.execute(...)`. |
| `...machiavillager.client` | outer | Client-only (separate source set): rendering, keybinds, push-to-talk UI. |
| `Machiavillager` (root) | bootstrap | Composition root: `onInitialize()` wires adapters into use cases. |

Glue with no rules (lifecycle logs, a `ping` command) lives straight in `minecraft`; no port needed.
Once `domain` has its first class, an ArchUnit test will fail the build if it imports Minecraft.

Threading rule: **never block the server tick thread**. Network I/O and waiting happen on
the bridge thread. World reads and writes happen only on the server thread.

## Protocol (Checkpoint 4)

Messages carry `request_id`, `action`/`event`, `args`. The mod replies with
`accepted | running | succeeded | failed | rejected` plus a reason. The schema will live in
`shared/protocol.schema.json` and be the single source both sides test against.

## Memory (Checkpoint 7)

Identity (versioned config) · short-term (bounded recent dialogue) · episodic (events with
time, place, participants) · semantic (facts, with provenance: `observed` by server vs
`claimed` by a player) · goals (pending/active/completed/failed/abandoned). Postgres full-text
search first; pgvector only if retrieval quality demands it.
