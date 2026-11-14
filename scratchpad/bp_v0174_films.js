// v0.17.4 Black Panther: encode BlackPantherHarness frames (run/screenshots/bp_*) into MP4s + frame sheets.
// node scratchpad/bp_v0174_films.js   (from the checkout root)
// Per move: <move>.mp4 + <move>_sheet.png (8 frames). Per kit: unsuited_moveset.mp4, suited_moveset.mp4, first_person.mp4 (the
// moves back to back) + a sheet with 2 frames of each move. Stills are copied as they are.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0174', 'black_panther');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter(f => f.startsWith('bp_'));
for (const f of files.filter(f => f.startsWith('bp_still_'))) {
	fs.copyFileSync(path.join(shots, f), path.join(out, f.replace('bp_still_', '')));
}
const moves = {};
for (const f of files) {
	const m = /^bp_(.+)_(\d{3})\.png$/.exec(f);
	if (m && !f.startsWith('bp_still_')) {
		(moves[m[1]] = moves[m[1]] || []).push(f);
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
const kits = { unsuited_moveset: [], suited_moveset: [], first_person: [] };
const kitSheets = { unsuited_moveset: [], suited_moveset: [], first_person: [] };
for (const [name, list] of Object.entries(moves).sort()) {
	list.sort();
	encode(list, path.join(out, name + '.mp4'));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.5', ...pick(list, 8)]);
	const kit = name.startsWith('fp_') ? 'first_person' : name.startsWith('u_') ? 'unsuited_moveset' : 'suited_moveset';
	kits[kit].push(...list);
	const s = pick(list.slice(Math.floor(list.length * 0.2)), 2);
	kitSheets[kit].push(...s);
	console.log(name, list.length);
}
for (const [kit, list] of Object.entries(kits)) {
	if (list.length) {
		encode(list, path.join(out, kit + '.mp4'));
		execFileSync('node', [sheet, path.join(out, kit + '_sheet.png'), '6', '0.34', ...kitSheets[kit]]);
	}
}
console.log('done ->', out);
