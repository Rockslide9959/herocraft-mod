#!/bin/sh
# Ball Breaker face close-ups (run/screenshots/bb_face_*, from BallBreakerHarness with "face" in run/bb.flag) next to the references.
# Usage (worktree root): sh scratchpad/spin/ball_breaker_face_sheet.sh <dir with ref_334px-Ballbreakerasb.png + ref_Ball_Breaker_Infobox_Manga.png>
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
REFS="$1"
OUT="$PWD/docs/screenshots/v0173/spin/ball_breaker_face_vs_refs.png"
R="$PWD/docs/reference/spin"
cd run/screenshots || exit 1
[ -f arial.ttf ] || cp /c/Windows/Fonts/arial.ttf arial.ttf
L="fontfile=arial.ttf:x=10:y=h-30:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6"
"$FF" -hide_banner -loglevel error -y \
	-i "$REFS/ref_334px-Ballbreakerasb.png" -i "$REFS/ref_Ball_Breaker_Infobox_Manga.png" -i "$R/ball_breaker_face_34.png" \
	-i bb_face_front_000.png -i bb_face_threequarter_000.png -i bb_face_threequarter_r_000.png -filter_complex "\
[0:v]crop=110:110:110:0,scale=-2:360,pad=iw+8:ih:4:0:white,drawtext=$L:text='All-Star Battle'[r0];\
[1:v]crop=200:160:100:0,scale=-2:360,pad=iw+8:ih:4:0:white,drawtext=$L:text='manga'[r1];\
[2:v]scale=-2:360:flags=neighbor,pad=iw+8:ih:4:0:white,drawtext=$L:text='before (flagged)'[r2];\
[r0][r1][r2]hstack=3[top];\
[3:v]crop=300:260:277:150,scale=-2:360,pad=iw+8:ih:4:0:white,drawtext=$L:text='now - front'[b0];\
[4:v]crop=300:260:277:150,scale=-2:360,pad=iw+8:ih:4:0:white,drawtext=$L:text='now - three-quarter'[b1];\
[5:v]crop=300:260:277:150,scale=-2:360,pad=iw+8:ih:4:0:white,drawtext=$L:text='now - other side'[b2];\
[b0][b1][b2]hstack=3[bot];[top][bot]xstack=inputs=2:layout=0_0|0_h0:fill=white[o]" -map "[o]" -frames:v 1 "$OUT"
echo "wrote $OUT"
