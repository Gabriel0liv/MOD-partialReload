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
from dataclasses import dataclass, field

from minecraft_rcon import RconClient

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN_ROOT = ROOT / "run" / "handshake-acceptance" / uuid.uuid4().hex
REPORT = ROOT / "build" / "reports" / "client-handshake-foundation-acceptance.json"
LOG_ROOT = ROOT / "build" / "reports" / "client-handshake-foundation-acceptance"
MARKER = re.compile(r"(?P<marker>CLIENT_HANDSHAKE_[A-Z_]+)(?:\s+|:)(?P<rest>.*)")
SERVER_MARKERS = {"CLIENT_HANDSHAKE_SERVER_ABSENT", "CLIENT_HANDSHAKE_SERVER_PENDING",
                  "CLIENT_HANDSHAKE_SERVER_COMPATIBLE", "CLIENT_HANDSHAKE_SERVER_INCOMPATIBLE",
                  "CLIENT_HANDSHAKE_SERVER_TIMED_OUT", "CLIENT_HANDSHAKE_SERVER_DISCONNECTED"}


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

    def wait_marker(self, marker: str, timeout: float = 60.0, after_line: int = -1) -> dict[str, object]:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            values = marker_entries(self.lines, marker) if marker.startswith("CLIENT_HANDSHAKE_") else [
                {"marker": marker, "line": index, "fields": {}}
                for index, line in enumerate(self.lines) if marker in line
            ]
            values = [value for value in values if int(value["line"]) > after_line]
            if values:
                return values[-1]
            if self.process is not None and self.process.poll() is not None:
                raise RuntimeError(f"{self.name} exited ({self.process.returncode}) before {marker}")
            time.sleep(.1)
        raise TimeoutError(f"timeout waiting for {marker}; log={self.log_path}")

    def stop(self, timeout: float = 40.0) -> int:
        if self.process is None:
            return 0
        if self.process.poll() is None:
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


class Acceptance:
    def __init__(self) -> None:
        self.server_port, self.rcon_port = free_port(), free_port()
        self.password = uuid.uuid4().hex
        self.server: OwnedProcess | None = None
        self.clients: list[OwnedProcess] = []
        self.rcon: RconClient | None = None
        self.scenarios: dict[str, dict[str, object]] = {}
        self.cleanup_result: dict[str, object] = {"status": "failed"}

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
        directory = RUN_ROOT / "server"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "server.properties").write_text("\n".join([
            "online-mode=false", "enable-rcon=true", "server-ip=127.0.0.1",
            f"server-port={self.server_port}", f"rcon.port={self.rcon_port}",
            f"rcon.password={self.password}", "enable-command-block=true", "spawn-protection=0", ""]),
            encoding="utf-8")
        (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")

    def start_server(self) -> None:
        self.prepare_server()
        server_env = self.env()
        server_env["PARTIALRELOAD_ACCEPTANCE_RUN_DIR"] = str(RUN_ROOT / "server")
        self.server = OwnedProcess("server", [str(ROOT / "gradlew.bat"), "--no-daemon", "--console=plain",
                                               "runServer"], server_env, ROOT,
                                   LOG_ROOT / "server.stdout.log")
        self.server.start()
        self.server.wait_marker("CLIENT_HANDSHAKE_FOUNDATION_CHANNEL_REGISTERED", 180)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                self.rcon = RconClient("127.0.0.1", self.rcon_port, self.password, timeout=5)
                self.rcon.connect()
                return
            except Exception:
                time.sleep(.5)
        raise TimeoutError("RCON unavailable")

    def start_client(self, name: str, username: str, *, with_mod: bool = True,
                     mode: str = "NORMAL") -> OwnedProcess:
        environment = self.env()
        directory = RUN_ROOT / ("client-with-mod" if with_mod else "client-without-mod")
        directory.mkdir(parents=True, exist_ok=True)
        environment.update({"PARTIALRELOAD_ACCEPTANCE_HOST": "127.0.0.1",
                            "PARTIALRELOAD_ACCEPTANCE_PORT": str(self.server_port),
                            "PARTIALRELOAD_ACCEPTANCE_USERNAME": username,
                            "PARTIALRELOAD_ACCEPTANCE_RUN_DIR": str(directory),
                            "PARTIALRELOAD_ACCEPTANCE_WITH_MOD": "true" if with_mod else "false"})
        if mode != "NORMAL":
            environment["JAVA_TOOL_OPTIONS"] += f" -Dpartialreload.handshake.acceptance.mode={mode}"
        task = "runClient"
        process = OwnedProcess(name, [str(ROOT / "gradlew.bat"), "--no-daemon", "--console=plain", task],
                               environment, ROOT, LOG_ROOT / f"{name}.stdout.log")
        process.start()
        self.clients.append(process)
        return process

    @staticmethod
    def challenge(entry: dict[str, object]) -> str | None:
        value = fields(entry).get("challenge")
        return value if value and value != "-" else None

    def compatible(self) -> None:
        client = self.start_client("compatible", "PRCompat")
        pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 90)
        server_ok = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_COMPATIBLE", 90)
        received = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED", 90)
        sent = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_SENT", 90)
        accepted = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_ACCEPTED", 90)
        client_ok = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_COMPATIBLE", 90)
        challenge = self.challenge(pending)
        if not challenge or any(self.challenge(item) != challenge for item in (server_ok, received, sent, accepted, client_ok)):
            raise AssertionError("challenge mismatch in compatible")
        self.scenarios["compatible"] = {"status": "passed", "challenge": challenge,
                                         "connection": fields(pending).get("connection"),
                                         "server_log": str(self.server.log_path),
                                         "client_log": str(client.log_path)}
        client.stop()
        self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_DISCONNECTED", 30)

    def reconnect(self) -> None:
        client = self.start_client("reconnect", "PRCompat")
        previous_pending_line = max(entry["line"] for entry in self.server.entries()
                                    if entry["marker"] == "CLIENT_HANDSHAKE_SERVER_PENDING")
        pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 90, previous_pending_line)
        self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_COMPATIBLE", 90, previous_pending_line)
        client.wait_marker("CLIENT_HANDSHAKE_CLIENT_RESET", 90)
        client.wait_marker("CLIENT_HANDSHAKE_CLIENT_COMPATIBLE", 90)
        old = self.scenarios["compatible"]
        if self.challenge(pending) == old["challenge"] or fields(pending).get("connection") == old["connection"]:
            raise AssertionError("reconnect reused challenge or connection")
        self.scenarios["reconnect"] = {"status": "passed", "challenge": self.challenge(pending),
                                        "previous_challenge": old["challenge"],
                                        "previous_connection": old["connection"],
                                        "connection": fields(pending).get("connection"),
                                        "server_log": str(self.server.log_path),
                                        "client_log": str(client.log_path)}
        client.stop()
        self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_DISCONNECTED", 30)

    def silent_timeout(self) -> None:
        previous_pending_line = max(entry["line"] for entry in self.server.entries()
                                    if entry["marker"] == "CLIENT_HANDSHAKE_SERVER_PENDING")
        client = self.start_client("silent-timeout", "PRSilent", mode="SILENT")
        pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 90, previous_pending_line)
        received = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED", 90)
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
        absent = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_ABSENT", 120)
        player = fields(absent).get("player")
        if any(entry["marker"] in SERVER_MARKERS - {"CLIENT_HANDSHAKE_SERVER_ABSENT",
                                                     "CLIENT_HANDSHAKE_SERVER_DISCONNECTED"}
               and fields(entry).get("player") == player for entry in self.server.entries()):
            raise AssertionError("absent client acquired a handshake session")
        self.scenarios["absent_client_allowed"] = {"status": "passed", "server_marker": absent,
                                                    "pending_seen": False,
                                                    "server_log": str(self.server.log_path),
                                                    "client_log": str(client.log_path)}
        client.stop()

    def connected_commit(self) -> None:
        if self.rcon is None:
            raise RuntimeError("RCON unavailable")
        response = self.rcon.command("partialreload apply prepared")
        if "TAG_RECIPE_COMMIT_PLAYERS_CONNECTED" not in response:
            raise AssertionError(f"commit was not blocked: {response}")
        self.scenarios["connected_commit_still_blocked"] = {"status": "passed", "response": response.strip()}

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
                shutil.rmtree(RUN_ROOT)
        except Exception as exc:
            errors.append(str(exc))
        self.cleanup_result = {"status": "passed" if not errors else "failed",
                               "owned_processes_absent": all(item.process is None or item.process.poll() is not None
                                                              for item in [self.server, *self.clients]),
                               "rcon_port_released": not port_open(self.rcon_port), "errors": errors}

    def run(self) -> dict[str, object]:
        self.start_server()
        try:
            self.compatible(); self.reconnect(); self.silent_timeout(); self.absent(); self.connected_commit()
        finally:
            self.cleanup()
        passed = len(self.scenarios) == 5 and all(item.get("status") == "passed" for item in self.scenarios.values())
        return {"status": "passed" if passed and self.cleanup_result["status"] == "passed" else "failed",
                "complete_run": passed and self.cleanup_result["status"] == "passed",
                "scenarios": self.scenarios, "cleanup": self.cleanup_result}


def port_open(port: int) -> bool:
    with socket.socket() as sock:
        sock.settimeout(.2)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def main() -> int:
    LOG_ROOT.mkdir(parents=True, exist_ok=True)
    acceptance = Acceptance()
    try:
        report = acceptance.run()
    except Exception as exc:
        acceptance.cleanup()
        report = {"status": "failed", "complete_run": False, "scenarios": acceptance.scenarios,
                  "cleanup": acceptance.cleanup_result, "error": str(exc)}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("CLIENT_HANDSHAKE_FOUNDATION_ACCEPTANCE_PASSED" if report["status"] == "passed"
          else "CLIENT_HANDSHAKE_FOUNDATION_ACCEPTANCE_FAILED")
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
