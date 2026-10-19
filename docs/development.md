# Development guide

## Toolchain

| Tool | Version | Why |
|---|---|---|
| JDK | **25** (Eclipse Temurin) | Minecraft 26.x requires Java 25 |
| IntelliJ IDEA | latest | Gradle import, debugger, HotSwap. Add the *Minecraft Development* plugin. |
| Python | **3.13** | Mature wheel support for all planned deps |
| uv | latest | Venvs, lockfile, fast installs |
| Git + GitHub CLI | latest | `gh` for repo, issues, PRs |

### Install on Windows

```powershell
winget install EclipseAdoptium.Temurin.25.JDK
winget install JetBrains.Toolbox
```

Install **IntelliJ IDEA** from Toolbox (the free tier of the unified IDE; the old Community
winget package is frozen at 2025.2).

Then open a new terminal and check `java -version` shows 25.

**Smart App Control** (Windows 11) blocks uv-downloaded Python builds and some Rust-compiled
wheels. Build the venv on a python.org install:
`uv venv --python "$env:LOCALAPPDATA\Programs\Python\Python313\python.exe"`.

## Backend

```bash
cd backend
uv sync
uv run uvicorn machiavillager.api.app:app --reload   # http://127.0.0.1:8000/health, docs at /docs
uv run pytest
uv run ruff check . && uv run ruff format --check .
```

`--reload` restarts the server on every save. No Minecraft restart is needed for backend changes.

## Mod

```bash
cd mod
./gradlew build          # compile + jar in build/libs/
./gradlew runClient      # dev Minecraft client (offline account "Player")
./gradlew runServer      # dev dedicated server; first run: set eula=true in mod/run/eula.txt
```

Dev client and server write worlds and configs into `mod/run/` (git-ignored).
For a dev server, set `online-mode=false` in `mod/run/server.properties` so the dev client can join.

### IntelliJ

1. *File → Open* → select `mod/` (not the repo root) → trust the Gradle project.
2. *Settings → Build Tools → Gradle*: Gradle JVM = Temurin 25.
3. Loom generates run configs (`Minecraft Client`, `Minecraft Server`). Use the **Debug**
   button to get breakpoints and HotSwap.

### What needs a restart

| Change | Restart? |
|---|---|
| Method body edits | No: *Build → Recompile*, HotSwap applies it (debug mode) |
| New/removed methods, fields, classes; signature changes | Yes, restart the client/server |
| `fabric.mod.json`, mixins, new entities/registrations | Yes |
| Textures/lang files | `F3+T` reloads resource packs in client |
| Backend Python | No Minecraft restart; uvicorn reloads |
| Personality/prompts (Checkpoint 7) | No restart at all, read from DB per conversation |

## AI coding assistants

- `AGENTS.md` is the shared instruction file (Codex reads it; `CLAUDE.md` imports it).
- Skills live in `.agents/skills/` (Codex) and `.claude/skills/` (Claude Code). They must stay
  identical: edit `.agents/skills/`, then run `python scripts/sync_skills.py`. CI checks it.
- Review agents: `.claude/agents/*.md` and `.codex/agents/*.toml`.
