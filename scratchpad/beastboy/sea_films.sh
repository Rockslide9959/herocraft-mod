#!/bin/sh
# Beast Boy HQ round, sea animals: MP4s + frame sheets + stills from SeaHarness frames into docs/screenshots/v0173/beastboy_hq/.
# Usage (from the worktree root): sh scratchpad/beastboy/sea_films.sh <frames dir (.../screenshots)> [out dir]
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SRC="$1"
OUT="${2:-$PWD/docs/screenshots/v0173/beastboy_hq}"
mkdir -p "$OUT"
cd "$SRC" || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
lbl() { echo "drawtext=fontfile=arial.ttf:text='$1':x=10:y=h-34:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"; }
enc() { "$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "${1}_%03d.png" -vf "$(lbl "$2"),format=yuv420p" -c:v libx264 -crf 20 "$3"; }
sheet() { # sheet <prefix> <cols> <step> <out>
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "${1}_%03d.png" \
		-vf "select='not(mod(n\,$3))',scale=427:240,tile=${2}x1:padding=2:color=white" -frames:v 1 "$4"
}
for f in SHARK DOLPHIN OCTOPUS WHALE; do
	lf=$(echo "$f" | tr 'A-Z' 'a-z')
	for st in f34 side tankf34; do [ -f "sh_${st}_${f}_000.png" ] && cp "sh_${st}_${f}_000.png" "$OUT/${lf}_${st}_after.png"; done
	if [ -f "sh_turn_${f}_000.png" ]; then enc "sh_turn_${f}" "$lf turntable" "$OUT/${lf}_turntable.mp4"; sheet "sh_turn_${f}" 5 12 "$OUT/${lf}_turntable_sheet.png"; fi
	if [ -f "sh_swim_${f}_000.png" ]; then enc "sh_swim_${f}" "$lf swimming (underwater)" "$OUT/${lf}_swim.mp4"; sheet "sh_swim_${f}" 6 18 "$OUT/${lf}_swim_sheet.png"; fi
	if [ -f "sh_land_${f}_000.png" ]; then enc "sh_land_${f}" "$lf out of water" "$OUT/${lf}_land.mp4"; sheet "sh_land_${f}" 6 11 "$OUT/${lf}_land_sheet.png"; fi
	list=""; rows=""; i=0
	for m in $(ls sh_move_${f}_*_000.png 2>/dev/null | sed "s/sh_move_${f}_//; s/_000.png//"); do
		enc "sh_move_${f}_${m}" "$lf  $m" "tmp_${lf}_$i.mp4"
		list="$list -i tmp_${lf}_$i.mp4"
		sheet "sh_move_${f}_${m}" 8 3 "tmprow_${lf}_$i.png"
		"$FF" -hide_banner -loglevel error -y -i "tmprow_${lf}_$i.png" -vf "drawtext=fontfile=arial.ttf:text='$m':x=8:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6" "tmprowl_${lf}_$i.png"
		rows="$rows -i tmprowl_${lf}_$i.png"
		i=$((i + 1))
	done
	if [ $i -gt 1 ]; then
		"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$i:v=1:a=0,format=yuv420p" -c:v libx264 -crf 20 "$OUT/${lf}_moves.mp4"
		"$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$i" -frames:v 1 "$OUT/${lf}_moves_sheet.png"
	elif [ $i -eq 1 ]; then
		cp "tmp_${lf}_0.mp4" "$OUT/${lf}_moves.mp4"; cp "tmprowl_${lf}_0.png" "$OUT/${lf}_moves_sheet.png"
	fi
	rm -f tmp_${lf}_*.mp4 tmprow_${lf}_*.png tmprowl_${lf}_*.png
done
ls "$OUT"
