"""Dedicated Forge acceptance for ordered transactional Global Loot Modifiers."""
from __future__ import annotations
import importlib.util, json, pathlib, re, sys, time

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = ROOT / "scripts" / "run-dedicated-loot-data-commit-acceptance.py"
spec = importlib.util.spec_from_file_location("loot_acceptance_base", BASE)
base = importlib.util.module_from_spec(spec); assert spec.loader
sys.modules[spec.name] = base; spec.loader.exec_module(base)
Acceptance, require = base.Acceptance, base.require
connect_functional_client, item_count = base.connect_functional_client, base.item_count
REPORT = ROOT / "build/reports/dedicated-glm-commit-acceptance.json"

def install(acceptance, generation: str) -> None:
    pack = acceptance.run_root / acceptance.server_directory_name / "world/datapacks/partialreload_glm_commit"
    entries = ["partialreload_glm:first", "partialreload_glm:removed"] if generation == "A" else ["partialreload_glm:new", "partialreload_glm:first"]
    files = {
        "pack.mcmeta": {"pack":{"pack_format":15,"description":"Partial Reload GLM acceptance"}},
        "data/partialreload_glm/loot_tables/base.json": {"type":"minecraft:chest","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:stone"}]}]},
        "data/forge/loot_modifiers/global_loot_modifiers.json": {"replace":True,"entries":entries},
        "data/partialreload_glm/loot_modifiers/first.json": modifier("minecraft:iron_ingot" if generation == "A" else "minecraft:diamond"),
    }
    if generation == "A": files["data/partialreload_glm/loot_modifiers/removed.json"] = modifier("minecraft:gold_ingot")
    else: files["data/partialreload_glm/loot_modifiers/new.json"] = modifier("minecraft:copper_ingot")
    for stale in ("removed.json", "new.json"):
        path = pack / "data/partialreload_glm/loot_modifiers" / stale
        if path.exists() and str(path.relative_to(pack)).replace("\\","/") not in files: path.unlink()
    for rel, value in files.items():
        path=pack/rel; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value)+"\n",encoding="utf-8")

def modifier(item: str) -> dict:
    return {"type":"partialreload:acceptance_add_item","conditions":[{"condition":"forge:loot_table_id","loot_table_id":"partialreload_glm:base"}],"item":item}

def probe(rcon, player: str) -> dict[str,int]:
    rcon.command(f"clear {player}"); rcon.command(f"loot give {player} loot partialreload_glm:base")
    return {item:item_count(rcon,player,"minecraft:"+item) for item in ("stone","iron_ingot","gold_ingot","diamond","copper_ingot")}

def run_once() -> int:
    acceptance=Acceptance(initial_connect_mode="CONTROL",client_mod_mode="without_mod",require_attempt_cleanup=True,server_mod_mode="with_mod")
    result={"status":"failed","complete_run":False,"run_id":acceptance.run_id}; client=None; stage="bootstrap"
    try:
        stage="server_start"
        acceptance.acquire_lock(); acceptance.prepare_server(); install(acceptance,"A"); acceptance.start_server()
        rcon=acceptance.rcon
        if rcon is None or acceptance.server is None: raise AssertionError("server or RCON unavailable")
        stage="client_login"
        client,aborts=connect_functional_client(acceptance,"PRGlm")
        stage="generation_a_probe"
        a=probe(rcon,"PRGlm")
        if not (a["stone"]==1 and a["iron_ingot"]==1 and a["gold_ingot"]==1): raise AssertionError(f"generation A mismatch: {a}")
        stage="generation_b_prepare"
        rcon.command("partialreload scan"); acceptance.wait_rcon_status(r"State:\s*IDLE.*Last scan:\s*(?!never)",120)
        install(acceptance,"B"); time.sleep(1.1); rcon.command("partialreload scan"); acceptance.wait_rcon_status(r"State:\s*IDLE.*Changed resources:\s*[1-9]",120)
        require(r"started",rcon.command("partialreload prepare glm"),"GLM prepare did not start"); acceptance.wait_rcon_status(r"State:\s*READY",180)
        require(r"Applicable:\s*true",rcon.command("partialreload prepared"),"GLM candidate invalid")
        cursor=acceptance.server.cursor(); require(r"queued",rcon.command("partialreload apply prepared"),"GLM commit not queued")
        acceptance.wait_rcon_status(r"State:\s*SUCCESS",120); acceptance.server.wait_marker("GLM_COMMIT_SUCCESS",60,cursor)
        stage="generation_b_probe"
        b=probe(rcon,"PRGlm")
        if not (b["stone"]==1 and b["diamond"]==1 and b["copper_ingot"]==1 and b["gold_ingot"]==0): raise AssertionError(f"generation B mismatch: {b}")
        order=rcon.command("partialreload debug active_glm"); require(r"partialreload_glm:new.*partialreload_glm:first",order,"GLM order B mismatch")
        stage="manual_rollback"
        require(r"queued",rcon.command("partialreload rollback glm"),"GLM rollback not queued"); acceptance.wait_rcon_status(r"State:\s*ROLLED_BACK",120)
        restored=probe(rcon,"PRGlm")
        if not (restored["iron_ingot"]==1 and restored["gold_ingot"]==1 and restored["diamond"]==0): raise AssertionError(f"rollback mismatch: {restored}")
        stage="client_cleanup"
        cleanup=acceptance.cleanup_attempt(client,client.name,"PRGlm",True,True)
        if cleanup.get("status")!="passed": raise AssertionError(f"client cleanup failed: {cleanup}")
        result.update(status="passed",complete_run=True,generation_a=a,generation_b=b,order_b=order,manual_rollback=restored,transient_login_aborts=aborts,cleanup=cleanup)
    except Exception as error: result["error"]={"status":"failed","stage":stage,"message":str(error)}
    finally:
        try: acceptance.cleanup()
        except Exception as error: result["global_cleanup_error"]=str(error)
        acceptance.release_lock(); result["global_cleanup"]=acceptance.cleanup_result
        if result.get("status")=="passed" and acceptance.cleanup_result.get("status")!="passed": result.update(status="failed",complete_run=False)
        REPORT.parent.mkdir(parents=True,exist_ok=True); REPORT.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print("GLM_COMMIT_ACCEPTANCE_PASSED" if result.get("status")=="passed" else "GLM_COMMIT_ACCEPTANCE_FAILED")
    return 0 if result.get("status")=="passed" else 1

if __name__ == "__main__": raise SystemExit(run_once())
