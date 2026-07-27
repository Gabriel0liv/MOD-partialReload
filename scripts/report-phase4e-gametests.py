"""Parse the real Forge GameTest log into a safety-gate report.

The parser deliberately fails closed when the batch or completion summary is
missing; it never supplies guessed counts.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOG_CANDIDATES = (ROOT / "run" / "logs" / "latest.log", ROOT / "build" / "reports" / "gametest.log")
REPORT = ROOT / "build" / "reports" / "phase4e-tag-recipe-gametests.json"
BATCH = "phase4e-tag-recipe-transaction"
# The task's risk matrix requires ten transacted scenarios in addition to the
# three existing smoke tests.  This is a coverage floor, not a global test
# count requirement.
MIN_REQUIRED_BATCH_TESTS = 10


def parse(log: str) -> dict[str, object]:
    batch_seen = BATCH in log
    totals = re.search(r"=+\s*(\d+) GAME TESTS COMPLETE\s*=+", log)
    failed_match = re.search(r"(?:failed|Failures?)\s*[:=]\s*(\d+)", log, re.I)
    batch_runs = re.findall(r"Running test batch ['\"]?([^'\"]+)['\"]?\s*\((\d+) tests?\)", log, re.I)
    batch_total = sum(int(count) for name, count in batch_runs if name.startswith(BATCH))
    failed = int(failed_match.group(1)) if failed_match else (0 if totals and "failed" not in log.lower() else None)
    global_total = int(totals.group(1)) if totals else None
    complete = bool(totals and batch_seen and failed == 0 and batch_total >= MIN_REQUIRED_BATCH_TESTS)
    return {
        "status": "passed" if complete else "failed",
        "batch": BATCH,
        "global_total": global_total,
        "batch_total": batch_total,
        "passed": batch_total if complete else 0,
        "failed": failed,
        "tests": [{"batch": name, "count": int(count)} for name, count in batch_runs if name.startswith(BATCH)],
    }


def main() -> int:
    log_path = next((path for path in LOG_CANDIDATES if path.exists()), None)
    if log_path is None:
        report = {"status": "failed", "batch": BATCH, "global_total": None,
                  "batch_total": 0, "passed": 0, "failed": None, "tests": [],
        "error": "GameTest log not found"}
    else:
        report = parse(log_path.read_text(encoding="utf-8", errors="replace"))
        report["log"] = str(log_path)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
