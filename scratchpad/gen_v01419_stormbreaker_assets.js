// v0.14.19 Stormbreaker: generates the item models (held + thrown) and all textures.
// Re-runnable. Run from the repo root: node scratchpad/gen_v01419_stormbreaker_assets.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero');
const TEX = path.join(ROOT, 'textures/item');
const MODELS = path.join(ROOT, 'models/item');

// ---------------- deterministic noise ----------------
let seed = 1417;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

function image(w, h, fn) {
	const px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = fn(x, y);
		const i = (y * w + x) * 4;
		if (!c) { px[i + 3] = 0; continue; }
		px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = c.length > 3 ? clamp(c[3]) : 255;
	}
	return px;
}
const write = (name, w, h, px) => { fs.writeFileSync(path.join(TEX, name + '.png'), encode(w, h, px)); console.log('wrote', name); };

// Uru metal: dark gunmetal, brushed horizontally, with a cold blue sheen catching a few streaks.
seed = 11;
write('stormbreaker_metal', 16, 16, image(16, 16, (x, y) => {
	const n = (rnd() - 0.5) * 10;
	const brushed = Math.sin(y * 1.7 + x * 0.15) * 4;
	let r = 70 + n + brushed, g = 77 + n + brushed, b = 92 + n + brushed;
	if ((x + y * 3) % 11 === 0) { r += 30; g += 42; b += 62; } // blue sheen glints
	if (y === 0 || x === 0) { r += 14; g += 16; b += 20; } // lit bevel
	if (y === 15 || x === 15) { r -= 16; g -= 16; b -= 14; } // shaded bevel
	return [r, g, b];
}));

// Edge / striking face: bright silver with a faint blue cast, polished toward the middle.
seed = 22;
write('stormbreaker_edge', 16, 16, image(16, 16, (x, y) => {
	const n = (rnd() - 0.5) * 8;
	const shine = 1 - Math.abs(x - 7.5) / 8;
	return [176 + shine * 50 + n, 188 + shine * 46 + n, 206 + shine * 40 + n];
}));

// Rune band: near-black Uru with thin glowing cyan rune strokes.
seed = 66;
write('stormbreaker_rune', 16, 16, image(16, 16, (x, y) => {
	const n = (rnd() - 0.5) * 6;
	const stroke = (x % 4 === 1 && y > 2 && y < 13) || (y === 4 && x % 8 < 3) || (y === 10 && (x + 4) % 8 < 3);
	if (stroke) return [110 + n, 205 + n, 235 + n];
	return [34 + n, 38 + n, 48 + n];
}));

// Handle: Groot-bark wood -- warm brown with dark vertical grooves and pale ridges.
seed = 33;
write('stormbreaker_handle', 16, 16, image(16, 16, (x, y) => {
	const n = (rnd() - 0.5) * 12;
	const wobble = Math.round(Math.sin(y * 0.6 + x) * 0.6);
	const col = (x + wobble + 16) % 4;
	let r = 96 + n, g = 66 + n * 0.8, b = 42 + n * 0.6;
	if (col === 0) { r -= 34; g -= 26; b -= 18; } // groove
	else if (col === 2) { r += 18; g += 14; b += 8; } // ridge
	return [r, g, b];
}));

// Grip: dark leather wrapped in diagonal bands.
seed = 44;
write('stormbreaker_grip', 16, 16, image(16, 16, (x, y) => {
	const n = (rnd() - 0.5) * 8;
	const band = (x + y) % 5;
	let r = 64 + n, g = 42 + n, b = 30 + n;
	if (band === 0) { r -= 22; g -= 16; b -= 12; } // gap between wraps
	else if (band === 2) { r += 22; g += 15; b += 10; } // lit edge of a wrap
	return [r, g, b];
}));

// Unforged Stormbreaker: a flat 16x16 tool sprite on the usual bottom-left -> top-right diagonal. Cold, dull grey
// metal head (axe blade on the upper side, hammer block on the lower), a rough dark core where the handle will be,
// and one dim orange glow line along the blade -- the heat it still needs.
(function unforged() {
	const c = { x: 7.6, y: 8.4 };
	const d = { x: Math.SQRT1_2, y: -Math.SQRT1_2 }; // along the handle, toward the top-right (head)
	const n = { x: Math.SQRT1_2, y: Math.SQRT1_2 }; // across the handle
	function shapeAt(px, py) {
		const ux = px - c.x, uy = py - c.y;
		const u = ux * d.x + uy * d.y; // along
		const v = ux * n.x + uy * n.y; // across (+v = lower-right = axe side)
		// head core
		if (u >= 1.8 && u <= 5.2 && Math.abs(v) <= 1.4) return { part: 'core', u, v };
		// hammer block (upper-left side)
		if (v <= -1.4 && v >= -4.0 && u >= 2.1 && u <= 4.9) return { part: v <= -3.3 ? 'hammer_face' : 'hammer', u, v };
		// axe blade (lower-right side), flaring toward the edge
		if (v >= 1.4 && v <= 4.9) {
			const flare = (v - 1.4) * 0.45;
			if (u >= 2.1 - flare && u <= 4.9 + flare) return { part: v >= 4.1 ? 'edge' : 'blade', u, v };
		}
		// stubby blaze-rod core / unfinished handle
		if (u >= -8.4 && u < 1.8 && Math.abs(v) <= 0.85) return { part: 'handle', u, v };
		return null;
	}
	const px = Buffer.alloc(16 * 16 * 4);
	seed = 55;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		// 4x4 supersampling: majority coverage decides the pixel, the most common part decides its colour
		const counts = {};
		let covered = 0, sample = null;
		for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) {
			const s = shapeAt(x + (sx + 0.5) / 4, y + (sy + 0.5) / 4);
			if (s) { covered++; counts[s.part] = (counts[s.part] || 0) + 1; sample = sample || s; }
		}
		const i = (y * 16 + x) * 4;
		const noise = (rnd() - 0.5) * 10;
		if (covered < 7) { px[i + 3] = 0; continue; }
		const part = Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
		const s = shapeAt(x + 0.5, y + 0.5) || sample;
		let col;
		if (part === 'handle') col = [74 + noise, 58 + noise, 46 + noise]; // soot-dark rod
		else if (part === 'core') col = [78 + noise, 80 + noise, 86 + noise];
		else if (part === 'hammer') col = [66 + noise, 68 + noise, 74 + noise];
		else if (part === 'hammer_face') col = [104 + noise, 106 + noise, 112 + noise];
		else if (part === 'blade') col = [92 + noise, 94 + noise, 100 + noise];
		else col = [190 + noise * 0.5, 96 + noise, 34 + noise]; // the dim orange glow line along the edge
		// simple top-left light
		if (s && part !== 'edge') {
			const shade = (-(s.v) * 2.0) - (s.u - 4) * 0.5;
			col = col.map((ch) => ch + shade);
		}
		px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = 255;
	}
	// a dark outline so it reads on any slot background
	const out = Buffer.from(px);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const i = (y * 16 + x) * 4;
		if (px[i + 3] !== 0) continue;
		let edge = false;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const nx = x + dx, ny = y + dy;
			if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[(ny * 16 + nx) * 4 + 3] !== 0) edge = true;
		}
		if (edge) { out[i] = 24; out[i + 1] = 22; out[i + 2] = 24; out[i + 3] = 255; }
	}
	write('unforged_stormbreaker', 16, 16, out);
})();

// ---------------- 3D model ----------------
// Same bounding-box convention as mjolnir.json / mjolnir_thrown.json: x/z centred on 8, the handle running up local
// +Y from y 0.5 to the head top at 14.375, so Mjolnir's display transforms (copied from minecraft:item/handheld_mace
// + minecraft:item/generated) carry over unchanged.
const ELEMENTS = [
	// [name, from, to, texture]
	['pommel', [6.9, 0.5, 6.9], [9.1, 1.5, 9.1], 'metal'],
	['handle', [7.3, 1.0, 7.3], [8.7, 10.5, 8.7], 'handle'],
	['grip', [7.05, 1.5, 7.05], [8.95, 6.75, 8.95], 'grip'],
	['grip_collar', [6.95, 6.75, 6.95], [9.05, 7.25, 9.05], 'metal'],
	['head_core', [6.6, 9.75, 6.8], [9.4, 14.375, 9.2], 'metal'],
	['rune_band', [6.5, 11.4, 6.7], [9.5, 12.6, 9.3], 'rune'],
	// the hammer side (-X here; buildElements mirrors it to +X): a squat block with a polished striking face
	['hammer', [4.7, 10.5, 6.6], [6.6, 13.6, 9.4], 'metal'],
	['hammer_face', [4.35, 10.7, 6.8], [4.7, 13.4, 9.2], 'edge'],
	// the axe side (+X here; mirrored to -X): a thin blade that widens away from the head into a tall bearded edge
	['blade_neck', [9.4, 10.8, 7.45], [10.6, 13.5, 8.55], 'metal'],
	['blade_inner', [10.6, 9.9, 7.55], [11.9, 14.375, 8.45], 'metal'],
	['blade_outer', [11.9, 8.9, 7.6], [13.2, 14.375, 8.4], 'edge'],
	['blade_edge', [13.2, 8.4, 7.68], [13.85, 14.375, 8.32], 'edge'],
];

function faceUv(w, h, k) {
	// sample a w x h window (clipped to the 16 px texture) from a slightly different spot per element
	w = Math.min(16, Math.max(0.5, w)); h = Math.min(16, Math.max(0.5, h));
	const u0 = Math.min(16 - w, (k * 3) % 13), v0 = Math.min(16 - h, (k * 5) % 11);
	const r = (v) => Math.round(v * 1000) / 1000;
	return [r(u0), r(v0), r(u0 + w), r(v0 + h)];
}

function buildElements(rotated) {
	return ELEMENTS.map(([name, from, to, tex], k) => {
		const sx = to[0] - from[0], sy = to[1] - from[1], sz = to[2] - from[2];
		const t = '#' + tex;
		const faces = {
			north: { uv: faceUv(sx, sy, k), texture: t },
			south: { uv: faceUv(sx, sy, k + 1), texture: t },
			east: { uv: faceUv(sz, sy, k + 2), texture: t },
			west: { uv: faceUv(sz, sy, k + 3), texture: t },
			up: { uv: faceUv(sx, sz, k + 4), texture: t },
			down: { uv: faceUv(sx, sz, k + 5), texture: t },
		};
		// v0.14.20: mirrored in X (x -> 16 - x) so the axe blade sits on -X and the hammer on +X. Through the -45 degree
		// icon-diagonal bake below that puts the blade on the same side as a vanilla axe sprite's (upper-left of the
		// head), so with the vanilla handheld transforms it is held like any axe -- blade DOWN / forward. With the blade on
		// +X (v0.14.19) it came out blade-up: the "upside down" Stormbreaker.
		from = [16 - to[0], from[1], from[2]];
		to = [16 - ELEMENTS[k][1][0], to[1], to[2]];
		const e = { from, to }; // (name kept in ELEMENTS for readability only; vanilla models have no such field)
		if (rotated) e.rotation = { origin: [8, 8, 8], axis: 'z', angle: -45, rescale: false };
		e.faces = faces;
		return e;
	});
}

const TEXTURES = {
	metal: 'projecthero:item/stormbreaker_metal',
	edge: 'projecthero:item/stormbreaker_edge',
	rune: 'projecthero:item/stormbreaker_rune',
	handle: 'projecthero:item/stormbreaker_handle',
	grip: 'projecthero:item/stormbreaker_grip',
	particle: 'projecthero:item/stormbreaker_metal',
};

const mjolnir = JSON.parse(fs.readFileSync(path.join(MODELS, 'mjolnir.json'), 'utf8'));
const mjolnirThrown = JSON.parse(fs.readFileSync(path.join(MODELS, 'mjolnir_thrown.json'), 'utf8'));

// v0.14.20: held exactly like Mjolnir and every vanilla tool -- the elements keep their -45 degree icon-diagonal bake
// and the hand contexts use the vanilla minecraft:item/handheld ROTATIONS unchanged ([0,-90,55] third person,
// [0,-90,25] first person, mirrored for the left hand as vanilla writes them). Only translation / scale are our own,
// for the model's size: a touch bigger than Mjolnir in third person, and in first person bigger than a vanilla tool
// but pushed left / down / away so the whole head shows instead of filling the right-hand corner. Checked side by side
// with Mjolnir and a vanilla iron axe in the client screenshot harness. gui: the plain vanilla tool icon diagonal (grip bottom-left, head top-right, blade showing). ground / head / fixed: Mjolnir's.
const DISPLAY = {
	thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 4.4, 1], scale: [1.1, 1.1, 1.1] },
	thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 4.4, 1], scale: [1.1, 1.1, 1.1] },
	firstperson_righthand: { rotation: [0, -90, 25], translation: [0.5, 5.2, -2.5], scale: [0.95, 0.95, 0.95] },
	firstperson_lefthand: { rotation: [0, 90, -25], translation: [0.5, 5.2, -2.5], scale: [0.95, 0.95, 0.95] },
	ground: mjolnir.display.ground,
	head: mjolnir.display.head,
	gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.85, 0.85, 0.85] },
	fixed: mjolnir.display.fixed,
};
const held = {
	credit: 'Stormbreaker 3D model (v0.14.19, hold reworked v0.14.20), generated by scratchpad/gen_v01419_stormbreaker_assets.js. Bounding box of mjolnir.json (x/z centred on 8, handle up local +Y from y 0.5 to 14.375), axe blade on -X and hammer face on +X (the vanilla axe sprite\'s sides), every element pre-rotated -45 degrees about Z (origin 8,8,8) onto the vanilla tool icon diagonal. Hand contexts use the vanilla minecraft:item/handheld rotations with our own translation / scale for the model\'s size; see the generator.',
	textures: TEXTURES,
	elements: buildElements(true),
	display: DISPLAY,
};
const thrown = {
	credit: 'Stormbreaker as rendered by StormbreakerEntityRenderer for the flying axe: same geometry as stormbreaker.json with NO per-element rotation (the renderer needs the raw handle-up +Y axis to tumble it about). Render-only, never in a creative tab.',
	textures: TEXTURES,
	elements: buildElements(false),
	display: mjolnirThrown.display,
};
fs.writeFileSync(path.join(MODELS, 'stormbreaker.json'), JSON.stringify(held, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'stormbreaker_thrown.json'), JSON.stringify(thrown, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'unforged_stormbreaker.json'), JSON.stringify({
	parent: 'minecraft:item/handheld',
	textures: { layer0: 'projecthero:item/unforged_stormbreaker' },
}, null, 2) + '\n');
console.log('wrote models');
