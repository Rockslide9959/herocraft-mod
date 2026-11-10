#!/bin/sh
# Grid of the aura stills (idle row, charge row), cropped round the player. Arg: output png.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
args=""; filt=""; k=0
for st in idle charge; do
for f in ssj ssj2 ssj3 god blue blue_overdrive instinct; do
	args="$args -i sa_still_${f}_${st}.png"
	filt="$filt[$k]crop=420:640:430:20,scale=280:427,drawtext=fontfile=arial.ttf:text='${f} ${st}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.6[v$k];"
	k=$((k + 1))
done; done
inputs=""; layout=""; i=0
while [ $i -lt $k ]; do inputs="$inputs[v$i]"; x=$(( (i % 7) * 280 )); y=$(( (i / 7) * 427 )); layout="$layout${layout:+|}${x}_${y}"; i=$((i + 1)); done
"$FF" -hide_banner -loglevel error -y $args -filter_complex "$filt${inputs}xstack=inputs=$k:layout=$layout" -frames:v 1 "$1"
