"""Dedicated PREPARE_ONLY acceptance for recipes; commit is deliberately refused."""
from __future__ import annotations
import json, pathlib, re, shutil, subprocess, sys, time, importlib.util
_spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
_module = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(_module)
Acceptance, install_generation, PACK, generation = _module.Acceptance, _module.install_generation, _module.PACK, _module.generation

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "dedicated-recipe-acceptance.json"

def main() -> int:
    class Args:
        server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
    acceptance = Acceptance(Args())
    results = {}
    install_generation("A", initial=True); acceptance.configure_rcon(); acceptance.start()
    try:
        acceptance.expect("startup", "partialreload status", r"Mode: FUNCTION_COMMIT_SUPPORTED")
        acceptance.expect("scan_a", "partialreload scan", r"scan started|scan", 30)
        acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        before = acceptance.fingerprints()
        install_generation("B")
        acceptance.expect("scan_b", "partialreload scan", r"scan started|scan", 30)
        acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        acceptance.expect("prepare", "partialreload prepare recipes", r"started|prepar", 30)
        acceptance.wait_state(r"State:\s*READY", 120)
        prepared = acceptance.expect("prepared", "partialreload prepared", r"PreparedRecipes|Technically applicable: true", 30)
        if "PreparedRecipes" not in prepared or "Technically applicable: true" not in prepared:
            raise AssertionError("recipe artifact was not applicable")
        results.update({"startup":"passed", "scan":"passed", "prepare":"passed", "prepared":"passed"})
        refusal = acceptance.expect("apply_refused", "partialreload apply prepared", r"Commit is not implemented for recipes", 30)
        if acceptance.fingerprints() != before: raise AssertionError("RecipeManager or lateral manager changed")
        results.update({"apply_refused":"passed", "recipe_manager_unchanged":"passed", "artifact_preserved":"passed"})
        acceptance.expect("discard", "partialreload discard", r"discarded", 30)
    except Exception:
        raise
    finally:
        try: acceptance.shutdown()
        finally:
            if acceptance.properties_backup.exists(): acceptance.properties_backup.replace(acceptance.server_properties)
            install_generation("A", initial=True)
            REPORT.parent.mkdir(parents=True, exist_ok=True); REPORT.write_text(json.dumps(results, indent=2)+"\n", encoding="utf-8")
    print("DEDICATED_RECIPE_ACCEPTANCE_PASSED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
