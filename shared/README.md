# shared

The contract between the Minecraft mod and the Python backend.

## Generated files: never edit by hand

The Pydantic models in `backend/src/machiavillager/domain/protocol.py` are the source of truth. These
files are generated from them:

- `protocol.schema.json`: JSON Schema (draft 2020-12) for every message. The mod reads it too.
- `PROTOCOL.md`: the human-readable reference, rendered from the schema. Field descriptions come from the models.
- `examples/invalid/`: one file per broken rule, generated from the valid examples.

Regenerate all of them with:

```
cd backend
uv run python scripts/export_protocol_schema.py
```

`uv run python scripts/export_protocol_schema.py --check` fails when any generated file is stale.
`backend/tests/test_protocol_contract.py` runs the same check, so CI catches a stale file. A
regeneration overwrites a hand edit, so change the model and regenerate instead.

## Examples

Humans write only valid examples:

- `examples/valid/`: one file per message shape. Each one must parse and validate.

Invalid examples are never written by hand. `backend/scripts/generate_invalid_examples.py` takes each
valid example and breaks one rule at a time: a missing required field, an unknown field, an explicit
null for an optional field, a wrong or unknown `type`, a wrong `v`, a value outside its length, pattern,
enum or range limits. A generated file is written only when both the parser and the JSON Schema reject
it. The file name gives the field, and the rule.

To cover a new rule, put it in the model and regenerate. To cover a new message shape, add one valid
example and regenerate.

## Both sides enforce the same files

The backend tests in `backend/tests/test_protocol_contract.py` check every valid example against the
parser and the schema, and every generated invalid example against both. The mod validates each
incoming message against `protocol.schema.json`, so a rule that lives in the schema is enforced on both
sides.

## Adding a message

Follow the `bridge-protocol` skill (`.agents/skills/bridge-protocol/SKILL.md`). Add the Pydantic model
first, with a description on every field. Then add one valid example, regenerate, and run the tests on
each side.
