// v0.17.5 Symbiote as a Secondary power: flattens SymbioteSecondaryHarness output (docs/screenshots/v0175/symbiote_secondary/screenshots)
// and encodes the film as MP4 + a frame sheet (never GIF); the film frames themselves are deleted afterwards.
//   node scratchpad/symbiote/films_secondary_v0175.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_secondary');
const shots = path.join(out, 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
const list = files.filter((f) => f.startsWith('film_secondary_'));
const tmp = path.join(shots, '_seq');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp);
list.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
	'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, 'sentry_shift_h_symbiote_suit_up_and_back.mp4')]);
const n = 16, pick = [];
for (let k = 0; k < n; k++) pick.push(path.join(shots, list[Math.min(list.length - 1, Math.round(k * (list.length - 1) / (n - 1)))]));
execFileSync('node', [sheet, path.join(out, 'sentry_shift_h_symbiote_suit_up_and_back_sheet.png'), '4', '0.4', ...pick]);
fs.rmSync(shots, { recursive: true, force: true });
console.log('film frames', list.length);
