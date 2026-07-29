"""Shared fail-closed policy for 4F-A infrastructure fingerprints."""
from __future__ import annotations

import json
import re
from enum import Enum
from types import SimpleNamespace

FINGERPRINT_SCHEMA_VERSION = 2


class FingerprintQuality(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    INSUFFICIENT = "INSUFFICIENT"


TCP_PROGRESS_STATES = {"SYN_SENT", "SYN_RECEIVED", "ESTABLISHED", "BOUND"}
TCP_IGNORED_STATES = {"LISTEN", "TIME_WAIT"}
TCP_TERMINAL_STATES = {"CLOSED", "CLOSE_WAIT", "FIN_WAIT_1", "FIN_WAIT_2", "LAST_ACK", "CLOSING", "DELETE_TCB"}
TERMINAL_TCP_STATES = TCP_TERMINAL_STATES

_TCP_ALIASES = {
    "SYNSENT": "SYN_SENT",
    "SYN_SENT": "SYN_SENT",
    "SYNRECEIVED": "SYN_RECEIVED",
    "SYN_RECEIVED": "SYN_RECEIVED",
    "ESTABLISHED": "ESTABLISHED",
    "BOUND": "BOUND",
    "LISTEN": "LISTEN",
    "TIMEWAIT": "TIME_WAIT",
    "TIME_WAIT": "TIME_WAIT",
    "CLOSEWAIT": "CLOSE_WAIT",
    "CLOSE_WAIT": "CLOSE_WAIT",
    "FINWAIT1": "FIN_WAIT_1",
    "FIN_WAIT_1": "FIN_WAIT_1",
    "FINWAIT2": "FIN_WAIT_2",
    "FIN_WAIT_2": "FIN_WAIT_2",
    "LASTACK": "LAST_ACK",
    "LAST_ACK": "LAST_ACK",
    "DELETETCB": "DELETE_TCB",
    "DELETE_TCB": "DELETE_TCB",
    "CLOSING": "CLOSING",
    "CLOSED": "CLOSED",
}

TERMINAL_ERROR_PATTERNS = tuple(re.compile(pattern, re.IGNORECASE) for pattern in (
    r"\bFailed to connect\b",
    r"\bConnection timed out\b",
    r"\bConnection refused\b",
    r"\bConnection reset\b",
    r"\bInternal Exception\b",
    r"\bDisconnected\b",
    r"\bUnknown custom packet\b",
    r"\brejected.*partialreload:client_sync\b",
    r"\bMod mismatch\b",
    r"\bLogin timed out\b",
    r"\bTimed out waiting for\b",
    r"\bjava\.[A-Za-z0-9_.]+Exception\b",
    r"\bnetty.*exception\b",
))


def normalize_fingerprint_value(value: object) -> str | bool | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    text = str(value)
    text = re.sub(r"\[[^\]]+\]\s*", "", text)
    text = re.sub(r"\b(?:run|attempt|pid|port|player|connection|uuid|username)=[^\s]+", "", text, flags=re.I)
    text = re.sub(r"[A-Fa-f0-9]{8,}(?:-[A-Fa-f0-9]{4,})*", "<id>", text)
    text = re.sub(r"0x[A-Fa-f0-9]+", "<addr>", text)
    text = re.sub(r"\b\d{2,}\b", "<n>", text)
    text = re.sub(r"[A-Za-z]:\\[^\s]+", "<path>", text)
    return re.sub(r"\s+", " ", text).strip()


def elapsed_connect_bucket(elapsed_seconds: object) -> str | None:
    if elapsed_seconds is None:
        return None
    try:
        elapsed = float(elapsed_seconds)
    except (TypeError, ValueError):
        return None
    if elapsed < 5:
        return "LT_5_SECONDS"
    if elapsed < 15:
        return "5_TO_15_SECONDS"
    if elapsed < 30:
        return "15_TO_30_SECONDS"
    if elapsed < 60:
        return "30_TO_60_SECONDS"
    return "GT_60_SECONDS"


def normalize_tcp_state(state: object) -> str | None:
    key = re.sub(r"[^A-Za-z0-9_]", "", str(state or "")).upper()
    return _TCP_ALIASES.get(key)


def tcp_state_summary(tcp_or_states: object) -> str | None:
    if tcp_or_states is None:
        return None
    values: list[object]
    if isinstance(tcp_or_states, dict):
        values = [entry.get("State") or entry.get("state") for entry in tcp_or_states.get("entries", []) if isinstance(entry, dict)]
    elif isinstance(tcp_or_states, (list, tuple, set)):
        values = list(tcp_or_states)
    else:
        values = [tcp_or_states]
    states = {state for state in (normalize_tcp_state(value) for value in values)
              if state and state not in TCP_IGNORED_STATES}
    return ",".join(sorted(states)) if states else "NONE"


def tcp_terminal_evidence(states: object) -> bool:
    summary = tcp_state_summary(states)
    if not summary or summary == "NONE":
        return False
    return any(state in TCP_TERMINAL_STATES for state in summary.split(","))


def last_error_log_signature(lines: list[str]) -> str | None:
    selected = []
    for line in lines:
        if any(pattern.search(line) for pattern in TERMINAL_ERROR_PATTERNS):
            cleaned = normalize_fingerprint_value(line)
            if cleaned:
                selected.append(str(cleaned))
    return " | ".join(selected[-5:]) if selected else None


def fingerprint_payload(evidence: object, diagnostics: dict[str, object]) -> dict[str, object]:
    last_client_marker = diagnostics.get("last_client_marker") or diagnostics.get("last_marker")
    meaningful = diagnostics.get("last_meaningful_client_marker") or last_client_marker
    if meaningful == "HANDSHAKE_ACCEPTANCE_CLIENT_CONNECT_HEARTBEAT":
        meaningful = None
    return {
        "schema_version": FINGERPRINT_SCHEMA_VERSION,
        "last_client_marker": normalize_fingerprint_value(last_client_marker),
        "last_meaningful_client_marker": normalize_fingerprint_value(meaningful),
        "last_server_marker": normalize_fingerprint_value(diagnostics.get("last_server_marker")),
        "last_client_screen": normalize_fingerprint_value(diagnostics.get("screen") or diagnostics.get("last_client_screen")),
        "disconnect_reason": normalize_fingerprint_value(diagnostics.get("disconnect_reason") or diagnostics.get("disconnected_reason")),
        "client_connection_phase": diagnostics.get("client_connection_phase") or "UNKNOWN",
        "server_login_timeout_seen": bool(diagnostics.get("server_login_timeout_seen")),
        "server_player_join_seen": bool(diagnostics.get("server_player_join_seen")),
        "player_present_in_rcon": bool(diagnostics.get("player_present_in_rcon")),
        "tcp_state_summary": tcp_state_summary(diagnostics.get("tcp_state_summary")),
        "tcp_terminal_evidence": tcp_terminal_evidence(diagnostics.get("tcp_state_summary")),
        "client_game_process_alive": diagnostics.get("client_game_process_alive"),
        "server_game_process_alive": diagnostics.get("server_game_process_alive"),
        "elapsed_connect_bucket": elapsed_connect_bucket(diagnostics.get("elapsed_connect_seconds")),
        "last_client_error_signature": normalize_fingerprint_value(diagnostics.get("last_client_error_signature") or diagnostics.get("last_client_log_signature")),
        "last_server_error_signature": normalize_fingerprint_value(diagnostics.get("last_server_error_signature") or diagnostics.get("last_server_log_signature")),
        "channel_rejection_seen": bool(getattr(evidence, "channel_rejection_seen", False) or diagnostics.get("channel_rejection_seen")),
        "unknown_custom_packet_seen": bool(getattr(evidence, "unknown_custom_packet_seen", False) or diagnostics.get("unknown_custom_packet_seen")),
        "partialreload_marker_seen": bool(diagnostics.get("partialreload_marker_seen")),
    }


def assess_fingerprint_quality(payload: dict[str, object]) -> tuple[FingerprintQuality, list[str]]:
    missing: list[str] = []
    if payload.get("schema_version") != FINGERPRINT_SCHEMA_VERSION:
        missing.append("schema_version")
    if payload.get("client_connection_phase") in {None, "UNKNOWN"}:
        missing.append("client_connection_phase")
    if payload.get("elapsed_connect_bucket") is None:
        missing.append("elapsed_connect_bucket")
    if payload.get("client_game_process_alive") is None:
        missing.append("client_game_process_alive")
    if payload.get("server_game_process_alive") is None:
        missing.append("server_game_process_alive")
    if payload.get("last_meaningful_client_marker") is None:
        missing.append("last_meaningful_client_marker")
    observable = any(payload.get(key) not in {None, "", False} for key in (
        "last_client_screen", "disconnect_reason", "tcp_state_summary",
        "last_client_error_signature", "last_server_error_signature",
    )) or payload.get("server_login_timeout_seen") is not None
    terminal = bool(
        payload.get("last_client_screen") in {"DisconnectedScreen", "ModMismatchDisconnectedScreen"}
        or payload.get("disconnect_reason")
        or payload.get("server_login_timeout_seen") is True
        or payload.get("tcp_terminal_evidence") is True
        or payload.get("client_game_process_alive") is False
        or payload.get("last_client_error_signature")
        or payload.get("last_server_error_signature")
    )
    if not missing and observable and terminal:
        return FingerprintQuality.HIGH, []
    if (payload.get("client_connection_phase") not in {None, "UNKNOWN"}
            and payload.get("elapsed_connect_bucket") is not None
            and payload.get("client_game_process_alive") is not None
            and payload.get("server_game_process_alive") is not None):
        return FingerprintQuality.MEDIUM, missing
    return FingerprintQuality.INSUFFICIENT, missing


def canonical_fingerprint(evidence: object, diagnostics: dict[str, object]) -> str:
    return json.dumps(fingerprint_payload(evidence, diagnostics), sort_keys=True, separators=(",", ":"))


def _stored_payload(attempt: dict[str, object]) -> dict[str, object] | None:
    try:
        value = json.loads(str(attempt.get("fingerprint")))
        return value if isinstance(value, dict) else None
    except Exception:
        return None


def validate_authorizable_attempt(attempt: dict[str, object]) -> tuple[bool, str | None]:
    if attempt.get("classification") != "INFRASTRUCTURE_FAILURE":
        return False, "NOT_INFRASTRUCTURE_FAILURE"
    if attempt.get("status") != "failed" or attempt.get("functional_trial") is not None:
        return False, "INVALID_ATTEMPT_STATUS"
    cleanup = attempt.get("cleanup") if isinstance(attempt.get("cleanup"), dict) else {}
    if cleanup.get("status") != "passed" or cleanup.get("residual_owned_pids") or cleanup.get("identity_mismatches"):
        return False, "ATTEMPT_CLEANUP_NOT_PASSED"
    evidence = attempt.get("attempt_evidence") if isinstance(attempt.get("attempt_evidence"), dict) else None
    if evidence is None:
        return False, "ATTEMPT_EVIDENCE_MISSING"
    if evidence.get("network_login") or evidence.get("network_login_seen"):
        return False, "NETWORK_LOGIN_TRUE"
    diagnostics = attempt.get("fingerprint_diagnostics") if isinstance(attempt.get("fingerprint_diagnostics"), dict) else {}
    if diagnostics.get("partialreload_marker_seen") or diagnostics.get("channel_rejection_seen") or diagnostics.get("unknown_custom_packet_seen"):
        return False, "PRODUCT_SIGNAL_PRESENT"
    if diagnostics.get("player_present_in_rcon"):
        return False, "PLAYER_PRESENT_IN_RCON"
    if not (evidence.get("client_ready") or evidence.get("client_ready_seen")):
        return False, "READY_MISSING"
    if not (evidence.get("connect_requested") or evidence.get("connect_requested_seen")):
        return False, "CONNECT_REQUESTED_MISSING"
    payload = _stored_payload(attempt)
    if payload is None or payload.get("schema_version") != FINGERPRINT_SCHEMA_VERSION:
        return False, "FINGERPRINT_SCHEMA_INVALID"
    recomputed_payload = fingerprint_payload(SimpleNamespace(channel_rejection_seen=False, unknown_custom_packet_seen=False), diagnostics)
    recomputed_fingerprint = json.dumps(recomputed_payload, sort_keys=True, separators=(",", ":"))
    if attempt.get("fingerprint") != recomputed_fingerprint:
        return False, "FINGERPRINT_INTEGRITY_MISMATCH"
    quality, missing = assess_fingerprint_quality(recomputed_payload)
    if attempt.get("fingerprint_quality") != quality.value:
        return False, "FINGERPRINT_INTEGRITY_MISMATCH"
    if list(attempt.get("fingerprint_missing_fields") or []) != missing:
        return False, "FINGERPRINT_INTEGRITY_MISMATCH"
    if quality != FingerprintQuality.HIGH or missing:
        return False, "FINGERPRINT_NOT_HIGH"
    return True, None


def validate_control_baseline_report(report: dict[str, object]) -> tuple[bool, str | None, set[str]]:
    if report.get("server_mod_mode") != "without_mod":
        return False, "CONTROL_SERVER_MODE_INVALID", set()
    if report.get("server_build_mode") != "independent_gradle_build":
        return False, "CONTROL_BUILD_MODE_INVALID", set()
    if report.get("classpath_isolated") is not True or report.get("partialreload_loaded") is not False or report.get("partialreload_markers_seen") is not False:
        return False, "CONTROL_ISOLATION_INVALID", set()
    if report.get("diagnostic_matrix") is not True or report.get("status") != "diagnostic_passed" or report.get("complete_run") is not False:
        return False, "CONTROL_MATRIX_METADATA_INVALID", set()
    if report.get("cleanup", {}).get("status") != "passed":
        return False, "CONTROL_CLEANUP_INVALID", set()
    cold = report.get("scenarios", {}).get("cold_login", {}) if isinstance(report.get("scenarios"), dict) else {}
    attempts = cold.get("attempts", []) if isinstance(cold, dict) else []
    if cold.get("matrix_complete") is not True or cold.get("launch_attempts") != 10 or cold.get("attempt_count") != 10 or len(attempts) != 10:
        return False, "CONTROL_MATRIX_INCOMPLETE", set()
    if cold.get("product_failures") != 0 or cold.get("harness_failures") != 0:
        return False, "CONTROL_FAILURES_PRESENT", set()
    if int(cold.get("valid_trials", 0)) + int(cold.get("infrastructure_failures", 0)) != 10:
        return False, "CONTROL_COUNTS_INVALID", set()
    fingerprints: set[str] = set()
    for attempt in attempts:
        cleanup = attempt.get("cleanup") if isinstance(attempt.get("cleanup"), dict) else {}
        if cleanup.get("status") != "passed":
            return False, "CONTROL_ATTEMPT_CLEANUP_INVALID", set()
        if attempt.get("classification") == "INFRASTRUCTURE_FAILURE":
            valid, error = validate_authorizable_attempt(attempt)
            if not valid:
                return False, error, set()
            fingerprints.add(str(attempt.get("fingerprint")))
    if int(cold.get("infrastructure_failures", 0)) and not fingerprints:
        return False, "NO_INFRASTRUCTURE_FINGERPRINTS", set()
    return True, None, fingerprints
