#!/bin/sh
# v0.17.4 Raven animation films: an MP4 and a frame sheet per scene into docs/screenshots/v0174/raven/ (OUT overrides); the
# one-frame HUD scenes are copied as PNG screenshots. Run from the worktree root after the harness run (frames in run/screenshots/ra_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="${OUT:-$PWD/docs/screenshots/v0174/raven}"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in ra_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#ra_}
	count=$(ls ra_${n}_[0-9]*.png | wc -l)
	case "$n" in
	hud*)
		last=$(printf "ra_%s_%03d.png" "$n" $((count - 1)))
		cp "$last" "$OUT/$n.png"
		echo "$n (screenshot)"
		continue
		;;
	esac
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "ra_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "ra_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
