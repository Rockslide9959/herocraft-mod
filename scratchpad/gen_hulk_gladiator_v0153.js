// v0.15.3: Gladiator Hulk -- adds the gladiator gear bones to geo/hulk.geo.json and paints their texture
// textures/entity/hulk_gladiator.png. Idempotent: every bone whose name starts with "gladiator" is removed first, then
// re-added, so re-running gen_hulk.js (which rebuilds hulk.geo.json from the user's bbmodel) followed by this script
// puts the gear back. Every existing Hulk bone and animation is untouched.
//
// The gear bones use per-face UVs into their OWN texture (hulk_gladiator.png, 256x256 over the geo's 64x64 UV space;
// every face is painted at 2 texels per model pixel). HulkRenderer's main pass hides them (hulk.png is not touched -- it is also the first-person
// arm skin); client/hulk/HulkGladiatorLayer draws only them with this texture while the player is Gladiator Hulk.
//
// Run from the repo root: node scratchpad/gen_hulk_gladiator_v0153.js
const fs = require('fs');
const path = require('path');
const L = require('./pnglib.js');

const ROOT = path.join(__dirname, '..');
const GEO = path.join(ROOT, 'src/main/resources/assets/projecthero/geo/hulk.geo.json');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/hulk_gladiator.png');
const TS = 256; // texture size in texels
const K = TS / 64; // texels per UV unit
const D = 2; // texels painted per model pixel (uv_size = face size * D / K)

// ------------------------------------------------------------------ palette + noise
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const C = {
	ironK: hex('#16171a'), ironD: hex('#2a2c31'), iron: hex('#3b3e45'), ironL: hex('#565a63'), ironH: hex('#7a7f89'),
	goldD: hex('#7a5a1c'), gold: hex('#b88f2e'), goldL: hex('#e2b852'), goldH: hex('#f6dc8a'),
	leaD: hex('#2e1d12'), lea: hex('#4f3220'), leaL: hex('#6e4a30'), stitch: hex('#a07a52'),
	redD: hex('#5c0e0e'), red: hex('#8e1a18'), redL: hex('#bb2e24'), redH: hex('#d9503a'),
	steelD: hex('#5d636b'), steel: hex('#8c939c'), steelL: hex('#b9c0c8'), steelH: hex('#e6ebef'),
	woodD: hex('#24170f'), wood: hex('#3b2618'),
	white: hex('#ece6d6'), yellow: hex('#e9c23a'), paintRed: hex('#b3201a'), clear: [0, 0, 0, 0],
};
let seed = 1234567;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const jit = (c, amt, x, y, s) => { const d = (hash(x, y, s) - 0.5) * 2 * amt; return [clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d), c[3]]; };
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => [clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), clamp(a[3] + (b[3] - a[3]) * t)];

// ------------------------------------------------------------------ materials: (u, v, w, h, face, ctx) -> RGBA
// u, v are texel coords inside the face (0..w-1, 0..h-1), v = 0 at the top of a side face.
function edge(u, v, w, h) { return Math.min(u, v, w - 1 - u, h - 1 - v); }
const MAT = {
	iron(u, v, w, h, face, s) {
		const e = edge(u, v, w, h);
		let c = jit(C.iron, 7, u, v, s);
		if (v === 0 && face !== 'up' && face !== 'down') c = C.ironL;
		if (e === 0) c = mix(c, C.ironK, 0.55);
		// faint brushed streaks
		if (hash(0, v, s + 3) > 0.8) c = mix(c, C.ironD, 0.35);
		return c;
	},
	ironTrim(u, v, w, h, face, s) {
		const e = edge(u, v, w, h);
		if (w >= 4 && h >= 4 && e === 0) return jit(v === 0 ? C.goldL : C.gold, 10, u, v, s);
		if (w >= 4 && h >= 4 && e === 1) return C.goldD;
		let c = jit(C.iron, 7, u, v, s);
		if (hash(0, v, s + 3) > 0.8) c = mix(c, C.ironD, 0.35);
		// rivets near the corners of bigger faces
		if (w >= 8 && h >= 8 && e === 2 && ((u === 2 || u === w - 3) && (v === 2 || v === h - 3))) return C.goldH;
		return c;
	},
	gold(u, v, w, h, face, s) {
		const e = edge(u, v, w, h);
		// brushed gold: a soft top-to-bottom gradient, a light top edge, a dark bottom / right edge
		const t = h <= 1 ? 0.5 : v / (h - 1);
		let c = jit(mix(C.goldL, C.goldD, 0.15 + t * 0.6), 5, u, v, s);
		if (v === 0 || u === 0) c = C.goldL;
		if (v === h - 1 || u === w - 1) c = C.goldD;
		if (e === 0 && w > 2 && h > 2 && hash(u, v, s + 9) > 0.85) c = C.goldH;
		return c;
	},
	leather(u, v, w, h, face, s) {
		const e = edge(u, v, w, h);
		let c = jit(C.lea, 9, u, v, s);
		if (e === 0) c = C.leaD;
		else if (e === 1 && (u + v) % 2 === 0 && w > 3 && h > 3) c = C.stitch;
		return c;
	},
	red(u, v, w, h, face, s) {
		let c = jit(C.red, 10, u, v, s);
		const strand = hash(u, 0, s + 5);
		if (strand > 0.7) c = C.redL;
		if (strand < 0.2) c = C.redD;
		if (v === 0 && face !== 'up' && face !== 'down') c = C.redH;
		return c;
	},
	cloth(u, v, w, h, face, s) {
		// the red war-kilt flaps: red cloth, gold-trimmed hem, frayed bottom
		if (v >= h - 2) return (u % 2 === 0) ? C.goldL : C.gold;
		if (u === 0 || u === w - 1) return C.redD;
		let c = jit(C.red, 9, u, v, s);
		if (hash(u, 0, s + 1) > 0.75) c = C.redL;
		if (v === 0) c = C.redD;
		return c;
	},
	steel(u, v, w, h, face, s) {
		// blade: darker at the top (towards the haft), polished bright edge at the bottom
		const t = h <= 1 ? 1 : v / (h - 1);
		let c = mix(C.steelD, C.steelL, t);
		c = jit(c, 6, u, v, s);
		if (v === h - 1 && face !== 'up' && face !== 'down') c = C.steelH;
		return c;
	},
	edge(u, v, w, h, face, s) {
		const t = h <= 1 ? 1 : v / (h - 1);
		let c = jit(mix(C.steel, C.steelH, t), 5, u, v, s);
		if (v === 0 && face !== 'up' && face !== 'down') c = C.steelD;
		return c;
	},
	wrap(u, v, w, h, face, s) {
		// the haft: dark wood, bound in leather strips every few texels along its length
		const along = (face === 'up' || face === 'down' || face === 'east' || face === 'west') ? Math.max(u, v) === u && w > h ? u : v : v;
		const k = (face === 'north' || face === 'south') ? 0 : along;
		if (k % 6 < 3) return jit((k % 6 === 0) ? C.leaL : C.lea, 6, u, v, s);
		return jit(C.wood, 6, u, v, s);
	},
	bracer(u, v, w, h, face, s) {
		if (face === 'up' || face === 'down') return C.clear;
		// leather wrap with two iron bands and studs
		const band = (v >= 2 && v <= 3) || (v >= h - 5 && v <= h - 4);
		if (band) return jit(v % 2 === 0 ? C.ironL : C.iron, 6, u, v, s);
		if (v === h / 2 && u % 3 === 1) return C.goldL;
		let c = jit(C.lea, 9, u, v, s);
		if (hash(0, v, s) > 0.75) c = C.leaD;
		return c;
	},
	kilt(u, v, w, h, face, s) {
		if (face === 'up' || face === 'down') return C.clear;
		// vertical leather strips, each with an iron stud, one-texel gaps between them (the leg shows through)
		const sw = 3;
		if (u % (sw + 1) === sw) return C.clear;
		if (v === h - 1 && hash(u, v, s) > 0.5) return C.clear; // ragged hem
		if (v === h - 3 && u % (sw + 1) === 1) return C.ironH;
		let c = jit(u % (sw + 1) === 0 ? C.leaL : C.lea, 8, u, v, s);
		if (v === 0) c = C.leaD;
		return c;
	},
	clear() { return C.clear; },
};

// ------------------------------------------------------------------ decals (whole-face painters)
// War paint on the face: red band under the eyes sweeping up at the temples, white drips on the cheeks, yellow dots.
// Face texel grid 16x16 (2 per head pixel). Eyes are head rows 4 (texel rows 8-9), cols 1-2 and 5-6 (texels 2-5, 10-13).
function warPaint(u, v) {
	const P = [
		'................',
		'................',
		'................',
		'................',
		'................',
		'y..............y',
		'rr............rr',
		'rr............rr',
		'rr............rr',
		'.r............r.',
		'.rrrrrr..rrrrrr.',
		'..rrrrr..rrrrr..',
		'..w.w.w..w.w.w..',
		'..w.w.w..w.w.w..',
		'....w..yy..w....',
		'.......yy.......',
	];
	const ch = P[v] && P[v][u];
	if (ch === 'r') return jit(C.paintRed, 10, u, v, 77);
	if (ch === 'w') return C.white;
	if (ch === 'y') return C.yellow;
	return C.clear;
}

function beltFront(u, v, w, h) {
	const cx = w / 2;
	if (Math.abs(u + 0.5 - cx) <= 3 && v >= 1 && v <= h - 2) return null; // buckle cube sits here
	return null;
}

function bossFace(u, v, w, h, face, s) {
	// round gold medallion with a red gem
	const cx = (w - 1) / 2, cy = (h - 1) / 2, r = Math.hypot(u - cx, v - cy);
	if (face !== 'north' && face !== 'south') return MAT.gold(u, v, w, h, face, s);
	if (r <= 1.2) return C.redH;
	if (r <= 2.0) return C.red;
	if (r <= w / 2 - 1) return jit(C.gold, 10, u, v, s);
	return r <= w / 2 ? C.goldD : C.goldD;
}

function helmetFront(u, v, w, h, face, s) {
	// the cap's brow: iron with a gold brow band along the bottom and a Sakaaran chevron
	if (face !== 'north') return MAT.iron(u, v, w, h, face, s);
	if (v >= h - 2) return v === h - 1 ? C.goldD : C.goldL;
	const cx = (w - 1) / 2;
	if (Math.abs(Math.abs(u - cx) - (h - 3 - v) * 1.4) < 0.8 && v < h - 2) return C.gold;
	return MAT.iron(u, v, w, h, face, s);
}

// ------------------------------------------------------------------ the gear
// Model pixels (player-sized Hulk model; front = -Z, his right = -X). o = origin, s = size.
const B = [];
function bone(name, parent, pivot, cubes, rotation) { B.push({ name, parent, pivot, cubes, rotation }); }

// Helmet (+ crest) -- child of head. Head cube x/z -4..4, y 24..32; the hat layer is inflated 0.5.
bone('gladiator_helmet', 'head', [0, 24, 0], [
	{ o: [-4, 29.75, -4], s: [8, 2.25, 8], inflate: 0.75, mat: 'iron', faces: { north: helmetFront } }, // cap, brow at y 29
	{ o: [-4, 24, -2.5], s: [8, 5.75, 6.5], inflate: 0.75, mat: 'iron' }, // sides + back skirt
	{ o: [-4, 24.5, -4], s: [0.25, 4.25, 2], inflate: 0.75, mat: 'ironTrim' }, // his right cheek guard
	{ o: [3.75, 24.5, -4], s: [0.25, 4.25, 2], inflate: 0.75, mat: 'ironTrim' }, // his left cheek guard
	{ o: [-0.5, 25.5, -4.5], s: [1, 4, 0.5], inflate: 0.35, mat: 'gold' }, // nose guard
	{ o: [-1, 32.5, -5], s: [2, 1.25, 10], mat: 'gold' }, // crest ridge
	{ o: [-0.6, 33.75, -4], s: [1.2, 2.75, 9.5], mat: 'red' }, // red crest
	{ o: [-0.6, 33.75, -4.75], s: [1.2, 1.5, 0.75], mat: 'red' }, // crest front
	{ o: [-0.6, 29.5, 4.75], s: [1.2, 4.25, 1.25], mat: 'red' }, // crest down the back
	{ o: [-5.5, 29.5, -1.5], s: [0.75, 2.5, 5], mat: 'ironTrim', rot: [0, 0, 0] }, // his right fin
	{ o: [4.75, 29.5, -1.5], s: [0.75, 2.5, 5], mat: 'ironTrim' }, // his left fin
]);
// War paint -- a decal shell just outside the hat layer; only the front face is painted.
bone('gladiator_paint', 'head', [0, 24, 0], [
	{ o: [-4, 24, -4], s: [8, 8, 8], inflate: 0.56, mat: 'clear', faces: { north: (u, v) => warPaint(u, v) } },
]);
// Pauldron -- one big plated shoulder on his LEFT arm (+X). Arm x 4..8, y 12..24, z -2..2.
bone('gladiator_pauldron', 'left_arm', [5, 22, 0], [
	{ o: [3.5, 23.75, -3], s: [5.5, 1.5, 6], mat: 'ironTrim' },
	{ o: [8.25, 21, -3], s: [1.25, 4, 6], mat: 'ironTrim' },
	{ o: [8.75, 18.5, -2.75], s: [1.25, 3.25, 5.5], mat: 'ironTrim' },
	{ o: [9.25, 16.75, -2.5], s: [1, 2.5, 5], mat: 'iron' },
	{ o: [3.75, 20.5, -3], s: [5, 3.25, 0.75], mat: 'iron' },
	{ o: [3.75, 20.5, 2.25], s: [5, 3.25, 0.75], mat: 'iron' },
	{ o: [5.5, 25.25, -1], s: [2.5, 1, 2], mat: 'gold' },
	{ o: [6.0, 26.25, -0.5], s: [1.5, 1, 1], mat: 'gold' },
]);
// Chest harness -- crossed straps front and back, a gold boss where they cross, plates on the straps. Body x -4..4,
// y 12..24, z -2..2 (jacket +0.25).
const ANG = 33.7;
bone('gladiator_harness', 'body', [0, 12, 0], [
	{ o: [-0.75, 10.8, -2.75], s: [1.5, 14.4, 0.5], mat: 'leather', rot: [0, 0, ANG], pivot: [0, 18, -2.5] },
	{ o: [-0.75, 10.8, -2.75], s: [1.5, 14.4, 0.5], mat: 'leather', rot: [0, 0, -ANG], pivot: [0, 18, -2.5] },
	{ o: [-0.75, 10.8, 2.25], s: [1.5, 14.4, 0.5], mat: 'leather', rot: [0, 0, ANG], pivot: [0, 18, 2.5] },
	{ o: [-0.75, 10.8, 2.25], s: [1.5, 14.4, 0.5], mat: 'leather', rot: [0, 0, -ANG], pivot: [0, 18, 2.5] },
	{ o: [-1.75, 16.25, -3.25], s: [3.5, 3.5, 0.75], mat: 'gold', faces: { north: bossFace } },
	{ o: [-1.75, 16.25, 2.5], s: [3.5, 3.5, 0.75], mat: 'ironTrim' },
	{ o: [1.33, 20.5, -3.1], s: [2, 2, 0.5], mat: 'ironTrim' },
	{ o: [-3.33, 20.5, -3.1], s: [2, 2, 0.5], mat: 'ironTrim' },
	{ o: [-4.4, 21.5, -2.5], s: [2.4, 2.6, 5], mat: 'leather' }, // strap over his right shoulder
]);
// War kilt -- belt + buckle on the body, a red cloth flap front and back, leather strips on each thigh.
bone('gladiator_kilt', 'body', [0, 12, 0], [
	{ o: [-4, 11.25, -2], s: [8, 2, 4], inflate: 0.6, mat: 'leather' },
	{ o: [-1.25, 10.75, -2.95], s: [2.5, 2.5, 0.5], mat: 'gold' },
	{ o: [-1.75, 4, -2.85], s: [3.5, 7.25, 0.4], mat: 'cloth' },
	{ o: [-1.75, 4, 2.45], s: [3.5, 7.25, 0.4], mat: 'cloth' },
]);
bone('gladiator_kilt_right', 'right_leg', [-2, 12, 0], [
	{ o: [-3.9, 6.5, -2], s: [4, 5.5, 4], inflate: 0.55, mat: 'kilt' },
]);
bone('gladiator_kilt_left', 'left_leg', [2, 12, 0], [
	{ o: [-0.1, 6.5, -2], s: [4, 5.5, 4], inflate: 0.55, mat: 'kilt' },
]);
// Bracers -- banded on both forearms (arms x +-4..8, hands at y 12).
for (const [side, parent, x, px] of [['right', 'right_arm', -8, -5], ['left', 'left_arm', 4, 5]]) {
	bone('gladiator_bracer_' + side, parent, [px, 22, 0], [
		{ o: [x, 12.75, -2], s: [4, 6, 4], inflate: 0.5, mat: 'bracer' },
		{ o: [x, 12.75, -2], s: [4, 1, 4], inflate: 0.8, mat: 'gold' },
		{ o: [x, 17.75, -2], s: [4, 1, 4], inflate: 0.8, mat: 'gold' },
	]);
}
// The hammer -- RIGHT hand. A two-headed Sakaaran sledgehammer: the haft runs forward through his fist (y 12.6); the bone
// is tilted so the heavy head hangs low in front of him.
const WEAPON_TILT = [25, 0, 0];
bone('gladiator_hammer', 'right_arm', [-6, 12.6, 0], [
	{ o: [-6.75, 11.85, -13], s: [1.5, 1.5, 17], mat: 'wrap' },
	{ o: [-7.25, 11.35, 4], s: [2.5, 2.5, 1.5], mat: 'gold' },
	{ o: [-7.5, 11.1, -14.25], s: [3, 3, 1.25], mat: 'gold' },
	{ o: [-9, 8.1, -20.25], s: [6, 9, 6], mat: 'ironTrim' },
	{ o: [-9.5, 16.6, -20.75], s: [7, 1, 7], mat: 'gold' },
	{ o: [-9.5, 7.6, -20.75], s: [7, 1, 7], mat: 'gold' },
	{ o: [-9.4, 10.6, -20.65], s: [6.8, 4, 6.8], mat: 'iron' },
	{ o: [-10.25, 11.6, -18.25], s: [1.25, 2, 2], mat: 'gold' },
	{ o: [-3, 11.6, -18.25], s: [1.25, 2, 2], mat: 'gold' },
], WEAPON_TILT);
// The axe -- LEFT hand. A big single-bit battle axe: haft forward through the fist, the bit hanging below it.
bone('gladiator_axe', 'left_arm', [6, 12.6, 0], [
	{ o: [5.25, 11.85, -15], s: [1.5, 1.5, 19], mat: 'wrap' },
	{ o: [4.75, 11.35, 4], s: [2.5, 2.5, 1.5], mat: 'gold' },
	{ o: [5.4, 12.0, -17.25], s: [1.2, 1.2, 2.25], mat: 'gold' },
	{ o: [4.85, 11.1, -14.5], s: [2.3, 3, 4], mat: 'ironTrim' },
	{ o: [5.5, 8.5, -14], s: [1, 2.75, 3], mat: 'iron' },
	{ o: [5.6, 5, -15.25], s: [0.8, 3.5, 5.5], mat: 'steel' },
	{ o: [5.65, 2.5, -16.25], s: [0.7, 2.5, 7.5], mat: 'edge' },
	{ o: [5.65, 4.75, -16.75], s: [0.7, 1.25, 1], mat: 'edge' },
	{ o: [5.65, 4.75, -9.25], s: [0.7, 1.25, 1], mat: 'edge' },
	{ o: [5.5, 14.35, -13.5], s: [1, 2.25, 2], mat: 'iron' },
	{ o: [5.6, 16.6, -13], s: [0.8, 1, 1], mat: 'gold' },
], WEAPON_TILT);

// ------------------------------------------------------------------ pack faces into the 64x64 UV space
const q = (x) => Math.max(1, Math.ceil(x * D - 1e-6)); // texels
const faces = [];
let seedN = 1;
for (const b of B) {
	for (const c of b.cubes) {
		c.seed = seedN++;
		const [sx, sy, sz] = c.s;
		c.uvFaces = {};
		const dims = { north: [sx, sy], south: [sx, sy], east: [sz, sy], west: [sz, sy], up: [sx, sz], down: [sx, sz] };
		for (const f of Object.keys(dims)) {
			const painter = (c.faces && c.faces[f]) || MAT[c.mat];
			const fw = q(dims[f][0]), fh = q(dims[f][1]);
			const entry = { cube: c, face: f, w: fw, h: fh, painter, uvw: dims[f][0], uvh: dims[f][1] };
			faces.push(entry);
		}
	}
}
// one shared fully transparent texel block for faces that draw nothing
const clearFaces = faces.filter(f => f.painter === MAT.clear);
const paintFaces = faces.filter(f => f.painter !== MAT.clear);
paintFaces.sort((a, b) => b.h - a.h || b.w - a.w);
const used = [];
let x = 0, y = 2, rowH = 0; // texel row 0-1 reserved: the transparent block at (0,0)
for (const f of paintFaces) {
	if (x + f.w > TS) { x = 0; y += rowH + 1; rowH = 0; }
	if (y + f.h > TS) throw new Error('texture full at ' + f.cube.mat + ' ' + f.face);
	f.tx = x; f.ty = y;
	x += f.w + 1; rowH = Math.max(rowH, f.h);
}
console.log('packed', paintFaces.length, 'faces, used rows up to', y + rowH, 'of', TS);
for (const f of clearFaces) { f.tx = 0; f.ty = 0; f.w = 1; f.h = 1; }

// ------------------------------------------------------------------ paint the texture
const img = { w: TS, h: TS, data: Buffer.alloc(TS * TS * 4) };
for (const f of paintFaces) {
	for (let v = 0; v < f.h; v++) for (let u = 0; u < f.w; u++) {
		const c = f.painter(u, v, f.w, f.h, f.face, f.cube.seed);
		const o = ((f.ty + v) * TS + f.tx + u) * 4;
		img.data[o] = c[0]; img.data[o + 1] = c[1]; img.data[o + 2] = c[2]; img.data[o + 3] = c[3];
	}
}
L.write(TEX, img);

// ------------------------------------------------------------------ write the bones into hulk.geo.json
const r4 = (n) => Math.round(n * 10000) / 10000;
const geo = JSON.parse(fs.readFileSync(GEO, 'utf8'));
const g = geo['minecraft:geometry'][0];
g.bones = g.bones.filter(b => !b.name.startsWith('gladiator'));
const names = new Set(g.bones.map(b => b.name));
for (const b of B) {
	if (!names.has(b.parent)) throw new Error('missing parent bone ' + b.parent);
	const out = { name: b.name, parent: b.parent, pivot: b.pivot, cubes: [] };
	if (b.rotation) out.rotation = b.rotation;
	for (const c of b.cubes) {
		const cube = { origin: c.o.map(r4), size: c.s.map(r4) };
		if (c.inflate) cube.inflate = c.inflate;
		if (c.rot && (c.rot[0] || c.rot[1] || c.rot[2])) { cube.pivot = c.pivot || c.o; cube.rotation = c.rot; }
		cube.uv = {};
		for (const f of faces.filter(ff => ff.cube === c)) {
			// UV units = texels / K; the uv_size is the face's own size, so 1 model px = K texels
			cube.uv[f.face] = { uv: [r4(f.tx / K), r4(f.ty / K)], uv_size: f.painter === MAT.clear ? [0.5, 0.5] : [r4(f.w / K), r4(f.h / K)] };
		}
		out.cubes.push(cube);
	}
	g.bones.push(out);
}
let text = JSON.stringify(geo, null, 1);
fs.writeFileSync(GEO, text.replace(/\n/g, '\r\n'));
console.log('wrote', B.length, 'gladiator bones into hulk.geo.json and', TEX);
