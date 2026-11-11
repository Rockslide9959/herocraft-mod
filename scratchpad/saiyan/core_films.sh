#!/bin/sh
# Saiyan revamp core films: MP4 + frame sheet per clip, plus the HUD stills, into docs/screenshots/v0173/saiyan_revamp/.
# Run from the worktree root after the SaiyanCoreHarness run (frames in run/screenshots/sc_*).
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
ROOT="$PWD"
OUT="$ROOT/docs/screenshots/v0173/saiyan_revamp"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
for n in vcharge_tap vcharge_hold flight rage ssj_transform ssj_end kaioken; do
	[ -f "sc_${n}_000.png" ] || continue
	"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i "sc_${n}_%03d.png" -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT/$n.mp4"
	count=$(ls sc_${n}_[0-9]*.png | wc -l)
	every=$(( (count + 15) / 16 ))
	"$FF" -hide_banner -loglevel error -y -start_number 0 -i "sc_${n}_%03d.png" -vf "select='not(mod(n\,$every))',scale=384:-2,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=4x4" -frames:v 1 "$OUT/${n}_sheet.png"
	echo "$n ($count frames)"
done
for s in hud_base hud_shift hud_alt ssj_hud kaioken_hud; do
	[ -f "sc_still_$s.png" ] && cp "sc_still_$s.png" "$OUT/$s.png"
done
ls -la "$OUT"
