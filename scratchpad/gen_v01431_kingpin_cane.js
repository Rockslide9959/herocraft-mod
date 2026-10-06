// v0.14.31 Kingpin's Cane: generates the 3D held model, the flat 16x16 GUI icon (+ its model) and every texture.
// Re-runnable. Run from the repo root: node scratchpad/gen_v01431_kingpin_cane.js
//
// Look: the Kingpin's walking cane -- a slim shaft of black lacquered ebony, a polished silver collar under a large
// faceted diamond head, and a silver ferrule on the tip. Held like a sword: the diamond head is the pommel (it sits
// just past the little finger of the fist, the way the Kingpin carries it) and the shaft is the striking length.
//
// GUI: KingpinCaneClient swaps in item/kingpin_cane_icon (the flat 16x16 sprite) for ItemDisplayContext.GUI.
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero');
const TEX = path.join(ROOT, 'textures/item');
const MODELS = path.join(ROOT, 'models/item');

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
function image(w, h, fn) {
	const px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = fn(x, y); const i = (y * w + x) * 4;
		if (!c) continue;
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = c.length > 3 ? c[3] : 255;
	}
	return px;
}
const write = (name, w, h, px) => { fs.writeFileSync(path.join(TEX, name + '.png'), encode(w, h, px)); console.log('wrote', name); };

// ---------------------------------------------------------------- palettes (4-shade ramps, darkest -> lightest)
const EBONY = ['#0d0b0f', '#17131a', '#221c26', '#352c3b'].map(hex); // black lacquered wood
const SILVER = ['#5d616b', '#8d939e', '#bfc5ce', '#eef1f6'].map(hex);
const GEM = ['#6f9fbe', '#9fcde6', '#cdeeff', '#ffffff'].map(hex); // icy diamond

// All model textures are 32x32: a face's uv window is its size in model units, so 1 unit = 2 texels.
const S = 32;
// Ebony: lengthwise grain (the shaft runs along the texture's v) -- dark body, a fine dark line every 5 columns and a
// lacquer highlight streak, broken every few rows so it reads as grain rather than stripes.
write('kingpin_cane_wood', S, S, image(S, S, (x, y) => {
	if (x % 5 === 0) return EBONY[0];
	if (x % 5 === 2 && (y + x * 3) % 11 < 7) return EBONY[3];
	return (x + Math.floor(y / 4)) % 3 === 0 ? EBONY[1] : EBONY[2];
}));
// Silver: polished bands -- bright line, mid, mid, dark seam.
write('kingpin_cane_silver', S, S, image(S, S, (x, y) => {
	const b = y % 6;
	if (b === 0) return SILVER[0];
	if (b === 1) return SILVER[3];
	return (x + y) % 7 === 0 ? SILVER[3] : SILVER[2 - (b > 3 ? 1 : 0)];
}));
// Diamond: four 16x16 quadrants, each a facet of a different brightness split by a diagonal into a lit and a shaded
// triangle with a hard white glint -- the model picks a different quadrant for each face direction, so neighbouring
// faces never match and the stepped head reads as cut facets.
write('kingpin_cane_gem', S, S, image(S, S, (x, y) => {
	const q = (x < 16 ? 0 : 1) + (y < 16 ? 0 : 2);
	const lx = x % 16, ly = y % 16;
	const base = [2, 1, 2, 0][q];
	const lit = (lx + ly) < 16 ? 1 : 0;
	if (lx === ly || lx + ly === 15) return GEM[3]; // facet edges
	if ((lx === 3 && ly === 4) || (lx === 4 && ly === 3) || (lx === 4 && ly === 4)) return GEM[3]; // glint
	return GEM[Math.min(3, base + lit)];
}));

// ---------------------------------------------------------------- GUI icon (16x16 sprite, vanilla sword diagonal)
// Hand-authored, matching the held model: the diamond head bottom-left (the pommel), silver collar, the black shaft up the
// diagonal and the silver ferrule top-right. Outline added automatically.
const ICON = [
	'................',
	'..............f.',
	'.............Fs.',
	'............wk..',
	'...........wk...',
	'..........wk....',
	'.........wk.....',
	'........wk......',
	'.......wk.......',
	'......wk........',
	'.....wk.........',
	'..hHcwk.........',
	'.hdHCc..........',
	'.HHdHh..........',
	'..dHd...........',
	'...h............',
];
const ICON_PAL = {
	w: EBONY[3], k: EBONY[1], c: SILVER[2], C: SILVER[3], s: SILVER[2], f: SILVER[3], F: SILVER[1],
	h: GEM[1], H: GEM[2], d: GEM[0],
};
(function icon() {
	const px = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const ch = ICON[y][x];
		const c = ch === 'H' && (x + y) % 3 === 0 ? GEM[3] : ICON_PAL[ch]; if (!c) continue;
		const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
	const out = Buffer.from(px);
	const OUT = hex('#08070a');
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const i = (y * 16 + x) * 4; if (px[i + 3]) continue;
		let edge = false;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const nx = x + dx, ny = y + dy;
			if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[(ny * 16 + nx) * 4 + 3]) edge = true;
		}
		if (edge) { out[i] = OUT[0]; out[i + 1] = OUT[1]; out[i + 2] = OUT[2]; out[i + 3] = 255; }
	}
	write('kingpin_cane', 16, 16, out);
})();

// ---------------------------------------------------------------- 3D model
// Built upright: x / z centred on 8, the cane running up local +Y from the diamond head (y -3.9) to the ferrule tip
// (y 18.7), then every element is pre-rotated -45 degrees about Z (origin 8,8,8) onto the vanilla sword sprite's
// diagonal, so the vanilla minecraft:item/handheld display transforms hold it like a sword: the fist closes around the
// grip (y ~0.9..3.5), the diamond head sits below the little finger and the shaft runs out ~1 block.
// box(xHalf, zHalf, y0, y1, tex): a box centred on the axis.
const box = (xh, zh, y0, y1, tex) => ({ from: [8 - xh, y0, 8 - zh], to: [8 + xh, y1, 8 + zh], tex });
// An octagonal layer of the diamond: a square core plus a narrower cross both ways.
const oct = (r, y0, y1) => [box(r * 0.72, r, y0, y1, 'gem'), box(r, r * 0.72, y0, y1, 'gem')];
const ELEMENTS = [
	// faceted diamond head: table at the pommel end, crown widening to the girdle, pavilion narrowing into the setting
	box(0.75, 0.75, -3.9, -3.5, 'gem'), // table
	...oct(1.4, -3.5, -3.0), // upper crown
	...oct(1.9, -3.0, -2.45), // crown
	...oct(2.25, -2.45, -1.85), // girdle
	...oct(1.85, -1.85, -1.3), // upper pavilion
	...oct(1.35, -1.3, -0.8), // pavilion
	box(0.8, 0.8, -0.8, -0.45, 'gem'), // culet
	// silver setting: four claws up the pavilion and a cup
	box(0.25, 1.45, -1.75, -0.6, 'silver'),
	box(1.45, 0.25, -1.75, -0.6, 'silver'),
	box(0.95, 0.95, -0.6, -0.1, 'silver'), // cup
	// collar: two rims around a waist
	box(0.8, 0.8, -0.1, 0.3, 'silver'),
	box(0.62, 0.62, 0.3, 0.75, 'silver'),
	box(0.75, 0.75, 0.75, 1.05, 'silver'),
	// the grip end of the shaft (a touch thicker), then the shaft itself
	box(0.56, 0.56, 1.05, 4.6, 'wood'),
	box(0.6, 0.6, 4.6, 4.85, 'silver'), // a thin ring where the grip ends
	box(0.5, 0.5, 4.85, 17.7, 'wood'),
	// ferrule
	box(0.56, 0.56, 17.7, 18.35, 'silver'),
	box(0.42, 0.42, 18.35, 18.7, 'silver'),
];

const r3 = (v) => Math.round(v * 1000) / 1000;
// A uv window the face's own size (1 unit = 2 texels). Gem faces each take a quadrant chosen by direction, so adjacent
// faces of one layer show different facets.
const GEM_QUAD = { north: [0, 0], south: [8, 8], east: [8, 0], west: [0, 8], up: [8, 0], down: [0, 8] };
function faceUv(tex, dir, w, h, k) {
	w = Math.min(tex === 'gem' ? 8 : 16, w); h = Math.min(tex === 'gem' ? 8 : 16, h);
	if (tex === 'gem') {
		const [u, v] = GEM_QUAD[dir];
		const du = Math.min(8 - w, (k * 1.5) % 4), dv = Math.min(8 - h, (k * 2.5) % 4);
		return [r3(u + du), r3(v + dv), r3(u + du + w), r3(v + dv + h)];
	}
	const u0 = Math.min(16 - w, (k * 3) % 9), v0 = Math.min(16 - h, (k * 5) % 7);
	return [r3(u0), r3(v0), r3(u0 + w), r3(v0 + h)];
}
function buildElements() {
	return ELEMENTS.map(({ from, to, tex }, k) => {
		const sx = to[0] - from[0], sy = to[1] - from[1], sz = to[2] - from[2];
		const t = '#' + tex;
		const face = (dir, w, h, kk) => ({ uv: faceUv(tex, dir, w, h, kk), texture: t });
		return {
			from: from.map(r3), to: to.map(r3),
			rotation: { origin: [8, 8, 8], axis: 'z', angle: -45, rescale: false },
			faces: {
				north: face('north', sx, sy, k),
				south: face('south', sx, sy, k + 1),
				east: face('east', sz, sy, k + 2),
				west: face('west', sz, sy, k + 3),
				up: face('up', sx, sz, k + 4),
				down: face('down', sx, sz, k + 5),
			},
		};
	});
}

// Vanilla minecraft:item/handheld hand transforms, verbatim (the model fills the same 16x16 diagonal as a sword sprite,
// a little longer). gui: identity, like minecraft:item/generated -- KingpinCaneClient draws the flat icon there, and
// this is also the fallback if no FRAPI renderer is present. ground / head / fixed: minecraft:item/generated's.
const DISPLAY = {
	thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 4.0, 0.5], scale: [0.85, 0.85, 0.85] },
	thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 4.0, 0.5], scale: [0.85, 0.85, 0.85] },
	firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
	firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
	ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
	head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
	gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
	fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
};
const held = {
	credit: 'Kingpin\'s Cane 3D model (v0.14.31), generated by scratchpad/gen_v01431_kingpin_cane.js. Built upright along +Y (diamond head at the pommel end, ferrule at the tip) and pre-rotated -45 degrees about Z onto the vanilla sword sprite diagonal; vanilla handheld transforms. The GUI icon is swapped in by KingpinCaneClient (client).',
	gui_light: 'front',
	textures: {
		wood: 'projecthero:item/kingpin_cane_wood',
		silver: 'projecthero:item/kingpin_cane_silver',
		gem: 'projecthero:item/kingpin_cane_gem',
		particle: 'projecthero:item/kingpin_cane_wood',
	},
	elements: buildElements(),
	display: DISPLAY,
};
fs.writeFileSync(path.join(MODELS, 'kingpin_cane.json'), JSON.stringify(held, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'kingpin_cane_icon.json'), JSON.stringify({
	parent: 'minecraft:item/generated',
	textures: { layer0: 'projecthero:item/kingpin_cane' },
}, null, 2) + '\n');
console.log('wrote models (' + ELEMENTS.length + ' elements)');
