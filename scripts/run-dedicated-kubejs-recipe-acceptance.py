"""Fail-closed research gate for KubeJS recipes.

The exact 2001 runtime was audited, but it cannot stage edited scripts without
mutating process-wide managers/listeners.  This script records that architectural
stop gate and deliberately does not start a server or call KubeJS.
"""
from __future__ import annotations

import json
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    report = root / "build" / "reports" / "dedicated-kubejs-recipe-acceptance.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    result = {
        "runtime_audited": True,
        "server_started": False,
        "scripts_executed": False,
        "target": "Minecraft 1.20.1 / Forge 47.4.10",
        "kubejs_version": "2001.6.5-build.26",
        "rhino_version": "2001.2.2-build.17",
        "architectury_version": "9.1.12",
        "comparison_builds": [
            "2001.6.5-build.16",
            "2001.6.5-build.24",
            "2001.6.5-build.26",
        ],
        "status": "KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE",
        "commit_implemented": False,
        "reason": (
            "ScriptType.SERVER resolves the active ServerScriptManager and "
            "ServerEvents stores process-wide listeners; loading edited scripts "
            "would clear or replace active runtime state."
        ),
        "evidence": {
            "active_manager_unchanged": True,
            "active_event_handlers_unchanged": True,
            "active_recipe_manager_unchanged": True,
            "functional_acceptance_executed": False,
        },
    }
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(result["status"])
    print(f"Report: {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
