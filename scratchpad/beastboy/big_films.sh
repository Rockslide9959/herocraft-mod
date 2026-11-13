#!/bin/sh
# v0.17.3 Beast Boy HQ round (rhino / elephant / gorilla / Beast): MP4s + frame sheets + before/after stills from the
# BigAnimalsHarness frames, into docs/screenshots/v0173/beastboy_hq/.
# Usage (from the worktree root): sh scratchpad/beastboy/big_films.sh <frames dir (.../screenshots)>
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SRC="$1"
OUT="$PWD/docs/screenshots/v0173/beastboy_hq"
BEFORE="$PWD/docs/screenshots/v0173/beastboy_fix"
mkdir -p "$OUT"
cd "$SRC" || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
C="crop=560:420:147:30"
lbl() { echo "drawtext=fontfile=arial.ttf:text='$1':x=10:y=h-34:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"; }
enc() { # enc <prefix> <label> <out.mp4> [setpts factor]
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "${1}_%03d.png" -vf "$C,$(lbl "$2"),setpts=${4:-1}*PTS,format=yuv420p" -c:v libx264 -crf 20 "$3"
}
for f in RHINO ELEPHANT GORILLA BEAST; do
	lf=$(echo "$f" | tr 'A-Z' 'a-z')
	enc "bh_turn_$f" "$lf turntable" "$OUT/${lf}_turntable.mp4"
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "bh_turn_${f}_%03d.png" -vf "select='not(mod(n\,6))',$C,scale=280:210,tile=8x1:padding=2:color=white" -frames:v 1 "$OUT/${lf}_turntable_frames.png"
	enc "bh_walk_$f" "$lf walk" "$OUT/${lf}_walk.mp4"
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "bh_walk_${f}_%03d.png" -vf "select='not(mod(n\,3))',$C,scale=280:210,tile=8x2:padding=2:color=white" -frames:v 1 "$OUT/${lf}_walk_frames.png"
	enc "bh_idle_$f" "$lf idle" "$OUT/${lf}_idle.mp4"
	# moves: real time then half speed, back to back; one sheet row per move
	list=""; rows=""; i=0
	for m in $(ls bh_move_${f}_*_000.png 2>/dev/null | sed "s/bh_move_${f}_//; s/_000.png//"); do
		nf=$(ls bh_move_${f}_${m}_*.png | wc -l); st=$(( (nf - 6) / 10 )); [ $st -lt 2 ] && st=2
		enc "bh_move_${f}_$m" "$lf  $m" "tmp_${lf}_${i}a.mp4"
		enc "bh_move_${f}_$m" "$lf  $m  (half speed)" "tmp_${lf}_${i}b.mp4" 2
		list="$list -i tmp_${lf}_${i}a.mp4 -i tmp_${lf}_${i}b.mp4"
		"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "bh_move_${f}_${m}_%03d.png" \
			-vf "select='gte(n\,2)*not(mod(n-2\,$st))',$C,scale=224:168,tile=10x1:padding=2:color=white,drawtext=fontfile=arial.ttf:text='$m':x=8:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6" \
			-frames:v 1 "tmprow_${lf}_$i.png"
		rows="$rows -i tmprow_${lf}_$i.png"
		i=$((i + 1))
	done
	n=$((i * 2))
	"$FF" -hide_banner -loglevel error -y $list -filter_complex "concat=n=$n:v=1:a=0,format=yuv420p" -c:v libx264 -crf 20 "$OUT/${lf}_moves.mp4"
	if [ $i -gt 1 ]; then
		"$FF" -hide_banner -loglevel error -y $rows -filter_complex "vstack=inputs=$i" -frames:v 1 "$OUT/${lf}_moves_frames.png"
	else
		cp "tmprow_${lf}_0.png" "$OUT/${lf}_moves_frames.png"
	fi
	rm -f tmp_${lf}_*.mp4 tmprow_${lf}_*.png
	# stills and before / after (the fix round's front three-quarter vs this round's)
	cp "bh_f34_${f}_000.png" "$OUT/${lf}_front34.png"
	cp "bh_side_${f}_000.png" "$OUT/${lf}_side.png"
	cp "bh_front_${f}_000.png" "$OUT/${lf}_front.png"
	cp "bh_back_${f}_000.png" "$OUT/${lf}_back.png"
	if [ -f "$BEFORE/${lf}_front34.png" ]; then
		"$FF" -hide_banner -loglevel error -y -i "$BEFORE/${lf}_front34.png" -i "bh_f34_${f}_000.png" -filter_complex \
			"[0]drawtext=fontfile=arial.ttf:text='before':x=10:y=10:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.6[a];[1]drawtext=fontfile=arial.ttf:text='after':x=10:y=10:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.6[b];[a][b]hstack=2" \
			"$OUT/${lf}_before_after.png"
	fi
done
echo "films in $OUT"
