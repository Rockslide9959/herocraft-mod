#!/bin/sh
# Andy's Suit + tester fixes films: MP4 (20 fps = real time) + contact sheet per recording, and composed stills, into
# docs/screenshots/v0173/andy_tester_fixes/. Run from the repo root.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="docs/screenshots/v0173/andy_tester_fixes"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for n in suit_regrow suit_headonly_regrow alternating head_pickup; do
	[ -f "af_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "af_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 "../../$OUT/$n.mp4"
	count=$(ls af_${n}_*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "af_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "../../$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
"$FF" -hide_banner -loglevel error -y -i af_suit_front.png -i af_suit_back.png -i af_suit_side.png -i af_suit_three_quarter.png -i af_nosuit_three_quarter.png \
	-filter_complex "[0]crop=360:480:247:0[a];[1]crop=360:480:247:0[b];[2]crop=360:480:247:0[c];[3]crop=360:480:247:0[d];[4]crop=360:480:247:0[e];[a][b][c][d][e]hstack=5" "../../$OUT/suit_views.png"
"$FF" -hide_banner -loglevel error -y -i af_clones_front.png -i af_clones_three_quarter.png -i af_clones_back.png -filter_complex "hstack=3" "../../$OUT/clones.png"
"$FF" -hide_banner -loglevel error -y -i af_off_hud.png -i af_back_on_hud.png -filter_complex "hstack" "../../$OUT/off_then_on_hud.png"
# the item icons, zoomed from the hotbar of the HUD still
"$FF" -hide_banner -loglevel error -y -i af_off_hud.png -vf "crop=200:44:ih*0+(iw/2-182):ih-44,scale=800:176:flags=neighbor" "../../$OUT/suit_icons_hotbar.png"
echo done
