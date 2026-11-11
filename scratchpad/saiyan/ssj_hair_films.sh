#!/bin/sh
# SSJ hair preview: turntable + head-turn MP4s, lineup PNG. Run from the worktree root after the harness run
# (frames in run/screenshots/sh_*). Arg 1 (optional): output dir.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="${1:-$PWD/docs/screenshots/v0173/ssj_hair_preview}"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
W=$("$FF" -hide_banner -i sh_still_front.png 2>&1 | grep -o '[0-9]\{3,4\}x[0-9]\{3,4\}' | head -1)
echo "frame size $W"
for n in turntable headturn; do
	[ -f "sh_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sh_${n}_%03d.png" -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT/$n.mp4"
	count=$(ls sh_${n}_[0-9]*.png | wc -l)
	every=$(( count / 16 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sh_${n}_%03d.png" -vf "select='not(mod(n\,$every))',crop=ih:ih,scale=300:300,tile=8x2" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
# lineup: front, 3/4, side, back, close-up (square crops from the middle of each frame)
args=""; filt=""; k=0
for v in front threequarter side back closeup; do
	args="$args -i sh_still_$v.png"
	filt="$filt[$k]crop=ih:ih,scale=480:480,drawtext=fontfile=arial.ttf:text='$v':x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55[v$k];"
	k=$((k + 1))
done
"$FF" -hide_banner -loglevel error -y $args -filter_complex "$filt[v0][v1][v2][v3][v4]hstack=inputs=5" -frames:v 1 "$OUT/lineup.png"
echo lineup
