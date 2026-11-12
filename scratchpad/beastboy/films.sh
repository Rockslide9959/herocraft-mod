#!/bin/sh
# Beast Boy art: MP4s + frame sheets + stills from the harness frames, into docs/screenshots/v0173/beastboy_art/.
# Usage (from the worktree root): sh scratchpad/beastboy/films.sh <frames dir (…/screenshots)> [forms...]
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SRC="$1"
shift
FORMS="${*:-TREX GORILLA RHINO ELEPHANT PTERODACTYL SHARK OCTOPUS WHALE MOUSE FLY BEAST SNAKE}"
OUT="$PWD/docs/screenshots/v0173/beastboy_art"
mkdir -p "$OUT"
cd "$SRC" || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
lbl() { echo "drawtext=fontfile=arial.ttf:text='$1':x=10:y=h-34:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"; }
enc() { # enc <pattern prefix> <label> <out mp4>
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "${1}_%03d.png" -vf "$(lbl "$2"),format=yuv420p" -c:v libx264 -crf 22 "$3"
}
sheet() { # sheet <prefix> <cols> <step> <label> <out png> : one row of frames 0, step, 2*step...
	n=$(ls ${1}_*.png 2>/dev/null | wc -l)
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "${1}_%03d.png" \
		-vf "select='not(mod(n\,$3))',scale=320:180,tile=${2}x1:padding=2:color=white" -frames:v 1 "$5"
}
# stills
[ -f ba_lineup_big_000.png ] && cp ba_lineup_big_000.png "$OUT/lineup_with_player.png"
[ -f ba_lineup_small_000.png ] && cp ba_lineup_small_000.png "$OUT/lineup_small_with_player.png"
for f in $FORMS; do
	lf=$(echo "$f" | tr 'A-Z' 'a-z')
	if [ -f "ba_turn_${f}_000.png" ]; then
		enc "ba_turn_${f}" "$f turntable" "$OUT/${lf}_turntable.mp4"
		"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "ba_turn_${f}_%03d.png" \
			-vf "select='not(mod(n\,12))',scale=427:240,tile=4x1:padding=2:color=white" -frames:v 1 "$OUT/${lf}_turntable.png"
	fi
	if [ -f "ba_walk_${f}_000.png" ]; then
		enc "ba_walk_${f}" "$f walk" "$OUT/${lf}_walk.mp4"
		sheet "ba_walk_${f}" 8 4 "$f walk" "$OUT/${lf}_walk_frames.png"
	fi
	# moves: one MP4 for the form (all moves back to back), one sheet row per move (8 frames)
	list=""
	rows=""
	i=0
	for m in $(ls ba_move_${f}_*_000.png 2>/dev/null | sed "s/ba_move_${f}_//; s/_000.png//"); do
		enc "ba_move_${f}_${m}" "$f  $m" "tmp_${lf}_$i.mp4"
		list="$list -i tmp_${lf}_$i.mp4"
		sheet "ba_move_${f}_${m}" 8 3 "$m" "tmprow_${lf}_$i.png"
		"$FF" -hide_banner -loglevel error -y -i "tmprow_${lf}_$i.png" -vf "$(echo "drawtext=fontfile=arial.ttf:text='$m':x=8:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6")" "tmprowl_${lf}_$i.png"
		rows="$rows -i tmprowl_${lf}_$i.png"
		i=$((i + 1))
	done
	if [ $i -gt 0 ]; then
		if [ $i -gt 1 ]; then
			"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$i:v=1:a=0,format=yuv420p" -c:v libx264 -crf 22 "$OUT/${lf}_moves.mp4"
			"$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$i" -frames:v 1 "$OUT/${lf}_moves_frames.png"
		else
			cp "tmp_${lf}_0.mp4" "$OUT/${lf}_moves.mp4"
			cp "tmprowl_${lf}_0.png" "$OUT/${lf}_moves_frames.png"
		fi
		rm -f tmp_${lf}_*.mp4 tmprow_${lf}_*.png tmprowl_${lf}_*.png
	fi
done
# quick morphs
list=""
rows=""
i=0
for q in GORILLA RHINO TREX SNAKE ELEPHANT OCTOPUS; do
	[ -f "ba_quick_${q}_000.png" ] || continue
	enc "ba_quick_${q}" "quick morph  $q" "tmpq_$i.mp4"
	list="$list -i tmpq_$i.mp4"
	sheet "ba_quick_${q}" 6 5 "$q" "tmpqr_$i.png"
	"$FF" -hide_banner -loglevel error -y -i "tmpqr_$i.png" -vf "drawtext=fontfile=arial.ttf:text='$q':x=8:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6" "tmpqrl_$i.png"
	rows="$rows -i tmpqrl_$i.png"
	cp "ba_quick_${q}_013.png" "$OUT/quick_$(echo $q | tr 'A-Z' 'a-z').png"
	i=$((i + 1))
done
if [ $i -gt 1 ]; then
	"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$i:v=1:a=0,format=yuv420p" -c:v libx264 -crf 22 "$OUT/quick_morphs.mp4"
	"$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$i" -frames:v 1 "$OUT/quick_morphs_frames.png"
fi
rm -f tmpq_*.mp4 tmpqr_*.png tmpqrl_*.png
ls "$OUT" | head -100
