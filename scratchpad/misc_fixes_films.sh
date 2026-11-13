#!/bin/sh
# v0.17.3 misc fixes: encode MiscFixesHarness frames (run/screenshots/mf_*) into MP4s + frame sheets.
set -e
FF="/c/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe"
ROOT="$(pwd)"
D="$ROOT/docs/screenshots/v0173/misc_fixes"
mkdir -p "$D"
cd "$ROOT/run/screenshots"
for k in landing_base landing_ssj tail tail_run ultra; do
  ls mf_${k}_000.png >/dev/null 2>&1 || continue
  "$FF" -y -loglevel error -framerate 20 -i "mf_${k}_%03d.png" -vf "scale=854:-2" -c:v h264_mf -b:v 5M -pix_fmt yuv420p "$D/saiyan_${k}.mp4"
  n=$(ls mf_${k}_[0-9]*.png | wc -l)
  step=$(( n / 12 )); [ $step -lt 1 ] && step=1
  files=""; i=0
  while [ $i -lt $n ] && [ $(echo $files | wc -w) -lt 12 ]; do files="$files $(printf 'mf_%s_%03d.png' $k $i)"; i=$((i+step)); done
  node "$ROOT/scratchpad/contact_sheet.js" "$D/saiyan_${k}_sheet.png" 4 0.5 $files
done
ls -la "$D"
