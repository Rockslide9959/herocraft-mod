// v0.16.1: composes the Wolverine frame-strip harness shots (run/screenshots/v0161s_<seq>_<view>_<i>.png) into sheets:
// one row per view (3/4 front, side, first person), one column per beat, full-size frames, 6 px dark gutters.
//   node scratchpad/compose_wolv_sheets.js <outDir>
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');

const SHOTS = path.join(__dirname, '..', 'run', 'screenshots');
const OUT = process.argv[2] || path.join(__dirname, 'shots_v0161', 'wolverine');
fs.mkdirSync(OUT, { recursive: true });
const GAP = 6;

function sheet(outName, rows) {
	const imgs = rows.map(r => r.map(f => {
		const p = path.join(SHOTS, f);
		return fs.existsSync(p) ? png.read(p) : null;
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

const frames = (seq, view, n = 5) => Array.from({ length: n }, (_, i) => `v0161s_${seq}_${view}_${i}.png`);
const views = ['q', 's', 'f'];
const which = process.argv.slice(3);
const want = (k) => which.length === 0 || which.includes(k);
if (want('m1')) {
	for (let k = 1; k <= 3; k++) sheet(`wolv_m1_strike${k}_sheet.png`, views.map(v => frames(`m1_s${k}`, v)));
}
if (want('deploy')) {
	sheet('wolv_deploy_sheet_v2.png', views.map(v => frames('deploy', v, 6)));
}
// abilities, 3/4 view: one row each -- R sweep, G cross slash, V frenzy, Z charge, Z strike, C roar
if (want('abil')) sheet('wolv_abilities_sheet.png', ['abil_R', 'abil_G', 'abil_V', 'abil_Zcharge', 'abil_Z', 'abil_C'].map(n => Array.from({ length: 5 }, (_, i) => 'v0161s_' + n + '_' + i + '.png')));
if (want('trail')) {
	// before (v0.16.1 first pass, the sheet trail) / after (thin arcs)
	const before = path.join(OUT, 'v0161w_10_fp_combo1_4.png');
	if (fs.existsSync(before)) fs.copyFileSync(before, path.join(SHOTS, 'v0161s_trailbefore_f_0.png'));
	sheet('wolv_trail_before_after.png', [['v0161s_trailbefore_f_0.png', 'v0161s_m1_s1_f_1.png', 'v0161s_m1_s1_f_2.png']]);
}
