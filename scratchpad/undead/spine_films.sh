#!/bin/sh
# v0.17.3 Undead rib cage + Spine Whip films: an MP4 and a contact sheet per recording, into docs/screenshots/v0173/undead_vmove/.
# Run from the repo root after the UndeadSpineHarness client run. Frames are sp_<name>_NNN.png in run/screenshots; each scene's
# frame step (1 = real time at 20 fps, 0.5 = half speed, 0.25 = quarter speed) is in the harness.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="docs/screenshots/v0173/undead_vmove"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for n in ribs shot whip_tp whip_back whip_fp whip_group; do
	[ -f "sp_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sp_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" "../../$OUT/$n.mp4"
	count=$(ls sp_${n}_*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sp_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "../../$OUT/${n}_sheet.png"
	echo "$n ($count frames, sheet every $every)"
done
echo done
