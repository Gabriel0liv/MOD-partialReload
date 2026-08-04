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
LOOT_BATCH = "phase4g-loot-transaction"
REQUIRED_LOOT_BATCH_TOTAL = 24
GLM_BATCH = "phase4h-glm-transaction"
REQUIRED_GLM_BATCH_TOTAL = 24
# The task's risk matrix requires ten transacted scenarios in addition to the
# three existing smoke tests.  This is a coverage floor, not a global test
# count requirement.
REQUIRED_TRANSACTIONAL_TESTS = {
    "successfulCommitPublishesGenerationB", "manualRollbackRestoresGenerationA",
    "playerPresentAtRequestIsRejected", "playerRaceAtSafePointFailsSafe",
    "beforeFirstTagBindFailsSafe", "afterRecipePublicationRollsBack",
    "beforeRollbackVerificationDegrades", "duringRollbackDegrades",
    "tagLifecyclePreservesMissingEmptyAndRemoved", "unsupportedRegistryFailsSafe",
}
REQUIRED_SMOKE_TESTS = {"forgeWrapperIsRecognized", "registryIdentityIsStable", "defaultPlayerProbeUsesRealServerList"}
REQUIRED_DEFERRED_TESTS = {
    "normalCommandRemainsBlockedWithPlayer",
    "deferredCommitClosesMenusMarksStaleAndPublishesImmediately",
    "menuCloseFailurePreventsFirstMutation",
    "loginLogoutAndPostCommitJoinClearStale",
    "deferredSafePointCapturesPlayerWhoJoinedAfterInitialPreflight",
    "deferredSafePointOmitsPlayerWhoLeftAfterInitialPreflight",
    "automaticRollbackDoesNotMarkStale",
    "degradedDeferredCommitIsNeverReportedAsSuccess",
    "deferredWithoutPlayersSucceedsWithZeroStale",
    "safePointIsIdempotentAfterDeferredSuccess",
    "concurrentDeferredCommitIsRejected",
    "manualRollbackWithPlayersRemainsBlocked",
}


def parse(log: str) -> dict[str, object]:
    batch_seen = BATCH in log
    totals = re.search(r"=+\s*(\d+) GAME TESTS COMPLETE\s*=+", log)
    failed_match = re.search(r"(?:failed|Failures?)\s*[:=]\s*(\d+)", log, re.I)
    batch_runs = re.findall(r"Running test batch ['\"]?([^'\"]+)['\"]?\s*\((\d+) tests?\)", log, re.I)
    batch_total = sum(int(count) for name, count in batch_runs if name.startswith(BATCH))
    loot_batch_total = sum(int(count) for name, count in batch_runs if name.startswith(LOOT_BATCH))
    glm_batch_total = sum(int(count) for name, count in batch_runs if name.startswith(GLM_BATCH))
    failed = int(failed_match.group(1)) if failed_match else (0 if totals and "failed" not in log.lower() else None)
    global_total = int(totals.group(1)) if totals else None
    markers = re.findall(r"PHASE4E_GAMETEST_PASSED:([A-Za-z0-9_]+)", log)
    counts = {name: markers.count(name) for name in set(markers)}
    required = REQUIRED_TRANSACTIONAL_TESTS | REQUIRED_SMOKE_TESTS | REQUIRED_DEFERRED_TESTS
    missing = sorted(required - set(markers))
    duplicates = sorted(name for name, count in counts.items() if count > 1)
    unexpected = sorted(set(markers) - required)
    loot_coverage_complete = loot_batch_total == REQUIRED_LOOT_BATCH_TOTAL
    glm_coverage_complete = glm_batch_total == REQUIRED_GLM_BATCH_TOTAL
    complete = bool(totals and batch_seen and failed == 0 and not missing and not duplicates
                    and loot_coverage_complete and glm_coverage_complete)
    return {
        "status": "passed" if complete else "failed",
        "batch": BATCH,
        "global_total": global_total,
        "batch_total": batch_total,
        "loot_batch": LOOT_BATCH,
        "loot_batch_total": loot_batch_total,
        "loot_required_tests": REQUIRED_LOOT_BATCH_TOTAL,
        "loot_coverage_complete": loot_coverage_complete,
        "glm_batch": GLM_BATCH,
        "glm_batch_total": glm_batch_total,
        "glm_required_tests": REQUIRED_GLM_BATCH_TOTAL,
        "glm_coverage_complete": glm_coverage_complete,
        "global_failed": failed,
        "observed_passed": len(set(markers)),
        "coverage_complete": not missing and not duplicates,
        "required_tests": sorted(required),
        "missing_tests": missing,
        "duplicate_markers": duplicates,
        "unexpected_markers": unexpected,
        "observed_tests": markers,
        "tests": [{"batch": name, "count": int(count)} for name, count in batch_runs if name.startswith(BATCH)],
        "loot_tests": [{"batch": name, "count": int(count)} for name, count in batch_runs if name.startswith(LOOT_BATCH)],
        "glm_tests": [{"batch": name, "count": int(count)} for name, count in batch_runs if name.startswith(GLM_BATCH)],
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
