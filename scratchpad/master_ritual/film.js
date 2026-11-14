// v0.17.4: run/screenshots/rit_<mode>_NNNN.png -> docs/screenshots/v0174/master_control/ritual_<mode>.mp4 + ritual_<mode>_sheet.jpg
//   node scratchpad/master_ritual/film.js <mode> [--every N]   (sheet: every Nth frame, default 10, labelled with the tick)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const SHOTS = path.join(ROOT, 'run/screenshots');
const OUT = path.join(ROOT, 'docs/screenshots/v0174/master_control');
const FONT = "fontfile='C\\:/Windows/Fonts/arialbd.ttf'";
const args = process.argv.slice(2);
const mode = args[0] || 'success';
const ei = args.indexOf('--every');
const every = ei >= 0 ? +args[ei + 1] : 10;
fs.mkdirSync(OUT, { recursive: true });
const re = new RegExp('^rit_' + mode + '_(\\d{4})\\.png$');
const n = fs.readdirSync(SHOTS).map((f) => (f.match(re) || [])[1]).filter(Boolean).map(Number).sort((a, b) => a - b);
if (!n.length) throw new Error('no frames');
let last = n[0];
for (const v of n) { if (v > last + 1) break; last = v; }
const count = last - n[0] + 1;
const input = path.join(SHOTS, `rit_${mode}_%04d.png`);
const ff = (a) => execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...a], { stdio: ['ignore', 'inherit', 'inherit'] });
const mp4 = path.join(OUT, `ritual_${mode}.mp4`);
ff(['-framerate', '20', '-start_number', String(n[0]), '-i', input, '-frames:v', String(count),
  '-vf', `scale=960:-2,drawtext=${FONT}:text='t=%{eif\\:n\\:d}':x=10:y=10:fontsize=20:fontcolor=white:borderw=2:bordercolor=black`,
  '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-preset', 'veryfast', '-r', '20', mp4]);
const tiles = Math.ceil(count / every);
const cols = 6, rows = Math.ceil(tiles / cols);
const sheet = path.join(OUT, `ritual_${mode}_sheet.jpg`);
ff(['-framerate', '20', '-start_number', String(n[0]), '-i', input, '-frames:v', String(count),
  '-vf', `select='not(mod(n\\,${every}))',scale=320:-2,drawtext=${FONT}:text='t=%{eif\\:n*${every}\\:d}':x=6:y=6:fontsize=18:fontcolor=white:borderw=2:bordercolor=black,tile=${cols}x${rows}`,
  '-frames:v', '1', '-q:v', '3', sheet]);
console.log('wrote', mp4, sheet, count, 'frames');
