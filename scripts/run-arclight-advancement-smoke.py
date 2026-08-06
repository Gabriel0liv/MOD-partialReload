"""Optional Arclight advancement smoke; absence never implies compatibility."""
from __future__ import annotations

import json
import os
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "arclight-advancement-smoke.json"


def main() -> int:
    runtime = os.environ.get("PARTIALRELOAD_ARCLIGHT_RUNTIME")
    if not runtime:
        result = {
            "status": "ARCLIGHT_ADVANCEMENT_SMOKE_NOT_EXECUTED",
            "compatibility_claimed": False,
            "reason": "PARTIALRELOAD_ARCLIGHT_RUNTIME is not configured",
        }
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print("ARCLIGHT_ADVANCEMENT_SMOKE_NOT_EXECUTED")
        return 0
    result = {
        "status": "ARCLIGHT_ADVANCEMENT_SMOKE_NOT_EXECUTED",
        "compatibility_claimed": False,
        "reason": "configured runtime requires an explicit disposable-server adapter",
        "runtime": str(pathlib.Path(runtime).resolve()),
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("ARCLIGHT_ADVANCEMENT_SMOKE_NOT_EXECUTED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
