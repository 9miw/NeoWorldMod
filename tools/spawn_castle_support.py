import subprocess
import time
import json
import os

def run_cmd(cmd):
    res = subprocess.run(["python3", "/home/miw/send_console.py", cmd], capture_output=True, text=True)
    time.sleep(0.5)
    return res

print("Adding temporary forceload chunks...")
run_cmd("forceload add 168 70")
run_cmd("forceload add 186 91")
run_cmd("forceload add 126 90")
run_cmd("forceload add 137 102")
time.sleep(1)

print("Spawning 4 Castle NPCs...")
# 1. Mira
run_cmd('execute positioned 168.5 117.0 70.5 run noppes npc "มิรา" create')
run_cmd('noppes npc "มิรา" home 168 117 70')

# 2. Thomas
run_cmd('execute positioned 186.5 117.0 91.5 run noppes npc "โทมัส" create')
run_cmd('noppes npc "โทมัส" home 186 117 91')

# 3. Elen
run_cmd('execute positioned 126.5 117.0 90.5 run noppes npc "เอเลน" create')
run_cmd('noppes npc "เอเลน" home 126 117 90')

# 4. Ruben
run_cmd('execute positioned 137.5 113.0 102.5 run noppes npc "รูเบน" create')
run_cmd('noppes npc "รูเบน" home 137 113 102')

print("Saving game...")
run_cmd("save-all flush")
time.sleep(7)
print("Spawn complete, ready to detect UUIDs.")
