// Turns run/screenshots/sp_<scene>_NNN.png into docs/screenshots/v0173/star_platinum_v3/<scene>.mp4 (+ a frame sheet in
// scratchpad/stand/sheets for checking). Run from the repo root. Args: scene names (default: every scene found).
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const src = 'run/screenshots';
const out = 'docs/screenshots/v0173/star_platinum_v3';
const sheets = 'scratchpad/stand/sheets';
fs.mkdirSync(out, { recursive: true });
fs.mkdirSync(sheets, { recursive: true });
const files = fs.readdirSync(src).filter(f => /^sp_.*_\d{3}\.png$/.test(f));
const scenes = [...new Set(files.map(f => f.replace(/^sp_(.*)_\d{3}\.png$/, '$1')))];
const want = process.argv.slice(2);
for (const s of scenes) {
  if (want.length && !want.includes(s)) continue;
  const n = files.filter(f => f.startsWith('sp_' + s + '_') && /^\d{3}$/.test(f.slice(('sp_' + s + '_').length, -4))).length;
  const input = path.join(src, 'sp_' + s + '_%03d.png');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', input, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '23',
    '-vf', 'scale=640:-2', path.join(out, s + '.mp4')]);
  const step = Math.max(1, Math.floor(n / 16));
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', input, '-vf',
    `select='not(mod(n\\,${step}))',scale=400:-1,tile=4x4`, '-frames:v', '1', path.join(sheets, s + '.png')]);
  console.log(s, n, 'frames');
}
