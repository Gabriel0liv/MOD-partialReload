"""Safety-gate placeholder: fail closed until all 4E-S scenarios are automated.

This intentionally does not start a server or claim evidence. It provides a
stable report contract so the consolidated runner cannot accidentally promote
the gate using historical basic-commit results.
"""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.json"
SCENARIOS = [
    "pre_mutation", "partial_bind", "recipe_publication", "ingredient_invalidation",
    "tags_updated_event", "degraded", "player_request", "player_race",
    "tag_absent_restore", "tag_removal", "unsupported_registries",
]

def main() -> int:
    result = {name: {"status": "blocked", "reason": "dedicated safety scenario not automated"} for name in SCENARIOS}
    result["status"] = "CLIENT_SYNC_BLOCKED_BY_SERVER_SAFETY_GATE"
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_BLOCKED")
    print(f"Report: {REPORT}")
    return 2

if __name__ == "__main__":
    raise SystemExit(main())
