"""Generate shared/examples/invalid from the valid examples, so humans never write invalid ones.

Each file in shared/examples/valid is parsed to find its model. The model's JSON Schema says which
rules the message must follow, and one mutant is made per rule the message breaks on purpose:

- structural: drop each required field, add an unknown field to every object, explicit null for
  each optional field, type = "not_a_type", v (or protocol) = 2 and = true;
- boundaries: a string longer than maxLength and shorter than minLength (when minLength >= 1),
  a string that breaks its pattern, an array longer than maxItems and shorter than minItems
  (when minItems >= 1), an integer above maximum and below minimum;
- values: a const or enum that does not list the value used.

A mutant is written only when both parse_message and the bundled JSON Schema reject it. Any
other mutant is reported as skipped, with the reason, and is not written.

Do not edit the generated files. Regenerate with scripts/export_protocol_schema.py.
"""

import copy
import json
from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Final

from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError

from machiavillager.domain.protocol import PROTOCOL_VERSION, parse_message, protocol_json_schema

# This file sits in backend/scripts/, so two parents up is the repo root that holds shared/.
REPO_ROOT = Path(__file__).resolve().parents[2]
VALID_DIR = REPO_ROOT / "shared" / "examples" / "valid"
INVALID_DIR = REPO_ROOT / "shared" / "examples" / "invalid"

# A field no message defines. Adding it must be rejected everywhere (extra="forbid").
UNEXPECTED_FIELD: Final = "unexpected_field"
# A value no const or enum lists. Plain ASCII so it stays readable in the file.
UNKNOWN_VALUE: Final = "not_a_known_value"
# Not in the type enum of any message, so the discriminator must refuse it.
UNKNOWN_TYPE: Final = "not_a_type"
# Wire version values a valid message must not carry. Python sees True as 1, so both are tested.
WRONG_WIRE_NUMBER: Final = 2
WRONG_WIRE_BOOLEAN: Final = True
# Every pattern in the protocol allows only letters, digits, hyphens and underscores, so this fails.
PATTERN_BREAKER: Final = "!!"
TYPE_KEY: Final = "type"
ROOT_NAME: Final = "root"
# Name of the file that holds the generated mutants and their rules, one per file.
FILE_SUFFIX: Final = ".json"
JSON_INDENT: Final = 2


@dataclass(frozen=True)
class Mutation:
    """One change to a valid message: where it lands, which rule it breaks, and the new value."""

    # Path from the root to the node that changes. The last element is the key or the index.
    target: tuple[str | int, ...]
    # Path shown in the file name. Differs from target only for an unknown field.
    where: str
    rule: str
    value: Any = None
    delete: bool = False


@dataclass(frozen=True)
class GeneratedExamples:
    """The mutants that passed both checks, keyed by file name, plus the ones that were skipped."""

    files: dict[str, str]
    skipped: list[str]


def _where(path: tuple[str | int, ...]) -> str:
    return ".".join(str(part) for part in path) or ROOT_NAME


def _resolve(schema: dict[str, Any], root: dict[str, Any]) -> dict[str, Any]:
    # Pydantic puts nested models in $defs. A single-item allOf is how it wraps a $ref that has
    # a description, so both forms are followed here.
    if "$ref" in schema:
        name = schema["$ref"].rsplit("/", 1)[-1]
        return _resolve(root["$defs"][name], root)
    if "allOf" in schema and len(schema["allOf"]) == 1:
        return _resolve(schema["allOf"][0], root)
    return schema


def _is_wire_version(schema: dict[str, Any]) -> bool:
    # Only the protocol number is a const 1 integer. An int check excludes JSON booleans.
    const = schema.get("const")
    return type(const) is int and const == PROTOCOL_VERSION


def _object_mutations(
    node: dict[str, Any],
    schema: dict[str, Any],
    root: dict[str, Any],
    path: tuple[str | int, ...],
    out: list[Mutation],
) -> None:
    properties: dict[str, Any] = schema.get("properties", {})
    required: list[str] = schema.get("required", [])

    # Dropping a required key is the most direct way to break a message's shape.
    for key in required:
        if key in node:
            child = path + (key,)
            out.append(Mutation(child, _where(child), "drop_required", delete=True))

    # extra="forbid" on every model, so an unknown key must fail on every object.
    out.append(Mutation(path + (UNEXPECTED_FIELD,), _where(path), "unknown_field", True))

    for key, child_schema in properties.items():
        if key in node:
            _walk(node[key], child_schema, root, path + (key,), key, out)

    # An optional key set to null must fail, because the Python default is not a JSON null.
    for key in properties:
        if key not in required:
            child = path + (key,)
            out.append(Mutation(child, _where(child), "null_optional", None))


def _array_mutations(
    node: list[Any],
    schema: dict[str, Any],
    root: dict[str, Any],
    path: tuple[str | int, ...],
    out: list[Mutation],
) -> None:
    where = _where(path)
    max_items: int | None = schema.get("maxItems")
    min_items: int = schema.get("minItems", 0)

    # Repeating a valid item keeps every item valid, so only the count breaks the rule.
    if node and max_items is not None:
        out.append(Mutation(path, where, "too_many_items", [node[0]] * (max_items + 1)))
    if min_items >= 1:
        out.append(Mutation(path, where, "too_few_items", node[: min_items - 1]))

    item_schema: dict[str, Any] = schema.get("items", {})
    for index, item in enumerate(node):
        _walk(item, item_schema, root, path + (index,), None, out)


def _scalar_mutations(
    node: Any,
    schema: dict[str, Any],
    path: tuple[str | int, ...],
    name: str | None,
    out: list[Mutation],
) -> None:
    where = _where(path)

    if "const" in schema or "enum" in schema:
        if name == TYPE_KEY:
            out.append(Mutation(path, where, "type_not_a_type", UNKNOWN_TYPE))
        elif name is not None and _is_wire_version(schema):
            out.append(Mutation(path, where, f"{name}_is_2", WRONG_WIRE_NUMBER))
            out.append(Mutation(path, where, f"{name}_is_true", WRONG_WIRE_BOOLEAN))
        else:
            out.append(Mutation(path, where, "unknown_value", UNKNOWN_VALUE))
        return

    if isinstance(node, str):
        if "maxLength" in schema:
            out.append(Mutation(path, where, "too_long", "a" * (schema["maxLength"] + 1)))
        if schema.get("minLength", 0) >= 1:
            # The empty string is the shortest value a minLength of 1 or more must refuse.
            out.append(Mutation(path, where, "too_short", ""))
        if "pattern" in schema:
            out.append(Mutation(path, where, "pattern", PATTERN_BREAKER))
    elif isinstance(node, int) and not isinstance(node, bool):
        if "maximum" in schema:
            out.append(Mutation(path, where, "above_maximum", schema["maximum"] + 1))
        if "minimum" in schema:
            out.append(Mutation(path, where, "below_minimum", schema["minimum"] - 1))


def _walk(
    node: Any,
    schema: dict[str, Any],
    root: dict[str, Any],
    path: tuple[str | int, ...],
    name: str | None,
    out: list[Mutation],
) -> None:
    resolved = _resolve(schema, root)
    if isinstance(node, dict):
        _object_mutations(node, resolved, root, path, out)
    elif isinstance(node, list):
        _array_mutations(node, resolved, root, path, out)
    else:
        _scalar_mutations(node, resolved, path, name, out)


def _apply(document: dict[str, Any], mutation: Mutation) -> dict[str, Any]:
    # A deep copy, so the valid example that the mutant came from is never changed.
    mutant = copy.deepcopy(document)
    *parents, key = mutation.target
    container: Any = mutant
    for step in parents:
        container = container[step]
    if mutation.delete:
        del container[key]
    else:
        container[key] = copy.deepcopy(mutation.value)
    return mutant


def _rejection_problem(mutant: dict[str, Any], validator: Draft202012Validator) -> str | None:
    # Returns None only when both the parser and the schema refuse the mutant.
    try:
        parse_message(json.dumps(mutant))
    except ValidationError:
        pass
    else:
        return "parse_message accepts it"

    if not list(validator.iter_errors(mutant)):
        return "the JSON schema accepts it"
    return None


def generate_invalid_examples(valid_dir: Path = VALID_DIR) -> GeneratedExamples:
    """Build one mutant per rule for every valid example, and keep those both checks reject."""

    validator = Draft202012Validator(protocol_json_schema(), format_checker=FormatChecker())
    files: dict[str, str] = {}
    skipped: list[str] = []

    for example in sorted(valid_dir.glob("*.json")):
        raw = example.read_text(encoding="utf-8")
        document: dict[str, Any] = json.loads(raw)
        # The model that parse_message picked decides which schema branch the walk follows.
        model_schema = type(parse_message(raw)).model_json_schema()

        mutations: list[Mutation] = []
        _walk(document, model_schema, model_schema, (), None, mutations)

        for mutation in mutations:
            mutant = _apply(document, mutation)
            label = f"{example.stem}__{mutation.where}__{mutation.rule}"
            problem = _rejection_problem(mutant, validator)
            if problem is not None:
                skipped.append(f"{label}: {problem}, not written")
                continue
            file_name = label + FILE_SUFFIX
            if file_name in files:
                raise ValueError(f"two mutations share the file name {file_name}")
            files[file_name] = json.dumps(mutant, indent=JSON_INDENT, ensure_ascii=False) + "\n"

    return GeneratedExamples(files=dict(sorted(files.items())), skipped=skipped)


def count_by_rule(file_names: Iterable[str]) -> Counter[str]:
    """Count generated files per rule. The rule is the last part of the file name."""

    return Counter(name[: -len(FILE_SUFFIX)].rsplit("__", 1)[-1] for name in file_names)


def compare_dir(directory: Path, expected: dict[str, str]) -> list[str]:
    """List every way the directory differs from the expected files. An empty list means current."""

    actual = {path.name: path for path in directory.glob(f"*{FILE_SUFFIX}")}
    problems: list[str] = []
    for name in sorted(set(expected) - set(actual)):
        problems.append(f"missing {directory / name}")
    for name in sorted(set(actual) - set(expected)):
        problems.append(f"unexpected {actual[name]} (no valid example produces it)")
    for name in sorted(set(expected) & set(actual)):
        if actual[name].read_text(encoding="utf-8") != expected[name]:
            problems.append(f"stale {actual[name]}")
    return problems


def write_dir(directory: Path, files: dict[str, str]) -> None:
    """Replace the generated files: delete every old JSON file, then write the new set."""

    directory.mkdir(parents=True, exist_ok=True)
    for old in directory.glob(f"*{FILE_SUFFIX}"):
        old.unlink()
    for name, content in files.items():
        # Bytes, not write_text: on Windows write_text turns \n into \r\n, so files would differ.
        (directory / name).write_bytes(content.encode("utf-8"))
