#!/bin/sh
# v0.17.3 Saiyan tail v2: encode MiscFixesHarness frames (run/screenshots/mf_*) into MP4s + frame sheets,
# plus side-by-side Saiyan | Spidermonkey MP4s for the clips both have.
set -e
FF="/c/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe"
ROOT="$(pwd)"
D="$ROOT/docs/screenshots/v0173/saiyan_tail_v2"
mkdir -p "$D"
cd "$ROOT/run/screenshots"
for k in tail_idle tail_turn tail_run tail_jumpfall tail_crouch tail_colours sm_idle sm_turn sm_run sm_jumpfall sm_crouch; do
  ls mf_${k}_000.png >/dev/null 2>&1 || continue
  "$FF" -y -loglevel error -framerate 20 -i "mf_${k}_%03d.png" -vf "scale=854:-2" -c:v h264_mf -b:v 5M -pix_fmt yuv420p "$D/${k}.mp4"
  n=$(ls mf_${k}_[0-9]*.png | wc -l)
  step=$(( n / 12 )); [ $step -lt 1 ] && step=1
  files=""; i=0
  while [ $i -lt $n ] && [ $(echo $files | wc -w) -lt 12 ]; do files="$files $(printf 'mf_%s_%03d.png' $k $i)"; i=$((i+step)); done
  node "$ROOT/scratchpad/contact_sheet.js" "$D/${k}_sheet.png" 4 0.5 $files
done
for k in idle turn run jumpfall crouch; do
  [ -f "$D/tail_${k}.mp4" ] && [ -f "$D/sm_${k}.mp4" ] || continue
  "$FF" -y -loglevel error -i "$D/tail_${k}.mp4" -i "$D/sm_${k}.mp4" -filter_complex "[0:v][1:v]hstack=inputs=2,scale=1280:-2" -c:v h264_mf -b:v 6M -pix_fmt yuv420p "$D/side_by_side_${k}.mp4"
done
ls -la "$D"
