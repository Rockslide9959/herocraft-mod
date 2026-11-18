// v0.17.5 Omnitrix code fix (round 4): OmnitrixCodeFixHarness (mode codefilm) output (run/omnicode_out) -> docs/screenshots/v0175/omnitrix_code_fix:
// the stills as they are, the film (the user's save: the watch put on over the Sentry, the code typed with real input -> CODE ACCEPTED,
// the flash) as an MP4 + frame sheet.
//   node scratchpad/omnitrix_code_fix_film_v0175.js   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'omnicode_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'omnitrix_code_fix');
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) {
	fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
const film = path.join(shots, 'film');
const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
const name = 'code_accepted_after_sentry_real_input';
const tmp = path.join(film, '_seq');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp);
frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
	'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
fs.rmSync(tmp, { recursive: true, force: true });
// the frame sheet: 20 frames across the whole clip (typing, the send, the flash), 4 a row, half size
const n = 20;
const pick = [];
for (let i = 0; i < n; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / (n - 1))]));
execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
console.log('wrote', fs.readdirSync(out).join(', '));
