---
name: bridge-protocol
description: Use when adding, changing, or debugging a message between the Minecraft mod and the Python backend — a new game action the NPC can request (follow_player, move_to...), a new world event the mod reports, request/response statuses, timeouts, auth, or reconnection.
---

# Mod ⇄ backend bridge protocol

Transport: one authenticated WebSocket from the mod (client side of the socket) to the backend.
JSON messages. Schema source of truth: `shared/protocol.schema.json` (created in Checkpoint 4).

## Message shapes

Backend → mod (action request):

```json
{"type": "action", "request_id": "act_01H...", "action": "follow_player",
 "args": {"player_uuid": "..."}, "timeout_ms": 30000}
```

Mod → backend (action status, may be sent several times per request):

```json
{"type": "action_status", "request_id": "act_01H...", "status": "running"}
{"type": "action_status", "request_id": "act_01H...", "status": "failed", "reason": "player_offline"}
```

Statuses: `accepted` → `running` → `succeeded | failed`; or `rejected` immediately (unknown action,
bad args, not allowlisted, rate limited, not permitted).

Mod → backend (event, no reply expected):

```json
{"type": "event", "event_id": "evt_...", "event": "player_chat",
 "occurred_at": "2026-10-18T12:00:00Z", "data": {"player_uuid": "...", "text": "hi"}}
```

## Adding a new action — do all steps, in order

1. **Schema**: add the action name and its `args` schema to `shared/protocol.schema.json`.
2. **Python domain**: add a Pydantic model for the args in `backend/.../domain/`.
3. **Python tests**: valid + invalid args round-trip against the schema.
4. **Java domain**: add a record for the args + validation in `mod/.../domain/` (no Minecraft imports);
   add the name to the action allowlist.
5. **Java tests**: JUnit for validation and rejection reasons.
6. **Java executor**: implement in `mod/.../minecraft/`; runs on the server thread via `server.execute`;
   long-running actions emit `running` then a terminal status.
7. **Agent tool** (if the LLM should use it): see the `npc-agent-tools` skill.
8. Manual check in `runClient` + backend running: request it, watch both logs show the same `request_id`.

## Invariants

- Every request has a unique `request_id`; every terminal status echoes it.
- The mod rejects anything not on the allowlist. Unknown fields → reject, don't ignore silently.
- The backend times out waiting (`timeout_ms`) and treats the action as failed; the mod also
  cancels it on its side.
- The mod never blocks the tick thread on the socket; the bridge has its own thread and queue.
- Reconnect with backoff (1s, 2s, 4s… max 30s). In-flight actions on disconnect → `failed: disconnected`.
- Auth: shared token from env (`MACHIAVILLAGER_BRIDGE_TOKEN`) in the connect handshake. Never log it.
- Log one line per message at debug: direction, type, request_id/event_id, action, status, latency.
