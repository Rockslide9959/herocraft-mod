#!/bin/sh
# Saiyan aura v3 (3D teardrop with jagged thorns) films: MP4 + frame sheet per clip, the compare stills and compare.png (v1, v2,
# v3 at the same angles next to the reference video frame) into docs/screenshots/v0173/saiyan_aura_v3/. Run from the worktree
# root after the SaiyanAuraV3Harness run (frames in run/screenshots/sv3_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
ROOT="$PWD"
OUT="$ROOT/docs/screenshots/v0173/saiyan_aura_v3"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
LBL="fontfile=arial.ttf:x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"
for n in ssj_idle_front ssj_idle_side ssj_idle_orbit ssj_charge base_charge transform night fp_charge; do
	[ -f "sv3_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sv3_${n}_%03d.png" -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT/$n.mp4"
	count=$(ls sv3_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 15) / 16 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sv3_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:-2,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=4x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
for s in front_v1 front_v2 front_v3 high_v1 high_v2 high_v3; do
	[ -f "sv3_still_$s.png" ] && cp "sv3_still_$s.png" "$OUT/still_$s.png"
done
# compare.png: the reference frame, then v1 / v2 / v3 straight on (top row) and three-quarter from above (bottom row)
REF="$ROOT/docs/reference/saiyan_aura/video_frame_2.5s.png"
C="crop=ih*0.75:ih,scale=-2:560"
"$FF" -hide_banner -loglevel error -y -i "$REF" -i sv3_still_front_v1.png -i sv3_still_front_v2.png -i sv3_still_front_v3.png \
	-i sv3_still_high_v1.png -i sv3_still_high_v2.png -i sv3_still_high_v3.png -filter_complex \
	"[0]scale=-2:560,crop=420:560,drawtext=$LBL:text='reference video'[r];[1]$C,drawtext=$LBL:text='v1 (old teardrop)'[a];[2]$C,drawtext=$LBL:text='v2 (flat thorns)'[b];[3]$C,drawtext=$LBL:text='v3 (3D + thorns)'[c];\
[4]$C,drawtext=$LBL:text='v1 - from above'[d];[5]$C,drawtext=$LBL:text='v2 - from above'[e];[6]$C,drawtext=$LBL:text='v3 - from above'[f];\
color=black:s=420x560:d=1[k];[r][a][b][c]hstack=4[top];[k][d][e][f]hstack=4[bot];[top][bot]vstack=2" \
	-frames:v 1 "$OUT/compare.png"
ls -la "$OUT"
