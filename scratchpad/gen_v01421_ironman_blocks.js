// v0.14.21: Iron Man block models -- the Stark Fabricator workstation and the Iron Man Suit Platform (Hall of Armor).
//
// Usage (from the repo root):  node scratchpad/gen_v01421_ironman_blocks.js [previewDir]
//
// Writes:
//   textures/block/stark_fabricator.png          64x64 atlas for the Fabricator's static JSON body (+ the item arm)
//   textures/block/iron_man_suit_platform.png    64x64 atlas for the Suit Platform
//   textures/machine/stark_fabricator_rig.png    GeckoLib texture for the animated rig (arm, hologram, glow strips)
//   textures/machine/stark_fabricator_rig_glowmask.png
//   models/block|item/{stark_fabricator,iron_man_suit_platform}.json, blockstates (facing [+ working])
//   geo/stark_fabricator.geo.json, animations/stark_fabricator.animation.json
// With a previewDir it also software-renders orthographic previews (front / side / 3-quarter / inventory icon).
//
// Coordinates: everything is authored in BLOCK PIXELS (0..16, the JSON model space) with the machine's FRONT on the
// north (z = 0) side -- the blockstate / GeoBlockRenderer then turn it to face the player who placed it.
// Geo conversion (GeckoLib mirrors x): geo x = 8 - bx, geo z = bz - 8; a render-space rotation [ax, ay, az]
// (applied Rz * Ry * Rx, right-handed, degrees) is written to the geo file as [-ax, -ay, az]; anim positions as [-dx, dy, dz].
const fs = require('fs');
const path = require('path');
const png = require('./pnglib');

const PREVIEW = process.argv[2] || null;
const RES = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');
const R3 = n => Math.round(n * 1000) / 1000;
const writeJson = (p, obj) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, '\t') + '\n'); };

// ================================================================ palette (3-4 step ramps, no noise)
const G = [[28, 31, 38], [44, 49, 58], [64, 71, 83], [90, 99, 113], [126, 136, 150]];   // gunmetal
const RED = [[84, 16, 20], [132, 28, 30], [172, 42, 40], [206, 74, 60]];
const GOLD = [[110, 78, 26], [166, 122, 42], [212, 170, 76], [240, 214, 140]];
const CY = [[14, 66, 84], [28, 136, 166], [64, 206, 236], [160, 244, 255], [232, 255, 255]];
const GL = [[12, 22, 30], [18, 34, 46], [26, 50, 64]];                                      // dark glass
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

// ================================================================ shared math
const DEG = Math.PI / 180;
const mm3 = (a, b) => a.map((r, i) => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const Rx = a => { const c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; };
const Ry = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; };
const Rz = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; };
const I3 = () => [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
const mv = (m, p) => [0, 1, 2].map(i => m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2]);
const aff = (m, t) => ({ m, t });
const affMul = (a, b) => ({ m: mm3(a.m, b.m), t: mv(a.m, b.t).map((v, i) => v + a.t[i]) });
const affApply = (a, p) => mv(a.m, p).map((v, i) => v + a.t[i]);
const rotR = r => mm3(Rz(r[2] * DEG), mm3(Ry(r[1] * DEG), Rx(r[0] * DEG))); // render-space euler -> matrix
const pivotRot = (piv, r) => { const m = rotR(r); const mp = mv(m, piv); return aff(m, piv.map((v, i) => v - mp[i])); };
const IDENT = aff(I3(), [0, 0, 0]);

// ================================================================ face geometry (block px, vanilla JSON UV orientation)
// texel (s, t) in 0..1 across the face, s = texture u direction, t = texture v direction (down the image).
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
function facePointJ(from, to, f, s, t) {
	const [x0, y0, z0] = from, [x1, y1, z1] = to;
	const L = (a, b, k) => a + (b - a) * k;
	switch (f) {
		case 'north': return [L(x1, x0, s), L(y1, y0, t), z0];
		case 'south': return [L(x0, x1, s), L(y1, y0, t), z1];
		case 'east': return [x1, L(y1, y0, t), L(z1, z0, s)];
		case 'west': return [x0, L(y1, y0, t), L(z0, z1, s)];
		case 'up': return [L(x0, x1, s), y1, L(z0, z1, t)];
		default: return [L(x0, x1, s), y0, L(z1, z0, t)];
	}
}
const faceDims = (from, to, f) => {
	const sz = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
	return (f === 'north' || f === 'south') ? [sz[0], sz[1]] : (f === 'east' || f === 'west') ? [sz[2], sz[1]] : [sz[0], sz[2]];
};
const NORMAL = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };

// ================================================================ atlas
function makeAtlas(W, H, fill) {
	const tex = Buffer.alloc(W * H * 4), glow = Buffer.alloc(W * H * 4);
	for (let i = 0; i < W * H; i++) { tex[i * 4] = fill[0]; tex[i * 4 + 1] = fill[1]; tex[i * 4 + 2] = fill[2]; tex[i * 4 + 3] = fill[3] ?? 255; }
	return { W, H, tex, glow, rects: [] };
}
// shelf-pack every rect (largest first)
function pack(atlas, rects) {
	const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
	let x = 0, y = 0, rowH = 0;
	for (const r of order) {
		if (x + r.w > atlas.W) { x = 0; y += rowH; rowH = 0; }
		r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
	}
	if (y + rowH > atlas.H) throw new Error(`atlas overflow ${atlas.W}x${atlas.H}: needs ${y + rowH} rows`);
	return y + rowH;
}
// paint result: [r,g,b] (opaque) | { c, a?, glow? }  -- glow = glowmask alpha (GeckoLib takes the RGB from the base)
function putTexel(atlas, x, y, res) {
	const i = (y * atlas.W + x) * 4;
	let c, a = 255, g = 0;
	if (Array.isArray(res)) c = res; else { c = res.c; a = res.a ?? 255; g = res.glow ?? 0; }
	atlas.tex[i] = c[0]; atlas.tex[i + 1] = c[1]; atlas.tex[i + 2] = c[2]; atlas.tex[i + 3] = a;
	if (g > 0) { atlas.glow[i] = 255; atlas.glow[i + 1] = 255; atlas.glow[i + 2] = 255; atlas.glow[i + 3] = g; }
}
function paintRect(atlas, r, painter) {
	for (let j = 0; j < r.h; j++) for (let i = 0; i < r.w; i++) {
		const s = (i + 0.5) / r.w, t = (j + 0.5) / r.h;
		const p = facePointJ(r.from, r.to, r.f, s, t);
		putTexel(atlas, r.u + i, r.v + j, painter({ style: r.style, f: r.f, i, j, w: r.w, h: r.h, s, t, p, el: r.el }));
	}
}
const texels = d => Math.max(1, Math.round(d));

// ================================================================ painters
// helpers on a face context q: q.i/q.j texel, q.w/q.h size, q.p world point (block px)
const edgeL = q => q.i === 0, edgeR = q => q.i === q.w - 1, edgeT = q => q.j === 0, edgeB = q => q.j === q.h - 1;
const onEdge = q => edgeL(q) || edgeR(q) || edgeT(q) || edgeB(q);
const sideFace = q => q.f !== 'up' && q.f !== 'down';
// a bevelled plate: lit top-left rim, shaded bottom-right rim
function plate(q, base, hi, lo) {
	if (edgeT(q) || edgeL(q)) return hi;
	if (edgeB(q) || edgeR(q)) return lo;
	return base;
}
const fx = q => q.p[0], fy = q => q.p[1], fz = q => q.p[2];
const near = (v, c, r = 0.5) => Math.abs(v - c) < r;
// horizontal coordinate across a side face, in block px, increasing to the viewer's right
const across = q => q.f === 'north' ? 16 - fx(q) : q.f === 'south' ? fx(q) : q.f === 'east' ? 16 - fz(q) : fz(q);

const P = {};
// ---------------- Stark Fabricator (static body)
P.plinth = q => q.f === 'down' ? G[0] : (edgeT(q) ? G[2] : (q.i % 3 === 1 ? G[0] : G[1]));
P.cabinet = q => {
	const x = fx(q), y = fy(q), z = fz(q);
	if (q.f === 'down') return G[0];
	if (q.f === 'up') return G[1];
	const a = across(q);
	if (y > 9.0) {                                               // lip under the worktop: the light strip
		if (q.f === 'north') return a < 1 || a > 15 ? G[1] : (a > 7 && a < 9 ? CY[4] : CY[2]);
		if (q.f === 'south') return G[1];
		return a > 1 && a < 15 ? CY[1] : G[1];
	}
	if (y > 8.0) return q.f === 'north' ? G[0] : RED[1];          // shadow seam / red side band
	if (y < 2.5) return G[1];                                     // kick band
	if (q.f === 'north') {
		// texel column c (0..15, left->right as seen from the front), row r (0 = bottom row at y 2.5)
		const c = Math.floor(a), r = Math.floor(y - 2.5);
		// panels: seams at c 0, 4, 11, 15; centre panel c 5..10
		if (c === 0 || c === 4 || c === 11 || c === 15) return G[0];
		if (c >= 5 && c <= 10) {                                   // centre: red Stark plate, gold rim, arc-reactor eye
			if (r === 5 || c === 5 || c === 10 || r === 0) return r === 5 || c === 5 ? GOLD[2] : GOLD[1];
			if (r === 2 && (c === 7 || c === 8)) return CY[4];
			if ((r === 1 || r === 3) && (c === 7 || c === 8)) return CY[2];
			if (r === 2 && (c === 6 || c === 9)) return CY[2];
			return RED[1];
		}
		const left = c < 4, local = left ? c - 1 : c - 12;           // 0..2 inside a side panel
		if (r === 5) return G[3];                                  // panel top highlight
		if (r === 0) return G[1];
		if (r === 3 && local === 1) return G[0];                   // recessed handle
		if (r === 1 && local === (left ? 0 : 2)) return left ? RED[3] : CY[3];   // status LEDs
		return local === 0 ? G[3] : G[2];
	}
	if (q.f === 'south') {
		if (near(a, 4, 0.5) || near(a, 12, 0.5)) return G[0];
		if (y > 6 && y < 7 && a > 5 && a < 11) return G[0];
		if (near(y, 4.5) && (near(a, 7) || near(a, 9))) return CY[2];  // power ports
		return y > 7 ? G[2] : G[1];
	}
	// east / west: seams, a vent grille, the red band above (y 8-9)
	if (y > 7.0) return GOLD[0];
	if (a < 1 || a > 15 || near(a, 5.5) || near(a, 10.5)) return G[0];
	if (a > 6 && a < 10 && y > 3 && y < 6.5) return (Math.floor(y) % 2 === 0) ? G[0] : G[3];
	return y > 6.5 ? G[3] : G[2];
};
P.worktop = q => {
	if (q.f === 'down') return G[0];
	if (q.f === 'up') {
		const x = fx(q), z = fz(q);
		if (x < 1 || x > 15 || z < 1 || z > 15) return GOLD[1];
		if (x < 2 || z < 2) return G[4];
		if (Math.floor(x) % 4 === 0 && Math.floor(z) % 4 === 0) return G[1];   // grid bolts
		return G[3];
	}
	const a = across(q);
	return (Math.floor(a) % 4 === 1) ? GOLD[3] : GOLD[2];
};
P.glass = q => {
	if (q.f !== 'up') return CY[1];
	const c = Math.floor(fx(q)) - 2, r = Math.floor(fz(q)) - 2;      // 0..7
	if (c === 0 || c === 7 || r === 0 || r === 7) return CY[1];
	if ((c === 3 || c === 4) && (r === 3 || r === 4)) return GL[2];
	return GL[1];
};
P.tray = q => {
	if (q.f !== 'up') return GOLD[1];
	const x = fx(q), z = fz(q);
	if (x < 12 || x > 14 || z < 2.5 || z > 4.5) return GOLD[2];
	if (near(x, 12.5) && near(z, 3)) return RED[2];
	if (near(x, 13.5) && near(z, 4)) return G[4];
	return G[0];
};
P.turret = q => {
	if (q.f === 'up') { const d = Math.hypot(fx(q) - 12.5, fz(q) - 10); return d < 0.9 ? G[0] : d < 1.4 ? GOLD[2] : G[2]; }
	if (q.f === 'down') return G[0];
	return edgeT(q) ? RED[2] : G[1];
};
P.post = q => {
	const y = fy(q);
	if (q.f === 'up') return GOLD[2];
	if (q.f === 'down') return G[0];
	if (y > 15) return GOLD[1];
	if (q.f === 'north' || q.f === 'south') return plate(q, G[2], G[3], G[1]);
	// inner side faces carry a red stripe, outer faces a seam
	const inner = (q.f === 'east' && fx(q) < 8) || (q.f === 'west' && fx(q) > 8);
	if (inner) return edgeL(q) || edgeR(q) ? G[1] : RED[1];
	return near(y, 13) ? G[0] : G[2];
};
P.header = q => {
	if (q.f === 'north') return CY[2];
	if (q.f === 'up') return (Math.floor(fx(q)) % 3 === 0) ? G[1] : G[2];
	if (q.f === 'down') return G[1];
	return G[2];
};
// the display: 11 x 4 texels. a = column left->right, b = row bottom->top. Two "text" lines and a status light.
function screenUi(q) {
	const a = q.i, b = q.h - 1 - q.j;
	if (a === 0 || a === q.w - 1 || b === 0 || b === q.h - 1) return 'bezel';
	if (b === 2 && a >= 1 && a <= 4) return 'bar';
	if (b === 1 && a >= 1 && a <= 6 && a !== 4) return 'line';
	if (a === q.w - 2 && b === 2) return 'dot';
	return 'glass';
}
P.screen = q => {
	if (q.f !== 'north') return G[1];
	return { bezel: G[1], bar: CY[2], dot: CY[4], line: CY[1], glass: GL[1] }[screenUi(q)];
};
P.backbox = q => {
	if (q.f === 'up') return G[1];
	return (q.f === 'south' && q.j % 2 === 1 && q.i > 1 && q.i < q.w - 2) ? G[0] : G[2];
};
// ---------------- robotic arm (shared by the GeckoLib rig and the JSON item model)
P.arm = q => {
	if (q.f === 'up') return RED[2];
	if (q.f === 'down') return RED[0];
	const long = Math.max(q.w, q.h);
	if (long >= 3 && (q.w >= q.h ? q.i : q.j) === Math.floor(long / 2)) return GOLD[1];   // gold band mid-segment
	return q.f === 'north' || q.f === 'south' ? RED[1] : RED[2];
};
P.joint = q => (q.f === 'east' || q.f === 'west') ? (q.i === 1 && q.j === 1 ? G[1] : GOLD[2]) : GOLD[1];
P.turntable = q => q.f === 'up' ? (onEdge(q) ? GOLD[2] : G[2]) : (q.j === 0 ? GOLD[2] : RED[1]);
P.housing = q => {
	if (q.f === 'down') return G[0];
	if (q.f === 'up') return G[2];
	if (q.j === 0) return RED[2];
	return edgeL(q) ? G[3] : G[2];
};
P.nozzle = q => q.f === 'down' ? G[0] : GOLD[2];
P.tip = q => ({ c: CY[4], glow: 255 });
P.spark = q => ({ c: [255, 236, 170], glow: 255 });
// ---------------- glow overlays + hologram (GeckoLib only; base texel cleared by the glowmask)
const NONE = { c: [0, 0, 0], a: 0 };
P.glowstrip = q => {
	if (sideFace(q) && q.f !== 'north') return NONE;
	if (q.f !== 'north') return NONE;
	return { c: CY[3], glow: 200 };
};
P.chaser = q => q.f === 'north' ? { c: CY[4], glow: 255 } : NONE;
P.holoscreen = q => {
	if (q.f !== 'north') return NONE;
	const k = screenUi(q);
	if (k === 'bar') return { c: CY[3], glow: 230 };
	if (k === 'dot') return { c: CY[4], glow: 255 };
	if (k === 'line') return { c: CY[2], glow: 180 };
	return NONE;
};
// flat ring discs (up / down faces only); r measured from the hologram axis (6, 6)
function ringDisc(q, rings) {
	if (q.f !== 'up' && q.f !== 'down') return NONE;
	const dx = Math.abs(fx(q) - 6), dz = Math.abs(fz(q) - 6);
	const d = Math.max(dx, dz, (dx + dz) * 0.7072);
	for (const [r0, r1, c, g] of rings) if (d >= r0 && d < r1) return { c, glow: g };
	return NONE;
}
P.holobase = q => ringDisc(q, [[2.0, 2.6, CY[2], 120], [3.0, 3.6, CY[3], 210]]);
P.holoscan = q => ringDisc(q, [[2.9, 3.6, CY[3], 160]]);
// hologram: an Iron Man helmet in light. Translucent fill, bright wire edges, faceplate on the front.
P.holohelm = q => {
	const edge = onEdge(q);
	if (q.f === 'north') {
		const a = q.i, b = q.h - 1 - q.j;
		const eye = b === 2 && (a === 0 || a === q.w - 1);
		if (eye || (b === 2 && (a === 1 || a === q.w - 2))) return { c: CY[4], glow: 255 };
		if (b === 0 && a > 0 && a < q.w - 1) return { c: CY[3], glow: 200 };        // mouth line
		if (edge) return { c: CY[3], glow: 190 };
		return { c: CY[2], glow: 70 };
	}
	if (edge) return { c: CY[3], glow: 170 };
	return { c: CY[2], glow: q.f === 'up' ? 80 : 55 };
};
P.beam = q => {
	if (!sideFace(q)) return NONE;
	return { c: CY[2], glow: Math.round(70 * (1 - q.t)) + 10 };
};

// ---------------- Suit Platform
const OCT_C = 8;
const inOct = (x, z) => Math.abs(x - OCT_C) <= 7 && Math.abs(z - OCT_C) <= 7 && Math.abs(x - OCT_C) + Math.abs(z - OCT_C) <= 11;
P.pbase = q => {
	if (q.f === 'down') return G[0];
	if (q.f === 'up') {
		const x = fx(q), z = fz(q);
		if (x < 1 || x > 15 || z < 1 || z > 15) return GOLD[1];
		return G[1];
	}
	return edgeT(q) ? G[3] : G[1];
};
P.oct = q => {
	if (q.f === 'down') return G[0];
	if (q.f !== 'up') return edgeT(q) ? GOLD[2] : RED[1];
	const x = fx(q), z = fz(q);
	const dx = Math.abs(x - 8), dz = Math.abs(z - 8);
	// rim: texel whose neighbour (1 px further out) leaves the octagon
	const sx = Math.sign(x - 8), sz = Math.sign(z - 8);
	if (!inOct(x + sx, z) || !inOct(x, z + sz) || !inOct(x + sx, z + sz)) return G[4];
	const m = Math.hypot(dx, dz);
	if (m < 1.5) return CY[4];
	if (m < 2.6) return CY[2];
	if (m < 3.4) return G[1];
	if (m < 4.6) return G[3];
	if (m < 5.3) return G[1];
	if (m < 6.4) return CY[3];
	if (m < 7.1) return CY[1];
	return (dx < 1 || dz < 1) ? G[1] : G[3];               // outer band, seams on the axes
};
P.ppost = q => {
	const y = fy(q);
	if (q.f === 'up') return GOLD[2];
	if (q.f === 'down') return G[0];
	if (y > 25 || y < 2) return GOLD[1];
	const inner = (q.f === 'east' && fx(q) < 8) || (q.f === 'west' && fx(q) > 8);
	if (q.f === 'north') {
		if (near(y, 8) || near(y, 15) || near(y, 21)) return G[0];
		return edgeL(q) ? G[3] : G[2];
	}
	if (inner) return near(across(q), 13.25 + 0.0, 0.6) || q.i === 1 ? CY[2] : G[2];
	if (q.f === 'south') return G[1];
	return near(y, 8) || near(y, 15) || near(y, 21) ? G[0] : RED[1];
};
P.spine = q => {
	if (q.f === 'up') return G[2];
	if (q.f !== 'north') return G[1];
	const c = q.i, r = q.h - 1 - q.j, W = q.w;          // c 0..10, r 0 at the bottom
	if (r >= q.h - 1) return GOLD[1];
	if ((c === 2 || c === W - 3) && r >= 1 && r <= q.h - 4) return (r % 6 === 0) ? CY[3] : CY[1];
	if (r === 6 || r === 12) return G[0];
	const cc = c - (W - 1) / 2, rr = r - (q.h - 4);       // crest centred near the top
	if (Math.abs(cc) <= 1.5 && rr >= -2 && rr <= 1) {
		if (Math.abs(cc) < 1 && (rr === -1 || rr === 0)) return rr === 0 && cc === 0 ? CY[4] : CY[2];
		return RED[1];
	}
	return c < 2 || c > W - 3 ? G[2] : G[1];
};
P.pheader = q => {
	if (q.f === 'up') return (Math.floor(fx(q)) % 4 === 0) ? G[1] : G[2];
	if (q.f === 'down') return G[1];
	if (q.f === 'north') return edgeT(q) ? GOLD[2] : (q.i % 5 === 2 ? GOLD[1] : RED[1]);
	return edgeT(q) ? GOLD[2] : G[2];
};
P.boom = q => {
	if (q.f === 'up') return G[2];
	if (q.f === 'down') return G[1];
	return edgeT(q) ? GOLD[2] : RED[1];
};
P.emitter = q => {
	if (q.f !== 'down') return G[1];
	const d = Math.hypot(fx(q) - 8, fz(q) - 8);
	return d < 0.8 ? CY[4] : d < 1.2 ? G[0] : CY[2];
};
P.clamp = q => {
	if (q.f === 'up') return GOLD[2];
	if (q.f === 'down') return G[0];
	if (q.f === 'north') return q.j === 1 ? G[0] : GOLD[1];
	return edgeT(q) ? GOLD[2] : RED[1];
};

// ================================================================ model descriptions
// element: { name, from, to, style, rot?: {axis, angle, origin}, hide?: [faces], cull?: {face: dir} }
const el = (name, from, to, style, o = {}) => ({ name, from, to, style, rot: o.rot || null, hide: new Set(o.hide || []), cull: o.cull || {} });

// ---------------- Fabricator: static body
const FAB = [
	el('plinth', [1, 0, 1], [15, 1.5, 15], 'plinth', { hide: ['up'], cull: { down: 'down' } }),
	el('cabinet', [0, 1.5, 0], [16, 10, 16], 'cabinet', { hide: ['up'], cull: { north: 'north', south: 'south', east: 'east', west: 'west' } }),
	el('worktop', [0, 10, 0], [16, 11, 16], 'worktop', { hide: ['down'], cull: { north: 'north', south: 'south', east: 'east', west: 'west' } }),
	el('glass', [2, 11, 2], [10, 11.5, 10], 'glass', { hide: ['down'] }),
	el('tray', [11, 11, 1.5], [15, 11.75, 5.5], 'tray', { hide: ['down'] }),
	el('turret', [11, 11, 8.5], [14, 12, 11.5], 'turret', { hide: ['down'] }),
	el('post_l', [0.5, 11, 12], [2.5, 16, 15], 'post', { hide: ['down'], cull: { up: 'up' } }),
	el('post_r', [13.5, 11, 12], [15.5, 16, 15], 'post', { hide: ['down'], cull: { up: 'up' } }),
	el('header', [2.5, 15, 12.5], [13.5, 16, 14.5], 'header', { cull: { up: 'up' } }),
	el('backbox', [2.5, 11, 14], [13.5, 15, 15], 'backbox', { hide: ['down'] }),
	el('screen', [2.5, 11.25, 12.2], [13.5, 15.25, 12.7], 'screen', { rot: { axis: 'x', angle: 22.5, origin: [8, 11.25, 12.7] } }),
];

// ---------------- Fabricator: animated rig (GeckoLib). bones in render space, block px.
const bones = [];
const BN = {};
function bone(name, parent, pivot, rot = [0, 0, 0]) { const b = { name, parent, pivot, rot, cubes: [] }; bones.push(b); BN[name] = b; return b; }
function cube(b, from, to, style, o = {}) { const c = { from, to, style, bone: b.name, hide: new Set(o.hide || []), json: o.json !== false }; b.cubes.push(c); return c; }

const ARM_X = 12.5, ARM_Z = 10;
const LEAN = -22.5;   // lower arm leans toward the front
bone('root', null, [8, 0, 8]);
const armBase = bone('arm_base', 'root', [ARM_X, 12, ARM_Z]);
cube(armBase, [11.5, 12, 9], [13.5, 12.75, 11], 'turntable', { hide: ['down'] });
const armLower = bone('arm_lower', 'arm_base', [ARM_X, 12.5, ARM_Z], [LEAN, 0, 0]);
cube(armLower, [11.75, 12.25, 9.25], [13.25, 13.25, 10.75], 'joint');
cube(armLower, [12, 13.25, 9.5], [13, 15.25, 10.5], 'arm', { hide: ['up', 'down'] });
cube(armLower, [11.75, 15.25, 9.25], [13.25, 16.25, 10.75], 'joint');
const armUpper = bone('arm_upper', 'arm_lower', [ARM_X, 15.75, ARM_Z], [-LEAN, 0, 0]);
cube(armUpper, [12, 15.25, 5.5], [13, 16.25, 9.25], 'arm', { hide: ['south'] });
const armHead = bone('arm_head', 'arm_upper', [ARM_X, 15.75, 5.75]);
cube(armHead, [11.9, 14.25, 5.1], [13.1, 15.25, 6.4], 'housing');
cube(armHead, [12.15, 13.75, 5.35], [12.85, 14.25, 6.15], 'nozzle', { hide: ['up'] });
const tipB = bone('arm_tip', 'arm_head', [ARM_X, 13.6, 5.75]);
cube(tipB, [12.25, 13.45, 5.5], [12.75, 13.75, 6.0], 'tip', { json: false });
const sparks = bone('sparks', 'arm_tip', [ARM_X, 13.4, 5.75]);
for (const [dx, dy, dz] of [[-0.9, -0.3, 0.2], [0.7, -0.6, -0.5], [0.2, -0.2, 0.9], [-0.4, -0.8, -0.8]]) {
	const x = ARM_X + dx, y = 13.2 + dy, z = 5.75 + dz;
	cube(sparks, [x - 0.15, y - 0.15, z - 0.15], [x + 0.15, y + 0.15, z + 0.15], 'spark', { json: false });
}
// hologram over the glass plate (centre 6, 6)
const holo = bone('holo', 'root', [6, 11.5, 6]);
cube(holo, [2.5, 11.55, 2.5], [9.5, 11.56, 9.5], 'holobase', { json: false });
cube(holo, [4.4, 11.6, 4.4], [7.6, 12.6, 7.6], 'beam', { json: false, hide: ['up', 'down'] });
const helm = bone('holo_helmet', 'holo', [6, 13.9, 6]);
cube(helm, [4.6, 12.6, 4.6], [7.4, 14.9, 7.4], 'holohelm', { json: false });
cube(helm, [4.9, 14.9, 4.9], [7.1, 15.4, 7.1], 'holohelm', { json: false });
const scan = bone('holo_scan', 'holo', [6, 11.6, 6]);
cube(scan, [2.5, 11.6, 2.5], [9.5, 11.61, 9.5], 'holoscan', { json: false });
// emissive overlays for the static body's light strips + the display
const glowB = bone('glow', 'root', [8, 0, 8]);
cube(glowB, [1, 9.0, -0.04], [15, 10, -0.02], 'glowstrip', { json: false });
cube(glowB, [2.5, 15, 12.46], [13.5, 16, 12.48], 'glowstrip', { json: false });
const chaser = bone('glow_chaser', 'glow', [8, 9.5, 0]);
cube(chaser, [7, 9.05, -0.06], [9, 9.95, -0.045], 'chaser', { json: false });
const scr = bone('screen_glow', 'root', [8, 11.25, 12.7], [22.5, 0, 0]);
cube(scr, [2.5, 11.25, 12.15], [13.5, 15.25, 12.17], 'holoscreen', { json: false });

// world (render-space) affine of each bone at a pose ({bone: {rot:[..], pos:[..], scale}})
function boneWorld(pose = {}) {
	const W = {};
	for (const b of bones) {
		const pz = pose[b.name] || {};
		const r = [0, 1, 2].map(i => b.rot[i] + ((pz.rot || [0, 0, 0])[i]));
		let local = pivotRot(b.pivot, r);
		if (pz.scale !== undefined && pz.scale !== 1) {
			const k = pz.scale; const S = aff([[k, 0, 0], [0, k, 0], [0, 0, k]], b.pivot.map(v => v * (1 - k)));
			local = affMul(local, S);
		}
		if (pz.pos) local = affMul(aff(I3(), pz.pos), local);
		W[b.name] = b.parent ? affMul(W[b.parent], local) : local;
	}
	return W;
}

// ---------------- the arm as plain JSON elements (rest pose) for the item model
function armJsonElements() {
	const W = boneWorld();
	const out = [];
	for (const b of bones) for (const c of b.cubes) {
		if (!c.json) continue;
		const A = W[b.name];
		const m = A.m;
		const ang = Math.atan2(m[2][1], m[1][1]) / DEG;
		if (Math.abs(m[0][0] - 1) > 1e-6) throw new Error('arm rest pose must only pitch about x');
		const allowed = [-45, -22.5, 0, 22.5, 45];
		if (!allowed.some(a => Math.abs(a - ang) < 1e-6)) throw new Error(`arm cube angle ${ang} not representable in JSON`);
		const from = [c.from[0] + A.t[0], c.from[1], c.from[2]], to = [c.to[0] + A.t[0], c.to[1], c.to[2]];
		let rot = null, shift = [0, 0];
		if (Math.abs(ang) < 1e-6) { shift = [A.t[1], A.t[2]]; }
		else {
			// (I - R) o = t in the yz plane
			const a11 = 1 - m[1][1], a12 = -m[1][2], a21 = -m[2][1], a22 = 1 - m[2][2];
			const det = a11 * a22 - a12 * a21;
			const oy = (A.t[1] * a22 - a12 * A.t[2]) / det, oz = (a11 * A.t[2] - a21 * A.t[1]) / det;
			rot = { axis: 'x', angle: R3(ang), origin: [R3(from[0]), R3(oy), R3(oz)] };
		}
		from[1] += shift[0]; to[1] += shift[0]; from[2] += shift[1]; to[2] += shift[1];
		out.push(el('arm_' + c.style, from.map(R3), to.map(R3), c.style, { rot, hide: [...c.hide] }));
	}
	return out;
}
const FAB_ITEM = [...FAB, ...armJsonElements()];

// ---------------- Suit Platform
const PLAT = [
	el('base', [0, 0, 0], [16, 1, 16], 'pbase', { cull: { down: 'down', north: 'north', south: 'south', east: 'east', west: 'west' } }),
	el('oct_c', [1, 1, 4], [15, 2.5, 12], 'oct', { hide: ['down'] }),
	el('oct_n1', [2, 1, 3], [14, 2.5, 4], 'oct', { hide: ['down', 'south'] }),
	el('oct_s1', [2, 1, 12], [14, 2.5, 13], 'oct', { hide: ['down', 'north'] }),
	el('oct_n2', [3, 1, 2], [13, 2.5, 3], 'oct', { hide: ['down', 'south'] }),
	el('oct_s2', [3, 1, 13], [13, 2.5, 14], 'oct', { hide: ['down', 'north'] }),
	el('oct_n3', [4, 1, 1], [12, 2.5, 2], 'oct', { hide: ['down', 'south'] }),
	el('oct_s3', [4, 1, 14], [12, 2.5, 15], 'oct', { hide: ['down', 'north'] }),
	el('post_l', [0.5, 1, 13], [2.5, 26, 15.5], 'ppost', { hide: ['down', 'up'] }),
	el('post_r', [13.5, 1, 13], [15.5, 26, 15.5], 'ppost', { hide: ['down', 'up'] }),
	el('spine', [2.5, 2.5, 14.6], [13.5, 22, 15.4], 'spine', { hide: ['down'] }),
	el('clamp_l', [2.5, 16.5, 13], [3.75, 19, 14.6], 'clamp'),
	el('clamp_r', [12.25, 16.5, 13], [13.5, 19, 14.6], 'clamp'),
	el('header', [0, 26, 12.5], [16, 28, 16], 'pheader'),
	el('boom', [6, 26.5, 6], [10, 27.75, 12.5], 'boom', { hide: ['south'] }),
	el('emitter', [6.5, 26, 6.5], [9.5, 26.5, 9.5], 'emitter', { hide: ['up'] }),
];
// Where the suit stands: IronManSuitPlatformRenderer.DISPLAY_Y_OFFSET (blocks) -- keep in sync.
const SUIT_Y_OFFSET = 0.2;
const SUIT_SCALE = 0.62;

// ================================================================ build JSON atlases + models
function buildJsonModel(atlas, elements, texName, particle) {
	const rects = [];
	for (const e of elements) {
		e.rects = {};
		for (const f of FACES) {
			if (e.hide.has(f)) continue;
			const [fw, fh] = faceDims(e.from, e.to, f);
			const r = { el: e, f, from: e.from, to: e.to, style: e.style, w: texels(fw), h: texels(fh) };
			e.rects[f] = r; rects.push(r);
		}
	}
	const used = pack(atlas, rects);
	for (const r of rects) paintRect(atlas, r, P[r.style]);
	const k = 16 / atlas.W;
	return {
		used,
		elements: elements.map(e => {
			const o = { name: e.name, from: e.from.map(R3), to: e.to.map(R3) };
			if (e.rot) o.rotation = { angle: e.rot.angle, axis: e.rot.axis, origin: e.rot.origin.map(R3) };
			o.faces = {};
			for (const f of FACES) {
				const r = e.rects[f];
				if (!r) continue;
				o.faces[f] = { uv: [R3(r.u * k), R3(r.v * k), R3((r.u + r.w) * k), R3((r.v + r.h) * k)], texture: '#' + texName };
				if (e.cull[f]) o.faces[f].cullface = e.cull[f];
			}
			return o;
		}),
	};
}

// --- Fabricator atlas: body + arm (the arm faces are only used by the item model)
const fabAtlas = makeAtlas(64, 64, G[2]);
const fabItemModel = buildJsonModel(fabAtlas, FAB_ITEM, 'body');
const fabBlockElements = fabItemModel.elements.slice(0, FAB.length);
const fabArmElements = fabItemModel.elements.slice(FAB.length);
console.log(`fabricator atlas: ${fabItemModel.used}/64 rows used, ${FAB.length} body + ${fabArmElements.length} item-arm elements`);

// --- Platform atlas
const platAtlas = makeAtlas(64, 64, G[2]);
const platModel = buildJsonModel(platAtlas, PLAT, 'base');
console.log(`platform atlas: ${platModel.used}/64 rows used, ${PLAT.length} elements`);

// --- GeckoLib rig atlas
const rigAtlas = makeAtlas(64, 64, [0, 0, 0, 0]);
{
	const rects = [];
	for (const b of bones) for (const c of b.cubes) {
		c.uv = {};
		for (const f of FACES) {
			const [fw, fh] = faceDims(c.from, c.to, f);
			const r = { el: c, f, from: c.from, to: c.to, style: c.style, w: texels(fw), h: texels(fh) };
			c.uv[f] = r; rects.push(r);
		}
	}
	const used = pack(rigAtlas, rects);
	// GeckoLib's per-face UV runs the other way round from vanilla JSON on the x axis (it mirrors x), so paint each
	// geo face with the texel order GeckoLib actually samples: see geoFacePoint below.
	for (const r of rects) {
		for (let j = 0; j < r.h; j++) for (let i = 0; i < r.w; i++) {
			const s = (i + 0.5) / r.w, t = (j + 0.5) / r.h;
			const p = geoFacePoint(r.from, r.to, r.f, s, t);
			const res = r.el.hide.has(r.f) ? NONE : P[r.style]({ style: r.style, f: r.f, i, j, w: r.w, h: r.h, s, t, p, el: r.el });
			putTexel(rigAtlas, r.u + i, r.v + j, res);
		}
	}
	console.log(`rig atlas: ${used}/64 rows used, ${bones.length} bones`);
}
// Where (block px) GeckoLib draws texel (s, t) of a geo cube face, given the cube in block px. Derived from the Titan
// generator's in-game-verified mapping (json space: north u runs -X -> +X; GeckoLib renders x = -json x). The face
// NAME in the geo file is the json-space name; json east (+x) is render west -- geoFaceName handles the swap.
function geoFacePoint(from, to, f, s, t) {
	// render-space face f; find its json-space name + that face's (s,t)->json point, then mirror back
	const jf = f === 'east' ? 'west' : f === 'west' ? 'east' : f;
	const jfrom = [8 - to[0], from[1], from[2] - 8], jto = [8 - from[0], to[1], to[2] - 8];
	const [x0, y0, z0] = jfrom, [x1, y1, z1] = jto;
	const X = a => x0 + a * (x1 - x0), Y = a => y1 - a * (y1 - y0), Z = a => z0 + a * (z1 - z0);
	let j;
	switch (jf) {
		case 'north': j = [X(s), Y(t), z0]; break;
		case 'south': j = [x1 - s * (x1 - x0), Y(t), z1]; break;
		case 'east': j = [x1, Y(t), Z(s)]; break;
		case 'west': j = [x0, Y(t), z1 - s * (z1 - z0)]; break;
		case 'up': j = [x1 - s * (x1 - x0), y1, Z(t)]; break;
		default: j = [x1 - s * (x1 - x0), y0, z1 - t * (z1 - z0)];
	}
	return [8 - j[0], j[1], j[2] + 8];
}
const geoFaceName = f => f === 'east' ? 'west' : f === 'west' ? 'east' : f;

// ================================================================ write assets
fs.mkdirSync(path.join(RES, 'textures/machine'), { recursive: true });
png.write(path.join(RES, 'textures/block/stark_fabricator.png'), { w: 64, h: 64, data: fabAtlas.tex });
png.write(path.join(RES, 'textures/block/iron_man_suit_platform.png'), { w: 64, h: 64, data: platAtlas.tex });
png.write(path.join(RES, 'textures/machine/stark_fabricator_rig.png'), { w: 64, h: 64, data: rigAtlas.tex });
png.write(path.join(RES, 'textures/machine/stark_fabricator_rig_glowmask.png'), { w: 64, h: 64, data: rigAtlas.glow });
const oldCore = path.join(RES, 'textures/block/stark_fabricator_core.png');
if (fs.existsSync(oldCore)) fs.unlinkSync(oldCore);

const FAB_DISPLAY = {
	gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] },
	ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
	fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
	thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
	thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
	firstperson_righthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
	firstperson_lefthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
};
// the platform is 28 px tall: shrink it and drop it so the whole gantry fits the slot
const PLAT_DISPLAY = {
	gui: { rotation: [30, 225, 0], translation: [0, -2.75, 0], scale: [0.45, 0.45, 0.45] },
	ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.2, 0.2, 0.2] },
	fixed: { rotation: [0, 180, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
	thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, -1.5], scale: [0.3, 0.3, 0.3] },
	thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 1.5, -1.5], scale: [0.3, 0.3, 0.3] },
	firstperson_righthand: { rotation: [0, 135, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3] },
	firstperson_lefthand: { rotation: [0, 135, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3] },
};
const FAB_TEX = { particle: 'projecthero:block/stark_fabricator', body: 'projecthero:block/stark_fabricator' };
writeJson(path.join(RES, 'models/block/stark_fabricator.json'), {
	parent: 'block/block', ambientocclusion: false, textures: FAB_TEX, elements: fabBlockElements, display: FAB_DISPLAY,
});
writeJson(path.join(RES, 'models/item/stark_fabricator.json'), {
	parent: 'block/block', ambientocclusion: false, textures: FAB_TEX, elements: [...fabBlockElements, ...fabArmElements], display: FAB_DISPLAY,
});
writeJson(path.join(RES, 'models/block/iron_man_suit_platform.json'), {
	parent: 'block/block', ambientocclusion: false,
	textures: { particle: 'projecthero:block/iron_man_suit_platform', base: 'projecthero:block/iron_man_suit_platform' },
	elements: platModel.elements, display: PLAT_DISPLAY,
});
writeJson(path.join(RES, 'models/item/iron_man_suit_platform.json'), { parent: 'projecthero:block/iron_man_suit_platform' });
const FACING_Y = { north: 0, east: 90, south: 180, west: 270 };
const variants = (model, extra = '') => Object.fromEntries(Object.entries(FACING_Y).map(([f, y]) => [`facing=${f}${extra}`, y ? { model, y } : { model }]));
writeJson(path.join(RES, 'blockstates/stark_fabricator.json'), { variants: variants('projecthero:block/stark_fabricator') });
writeJson(path.join(RES, 'blockstates/iron_man_suit_platform.json'), { variants: variants('projecthero:block/iron_man_suit_platform') });

// ---- geo
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.stark_fabricator', texture_width: 64, texture_height: 64,
			visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0],
		},
		bones: bones.map(b => {
			const o = { name: b.name };
			if (b.parent) o.parent = b.parent;
			o.pivot = [R3(8 - b.pivot[0]), R3(b.pivot[1]), R3(b.pivot[2] - 8)];
			if (b.rot.some(v => v !== 0)) o.rotation = [R3(-b.rot[0]), R3(-b.rot[1]), R3(b.rot[2])];
			if (b.cubes.length) o.cubes = b.cubes.map(c => {
				const cc = {
					origin: [R3(8 - c.to[0]), R3(c.from[1]), R3(c.from[2] - 8)],
					size: [0, 1, 2].map(i => R3(c.to[i] - c.from[i])),
					uv: {},
				};
				for (const f of FACES) { const r = c.uv[f]; cc.uv[geoFaceName(f)] = { uv: [r.u, r.v], uv_size: [r.w, r.h] }; }
				return cc;
			});
			return o;
		}),
	}],
};
writeJson(path.join(RES, 'geo/stark_fabricator.geo.json'), geo);

// ---- animations. frames: [seconds, { bone: { rot:[render deg], pos:[render px], scale } }, easing?]
const animations = {};
const clips = {};
function clip(name, len, frames) {
	// only frames that define a channel become its keyframes, so channels never snap back to rest in between
	const out = {};
	const names = new Set(frames.flatMap(([, p]) => Object.keys(p)));
	for (const bn of names) {
		const ch = {};
		for (const key of ['rot', 'pos', 'scale']) {
			const k = {};
			for (const [t, p, e] of frames) {
				const entry = p[bn];
				if (!entry || entry[key] === undefined) continue;
				const v = entry[key];
				let vec = key === 'rot' ? [-v[0], -v[1], v[2]] : key === 'pos' ? [-v[0], v[1], v[2]] : [v, v, v];
				vec = vec.map(n => R3(n) + 0);
				const ease = entry.linear ? null : e;
				k[t.toFixed(4)] = ease ? { vector: vec, easing: ease } : vec;
			}
			if (Object.keys(k).length) ch[key === 'rot' ? 'rotation' : key === 'pos' ? 'position' : 'scale'] = k;
		}
		out[bn] = ch;
	}
	animations['animation.stark_fabricator.' + name] = { loop: true, animation_length: len, bones: out };
	clips[name] = { len, frames };
}
// IDLE (6 s): arm parked over the parts tray, breathing; standby hologram turning slowly; scan ring drifting up.
clip('idle', 6, [
	[0, { arm_base: { rot: [0, 0, 0] }, arm_lower: { rot: [0, 0, 0] }, arm_head: { rot: [0, 0, 0] }, holo_helmet: { rot: [0, 0, 0], scale: 0.8, linear: true }, holo_scan: { pos: [0, 0, 0], scale: 1 }, arm_tip: { scale: 1 }, sparks: { scale: 0 }, glow_chaser: { pos: [0, 0, 0], scale: 0 } }],
	[1.5, { arm_base: { rot: [0, 6, 0] }, arm_lower: { rot: [3, 0, 0] }, arm_head: { rot: [-4, 0, 0] }, holo_helmet: { rot: [0, 90, 0], scale: 0.8, linear: true }, holo_scan: { pos: [0, 1.75, 0], scale: 0.9 }, arm_tip: { scale: 0.6 }, sparks: { scale: 0 }, glow_chaser: { pos: [0, 0, 0], scale: 0 } }, 'easeinoutsine'],
	[3, { arm_base: { rot: [0, 0, 0] }, arm_lower: { rot: [0, 0, 0] }, arm_head: { rot: [0, 0, 0] }, holo_helmet: { rot: [0, 180, 0], scale: 0.8, linear: true }, holo_scan: { pos: [0, 3.5, 0], scale: 0.8 }, arm_tip: { scale: 1 }, sparks: { scale: 0 }, glow_chaser: { pos: [0, 0, 0], scale: 0 } }, 'easeinoutsine'],
	[4.5, { arm_base: { rot: [0, -5, 0] }, arm_lower: { rot: [-2, 0, 0] }, arm_head: { rot: [3, 0, 0] }, holo_helmet: { rot: [0, 270, 0], scale: 0.8, linear: true }, holo_scan: { pos: [0, 1.75, 0], scale: 0.9 }, arm_tip: { scale: 0.6 }, sparks: { scale: 0 }, glow_chaser: { pos: [0, 0, 0], scale: 0 } }, 'easeinoutsine'],
	[6, { arm_base: { rot: [0, 0, 0] }, arm_lower: { rot: [0, 0, 0] }, arm_head: { rot: [0, 0, 0] }, holo_helmet: { rot: [0, 360, 0], scale: 0.8, linear: true }, holo_scan: { pos: [0, 0, 0], scale: 1 }, arm_tip: { scale: 1 }, sparks: { scale: 0 }, glow_chaser: { pos: [0, 0, 0], scale: 0 } }, 'easeinoutsine'],
]);
// WORKING (2 s loop): arm swung over the plate, the emitter welding in short strokes with sparks; the hologram
// spins fast and the scan ring sweeps; a light pulse runs along the front strip.
// IK for the working strokes: aim the base (yaw) at the target, then solve the shoulder (arm_lower) + elbow
// (arm_upper) offsets so the welding tip lands on it, keeping the emitter head hanging straight down.
const TIP_LOCAL = [ARM_X, 13.45, 5.75];
const tipOf = pose => affApply(boneWorld(pose).arm_tip, TIP_LOCAL);
function armPose(yaw, lean, upper) {
	return { arm_base: { rot: [0, yaw, 0] }, arm_lower: { rot: [lean, 0, 0] }, arm_upper: { rot: [upper, 0, 0] }, arm_head: { rot: [-(lean + upper), 0, 0] } };
}
function solveArm(target) {
	const yaw = Math.atan2(-(target[0] - ARM_X), -(target[2] - ARM_Z)) / DEG;
	let best = null;
	const search = (l0, l1, u0, u1, step) => {
		for (let l = l0; l <= l1; l += step) for (let u = u0; u <= u1; u += step) {
			const p = tipOf(armPose(yaw, l, u));
			const e = Math.hypot(p[0] - target[0], p[1] - target[1], p[2] - target[2]) + 0.002 * Math.abs(l + u);
			if (!best || e < best.e) best = { e, lean: l, upper: u };
		}
	};
	search(-60, 30, -70, 70, 1);
	search(best.lean - 1, best.lean + 1, best.upper - 1, best.upper + 1, 0.1);
	if (best.e > 0.2) throw new Error(`arm cannot reach ${target}: err ${best.e}`);
	return { yaw: R3(yaw), lean: R3(best.lean), upper: R3(best.upper) };
}
// weld points down the right flank of the hologram (it occupies x 4.6-7.4, z 4.6-7.4), with lifts in between
const STROKES = [
	[0.0, [8.2, 12.4, 7.8], 1.6, 1.0, [5.5, 0, 0]],
	[0.25, [8.6, 13.2, 7.0], 0.7, 0],
	[0.5, [8.2, 12.4, 5.4], 1.6, 1.2, [0, 0, 0]],
	[0.75, [8.6, 13.2, 6.2], 0.7, 0],
	[1.0, [8.2, 12.4, 6.6], 1.6, 0.9, [-5.5, 0, 0]],
	[1.25, [8.7, 13.2, 5.6], 0.7, 0],
	[1.5, [8.2, 12.5, 4.8], 1.6, 1.2, [0, 0, 0]],
	[1.75, [8.6, 13.2, 6.6], 0.7, 0],
	[2.0, [8.2, 12.4, 7.8], 1.6, 1.0, [5.5, 0, 0]],
];
const HELMET_KEYS = { 0: [0, 1.05], 0.5: [90, 1.0], 1: [180, 1.05], 1.5: [270, 1.0], 2: [360, 1.05] };
const SCAN_KEYS = { 0: [0, 1.1], 0.5: [3.4, 0.85], 1: [0, 1.1], 1.5: [3.4, 0.85], 2: [0, 1.1] };
clip('working', 2, STROKES.map(([t, target, tip, spark, chase], i) => {
	const sol = solveArm(target);
	const p = armPose(sol.yaw, sol.lean, sol.upper);
	p.arm_tip = { scale: tip };
	p.sparks = { scale: spark };
	if (chase) p.glow_chaser = { pos: chase, scale: 1 };
	if (HELMET_KEYS[t]) p.holo_helmet = { rot: [0, HELMET_KEYS[t][0], 0], scale: HELMET_KEYS[t][1], linear: true };
	if (SCAN_KEYS[t]) p.holo_scan = { pos: [0, SCAN_KEYS[t][0], 0], scale: SCAN_KEYS[t][1] };
	return i === 0 ? [t, p] : [t, p, 'easeinoutsine'];
}));
writeJson(path.join(RES, 'animations/stark_fabricator.animation.json'), { format_version: '1.8.0', animations });
// where the welding tip ends up (block px) -- the Java side spawns its sparks here (StarkFabricatorBlock.TIP_*)
const tipAt = pose => tipOf(pose).map(v => v.toFixed(2));
console.log('tip idle', tipAt(clips.idle.frames[0][1]).join(', '), '| working', clips.working.frames.map(fr => tipAt(fr[1]).join(',')).join('  '));
console.log('wrote models, blockstates, geo, animations, textures');

// ================================================================ preview (software rasteriser)
if (PREVIEW) {
	fs.mkdirSync(PREVIEW, { recursive: true });
	const shadeOf = n => 0.6 * n[0] * n[0] + 0.8 * n[2] * n[2] + (n[1] > 0 ? 1.0 : 0.5) * n[1] * n[1];
	// quads: { p0, p1, p3 (block px, s/t corners), sample(s,t) -> {c,a,glow}, n }
	function jsonQuads(elements, atlas, yRot = 0) {
		const out = [];
		const Y = pivotRot([8, 8, 8], [0, -yRot, 0]); // blockstate y = clockwise from above
		for (const e of elements) {
			let A = IDENT;
			if (e.rot) {
				const r = [0, 0, 0]; r[{ x: 0, y: 1, z: 2 }[e.rot.axis]] = e.rot.angle;
				A = pivotRot(e.rot.origin, r);
			}
			A = affMul(Y, A);
			for (const f of FACES) {
				const r = e.rects[f];
				if (!r) continue;
				const pt = (s, t) => affApply(A, facePointJ(e.from, e.to, f, s, t));
				const n = mv(A.m, NORMAL[f]);
				out.push({
					p0: pt(0, 0), p1: pt(1, 0), p3: pt(0, 1), n,
					sample: (s, t) => {
						const x = r.u + Math.min(r.w - 1, Math.floor(s * r.w)), y = r.v + Math.min(r.h - 1, Math.floor(t * r.h));
						const i = (y * atlas.W + x) * 4;
						return { c: [atlas.tex[i], atlas.tex[i + 1], atlas.tex[i + 2]], a: atlas.tex[i + 3], glow: 0 };
					},
				});
			}
		}
		return out;
	}
	function geoQuads(pose) {
		const W = boneWorld(pose);
		const out = [];
		for (const b of bones) for (const c of b.cubes) {
			const A = W[b.name];
			for (const f of FACES) {
				const r = c.uv[f];
				const pt = (s, t) => affApply(A, geoFacePoint(c.from, c.to, f, s, t));
				out.push({
					p0: pt(0, 0), p1: pt(1, 0), p3: pt(0, 1), n: mv(A.m, NORMAL[f]), geo: true,
					sample: (s, t) => {
						const x = r.u + Math.min(r.w - 1, Math.floor(s * r.w)), y = r.v + Math.min(r.h - 1, Math.floor(t * r.h));
						const i = (y * 64 + x) * 4;
						const g = rigAtlas.glow[i + 3];
						return { c: [rigAtlas.tex[i], rigAtlas.tex[i + 1], rigAtlas.tex[i + 2]], a: g > 0 ? 0 : rigAtlas.tex[i + 3], glow: g };
					},
				});
			}
		}
		return out;
	}
	// a stand-in for the racked suit: armour-stand proportions at the renderer's scale/offset
	function suitQuads() {
		const k = SUIT_SCALE, y0 = SUIT_Y_OFFSET * 16;
		const boxes = [ // armour-stand model px (feet at 0), +1 px armour inflation
			[[-4.5, 0, -2.5], [-0.2, 12.5, 2.5]], [[0.2, 0, -2.5], [4.5, 12.5, 2.5]],
			[[-5, 12, -3], [5, 24.5, 3]], [[-9, 13, -2.5], [-5, 25, 2.5]], [[5, 13, -2.5], [9, 25, 2.5]],
			[[-4.5, 24, -4.5], [4.5, 33, 4.5]],
		];
		const out = [];
		for (const [a, b] of boxes) {
			const from = [8 + a[0] * k, y0 + a[1] * k, 8 + a[2] * k], to = [8 + b[0] * k, y0 + b[1] * k, 8 + b[2] * k];
			for (const f of FACES) out.push({
				p0: facePointJ(from, to, f, 0, 0), p1: facePointJ(from, to, f, 1, 0), p3: facePointJ(from, to, f, 0, 1), n: NORMAL[f],
				sample: () => ({ c: [176, 40, 36], a: 255, glow: 0 }),
			});
		}
		return out;
	}
	// view: yaw (deg) around y then pitch around x; camera looks along view -z; scale px per model px
	function render(quads, yaw, pitch, scale, W, H, center, file, opts = {}) {
		const V = mm3(Rx(pitch * DEG), Ry(yaw * DEG));
		const img = Buffer.alloc(W * H * 4); const zb = new Float32Array(W * H).fill(-1e9);
		for (let i = 0; i < W * H; i++) { const bg = opts.bg || [196, 200, 206]; img[i * 4] = bg[0]; img[i * 4 + 1] = bg[1]; img[i * 4 + 2] = bg[2]; img[i * 4 + 3] = 255; }
		const proj = p => { const q = mv(V, [p[0] - center[0], p[1] - center[1], p[2] - center[2]]); return [W / 2 + q[0] * scale, H / 2 - q[1] * scale, q[2]]; };
		const passes = [false, true];
		for (const glowPass of passes) for (const qd of quads) {
			const n = mv(V, qd.n);
			if (n[2] <= 1e-6 && !qd.geo) continue;      // back-face cull (geo glow layer is drawn double-sided)
			if (n[2] <= 1e-6 && !glowPass) continue;
			const a = proj(qd.p0), b = proj(qd.p1), d = proj(qd.p3);
			const ex = [b[0] - a[0], b[1] - a[1]], ey = [d[0] - a[0], d[1] - a[1]];
			const det = ex[0] * ey[1] - ex[1] * ey[0];
			if (Math.abs(det) < 1e-9) continue;
			const xs = [a[0], b[0], d[0], b[0] + ey[0]], ys = [a[1], b[1], d[1], b[1] + ey[1]];
			const xmin = Math.max(0, Math.floor(Math.min(...xs))), xmax = Math.min(W - 1, Math.ceil(Math.max(...xs)));
			const ymin = Math.max(0, Math.floor(Math.min(...ys))), ymax = Math.min(H - 1, Math.ceil(Math.max(...ys)));
			const lit = (opts.flat ? 1 : shadeOf(qd.n));
			for (let y = ymin; y <= ymax; y++) for (let x = xmin; x <= xmax; x++) {
				const px = x + 0.5 - a[0], py = y + 0.5 - a[1];
				const s = (px * ey[1] - py * ey[0]) / det, t = (ex[0] * py - ex[1] * px) / det;
				if (s < 0 || s > 1 || t < 0 || t > 1) continue;
				const z = a[2] + s * (b[2] - a[2]) + t * (d[2] - a[2]);
				const o = y * W + x;
				const smp = qd.sample(s, t);
				if (!glowPass) {
					if (smp.a < 128 || z < zb[o]) continue;
					zb[o] = z;
					for (let k = 0; k < 3; k++) img[o * 4 + k] = Math.min(255, smp.c[k] * lit);
				} else {
					if (!smp.glow || z < zb[o] - 0.01) continue;
					const al = smp.glow / 255;
					for (let k = 0; k < 3; k++) img[o * 4 + k] = Math.round(img[o * 4 + k] * (1 - al) + smp.c[k] * al);
				}
			}
		}
		png.write(path.join(PREVIEW, file), { w: W, h: H, data: img });
	}
	function sheet(name, quadsFor, center, scale, W, H) {
		render(quadsFor(), 180, 0, scale, W, H, center, `${name}_front.png`);
		render(quadsFor(), -90, 0, scale, W, H, center, `${name}_side.png`);
		render(quadsFor(), 180 - 40, 28, scale, W, H, center, `${name}_34.png`);
		render(quadsFor(), 40, 28, scale, W, H, center, `${name}_34back.png`);
		render(quadsFor(), 180, 89.9, scale, W, H, center, `${name}_top.png`);
	}
	// GUI icon: vanilla applies translate, rotationXYZ, scale about the model centre; slot = 16x16 model px
	function guiIcon(quads, disp, file) {
		const Rg = mm3(Rx(disp.rotation[0] * DEG), mm3(Ry(disp.rotation[1] * DEG), Rz(disp.rotation[2] * DEG)));
		const s = disp.scale[0], T = disp.translation;
		const tq = quads.map(q => {
			const tp = p => { const v = mv(Rg, [p[0] - 8, p[1] - 8, p[2] - 8]).map(c => c * s); return [v[0] + T[0], v[1] + T[1], v[2] + T[2]]; };
			return { ...q, p0: tp(q.p0), p1: tp(q.p1), p3: tp(q.p3), n: mv(Rg, q.n) };
		});
		render(tq, 0, 0, 8, 128, 128, [0, 0, 0], file, { bg: [139, 139, 139] });
	}
	const fabIdle = clips.idle.frames[0][1], fabWork = clips.working.frames[0][1];
	const fabQ = pose => () => [...jsonQuads(FAB, fabAtlas), ...geoQuads(pose)];
	sheet('fab_idle', fabQ(fabIdle), [8, 8, 8], 22, 520, 460);
	sheet('fab_work', fabQ(fabWork), [8, 8, 8], 22, 520, 460);
	render(fabQ(clips.working.frames[2][1])(), 180 - 40, 28, 22, 520, 460, [8, 8, 8], 'fab_work_b_34.png');
	render([...jsonQuads(FAB_ITEM, fabAtlas)], 180 - 40, 28, 22, 520, 460, [8, 8, 8], 'fab_itemmodel_34.png');
	guiIcon(jsonQuads(FAB_ITEM, fabAtlas), FAB_DISPLAY.gui, 'fab_gui.png');
	const platQ = (withSuit) => () => [...jsonQuads(PLAT, platAtlas), ...(withSuit ? suitQuads() : [])];
	sheet('plat', platQ(false), [8, 14, 8], 16, 520, 600);
	sheet('plat_suit', platQ(true), [8, 14, 8], 16, 520, 600);
	guiIcon(jsonQuads(PLAT, platAtlas), PLAT_DISPLAY.gui, 'plat_gui.png');
	// textures, 6x
	for (const [n, at] of [['fab_atlas', fabAtlas], ['plat_atlas', platAtlas], ['rig_atlas', rigAtlas]]) {
		const up = png.upscale({ w: 64, h: 64, data: at.tex }, 6, [255, 0, 255]);
		png.write(path.join(PREVIEW, `${n}.png`), up);
	}
	console.log(`previews written to ${PREVIEW}`);
}
