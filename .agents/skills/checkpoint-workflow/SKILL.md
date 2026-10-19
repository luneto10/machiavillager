---
name: checkpoint-workflow
description: Use when starting, continuing, or closing a roadmap checkpoint (GitHub milestone "Checkpoint N") — planning the work, teaching the owner, verifying acceptance criteria, committing, opening the PR, and updating issues.
---

# Checkpoint workflow

The owner is learning. Each checkpoint follows steps A–G and stops for confirmation.

## A. Explain
What we build, why, and where it sits in `docs/architecture.md`. Short.

## B. Plan
- `gh issue list --milestone "Checkpoint N"` to load the scope and acceptance criteria.
- List files to touch, new dependencies (verify current version + maintenance first), risks.
- Create a branch: `git switch -c cpN-short-name`.

## C. Implement
Smallest working increment per issue. Follow `onion-architecture`, `fabric-mod-dev`,
`bridge-protocol`, `npc-agent-tools` as relevant. Explain non-obvious code as you go.

## D. Test
- Automated: `cd backend && uv run pytest`, `cd mod && ./gradlew build` (and `./gradlew test` once tests exist).
- Manual: exact steps the owner runs (`runClient`, commands to type, expected log lines).
- **Never** mark a criterion done without evidence (test output, log line, screenshot).
  If something couldn't be verified, say so explicitly.

## E. Review
Check: tick-thread blocking, secrets, layer violations, unvalidated input from the LLM or players,
needless complexity. Use the review agents (`onion-reviewer`, `mod-thread-safety-reviewer`).

## F. Commit
Conventional commits, small and meaningful. No AI attribution lines.
`git push -u origin cpN-short-name` then `gh pr create --fill --milestone "Checkpoint N"`,
body links issues with `Closes #n`.

## G. Checkpoint summary
What works · what was tested (with evidence) · what remains · what we learned · what's next.
Then **stop** and wait for the owner to confirm before starting checkpoint N+1.
