#!/bin/sh
# SSJ hair v2 preview outputs: ${P}_lineup.png, ${P}_turntable.mp4 (+ sheet), ${P}_compare.png (side and back next to crops of
# the user's reference sheets). Run from the worktree root after the harness run. Arg 1 (optional): output dir.
P="${P:-v2}"
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="${1:-$PWD/docs/screenshots/v0173/ssj_hair_preview}"
REF="$PWD/docs/reference/ssj_hair_refs"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
LBL="fontfile=arial.ttf:x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"
if [ -f sh_turntable_000.png ]; then
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sh_turntable_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT/${P}_turntable.mp4"
	count=$(ls sh_turntable_[0-9]*.png | wc -l)
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sh_turntable_%03d.png" -vf "select='not(mod(n\,$((count / 16))))',crop=ih:ih,scale=300:300,tile=8x2" -frames:v 1 "$OUT/${P}_turntable_sheet.png"
fi
args=""; filt=""; k=0
for v in front threequarter side2 back topthreequarter; do
	args="$args -i sh_still_$v.png"
	lab=$v; [ $v = side2 ] && lab=side; [ $v = topthreequarter ] && lab='top 3/4 (back)'
	filt="$filt[$k]crop=ih:ih,scale=480:480,drawtext=$LBL:text='$lab'[v$k];"
	k=$((k + 1))
done
"$FF" -hide_banner -loglevel error -y $args -filter_complex "$filt[v0][v1][v2][v3][v4]hstack=inputs=5" -frames:v 1 "$OUT/${P}_lineup.png"
# compare: top row = side (ours, then the Z close-up and the Super turnaround side), bottom row = back (ours, Super back,
# Z back, Blue back-3/4 for the radiating layout)
"$FF" -hide_banner -loglevel error -y -i sh_still_side2.png -i sh_still_back.png -i "$REF/goku_ssj_turnaround_z.png" \
	-i "$REF/goku_ssj_turnaround_super.png" -i "$REF/goku_ssj_blue_angles.png" -filter_complex \
	"[0]crop=ih:ih,scale=400:400,drawtext=$LBL:text='${P} side'[a];\
[2]crop=165:165:395:228,scale=400:400,drawtext=$LBL:text='ref side (Z)':fontcolor=black:boxcolor=white@0.7[b];\
[3]crop=110:110:455:40,scale=400:400,drawtext=$LBL:text='ref side (Super)':fontcolor=black:boxcolor=white@0.7[c];\
[1]crop=ih:ih,scale=400:400,drawtext=$LBL:text='${P} back'[d];\
[3]crop=110:110:58:40,scale=400:400,drawtext=$LBL:text='ref back (Super)':fontcolor=black:boxcolor=white@0.7[e];\
[2]crop=110:110:80:55,scale=400:400,drawtext=$LBL:text='ref back (Z)':fontcolor=black:boxcolor=white@0.7[f];\
[a][b][c]hstack=3[top];[d][e][f]hstack=3[bot];[top][bot]vstack" -frames:v 1 "$OUT/${P}_compare.png"
ls -la "$OUT" | grep ${P}_
