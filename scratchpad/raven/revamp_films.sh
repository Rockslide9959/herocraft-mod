#!/bin/sh
# Raven revamp films: an MP4 and a frame sheet per scene into docs/screenshots/v0173/raven_revamp/ (OUT overrides); one-frame
# scenes (the wheel, the HUD) are copied as PNG screenshots instead. Run from the worktree root after the harness run
# (frames in run/screenshots/rr_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="${OUT:-$PWD/docs/screenshots/v0173/raven_revamp}"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in rr_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#rr_}
	count=$(ls rr_${n}_[0-9]*.png | wc -l)
	case "$n" in
	wheel*|hud*)
		last=$(printf "rr_%s_%03d.png" "$n" $((count - 1)))
		cp "$last" "$OUT/$n.png"
		echo "$n (screenshot)"
		continue
		;;
	esac
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "rr_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "rr_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
