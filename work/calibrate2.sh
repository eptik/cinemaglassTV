#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
enc() {
  local in=$1 start=$2 secs=$3 name=$4 w=960 h=540 fps=10 colors=80
  ffmpeg -y -v error -ss "$start" -t "$secs" -i "$in" \
    -vf "fps=$fps,scale=$w:$h:force_original_aspect_ratio=increase,crop=$w:$h,split[a][b];[a]palettegen=max_colors=$colors:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
    -loop 0 "work/gifs/$name.gif"
  printf '%-22s %ss -> %s\n' "$name" "$secs" "$(du -h "work/gifs/$name.gif" | cut -f1)"
}
enc "/Users/robingrodziski/Downloads/13207934_1920_1080_24fps.mp4" 1 6 v13207934
enc "/Users/robingrodziski/Downloads/12442569_1920_1080_30fps.mp4" 0 6 v12442569
enc "/Users/robingrodziski/Downloads/17026195_1080_1920_30fps.mp4" 10 6 v17026195
