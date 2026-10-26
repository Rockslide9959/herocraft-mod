// v0.17.1 Chlorokinesis revamp: every texture the power draws -- the particle sprites (leaf / petal / spore / sap), the
// code-drawn geometry's textures (vine, root, leaf, petal, spore puff) and the Treant's bark skin + glowing eyes.
// Usage (from the repo root): node scratchpad/chlorokinesis_v0171/gen_chloro_v0171.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encode, preview } = require('../suits/skin_tools.js');

const ROOT = path.resolve(__dirname, '..', '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures');
const PART = path.join(TEX, 'particle');
const MUT = path.join(TEX, 'entity/mutation');
const PREV = path.join(__dirname, 'previews');
for (const d of [PART, MUT, PREV]) fs.mkdirSync(d, { recursive: true });

// deterministic noise
let seed = 1337;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const hash = (x, y, s = 0) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, Math.round(v)));
const mix = (a, b, t) => a + (b - a) * t;

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function put(im, x, y, r, g, b, a = 255) {
	if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const i = (y * im.w + x) * 4; im.px[i] = clamp(r); im.px[i + 1] = clamp(g); im.px[i + 2] = clamp(b); im.px[i + 3] = clamp(a);
}
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function save(im, file, scale = 12) {
	fs.writeFileSync(file, encode(im.w, im.h, im.px));
	const pv = preview(im, scale);
	fs.writeFileSync(path.join(PREV, path.basename(file)), encode(pv.w, pv.h, pv.px));
}

// ---------------- the leaf shape (used by the leaf particle, the geometry leaf, the canopy) ----------------
/** A pointed leaf along the diagonal of an n x n tile: green body, lighter midrib, darker edge. */
function leaf(n, base, light, dark, rot = 0) {
	const im = img(n, n);
	const c = (n - 1) / 2;
	for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
		// rotate into leaf space: u along the leaf (-1..1), v across
		const dx = (x - c) / c, dy = (y - c) / c;
		const ca = Math.cos(rot), sa = Math.sin(rot);
		const u = dx * ca + dy * sa, v = -dx * sa + dy * ca;
		const halfW = 0.55 * Math.pow(Math.max(0, 1 - u * u), 0.8) * (u < 0 ? 1 - 0.25 * -u : 1);
		if (Math.abs(v) > halfW || Math.abs(u) > 0.98) continue;
		const edge = Math.abs(v) / Math.max(halfW, 1e-3);
		let t = 0.15 + 0.6 * (1 - edge) + (hash(x, y, 7) - 0.5) * 0.15;
		let col = [mix(dark[0], base[0], t), mix(dark[1], base[1], t), mix(dark[2], base[2], t)];
		if (Math.abs(v) < 0.08 && u > -0.95) col = light; // the midrib
		else if (edge > 0.82) col = dark;
		// side veins
		const vein = Math.abs(((u * 3 + Math.abs(v) * 2.2) % 1 + 1) % 1 - 0.5);
		if (vein < 0.07 && edge < 0.75 && Math.abs(v) > 0.1) col = [mix(col[0], light[0], 0.5), mix(col[1], light[1], 0.5), mix(col[2], light[2], 0.5)];
		put(im, x, y, col[0], col[1], col[2], 255);
	}
	// the stalk
	const sx = Math.round(c - Math.cos(rot) * c * 0.98), sy = Math.round(c - Math.sin(rot) * c * 0.98);
	put(im, sx, sy, dark[0], dark[1], dark[2], 255);
	return im;
}

// ---------------- particles (8x8) ----------------
const LEAF_COLS = [
	[[92, 168, 52], [168, 222, 110], [40, 92, 26]],
	[[70, 146, 44], [140, 206, 92], [30, 78, 20]],
	[[128, 186, 58], [198, 236, 128], [62, 110, 30]],
	[[168, 182, 54], [222, 228, 120], [96, 104, 28]], // a yellowing one
];
LEAF_COLS.forEach((c, i) => save(leaf(8, c[0], c[1], c[2], Math.PI / 4 + (i % 2 ? 0.25 : -0.15)), path.join(PART, `chloro_leaf_${i}.png`)));

function petal(n, col, rot) {
	const im = img(n, n);
	const c = (n - 1) / 2;
	for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
		const dx = (x - c) / c, dy = (y - c) / c;
		const ca = Math.cos(rot), sa = Math.sin(rot);
		const u = dx * ca + dy * sa, v = -dx * sa + dy * ca;
		// a teardrop: wide and round at +u, pinched at -u
		const w = 0.62 * Math.sqrt(Math.max(0, (1 - u) * (1 + u))) * (0.55 + 0.45 * (u + 1) / 2);
		if (Math.abs(v) > w || u < -0.95 || u > 0.95) continue;
		const e = Math.abs(v) / Math.max(w, 1e-3);
		const t = 0.75 + 0.25 * (1 - e) - (u < -0.4 ? 0.2 : 0);
		put(im, x, y, col[0] * t, col[1] * t, col[2] * t, 255);
	}
	return im;
}
[[255, 172, 208], [255, 246, 250], [240, 110, 178]].forEach((c, i) => save(petal(8, c, Math.PI / 4 + i * 0.6), path.join(PART, `chloro_petal_${i}.png`)));

// spores: four frames, a glowing mote that swells and fades (drawn white-yellow-green, tinted further in code)
for (let f = 0; f < 4; f++) {
	const im = img(8, 8);
	const r = 1.4 + f * 0.7;
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		const d = Math.hypot(x - 3.5, y - 3.5);
		if (d > r + 0.6) continue;
		const core = Math.max(0, 1 - d / (r + 0.6));
		put(im, x, y, mix(150, 255, core), mix(200, 255, core), mix(60, 170, core), 255 * Math.min(1, core * 1.6) * (1 - f * 0.12));
	}
	save(im, path.join(PART, `chloro_spore_${f}.png`));
}

// sap: three frames, a bright drop with a white glint
for (let f = 0; f < 3; f++) {
	const im = img(8, 8);
	const r = 2.6 - f * 0.6;
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		const dx = x - 3.5, dy = (y - 4.0) * (y < 4 ? 0.7 : 1.0);
		const d = Math.hypot(dx, dy);
		if (d > r) continue;
		const core = 1 - d / r;
		put(im, x, y, mix(60, 210, core), mix(200, 255, core), mix(40, 150, core), 255);
	}
	put(im, 3, 3, 255, 255, 240, 255);
	save(im, path.join(PART, `chloro_sap_${f}.png`));
}

// ---------------- geometry textures (16x16, 32x32) ----------------
// vine: a tileable green stem, u = round the tube, v = along it
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const round = Math.sin((x + 0.5) / 16 * Math.PI); // lit in the middle of the band
		const streak = hash(x, 0, 3) * 0.5 + 0.5 * Math.sin((y + x * 0.7) / 16 * Math.PI * 4) * 0.25;
		const node = (y % 8 === 0) ? 0.7 : 1.0;
		const t = (0.45 + 0.55 * round) * node * (0.85 + streak * 0.3);
		put(im, x, y, mix(28, 118, t), mix(64, 186, t), mix(18, 62, t), 255);
	}
	save(im, path.join(MUT, 'p22_vine.png'));
}
// root: tileable brown bark with twisting grooves
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const round = Math.sin((x + 0.5) / 16 * Math.PI);
		const groove = Math.abs(Math.sin(((x + y * 0.5) / 16) * Math.PI * 3));
		const n = hash(x, y, 11);
		const t = (0.35 + 0.55 * round) * (groove < 0.25 ? 0.55 : 1) * (0.88 + n * 0.24);
		put(im, x, y, mix(46, 150, t), mix(30, 104, t), mix(18, 64, t), 255);
	}
	save(im, path.join(MUT, 'p22_root.png'));
}
// the geometry leaf (16x16, pointing up the v axis) and a flower petal (16x16, white, tinted in code)
save(leaf(16, [96, 176, 56], [176, 230, 120], [36, 90, 24], -Math.PI / 2), path.join(MUT, 'p22_leaf.png'));
save(petal(16, [255, 255, 255], -Math.PI / 2), path.join(MUT, 'p22_petal.png'));
// a soft spore puff: radial alpha with a little speckle
{
	const im = img(32, 32);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const d = Math.hypot(x - 15.5, y - 15.5) / 15.5;
		if (d > 1) continue;
		const a = Math.pow(1 - d, 1.6) * 255 * (0.8 + hash(x, y, 5) * 0.4);
		const sp = hash(x >> 1, y >> 1, 9) > 0.86 ? 1 : 0;
		// premultiplied: it is drawn additively (RenderType.eyes), so the colour itself must fade to black at the rim
		const k = Math.min(255, a) / 255;
		put(im, x, y, (sp ? 250 : 210) * k, (sp ? 255 : 230) * k, (sp ? 170 : 120) * k, a);
	}
	save(im, path.join(MUT, 'p22_spore.png'), 6);
}

// ---------------- the Treant: 64x64 player skin of bark, moss and a hollow face ----------------
{
	const im = img(64, 64);
	const bark = (x, y) => {
		// vertical bark plates split by dark furrows that wander a little down the trunk
		const wob = Math.sin(y * 0.45 + (x >> 2) * 1.7) * 0.8;
		const fx = ((x + wob) % 3 + 3) % 3;
		const furrow = fx < 0.9;
		const n = hash(x, y >> 1, 21);
		let t = furrow ? 0.28 + n * 0.12 : 0.62 + n * 0.3;
		if (!furrow && hash(x, y, 23) > 0.85) t += 0.15; // lit plate edges
		return [mix(30, 118, t), mix(20, 84, t), mix(12, 56, t)];
	};
	// base layer regions (64x64 classic): head 0-32 x 0-16, body/arm/leg rows 16-32 and 48-64
	const base = (x, y) => (y < 16 && x < 32) || (y >= 16 && y < 32) || (y >= 48 && x >= 16 && x < 48);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		if (!base(x, y)) continue;
		const [r, g, b] = bark(x, y);
		put(im, x, y, r, g, b, 255);
		// moss patches on top surfaces and shoulders
		if (hash(x >> 1, y >> 1, 31) > 0.9) {
			const m = 0.7 + hash(x, y, 32) * 0.3;
			put(im, x, y, 70 * m, 130 * m, 40 * m, 255);
		}
	}
	// the top of the head is all moss (the crown sits on it)
	for (let y = 0; y < 8; y++) for (let x = 8; x < 16; x++) {
		const m = 0.7 + hash(x, y, 33) * 0.3;
		put(im, x, y, 64 * m, 124 * m, 36 * m, 255);
	}
	// face (head front 8-16 x 8-16): deep hollow eye sockets and a gnarled mouth line
	for (const [ex, ey] of [[9, 11], [10, 11], [13, 11], [14, 11], [9, 12], [10, 12], [13, 12], [14, 12]]) put(im, ex, ey, 14, 9, 5, 255);
	for (const [mx, my] of [[10, 14], [11, 14], [12, 14], [13, 14]]) put(im, mx, my, 22, 14, 8, 255);
	// a brow ridge
	for (let x = 8; x < 16; x++) { const [r, g, b] = get(im, x, 10); put(im, x, 10, r * 1.25, g * 1.2, b * 1.15, 255); }
	// the overlay layer: hanging moss and a few leaves on the jacket / sleeves / pants (cutout)
	const over = (x, y) => (y >= 32 && y < 48) || (y >= 48 && (x < 16 || x >= 48));
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		if (!over(x, y)) continue;
		if (hash(x, y >> 2, 41) > 0.8) {
			const m = 0.65 + hash(x, y, 42) * 0.35;
			put(im, x, y, 76 * m, 142 * m, 44 * m, 255);
		}
	}
	save(im, path.join(MUT, 'p22_treant.png'), 8);
	// glowing eyes (drawn full-bright)
	const eyes = img(64, 64);
	for (const [ex, ey] of [[9, 11], [10, 11], [13, 11], [14, 11]]) put(eyes, ex, ey, 190, 255, 120, 255);
	for (const [ex, ey] of [[9, 12], [14, 12]]) put(eyes, ex, ey, 120, 220, 80, 255);
	save(eyes, path.join(MUT, 'p22_treant_eyes.png'), 8);
}

// ---------------- the particle definitions ----------------
const PJSON = path.join(ROOT, 'src/main/resources/assets/projecthero/particles');
fs.mkdirSync(PJSON, { recursive: true });
const defs = { chloro_leaf: 4, chloro_petal: 3, chloro_spore: 4, chloro_sap: 3 };
for (const [name, n] of Object.entries(defs)) {
	const textures = []; for (let i = 0; i < n; i++) textures.push(`projecthero:${name}_${i}`);
	fs.writeFileSync(path.join(PJSON, `${name}.json`), JSON.stringify({ textures }, null, 2) + '\n');
}
console.log('chlorokinesis textures written');
