#!/bin/sh
# Beast Boy hq round (land mammals): MP4s + frame sheets + stills from LandHqHarness frames into docs/screenshots/v0173/beastboy_hq/.
# Usage (from the worktree root): sh scratchpad/beastboy/land_films.sh <frames dir (.../screenshots)> [forms...]
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SRC="$1"
shift
FORMS="${*:-WOLF CHEETAH TIGER BEAR MOUSE}"
OUT="$PWD/docs/screenshots/v0173/beastboy_hq"
mkdir -p "$OUT"
cd "$SRC" || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
lbl() { echo "drawtext=fontfile=arial.ttf:text='$1':x=10:y=h-34:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"; }
enc() { # enc <prefix> <label> <out mp4>
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "${1}_%03d.png" -vf "$(lbl "$2"),format=yuv420p" -c:v libx264 -crf 20 "$3"
}
sheet() { # sheet <prefix> <cols> <step> <out png>: one row of frames 0, step, 2*step... (zoomed on the animal)
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "${1}_%03d.png" \
		-vf "select='not(mod(n\,$3))',crop=560:360:147:60,scale=280:180,tile=${2}x1:padding=2:color=white" -frames:v 1 "$4"
}
for f in $FORMS; do
	lf=$(echo "$f" | tr 'A-Z' 'a-z')
	for v in f34 side front back; do [ -f "lh_${v}_${f}_000.png" ] && cp "lh_${v}_${f}_000.png" "$OUT/${lf}_after_${v}.png"; done
	if [ -f "lh_turn_${f}_000.png" ]; then
		enc "lh_turn_${f}" "$f turntable" "$OUT/${lf}_turntable.mp4"
		sheet "lh_turn_${f}" 6 12 "$OUT/${lf}_turntable_frames.png"
	fi
	for k in walk run idle; do
		if [ -f "lh_${k}_${f}_000.png" ]; then
			enc "lh_${k}_${f}" "$f $k" "tmp_${lf}_${k}.mp4"
			sheet "lh_${k}_${f}" 8 $([ $k = idle ] && echo 12 || echo 3) "tmp_${lf}_${k}.png"
		fi
	done
	# walk + run (+ idle) in one MP4 and one sheet
	list=""; rows=""; n=0
	for k in walk run idle; do [ -f "tmp_${lf}_${k}.mp4" ] && { list="$list -i tmp_${lf}_${k}.mp4"; rows="$rows -i tmp_${lf}_${k}.png"; n=$((n + 1)); }; done
	if [ $n -gt 0 ]; then
		"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$n:v=1:a=0,format=yuv420p" -c:v libx264 -crf 20 "$OUT/${lf}_walk_run.mp4"
		"$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$n" -frames:v 1 "$OUT/${lf}_walk_run_frames.png"
	fi
	rm -f tmp_${lf}_*.mp4 tmp_${lf}_*.png
	# moves (filmed at half speed)
	list=""; rows=""; i=0
	for m in $(ls lh_move_${f}_*_000.png 2>/dev/null | sed "s/lh_move_${f}_//; s/_000.png//"); do
		enc "lh_move_${f}_${m}" "$f  $m  (0.5x)" "tmpm_${lf}_$i.mp4"
		list="$list -i tmpm_${lf}_$i.mp4"
		sheet "lh_move_${f}_${m}" 8 3 "tmpr_${lf}_$i.png"
		rows="$rows -i tmpr_${lf}_$i.png"
		i=$((i + 1))
	done
	[ "$f" = WOLF ] && [ -f lh_wolfseq_000.png ] && {
		enc lh_wolfseq "WOLF  run, stop, crouch to sit, stand" "tmpm_${lf}_$i.mp4"; list="$list -i tmpm_${lf}_$i.mp4"
		sheet lh_wolfseq 8 14 "tmpr_${lf}_$i.png"; rows="$rows -i tmpr_${lf}_$i.png"; i=$((i + 1)); }
	if [ $i -gt 0 ]; then
		"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$i:v=1:a=0,format=yuv420p" -c:v libx264 -crf 20 "$OUT/${lf}_moves.mp4"
		if [ $i -gt 1 ]; then "$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$i" -frames:v 1 "$OUT/${lf}_moves_frames.png"; else cp tmpr_${lf}_0.png "$OUT/${lf}_moves_frames.png"; fi
		rm -f tmpm_${lf}_*.mp4 tmpr_${lf}_*.png
	fi
done
ls "$OUT"
