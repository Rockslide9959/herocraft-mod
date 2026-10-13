// v0.16.1: custom-model vs player-rig comparison sheets for the six raid units on 64x64 player skins (the basic / baby
// Risen use the vanilla zombie look and appear only in the lineups).
// Usage: node scratchpad/compare_player_skins.js <customModelShotsDir> <playerShotsDir>
// (shots from the TEMP harness scratchpad/V0161RaidModelHarness.java.txt: run/raidmodels.flag = "new" on the custom models,
// then "player" on the player rig). Writes <main>/scratchpad/shots_v0161/raid_models/compare_player_<unit>.png.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { decodePng } = require('./lib_geckogen_anim.js');
const G = require('./gen_raid_undead_models.js');

const [GEO, PLAYER] = process.argv.slice(2);
const OUT = G.PREVIEW;
fs.mkdirSync(path.join(OUT, 'frames_player'), { recursive: true });

function crop(img, x0, y0, x1, y1) {
	const w = x1 - x0, h = y1 - y0, px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) img.px.copy(px, y * w * 4, ((y + y0) * img.w + x0) * 4, ((y + y0) * img.w + x1) * 4);
	return { w, h, px };
}
function scaled(img, w) {
	const h = Math.round(img.h * w / img.w), out = Buffer.alloc(w * h * 4), fx = img.w / w, fy = img.h / h;
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		let r = 0, g = 0, b = 0, n = 0;
		for (let sy = Math.floor(y * fy); sy < Math.min(img.h, Math.ceil((y + 1) * fy)); sy++) for (let sx = Math.floor(x * fx); sx < Math.min(img.w, Math.ceil((x + 1) * fx)); sx++) {
			const i = (sy * img.w + sx) * 4; r += img.px[i]; g += img.px[i + 1]; b += img.px[i + 2]; n++;
		}
		const o = (y * w + x) * 4; out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n; out[o + 3] = 255;
	}
	return { w, h, px: out };
}
const load = f => { const im = decodePng(fs.readFileSync(f)); return im.w >= 1600 ? crop(im, 400, 60, 1200, 860) : im; };
const label = s => s.toUpperCase().replace(/_/g, ' ');

function build(unit, prefix, title, skinName) {
	const views = ['a_threequarter', 'b_front', 'c_side', 'e_night'];
	const pick = (dir, kind, v) => { const f = path.join(dir, `raid_${kind}_${prefix}_${v}.png`); return fs.existsSync(f) ? f : null; };
	const geoRow = views.map(v => [pick(GEO, 'new', v), 'CUSTOM MODEL - ' + label(v.slice(2))]).filter(x => x[0]);
	const plRow = views.map(v => [pick(PLAYER, 'player', v), 'PLAYER RIG - ' + label(v.slice(2))]).filter(x => x[0]);
	const acts = fs.readdirSync(PLAYER).filter(f => f.startsWith(`raid_player_${prefix}_d_`)).map(f => [path.join(PLAYER, f), 'PLAYER RIG - ' + label(f.replace(`raid_player_${prefix}_d_`, '').replace('.png', ''))]);
	const cw = 480, rowH = cw + 40;
	const rows = [geoRow, plRow];
	for (let i = 0; i < acts.length; i += 4) rows.push(acts.slice(i, i + 4));
	const skinSheet = path.join(OUT, 'skins', `${skinName}_texture.png`);
	const skin = fs.existsSync(skinSheet) ? decodePng(fs.readFileSync(skinSheet)) : null;
	const skinH = skin ? Math.round(skin.h * (cw * 4) / skin.w) : 0;
	const cv = new L.Canvas(cw * 4, 70 + rows.length * rowH + (skin ? skinH + 40 : 0), [26, 28, 34]);
	cv.text(`${title} - CUSTOM MODEL (TOP) VS THE PLAYER RIG + 64X64 SKIN IN THE CLIENT`, 20, 20, 4, [240, 244, 250]);
	rows.forEach((row, r) => row.forEach(([f, lab], c) => {
		const im = scaled(load(f), cw);
		cv.blit(im.px, im.w, im.h, c * cw, 70 + r * rowH + 34);
		cv.text(lab, c * cw + 10, 70 + r * rowH + 8, 2, r === 0 ? [255, 200, 160] : [190, 230, 255]);
	}));
	if (skin) { const s = scaled(skin, cw * 4); cv.blit(s.px, s.w, s.h, 0, 70 + rows.length * rowH + 30); }
	cv.save(path.join(OUT, `compare_player_${unit}.png`));
	for (const [f] of plRow.concat(acts)) fs.copyFileSync(f, path.join(OUT, 'frames_player', path.basename(f)));
	console.log('wrote compare_player_' + unit);
}
const UNITS = [
	['raid_zombie_armoured', '02_raid_zombie_armoured', 'ARMOURED RISEN', 'raid_zombie_armoured'],
	['acid_zombie', '04_acid_zombie', 'ACID ZOMBIE', 'acid_zombie'],
	['juggernaut_zombie', '05_juggernaut_zombie', 'JUGGERNAUT', 'juggernaut_zombie'], ['sword_skeleton', '06_sword_skeleton', 'SWORD SKELETON', 'sword_skeleton'],
	['cursed_zombie', '07_cursed_zombie', 'CURSED ZOMBIE', 'cursed_zombie'], ['pillager_spy', '08_pillager_spy', 'PILLAGER SPY', 'pillager_spy'],
];
for (const [u, p, t, s] of UNITS) build(u, p, t, s);
for (const [n, lab] of [['00_lineup', 'DAY'], ['00_lineup_night', 'NIGHT']]) {
	const g = path.join(GEO, `raid_new_${n}.png`), pl = path.join(PLAYER, `raid_player_${n}.png`);
	if (!fs.existsSync(pl)) continue;
	const b = scaled(decodePng(fs.readFileSync(pl)), 1600);
	const a = fs.existsSync(g) ? scaled(decodePng(fs.readFileSync(g)), 1600) : null;
	const cv = new L.Canvas(1600, 70 + b.h + (a ? a.h + 40 : 0), [26, 28, 34]);
	cv.text(`RAID LINEUP (${lab}) - ${a ? 'CUSTOM MODELS (TOP) VS PLAYER RIG (BOTTOM)' : 'PLAYER RIG'}`, 20, 20, 4, [240, 244, 250]);
	if (a) cv.blit(a.px, a.w, a.h, 0, 70);
	cv.blit(b.px, b.w, b.h, 0, 70 + (a ? a.h + 40 : 0));
	cv.save(path.join(OUT, `compare_player_lineup${lab === 'NIGHT' ? '_night' : ''}.png`));
	console.log('wrote compare_player_lineup ' + lab);
}
