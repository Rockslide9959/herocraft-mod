#!/bin/sh
# Saiyan aura v2 (jagged flame) films: MP4 + frame sheet per clip, the stills, and compare_sketch.png (front still next to the
# recreated sketch) into docs/screenshots/v0173/saiyan_aura_v2/. Run from the worktree root after the SaiyanAuraV2Harness
# run (frames in run/screenshots/sa_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
ROOT="$PWD"
OUT="$ROOT/docs/screenshots/v0173/saiyan_aura_v2"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
LBL="fontfile=arial.ttf:x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"
for n in ssj_idle_front ssj_idle_orbit ssj_charge base_charge transform night fp_charge; do
	[ -f "sa_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sa_${n}_%03d.png" -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT/$n.mp4"
	count=$(ls sa_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 15) / 16 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sa_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:-2,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=4x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
for s in front_idle front_idle2 front_charge front_base_charge; do
	[ -f "sa_still_$s.png" ] && cp "sa_still_$s.png" "$OUT/still_$s.png"
done
# the sketch next to the front still (middle of the frame, same height)
node "$ROOT/scratchpad/saiyan/aura_sketch_recreation.js" "$PWD/aura_sketch.png" 480 640
"$FF" -hide_banner -loglevel error -y -i aura_sketch.png -i sa_still_front_idle.png -i sa_still_front_charge.png -filter_complex \
	"[0]scale=-2:640,drawtext=$LBL:text='sketch (redrawn)'[a];[1]crop=ih*0.75:ih,scale=-2:640,drawtext=$LBL:text='in game - SSJ idle'[b];[2]crop=ih*0.75:ih,scale=-2:640,drawtext=$LBL:text='in game - charging'[c];[a][b][c]hstack=3" \
	-frames:v 1 "$OUT/compare_sketch.png"
ls -la "$OUT"
