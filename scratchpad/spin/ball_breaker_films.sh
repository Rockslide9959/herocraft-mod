#!/bin/sh
# Ball Breaker model candidates: MP4 + still sheet + a sheet against the online references, into docs/screenshots/v0173/spin/.
# Run from the worktree root after the harness run (frames in run/screenshots/bb_*).
# Usage: sh scratchpad/spin/ball_breaker_films.sh <dir with the reference images ref_*.png>
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
REFS="$1"
OUT="$PWD/docs/screenshots/v0173/spin"
mkdir -p "$OUT"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
LBL="drawtext=fontfile=arial.ttf:text='A  custom model':x=w*0.18:y=h-40:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55,drawtext=fontfile=arial.ttf:text='B  PlanetMinecraft skin':x=w*0.6:y=h-40:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"
"$FF" -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i bb_turntable_%03d.png -framerate 20 -start_number 0 -i bb_fx_%03d.png \
	-filter_complex "[0:v][1:v]concat=n=2:v=1[c];[c]$LBL,format=yuv420p[o]" -map "[o]" -c:v libx264 -crf 20 "$OUT/ball_breaker_candidates.mp4"
# still sheet: row 1 side by side, row 2 A close, row 3 B close; front / three-quarter / side / back
i=0
rm -f sheet_*.png
for n in pair a b; do
	for v in front threequarter side back; do
		cp "bb_${n}_${v}_000.png" "$(printf 'sheet_%02d.png' $i)"
		i=$((i + 1))
	done
done
"$FF" -hide_banner -loglevel error -y -start_number 0 -i sheet_%02d.png \
	-vf "crop=640:400:107:20,scale=480:300,drawtext=fontfile=arial.ttf:text='%{n}':x=6:y=6:fontsize=1:fontcolor=black@0,tile=4x3:padding=4:color=white" \
	-frames:v 1 "$OUT/ball_breaker_candidates_sheet.png"
# against the references: top row = references (All-Star Battle render, manga, colour alts, concept sketch), bottom = A front,
# A three-quarter, B front, B three-quarter
H=420
"$FF" -hide_banner -loglevel error -y \
	-i "$REFS/ref_334px-Ballbreakerasb.png" -i "$REFS/ref_Ball_Breaker_Infobox_Manga.png" -i "$REFS/ref_449px-Ballbreaker_ASB_Color_Alts_A-B.png" \
	-i "$REFS/ref_616px-Ballbreaker_conceptart2.png" \
	-i bb_a_front_000.png -i bb_a_threequarter_000.png -i bb_b_front_000.png -i bb_b_threequarter_000.png \
	-filter_complex "\
[0:v]scale=-2:$H,pad=iw+8:ih:4:0:white[r0];[1:v]scale=-2:$H,pad=iw+8:ih:4:0:white[r1];[2:v]scale=-2:$H,pad=iw+8:ih:4:0:white[r2];[3:v]scale=-2:$H,pad=iw+8:ih:4:0:white[r3];\
[r0][r1][r2][r3]hstack=4,drawtext=fontfile=arial.ttf:text='references (jojowiki)':x=8:y=h-30:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.55[top];\
[4:v]crop=440:420:207:20,pad=iw+8:ih:4:0:white[a0];[5:v]crop=440:420:207:20,pad=iw+8:ih:4:0:white[a1];\
[6:v]crop=440:420:207:20,pad=iw+8:ih:4:0:white[b0];[7:v]crop=440:420:207:20,pad=iw+8:ih:4:0:white[b1];\
[a0][a1][b0][b1]hstack=4,drawtext=fontfile=arial.ttf:text='A custom model':x=10:y=10:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55,drawtext=fontfile=arial.ttf:text='B PlanetMinecraft skin':x=906:y=10:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55[bot];\
[top][bot]xstack=inputs=2:layout=0_0|0_h0:fill=white[o]" -map "[o]" -frames:v 1 "$OUT/ball_breaker_vs_refs.png"
rm -f sheet_*.png
ls -la "$OUT"/ball_breaker_*
