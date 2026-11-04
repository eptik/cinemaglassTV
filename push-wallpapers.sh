#!/usr/bin/env bash
# Add your own wallpapers to the launcher.
#
#   ./push-wallpapers.sh ~/Downloads/my-wallpapers
#
# Any .jpg/.jpeg/.png in the folder is copied at native resolution (no resize), renamed to a
# slug ("The Dark Knight (2008).jpg" -> the-dark-knight-2008.jpg), and pushed to
# the TV's "mine" folder. Those appear first in the rotation and are never
# evicted by the TMDB refresh.
#
# The caption under the clock is the file name, so name files like
# "Blade Runner 2049 2017.jpg" and it reads "BLADE RUNNER 2049 2017".
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TV_IP="${TV_IP:-192.168.1.76}"
DEST="/sdcard/Android/data/com.gathin.tvhome/files/mine"
ADB="$HERE/work/adb-client/bin/python $HERE/work/tv_adb.py"

SRC="${1:-}"
[ -d "$SRC" ] || { echo "usage: $0 <folder-with-images>"; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

shopt -s nullglob nocaseglob
files=("$SRC"/*.jpg "$SRC"/*.jpeg "$SRC"/*.png)
[ ${#files[@]} -gt 0 ] || { echo "no images in $SRC"; exit 1; }

$ADB "$TV_IP" -- "mkdir -p $DEST"

for f in "${files[@]}"; do
  base="$(basename "$f")"
  base="${base%.*}"
  slug="$(printf '%s' "$base" | tr '[:upper:]' '[:lower:]' \
          | LC_ALL=C sed 's/[^a-z0-9][^a-z0-9]*/-/g; s/^-//; s/-$//')"
  [ -n "$slug" ] || slug="wallpaper-$(date +%s)"
  out="$TMP/$slug.jpg"
  # Native resolution on purpose: no resampling, the panel scales it down itself.
  ext="$(printf '%s' "${f##*.}" | tr '[:upper:]' '[:lower:]')"
  if [ "$ext" != "jpg" ] && [ "$ext" != "jpeg" ]; then
    sips -s format jpeg -s formatOptions 95 "$f" --out "$out" >/dev/null
  else
    cp "$f" "$out"
  fi
  echo "-> $slug.jpg  ($(du -h "$out" | cut -f1))"
  $ADB --push "$out" "$DEST/$slug.jpg" "$TV_IP"
done

echo
echo "Pushed ${#files[@]} wallpaper(s). Restart the launcher to pick them up:"
echo "  adb shell am force-stop com.gathin.tvhome && adb shell input keyevent KEYCODE_HOME"
