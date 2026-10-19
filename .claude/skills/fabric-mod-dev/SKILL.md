---
name: fabric-mod-dev
description: Use when writing or changing Java code in mod/ — registering entities, events, commands or network payloads, touching client vs server code, running runClient/runServer, debugging a crash, deciding if a change needs a restart, or bumping Minecraft/Fabric versions.
---

# Fabric mod development (Minecraft 26.3, Java 25)

## Facts that matter

- Minecraft **26.x is unobfuscated**: code uses Mojang's official names (`ServerLevel`, `ServerPlayer`,
  `MinecraftServer`, `Identifier`, `BuiltInRegistries`). There are no Yarn mappings. Old tutorials
  using Yarn names (`ServerWorld`, `PlayerEntity`, `World`) or pre-26 Mojang names do not match — **verify
  every class/method name** in the decompiled sources (`./gradlew genSources`, then Ctrl+N in IntelliJ)
  or Fabric docs for 26.x before writing it.
- Loom plugin id: `net.fabricmc.fabric-loom`. Versions live in `mod/gradle.properties`.
- Split source sets: `src/main` (common, runs on server) and `src/client` (client only).
  Client code (rendering, keybinds, screens, `Minecraft` client class) **must** go in `src/client`.
  The compiler blocks `main` from importing `client`.

## Package layout (create when first needed)

| Package | Contents | Rule |
|---|---|---|
| `...machiavillager.core` | protocol records, validation, allowlists, rate limiting | No `net.minecraft` / `net.fabricmc` imports. JUnit-testable. |
| `...machiavillager.bridge` | WebSocket client to backend | Own thread. Never touches world state directly. |
| `...machiavillager.game` | NPC entity, AI goals, perception, action executors | Server thread only. |
| `...machiavillager.client` | renderers, keybinds, HUD | `src/client` source set. |

## Threading (most important rule)

- The server tick thread must never block: no HTTP/WebSocket calls, `Thread.sleep`, `future.get()`,
  file I/O loops, or LLM waits on it.
- From any other thread, hop onto the server thread with `server.execute(() -> ...)` before reading
  or mutating entities, blocks, or players.
- Long actions (follow, walk to) are state machines advanced by entity goals/ticks; they report
  progress back through the bridge asynchronously.

## Run and debug

```bash
./gradlew runClient     # dev client, worlds in mod/run/
./gradlew runServer     # dev server; accept eula in mod/run/eula.txt, online-mode=false
./gradlew build         # jar in mod/build/libs
```

Use IntelliJ **Debug** on the Loom run configs for breakpoints + HotSwap.

| Change | Needs restart? |
|---|---|
| Method body | No (Build → Recompile, HotSwap) |
| Add/remove members, classes, signatures | Yes |
| `fabric.mod.json`, mixins, registrations (entities, payloads, commands) | Yes |
| Textures/lang | `F3+T` in client |

## Crash triage

1. Read the first `Caused by:` in `mod/run/logs/latest.log` or `crash-reports/`.
2. `NoSuchMethodError`/`ClassNotFound` → wrong name for 26.3 or client class in common code.
3. `Accessing LegacyRandomSource from multiple threads` / `ConcurrentModificationException`
   → world touched off the server thread; wrap in `server.execute`.

## Version bumps

Check https://fabricmc.net/develop, Fabric API and Simple Voice Chat on Modrinth for the new
version **before** bumping. Bump `minecraft_version`, `loader_version`, `fabric_api_version`,
`loom_version` together in one commit, plus `fabric.mod.json` `depends.minecraft`.
