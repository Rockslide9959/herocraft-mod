#!/bin/sh
# Saiyan move-kit films: an MP4 and a frame sheet per move into docs/screenshots/v0173/saiyan_kit/.
# Run from the worktree root after the harness run (frames in run/screenshots/sk_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0173/saiyan_kit"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in sk_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#sk_}
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sk_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	count=$(ls sk_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sk_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
