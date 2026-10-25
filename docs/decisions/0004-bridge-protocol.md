# 0004. Mod ⇄ backend bridge protocol

| | |
|---|---|
| **Date** | 2026-10-24 |
| **Status** | Accepted (Checkpoint 4) |
| **Issues** | #7, #8, #9, #10 |
| **Contract** | [`shared/protocol.schema.json`](../../shared/protocol.schema.json) |

## Context

The AI lives in the Python backend; the game runs in the Java mod. They must talk both ways at any
moment (the backend asks him to speak, the mod reports that a player spoke), without ever slowing the
server tick and without giving the AI any power beyond an allowlist.

## Summary

| # | Decision | In one line |
|---|---|---|
| 1 | Transport | One WebSocket; the mod is the client |
| 2 | Backend role | Optional: without it the mod plays as in Checkpoint 3 |
| 3 | Who decides | The mod's dice drives daily behavior; the backend reacts to events |
| 4 | Contract | Pydantic models generate the JSON Schema; shared examples + mutations on both sides |
| 5 | Versioning | `"v": 1` on every message, `hello` / `welcome` handshake |
| 6 | Message types | `hello`, `welcome`, `action`, `action_status`, `event`, `error` |
| 7 | Speech | `speak` takes 1-8 lines; the mod paces them |
| 8 | Interruption | Chat within 24 blocks stops him after the current line |
| 9 | Unprompted speech | At most one per player every 3 min |
| 10 | Conversations | Reserved now (`conversation_id`), built in Checkpoint 5 |
| 11 | Configuration | Env vars now; no token means the bridge is off |
| 12 | JSON in Java | Gson, already shipped with Minecraft |

## Decisions

### 1. Transport: one WebSocket, the mod is the client

- **Decision:** one long-lived WebSocket. The mod dials the backend; the backend never dials the game.
- **Why:** both sides can push at any moment over one connection. The game server never opens a port.
  The JDK ships `java.net.http.WebSocket` (no new dependency in the mod); FastAPI supports it natively.
- **Rejected:**
  - HTTP polling: latency, and the backend cannot push.
  - HTTP both ways: the game server would need an inbound port.
  - gRPC: heavy dependency in the mod.
  - Raw TCP: we would reinvent message framing.
  - A message broker: one more service to run.

### 2. The backend is optional

- **Decision:** with no token or with the backend down, the mod behaves exactly as in Checkpoint 3
  (dice, barks, fixed meeting agenda).
- **Why:** the game must never depend on the network. The AI improves him; it does not run him.

### 3. The mod keeps the day-to-day brain

- **Decision:** the dice still picks Follow, Wander, Bark, Hint and Summon. The backend acts on events
  (conversation, meeting, plans), never per tick.
- **Why:** cheaper, faster, and safe when the backend is down.

### 4. Contract generated from the Python models

- **Decision:** the Pydantic models in `backend/src/machiavillager/domain/protocol.py` are the source of
  truth. `backend/scripts/export_protocol_schema.py` writes `shared/protocol.schema.json` (JSON Schema
  2020-12), which is committed and never edited by hand. A backend test fails when the committed file
  is stale. Java and Python tests validate the same `shared/examples`.
- **Why:** the backend needs these models anyway, so a hand-written schema would be a second copy that
  can drift. One source, checked in CI.
- **Testing:** `examples/valid` has one file per message shape; `examples/invalid` keeps only business
  rules (too many lines, interrupted without counts...). Structural rules (missing or wrong `v`, unknown
  `type`, extra fields) are covered by automatic mutations of the valid examples, on both sides.
- **Changed:** first accepted as a hand-written schema; switched the same day, before any runtime code
  depended on it. Trade-off accepted: the backend owns the contract; the Java tests still enforce it.

### 5. Versioning and handshake

- **Decision:** every message carries `"v": 1`. On connect the mod sends `hello` (mod version, protocol
  version); the backend answers `welcome`, or `error: unsupported_protocol` and closes.
- **Why:** a future format change is detected at connect time instead of failing in odd ways later.

### 6. Message types

| Type | Direction | Purpose |
|---|---|---|
| `hello` | mod → backend | First message after connecting |
| `welcome` | backend → mod | Handshake accepted |
| `action` | backend → mod | Request an allowlisted action |
| `action_status` | mod → backend | Progress of a request (may be sent several times) |
| `event` | mod → backend | Something happened in the world; no reply |
| `error` | both | A message could not be accepted |

Unknown fields are rejected, never silently ignored.

### 7. Speech is multi-line

- **Decision:** the only action in Checkpoint 4 is `speak {lines: 1-8 strings of 1-256 chars}`. The mod
  paces the lines with `ReadingTime`. Statuses: `accepted → running → succeeded`.
- **Why:** he talks in bursts like a person typing, and the backend never has to manage timing.

### 8. Interruption

- **Decision:** a player who talks in normal chat within 24 blocks while he speaks interrupts him. He
  finishes the current line, drops the rest, reports `interrupted {spoken, of}`, and the player's
  `player_chat` event follows. Commands never interrupt; players out of range do not either.
- **Why:** it is what a person does, and it gives the AI a chance to react ("as I was saying...").

### 9. Unprompted speech is limited

- **Decision:** the backend may start speech from events. The mod allows at most one unprompted `speak`
  per player every 3 minutes (configurable); replies to a player who spoke to him do not count.
- **Why:** endless monologues are tiring. The limit lives in the mod, so a misbehaving backend cannot
  spam players.

### 10. Conversations come in Checkpoint 5

- **Decision:** the contract already reserves an optional `conversation_id`. Conversation history and
  the 60-second silence rule are built together with the AI.

### 11. Configuration

- **Decision:** `MACHIAVILLAGER_BRIDGE_URL` (default `ws://localhost:8000/bridge`) and
  `MACHIAVILLAGER_BRIDGE_TOKEN`. No token: the bridge stays off. `wss://` arrives with deployment
  (Checkpoint 13). The settings screen (#56) will read the same values later.
- **Rule:** the token is never logged.

### 12. JSON in Java with Gson

- **Decision:** parse with Gson, which Minecraft already ships. Strict validation lives in domain
  records; full JSON Schema validation runs in tests.
- **Rejected:** Jackson, one more library to bundle.

## Consequences

- The bridge has its own thread and queue and hands work to the server thread with `server.execute`.
- Adding an action means: schema branch, examples, Java domain record + allowlist, Python model,
  executor, tests on both sides (see the `bridge-protocol` skill).
- A breaking format change bumps the protocol version.
