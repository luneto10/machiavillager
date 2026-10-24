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

1. Copy `../.env.example` to `backend/.env` and set `MACHIAVILLAGER_BRIDGE_TOKEN` (generate it as
   the comment there shows).
2. Run `uv run uvicorn machiavillager.api.app:app --reload`.
3. The mod needs the same token (Checkpoint 4 #9). With no token set, the endpoint refuses every
   connection.

Layer rules live in [`docs/architecture.md`](../docs/architecture.md).
