#!/bin/sh
# Contact sheet: bs_sheet.sh <name> <every> <cols>x<rows> [out]  (frames run/screenshots/bs_<name>_NNN.png; frame numbers drawn on)
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
OUT=${4:-sheet_$1.png}
"$FF" -hide_banner -loglevel error -y -start_number 0 -i "bs_$1_%03d.png" -vf "select='not(mod(n\,$2))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=$3" -frames:v 1 "$OUT"
echo "$OUT"
