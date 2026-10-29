#!/usr/bin/env bash
# v0.17.1 (user: "make the drill construct in the mechs sound effect sound better and different, right now its a really loud
# tring sound effect"): the Giga Drill's three sounds, synthesised -- no whine, no chime. A low motor (a 62 Hz sawtooth and its
# octave) whirring as the spiral turns (amplitude rolling at 16 Hz), a gritty rattle on top, all low-passed so nothing rings.
#   spin   0.40 s  the motor whirr (replayed every 6 ticks, pitch 0.8 -> 1.25 as it spins up)
#   grind  0.40 s  the same motor bogged down, heavy crunchy grit (biting rock)
#   charge 1.10 s  let go: the motor revs up hard into a rushing roar
# Usage: bash make_drill_sfx.sh   (writes into src/main/resources/assets/projecthero/sounds/green_lantern)
set -e
FF=/c/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe
OUT="$(cd "$(dirname "$0")/../../.." && pwd)/src/main/resources/assets/projecthero/sounds/green_lantern"
enc() { "$FF" -y -loglevel error -f lavfi -i "$1" -af "$2" -ac 1 -ar 44100 -c:a libvorbis -q:a 5 "$OUT/$3.ogg"; }

SPIN="aevalsrc=exprs='0.42*(2*mod(62*t\,1)-1)*(0.62+0.38*sin(2*PI*16*t))+0.22*sin(2*PI*124*t+0.6)*(0.7+0.3*sin(2*PI*16*t+1.2))+0.16*(2*random(0)-1)*(0.55+0.45*sin(2*PI*32*t))':s=44100:d=0.40"
enc "$SPIN" "lowpass=f=1800,highpass=f=40,acompressor=threshold=-18dB:ratio=3,volume=0.9,afade=t=in:d=0.03,afade=t=out:st=0.33:d=0.07,alimiter=limit=0.6" mech_drill_spin

GRIND="aevalsrc=exprs='0.34*(2*mod(48*t\,1)-1)*(0.6+0.4*sin(2*PI*11*t))+0.38*(2*random(0)-1)*(0.35+0.65*abs(sin(2*PI*23*t)))+0.12*(2*random(1)-1)*gt(random(2)\,0.93)':s=44100:d=0.40"
enc "$GRIND" "lowpass=f=2600,highpass=f=50,acompressor=threshold=-18dB:ratio=3,volume=0.8,afade=t=in:d=0.02,afade=t=out:st=0.32:d=0.08,alimiter=limit=0.6" mech_drill_grind

# the rev: frequency sweeping 45 -> 120 Hz (phase = integral of f), the whirr speeding with it, a noise rush swelling in
CHARGE="aevalsrc=exprs='0.4*(2*mod(45*t+34*t*t\,1)-1)*(0.6+0.4*sin(2*PI*(14*t+14*t*t)))+0.18*sin(2*PI*(90*t+68*t*t))+0.3*(2*random(0)-1)*min(1\,t*1.2)':s=44100:d=1.10"
enc "$CHARGE" "lowpass=f=2400,highpass=f=40,acompressor=threshold=-18dB:ratio=3,volume=1.0,afade=t=in:d=0.04,afade=t=out:st=0.85:d=0.25,alimiter=limit=0.7" mech_drill_charge
echo "drill sfx written to $OUT"
