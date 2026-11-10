#!/bin/sh
# Undead ejection moves films: MP4 (20 fps = real time) + a contact sheet per recording into docs/screenshots/v0173/undead_eject/.
# Run from the repo root after the UndeadEjectHarness client run (frames in run/screenshots/ue_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="docs/screenshots/v0173/undead_eject"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for n in flight_tp flight_fp spray_tp spray_wall spray_fp whirl_tp whirl_fp feet_tp combustion_tp combustion_fp deadline_tp deadline_fp; do
	[ -f "ue_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "ue_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "../../$OUT/$n.mp4"
	count=$(ls ue_${n}_*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "ue_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "../../$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
echo done
