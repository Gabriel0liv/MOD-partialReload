"""Dedicated Forge acceptance for the atomic loot-data + GLM safe point."""
from __future__ import annotations
import importlib.util, json, pathlib, sys, time

ROOT=pathlib.Path(__file__).resolve().parents[1]
GLM=ROOT/"scripts/run-dedicated-glm-commit-acceptance.py"
spec=importlib.util.spec_from_file_location("glm_acceptance_base",GLM); glm=importlib.util.module_from_spec(spec); assert spec.loader
sys.modules[spec.name]=glm; spec.loader.exec_module(glm)
Acceptance,require=glm.Acceptance,glm.require
connect_functional_client,item_count=glm.connect_functional_client,glm.item_count
REPORT=ROOT/"build/reports/dedicated-loot-glm-joint-acceptance.json"

def install(a,generation:str)->None:
    pack=a.run_root/a.server_directory_name/"world/datapacks/partialreload_loot_glm_joint"
    item={"A":"stone","B":"diamond","C":"emerald"}[generation]
    extra={"A":"iron_ingot","B":"gold_ingot","C":"copper_ingot"}[generation]
    files={
      "pack.mcmeta":{"pack":{"pack_format":15,"description":"Partial Reload joint acceptance"}},
      "data/partialreload_joint/loot_tables/base.json":{"type":"minecraft:chest","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:"+item}]}]},
      "data/forge/loot_modifiers/global_loot_modifiers.json":{"replace":True,"entries":["partialreload_joint:add"]},
      "data/partialreload_joint/loot_modifiers/add.json":{"type":"partialreload:acceptance_add_item","conditions":[{"condition":"forge:loot_table_id","loot_table_id":"partialreload_joint:base"}],"item":"minecraft:"+extra},
    }
    for rel,value in files.items(): p=pack/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value)+"\n",encoding="utf-8")

def probe(rcon,player):
    rcon.command(f"clear {player}");rcon.command(f"loot give {player} loot partialreload_joint:base")
    return {x:item_count(rcon,player,"minecraft:"+x) for x in ("stone","diamond","emerald","iron_ingot","gold_ingot","copper_ingot")}

def run_once()->int:
    a=Acceptance(initial_connect_mode="CONTROL",client_mod_mode="without_mod",require_attempt_cleanup=True,server_mod_mode="with_mod")
    result={"status":"failed","complete_run":False,"run_id":a.run_id};client=None;stage="bootstrap"
    try:
      stage="server_start";a.acquire_lock();a.prepare_server();install(a,"A");a.start_server();rcon=a.rcon
      if rcon is None or a.server is None: raise AssertionError("server or RCON unavailable")
      stage="client_login";client,aborts=connect_functional_client(a,"PRJoint")
      stage="generation_a_probe";before=probe(rcon,"PRJoint")
      if not (before["stone"]==1 and before["iron_ingot"]==1): raise AssertionError(f"generation A mismatch: {before}")
      stage="joint_commit"
      rcon.command("partialreload scan");a.wait_rcon_status(r"State:\s*IDLE.*Last scan:\s*(?!never)",120)
      install(a,"B");time.sleep(1.1);rcon.command("partialreload scan");a.wait_rcon_status(r"State:\s*IDLE.*Changed resources:\s*[1-9]",120)
      require(r"started",rcon.command("partialreload prepare loot_glm"),"joint prepare failed");a.wait_rcon_status(r"State:\s*READY",240)
      cursor=a.server.cursor();require(r"queued",rcon.command("partialreload apply prepared"),"joint commit not queued");a.wait_rcon_status(r"State:\s*SUCCESS",120);a.server.wait_marker("LOOT_GLM_JOINT_COMMIT_SUCCESS",60,cursor)
      stage="generation_b_probe";after=probe(rcon,"PRJoint")
      if not (after["diamond"]==1 and after["gold_ingot"]==1 and sum(after.values())==2): raise AssertionError(f"generation B mismatch: {after}")
      stage="fault_rollback"
      install(a,"C");time.sleep(1.1);rcon.command("partialreload scan");a.wait_rcon_status(r"State:\s*IDLE.*Changed resources:\s*[1-9]",120)
      rcon.command("partialreload prepare loot_glm");a.wait_rcon_status(r"State:\s*READY",240)
      fault_response=rcon.command("partialreload debug joint_glm_fault_after_loot")
      require(r"GLM fault armed:\s*AFTER_LOOT_BEFORE_GLM",fault_response,"joint fault was not armed")
      apply_response=rcon.command("partialreload apply prepared")
      require(r"queued",apply_response,"faulted joint commit was not queued")
      a.wait_rcon_status(r"State:\s*ROLLED_BACK",120)
      rolled=probe(rcon,"PRJoint")
      if not (rolled["diamond"]==1 and rolled["gold_ingot"]==1 and rolled["emerald"]==0 and rolled["copper_ingot"]==0): raise AssertionError(f"joint rollback mismatch: {rolled}")
      stage="client_cleanup";cleanup=a.cleanup_attempt(client,client.name,"PRJoint",True,True)
      if cleanup.get("status")!="passed": raise AssertionError(f"client cleanup failed: {cleanup}")
      result.update(status="passed",complete_run=True,generation_a=before,generation_b=after,
                    fault_arm_response=fault_response,fault_apply_response=apply_response,
                    fault_rollback=rolled,no_mixed_generation_observed=True,
                    transient_login_aborts=aborts,cleanup=cleanup)
    except Exception as error: result["error"]={"status":"failed","stage":stage,"message":str(error)}
    finally:
      try:a.cleanup()
      except Exception as error:result["global_cleanup_error"]=str(error)
      a.release_lock();result["global_cleanup"]=a.cleanup_result
      if result.get("status")=="passed" and a.cleanup_result.get("status")!="passed":result.update(status="failed",complete_run=False)
      REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print("LOOT_GLM_JOINT_COMMIT_ACCEPTANCE_PASSED" if result.get("status")=="passed" else "LOOT_GLM_JOINT_COMMIT_ACCEPTANCE_FAILED")
    return 0 if result.get("status")=="passed" else 1

if __name__=="__main__":raise SystemExit(run_once())
