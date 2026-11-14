#!/bin/sh
# v0.17.4 misc tweaks: copies the harness stills and makes the rift MP4 + frame sheet into docs/screenshots/v0174/misc_tweaks/.
# Run from the worktree root after the MiscTweaksHarness run.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0174/misc_tweaks"
mkdir -p "$OUT"
cd run/screenshots || exit 1
for f in mt_*.png; do
	[ -f "$f" ] && cp "$f" "$OUT/${f#mt_}"
done
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
if [ -f mtv_rift_000.png ]; then
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "mtv_rift_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/raven_rift_squad.mp4"
	count=$(ls mtv_rift_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "mtv_rift_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/raven_rift_squad_sheet.png"
	echo "rift ($count frames)"
fi
