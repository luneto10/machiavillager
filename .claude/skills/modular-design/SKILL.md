---
name: modular-design
description: Use when designing a new feature, NPC action, timer, goal, command or adapter in this repo — choosing between a new class and editing an existing one, adding an extension point, or picking a design pattern (catalog/option, sealed command, supplier pull, builder, countdown) so new behavior plugs in without rewriting the engine.
---

# Modular design in Machiavillager

Goal: **new behavior = new small class + one registration line.** Existing engines do not change.
Balance with YAGNI: build the extension point when the second use is real or scheduled in the
roadmap, not speculatively.

## Size and shape

- One class per responsibility, one reason to change. Split at ~200 lines or when a class does "X and Y".
- Group by feature: `minecraft/npc/` (body), `minecraft/behavior/` (brain glue), `domain/behavior/`
  (decision rules), `domain/time/` (timers). A future soldier gets `minecraft/soldier/`.
- Named constants for every tunable number at the top of the class (`START_DISTANCE`, weights).
- Entry points stay an index: `onInitialize()` is a list of `X.register()` calls, no logic.

## Patterns already in use (reuse them, don't invent parallels)

| Need | Pattern | Where |
|---|---|---|
| React to game moments | Observer: Fabric events, `register()` per feature class | `ServerLifecycleLog`, `DevCommands` |
| Assemble an immutable object step by step | Builder (`add(...)` returns `this`, `build()` validates) | `WeightedDice.builder()` |
| Pick one option by weight | `WeightedDice<T>` (generic, seeded `RandomGenerator`) | `ChooseNextAction` |
| A decision made now, executed later | Command: sealed `NpcAction` records | `NpcAction.Follow/Wander/Bark` |
| Plug in a new action kind | Catalog of `ActionOption`s; each proposes candidates or nothing | `FollowOption`, `BarkOption` |
| Handle every decision kind safely | `switch` over the sealed interface (compiler forces new cases) | `NpcBrain.act` |
| Body follows the brain without coupling | Pull via `Supplier<T>`: the goal asks every tick | `FollowPlayerGoal(targetProvider)` |
| Do something every N / random ticks, cooldowns | `Countdown.every(n)` / `randomBetween(min, max, rng)` | `NpcBrain`, `FollowPlayerGoal` |
| Swap tech (template → LLM, chat → voice) | Port interface in application + adapter outside | planned `LineWriter`, `Speaker` |
| One place for "the NPC says something" | Single adapter | `NpcSpeech.say` |
| Lines of dialogue | `Barks` record in domain (LLM fills batches later) | `domain/Barks` |
| Dev-only diagnostics | Gate on `FabricLoader.isDevelopmentEnvironment()`, build text lazily with a `Supplier` | `BrainDebugOverlay` |

## Adding a new NPC action (the canonical recipe)

1. `domain/behavior/NpcAction`: add a record to the sealed `permits` list (carry targets as UUIDs).
2. `domain/behavior/<Name>Option implements ActionOption`: propose only when possible; weight by mood/act.
3. Add it to `ChooseNextAction.withDefaultCatalog()`.
4. Compile: `NpcBrain.act` and `describe` fail until the new case is handled. Handle it there
   (instant actions) or expose it to a goal through a `Supplier` (continuous actions).
5. Unit-test the option (possible/impossible, weights) with a seeded `Random`.

## Smells to refuse

- An `if (action == ...)` chain instead of the sealed switch.
- A goal or adapter reading another class's private state instead of asking through a method/`Supplier`.
- A second hand-rolled tick counter instead of `Countdown`.
- An interface with one implementation and no fake, "for later".
- Game objects (`Player`, `ServerLevel`) inside `domain`/`application`: pass a snapshot or UUID.
