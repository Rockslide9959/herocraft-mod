#!/bin/sh
# One-sweep regrowth / 30-block head throw / head view films: MP4 (20 fps = real time; *_slow4 played at 5 fps = 4x slower, one frame a game tick) + a frame
# sheet per recording into docs/screenshots/v0173/undead_regen_v2/. Run from the repo root after the UndeadRegenV2Harness client
# run (frames in run/screenshots/u2_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
OUT="docs/screenshots/v0173/undead_regen_v2"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for n in arm_real arm_slow4 leg_real leg_slow4 fp_rip_aim fp_head_body fp_head_after throw_side throw_back; do
	[ -f "u2_${n}_000.png" ] || continue
	fr=20; case "$n" in *_slow4) fr=5;; esac
	"$FF" -hide_banner -loglevel error -y -framerate $fr -start_number 0 -i "u2_${n}_%03d.png" -r 20 -c:v libx264 -pix_fmt yuv420p -crf 20 "../../$OUT/$n.mp4"
	count=$(ls u2_${n}_*.png | wc -l)
	every=$(( (count + 19) / 20 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "u2_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=427:240,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=5x4" -frames:v 1 "../../$OUT/${n}_sheet.png"
	echo "$n ($count frames, every $every)"
done
echo done
