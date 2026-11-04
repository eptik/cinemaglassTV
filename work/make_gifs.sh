#!/usr/bin/env bash
# Encode the OLED loop videos into small, seamless GIF wallpapers for the launcher.
#   WIDTH HEIGHT FPS COLORS SECONDS START
set -eu
OUT="$1"; IN="$2"; START="$3"; SECS="$4"
W=1280; H=720; FPS=12; COLORS=128

ffmpeg -y -v error -ss "$START" -t "$SECS" -i "$IN" \
  -vf "fps=$FPS,scale=${W}:${H}:force_original_aspect_ratio=increase,crop=${W}:${H},split[a][b];[a]palettegen=max_colors=${COLORS}:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
  -loop 0 "$OUT"
