// v0.16.1 Iron Man comic rework -- placeholder assets (idempotent). Run from the repo root:
//   node scratchpad/gen_v0161_ironman_comic_assets.js
// * Mark 1 V2: a GOLD copy of the Mark 1's armour texture (geo stays mark_1's -- SuperheroArmorVisuals maps it), the
//   glowmask copied as-is, gold copies of the four Mark 1 item sprites + the blueprint sprite, item models.
// * The new signature parts (gilded plating, pressure hull, stealth coating, thermal shielding, hydraulic frame): 16x16
//   procedural sprites + item models.
'use strict';
const fs = require('fs');
const path = require('path');
const { decodePng } = require('./pngdecode_v0161.js');
const { RES, Canvas, writePng, writeJson, clamp, mix, hex, fbm } = require('./v0161_asset_lib.js');

const GOLD_DARK = hex('#5a3a08'), GOLD_MID = hex('#c8901c'), GOLD_HI = hex('#ffe27a');

function goldTint(src, keepGlow) {
	const out = new Canvas(src.w, src.h);
	for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) {
		const i = (y * src.w + x) * 4;
		const r = src.px[i], g = src.px[i + 1], b = src.px[i + 2], a = src.px[i + 3];
		if (a === 0) continue;
		if (keepGlow && keepGlow(x, y)) { out.set(x, y, [r, g, b], a); continue; }
		const l = (0.3 * r + 0.59 * g + 0.11 * b) / 255;
		const c = l < 0.5 ? mix(GOLD_DARK, GOLD_MID, l / 0.5) : mix(GOLD_MID, GOLD_HI, (l - 0.5) / 0.5);
		out.set(x, y, c, a);
	}
	return out;
}

function load(rel) {
	return decodePng(path.join(RES, rel));
}

// ---- Mark 1 V2 armour texture + glowmask
const armour = load('textures/armor/mark_1.png');
const glowFile = path.join(RES, 'textures/armor/mark_1_glowmask.png');
const glow = fs.existsSync(glowFile) ? decodePng(glowFile) : null;
const isGlow = glow ? (x, y) => glow.px[(y * glow.w + x) * 4 + 3] > 0 : null;
writePng('textures/armor/mark_1_v2.png', goldTint(armour, isGlow));
if (glow) {
	const g = new Canvas(glow.w, glow.h);
	glow.px.copy(g.px);
	writePng('textures/armor/mark_1_v2_glowmask.png', g);
}

// ---- item sprites
for (const piece of ['helmet', 'chestplate', 'leggings', 'boots']) {
	writePng(`textures/item/iron_man_mark_1_v2_${piece}.png`, goldTint(load(`textures/item/iron_man_mark_1_${piece}.png`)));
	writeJson(`models/item/iron_man_mark_1_v2_${piece}.json`, { parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/iron_man_mark_1_v2_${piece}` } });
}
writePng('textures/item/mark_1_v2_blueprint.png', goldTint(load('textures/item/mark_1_blueprint.png')));
writeJson('models/item/mark_1_v2_blueprint.json', { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/mark_1_v2_blueprint' } });

// ---- signature parts (16x16)
function sprite(name, paint) {
	const c = new Canvas(16, 16);
	paint(c);
	writePng(`textures/item/${name}.png`, c);
	writeJson(`models/item/${name}.json`, { parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/${name}` } });
}
function plate(c, x0, y0, x1, y1, base, hi, lo, seed) {
	for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
		const n = fbm(x * 0.4, y * 0.4, seed) - 0.5;
		let col = base.map(v => clamp(v + n * 40));
		if (y === y0 || x === x0) col = hi;
		if (y === y1 || x === x1) col = lo;
		c.set(x, y, col);
	}
}
sprite('gilded_plating', c => {
	plate(c, 2, 3, 13, 12, hex('#d9a52a'), hex('#fff0a0'), hex('#6a4508'), 1);
	for (const [x, y] of [[4, 5], [11, 5], [4, 10], [11, 10]]) c.set(x, y, hex('#7a5210'));
});
sprite('pressure_hull', c => {
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x - 7.5, dy = y - 7.5, d = Math.sqrt(dx * dx + dy * dy);
		if (d < 6.8) c.set(x, y, d > 5.6 ? hex('#1d4a5e') : mix(hex('#3d9ab8'), hex('#9fe6ff'), clamp((4 - d) / 4, 0, 1) * 255 / 255));
		if (d < 2.2) c.set(x, y, hex('#e0fbff'));
	}
	for (const [x, y] of [[7, 1], [7, 14], [1, 7], [14, 7]]) c.set(x, y, hex('#0e2c3a'));
});
sprite('stealth_coating', c => {
	plate(c, 2, 2, 13, 13, hex('#24272c'), hex('#4a5058'), hex('#0c0d10'), 3);
	for (let i = 0; i < 9; i++) c.set(3 + i, 12 - i, hex('#3a6f7a'));
	c.set(12, 3, hex('#7fd6e0'));
});
sprite('thermal_shielding', c => {
	for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
		const hexOn = ((x + (y % 2) * 1) % 3 === 0) || y % 3 === 0;
		c.set(x, y, hexOn ? hex('#4a2a10') : mix(hex('#ff7a20'), hex('#ffd06a'), (14 - y) / 12));
	}
	for (let x = 2; x < 14; x++) { c.set(x, 2, hex('#e8eef5')); c.set(x, 13, hex('#7c8696')); }
});
sprite('hydraulic_frame', c => {
	for (let y = 1; y < 15; y++) { c.set(3, y, hex('#9aa2ad')); c.set(4, y, hex('#5c6370')); c.set(11, y, hex('#9aa2ad')); c.set(12, y, hex('#5c6370')); }
	for (let x = 3; x < 13; x++) { c.set(x, 2, hex('#c43a2a')); c.set(x, 13, hex('#c43a2a')); c.set(x, 7, hex('#d8a024')); c.set(x, 8, hex('#8a5a10')); }
	for (let y = 3; y < 7; y++) { c.set(7, y, hex('#d8dde5')); c.set(8, y, hex('#8e96a3')); }
	for (let y = 9; y < 13; y++) { c.set(7, y, hex('#d8dde5')); c.set(8, y, hex('#8e96a3')); }
});

console.log('wrote mark_1_v2 + signature-part assets');
