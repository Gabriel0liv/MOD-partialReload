"""Adjudicate 4F-A pre-login infrastructure failures using schema-2 evidence."""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
from collections import Counter

import handshake_infrastructure_policy as policy


def cold_scenario(report: dict[str, object]) -> dict[str, object]:
    scenarios = report.get("scenarios", {})
    cold = scenarios.get("cold_login", {}) if isinstance(scenarios, dict) else {}
    return cold if isinstance(cold, dict) else {}


def cold_attempts(report: dict[str, object]) -> list[dict[str, object]]:
    attempts = cold_scenario(report).get("attempts", [])
    return list(attempts) if isinstance(attempts, list) else []


def summarize(report: dict[str, object]) -> dict[str, object]:
    attempts = cold_attempts(report)
    counts = Counter(str(item.get("classification")) for item in attempts)
    return {
        "launches": len(attempts),
        "valid": counts.get("VALID_PASS", 0),
        "infrastructure": counts.get("INFRASTRUCTURE_FAILURE", 0),
        "product": counts.get("PRODUCT_FAILURE", 0),
        "harness": counts.get("HARNESS_FAILURE", 0),
    }


def all_cleanups_passed(report: dict[str, object]) -> bool:
    attempts = cold_attempts(report)
    return bool(attempts) and all((item.get("cleanup") or {}).get("status") == "passed" for item in attempts)


def high_fingerprints(report: dict[str, object]) -> set[str]:
    return {str(item.get("fingerprint")) for item in cold_attempts(report)
            if policy.validate_authorizable_attempt(item)[0]}


def high_causal_signatures(report: dict[str, object]) -> set[str]:
    result = set()
    for item in cold_attempts(report):
        if item.get("classification") != "INFRASTRUCTURE_FAILURE":
            continue
        try:
            payload = policy.causal_signature_payload(item)
            ok, _ = policy.causal_signature_authorizable(payload)
            if ok:
                result.add(json.dumps(payload, sort_keys=True, separators=(",", ":")))
        except Exception:
            pass
    return result


def expected_client_mode(profile: str) -> str:
    if profile == "compatible_client":
        return "with_mod"
    if profile == "absent_client":
        return "without_mod"
    raise ValueError("INVALID_COMPARISON_PROFILE")


def validate_target_report(report: dict[str, object], expected_client_mod_mode: str | None = None
                           ) -> tuple[bool, str | None]:
    cold = cold_scenario(report)
    attempts = cold_attempts(report)
    if report.get("status") == "failed" and report.get("bootstrap_completed") is False:
        return False, "TARGET_EXECUTION_FAILED"
    if report.get("server_mod_mode") != "with_mod" or report.get("server_main_mod_present") is not True:
        return False, "TARGET_SERVER_MODE_INVALID"
    scope_valid, scope_error, _ = policy.validate_authorization_scope(report, expected_client_mod_mode)
    if not scope_valid:
        return False, scope_error
    if report.get("diagnostic_matrix") is not True or report.get("cleanup", {}).get("status") != "passed":
        return False, "TARGET_METADATA_INVALID"
    if cold.get("matrix_complete") is not True or cold.get("launch_attempts") != 10 or len(attempts) != 10:
        return False, "TARGET_MATRIX_INCOMPLETE"
    if cold.get("product_failures") != 0 or cold.get("harness_failures") != 0:
        return False, "TARGET_PRODUCT_OR_HARNESS_FAILURE"
    if not all_cleanups_passed(report):
        return False, "TARGET_CLEANUP_FAILED"
    return True, None


def historical_failure(quota: dict[str, object]) -> dict[str, object]:
    failed = next((item for item in cold_attempts(quota)
                   if item.get("classification") == "INFRASTRUCTURE_FAILURE"), {})
    quality = failed.get("fingerprint_quality") or "INSUFFICIENT"
    return {"fingerprint_quality": quality, "quality": quality,
            "fingerprint": failed.get("fingerprint"),
            "explained": False, "authorized": False}


def adjudicate(quota: dict[str, object], control: dict[str, object], target: dict[str, object],
               comparison_profile: str = "absent_client") -> dict[str, object]:
    expected_mode = expected_client_mode(comparison_profile)
    control_valid, control_error, control_fingerprints = policy.validate_control_baseline_report(control, expected_mode)
    target_valid, target_error = validate_target_report(target, expected_mode)
    control_summary = summarize(control)
    target_summary = summarize(target)
    target_high = high_fingerprints(target)
    control_causal = high_causal_signatures(control)
    target_causal = high_causal_signatures(target)
    history = historical_failure(quota)
    historical_fp = history.get("fingerprint") if history.get("fingerprint_quality") == "HIGH" else None
    historical_attempt = next((item for item in cold_attempts(quota)
                               if item.get("classification") == "INFRASTRUCTURE_FAILURE"), {})
    historical_causal = policy.canonical_causal_signature(historical_attempt) if historical_attempt else None
    scope_match = False
    scope_differences: list[str] = []
    control_scope = target_scope = None
    if control_valid and target_valid:
        control_scope = policy.scope_from_dict(control["authorization_scope"])
        target_scope = policy.scope_from_dict(target["authorization_scope"])
        scope_match, scope_differences = policy.authorization_scope_matches(control_scope, target_scope)
    errors = {}
    if not control_valid:
        errors["control"] = control_error
    if not target_valid:
        errors["target"] = target_error
    decision = "CASE_INVALID_EVIDENCE" if errors else "CASE_UNRESOLVED_INSUFFICIENT_DIAGNOSTICS"
    authorized: set[str] = set()
    if not errors and not scope_match:
        errors["scope"] = "INFRASTRUCTURE_AUTHORIZATION_SCOPE_MISMATCH"
        decision = "CASE_INVALID_EVIDENCE"
    elif not errors and comparison_profile == "compatible_client":
        if historical_fp and historical_fp in control_fingerprints:
            decision = "COMPATIBLE_CASE_A_MATCHED_INFRASTRUCTURE"
            authorized = {str(historical_fp)}
        elif control_summary["valid"] == 10 and control_summary["infrastructure"] == 0 and target_summary["valid"] == 10 and target_summary["infrastructure"] == 0:
            decision = "COMPATIBLE_CASE_D_RECONFIRMED"
        elif control_summary["valid"] == 10 and control_summary["infrastructure"] == 0 and historical_fp and historical_fp in target_high:
            decision = "COMPATIBLE_CASE_B_TARGET_CORRELATED"
        elif historical_fp and historical_fp in target_high and control_fingerprints and historical_fp not in control_fingerprints:
            decision = "COMPATIBLE_CASE_C_NON_EQUIVALENT"
        elif historical_fp and historical_fp not in control_fingerprints and historical_fp not in target_high:
            decision = "COMPATIBLE_CASE_HISTORICAL_NOT_REPRODUCED"
        else:
            decision = "COMPATIBLE_CASE_UNRESOLVED"
        if historical_causal and historical_causal in control_causal:
            causal_decision = "CAUSAL_MATCH_CONTROL_OBSERVED"
        elif historical_causal and historical_causal in target_causal and historical_causal not in control_causal:
            causal_decision = "CAUSAL_TARGET_CORRELATED"
        elif historical_causal and historical_causal not in control_causal and historical_causal not in target_causal:
            causal_decision = "CAUSAL_HISTORICAL_NOT_REPRODUCED"
        elif control_causal & target_causal:
            causal_decision = "CAUSAL_SHARED_INFRASTRUCTURE_CANDIDATE"
        else:
            causal_decision = "CAUSAL_EVIDENCE_INSUFFICIENT"
    elif not errors:
        if control_summary["valid"] == 10 and control_summary["infrastructure"] == 0 and target_summary["valid"] == 10 and target_summary["infrastructure"] == 0:
            decision = "CASE_D_RECONFIRMED"
        elif control_fingerprints & target_high:
            decision = "CASE_A_LATE_FINGERPRINT_CONFIRMED"
            authorized = control_fingerprints & target_high
        elif control_summary["valid"] == 10 and control_summary["infrastructure"] == 0 and target_high:
            decision = "CASE_B_PRODUCT_CORRELATED_PRELOGIN_FAILURE"
        elif control_fingerprints and target_high and not (control_fingerprints & target_high):
            decision = "CASE_C_NON_EQUIVALENT_FAILURES"
        elif control_fingerprints and target_summary["valid"] == 10 and target_summary["infrastructure"] == 0:
            decision = "CASE_CONTROL_BASELINE_ESTABLISHED"
            authorized = control_fingerprints
    return {
        "policy": {
            "fingerprint_schema_version": policy.FINGERPRINT_SCHEMA_VERSION,
            "terminal_tcp_states": sorted(policy.TCP_TERMINAL_STATES),
            "progress_tcp_states": sorted(policy.TCP_PROGRESS_STATES),
        },
        "previous_case": "D",
        "comparison_profile": comparison_profile,
        "scope_match": scope_match,
        "scope_differences": scope_differences,
        "expected_authorization_scope": policy.scope_to_dict(control_scope) if control_scope else None,
        "decision": decision,
        "previous_fingerprint_decision": "COMPATIBLE_CASE_C_NON_EQUIVALENT" if comparison_profile == "compatible_client" else None,
        "causal_decision": locals().get("causal_decision"),
        "historical_causal_signature": historical_causal,
        "additional_control_failure_modes": sorted(control_fingerprints - ({str(historical_fp)} if historical_fp else set())),
        "additional_target_failure_modes": sorted(target_high - ({str(historical_fp)} if historical_fp else set())),
        "control_causal_signatures": sorted(control_causal),
        "target_causal_signatures": sorted(target_causal),
        "historical_failure": history,
        "control_baseline": {
            "valid_report": control_valid,
            "established": decision == "CASE_CONTROL_BASELINE_ESTABLISHED",
            **control_summary,
            "authorized_attempts": len(control_fingerprints),
            "all_cleanups_passed": all_cleanups_passed(control),
        },
        "target_requalification": {
            "valid_report": target_valid,
            **target_summary,
            "all_cleanups_passed": all_cleanups_passed(target),
        },
        "prospective_quota_allowed": decision in {"CASE_D_RECONFIRMED", "CASE_CONTROL_BASELINE_ESTABLISHED", "CASE_A_LATE_FINGERPRINT_CONFIRMED",
                                                  "COMPATIBLE_CASE_A_MATCHED_INFRASTRUCTURE", "COMPATIBLE_CASE_D_RECONFIRMED"},
        "authorized_fingerprints": sorted(authorized),
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("quota_report")
    parser.add_argument("control_report")
    parser.add_argument("target_report")
    parser.add_argument("--comparison-profile", choices=("absent_client", "compatible_client"), default="absent_client")
    parser.add_argument("--out", default="build/reports/handshake-infrastructure-adjudication.json")
    args = parser.parse_args()
    quota_path = pathlib.Path(args.quota_report)
    quota_report = json.loads(quota_path.read_text(encoding="utf-8")) if quota_path.exists() else {}
    reports = [quota_report] + [json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
                                for path in (args.control_report, args.target_report)]
    result = adjudicate(*reports, args.comparison_profile)
    output = pathlib.Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 1 if result["decision"] == "CASE_INVALID_EVIDENCE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
