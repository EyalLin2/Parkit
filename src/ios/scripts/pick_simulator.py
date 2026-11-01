#!/usr/bin/env python3
"""Prints the UDID of an iPhone simulator on this Mac: the newest iOS, a current model.

With "small": an iPhone SE, the smallest screen iOS 17+ runs on, created if needed. If
this Xcode can't make one, the smallest iPhone it already has.
"""
import json
import re
import subprocess
import sys

small = len(sys.argv) > 1 and sys.argv[1] == "small"
data = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"]))
best = None
for runtime, devices in data["devices"].items():
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    if not m:
        continue
    version = (int(m[1]), int(m[2]))
    for d in devices:
        n = re.fullmatch(r"iPhone (\d+)( Pro)?", d["name"])
        if not n:
            continue
        rank = (version, int(n[1]), bool(n[2]))
        if best is None or rank > best[0]:
            best = (rank, d, runtime)
if best is None:
    sys.exit("no iPhone simulator on this machine")

if small:
    runtime = best[2]
    for kind in ["iPhone-SE-3rd-generation", "iPhone-16e", "iPhone-17e"]:
        made = subprocess.run(
            ["xcrun", "simctl", "create", "ParkIt small", f"com.apple.CoreSimulator.SimDeviceType.{kind}", runtime],
            capture_output=True, text=True,
        )
        if made.returncode == 0:
            print(f"{kind} {best[0][0]}", file=sys.stderr)
            print(made.stdout.strip())
            sys.exit(0)
    print("no small device type available, using the regular one", file=sys.stderr)

print(f"{best[1]['name']} {best[0][0]}", file=sys.stderr)
print(best[1]["udid"])
