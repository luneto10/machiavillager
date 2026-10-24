"""Regenerate shared/protocol.schema.json from the Pydantic models in the backend domain.

Run from backend/:
    uv run python scripts/export_protocol_schema.py

Pass --check to exit 1 instead of writing when the committed file is stale. The test suite
runs the same comparison, so a stale file fails CI.
"""

import argparse
import json
import sys
from pathlib import Path

from machiavillager.domain.protocol import protocol_json_schema

# This file sits in backend/scripts/, so two parents up is the repo root that holds shared/.
REPO_ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = REPO_ROOT / "shared" / "protocol.schema.json"


def render_schema() -> str:
    """Serialize the schema the way the committed file is stored."""

    # The trailing newline keeps the file POSIX-friendly and stable under git diffs.
    return json.dumps(protocol_json_schema(), indent=2, sort_keys=False) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="fail if the file is stale")
    args = parser.parse_args(argv)

    expected = render_schema()

    if args.check:
        current = SCHEMA_PATH.read_text(encoding="utf-8") if SCHEMA_PATH.exists() else ""
        if current != expected:
            print(
                f"{SCHEMA_PATH} is stale. Run: cd backend; uv run python "
                "scripts/export_protocol_schema.py",
                file=sys.stderr,
            )
            return 1
        print(f"{SCHEMA_PATH} is up to date")
        return 0

    # Bytes, not write_text: on Windows write_text turns \n into \r\n and the file would differ.
    SCHEMA_PATH.write_bytes(expected.encode("utf-8"))
    print(f"wrote {SCHEMA_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
