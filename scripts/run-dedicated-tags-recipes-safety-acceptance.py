"""Dedicated Forge acceptance for the recoverable 4E-S fault matrix."""
from __future__ import annotations
import importlib.util, json, pathlib, re, shutil

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
Acceptance, install_generation, PACK = module.Acceptance, module.install_generation, module.PACK
REPORT = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.json"
LOG = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.log"

FAULTS = ["BEFORE_FIRST_TAG_BIND", "AFTER_FIRST_TAG_BIND", "BEFORE_SECOND_TAG_BIND",
          "AFTER_ALL_TAG_BINDS", "BEFORE_RECIPE_PUBLICATION", "AFTER_RECIPE_PUBLICATION",
          "AFTER_INGREDIENT_INVALIDATION", "AFTER_TAGS_UPDATED_EVENT", "BEFORE_VERIFICATION"]

def structured(letter: str, initial: bool = False) -> None:
    install_generation(letter, initial=initial)
    value = "minecraft:stone" if letter == "A" else "minecraft:dirt"
    for registry in ("items", "blocks"):
        path = PACK / f"data/partialreload_test/tags/{registry}/{registry[:-1]}_joint.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"replace": True, "values": [value]}) + "\n", encoding="utf-8")

def main() -> int:
    class Args:
        server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
    results = {}
    for fault in FAULTS:
        acceptance = Acceptance(Args())
        structured("A", initial=True); acceptance.configure_rcon(); acceptance.start()
        try:
            acceptance.expect("status", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED", 30)
            acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
            structured("B"); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
            acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
            acceptance.expect("arm", f"partialreload debug fault tags_recipes set {fault}", r"fault armed", 30)
            acceptance.expect("apply", "partialreload apply prepared", r"queued", 30)
            terminal = acceptance.expect("terminal", "partialreload transaction", r"Status: (FAILED_SAFE|ROLLED_BACK)", 60)
            expected = "FAILED_SAFE" if fault == "BEFORE_FIRST_TAG_BIND" else "ROLLED_BACK"
            if f"Status: {expected}" not in terminal:
                raise AssertionError(f"{fault}: expected {expected}, observed {terminal}")
            journal = acceptance.expect("journal", "partialreload debug tag_recipe_journal", r"Transaction", 30)
            tx = acceptance.expect("transaction_probe", "partialreload debug tag_recipe_transaction", r"status=", 30)
            results[fault] = {"status": "passed", "fault_plan": [fault], "transaction": terminal.strip(), "journal_observed": journal.strip(), "transaction_probe": tx.strip()}
        except Exception as exc:
            results[fault] = {"status": "failed", "fault_plan": [fault], "error": str(exc)}
        finally:
            try: acceptance.shutdown()
            except Exception as exc: results.setdefault(fault, {})["shutdown_error"] = str(exc)
            structured("A", initial=True)
    # Dedicated isolated DEGRADED scenario: the primary fault is consumed
    # after recipe publication, then rollback itself is faulted.
    acceptance = Acceptance(Args()); structured("A", initial=True); acceptance.configure_rcon(); acceptance.start()
    try:
        acceptance.expect("status", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        structured("B"); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
        acceptance.expect("arm", "partialreload debug fault tags_recipes sequence AFTER_RECIPE_PUBLICATION DURING_ROLLBACK", r"sequence armed", 30)
        acceptance.expect("apply", "partialreload apply prepared", r"queued", 30)
        terminal = acceptance.expect("degraded", "partialreload transaction", r"Status: DEGRADED", 60)
        results["DEGRADED"] = {"status": "passed", "fault_plan": ["AFTER_RECIPE_PUBLICATION", "DURING_ROLLBACK"], "transaction": terminal.strip(), "restart_required": True}
    except Exception as exc:
        results["DEGRADED"] = {"status": "failed", "fault_plan": ["AFTER_RECIPE_PUBLICATION", "DURING_ROLLBACK"], "error": str(exc)}
    finally:
        try: acceptance.shutdown()
        except Exception as exc: results.setdefault("DEGRADED", {})["shutdown_error"] = str(exc)
        structured("A", initial=True)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    LOG.write_text("\n".join(f"{k}: {v.get('status')}" for k, v in results.items()) + "\n", encoding="utf-8")
    ok = bool(results) and all(v.get("status") == "passed" for v in results.values())
    print("DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_PASSED" if ok else "DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_FAILED")
    print(f"Report: {REPORT}")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
