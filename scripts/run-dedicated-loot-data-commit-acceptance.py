"""Dedicated Forge acceptance for transactional predicates/modifiers/loot publication."""
from __future__ import annotations

import importlib.util
import json
import os
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
HARNESS = ROOT / "scripts" / "run-client-handshake-foundation-acceptance.py"
REPORT = ROOT / "build" / "reports" / "dedicated-loot-data-commit-acceptance.json"

spec = importlib.util.spec_from_file_location("partialreload_handshake_acceptance", HARNESS)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = module
spec.loader.exec_module(module)
Acceptance = module.Acceptance

MAX_CLIENT_LAUNCHES = 5


def require(pattern: str, value: str, message: str) -> str:
    if not re.search(pattern, value, re.I | re.S):
        raise AssertionError(f"{message}: {value}")
    return value


def install_fixture(acceptance, generation: str) -> None:
    pack = acceptance.run_root / acceptance.server_directory_name / "world" / "datapacks" / "partialreload_loot_commit"
    b = generation == "B"
    files: dict[str, str] = {
        "pack.mcmeta": json.dumps({"pack": {"pack_format": 15, "description": "Partial Reload loot commit acceptance"}}),
        "data/partialreload_loot/predicates/generation.json": json.dumps({
            "condition": "minecraft:inverted",
            "term": {"condition": "minecraft:random_chance", "chance": 0.0 if b else 1.0},
        }),
        "data/partialreload_loot/item_modifiers/generation.json": json.dumps({
            "function": "minecraft:set_count", "count": 3 if b else 1,
            "conditions": [{"condition": "minecraft:reference", "name": "partialreload_loot:generation"}],
        }),
        "data/partialreload_loot/loot_tables/generation.json": json.dumps({
            "type": "minecraft:chest",
            "pools": [{"rolls": 1, "entries": [{
                "type": "minecraft:item", "name": "minecraft:diamond" if b else "minecraft:stone",
                **({"conditions": [{"condition": "minecraft:reference", "name": "partialreload_loot:generation"}],
                    "functions": [{"function": "minecraft:reference", "name": "partialreload_loot:generation"}]} if b else {}),
            }]}],
        }),
    }
    removed_path = pack / "data/partialreload_loot/loot_tables/removed.json"
    added_path = pack / "data/partialreload_loot/loot_tables/added.json"
    if b:
        removed_path.unlink(missing_ok=True)
        files["data/partialreload_loot/loot_tables/added.json"] = json.dumps({
            "type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": "minecraft:emerald"}]}],
        })
    else:
        added_path.unlink(missing_ok=True)
        files["data/partialreload_loot/loot_tables/removed.json"] = json.dumps({
            "type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": "minecraft:coal"}]}],
        })
    for relative, content in files.items():
        path = pack / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content + "\n", encoding="utf-8")


def item_count(rcon, player: str, item: str) -> int:
    response = rcon.command(f"clear {player} {item} 0")
    match = re.search(r"(?:Found|Removed)\s+(\d+)", response, re.I)
    if match:
        return int(match.group(1))
    if re.search(r"No items were found|0 item", response, re.I):
        return 0
    raise AssertionError(f"could not read {item} count: {response}")


def predicate_value(rcon) -> bool:
    rcon.command("scoreboard objectives add pr_loot dummy")
    rcon.command("scoreboard players set $predicate pr_loot 0")
    rcon.command("execute if predicate partialreload_loot:generation run scoreboard players set $predicate pr_loot 1")
    response = rcon.command("scoreboard players get $predicate pr_loot")
    return bool(re.search(r"\bhas\s+1(?:\s|\[)|:\s*1\b", response, re.I))


def loot_probe(rcon, player: str, table: str, item: str) -> int:
    rcon.command(f"clear {player}")
    rcon.command(f"loot give {player} loot {table}")
    return item_count(rcon, player, item)


def modifier_probe(rcon, player: str) -> int:
    rcon.command(f"item replace entity {player} weapon.mainhand with minecraft:stick 1")
    rcon.command(f"item modify entity {player} weapon.mainhand partialreload_loot:generation")
    return item_count(rcon, player, "minecraft:stick")


def connect_functional_client(acceptance, username: str) -> tuple[object, list[dict[str, object]]]:
    """Obtain one real login without hiding product or cleanup failures."""
    transient_aborts: list[dict[str, object]] = []
    for launch in range(1, MAX_CLIENT_LAUNCHES + 1):
        name = "loot-client" if launch == 1 else f"loot-client-{launch}"
        client = acceptance.start_client(name, username, with_mod=False)
        try:
            ready = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 420)
            control = acceptance.run_root / name / "control"
            (control / "connect.request").write_text("connect\n", encoding="utf-8")
            client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, ready["line"])
            if not acceptance.wait_player_present(username, 30):
                raise AssertionError("player did not become present")
            return client, transient_aborts
        except Exception as exc:
            markers = {str(entry.get("marker", "")) for entry in client.entries()}
            output = "\n".join(client.lines)
            prelogin = (
                "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" in markers
                and "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" not in markers
            )
            product_signal = (
                any(marker.startswith("CLIENT_HANDSHAKE_") for marker in markers)
                or "Unknown custom packet" in output
                or "channel mismatch" in output.lower()
                or "rejected partialreload:client_sync" in output.lower()
            )
            cleanup = acceptance.cleanup_attempt(
                client, name, username, False, False, False
            )
            if (prelogin and not product_signal and cleanup.get("status") == "passed"
                    and launch < MAX_CLIENT_LAUNCHES):
                transient_aborts.append({
                    "launch": launch,
                    "classification": "INFRASTRUCTURE_FAILURE",
                    "infrastructure_subtype": "TRANSIENT_LOGIN_ABORT",
                    "cleanup": cleanup,
                    "error": str(exc),
                })
                continue
            raise AssertionError(
                f"client launch {launch} failed; prelogin={prelogin}; "
                f"productSignal={product_signal}; cleanup={cleanup}; error={exc}"
            ) from exc
    raise AssertionError("client launch quota exhausted")


def run_once() -> int:
    acceptance = Acceptance(initial_connect_mode="CONTROL", client_mod_mode="without_mod",
                            require_attempt_cleanup=True, server_mod_mode="with_mod")
    result: dict[str, object] = {"status": "failed", "complete_run": False, "run_id": acceptance.run_id}
    client = None
    try:
        acceptance.acquire_lock()
        acceptance.prepare_server()
        install_fixture(acceptance, "A")
        acceptance.start_server()
        assert acceptance.rcon is not None and acceptance.server is not None
        rcon = acceptance.rcon
        client, transient_aborts = connect_functional_client(acceptance, "PRLoot")

        require(r"False|0", str(predicate_value(rcon)), "generation A predicate should be false")
        if modifier_probe(rcon, "PRLoot") != 1:
            raise AssertionError("generation A modifier count mismatch")
        if loot_probe(rcon, "PRLoot", "partialreload_loot:generation", "minecraft:stone") != 1:
            raise AssertionError("generation A table mismatch")
        generated_before = rcon.command("data get entity PRLoot Inventory")

        rcon.command("partialreload scan")
        acceptance.wait_rcon_status(r"State:\s*IDLE.*Last scan:\s*(?!never)", 120)
        install_fixture(acceptance, "B")
        time.sleep(1.1)
        rcon.command("partialreload scan")
        acceptance.wait_rcon_status(r"State:\s*IDLE.*Changed resources:\s*[1-9]", 120)
        require(r"started|preparation", rcon.command("partialreload prepare loot"), "loot preparation did not start")
        acceptance.wait_rcon_status(r"State:\s*READY", 180)
        prepared = rcon.command("partialreload prepared")
        require(r"Expanded scope:.*predicates.*item_modifiers|Expanded scope:.*item_modifiers.*predicates",
                prepared, "wrong prepared artifact")
        require(r"Technically applicable:\s*true", prepared, "loot artifact is not applicable")
        server_cursor = acceptance.server.cursor()
        require(r"queued", rcon.command("partialreload apply prepared"), "loot transaction not queued")
        acceptance.wait_rcon_status(r"State:\s*SUCCESS", 120)
        success = acceptance.server.wait_marker("LOOT_DATA_COMMIT_SUCCESS", 60, server_cursor)
        if not acceptance.wait_player_present("PRLoot", 10):
            raise AssertionError("commit disconnected the player")

        generated_after_commit = rcon.command("data get entity PRLoot Inventory")
        if generated_after_commit.strip() != generated_before.strip():
            raise AssertionError("pre-existing generated inventory changed retroactively")

        if not predicate_value(rcon):
            raise AssertionError("generation B predicate inactive")
        if modifier_probe(rcon, "PRLoot") != 3:
            raise AssertionError("generation B modifier inactive")
        if loot_probe(rcon, "PRLoot", "partialreload_loot:generation", "minecraft:diamond") != 3:
            raise AssertionError("generation B table/reference mismatch")
        if loot_probe(rcon, "PRLoot", "partialreload_loot:added", "minecraft:emerald") != 1:
            raise AssertionError("generation B added table inactive")
        if loot_probe(rcon, "PRLoot", "partialreload_loot:removed", "minecraft:coal") != 0:
            raise AssertionError("removed table remained active")

        require(r"queued", rcon.command("partialreload rollback loot"), "manual rollback not queued")
        acceptance.wait_rcon_status(r"State:\s*ROLLED_BACK", 120)
        if predicate_value(rcon):
            raise AssertionError("rollback did not restore predicate A")
        if modifier_probe(rcon, "PRLoot") != 1:
            raise AssertionError("rollback did not restore modifier A")
        if loot_probe(rcon, "PRLoot", "partialreload_loot:generation", "minecraft:stone") != 1:
            raise AssertionError("rollback did not restore table A")
        if loot_probe(rcon, "PRLoot", "partialreload_loot:removed", "minecraft:coal") != 1:
            raise AssertionError("rollback did not restore removed table")

        cleanup = acceptance.cleanup_attempt(client, client.name, "PRLoot", True, True)
        if cleanup.get("status") != "passed":
            raise AssertionError(f"client cleanup failed: {cleanup}")
        result.update({
            "status": "passed", "complete_run": True, "client_main_mod_present": False,
            "generation_a": {"status": "passed", "preexisting_item_snapshot": generated_before.strip()},
            "generation_b": {"status": "passed", "server_marker": acceptance.server.lines[int(success["line"])],
                             "preexisting_item_unchanged": True},
            "player_remained_connected": True,
            "client_launch_attempts": len(transient_aborts) + 1,
            "transient_login_aborts": transient_aborts,
            "manual_rollback": {"status": "passed"}, "cleanup": cleanup,
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
    print("LOOT_DATA_COMMIT_ACCEPTANCE_PASSED" if result.get("status") == "passed"
          else "LOOT_DATA_COMMIT_ACCEPTANCE_FAILED")
    return 0 if result.get("status") == "passed" else 1


def main() -> int:
    return run_once()


if __name__ == "__main__":
    raise SystemExit(main())
