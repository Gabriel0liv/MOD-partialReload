"""Optional Arclight 1.20.1 smoke; absence is reported without claiming compatibility."""
from __future__ import annotations
import json, os, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "arclight-loot-data-smoke.json"

def main() -> int:
    configured = os.environ.get("PARTIALRELOAD_ARCLIGHT_1_20_1")
    if not configured or not pathlib.Path(configured).is_file():
        result = {"status": "ARCLIGHT_SMOKE_NOT_EXECUTED", "reason": "runtime not configured"}
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print("ARCLIGHT_SMOKE_NOT_EXECUTED")
        return 0
    result = {"status": "ARCLIGHT_SMOKE_NOT_EXECUTED",
              "reason": "configured runtime requires a separately approved disposable launch contract"}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("ARCLIGHT_SMOKE_NOT_EXECUTED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
