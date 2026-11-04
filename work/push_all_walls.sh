#!/usr/bin/env bash
# Push work/wall-push/*.jpg to the TV's mine folder at native size.
set -u
cd "$(dirname "$0")/.." || exit 1
IP="${TV_IP:-192.168.1.76}"
DEST="/sdcard/Android/data/com.gathin.tvhome/files/mine"
adb() { work/adb-client/bin/python work/tv_adb.py "$@"; }

adb "$IP" -- "mkdir -p $DEST"
ok=0
SRC="${1:-work/wall-push}"
for f in "$SRC"/*.jpg; do
  if adb --push "$f" "$DEST/$(basename "$f")" "$IP" | grep -q "MD5 verified"; then
    ok=$((ok + 1))
    echo "[$ok] $(basename "$f")"
  else
    echo "FAILED $(basename "$f")"
  fi
done
echo "pushed $ok files"
adb "$IP" -- "ls $DEST | wc -l"
