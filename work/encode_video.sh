#!/usr/bin/env bash
# Encode the OLED loops as muted, hardware-decodable 1080p H.264 wallpaper clips.
set -eu
cd "$(dirname "$0")/.."
D="/Users/robingrodziski/Downloads"
mkdir -p work/videos
W=1920; H=1080; SECS=8

enc() {
  local in=$1 start=$2 out=$3
  ffmpeg -y -v error -ss "$start" -t "$SECS" -i "$D/$in" \
    -vf "scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,fps=24" \
    -an -c:v libx264 -preset slow -crf 26 -pix_fmt yuv420p -profile:v high -level 4.0 \
    -g 48 -movflags +faststart "$out"
  printf '%-12s %-36s %s\n' "$(basename "$out")" "$in" "$(du -h "$out" | cut -f1)"
}

enc "12184333_1920_1080_24fps.mp4"            40 "work/videos/wp01.mp4"
enc "12812283_3840_2160_30fps.mp4"             8 "work/videos/wp02.mp4"
enc "12442569_1920_1080_30fps.mp4"             0 "work/videos/wp03.mp4"
enc "10557668-hd_3840_2160_30fps.mp4"          8 "work/videos/wp04.mp4"
enc "4112431-uhd_3840_2160_25fps.mp4"         20 "work/videos/wp05.mp4"
enc "17026195_1080_1920_30fps.mp4"            12 "work/videos/wp06.mp4"
enc "6041715-uhd_3840_2160_24fps.mp4"         30 "work/videos/wp07.mp4"
enc "13207934_1920_1080_24fps.mp4"             1 "work/videos/wp08.mp4"

du -ch work/videos/wp0*.mp4 | tail -1
