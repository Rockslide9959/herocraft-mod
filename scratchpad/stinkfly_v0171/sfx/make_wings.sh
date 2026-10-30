#!/usr/bin/env bash
# v0.17.1 (user: "Give his wings insect wing flapping sound effects"): Stinkfly's wing buzz, synthesised -- airy noise chopped by
# a 35 Hz wingbeat (each beat a soft "fff"), a 175 Hz hum riding the same beat, band-passed to an insect's range.
#   stinkfly_wings      0.85 s  the flying loop (replayed every 16 ticks; beat and hum both whole cycles, so it joins cleanly)
#   stinkfly_wings_up   0.45 s  take-off: a few hard strokes winding up into the buzz
set -e
FF=/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe
OUT="$(cd "$(dirname "$0")/../../.." && pwd)/src/main/resources/assets/projecthero/sounds/omnitrix"
enc() { "$FF" -y -loglevel error -f lavfi -i "$1" -af "$2" -ac 1 -ar 44100 -c:a libvorbis -q:a 5 "$OUT/$3.ogg"; }
LOOP="aevalsrc=exprs='0.55*(2*random(0)-1)*pow(0.5+0.5*sin(2*PI*35*t)\,3)+0.22*sin(2*PI*175*t)*(0.55+0.45*sin(2*PI*35*t))+0.08*sin(2*PI*350*t)':s=44100:d=0.85"
enc "$LOOP" "highpass=f=140,lowpass=f=1500,acompressor=threshold=-16dB:ratio=3,volume=1.1,afade=t=in:d=0.04,afade=t=out:st=0.79:d=0.06,alimiter=limit=0.7" stinkfly_wings
UP="aevalsrc=exprs='0.7*(2*random(0)-1)*pow(0.5+0.5*sin(2*PI*(12*t+26*t*t))\,3)*min(1\,0.4+t*2)+0.25*sin(2*PI*(90*t+90*t*t))':s=44100:d=0.45"
enc "$UP" "highpass=f=120,lowpass=f=1600,acompressor=threshold=-16dB:ratio=3,volume=1.1,afade=t=in:d=0.02,afade=t=out:st=0.37:d=0.08,alimiter=limit=0.75" stinkfly_wings_up
echo "wing sfx written to $OUT"
