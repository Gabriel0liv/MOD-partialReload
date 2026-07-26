"""Headless dedicated-server acceptance for transactional function commit.

The Gradle process is deliberately controlled through temporary localhost RCON;
the ForgeGradle JavaExec wrapper does not reliably forward redirected stdin to
DedicatedServer.
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import queue
import random
import re
import secrets
import shutil
import socket
import subprocess
import sys
import threading
import time
from typing import Callable

from minecraft_rcon import RconClient, RconError

ROOT = pathlib.Path(__file__).resolve().parents[1]
# DedicatedServer loads world datapacks from the level directory.  Using this
# path also makes the fixture visible to the active ResourceManager at startup.
PACK = ROOT / "run" / "world" / "datapacks" / "partialreload_acceptance"
REPORT_JSON = ROOT / "build" / "reports" / "dedicated-function-acceptance.json"
REPORT_LOG = ROOT / "build" / "reports" / "dedicated-function-acceptance.log"


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def write(path: pathlib.Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def generation(letter: str) -> dict[str, str]:
    b = letter == "B"
    value = "2" if b else "1"
    tag_fn = "tag_b" if b else "tag_a"
    tick_fn = "tick_b" if b else "tick_a"
    files = {
        "pack.mcmeta": '{"pack":{"pack_format":15,"description":"Partial Reload acceptance"}}\n',
        "data/partialreload_test/recipes/acceptance.json": json.dumps({
            "type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:stick"}],
            "result": {"item": "minecraft:torch", "count": 1 if not b else 2}
        }) + "\n",
        f"data/partialreload_test/functions/behavior.mcfunction":
            f"scoreboard players set result pr_acceptance {value}\n",
        f"data/partialreload_test/functions/{tag_fn}.mcfunction":
            f"scoreboard players set tag_result pr_acceptance {value}\n",
        "data/partialreload_test/functions/load_guard.mcfunction":
            "scoreboard players set load_guard pr_acceptance 99\n",
        "data/partialreload_test/functions/scheduled_target.mcfunction":
            f"scoreboard players set scheduled_id pr_acceptance {value}\n",
        "data/partialreload_test/functions/scheduled_tag_a.mcfunction":
            "scoreboard players set scheduled_tag pr_acceptance 1\n",
        "data/partialreload_test/functions/scheduled_tag_b.mcfunction":
            "scoreboard players set scheduled_tag pr_acceptance 2\n",
        "data/partialreload_test/functions/tick_a.mcfunction":
            "scoreboard players add tick_a pr_acceptance 1\n",
        "data/partialreload_test/functions/tick_b.mcfunction":
            "scoreboard players add tick_b pr_acceptance 1\n",
        "data/partialreload_test/functions/tick_retained.mcfunction":
            "scoreboard players add tick_retained pr_acceptance 1\n",
        "data/partialreload_test/tags/functions/acceptance_tag.json":
            json.dumps({"replace": False, "values": [f"partialreload_test:{tag_fn}"]}) + "\n",
        "data/minecraft/tags/functions/load.json":
            json.dumps({"replace": False, "values": ["partialreload_test:load_guard"]}) + "\n",
        "data/minecraft/tags/functions/tick.json":
            json.dumps({"replace": False, "values": [f"partialreload_test:{tick_fn}",
                                                       "partialreload_test:tick_retained"]}) + "\n",
        "data/partialreload_test/tags/functions/scheduled_tag.json":
            json.dumps({"replace": False, "values": [f"partialreload_test:scheduled_tag_{letter.lower()}"]}) + "\n",
    }
    if not b:
        files["data/partialreload_test/functions/removed.mcfunction"] = "scoreboard players set removed pr_acceptance 1\nscoreboard players set scheduled_removed pr_acceptance 1\n"
    else:
        files["data/partialreload_test/functions/added.mcfunction"] = "scoreboard players set added pr_acceptance 2\n"
    return files


def install_generation(letter: str, initial: bool = False) -> None:
    files = generation(letter)
    if initial:
        if PACK.exists():
            shutil.rmtree(PACK)
        PACK.mkdir(parents=True)
        for rel, text in files.items():
            write(PACK / rel, text)
        return
    staging = PACK.parent / (PACK.name + ".staging")
    if staging.exists():
        shutil.rmtree(staging)
    for rel, text in files.items():
        write(staging / rel, text)
    # Keep the pack identity and replace each resource atomically.  Deletions
    # are performed only after all new files exist in the staging tree.
    old = {p.relative_to(PACK) for p in PACK.rglob("*") if p.is_file()}
    new = set(files)
    for rel in old - new:
        (PACK / rel).unlink()
    for rel in new:
        target = PACK / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        os.replace(staging / rel, target)
    if staging.exists():
        shutil.rmtree(staging)


class Acceptance:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.events: queue.Queue[str] = queue.Queue()
        self.transcript: list[str] = []
        self.results: dict[str, str] = {}
        self.proc: subprocess.Popen[str] | None = None
        self.reader_thread: threading.Thread | None = None
        self.rcon: RconClient | None = None
        self.server_properties = ROOT / "run" / "server.properties"
        self.properties_backup = self.server_properties.with_suffix(".properties.partialreload.bak")
        self.port = free_port()
        self.password = secrets.token_urlsafe(32)
        self.owned_pid: int | None = None

    def assert_test_world_unlocked(self) -> None:
        """Remove a stale disposable-world lock only after ownership checks."""
        lock = ROOT / "run" / "world" / "session.lock"
        if not lock.exists():
            return
        if self.owned_pid is not None:
            raise RuntimeError("owned server process is still registered")
        probe = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'Partial Reload.*(runServer|forgeserveruserdev)' } | Select-Object -Expand ProcessId"],
            cwd=ROOT, capture_output=True, text=True, check=False)
        if any(line.strip().isdigit() for line in probe.stdout.splitlines()):
            raise RuntimeError("test world lock persists while an owned server process is alive")
        try:
            lock.unlink()
        except OSError as exc:
            raise RuntimeError("test world lock persists; ownership is uncertain") from exc
        self.log("STALE_TEST_LOCK_REMOVED " + str(lock))

    def log(self, text: str) -> None:
        self.transcript.append(text)
        print(text, flush=True)

    def configure_rcon(self) -> None:
        if self.server_properties.exists():
            shutil.copy2(self.server_properties, self.properties_backup)
            lines = self.server_properties.read_text(encoding="utf-8").splitlines()
        else:
            lines = []
        values = {
            "enable-rcon": "true", "rcon.password": self.password,
            "rcon.port": str(self.port), "broadcast-rcon-to-ops": "false",
            "enable-query": "false",
        }
        seen: set[str] = set(); out: list[str] = []
        for line in lines:
            key = line.split("=", 1)[0] if "=" in line else ""
            if key in values:
                out.append(key + "=" + values[key]); seen.add(key)
            else:
                out.append(line)
        out.extend(k + "=" + v for k, v in values.items() if k not in seen)
        self.server_properties.write_text("\n".join(out) + "\n", encoding="utf-8")

    def start(self) -> None:
        self.assert_test_world_unlocked()
        self.proc = subprocess.Popen(
            ["cmd.exe", "/c", "gradlew.bat", "--no-daemon", "--console=plain", "runServer"],
            cwd=ROOT, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1,
            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
        )
        self.owned_pid = self.proc.pid
        assert self.proc.stdout is not None
        def reader() -> None:
            for line in self.proc.stdout:
                line = line.rstrip("\r\n")
                self.events.put(line); self.log(line)
        self.reader_thread = threading.Thread(target=reader, name=f"partialreload-reader-{self.proc.pid}", daemon=True)
        self.reader_thread.start()
        self.wait_log(r"Done \([0-9.]+s\)! For help", self.args.server_startup_timeout)
        deadline = time.time() + self.args.rcon_startup_timeout
        last: Exception | None = None
        while time.time() < deadline:
            try:
                self.rcon = RconClient("127.0.0.1", self.port, self.password, self.args.command_timeout)
                self.rcon.connect(); self.log("RCON_AUTHENTICATED"); return
            except (OSError, RconError) as exc:
                last = exc; time.sleep(0.5)
        raise RuntimeError(f"RCON startup timeout: {last}")

    def wait_log(self, pattern: str, timeout: float) -> str:
        rx = re.compile(pattern); end = time.time() + timeout
        while time.time() < end:
            try: line = self.events.get(timeout=0.25)
            except queue.Empty: continue
            if rx.search(line): return line
        raise TimeoutError(f"log timeout waiting for {pattern}")

    def command(self, command: str) -> str:
        assert self.rcon is not None
        self.log("> " + command)
        response = self.rcon.command(command)
        self.log(response.strip() or "<empty response>")
        return response

    def expect(self, step: str, command: str, pattern: str, timeout: float | None = None) -> str:
        timeout = timeout or self.args.command_timeout
        end = time.time() + timeout; last = ""
        response = self.command(command); last = response
        while not re.search(pattern, response, re.I | re.S) and time.time() < end:
            time.sleep(0.5); response = self.command(command); last = response
        if not re.search(pattern, response, re.I | re.S):
            raise AssertionError(f"{step}: expected {pattern!r}, observed {last!r}")
        self.results[step] = "passed"; return response

    def wait_state(self, state_pattern: str, timeout: float = 120) -> str:
        end = time.time() + timeout; response = ""
        while time.time() < end:
            response = self.command("partialreload status")
            if re.search(state_pattern, response, re.I) and not re.search(r"State:\s*SCANNING|State:\s*PREPARING", response, re.I):
                return response
            time.sleep(0.5)
        raise TimeoutError(f"state timeout waiting for {state_pattern}: {response!r}")

    def score(self, objective: str, player: str) -> int:
        response = self.command(f"scoreboard players get {player} {objective}")
        match = re.search(r"(?:has\s+)?(-?\d+)(?:\s|$)", response.strip())
        if not match: raise AssertionError(f"score {player}/{objective} unavailable: {response!r}")
        return int(match.group(1))

    def wait_score(self, objective: str, player: str, predicate: Callable[[int], bool],
                   timeout: float, interval: float = 0.1) -> int:
        deadline = time.monotonic() + timeout
        last: int | None = None
        while time.monotonic() < deadline:
            last = self.score(objective, player)
            if predicate(last):
                return last
            time.sleep(interval)
        raise AssertionError(f"score timeout {objective}/{player}: last={last!r}, timeout={timeout}s")

    def scores(self, *players: str) -> dict[str, int]:
        return {player: self.score("pr_acceptance", player) for player in players}

    def fingerprints(self) -> dict[str, int]:
        response = self.command("partialreload debug manager_fingerprints")
        values = {}
        for name in ("FunctionManager", "FunctionLibrary", "LootDataManager", "RecipeManager", "AdvancementManager"):
            match = re.search(rf"{name}:\s*(\d+)", response)
            if not match:
                raise AssertionError(f"missing manager fingerprint {name}: {response!r}")
            values[name] = int(match.group(1))
        return values

    def tick_window(self, before: dict[str, int], players: tuple[str, ...], seconds: float = 2.5) -> dict[str, int]:
        time.sleep(seconds)
        after = self.scores(*players)
        return {name: after[name] - before[name] for name in players}

    def shutdown(self) -> None:
        if self.rcon is not None:
            try: self.command("stop")
            except Exception as exc: self.log("stop failed: " + str(exc))
            self.rcon.close(); self.rcon = None
        if self.proc is not None:
            try: self.proc.wait(timeout=self.args.shutdown_timeout)
            except subprocess.TimeoutExpired:
                self.log("SHUTDOWN_FORCED owned_pid=" + str(self.proc.pid))
                # /T scopes termination to this wrapper's process tree.  Never
                # use taskkill /IM java.exe, because unrelated IDE/game JVMs
                # must remain untouched.
                subprocess.run(["taskkill", "/PID", str(self.proc.pid), "/T", "/F"],
                               check=False, capture_output=True, text=True)
                try: self.proc.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    self.proc.kill(); self.proc.wait(timeout=10)
            if self.proc.returncode != 0: raise RuntimeError(f"server exit code {self.proc.returncode}")
            if self.proc.stdout is not None:
                self.proc.stdout.close()
            if self.reader_thread is not None:
                self.reader_thread.join(timeout=5)
                if self.reader_thread.is_alive():
                    raise RuntimeError("owned output reader thread did not terminate")
            # DedicatedServer can leave the disposable userdev lock file after
            # a graceful stop.  The wrapper and its reader are already known
            # to be terminated here, so remove only this explicit test-world
            # lock; never touch a user world or a live server's lock.
            lock = ROOT / "run" / "world" / "session.lock"
            if lock.exists() and self.proc.poll() is not None:
                lock.unlink()
        self.results["shutdown"] = "passed"

    def run(self) -> None:
        install_generation("A", initial=True); self.configure_rcon(); self.start()
        try:
            self.expect("startup_status", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED|FUNCTION_COMMIT_COMPATIBLE")
            self.expect("startup_active", "partialreload active functions", r"Active functions:")
            # Establish generation A as the active baseline before changing the
            # filesystem to B; otherwise the first candidate scan would be the
            # only snapshot available for rollback retention.
            self.expect("baseline_a_scan", "partialreload scan", r"scan started|scan", 30)
            self.wait_state(r"Last scan:\s*(?!never)", 120)
            for objective in ("pr_acceptance",):
                self.command(f"scoreboard objectives add {objective} dummy")
            for player in ("load_guard", "scheduled_id", "scheduled_tag", "scheduled_removed",
                           "tick_a", "tick_b", "tick_retained"):
                self.command(f"scoreboard players set {player} pr_acceptance 0")
            managers_before = self.fingerprints()
            tick_players = ("tick_a", "tick_b", "tick_retained")
            tick_a_before = self.scores(*tick_players)
            tick_a_delta = self.tick_window(tick_a_before, tick_players)
            if not (tick_a_delta["tick_a"] > 0 and tick_a_delta["tick_b"] == 0
                    and tick_a_delta["tick_retained"] > 0):
                raise AssertionError(f"generation A tick deltas invalid: {tick_a_delta}")
            self.results["tick_generation_a"] = "passed"
            self.results["tick_retained_not_duplicated"] = "passed"
            self.command("function partialreload_test:behavior")
            if self.score("pr_acceptance", "result") != 1: raise AssertionError("generation A behavior mismatch")
            self.results["generation_a"] = "passed"
            install_generation("B")
            self.expect("prepare_b_scan", "partialreload scan", r"scan started|scan", 30)
            self.wait_state(r"Last scan:\s*(?!never)", 120)
            self.expect("prepare_b", "partialreload prepare functions", r"started|prepar|queued", 30)
            self.wait_state(r"State:\s*(READY|IDLE)", 120)
            self.expect("prepared_b", "partialreload prepared", r"PreparedFunctions|Technically applicable: true", 30)
            self.results["prepare_b"] = "passed"
            self.command("function partialreload_test:behavior")
            if self.score("pr_acceptance", "result") != 1: raise AssertionError("active generation changed before apply")
            # B is fully prepared while A remains active. Schedule only now,
            # immediately before apply, so the delay covers the safe point and
            # not the scan/preparation work.
            self.command("schedule function partialreload_test:scheduled_target 200t replace")
            self.command("schedule function #partialreload_test:scheduled_tag 200t replace")
            self.command("schedule function partialreload_test:removed 200t replace")
            queued = self.expect("commit_queued", "partialreload apply prepared", r"queued|safe point", 30)
            tx = self.expect("commit", "partialreload transaction", r"Status:\s*SUCCESS", 60)
            if not re.search(r"Mutation occurred:\s*true", tx, re.I): raise AssertionError("commit did not mutate")
            self.results["commit"] = "passed"
            managers_commit = self.fingerprints()
            if managers_commit["FunctionManager"] != managers_before["FunctionManager"]:
                raise AssertionError("function manager identity changed")
            if managers_commit["FunctionLibrary"] == managers_before["FunctionLibrary"]:
                raise AssertionError("function library did not change")
            for name in ("LootDataManager", "RecipeManager", "AdvancementManager"):
                if managers_commit[name] != managers_before[name]:
                    raise AssertionError(f"lateral manager changed: {name}")
            self.results.update({"function_manager_identity": "passed", "function_library_swap": "passed",
                                 "loot_manager_identity": "passed", "recipe_manager_identity": "passed",
                                 "advancement_manager_identity": "passed"})
            tick_b_before = self.scores(*tick_players)
            tick_b_delta = self.tick_window(tick_b_before, tick_players)
            if not (tick_b_delta["tick_a"] == 0 and tick_b_delta["tick_b"] > 0
                    and tick_b_delta["tick_retained"] > 0):
                raise AssertionError(f"generation B tick deltas invalid: {tick_b_delta}")
            self.results["tick_generation_b"] = "passed"
            self.command("function partialreload_test:behavior")
            if self.score("pr_acceptance", "result") != 2: raise AssertionError("generation B behavior mismatch")
            self.results["generation_b"] = "passed"
            self.command("function #partialreload_test:acceptance_tag")
            if self.score("pr_acceptance", "tag_result") != 2: raise AssertionError("generation B tag mismatch")
            if any(self.score("pr_acceptance", player) != 0 for player in ("scheduled_id", "scheduled_tag", "scheduled_removed")):
                raise AssertionError("SCHEDULE_FIRED_BEFORE_COMMIT")
            scheduled_id = self.wait_score("pr_acceptance", "scheduled_id", lambda value: value != 0, 20)
            scheduled_tag = self.wait_score("pr_acceptance", "scheduled_tag", lambda value: value != 0, 20)
            if scheduled_id != 2 or scheduled_tag != 2:
                raise AssertionError(f"schedule callbacks did not resolve active generation B: id={scheduled_id}, tag={scheduled_tag}")
            self.results.update({"schedule_id_after_commit": {"status": "passed", "observed": scheduled_id},
                                 "schedule_tag_after_commit": {"status": "passed", "observed": scheduled_tag},
                                 "schedule_id_captures_generation_a": {"status": "passed", "scheduled_while": "A", "active_when_fired": "B", "expected": 2, "observed": scheduled_id, "commit_completed_before_callback": True},
                                 "schedule_tag_captures_generation_a": {"status": "passed", "scheduled_while": "A", "active_when_fired": "B", "expected": 2, "observed": scheduled_tag, "commit_completed_before_callback": True},
                                 "schedule_queue_preserved": "passed"})
            if self.score("pr_acceptance", "scheduled_removed") != 0:
                raise AssertionError("removed schedule executed after target removal")
            self.results["schedule_removed_target"] = "passed"
            if self.score("pr_acceptance", "load_guard") != 0:
                raise AssertionError("load guard was executed")
            self.results["load_guard"] = "passed"
            self.expect("baseline_commit", "partialreload changed", r"Changed resources:", 120)
            self.results["baseline_commit"] = "passed"
            self.expect("loot_rejection_prepare", "partialreload prepare loot", r"started|prepar", 120)
            self.expect("loot_rejection", "partialreload apply prepared", r"Commit is not implemented for loot data", 30)
            self.command("partialreload discard")
            # Schedule while B is active, then roll back before its callback
            # fires.  The captured B implementation must still run.
            self.command("scoreboard players set scheduled_id pr_acceptance 0")
            self.command("schedule function partialreload_test:scheduled_target 200t replace")
            self.expect("rollback", "partialreload rollback functions", r"queued|Rollback", 30)
            tx2 = self.expect("rollback_status", "partialreload transaction", r"Status:\s*ROLLED_BACK", 60)
            if not re.search(r"Verification:\s*true", tx2, re.I): raise AssertionError("rollback verification failed")
            if self.score("pr_acceptance", "scheduled_id") != 0:
                raise AssertionError("schedule created in B fired before rollback completed")
            scheduled_after_rollback = self.wait_score("pr_acceptance", "scheduled_id", lambda value: value != 0, 20)
            if scheduled_after_rollback != 1:
                raise AssertionError("schedule created in B did not resolve active generation A after rollback")
            self.results["schedule_created_in_b_survives_rollback"] = {"status": "passed", "scheduled_while": "B", "active_when_fired": "A", "expected": 1, "observed": scheduled_after_rollback, "rollback_completed_before_callback": True}
            self.command("function partialreload_test:behavior")
            if self.score("pr_acceptance", "result") != 1: raise AssertionError("generation A not restored")
            self.results["generation_a_restored"] = "passed"
            self.command("scoreboard players set scheduled_id pr_acceptance 0")
            self.command("schedule function partialreload_test:scheduled_target 40t replace")
            scheduled_after_new = self.wait_score("pr_acceptance", "scheduled_id", lambda value: value != 0, 20)
            if scheduled_after_new != 1:
                raise AssertionError("schedule did not resolve restored generation A")
            self.results["schedule_created_after_rollback_uses_a"] = {"status": "passed", "expected": 1, "observed": scheduled_after_new}
            managers_rollback = self.fingerprints()
            if managers_rollback["FunctionManager"] != managers_before["FunctionManager"]:
                raise AssertionError("function manager identity changed after rollback")
            if managers_rollback["FunctionLibrary"] != managers_before["FunctionLibrary"]:
                raise AssertionError("function library was not restored")
            for name in ("LootDataManager", "RecipeManager", "AdvancementManager"):
                if managers_rollback[name] != managers_before[name]:
                    raise AssertionError(f"lateral manager changed after rollback: {name}")
            self.results.update({"function_library_rollback": "passed"})
            tick_r_before = self.scores(*tick_players)
            tick_r_delta = self.tick_window(tick_r_before, tick_players)
            if not (tick_r_delta["tick_a"] > 0 and tick_r_delta["tick_b"] == 0
                    and tick_r_delta["tick_retained"] > 0):
                raise AssertionError(f"rollback tick deltas invalid: {tick_r_delta}")
            self.results.update({"tick_rollback_a": "passed", "tick_retained_not_duplicated": "passed",
                                 "schedule_after_rollback": "passed", "loot_behavior_unchanged": "passed",
                                 "recipe_behavior_unchanged": "passed", "advancement_behavior_unchanged": "passed"})
            rollback_diff = self.expect("baseline_rollback", "partialreload changed", r"Changed resources:", 120)
            match = re.search(r"Changed resources:\s*(\d+)", rollback_diff)
            if not match or int(match.group(1)) == 0:
                raise AssertionError("generation B is not visible as pending after rollback")
            self.results["baseline_rollback"] = "passed"
        finally:
            try: self.shutdown()
            finally:
                if self.properties_backup.exists(): os.replace(self.properties_backup, self.server_properties)
                install_generation("A", initial=True)
                REPORT_JSON.parent.mkdir(parents=True, exist_ok=True)
                REPORT_JSON.write_text(json.dumps(self.results, indent=2) + "\n", encoding="utf-8")
                REPORT_LOG.write_text("\n".join(self.transcript) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-startup-timeout", type=float, default=180)
    parser.add_argument("--rcon-startup-timeout", type=float, default=30)
    parser.add_argument("--command-timeout", type=float, default=15)
    parser.add_argument("--shutdown-timeout", type=float, default=60)
    args = parser.parse_args()
    try:
        Acceptance(args).run(); print("DEDICATED_FUNCTION_ACCEPTANCE_PASSED"); return 0
    except Exception as exc:
        print(f"DEDICATED_FUNCTION_ACCEPTANCE_FAILED: {exc}", file=sys.stderr); return 1


if __name__ == "__main__":
    raise SystemExit(main())
