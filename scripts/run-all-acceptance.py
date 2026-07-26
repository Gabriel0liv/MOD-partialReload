"""Run all dedicated acceptance suites sequentially with ownership checks."""
from __future__ import annotations
import json, pathlib, subprocess, sys, time

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build" / "reports"
SUITES = [
    ("function_acceptance", "run-dedicated-function-acceptance.py", []),
    ("recipe_acceptance", "run-dedicated-recipe-acceptance.py", []),
    ("tag_acceptance", "run-dedicated-tag-acceptance.py", []),
    ("joint_acceptance", "run-dedicated-tags-recipes-acceptance.py", []),
    ("tag_recipe_commit_acceptance", "run-dedicated-tags-recipes-commit-acceptance.py", []),
    ("joint_safety_acceptance", "run-dedicated-tags-recipes-safety-acceptance.py", []),
    ("kubejs_expected_block", "run-dedicated-kubejs-recipe-acceptance.py", []),
]

def owned_processes() -> list[str]:
    # Do not enumerate/kill Java globally.  This runner only checks for the
    # Gradle/server command line that its child suites are allowed to create.
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
        "Get-CimInstance Win32_Process | Where-Object {$_.ProcessId -ne $PID -and $_.CommandLine -notmatch 'Get-CimInstance' -and $_.CommandLine -match 'Partial Reload.*(runServer|forgeserveruserdev)'} | Select-Object -Expand ProcessId"],
        cwd=ROOT, capture_output=True, text=True, check=False).stdout
    return [line.strip() for line in out.splitlines() if line.strip().isdigit()]

def main() -> int:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    result: dict[str, object] = {"started_at": time.time(), "suites": {}}
    for key, script, args in SUITES:
        if owned_processes():
            result["orphan_process_check"] = "failed"
            break
        started = time.time()
        proc = subprocess.run([sys.executable, str(ROOT / "scripts" / script), *args], cwd=ROOT)
        result["suites"][key] = {"status": "passed" if proc.returncode == 0 else "failed",
                                  "exit_code": proc.returncode, "duration_seconds": round(time.time() - started, 2)}
        if proc.returncode != 0:
            break
        if owned_processes():
            result["orphan_process_check"] = "failed"
            break
    else:
        result["orphan_process_check"] = "passed"
    lock = ROOT / "run" / "world" / "session.lock"
    # A stale lock is a diagnostic failure, never something this consolidator
    # silently deletes.  Individual suites own cleanup of their disposable
    # worlds after their process tree has terminated.
    result["world_lock_check"] = "passed" if not lock.exists() else "failed"
    result["restoration_check"] = "passed" if not (ROOT / "run" / "server.properties.partialreload.bak").exists() else "failed"
    result["finished_at"] = time.time()
    (REPORT_DIR / "all-acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    ok = all(v.get("status") == "passed" for v in result["suites"].values()) and result.get("orphan_process_check") == "passed" and result["world_lock_check"] == "passed" and result["restoration_check"] == "passed"
    print("ALL_ACCEPTANCE_PASSED" if ok else "ALL_ACCEPTANCE_FAILED")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
