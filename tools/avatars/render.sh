#!/bin/sh
# Renders tools/avatars/out/*.svg to 256px PNGs in the Android app (needs Google Chrome).
set -e
cd "$(dirname "$0")"
python3 legends.py
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DEST=../../src/android/app/src/main/res/drawable-nodpi
for svg in out/*.svg; do
  name=$(basename "$svg" .svg)
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --window-size=256,256 --screenshot="$DEST/$name.png" "file://$PWD/$svg" >/dev/null 2>&1
  echo "$name.png"
done
