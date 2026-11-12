#!/bin/sh
# Wizard films: stills, and an MP4 + frame sheet per scene, into docs/screenshots/v0173/shazam_wizard/.
# Run from the worktree root after the WizardHarness run (frames in run/screenshots/wz_*).
# Whole loop: node scratchpad/shazam/wizard_harness_hook.js . ; node scratchpad/low_graphics_options.js run/options.txt ;
# echo stills > run/wizard.flag ; ./gradlew runClient ; sh scratchpad/shazam/wizard_films.sh ; node scratchpad/shazam/wizard_harness_hook.js . off
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0173/shazam_wizard"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in wz_still_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#wz_still_}
	cp "$f" "$OUT/$n.png"
done
for n in idle orbit grant; do
	[ -f "wz_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "wz_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	count=$(ls wz_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "wz_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=320:320,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
