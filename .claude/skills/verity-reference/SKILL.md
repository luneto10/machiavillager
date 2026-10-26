---
name: verity-reference
description: Use when designing an NPC AI feature (perception or action tools, memory, prompt structure, relationship meters, proactive events, voice) and you want to see how the Verity mod solved it first. Lists the Verity repos, their licenses, its architecture and how each idea maps to our stack.
---

# Verity as a reference

Verity is a Minecraft mod with an LLM companion (a talking yellow sphere) that the owner considers
the bar to reach. Before building an NPC AI feature, check how Verity does it, then build our own
version in our architecture.

## Repos and what you may do with them

| Repo | What it is | License | Use |
|---|---|---|---|
| [Klisuaiji/verity-JE](https://github.com/Klisuaiji/verity-JE) | The original mod (VarmiteYT), decompiled | none stated | **Read for ideas only. Never copy code or prompt text.** |
| [hanchen68/verity-extended](https://github.com/hanchen68/verity-extended) | Forge add-on: awareness events, AI building, tool trimming, voice | MIT | Ideas and code may be reused with attribution |
| [MrPauk335/Verity-Minecraft-Mod](https://github.com/MrPauk335/Verity-Minecraft-Mod) | Fabric re-implementation (dialogue controller, LLM client, TTS payload) | CC0 | Free to reuse |
| [verity-mods/verity-enhanced](https://github.com/verity-mods/verity-enhanced) | README and an external download link, no source | MIT text, no code | Ignore; do not download its archive |

Read files with `gh api repos/<owner>/<repo>/contents/<path> --jq .content | base64 -d`; list them
with `gh api "repos/<owner>/<repo>/git/trees/HEAD?recursive=1" --jq '.tree[].path'`.

## How Verity is built

Everything runs inside the Java mod: no separate backend.

- **LLM:** langchain4j calls the provider (OpenAI-compatible, Ollama, others) from the mod.
- **Prompt:** one XML template (`src/main/resources/prompts/verity.xml` in verity-JE) with
  `{PLACEHOLDERS}` filled per call: identity, personality, lore, secrets, likes and dislikes, rules,
  current karma, date. Each player line arrives as `[date] <NAME> text`.
- **Secrets gated by the relationship:** the prompt lists secrets plus a disclosure rule (low karma:
  never reveal; middle: hint; high: tell). The model gets the gate, not a hidden script.
- **Perception tools** (`entity/LLM/actions/Tools.java`, about 40 `@Tool` methods): biome,
  inventory, nearby entities, nearest ore by type, nearest village, nearest nether fortress, player
  and own coordinates, dimension, distance, health, light level, game time, difficulty, the block
  the player looks at, installed mods. The model asks only when it needs the fact, so the prompt
  stays small.
- **Action tools:** change karma, change expression, play a sound or its favourite song, drop an
  item, follow or stop following, teleport to the player, transform.
- **Memory** (`entity/LLM/store/`): two JSON files in the world folder. `verity_chat_memory.json`
  keeps the chat history per memory id; `verity_memory.json` is a key to text map the model manages
  through tools (add, get, remove, list keys), saved on every add.
- **Relationship meter:** karma 0 to 20, moved by tools and by events (damage, being left alone,
  attention, help). Shown on a HUD bar.
- **Voice:** local TTS and STT through sherpa-onnx (`SherpaBridge`), microphone capture on the client.
- **verity-extended adds:** proactive awareness events with per-event cooldowns (low health, death,
  sleeping, entering a world), `AiEventThrottle`, `ContextTrimmer`, `ToolAvailabilityFilter` and
  `ToolListTrimmer` (send only the tools that apply right now), web search, camera vision.

## More details worth reusing

- **Chat window:** `MessageWindowChatMemory` with the last **20 messages**, saved on every message and
  never reset by silence. Game events enter the same window as short system lines
  (`<SYSTEM> A heavy block has dropped on verity during this turn`), which is how he remembers what
  happened, not only what was said. Idle chatter is a `<SYSTEM>` prompt too ("you are bored, comment
  on the environment").
- **Token cost:** the world is not in the prompt. A fixed template plus the 20-message window; facts
  come from tools only when asked.
- **Providers (`VerityConfig`):** the player picks `AiProvider` (OpenAI default, Groq, Gemini,
  OpenRouter, Mistral, Ollama), endpoint, model and key in the mod config; `temperature` 0.8. Ollama:
  `localhost` is forced to `127.0.0.1` because Java resolves IPv6 first on Windows.
- **Voice:** `TTSProvider` NATIVE (OS voice), LOCAL (sherpa-onnx), GROQ, KOKORO (server, voice
  `am_fenrir`); `STTProvider` NATIVE, GROQ (Whisper), WHISPER (local). Check these first at CP11.
- **Config worth copying:** `requireVerity` (must say his name to talk to him), `immersiveMode`
  (hide UI and chat when voice is on), a karma bar HUD.

## Mapping to Machiavillager

| Verity | Ours |
|---|---|
| LLM called from the mod | Backend (Python, PydanticAI) decides; the mod validates and executes (ADR 0002, 0004) |
| XML prompt template | `persona/machiavillager.md` + `PromptBuilder` (fixed rules, persona, profanity, state block) |
| Secrets gated by karma | Persona sections gated by story phase (`## Phase: <name>`, only the current one is sent) |
| Karma 0-20 | Affinity per player and independence 0-100; ambition drives the phase |
| `@Tool` perception and actions in Java | PydanticAI tools in the backend that send an allowlisted bridge request; the mod answers with the data or runs the action |
| JSON memory files | Checkpoint 7 (#72): same two kinds, in one SQLite file per world (no install, no server): chat window, game events as system lines, model-managed memories marked `claimed` when a player said it |
| Proactive events with cooldowns | Mod events over the bridge, triggered with cooldowns, never per tick |
| Tool list trimming | Register only the tools that apply this turn (`npc-agent-tools`) |

When a Verity idea conflicts with our hard rules (`AGENTS.md`: the LLM never runs arbitrary
commands, never blocks the server tick), our rules win.
