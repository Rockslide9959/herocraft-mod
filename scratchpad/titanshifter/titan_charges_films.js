// v0.17.5 Titan charges: TitanChargesHarness output (run/titancharges_out/screenshots) -> docs/screenshots/v0175/titan_charges:
// every still as it is, every film as an MP4 (never a GIF) + a 16-frame sheet.
//   node scratchpad/titanshifter/titan_charges_films.js      (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'titancharges_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'titan_charges');
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter((f) => f.endsWith('.png'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
for (const dir of fs.readdirSync(shots).filter((d) => d.startsWith('film_'))) {
	const film = path.join(shots, dir);
	const frames = fs.readdirSync(film).filter((f) => /^f\d{3}\.png$/.test(f)).sort();
	if (frames.length < 4) continue;
	const name = dir.replace(/^film_/, '');
	const pick = [];
	for (let i = 0; i < 16; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / 15)]));
	execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
	const tmp = path.join(film, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '4M',
		'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	fs.rmSync(tmp, { recursive: true, force: true });
	console.log(name, frames.length, 'frames');
}
console.log(fs.readdirSync(out).length, 'files in', path.relative(root, out));
