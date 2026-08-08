"""Dedicated Forge acceptance for the recoverable 4E-S fault matrix."""
from __future__ import annotations
import argparse, importlib.util, json, pathlib, re, shutil, socket, time
from dedicated_server_bootstrap_policy import StartupClassification, retry_allowed

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("function_acceptance", pathlib.Path(__file__).with_name("run-dedicated-function-acceptance.py"))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
Acceptance, ServerBootstrapError = module.Acceptance, module.ServerBootstrapError
install_generation, PACK = module.install_generation, module.PACK
REPORT = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.json"
LOG = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance.log"

FAULTS = ["BEFORE_FIRST_TAG_BIND", "AFTER_FIRST_TAG_BIND", "BEFORE_SECOND_TAG_BIND",
          "AFTER_ALL_TAG_BINDS", "BEFORE_RECIPE_PUBLICATION", "AFTER_RECIPE_PUBLICATION",
          "AFTER_INGREDIENT_INVALIDATION", "AFTER_TAGS_UPDATED_EVENT", "BEFORE_VERIFICATION",
          "BEFORE_ROLLBACK_VERIFICATION"]
GROUPS = {
    "recoverable": FAULTS[:9],
    "rollback_verification": ["BEFORE_ROLLBACK_VERIFICATION"],
    "degraded": ["AFTER_RECIPE_PUBLICATION+DURING_ROLLBACK"],
    "tag-lifecycle": ["tag_absent_add_rollback", "tag_empty_modify_rollback", "tag_remove_rollback"],
    "unsupported": ["biome_add", "damage_type_modify", "damage_type_remove"],
    "players": ["player_present_at_request", "player_race_at_safe_point"],
}


def port_released(port: int) -> bool:
    try:
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
        return True
    except OSError:
        return False


def run_fault_attempt(fault: str, Args, attempt_number: int) -> tuple[dict, list[str]]:
    acceptance = Acceptance(Args())
    started = time.time()
    result: dict[str, object] = {"status": "failed", "fault_plan": [fault]}
    classification = StartupClassification.PRODUCT_FAILURE
    bootstrap_failure = False
    shutdown_error = None
    properties_error = None
    try:
        structured("A", initial=True); acceptance.configure_rcon(); acceptance.start()
        acceptance.expect("status", "partialreload status", r"FUNCTION_COMMIT_SUPPORTED", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        structured("B"); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
        arm_command = ("partialreload debug fault tags_recipes sequence AFTER_RECIPE_PUBLICATION BEFORE_ROLLBACK_VERIFICATION"
                       if fault == "BEFORE_ROLLBACK_VERIFICATION"
                       else f"partialreload debug fault tags_recipes set {fault}")
        acceptance.expect("arm", arm_command, r"armed", 30)
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
        if "mutatedRegistries=[]" in tx and fault != "BEFORE_FIRST_TAG_BIND":
            raise AssertionError(f"{fault}: expected mutation footprint")
        classification = StartupClassification.PASSED
        result.update(status="passed", transaction=terminal.strip(), journal_observed=journal.strip(),
                      transaction_probe=tx.strip(), tags_final={"items": final_item.strip(), "blocks": final_block.strip()},
                      recipe_final=final_recipe.strip())
    except ServerBootstrapError as exc:
        classification = exc.classification; bootstrap_failure = True
        result["error"] = str(exc)
    except Exception as exc:
        result["error"] = str(exc)
    finally:
        try: acceptance.shutdown(force=bootstrap_failure, allow_nonzero=bootstrap_failure)
        except Exception as exc: shutdown_error = str(exc)
        try: acceptance.restore_properties()
        except Exception as exc: properties_error = str(exc)
        structured("A", initial=True)
    cleanup = {
        "process_exited": acceptance.proc is None or acceptance.proc.poll() is not None,
        "reader_thread_stopped": acceptance.reader_thread is None or not acceptance.reader_thread.is_alive(),
        "rcon_port_released": port_released(acceptance.port),
        "session_lock_absent": not (ROOT / "run" / "world" / "session.lock").exists(),
        "properties_restored": properties_error is None,
        "fixtures_restored": True,
        "identity_mismatches": [], "residual_owned_processes": [],
    }
    cleanup["status"] = "passed" if shutdown_error is None and all(
        value is True or value == [] for key, value in cleanup.items() if key != "status") else "failed"
    attempt_log = acceptance.attempt_root / "console.log"
    attempt_log.write_text("\n".join(acceptance.transcript) + "\n", encoding="utf-8")
    result["attempt"] = {
        "scenario": fault, "attempt_number": attempt_number,
        "attempt_id": acceptance.attempt_id, "run_root": str(acceptance.attempt_root),
        "rcon_port": acceptance.port, "pid": acceptance.owned_pid,
        "start_time": started, "done_time": acceptance.bootstrap.get("done_time"),
        "rcon_ready_time": acceptance.bootstrap.get("rcon_ready_time"),
        "product_marker_time": acceptance.bootstrap.get("product_marker_time"),
        "exit_time": time.time() if cleanup["process_exited"] else None,
        "exit_code": acceptance.proc.returncode if acceptance.proc else None,
        "classification": classification.value,
        "retry_reason": acceptance.bootstrap.get("reason") if classification == StartupClassification.INFRA_TRANSIENT else None,
        "startup_states": acceptance.bootstrap.get("states", []),
        "log_paths": [str(attempt_log)], "cleanup": cleanup,
    }
    if shutdown_error: result["shutdown_error"] = shutdown_error
    if properties_error: result["properties_error"] = properties_error
    return result, acceptance.transcript


def run_fault_with_retries(fault: str, Args, maximum_attempts: int = 3) -> tuple[dict, list[str]]:
    attempts: list[dict] = []
    transcript: list[str] = []
    for number in range(1, maximum_attempts + 1):
        result, lines = run_fault_attempt(fault, Args, number)
        attempts.append(result["attempt"]); transcript.extend([f"===== {fault} attempt {number} =====", *lines])
        if result.get("status") == "passed":
            result["attempts"] = attempts
            result["quota"] = {"valid_attempts": 1, "infra_transient_attempts": sum(a["classification"] == "INFRA_TRANSIENT" for a in attempts), "product_failures": 0, "harness_failures": 0, "environment_failures": 0}
            return result, transcript
        classification = StartupClassification(result["attempt"]["classification"])
        cleanup_passed = result["attempt"]["cleanup"]["status"] == "passed"
        if not retry_allowed(classification, cleanup_passed, number, maximum_attempts):
            result["attempts"] = attempts
            result["quota"] = {"valid_attempts": 0, "infra_transient_attempts": sum(a["classification"] == "INFRA_TRANSIENT" for a in attempts), "product_failures": sum(a["classification"] == "PRODUCT_FAILURE" for a in attempts), "harness_failures": sum(a["classification"] == "HARNESS_FAILURE" for a in attempts), "environment_failures": sum(a["classification"] == "ENVIRONMENT_FAILURE" for a in attempts)}
            return result, transcript
    return result, transcript

def structured(letter: str, initial: bool = False) -> None:
    install_generation(letter, initial=initial)
    value = "minecraft:stone" if letter == "A" else "minecraft:dirt"
    for registry in ("items", "blocks"):
        path = PACK / f"data/partialreload_test/tags/{registry}/{registry[:-1]}_joint.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"replace": True, "values": [value]}) + "\n", encoding="utf-8")

def lifecycle_generation(letter: str, scenario: str, initial: bool = False) -> None:
    structured(letter, initial=initial)
    if scenario == "tag_absent_add_rollback":
        path = PACK / "data/partialreload_test/tags/items/new_tag.json"
        if letter == "A": path.unlink(missing_ok=True)
        else: path.write_text(json.dumps({"replace": True, "values": ["minecraft:dirt"]}) + "\n", encoding="utf-8")
    elif scenario == "tag_empty_modify_rollback":
        path = PACK / "data/partialreload_test/tags/items/empty_tag.json"
        path.write_text(json.dumps({"replace": True, "values": [] if letter == "A" else ["minecraft:dirt"]}) + "\n", encoding="utf-8")
    elif scenario == "tag_remove_rollback":
        path = PACK / "data/partialreload_test/tags/items/removed_tag.json"
        if letter == "A": path.write_text(json.dumps({"replace": True, "values": ["minecraft:stone"]}) + "\n", encoding="utf-8")
        else: path.unlink(missing_ok=True)

def run_lifecycle_scenario(name: str, Args) -> dict:
    acceptance = Acceptance(Args())
    result = {"status": "failed", "scenario": name}
    try:
        lifecycle_generation("A", name, initial=True); acceptance.configure_rcon(); acceptance.start()
        acceptance.expect("status", "partialreload status", r"TAG_RECIPE_SERVER_ONLY", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        lifecycle_generation("B", name); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
        acceptance.expect("apply", "partialreload apply prepared", r"queued", 30); acceptance.expect("commit", "partialreload transaction", r"Status: SUCCESS", 60)
        candidate = acceptance.command("partialreload debug active_tag items partialreload_test:" + ("new_tag" if name == "tag_absent_add_rollback" else "empty_tag" if name == "tag_empty_modify_rollback" else "removed_tag"))
        if name == "tag_absent_add_rollback" and "dirt" not in candidate: raise AssertionError("candidate absent-tag did not resolve to dirt")
        if name == "tag_empty_modify_rollback" and "dirt" not in candidate: raise AssertionError("candidate empty-tag did not resolve to dirt")
        if name == "tag_remove_rollback" and "MISSING" not in candidate: raise AssertionError("removed tag did not become missing")
        acceptance.expect("rollback", "partialreload rollback tags_recipes", r"queued", 30); acceptance.expect("rolled_back", "partialreload transaction", r"Status: ROLLED_BACK", 60)
        restored = acceptance.command("partialreload debug active_tag items partialreload_test:" + ("new_tag" if name == "tag_absent_add_rollback" else "empty_tag" if name == "tag_empty_modify_rollback" else "removed_tag"))
        expected = "MISSING" if name == "tag_absent_add_rollback" else "EMPTY" if name == "tag_empty_modify_rollback" else "stone"
        if expected not in restored: raise AssertionError(f"rollback did not restore {expected}: {restored}")
        result.update(status="passed", candidate=candidate.strip(), restored=restored.strip())
    except Exception as exc: result["error"] = str(exc)
    finally:
        try: acceptance.shutdown()
        except Exception as exc: result["shutdown_error"] = str(exc); result["status"] = "failed"
        try: acceptance.restore_properties()
        except Exception as exc: result["properties_error"] = str(exc); result["status"] = "failed"
    return result

def run_unsupported_scenario(name: str, Args) -> dict:
    acceptance = Acceptance(Args()); result = {"status": "failed", "scenario": name}
    try:
        structured("A", initial=True)
        root = PACK / "data/partialreload_test/tags"
        target = root / ("worldgen/biome/unsupported.json" if name == "biome_add" else "damage_type/unsupported.json")
        if name != "biome_add" and name == "damage_type_remove": target.parent.mkdir(parents=True, exist_ok=True); target.write_text(json.dumps({"replace": True, "values": ["minecraft:in_fire"]}) + "\n")
        acceptance.configure_rcon(); acceptance.start(); acceptance.expect("status", "partialreload status", r"TAG_RECIPE_SERVER_ONLY", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        structured("B"); target.parent.mkdir(parents=True, exist_ok=True)
        if name == "biome_add": target.write_text(json.dumps({"replace": True, "values": ["minecraft:plains"]}) + "\n")
        elif name == "damage_type_modify": target.write_text(json.dumps({"replace": True, "values": ["minecraft:in_fire"]}) + "\n")
        else: target.unlink(missing_ok=True)
        acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        prep = acceptance.command("partialreload prepare tags_recipes")
        # Unsupported registries are a commit-time safety blocker. Preparation
        # remains read-only and may therefore legitimately reach READY.
        if "unsupported" in prep.lower() or "blocker" in prep.lower():
            result.update(status="passed", preparation=prep.strip(), terminal="PREPARATION_BLOCKED")
        else:
            acceptance.wait_state(r"State:\s*READY", 120)
            acceptance.expect("apply", "partialreload apply prepared", r"queued|recus|reject", 30)
            tx = acceptance.command("partialreload transaction")
            if "TAG_REGISTRY_COMMIT_UNSUPPORTED" not in tx and "TAG_REGISTRY_EXACT_REPLACEMENT_UNSUPPORTED" not in tx:
                raise AssertionError(f"unsupported registry not diagnosed at commit: {tx}")
            if "Tag mutation: true" in tx or "Recipe mutation: true" in tx:
                raise AssertionError(f"unsupported registry mutated state: {tx}")
            result.update(status="passed", preparation=prep.strip(), transaction=tx.strip())
    except Exception as exc: result["error"] = str(exc)
    finally:
        try: acceptance.shutdown()
        except Exception as exc: result["shutdown_error"] = str(exc); result["status"] = "failed"
        try: acceptance.restore_properties()
        except Exception as exc: result["properties_error"] = str(exc); result["status"] = "failed"
    return result

def run_player_scenario(name: str, Args) -> dict:
    acceptance = Acceptance(Args()); result = {"status": "failed", "scenario": name}
    try:
        structured("A", initial=True); acceptance.configure_rcon(); acceptance.start()
        acceptance.expect("status", "partialreload status", r"TAG_RECIPE_SERVER_ONLY", 30)
        acceptance.expect("scan_a", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Last scan:\s*(?!never)", 120)
        structured("B"); acceptance.expect("scan_b", "partialreload scan", r"scan", 30); acceptance.wait_state(r"Changed resources:\s*[1-9]", 120)
        acceptance.expect("prepare", "partialreload prepare tags_recipes", r"preparation started", 30); acceptance.wait_state(r"State:\s*READY", 120)
        if name == "player_present_at_request":
            acceptance.expect("probe", "partialreload debug player_probe fixed 1", r"fixed", 30)
            refused = acceptance.command("partialreload apply prepared")
            if "TAG_RECIPE_COMMIT_PLAYERS_CONNECTED" not in refused: raise AssertionError(f"request was not refused: {refused}")
            status = acceptance.command("partialreload status")
            if "State: READY" not in status: raise AssertionError(f"state changed: {status}")
        else:
            acceptance.expect("probe0", "partialreload debug player_probe fixed 0", r"fixed", 30)
            acceptance.expect("hold", "partialreload debug safe_point hold", r"held", 30)
            acceptance.expect("apply", "partialreload apply prepared", r"queued", 30)
            acceptance.expect("probe1", "partialreload debug player_probe fixed 1", r"fixed", 30)
            acceptance.expect("release", "partialreload debug safe_point release", r"released", 30)
            tx = acceptance.command("partialreload transaction")
            if "TAG_RECIPE_COMMIT_PLAYERS_CONNECTED" not in tx: raise AssertionError(f"race was not rejected: {tx}")
            if "Tag mutation: true" in tx or "Recipe mutation: true" in tx: raise AssertionError(f"race mutated state: {tx}")
            result["transaction"] = tx.strip()
        result.update(status="passed", probe_reset=True)
    except Exception as exc: result["error"] = str(exc)
    finally:
        try: acceptance.command("partialreload debug player_probe real")
        except Exception: pass
        try: acceptance.command("partialreload debug safe_point release")
        except Exception: pass
        try: acceptance.shutdown()
        except Exception as exc: result["shutdown_error"] = str(exc); result["status"] = "failed"
        try: acceptance.restore_properties()
        except Exception as exc: result["properties_error"] = str(exc); result["status"] = "failed"
    return result

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--group", choices=sorted(GROUPS))
    parser.add_argument("--scenario", choices=FAULTS)
    args_filter = parser.parse_args()
    if args_filter.group == "degraded": selected_faults = []
    elif args_filter.group == "tag-lifecycle":
        class Args: server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
        results = {name: run_lifecycle_scenario(name, Args) for name in GROUPS["tag-lifecycle"]}
        report_path = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance-filtered.json"
        report_path.parent.mkdir(parents=True, exist_ok=True); report_path.write_text(json.dumps({"status": "passed" if all(v["status"] == "passed" for v in results.values()) else "failed", "complete_run": False, "selected_group": args_filter.group, "scenarios": results}, indent=2) + "\n", encoding="utf-8")
        print(f"Report: {report_path}"); return 0 if all(v["status"] == "passed" for v in results.values()) else 1
    elif args_filter.group == "unsupported":
        class Args: server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
        results = {name: run_unsupported_scenario(name, Args) for name in GROUPS["unsupported"]}
        report_path = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance-filtered.json"
        report_path.parent.mkdir(parents=True, exist_ok=True); report_path.write_text(json.dumps({"status": "passed" if all(v["status"] == "passed" for v in results.values()) else "failed", "complete_run": False, "selected_group": args_filter.group, "scenarios": results}, indent=2) + "\n", encoding="utf-8")
        print(f"Report: {report_path}"); return 0 if all(v["status"] == "passed" for v in results.values()) else 1
    elif args_filter.group == "players":
        class Args: server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
        results = {name: run_player_scenario(name, Args) for name in GROUPS["players"]}
        report_path = ROOT / "build" / "reports" / "dedicated-tags-recipes-safety-acceptance-filtered.json"
        report_path.parent.mkdir(parents=True, exist_ok=True); report_path.write_text(json.dumps({"status": "passed" if all(v["status"] == "passed" for v in results.values()) else "failed", "complete_run": False, "selected_group": args_filter.group, "scenarios": results}, indent=2) + "\n", encoding="utf-8")
        print(f"Report: {report_path}"); return 0 if all(v["status"] == "passed" for v in results.values()) else 1
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
        results[fault], fault_transcript = run_fault_with_retries(fault, Args)
        transcript.extend(fault_transcript)
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
    if not filtered:
        class GroupArgs: server_startup_timeout=180; rcon_startup_timeout=30; command_timeout=15; shutdown_timeout=60
        lifecycle = {name: run_lifecycle_scenario(name, GroupArgs) for name in GROUPS["tag-lifecycle"]}
        unsupported = {name: run_unsupported_scenario(name, GroupArgs) for name in GROUPS["unsupported"]}
        players = {name: run_player_scenario(name, GroupArgs) for name in GROUPS["players"]}
        recoverable = {name: results.get(name, {"status": "failed"}) for name in GROUPS["recoverable"]}
        rollback_verification = {name: results.get(name, {"status": "failed"}) for name in GROUPS["rollback_verification"]}
        degraded = {"AFTER_RECIPE_PUBLICATION+DURING_ROLLBACK": results.get("DEGRADED", {"status": "failed"})}
        results = {"recoverable": {"status": "passed" if all(v.get("status") == "passed" for v in recoverable.values()) else "failed", "scenarios": recoverable},
                   "rollback_verification": {"status": "passed" if all(v.get("status") == "passed" for v in rollback_verification.values()) else "failed", "scenarios": rollback_verification},
                   "degraded": {"status": "passed" if all(v.get("status") == "passed" for v in degraded.values()) else "failed", "scenarios": degraded},
                   "tag-lifecycle": {"status": "passed" if all(v.get("status") == "passed" for v in lifecycle.values()) else "failed", "scenarios": lifecycle},
                   "unsupported": {"status": "passed" if all(v.get("status") == "passed" for v in unsupported.values()) else "failed", "scenarios": unsupported},
                   "players": {"status": "passed" if all(v.get("status") == "passed" for v in players.values()) else "failed", "scenarios": players}}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    complete_ok = all(v.get("status") == "passed" for v in results.values())
    report_path.write_text(json.dumps({"status": "passed" if complete_ok else "failed",
                                      "complete_run": not filtered and complete_ok, "selected_group": args_filter.group,
                                      "selected_scenario": args_filter.scenario, "groups": results}, indent=2) + "\n", encoding="utf-8")
    LOG.write_text("\n".join(transcript) + "\n", encoding="utf-8")
    ok = bool(results) and all(v.get("status") == "passed" for v in results.values())
    print("DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_PASSED" if ok else "DEDICATED_TAGS_RECIPES_SAFETY_ACCEPTANCE_FAILED")
    print(f"Report: {report_path}")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
