#!/usr/bin/env bash
# Full-length, uncropped wallpaper encodes: fit into 1920x1080 (letterbox only when the source is not 16:9),
# native frame rate, no audio. Replaces the 8-second excerpts.
set -eu
cd "$(dirname "$0")/.."
D="/Users/robingrodziski/Downloads"
mkdir -p work/videos

enc() {
  local in=$1 out=$2
  ffmpeg -y -v error -i "$D/$in" \
    -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black" \
    -an -c:v libx264 -preset medium -crf 26 -pix_fmt yuv420p -profile:v high -level 4.0 \
    -g 48 -movflags +faststart "$out"
  printf '%-12s %-36s %8s  %ss\n' "$(basename "$out")" "$in" \
    "$(du -h "$out" | cut -f1)" \
    "$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$out" | cut -d. -f1)"
}

enc "12184333_1920_1080_24fps.mp4"            "work/videos/wp01.mp4"
enc "12812283_3840_2160_30fps.mp4"            "work/videos/wp02.mp4"
enc "12442569_1920_1080_30fps.mp4"            "work/videos/wp03.mp4"
enc "10557668-hd_3840_2160_30fps.mp4"         "work/videos/wp04.mp4"
enc "4112431-uhd_3840_2160_25fps.mp4"         "work/videos/wp05.mp4"
enc "17026195_1080_1920_30fps.mp4"            "work/videos/wp06.mp4"
enc "6041715-uhd_3840_2160_24fps.mp4"         "work/videos/wp07.mp4"
enc "13207934_1920_1080_24fps.mp4"            "work/videos/wp08.mp4"

du -ch work/videos/wp0*.mp4 | tail -1
