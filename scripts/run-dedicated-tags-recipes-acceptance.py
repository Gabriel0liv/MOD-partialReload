"""Dedicated PREPARE_ONLY acceptance for the joint tags + recipes candidate."""
from __future__ import annotations
import importlib.util, json, pathlib, re

spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
Acceptance, install_generation, PACK, generation = module.Acceptance, module.install_generation, module.PACK, module.generation
ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "dedicated-tags-recipes-acceptance.json"

def install_joint_generation(letter: str, initial: bool = False) -> None:
    # Keep recipe JSON byte-for-byte identical; only the item tag changes.
    install_generation("A", initial=initial)
    target = PACK / "data/partialreload_test/tags/items/joint.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    item = "minecraft:stone" if letter == "A" else "minecraft:dirt"
    target.write_text(json.dumps({"replace": True, "values": [item]}) + "\n", encoding="utf-8")
    recipe = PACK / "data/partialreload_test/recipes/acceptance.json"
    recipe.write_text(json.dumps({"type": "minecraft:crafting_shapeless", "ingredients": [{"tag": "partialreload_test:joint"}], "result": {"item": "minecraft:torch"}}) + "\n", encoding="utf-8")

def main() -> int:
    class Args:
        server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
    acceptance = Acceptance(Args()); results = {}
    install_joint_generation("A", initial=True); acceptance.configure_rcon(); acceptance.start()
    try:
        acceptance.expect("startup", "partialreload status", r"Mode: FUNCTION_COMMIT_SUPPORTED")
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        before = acceptance.fingerprints()
        install_joint_generation("B")
        acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        prepared_start = acceptance.expect("prepare", "partialreload prepare tags_recipes", r"Joint tag and recipe preparation started", 30)
        acceptance.wait_state(r"State:\s*READY", 120)
        response = acceptance.expect("prepared", "partialreload prepared", r"PreparedTagsAndRecipes", 30)
        if "Technically applicable: true" not in response: raise AssertionError("joint candidate is not applicable: " + response)
        if not re.search(r"revalidated due to tag changes:\s*[1-9]", response, re.I): raise AssertionError("recipe was not revalidated")
        results.update({"shared_snapshot":"passed", "candidate_tag_b_resolved":"passed", "recipe_revalidated_from_tag_change":"passed", "recipe_json_unchanged":"passed"})
        after = acceptance.fingerprints()
        if before != after: raise AssertionError("active managers changed during preparation")
        results.update({"active_tag_a_unchanged":"passed", "active_recipe_a_unchanged":"passed", "registry_bindings_unchanged":"passed", "recipe_manager_unchanged":"passed"})
        refusal = acceptance.expect("apply_rejected", "partialreload apply prepared", r"Commit is not implemented for joint tag and recipe candidates", 30)
        if acceptance.fingerprints() != before: raise AssertionError("apply refusal mutated active managers")
        results.update({"apply_rejected":"passed", "artifact_preserved":"passed"})
        acceptance.expect("discard", "partialreload discard", r"discarded", 30)
        results["shutdown"] = "passed"
    finally:
        try: acceptance.shutdown()
        finally:
            if acceptance.properties_backup.exists(): acceptance.properties_backup.replace(acceptance.server_properties)
            install_joint_generation("A", initial=True)
            REPORT.parent.mkdir(parents=True, exist_ok=True); REPORT.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print("DEDICATED_TAGS_RECIPES_ACCEPTANCE_PASSED")
    return 0

if __name__ == "__main__": raise SystemExit(main())
