#!/usr/bin/env bash
# TEMP: films and contact sheets for the NBC-style regeneration (docs/screenshots/v0173/andy_nbc_regen).
set -e
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
SS=run/screenshots
OUT=docs/screenshots/v0173/andy_nbc_regen
FONT=run/arial.ttf
mkdir -p "$OUT"
# from a head: the server ran at 5 tps (a quarter speed) and every client tick is a frame: 20 fps = quarter speed, 80 fps = real
for st in 3d st; do
  in="$SS/nbc_head_${st}_%03d.png"
  "$FF" -v error -y -framerate 5 -i "$in" -vf "crop=480:300:187:110,scale=960:600:flags=neighbor,format=yuv420p" -c:v libx264 -crf 20 -preset veryfast "$OUT/from_head_${st}_quarter_speed.mp4"
  "$FF" -v error -y -framerate 20 -i "$in" -vf "crop=480:300:187:110,scale=960:600:flags=neighbor,format=yuv420p" -c:v libx264 -crf 20 -preset veryfast "$OUT/from_head_${st}_real_speed.mp4"
  "$FF" -v error -y -framerate 20 -i "$in" -vf "select='between(n\,0\,47)*not(mod(n\,2))',crop=480:300:187:110,scale=300:-1,drawtext=fontfile=$FONT:text='%{n}':x=4:y=4:fontsize=14:fontcolor=white,tile=6x4" -frames:v 1 "$OUT/from_head_${st}_sheet.png"
done
"$FF" -v error -y -framerate 5 -i "$SS/nbc_head_3d_%03d.png" -framerate 5 -i "$SS/nbc_head_st_%03d.png" -filter_complex \
  "[0:v]crop=480:300:187:110,scale=640:400:flags=neighbor,drawtext=fontfile=$FONT:text='3D look':x=10:y=10:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.5[l];[1:v]crop=480:300:187:110,scale=640:400:flags=neighbor,drawtext=fontfile=$FONT:text='Stepped look':x=10:y=10:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.5[r];[l][r]hstack,format=yuv420p" \
  -c:v libx264 -crf 20 -preset veryfast "$OUT/from_head_both_looks_quarter_speed.mp4"
# the hand segment, stills
for st in 3d st; do
  args=(); filt=""; i=0
  for p in 0 25 50 67 84 92; do
    f="$SS/nbc_hand_${st}_p${p}.png"; [ -f "$f" ] || continue
    args+=(-i "$f")
    filt+="[$i:v]crop=360:360:247:60,scale=260:260:flags=neighbor,drawtext=fontfile=$FONT:text='hand p ${p}':x=6:y=6:fontsize=16:fontcolor=white:box=1:boxcolor=black@0.5[v$i];"
    i=$((i+1))
  done
  ins=""; for ((k=0;k<i;k++)); do ins+="[v$k]"; done
  "$FF" -v error -y "${args[@]}" -filter_complex "${filt}${ins}hstack=inputs=$i" "$OUT/_hand_${st}.png"
done
"$FF" -v error -y -i "$OUT/_hand_3d.png" -i "$OUT/_hand_st.png" -filter_complex "[0:v][1:v]vstack" "$OUT/hand_segment_both_looks.png"
rm -f "$OUT/_hand_3d.png" "$OUT/_hand_st.png"
# the armour
args=(); filt=""; i=0
for s in armour_1_on armour_2_arm_lost armour_3_arm_back_pad_gone armour_4_leg_lost armour_5_torso_lost; do
  args+=(-i "$SS/nbc_${s}.png")
  filt+="[$i:v]crop=560:480:147:0,scale=350:300,drawtext=fontfile=$FONT:text='${s#armour_}':x=6:y=6:fontsize=16:fontcolor=white:box=1:boxcolor=black@0.5[v$i];"
  i=$((i+1))
done
ins=""; for ((k=0;k<i;k++)); do ins+="[v$k]"; done
"$FF" -v error -y "${args[@]}" -filter_complex "${filt}${ins}hstack=inputs=$i" "$OUT/armour_falls_off.png"
"$FF" -v error -y -framerate 20 -i "$SS/nbc_armour_regrow_%03d.png" -vf "format=yuv420p" -c:v libx264 -crf 20 -preset veryfast "$OUT/armour_regrow_real_speed.mp4"
ls "$OUT"
