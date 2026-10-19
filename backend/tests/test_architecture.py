"""Enforces the onion rule: a layer may import only itself and layers further inside."""

import ast
from pathlib import Path

PACKAGE = Path(__file__).parents[1] / "src" / "machiavillager"
LAYERS = ["domain", "application", "infrastructure", "api"]  # inner -> outer
# Third-party frameworks that must stay out of the inner layers.
FORBIDDEN_IN_CORE = {"fastapi", "starlette", "sqlalchemy", "pydantic_ai", "httpx", "uvicorn"}


def _imports(path: Path) -> set[str]:
    names: set[str] = set()
    for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
        if isinstance(node, ast.Import):
            names.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
            names.add(node.module)
    return names


def test_layers_only_import_inward():
    violations = []
    for rank, layer in enumerate(LAYERS):
        outer = LAYERS[rank + 1 :]
        for path in (PACKAGE / layer).rglob("*.py"):
            for name in _imports(path):
                parts = name.split(".")
                if parts[0] == "machiavillager" and len(parts) > 1 and parts[1] in outer:
                    violations.append(f"{path.relative_to(PACKAGE)} imports {name}")
                if layer in ("domain", "application") and parts[0] in FORBIDDEN_IN_CORE:
                    violations.append(f"{path.relative_to(PACKAGE)} imports framework {name}")
    assert not violations, "\n".join(violations)
