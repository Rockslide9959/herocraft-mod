#!/bin/sh
# TEMP: tiles the pose-tuning stills (bs_pose_<set>_<age>.png, age index $1) into sheet_pose_$1.png
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for i in 0 1 2 3 4 5 6 7; do cp "bs_pose_${i}_$1.png" "p_$i.png"; done
"$FF" -hide_banner -loglevel error -y -start_number 0 -i p_%d.png -vf "scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=20:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=4x2" -frames:v 1 "sheet_pose_$1.png"
echo "sheet_pose_$1.png"
