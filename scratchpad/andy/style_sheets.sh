#!/usr/bin/env bash
# TEMP: side-by-side contact sheets (top row 3D, bottom row stepped) and films for the two regrowth looks.
set -e
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SS=run/screenshots
OUT=docs/screenshots/v0173/andy_regrow_styles
mkdir -p "$OUT"
PS="12 25 38 50 60 70 80 90 97"
CROP="crop=560:560:360:80,scale=280:280:flags=neighbor"
for part in arm leg torso head; do
  for st in 3d st; do
    args=(); filt=""; i=0
    for p in $PS; do
      f="$SS/st_${part}_${st}_p${p}.png"
      [ -f "$f" ] || continue
      args+=(-i "$f")
      filt+="[$i:v]$CROP,drawtext=fontfile=run/arial.ttf:text='p ${p}':x=6:y=6:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.5[v$i];"
      i=$((i+1))
    done
    [ $i -gt 0 ] || continue
    ins=""; for ((k=0;k<i;k++)); do ins+="[v$k]"; done
    "$FF" -v error -y "${args[@]}" -filter_complex "${filt}${ins}hstack=inputs=$i" "$OUT/_${part}_${st}.png"
  done
  if [ -f "$OUT/_${part}_3d.png" ] && [ -f "$OUT/_${part}_st.png" ]; then
    "$FF" -v error -y -i "$OUT/_${part}_3d.png" -i "$OUT/_${part}_st.png" -filter_complex \
      "[0:v]pad=iw:ih+28:0:28:color=0x202020,drawtext=fontfile=run/arial.ttf:text='3D look':x=8:y=4:fontsize=20:fontcolor=white[a];[1:v]pad=iw:ih+28:0:28:color=0x202020,drawtext=fontfile=run/arial.ttf:text='Stepped look':x=8:y=4:fontsize=20:fontcolor=white[b];[a][b]vstack" \
      "$OUT/${part}_styles.png"
    rm -f "$OUT/_${part}_3d.png" "$OUT/_${part}_st.png"
  fi
done
# films: 3D and stepped side by side, at the capture rate (slowed 4x: one game tick per frame at 5 fps = real NBC-speed /4)
for part in arm torso; do
  a="$SS/st_film_${part}_3d_%03d.png"; b="$SS/st_film_${part}_st_%03d.png"
  if ls $SS/st_film_${part}_3d_000.png >/dev/null 2>&1; then
    "$FF" -v error -y -framerate 20 -i "$a" -framerate 20 -i "$b" -filter_complex \
      "[0:v]crop=640:620:320:50,drawtext=fontfile=run/arial.ttf:text='3D':x=10:y=10:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.5[l];[1:v]crop=640:620:320:50,drawtext=fontfile=run/arial.ttf:text='Stepped':x=10:y=10:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.5[r];[l][r]hstack,format=yuv420p" \
      -c:v libx264 -crf 20 -preset veryfast "$OUT/${part}_side_by_side_4x_slow.mp4"
    "$FF" -v error -y -framerate 80 -i "$a" -framerate 80 -i "$b" -filter_complex \
      "[0:v]crop=640:620:320:50[l];[1:v]crop=640:620:320:50[r];[l][r]hstack,format=yuv420p" \
      -c:v libx264 -crf 20 -preset veryfast "$OUT/${part}_side_by_side_real_speed.mp4"
    "$FF" -v error -y -framerate 20 -i "$b" -vf "select='not(mod(n\,5))',crop=640:620:320:50,scale=240:-1,tile=8x2" -frames:v 1 "$OUT/${part}_stepped_film_sheet.png"
  fi
done
ls "$OUT"
