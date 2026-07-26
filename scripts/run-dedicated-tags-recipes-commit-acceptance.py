"""Dedicated acceptance for the server-only joint tag/recipe commit (Fase 4E)."""
from __future__ import annotations

import importlib.util
import json
import pathlib
import re

_spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
_module = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(_module)
Acceptance = _module.Acceptance
PACK = _module.PACK
install_generation = _module.install_generation
ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "reports" / "dedicated-tags-recipes-commit-acceptance.json"


def install_joint(letter: str, initial: bool = False) -> None:
    install_generation(letter, initial=initial)
    item = "minecraft:stone" if letter == "A" else "minecraft:dirt"
    (PACK / "data/partialreload_test/tags/items/joint.json").parent.mkdir(parents=True, exist_ok=True)
    (PACK / "data/partialreload_test/tags/items/joint.json").write_text(
        json.dumps({"replace": True, "values": [item]}) + "\n", encoding="utf-8"
    )
    # Keep the recipe bytes stable; only candidate tag bindings change.
    (PACK / "data/partialreload_test/recipes/acceptance.json").write_text(
        json.dumps({"type": "minecraft:crafting_shapeless", "ingredients": [{"tag": "partialreload_test:joint"}], "result": {"item": "minecraft:torch", "count": 1 if letter == "A" else 2}}) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    class Args:
        server_startup_timeout = 180
        rcon_startup_timeout = 30
        command_timeout = 15
        shutdown_timeout = 60

    acceptance = Acceptance(Args())
    results: dict[str, object] = {}
    try:
        install_joint("A", initial=True)
        acceptance.configure_rcon()
        acceptance.start()
        acceptance.expect("startup", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30)
        acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        before = acceptance.fingerprints()

        install_joint("B")
        acceptance.expect("scan_b", "partialreload scan", r"scan", 30)
        acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        acceptance.expect("prepare_joint", "partialreload prepare tags_recipes", r"started|preparation", 60)
        acceptance.wait_state(r"State:\s*READY", 120)
        prepared = acceptance.expect("prepared", "partialreload prepared", r"PreparedTagsAndRecipes", 30)
        if not re.search(r"Technically applicable:\s*true", prepared, re.I):
            raise AssertionError("joint artifact is not applicable: " + prepared)
        active_a = acceptance.expect("active_a", "partialreload debug active_tag items partialreload_test:joint", r"members=.*minecraft:stone", 30)
        recipe_a = acceptance.expect("recipe_a", "partialreload debug active_recipe partialreload_test:acceptance", r"result=.*torch.*count=1", 30)

        acceptance.expect("queued", "partialreload apply prepared", r"queued|safe point", 30)
        transaction = acceptance.expect("commit_success", "partialreload transaction", r"Status:\s*SUCCESS", 60)
        if not re.search(r"Tag mutation:\s*true", transaction, re.I) or not re.search(r"Recipe mutation:\s*true", transaction, re.I):
            raise AssertionError("joint transaction did not report both mutations: " + transaction)
        active_b = acceptance.expect("active_b", "partialreload debug active_tag items partialreload_test:joint", r"members=.*minecraft:dirt", 30)
        if "minecraft:stone" in active_b:
            raise AssertionError("candidate tag still contains stone: " + active_b)
        recipe_b = acceptance.expect("recipe_b", "partialreload debug active_recipe partialreload_test:acceptance", r"result=.*torch.*count=2", 30)
        after_commit = acceptance.fingerprints()
        for name in ("LootDataManager", "RecipeManager", "AdvancementManager"):
            if after_commit[name] != before[name]:
                raise AssertionError(f"lateral manager changed: {name}")
        results.update({
            "commit": {"status": "passed", "transaction": transaction.strip()},
            "candidate_tag_b_active": {"status": "passed", "expected": ["minecraft:dirt"], "observed": active_b.strip()},
            "active_tag_a_before_commit": {"status": "passed", "expected": ["minecraft:stone"], "observed": active_a.strip()},
            "recipe_publication_scenario": {"status": "pending-rollback-check", "result_before": recipe_a.strip(), "result_after": recipe_b.strip()},
            "function_manager_identity": {"status": "passed", "observed": after_commit["FunctionManager"]},
            "function_library_unchanged": {"status": "passed", "before": before["FunctionLibrary"], "after": after_commit["FunctionLibrary"]},
            "loot_manager_identity": {"status": "passed", "before": before["LootDataManager"], "after": after_commit["LootDataManager"]},
            "recipe_manager_identity": {"status": "passed", "before": before["RecipeManager"], "after": after_commit["RecipeManager"]},
            "advancement_manager_identity": {"status": "passed", "before": before["AdvancementManager"], "after": after_commit["AdvancementManager"]},
        })

        acceptance.expect("rollback_queued", "partialreload rollback tags_recipes", r"queued|safe point", 30)
        rollback = acceptance.expect("rollback_success", "partialreload transaction", r"Status:\s*ROLLED_BACK", 60)
        restored = acceptance.expect("restored_a", "partialreload debug active_tag items partialreload_test:joint", r"members=.*minecraft:stone", 30)
        if "minecraft:dirt" in restored:
            raise AssertionError("rollback left dirt in active tag: " + restored)
        after_rollback = acceptance.fingerprints()
        recipe_restored = acceptance.expect("recipe_restored", "partialreload debug active_recipe partialreload_test:acceptance", r"result=.*torch.*count=1", 30)
        if after_rollback["LootDataManager"] != before["LootDataManager"] or after_rollback["RecipeManager"] != before["RecipeManager"] or after_rollback["AdvancementManager"] != before["AdvancementManager"]:
            raise AssertionError("lateral manager changed after rollback")
        results.update({
            "rollback": {"status": "passed", "transaction": rollback.strip()},
            "active_tag_a_restored": {"status": "passed", "expected": ["minecraft:stone"], "observed": restored.strip()},
            "recipe_publication_scenario": {"status": "passed", "result_before": recipe_a.strip(), "result_after": recipe_b.strip(), "result_after_rollback": recipe_restored.strip()},
            "function_library_rollback": {"status": "passed", "before": before["FunctionLibrary"], "after": after_rollback["FunctionLibrary"]},
        })
        acceptance.command("partialreload discard")
        return_code = 0
    except Exception as exc:
        results["error"] = {"status": "failed", "message": str(exc)}
        return_code = 1
    finally:
        try:
            acceptance.shutdown()
        except Exception as exc:
            results["shutdown"] = {"status": "failed", "message": str(exc)}
            return_code = 1
        if acceptance.properties_backup.exists():
            acceptance.properties_backup.replace(acceptance.server_properties)
        install_joint("A", initial=True)
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    if return_code == 0:
        print("DEDICATED_TAGS_RECIPES_COMMIT_ACCEPTANCE_PASSED")
    return return_code


if __name__ == "__main__":
    raise SystemExit(main())
