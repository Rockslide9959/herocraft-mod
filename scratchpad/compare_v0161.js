// v0.16.1: concept-vs-in-game comparison sheets. Top: the approved concept sheet; bottom: the real game model in the
// client (daylight, dusk in the rain, and an attack at dusk) from the screenshot harness.
// Usage: node scratchpad/compare_v0161.js <screenshotsDir>   (writes scratchpad/shots_v0161/models/compare_<unit>.png
// in the main checkout and copies the shots next to them)
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { decodePng } = require('./lib_geckogen_anim.js');

const SHOTS = process.argv[2];
const ROOT = path.join(__dirname, '..', '..', '..', '..', 'scratchpad');
const CONCEPTS = path.join(ROOT, 'concepts_v0161');
const OUT = path.join(ROOT, 'shots_v0161', 'models');
fs.mkdirSync(OUT, { recursive: true });

function scaled(img, w) { // area-averaged resize to width w
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
const UNITS = [
	['rime_hound', '05_hound.png', '01_rime_hound', 'pounce'], ['jotun_raider', '01_raider.png', '02_jotun_raider', 'spear_thrust'],
	['frost_shaman', '02_shaman.png', '03_frost_shaman', 'frost_nova'], ['ice_brute', '04_brute.png', '04_ice_brute', 'glacier_spikes'],
	['frost_giant_warlord', '03_warlord.png', '05_frost_giant_warlord', 'rally'], ['king_laufey', '06_laufey.png', '06_king_laufey', 'casket_open'],
];
const W = 2400;
function build(unit, conceptFile, shots, labels) {
	const top = scaled(load(path.join(CONCEPTS, conceptFile)), W);
	const cw = Math.floor(W / shots.length);
	const imgs = shots.map(s => scaled(load(path.join(SHOTS, s)), cw));
	const bh = Math.max(...imgs.map(i => i.h));
	const cv = new L.Canvas(W, 60 + top.h + 60 + bh, [26, 28, 34]);
	cv.text(`${unit.toUpperCase().replace(/_/g, ' ')} - CONCEPT (TOP) VS THE REAL GAME MODEL IN THE CLIENT (BOTTOM)`, 24, 18, 4, [240, 244, 250]);
	cv.blit(top.px, top.w, top.h, 0, 60);
	imgs.forEach((im, i) => { cv.blit(im.px, im.w, im.h, i * cw, 120 + top.h); cv.text(labels[i], i * cw + 16, 80 + top.h, 3, [200, 220, 240]); });
	cv.save(path.join(OUT, `compare_${unit}.png`));
	console.log('wrote compare_' + unit);
}
for (const [unit, concept, pre, atk] of UNITS)
	build(unit, concept, [`v0161fm_${pre}_day_idle.png`, `v0161fm_${pre}_dusk_idle.png`, `v0161fm_${pre}_dusk_${atk}.png`], ['IN GAME - DAYLIGHT', 'IN GAME - DUSK, RAIN', `IN GAME - ${atk.toUpperCase().replace(/_/g, ' ')} AT DUSK`]);
build('ymir_arm', '07_ymir_arm.png', ['v0161fm_07_ymir_day_palm_slam.png', 'v0161fm_07_ymir_dusk_palm_slam.png'], ['IN GAME - PALM SLAM, DAYLIGHT', 'IN GAME - PALM SLAM, DUSK']);
build('ultron_omega', '08_ultron_omega.png', ['v0161fm_08_ultron_omega_day_idle.png', 'v0161fm_08_ultron_omega_day_vibranium_strike.png', 'v0161fm_08_ultron_omega_dusk_idle.png'], ['IN GAME - HOVER, DAYLIGHT', 'IN GAME - VIBRANIUM STRIKE', 'IN GAME - DUSK, RAIN']);
build('lineup', '00_lineup.png', ['v0161fm_00_lineup_day.png', 'v0161fm_00_lineup_dusk_rain.png', 'v0161fm_00_lineup_night.png'], ['IN GAME - DAY', 'IN GAME - DUSK, RAIN', 'IN GAME - NIGHT']);
for (const f of fs.readdirSync(SHOTS)) if (/^v0161fm_.*\.png$/.test(f)) fs.copyFileSync(path.join(SHOTS, f), path.join(OUT, f));
