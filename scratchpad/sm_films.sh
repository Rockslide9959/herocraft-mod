#!/bin/sh
# Saiyan moves films: MP4 + contact sheet per recording into docs/screenshots/v0173/saiyan_moves/.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
cd "$(dirname "$0")/.." || exit 1
OUT="docs/screenshots/v0173/saiyan_moves"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in sm_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#sm_}
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sm_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 "../../$OUT/$n.mp4"
	count=$(ls sm_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sm_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "../../$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
