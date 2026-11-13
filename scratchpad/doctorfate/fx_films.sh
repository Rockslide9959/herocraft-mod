#!/bin/sh
# Frame sheets (and MP4s with "mp4") per scene from run/screenshots/fx_<tag>_<scene>_NNN.png.
# Usage (worktree root): sh scratchpad/doctorfate/fx_films.sh <tag> <outdir> [mp4] [scene filter regex]
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
TAG="$1"; OUT="$PWD/$2"; MODE="$3"; FILT="${4:-.}"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in fx_${TAG}_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}; n=${n#fx_${TAG}_}
	echo "$n" | grep -Eq "$FILT" || continue
	count=$(ls fx_${TAG}_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "fx_${TAG}_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_${TAG}_sheet.png"
	if [ "$MODE" = "mp4" ]; then
		"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "fx_${TAG}_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/${n}.mp4"
	fi
	echo "$n ($count frames)"
done
