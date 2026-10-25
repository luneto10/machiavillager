"""Regenerate the shared contract from the Pydantic models in the backend domain.

Run from backend/:
    uv run python scripts/export_protocol_schema.py

This writes shared/protocol.schema.json, shared/PROTOCOL.md and the generated files in
shared/examples/invalid/. Pass --check to exit 1 instead of writing when any of them is stale.
The test suite runs the same comparisons, so a stale file fails CI.
"""

import argparse
import json
import sys
import tempfile
from pathlib import Path

from generate_invalid_examples import (
    INVALID_DIR,
    VALID_DIR,
    compare_dir,
    count_by_rule,
    generate_invalid_examples,
    write_dir,
)
from json_schema_for_humans.generate import generate_from_file_object
from json_schema_for_humans.generation_configuration import GenerationConfiguration

from machiavillager.domain.protocol import protocol_json_schema

# This file sits in backend/scripts/, so two parents up is the repo root that holds shared/.
REPO_ROOT = Path(__file__).resolve().parents[2]
SHARED_DIR = REPO_ROOT / "shared"
SCHEMA_PATH = SHARED_DIR / "protocol.schema.json"
PROTOCOL_MD_PATH = SHARED_DIR / "PROTOCOL.md"

# The plain Markdown template. The footer is turned off in render_protocol_md: it carries a time.
MARKDOWN_TEMPLATE: str = "md"


def render_schema() -> str:
    """Serialize the schema the way the committed file is stored."""

    # The trailing newline keeps the file POSIX-friendly and stable under git diffs.
    return json.dumps(protocol_json_schema(), indent=2, sort_keys=False) + "\n"


def render_protocol_md() -> str:
    """Render the Markdown reference from the schema text, with no timestamps or footers."""

    # The library opens a schema given as a path and never closes it, which fails the test suite
    # under -W error. So both files are opened here, inside with-blocks, and handed over open.
    with tempfile.TemporaryDirectory() as scratch:
        schema_file = Path(scratch) / "protocol.schema.json"
        output_file = Path(scratch) / "PROTOCOL.md"
        schema_file.write_text(render_schema(), encoding="utf-8")
        with (
            schema_file.open(encoding="utf-8") as schema_fp,
            output_file.open("w", encoding="utf-8") as output_fp,
        ):
            generate_from_file_object(
                schema_fp,
                output_fp,
                config=GenerationConfiguration(
                    template_name=MARKDOWN_TEMPLATE,
                    with_footer=False,
                ),
            )
        # read_text turns the platform newline back into \n, so the result is the same on Windows.
        return output_file.read_text(encoding="utf-8")


def _stale(path: Path, expected: str) -> list[str]:
    current = path.read_text(encoding="utf-8") if path.exists() else ""
    if current == expected:
        return []
    return [f"{path} is stale"]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="fail if a generated file is stale")
    args = parser.parse_args(argv)

    schema_text = render_schema()
    markdown_text = render_protocol_md()
    generated = generate_invalid_examples(VALID_DIR)

    for skipped in generated.skipped:
        print(f"skipped {skipped}")

    if args.check:
        problems = _stale(SCHEMA_PATH, schema_text) + _stale(PROTOCOL_MD_PATH, markdown_text)
        problems += compare_dir(INVALID_DIR, generated.files)
        if problems:
            for problem in problems:
                print(
                    f"{problem}. Run: cd backend; uv run python scripts/export_protocol_schema.py",
                    file=sys.stderr,
                )
            return 1
        print("shared contract files are up to date")
        return 0

    # Bytes, not write_text: on Windows write_text turns \n into \r\n and the files would differ.
    SCHEMA_PATH.write_bytes(schema_text.encode("utf-8"))
    PROTOCOL_MD_PATH.write_bytes(markdown_text.encode("utf-8"))
    write_dir(INVALID_DIR, generated.files)

    print(f"wrote {SCHEMA_PATH}")
    print(f"wrote {PROTOCOL_MD_PATH}")
    print(f"wrote {len(generated.files)} invalid examples to {INVALID_DIR}")
    for rule, count in sorted(count_by_rule(generated.files).items()):
        print(f"  {rule}: {count}")
    print(f"skipped {len(generated.skipped)} mutations (see above)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
