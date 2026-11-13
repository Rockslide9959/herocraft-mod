#!/bin/sh
# Beast Boy morph round: MP4s + frame sheets from MorphHarness frames into docs/screenshots/v0173/beastboy_morph/.
# Usage (from the worktree root): sh scratchpad/beastboy/morph_films.sh <frames dir (.../screenshots)> [shots...]
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SRC="$1"
shift
OUT="$PWD/docs/screenshots/v0173/beastboy_morph"
mkdir -p "$OUT"
cd "$SRC" || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
SHOTS="${*:-$(ls mo_*_000.png | sed 's/^mo_//; s/_000.png$//')}"
for s in $SHOTS; do
	n=$(ls mo_${s}_*.png | wc -l)
	case "$s" in
		live_*) fps=20; label="$s  (in game, synced state, 1x)";;
		slow_*) fps=40; label="$s  (0.5x)";;
		*) fps=40; label="$s  (1x)";;
	esac
	"$FF" -hide_banner -loglevel error -y -framerate $fps -start_number 0 -i "mo_${s}_%03d.png" \
		-vf "drawtext=fontfile=arial.ttf:text='$label':x=10:y=h-34:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55,format=yuv420p" \
		-c:v libx264 -crf 20 "$OUT/${s}.mp4"
	# frame sheet: 12 frames spread over the clip, 2 rows of 6
	step=$(( (n + 11) / 12 ))
	[ $step -lt 1 ] && step=1
	"$FF" -hide_banner -loglevel error -y -framerate 1 -start_number 0 -i "mo_${s}_%03d.png" \
		-vf "select='not(mod(n\,$step))',crop=iw*0.6:ih*0.75:iw*0.2:ih*0.1,scale=320:-2,tile=6x2:padding=2:color=white" -frames:v 1 "$OUT/${s}_frames.png"
done
ls "$OUT"
