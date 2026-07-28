"""Real Forge client/server acceptance for the optional 4F-A handshake."""
from __future__ import annotations

import json
import os
import pathlib
import re
import shutil
import socket
import subprocess
import threading
import time
import uuid
import argparse
from dataclasses import dataclass, field

from minecraft_rcon import RconClient

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN_ROOT = ROOT / "run" / "handshake-acceptance" / uuid.uuid4().hex
REPORT = ROOT / "build" / "reports" / "client-handshake-foundation-acceptance.json"
LOG_ROOT = ROOT / "build" / "reports" / "client-handshake-foundation-acceptance"
MARKER = re.compile(r"(?P<marker>(?:CLIENT_HANDSHAKE|HANDSHAKE_ACCEPTANCE_CLIENT)_[A-Z_]+)(?:\s+|:|$)(?P<rest>.*)")
SERVER_MARKERS = {"CLIENT_HANDSHAKE_SERVER_ABSENT", "CLIENT_HANDSHAKE_SERVER_PENDING",
                  "CLIENT_HANDSHAKE_SERVER_COMPATIBLE", "CLIENT_HANDSHAKE_SERVER_INCOMPATIBLE",
                  "CLIENT_HANDSHAKE_SERVER_TIMED_OUT", "CLIENT_HANDSHAKE_SERVER_DISCONNECTED",
                  "CLIENT_HANDSHAKE_SERVER_DISCOVERING", "CLIENT_HANDSHAKE_SERVER_PRESENCE_RECEIVED"}


@dataclass(frozen=True)
class LoginEvidence:
    client_ready_seen: bool
    initial_connect_triggered: bool
    network_login_seen: bool
    server_pending_seen: bool
    server_compatible_seen: bool


@dataclass(frozen=True)
class AttemptWindow:
    server_start_line: int
    client_start_line: int
    server_end_line: int | None
    client_end_line: int | None


@dataclass(frozen=True)
class AttemptEvidence:
    client_ready_seen: bool
    connect_requested_seen: bool
    network_login_seen: bool
    server_absent_seen: bool
    server_pending_seen: bool
    server_compatible_seen: bool
    network_logout_seen: bool
    server_disconnected_seen: bool
    player: str | None
    connection: str | None


def entries_in_window(process: "OwnedProcess | None", start: int, end: int | None,
                      run_id: str | None = None, attempt_id: str | None = None) -> list[dict[str, object]]:
    if process is None:
        return []
    upper = len(process.lines) - 1 if end is None else end
    return [entry for entry in process.entries()
            if start < int(entry["line"]) <= upper
            and (run_id is None or fields(entry).get("run") == run_id)
            and (attempt_id is None or fields(entry).get("attempt") == attempt_id)]


def attempt_marker_evidence(server: "OwnedProcess | None", client: "OwnedProcess | None",
                            window: AttemptWindow, run_id: str, attempt_id: str) -> dict[str, object]:
    # Acceptance run/attempt fields exist only on helper client markers.  The
    # server deliberately cannot receive these identifiers over the protocol;
    # correlate its events by the attempt window and player/connection fields.
    server_entries = entries_in_window(server, window.server_start_line, window.server_end_line)
    client_entries = entries_in_window(client, window.client_start_line, window.client_end_line,
                                       run_id, attempt_id)
    server_markers = {entry["marker"] for entry in server_entries}
    client_markers = {entry["marker"] for entry in client_entries}
    return {
        "server_entries": server_entries,
        "client_entries": client_entries,
        "ready": "HANDSHAKE_ACCEPTANCE_CLIENT_READY" in client_markers,
        "connect_requested": "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" in client_markers,
        "network_login": "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" in client_markers,
        "server_absent": "CLIENT_HANDSHAKE_SERVER_ABSENT" in server_markers,
        "server_pending": "CLIENT_HANDSHAKE_SERVER_PENDING" in server_markers,
        "server_compatible": "CLIENT_HANDSHAKE_SERVER_COMPATIBLE" in server_markers,
        "network_logout": "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT" in client_markers,
        "server_disconnected": "CLIENT_HANDSHAKE_SERVER_DISCONNECTED" in server_markers,
    }


def attempt_evidence(server: "OwnedProcess | None", client: "OwnedProcess | None",
                     window: AttemptWindow, run_id: str, attempt_id: str,
                     expected_mode: str, expected_client_mod_mode: str) -> AttemptEvidence:
    evidence = attempt_marker_evidence(server, client, window, run_id, attempt_id)
    server_entries = evidence["server_entries"]
    absent = next((entry for entry in server_entries
                   if entry["marker"] in {"CLIENT_HANDSHAKE_SERVER_ABSENT",
                                           "CLIENT_HANDSHAKE_SERVER_PENDING"}), None)
    player = fields(absent).get("player") if absent else None
    connection = fields(absent).get("connection") if absent else None
    return AttemptEvidence(
        bool(evidence["ready"]), bool(evidence["connect_requested"]),
        bool(evidence["network_login"]), bool(evidence["server_absent"]),
        bool(evidence["server_pending"]), bool(evidence["server_compatible"]),
        bool(evidence["network_logout"]), bool(evidence["server_disconnected"]),
        player, connection)


def first_network_divergence(successful_lines: list[str], failed_lines: list[str]) -> dict[str, object]:
    def normalize(line: str) -> str:
        value = re.sub(r"\[[^]]+\]", "", line)
        value = re.sub(r"\b(?:run|attempt|challenge|player|connection|port|pid)=\S+", "", value)
        value = re.sub(r"\b\d{4,}\b", "<n>", value)
        value = re.sub(r"\s+", " ", value).strip()
        return value

    success = [(normalize(line), line) for line in successful_lines]
    failure = [(normalize(line), line) for line in failed_lines]
    success_set = {item[0] for item in success}
    failure_set = {item[0] for item in failure}
    common = [item[1] for item in success if item[0] in failure_set]
    success_only = next((raw for key, raw in success if key not in failure_set), None)
    failure_only = next((raw for key, raw in failure if key not in success_set), None)
    terminal = next((raw for raw in reversed(failed_lines)
                     if any(token in raw for token in ("Disconnected", "Timed out", "lost connection"))), None)
    return {"last_common_event": common[-1] if common else None,
            "first_success_only_event": success_only,
            "first_failure_only_event": failure_only,
            "failure_terminal_event": terminal}


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


@dataclass
class OwnedProcess:
    name: str
    command: list[str]
    env: dict[str, str]
    cwd: pathlib.Path
    log_path: pathlib.Path
    process: subprocess.Popen[str] | None = None
    thread: threading.Thread | None = None
    lines: list[str] = field(default_factory=list)

    def start(self) -> None:
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        self.process = subprocess.Popen(self.command, cwd=self.cwd, env=self.env,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, encoding="utf-8", errors="replace", bufsize=1)

        def read() -> None:
            assert self.process is not None and self.process.stdout is not None
            with self.log_path.open("w", encoding="utf-8") as output:
                for line in self.process.stdout:
                    self.lines.append(line.rstrip("\r\n"))
                    output.write(line)
                    output.flush()
        self.thread = threading.Thread(target=read, name=f"handshake-reader-{self.name}", daemon=False)
        self.thread.start()

    def cursor(self) -> int:
        return len(self.lines) - 1

    def wait_marker(self, marker: str, timeout: float = 60.0, after_line: int = -1,
                    expected_fields: dict[str, str] | None = None) -> dict[str, object]:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            values = marker_entries(self.lines, marker) if marker.startswith(("CLIENT_HANDSHAKE_", "HANDSHAKE_ACCEPTANCE_CLIENT_")) else [
                {"marker": marker, "line": index, "fields": {}}
                for index, line in enumerate(self.lines) if marker in line
            ]
            values = [value for value in values if int(value["line"]) > after_line
                      and (expected_fields is None or all(
                          fields(value).get(key) == expected for key, expected in expected_fields.items()))]
            if values:
                return values[0]
            if self.process is not None and self.process.poll() is not None:
                raise RuntimeError(f"{self.name} exited ({self.process.returncode}) before {marker}")
            time.sleep(.1)
        observed = [entry for entry in self.entries() if int(entry["line"]) > after_line]
        status = "running" if self.process is None or self.process.poll() is None else str(self.process.returncode)
        raise TimeoutError(
            f"{self.name} pid={self.process.pid if self.process else None}: timeout waiting for {marker}; "
            f"after_line={after_line}; running={status}; observed={observed}; tail={self.lines[-80:]}; log={self.log_path}")

    def stop(self, timeout: float = 40.0) -> int:
        if self.process is None:
            return 0
        if self.process.poll() is None:
            try:
                self.process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                subprocess.run(["taskkill", "/PID", str(self.process.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        self.process.wait(timeout=timeout)
        if self.thread is not None:
            self.thread.join(timeout=timeout)
            if self.thread.is_alive():
                raise RuntimeError(f"reader thread remains: {self.name}")
        return int(self.process.returncode or 0)

    def entries(self) -> list[dict[str, object]]:
        return all_marker_entries(self.lines)


def marker_entries(lines: list[str], marker: str) -> list[dict[str, object]]:
    return [entry for entry in all_marker_entries(lines) if entry["marker"] == marker]


def all_marker_entries(lines: list[str]) -> list[dict[str, object]]:
    result = []
    for index, line in enumerate(lines):
        match = MARKER.search(line)
        if not match:
            continue
        fields = {}
        for token in match.group("rest").split():
            if "=" in token:
                key, value = token.split("=", 1)
                fields[key] = value
        result.append({"marker": match.group("marker"), "line": index, "fields": fields})
    return result


def validate_report(report: dict[str, object]) -> tuple[bool, str]:
    required = {"compatible", "reconnect", "silent_timeout", "absent_client_allowed",
                "connected_commit_still_blocked"}
    scenarios = report.get("scenarios")
    if report.get("status") != "passed" or report.get("complete_run") is not True:
        return False, "status/complete_run"
    if not isinstance(scenarios, dict) or set(scenarios) != required:
        return False, "scenario set"
    for name in required:
        if scenarios[name].get("status") != "passed":
            return False, name
    compatible = scenarios["compatible"]
    reconnect = scenarios["reconnect"]
    if reconnect.get("same_client_process") is not True:
        return False, "reconnect process identity"
    if reconnect.get("reset_line") is None:
        return False, "missing client reset evidence"
    if not compatible.get("challenge") or not reconnect.get("challenge"):
        return False, "missing challenge evidence"
    if reconnect.get("challenge") == reconnect.get("previous_challenge"):
        return False, "reconnect challenge reused"
    if reconnect.get("connection") == reconnect.get("previous_connection"):
        return False, "reconnect connection reused"
    silent = scenarios["silent_timeout"]
    if silent.get("response_sent") is not False:
        return False, "SILENT response evidence"
    if scenarios["absent_client_allowed"].get("pending_seen") is True:
        return False, "ABSENT client acquired session"
    if report.get("cleanup", {}).get("status") != "passed":
        return False, "cleanup"
    return True, "ok"


def fields(entry: dict[str, object]) -> dict[str, str]:
    return entry["fields"]


def classify_failure(evidence: LoginEvidence, mode: str = "CONTROL") -> str:
    if not evidence.client_ready_seen and mode == "CONTROL":
        return "CLIENT_BOOT_NOT_READY"
    if not evidence.initial_connect_triggered:
        return "CLIENT_CONNECT_NOT_TRIGGERED"
    if not evidence.network_login_seen:
        return "FORGE_LOGIN_NOT_COMPLETED"
    if not evidence.server_pending_seen:
        return "PARTIALRELOAD_HANDSHAKE_NOT_STARTED"
    if not evidence.server_compatible_seen:
        return "PARTIALRELOAD_HANDSHAKE_FAILED"
    return "UNKNOWN_ACCEPTANCE_FAILURE"


def login_evidence(server: "OwnedProcess | None", client: "OwnedProcess | None",
                   mode: str = "CONTROL", server_port: int | None = None) -> LoginEvidence:
    client_markers = {e["marker"] for e in ([] if client is None else client.entries())}
    server_markers = {e["marker"] for e in ([] if server is None else server.entries())}
    triggered = "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" in client_markers
    if mode == "LAUNCH_ARGS" and client is not None and client.process is not None:
        tree = process_tree(client.process.pid)
        game = find_game_process(tree, "client")
        command = str(game.get("command_line") if game else "")
        triggered = bool(re.search(r"(?:^|\s)--server\s+127\.0\.0\.1(?:\s|$)", command)
                        and server_port is not None
                        and re.search(rf"(?:^|\s)--port\s+{int(server_port)}(?:\s|$)", command))
    return LoginEvidence(
        "HANDSHAKE_ACCEPTANCE_CLIENT_READY" in client_markers,
        triggered,
        "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" in client_markers,
        "CLIENT_HANDSHAKE_SERVER_PENDING" in server_markers,
        "CLIENT_HANDSHAKE_SERVER_COMPATIBLE" in server_markers)


def login_diagnostics(server: "OwnedProcess | None", client: "OwnedProcess | None") -> dict[str, object]:
    keywords = re.compile(r"Failed to connect|Connection Lost|Disconnected|Internal Exception|Exception|"
                          r"Mod mismatch|Channel|Registry|Handshake", re.IGNORECASE)
    client_lines = [] if client is None else client.lines
    server_lines = [] if server is None else server.lines
    client_entries = [] if client is None else client.entries()
    return {
        "client_tail": client_lines[-80:],
        "server_tail": server_lines[-80:],
        "client_error_candidates": [line for line in client_lines if keywords.search(line)][-40:],
        "server_error_candidates": [line for line in server_lines if keywords.search(line)][-40:],
        "client_ready_seen": any(e["marker"] == "HANDSHAKE_ACCEPTANCE_CLIENT_READY" for e in client_entries),
        "initial_connect_triggered": any(e["marker"] == "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" for e in client_entries),
        "network_login_seen": any(e["marker"] == "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" for e in client_entries),
    }


def descendant_processes(root_pid: int, processes: list[dict[str, object]]) -> list[dict[str, object]]:
    if root_pid <= 0:
        return []
    by_parent: dict[int, list[dict[str, object]]] = {}
    for item in processes:
        try:
            pid, parent = int(item.get("pid", 0)), int(item.get("parent_pid", 0))
        except (TypeError, ValueError):
            continue
        if pid > 0 and parent > 0:
            by_parent.setdefault(parent, []).append({"pid": pid, "parent_pid": parent,
                                                       "command_line": item.get("command_line"),
                                                       "creation_time": item.get("creation_time")})
    result: list[dict[str, object]] = []
    pending, seen = [root_pid], {root_pid}
    while pending:
        parent = pending.pop(0)
        for child in by_parent.get(parent, []):
            pid = int(child["pid"])
            if pid in seen:
                continue
            seen.add(pid)
            result.append(child)
            pending.append(pid)
    return result


def process_tree(root_pid: int | None) -> list[dict[str, object]]:
    if root_pid is None or root_pid <= 0:
        return []
    script = ("Get-CimInstance Win32_Process | "
              "Select-Object ProcessId,ParentProcessId,CommandLine,CreationDate | "
              "ConvertTo-Json -Compress")
    try:
        raw = subprocess.check_output(["powershell", "-NoProfile", "-Command", script], text=True,
                                      stderr=subprocess.DEVNULL)
        values = json.loads(raw) if raw.strip() else []
        values = values if isinstance(values, list) else [values]
        normalized = [{"pid": value.get("ProcessId"), "parent_pid": value.get("ParentProcessId"),
                       "command_line": value.get("CommandLine"), "creation_time": value.get("CreationDate")}
                      for value in values if isinstance(value, dict)]
        return descendant_processes(int(root_pid), normalized)
    except Exception:
        return []


def find_game_process(tree: list[dict[str, object]], role: str) -> dict[str, object] | None:
    if role not in {"client", "server"}:
        return None
    tokens = (("forgeclientuserdev", "net.minecraft.client.main.Main", "launchtarget forgeclient")
              if role == "client" else
              ("forgeserveruserdev", "net.minecraft.server.Main", "launchtarget forgeserver"))
    for item in tree:
        command = str(item.get("command_line") or "").lower()
        if any(token in command for token in tokens):
            return item
    return None


def launch_args_evidence(process: OwnedProcess | None, server_port: int) -> dict[str, object]:
    command = ""
    if process is not None and process.process is not None:
        game = find_game_process(process_tree(process.process.pid), "client")
        command = str(game.get("command_line") if game else "")
    server_match = re.search(r"(?:^|\s)--server\s+(\S+)", command)
    port_match = re.search(r"(?:^|\s)--port\s+(\d+)", command)
    return {"server_arg_present": server_match is not None,
            "server_value_matches": bool(server_match and server_match.group(1) == "127.0.0.1"),
            "port_arg_present": port_match is not None,
            "port_value_matches": bool(port_match and int(port_match.group(1)) == server_port),
            "launch_target": "forgeclientuserdev" if "forgeclientuserdev" in command else None}


def process_summary(process: OwnedProcess | None, role: str) -> dict[str, object]:
    if process is None or process.process is None:
        return {"wrapper_pid": None, "game_pid": None, "descendant_pids": []}
    tree = process_tree(process.process.pid)
    game = find_game_process(tree, role)
    return {"wrapper_pid": process.process.pid, "game_pid": game.get("pid") if game else None,
            "descendant_pids": [item.get("pid") for item in tree]}


def capture_thread_dumps(run_log_root: pathlib.Path, client: OwnedProcess | None,
                         server: OwnedProcess | None) -> list[str]:
    errors: list[str] = []
    dump_root = run_log_root / "thread-dumps"
    dump_root.mkdir(parents=True, exist_ok=True)
    java_home_value = os.environ.get("JAVA_HOME", "")
    java_home = pathlib.Path(java_home_value) if java_home_value else pathlib.Path()
    if not java_home_value:
        candidates = sorted(pathlib.Path(os.environ.get("USERPROFILE", ""), ".gradle", "jdks").glob("*/jdk-*"),
                            key=lambda path: (path / "bin" / "jcmd.exe").exists(), reverse=True)
        if candidates:
            java_home = candidates[0]
    jcmd = java_home / "bin" / "jcmd.exe"
    if not jcmd.exists():
        jcmd = java_home / "bin" / "jstack.exe"
    if not jcmd.exists():
        errors.append("same JDK jcmd/jstack unavailable")
        return errors
    for label, process in (("client", client), ("server", server)):
        if process is None or process.process is None:
            continue
        tree = process_tree(process.process.pid)
        game_info = find_game_process(tree, "client" if label == "client" else "server")
        if game_info is None:
            errors.append(f"{label}: OWNED_GAME_PROCESS_NOT_FOUND")
            continue
        game = int(game_info["pid"])
        try:
            commands = [("thread", ["Thread.print", "-l"]),
                        ("command-line", ["VM.command_line"])]
            if label == "client":
                commands.append(("system-properties", ["VM.system_properties"]))
            for suffix, arguments in commands:
                output = subprocess.run([str(jcmd), str(game), *arguments], text=True,
                                        capture_output=True, timeout=30, check=False)
                text = output.stdout
                if label == "client" and suffix == "system-properties":
                    text = "\n".join(line for line in text.splitlines()
                                       if not any(secret in line.lower() for secret in
                                                  ("rcon.password", "accessToken", "session", "token")))
                (dump_root / f"{label}-{suffix}.txt").write_text(text, encoding="utf-8")
                if output.returncode != 0:
                    errors.append(f"{label} {suffix}: exit={output.returncode}: {output.stderr.strip()}")
        except Exception as exc:
            errors.append(f"{label}: {exc}")
    return errors


def capture_tcp_state(run_log_root: pathlib.Path, server_port: int,
                      client: OwnedProcess | None, server: OwnedProcess | None,
                      filename: str = "tcp-state.json") -> dict[str, object]:
    """Capture only connections owned by this acceptance and the server port."""
    pids = set()
    for process in (client, server):
        if process is not None and process.process is not None:
            pids.update(int(item["pid"]) for item in process_tree(process.process.pid))
            pids.add(process.process.pid)
    script = ("Get-NetTCPConnection -ErrorAction SilentlyContinue | "
              "Select-Object LocalAddress,LocalPort,RemoteAddress,RemotePort,State,OwningProcess | "
              "ConvertTo-Json -Compress")
    try:
        raw = subprocess.check_output(["powershell", "-NoProfile", "-Command", script],
                                      text=True, stderr=subprocess.DEVNULL)
        values = json.loads(raw) if raw.strip() else []
        values = values if isinstance(values, list) else [values]
        filtered = [value for value in values
                    if int(value.get("OwningProcess", -1)) in pids
                    and (int(value.get("LocalPort", -1)) == server_port
                         or int(value.get("RemotePort", -1)) == server_port)]
        result = {"entries": filtered, "server_port": server_port}
    except Exception as exc:
        result = {"entries": [], "server_port": server_port, "error": str(exc)}
    (run_log_root / filename).write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


class Acceptance:
    def __init__(self, initial_connect_mode: str = "CONTROL", cold_login_probes: int = 0,
                 client_mod_mode: str = "with_mod", strict_client_isolation: bool = False,
                 require_attempt_cleanup: bool = False, fresh_server_per_probe: bool = False,
                 cycles: int = 0, server_mod_mode: str = "with_mod",
                 server_smoke_only: bool = False, required_valid_trials: int = 0,
                 maximum_launch_attempts: int = 0) -> None:
        self.run_id = uuid.uuid4().hex
        self.run_log_root = LOG_ROOT / self.run_id
        self.server_port, self.rcon_port = free_port(), free_port()
        self.server_directory_name = "server"
        self.password = uuid.uuid4().hex
        self.server: OwnedProcess | None = None
        self.clients: list[OwnedProcess] = []
        self.rcon: RconClient | None = None
        self.scenarios: dict[str, dict[str, object]] = {}
        self.cleanup_result: dict[str, object] = {"status": "failed"}
        self.attempt_ids: dict[str, str] = {}
        self.initial_connect_mode = initial_connect_mode
        self.cold_login_probes = cold_login_probes
        self.client_mod_mode = client_mod_mode
        self.strict_client_isolation = strict_client_isolation
        self.require_attempt_cleanup = require_attempt_cleanup
        self.fresh_server_per_probe = fresh_server_per_probe
        self.cycles = cycles
        self.server_mod_mode = server_mod_mode
        self.server_task = "runServer"
        self.server_build_mode = "root_gradle" if server_mod_mode == "with_mod" else "independent_gradle_build"
        self.server_project_directory = "." if server_mod_mode == "with_mod" else "acceptance/forge-control-server"
        self.server_smoke_only = server_smoke_only
        self.required_valid_trials = required_valid_trials
        self.maximum_launch_attempts = maximum_launch_attempts
        self.failure_capture_errors: list[str] = []
        self.failure_tcp_state: dict[str, object] = {}
        self.failure_process_tree: dict[str, object] = {}

    def env(self) -> dict[str, str]:
        result = os.environ.copy()
        if not result.get("JAVA_HOME"):
            jdks = sorted((path for path in pathlib.Path(result.get("USERPROFILE", ""), ".gradle", "jdks").glob("*/jdk-*")
                           if (path / "bin" / "java.exe").exists()), reverse=True)
            if jdks:
                result["JAVA_HOME"] = str(jdks[0])
        options = result.get("JAVA_TOOL_OPTIONS", "") + " -Dpartialreload.handshake.acceptance=true"
        cache = pathlib.Path(os.environ.get("USERPROFILE", "")) / ".gradle" / "caches" / "modules-2" / "files-2.1"
        module_jars = []
        for pattern in ("cpw.mods.bootstraplauncher", "cpw.mods.securejarhandler",
                        "org.ow2.asm", "net.minecraftforge.JarJarFileSystems"):
            root = cache / pattern
            if root.exists():
                module_jars.extend(str(path) for path in root.rglob("*.jar")
                                   if "sources" not in path.name and "javadoc" not in path.name)
        options += " --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.util.jar=ALL-UNNAMED"
        result["JAVA_TOOL_OPTIONS"] = options.strip()
        # The module path is supplied by the ForgeGradle run task itself. It
        # must not be put in JDK_JAVA_OPTIONS because that environment is also
        # consumed by the Gradle launcher and would corrupt its classpath.
        result.pop("JDK_JAVA_OPTIONS", None)
        return result

    def prepare_server(self) -> None:
        directory = RUN_ROOT / self.server_directory_name
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "server.properties").write_text("\n".join([
            "online-mode=false", "enable-rcon=true", "server-ip=127.0.0.1",
            f"server-port={self.server_port}", f"rcon.port={self.rcon_port}",
            f"rcon.password={self.password}", "enable-command-block=true", "spawn-protection=0", ""]),
            encoding="utf-8")
        (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")

    def start_server(self) -> None:
        self.prepare_server()
        command = [str(ROOT / "gradlew.bat")]
        if self.server_mod_mode == "without_mod":
            control_build = ROOT / "acceptance" / "forge-control-server"
            (control_build / "build" / "classes" / "java" / "main").mkdir(parents=True, exist_ok=True)
            (control_build / "build" / "resources" / "main").mkdir(parents=True, exist_ok=True)
            versions_task = [str(ROOT / "gradlew.bat"), "--no-daemon", "--console=plain",
                             "reportHandshakeAcceptanceVersions"]
            subprocess.run(versions_task, cwd=ROOT, env=self.env(), check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
            versions_path = ROOT / "build" / "reports" / "handshake-acceptance-versions.json"
            versions = json.loads(versions_path.read_text(encoding="utf-8"))
            required = ("minecraft_version", "forge_version", "mapping_channel", "mapping_version")
            if any(not str(versions.get(key, "")).strip() for key in required):
                raise RuntimeError("Handshake acceptance versions report is incomplete")
            command += ["-p", str(ROOT / "acceptance" / "forge-control-server"), "--no-daemon", "--console=plain"]
            command += [f"-P{key}={versions[key]}" for key in required]
        else:
            command += ["--no-daemon", "--console=plain"]
        command += [self.server_task]
        server_env = self.env()
        server_env["PARTIALRELOAD_ACCEPTANCE_RUN_ID"] = self.run_id
        server_env["PARTIALRELOAD_ACCEPTANCE_RUN_DIR"] = str(RUN_ROOT / self.server_directory_name)
        self.server = OwnedProcess("server", command, server_env, ROOT,
                                   self.run_log_root / "server.stdout.log")
        self.server.start()
        if self.server_mod_mode == "with_mod":
            self.server.wait_marker("CLIENT_HANDSHAKE_FOUNDATION_CHANNEL_REGISTERED", 180)
        self.server.wait_marker("Done", 180)
        if self.server_mod_mode == "without_mod":
            self.inspect_control_classpath()
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                self.rcon = RconClient("127.0.0.1", self.rcon_port, self.password, timeout=5)
                self.rcon.connect()
                return
            except Exception:
                time.sleep(.5)
        raise TimeoutError("RCON unavailable")

    def inspect_control_classpath(self) -> dict[str, object]:
        classpath_file = ROOT / "acceptance" / "forge-control-server" / "build" / "classpath" / "runServer_minecraftClasspath.txt"
        entries = []
        if classpath_file.exists():
            entries = [line.strip() for line in classpath_file.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip()]
        root_main = str((ROOT / "build" / "classes" / "java" / "main")).lower()
        root_resources = str((ROOT / "build" / "resources" / "main")).lower()
        forbidden = [entry for entry in entries if entry.lower().startswith(root_main) or entry.lower().startswith(root_resources)
                     or (entry.lower().endswith("partialreload.jar") and str(ROOT).lower() in entry.lower())]
        result = {"game_pid_found": self.server is not None, "argfiles_expanded": classpath_file.exists(),
                  "legacy_classpath_found": bool(entries), "forbidden_entries": forbidden,
                  "partialreload_module_present": any("partialreload" in entry.lower() for entry in entries),
                  "isolated": classpath_file.exists() and not forbidden and not any("partialreload" in entry.lower() for entry in entries)}
        report = ROOT / "build" / "reports" / "control-server-effective-classpath.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2), encoding="utf-8")
        return result

    def start_client(self, name: str, username: str, *, with_mod: bool = True,
                     mode: str = "NORMAL") -> OwnedProcess:
        environment = self.env()
        directory = RUN_ROOT / name
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True, exist_ok=True)
        attempt_id = uuid.uuid4().hex
        self.attempt_ids[name] = attempt_id
        # Forge's first-run accessibility onboarding otherwise blocks the real title screen.
        # This file is owned by the disposable acceptance directory.
        (directory / "options.txt").write_text("onboardAccessibility:false\n", encoding="utf-8")
        environment.update({"PARTIALRELOAD_ACCEPTANCE_HOST": "127.0.0.1",
                            "PARTIALRELOAD_ACCEPTANCE_PORT": str(self.server_port),
                            "PARTIALRELOAD_ACCEPTANCE_USERNAME": username,
                            "PARTIALRELOAD_ACCEPTANCE_RUN_DIR": str(directory),
                            "PARTIALRELOAD_ACCEPTANCE_WITH_MOD": "true" if with_mod else "false",
                            "PARTIALRELOAD_ACCEPTANCE_CONTROL_DIR": str(directory / "control"),
                            "PARTIALRELOAD_ACCEPTANCE_INITIAL_CONNECT_MODE": self.initial_connect_mode,
                            "PARTIALRELOAD_ACCEPTANCE_RUN_ID": self.run_id,
                            "PARTIALRELOAD_ACCEPTANCE_ATTEMPT_ID": attempt_id})
        if mode != "NORMAL":
            environment["JAVA_TOOL_OPTIONS"] += f" -Dpartialreload.handshake.acceptance.mode={mode}"
        task = "runClient"
        process = OwnedProcess(name, [str(ROOT / "gradlew.bat"), "--no-daemon", "--console=plain", task],
                               environment, ROOT, self.run_log_root / f"{name}-control.stdout.log")
        process.start()
        self.clients.append(process)
        return process

    def cleanup_attempt(self, client: OwnedProcess, name: str, username: str,
                        entered_server: bool, expect_server_disconnect: bool) -> dict[str, object]:
        control = RUN_ROOT / name / "control"
        control.mkdir(parents=True, exist_ok=True)
        client_cursor = client.cursor()
        server_cursor = self.server.cursor()
        (control / "exit.request").write_text("exit\n", encoding="utf-8")
        exit_seen = True
        try:
            client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_EXIT_REQUESTED", 20, client_cursor)
        except Exception:
            exit_seen = False
        logout_seen = not entered_server
        server_disconnected_seen = not expect_server_disconnect
        if entered_server:
            try:
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT", 30, client_cursor)
                logout_seen = True
            except Exception:
                logout_seen = False
            if expect_server_disconnect:
                try:
                    self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_DISCONNECTED", 30, server_cursor)
                    server_disconnected_seen = True
                except Exception:
                    server_disconnected_seen = False
        player_absent = True
        if self.rcon is not None:
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                try:
                    listing = self.rcon.command("list")
                    if username not in listing:
                        break
                except Exception:
                    pass
                time.sleep(.25)
            else:
                player_absent = False
        try:
            client.stop()
        except Exception:
            pass
        owned_absent = client.process is not None and client.process.poll() is not None
        tcp = capture_tcp_state(self.run_log_root, self.server_port, client, self.server)
        active_states = {"ESTABLISHED", "SYN_SENT", "SYN_RECEIVED", "CLOSE_WAIT",
                         "FIN_WAIT_1", "FIN_WAIT_2"}
        tcp_absent = not any(str(entry.get("State", "")).upper() in active_states
                             for entry in tcp.get("entries", []))
        reader_stopped = client.thread is None or not client.thread.is_alive()
        status = all((exit_seen, logout_seen, server_disconnected_seen, player_absent,
                      owned_absent, tcp_absent, reader_stopped))
        return {"status": "passed" if status else "failed",
                "client_logout_seen": logout_seen,
                "server_disconnect_seen": server_disconnected_seen,
                "player_absent_from_rcon": player_absent,
                "owned_processes_absent": owned_absent,
                "tcp_connections_absent": tcp_absent,
                "reader_threads_stopped": reader_stopped,
                "exit_requested_seen": exit_seen}

    @staticmethod
    def challenge(entry: dict[str, object]) -> str | None:
        value = fields(entry).get("challenge")
        return value if value and value != "-" else None

    def compatible(self) -> None:
        client = self.start_client("compatible-reconnect", "PRCompat")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 120)
        client_cursor = client.cursor()
        server_cursor = self.server.cursor()
        control = RUN_ROOT / "compatible-reconnect" / "control"
        control.mkdir(parents=True, exist_ok=True)
        (control / "connect.request").write_text("connect\n", encoding="utf-8")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, client_cursor)
        network_login = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, client_cursor)
        pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 90, server_cursor)
        server_ok = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_COMPATIBLE", 90, int(pending["line"]))
        received = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED", 90, client_cursor)
        sent = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_SENT", 90, int(received["line"]))
        accepted = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_ACCEPTED", 90, int(sent["line"]))
        client_ok = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_COMPATIBLE", 90, int(accepted["line"]))
        challenge = self.challenge(pending)
        if not challenge or any(self.challenge(item) != challenge for item in (server_ok, received, sent, accepted, client_ok)):
            raise AssertionError("challenge mismatch in compatible")
        self.scenarios["compatible"] = {"status": "passed", "challenge": challenge,
                                         "connection": fields(pending).get("connection"),
                                         "player": fields(pending).get("player"),
                                         "client_ready_seen": True,
                                         "network_login_seen": True,
                                         "network_login_line": int(network_login["line"]),
                                         "client_pid": client.process.pid if client.process else None,
                                         "server_log": str(self.server.log_path),
                                         "client_log": str(client.log_path)}
        self.reconnect_client = client

    def reconnect(self) -> None:
        client = self.reconnect_client
        old = self.scenarios["compatible"]
        control = RUN_ROOT / "compatible-reconnect" / "control"
        control.mkdir(parents=True, exist_ok=True)
        server_cursor = self.server.cursor(); client_cursor = client.cursor()
        (control / "disconnect.request").write_text("disconnect\n", encoding="utf-8")
        requested = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECT_REQUESTED", 60, client_cursor)
        reset = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_RESET", 60, int(requested["line"]))
        logout = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT", 60, int(requested["line"]))
        disconnected = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_DISCONNECTED", 60, server_cursor)
        # RECONNECT_READY may be emitted while the logout/disconnect waits are
        # in progress.  Keep the cursor captured before disconnect and search
        # from that causal point instead of taking a new cursor afterwards.
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_READY", 60, client_cursor)
        reconnect_trigger_cursor = client.cursor()
        server_reconnect_cursor = self.server.cursor()
        (control / "reconnect.request").write_text("reconnect\n", encoding="utf-8")
        reconnect_requested = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_REQUESTED", 60,
                                                  reconnect_trigger_cursor)
        pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 90, server_reconnect_cursor)
        server_ok = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_COMPATIBLE", 90, int(pending["line"]))
        received = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED", 90, reconnect_requested["line"])
        sent = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_SENT", 90, int(received["line"]))
        accepted = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_ACCEPTED", 90, int(sent["line"]))
        client_ok = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_COMPATIBLE", 90, int(accepted["line"]))
        if self.challenge(server_ok) != self.challenge(pending) or any(
                self.challenge(item) != self.challenge(pending) for item in (received, sent, accepted, client_ok)):
            raise AssertionError("reconnect challenge mismatch")
        if self.challenge(pending) == old["challenge"] or fields(pending).get("connection") == old["connection"]:
            raise AssertionError("reconnect reused challenge or connection")
        if int(reset["line"]) <= int(requested["line"]):
            raise AssertionError("client reset did not follow disconnect")
        self.scenarios["reconnect"] = {"status": "passed", "challenge": self.challenge(pending),
                                        "previous_challenge": old["challenge"],
                                        "previous_connection": old["connection"],
                                        "connection": fields(pending).get("connection"),
                                        "same_client_process": True,
                                        "reset_line": int(reset["line"]),
                                        "network_logout_line": int(logout["line"]),
                                        "server_log": str(self.server.log_path),
                                        "client_log": str(client.log_path)}

    def silent_timeout(self) -> None:
        client = self.start_client("silent-timeout", "PRSilent", mode="SILENT")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 120)
        client_cursor = client.cursor(); previous_pending_line = self.server.cursor()
        control = RUN_ROOT / "silent-timeout" / "control"
        control.mkdir(parents=True, exist_ok=True)
        (control / "connect.request").write_text("connect\n", encoding="utf-8")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, client_cursor)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, client_cursor)
        pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 90, previous_pending_line)
        received = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED", 90, client_cursor)
        timed = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_TIMED_OUT", 40, int(pending["line"]))
        challenge = self.challenge(pending)
        if self.challenge(timed) != challenge or fields(timed).get("error") != "TAG_RECIPE_CLIENT_READY_TIMEOUT":
            raise AssertionError("silent timeout evidence mismatch")
        if any(entry["marker"] in {"CLIENT_HANDSHAKE_CLIENT_HELLO_SENT", "CLIENT_HANDSHAKE_CLIENT_ACCEPTED",
                                   "CLIENT_HANDSHAKE_CLIENT_COMPATIBLE"} for entry in client.entries()):
            raise AssertionError("SILENT client sent a handshake response")
        self.scenarios["silent_timeout"] = {"status": "passed", "challenge": challenge,
                                             "response_sent": False,
                                             "server_marker": timed, "client_marker": received,
                                             "server_log": str(self.server.log_path),
                                             "client_log": str(client.log_path)}
        client.stop()

    def absent(self) -> None:
        client = self.start_client("absent", "PRAbsent", with_mod=False)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 120)
        client_cursor = client.cursor(); server_cursor = self.server.cursor()
        control = RUN_ROOT / "absent" / "control"
        control.mkdir(parents=True, exist_ok=True)
        (control / "connect.request").write_text("connect\n", encoding="utf-8")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, client_cursor)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, client_cursor)
        absent = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_ABSENT", 120, server_cursor)
        player = fields(absent).get("player")
        if self.rcon is not None:
            listing = self.rcon.command("list")
            if "PRAbsent" not in listing:
                raise AssertionError("absent client was not present in RCON list")
        if any(entry["marker"] in SERVER_MARKERS - {"CLIENT_HANDSHAKE_SERVER_ABSENT",
                                                     "CLIENT_HANDSHAKE_SERVER_DISCONNECTED"}
               and fields(entry).get("player") == player for entry in self.server.entries()):
            raise AssertionError("absent client acquired a handshake session")
        self.scenarios["absent_client_allowed"] = {"status": "passed", "server_marker": absent,
                                                    "pending_seen": False,
                                                    "server_log": str(self.server.log_path),
                                                    "client_log": str(client.log_path)}
        cleanup = self.cleanup_attempt(client, "absent", "PRAbsent", True, True)
        self.scenarios["absent_client_allowed"]["cleanup"] = cleanup
        if cleanup["status"] != "passed":
            self.scenarios["absent_client_allowed"]["status"] = "failed"
            raise AssertionError("absent client cleanup failed")

    def connected_commit(self) -> None:
        if self.rcon is None:
            raise RuntimeError("RCON unavailable")
        response = self.rcon.command("partialreload apply prepared")
        if "TAG_RECIPE_COMMIT_PLAYERS_CONNECTED" not in response:
            raise AssertionError(f"commit was not blocked: {response}")
        self.scenarios["connected_commit_still_blocked"] = {"status": "passed", "response": response.strip()}

    def absent_reconnect_stress(self) -> None:
        cycles = self.cycles or 5
        client = self.start_client("absent-reconnect-stress", "PRAbsentStress", with_mod=False)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 120)
        control = RUN_ROOT / "absent-reconnect-stress" / "control"
        control.mkdir(parents=True, exist_ok=True)
        connections: list[str] = []
        for cycle in range(cycles):
            client_cursor = client.cursor()
            server_cursor = self.server.cursor()
            (control / ("connect.request" if cycle == 0 else "reconnect.request")).write_text(
                "connect\n", encoding="utf-8")
            if cycle == 0:
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, client_cursor)
            else:
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_REQUESTED", 60, client_cursor)
            client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, client_cursor)
            absent = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_ABSENT", 120, server_cursor)
            connection = fields(absent).get("connection")
            if not connection or connection in connections:
                raise AssertionError("absent reconnect reused connection identity")
            connections.append(connection)
            if any(entry["marker"] in {"CLIENT_HANDSHAKE_SERVER_PENDING",
                                       "CLIENT_HANDSHAKE_SERVER_COMPATIBLE",
                                       "CLIENT_HANDSHAKE_SERVER_TIMED_OUT"}
                   and fields(entry).get("player") == fields(absent).get("player")
                   for entry in self.server.entries()):
                raise AssertionError("absent reconnect acquired a handshake session")
            if cycle < cycles - 1:
                disconnect_cursor = client.cursor(); server_disconnect_cursor = self.server.cursor()
                (control / "disconnect.request").write_text("disconnect\n", encoding="utf-8")
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECT_REQUESTED", 60, disconnect_cursor)
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT", 60, disconnect_cursor)
                self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_DISCONNECTED", 60, server_disconnect_cursor)
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_READY", 60,
                                   disconnect_cursor)
        cleanup = self.cleanup_attempt(client, "absent-reconnect-stress", "PRAbsentStress", True, False)
        self.scenarios["absent_reconnect_stress"] = {
            "status": "passed" if cleanup["status"] == "passed" else "failed",
            "same_client_process": True, "cycles": cycles,
            "connections": connections, "cleanup": cleanup,
            "client_log": str(client.log_path), "server_log": str(self.server.log_path)}

    def cold_login(self) -> None:
        attempts = []
        for index in range(1, self.cold_login_probes + 1):
            if index > 1 and self.fresh_server_per_probe:
                if self.rcon is not None:
                    self.rcon.close()
                    self.rcon = None
                if self.server is not None:
                    self.server.stop()
                    self.server = None
                self.server_port, self.rcon_port = free_port(), free_port()
                self.server_directory_name = f"server-{index:02d}"
                self.start_server()
            name = f"cold-{index:02d}"
            username = (f"PRWith{index:02d}" if self.client_mod_mode == "with_mod"
                        else f"PRBase{index:02d}")
            server_start_line = self.server.cursor()
            client = self.start_client(name, username,
                                       with_mod=self.client_mod_mode == "with_mod")
            client_start_line = client.cursor()
            try:
                ready = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 90)
                client_cursor = client.cursor()
                server_cursor = self.server.cursor()
                capture_tcp_state(self.run_log_root, self.server_port, client, self.server,
                                  f"tcp-before-connect-{name}.json")
                if self.initial_connect_mode == "CONTROL":
                    control = RUN_ROOT / name / "control"
                    control.mkdir(parents=True, exist_ok=True)
                    (control / "connect.request").write_text("connect\n", encoding="utf-8")
                    client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, client_cursor)
                    capture_tcp_state(self.run_log_root, self.server_port, client, self.server,
                                      f"tcp-after-connect-request-{name}.json")
                login = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 60, client_cursor)
                if self.client_mod_mode == "without_mod":
                    absent = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_ABSENT", 60, server_cursor)
                    attempts.append({"attempt": index, "status": "passed", "ready": ready,
                                     "login": login, "absent": absent, "log": str(client.log_path)})
                else:
                    pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 60, server_cursor)
                    compatible = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_COMPATIBLE", 60, int(pending["line"]))
                    attempts.append({"attempt": index, "status": "passed", "ready": ready,
                                     "login": login, "pending": pending, "compatible": compatible,
                                     "log": str(client.log_path)})
            except Exception as exc:
                attempts.append({"attempt": index, "status": "failed", "error": str(exc),
                                 "log": str(client.log_path), "attempt_id": self.attempt_ids.get(name)})
                self.scenarios["cold_login"] = {"status": "failed", "mode": self.initial_connect_mode,
                                                 "attempts": attempts, "attempt_count": len(attempts),
                                                 "passed": sum(item.get("status") == "passed" for item in attempts)}
                # Continue with the next fresh client when this attempt cleaned up.
            finally:
                window = AttemptWindow(server_start_line, client_start_line,
                                       self.server.cursor(), client.cursor())
                capture_tcp_state(self.run_log_root, self.server_port, client, self.server,
                                  f"tcp-before-cleanup-{name}.json")
                evidence = attempt_evidence(
                    self.server, client, window, self.run_id,
                    self.attempt_ids.get(name, ""), self.initial_connect_mode,
                    self.client_mod_mode)
                if attempts and attempts[-1].get("status") == "failed":
                    launch = launch_args_evidence(client, self.server_port)
                    classification = classify_failure(LoginEvidence(
                        evidence.client_ready_seen, evidence.connect_requested_seen,
                        evidence.network_login_seen, evidence.server_pending_seen,
                        evidence.server_compatible_seen), self.initial_connect_mode)
                    if (self.initial_connect_mode == "LAUNCH_ARGS"
                            and all(launch.get(key) for key in
                                    ("server_arg_present", "server_value_matches",
                                     "port_arg_present", "port_value_matches"))
                            and not evidence.network_login_seen):
                        classification = "LAUNCH_ARGS_PROPAGATED_BUT_NATIVE_CONNECT_NOT_STARTED"
                    attempts[-1]["classification"] = classification
                    attempts[-1]["login_diagnostics"] = login_diagnostics(self.server, client)
                    attempts[-1]["launch_args"] = launch
                    attempts[-1]["processes"] = {
                        "client": process_summary(client, "client"),
                        "server": process_summary(self.server, "server")}
                    attempts[-1]["window"] = {
                        "server_start_line": window.server_start_line,
                        "client_start_line": window.client_start_line,
                        "server_end_line": window.server_end_line,
                        "client_end_line": window.client_end_line}
                if evidence.connect_requested_seen and not evidence.network_login_seen:
                    self.failure_capture_errors = capture_thread_dumps(self.run_log_root, client, self.server)
                    self.failure_tcp_state = capture_tcp_state(self.run_log_root, self.server_port, client, self.server)
                    self.failure_process_tree = {
                        "client": process_tree(client.process.pid) if client.process else [],
                        "server": process_tree(self.server.process.pid) if self.server and self.server.process else []}
                entered = evidence.network_login_seen
                attempt_cleanup = self.cleanup_attempt(
                    client, name, username, entered,
                    entered)
                if attempts:
                    attempts[-1]["cleanup"] = attempt_cleanup
                    final_window = AttemptWindow(window.server_start_line, window.client_start_line,
                                                 self.server.cursor(), client.cursor())
                    attempts[-1]["attempt_evidence"] = attempt_marker_evidence(
                        self.server, client, final_window, self.run_id, self.attempt_ids.get(name, ""))
                    if attempt_cleanup["status"] != "passed":
                        attempts[-1]["status"] = "failed"
                        attempts[-1]["classification"] = "ATTEMPT_CLEANUP_FAILED"
        passed_count = sum(item.get("status") == "passed" for item in attempts)
        self.scenarios["cold_login"] = {"status": "passed" if passed_count == self.cold_login_probes else "failed",
                                         "client_mod_mode": self.client_mod_mode,
                                         "mode": self.initial_connect_mode,
                                         "attempts": attempts, "attempt_count": len(attempts),
                                         "passed": passed_count,
                                         "failed": len(attempts) - passed_count}

    def cleanup(self) -> None:
        errors = []
        for client in reversed(self.clients):
            try:
                client.stop()
            except Exception as exc:
                errors.append(str(exc))
        if self.rcon is not None:
            try:
                self.rcon.command("stop")
            except Exception:
                pass
            self.rcon.close()
        if self.server is not None:
            try:
                self.server.stop()
            except Exception as exc:
                errors.append(str(exc))
        try:
            if RUN_ROOT.exists():
                deadline = time.monotonic() + 15
                while RUN_ROOT.exists() and time.monotonic() < deadline:
                    try:
                        shutil.rmtree(RUN_ROOT)
                    except OSError as exc:
                        last_error = exc
                        time.sleep(.5)
                if RUN_ROOT.exists():
                    errors.append(str(locals().get("last_error", "owned run root remains")))
        except Exception as exc:
            errors.append(str(exc))
        self.cleanup_result = {"status": "passed" if not errors else "failed",
                               "owned_processes_absent": all(item.process is None or item.process.poll() is not None
                                                              for item in [self.server, *self.clients]),
                               "rcon_port_released": not port_open(self.rcon_port), "errors": errors}

    def run(self, selected: set[str] | None = None) -> dict[str, object]:
        self.start_server()
        if self.server_smoke_only:
            try:
                return {"status": "diagnostic_passed", "complete_run": False,
                        "server_mod_mode": self.server_mod_mode,
                        "server_build_mode": self.server_build_mode,
                        "server_project_directory": self.server_project_directory,
                        "server_task": self.server_task, "server_booted": True,
                        "rcon_ready": self.rcon is not None,
                        "partialreload_loaded": self.server_mod_mode == "with_mod",
                        "classpath_isolated": self.server_mod_mode == "without_mod"}
            finally:
                self.cleanup()
        cold_mode = False
        try:
            selected = selected or {"compatible", "reconnect", "silent_timeout", "absent_client_allowed", "connected_commit_still_blocked"}
            if self.cold_login_probes:
                self.cold_login()
                cold_mode = True
            else:
                if "compatible" in selected: self.compatible()
                if "reconnect" in selected: self.reconnect()
                if "silent_timeout" in selected: self.silent_timeout()
                if "absent_client_allowed" in selected: self.absent()
                if "connected_commit_still_blocked" in selected: self.connected_commit()
                if "absent_reconnect_stress" in selected: self.absent_reconnect_stress()
        finally:
            self.cleanup()
        if cold_mode:
            cold_passed = self.scenarios.get("cold_login", {}).get("status") == "passed"
            return {"status": "diagnostic_passed" if cold_passed else "failed", "complete_run": False,
                    "mode": self.initial_connect_mode, "scenarios": self.scenarios,
                    "server_mod_mode": self.server_mod_mode, "server_task": self.server_task,
                    "server_build_mode": self.server_build_mode,
                    "server_project_directory": self.server_project_directory,
                    "server_main_mod_present": self.server_mod_mode == "with_mod",
                    "required_valid_trials": self.required_valid_trials,
                    "maximum_launch_attempts": self.maximum_launch_attempts,
                    "cleanup": self.cleanup_result, "run_id": self.run_id,
                    "log_root": str(self.run_log_root), "attempt_ids": self.attempt_ids}
        full = selected == {"compatible", "reconnect", "silent_timeout", "absent_client_allowed", "connected_commit_still_blocked"}
        passed = all(item.get("status") == "passed" for item in self.scenarios.values())
        return {"status": "passed" if passed and self.cleanup_result["status"] == "passed" else "failed",
                "complete_run": full and passed and self.cleanup_result["status"] == "passed",
                "scenarios": self.scenarios, "server_mod_mode": self.server_mod_mode,
                "server_task": self.server_task,
                "server_build_mode": self.server_build_mode,
                "server_project_directory": self.server_project_directory,
                "server_main_mod_present": self.server_mod_mode == "with_mod",
                "cleanup": self.cleanup_result}


def port_open(port: int) -> bool:
    with socket.socket() as sock:
        sock.settimeout(.2)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenarios", default=None)
    parser.add_argument("--report", default=None)
    parser.add_argument("--initial-connect-mode", choices=("control", "launch_args"), default="control")
    parser.add_argument("--cold-login-probes", type=int, default=0)
    parser.add_argument("--client-mod-mode", choices=("with_mod", "without_mod"), default="with_mod")
    parser.add_argument("--server-mod-mode", choices=("with_mod", "without_mod"), default="with_mod")
    parser.add_argument("--strict-client-isolation", action="store_true")
    parser.add_argument("--require-attempt-cleanup", action="store_true")
    parser.add_argument("--fresh-server-per-probe", action="store_true")
    parser.add_argument("--cycles", type=int, default=0)
    parser.add_argument("--server-smoke-only", action="store_true")
    parser.add_argument("--required-valid-trials", type=int, default=0)
    parser.add_argument("--maximum-launch-attempts", type=int, default=0)
    args = parser.parse_args()
    LOG_ROOT.mkdir(parents=True, exist_ok=True)
    acceptance = Acceptance(args.initial_connect_mode.upper(), args.cold_login_probes, args.client_mod_mode,
                            args.strict_client_isolation, args.require_attempt_cleanup,
                            args.fresh_server_per_probe, args.cycles, args.server_mod_mode,
                            args.server_smoke_only, args.required_valid_trials,
                            args.maximum_launch_attempts)
    selected = None if not args.scenarios else set(args.scenarios.split(","))
    try:
        report = acceptance.run(selected)
    except Exception as exc:
        client_process = acceptance.clients[-1] if acceptance.clients else None
        server_process = acceptance.server
        evidence = login_evidence(server_process, client_process, acceptance.initial_connect_mode,
                                  acceptance.server_port)
        diagnostic_errors = acceptance.failure_capture_errors
        if evidence.initial_connect_triggered and not evidence.network_login_seen and not diagnostic_errors:
            diagnostic_errors = capture_thread_dumps(acceptance.run_log_root, client_process, server_process)
        trees = acceptance.failure_process_tree or {
            "client": process_tree(client_process.process.pid) if client_process and client_process.process else [],
            "server": process_tree(server_process.process.pid) if server_process and server_process.process else []}
        tcp = acceptance.failure_tcp_state or capture_tcp_state(acceptance.run_log_root, acceptance.server_port,
                                                                 client_process, server_process)
        acceptance.cleanup()
        report = {"status": "failed", "complete_run": False, "scenarios": acceptance.scenarios,
                  "cleanup": acceptance.cleanup_result, "error": str(exc),
                  "failed_scenario": next((name for name in (selected or []) if name not in acceptance.scenarios), None),
                  "classification": classify_failure(login_evidence(
                      acceptance.server, acceptance.clients[-1] if acceptance.clients else None,
                      acceptance.initial_connect_mode, acceptance.server_port),
                      acceptance.initial_connect_mode),
                  "expected_marker": str(exc), "last_server_markers": acceptance.server.entries()[-80:] if acceptance.server else [],
                  "last_client_markers": acceptance.clients[-1].entries()[-80:] if acceptance.clients else [],
                  "login_diagnostics": login_diagnostics(acceptance.server,
                                                         acceptance.clients[-1] if acceptance.clients else None),
                  "diagnostic_capture_errors": diagnostic_errors,
                  "process_tree": trees,
                  "processes": {"client": process_summary(client_process, "client"),
                                "server": process_summary(server_process, "server")},
                  "launch_args": launch_args_evidence(client_process, acceptance.server_port),
                  "tcp_state": tcp,
                  "run_id": acceptance.run_id,
                  "attempt_ids": acceptance.attempt_ids}
    if selected is not None and report.get("status") == "passed":
        report["status"] = "diagnostic_passed"
    output = pathlib.Path(args.report) if args.report else REPORT
    output = output if output.is_absolute() else ROOT / output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if report["status"] == "passed" and report.get("complete_run"):
        print("CLIENT_HANDSHAKE_FOUNDATION_ACCEPTANCE_PASSED")
    elif report["status"] == "diagnostic_passed":
        print("CLIENT_HANDSHAKE_FOUNDATION_ACCEPTANCE_DIAGNOSTIC_PASSED")
    else:
        print("CLIENT_HANDSHAKE_FOUNDATION_ACCEPTANCE_FAILED")
    return 0 if report["status"] in {"passed", "diagnostic_passed"} else 1


if __name__ == "__main__":
    raise SystemExit(main())
