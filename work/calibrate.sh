#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
IN="/Users/robingrodziski/Downloads/12184333_1920_1080_24fps.mp4"
run() {
  local w=$1 h=$2 fps=$3 colors=$4 secs=$5 name=$6
  ffmpeg -y -v error -ss 30 -t "$secs" -i "$IN" \
    -vf "fps=$fps,scale=$w:$h:force_original_aspect_ratio=increase,crop=$w:$h,split[a][b];[a]palettegen=max_colors=$colors:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
    -loop 0 "work/gifs/$name.gif"
  printf '%-28s %s x %s @%sfps %scolors %ss -> %s\n' "$name" "$w" "$h" "$fps" "$colors" "$secs" "$(du -h "work/gifs/$name.gif" | cut -f1)"
}
run 960 540 10 96 5 c960_10_96
run 800 450 10 96 5 c800_10_96
run 720 405 10 64 5 c720_10_64
run 640 360 12 64 5 c640_12_64
