#!/bin/sh
# v0.17.5 Beast Boy fish form films: an MP4 and a frame sheet per scene (stills copied as PNG) into
# docs/screenshots/v0175/beastboy_fish. Run from the worktree root after the BeastFishHarness run (frames in run/screenshots/bf_*).
# Harness: scratchpad/BeastFishHarness.v0175.java.txt (copy to src/client/java/com/projecthero/mod/client/BeastFishHarness.java, call
# BeastFishHarness.init() after ModKeyBindings.initialize() in ProjectHeroModClient, write run/beastfish.flag, low graphics, runClient).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0175/beastboy_fish"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in bf_*_000.png; do
	[ -f "$f" ] || continue
	n=${f%_000.png}
	n=${n#bf_}
	count=$(ls bf_${n}_[0-9]*.png | wc -l)
	if [ "$count" -le 2 ]; then
		cp "bf_${n}_$(printf %03d $((count - 1))).png" "$OUT/$n.png"
		echo "$n (still)"
		continue
	fi
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "bf_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 23 "$OUT/$n.mp4"
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "bf_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
