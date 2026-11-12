#!/bin/sh
# ShazamSkinHarness output -> docs/screenshots/v0173/shazam_skin/: stills, a contact sheet of them, and an MP4 + frame sheet per film.
# Run from the worktree root after the harness run (frames in run/screenshots/szs_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="$PWD/docs/screenshots/v0173/shazam_skin"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for f in szs_still_*.png; do cp "$f" "$OUT/${f#szs_still_}"; done
for who in steve_wide_champion alex_slim_champion zuri_wide_family noor_slim_family; do
	"$FF" -hide_banner -loglevel error -y -i "szs_still_${who}_front.png" -i "szs_still_${who}_right.png" -i "szs_still_${who}_back.png" -i "szs_still_${who}_left.png" \
		-filter_complex "[0]crop=300:400:277:40[a];[1]crop=300:400:277:40[b];[2]crop=300:400:277:40[c];[3]crop=300:400:277:40[d];[a][b][c][d]hstack=inputs=4,scale=1600:-1:flags=neighbor" "$OUT/sheet_${who}.png"
done
for n in walk fly; do
	[ -f "szs_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "szs_${n}_%03d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/$n.mp4"
	count=$(ls szs_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "szs_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:216,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
