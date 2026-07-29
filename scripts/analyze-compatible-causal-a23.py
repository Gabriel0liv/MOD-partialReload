"""Offline causal analysis for 4F-A23 compatible-client infrastructure failures."""
from __future__ import annotations

import argparse
import json
import pathlib
from collections import Counter, defaultdict

import handshake_infrastructure_policy as policy


def load_report(path: pathlib.Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def cold_attempts(report: dict[str, object]) -> list[dict[str, object]]:
    scenarios = report.get("scenarios", {})
    cold = scenarios.get("cold_login", {}) if isinstance(scenarios, dict) else {}
    attempts = cold.get("attempts", []) if isinstance(cold, dict) else []
    return list(attempts) if isinstance(attempts, list) else []


def high_infrastructure_attempts(report: dict[str, object]) -> list[dict[str, object]]:
    result = []
    for attempt in cold_attempts(report):
        if attempt.get("classification") != "INFRASTRUCTURE_FAILURE":
            continue
        ok, _ = policy.validate_authorizable_attempt(attempt)
        if ok:
            result.append(attempt)
    return result


def first_historical_attempt(report: dict[str, object]) -> dict[str, object] | None:
    return next((attempt for attempt in cold_attempts(report)
                 if attempt.get("classification") == "INFRASTRUCTURE_FAILURE"), None)


def attempt_causal_summary(attempt: dict[str, object],
                           historical_fingerprint: str | None = None,
                           historical_causal_signature: str | None = None) -> dict[str, object]:
    fingerprint = str(attempt.get("fingerprint") or "")
    causal_signature = policy.canonical_causal_signature(attempt)
    causal_payload = policy.causal_signature_payload(attempt)
    causal_ok, causal_error = policy.causal_signature_authorizable(causal_payload)
    diagnostics = attempt.get("fingerprint_diagnostics") if isinstance(attempt.get("fingerprint_diagnostics"), dict) else {}
    stored_payload = json.loads(fingerprint) if fingerprint else {}
    return {
        "classification": attempt.get("classification"),
        "fingerprint": fingerprint,
        "fingerprint_quality": attempt.get("fingerprint_quality"),
        "fingerprint_missing_fields": attempt.get("fingerprint_missing_fields"),
        "matches_historical_fingerprint": fingerprint == historical_fingerprint,
        "fingerprint_diff_from_historical": (
            policy.compare_fingerprint_payloads(historical_fingerprint, fingerprint)
            if historical_fingerprint and fingerprint else None
        ),
        "causal_signature": causal_signature,
        "causal_signature_payload": causal_payload,
        "causal_authorizable": causal_ok,
        "causal_authorization_error": causal_error,
        "matches_historical_causal_signature": causal_signature == historical_causal_signature,
        "causal_timeline": policy.causal_timeline(attempt),
        "first_terminal_event": policy.first_terminal_event(policy.causal_timeline(attempt), diagnostics),
        "last_meaningful_client_marker": stored_payload.get("last_meaningful_client_marker"),
        "last_client_marker": stored_payload.get("last_client_marker"),
        "last_server_marker": stored_payload.get("last_server_marker"),
        "screen": stored_payload.get("last_client_screen"),
        "disconnect_reason": stored_payload.get("disconnect_reason"),
        "elapsed_bucket": stored_payload.get("elapsed_connect_bucket"),
        "tcp_states": stored_payload.get("tcp_state_summary"),
        "terminal_tcp_evidence": stored_payload.get("tcp_terminal_evidence"),
        "client_process_alive": stored_payload.get("client_game_process_alive"),
        "server_process_alive": stored_payload.get("server_game_process_alive"),
        "client_error_signature": stored_payload.get("last_client_error_signature"),
        "server_error_signature": stored_payload.get("last_server_error_signature"),
        "cleanup": attempt.get("cleanup"),
    }


def report_summary(report: dict[str, object]) -> dict[str, object]:
    attempts = cold_attempts(report)
    counts = Counter(str(attempt.get("classification")) for attempt in attempts)
    return {
        "run_id": report.get("run_id"),
        "server_mod_mode": report.get("server_mod_mode"),
        "client_mod_mode": report.get("client_mod_mode"),
        "authorization_scope": report.get("authorization_scope"),
        "launches": len(attempts),
        "valid": counts.get("VALID_PASS", 0),
        "infrastructure": counts.get("INFRASTRUCTURE_FAILURE", 0),
        "product": counts.get("PRODUCT_FAILURE", 0),
        "harness": counts.get("HARNESS_FAILURE", 0),
        "cleanup_passed": (report.get("cleanup") or {}).get("status") == "passed",
    }


def analyze(historical: dict[str, object],
            control: dict[str, object],
            target: dict[str, object]) -> dict[str, object]:
    historical_attempt = first_historical_attempt(historical)
    historical_fingerprint = str(historical_attempt.get("fingerprint")) if historical_attempt else None
    historical_causal_signature = policy.canonical_causal_signature(historical_attempt) if historical_attempt else None
    control_failures = [attempt_causal_summary(attempt, historical_fingerprint, historical_causal_signature)
                        for attempt in high_infrastructure_attempts(control)]
    target_failures = [attempt_causal_summary(attempt, historical_fingerprint, historical_causal_signature)
                       for attempt in high_infrastructure_attempts(target)]
    historical_summary = (attempt_causal_summary(historical_attempt, historical_fingerprint, historical_causal_signature)
                          if historical_attempt else None)
    target_matches_historical = any(item["matches_historical_causal_signature"] for item in target_failures)
    control_matches_historical = any(item["matches_historical_causal_signature"] for item in control_failures)
    if target_matches_historical and not control_matches_historical:
        causal_decision = "CAUSAL_TARGET_CORRELATED"
    elif control_matches_historical:
        causal_decision = "CAUSAL_MATCH_CONTROL_OBSERVED"
    elif not control_failures and not target_failures:
        causal_decision = "CAUSAL_HISTORICAL_NOT_REPRODUCED"
    else:
        causal_decision = "CAUSAL_HISTORICAL_NOT_REPRODUCED"
    return {
        "status": "passed",
        "comparison_profile": "compatible_client",
        "historical": historical_summary,
        "control": {**report_summary(control), "infrastructure_failures": control_failures},
        "target": {**report_summary(target), "infrastructure_failures": target_failures},
        "causal_decision": causal_decision,
        "historical_causal_signature": historical_causal_signature,
        "historical_fingerprint": historical_fingerprint,
    }


def build_baseline(analysis: dict[str, object],
                   control_reports: list[dict[str, object]],
                   source_report: str) -> dict[str, object]:
    historical_signature = analysis.get("historical_causal_signature")
    historical_fingerprint = analysis.get("historical_fingerprint")
    occurrences: list[dict[str, object]] = []
    run_ids: set[str] = set()
    associated_fingerprints: set[str] = set()
    scopes = []
    for report in control_reports:
        scopes.append(report.get("authorization_scope"))
        for attempt in high_infrastructure_attempts(report):
            signature = policy.canonical_causal_signature(attempt)
            if signature != historical_signature:
                continue
            run_id = str(report.get("run_id") or "")
            run_ids.add(run_id)
            associated_fingerprints.add(str(attempt.get("fingerprint")))
            occurrences.append({
                "run_id": run_id,
                "launch": attempt.get("launch"),
                "fingerprint": attempt.get("fingerprint"),
                "causal_signature": signature,
                "matches_historical_fingerprint": attempt.get("fingerprint") == historical_fingerprint,
            })
    scope = scopes[0] if scopes else None
    scope_match = all(scope == item for item in scopes if item is not None)
    passed = bool(historical_signature) and len(run_ids) >= 2 and len(occurrences) >= 2 and scope_match
    return {
        "status": "passed" if passed else "failed",
        "comparison_profile": "compatible_client",
        "baseline_kind": "causal_signature_recurrence",
        "historical_fingerprint": historical_fingerprint,
        "historical_causal_signature": historical_signature,
        "required_independent_control_runs": 2,
        "matching_control_runs": sorted(run_ids),
        "matching_occurrences": occurrences,
        "scope_match": scope_match,
        "authorization_scope": scope,
        "authorized_causal_signatures": [historical_signature] if passed else [],
        "associated_control_fingerprints": sorted(associated_fingerprints) if passed else [],
        "source_report": source_report,
        "failure_reason": None if passed else "CAUSAL_BASELINE_NOT_REPRODUCED_IN_TWO_CONTROL_RUNS",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    analyze_parser = sub.add_parser("analyze")
    analyze_parser.add_argument("historical")
    analyze_parser.add_argument("control")
    analyze_parser.add_argument("target")
    analyze_parser.add_argument("--out", required=True)
    baseline_parser = sub.add_parser("baseline")
    baseline_parser.add_argument("analysis")
    baseline_parser.add_argument("control_reports", nargs="+")
    baseline_parser.add_argument("--out", required=True)
    args = parser.parse_args()

    if args.command == "analyze":
        result = analyze(load_report(pathlib.Path(args.historical)),
                         load_report(pathlib.Path(args.control)),
                         load_report(pathlib.Path(args.target)))
    else:
        result = build_baseline(load_report(pathlib.Path(args.analysis)),
                                [load_report(pathlib.Path(path)) for path in args.control_reports],
                                args.analysis)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result.get("status") == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
