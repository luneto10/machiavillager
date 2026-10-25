# shared

The contract between the Minecraft mod and the Python backend.

- `protocol.schema.json`: JSON Schema (draft 2020-12) for every message. **Generated, do not edit
  by hand.** The source of truth is the Pydantic models in
  `backend/src/machiavillager/domain/protocol.py`. Regenerate with:

  ```
  cd backend
  uv run python scripts/export_protocol_schema.py
  ```

  `uv run python scripts/export_protocol_schema.py --check` fails when the file is stale.
  `backend/tests/test_protocol_contract.py` runs the same check, so CI catches a stale file.
- `examples/valid/`: messages that must validate. One per message shape.
- `examples/invalid/`: messages that must fail. Business rules only (id format, line length,
  line count, reasons). Each file breaks exactly one rule, named by the file.

Structural rules (unknown fields, missing or wrong `v`, missing or unknown `type`) are not
examples. Both test suites generate mutants of every valid example and require them to fail:
the backend in `backend/tests/test_protocol_contract.py`, the mod in
`mod/src/test/java/.../bridge/ProtocolContractTest.java`. A rule that only one side enforces is a
bug.

## Adding a message

Follow the `bridge-protocol` skill (`.agents/skills/bridge-protocol/SKILL.md`). Add the Pydantic
model first, regenerate the schema, then add one valid example and one invalid example per new
business rule. Then run the tests on each side.
