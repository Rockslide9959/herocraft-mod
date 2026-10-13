// v0.16.1: composes the Spider-Man harness shots (run/screenshots/v0161sm_*.png) into frame sheets, one row per view.
//   node scratchpad/compose_spidey_sheets.js <outDir> [scale]
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');

const SHOTS = path.join(__dirname, '..', 'run', 'screenshots');
const OUT = process.argv[2] || path.join(__dirname, 'shots_v0161', 'spiderman');
const SCALE = Number(process.argv[3] || 1); // 2 = half size
fs.mkdirSync(OUT, { recursive: true });
const GAP = 6;

function shrink(img, k) {
	if (k <= 1) return img;
	const w = Math.floor(img.w / k), h = Math.floor(img.h / k);
	const data = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		for (let c = 0; c < 4; c++) {
			let s = 0;
			for (let dy = 0; dy < k; dy++) for (let dx = 0; dx < k; dx++) s += img.data[((y * k + dy) * img.w + x * k + dx) * 4 + c];
			data[(y * w + x) * 4 + c] = Math.round(s / (k * k));
		}
	}
	return { w, h, data };
}

function sheet(outName, rows) {
	const imgs = rows.map(r => r.map(f => {
		const p = path.join(SHOTS, f);
		return fs.existsSync(p) ? shrink(png.read(p), SCALE) : null;
	}));
	const any = imgs.flat().find(Boolean);
	if (!any) { console.log('skip', outName); return; }
	const fw = any.w, fh = any.h;
	const cols = Math.max(...rows.map(r => r.length));
	const W = cols * fw + (cols + 1) * GAP, H = rows.length * fh + (rows.length + 1) * GAP;
	const data = Buffer.alloc(W * H * 4);
	for (let i = 0; i < W * H; i++) { data[i * 4] = 24; data[i * 4 + 1] = 24; data[i * 4 + 2] = 28; data[i * 4 + 3] = 255; }
	imgs.forEach((row, ri) => row.forEach((img, ci) => {
		if (!img) return;
		const ox = GAP + ci * (fw + GAP), oy = GAP + ri * (fh + GAP);
		for (let y = 0; y < Math.min(fh, img.h); y++) {
			img.data.copy(data, ((oy + y) * W + ox) * 4, y * img.w * 4, y * img.w * 4 + Math.min(fw, img.w) * 4);
		}
	}));
	png.write(path.join(OUT, outName), { w: W, h: H, data });
	console.log('wrote', outName, W + 'x' + H);
}

const frames = (seq, n) => Array.from({ length: n }, (_, i) => `v0161sm_${seq}_${i}.png`);
const moves = { strike: 7, zipkick: 6, uppercut: 5, whip: 6, hurl: 6, thwip: 4, landing: 5, swingthrow: 4, swingthrowrel: 4 };
for (const [m, n] of Object.entries(moves)) sheet(`spidey_${m}_sheet.png`, ['q', 's'].map(v => frames(`${m}_${v}`, n)));
sheet('spidey_swing_sheet.png', [frames('swing_side', 5), frames('release_side', 5), frames('swing_back', 5)]);
sheet('spidey_tightrope_sheet.png', [['v0161sm_rope_side.png', 'v0161sm_rope_front.png', 'v0161sm_rope_hang.png'], frames('rope_walk', 4)]);
sheet('spidey_perch_sheet.png', [frames('perch_s', 5), frames('perch_q', 5), frames('perch_f', 5)]);
for (const s of ['weave_wide', 'weave_wide2', 'weave_standing', 'weave_standing_below', 'wall', 'wall_front', 'hud', 'hud_weave_alarm']) {
	const p = path.join(SHOTS, `v0161sm_${s}.png`);
	if (fs.existsSync(p)) fs.copyFileSync(p, path.join(OUT, `spidey_${s}.png`));
}
