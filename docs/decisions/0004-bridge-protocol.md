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
| 4 | Contract | Pydantic models generate the JSON Schema, PROTOCOL.md and the invalid examples; humans write valid examples only |
| 5 | Versioning | `"v": 1` on every message, `hello` / `welcome` handshake |
| 6 | Message types | `hello`, `welcome`, `action`, `action_status`, `event`, `error` |
| 7 | Speech | `speak` takes 1-8 lines and an optional private recipient `to`; the mod paces them |
| 8 | Interruption | Chat within 24 blocks stops him after the current line |
| 9 | Unprompted speech | At most one per player every 3 min |
| 10 | Conversations | Reserved now (`conversation_id`, and `addressed` on chat), built in Checkpoint 5 |
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

- **Decision:** the Pydantic models in `backend/src/machiavillager/domain/protocol/` are the source of
  truth. `backend/scripts/export_protocol_schema.py` writes `shared/protocol.schema.json` (JSON Schema
  2020-12), which is committed and never edited by hand. A backend test fails when the committed file
  is stale. Java and Python tests validate the same `shared/examples`.
- **Why:** the backend needs these models anyway, so a hand-written schema would be a second copy that
  can drift. One source, checked in CI.
- **Testing:** humans write only the valid examples, one file per message shape. The invalid cases live
  in one generated `shared/examples/invalid.jsonl`, one per (message type, field, rule): a missing field,
  an unknown field, a wrong `v`, a value out of range, a broken pattern... A case is written only when
  both the parser and the schema reject it. Java and Python test the same file. The mod validates each
  incoming message against the bundled schema, so the rules live only in the schema, not in Java code.
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
| `event` | mod → backend | Something happened in the world; no reply. Events: `player_chat`, and the dev-only `dev_model` |
| `error` | both | A message could not be accepted |

Unknown fields are rejected, never silently ignored.

**Dev-only fields and events** (on when `MACHIAVILLAGER_DEV_ENDPOINTS` is on, nothing else changes):

- `welcome.models` (optional list of `provider:model`, max 16): the models the mod may suggest in commands. Omitted unless dev endpoints are on.
- `action.model` (optional `provider:model`) and `action.latency_ms` (optional int, 0..600000): which model wrote a `speak` and how long it took, for the HUD. Omitted for canned lines and dev echo.
- `event` `dev_model` with `data.op` `set` (needs `data.model`) or `reset` (no `model`): switches the model that answers, or goes back to the chain from the environment. Ignored with a log line when dev endpoints are off; no reply is sent.

### 7. Speech is multi-line

- **Decision:** the only action in Checkpoint 4 is `speak {lines: 1-8 strings of 1-256 chars}`. The mod
  paces the lines with `ReadingTime`. Statuses: `accepted → running → succeeded`.
- **Why:** he talks in bursts like a person typing, and the backend never has to manage timing.
- **Private speech:** `args.to` (a player UUID) makes the lines private text for that one player, at any
  distance, never voice. Without `to`, the lines are normal speech for players nearby. Explicit null is
  refused, as with `conversation_id`. One optional field, so the action set does not change.

### 8. Interruption

- **Decision:** a player who talks in normal chat within 24 blocks while he speaks interrupts him. He
  finishes the current line, drops the rest, reports `interrupted {spoken, of}`, and the player's
  `player_chat` event follows. Commands never interrupt; players out of range do not either.
- **Why:** it is what a person does, and it gives the AI a chance to react ("as I was saying...").

### 9. Unprompted speech is limited

- **Decision:** the backend may start speech from events. The mod allows at most one unprompted `speak`
  per player every 3 minutes (configurable); replies to a player who spoke to him do not count.
  A reply is recognised by `in_reply_to`: the `speak` names the `player_chat` event it answers. The mod
  skips the limit for it and does not start the cooldown. Speech without `in_reply_to` counts as
  unprompted. The busy rule still applies: one speech at a time.
- **Why:** endless monologues are tiring. The limit lives in the mod, so a misbehaving backend cannot
  spam players.

### 10. Conversations come in Checkpoint 5

- **Decision:** the contract already reserves an optional `conversation_id`. Conversation history and
  the 60-second silence rule are built together with the AI.
- **Addressed chat:** `player_chat` carries `addressed`, which says why the mod forwarded the line:
  `name`, `conversation`, `near`, `butt_in` (the player talked over him) or `private` (the @m channel).
- **NPC state:** `player_chat` may carry an optional `npc` object (phase, mood, affinity, independence and nearby players) with his state when the player spoke; without it the backend answers without game state.

### 11. Configuration

- **Decision:** `MACHIAVILLAGER_BRIDGE_URL` (default `ws://localhost:8000/bridge`) and
  `MACHIAVILLAGER_BRIDGE_TOKEN`. No token: the bridge stays off. `wss://` arrives with deployment
  (Checkpoint 13). The settings screen (#56) will read the same values later.
- **Rule:** the token is never logged.

### 12. JSON in Java with Gson

- **Decision:** parse with Gson, which Minecraft already ships. Strict validation lives in domain
  records; full JSON Schema validation runs in tests.
- **Rejected:** Jackson, one more library to bundle.

### 13. Relationship suggestions: the AI suggests, the mod decides

- **Decision:** after a conversation the backend may send `suggest_relationship {player, affinity_delta,
  reason`. `affinity_delta` is an int from -10 to 10, and 0 is never sent (the contract refuses it).
  `reason` is 1 to 120 characters, logged by the mod with the change and never shown to players. The
  backend sends it before the speak it belongs to and waits for its terminal status first, with the
  same timeout as speech; a failure of the suggestion never stops the speak.- **The AI never sets values.** It suggests a bounded move; the mod checks that the player exists,
  caps the change, rate-limits it (30 seconds between changes, +10 and -20 at most per 15 minutes
  for each player) and applies it. Replies: `rejected` with reason
  `unknown_player`, `rate_limited` or `capped`; otherwise the usual progress and terminal statuses.
- **Provenance is `claimed`.** The model only reports what the conversation suggests, so a big move
  from a chat line cannot come from it alone; bigger moves come from `observed` server events.
- **Why the writer clamps positive gains to +5:** feeling good about someone must be harder to earn
  than feeling bad. The writer clamps before the contract sees the value.
- **Why a feeling is optional and rare:** the prompt tells the model to omit it almost always, so most
  replies carry no suggestion and no extra round trip.

## Consequences

- The bridge has its own thread and queue and hands work to the server thread with `server.execute`.
- Adding an action means: Python model, regenerated shared files, one valid example, Java domain
  record + allowlist, executor, tests on both sides (see the `bridge-protocol` skill).
- A breaking format change bumps the protocol version.
- Dev only: `POST /dev/chat` builds a `player_chat` event and sends it through the same handler as a real
  one (`api/bridge/events.py`). With `MACHIAVILLAGER_DEV_ECHO` on, the echo responder answers every chat
  line with a `speak` of `(echo) <text>`, as a background task so the socket keeps reading the statuses.
  It tests the bridge without the LLM. It is not game behavior.
