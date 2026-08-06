"""Dedicated Forge acceptance for transactional advancement publication and rollback."""
from __future__ import annotations

import importlib.util
import json
import pathlib
import re
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
HARNESS = ROOT / "scripts" / "run-client-handshake-foundation-acceptance.py"
REPORT = ROOT / "build" / "reports" / "dedicated-advancement-commit-acceptance.json"

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


def advancement_json(generation: str) -> dict[str, dict[str, object]]:
    b = generation == "B"
    root = {
        "display": {
            "icon": {"item": "minecraft:diamond" if b else "minecraft:stone"},
            "title": {"text": f"Partial Reload {generation}"},
            "description": {"text": f"Advancement generation {generation}"},
            "background": "minecraft:textures/gui/advancements/backgrounds/stone.png",
            "show_toast": False,
            "announce_to_chat": False,
            "hidden": False,
        },
        "criteria": {"root": {"trigger": "minecraft:impossible"}},
    }
    child = {
        "parent": "partialreload_advancement:root",
        "display": {
            "icon": {"item": "minecraft:emerald" if b else "minecraft:coal"},
            "title": {"text": f"Child {generation}"},
            "description": {"text": "Progress must survive publication"},
            "show_toast": False,
            "announce_to_chat": False,
            "hidden": False,
        },
        "criteria": {
            "kept": {"trigger": "minecraft:impossible"},
            ("added" if b else "removed"): {"trigger": "minecraft:impossible"},
        },
        "requirements": [["kept"], ["added" if b else "removed"]],
    }
    result = {"root": root, "child": child}
    result["added" if b else "removed"] = {
        "parent": "partialreload_advancement:root",
        "display": {
            "icon": {"item": "minecraft:gold_ingot" if b else "minecraft:iron_ingot"},
            "title": {"text": "Added in B" if b else "Removed after A"},
            "description": {"text": "Visibility makes vanilla client add/remove packets observable"},
            "show_toast": False,
            "announce_to_chat": False,
            "hidden": False,
        },
        "criteria": {"only": {"trigger": "minecraft:impossible"}},
    }
    return result


def install_fixture(acceptance, generation: str) -> None:
    pack = acceptance.run_root / acceptance.server_directory_name / "world" / "datapacks" / "partialreload_advancement_commit"
    base = pack / "data" / "partialreload_advancement" / "advancements"
    base.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({
        "pack": {"pack_format": 15, "description": "Partial Reload advancement acceptance"}
    }) + "\n", encoding="utf-8")
    wanted = advancement_json(generation)
    for name in {"root", "child", "added", "removed"}:
        path = base / f"{name}.json"
        if name in wanted:
            path.write_text(json.dumps(wanted[name], sort_keys=True) + "\n", encoding="utf-8")
        else:
            path.unlink(missing_ok=True)


def connect_client(acceptance, name: str, username: str,
                   transient_aborts: list[dict[str, object]], maximum_launches: int = 3):
    """Obtain one functional login while retaining fail-closed product evidence.

    Forge userdev occasionally terminates a connect before login.  That event is
    retryable only when the client itself reports a timeout, no Partial Reload or
    channel error was emitted, and physical cleanup succeeds.
    """
    for launch in range(1, maximum_launches + 1):
        launch_name = name if launch == 1 else f"{name}-retry-{launch}"
        client = acceptance.start_client(launch_name, username, with_mod=False)
        ready = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 420)
        control = acceptance.run_root / launch_name / "control"
        (control / "connect.request").write_text("connect\n", encoding="utf-8")
        try:
            login = client.wait_marker(
                "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, ready["line"]
            )
        except Exception:
            attempt_lines = client.lines[int(ready["line"]) + 1:]
            joined = "\n".join(attempt_lines)
            retryable = (
                "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" in joined
                and "HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECTED_SCREEN" in joined
                and re.search(r"\bTimed out\b", joined, re.I) is not None
                and "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" not in joined
                and "CLIENT_HANDSHAKE_" not in joined
                and "Unknown custom packet" not in joined
                and re.search(r"rejected.*partialreload:client_sync", joined, re.I) is None
            )
            cleanup = acceptance.cleanup_attempt(
                client, launch_name, username, False, False, expect_exit_request=False
            )
            if not retryable or cleanup.get("status") != "passed":
                raise
            transient_aborts.append({
                "client": launch_name,
                "launch": launch,
                "classification": "INFRASTRUCTURE_FAILURE",
                "infrastructure_subtype": "TRANSIENT_LOGIN_ABORT",
                "cleanup": cleanup,
            })
            if launch == maximum_launches:
                raise AssertionError(
                    f"{username} exhausted {maximum_launches} launches after transient login aborts"
                )
            continue
        if not acceptance.wait_player_present(username, 30):
            raise AssertionError(f"{username} did not become present")
        return client, control, login, launch_name
    raise AssertionError(f"{username} did not obtain a functional login")


def client_probe(client, control: pathlib.Path, start_line: int) -> tuple[dict[str, object], str]:
    (control / "probe-advancements.request").write_text("probe\n", encoding="utf-8")
    marker = client.wait_marker("ADVANCEMENT_ACCEPTANCE_CLIENT_STATE", 45, start_line)
    return marker, client.lines[int(marker["line"])]


def assert_generation(line: str, generation: str, *, progress: bool, selected: bool) -> None:
    require(r"partialreload_advancement:root", line, "root absent on client")
    require(r"partialreload_advancement:child", line, "child absent on client")
    if generation == "A":
        require(r"partialreload_advancement:removed", line, "A removed fixture absent")
        if "partialreload_advancement:added" in line:
            raise AssertionError(f"B-only advancement leaked into A: {line}")
    else:
        require(r"partialreload_advancement:added", line, "B added fixture absent")
        if "partialreload_advancement:removed" in line:
            raise AssertionError(f"removed advancement remained on client: {line}")
    if progress:
        require(r"childCompleted=kept\b", line, "compatible criterion progress was not preserved")
        require(r"childRemaining=(?:added|removed)\b", line, "candidate criterion was not pending")
    if selected:
        require(r"selected=partialreload_advancement:root\b", line, "selected tab was not preserved")


def wait_client_generation(client, control: pathlib.Path, start_line: int, generation: str,
                           *, progress: bool, selected: bool,
                           timeout: float = 45.0) -> tuple[dict[str, object], str]:
    deadline = time.monotonic() + timeout
    cursor = start_line
    last_error: AssertionError | None = None
    while time.monotonic() < deadline:
        marker, line = client_probe(client, control, cursor)
        cursor = int(marker["line"])
        try:
            assert_generation(line, generation, progress=progress, selected=selected)
            return marker, line
        except AssertionError as error:
            last_error = error
            time.sleep(.5)
    raise AssertionError(f"client advancement state did not converge: {last_error}")


def prepare_generation(acceptance, generation: str) -> None:
    assert acceptance.rcon is not None
    install_fixture(acceptance, generation)
    time.sleep(1.1)
    acceptance.rcon.command("partialreload scan")
    acceptance.wait_rcon_status(r"State:\s*IDLE.*Changed resources:\s*[1-9]", 120)
    require(r"started|preparation", acceptance.rcon.command("partialreload prepare advancements"),
            "advancement preparation did not start")
    acceptance.wait_rcon_status(r"State:\s*READY", 180)
    require(r"Applicable:\s*true", acceptance.rcon.command("partialreload prepared"),
            "prepared advancements were not applicable")


def run_once() -> int:
    acceptance = Acceptance(initial_connect_mode="CONTROL", client_mod_mode="without_mod",
                            require_attempt_cleanup=True, server_mod_mode="with_mod")
    result: dict[str, object] = {"status": "failed", "complete_run": False, "run_id": acceptance.run_id}
    clients: list[tuple[object, str, str]] = []
    transient_aborts: list[dict[str, object]] = []
    try:
        acceptance.acquire_lock()
        acceptance.prepare_server()
        install_fixture(acceptance, "A")
        acceptance.start_server()
        assert acceptance.rcon is not None and acceptance.server is not None
        rcon = acceptance.rcon
        require(r"armed", rcon.command(
            "partialreload debug advancement_fault set BEFORE_MANAGER_PUBLICATION"
        ), "advancement fault command was not available")
        require(r"cleared", rcon.command("partialreload debug advancement_fault clear"),
                "advancement fault command could not be cleared")
        first, first_control, first_login, first_name = connect_client(
            acceptance, "advancement-client-a", "PRAdvA", transient_aborts
        )
        clients.append((first, first_name, "PRAdvA"))
        second, second_control, second_login, second_name = connect_client(
            acceptance, "advancement-client-b", "PRAdvB", transient_aborts
        )
        clients.append((second, second_name, "PRAdvB"))

        for player in ("PRAdvA", "PRAdvB"):
            require(r"Granted", rcon.command(f"advancement grant {player} only partialreload_advancement:root"),
                    "root progress grant failed")
            require(r"Granted", rcon.command(f"advancement grant {player} only partialreload_advancement:child kept"),
                    "partial child progress grant failed")
        (first_control / "select-advancement.request").write_text("select\n", encoding="utf-8")
        selected = first.wait_marker("ADVANCEMENT_ACCEPTANCE_CLIENT_TAB_SELECTED", 45, first_login["line"])
        probe_a, line_a = wait_client_generation(
            first, first_control, selected["line"], "A", progress=True, selected=True
        )

        rcon.command("partialreload scan")
        acceptance.wait_rcon_status(r"State:\s*IDLE.*Last scan:\s*(?!never)", 120)
        prepare_generation(acceptance, "B")
        manager_before = rcon.command("partialreload debug manager_fingerprints")
        cursor = acceptance.server.cursor()
        require(r"queued", rcon.command("partialreload apply prepared"), "advancement transaction not queued")
        acceptance.wait_rcon_status(r"State:\s*SUCCESS", 120)
        success = acceptance.server.wait_marker("ADVANCEMENT_COMMIT_SUCCESS", 60, cursor)
        if not acceptance.wait_player_present("PRAdvA", 10) or not acceptance.wait_player_present("PRAdvB", 10):
            raise AssertionError("advancement commit disconnected an existing player")

        probe_b1, line_b1 = wait_client_generation(
            first, first_control, probe_a["line"], "B", progress=True, selected=True
        )
        probe_b2, line_b2 = wait_client_generation(
            second, second_control, second_login["line"], "B", progress=True, selected=False
        )
        manager_after = rcon.command("partialreload debug manager_fingerprints")
        before_id = re.search(r"AdvancementManager:\s*(\d+)", manager_before)
        after_id = re.search(r"AdvancementManager:\s*(\d+)", manager_after)
        if before_id is None or after_id is None or before_id.group(1) != after_id.group(1):
            raise AssertionError(f"ServerAdvancementManager identity changed: {manager_before} / {manager_after}")

        third, third_control, third_login, third_name = connect_client(
            acceptance, "advancement-client-new", "PRAdvNew", transient_aborts
        )
        clients.append((third, third_name, "PRAdvNew"))
        require(r"Granted", rcon.command("advancement grant PRAdvNew only partialreload_advancement:root"),
                "new player could not resolve generation B root")
        _, line_new = wait_client_generation(
            third, third_control, third_login["line"], "B", progress=False, selected=False
        )

        prepare_generation(acceptance, "A")
        require(r"armed", rcon.command("partialreload debug advancement_fault set AFTER_FIRST_PLAYER_REBIND"),
                "rollback fault was not armed")
        rollback_cursor = acceptance.server.cursor()
        require(r"queued", rcon.command("partialreload apply prepared"), "faulted transaction not queued")
        acceptance.wait_rcon_status(r"State:\s*ROLLED_BACK", 120)
        rollback = acceptance.server.wait_marker("ADVANCEMENT_COMMIT_FAILED_ROLLED_BACK", 60, rollback_cursor)
        for client, name, _ in clients:
            control = acceptance.run_root / name / "control"
            wait_client_generation(
                client, control, client.cursor() - 1, "B",
                progress=not name.startswith("advancement-client-new"),
                selected=name.startswith("advancement-client-a")
            )

        cleanups = []
        for client, name, username in reversed(clients):
            cleanup = acceptance.cleanup_attempt(client, name, username, True, True)
            cleanups.append({"client": name, **cleanup})
            if cleanup.get("status") != "passed":
                raise AssertionError(f"client cleanup failed: {cleanup}")
        result.update({
            "status": "passed", "complete_run": True,
            "generation_a": {"status": "passed", "client_state": line_a},
            "generation_b": {"status": "passed", "client_a": line_b1, "client_b": line_b2,
                             "manager_identity_preserved": True,
                             "server_marker": acceptance.server.lines[int(success["line"])]},
            "new_player": {"status": "passed", "client_state": line_new},
            "automatic_rollback": {"status": "passed",
                                   "server_marker": acceptance.server.lines[int(rollback["line"])]},
            "players_connected": 2, "vanilla_packets_only": True, "cleanup": cleanups,
            "transient_login_aborts": transient_aborts,
        })
    except Exception as exc:
        result["error"] = {"status": "failed", "message": str(exc)}
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
    print("ADVANCEMENT_COMMIT_ACCEPTANCE_PASSED" if result.get("status") == "passed"
          else "ADVANCEMENT_COMMIT_ACCEPTANCE_FAILED")
    return 0 if result.get("status") == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(run_once())
