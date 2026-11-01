#!/usr/bin/env python3
"""Prints the UDID of an iPhone simulator this Mac has: the newest iOS, a current model."""
import json
import re
import subprocess
import sys

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
            best = (rank, d)
if best is None:
    sys.exit("no iPhone simulator on this machine")
print(f"{best[1]['name']} {best[0][0]}", file=sys.stderr)
print(best[1]["udid"])
