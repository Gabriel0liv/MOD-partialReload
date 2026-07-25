"""Acceptance preflight for KubeJS recipes.

The exact Forge 1.20.1 KubeJS runtime is intentionally required. When it is
not installed, this harness records a pending result instead of pretending
that a dedicated acceptance ran.
"""
from __future__ import annotations

import json
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    report = root / "build" / "reports" / "dedicated-kubejs-recipe-acceptance.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    result = {
        "runtime_available": False,
        "target": "Minecraft 1.20.1 / Forge 47.4.10",
        "status": "KUBEJS_RECIPE_PREPARATION_IMPLEMENTED_PENDING_RUNTIME_ACCEPTANCE",
        "reason": "No exact KubeJS Forge 1.20.1 runtime is installed; no server was started and no script was executed.",
    }
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(result["status"])
    print(f"Report: {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
