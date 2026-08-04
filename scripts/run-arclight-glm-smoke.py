"""Optional Arclight 1.20.1 GLM smoke; absence is explicitly non-claiming."""
from __future__ import annotations
import json, os, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
REPORT=ROOT/"build/reports/arclight-glm-smoke.json"
runtime=os.environ.get("PARTIALRELOAD_ARCLIGHT_RUNTIME")
result={"status":"ARCLIGHT_GLM_SMOKE_NOT_EXECUTED","compatibility_claimed":False,"reason":"PARTIALRELOAD_ARCLIGHT_RUNTIME is not configured"}
if runtime and pathlib.Path(runtime).exists():
    result={"status":"ARCLIGHT_GLM_SMOKE_NOT_EXECUTED","compatibility_claimed":False,"reason":"configured runtime requires an explicit disposable-server adapter"}
REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print(result["status"])
