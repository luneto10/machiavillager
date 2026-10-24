# 0004. Mod ⇄ backend bridge protocol

Date: 2026-10-24 · Status: accepted (Checkpoint 4)

## Context

The AI lives in the Python backend; the game runs in the Java mod. They need to talk both ways at any
moment (the backend asks him to speak, the mod reports that a player spoke) without ever slowing the
server tick, and without giving the AI any power beyond an allowlist.

## Decisions

1. **Transport: one WebSocket, the mod is the client.** Both sides push whenever they need to, over a
   single long-lived connection. The mod dials out, so the Minecraft server never opens a port. The
   mod uses the JDK's `java.net.http.WebSocket` (no new dependency); the backend uses FastAPI.
   Rejected: HTTP polling (latency, no push), HTTP both ways (inbound port on the game server), gRPC
   (heavy dependency in the mod), raw TCP (we would reinvent framing), a broker (extra service).
2. **The backend is optional.** Without it (no token, or backend down) the mod behaves exactly as in
   Checkpoint 3: dice, barks, fixed agenda. The AI only improves him.
3. **The mod keeps the day-to-day brain.** The dice still picks Follow/Wander/Bark/Hint/Summon. The
   backend acts on events (conversation, meeting, plans), never per tick.
4. **Hand-written contract in `shared/`.** `shared/protocol.schema.json` (JSON Schema 2020-12) plus
   `shared/examples/{valid,invalid}`; Java and Python tests both load the same examples. Neutral:
   neither side generates the other's contract.
5. **Versioned with a handshake.** Every message carries `"v": 1`. On connect the mod sends `hello`
   (mod version, protocol version); the backend answers `welcome` or `error: unsupported_protocol`
   and closes.
6. **Message types:** `hello`, `welcome`, `action`, `action_status`, `event`, `error`.
   Unknown fields are rejected, never ignored.
7. **Speech is multi-line.** The first and only action in Checkpoint 4 is
   `speak {lines: 1-8 strings of 1-256 chars}`. The mod paces the lines with `ReadingTime`, so it
   reads like typing; statuses `accepted → running → succeeded`.
8. **Interruption.** A player who talks in normal chat within his hearing range (24 blocks) while he
   speaks interrupts him: he finishes the current line, drops the rest, and reports
   `interrupted` with `{spoken, of}`; the player's `player_chat` event follows. Commands never
   interrupt.
9. **He may talk unprompted, with a limit.** The backend may start speech from events; the mod
   allows at most one unprompted `speak` per player every 3 minutes (configurable). Replies to a
   player who spoke to him do not count.
10. **Conversations come in Checkpoint 5.** The contract already reserves an optional
    `conversation_id`; history and the 60 s silence rule are implemented with the AI.
11. **Config through env vars for now:** `MACHIAVILLAGER_BRIDGE_URL` (default
    `ws://localhost:8000/bridge`) and `MACHIAVILLAGER_BRIDGE_TOKEN`. No token: the bridge stays off.
    `wss://` arrives with deployment (Checkpoint 13). The token is never logged.
12. **JSON in Java with Gson**, which Minecraft already ships. Strict validation lives in domain
    records; full JSON Schema validation runs in tests.

## Consequences

- The game never depends on the network: the bridge has its own thread and queue and hands work to
  the server thread with `server.execute`.
- Adding an action means: schema, examples, Java domain record + allowlist, Python model, executor,
  tests on both sides (see the `bridge-protocol` skill).
- The protocol version must be bumped for breaking changes.
