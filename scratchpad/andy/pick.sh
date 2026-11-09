#!/bin/sh
# TEMP: pick.sh <name> <out> <frame>...  tiles the named frames of a recording (4 across)
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
cd run/screenshots || exit 1
name=$1; out=$2; shift 2
rm -f pk_*.png
i=0
for f in "$@"; do cp "$(printf 'bs_%s_%03d.png' "$name" "$f")" "pk_$i.png"; i=$((i+1)); done
cols=4; rows=$(( (i + 3) / 4 ))
"$FF" -hide_banner -loglevel error -y -start_number 0 -i pk_%d.png -vf "scale=427:240,tile=${cols}x${rows}" -frames:v 1 "$out"
echo "$out"
