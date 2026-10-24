# shared

The contract between the Minecraft mod and the Python backend.

- `protocol.schema.json`: JSON Schema (draft 2020-12) for every message. Source of truth.
- `examples/valid/`: messages that must validate.
- `examples/invalid/`: messages that must fail. Each file breaks exactly one rule, named by the file.

Both test suites read these files: the backend (`backend/tests/test_protocol_contract.py`) and
the mod (`mod/src/test/java/.../bridge/ProtocolContractTest.java`). A rule that only one side
tests is a bug.

## Adding a message

Follow the `bridge-protocol` skill (`.agents/skills/bridge-protocol/SKILL.md`). Add the schema
branch first, then one valid and one invalid example per new rule, then the tests on each side.
