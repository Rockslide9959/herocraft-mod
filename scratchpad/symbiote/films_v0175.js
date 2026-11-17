// v0.17.5 Symbiote suit: copy SymbioteSuitHarness stills into docs/screenshots/v0175/symbiote_suit/, build the before / after and
// presets sheets, and encode the films (suit flowing off + on, sheen + veins, moves in Toxin colours) as MP4 + frame sheets (never GIF).
//   node scratchpad/symbiote/films_v0175.js <after shots dir> [before shots dir]
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const PNG = require('../pnglib.js');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const shots = process.argv[2];
const before = process.argv[3];
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_suit');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
if (before) {
	for (const f of fs.readdirSync(before).filter((f) => f.endsWith('.png'))) fs.copyFileSync(path.join(before, f), path.join(out, f));
}

// before | after, one row per view
const views = ['01_front', '02_back', '03_side', '04_three_quarter', '05_face_closeup', '06_emblem_closeup', '07_back_emblem_closeup'];
const pairs = [];
for (const v of views) {
	const b = path.join(out, 'before_' + v + '.png'), a = path.join(out, 'after_' + v + '.png');
	if (fs.existsSync(b) && fs.existsSync(a)) pairs.push(b, a);
}
if (pairs.length) execFileSync('node', [sheet, path.join(out, 'compare_before_after.png'), '2', '0.5', ...pairs]);

// presets: fronts on top, backs below
const presets = ['classic', 'anti_venom', 'carnage', 'scream', 'agony', 'riot', 'toxin'];
function crop(file, x0, y0, w, h) {
	const im = PNG.read(file);
	const o = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) im.px.copy(o, y * w * 4, ((y0 + y) * im.W + x0) * 4, ((y0 + y) * im.W + x0 + w) * 4);
	return { W: w, H: h, px: o };
}
const tiles = [];
for (const side of ['front', 'back']) for (const p of presets) {
	const f = path.join(out, 'preset_' + p + '_' + side + '.png');
	if (fs.existsSync(f)) tiles.push(crop(f, 427 - 120, 20, 240, 440));
}
if (tiles.length) {
	const cols = presets.length, tw = tiles[0].W, th = tiles[0].H, gap = 4;
	const rows = Math.ceil(tiles.length / cols);
	const W = cols * tw + (cols - 1) * gap, H = rows * th + (rows - 1) * gap;
	const o = Buffer.alloc(W * H * 4, 255);
	tiles.forEach((t, i) => {
		const ox = (i % cols) * (tw + gap), oy = Math.floor(i / cols) * (th + gap);
		for (let y = 0; y < th; y++) t.px.copy(o, ((oy + y) * W + ox) * 4, y * tw * 4, (y + 1) * tw * 4);
	});
	PNG.write(path.join(out, 'presets_side_by_side.png'), W, H, o);
}

for (const [prefix, name, fps] of [['film_flow_', 'suit_flow_off_and_on', 20], ['film_sheen_', 'sheen_and_veins', 20], ['film_moves_', 'moves_toxin_tendril_spikes_blade', 20]]) {
	const film = files.filter((f) => f.startsWith(prefix));
	if (!film.length) {
		console.log('no frames for', name);
		continue;
	}
	const tmp = path.join(shots, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	const mp4 = path.join(out, name + '.mp4');
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
		'-pix_fmt', 'yuv420p', mp4]);
	fs.rmSync(tmp, { recursive: true, force: true });
	const step = Math.max(1, Math.floor(film.length / 16));
	const pick = [];
	for (let i = 0; i < film.length && pick.length < 16; i += step) pick.push(path.join(shots, film[i]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
	console.log(name, 'frames', film.length);
}
console.log('done ->', out);
