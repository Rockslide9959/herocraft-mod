#!/bin/sh
# SSJ hair preview extras: the reference sketch, front-vs-sketch comparison and the extra views. Run from the worktree root
# after ssj_hair_films.sh.
FF="/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe"
D="$PWD/docs/screenshots/v0173/ssj_hair_preview"
node scratchpad/saiyan/ssj_reference_sketch.js "$D/reference_sketch_front.png"
cd run/screenshots || exit 1
LBL="fontfile=arial.ttf:x=8:y=8:fontsize=22:fontcolor=white:box=1:boxcolor=black@0.55"
"$FF" -hide_banner -loglevel error -y -i sh_still_front.png -i "$D/reference_sketch_front.png" -filter_complex \
	"[0]crop=ih:ih,drawtext=$LBL:text='in game (front)'[a];[1]drawtext=$LBL:text='reference silhouette sketch'[b];[a][b]hstack" \
	-frames:v 1 "$D/compare_front_vs_reference.png"
"$FF" -hide_banner -loglevel error -y -i sh_still_high.png -i sh_still_body.png -i sh_still_front_nohair.png -filter_complex \
	"[0]crop=ih:ih,drawtext=$LBL:text='from above'[a];[1]crop=ih:ih,drawtext=$LBL:text='full body'[b];[2]crop=ih:ih,drawtext=$LBL:text='without (plain Steve)'[c];[a][b][c]hstack=3" \
	-frames:v 1 "$D/extra_views.png"
ls -la "$D"
