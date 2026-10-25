# Machiavillager backend

FastAPI service that will host the NPC's brain: conversations, tools, memory, voice.

```bash
uv sync                                                     # create .venv, install deps
uv run uvicorn machiavillager.api.app:app --reload          # http://127.0.0.1:8000/health
uv run pytest                                               # tests (incl. onion layer rules)
uv run ruff check . && uv run ruff format --check .         # lint + format
```

Windows with Smart App Control: uv's own Python builds are blocked. Use a python.org
install instead: `uv venv --python "$env:LOCALAPPDATA\Programs\Python\Python313\python.exe"`.

## Bridge

The Minecraft mod connects to `ws://localhost:8000/bridge` (Checkpoint 4, issue #8).

1. Copy `.env.example` to `.env` at the repo root and set `MACHIAVILLAGER_BRIDGE_TOKEN` (generate it as
   the comment there shows). The backend and the mod both read this one file; `backend/.env` is a
   fallback only.
2. Run `uv run uvicorn machiavillager.api.app:app --reload`.
3. The mod reads the same root `.env` when `runClient` or `runServer` starts (issue #9). With no token
   set, the endpoint refuses every connection and the mod stays off.
4. In game, `/machiavillager bridge status` (operators) shows the link state.
5. Dev only: set `MACHIAVILLAGER_DEV_ENDPOINTS=true` in the root `.env` and restart the backend. Then open
   http://localhost:8000/docs, expand `POST /dev/speak` and send `{"lines": ["Hello there."]}`. The reply is
   the mod's final status (`succeeded`, `rejected`, `failed`, or `interrupted`). With no mod connected it
   answers 503. The endpoint does not exist unless the flag is set.

Layer rules live in [`docs/architecture.md`](../docs/architecture.md).
