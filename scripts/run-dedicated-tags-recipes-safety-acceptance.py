"""Dedicated Forge acceptance for the recoverable 4E-S fault matrix."""
from __future__ import annotations
import argparse, importlib.util, json, pathlib, re, shutil

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
Acceptance, install_generation, PACK = module.Acceptance, module.install_generation, module.PACK
REPORT = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.json"
LOG = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.log"

FAULTS = ["BEFORE_FIRST_TAG_BIND", "AFTER_FIRST_TAG_BIND", "BEFORE_SECOND_TAG_BIND",
          "AFTER_ALL_TAG_BINDS", "BEFORE_RECIPE_PUBLICATION", "AFTER_RECIPE_PUBLICATION",
          "AFTER_INGREDIENT_INVALIDATION", "AFTER_TAGS_UPDATED_EVENT", "BEFORE_VERIFICATION",
          "BEFORE_ROLLBACK_VERIFICATION"]
GROUPS = {
    "recoverable": FAULTS[:9],
    "rollback_verification": ["BEFORE_ROLLBACK_VERIFICATION"],
    "degraded": [],
    "tag-lifecycle": [],
    "unsupported": [],
}

def structured(letter: str, initial: bool = False) -> None:
    install_generation(letter, initial=initial)
    value = "minecraft:stone" if letter == "A" else "minecraft:dirt"
    for registry in ("items", "blocks"):
        path = PACK / f"data/partialreload_test/tags/{registry}/{registry[:-1]}_joint.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"replace": True, "values": [value]}) + "\n", encoding="utf-8")

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--group", choices=sorted(GROUPS))
    parser.add_argument("--scenario", choices=FAULTS)
    args_filter = parser.parse_args()
    if args_filter.group == "degraded": selected_faults = []
    elif args_filter.group in ("tag-lifecycle", "unsupported"):
        report = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance-filtered.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps({"status": "failed", "complete_run": False, "selected_group": args_filter.group,
                                      "error": "scenario group is not implemented"}, indent=2) + "\n", encoding="utf-8")
        print(f"Filtered group {args_filter.group} is not implemented; report: {report}")
        return 1
    elif args_filter.scenario: selected_faults = [args_filter.scenario]
    elif args_filter.group: selected_faults = GROUPS[args_filter.group]
    else: selected_faults = FAULTS
    filtered = bool(args_filter.group or args_filter.scenario)
    report_path = ROOT / "build" / "reports" / ("dedicated-tags-recipes-safety-acceptance-filtered.json" if filtered else "dedicated-tags-recipes-safety-acceptance.json")
    class Args:
        server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
    results = {}
    transcript: list[str] = []
    for fault in selected_faults:
        acceptance = Acceptance(Args())
        try:
            structured("A", initial=True); acceptance.configure_rcon(); acceptance.start()
            acceptance.expect("status", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED", 30)
            acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
            structured("B"); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
            acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
            acceptance.expect("arm", f"partialreload debug fault tags_recipes set {fault}", r"fault armed", 30)
            acceptance.expect("apply", "partialreload apply prepared", r"queued", 30)
            terminal = acceptance.expect("terminal", "partialreload transaction", r"Status: (FAILED_SAFE|ROLLED_BACK|DEGRADED)", 60)
            expected = "FAILED_SAFE" if fault == "BEFORE_FIRST_TAG_BIND" else ("DEGRADED" if fault == "BEFORE_ROLLBACK_VERIFICATION" else "ROLLED_BACK")
            if f"Status: {expected}" not in terminal:
                raise AssertionError(f"{fault}: expected {expected}, observed {terminal}")
            journal = acceptance.expect("journal", "partialreload debug tag_recipe_journal", r"Transaction", 30)
            tx = acceptance.expect("transaction_probe", "partialreload debug tag_recipe_transaction", r"status=", 30)
            final_item = acceptance.expect("final_item", "partialreload debug active_tag items partialreload_test:item_joint", r"stone", 30)
            final_block = acceptance.expect("final_block", "partialreload debug active_tag blocks partialreload_test:block_joint", r"stone", 30)
            final_recipe = acceptance.expect("final_recipe", "partialreload debug active_recipe partialreload_test:acceptance", r"count=1", 30)
            if "mutatedRegistries=[]" in tx and fault != "BEFORE_FIRST_TAG_BIND": raise AssertionError(f"{fault}: expected mutation footprint")
            results[fault] = {"status": "passed", "fault_plan": [fault], "transaction": terminal.strip(), "journal_observed": journal.strip(), "transaction_probe": tx.strip(), "tags_final": {"items": final_item.strip(), "blocks": final_block.strip()}, "recipe_final": final_recipe.strip()}
        except Exception as exc:
            results[fault] = {"status": "failed", "fault_plan": [fault], "error": str(exc)}
        finally:
            try: acceptance.shutdown()
            except Exception as exc: results.setdefault(fault, {})["shutdown_error"] = str(exc); results.setdefault(fault, {})["status"] = "failed"
            try: acceptance.restore_properties()
            except Exception as exc: results.setdefault(fault, {})["properties_error"] = str(exc); results.setdefault(fault, {})["status"] = "failed"
            transcript.extend([f"===== {fault} =====", *acceptance.transcript]); structured("A", initial=True)
    # Dedicated isolated DEGRADED scenario: the primary fault is consumed
    # after recipe publication, then rollback itself is faulted.
    acceptance = Acceptance(Args()) if args_filter.group in (None, "degraded") and not args_filter.scenario else None
    if acceptance is None:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps({"status": "passed", "complete_run": False, "selected_group": args_filter.group,
                                           "selected_scenario": args_filter.scenario, "scenarios": results}, indent=2) + "\n", encoding="utf-8")
        print(f"Filtered acceptance complete: {report_path}")
        return 0 if all(v.get("status") == "passed" for v in results.values()) else 1
    try:
        structured("A", initial=True); acceptance.configure_rcon(); acceptance.start()
        acceptance.expect("status", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        structured("B"); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
        acceptance.expect("arm", "partialreload debug fault tags_recipes sequence AFTER_RECIPE_PUBLICATION DURING_ROLLBACK", r"sequence armed", 30)
        acceptance.expect("apply", "partialreload apply prepared", r"queued", 30)
        terminal = acceptance.expect("degraded", "partialreload transaction", r"Status: DEGRADED", 60)
        status = acceptance.expect("degraded_status", "partialreload status", r"State:\s*DEGRADED", 15)
        if not re.search(r"Restart required:\s*true", status, re.I):
            raise AssertionError("DEGRADED status did not report restart required")
        apply_reject = acceptance.command("partialreload apply prepared")
        rollback_reject = acceptance.command("partialreload rollback tags_recipes")
        if not re.search(r"DEGRADED|restart is required", apply_reject, re.I):
            raise AssertionError(f"DEGRADED apply was not rejected: {apply_reject!r}")
        if not re.search(r"DEGRADED|restart is required", rollback_reject, re.I):
            raise AssertionError(f"DEGRADED rollback was not rejected: {rollback_reject!r}")
        results["DEGRADED"] = {"status": "passed", "fault_plan": ["AFTER_RECIPE_PUBLICATION", "DURING_ROLLBACK"], "transaction": terminal.strip(), "status_probe": status.strip(), "apply_rejected": apply_reject.strip(), "rollback_rejected": rollback_reject.strip(), "restart_required": True}
    except Exception as exc:
        results["DEGRADED"] = {"status": "failed", "fault_plan": ["AFTER_RECIPE_PUBLICATION", "DURING_ROLLBACK"], "error": str(exc)}
    finally:
        try: acceptance.shutdown()
        except Exception as exc: results.setdefault("DEGRADED", {})["shutdown_error"] = str(exc); results.setdefault("DEGRADED", {})["status"] = "failed"
        try: acceptance.restore_properties()
        except Exception as exc: results.setdefault("DEGRADED", {})["properties_error"] = str(exc); results.setdefault("DEGRADED", {})["status"] = "failed"
        transcript.extend(["===== DEGRADED =====", *acceptance.transcript]); structured("A", initial=True)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps({"status": "passed" if all(v.get("status") == "passed" for v in results.values()) else "failed",
                                      "complete_run": not filtered, "selected_group": args_filter.group,
                                      "selected_scenario": args_filter.scenario, "scenarios": results}, indent=2) + "\n", encoding="utf-8")
    LOG.write_text("\n".join(transcript) + "\n", encoding="utf-8")
    ok = bool(results) and all(v.get("status") == "passed" for v in results.values())
    print("DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_PASSED" if ok else "DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_FAILED")
    print(f"Report: {report_path}")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
