#!/usr/bin/env python3
"""Fails if the app asks for a string that neither language has. Runs in CI."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "ParkIt"
KEY = re.compile(r'^"((?:[^"\\]|\\.)*)" = ', re.M)
USE = re.compile(r'\bLP?\("([a-z0-9_]+)"')
# Keys passed around as plain strings before reaching L().
INDIRECT = re.compile(r'"((?:error|report_size|report_status|lot|intro|tip|rewards_tab)_[a-z0-9_]+)"')


def keys(lang: str) -> set[str]:
    text = (ROOT / f"Resources/{lang}.lproj/Localizable.strings").read_text(encoding="utf-8")
    return set(KEY.findall(text))


he, en = keys("he"), keys("en")
missing = set()
for f in ROOT.rglob("*.swift"):
    text = f.read_text(encoding="utf-8")
    for key in USE.findall(text) + INDIRECT.findall(text):
        for lang, table in (("he", he), ("en", en)):
            if key not in table and not any(k.startswith(f"{key}#") for k in table):
                missing.add(f"{f.name}: {key} ({lang})")
for name in ("newbie", "bronze", "silver", "gold", "legend"):
    for prefix in ("level", "perk"):
        if f"{prefix}_{name}" not in he or f"{prefix}_{name}" not in en:
            missing.add(f"Levels.swift: {prefix}_{name}")
for size in ("compact", "regular", "large"):
    if f"report_size_{size}" not in he or f"report_size_{size}" not in en:
        missing.add(f"ReportAndSign.swift: report_size_{size}")
if missing:
    print("\n".join(sorted(missing)))
    sys.exit(1)
print("all strings present")
