#!/usr/bin/env bash
# Push every encoded wallpaper clip to the TV's app storage, replacing what is there.
set -eu
cd "$(dirname "$0")/.."
PY="work/adb-client/bin/python"
ADB="work/tv_adb.py"
DEST="/sdcard/Android/data/com.gathin.tvhome/files/wallpapers"

$PY $ADB 192.168.1.6 -- "mkdir -p $DEST" >/dev/null
for f in work/videos/wp*.mp4; do
  $PY $ADB --push "$f" "$DEST/$(basename "$f")" 192.168.1.6
done
$PY $ADB 192.168.1.6 -- "ls -l $DEST"
