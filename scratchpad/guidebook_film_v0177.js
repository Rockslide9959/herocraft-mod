// v0.17.7 Guidebook remake: GuideHarness output (run/guide_out) -> docs/screenshots/v0177/guidebook:
// the stills, and film_book as an MP4 + a 16-frame sheet (never a GIF).
//   node scratchpad/guidebook_film_v0177.js      (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'guide_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'guidebook');
const CS = path.join(root, 'scratchpad', 'contact_sheet.js');
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
const film = path.join(root, 'run', 'guide_out', 'film_book', 'screenshots');
// keep only complete PNGs (ending in IEND)
const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)
	&& fs.readFileSync(path.join(film, f)).subarray(-8, -4).toString() === 'IEND').sort();
const pick = [];
for (let i = 0; i < 16; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / 15)]));
execFileSync('node', [CS, path.join(out, 'guidebook_clickthrough_sheet.png'), '4', '0.5', ...pick]);
const tmp = path.join(film, '_seq');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp);
frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
// one frame every 2 ticks = 10 fps of game time
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '10', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=960:-2', '-c:v', 'h264_mf', '-b:v', '4M',
	'-pix_fmt', 'yuv420p', path.join(out, 'guidebook_clickthrough.mp4')]);
fs.rmSync(tmp, { recursive: true, force: true });
console.log('done', out, frames.length, 'frames');
