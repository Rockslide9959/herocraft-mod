// v0.14.19 Stormbreaker (rebuilt v0.16.1): generates the item models (held + thrown) and all textures.
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

// v0.16.1: every texture is 32x32 now (Mjolnir's metal is 32x32 too) -- twice the detail per model pixel, which is
// most of what made the old 16x16 Stormbreaker read as blocky next to the hammer. UVs stay in the usual 0..16 space.
const T = 32;
const hash2 = (x, y, s) => { const v = Math.sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453; return v - Math.floor(v); };
const vnoise = (x, y, s) => { // smooth value noise
	const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
	const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
	const a = hash2(xi, yi, s), b = hash2(xi + 1, yi, s), c = hash2(xi, yi + 1, s), d = hash2(xi + 1, yi + 1, s);
	return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
};

// Uru metal: dark blued steel, finely brushed along the blade, darker mottling, a cold blue sheen in long streaks.
seed = 11;
write('stormbreaker_metal', T, T, image(T, T, (x, y) => {
	const n = (rnd() - 0.5) * 7;
	const mott = (vnoise(x / 6, y / 6, 3) - 0.5) * 18;
	const brushed = (vnoise(x / 9, y * 1.3, 5) - 0.5) * 12;
	const streak = Math.max(0, Math.sin((x * 0.35 + y * 0.9) * 0.7) - 0.75) * 90;
	let r = 86 + n + mott + brushed, g = 94 + n + mott + brushed, b = 114 + n + mott + brushed;
	r += streak * 0.35; g += streak * 0.5; b += streak * 0.8;
	if (y <= 1 || x <= 1) { r += 16; g += 18; b += 22; } // lit bevel
	if (y >= T - 2 || x >= T - 2) { r -= 18; g -= 18; b -= 14; } // shaded bevel
	return [r, g, b];
}));

// Bevel: the ground facet between the dark blade and the cutting edge -- mid steel, lightening toward one side.
seed = 17;
write('stormbreaker_bevel', T, T, image(T, T, (x, y) => {
	const n = (rnd() - 0.5) * 6;
	const k = x / (T - 1);
	const grind = (vnoise(x * 0.4, y * 2.2, 9) - 0.5) * 14; // grinding marks across
	return [124 + k * 52 + n + grind, 134 + k * 52 + n + grind, 152 + k * 48 + n + grind];
}));

// Edge: the honed cutting edge -- bright silver-white with a cold blue cast and a hot specular line.
seed = 22;
write('stormbreaker_edge', T, T, image(T, T, (x, y) => {
	const n = (rnd() - 0.5) * 6;
	const shine = 1 - Math.abs(x - 15.5) / 16;
	const spec = Math.abs(x - 19) < 1.5 ? 28 : 0;
	return [178 + shine * 50 + n + spec, 192 + shine * 46 + n + spec, 212 + shine * 40 + n + spec];
}));

// Rune: near-black Uru with glowing cyan Norse-style strokes (vertical staves with branching twigs) and a soft halo.
seed = 66;
write('stormbreaker_rune', T, T, image(T, T, (x, y) => {
	const n = (rnd() - 0.5) * 5;
	const col = x % 8, row = y % 16;
	const stave = col === 3 && row > 1 && row < 14;
	const twig = (row === 4 + (x >> 3) % 3 && (col === 4 || col === 5)) || (row === 9 - (x >> 3) % 2 && (col === 1 || col === 2))
		|| (row === 11 && col === 4);
	if (stave || twig) return [150 + n, 232 + n, 255];
	// halo next to a stroke
	const near = (Math.abs(col - 3) === 1 && row > 1 && row < 14);
	if (near) return [52 + n, 104 + n, 140 + n];
	return [30 + n, 34 + n, 44 + n];
}));

// Handle: Groot's branch -- warm bark with twisting, slanted fibre grooves, pale ridges and the odd dark knot.
seed = 33;
write('stormbreaker_handle', T, T, image(T, T, (x, y) => {
	const n = (rnd() - 0.5) * 8;
	const twist = x + y * 0.35 + Math.sin(y * 0.4) * 1.6 + (vnoise(x * 0.3, y * 0.15, 2) - 0.5) * 3;
	const fibre = Math.sin(twist * 1.55);
	let r = 118 + n, g = 84 + n * 0.8, b = 54 + n * 0.6;
	if (fibre < -0.55) { r -= 40; g -= 31; b -= 21; } // groove
	else if (fibre > 0.65) { r += 20; g += 16; b += 9; } // ridge
	const knot = Math.hypot(x - 22, (y % 32) - 9);
	if (knot < 2.6) { const k = 1 - knot / 2.6; r -= 46 * k; g -= 36 * k; b -= 24 * k; }
	const moss = vnoise(x / 5, y / 5, 7);
	if (moss > 0.82) { r -= 18; g += 4; b -= 12; } // a hint of living green
	return [r, g, b];
}));

// Grip: dark leather strap wound diagonally, each wrap shaded round, a stitched line down the strap.
seed = 44;
write('stormbreaker_grip', T, T, image(T, T, (x, y) => {
	const n = (rnd() - 0.5) * 6;
	const band = ((x + y * 1.0) % 8 + 8) % 8; // 8 px per wrap
	const round = Math.sin(band / 8 * Math.PI); // lit across the middle of each wrap
	let r = 46 + round * 36 + n, g = 30 + round * 24 + n, b = 22 + round * 16 + n;
	if (band < 1) { r = 20 + n; g = 13 + n; b = 10 + n; } // the gap between wraps
	if (band === 4 && (y % 3 === 0)) { r += 40; g += 30; b += 20; } // stitching
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
// +Y from y 0.5 to the head (the blade's horn now reaches a little past 14.375), so Mjolnir's display transforms
// (copied from minecraft:item/handheld_mace + minecraft:item/generated) carry over.
//
// v0.16.1 rebuild (the old 12-box axe read as blocky next to Mjolnir): the MCU Stormbreaker --
//  - a crescent axe blade built from half-pixel rows, each row three stepped boxes (dark Uru body, a lighter ground
//    bevel, a thin bright cutting edge), so the edge curves in an arc, the upper horn sweeps up and the bearded lower
//    point droops down along the haft, and the back of the blade curves away from the handle (a hollow crescent);
//  - a rune strip glowing down the flat of the blade and a rune band round the head;
//  - the hammer block on the back: a stepped, tapering poll with a polished striking face;
//  - Groot's branch for a handle: stacked, slightly offset segments that wander and twist, with knots;
//  - a leather-wrapped grip of alternating wraps, a metal collar and a capped pommel.
// Built here on +X (axe) / -X (hammer) and mirrored by buildElements.
const ELEMENTS = [];
const box = (name, from, to, tex) => ELEMENTS.push([name, from, to, tex]);
const r3 = (v) => Math.round(v * 1000) / 1000;

// head core, a cap and a crown knob, the rune band
box('head_core', [6.55, 9.6, 6.75], [9.45, 14.35, 9.25], 'metal');
box('head_cap', [6.8, 14.35, 7.0], [9.2, 14.75, 9.0], 'bevel');
box('head_knob', [7.45, 14.75, 7.45], [8.55, 15.2, 8.55], 'edge');
box('head_collar', [6.75, 9.2, 6.95], [9.25, 9.6, 9.05], 'bevel');
box('rune_band', [6.45, 11.45, 6.65], [9.55, 12.55, 9.35], 'rune');

// the blade, row by row
const BLADE_Y0 = 7.6, BLADE_Y1 = 15.0, ROW = 0.5;
const yMid = 11.5;
for (let y = BLADE_Y0; y < BLADE_Y1 - 1e-6; y += ROW) {
	const yc = y + ROW / 2;
	const t = (yc - yMid) / ((BLADE_Y1 - BLADE_Y0) / 2); // -1 (beard tip) .. 1 (horn tip)
	if (Math.abs(t) > 1) continue;
	// the cutting edge: a convex arc, reaching furthest a little below the middle (an MCU-style bearded axe)
	const edge = 11.6 + 3.4 * Math.sqrt(Math.max(0, 1 - Math.pow((yc - 11.1) / 3.95, 2)));
	// the back of the blade: attached to the head across its middle, curving away from the haft toward both points
	const off = Math.max(0, Math.abs(yc - 11.9) - 1.55);
	const inner = 9.45 + 2.2 * Math.pow(off / 2.3, 1.35) + (yc < 9.6 ? 0.4 : 0);
	const width = edge - inner;
	if (width < 0.7) continue;
	// thickness tapers toward the tips
	const th = 0.42 - 0.12 * Math.abs(t);
	const edgeW = Math.min(0.75, width * 0.32);
	const bevelW = Math.min(1.2, (width - edgeW) * 0.5);
	const bodyTo = edge - edgeW - bevelW;
	const y0 = r3(y), y1 = r3(y + ROW);
	if (bodyTo > inner + 0.05) box('blade_body', [r3(inner), y0, r3(8 - th)], [r3(bodyTo), y1, r3(8 + th)], 'metal');
	box('blade_bevel', [r3(Math.max(inner, bodyTo)), y0, r3(8 - th * 0.72)], [r3(edge - edgeW), y1, r3(8 + th * 0.72)], 'bevel');
	box('blade_edge', [r3(edge - edgeW), y0, r3(8 - th * 0.38)], [r3(edge), y1, r3(8 + th * 0.38)], 'edge');
}
// the glowing rune strip down the flat of the blade (both faces), just proud of it
box('blade_rune', [10.6, 9.9, 7.5], [11.2, 13.4, 8.5], 'rune');
// the throat where the blade meets the head
box('blade_throat', [9.45, 10.4, 7.4], [10.1, 13.2, 8.6], 'metal');

// the hammer poll on the back: stepped down toward a polished face
box('poll_root', [5.7, 10.1, 6.6], [6.55, 13.9, 9.4], 'metal');
box('poll', [4.75, 10.4, 6.75], [5.7, 13.6, 9.25], 'metal');
box('poll_rim', [4.45, 10.55, 6.9], [4.75, 13.45, 9.1], 'bevel');
box('poll_face', [4.2, 10.75, 7.1], [4.45, 13.25, 8.9], 'bevel');
box('poll_rune', [5.0, 11.5, 6.68], [5.5, 12.5, 9.32], 'rune');

// Groot's branch: segments that wander a little in x / z and change girth, with two knots
const HANDLE_Y0 = 5.9, HANDLE_Y1 = 9.25;
for (let y = HANDLE_Y0, i = 0; y < HANDLE_Y1 - 1e-6; y += 0.85, i++) {
	const dx = Math.sin(i * 1.7) * 0.12, dz = Math.cos(i * 1.3) * 0.12;
	const g = 0.66 + 0.06 * Math.sin(i * 2.1);
	box('branch', [r3(8 - g + dx), r3(y), r3(8 - g + dz)], [r3(8 + g + dx), r3(Math.min(HANDLE_Y1, y + 0.85)), r3(8 + g + dz)], 'handle');
}
box('knot', [8.55, 7.4, 7.6], [8.95, 7.9, 8.2], 'handle');
box('knot', [7.2, 8.5, 7.15], [7.7, 8.9, 7.45], 'handle');
// a root of the branch running up into the head on one side
box('root', [7.05, 8.6, 7.35], [7.45, 9.6, 7.85], 'handle');

// the grip: leather wraps (alternating girth so each wrap stands out), a collar, the pommel
box('grip_core', [7.3, 1.3, 7.3], [8.7, 5.9, 8.7], 'grip');
for (let y = 1.4, i = 0; y < 5.7; y += 0.55, i++) {
	const g = i % 2 === 0 ? 0.92 : 0.82;
	box('wrap', [r3(8 - g), r3(y), r3(8 - g)], [r3(8 + g), r3(y + 0.42), r3(8 + g)], 'grip');
}
box('collar', [7.05, 5.8, 7.05], [8.95, 6.25, 8.95], 'bevel');
box('pommel', [6.95, 0.55, 6.95], [9.05, 1.35, 9.05], 'metal');
box('pommel_cap', [7.35, 0.3, 7.35], [8.65, 0.55, 8.65], 'edge');

/** Block-projected UVs (like Blockbench's auto-UV): neighbouring rows / segments sample neighbouring texels, so the
 * grain, the wraps and the brushing run on continuously across the stepped boxes instead of restarting per box. */
function projUv(a0, a1, b0, b1) {
	const c = (v) => Math.max(0, Math.min(16, r3(v)));
	return [c(a0), c(b0), c(a1), c(b1)];
}

function buildElements(rotated) {
	return ELEMENTS.map(([name, from, to, tex], k) => {
		const t = '#' + tex;
		const [x0, y0, z0] = from;
		const [x1, y1, z1] = to;
		const faces = {
			north: { uv: projUv(16 - x1, 16 - x0, 16 - y1, 16 - y0), texture: t },
			south: { uv: projUv(x0, x1, 16 - y1, 16 - y0), texture: t },
			east: { uv: projUv(16 - z1, 16 - z0, 16 - y1, 16 - y0), texture: t },
			west: { uv: projUv(z0, z1, 16 - y1, 16 - y0), texture: t },
			up: { uv: projUv(x0, x1, z0, z1), texture: t },
			down: { uv: projUv(x0, x1, 16 - z1, 16 - z0), texture: t },
		};
		// v0.14.20: mirrored in X (x -> 16 - x) so the axe blade sits on -X and the hammer on +X. Through the -45 degree
		// icon-diagonal bake below that puts the blade on the same side as a vanilla axe sprite's (upper-left of the
		// head), so with the vanilla handheld transforms it is held like any axe -- blade DOWN / forward. With the blade on
		// +X (v0.14.19) it came out blade-up: the "upside down" Stormbreaker.
		const mf = [r3(16 - x1), y0, z0];
		const mt = [r3(16 - x0), y1, z1];
		const e = { from: mf, to: mt };
		if (rotated) e.rotation = { origin: [8, 8, 8], axis: 'z', angle: -45, rescale: false };
		e.faces = faces;
		return e;
	});
}

const TEXTURES = {
	metal: 'projecthero:item/stormbreaker_metal',
	bevel: 'projecthero:item/stormbreaker_bevel',
	edge: 'projecthero:item/stormbreaker_edge',
	rune: 'projecthero:item/stormbreaker_rune',
	handle: 'projecthero:item/stormbreaker_handle',
	grip: 'projecthero:item/stormbreaker_grip',
	particle: 'projecthero:item/stormbreaker_metal',
};
console.log('elements', ELEMENTS.length);

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
	credit: 'Stormbreaker 3D model (v0.14.19, hold reworked v0.14.20, rebuilt v0.16.1: crescent blade of stepped half-pixel rows with bevel + cutting edge, stepped hammer poll, Groot-branch haft, leather wraps, 32x32 textures), generated by scratchpad/gen_v01419_stormbreaker_assets.js. Bounding box of mjolnir.json (x/z centred on 8, handle up local +Y from y 0.5 to 14.375), axe blade on -X and hammer face on +X (the vanilla axe sprite\'s sides), every element pre-rotated -45 degrees about Z (origin 8,8,8) onto the vanilla tool icon diagonal. Hand contexts use the vanilla minecraft:item/handheld rotations with our own translation / scale for the model\'s size; see the generator.',
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
