#!/usr/bin/env bash
# Encode the 8 OLED loops into launcher GIF wallpapers.
set -eu
cd "$(dirname "$0")/.."
D="/Users/robingrodziski/Downloads"
W=960; H=540; FPS=12; COLORS=80; SECS=6

enc() {
  local in=$1 start=$2 out=$3
  ffmpeg -y -v error -ss "$start" -t "$SECS" -i "$D/$in" \
    -vf "fps=$FPS,scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,split[a][b];[a]palettegen=max_colors=$COLORS:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
    -loop 0 "$out"
  printf '%-14s %-38s %s\n' "$(basename "$out")" "$in" "$(du -h "$out" | cut -f1)"
}

enc "12184333_1920_1080_24fps.mp4"            40 "work/gifs/wp01.gif"
enc "12812283_3840_2160_30fps.mp4"             8 "work/gifs/wp02.gif"
enc "12442569_1920_1080_30fps.mp4"             0 "work/gifs/wp03.gif"
enc "10557668-hd_3840_2160_30fps.mp4"          8 "work/gifs/wp04.gif"
enc "4112431-uhd_3840_2160_25fps.mp4"         20 "work/gifs/wp05.gif"
enc "17026195_1080_1920_30fps.mp4"            12 "work/gifs/wp06.gif"
enc "6041715-uhd_3840_2160_24fps.mp4"         30 "work/gifs/wp07.gif"
enc "13207934_1920_1080_24fps.mp4"             1 "work/gifs/wp08.gif"

du -ch work/gifs/wp0*.gif | tail -1
