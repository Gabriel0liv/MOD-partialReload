"""Real Forge acceptance for opt-in server commit with client refresh deferred until relog."""
from __future__ import annotations

import importlib.util
import json
import pathlib
import re
import os
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
HARNESS = ROOT / "scripts" / "run-client-handshake-foundation-acceptance.py"
REPORT = ROOT / "build" / "reports" / "deferred-client-refresh-acceptance.json"

spec = importlib.util.spec_from_file_location("partialreload_handshake_acceptance", HARNESS)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = module
spec.loader.exec_module(module)
Acceptance = module.Acceptance


def require(pattern: str, value: str, message: str) -> str:
    if not re.search(pattern, value, re.I | re.S):
        raise AssertionError(f"{message}: {value}")
    return value


def marker_line(process, marker: dict[str, object]) -> str:
    return process.lines[int(marker["line"])]


def probe_client(client, control: pathlib.Path, start_line: int, expected_count: int, expected_item: str):
    (control / "probe-data.request").write_text("probe\n", encoding="utf-8")
    marker = client.wait_marker("DEFERRED_REFRESH_ACCEPTANCE_CLIENT_DATA", 45, start_line)
    line = marker_line(client, marker)
    require(fr"recipeResultCount={expected_count}\b", line, "client recipe generation mismatch")
    require(fr"tagMembers=[^\r\n]*{re.escape(expected_item)}", line, "client tag generation mismatch")
    return marker


def run_once() -> int:
    acceptance = Acceptance(initial_connect_mode="CONTROL", client_mod_mode="without_mod",
                            require_attempt_cleanup=True, server_mod_mode="with_mod")
    result: dict[str, object] = {"status": "failed", "complete_run": False, "run_id": acceptance.run_id}
    client = None
    cleanup = {"status": "failed"}
    try:
        acceptance.acquire_lock()
        acceptance.start_server()
        assert acceptance.rcon is not None and acceptance.server is not None
        client = acceptance.start_client("deferred-client", "PRDeferred", with_mod=False)
        ready = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 420)
        control = acceptance.run_root / "deferred-client" / "control"
        (control / "connect.request").write_text("connect\n", encoding="utf-8")
        login = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, ready["line"])
        if not acceptance.wait_player_present("PRDeferred", 30):
            raise AssertionError("client did not become present in RCON")

        probe_a = probe_client(client, control, login["line"], 1, "minecraft:stone")
        active_a = acceptance.rcon.command("partialreload debug active_recipe partialreload_test:acceptance")
        require(r"present=true.*count=1", active_a, "server generation A recipe missing")

        acceptance.prepare_connected_commit_artifact()
        acceptance.rcon.command("execute at PRDeferred run setblock ~1 ~ ~ minecraft:crafting_table")
        open_cursor = client.cursor()
        (control / "open-container.request").write_text("open\n", encoding="utf-8")
        opened = client.wait_marker("DEFERRED_REFRESH_ACCEPTANCE_CONTAINER_OPENED", 45, open_cursor)

        server_cursor = acceptance.server.cursor()
        response = acceptance.rcon.command("partialreload apply prepared deferred")
        require(r"queued.*DEFER_CLIENT_REFRESH_UNTIL_RELOGIN", response, "deferred transaction not queued")
        transaction = acceptance.wait_rcon_status(r"State:\s*SUCCESS", 90)
        success = acceptance.server.wait_marker("TAG_RECIPE_COMMIT_SUCCESS_DEFERRED_CLIENT_REFRESH", 90,
                                                server_cursor)
        closed = client.wait_marker("DEFERRED_REFRESH_ACCEPTANCE_CONTAINER_CLOSED", 45, opened["line"])
        status = acceptance.rcon.command("partialreload status")
        require(r"tagRecipeGeneration=1\b", status, "server generation did not advance")
        require(r"staleClientCount=1\b", status, "connected client was not marked stale")

        recipe_changed = acceptance.rcon.command("partialreload debug active_recipe partialreload_test:acceptance")
        recipe_removed = acceptance.rcon.command("partialreload debug active_recipe partialreload_test:removed_recipe")
        recipe_added = acceptance.rcon.command("partialreload debug active_recipe partialreload_test:new_recipe")
        tag_b = acceptance.rcon.command("partialreload debug active_tag items partialreload_test:joint")
        require(r"present=true.*count=2", recipe_changed, "changed recipe was not authoritative")
        require(r"present=false", recipe_removed, "removed recipe remained authoritative")
        require(r"present=true.*diamond", recipe_added, "new recipe was not authoritative")
        require(r"members=.*minecraft:dirt", tag_b, "new tag binding was not authoritative")
        stale_probe = probe_client(client, control, closed["line"], 1, "minecraft:stone")

        disconnect_cursor = client.cursor()
        (control / "disconnect.request").write_text("disconnect\n", encoding="utf-8")
        logout = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT", 45, disconnect_cursor)
        acceptance.wait_rcon_status(r"staleClientCount=0\b", 30)
        reconnect_ready = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_READY", 45, logout["line"])
        (control / "reconnect.request").write_text("reconnect\n", encoding="utf-8")
        reconnect_login = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120,
                                             reconnect_ready["line"])
        if not acceptance.wait_player_present("PRDeferred", 30):
            raise AssertionError("reconnected client did not become present in RCON")
        time.sleep(1.0)
        probe_b = probe_client(client, control, reconnect_login["line"], 2, "minecraft:dirt")
        post_relog_status = acceptance.rcon.command("partialreload status")
        require(r"staleClientCount=0\b", post_relog_status, "relogged client became stale")

        cleanup = acceptance.cleanup_attempt(client, "deferred-client", "PRDeferred", True, True)
        if cleanup.get("status") != "passed":
            raise AssertionError(f"client cleanup failed: {cleanup}")
        result.update({
            "status": "passed",
            "complete_run": True,
            "client_main_mod_present": False,
            "server_generation_a": {"status": "passed", "recipe": active_a.strip()},
            "menu_closed_before_commit": {"status": "passed", "opened": marker_line(client, opened),
                                          "closed": marker_line(client, closed)},
            "server_generation_b": {"status": "passed", "changed": recipe_changed.strip(),
                                    "removed": recipe_removed.strip(), "added": recipe_added.strip(),
                                    "tag": tag_b.strip()},
            "stale_until_relog": {"status": "passed", "commit": marker_line(acceptance.server, success),
                                  "client_before": marker_line(client, probe_a),
                                  "client_while_stale": marker_line(client, stale_probe)},
            "relog_refresh": {"status": "passed", "client_after": marker_line(client, probe_b),
                              "status_output": post_relog_status.strip()},
            "cleanup": cleanup,
        })
    except Exception as exc:
        result["error"] = {"status": "failed", "message": str(exc)}
        client_lines = list(client.lines) if client is not None else []
        server_lines = list(acceptance.server.lines) if acceptance.server is not None else []
        result["pre_functional_evidence"] = {
            "ready_seen": any("HANDSHAKE_ACCEPTANCE_CLIENT_READY" in line for line in client_lines),
            "connect_requested_seen": any("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" in line for line in client_lines),
            "network_login_seen": any("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" in line for line in client_lines),
            "partialreload_protocol_marker_seen": any("CLIENT_HANDSHAKE_SERVER_PRESENCE_RECEIVED" in line
                                                        or "CLIENT_HANDSHAKE_SERVER_PENDING" in line
                                                        or "CLIENT_HANDSHAKE_SERVER_COMPATIBLE" in line
                                                        for line in server_lines),
            "channel_rejection_seen": any("rejected" in line.lower() and "partialreload:client_sync" in line.lower()
                                           for line in client_lines + server_lines),
            "unknown_custom_packet_seen": any("Unknown custom packet" in line for line in client_lines + server_lines),
        }
    finally:
        try:
            acceptance.cleanup()
        except Exception as exc:
            result["global_cleanup_error"] = str(exc)
        acceptance.release_lock()
        result["global_cleanup"] = acceptance.cleanup_result
        if result.get("status") == "passed" and acceptance.cleanup_result.get("status") != "passed":
            result["status"] = "failed"
            result["complete_run"] = False
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if result.get("status") == "passed":
        print("DEFERRED_CLIENT_REFRESH_ACCEPTANCE_PASSED")
        return 0
    print("DEFERRED_CLIENT_REFRESH_ACCEPTANCE_FAILED")
    return 1


def transient_pre_functional_abort(report: dict[str, object]) -> bool:
    error = report.get("error")
    cleanup = report.get("global_cleanup")
    message = str(error.get("message", "")) if isinstance(error, dict) else ""
    evidence = report.get("pre_functional_evidence")
    return (("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" in message)
            and isinstance(evidence, dict)
            and evidence.get("ready_seen") is True
            and evidence.get("connect_requested_seen") is True
            and evidence.get("network_login_seen") is False
            and evidence.get("partialreload_protocol_marker_seen") is False
            and evidence.get("channel_rejection_seen") is False
            and evidence.get("unknown_custom_packet_seen") is False
            and isinstance(cleanup, dict) and cleanup.get("status") == "passed")


def main() -> int:
    if os.environ.get("PARTIALRELOAD_DEFERRED_SINGLE_ATTEMPT") == "1":
        return run_once()
    attempts: list[dict[str, object]] = []
    for launch in range(1, 6):
        environment = os.environ.copy()
        environment["PARTIALRELOAD_DEFERRED_SINGLE_ATTEMPT"] = "1"
        completed = subprocess.run([sys.executable, str(pathlib.Path(__file__).resolve())], cwd=ROOT,
                                   env=environment, text=True, capture_output=True, check=False)
        try:
            report = json.loads(REPORT.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            report = {"status": "failed", "error": {"message": f"invalid child report: {exc}"}}
        transient = transient_pre_functional_abort(report)
        attempts.append({"launch": launch, "status": report.get("status"),
                         "classification": "TRANSIENT_INFRASTRUCTURE_FAILURE" if transient
                         else "VALID_PASS" if report.get("status") == "passed" else "HARNESS_OR_PRODUCT_FAILURE",
                         "run_id": report.get("run_id"), "cleanup": report.get("global_cleanup"),
                         "error": report.get("error")})
        if report.get("status") == "passed":
            final = dict(report)
            final["launch_attempts"] = launch
            final["attempts"] = attempts
            REPORT.write_text(json.dumps(final, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            print(completed.stdout, end="")
            print("DEFERRED_CLIENT_REFRESH_ACCEPTANCE_PASSED")
            return 0
        if not transient:
            final = dict(report)
            final["launch_attempts"] = launch
            final["attempts"] = attempts
            REPORT.write_text(json.dumps(final, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            print(completed.stdout, end="")
            if completed.stderr:
                print(completed.stderr, end="", file=sys.stderr)
            print("DEFERRED_CLIENT_REFRESH_ACCEPTANCE_FAILED")
            return 1
    final = {"status": "failed", "complete_run": False, "launch_attempts": len(attempts),
             "attempts": attempts, "error": {"status": "failed",
             "message": "DEFERRED_CLIENT_REFRESH_VALID_TRIAL_NOT_REACHED"}}
    REPORT.write_text(json.dumps(final, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("DEFERRED_CLIENT_REFRESH_ACCEPTANCE_FAILED")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
