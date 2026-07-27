"""Run all dedicated acceptance suites sequentially with ownership checks."""
from __future__ import annotations
import hashlib, json, pathlib, subprocess, sys, time

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build" / "reports"
SUITES = [
    ("function_acceptance", "run-dedicated-function-acceptance.py", "dedicated-function-acceptance.json", "FUNCTION_ACCEPTANCE_PASSED"),
    ("recipe_acceptance", "run-dedicated-recipe-acceptance.py", "dedicated-recipe-acceptance.json", None),
    ("tag_acceptance", "run-dedicated-tag-acceptance.py", "dedicated-tag-acceptance.json", None),
    ("joint_acceptance", "run-dedicated-tags-recipes-acceptance.py", "dedicated-tags-recipes-acceptance.json", None),
    ("tag_recipe_commit_acceptance", "run-dedicated-tags-recipes-commit-acceptance.py", "dedicated-tags-recipes-commit-acceptance.json", None),
    ("joint_safety_acceptance", "run-dedicated-tags-recipes-safety-acceptance.py", "dedicated-tags-recipes-safety-acceptance.json", "DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_PASSED"),
    ("kubejs_expected_block", "run-dedicated-kubejs-recipe-acceptance.py", "dedicated-kubejs-recipe-acceptance.json", None),
]

def owned_processes() -> list[str]:
    # Do not enumerate/kill Java globally.  This runner only checks for the
    # Gradle/server command line that its child suites are allowed to create.
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
        "Get-CimInstance Win32_Process | Where-Object {$_.ProcessId -ne $PID -and $_.CommandLine -notmatch 'Get-CimInstance' -and $_.CommandLine -match 'Partial Reload.*(runServer|forgeserveruserdev)'} | Select-Object -Expand ProcessId"],
        cwd=ROOT, capture_output=True, text=True, check=False).stdout
    return [line.strip() for line in out.splitlines() if line.strip().isdigit()]

def fixture_fingerprint() -> str:
    root = ROOT / "run" / "world" / "datapacks"
    digest = hashlib.sha256()
    if not root.exists():
        digest.update(b"<absent>")
        return digest.hexdigest()
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        digest.update(path.relative_to(root).as_posix().encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()

def configured_rcon_port() -> int | None:
    props = ROOT / "run" / "server.properties"
    if not props.exists():
        return None
    for line in props.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith("rcon.port="):
            try:
                return int(line.split("=", 1)[1])
            except ValueError:
                return None
    return None

def port_released(port: int | None) -> bool:
    if port is None:
        return True
    command = f"Get-NetTCPConnection -LocalPort {port} -State Listen -ErrorAction SilentlyContinue | Measure-Object | Select-Object -ExpandProperty Count"
    output = subprocess.run(["powershell", "-NoProfile", "-Command", command], cwd=ROOT,
                            capture_output=True, text=True, check=False).stdout.strip()
    return output in {"", "0"}

def main() -> int:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    result: dict[str, object] = {"started_at": time.time(), "suites": {}}
    logs = REPORT_DIR / "all-acceptance"; logs.mkdir(parents=True, exist_ok=True)
    expected_fixtures = fixture_fingerprint()
    for key, script, report_name, marker in SUITES:
        if owned_processes():
            result["orphan_process_check"] = "failed"
            break
        started = time.time()
        report = REPORT_DIR / report_name
        rcon_port = configured_rcon_port()
        before = report.stat().st_mtime_ns if report.exists() else 0
        proc = subprocess.run([sys.executable, str(ROOT / "scripts" / script)], cwd=ROOT, capture_output=True, text=True)
        stdout_path = logs / f"{key}.stdout.log"; stderr_path = logs / f"{key}.stderr.log"
        stdout_path.write_text(proc.stdout, encoding="utf-8"); stderr_path.write_text(proc.stderr, encoding="utf-8")
        print(proc.stdout, end="")
        if proc.stderr:
            print(proc.stderr, end="", file=sys.stderr)
        valid = False; data = None
        try:
            data = json.loads(report.read_text(encoding="utf-8")); valid = report.stat().st_mtime_ns > before
            if key == "joint_safety_acceptance":
                valid = valid and data.get("complete_run") is True and set(data.get("groups", {})) == {"recoverable", "rollback_verification", "degraded", "tag-lifecycle", "unsupported", "players"} and all(v.get("status") == "passed" for v in data.get("groups", {}).values())
            else:
                valid = valid and data.get("status") == "passed"
        except (OSError, json.JSONDecodeError):
            valid = False
        marker_ok = marker is None or marker in proc.stdout
        cleanup = {"owned_processes_absent": not bool(owned_processes()),
                   "session_lock_absent": not (ROOT / "run" / "world" / "session.lock").exists(),
                   "properties_backup_absent": not (ROOT / "run" / "server.properties.partialreload.bak").exists(),
                   "rcon_port_released": port_released(rcon_port),
                   "fixtures_restored": fixture_fingerprint() == expected_fixtures}
        result["suites"][key] = {"status": "passed" if proc.returncode == 0 and valid and marker_ok and all(cleanup.values()) else "failed",
                                  "exit_code": proc.returncode, "duration_seconds": round(time.time() - started, 2),
                                  "report_path": str(report), "report_valid": valid, "expected_marker_observed": marker_ok,
                                  "stdout_log": str(stdout_path), "stderr_log": str(stderr_path),
                                  "rcon_port": rcon_port, "cleanup": cleanup}
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
    result["cleanup"] = {"owned_processes_absent": not bool(owned_processes()),
                          "rcon_ports_released": all(v.get("cleanup", {}).get("rcon_port_released", False) for v in result["suites"].values()),
                          "session_lock_absent": not lock.exists(),
                          "properties_restored": not (ROOT / "run" / "server.properties.partialreload.bak").exists(),
                          "fixtures_restored": fixture_fingerprint() == expected_fixtures}
    result["finished_at"] = time.time()
    (REPORT_DIR / "all-acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    ok = len(result["suites"]) == len(SUITES) and all(v.get("status") == "passed" for v in result["suites"].values()) and result.get("orphan_process_check") == "passed" and result["world_lock_check"] == "passed" and result["restoration_check"] == "passed" and all(result["cleanup"].values())
    result["status"] = "passed" if ok else "failed"
    print("ALL_ACCEPTANCE_PASSED" if ok else "ALL_ACCEPTANCE_FAILED")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
