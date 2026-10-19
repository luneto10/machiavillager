"""Mirror .agents/skills (Codex, source of truth) to .claude/skills (Claude Code).

    python scripts/sync_skills.py          # copy
    python scripts/sync_skills.py --check  # exit 1 if out of sync (used in CI)
"""

import filecmp
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / ".agents" / "skills"
TARGET = ROOT / ".claude" / "skills"


def differences(cmp: filecmp.dircmp) -> list[str]:
    found = [f"only in {cmp.left}: {n}" for n in cmp.left_only]
    found += [f"only in {cmp.right}: {n}" for n in cmp.right_only]
    found += [f"differs: {Path(cmp.left, n)}" for n in cmp.diff_files]
    for sub in cmp.subdirs.values():
        found += differences(sub)
    return found


if __name__ == "__main__":
    if "--check" in sys.argv:
        problems = differences(filecmp.dircmp(SOURCE, TARGET)) if TARGET.exists() else ["missing"]
        if problems:
            print("Skills out of sync. Run: python scripts/sync_skills.py", *problems, sep="\n")
            sys.exit(1)
        print("Skills in sync.")
    else:
        shutil.rmtree(TARGET, ignore_errors=True)
        shutil.copytree(SOURCE, TARGET)
        print(f"Copied {SOURCE} -> {TARGET}")
