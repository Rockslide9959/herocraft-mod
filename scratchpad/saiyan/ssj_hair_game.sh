#!/bin/sh
# SSJ hair in game: MP4 + frame sheet per clip (transform, transform_close, charge_orbit, turntable), the lineup and the
# first-person stills, into docs/screenshots/v0173/ssj_hair_ingame/. Run from the worktree root after the harness run.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0173/ssj_hair_ingame"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
LBL="fontfile=arial.ttf:x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"
for n in transform transform_close charge_orbit turntable; do
	[ -f "sh_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sh_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT/$n.mp4"
	count=$(ls sh_${n}_[0-9]*.png | wc -l)
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sh_${n}_%03d.png" -vf "select='not(mod(n\,$((count / 16))))',crop=ih:ih,scale=300:300,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=8x2" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
args=""; filt=""; k=0
for v in front threequarter side2 back topthreequarter; do
	args="$args -i sh_still_$v.png"
	filt="$filt[$k]crop=ih:ih,scale=400:400,drawtext=$LBL:text='$v'[v$k];"
	k=$((k + 1))
done
"$FF" -hide_banner -loglevel error -y $args -filter_complex "$filt[v0][v1][v2][v3][v4]hstack=inputs=5" -frames:v 1 "$OUT/lineup.png"
"$FF" -hide_banner -loglevel error -y -i sh_still_fp_level.png -i sh_still_fp_up.png -i sh_still_fp_charge.png -filter_complex \
	"[0]scale=427:240,drawtext=$LBL:text='first person'[a];[1]scale=427:240,drawtext=$LBL:text='looking up'[b];[2]scale=427:240,drawtext=$LBL:text='charging'[c];[a][b][c]hstack=3" \
	-frames:v 1 "$OUT/first_person.png"
ls -la "$OUT"
