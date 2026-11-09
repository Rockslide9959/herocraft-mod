#!/bin/sh
# v0.17.3 flight feel: encode the harness frames (run/screenshots/ff_*) into before/after MP4s and copy the contact sheets.
set -e
FF="/c/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe"
ROOT="$(pwd)"
D="$ROOT/docs/screenshots/v0173/flight_feel"
mkdir -p "$D"
cd "$ROOT/run/screenshots"
for k in maxsteel; do
  "$FF" -y -loglevel error -framerate 10 -i "ff_${k}_before_%03d.png" -framerate 10 -i "ff_${k}_after_%03d.png" \
    -filter_complex "[0:v][1:v]hstack=inputs=2,scale=1280:-2" -c:v h264_mf -b:v 6M -pix_fmt yuv420p "$D/${k}_before_vs_after.mp4"
  cp "${k}_before.png" "$D/${k}_before_sheet.png"
  cp "${k}_after.png" "$D/${k}_after_sheet.png"
done
ls -la "$D"
