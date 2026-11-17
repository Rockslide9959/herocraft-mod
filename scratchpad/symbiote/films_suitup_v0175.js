// v0.17.5 round 2 Symbiote suit-up: copy SymbioteSuitUpHarness stills into docs/screenshots/v0175/symbiote_suitup/, build the
// before / after sheets (shoulder pads gone, the mouth with no green -- "before" = the v0.17.5 symbiote_suit / symbiote_lethal shots),
// and encode every film as MP4 + a frame sheet (never GIF).
//   node scratchpad/symbiote/films_suitup_v0175.js <shots dir>
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const shots = process.argv[2];
const v0175 = path.join(root, 'docs', 'screenshots', 'v0175');
const out = path.join(v0175, 'symbiote_suitup');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => f.startsWith('after_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));

const ex = (f) => fs.existsSync(f);
function pairs(name, list) {
	const args = [];
	for (const [a, b] of list) if (ex(a) && ex(b)) args.push(a, b);
	if (args.length) {
		execFileSync('node', [sheet, path.join(out, name), '2', '0.5', ...args]);
		console.log('sheet', name);
	}
}
pairs('shoulder_pads_before_after.png', [
	[path.join(v0175, 'symbiote_suit', 'after_01_front.png'), path.join(out, 'after_classic_front.png')],
	[path.join(v0175, 'symbiote_suit', 'after_04_three_quarter.png'), path.join(out, 'after_classic_three_quarter.png')],
	[path.join(v0175, 'symbiote_lethal', 'lethal_01_front.png'), path.join(out, 'after_lethal_front.png')],
]);
pairs('mouth_no_green_before_after.png', [
	[path.join(v0175, 'symbiote_lethal', 'face_01_closed.png'), path.join(out, 'after_lethal_face_closed.png')],
	[path.join(v0175, 'symbiote_lethal', 'face_04_open_tongue_lolling.png'), path.join(out, 'after_lethal_face_tongue_out.png')],
	[path.join(v0175, 'symbiote_lethal', 'face_05_lolling_three_quarter.png'), path.join(out, 'after_lethal_face_tongue_out_three_quarter.png')],
]);

function film(prefix, name, fps, sheetFrames) {
	const list = files.filter((f) => f.startsWith(prefix) && /^\d+\.png$/.test(f.slice(prefix.length)));
	if (!list.length) {
		console.log('no frames for', name);
		return;
	}
	const tmp = path.join(shots, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	list.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	fs.rmSync(tmp, { recursive: true, force: true });
	const n = sheetFrames || 16;
	const pick = [];
	for (let k = 0; k < n; k++) pick.push(path.join(shots, list[Math.min(list.length - 1, Math.round(k * (list.length - 1) / (n - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
	console.log(name, 'frames', list.length);
}
for (const look of ['classic', 'lethal', 'custom', 'first_person']) {
	film('film_' + look + '_on_', look + '_suit_up', 20);
	film('film_' + look + '_off_', look + '_take_off', 20);
}
film('slow_edge_', 'slow_motion_leading_edge', 30, 20);
film('slow_face_', 'slow_motion_face_eyes_and_jaw', 30, 20);
film('slow_face_off_', 'slow_motion_face_coming_off', 30, 12);
console.log('done ->', out);
