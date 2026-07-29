"""Adjudicate 4F-A pre-login infrastructure failures after Case D requalification."""
from __future__ import annotations

import argparse
import json
import pathlib
from collections import Counter


def cold_attempts(report: dict[str, object]) -> list[dict[str, object]]:
    scenarios = report.get("scenarios", {})
    if isinstance(scenarios, dict):
        cold = scenarios.get("cold_login", {})
        if isinstance(cold, dict) and isinstance(cold.get("attempts"), list):
            return list(cold["attempts"])
    return list(report.get("attempts", [])) if isinstance(report.get("attempts"), list) else []


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
    result: set[str] = set()
    for item in cold_attempts(report):
        if item.get("classification") != "INFRASTRUCTURE_FAILURE":
            continue
        if item.get("fingerprint_quality") != "HIGH" or not item.get("fingerprint"):
            continue
        diagnostics = item.get("fingerprint_diagnostics") if isinstance(item.get("fingerprint_diagnostics"), dict) else {}
        if ((item.get("cleanup") or {}).get("status") == "passed"
                and diagnostics.get("partialreload_marker_seen") is False
                and diagnostics.get("channel_rejection_seen") is False
                and diagnostics.get("unknown_custom_packet_seen") is False):
            result.add(str(item.get("fingerprint")))
    return result


def non_high_infrastructure(report: dict[str, object]) -> bool:
    return any(item.get("classification") == "INFRASTRUCTURE_FAILURE"
               and item.get("fingerprint_quality") != "HIGH"
               for item in cold_attempts(report))


def adjudicate(quota: dict[str, object], control: dict[str, object], target: dict[str, object]) -> dict[str, object]:
    quota_attempts = cold_attempts(quota)
    failed_quota = next((item for item in quota_attempts
                         if item.get("classification") == "INFRASTRUCTURE_FAILURE"), {})
    control_summary = summarize(control)
    target_summary = summarize(target)
    control_high = high_fingerprints(control)
    target_high = high_fingerprints(target)
    failed_quota_quality = failed_quota.get("fingerprint_quality") or "INSUFFICIENT"
    quota_high = {str(failed_quota.get("fingerprint"))} if failed_quota_quality == "HIGH" and failed_quota.get("fingerprint") else set()
    authorized: set[str] = set()
    control_isolated = control.get("server_mod_mode") == "without_mod" and control.get("classpath_isolated") is True and control.get("partialreload_loaded") is False
    control_baseline_established = bool(
        control_high
        and control_summary["product"] == 0
        and control_summary["harness"] == 0
        and all_cleanups_passed(control)
        and control_isolated
        and target_summary["product"] == 0
        and target_summary["harness"] == 0
        and all_cleanups_passed(target)
    )
    if control_baseline_established:
        decision = "CASE_CONTROL_BASELINE_ESTABLISHED"
        authorized = control_high
    elif control_summary["valid"] == 10 and control_summary["infrastructure"] == 0 and target_summary["valid"] == 10 and target_summary["infrastructure"] == 0:
        decision = "CASE_D_RECONFIRMED"
    elif control_high & target_high:
        decision = "CASE_A_LATE_FINGERPRINT_CONFIRMED"
        authorized = control_high & target_high
    elif control_high & quota_high:
        decision = "CASE_A_LATE_FINGERPRINT_CONFIRMED"
        authorized = control_high & quota_high
    elif control_summary["valid"] == 10 and control_summary["infrastructure"] == 0 and target_high:
        decision = "CASE_B_PRODUCT_CORRELATED_PRELOGIN_FAILURE"
    elif control_high and target_high and not (control_high & target_high):
        decision = "CASE_C_NON_EQUIVALENT_FAILURES"
    elif non_high_infrastructure(control) or non_high_infrastructure(target) or failed_quota.get("fingerprint_quality") in {"MEDIUM", "INSUFFICIENT", None}:
        decision = "CASE_UNRESOLVED_INSUFFICIENT_DIAGNOSTICS"
    else:
        decision = "CASE_UNRESOLVED_INSUFFICIENT_DIAGNOSTICS"
    historical_explained = bool(failed_quota_quality == "HIGH" and quota_high and (quota_high & (control_high | target_high)))
    return {
        "previous_case": "D",
        "decision": decision,
        "historical_failure": {
            "fingerprint_quality": failed_quota_quality,
            "explained": historical_explained,
            "authorized": False,
        },
        "control_baseline": {
            "established": control_baseline_established,
            **control_summary,
            "all_cleanups_passed": all_cleanups_passed(control),
        },
        "target_requalification": {
            **target_summary,
            "all_cleanups_passed": all_cleanups_passed(target),
        },
        "prospective_quota_allowed": decision in {"CASE_CONTROL_BASELINE_ESTABLISHED", "CASE_A_LATE_FINGERPRINT_CONFIRMED"},
        "authorized_fingerprints": sorted(authorized),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("quota_report")
    parser.add_argument("control_report")
    parser.add_argument("target_report")
    parser.add_argument("--out", default="build/reports/handshake-infrastructure-adjudication.json")
    args = parser.parse_args()
    reports = [json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
               for path in (args.quota_report, args.control_report, args.target_report)]
    result = adjudicate(*reports)
    output = pathlib.Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
