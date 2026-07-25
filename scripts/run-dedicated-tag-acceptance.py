"""Dedicated read-only acceptance for general datapack tags."""
from __future__ import annotations
import importlib.util, json, pathlib

spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
Acceptance, install_generation, PACK = module.Acceptance, module.install_generation, module.PACK
ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "dedicated-tag-acceptance.json"

def add_tag(letter: str) -> None:
    target = PACK / "data/partialreload_test/tags/items/acceptance.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    item = "minecraft:stone" if letter == "A" else "minecraft:dirt"
    target.write_text(json.dumps({"replace": False, "values": [item]}) + "\n", encoding="utf-8")

def main() -> int:
    class Args:
        server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
    results = {}; acceptance = Acceptance(Args())
    install_generation("A", initial=True); add_tag("A"); acceptance.configure_rcon(); acceptance.start()
    try:
        acceptance.expect("startup", "partialreload status", r"Mode: FUNCTION_COMMIT_SUPPORTED", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30)
        acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        before = acceptance.fingerprints()
        add_tag("B")
        acceptance.expect("scan_b", "partialreload scan", r"scan", 30)
        acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        acceptance.expect("prepare", "partialreload prepare tags", r"started|prepar", 30)
        acceptance.wait_state(r"State:\s*READY", 120)
        prepared = acceptance.expect("prepared", "partialreload prepared", r"PreparedTags", 30)
        if "PreparedTags" not in prepared or "Technically applicable: true" not in prepared: raise AssertionError("PreparedTags artifact was not applicable")
        results.update({"startup":"passed", "scan":"passed", "prepare":"passed", "prepared":"passed", "candidate_applicable":"passed"})
        acceptance.expect("apply_refused", "partialreload apply prepared", r"Commit is not implemented for tags", 30)
        if acceptance.fingerprints() != before: raise AssertionError("active managers changed")
        results.update({"apply_refused":"passed", "active_bindings_unchanged":"passed", "artifact_preserved":"passed"})
        acceptance.expect("discard", "partialreload discard", r"discarded", 30)
    finally:
        try: acceptance.shutdown()
        finally:
            if acceptance.properties_backup.exists(): acceptance.properties_backup.replace(acceptance.server_properties)
            install_generation("A", initial=True); add_tag("A")
            REPORT.parent.mkdir(parents=True, exist_ok=True); REPORT.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print("DEDICATED_TAG_ACCEPTANCE_PASSED")
    return 0

if __name__ == "__main__": raise SystemExit(main())
