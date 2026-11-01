#!/usr/bin/env python3
"""Copies what the iPhone app shares with Android: strings, fonts, images, map style.

Android stays the source of truth for wording. Run after strings change there:

    python3 src/ios/scripts/sync_android_resources.py

Result: ParkIt/Resources/{he,en}.lproj/Localizable.strings, with Android's
formats converted (%1$s -> %1$@, %d -> %ld). Plurals become key#one/two/other,
string arrays become key#0, key#1...
"""
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ANDROID = ROOT / "src/android/app/src/main"
OUT = ROOT / "src/ios/ParkIt/Resources"


def clean(text: str) -> str:
    text = text.strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    text = re.sub(r"\\([\'\"@?])", r"\1", text)
    text = text.replace("\\n", "\n")
    # Android formats to Cocoa formats.
    text = re.sub(r"%(\d+\$)?s", lambda m: f"%{m.group(1) or ''}@", text)
    text = re.sub(r"%(\d+\$)?d", lambda m: f"%{m.group(1) or ''}ld", text)
    return text


def quote(text: str) -> str:
    return text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def full(el: ET.Element) -> str:
    return "".join(el.itertext())


def convert(src: Path, dst: Path) -> None:
    lines = []
    for el in ET.parse(src).getroot():
        name = el.get("name")
        if el.tag == "string":
            lines.append((name, clean(full(el))))
        elif el.tag == "plurals":
            for item in el:
                lines.append((f"{name}#{item.get('quantity')}", clean(full(item))))
        elif el.tag == "string-array":
            for i, item in enumerate(el):
                lines.append((f"{name}#{i}", clean(full(item))))
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text("".join(f'"{quote(k)}" = "{quote(v)}";\n' for k, v in lines), encoding="utf-8")
    print(dst.relative_to(ROOT), len(lines))


def main() -> None:
    convert(ANDROID / "res/values/strings.xml", OUT / "en.lproj/Localizable.strings")
    convert(ANDROID / "res/values-he/strings.xml", OUT / "he.lproj/Localizable.strings")

    fonts = OUT / "Fonts"
    fonts.mkdir(parents=True, exist_ok=True)
    # heebo_regular.ttf is the variable font with every weight; the other cuts share its
    # PostScript name, and iOS would register only one of them.
    for f in [ANDROID / "res/font/heebo_regular.ttf", ANDROID / "res/font/suez_one.ttf"]:
        shutil.copy(f, fonts / f.name)

    images = OUT / "Images"
    images.mkdir(parents=True, exist_ok=True)
    for f in (ANDROID / "res/drawable-nodpi").iterdir():
        if f.name.startswith("avatar_"):
            shutil.copy(f, images / f.name)
        elif f.name.startswith("intro_"):
            shutil.copy(f, images / f"{f.stem}_en{f.suffix}")
    for f in (ANDROID / "res/drawable-he-nodpi").iterdir():
        shutil.copy(f, images / f"{f.stem}_he{f.suffix}")

    shutil.copy(ANDROID / "assets/map_style.json", OUT / "map_style.json")


if __name__ == "__main__":
    main()
