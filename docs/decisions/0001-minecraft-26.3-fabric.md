# 0001. Minecraft 26.3 on Fabric

Date: 2026-10-18 · Status: accepted

## Context

We need one Minecraft version and loader for a standalone mod with a custom NPC entity,
client/server networking, and Simple Voice Chat (SVC) integration. Checked 2026-10-18:

| | Fabric | NeoForge |
|---|---|---|
| 26.3 support | Loader 0.19.5, Fabric API 0.163.0+26.3, Loom 1.18 | 26.3.0.x builds available |
| SVC 2.6.24 for 26.3 | yes | yes |
| Official template on 26.3 | `FabricMC/fabric-example-mod` default branch = 26.3 | MDK available |
| Entity registration | Registry + `FabricDefaultAttributeRegistry` | `DeferredRegister` + events |
| Networking | Fabric Networking API (payload codecs) | NeoForge payload registrar |
| Big modpack draw | smaller mods, fast updates | Create, larger tech mods (Create not on 26.3 yet) |
| JDK | 25 | 25 |

26.1+ ships unobfuscated, so both loaders use Mojang's names. No Yarn/Parchment mapping layer.

## Decision

Minecraft **26.3**, **Fabric**, **Java 25**.

- Latest stable, both loaders and SVC already support it; no reason to start on an old version.
- Fabric: lighter, official template already on 26.3, split client/server source sets catch
  client-only code leaking into the server at compile time. We don't need Create or a big modpack.

## Consequences

- 26.4 is in snapshots. Stay on 26.3 until SVC and Fabric API both ship 26.4, then bump in one PR.
- If friends later want a NeoForge modpack, the `core` package (no Minecraft imports) ports as is;
  only `game`/`bridge` glue changes.
