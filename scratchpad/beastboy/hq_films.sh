#!/bin/sh
# Beast Boy HQ round (T-Rex, pterodactyl, hawk, bat, snake, fly): MP4s + frame sheets + stills from BeastHQHarness frames,
# into docs/screenshots/v0173/beastboy_hq/. Usage (from the worktree root): sh scratchpad/beastboy/hq_films.sh <frames dir>
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SRC="$1"
OUT="$PWD/docs/screenshots/v0173/beastboy_hq"
mkdir -p "$OUT"
cd "$SRC" || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
lbl() { echo "drawtext=fontfile=arial.ttf:text='$1':x=10:y=h-34:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"; }
enc() { "$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "${1}_%03d.png" -vf "$(lbl "$2"),format=yuv420p" -c:v libx264 -crf 20 "$3"; }
sheet() { # sheet <prefix> <cols> <step> <out>
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "${1}_%03d.png" \
		-vf "select='not(mod(n\,$3))',scale=320:180,tile=${2}x1:padding=2:color=white" -frames:v 1 "$4"
}
for f in TREX PTERODACTYL HAWK BAT SNAKE FLY; do
	lf=$(echo "$f" | tr 'A-Z' 'a-z')
	for k in f34 side f34air topair; do [ -f "hq_${k}_${f}_000.png" ] && cp "hq_${k}_${f}_000.png" "$OUT/${lf}_${k}.png"; done
	if [ -f "hq_turn_${f}_000.png" ]; then
		enc "hq_turn_${f}" "$f turntable" "$OUT/${lf}_turntable.mp4"
		sheet "hq_turn_${f}" 6 12 "$OUT/${lf}_turntable_frames.png"
	fi
	if [ -f "hq_seq_${f}_000.png" ]; then
		enc "hq_seq_${f}" "$f walk / flight" "$OUT/${lf}_walk_fly.mp4"
		"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "hq_seq_${f}_%03d.png" \
			-vf "select='not(mod(n\,10))',scale=320:180,tile=5x3:padding=2:color=white" -frames:v 1 "$OUT/${lf}_walk_fly_frames.png"
	fi
	list=""; rows=""; i=0
	for m in $(ls hq_move_${f}_*_000.png 2>/dev/null | sed "s/hq_move_${f}_//; s/_000.png//"); do
		enc "hq_move_${f}_${m}" "$f  $m" "tmp_${lf}_$i.mp4"
		list="$list -i tmp_${lf}_$i.mp4"
		sheet "hq_move_${f}_${m}" 8 3 "tmprow_${lf}_$i.png"
		"$FF" -hide_banner -loglevel error -y -i "tmprow_${lf}_$i.png" -vf "drawtext=fontfile=arial.ttf:text='$m':x=8:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6" "tmprowl_${lf}_$i.png"
		rows="$rows -i tmprowl_${lf}_$i.png"
		i=$((i + 1))
	done
	if [ $i -gt 1 ]; then
		"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$i:v=1:a=0,format=yuv420p" -c:v libx264 -crf 20 "$OUT/${lf}_moves.mp4"
		"$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$i" -frames:v 1 "$OUT/${lf}_moves_frames.png"
	elif [ $i -eq 1 ]; then
		cp "tmp_${lf}_0.mp4" "$OUT/${lf}_moves.mp4"; cp "tmprowl_${lf}_0.png" "$OUT/${lf}_moves_frames.png"
	fi
	rm -f tmp_${lf}_*.mp4 tmprow_${lf}_*.png tmprowl_${lf}_*.png
	if [ -f "hq_quick_${f}_000.png" ]; then
		enc "hq_quick_${f}" "quick morph $f" "$OUT/quick_${lf}.mp4"
		sheet "hq_quick_${f}" 6 4 "$OUT/quick_${lf}_frames.png"
	fi
done
ls "$OUT"
