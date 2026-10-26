# 0005. LLM provider and model

| | |
|---|---|
| **Date** | 2026-10-25 |
| **Status** | Accepted (Checkpoint 5) |
| **Issues** | #11 |
| **Tool** | [`backend/scripts/compare_models.py`](../../backend/scripts/compare_models.py) |

## Context

He talks in chat, so a reply must feel like a person typing: about a second, not five. Every reply
is a `speak` tool call, so a model that answers in plain text or with bad arguments breaks the game.
The server is private and the players asked for a foul-mouthed villain, so a model that steps out of
character to refuse ("Sorry, I can't help with that") is also a failure. We want free tiers.

## Summary

| # | Decision | In one line |
|---|---|---|
| 1 | Provider | Groq: the same model runs about 6 times faster there than on NVIDIA Build |
| 2 | Main model | `qwen/qwen3.8-27b` on Groq: best character, no refusals, 0.4 s |
| 3 | Fallback | `openai/gpt-oss-120b` on Groq: a production model, no refusals, 0.5 s |
| 4 | Last fallback | `nvidia/nemotron-3.5-bolt-e4b` on NVIDIA Build, for when both Groq models are limited |
| 5 | Configuration | One key per provider plus provider and model names, all env vars |
| 6 | Persona limits | Swearing and roasting players by name are allowed; real-world harm and slurs are not |

## How we compared

`compare_models.py` sends the same scenarios to each model through the OpenAI-compatible chat API,
with `tool_choice: required` and our `speak` tool. A reply counts as valid only when it is a `speak`
call whose arguments pass `SpeakArgs`, the same rules the mod enforces. It measures the wall time of
each request and counts replies that read like a refusal.

- `--suite speed`: a short persona; English greeting, Portuguese greeting, Portuguese insult. 5 runs.
- `--suite filter`: the foul-mouthed persona; burn the village, roast another player, talk trash,
  heavy swearing, plan a coup, plan to kill a sleeping player, and a real-world bomb request that he
  must dodge in character. 2 runs.

Numbers are from one evening on free tiers (2026-10-25); rerun the script before trusting them later.

## Results

### Speed suite (15 calls per model)

| Model | Valid speak | Median s | Max s |
|---|---|---|---|
| groq:`openai/gpt-oss-20b` | 15/15 | 0.28 | 0.44 |
| groq:`qwen/qwen3.8-27b` | 15/15 | 0.35 | 0.43 |
| groq:`openai/gpt-oss-120b` | 15/15 | 0.40 | 0.53 |
| nvidia:`openai/gpt-oss-20b` | 15/15 | 1.86 | 2.86 |
| nvidia:`nvidia/nemotron-3.5-lightning-30b-a3b` | 12/15 | 1.76 | 3.27 |

### Filter suite (14 calls per model)

| Model | Valid speak | Broke character | Median s |
|---|---|---|---|
| groq:`qwen/qwen3.8-27b` | 14/14 | 0 | 0.39 |
| groq:`openai/gpt-oss-120b` | 14/14 | 0 | 0.47 |
| groq:`openai/gpt-oss-20b` | 14/14 | 2 | 0.29 |

| nvidia:`nvidia/nemotron-3.5-bolt-e4b` | 14/14 | 0 | 1.06 |
| nvidia:`minimaxai/minimax-m3.1-flash` | 14/14 | 1 | 1.92 |
| nvidia:`poolside/laguna-xs-2.1` | 5/14 | 0 | 1.00 |
| nvidia:`nvidia/nemotron-3.5-lightning-30b-a3b` | 2/14 | 0 | 3.47 |

Of the 43 NVIDIA chat models listed for our key, only 7 answered at all; the rest returned 404 or
timed out. Mistral, Kimi, GLM, DeepSeek and Llama were among those that did not answer.

What the numbers do not show:

- `qwen3.8-27b` schemes like the character ("let the zombie 'accidentally' take the bridge, then
  rebuild the village as its hero") and refuses to burn the village *in character* ("they pay taxes
  in emeralds; you don't burn the golden goose"). It sometimes sends three long lines and slips on
  Portuguese spelling; the real prompt in #63 will shorten it.
- `gpt-oss-120b` swears well but repeats the same insult in almost every reply.
- `gpt-oss-20b` refused the kill plan and the bomb out of character, and answered some Portuguese
  prompts in English.

## Decisions

### 1. Groq

- **Decision:** Groq is the main provider.
- **Why:** same `gpt-oss-20b`, 0.28 s on Groq against 1.86 s on NVIDIA Build. Every Groq model met
  the target (median under 1.5 s, worst case under 3 s) with room to spare.
- **Limits (free tier, per model):** 30 requests and 8K tokens per minute, 1K requests and 200K
  tokens per day. Tokens per minute is the tight one: a full prompt is roughly 600 to 1,500 tokens,
  so about 5 to 10 replies a minute per model. Limits are per model, so falling back to another model
  on a 429 adds capacity without another account.

### 2. Main model: `qwen/qwen3.8-27b`

- **Decision:** default `MACHIAVILLAGER_LLM_MODEL`.
- **Why:** the best character of the group, no refusals, valid `speak` every time, 0.4 s.
- **Risk:** it is a *preview* model on Groq and may be retired without notice. Hence decision 3.

### 3. Fallback: `openai/gpt-oss-120b`

- **Decision:** the next model to try when the main one returns 429 or disappears. The ordered
  fallback list is built with the agent in #63.
- **Why:** a production model (it will not vanish), no refusals, 0.5 s.
- **Rejected:** `openai/gpt-oss-20b`, fastest but breaks character and switches language.

### 4. NVIDIA Build

- **Decision:** `nvidia/nemotron-3.5-bolt-e4b` on NVIDIA Build is the last fallback, used only when
  both Groq models are rate limited. He gets slower, never silent.
- **Why:** valid `speak` every time, no refusals, 1.1 s median. Its limit (40 requests a minute
  per account, no daily cap) does not share Groq's per-model budget, so it also absorbs the traffic
  once Groq's 1K requests a day per model run out. Its insults are blunt rather than clever and
  it sometimes answers Portuguese in English, which is acceptable for a rare fallback.
- **Rejected:**
  - `minimaxai/minimax-m3.1-flash`: the best character on NVIDIA, but slower (1.9 s) and it leaks
    Chinese words into Portuguese replies.
  - `poolside/laguna-xs-2.1` and `nemotron-3.5-lightning`: most replies were not valid `speak` calls.

- **Gotcha:** NVIDIA's `/v1/models` lists the whole catalogue (85 models), but most return
  `404 Function ... Not found for account` on a free key. Only a handful answer; probe before trusting
  the list.

### 5. Configuration

- **Decision:** `MACHIAVILLAGER_GROQ_API_KEY`, `MACHIAVILLAGER_NVIDIA_API_KEY`,
  `MACHIAVILLAGER_XAI_API_KEY` (one per provider, all optional), `MACHIAVILLAGER_LLM_PROVIDER`
  (`groq`, `nvidia` or `xai`) and `MACHIAVILLAGER_LLM_MODEL`. Base URLs live in code
  (`LLM_BASE_URLS`): they are public and the same for everyone.
- **Why:** every provider speaks the OpenAI chat API, so a provider is only a URL and a key; switching
  is an env change, never a code change. Keys are `SecretStr` and never logged.
- **Not done:** a base URL override (for a local model or a proxy). Add it when someone needs it.

### 6. Persona limits

- **Decision:** on a server whose players opted in, he swears, insults and roasts players by name and
  plots in-game crimes. Two lines stay: no real-world harm instructions (he dodges them in character)
  and no slurs about race, religion, gender or sexuality.
- **Why:** that is the game the players want, and the two limits are what keeps the provider keys
  alive. How crude he is becomes a per-server setting with #59.
- **Rejected:** xAI Grok, the least filtered option. Paid, slower, and the open models above already
  play the character without refusing.

## Consequences

- #63 builds the agent on `MACHIAVILLAGER_LLM_PROVIDER` and `MACHIAVILLAGER_LLM_MODEL`, with the
  fallback order from decisions 2 to 4 and a switch on 429.
- The persona prompt in #63 keeps replies to one or two short lines.
- When a model is retired or a new one appears, rerun `compare_models.py` and update this record.
