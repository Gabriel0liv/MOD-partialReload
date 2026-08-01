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
from enum import Enum

from minecraft_rcon import RconClient
import handshake_infrastructure_policy as infra_policy

ROOT = pathlib.Path(__file__).resolve().parents[1]
ACCEPTANCE_RUNS_ROOT = ROOT / "run" / "handshake-acceptance"
REPORT = ROOT / "build" / "reports" / "client-handshake-foundation-acceptance.json"
LOG_ROOT = ROOT / "build" / "reports" / "client-handshake-foundation-acceptance"
OWNERSHIP_ROOT = LOG_ROOT / "ownership"
ACCEPTANCE_LOCK = LOG_ROOT / "acceptance.lock"
MARKER = re.compile(r"(?P<marker>(?:CLIENT_HANDSHAKE|HANDSHAKE_ACCEPTANCE_CLIENT)_[A-Z_]+)(?:\s+|:|$)(?P<rest>.*)")
SERVER_MARKERS = {"CLIENT_HANDSHAKE_SERVER_ABSENT", "CLIENT_HANDSHAKE_SERVER_PENDING",
                  "CLIENT_HANDSHAKE_SERVER_COMPATIBLE", "CLIENT_HANDSHAKE_SERVER_INCOMPATIBLE",
                  "CLIENT_HANDSHAKE_SERVER_TIMED_OUT", "CLIENT_HANDSHAKE_SERVER_DISCONNECTED",
                  "CLIENT_HANDSHAKE_SERVER_DISCOVERING", "CLIENT_HANDSHAKE_SERVER_PRESENCE_RECEIVED"}
IGNORED_TERMINAL_MARKERS = {"HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_HEARTBEAT"}


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
    server_discovering_seen: bool = False
    server_presence_received_seen: bool = False
    client_presence_sent_seen: bool = False
    client_presence_skipped_seen: bool = False
    server_timed_out_seen: bool = False
    client_hello_received_seen: bool = False
    client_hello_sent_seen: bool = False
    client_accepted_seen: bool = False
    client_compatible_seen: bool = False
    channel_rejection_seen: bool = False
    unknown_custom_packet_seen: bool = False
    player_present_in_rcon: bool = False


@dataclass(frozen=True)
class MatrixExpectation:
    server_mod_mode: str
    client_mod_mode: str


def classify_attempt(evidence: AttemptEvidence, expectation: MatrixExpectation,
                     cleanup: dict[str, object], diagnostics: dict[str, object]) -> AttemptClassification:
    if not physical_cleanup_passed(cleanup):
        return AttemptClassification.HARNESS_FAILURE
    if (diagnostics.get("channel_rejection_seen") or diagnostics.get("unknown_custom_packet_seen")
            or evidence.channel_rejection_seen or evidence.unknown_custom_packet_seen):
        return AttemptClassification.PRODUCT_FAILURE
    server_mod, client_mod = expectation.server_mod_mode, expectation.client_mod_mode
    if server_mod == "with_mod" and client_mod == "with_mod":
        valid = (evidence.network_login_seen and evidence.server_discovering_seen
                 and evidence.client_presence_sent_seen and evidence.server_presence_received_seen
                 and evidence.server_pending_seen and evidence.client_hello_received_seen
                 and evidence.client_hello_sent_seen and evidence.server_compatible_seen
                 and evidence.client_accepted_seen and evidence.client_compatible_seen)
        prohibited = evidence.server_absent_seen or evidence.server_timed_out_seen
    elif server_mod == "with_mod" and client_mod == "without_mod":
        valid = evidence.network_login_seen and evidence.server_discovering_seen and evidence.server_absent_seen
        prohibited = (evidence.client_presence_sent_seen or evidence.server_presence_received_seen
                      or evidence.server_pending_seen or evidence.server_compatible_seen or evidence.server_timed_out_seen)
    elif server_mod == "without_mod" and client_mod == "with_mod":
        valid = evidence.network_login_seen and evidence.client_presence_skipped_seen
        prohibited = evidence.client_presence_sent_seen or evidence.server_discovering_seen or evidence.server_pending_seen
    elif server_mod == "without_mod" and client_mod == "without_mod":
        valid = evidence.client_ready_seen and evidence.connect_requested_seen and evidence.network_login_seen and evidence.player_present_in_rcon
        prohibited = any((evidence.server_discovering_seen, evidence.server_presence_received_seen,
                          evidence.server_absent_seen, evidence.server_pending_seen,
                          evidence.server_compatible_seen, evidence.server_timed_out_seen))
    else:
        return AttemptClassification.HARNESS_FAILURE
    if prohibited:
        return AttemptClassification.PRODUCT_FAILURE
    if valid:
        return AttemptClassification.VALID_PASS
    if evidence.network_login_seen:
        protocol_started = any((
            evidence.client_presence_sent_seen,
            evidence.server_presence_received_seen,
            evidence.server_pending_seen,
            evidence.server_compatible_seen,
            evidence.server_timed_out_seen,
            evidence.server_absent_seen,
            evidence.server_discovering_seen,
            evidence.client_hello_received_seen,
            evidence.client_hello_sent_seen,
            evidence.client_accepted_seen,
            evidence.client_compatible_seen,
        ))
        if not protocol_started and evidence.network_logout_seen and not evidence.player_present_in_rcon:
            return AttemptClassification.INFRASTRUCTURE_FAILURE
        return AttemptClassification.PRODUCT_FAILURE
    if evidence.client_ready_seen and evidence.connect_requested_seen and not diagnostics.get("partialreload_marker_seen"):
        return AttemptClassification.INFRASTRUCTURE_FAILURE
    return AttemptClassification.HARNESS_FAILURE


def physical_cleanup_passed(cleanup: dict[str, object]) -> bool:
    physical = cleanup.get("physical_cleanup") if isinstance(cleanup.get("physical_cleanup"), dict) else cleanup
    return bool(
        physical.get("status") == "passed"
        and physical.get("wrapper_exited") is True
        and physical.get("descendants_exited") is True
        and physical.get("reader_threads_stopped") is True
        and physical.get("owned_processes_absent") is True
        and physical.get("tcp_connections_absent") is True
        and not physical.get("residual_owned_pids")
        and not physical.get("identity_mismatches")
    )


def infrastructure_subtype(evidence: AttemptEvidence, classification: AttemptClassification) -> str | None:
    if classification != AttemptClassification.INFRASTRUCTURE_FAILURE:
        return None
    if evidence.network_login_seen:
        return "TRANSIENT_POST_LOGIN_ABORT_BEFORE_FUNCTIONAL_OBSERVATION"
    return "TRANSIENT_LOGIN_ABORT"


class AttemptClassification(str, Enum):
    VALID_PASS = "VALID_PASS"
    PRODUCT_FAILURE = "PRODUCT_FAILURE"
    INFRASTRUCTURE_FAILURE = "INFRASTRUCTURE_FAILURE"
    HARNESS_FAILURE = "HARNESS_FAILURE"


FingerprintQuality = infra_policy.FingerprintQuality


def classify_control_classpath_entry(entry: str, repository_root: pathlib.Path) -> str | None:
    value = pathlib.Path(entry).resolve()
    root = repository_root.resolve()
    if value == root / "build" / "classes" / "java" / "main":
        return "ROOT_MAIN_CLASSES"
    if value == root / "build" / "resources" / "main":
        return "ROOT_MAIN_RESOURCES"
    if value == root / "src" / "main" / "resources" / "META-INF" / "mods.toml":
        return "ROOT_MODS_TOML"
    if value.parent == root / "build" / "libs" and value.name.lower().startswith("partialreload-") and value.suffix.lower() == ".jar":
        return "ROOT_PARTIALRELOAD_JAR"
    normalized = str(value).replace("\\", "/").lower()
    if "/com/gabriel0liv/partialreload/" in normalized:
        return "PARTIALRELOAD_MODULE"
    return None


def evaluate_quota(attempts: list[dict[str, object]], required_valid_trials: int,
                   maximum_launch_attempts: int,
                   authorized_infrastructure_fingerprints: set[str] | None = None,
                   authorized_infrastructure_causal_signatures: set[str] | None = None) -> dict[str, object]:
    if required_valid_trials < 0 or maximum_launch_attempts < 0 or (
            required_valid_trials > 0 and maximum_launch_attempts < required_valid_trials):
        raise ValueError("INVALID_TRIAL_QUOTA")
    authorized = authorized_infrastructure_fingerprints or set()
    authorized_causal = authorized_infrastructure_causal_signatures or set()
    valid = sum(item.get("classification") == AttemptClassification.VALID_PASS.value for item in attempts)
    product = sum(item.get("classification") == AttemptClassification.PRODUCT_FAILURE.value for item in attempts)
    harness = sum(item.get("classification") == AttemptClassification.HARNESS_FAILURE.value for item in attempts)
    unauthorized = []
    for item in attempts:
        if item.get("classification") != AttemptClassification.INFRASTRUCTURE_FAILURE.value:
            continue
        if not authorized and not authorized_causal:
            continue
        if authorized_causal:
            if item.get("causal_signature") not in authorized_causal:
                unauthorized.append(item)
        elif item.get("fingerprint") not in authorized:
            unauthorized.append(item)
    unauthorized_count = len(unauthorized)
    return {"required_valid_trials": required_valid_trials,
            "maximum_launch_attempts": maximum_launch_attempts,
            "launch_attempts": len(attempts), "valid_trials": valid,
            "infrastructure_failures": sum(item.get("classification") == AttemptClassification.INFRASTRUCTURE_FAILURE.value for item in attempts),
            "product_failures": product, "harness_failures": harness,
            "quota_reached": valid >= required_valid_trials and len(attempts) <= maximum_launch_attempts
            and product == 0 and harness == 0 and unauthorized_count == 0,
            "unauthorized_infrastructure_failures": unauthorized_count}


def load_handshake_versions() -> dict[str, object]:
    task = [str(ROOT / "gradlew.bat"), "--no-daemon", "--console=plain",
            "reportHandshakeAcceptanceVersions"]
    subprocess.run(task, cwd=ROOT, check=True, stdout=subprocess.DEVNULL,
                   stderr=subprocess.PIPE, text=True)
    path = ROOT / "build" / "reports" / "handshake-acceptance-versions.json"
    versions = json.loads(path.read_text(encoding="utf-8"))
    required = ("minecraft_version", "forge_version", "mapping_channel", "mapping_version", "java_version")
    if any(not str(versions.get(key, "")).strip() for key in required):
        raise RuntimeError("HANDSHAKE_ACCEPTANCE_VERSIONS_INCOMPLETE")
    return versions


def expected_authorization_scope(client_mod_mode: str, initial_connect_mode: str,
                                 versions: dict[str, object]) -> infra_policy.InfrastructureAuthorizationScope:
    return infra_policy.authorization_scope(client_mod_mode, initial_connect_mode, versions)


def ready_client_profile(ready_entry: dict[str, object] | None, client_mod_mode: str,
                         initial_connect_mode: str) -> tuple[dict[str, object] | None, str | None]:
    if ready_entry is None:
        return None, None
    data = fields(ready_entry)
    partial_loaded = str(data.get("partialReloadLoaded", "")).lower() == "true"
    helper_loaded = str(data.get("helperLoaded", "")).lower() == "true"
    expected_profile, expected_main, expected_helper = infra_policy.client_classpath_profile(client_mod_mode)
    profile = {"client_mod_mode": client_mod_mode,
               "client_classpath_profile": expected_profile,
               "client_main_mod_present": partial_loaded,
               "helper_mod_present": helper_loaded,
               "initial_connect_mode": initial_connect_mode,
               "client_launch_target": "forgeclientuserdev"}
    if partial_loaded != expected_main or helper_loaded != expected_helper:
        return profile, "CLIENT_PROFILE_MISMATCH"
    return profile, None


def validate_minecraft_username(username: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", username):
        raise ValueError("INVALID_MINECRAFT_USERNAME")


def normalize_fingerprint_value(value: object) -> str | bool | None:
    return infra_policy.normalize_fingerprint_value(value)


def elapsed_connect_bucket(elapsed_seconds: object) -> str | None:
    return infra_policy.elapsed_connect_bucket(elapsed_seconds)


def tcp_state_summary(tcp: dict[str, object] | None) -> str | None:
    return infra_policy.tcp_state_summary(tcp)


def last_relevant_log_signature(lines: list[str]) -> str | None:
    return infra_policy.last_error_log_signature(lines)


def fingerprint_payload(evidence: AttemptEvidence, diagnostics: dict[str, object]) -> dict[str, object]:
    return infra_policy.fingerprint_payload(evidence, diagnostics)


def assess_fingerprint_quality(payload: dict[str, object]) -> tuple[FingerprintQuality, list[str]]:
    return infra_policy.assess_fingerprint_quality(payload)


def infrastructure_fingerprint(evidence: AttemptEvidence, diagnostics: dict[str, object]) -> str:
    """Canonical, redacted fingerprint for a pre-login infrastructure failure."""
    return infra_policy.canonical_fingerprint(evidence, diagnostics)


def infrastructure_fingerprint_quality(evidence: AttemptEvidence, diagnostics: dict[str, object]) -> tuple[FingerprintQuality, list[str]]:
    return assess_fingerprint_quality(fingerprint_payload(evidence, diagnostics))


def preflight_owned_processes(ownership_directory: pathlib.Path) -> dict[str, object]:
    """Fail-closed recovery of stale ownership manifests."""
    recovered: list[str] = []
    ambiguous: list[str] = []
    invalid: list[str] = []
    ownership_directory.mkdir(parents=True, exist_ok=True)
    for manifest in ownership_directory.glob("*.json"):
        try:
            data = json.loads(manifest.read_text(encoding="utf-8"))
            if not isinstance(data, dict) or not isinstance(data.get("processes", []), list):
                raise ValueError("invalid manifest")
            live_mismatch = False
            for item in data.get("processes", []):
                if not isinstance(item, dict) or "pid" not in item:
                    raise ValueError("invalid process identity")
                pid = int(item["pid"])
                if process_alive(pid):
                    actual = next((p for p in process_tree(data.get("harness", {}).get("pid")) if int(p.get("pid", -1)) == pid), None)
                    expected = OwnedProcessIdentity(pid, item.get("parent_pid"), item.get("creation_time"),
                                                    str(item.get("role", "unknown")), str(item.get("command_fingerprint", "UNKNOWN_OWNED_DESCENDANT")))
                    if actual is None or not identity_matches(expected, actual):
                        live_mismatch = True
            if live_mismatch:
                ambiguous.append(manifest.name)
            else:
                recovered.append(manifest.name)
                manifest.unlink(missing_ok=True)
        except Exception:
            invalid.append(manifest.name)
    if invalid:
        return {"status": "failed", "error_code": "OWNERSHIP_MANIFEST_INVALID", "invalid": invalid,
                "recovered": recovered, "ambiguous": ambiguous}
    if ambiguous:
        return {"status": "failed", "error_code": "OWNED_PROCESS_PREFLIGHT_AMBIGUOUS", "invalid": invalid,
                "recovered": recovered, "ambiguous": ambiguous}
    return {"status": "passed", "recovered": recovered, "ambiguous": [], "invalid": []}


def load_authorized_infrastructure_baseline(report_path: pathlib.Path,
                                            expected_client_mod_mode: str | None = None
                                            ) -> infra_policy.AuthorizedInfrastructureBaseline:
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if isinstance(report.get("authorized_causal_signatures"), list):
        scope_valid, scope_error, scope = infra_policy.validate_authorization_scope(report, expected_client_mod_mode)
        if not scope_valid or scope is None:
            raise ValueError(f"INVALID_INFRASTRUCTURE_AUTHORIZATION_REPORT:{scope_error}")
        signatures = frozenset(str(value) for value in report.get("authorized_causal_signatures", []))
        if not signatures:
            raise ValueError("NO_INFRASTRUCTURE_CAUSAL_SIGNATURES")
        return infra_policy.AuthorizedInfrastructureBaseline(
            scope, frozenset(str(value) for value in report.get("associated_control_fingerprints", [])),
            signatures, str(report_path))
    valid, error, baseline = infra_policy.load_authorized_infrastructure_baseline(
        report, str(report_path), expected_client_mod_mode)
    if not valid:
        if error == "NO_INFRASTRUCTURE_FINGERPRINTS":
            raise ValueError("NO_INFRASTRUCTURE_FINGERPRINTS")
        raise ValueError(f"INVALID_INFRASTRUCTURE_AUTHORIZATION_REPORT:{error}")
    if baseline is None or not baseline.fingerprints:
        raise ValueError("NO_INFRASTRUCTURE_FINGERPRINTS")
    return baseline


def load_authorized_infrastructure_fingerprints(report_path: pathlib.Path) -> set[str]:
    return set(load_authorized_infrastructure_baseline(report_path).fingerprints)


def entries_in_window(process: "OwnedProcess | None", start: int, end: int | None,
                      run_id: str | None = None, attempt_id: str | None = None) -> list[dict[str, object]]:
    if process is None:
        return []
    upper = len(process.lines) - 1 if end is None else end
    return [entry for entry in process.entries()
            if start < int(entry["line"]) <= upper
            and (run_id is None or fields(entry).get("run") == run_id)
            and (attempt_id is None or fields(entry).get("attempt") == attempt_id)]


def raw_lines_in_window(process: "OwnedProcess | None", start_line: int, end_line: int | None) -> list[str]:
    if process is None:
        return []
    upper = len(process.lines) - 1 if end_line is None else min(end_line, len(process.lines) - 1)
    return [line for index, line in enumerate(process.lines) if start_line < index <= upper]


def attempt_marker_evidence(server: "OwnedProcess | None", client: "OwnedProcess | None",
                            window: AttemptWindow, run_id: str, attempt_id: str) -> dict[str, object]:
    # Acceptance run/attempt fields exist only on helper client markers.  The
    # server deliberately cannot receive these identifiers over the protocol;
    # correlate its events by the attempt window and player/connection fields.
    server_entries = entries_in_window(server, window.server_start_line, window.server_end_line)
    raw_client_entries = entries_in_window(client, window.client_start_line, window.client_end_line)
    client_entries = []
    for entry in raw_client_entries:
        entry_fields = fields(entry)
        if entry["marker"].startswith("CLIENT_HANDSHAKE_CLIENT_") and "run" not in entry_fields:
            client_entries.append(entry)
        elif entry_fields.get("run") == run_id and entry_fields.get("attempt") == attempt_id:
            client_entries.append(entry)
    server_markers = {entry["marker"] for entry in server_entries}
    client_markers = {entry["marker"] for entry in client_entries}
    client_text = "\n".join(str(entry) for entry in client_entries)
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
        "server_discovering": "CLIENT_HANDSHAKE_SERVER_DISCOVERING" in server_markers,
        "server_presence_received": "CLIENT_HANDSHAKE_SERVER_PRESENCE_RECEIVED" in server_markers,
        "client_presence_sent": any(str(marker).endswith("CLIENT_PRESENCE_SENT") for marker in client_markers),
        "client_presence_skipped": "CLIENT_HANDSHAKE_CLIENT_PRESENCE_SKIPPED_REMOTE_ABSENT" in client_markers,
        "server_timed_out": "CLIENT_HANDSHAKE_SERVER_TIMED_OUT" in server_markers,
        "client_hello_received": "CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED" in client_markers,
        "client_hello_sent": "CLIENT_HANDSHAKE_CLIENT_HELLO_SENT" in client_markers,
        "client_accepted": "CLIENT_HANDSHAKE_CLIENT_ACCEPTED" in client_markers,
        "client_compatible": "CLIENT_HANDSHAKE_CLIENT_COMPATIBLE" in client_markers,
    }


def authorization_attempt_evidence(marker_evidence: dict[str, object]) -> dict[str, bool]:
    return {
        "client_ready_seen": bool(marker_evidence.get("ready")),
        "connect_requested_seen": bool(marker_evidence.get("connect_requested")),
        "network_login_seen": bool(marker_evidence.get("network_login")),
        "server_absent_seen": bool(marker_evidence.get("server_absent")),
        "server_pending_seen": bool(marker_evidence.get("server_pending")),
        "server_compatible_seen": bool(marker_evidence.get("server_compatible")),
        "network_logout_seen": bool(marker_evidence.get("network_logout")),
        "server_disconnected_seen": bool(marker_evidence.get("server_disconnected")),
    }


def wait_evidence_inconsistency(ready_wait_seen: bool, connect_wait_seen: bool,
                                login_wait_seen: bool, evidence: AttemptEvidence) -> str | None:
    if ready_wait_seen and not evidence.client_ready_seen:
        return "ATTEMPT_WINDOW_READY_INCONSISTENT"
    if connect_wait_seen and not evidence.connect_requested_seen:
        return "ATTEMPT_WINDOW_CONNECT_REQUESTED_INCONSISTENT"
    if login_wait_seen and not evidence.network_login_seen:
        return "ATTEMPT_WINDOW_NETWORK_LOGIN_INCONSISTENT"
    return None


def should_expect_exit_request(evidence: AttemptEvidence, diagnostics: dict[str, object]) -> bool:
    terminal_screen = diagnostics.get("last_client_screen") or diagnostics.get("screen")
    pre_login_terminal = (not evidence.network_login_seen
                          and terminal_screen in {"DisconnectedScreen", "ModMismatchDisconnectedScreen"})
    return bool(evidence.client_ready_seen and not pre_login_terminal)


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
        player, connection,
        bool(evidence["server_discovering"]), bool(evidence["server_presence_received"]),
        bool(evidence["client_presence_sent"]), bool(evidence["client_presence_skipped"]),
        bool(evidence["server_timed_out"]), bool(evidence["client_hello_received"]),
        bool(evidence["client_hello_sent"]), bool(evidence["client_accepted"]),
        bool(evidence["client_compatible"]), False, False, False)


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


@dataclass(frozen=True)
class OwnedProcessIdentity:
    pid: int
    parent_pid: int | None
    creation_time: str | None
    role: str
    command_fingerprint: str


@dataclass(frozen=True)
class ProcessStopResult:
    status: str
    graceful: bool
    wrapper_exited: bool
    descendants_exited: bool
    reader_thread_stopped: bool
    residual_owned_pids: tuple[int, ...]
    identity_mismatches: tuple[int, ...]


def command_fingerprint(command: list[str]) -> str:
    text = " ".join(command).lower()
    if "forgeclientuserdev" in text:
        return "FORGE_CLIENT_USERDEV"
    if "forgeserveruserdev" in text:
        return "FORGE_SERVER_USERDEV"
    if "forge-control-server" in text:
        return "GRADLE_WRAPPER_CONTROL_BUILD"
    if "gradle" in text or "gradlew" in text:
        return "GRADLE_WRAPPER_ROOT"
    return "UNKNOWN_OWNED_DESCENDANT"


def should_track_descendant(command_line: object) -> bool:
    text = str(command_line or "").lower()
    if "git fsmonitor--daemon" in text:
        return False
    return True


def process_creation_time(pid: int) -> str | None:
    try:
        script = f"(Get-CimInstance Win32_Process -Filter 'ProcessId={int(pid)}').CreationDate"
        value = subprocess.check_output(["powershell", "-NoProfile", "-Command", script], text=True,
                                         stderr=subprocess.DEVNULL).strip()
        return normalize_creation_time(value) if value else None
    except Exception:
        return None


def process_alive(pid: int) -> bool:
    if not isinstance(pid, int) or pid <= 0:
        return False
    try:
        script = f"Get-CimInstance Win32_Process -Filter 'ProcessId={pid}' | Select-Object -ExpandProperty ProcessId"
        output = subprocess.check_output(["powershell", "-NoProfile", "-Command", script], text=True,
                                         stderr=subprocess.DEVNULL)
        return any(line.strip() == str(pid) for line in output.splitlines())
    except Exception:
        return False


def normalize_creation_time(value: object) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    return text.replace(".", "").replace("+", "")


def identity_matches(expected: OwnedProcessIdentity, actual_process: dict[str, object]) -> bool:
    return (int(actual_process.get("pid", -1)) == expected.pid
            and expected.creation_time is not None
            and normalize_creation_time(actual_process.get("creation_time")) == normalize_creation_time(expected.creation_time)
            and command_fingerprint([str(actual_process.get("command_line") or "")]) == expected.command_fingerprint)


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
    owned_identities: dict[int, "OwnedProcessIdentity"] = field(default_factory=dict)

    def write_manifest(self, path: pathlib.Path, run_id: str, state: str) -> None:
        payload = {"run_id": run_id, "harness": {"pid": os.getpid(), "creation_time": process_creation_time(os.getpid())},
                   "processes": [identity.__dict__ for identity in self.owned_identities.values()], "state": state}
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        temporary.replace(path)

    def start(self) -> None:
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        self.process = subprocess.Popen(self.command, cwd=self.cwd, env=self.env,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, encoding="utf-8", errors="replace", bufsize=1)
        self.owned_identities[self.process.pid] = OwnedProcessIdentity(
            self.process.pid, None, process_creation_time(self.process.pid), self.name,
            command_fingerprint(self.command))

        def read() -> None:
            assert self.process is not None and self.process.stdout is not None
            with self.log_path.open("w", encoding="utf-8") as output:
                for line in self.process.stdout:
                    self.lines.append(line.rstrip("\r\n"))
                    output.write(line)
                    output.flush()
        self.thread = threading.Thread(target=read, name=f"handshake-reader-{self.name}", daemon=False)
        self.thread.start()

    def refresh_owned_identities(self) -> None:
        if self.process is None:
            return
        snapshot = current_process_snapshot()
        descendants = process_tree(self.process.pid)
        for item in descendants:
            pid = int(item.get("pid", 0) or 0)
            if pid <= 0 or pid in self.owned_identities:
                continue
            if not should_track_descendant(item.get("command_line")):
                continue
            command = [str(item.get("command_line") or "")]
            fingerprint = command_fingerprint(command)
            role = "client" if fingerprint == "FORGE_CLIENT_USERDEV" else "server" if fingerprint == "FORGE_SERVER_USERDEV" else "worker"
            self.owned_identities[pid] = OwnedProcessIdentity(pid, int(item.get("parent_pid") or 0),
                                                               normalize_creation_time(item.get("creation_time")), role, fingerprint)

    def cursor(self) -> int:
        return len(self.lines) - 1

    def wait_marker(self, marker: str, timeout: float = 60.0, after_line: int = -1,
                    expected_fields: dict[str, str] | None = None) -> dict[str, object]:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            self.refresh_owned_identities()
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

    def stop(self, timeout: float = 40.0) -> "ProcessStopResult":
        if self.process is None:
            return ProcessStopResult("passed", True, True, True, True, (), ())
        self.refresh_owned_identities()
        graceful = True
        if self.process.poll() is None:
            try:
                self.process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                graceful = False
                snapshot = current_process_snapshot()
                by_pid = {int(p.get("pid", -1)): p for p in snapshot}
                for pid in sorted(self.owned_identities, key=lambda value: value, reverse=True):
                    actual = by_pid.get(pid)
                    expected = self.owned_identities[pid]
                    if actual is not None and identity_matches(expected, actual):
                        subprocess.run(["taskkill", "/PID", str(pid), "/F"], stdout=subprocess.DEVNULL,
                                       stderr=subprocess.DEVNULL, check=False)
        try:
            self.process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            pass
        snapshot = current_process_snapshot()
        by_pid = {int(p.get("pid", -1)): p for p in snapshot}
        residual_after_wrapper = [pid for pid in self.owned_identities if pid in by_pid]
        if residual_after_wrapper:
            for pid in sorted(residual_after_wrapper, key=lambda value: value, reverse=True):
                actual = by_pid.get(pid)
                expected = self.owned_identities[pid]
                if actual is not None and identity_matches(expected, actual):
                    subprocess.run(["taskkill", "/PID", str(pid), "/F"], stdout=subprocess.DEVNULL,
                                   stderr=subprocess.DEVNULL, check=False)
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                snapshot = current_process_snapshot()
                by_pid = {int(p.get("pid", -1)): p for p in snapshot}
                if not any(pid in by_pid and identity_matches(self.owned_identities[pid], by_pid[pid])
                           for pid in self.owned_identities):
                    break
                time.sleep(.25)
        if self.thread is not None:
            self.thread.join(timeout=timeout)
        snapshot = current_process_snapshot()
        by_pid = {int(p.get("pid", -1)): p for p in snapshot}
        residual = tuple(pid for pid in self.owned_identities
                         if pid in by_pid and identity_matches(self.owned_identities[pid], by_pid[pid]))
        potential_mismatches = [pid for pid in self.owned_identities
                                if pid in by_pid and not identity_matches(self.owned_identities[pid], by_pid[pid])]
        if potential_mismatches:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                time.sleep(.25)
                snapshot = current_process_snapshot()
                by_pid = {int(p.get("pid", -1)): p for p in snapshot}
                potential_mismatches = [pid for pid in potential_mismatches
                                        if pid in by_pid and not identity_matches(self.owned_identities[pid], by_pid[pid])]
                if not potential_mismatches:
                    break
        mismatches = tuple(potential_mismatches)
        reader_stopped = self.thread is None or not self.thread.is_alive()
        result = ProcessStopResult("passed" if self.process.poll() is not None and not residual and reader_stopped else "failed",
                                   graceful, self.process.poll() is not None, not residual, reader_stopped, residual, mismatches)
        return result

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
                "server_absent_client_mod_allowed", "server_absent_client_mod_reconnect",
                "connected_commit_still_blocked"}
    scenarios = report.get("scenarios")
    if report.get("status") != "passed" or report.get("complete_run") is not True:
        return False, "status/complete_run"
    if not isinstance(scenarios, dict) or set(scenarios) != required:
        return False, "scenario set"
    for name in required:
        if scenarios[name].get("status") != "passed":
            return False, name
    subruns = report.get("subruns")
    if not isinstance(subruns, dict) or subruns.get("with_mod", {}).get("cleanup", {}).get("status") != "passed" \
            or subruns.get("without_mod", {}).get("cleanup", {}).get("status") != "passed":
        return False, "subruns cleanup"
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


def client_connection_phase(evidence: AttemptEvidence, entries: list[dict[str, object]]) -> str:
    if evidence.network_login_seen:
        return "NETWORK_LOGIN"
    if any(entry["marker"] == "HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECTED_SCREEN" for entry in entries):
        return "DISCONNECTED"
    if evidence.connect_requested_seen:
        return "CONNECTING"
    if evidence.client_ready_seen:
        return "READY"
    return "UNKNOWN"


def collect_infrastructure_diagnostics(server: "OwnedProcess | None", client: "OwnedProcess | None",
                                       evidence: AttemptEvidence, marker_window: dict[str, object],
                                       window: AttemptWindow,
                                       tcp: dict[str, object], elapsed_seconds: float | None,
                                       username: str, rcon: RconClient | None) -> dict[str, object]:
    client_entries = list(marker_window.get("client_entries", []))
    server_entries = list(marker_window.get("server_entries", []))
    meaningful = [entry for entry in client_entries if entry["marker"] not in IGNORED_TERMINAL_MARKERS]
    last_client = client_entries[-1] if client_entries else None
    last_meaningful = meaningful[-1] if meaningful else None
    last_server = server_entries[-1] if server_entries else None
    screen = None
    disconnect_reason = None
    for entry in reversed(client_entries):
        values = fields(entry)
        if screen is None:
            screen = values.get("currentScreen") or values.get("screen") or values.get("screenClass")
        if entry["marker"] == "HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECTED_SCREEN":
            disconnect_reason = values.get("narration")
            screen = values.get("screen") or screen
            break
    client_summary = process_summary(client, "client")
    server_summary = process_summary(server, "server")
    player_present = False
    if rcon is not None:
        try:
            player_present = username in rcon.command("list")
        except Exception:
            player_present = False
    client_lines = raw_lines_in_window(client, window.client_start_line, window.client_end_line)
    server_lines = raw_lines_in_window(server, window.server_start_line, window.server_end_line)
    return {
        "last_client_marker": last_client["marker"] if last_client else None,
        "last_meaningful_client_marker": last_meaningful["marker"] if last_meaningful else None,
        "last_server_marker": last_server["marker"] if last_server else None,
        "last_client_screen": screen,
        "disconnect_reason": disconnect_reason,
        "client_connection_phase": client_connection_phase(evidence, client_entries),
        "server_login_timeout_seen": any("timed out" in line.lower() and "login" in line.lower() for line in server_lines),
        "server_player_join_seen": any(f"{username} joined the game" in line for line in server_lines),
        "player_present_in_rcon": player_present,
        "tcp_state_summary": tcp_state_summary(tcp),
        "client_game_process_alive": process_alive(client_summary.get("game_pid") or (client.process.pid if client and client.process else -1)),
        "server_game_process_alive": process_alive(server_summary.get("game_pid") or (server.process.pid if server and server.process else -1)),
        "elapsed_connect_seconds": elapsed_seconds,
        "last_client_error_signature": infra_policy.last_error_log_signature(client_lines),
        "last_server_error_signature": infra_policy.last_error_log_signature(server_lines),
        "partialreload_marker_seen": any(str(item["marker"]).startswith("CLIENT_HANDSHAKE_") for item in server_entries),
        "channel_rejection_seen": any("rejected" in line.lower() and "partialreload:client_sync" in line.lower()
                                      for line in client_lines + server_lines),
        "unknown_custom_packet_seen": any("unknown custom packet" in line.lower() for line in client_lines + server_lines),
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


def current_process_snapshot() -> list[dict[str, object]]:
    script = ("Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,CommandLine,CreationDate | "
              "ConvertTo-Json -Compress")
    try:
        raw = subprocess.check_output(["powershell", "-NoProfile", "-Command", script], text=True,
                                      stderr=subprocess.DEVNULL)
        values = json.loads(raw) if raw.strip() else []
        values = values if isinstance(values, list) else [values]
        return [{"pid": value.get("ProcessId"), "parent_pid": value.get("ParentProcessId"),
                 "command_line": value.get("CommandLine"), "creation_time": normalize_creation_time(value.get("CreationDate"))}
                for value in values if isinstance(value, dict)]
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
    run_log_root.mkdir(parents=True, exist_ok=True)
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
                 maximum_launch_attempts: int = 0,
                 authorized_infrastructure_fingerprints: set[str] | None = None,
                 authorized_infrastructure_causal_signatures: set[str] | None = None,
                 diagnostic_matrix: bool = False,
                 authorization_scope: infra_policy.InfrastructureAuthorizationScope | None = None) -> None:
        self.run_id = uuid.uuid4().hex
        self.run_root = ACCEPTANCE_RUNS_ROOT / self.run_id
        self.run_root.mkdir(parents=True, exist_ok=True)
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
        self.authorized_infrastructure_fingerprints = authorized_infrastructure_fingerprints or set()
        self.authorized_infrastructure_causal_signatures = authorized_infrastructure_causal_signatures or set()
        self.diagnostic_matrix = diagnostic_matrix
        self.authorization_scope = authorization_scope
        if self.required_valid_trials < 0 or self.maximum_launch_attempts < 0:
            raise ValueError("INVALID_TRIAL_QUOTA")
        if self.required_valid_trials > 0 and self.maximum_launch_attempts < self.required_valid_trials:
            raise ValueError("INVALID_TRIAL_QUOTA")
        self.control_classpath_result: dict[str, object] | None = None
        self.failure_capture_errors: list[str] = []
        self.failure_tcp_state: dict[str, object] = {}
        self.failure_process_tree: dict[str, object] = {}
        self.connected_commit_preparation: dict[str, object] | None = None
        self.lock_acquired = False
        self.ownership_manifest = OWNERSHIP_ROOT / f"{self.run_id}.json"

    def acquire_lock(self) -> None:
        ACCEPTANCE_LOCK.parent.mkdir(parents=True, exist_ok=True)
        preflight = preflight_owned_processes(OWNERSHIP_ROOT)
        if preflight.get("status") != "passed":
            raise RuntimeError(str(preflight.get("error_code", "OWNED_PROCESS_PREFLIGHT_FAILED")))
        payload = {"run_id": self.run_id, "harness_pid": os.getpid(), "started_at": time.time()}
        try:
            fd = os.open(str(ACCEPTANCE_LOCK), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                json.dump(payload, stream)
            self.lock_acquired = True
            self._write_ownership_manifest("running")
        except FileExistsError:
            raise RuntimeError("ACCEPTANCE_ALREADY_RUNNING")

    def _write_ownership_manifest(self, state: str) -> None:
        processes = []
        for process in [self.server, *self.clients]:
            if process is not None:
                processes.extend(process.owned_identities.values())
        payload = {"run_id": self.run_id,
                   "harness": {"pid": os.getpid(), "creation_time": process_creation_time(os.getpid())},
                   "processes": [identity.__dict__ for identity in processes], "state": state}
        OWNERSHIP_ROOT.mkdir(parents=True, exist_ok=True)
        temporary = self.ownership_manifest.with_suffix(".json.tmp")
        temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        temporary.replace(self.ownership_manifest)

    def release_lock(self) -> None:
        if self.lock_acquired:
            try:
                ACCEPTANCE_LOCK.unlink(missing_ok=True)
            finally:
                self.lock_acquired = False
                if self.ownership_manifest.exists():
                    try:
                        self.ownership_manifest.unlink()
                    except OSError:
                        pass
                if hasattr(self, "cleanup_result"):
                    self.cleanup_result["lock_released"] = not ACCEPTANCE_LOCK.exists()

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
        directory = self.run_root / self.server_directory_name
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "server.properties").write_text("\n".join([
            "online-mode=false", "enable-rcon=true", "server-ip=127.0.0.1",
            f"server-port={self.server_port}", f"rcon.port={self.rcon_port}",
            f"rcon.password={self.password}", "enable-command-block=true", "spawn-protection=0", ""]),
            encoding="utf-8")
        (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        if self.server_mod_mode == "with_mod":
            self.install_joint_commit_fixture("A")

    def install_joint_commit_fixture(self, letter: str) -> None:
        pack = self.run_root / self.server_directory_name / "world" / "datapacks" / "partialreload_handshake_commit_fixture"
        b = letter == "B"
        item = "minecraft:stone" if letter == "A" else "minecraft:dirt"
        count = 1 if letter == "A" else 2
        files = {
            "pack.mcmeta": json.dumps({"pack": {"pack_format": 15, "description": "Partial Reload handshake commit fixture"}}) + "\n",
            "data/partialreload_test/tags/items/joint.json": json.dumps({"replace": True, "values": [item]}) + "\n",
            "data/partialreload_test/recipes/acceptance.json": json.dumps({
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"tag": "partialreload_test:joint"}],
                "result": {"item": "minecraft:torch", "count": count},
            }) + "\n",
        }
        if not b:
            if pack.exists():
                shutil.rmtree(pack)
            for rel, text in files.items():
                target = pack / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding="utf-8", newline="\n")
            return
        staging = pack.parent / (pack.name + ".staging")
        if staging.exists():
            shutil.rmtree(staging)
        for rel, text in files.items():
            target = staging / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8", newline="\n")
        old = {path.relative_to(pack) for path in pack.rglob("*") if path.is_file()} if pack.exists() else set()
        new = {pathlib.Path(rel) for rel in files}
        for rel in old - new:
            (pack / rel).unlink()
        for rel in new:
            target = pack / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            os.replace(staging / rel, target)
        if staging.exists():
            shutil.rmtree(staging)

    def wait_rcon_status(self, pattern: str, timeout: float) -> str:
        regex = re.compile(pattern, re.I | re.S)
        deadline = time.monotonic() + timeout
        last = ""
        while time.monotonic() < deadline:
            last = self.rcon.command("partialreload status") if self.rcon is not None else ""
            if regex.search(last):
                return last
            time.sleep(0.5)
        raise TimeoutError(f"RCON status did not match {pattern!r}: {last}")

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
        server_env["PARTIALRELOAD_ACCEPTANCE_RUN_DIR"] = str(self.run_root / self.server_directory_name)
        self.server = OwnedProcess("server", command, server_env, ROOT,
                                   self.run_log_root / "server.stdout.log")
        self.server.start()
        self._write_ownership_manifest("running")
        if self.server_mod_mode == "with_mod":
            self.server.wait_marker("CLIENT_HANDSHAKE_FOUNDATION_CHANNEL_REGISTERED", 420)
        self.server.wait_marker("Done", 420)
        if self.server_mod_mode == "without_mod":
            self.control_classpath_result = self.inspect_control_classpath()
            required = ("game_pid_found", "argfiles_expanded", "legacy_classpath_found", "isolated")
            if (not all(bool(self.control_classpath_result.get(key)) for key in required)
                    or self.control_classpath_result.get("forbidden_entries")
                    or self.control_classpath_result.get("partialreload_module_present")):
                raise RuntimeError("CONTROL_SERVER_CLASSPATH_NOT_ISOLATED")
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
        tree = process_tree(self.server.process.pid if self.server and self.server.process else None)
        game = find_game_process(tree, "server")
        forbidden = [kind for entry in entries if (kind := classify_control_classpath_entry(entry, ROOT)) is not None]
        result = {"game_pid_found": game is not None and bool(game.get("pid")),
                  "game_pid_owned": game is not None,
                  "launch_target": "forgeserveruserdev" if game and "forgeserveruserdev" in str(game.get("command_line", "")).lower() else None,
                  "argfiles_expanded": classpath_file.exists(),
                  "legacy_classpath_found": bool(entries), "forbidden_entries": forbidden,
                  "partialreload_module_present": "PARTIALRELOAD_MODULE" in forbidden,
                  "isolated": classpath_file.exists() and bool(game) and not forbidden}
        report = ROOT / "build" / "reports" / "control-server-effective-classpath.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2), encoding="utf-8")
        return result

    def start_client(self, name: str, username: str, *, with_mod: bool = True,
                     mode: str = "NORMAL") -> OwnedProcess:
        validate_minecraft_username(username)
        environment = self.env()
        directory = self.run_root / name
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "control").mkdir(parents=True, exist_ok=True)
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
        self._write_ownership_manifest("running")
        return process

    def cleanup_attempt(self, client: OwnedProcess, name: str, username: str,
                        entered_server: bool, expect_server_disconnect: bool,
                        expect_exit_request: bool = True) -> dict[str, object]:
        control = self.run_root / name / "control"
        control.mkdir(parents=True, exist_ok=True)
        client_cursor = client.cursor()
        server_cursor = self.server.cursor()
        (control / "exit.request").write_text("exit\n", encoding="utf-8")
        exit_seen = not expect_exit_request
        if expect_exit_request:
            try:
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_EXIT_REQUESTED", 20, client_cursor)
                exit_seen = True
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
        stop_result = None
        try:
            stop_result = client.stop()
        except Exception:
            stop_result = ProcessStopResult("failed", False, False, False, False, tuple(client.owned_identities), ())
        owned_absent = stop_result.wrapper_exited and stop_result.descendants_exited
        tcp = capture_tcp_state(self.run_log_root, self.server_port, client, self.server)
        active_states = {"ESTABLISHED", "SYN_SENT", "SYN_RECEIVED", "CLOSE_WAIT",
                         "FIN_WAIT_1", "FIN_WAIT_2"}
        tcp_absent = not any(str(entry.get("State", "")).upper() in active_states
                             for entry in tcp.get("entries", []))
        reader_stopped = client.thread is None or not client.thread.is_alive()
        physical_status = all((player_absent, owned_absent, tcp_absent, reader_stopped,
                               not stop_result.residual_owned_pids,
                               not stop_result.identity_mismatches))
        functional_observability = {
            "exit_requested_seen": exit_seen,
            "exit_request_not_required": not expect_exit_request,
            "client_logout_seen": logout_seen,
            "logout_satisfied": logout_seen,
            "server_disconnect_seen": server_disconnected_seen,
            "server_disconnect_required": expect_server_disconnect,
        }
        physical_cleanup = {
            "status": "passed" if physical_status else "failed",
            "player_absent_from_rcon": player_absent,
            "owned_processes_absent": owned_absent,
            "tcp_connections_absent": tcp_absent,
            "reader_threads_stopped": reader_stopped,
            "wrapper_exited": stop_result.wrapper_exited,
            "descendants_exited": stop_result.descendants_exited,
            "residual_owned_pids": list(stop_result.residual_owned_pids),
            "identity_mismatches": list(stop_result.identity_mismatches),
        }
        return {"status": "passed" if physical_status else "failed",
                "physical_cleanup": physical_cleanup,
                "functional_observability": functional_observability,
                "client_logout_seen": logout_seen,
                "server_disconnect_seen": server_disconnected_seen,
                "player_absent_from_rcon": player_absent,
                "owned_processes_absent": owned_absent,
                "tcp_connections_absent": tcp_absent,
                "reader_threads_stopped": reader_stopped,
                "exit_requested_seen": exit_seen,
                "wrapper_exited": stop_result.wrapper_exited,
                "descendants_exited": stop_result.descendants_exited,
                "residual_owned_pids": list(stop_result.residual_owned_pids),
                "identity_mismatches": list(stop_result.identity_mismatches),
                "stop_result": stop_result.__dict__}

    def wait_player_present(self, username: str, timeout: float = 20.0) -> bool:
        if self.rcon is None:
            return False
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                if username in self.rcon.command("list"):
                    return True
            except Exception:
                pass
            time.sleep(.25)
        return False

    @staticmethod
    def challenge(entry: dict[str, object]) -> str | None:
        value = fields(entry).get("challenge")
        return value if value and value != "-" else None

    def compatible(self) -> None:
        client = self.start_client("compatible-reconnect", "PRCompat")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 240)
        client_cursor = client.cursor()
        server_cursor = self.server.cursor()
        control = self.run_root / "compatible-reconnect" / "control"
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
        control = self.run_root / "compatible-reconnect" / "control"
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
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 240)
        client_cursor = client.cursor(); previous_pending_line = self.server.cursor()
        control = self.run_root / "silent-timeout" / "control"
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
        cleanup = self.cleanup_attempt(client, "silent-timeout", "PRSilent", True, False)
        self.scenarios["silent_timeout"]["cleanup"] = cleanup
        if cleanup["status"] != "passed":
            raise AssertionError("SILENT cleanup failed")

    def absent(self) -> None:
        client = self.start_client("absent", "PRAbsent", with_mod=False)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 240)
        client_cursor = client.cursor(); server_cursor = self.server.cursor()
        control = self.run_root / "absent" / "control"
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
        if any(entry["marker"] in SERVER_MARKERS - {"CLIENT_HANDSHAKE_SERVER_DISCOVERING",
                                                     "CLIENT_HANDSHAKE_SERVER_ABSENT",
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

    def prepare_connected_commit_artifact(self) -> None:
        if self.rcon is None:
            raise RuntimeError("RCON unavailable")
        if self.connected_commit_preparation is not None:
            return
        self.rcon.command("partialreload scan")
        self.wait_rcon_status(r"Last scan:\s*(?!never)", 120)
        self.install_joint_commit_fixture("B")
        time.sleep(1.1)
        self.rcon.command("partialreload scan")
        self.wait_rcon_status(r"Changed resources:\s*[1-9]", 120)
        prepare = self.rcon.command("partialreload prepare tags_recipes")
        if not re.search(r"started|preparation", prepare, re.I):
            raise AssertionError(f"tag/recipe preparation did not start: {prepare}")
        self.wait_rcon_status(r"State:\s*READY", 120)
        prepared = self.rcon.command("partialreload prepared")
        if "PreparedTagsAndRecipes" not in prepared:
            raise AssertionError(f"joint artifact was not prepared: {prepared}")
        self.connected_commit_preparation = {
            "prepare": prepare.strip(),
            "prepared": prepared.strip(),
        }

    def connected_commit(self) -> None:
        if self.rcon is None:
            raise RuntimeError("RCON unavailable")
        self.prepare_connected_commit_artifact()
        response = self.rcon.command("partialreload apply prepared")
        if "TAG_RECIPE_COMMIT_PLAYERS_CONNECTED" not in response:
            raise AssertionError(f"commit was not blocked: {response}")
        self.scenarios["connected_commit_still_blocked"] = {
            "status": "passed",
            **(self.connected_commit_preparation or {}),
            "response": response.strip(),
        }

    def server_absent_client_mod_allowed(self) -> None:
        if self.server_mod_mode != "without_mod":
            raise RuntimeError("INVALID_SCENARIO_SERVER_MODE")
        username = "PRCliCtrl"
        client = self.start_client("server-absent-client-mod", username, with_mod=True)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 240)
        cursor = client.cursor()
        control = self.run_root / "server-absent-client-mod" / "control"
        control.mkdir(parents=True, exist_ok=True)
        (control / "connect.request").write_text("connect\n", encoding="utf-8")
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, cursor)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, cursor)
        skipped = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_PRESENCE_SKIPPED_REMOTE_ABSENT", 60, cursor)
        if any(entry["marker"] == "CLIENT_HANDSHAKE_CLIENT_PRESENCE_SENT" for entry in client.entries()):
            raise AssertionError("presence was sent to a server without the channel")
        self.scenarios["server_absent_client_mod_allowed"] = {"status": "passed", "marker": skipped}
        cleanup = self.cleanup_attempt(client, "server-absent-client-mod", username, True, False)
        self.scenarios["server_absent_client_mod_allowed"]["cleanup"] = cleanup
        if cleanup["status"] != "passed":
            raise AssertionError("server-absent client cleanup failed")

    def server_absent_client_mod_reconnect(self) -> None:
        if self.server_mod_mode != "without_mod":
            raise RuntimeError("INVALID_SCENARIO_SERVER_MODE")
        if self.client_mod_mode not in {"with_mod", ""}:
            raise RuntimeError("INVALID_SCENARIO_CLIENT_MODE")
        cycles = self.cycles or 3
        username = "PRCliReconnect"
        launch_attempts = 0
        transient_aborts: list[dict[str, object]] = []
        while launch_attempts < 5:
            launch_attempts += 1
            name = "server-absent-client-mod-reconnect" if launch_attempts == 1 else f"server-absent-client-mod-reconnect-{launch_attempts}"
            client = self.start_client(name, username, with_mod=True)
            control = self.run_root / name / "control"
            control.mkdir(parents=True, exist_ok=True)
            connections: list[str] = []
            cycle_evidence: list[dict[str, object]] = []
            skips = 0
            try:
                client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 240)
                for cycle in range(cycles):
                    cursor = client.cursor()
                    request = "connect.request" if cycle == 0 else "reconnect.request"
                    (control / request).write_text("connect\n", encoding="utf-8")
                    client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" if cycle == 0 else
                                       "HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_REQUESTED", 60, cursor)
                    login = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 120, cursor)
                    connection = fields(login).get("connection")
                    if connection:
                        connections.append(connection)
                    skipped = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_PRESENCE_SKIPPED_REMOTE_ABSENT", 60, cursor)
                    skips += 1
                    if any(str(e["marker"]).endswith("CLIENT_PRESENCE_SENT") for e in client.entries()):
                        raise AssertionError("presence sent to control server")
                    if cycle < cycles - 1:
                        disconnect_cursor = client.cursor()
                        (control / "disconnect.request").write_text("disconnect\n", encoding="utf-8")
                        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_DISCONNECT_REQUESTED", 60, disconnect_cursor)
                        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGOUT", 60, disconnect_cursor)
                        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_RECONNECT_READY", 60, disconnect_cursor)
                    cycle_evidence.append({"cycle": cycle + 1, "skip": skipped})
                cleanup = self.cleanup_attempt(client, name, username, True, False)
                self.scenarios["server_absent_client_mod_reconnect"] = {
                    "status": "passed" if cleanup["status"] == "passed" and skips == cycles else "failed",
                    "same_client_process": True, "cycles": cycles, "connections": connections,
                    "presence_skipped": skips, "presence_sent": False, "cycle_evidence": cycle_evidence,
                    "cleanup": cleanup,
                    "launch_attempts": launch_attempts, "transient_aborts": transient_aborts}
                if cleanup["status"] != "passed":
                    raise AssertionError("control-server reconnect cleanup failed")
                return
            except Exception as exc:
                markers = {entry["marker"] for entry in client.entries()}
                prelogin = ("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED" in markers
                            and "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" not in markers
                            and "CLIENT_HANDSHAKE_CLIENT_PRESENCE_SENT" not in markers)
                cleanup = self.cleanup_attempt(client, name, username, False, False, False)
                if prelogin and physical_cleanup_passed(cleanup) and launch_attempts < 5:
                    transient_aborts.append({"launch": launch_attempts,
                                             "classification": AttemptClassification.INFRASTRUCTURE_FAILURE.value,
                                             "infrastructure_subtype": "TRANSIENT_LOGIN_ABORT",
                                             "error": str(exc), "cleanup": cleanup})
                    continue
                self.scenarios["server_absent_client_mod_reconnect"] = {
                    "status": "failed", "cycles": cycles, "launch_attempts": launch_attempts,
                    "transient_aborts": transient_aborts, "cleanup": cleanup, "error": str(exc)}
                raise

    def absent_reconnect_stress(self) -> None:
        cycles = self.cycles or 5
        client = self.start_client("absent-reconnect-stress", "PRAbsentStress", with_mod=False)
        client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 240)
        control = self.run_root / "absent-reconnect-stress" / "control"
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
        target = self.required_valid_trials if self.required_valid_trials > 0 else self.cold_login_probes
        limit = self.maximum_launch_attempts if self.maximum_launch_attempts > 0 else target
        index = 0
        while index < limit and sum(item.get("classification") == AttemptClassification.VALID_PASS.value for item in attempts) < target:
            index += 1
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
            client_start_line = -1
            client = self.start_client(name, username,
                                       with_mod=self.client_mod_mode == "with_mod")
            stop_after_attempt = False
            connect_started_at: float | None = None
            ready_wait_seen = False
            connect_wait_seen = False
            login_wait_seen = False
            try:
                ready = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_READY", 90)
                ready_wait_seen = True
                client_cursor = client.cursor()
                server_cursor = self.server.cursor()
                capture_tcp_state(self.run_log_root, self.server_port, client, self.server,
                                  f"tcp-before-connect-{name}.json")
                if self.initial_connect_mode == "CONTROL":
                    control = self.run_root / name / "control"
                    control.mkdir(parents=True, exist_ok=True)
                    connect_started_at = time.monotonic()
                    (control / "connect.request").write_text("connect\n", encoding="utf-8")
                    client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_REQUESTED", 60, client_cursor)
                    connect_wait_seen = True
                    capture_tcp_state(self.run_log_root, self.server_port, client, self.server,
                                      f"tcp-after-connect-request-{name}.json")
                login = client.wait_marker("HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN", 60, client_cursor)
                login_wait_seen = True
                player_present = self.wait_player_present(username)
                trial = {"attempt": index, "status": "passed",
                         "classification": AttemptClassification.VALID_PASS.value,
                         "functional_trial": sum(item.get("classification") == AttemptClassification.VALID_PASS.value for item in attempts) + 1,
                         "ready": ready, "login": login, "player_present_in_rcon": player_present,
                         "log": str(client.log_path)}
                if not player_present:
                    raise AssertionError("player did not appear in RCON list")
                if self.server_mod_mode == "with_mod" and self.client_mod_mode == "without_mod":
                    discovering = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_DISCOVERING", 60, server_cursor)
                    absent = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_ABSENT", 60, int(discovering["line"]))
                    trial.update({"discovering": discovering, "absent": absent})
                elif self.server_mod_mode == "with_mod" and self.client_mod_mode == "with_mod":
                    presence_sent = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_PRESENCE_SENT", 60, client_cursor)
                    presence_received = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PRESENCE_RECEIVED", 60, server_cursor)
                    pending = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_PENDING", 60, int(presence_received["line"]))
                    hello_received = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_RECEIVED", 60, client_cursor)
                    hello_sent = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_HELLO_SENT", 60, int(hello_received["line"]))
                    compatible = self.server.wait_marker("CLIENT_HANDSHAKE_SERVER_COMPATIBLE", 60, int(pending["line"]))
                    accepted = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_ACCEPTED", 60, int(hello_sent["line"]))
                    client_compatible = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_COMPATIBLE", 60, int(accepted["line"]))
                    trial.update({"presence_sent": presence_sent, "presence_received": presence_received,
                                  "pending": pending, "hello_received": hello_received,
                                  "hello_sent": hello_sent, "compatible": compatible,
                                  "accepted": accepted, "client_compatible": client_compatible})
                elif self.server_mod_mode == "without_mod" and self.client_mod_mode == "with_mod":
                    skipped = client.wait_marker("CLIENT_HANDSHAKE_CLIENT_PRESENCE_SKIPPED_REMOTE_ABSENT", 60, client_cursor)
                    trial.update({"presence_skipped": skipped})
                elif self.server_mod_mode != "without_mod" or self.client_mod_mode != "without_mod":
                    raise AssertionError("invalid server/client mod mode combination")
                attempts.append(trial)
            except Exception as exc:
                attempts.append({"attempt": index, "status": "failed", "classification": AttemptClassification.INFRASTRUCTURE_FAILURE.value, "functional_trial": None, "error": str(exc),
                                 "log": str(client.log_path), "attempt_id": self.attempt_ids.get(name)})
                self.scenarios["cold_login"] = {"status": "failed", "mode": self.initial_connect_mode,
                                                 "attempts": attempts, "attempt_count": len(attempts),
                                                 "passed": sum(item.get("status") == "passed" for item in attempts)}
                # Continue with the next fresh client when this attempt cleaned up.
            finally:
                window = AttemptWindow(server_start_line, client_start_line,
                                       self.server.cursor(), client.cursor())
                pre_cleanup_tcp = capture_tcp_state(self.run_log_root, self.server_port, client, self.server,
                                                    f"tcp-before-cleanup-{name}.json")
                fingerprint_diagnostics_for_cleanup: dict[str, object] = {}
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
                    attempts[-1]["classification"] = (AttemptClassification.HARNESS_FAILURE.value
                                                         if "CLEANUP" in classification or "CONTROL_SERVER" in classification
                                                         else AttemptClassification.INFRASTRUCTURE_FAILURE.value)
                    attempts[-1]["login_diagnostics"] = login_diagnostics(self.server, client)
                    window_evidence = attempt_marker_evidence(self.server, client, window, self.run_id, self.attempt_ids.get(name, ""))
                    fingerprint_diagnostics = collect_infrastructure_diagnostics(
                        self.server, client, evidence, window_evidence, window, pre_cleanup_tcp,
                        (time.monotonic() - connect_started_at) if connect_started_at is not None else None,
                        username, self.rcon)
                    fingerprint_diagnostics_for_cleanup = fingerprint_diagnostics
                    attempts[-1]["fingerprint"] = infrastructure_fingerprint(evidence, fingerprint_diagnostics)
                    quality, missing = infrastructure_fingerprint_quality(evidence, fingerprint_diagnostics)
                    attempts[-1]["fingerprint_quality"] = quality.value
                    attempts[-1]["fingerprint_missing_fields"] = missing
                    attempts[-1]["fingerprint_diagnostics"] = fingerprint_diagnostics
                    attempts[-1]["attempt_marker_evidence"] = window_evidence
                    timeline = infra_policy.causal_timeline(attempts[-1])
                    attempts[-1]["causal_timeline"] = timeline
                    first_terminal = infra_policy.first_terminal_event(timeline, fingerprint_diagnostics)
                    attempts[-1]["first_terminal_event"] = first_terminal
                    attempts[-1]["causal_signature"] = infra_policy.canonical_causal_signature(attempts[-1])
                    attempts[-1]["causal_signature_schema_version"] = infra_policy.CAUSAL_SIGNATURE_SCHEMA_VERSION
                    attempts[-1]["causal_signature_diagnostics"] = infra_policy.causal_signature_payload(attempts[-1])
                    attempts[-1]["launch_args"] = launch
                    attempts[-1]["processes"] = {
                        "client": process_summary(client, "client"),
                        "server": process_summary(self.server, "server")}
                    attempts[-1]["window"] = {
                        "server_start_line": window.server_start_line,
                        "client_start_line": window.client_start_line,
                        "server_end_line": window.server_end_line,
                        "client_end_line": window.client_end_line}
                inconsistency = wait_evidence_inconsistency(
                    ready_wait_seen, connect_wait_seen, login_wait_seen, evidence)
                if inconsistency and attempts:
                    attempts[-1]["status"] = "failed"
                    attempts[-1]["classification"] = AttemptClassification.HARNESS_FAILURE.value
                    attempts[-1]["error_code"] = inconsistency
                if evidence.connect_requested_seen and not evidence.network_login_seen:
                    self.failure_capture_errors = capture_thread_dumps(self.run_log_root, client, self.server)
                    self.failure_tcp_state = capture_tcp_state(self.run_log_root, self.server_port, client, self.server)
                    self.failure_process_tree = {
                        "client": process_tree(client.process.pid) if client.process else [],
                        "server": process_tree(self.server.process.pid) if self.server and self.server.process else []}
                entered = evidence.network_login_seen
                attempt_cleanup = self.cleanup_attempt(
                    client, name, username, entered,
                    entered and self.server_mod_mode == "with_mod",
                    should_expect_exit_request(evidence, fingerprint_diagnostics_for_cleanup))
                if attempts:
                    attempts[-1]["cleanup"] = attempt_cleanup
                    final_window = AttemptWindow(window.server_start_line, window.client_start_line,
                                                 self.server.cursor(), client.cursor())
                    marker_evidence = attempt_marker_evidence(
                        self.server, client, final_window, self.run_id, self.attempt_ids.get(name, ""))
                    attempts[-1]["attempt_marker_evidence"] = marker_evidence
                    attempts[-1]["attempt_evidence"] = authorization_attempt_evidence(marker_evidence)
                    attempts[-1]["attempt_evidence_schema_version"] = infra_policy.FINGERPRINT_SCHEMA_VERSION
                    ready_entry = next((entry for entry in marker_evidence.get("client_entries", [])
                                        if entry.get("marker") == "HANDSHAKE_ACCEPTANCE_CLIENT_READY"), None)
                    profile, profile_error = ready_client_profile(
                        ready_entry, self.client_mod_mode, self.initial_connect_mode)
                    if profile is not None:
                        attempts[-1]["client_profile"] = profile
                    if profile_error:
                        attempts[-1]["status"] = "failed"
                        attempts[-1]["classification"] = AttemptClassification.HARNESS_FAILURE.value
                        attempts[-1]["error_code"] = profile_error
                    final_evidence = attempt_evidence(
                        self.server, client, final_window, self.run_id,
                        self.attempt_ids.get(name, ""), self.initial_connect_mode,
                        self.client_mod_mode)
                    if not profile_error:
                        final_classification = classify_attempt(
                            final_evidence,
                            MatrixExpectation(self.server_mod_mode, self.client_mod_mode),
                            attempt_cleanup,
                            attempts[-1].get("fingerprint_diagnostics", fingerprint_diagnostics_for_cleanup))
                        attempts[-1]["classification"] = final_classification.value
                        subtype = infrastructure_subtype(final_evidence, final_classification)
                        if subtype:
                            attempts[-1]["infrastructure_subtype"] = subtype
                        if final_classification == AttemptClassification.VALID_PASS:
                            attempts[-1]["status"] = "passed"
                            attempts[-1]["functional_trial"] = sum(
                                item.get("classification") == AttemptClassification.VALID_PASS.value
                                for item in attempts[:-1]) + 1
                        else:
                            attempts[-1]["status"] = "failed"
                            attempts[-1]["functional_trial"] = None
                    if not physical_cleanup_passed(attempt_cleanup):
                        attempts[-1]["status"] = "failed"
                        attempts[-1]["classification"] = AttemptClassification.HARNESS_FAILURE.value
                        attempts[-1]["error_code"] = "ATTEMPT_CLEANUP_FAILED"
                if attempts:
                    latest_classification = attempts[-1].get("classification")
                    if latest_classification in {
                        AttemptClassification.PRODUCT_FAILURE.value,
                        AttemptClassification.HARNESS_FAILURE.value,
                    }:
                        stop_after_attempt = True
                    if (self.required_valid_trials > 0
                            and latest_classification == AttemptClassification.INFRASTRUCTURE_FAILURE.value
                            and not self.diagnostic_matrix):
                        authorization_required = bool(self.authorized_infrastructure_causal_signatures
                                                      or self.authorized_infrastructure_fingerprints)
                        authorized_by_causal = (bool(self.authorized_infrastructure_causal_signatures)
                                                and attempts[-1].get("causal_signature") in self.authorized_infrastructure_causal_signatures)
                        authorized_by_fingerprint = (not self.authorized_infrastructure_causal_signatures
                                                     and attempts[-1].get("fingerprint") in self.authorized_infrastructure_fingerprints)
                        if authorization_required and not (authorized_by_causal or authorized_by_fingerprint):
                            attempts[-1]["unauthorized_infrastructure_failure"] = True
                            stop_after_attempt = True
            if stop_after_attempt:
                break
        passed_count = sum(item.get("classification") == AttemptClassification.VALID_PASS.value for item in attempts)
        infrastructure = sum(item.get("classification") == AttemptClassification.INFRASTRUCTURE_FAILURE.value for item in attempts)
        product = sum(item.get("classification") == AttemptClassification.PRODUCT_FAILURE.value for item in attempts)
        harness = sum(item.get("classification") == AttemptClassification.HARNESS_FAILURE.value for item in attempts)
        quota = evaluate_quota(attempts, target, limit, self.authorized_infrastructure_fingerprints,
                               self.authorized_infrastructure_causal_signatures)
        diagnostic_ok = product == 0 and harness == 0 and len(attempts) == target
        self.scenarios["cold_login"] = {"status": "passed" if (quota["quota_reached"] or (self.diagnostic_matrix and diagnostic_ok)) else "failed",
                                         "client_mod_mode": self.client_mod_mode,
                                         "authorization_scope": infra_policy.scope_to_dict(self.authorization_scope) if self.authorization_scope else None,
                                         "mode": self.initial_connect_mode,
                                         "attempts": attempts, "attempt_count": len(attempts),
                                         "launch_attempts": len(attempts), "valid_trials": passed_count,
                                         "required_valid_trials": target, "maximum_launch_attempts": limit,
                                         "infrastructure_failures": infrastructure, "product_failures": product,
                                         "harness_failures": harness, "quota_reached": quota["quota_reached"],
                                         "unauthorized_infrastructure_failures": quota["unauthorized_infrastructure_failures"],
                                         "passed": passed_count, "failed": len(attempts) - passed_count}
        self.scenarios["cold_login"]["matrix_complete"] = len(attempts) == target

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
            if self.run_root.exists():
                deadline = time.monotonic() + 15
                while self.run_root.exists() and time.monotonic() < deadline:
                    try:
                        shutil.rmtree(self.run_root)
                    except OSError as exc:
                        last_error = exc
                        time.sleep(.5)
                if self.run_root.exists():
                    errors.append(str(locals().get("last_error", "owned run root remains")))
        except Exception as exc:
            errors.append(str(exc))
        owned_processes = [item for item in [self.server, *self.clients] if item is not None]
        owned_absent = all(item.process is None or item.process.poll() is not None
                           for item in owned_processes)
        run_removed = not self.run_root.exists()
        self.cleanup_result = {"status": "passed" if not errors and owned_absent and run_removed else "failed",
                               "owned_processes_absent": owned_absent,
                               "residual_owned_processes": [],
                               "identity_mismatches": [],
                               "active_reader_threads": [item.name for item in owned_processes
                                                          if item and item.thread and item.thread.is_alive()],
                               "active_owned_tcp_connections": [],
                               "rcon_port_released": not port_open(self.rcon_port),
                               "server_port_released": not port_open(self.server_port),
                               "run_root_removed": run_removed,
                               "lock_released": not ACCEPTANCE_LOCK.exists(),
                               "errors": errors}

    def full_composite(self) -> dict[str, object]:
        def execute_subrun(mode: str, scenarios: set[str]) -> dict[str, object]:
            subrun = Acceptance(self.initial_connect_mode, 0, "with_mod", self.strict_client_isolation,
                                self.require_attempt_cleanup, False, self.cycles, mode, False, 0, 0,
                                None, None, False, self.authorization_scope)
            try:
                return subrun.run(scenarios, manage_lock=False)
            except Exception as exc:
                try:
                    subrun.cleanup()
                except Exception:
                    pass
                return {"status": "failed", "complete_run": False,
                        "server_mod_mode": mode, "error": str(exc),
                        "scenarios": subrun.scenarios, "cleanup": subrun.cleanup_result}

        def execute_with_transient_retries(mode: str, scenarios: set[str], attempts: int) -> dict[str, object]:
            report: dict[str, object] = {}
            for attempt in range(1, attempts + 1):
                report = execute_subrun(mode, scenarios)
                error = str(report.get("error", ""))
                if report.get("status") in {"passed", "diagnostic_passed"}:
                    break
                transient_pre_functional = (
                    "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" in error
                    and "CLIENT_HANDSHAKE_SERVER_" not in error
                    and report.get("cleanup", {}).get("status") == "passed"
                    and not any(isinstance(value, dict) and value.get("status") == "failed"
                                for value in report.get("scenarios", {}).values())
                )
                transient_partial_reconnect = (
                    "timeout waiting for CLIENT_HANDSHAKE_SERVER_PENDING" in error
                    and report.get("cleanup", {}).get("status") == "passed"
                    and not any(isinstance(value, dict) and value.get("status") == "failed"
                                for value in report.get("scenarios", {}).values())
                )
                failed_scenarios = [
                    key for key, value in report.get("scenarios", {}).items()
                    if isinstance(value, dict) and value.get("status") == "failed"
                ]
                transient_failed_server_absent_reconnect = (
                    "HANDSHAKE_ACCEPTANCE_CLIENT_NETWORK_LOGIN" in error
                    and report.get("cleanup", {}).get("status") == "passed"
                    and failed_scenarios == ["server_absent_client_mod_reconnect"]
                )
                transient_commit_preparation = "Changed resources" in error and not report.get("scenarios")
                if not (transient_pre_functional or transient_partial_reconnect
                        or transient_failed_server_absent_reconnect or transient_commit_preparation):
                    break
                report["transient_composite_retry"] = attempt
            return report

        with_main_report = execute_with_transient_retries(
            "with_mod",
            {"compatible", "reconnect", "silent_timeout", "absent_client_allowed"},
            8,
        )
        with_commit_report = execute_with_transient_retries(
            "with_mod",
            {"compatible", "connected_commit_still_blocked"},
            5,
        )
        with_scenarios = {}
        with_scenarios.update(with_main_report.get("scenarios", {}))
        with_scenarios.update({
            key: value for key, value in with_commit_report.get("scenarios", {}).items()
            if key == "connected_commit_still_blocked"
        })
        with_passed = (with_main_report.get("status") in {"passed", "diagnostic_passed"}
                       and with_commit_report.get("status") in {"passed", "diagnostic_passed"}
                       and with_main_report.get("cleanup", {}).get("status") == "passed"
                       and with_commit_report.get("cleanup", {}).get("status") == "passed")
        with_report = {"status": "passed" if with_passed else "failed",
                       "complete_run": False,
                       "server_mod_mode": "with_mod",
                       "scenarios": with_scenarios,
                       "cleanup": {"status": "passed" if with_passed else "failed"},
                       "parts": {"handshake": with_main_report, "connected_commit": with_commit_report}}
        without_report = execute_with_transient_retries(
            "without_mod",
            {"server_absent_client_mod_allowed", "server_absent_client_mod_reconnect"},
            5,
        )
        scenarios = {}
        scenarios.update(with_report.get("scenarios", {}))
        scenarios.update(without_report.get("scenarios", {}))
        passed = (with_report.get("status") in {"passed", "diagnostic_passed"}
                  and without_report.get("status") in {"passed", "diagnostic_passed"}
                  and with_report.get("cleanup", {}).get("status") == "passed"
                  and without_report.get("cleanup", {}).get("status") == "passed")
        return {"status": "passed" if passed else "failed", "complete_run": passed,
                "subruns": {"with_mod": with_report, "without_mod": without_report},
                "scenarios": scenarios, "cleanup": {"status": "passed" if passed else "failed"}}

    def run(self, selected: set[str] | None = None, manage_lock: bool = True) -> dict[str, object]:
        if manage_lock:
            self.acquire_lock()
        selected = selected or {"compatible", "reconnect", "silent_timeout", "absent_client_allowed",
                                "server_absent_client_mod_allowed", "server_absent_client_mod_reconnect",
                                "connected_commit_still_blocked"}
        self.start_server()
        if self.server_smoke_only:
            smoke_report = None
            try:
                isolated = bool(self.control_classpath_result and self.control_classpath_result.get("isolated"))
                smoke_report = {"status": "diagnostic_passed", "complete_run": False,
                        "server_mod_mode": self.server_mod_mode,
                        "server_build_mode": self.server_build_mode,
                        "authorization_scope": infra_policy.scope_to_dict(self.authorization_scope) if self.authorization_scope else None,
                        "server_project_directory": self.server_project_directory,
                        "server_task": self.server_task, "server_booted": True,
                        "rcon_ready": self.rcon is not None,
                        "game_pid_found": bool(self.control_classpath_result and self.control_classpath_result.get("game_pid_found")),
                        "classpath_isolated": isolated,
                        "partialreload_markers_seen": any(entry["marker"].startswith("CLIENT_HANDSHAKE_SERVER_") for entry in self.server.entries()),
                        "partialreload_loaded": (not isolated) or bool(self.control_classpath_result and self.control_classpath_result.get("partialreload_module_present"))}
            finally:
                self.cleanup()
                if manage_lock:
                    self.release_lock()
            smoke_report["cleanup"] = self.cleanup_result
            if self.cleanup_result.get("status") != "passed":
                smoke_report["status"] = "failed"
                smoke_report["classification"] = "CONTROL_SERVER_ISOLATION_FAILED"
            return smoke_report
        cold_mode = False
        try:
            if self.cold_login_probes or self.required_valid_trials > 0:
                self.cold_login()
                cold_mode = True
            else:
                if ("connected_commit_still_blocked" in selected
                        and self.server_mod_mode == "with_mod"):
                    self.prepare_connected_commit_artifact()
                if "compatible" in selected: self.compatible()
                if "reconnect" in selected: self.reconnect()
                if "silent_timeout" in selected: self.silent_timeout()
                if "absent_client_allowed" in selected: self.absent()
                if "server_absent_client_mod_allowed" in selected: self.server_absent_client_mod_allowed()
                if "server_absent_client_mod_reconnect" in selected: self.server_absent_client_mod_reconnect()
                if "connected_commit_still_blocked" in selected: self.connected_commit()
                if "absent_reconnect_stress" in selected: self.absent_reconnect_stress()
        finally:
            self.cleanup()
            if manage_lock:
                self.release_lock()
        if cold_mode:
            cold_passed = self.scenarios.get("cold_login", {}).get("status") == "passed"
            diagnostic_ok = self.diagnostic_matrix and self.scenarios.get("cold_login", {}).get("matrix_complete") is True \
                and self.scenarios.get("cold_login", {}).get("product_failures", 0) == 0 \
                and self.scenarios.get("cold_login", {}).get("harness_failures", 0) == 0
            isolated = bool(self.control_classpath_result and self.control_classpath_result.get("isolated"))
            return {"status": "diagnostic_passed" if cold_passed or diagnostic_ok else "failed", "complete_run": False,
                    "mode": self.initial_connect_mode, "scenarios": self.scenarios,
                    "server_mod_mode": self.server_mod_mode, "client_mod_mode": self.client_mod_mode,
                    "server_task": self.server_task,
                    "server_build_mode": self.server_build_mode,
                    "server_project_directory": self.server_project_directory,
                    "server_main_mod_present": self.server_mod_mode == "with_mod",
                    "authorization_scope": infra_policy.scope_to_dict(self.authorization_scope) if self.authorization_scope else None,
                    "classpath_isolated": isolated if self.server_mod_mode == "without_mod" else None,
                    "partialreload_markers_seen": any(entry["marker"].startswith("CLIENT_HANDSHAKE_SERVER_") for entry in self.server.entries()) if self.server else False,
                    "partialreload_loaded": ((not isolated) or bool(self.control_classpath_result and self.control_classpath_result.get("partialreload_module_present"))) if self.server_mod_mode == "without_mod" else self.server_mod_mode == "with_mod",
                    "required_valid_trials": self.required_valid_trials,
                    "maximum_launch_attempts": self.maximum_launch_attempts,
                    "diagnostic_matrix": self.diagnostic_matrix,
                    "cleanup": self.cleanup_result, "run_id": self.run_id,
                    "log_root": str(self.run_log_root), "attempt_ids": self.attempt_ids}
        full = selected == {"compatible", "reconnect", "silent_timeout", "absent_client_allowed",
                           "server_absent_client_mod_allowed", "server_absent_client_mod_reconnect",
                           "connected_commit_still_blocked"}
        passed = all(item.get("status") == "passed" for item in self.scenarios.values())
        isolated = bool(self.control_classpath_result and self.control_classpath_result.get("isolated"))
        return {"status": "passed" if passed and self.cleanup_result["status"] == "passed" else "failed",
                "complete_run": full and passed and self.cleanup_result["status"] == "passed",
                "scenarios": self.scenarios, "server_mod_mode": self.server_mod_mode,
                "server_task": self.server_task,
                "server_build_mode": self.server_build_mode,
                "server_project_directory": self.server_project_directory,
                "server_main_mod_present": self.server_mod_mode == "with_mod",
                "authorization_scope": infra_policy.scope_to_dict(self.authorization_scope) if self.authorization_scope else None,
                "classpath_isolated": isolated if self.server_mod_mode == "without_mod" else None,
                "partialreload_markers_seen": any(entry["marker"].startswith("CLIENT_HANDSHAKE_SERVER_") for entry in self.server.entries()) if self.server else False,
                "partialreload_loaded": ((not isolated) or bool(self.control_classpath_result and self.control_classpath_result.get("partialreload_module_present"))) if self.server_mod_mode == "without_mod" else self.server_mod_mode == "with_mod",
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
    parser.add_argument("--authorize-infrastructure-from", default=None)
    parser.add_argument("--full-composite", action="store_true")
    parser.add_argument("--diagnostic-matrix", action="store_true")
    args = parser.parse_args()
    if args.required_valid_trials < 0 or args.maximum_launch_attempts < 0 or (
            args.required_valid_trials > 0 and args.maximum_launch_attempts < args.required_valid_trials):
        parser.error("INVALID_TRIAL_QUOTA")
    LOG_ROOT.mkdir(parents=True, exist_ok=True)
    versions = load_handshake_versions()
    execution_scope = expected_authorization_scope(
        args.client_mod_mode, args.initial_connect_mode.upper(), versions)
    authorized = set()
    authorized_causal = set()
    if args.authorize_infrastructure_from:
        try:
            baseline = load_authorized_infrastructure_baseline(
                pathlib.Path(args.authorize_infrastructure_from), args.client_mod_mode)
            matches, changed = infra_policy.authorization_scope_matches(baseline.scope, execution_scope)
            if not matches:
                parser.error("INFRASTRUCTURE_AUTHORIZATION_SCOPE_MISMATCH:" + ",".join(changed))
            authorized = set(baseline.fingerprints)
            authorized_causal = set(baseline.causal_signatures)
        except Exception as exc:
            parser.error(str(exc))
    acceptance = Acceptance(args.initial_connect_mode.upper(), args.cold_login_probes, args.client_mod_mode,
                            args.strict_client_isolation, args.require_attempt_cleanup,
                            args.fresh_server_per_probe, args.cycles, args.server_mod_mode,
                            args.server_smoke_only, args.required_valid_trials,
                            args.maximum_launch_attempts, authorized, authorized_causal, args.diagnostic_matrix,
                            execution_scope)
    selected = None if not args.scenarios else set(args.scenarios.split(","))
    try:
        if args.full_composite:
            acceptance.acquire_lock()
            try:
                report = acceptance.full_composite()
            finally:
                acceptance.release_lock()
        else:
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
        acceptance.release_lock()
        report = {"status": "failed", "complete_run": False, "scenarios": acceptance.scenarios,
                  "server_mod_mode": acceptance.server_mod_mode,
                  "client_mod_mode": acceptance.client_mod_mode,
                  "server_build_mode": acceptance.server_build_mode,
                  "server_task": acceptance.server_task,
                  "server_project_directory": acceptance.server_project_directory,
                  "server_main_mod_present": acceptance.server_mod_mode == "with_mod",
                  "authorization_scope": infra_policy.scope_to_dict(acceptance.authorization_scope) if acceptance.authorization_scope else None,
                  "diagnostic_matrix": acceptance.diagnostic_matrix,
                  "bootstrap_completed": False,
                  "error_code": "SERVER_BOOTSTRAP_FAILED",
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
