// v0.16.1 Supervillain bosses: old-vs-new comparison sheets from the client harness screenshots (svm_old_* taken on the
// unmodified code, svm_new_* with the new GeckoLib models), plus attack-frame sheets.
// Usage: node scratchpad/compare_supervillain.js <screenshotsDir>
// Writes <main scratchpad>/shots_v0161/supervillain_models/compare_<boss>.png and attacks_<boss>.png, and copies the shots.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { decodePng } = require('./lib_geckogen_anim.js');
const G = require('./gen_supervillain_models.js');

const SHOTS = process.argv[2];
const OUT = G.SHOTS_DIR;
fs.mkdirSync(path.join(OUT, 'game'), { recursive: true });

function scaled(img, w) {
	const h = Math.round(img.h * w / img.w);
	const out = Buffer.alloc(w * h * 4);
	const fx = img.w / w, fy = img.h / h;
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		let r = 0, g = 0, b = 0, n = 0;
		for (let sy = Math.floor(y * fy); sy < Math.min(img.h, Math.ceil((y + 1) * fy)); sy++) for (let sx = Math.floor(x * fx); sx < Math.min(img.w, Math.ceil((x + 1) * fx)); sx++) {
			const i = (sy * img.w + sx) * 4; r += img.px[i]; g += img.px[i + 1]; b += img.px[i + 2]; n++;
		}
		const o = (y * w + x) * 4; out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n; out[o + 3] = 255;
	}
	return { w, h, px: out };
}
const load = f => decodePng(fs.readFileSync(f));
const W = 2400;
function grid(file, title, rows) { // rows: [{ label, shots: [[file, caption]] }]
	const cols = Math.max(...rows.map(r => r.shots.length));
	const cw = Math.floor(W / cols);
	const imgs = rows.map(r => r.shots.map(([f]) => scaled(load(path.join(SHOTS, f)), cw)));
	const rh = imgs.map(r => Math.max(...r.map(i => i.h)));
	const H = 70 + rows.reduce((a, r, i) => a + 50 + rh[i], 0);
	const cv = new L.Canvas(W, H, [26, 28, 34]);
	cv.text(title, 24, 22, 4, [240, 244, 250]);
	let y = 70;
	rows.forEach((r, i) => {
		cv.text(r.label, 24, y + 14, 3, [255, 210, 120]);
		y += 50;
		imgs[i].forEach((im, j) => { cv.blit(im.px, im.w, im.h, j * cw, y); cv.text(r.shots[j][1], j * cw + 12, y + 10, 2, [230, 236, 246]); });
		y += rh[i];
	});
	cv.save(path.join(OUT, file));
	console.log('wrote ' + file);
}
const BOSSES = [['chimera', '01_chimera'], ['arsenal', '02_arsenal'], ['omega_mage', '03_omega_mage']];
for (const [id, pre] of BOSSES) {
	const set = v => [[`svm_${v}_${pre}_a_front.png`, 'FRONT'], [`svm_${v}_${pre}_b_threequarter.png`, 'THREE-QUARTER'], [`svm_${v}_${pre}_c_side.png`, 'SIDE'], [`svm_${v}_${pre}_e_dusk.png`, 'DUSK']];
	grid(`compare_${id}.png`, `THE ${id.toUpperCase().replace('_', ' ')} - OLD IN-GAME LOOK (TOP) VS NEW GECKOLIB MODEL (BOTTOM)`, [
		{ label: 'BEFORE: TINTED ZOMBIE MESH', shots: set('old') },
		{ label: 'AFTER: CUSTOM MODEL, SAME HIT-BOX', shots: set('new') },
	]);
	const atk = ['0_intro', 'd_melee_7', 'd_melee_alt_7', 'd_shoot_7', 'd_slam_7', 'd_repel_7', 'd_cast_2_14', 'd_cast_2_25', 'd_cast_4_30', 'd_cast_4_45', 'd_burst_7', 'f_death'];
	const cap = { '0_intro': 'INTRO ROAR', d_melee_7: 'MELEE', d_melee_alt_7: 'MELEE ALT', d_shoot_7: 'SHOOT', d_slam_7: 'SLAM', d_repel_7: 'REPEL', d_cast_2_14: 'CAST 2 WIND-UP', d_cast_2_25: 'CAST 2 RELEASE', d_cast_4_30: 'ULTIMATE WIND-UP', d_cast_4_45: 'ULTIMATE RELEASE', d_burst_7: 'BURST', f_death: 'DEATH' };
	grid(`attacks_${id}.png`, `THE ${id.toUpperCase().replace('_', ' ')} - IN-GAME ANIMATION FRAMES (SERVER-SYNCED CLIPS)`, [0, 1, 2].map(r => ({ label: '', shots: atk.slice(r * 4, r * 4 + 4).map(a => [`svm_new_${pre}_${a}.png`, cap[a]]) })));
}
grid('compare_lineup.png', 'SUPERVILLAIN LINEUP - BEFORE (TOP) VS AFTER (BOTTOM)', [
	{ label: 'BEFORE', shots: [['svm_old_00_lineup_day.png', 'DAY'], ['svm_old_00_lineup_dusk.png', 'DUSK'], ['svm_old_00_lineup_night.png', 'NIGHT']] },
	{ label: 'AFTER', shots: [['svm_new_00_lineup_day.png', 'DAY'], ['svm_new_00_lineup_dusk.png', 'DUSK'], ['svm_new_00_lineup_night.png', 'NIGHT']] },
]);
for (const f of fs.readdirSync(SHOTS)) if (/^svm_new_.*\.png$/.test(f)) fs.copyFileSync(path.join(SHOTS, f), path.join(OUT, 'game', f));
