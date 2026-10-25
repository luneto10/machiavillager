# shared

The contract between the Minecraft mod and the Python backend.

## Generated files: never edit by hand

The Pydantic models in `backend/src/machiavillager/domain/protocol/` are the source of truth. These
files are generated from them:

- `protocol.schema.json`: JSON Schema (draft 2020-12) for every message. The mod reads it too.
- `PROTOCOL.md`: the human-readable reference, rendered from the schema. Field descriptions come from the models.
- `examples/invalid.jsonl`: one line per invalid case, generated from the valid examples.

Regenerate all of them with:

```
cd backend
uv run python scripts/export_protocol_schema.py
```

`uv run python scripts/export_protocol_schema.py --check` fails when any generated file is stale or missing.
`backend/tests/test_protocol_contract.py` runs the same check, so CI catches a stale file. A
regeneration overwrites a hand edit, so change the model and regenerate instead.

## Examples

Humans write only valid examples:

- `examples/valid/`: one file per message shape. Each one must parse and validate.

Invalid cases are never written by hand. `backend/scripts/generate_invalid_examples.py` takes each
valid example and breaks one rule at a time: a missing required field, an unknown field, an explicit
null for an optional field, a wrong or unknown `type`, a wrong `v`, a value outside its length, pattern,
enum or range limits. A mutant is kept only when both the parser and the JSON Schema reject it.

Cases are deduplicated per (message type, field, rule). The message type is the Pydantic model
(`SpeakAction`, `PlayerChatEvent`...), and array items fold into one field (`args.lines.*`). Each line of
`invalid.jsonl` is `{"name": "<Model>.<field>.<rule>", "message": {...}}`, for example
`SpeakAction.args.lines.too_many_items` or `SpeakAction.root.unknown_field`.

To cover a new rule, put it in the model and regenerate. To cover a new message shape, add one valid
example and regenerate.

## Both sides enforce the same files

The backend tests in `backend/tests/test_protocol_contract.py` check every valid example against the
parser and the schema, and every case in `invalid.jsonl` against both. The Java tests in
`mod/src/test/.../bridge/ProtocolContractTest.java` run the same files through the decoder and the
schema. The mod validates each incoming message against `protocol.schema.json`, so a rule that lives in
the schema is enforced on both sides.

## Adding a message

Follow the `bridge-protocol` skill (`.agents/skills/bridge-protocol/SKILL.md`). Add the Pydantic model
first, with a description on every field. Then add one valid example, regenerate, and run the tests on
each side.
