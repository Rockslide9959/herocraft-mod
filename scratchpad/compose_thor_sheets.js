// v0.16.1: composes the Thor frame-strip harness shots (run/screenshots/v0161t_<seq>_<view>_<i>.png) into sheets:
// one row per view, one column per beat, half-size frames, 6 px dark gutters.
//   node scratchpad/compose_thor_sheets.js <outDir> [dual|twin|storm|field ...]
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');

const SHOTS = path.join(__dirname, '..', 'run', 'screenshots');
const OUT = process.argv[2] || path.join(__dirname, 'shots_v0161', 'thor');
fs.mkdirSync(OUT, { recursive: true });
const GAP = 6;
const SCALE = 2; // downsample factor

function shrink(img) {
	const w = Math.floor(img.w / SCALE), h = Math.floor(img.h / SCALE);
	const data = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		for (let c = 0; c < 4; c++) {
			let s = 0;
			for (let dy = 0; dy < SCALE; dy++) for (let dx = 0; dx < SCALE; dx++) {
				s += img.data[((y * SCALE + dy) * img.w + (x * SCALE + dx)) * 4 + c];
			}
			data[(y * w + x) * 4 + c] = Math.round(s / (SCALE * SCALE));
		}
	}
	return { w, h, data };
}

function sheet(outName, rows) {
	const imgs = rows.map(r => r.map(f => {
		const p = path.join(SHOTS, f);
		return fs.existsSync(p) ? shrink(png.read(p)) : null;
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

const frames = (seq, view, n) => Array.from({ length: n }, (_, i) => `v0161t_${seq}_${view}_${i}.png`);
const which = process.argv.slice(3);
const want = (k) => which.length === 0 || which.includes(k);
if (want('dual')) {
	for (let k = 1; k <= 4; k++) sheet(`thor_dual_hit${k}_sheet.png`, ['q', 's', 'f'].map(v => frames('dual' + k, v, 6)));
}
if (want('twin')) sheet('thor_twin_thunder_sheet.png', ['q', 'w', 'f'].map(v => frames('twin', v, 8)));
if (want('storm')) sheet('thor_storm_charged_sheet.png', ['q', 'f'].map(v => frames('storm', v, 4)));
if (want('field')) sheet('thor_thunder_field_sheet.png', ['fw', 'fc'].map(v => frames('field', v, 6)));
