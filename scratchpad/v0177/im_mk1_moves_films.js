// v0.17.7 Iron Man Mark 1 moveset: IronManMk1MovesHarness frames (run/screenshots/mk1_*) -> MP4s + frame sheets.
// node scratchpad/v0177/im_mk1_moves_films.js   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'screenshots', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'ironman_mk1');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const films = {};
for (const f of fs.readdirSync(shots)) {
	const m = /^mk1_(.+)_(\d{3})\.png$/.exec(f);
	if (m) {
		(films[m[1]] = films[m[1]] || []).push(f);
	}
}
function encode(list, mp4) {
	const tmp = path.join(shots, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	list.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
		'-pix_fmt', 'yuv420p', mp4]);
	fs.rmSync(tmp, { recursive: true, force: true });
}
function pick(list, n) {
	const step = Math.max(1, Math.floor(list.length / n));
	const r = [];
	for (let i = 0; i < list.length && r.length < n; i += step) r.push(path.join(shots, list[i]));
	return r;
}
for (const [name, list] of Object.entries(films).sort()) {
	list.sort();
	encode(list, path.join(out, name + '.mp4'));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.5', ...pick(list, 8)]);
	console.log(name, list.length);
}
console.log('done ->', out);
