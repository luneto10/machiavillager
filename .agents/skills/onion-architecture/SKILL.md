---
name: onion-architecture
description: Use when adding or moving code in the mod (mod/src) or the backend (backend/src) — deciding which layer a class belongs in (domain, application, minecraft/infrastructure, api), adding a port or adapter, wiring dependencies, or when ArchitectureTest (Java) or test_architecture.py (Python) fails.
---

# Onion architecture (mod and backend)

Dependencies point **inward only**. Inner rings never know outer rings exist.

```
Mod (Java)                                   Backend (Python)
bootstrap: Machiavillager.onInitialize()     api            (FastAPI routes, composition root)
minecraft / bridge / client  (adapters)      infrastructure (Postgres, PydanticAI, Azure)
application  (use cases + ports)             application    (use cases + ports)
domain       (pure rules)                    domain         (pure rules)
```

Enforced by tests, so a violation fails the build:
- Mod: `mod/src/test/java/.../ArchitectureTest.java` (ArchUnit). `domain`/`application` may not use
  `net.minecraft`, `net.fabricmc`, `com.mojang`; `domain` may not use outer packages; `application`
  may not use adapters.
- Backend: `backend/tests/test_architecture.py` (stdlib `ast`).

## Where does it go? Ask in order, stop at the first yes

1. **A rule or concept with no I/O?** (acts, ambition, moods, dice, Scepter rules, memory provenance,
   allowlists, `NpcAction` decisions) → `domain`. Pure Java / Python. Inputs come in as arguments
   (a `Situation` snapshot, a `RandomGenerator`), never read from the world directly.
2. **A workflow that coordinates domain objects and needs the outside world?** ("choose next action",
   "plant a village hint", "handle player chat") → `application`. Declare what it needs as a **port**
   (Java interface / Python `Protocol`) only when an adapter or a test fake needs it.
3. **Talks to a real technology?**
   - Mod: Minecraft/Fabric (entities, goals, commands, chat, world queries) → `minecraft`;
     backend WebSocket → `bridge`; rendering/keybinds → `client` (client source set).
   - Backend: Postgres, LLM, speech, env settings → `infrastructure`.
   Adapters translate vendor types to domain types at the boundary (e.g. `ServerPlayer` →
   `NearbyPlayer(uuid, name, distance)` in `SituationSnapshot`).
4. **Wiring?** Mod: `Machiavillager.onInitialize()` (only `register()` calls and object assembly).
   Backend: `api` composition root.

## Worked example (mod): the NPC's decisions

```
minecraft  SituationSnapshot.take()   reads real players → Situation        (adapter: world → domain)
application ChooseNextAction.choose() options propose → WeightedDice → roll (use case)
domain     FollowOption, NpcAction.Follow(uuid), WeightedDice, Countdown    (rules, pure)
minecraft  NpcBrain.act() + FollowPlayerGoal                                (adapter: decision → body)
```

## Rules of thumb

- The domain receives **snapshots and IDs** (UUIDs), never live game objects.
- Decisions are **data** (`NpcAction` records); execution lives in the outer ring.
- Randomness and time are passed in (`RandomGenerator`, `Countdown`) so tests are deterministic.
- No port until a second implementation or a fake needs it. No empty layer files.
- Vendor errors are translated at the boundary; inner rings never catch Minecraft/HTTP exceptions.

## Testing per ring

| Ring | Test |
|---|---|
| domain | JUnit / pytest, no fixtures, milliseconds; seeded `Random(42)` for anything random |
| application | same, with tiny fakes implementing the ports |
| minecraft / infrastructure | in game (`runClient`) or integration tests; keep this ring thin |

## Checklist

- [ ] `cd mod && ./gradlew build` (includes ArchitectureTest) and/or `cd backend && uv run pytest`
- [ ] New class sits in the ring the decision list gives
- [ ] Inner code got a unit test; outer code got a manual in-game check described in the PR
