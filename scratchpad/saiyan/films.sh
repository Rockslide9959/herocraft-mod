#!/bin/sh
# Saiyan aura films: MP4 + contact sheet per recording, plus the lineup PNG, into docs/screenshots/v0173/saiyan_aura/.
# Run from the worktree root after the harness run (frames in run/screenshots/sa_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0173/saiyan_aura"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in sa_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#sa_}
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sa_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	count=$(ls sa_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sa_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
# lineup: every form idle (top row) and charging (bottom row), plus base charging
args=""; filt=""; k=0
for st in idle charge; do
	for f in ssj ssj2 ssj3 god blue blue_overdrive instinct; do
		args="$args -i sa_still_${f}_${st}.png"
		filt="$filt[$k]crop=420:640:430:20,scale=280:427,drawtext=fontfile=arial.ttf:text='${f} ${st}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.6[v$k];"
		k=$((k + 1))
	done
	args="$args -i sa_still_base_charge.png"
	filt="$filt[$k]crop=420:640:430:20,scale=280:427,drawtext=fontfile=arial.ttf:text='base charge':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.6[v$k];"
	k=$((k + 1))
done
inputs=""; layout=""; i=0
while [ $i -lt $k ]; do inputs="$inputs[v$i]"; x=$(( (i % 8) * 280 )); y=$(( (i / 8) * 427 )); layout="$layout${layout:+|}${x}_${y}"; i=$((i + 1)); done
"$FF" -hide_banner -loglevel error -y $args -filter_complex "$filt${inputs}xstack=inputs=$k:layout=$layout" -frames:v 1 "$OUT/aura_lineup.png"
echo lineup
