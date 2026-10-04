// v0.14.21 Necrotic Blade: generates the 3D held model, the 2D GUI icon model and every texture.
// Re-runnable. Run from the repo root: node scratchpad/gen_v01421_necrotic_blade.js
//
// Look: a skeleton of a sword. Blackened-iron blade with a serrated edge and a broken line of sickly green runes down the
// fuller; a ribcage crossguard around a vertebra with a green soul gem; a grip of leather-wrapped vertebrae (the
// "spine"); a skull pommel whose crown is the pommel end (the skull sits on top of the spine grip, so it reads upside
// down when the blade points up) with glowing green eye sockets.
//
// Glow: every face of the #glow elements carries tintindex 0, which NecroticBladeClient re-emits with an emissive FRAPI
// material (no colour provider is registered), so the runes / eyes / gem are fullbright in the dark.
// GUI: NecroticBladeClient swaps in item/necrotic_blade_icon (the flat 16x16 sprite) for ItemDisplayContext.GUI.
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

// ---------------------------------------------------------------- palettes (3-4 shade ramps, no noise)
const STEEL = ['#1d1b22', '#2b2832', '#3a3643', '#4c4757'].map(hex); // blackened iron, darkest -> lightest
const EDGE = ['#5d5a68', '#7f7b8b', '#a4a0ae', '#c9c5d0'].map(hex); // honed edge
const BONE = ['#8a7f63', '#b3a785', '#d6cca9', '#ece4c6'].map(hex);
const WRAP = ['#1e1611', '#33261c', '#4a3828', '#5f4a35'].map(hex);
const GLOW = ['#1f9e2c', '#3fd63c', '#7cf55a', '#cbffa6'].map(hex);

// All model textures are 32x32: a face's uv window is its size in model units, so 1 unit = 2 texels.
const S = 32;
// Blackened iron: flat mid tone, a darker forge seam every 8 rows and a lighter fleck row under it -- reads as layered,
// folded metal without noise.
write('necrotic_blade_steel', S, S, image(S, S, (x, y) => {
	if (y % 8 === 0) return STEEL[1];
	if (y % 8 === 1 && (x + y) % 6 < 3) return STEEL[3];
	return STEEL[2];
}));
// Honed edge: light centre line, mid either side.
write('necrotic_blade_edge', S, S, image(S, S, (x, y) => {
	if (y % 6 === 0) return EDGE[1];
	return x % 4 === 1 ? EDGE[3] : EDGE[2];
}));
// Bone: warm ivory with a darker growth ring every 5 rows.
write('necrotic_blade_bone', S, S, image(S, S, (x, y) => {
	if (y % 5 === 0) return BONE[1];
	if (y % 5 === 1) return BONE[3];
	return BONE[2];
}));
// Grip: diagonal leather wraps -- gap, lit edge, body, body.
write('necrotic_blade_grip', S, S, image(S, S, (x, y) => [WRAP[0], WRAP[3], WRAP[2], WRAP[1]][(x + y) % 4]));
// Glow: green body with bright glyph ticks -- emissive in game.
write('necrotic_blade_glow', S, S, image(S, S, (x, y) => {
	if (y % 4 === 0) return GLOW[3];
	if (x % 2 === 0 && y % 4 === 2) return GLOW[3];
	return GLOW[2];
}));

// ---------------------------------------------------------------- GUI icon (16x16 sprite, vanilla sword diagonal)
// Hand-authored, vanilla sword diagonal: tip top-right, ribcage guard with a soul gem, wrapped spine grip, skull
// pommel with glowing eyes bottom-left. Serrations alternate between the edges. Outline added automatically.
const ICON = [
	'................',
	'.............EE.',
	'............egd.',
	'...........ehdl.',
	'..........egd...',
	'....b...eehd....',
	'...b....egd.....',
	'...bb..ehdl.....',
	'....Bbegd.......',
	'.....Bhd........',
	'....wWBb..b.....',
	'...Ww..Bbb......',
	'.bbb....B.......',
	'bGbGb...........',
	'bbBbb...........',
	'.BbB............',
];
const ICON_PAL = {
	e: EDGE[2], E: EDGE[3], l: EDGE[1], m: STEEL[3], d: STEEL[2], g: GLOW[2], h: GLOW[3], G: GLOW[2],
	b: BONE[2], B: BONE[1], w: WRAP[2], W: WRAP[1],
};
(function icon() {
	const px = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const c = ICON_PAL[ICON[y][x]]; if (!c) continue;
		const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
	const out = Buffer.from(px);
	const OUT = hex('#0e0c12');
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const i = (y * 16 + x) * 4; if (px[i + 3]) continue;
		let edge = false;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const nx = x + dx, ny = y + dy;
			if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[(ny * 16 + nx) * 4 + 3]) edge = true;
		}
		if (edge) { out[i] = OUT[0]; out[i + 1] = OUT[1]; out[i + 2] = OUT[2]; out[i + 3] = 255; }
	}
	write('necrotic_blade', 16, 16, out);
})();

// ---------------------------------------------------------------- 3D model
// Built upright: x / z centred on 8, the sword running up local +Y from the pommel (y -2.9) to the tip (y 18.4), then
// every element is pre-rotated -45 degrees about Z (origin 8,8,8) onto the vanilla sword sprite's diagonal (pommel
// bottom-left at ~(0.6, 0.6), tip top-right at ~(15.7, 15.7)) -- the same footprint as a 16x16 handheld sprite, so the
// vanilla minecraft:item/handheld display transforms hold it exactly like a vanilla sword.
const Z = (half) => [8 - half, 8 + half];
const box = (x0, x1, y0, y1, zh, tex) => ({ from: [x0, y0, Z(zh)[0]], to: [x1, y1, Z(zh)[1]], tex });
const ELEMENTS = [
	// skull pommel: crown at the pommel end, face on both flats, glowing sockets
	box(7.0, 9.0, -2.9, -2.6, 0.8, 'bone'), // crown cap
	box(6.6, 9.4, -2.6, -0.8, 1.2, 'bone'), // cranium
	box(7.0, 9.0, -0.8, -0.2, 0.9, 'bone'), // jaw / teeth
	box(6.95, 7.85, -1.75, -1.05, 1.3, 'glow'), // eye
	box(8.15, 9.05, -1.75, -1.05, 1.3, 'glow'), // eye
	box(7.8, 8.2, -0.95, -0.7, 1.25, 'steel'), // nose cavity
	// grip: leather-wrapped spine with three vertebra rings
	box(7.35, 8.65, -0.4, 3.0, 0.65, 'grip'),
	box(7.1, 8.9, 0.3, 0.65, 0.9, 'bone'),
	box(7.1, 8.9, 1.3, 1.65, 0.9, 'bone'),
	box(7.1, 8.9, 2.3, 2.65, 0.9, 'bone'),
	// ribcage crossguard around a vertebra with a soul gem
	box(6.6, 9.4, 3.0, 4.2, 1.0, 'bone'), // vertebra
	box(7.5, 8.5, 3.25, 3.95, 1.1, 'glow'), // soul gem
	box(3.8, 6.6, 3.3, 3.9, 0.6, 'bone'), // lower rib, left
	box(9.4, 12.2, 3.3, 3.9, 0.6, 'bone'), // lower rib, right
	box(3.2, 4.0, 3.6, 5.0, 0.55, 'bone'), // rib curling toward the blade
	box(12.0, 12.8, 3.6, 5.0, 0.55, 'bone'),
	box(3.2, 3.7, 5.0, 5.8, 0.45, 'bone'), // rib claw
	box(12.3, 12.8, 5.0, 5.8, 0.45, 'bone'),
	box(5.0, 6.6, 4.2, 4.7, 0.5, 'bone'), // upper rib, left
	box(9.4, 11.0, 4.2, 4.7, 0.5, 'bone'), // upper rib, right
	box(5.0, 5.5, 4.7, 5.3, 0.45, 'bone'),
	box(10.5, 11.0, 4.7, 5.3, 0.45, 'bone'),
	box(7.0, 9.0, 4.2, 4.7, 0.8, 'steel'), // collar
	// blade
	box(6.3, 9.7, 4.7, 5.4, 0.5, 'steel'), // root flare
	box(6.8, 9.2, 5.4, 15.6, 0.4, 'steel'), // body
	box(6.4, 6.8, 5.4, 15.2, 0.3, 'edge'), // edge, left
	box(9.2, 9.6, 5.4, 15.2, 0.3, 'edge'), // edge, right
	box(7.2, 8.8, 15.6, 17.0, 0.35, 'steel'), // taper
	box(6.8, 7.2, 15.2, 16.4, 0.28, 'edge'),
	box(8.8, 9.2, 15.2, 16.4, 0.28, 'edge'),
	box(7.6, 8.4, 17.0, 18.4, 0.3, 'edge'), // point
	box(7.2, 7.6, 16.4, 17.4, 0.25, 'edge'),
	box(8.4, 8.8, 16.4, 17.4, 0.25, 'edge'),
	// serrations: hooked teeth, staggered between the two edges
	...[6.0, 8.2, 10.4, 12.6].flatMap((y) => [box(5.9, 6.4, y, y + 0.8, 0.25, 'edge'), box(6.15, 6.4, y + 0.8, y + 1.2, 0.22, 'edge')]),
	...[7.1, 9.3, 11.5, 13.7].flatMap((y) => [box(9.6, 10.1, y, y + 0.8, 0.25, 'edge'), box(9.6, 9.85, y + 0.8, y + 1.2, 0.22, 'edge')]),
	// rune line down the fuller (broken into glyphs), proud of the flats on both sides
	...[[5.6, 7.0], [7.4, 8.0], [8.4, 10.0], [10.4, 11.0], [11.4, 12.8], [13.2, 13.8], [14.2, 14.8]].map(([a, b]) => box(7.6, 8.4, a, b, 0.5, 'glow')),
];

const r3 = (v) => Math.round(v * 1000) / 1000;
function faceUv(w, h, k) {
	w = Math.min(16, w); h = Math.min(16, h);
	const u0 = Math.min(16 - w, (k * 3) % 9), v0 = Math.min(16 - h, (k * 5) % 7);
	return [r3(u0), r3(v0), r3(u0 + w), r3(v0 + h)];
}
function buildElements() {
	return ELEMENTS.map(({ from, to, tex }, k) => {
		const sx = to[0] - from[0], sy = to[1] - from[1], sz = to[2] - from[2];
		const t = '#' + tex;
		const face = (uv) => tex === 'glow' ? { uv, texture: t, tintindex: 0 } : { uv, texture: t };
		return {
			from: from.map(r3), to: to.map(r3),
			rotation: { origin: [8, 8, 8], axis: 'z', angle: -45, rescale: false },
			faces: {
				north: face(faceUv(sx, sy, k)),
				south: face(faceUv(sx, sy, k + 1)),
				east: face(faceUv(sz, sy, k + 2)),
				west: face(faceUv(sz, sy, k + 3)),
				up: face(faceUv(sx, sz, k + 4)),
				down: face(faceUv(sx, sz, k + 5)),
			},
		};
	});
}

// Vanilla minecraft:item/handheld hand transforms, verbatim (the model fills the same 16x16 diagonal as a sword sprite).
// gui: identity, like minecraft:item/generated -- NecroticBladeClient draws the flat icon there, and this is also the
// fallback 3D icon if no FRAPI renderer is present. ground / head / fixed: minecraft:item/generated's.
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
	credit: 'Necrotic Blade 3D model (v0.14.21), generated by scratchpad/gen_v01421_necrotic_blade.js. Built upright along +Y and pre-rotated -45 degrees about Z onto the vanilla sword sprite diagonal; vanilla handheld transforms. #glow faces are made emissive and the GUI icon is swapped in by NecroticBladeClient (client).',
	gui_light: 'front',
	textures: {
		steel: 'projecthero:item/necrotic_blade_steel',
		edge: 'projecthero:item/necrotic_blade_edge',
		bone: 'projecthero:item/necrotic_blade_bone',
		grip: 'projecthero:item/necrotic_blade_grip',
		glow: 'projecthero:item/necrotic_blade_glow',
		particle: 'projecthero:item/necrotic_blade_steel',
	},
	elements: buildElements(),
	display: DISPLAY,
};
fs.writeFileSync(path.join(MODELS, 'necrotic_blade.json'), JSON.stringify(held, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'necrotic_blade_icon.json'), JSON.stringify({
	parent: 'minecraft:item/generated',
	textures: { layer0: 'projecthero:item/necrotic_blade' },
}, null, 2) + '\n');
console.log('wrote models (' + ELEMENTS.length + ' elements)');
