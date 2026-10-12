// v0.16.1 raid undead: old-vs-new in-game comparison sheets from the screenshot harness (RaidModelHarness, a TEMP harness:
// run/raidmodels.flag = "old" on the unmodified code, then "new"). Top row: the old look (vanilla zombie / skeleton /
// pillager meshes, re-skinned); middle: the new models (three-quarter, front, side, night); bottom: animation frames.
// Usage: node scratchpad/compare_raid_models.js <oldShotsDir> <newShotsDir>
// Writes <main>/scratchpad/shots_v0161/raid_models/compare_<unit>.png and copies the new frames next to them.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { decodePng } = require('./lib_geckogen_anim.js');
const G = require('./gen_raid_undead_models.js');

const [OLD, NEW] = process.argv.slice(2);
const OUT = G.PREVIEW;
fs.mkdirSync(path.join(OUT, 'frames'), { recursive: true });

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
const load = f => { const im = decodePng(fs.readFileSync(f)); return im.w >= 1600 ? crop(im, 400, 60, 1200, 860) : im; }; // 1600x900 shots: the centre square
const label = s => s.toUpperCase().replace(/_/g, ' ');

function build(unit, prefix, title) {
	const pick = (dir, kind, suffix) => { const f = path.join(dir, `raid_${kind}_${prefix}_${suffix}.png`); return fs.existsSync(f) ? f : null; };
	const views = ['a_threequarter', 'b_front', 'c_side', 'e_night'];
	const oldRow = views.map(v => [pick(OLD, 'old', v), 'OLD - ' + label(v.slice(2))]).filter(x => x[0]);
	const newRow = views.map(v => [pick(NEW, 'new', v), 'NEW - ' + label(v.slice(2))]).filter(x => x[0]);
	const acts = fs.readdirSync(NEW).filter(f => f.startsWith(`raid_new_${prefix}_d_`)).map(f => [path.join(NEW, f), 'NEW - ' + label(f.replace(`raid_new_${prefix}_d_`, '').replace('.png', ''))]);
	const cw = 480;
	const rows = [oldRow, newRow];
	for (let i = 0; i < acts.length; i += 4) rows.push(acts.slice(i, i + 4));
	const W = cw * 4, rowH = cw + 40;
	const cv = new L.Canvas(W, 70 + rows.length * rowH, [26, 28, 34]);
	cv.text(`${title} - OLD IN-GAME LOOK (TOP ROW) VS THE NEW MODEL IN THE CLIENT`, 20, 20, 4, [240, 244, 250]);
	rows.forEach((row, r) => row.forEach(([f, lab], c) => {
		const im = scaled(load(f), cw);
		cv.blit(im.px, im.w, im.h, c * cw, 70 + r * rowH + 34);
		cv.text(lab, c * cw + 10, 70 + r * rowH + 8, 2, r === 0 ? [255, 190, 160] : [190, 230, 255]);
	}));
	cv.save(path.join(OUT, `compare_${unit}.png`));
	for (const [f] of newRow.concat(acts)) fs.copyFileSync(f, path.join(OUT, 'frames', path.basename(f)));
	console.log('wrote compare_' + unit);
}
const UNITS = [
	['raid_zombie', '01_raid_zombie', 'RISEN (RAID ZOMBIE)'], ['raid_zombie_armoured', '02_raid_zombie_armoured', 'ARMOURED RISEN'],
	['raid_zombie_baby', '03_raid_zombie_baby', 'GRAVE-CHILD (BABY RAID ZOMBIE)'], ['acid_zombie', '04_acid_zombie', 'ACID ZOMBIE'],
	['juggernaut_zombie', '05_juggernaut_zombie', 'JUGGERNAUT'], ['sword_skeleton', '06_sword_skeleton', 'SWORD SKELETON'],
	['cursed_zombie', '07_cursed_zombie', 'CURSED ZOMBIE'], ['pillager_spy', '08_pillager_spy', 'PILLAGER SPY'],
	['powered_zombie_pyrokinesis', '09_empowered_zombie_pyrokinesis', 'POWERED ZOMBIE BOSS - PYROKINESIS'],
	['powered_zombie_cryokinesis', '10_empowered_zombie_cryokinesis', 'POWERED ZOMBIE BOSS - CRYOKINESIS'],
	['powered_zombie_electrokinesis', '11_empowered_zombie_electrokinesis', 'POWERED ZOMBIE BOSS - ELECTROKINESIS'],
	['powered_zombie_crystalkinesis', '12_empowered_zombie_crystalkinesis', 'POWERED ZOMBIE BOSS - CRYSTALKINESIS'],
	['powered_zombie_plant', '13_empowered_zombie_plant_manipulation', 'POWERED ZOMBIE BOSS - PLANTS'],
	['powered_zombie_telekinesis', '14_empowered_zombie_telekinesis', 'POWERED ZOMBIE BOSS - TELEKINESIS'],
	['powered_zombie_flight', '15_empowered_zombie_flight', 'POWERED ZOMBIE BOSS - FLIGHT'],
	['powered_zombie_super_speed', '16_empowered_zombie_super_speed', 'POWERED ZOMBIE BOSS - SUPER SPEED'],
];
for (const [u, p, t] of UNITS) build(u, p, t);
// the lineups
{
	const o = path.join(OLD, 'raid_old_00_lineup.png'), n = path.join(NEW, 'raid_new_00_lineup.png');
	if (fs.existsSync(o) && fs.existsSync(n)) {
		const a = scaled(decodePng(fs.readFileSync(o)), 1600), b = scaled(decodePng(fs.readFileSync(n)), 1600);
		const cv = new L.Canvas(1600, 70 + a.h + b.h + 40, [26, 28, 34]);
		cv.text('RAID UNDEAD LINEUP - OLD (TOP) VS NEW (BOTTOM)', 20, 20, 4, [240, 244, 250]);
		cv.blit(a.px, a.w, a.h, 0, 70); cv.blit(b.px, b.w, b.h, 0, 110 + a.h);
		cv.save(path.join(OUT, 'compare_lineup.png'));
		console.log('wrote compare_lineup');
	}
}
