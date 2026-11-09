#!/bin/sh
# Bone Sword v2 films: an MP4 (20 fps = real time; *_slow at 10 fps = half speed) and a contact sheet for each recording, and the
# side-by-side stills, into docs/screenshots/v0173/andy_bone_sword_v2/.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="docs/screenshots/v0173/andy_bone_sword_v2"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for n in fp_draw tp_draw_front tp_draw_side fp_drips tp_drips kit_tp kit_fp tear_tp tear_fp; do
	[ -f "bs_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "bs_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 "../../$OUT/$n.mp4"
	"$FF" -hide_banner -loglevel error -y -framerate 10 -start_number 0 -i "bs_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 "../../$OUT/${n}_slow.mp4"
	count=$(ls bs_${n}_*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "bs_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "../../$OUT/${n}_sheet.png"
	echo "$n ($count frames, sheet every $every)"
done
if [ -f bs_fp_vanilla.png ] && [ -f bs_fp_bone.png ]; then
	"$FF" -hide_banner -loglevel error -y -i bs_fp_vanilla.png -i bs_fp_bone.png -filter_complex "[0]drawtext=fontfile=arial.ttf:text='vanilla netherite sword':x=10:y=10:fontsize=24:fontcolor=white:box=1:boxcolor=black@0.6[a];[1]drawtext=fontfile=arial.ttf:text='Bone Sword':x=10:y=10:fontsize=24:fontcolor=white:box=1:boxcolor=black@0.6[b];[a][b]hstack" "../../$OUT/fp_hold_vs_vanilla.png"
	"$FF" -hide_banner -loglevel error -y -i bs_fp_vanilla.png -i bs_fp_bone.png -filter_complex "[0]crop=380:330:474:150,drawtext=fontfile=arial.ttf:text='vanilla':x=6:y=6:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6[a];[1]crop=380:330:474:150,drawtext=fontfile=arial.ttf:text='Bone Sword':x=6:y=6:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6[b];[a][b]hstack" "../../$OUT/fp_hold_vs_vanilla_close.png"
	echo fp compare
fi
if [ -f bs_tp_vanilla.png ] && [ -f bs_tp_bone.png ]; then
	"$FF" -hide_banner -loglevel error -y -i bs_tp_vanilla.png -i bs_tp_bone.png -filter_complex "[0]drawtext=fontfile=arial.ttf:text='vanilla netherite sword':x=10:y=10:fontsize=24:fontcolor=white:box=1:boxcolor=black@0.6[a];[1]drawtext=fontfile=arial.ttf:text='Bone Sword':x=10:y=10:fontsize=24:fontcolor=white:box=1:boxcolor=black@0.6[b];[a][b]hstack" "../../$OUT/tp_hold_vs_vanilla.png"
	echo tp compare
fi
for s in hud_struggle hud_sword_moves hud_moves; do
	[ -f "bs_$s.png" ] && cp "bs_$s.png" "../../$OUT/$s.png"
done
echo done
