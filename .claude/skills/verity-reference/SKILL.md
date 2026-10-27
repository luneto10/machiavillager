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

## What Verity does about relationship, insults, identity and cadence (studied 2026-10-26)

Ideas only for verity-JE; verity-extended is MIT. Paths are repo-relative.

- **Karma** (verity-JE `entity/LLM/actions/Tools.java`, `event/ModEvents.java`): one global float 0-20 saved
  with the world. The MODEL changes it through tools (get, set, +1, -1) when the player is kind or rude;
  code also lowers it on damage (about -2) and by loneliness (-1 per ~150 s away). No per-turn cap, no
  cooldown. Disclosure gates by karma: low never reveals secrets, middle hints, high tells.
  verity-extended (`compat/KarmaDailyCap.java`, `mixin/VerityKarmaCapMixin.java`) adds a hard daily cap on
  GAINS only, through the one function every change passes. Ours is stronger: bounded suggestion, mod caps,
  separate rise and drop waits, a rise soon after a drop at most half of it. Lesson kept: a single choke
  point for every affinity change.
- **Insults:** no ladder. Forgiveness needs begging: verity-JE `event/VerityPleadingHandler.java` counts
  apologies (3 needed). Adopt later: an apology counter and an irritation count (rude turns in a window)
  that pick the register text (joke, ask to stop, curt, silent).
- **Identity** (verity-JE `prompts/verity.xml`): never a named model, vendor, assistant or chatbot; asked
  "are you an AI", answer in character and never explain it is software. Ours: a "Who you are" persona
  section. Keep the answer playful, never a long sincere lie.
- **Cadence** (verity-extended `client/VerityAwarenessManager.java`, `ai/AiEventThrottle.java`):
  event-driven proactive lines with a priority per event, one global cooldown (default 300 s), ONE request
  in flight and a single deferred slot that only a higher priority replaces, edge-triggered state so events
  do not repeat, a quiet window after combat, suppressed events merged into the next line ("N more times").
  Reply length is short by default; stories only when asked. Dedup of identical text within 10 s;
  no repeating the same sentence twice in a row.
- **Personality content:** only fixed lists of likes, dislikes, lore and secrets. No random "on his mind"
  topic injection: that would be ours (a rotating daily-life line with a no-repeat window).
- **Memory** (verity-extended `memory/ConversationContext.java`, `ai/ContextTrimmer.java`,
  `proxy/PromptInjections.java`): last 12 turns verbatim, older turns in a rolling summary (~800 chars,
  refreshed at most once per 60 s, off the request path), old tool output cut hard, changing context put at
  the TAIL of the prompt so the static prefix stays cacheable.
- **Silence:** not a model choice anywhere; it comes from code (cooldowns, quiet windows, dedup, in-flight
  guard). Ours to add: a sulk (empty reply) at low affinity after repeated insults.
