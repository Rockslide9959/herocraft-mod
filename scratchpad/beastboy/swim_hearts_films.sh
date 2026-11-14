#!/bin/sh
# v0.17.4 Beast Boy swim / hearts films: an MP4 and a frame sheet per scene (stills copied as PNG) into
# docs/screenshots/v0174/beast_boy/<subdir>. Run from the worktree root after the harness run (frames in run/screenshots/sh_*).
# Usage: sh scratchpad/beastboy/swim_hearts_films.sh [subdir]   (e.g. "before"; default: the folder itself)
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0174/beast_boy/$1"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in sh_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#sh_}
	count=$(ls sh_${n}_[0-9]*.png | wc -l)
	if [ "$count" -le 2 ]; then
		cp "sh_${n}_$(printf %03d $((count - 1))).png" "$OUT/$n.png"
		echo "$n (still)"
		continue
	fi
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sh_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sh_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
