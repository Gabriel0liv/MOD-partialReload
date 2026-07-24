import pathlib
import queue
import re
import subprocess
import sys
import threading
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "run" / "dedicated-function-acceptance.log"
events = queue.Queue()

proc = subprocess.Popen(
    ["cmd.exe", "/c", "gradlew.bat", "--no-daemon", "runServer", "--console=plain"],
    cwd=ROOT,
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    bufsize=1,
)

def reader():
    for line in proc.stdout:
        line = line.rstrip("\r\n")
        events.put(line)
        print(line, flush=True)

threading.Thread(target=reader, daemon=True).start()
transcript = []

def wait_for(pattern, timeout=120):
    end = time.time() + timeout
    regex = re.compile(pattern)
    while time.time() < end:
        try:
            line = events.get(timeout=0.2)
        except queue.Empty:
            continue
        transcript.append(line)
        if regex.search(line):
            return line
    raise RuntimeError(f"timeout waiting for {pattern}")

def send(command):
    transcript.append("> " + command)
    print("> " + command, flush=True)
    proc.stdin.write(command + "\n")
    proc.stdin.flush()

try:
    wait_for(r"Done \([0-9.]+s\)! For help", 240)
    send("partialreload status")
    wait_for(r"Mode: FUNCTION_COMMIT_SUPPORTED")
    send("partialreload active functions")
    wait_for(r"Active functions:")
    send("scoreboard objectives add pr_dedicated dummy")
    time.sleep(1)
    send("scoreboard players set result pr_dedicated 1")
    time.sleep(1)
    send("function partialreload:gametest/valid")
    time.sleep(1)
    send("partialreload scan")
    wait_for(r"Scan complete:", 120)
    send("partialreload changed")
    wait_for(r"Changed resources:")
    send("partialreload prepare functions")
    wait_for(r"Function preparation started")
    send("partialreload prepared")
    wait_for(r"Technically applicable: true", 120)
    send("partialreload apply prepared")
    wait_for(r"queued for the next safe point")
    time.sleep(2)
    send("partialreload transaction")
    wait_for(r"Status: SUCCESS", 60)
    wait_for(r"Load policy: DO_NOT_RUN", 30)
    send("partialreload active functions")
    wait_for(r"Load pending: false")
    send("partialreload prepare loot")
    wait_for(r"Joint loot data preparation started", 30)
    time.sleep(3)
    send("partialreload apply prepared")
    wait_for(r"Commit is not implemented for loot data", 60)
    send("partialreload discard")
    wait_for(r"Prepared artifact discarded", 30)
    send("partialreload rollback functions")
    wait_for(r"Rollback transaction .* queued", 30)
    time.sleep(2)
    send("partialreload transaction")
    wait_for(r"Status: ROLLED_BACK", 60)
    send("partialreload scan")
    wait_for(r"Scan complete:", 120)
    send("partialreload changed")
    wait_for(r"Changed resources:")
    send("stop")
    wait_for(r"ThreadedAnvilChunkStorage: All dimensions are saved", 120)
    proc.wait(timeout=120)
    if proc.returncode != 0:
        raise RuntimeError(f"server exit code {proc.returncode}")
    REPORT.write_text("\n".join(transcript), encoding="utf-8")
    print("DEDICATED_FUNCTION_ACCEPTANCE_PASSED")
except Exception as exc:
    REPORT.write_text("\n".join(transcript) + "\nERROR: " + repr(exc), encoding="utf-8")
    try:
        proc.stdin.write("stop\n")
        proc.stdin.flush()
    except Exception:
        pass
    try:
        proc.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proc.kill()
    print(f"DEDICATED_FUNCTION_ACCEPTANCE_FAILED: {exc}", file=sys.stderr)
    sys.exit(1)
