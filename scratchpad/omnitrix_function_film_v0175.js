// v0.17.5 Omnitrix function mode: OmnitrixFunctionHarness output (run/omnifunc_out/screenshots) -> docs/screenshots/v0175/omnitrix_function:
// the stills as they are, the film (prime -> Shift+right-click -> the code typed -> Master Control unlocking) as an MP4 + a frame sheet.
//   node scratchpad/omnitrix_function_film_v0175.js   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'omnifunc_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'omnitrix_function');
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) {
	fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
const film = path.join(shots, 'film');
const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
const tmp = path.join(film, '_seq');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp);
frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
const mp4 = path.join(out, 'prime_function_code_master_control.mp4');
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
	'-pix_fmt', 'yuv420p', mp4]);
fs.rmSync(tmp, { recursive: true, force: true });
// the frame sheet: 16 frames across the film, 4 a row, half size
const n = 16;
const pick = [];
for (let i = 0; i < n; i++) pick.push(path.join(film, frames[Math.min(frames.length - 1, Math.round(i * (frames.length - 1) / (n - 1)))]));
execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, 'prime_function_code_master_control_sheet.png'), '4', '0.5', ...pick]);
console.log('wrote', fs.readdirSync(out).join(', '));
