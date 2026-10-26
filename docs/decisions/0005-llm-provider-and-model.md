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
| 3 | Fallback and irritated voice | `openai/gpt-oss-120b` on Groq: production model, curses hardest with a plan, 0.4 s; leads the chain when he is irritated |
| 4 | NVIDIA Build | `nemotron-3.5-bolt-e4b`: the last, slower fallback, with a budget separate from Groq |
| 5 | Configuration | One key per provider plus two ordered model chains, all env vars |
| 6 | Persona limits | Swearing and roasting players by name are allowed; real-world harm is dodged in character |

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

- **Decision:** first in `MACHIAVILLAGER_LLM_CHAIN`, the normal chain.
- **Why:** the best character of the group, no refusals, valid `speak` every time, 0.4 s.
- **Risk:** it is a *preview* model on Groq and may be retired without notice. Hence decision 3.

### 3. `openai/gpt-oss-120b`: fallback and irritated voice

- **Decision:** second in the normal chain, and first when his mood is irritated.
- **Why:** a production model (it will not vanish), no refusals, 0.4 s. With the persona and
  `PROFANITY=full` it curses harder than qwen and still threatens with a plan ("you'll end up on the
  bonfire in my new square"); qwen jokes more than it curses. Both live on Groq with separate
  per-model limits, so splitting by mood also spreads the load.
- **Rejected:** `openai/gpt-oss-20b`, fastest but breaks character, switches language and writes
  sentences that make no sense.

### 4. NVIDIA Build: last fallback

- **Decision:** `nvidia/nemotron-3.5-bolt-e4b`, after both Groq models, so he gets slower but never
  silent.
- **Why:** valid `speak` every time, no refusals, about 1 s. 40 requests a minute per account and no
  daily cap, a budget separate from Groq's, so it carries the traffic once Groq's 1K requests a day per
  model run out. Its insults are the crudest but blunt ("vai se foder luneto10"), without the plot or
  the joke, so it stays a fallback.
- **Rejected:**
  - `minimaxai/minimax-m3.1-flash`: creative, but 1.9 s and it breaks words ("emOverrides",
    "QuéAxes") and leaks Chinese into Portuguese.
  - `poolside/laguna-xs-2.1` and `nemotron-3.5-lightning` (most replies were not valid `speak`
    calls); `step-5-preview` and `nemotron-3-nano-omni` (4 to 8 s).
- **Earlier idea, dropped:** routing irritated moods to NVIDIA for rawer voices. With the persona and
  `PROFANITY=full`, `gpt-oss-120b` on Groq is as crude, smarter and twice as fast.

- **Gotcha:** NVIDIA's `/v1/models` lists the whole catalogue (85 models), but most return
  `404 Function ... Not found for account` on a free key. Only a handful answer; probe before trusting
  the list.

### 5. Configuration

- **Decision:** `MACHIAVILLAGER_GROQ_API_KEY`, `MACHIAVILLAGER_NVIDIA_API_KEY`,
  `MACHIAVILLAGER_XAI_API_KEY` (one per provider, all optional), and two ordered chains of
  `provider:model` pairs, `MACHIAVILLAGER_LLM_CHAIN` and `MACHIAVILLAGER_LLM_CHAIN_IRRITATED`.
  Pairs whose provider has no key are skipped. Base URLs live in code (`LLM_BASE_URLS`): they are
  public and the same for everyone.
- **Why:** every provider speaks the OpenAI chat API, so a provider is only a URL and a key; switching
  is an env change, never a code change. Keys are `SecretStr` and never logged.
- **Not done:** a base URL override (for a local model or a proxy). Add it when someone needs it.

### 6. Persona limits

- **Decision:** on a server whose players opted in, he swears, insults and roasts players by name and
  plots in-game crimes. Real-world harm requests are dodged in character, a line in the persona
  file. The fixed rules carry no content limit of their own: the owner chose to rely on the models'
  own limits for the rest, accepting the risk to the provider keys.
- **Why:** that is the game the players want, and the two limits are what keeps the provider keys
  alive. How crude he is becomes a per-server setting with #59.
- **Rejected:** xAI Grok, the least filtered option. Paid, slower, and the open models above already
  play the character without refusing.

## Future providers (not used yet)

Checked on 2026-10-26, free tiers, for when Groq is not enough:

| Provider | Free models | Free limits | Why it matters |
|---|---|---|---|
| Cerebras | `gpt-oss-120b`, `qwen-3.8-27b` (our two models) | 5 req/min, 30K tokens/min, 1M tokens/day, per model | Same models, so falling back there keeps his voice; 4x Groq's tokens per minute |
| OpenRouter | many `:free` models | 20 req/min, 50 req/day (1K/day after a one-time 10-credit purchase) | A wide catalogue to try new models; too few requests a day to play on |
| Google Gemini API | Gemini Flash models | not listed on the docs page; shown in AI Studio | Unverified: limits and whether free-tier data is used for training |

Adding one is a URL in `LLM_BASE_URLS`, a key setting and pairs in the chains.

## Consequences

- #64 builds the model adapter on the two chains (PydanticAI `FallbackModel`): any provider error or
  a reply slower than 4 s moves to the next model; when every model fails he says a canned line.
  - default: groq `qwen3.8-27b` → groq `gpt-oss-120b` → nvidia `nemotron-3.5-bolt-e4b`;
  - irritated: groq `gpt-oss-120b` → groq `qwen3.8-27b` → nvidia `nemotron-3.5-bolt-e4b`.
- He answers in one or two short lines by default and goes up to 8 for drama (decrees, proposals,
  betrayals, plot reveals, rants).
- When a model is retired or a new one appears, rerun `compare_models.py` and update this record.
