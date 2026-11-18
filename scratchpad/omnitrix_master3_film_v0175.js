// v0.17.5 Omnitrix Master Control round 3: OmnitrixMasterHarness (mode round3) output (run/omnimaster_out) -> docs/screenshots/v0175/omnitrix_master3:
// the stills as they are, the two films (first person: the code typed -> white / black flash -> dial run -> Master Control on; third
// person: the code again -> the flash on the other phase -> off) as MP4s + frame sheets.
//   node scratchpad/omnitrix_master3_film_v0175.js   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'omnimaster_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'omnitrix_master3');
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) {
	fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
for (const [dir, name] of [['film', 'unlock_flash_hourglass_first_person'], ['film3', 'unlock_flash_hourglass_third_person']]) {
	const film = path.join(shots, dir);
	if (!fs.existsSync(film)) continue;
	const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
	if (!frames.length) continue;
	const tmp = path.join(film, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
		'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	fs.rmSync(tmp, { recursive: true, force: true });
	// the frame sheet: 20 frames, across the clip (the flash, then the dial run starting), 4 a row, half size
	const n = 20;
	const from = Math.min(8, frames.length - 1), to = Math.min(frames.length - 1, 58); // the 40-tick flash (+ the first dial steps)
	const pick = [];
	for (let i = 0; i < n; i++) pick.push(path.join(film, frames[Math.min(frames.length - 1, from + Math.round(i * (to - from) / (n - 1)))]));
	execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
}
console.log('wrote', fs.readdirSync(out).join(', '));
