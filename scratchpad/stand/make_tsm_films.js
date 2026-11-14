// v0.17.4: run/screenshots/tsm_<scene>_NNN.png -> docs/screenshots/v0174/star_platinum/<scene>.mp4 + <scene>_sheet.png.
// Run from the repo root after the TimeStopMobilityHarness client (scratchpad/stand/tsmob_hook.js).
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const src = 'run/screenshots';
const out = 'docs/screenshots/v0174/star_platinum';
fs.mkdirSync(out, { recursive: true });
const files = fs.readdirSync(src).filter(f => /^tsm_.*_\d{3}\.png$/.test(f));
const scenes = [...new Set(files.map(f => f.replace(/^tsm_(.*)_\d{3}\.png$/, '$1')))];
for (const s of scenes) {
  const n = files.filter(f => f.startsWith('tsm_' + s + '_')).length;
  const input = path.join(src, 'tsm_' + s + '_%03d.png');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', input, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '23',
    '-vf', 'scale=854:-2', path.join(out, s + '.mp4')]);
  const step = Math.max(1, Math.floor(n / 16));
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', input, '-vf',
    `select='not(mod(n\\,${step}))',scale=400:-1,tile=4x4`, '-frames:v', '1', path.join(out, s + '_sheet.png')]);
  console.log(s, n, 'frames');
}
