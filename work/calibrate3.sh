#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
enc() {
  local in=$1 start=$2 secs=$3 name=$4 w=$5 h=$6 fps=$7 colors=$8
  ffmpeg -y -v error -ss "$start" -t "$secs" -i "$in" \
    -vf "fps=$fps,scale=$w:$h:force_original_aspect_ratio=increase,crop=$w:$h,split[a][b];[a]palettegen=max_colors=$colors:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
    -loop 0 "work/gifs/$name.gif"
  printf '%-26s -> %s\n' "$name" "$(du -h "work/gifs/$name.gif" | cut -f1)"
}
enc "/Users/robingrodziski/Downloads/12442569_1920_1080_30fps.mp4" 0 6 w1_720_8_64 720 405 8 64
enc "/Users/robingrodziski/Downloads/12442569_1920_1080_30fps.mp4" 0 6 w1_720_10_64 720 405 10 64
enc "/Users/robingrodziski/Downloads/12442569_1920_1080_30fps.mp4" 0 6 w1_960_8_64 960 540 8 64
enc "/Users/robingrodziski/Downloads/17026195_1080_1920_30fps.mp4" 10 6 w2_960_8_64 960 540 8 64
