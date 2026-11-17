// v0.17.5 Symbiote Lethal Protector: copy SymbioteLethalHarness stills into docs/screenshots/v0175/symbiote_lethal/, build the
// Classic-vs-Lethal and face sheets, and encode the films (idle mouth, snarl + tongue lash on attack, suit flowing on) as MP4 + frame
// sheets (never GIF).
//   node scratchpad/symbiote/films_lethal_v0175.js <shots dir>
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const shots = process.argv[2];
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_lethal');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));

const have = (n) => fs.existsSync(path.join(out, n + '.png'));
const pairs = [['classic_01_front', 'lethal_01_front'], ['classic_face', 'face_01_closed'], ['classic_face_three_quarter', 'face_02_closed_three_quarter']];
const sbs = [];
for (const [a, b] of pairs) if (have(a) && have(b)) sbs.push(path.join(out, a + '.png'), path.join(out, b + '.png'));
if (sbs.length) execFileSync('node', [sheet, path.join(out, 'classic_vs_lethal.png'), '2', '0.5', ...sbs]);
const faces = files.filter((f) => f.startsWith('face_')).map((f) => path.join(out, f));
if (faces.length) execFileSync('node', [sheet, path.join(out, 'face_sheet.png'), '3', '0.4', ...faces]);

for (const [prefix, name] of [['film_idle_', 'mouth_idle_closeup'], ['film_attack_', 'snarl_and_tongue_lash_on_attack'], ['film_flow_', 'suit_flow_off_and_on']]) {
	const film = files.filter((f) => f.startsWith(prefix));
	if (!film.length) {
		console.log('no frames for', name);
		continue;
	}
	const tmp = path.join(shots, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
		'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	fs.rmSync(tmp, { recursive: true, force: true });
	const step = Math.max(1, Math.floor(film.length / 16));
	const pick = [];
	for (let i = 0; i < film.length && pick.length < 16; i += step) pick.push(path.join(shots, film[i]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
	console.log(name, 'frames', film.length);
}
console.log('done ->', out);
