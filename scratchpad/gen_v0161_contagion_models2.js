// v0.16.1 Ultron Contagion GeckoLib structures, REMAKE (replaces section 6 of gen_v0161_contagion_assets.js, which no
// longer writes these four):
//   ultron_nexus, vibranium_core, lift_engine, uplink_antenna
//   -> geo/ultron/<n>.geo.json, textures/entity/ultron/<n>.png + <n>_glowmask.png, animations/ultron/<n>.animation.json
// usage (from the repo/worktree root):  node scratchpad/gen_v0161_contagion_models2.js [previewDir]
// With a previewDir (default: <main repo>/scratchpad/shots_v0161/contagion if it exists) it also software-renders
// posed previews models2_<n>.png (front / three-quarter / side + an animated frame), using the Titan generator's
// renderer maths (gen_v01420_titan_model.js) so the previews match GeckoLib's rotation conventions.
//
// Modelling: every cube is placed through a FRAME (3x3 rotation + origin, composed freely: radial yaw, lean, twist,
// tube segments along arbitrary directions); the frame rotation is decomposed into GeckoLib's Euler angles and the
// round-trip is asserted. Per-face UV at 3 texels per model pixel (dropped automatically for the largest faces if a
// texture would not fit 512x512); identical cubes share their UV rects. Every face of every cube is hand-shaded:
// bevelled edges, panel seams, rivets, scratches, grime, red light strips / vein channels (also in the glowmask).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./v0161_asset_lib.js');
const { encodePng, clamp, mix, mul, hex, hash3, vnoise, fbm, RES } = L;

let PREVIEW = process.argv[2] || null;
if (!PREVIEW) {
	const guess = path.join(L.ROOT, '..', '..', '..', 'scratchpad', 'shots_v0161', 'contagion');
	if (fs.existsSync(guess)) PREVIEW = guess;
}
const ONLY = process.env.ONLY || null; // ONLY=ultron_nexus node ... to iterate on one model

// ================================================================ palette
const RED = hex('FF2010'), RED_L = hex('FF6040'), RED_M = hex('C8160C'), RED_D = hex('6E0806'), HOT = hex('FF9C7A'), WHOT = [255, 228, 214];
const BLK = hex('16181E'), BLK2 = hex('1E2128'), PLT = hex('2E323B'), PLT2 = hex('3A3F49'), STL = hex('454B57'), STL2 = hex('5C6370');
const SIL = hex('A8B0BE'), SIL2 = hex('D8DEE8');
const VIO_D = hex('3A2370'), VIO = hex('8A4DFF'), VIO_M = hex('A97CFF'), VIO_L = hex('E6D8FF');
const ROCK = hex('24242A'), ROCK2 = hex('4A4A50');

// ================================================================ maths (rotation convention copied from the Titan generator)
const DEG = Math.PI / 180;
const I3 = () => [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
const mm3 = (a, b) => a.map((r, i) => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const mv3 = (m, v) => [0, 1, 2].map(i => m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2]);
const Rx = a => { const c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; };
const Ry = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; };
const Rz = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; };
const MIR = [[-1, 0, 0], [0, 1, 0], [0, 0, 1]];
// GeckoLib: baked x = -json x, baked rotation = (-rx, -ry, rz), applied as Rz * Ry * Rx.
function rotJ(r) { return mm3(MIR, mm3(Rz(r[2] * DEG), mm3(Ry(-r[1] * DEG), mm3(Rx(-r[0] * DEG), MIR)))); }
// inverse of rotJ: geo Euler angles (degrees) for a json-space rotation matrix
function eulerOf(M) {
	const P = mm3(MIR, mm3(M, MIR));
	const b = -Math.asin(clamp(P[2][0], -1, 1));
	let c, a;
	if (Math.abs(P[2][0]) < 1 - 1e-10) { c = Math.atan2(P[2][1], P[2][2]); a = Math.atan2(P[1][0], P[0][0]); }
	else { a = 0; c = Math.atan2(-P[1][2], P[1][1]); }
	const r = [-c / DEG, -b / DEG, a / DEG].map(v => Math.round(v * 1000) / 1000);
	const back = rotJ(r);
	for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) if (Math.abs(back[i][j] - M[i][j]) > 2e-3) throw new Error('euler round-trip failed ' + JSON.stringify(r) + JSON.stringify(M) + JSON.stringify(back));
	return r;
}
const vadd = (a, b) => a.map((v, i) => v + b[i]);
const vsub = (a, b) => a.map((v, i) => v - b[i]);
const vscl = (a, k) => a.map(v => v * k);
const vlen = a => Math.hypot(a[0], a[1], a[2]);
const vnorm = a => vscl(a, 1 / (vlen(a) || 1));
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const isI = M => M.every((r, i) => r.every((v, j) => Math.abs(v - (i === j ? 1 : 0)) < 1e-9));

// frames: local -> json space.  f.t(x,y,z) translate in local axes; f.rx/ry/rz(deg) rotate (local post-multiply).
class Frame {
	constructor(M = I3(), O = [0, 0, 0]) { this.M = M; this.O = O; }
	t(x, y, z) { return new Frame(this.M, vadd(this.O, mv3(this.M, [x, y, z]))); }
	rx(d) { return new Frame(mm3(this.M, Rx(d * DEG)), this.O); }
	ry(d) { return new Frame(mm3(this.M, Ry(d * DEG)), this.O); }
	rz(d) { return new Frame(mm3(this.M, Rz(d * DEG)), this.O); }
	p(l) { return vadd(this.O, mv3(this.M, l)); }
}
const F0 = new Frame();
const at = (x, y, z) => new Frame(I3(), [x, y, z]);
// frame at p whose local +Z points along dir and local +Y as close as possible to `up`
function orient(p, dir, up = [0, 1, 0]) {
	const z = vnorm(dir);
	let x = cross(up, z);
	if (vlen(x) < 1e-4) x = cross([1, 0, 0], z);
	x = vnorm(x);
	const y = cross(z, x);
	return new Frame([[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]], p.slice());
}
// radial frame: origin on the circle (radius r, height y, angle th degrees, 0 = +Z), local +Z = outward, +Y = up
const radial = (th, r, y) => at(0, y, 0).ry(th).t(0, 0, r);

// seeded PRNG
function rng(seed) { let a = seed >>> 0; return () => { a |= 0; a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
const strHash = s => { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; };

// ================================================================ model
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
const NORMALS = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
class Model {
	constructor(name) { this.name = name; this.bones = []; this.B = {}; }
	bone(name, parent, pivot) {
		if (this.B[name]) throw new Error('dup bone ' + name);
		if (parent && !this.B[parent]) throw new Error('missing parent ' + parent);
		const b = { name, parent, pivot: pivot.slice(), rot: [0, 0, 0], cubes: [] }; this.bones.push(b); this.B[name] = b; return b;
	}
	// box in frame-local coordinates lo..hi
	box(bone, f, lo, hi, mat, opts = {}) {
		const size = [0, 1, 2].map(i => hi[i] - lo[i]);
		if (size.some(v => v <= 0)) throw new Error(`${this.name}: bad box size ${size}`);
		const r = isI(f.M) ? null : eulerOf(f.M);
		const from = r ? vadd(f.O, lo) : vadd(f.O, lo);
		const c = { bone, from, to: vadd(from, size), size, mat, rot: r, pivot: r ? f.O.slice() : null, opts, M: f.M, O: f.O, lo };
		this.B[bone].cubes.push(c); return c;
	}
	// centred box: footprint w x d centred on the frame's local x/z origin, from local y0 up by h
	cbox(bone, f, w, h, d, mat, opts = {}, y0 = 0) { return this.box(bone, f, [-w / 2, y0, -d / 2], [w / 2, y0 + h, d / 2], mat, opts); }
	// regular octagonal prism of across-flats width w: union of four w x (w tan 22.5) slabs at 0/45/90/135 degrees
	oct(bone, f, w, h, mat, opts = {}) {
		const s = w * Math.tan(22.5 * DEG) + 0.02;
		for (let k = 0; k < 4; k++) this.cbox(bone, f.ry(k * 45), s, h, w, mat, opts);
	}
	// tube through points: rectangular cross-section w (local x) by h (local y, toward `up`), overlap at joints
	tube(bone, pts, w, h, mat, opts = {}, up = [0, 1, 0], taper = 1) {
		for (let k = 0; k < pts.length - 1; k++) {
			const a = pts[k], b = pts[k + 1], d = vsub(b, a), len = vlen(d);
			const s = 1 - (1 - taper) * k / Math.max(1, pts.length - 2);
			const ww = Math.max(0.5, w * s), hh = Math.max(0.5, h * s);
			const ext = Math.min(ww, hh) * 0.45;
			const upv = typeof up === 'function' ? up(k, a, b) : up;
			const f = orient(a, d, upv);
			this.box(bone, f, [-ww / 2, -hh / 2, k === 0 ? 0 : -ext], [ww / 2, hh / 2, len + (k === pts.length - 2 ? 0 : ext)], mat, opts);
		}
	}
	allCubes() { return this.bones.flatMap(b => b.cubes); }
}

// ================================================================ texture layout (per-face UV, shared rects)
const TEX_MAX = 512;
function faceDims(c, f) {
	const s = c.size;
	return (f === 'north' || f === 'south') ? [s[0], s[1]] : (f === 'east' || f === 'west') ? [s[2], s[1]] : [s[0], s[2]];
}
// 3D point (cube-local, relative to `from`) of face texel (s, t in 0..1, t = 0 at the top for side faces) -- Titan's verified mapping
function faceLocal(c, f, s, t) {
	const [X, Y, Z] = c.size;
	switch (f) {
		case 'north': return [s * X, Y - t * Y, 0];
		case 'south': return [X - s * X, Y - t * Y, Z];
		case 'east': return [X, Y - t * Y, t * 0 + s * Z];
		case 'west': return [0, Y - t * Y, Z - s * Z];
		case 'up': return [X - s * X, Y, t * Z];
		default: return [X - s * X, 0, Z - t * Z];
	}
}
function shareKey(c) {
	if (c.opts.unique) return 'u' + Math.random();
	const o = Object.assign({}, c.opts); delete o.d;
	return JSON.stringify([c.size.map(v => Math.round(v * 100)), c.mat, o]);
}
function layout(m) {
	const cubes = m.allCubes();
	const groups = new Map();
	for (const c of cubes) { const k = shareKey(c); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(c); c.key = k; c.seed = strHash(k) % 100000; }
	for (const D of [3, 2.75, 2.5, 2.25, 2, 1.75, 1.5, 1.25, 1]) {
		const rects = [];
		for (const [k, cs] of groups) {
			const c = cs[0]; c.uvr = {};
			const low = cs.every(q => q.from[1] <= 0.6 && !q.rot) || c.opts.lowDown;
			for (const f of FACES) {
				const [fw, fh] = faceDims(c, f);
				let d = c.opts.d || 3;
				if (Math.max(fw, fh) > 7) d = Math.min(d, D);
				if (f === 'down' && low) d = 1;
				const r = { f, fw, fh, w: Math.max(1, Math.round(fw * d)), h: Math.max(1, Math.round(fh * d)), d, c };
				c.uvr[f] = r; rects.push(r);
			}
			for (const q of cs) q.uvr = c.uvr;
		}
		rects.sort((a, b) => b.h - a.h || b.w - a.w);
		let x = 0, y = 0, rowH = 0;
		for (const r of rects) {
			if (x + r.w > TEX_MAX) { x = 0; y += rowH; rowH = 0; }
			r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
		}
		const need = y + rowH;
		if (need <= TEX_MAX) {
			let H = 16; while (H < need) H *= 2;
			m.TW = TEX_MAX; m.TH = H; m.D = D; m.rects = rects; m.groups = groups;
			// shrink width if possible (keep it simple: 512 wide)
			return;
		}
	}
	throw new Error(m.name + ': texture does not fit 512x512');
}

// ================================================================ painting
const FIDX = { north: 0, south: 1, east: 2, west: 3, up: 4, down: 5 };
const MAT = {
	black: BLK, black2: BLK2, plate: PLT, plate2: PLT2, steel: STL, steel2: STL2, silver: SIL,
};
function lightCol(dc) { return dc < 0.12 ? WHOT : dc < 0.3 ? HOT : dc < 0.55 ? RED_L : RED; }
function veinCol(b) { return b > 0.9 ? HOT : b > 0.6 ? RED_L : b > 0.35 ? RED : RED_M; }

// scratches: a few random segments per face (face-local px coords)
function scratchList(seed, fw, fh) {
	const R = rng(seed), out = [];
	const n = Math.floor(fw * fh / 60) + (R() < 0.5 ? 1 : 0);
	for (let k = 0; k < Math.min(n, 6); k++) {
		const x = R() * fw, y = R() * fh, a = (R() - 0.5) * 1.6 + (R() < 0.5 ? 0 : Math.PI / 2) * 0.3, l = 1 + R() * 3.5;
		out.push([x, y, x + Math.cos(a) * l, y + Math.sin(a) * l]);
	}
	return out;
}
function segDist(px, py, s) {
	const [x0, y0, x1, y1] = s, dx = x1 - x0, dy = y1 - y0, L2 = dx * dx + dy * dy || 1;
	const t = clamp(((px - x0) * dx + (py - y0) * dy) / L2, 0, 1);
	return Math.hypot(px - x0 - t * dx, py - y0 - t * dy);
}

function paintModel(m) {
	const TW = m.TW, TH = m.TH;
	const tex = Buffer.alloc(TW * TH * 4), glow = Buffer.alloc(TW * TH * 4);
	const put = (buf, x, y, col, a = 255) => { const i = (y * TW + x) * 4; buf[i] = clamp(Math.round(col[0])); buf[i + 1] = clamp(Math.round(col[1])); buf[i + 2] = clamp(Math.round(col[2])); buf[i + 3] = a; };
	for (const [k, cs] of m.groups) {
		const c = cs[0];
		for (const f of FACES) {
			const r = c.uvr[f];
			const scr = scratchList(c.seed * 7 + FIDX[f], r.fw, r.fh);
			for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
				const s = (x + 0.5) / r.w, t = (y + 0.5) / r.h;
				const ctx = { c, f, x, y, w: r.w, h: r.h, s, t, u: s * r.fw, v: t * r.fh, fw: r.fw, fh: r.fh, d: r.d, lp: faceLocal(c, f, s, t), side: f !== 'up' && f !== 'down', fi: FIDX[f], seed: c.seed, scr };
				const out = shade(ctx);
				put(tex, r.u + x, r.v + y, out.col);
				if (out.glow) put(glow, r.u + x, r.v + y, out.glow);
			}
		}
	}
	m.tex = tex; m.glow = glow;
}

function shade(q) {
	const { c } = q, o = c.opts;
	switch (c.mat) {
		case 'red': return redLight(q, 1);
		case 'reddim': return redLight(q, 0.55);
		case 'eye': return eyePaint(q);
		case 'crystal': return crystal(q);
		case 'rock': return rock(q);
		case 'cable': return cable(q);
		case 'vein': return veinPaint(q);
		case 'grille': return grille(q);
		case 'nozzle': return nozzle(q);
		default: return metal(q, MAT[c.mat] || PLT, o);
	}
}

function bevel(q, col, strength = 1) {
	const { x, y, w, h, f } = q;
	if (q.side) {
		if (y === 0) col = mix(col, SIL2, 0.32 * strength);
		else if (y === 1 && h > 6) col = mix(col, SIL, 0.1 * strength);
		if (y === h - 1) col = mul(col, 1 - 0.42 * strength);
		if (x === 0) col = mix(col, SIL, 0.16 * strength);
		if (x === w - 1) col = mul(col, 1 - 0.28 * strength);
	} else if (f === 'up') {
		if (x === 0 || y === 0 || x === w - 1 || y === h - 1) col = mix(col, SIL2, 0.25 * strength);
		else if ((x === 1 || y === 1 || x === w - 2 || y === h - 2) && w > 5 && h > 5) col = mul(col, 0.86);
	} else {
		if (x === 0 || y === 0 || x === w - 1 || y === h - 1) col = mul(col, 0.8);
	}
	return col;
}

function metal(q, base, o) {
	const { f, s, t, u, v, fw, fh, lp, side, fi, seed, x, y, w, h, d } = q;
	let col = base.slice();
	if (side) col = mul(col, 1.12 - 0.24 * t);
	else if (f === 'up') col = mul(col, 1.14);
	else col = mul(col, 0.66);
	const n = fbm(lp[0] * 0.21 + seed * 0.013, lp[1] * 0.21 + fi * 3.1, lp[2] * 0.21 + seed * 0.007, 3);
	col = mul(col, 0.82 + 0.34 * n);
	col = mul(col, 0.94 + 0.12 * hash3(x + seed, y, fi));
	if (side) col = mul(col, 0.96 + 0.07 * vnoise(u * 1.7 + seed, fi, 0.3)); // vertical streaks
	// panel seams + rivets
	const nu = fw >= 9 ? Math.max(1, Math.round(fw / 7.5)) : 1;
	const nv = fh >= 11 ? Math.max(1, Math.round(fh / 9)) : 1;
	const pu = fw / nu, pv = fh / nv;
	const su = u - Math.round(u / pu) * pu, sv = v - Math.round(v / pv) * pv; // signed distance to nearest seam (px)
	const tex = 1 / d;
	const atEdgeU = Math.round(u / pu) === 0 || Math.round(u / pu) === nu, atEdgeV = Math.round(v / pv) === 0 || Math.round(v / pv) === nv;
	if (!o.noSeams) {
		if (nu > 1 && !atEdgeU && Math.abs(su) < tex * 0.55) col = mul(col, 0.5);
		else if (nu > 1 && !atEdgeU && su > 0 && su < tex * 1.5) col = mix(col, SIL, 0.14);
		if (nv > 1 && !atEdgeV && Math.abs(sv) < tex * 0.55) col = mul(col, 0.5);
		else if (nv > 1 && !atEdgeV && sv > 0 && sv < tex * 1.5) col = mix(col, SIL, 0.14);
	}
	// rivets near panel corners (only on panels big enough)
	if (!o.noRivets && pu >= 4 && pv >= 4 && d >= 2) {
		const ru = ((u % pu) + pu) % pu, rv = ((v % pv) + pv) % pv;
		for (const cu of [1.1, pu - 1.1]) for (const cv of [1.1, pv - 1.1]) {
			const dd = Math.hypot(ru - cu, rv - cv);
			if (dd < 0.36) col = mix(col, SIL2, 0.55);
			else if (dd < 0.62 && ru - cu > 0 && rv - cv > 0) col = mul(col, 0.55);
		}
	}
	// scratches
	if (!o.noScratch) for (const sg of q.scr) if (segDist(u, v, sg) < 0.17) { col = mix(col, SIL, 0.32); break; }
	// grime at the bottom of side faces
	if (side && t > 0.72) col = mul(col, 1 - 0.3 * (t - 0.72) / 0.28 * (0.6 + 0.4 * n));
	col = bevel(q, col, o.bevel == null ? 1 : o.bevel);
	let glow = null;
	// light features
	const L = (g) => { glow = g; col = g; };
	if (o.glowFaces && o.glowFaces.includes(f)) { const dc = Math.hypot(s - 0.5, t - 0.5) * 1.4; L(lightCol(dc)); return { col, glow }; }
	const sideSel = sel => sel === true ? side : (Array.isArray(sel) ? sel.includes(f) : false);
	if (o.trim && side) { // glowing seam line one texel inside the bottom (b) and/or top (t) edge
		if ((o.trim.includes('b') && y === h - 2) || (o.trim.includes('t') && y === 1)) L(((x + seed) % 7 === 0) ? HOT : RED);
	}
	if (o.stripV && sideSel(o.stripV) && fw >= 1) {
		const half = fw >= 5 && d >= 2 ? 1 : 0.5;
		if (Math.abs(u - fw / 2) <= half * tex * (d >= 2 ? 0.9 : 0.6) + 1e-6 && v > 0.6 && v < fh - 0.6) L((Math.floor(v * 1.3 + seed) % 5 === 0) ? HOT : RED);
		else if (Math.abs(u - fw / 2) <= tex * 1.6 && v > 0.6 && v < fh - 0.6) col = mix(col, RED_D, 0.6);
	}
	if (o.stripH && sideSel(o.stripH)) {
		if (Math.abs(v - fh / 2) <= tex * 0.55 && u > 0.5 && u < fw - 0.5) L((Math.floor(u * 1.3 + seed) % 5 === 0) ? HOT : RED);
		else if (Math.abs(v - fh / 2) <= tex * 1.6 && u > 0.5 && u < fw - 0.5) col = mix(col, RED_D, 0.6);
	}
	if (o.vein && side && fw >= 2.5) { // meandering vein channel(s) running up the face
		const nvn = Math.max(1, Math.round(fw / 9));
		for (let k = 0; k < nvn; k++) {
			const cx = fw * (k + 0.5) / nvn + jog(v, k + 5, seed, 3.4) * Math.min(1.6, fw * 0.2);
			const dd = Math.abs(u - cx);
			if (dd < tex * 0.6) { const b = 0.45 + 0.55 * (0.5 + 0.5 * Math.sin(v * 0.22 + seed + k)); L(veinCol(b)); break; }
			else if (dd < tex * 1.7) { col = mix(col, RED_D, 0.75); glow = mul(RED_D, 0.9); }
		}
	}
	if (o.stripL && o.stripL.includes(f)) { // light line along the box's local Z axis (tubes, beams)
		const along = (f === 'up' || f === 'down') ? Math.abs(u - fw / 2) : Math.abs(v - fh / 2);
		const lenc = (f === 'up' || f === 'down') ? v : u, lenL = (f === 'up' || f === 'down') ? fh : fw;
		if (lenc > 0.6 && lenc < lenL - 0.6) {
			if (along <= tex * 0.6) L((Math.floor(lenc * 1.3 + seed) % 5 === 0) ? HOT : RED);
			else if (along <= tex * 1.6) col = mix(col, RED_D, 0.6);
		}
	}
	if (o.dots && side && fw >= 3 && fh >= 3) { // small status lights in a row near the top
		const nd = Math.floor(fw / 2.5);
		for (let k = 0; k < nd; k++) { const cx = (k + 0.5) * fw / nd; if (Math.abs(u - cx) < 0.4 && Math.abs(v - 1.6) < 0.4) L(k % 2 ? RED : HOT); }
	}
	return { col, glow };
}

function redLight(q, k) {
	const { s, t, x, y, w, h, side } = q;
	const dc = Math.hypot(s - 0.5, t - 0.5) * 1.5;
	let col = lightCol(dc / k);
	if (x === 0 || y === 0 || x === w - 1 || y === h - 1) col = mix(col, RED_M, 0.5);
	if (k < 1) col = mul(col, 0.8);
	return { col, glow: col };
}
function eyePaint(q) {
	const { s, t, f, x, y, w, h } = q;
	const dc = Math.hypot(s - 0.5, t - 0.5);
	let col = dc < 0.1 ? WHOT : dc < 0.22 ? HOT : dc < 0.34 ? RED_L : dc < 0.46 ? RED : RED_M;
	if (Math.abs(dc - 0.3) < 0.025) col = mix(col, WHOT, 0.5);
	if (x === 0 || y === 0 || x === w - 1 || y === h - 1) col = RED_M;
	return { col, glow: col };
}
function crystal(q) {
	const { s, t, f, x, y, w, h, seed, lp, fi } = q;
	const k = q.c.opts.tone == null ? 0.5 : q.c.opts.tone;
	const n = fbm(lp[0] * 0.3 + seed * 0.01, lp[1] * 0.3, lp[2] * 0.3 + fi, 3);
	let col = mix(mix(VIO_D, VIO, 0.55 + 0.45 * k), VIO_M, 0.6 * n * k);
	col = mix(col, VIO_D, 0.45 * t * (1 - 0.5 * k));   // facets darken toward the lower edge
	let glow = null;
	const band = Math.abs(((s * 0.9 + t * 1.3 + seed * 0.003) * 3) % 1 - 0.5);
	if (band < 0.06) { col = mix(col, VIO_M, 0.6); glow = col; }
	if (Math.abs(s - t) < 0.05 || Math.abs(s + t - 1) < 0.04) { col = mix(VIO_M, VIO_L, 0.6); glow = col; }
	if (Math.hypot(s - 0.5, t - 0.5) < 0.12) { col = mix(col, VIO_L, 0.5); glow = col; }
	if (f === 'up') col = mix(col, VIO_L, 0.18);
	if (x === 0 || y === 0) { col = mix(col, VIO_L, 0.75); glow = col; }
	if (x === w - 1 || y === h - 1) col = mix(col, VIO_D, 0.5);
	col = mul(col, 0.92 + 0.14 * hash3(x, y, seed + fi));
	if (!glow && hash3(x, y, seed * 3 + fi) > 0.93) { col = VIO_L; glow = col; }   // sparkle
	if (!glow) glow = mul(col, 0.55);                                               // dim inner glow everywhere
	return { col, glow };
}
function rock(q) {
	const { lp, fi, seed, x, y, w, h, side, f } = q;
	const n = fbm(lp[0] * 0.35 + seed * 0.01, lp[1] * 0.35, lp[2] * 0.35 + fi, 4);
	let col = mix(ROCK, ROCK2, n);
	col = mul(col, f === 'up' ? 1.1 : f === 'down' ? 0.6 : 0.95 - 0.15 * q.t);
	col = mul(col, 0.9 + 0.2 * hash3(x, y, seed + fi));
	const cr = fbm(lp[0] * 0.5 + 7, lp[1] * 0.5 + 3, lp[2] * 0.5, 3);
	let glow = null;
	if (Math.abs(cr - 0.5) < 0.022) {
		if (q.c.opts.redCracks && fbm(lp[0] * 0.2, lp[1] * 0.2 + 9, lp[2] * 0.2, 2) > 0.5) { col = RED; glow = RED; }
		else col = mul(col, 0.45);
	}
	if (!glow) col = bevel(q, col, 0.5);
	return { col, glow };
}
function cable(q) {
	const { lp, f, s, t, x, y, w, h, c, seed } = q;
	// along = local z of the tube box (the cable's length axis)
	const along = lp[2] + c.lo[2];
	let col = mix(BLK, PLT, 0.35);
	const across = (f === 'east' || f === 'west') ? t : s;
	if (f !== 'north' && f !== 'south') {
		col = mul(col, 0.75 + 0.55 * Math.sin(across * Math.PI)); // round sheen
		const rib = ((along % 2.2) + 2.2) % 2.2;
		if (rib < 0.4) col = mul(col, 0.5); else if (rib < 0.8) col = mix(col, STL2, 0.25);
		if (f === 'down') col = mul(col, 0.7);
	} else col = bevel(q, mul(col, 0.8), 0.8);
	col = mul(col, 0.92 + 0.14 * hash3(x, y, seed));
	let glow = null;
	if (c.opts.redBand && f !== 'north' && f !== 'south') {
		const p = ((along + seed * 0.37) % 9 + 9) % 9;
		if (p < 0.7) { col = p < 0.35 ? HOT : RED; glow = col; }
	}
	if (c.opts.redLine && (f === 'up' || f === 'east') && Math.abs(across - 0.5) < 0.12) { col = RED; glow = RED; }
	return { col, glow };
}
// circuit-trace meander: straight runs that jog diagonally between random lanes (offset in -1..1)
function jog(v, k, seed, period = 3.2) {
	const n = Math.floor(v / period), fr = v / period - n;
	const o = i => hash3(i, k * 31 + 7, seed) * 2 - 1;
	const m = clamp((fr - 0.62) / 0.38, 0, 1);
	return o(n) + (o(n + 1) - o(n)) * m;
}
function veinPaint(q) {
	const { u, v, fw, fh, side, seed, d, x, y, f } = q;
	let base = metal(q, BLK2, { noRivets: true, noScratch: true, bevel: 0.4 }).col;
	if (!side) return { col: base, glow: null };
	const tex = 1 / d;
	let col = base, glow = null;
	const nvn = Math.max(1, Math.round(fw / 4.5));
	for (let k = 0; k < nvn; k++) {
		const cx = fw * (k + 0.5) / nvn + jog(v, k, seed, 2.6) * Math.min(1.5, fw * 0.16);
		const dd = Math.abs(u - cx);
		const b = 0.4 + 0.6 * (0.5 + 0.5 * Math.sin(v * 0.31 + k * 2.3 + seed * 0.02));
		if (dd < tex * 0.75) { col = veinCol(b); glow = col; return { col, glow }; }
		if (dd < tex * 2) { col = mix(base, RED_D, 0.8); glow = mul(RED_D, 0.85); }
	}
	// little horizontal capillaries
	const hv = ((v + seed * 0.13) % 5 + 5) % 5;
	if (hv < tex * 0.6 && hash3(Math.floor(v / 5), seed, 3) > 0.45) { const cc = mix(RED_M, RED, 0.5); return { col: cc, glow: cc }; }
	return { col, glow };
}
function grille(q) {
	const { v, side, d, x, y, w, h } = q;
	if (!side) return metal(q, PLT, { noSeams: true });
	const p = (v % 1.5) / 1.5;
	if (x === 0 || x === w - 1) return { col: mul(STL, 0.8), glow: null };
	if (p < 0.45) { let col = mix(STL, SIL, p < 0.15 ? 0.35 : 0); col = mul(col, 0.9 + 0.15 * hash3(x, y, 2)); return { col, glow: null }; }
	const col = mix([20, 6, 6], RED_D, 0.55 + 0.45 * Math.sin(p * Math.PI)); return { col, glow: mul(col, 1.1) };
}
function nozzle(q) {
	const { t, f, s, x, y, w, h, side } = q;
	if (f === 'down') { const dc = Math.hypot(s - 0.5, t - 0.5) * 1.4; const col = lightCol(dc); return { col, glow: col }; }
	if (f === 'up') return metal(q, STL, {});
	if (t < 0.35) return metal(q, STL, { noRivets: true });
	const k = (t - 0.35) / 0.65;
	let col = mix(RED_D, k > 0.75 ? HOT : RED_L, k);
	if (x % 3 === 0) col = mul(col, 0.8);
	return { col, glow: col };
}

// ================================================================ output
function geoJson(m) {
	const R3 = n => Math.round(n * 1000) / 1000;
	let lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
	for (const c of m.allCubes()) for (const cx of [0, 1]) for (const cy of [0, 1]) for (const cz of [0, 1]) {
		const p = c.O ? vadd(c.O, mv3(c.M, [c.lo[0] + cx * c.size[0], c.lo[1] + cy * c.size[1], c.lo[2] + cz * c.size[2]])) : null;
		for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], p[i]); hi[i] = Math.max(hi[i], p[i]); }
	}
	m.bounds = { lo, hi };
	const halfW = Math.max(-lo[0], hi[0], -lo[2], hi[2]);
	return {
		format_version: '1.12.0',
		'minecraft:geometry': [{
			description: {
				identifier: `geometry.${m.name}`, texture_width: m.TW, texture_height: m.TH,
				visible_bounds_width: Math.ceil((halfW * 2 + 16) / 16), visible_bounds_height: Math.ceil((hi[1] - Math.min(0, lo[1]) + 16) / 16),
				visible_bounds_offset: [0, R3((hi[1] + Math.min(0, lo[1])) / 32), 0],
			},
			bones: m.bones.map(b => {
				const o = { name: b.name };
				if (b.parent) o.parent = b.parent;
				o.pivot = b.pivot.map(R3);
				if (b.cubes.length) o.cubes = b.cubes.map(c => {
					const cc = { origin: c.from.map(R3), size: c.size.map(R3) };
					if (c.rot) { cc.pivot = c.pivot.map(R3); cc.rotation = c.rot.map(R3); }
					cc.uv = {};
					for (const f of FACES) { const r = c.uvr[f]; cc.uv[f] = { uv: [r.u, r.v], uv_size: [r.w, r.h] }; }
					return cc;
				});
				return o;
			}),
		}],
	};
}
const written = [];
function writeFile(rel, buf) { const p = path.join(RES, rel); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, buf); written.push(rel); }
function emit(m, animations) {
	layout(m);
	paintModel(m);
	const geo = geoJson(m);
	writeFile(`geo/ultron/${m.name}.geo.json`, JSON.stringify(geo, null, '\t') + '\n');
	writeFile(`textures/entity/ultron/${m.name}.png`, encodePng(m.TW, m.TH, m.tex));
	writeFile(`textures/entity/ultron/${m.name}_glowmask.png`, encodePng(m.TW, m.TH, m.glow));
	const anim = { format_version: '1.8.0', animations: {} };
	for (const [n, a] of Object.entries(animations)) anim.animations[n] = a.json;
	writeFile(`animations/ultron/${m.name}.animation.json`, JSON.stringify(anim, null, '\t') + '\n');
	m.anims = animations;
	console.log(`${m.name}: ${m.bones.length} bones, ${m.allCubes().length} cubes, ${m.groups.size} unique UV groups, texture ${m.TW}x${m.TH} @ ${m.D} texels/px (max), bounds y ${m.bounds.lo[1].toFixed(1)}..${m.bounds.hi[1].toFixed(1)}, half-width ${Math.max(-m.bounds.lo[0], m.bounds.hi[0], -m.bounds.lo[2], m.bounds.hi[2]).toFixed(1)}`);
	validate(m, geo, anim);
	if (PREVIEW) preview(m);
}

// ================================================================ animations: { bone: { rotation|position|scale: { tick: [x,y,z] | {v, e} } } }
const LIN = v => ({ v, e: 'linear' });
function mkClip(lengthTicks, loop, spec) {
	const json = L.clip(lengthTicks, loop, spec);
	return { json, spec, len: lengthTicks };
}
function spin(ticks, turns, axis = 1, steps = 8) { const o = {}; for (let k = 0; k <= steps; k++) { const v = [0, 0, 0]; v[axis] = 360 * turns * k / steps; o[Math.round(ticks * k / steps)] = LIN(v); } return o; }
function sampleChan(keys, t) {
	const ts = Object.keys(keys).map(Number).sort((a, b) => a - b);
	const val = k => { const x = keys[k]; return Array.isArray(x) ? x : x.v; };
	if (t <= ts[0]) return val(ts[0]);
	for (let i = 0; i < ts.length - 1; i++) if (t <= ts[i + 1]) {
		const a = ts[i], b = ts[i + 1], k = (t - a) / (b - a);
		const x = keys[b], e = Array.isArray(x) ? 'sine' : (x.e || 'sine');
		const kk = e === 'linear' ? k : 0.5 - 0.5 * Math.cos(k * Math.PI);
		return val(a).map((v, j) => v + (val(b)[j] - v) * kk);
	}
	return val(ts[ts.length - 1]);
}
function samplePose(clipObj, t) {
	const pose = {};
	for (const [bn, ch] of Object.entries(clipObj.spec)) {
		pose[bn] = { rot: ch.rotation ? sampleChan(ch.rotation, t) : [0, 0, 0], pos: ch.position ? sampleChan(ch.position, t) : [0, 0, 0], scl: ch.scale ? sampleChan(ch.scale, t) : [1, 1, 1] };
	}
	return pose;
}

// ================================================================ validation
function validate(m, geo, anim) {
	JSON.parse(JSON.stringify(geo)); JSON.parse(JSON.stringify(anim));
	const names = new Set(m.bones.map(b => b.name));
	for (const [n, a] of Object.entries(anim.animations)) for (const bn of Object.keys(a.bones)) if (!names.has(bn)) throw new Error(`${m.name}: ${n} animates missing bone ${bn}`);
	for (const c of m.allCubes()) for (const f of FACES) { const r = c.uvr[f]; if (r.u < 0 || r.v < 0 || r.u + r.w > m.TW || r.v + r.h > m.TH) throw new Error(`${m.name}: UV off texture`); }
	// every face texel opaque
	for (const r of m.rects) for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) if (m.tex[((r.v + y) * m.TW + r.u + x) * 4 + 3] !== 255) throw new Error(`${m.name}: unpainted texel`);
}

// ================================================================ preview (software renderer after gen_v01420_titan_model.js)
function preview(m) {
	fs.mkdirSync(PREVIEW, { recursive: true });
	const TW = m.TW;
	const texAt = (r, s, t) => {
		const x = Math.min(r.w - 1, Math.max(0, Math.floor(s * r.w))), y = Math.min(r.h - 1, Math.max(0, Math.floor(t * r.h)));
		const i = ((r.v + y) * TW + r.u + x) * 4;
		return [m.tex[i], m.tex[i + 1], m.tex[i + 2], m.glow[i + 3] ? [m.glow[i], m.glow[i + 1], m.glow[i + 2]] : null];
	};
	function boneMats(pose) {
		const W = {};
		for (const b of m.bones) {
			const p = pose[b.name] || { rot: [0, 0, 0], pos: [0, 0, 0], scl: [1, 1, 1] };
			const R = rotJ(p.rot);
			const S = [[p.scl[0], 0, 0], [0, p.scl[1], 0], [0, 0, p.scl[2]]];
			const RS = mm3(R, S);
			const piv = b.pivot;
			const mp = mv3(RS, piv);
			const local = { m: RS, t: [piv[0] - mp[0] + -p.pos[0] * 0 + p.pos[0], piv[1] - mp[1] + p.pos[1], piv[2] - mp[2] + p.pos[2]] };
			W[b.name] = b.parent ? { m: mm3(W[b.parent].m, local.m), t: vadd(mv3(W[b.parent].m, local.t), W[b.parent].t) } : local;
		}
		return W;
	}
	const Lgt = vnorm([-0.45, 0.85, -0.55]);
	function render(pose, yaw, pitch, Wd, Hd, fit) {
		const W = boneMats(pose);
		const img = new Float32Array(Wd * Hd * 3), gl = new Float32Array(Wd * Hd * 3);
		const zb = new Float32Array(Wd * Hd).fill(-Infinity);
		for (let y = 0; y < Hd; y++) for (let x = 0; x < Wd; x++) { const i = (y * Wd + x) * 3, k = y / Hd; img[i] = 44 + 30 * k; img[i + 1] = 48 + 30 * k; img[i + 2] = 58 + 26 * k; }
		const cyw = Math.cos(yaw * DEG), syw = Math.sin(yaw * DEG), cp = Math.cos(pitch * DEG), sp = Math.sin(pitch * DEG);
		const view = p => { const x = p[0] * cyw - p[2] * syw, z = p[0] * syw + p[2] * cyw; return [x, p[1] * cp + z * sp, -(z * cp - p[1] * sp)]; };
		const { scale, cx, cy } = fit;
		const scr = p => [Wd / 2 + (p[0] - cx) * scale, Hd / 2 - (p[1] - cy) * scale, p[2]];
		// ground disc (not when looking from below)
		if (pitch >= 0) for (let y = 0; y < Hd; y++) for (let x = 0; x < Wd; x++) {
			// invert: screen -> view plane, find world point on y=0 plane
			const vx = (x + 0.5 - Wd / 2) / scale + cx, vy = (Hd / 2 - y - 0.5) / scale + cy;
			if (sp < 1e-3) { if (vy < 0) { const i = (y * Wd + x) * 3; img[i] = 40; img[i + 1] = 38; img[i + 2] = 36; } continue; }
			const z = vy / sp; // world y = 0 -> vy = z*sp
			const wx = vx * cyw + z * syw, wz = -vx * syw + z * cyw;
			const rr = Math.hypot(wx, wz);
			if (rr < 70) { const i = (y * Wd + x) * 3; const k = 0.8 + 0.2 * hash3(Math.floor(wx / 4), Math.floor(wz / 4), 1); const tint = rr < 60 ? [46, 36, 38] : [40, 44, 40]; img[i] = tint[0] * k; img[i + 1] = tint[1] * k; img[i + 2] = tint[2] * k; zb[y * Wd + x] = -(z * cp) - 1e-3; }
		}
		for (const c of m.allCubes()) {
			const bw = W[c.bone];
			const cm = mm3(bw.m, c.M), co = vadd(mv3(bw.m, c.O), bw.t);
			const worldOf = lpt => vadd(mv3(cm, vadd(c.lo, lpt)), co);
			for (const f of FACES) {
				const r = c.uvr[f];
				const nW = vnorm(mv3(cm, NORMALS[f]));
				const nv = view(nW);
				if (nv[2] < -1e-6 && false) continue;
				const light = 0.42 + 0.58 * Math.max(0, dot(nW, Lgt)) + 0.08 * Math.max(0, nW[1]);
				const P = (s, t) => { const w = worldOf(faceLocal(c, f, s, t)); return [scr(view(w)), w[1]]; };
				const q = [P(0, 0), P(1, 0), P(0, 1), P(1, 1)];
				const tris = [[[0, 0, q[0]], [1, 0, q[1]], [0, 1, q[2]]], [[1, 0, q[1]], [1, 1, q[3]], [0, 1, q[2]]]];
				for (const tri of tris) {
					const [A, Bq, C] = tri.map(v => v[2][0]);
					const xs = [A[0], Bq[0], C[0]], ys = [A[1], Bq[1], C[1]];
					const x0 = Math.max(0, Math.floor(Math.min(...xs))), x1 = Math.min(Wd - 1, Math.ceil(Math.max(...xs)));
					const y0 = Math.max(0, Math.floor(Math.min(...ys))), y1 = Math.min(Hd - 1, Math.ceil(Math.max(...ys)));
					const den = (Bq[1] - C[1]) * (A[0] - C[0]) + (C[0] - Bq[0]) * (A[1] - C[1]);
					if (Math.abs(den) < 1e-9) continue;
					for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
						const px = x + 0.5, py = y + 0.5;
						const l1 = ((Bq[1] - C[1]) * (px - C[0]) + (C[0] - Bq[0]) * (py - C[1])) / den;
						const l2 = ((C[1] - A[1]) * (px - C[0]) + (A[0] - C[0]) * (py - C[1])) / den;
						const l3 = 1 - l1 - l2;
						if (l1 < -1e-4 || l2 < -1e-4 || l3 < -1e-4) continue;
						const wy = l1 * tri[0][2][1] + l2 * tri[1][2][1] + l3 * tri[2][2][1];
						if (wy < -0.05) continue; // below ground
						const z = l1 * A[2] + l2 * Bq[2] + l3 * C[2];
						const idx = y * Wd + x;
						if (z <= zb[idx]) continue;
						zb[idx] = z;
						const s = l1 * tri[0][0] + l2 * tri[1][0] + l3 * tri[2][0];
						const t = l1 * tri[0][1] + l2 * tri[1][1] + l3 * tri[2][1];
						const [rr, gg, bb, g] = texAt(r, s, t);
						const k = g ? 1 : light;
						img[idx * 3] = rr * k; img[idx * 3 + 1] = gg * k; img[idx * 3 + 2] = bb * k;
						if (g) { gl[idx * 3] = g[0]; gl[idx * 3 + 1] = g[1]; gl[idx * 3 + 2] = g[2]; } else { gl[idx * 3] = gl[idx * 3 + 1] = gl[idx * 3 + 2] = 0; }
					}
				}
			}
		}
		// bloom: two box-blur passes of the glow buffer
		const blur = (src, rad) => {
			const tmp = new Float32Array(src.length), out = new Float32Array(src.length);
			for (let y = 0; y < Hd; y++) for (let ch = 0; ch < 3; ch++) { let acc = 0; for (let x = -rad; x < Wd + rad; x++) { const a = x + rad, b = x - rad - 1; if (a < Wd && a >= 0) acc += src[(y * Wd + a) * 3 + ch]; if (b >= 0 && b < Wd) acc -= src[(y * Wd + b) * 3 + ch]; if (x >= 0 && x < Wd) tmp[(y * Wd + x) * 3 + ch] = acc / (2 * rad + 1); } }
			for (let x = 0; x < Wd; x++) for (let ch = 0; ch < 3; ch++) { let acc = 0; for (let y = -rad; y < Hd + rad; y++) { const a = y + rad, b = y - rad - 1; if (a < Hd && a >= 0) acc += tmp[(a * Wd + x) * 3 + ch]; if (b >= 0 && b < Hd) acc -= tmp[(b * Wd + x) * 3 + ch]; if (y >= 0 && y < Hd) out[(y * Wd + x) * 3 + ch] = acc / (2 * rad + 1); } }
			return out;
		};
		const b1 = blur(blur(gl, 3), 3), b2 = blur(blur(gl, 9), 9);
		const out = Buffer.alloc(Wd * Hd * 4);
		for (let i = 0; i < Wd * Hd; i++) for (let ch = 0; ch < 3; ch++) { out[i * 4 + ch] = clamp(Math.round(img[i * 3 + ch] + 0.7 * b1[i * 3 + ch] + 0.45 * b2[i * 3 + ch])); out[i * 4 + 3] = 255; }
		return out;
	}
	const views = [[0, 6], [38, 20], [90, 6]];
	const { lo, hi } = m.bounds;
	const ext = Math.max(hi[0], -lo[0], hi[2], -lo[2]);
	const Wd = 420, Hd = 560;
	const fitAll = { scale: Math.min(Hd * 0.9 / (hi[1] + 6), Wd * 0.94 / (2 * ext)), cx: 0, cy: hi[1] / 2 };
	const rows = [];
	rows.push(views.map(([y, p]) => render({}, y, p, Wd, Hd, fitAll)));
	const clips = Object.values(m.anims);
	const extra = m.previewExtra || [];
	const r2 = [render(samplePose(clips[0], clips[0].len * 0.3), 20, 12, Wd, Hd, fitAll)];
	for (const e of extra) r2.push(render(e.pose ? samplePose(m.anims[e.pose[0]], e.pose[1]) : {}, e.yaw, e.pitch, Wd, Hd, { scale: e.scale, cx: e.cx || 0, cy: e.cy }));
	while (r2.length < 3) r2.push(render(samplePose(clips[clips.length - 1], clips[clips.length - 1].len * 0.55), 200, 25, Wd, Hd, fitAll));
	rows.push(r2);
	const SW = Wd * 3, SH = Hd * rows.length, sheet = Buffer.alloc(SW * SH * 4);
	rows.forEach((row, ri) => row.forEach((img, ci) => { for (let y = 0; y < Hd; y++) img.copy(sheet, ((ri * Hd + y) * SW + ci * Wd) * 4, y * Wd * 4, (y + 1) * Wd * 4); }));
	const file = path.join(PREVIEW, `models2_${m.name}.png`);
	fs.writeFileSync(file, encodePng(SW, SH, sheet));
	// texture sheet too (2x) for inspection
	const k = 2, tw = m.TW * k, th = m.TH * k, ts = Buffer.alloc(tw * th * 4);
	for (let y = 0; y < th; y++) for (let x = 0; x < tw; x++) { const si = (Math.floor(y / k) * m.TW + Math.floor(x / k)) * 4, di = (y * tw + x) * 4; const a = m.tex[si + 3] / 255; for (let q = 0; q < 3; q++) ts[di + q] = Math.round(m.tex[si + q] * a + 90 * (1 - a)); ts[di + 3] = 255; }
	fs.writeFileSync(path.join(PREVIEW, `models2_${m.name}_texture.png`), encodePng(tw, th, ts));
	console.log('  preview -> ' + file);
}

// ================================================================================================ MODELS
module.exports = { Model, Frame, F0, at, orient, radial, rng, mkClip, spin, LIN, emit };
const MODELS = {};

// ------------------------------------------------------------------------------------------------ ultron_nexus
MODELS.ultron_nexus = () => {
	const m = new Model('ultron_nexus');
	const R = rng(1601);
	const J = a => (R() * 2 - 1) * a;
	m.bone('root', null, [0, 0, 0]);
	m.bone('base', 'root', [0, 0, 0]);
	m.bone('roots', 'base', [0, 0, 0]);
	m.bone('spire_lower', 'base', [0, 12, 0]);
	m.bone('spire_mid', 'spire_lower', [0, 52, 0]);
	m.bone('spire_upper', 'spire_mid', [0, 90, 0]);
	m.bone('eye', 'spire_upper', [0, 118, 0]);
	m.bone('crown', 'spire_upper', [0, 128, 0]);
	m.bone('ring_1', 'root', [0, 68, 0]);
	m.bone('ring_2', 'root', [0, 88, 0]);
	m.bone('ring_3', 'root', [0, 104, 0]);

	// ---- base: stepped octagonal mound of black metal, buttress claws, rocks
	m.oct('base', at(0, -2, 0), 34, 6, 'black', { trim: 'b' });
	m.oct('base', at(0, 4, 0).ry(22.5), 28, 4, 'plate', { trim: 't' });
	m.oct('base', at(0, 8, 0), 22, 5, 'black2', { vein: true });
	for (let k = 0; k < 8; k++) { // armoured skirt slabs around the mound, slightly tilted
		const th = k * 45 + 22.5;
		const f = radial(th, 13, -1).rx(-8 + J(3)).rz(J(4));
		m.box('base', f, [-6, 0, 0], [6, 8, 3], k % 2 ? 'plate' : 'plate2', { trim: 't' });
		m.box('base', f.t(0, 0, 3), [-1, 1, 0], [1, 7, 1], 'steel2', { noSeams: true });
	}
	for (let k = 0; k < 8; k++) { // buttress claws: two-segment angled blades leaning in to the spire
		const th = k * 45;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)];
		const P = (r, y) => [dir[0] * r, y, dir[2] * r];
		const up = dir; // thickness axis faces outward
		const big = k % 2 === 0;
		const r0 = big ? 27 : 22, y1 = big ? 22 : 16, y2 = big ? 40 : 30;
		m.tube('base', [P(r0, -3), P(r0 - 6, y1 * 0.55), P(13, y1), P(10, y2)], big ? 6 : 4, big ? 4 : 3, 'black', { trim: 'b', stripV: ['east', 'west'] }, up, 0.6);
		// outer armour plate + tooth on each claw
		const mid = P(r0 - 3, y1 * 0.3);
		const f = orient(P(r0 - 1.5, -1), vsub(P(r0 - 6, y1 * 0.55), P(r0, -3)), dir).t(0, 2.2, 0);
		m.box('base', f, [-(big ? 4 : 3), 0, 0], [big ? 4 : 3, 1.5, big ? 13 : 10], 'plate2', {});
		m.box('base', orient(P(r0 + 0.5, 0), [dir[0], -0.6, dir[2]], [0, 1, 0]), [-1, -1, 0], [1, 1, 4], 'steel', { noSeams: true });
	}
	for (let k = 0; k < 18; k++) { // broken rock chunks
		const th = k * 20 + J(9), r = 20 + R() * 24, s = 3 + R() * 6;
		const f = radial(th, r, -s * 0.45).rx(J(25)).ry(J(40)).rz(J(25));
		m.box('base', f, [-s / 2, 0, -s * 0.4], [s / 2, s * (0.6 + R() * 0.5), s * 0.4], 'rock', { redCracks: k % 3 === 0, unique: false, d: 2 });
	}
	for (let k = 0; k < 7; k++) { // shattered black metal shards stuck in the ground
		const th = k * 51 + 20, r = 30 + R() * 12;
		const f = radial(th, r, -3).rx(-20 + J(15)).rz(J(30));
		m.box('base', f, [-2, 0, -0.6], [2, 7 + R() * 5, 0.6], 'plate', { trim: 'b' });
	}

	// ---- roots: thick segmented cable bundles diving into the ground
	for (let k = 0; k < 7; k++) {
		const th = k * (360 / 7) + 12;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)], tan = [dir[2], 0, -dir[0]];
		const wob = J(1);
		const P = (r, y, side = 0) => vadd(vadd(vscl(dir, r), [0, y, 0]), vscl(tan, side + Math.sin(r * 0.12 + k) * 2.5 * wob));
		const path = [[9, 12], [17, 15], [26, 13], [34, 7], [41, -1], [46, -9]];
		const pts = path.map(([r, y]) => P(r, y));
		m.tube('roots', pts, 5, 5, 'cable', { redBand: true }, [0, 1, 0], 0.75);
		const pts2 = path.map(([r, y], i) => P(r + 0.5, y - 2.2 + (i === 0 ? 1 : 0), 3.2));
		m.tube('roots', pts2, 2.2, 2.2, 'cable', { redLine: true }, [0, 1, 0]);
		const pts3 = path.slice(0, 5).map(([r, y], i) => P(r - 0.5, y - 1.5, -3));
		m.tube('roots', pts3, 2, 2, 'cable', {}, [0, 1, 0]);
		// clamp collars at the joints
		for (let i = 1; i < 5; i++) {
			const a = pts[i], d = vsub(pts[i + 1], pts[i - 1]);
			const sz = 7.4 - i * 0.6;
			m.box('roots', orient(a, d), [-sz / 2, -sz / 2, -1], [sz / 2, sz / 2, 1], 'steel', { trim: 'tb', noSeams: true });
		}
	}

	// ---- spire: star-section core with vein channels, sheathed by tiers of overlapping armour scales
	const coreSeg = (bone, y0, y1, w) => { m.cbox(bone, at(0, y0, 0), w, y1 - y0, w, 'vein'); m.cbox(bone, at(0, y0, 0).ry(45), w, y1 - y0, w, 'vein'); };
	coreSeg('spire_lower', 10, 54, 17);
	coreSeg('spire_mid', 52, 92, 13);
	coreSeg('spire_upper', 90, 108, 10);
	const tier = (bone, y, r, n, w, h, off, mat, lean, deco = true, trim = true) => {
		for (let k = 0; k < n; k++) {
			const th = off + k * 360 / n;
			const f = radial(th, r, y).rx(lean + J(2)).rz(J(3.5));
			m.box(bone, f, [-w / 2, 0, 0], [w / 2, h, 2.4], (k + Math.round(off)) % 2 ? mat : (mat === 'black' ? 'black2' : 'plate'), trim ? { trim: 'b' } : {});
			if (deco) {
				m.box(bone, f.t(0, 2, 2.4), [-1, 0, 0], [1, h - 4, 1], 'steel', { noSeams: true });          // raised spine rib
				m.box(bone, f.t(0, 0, 1.2).rz(45), [-1.8, -1.8, -1], [1.8, 1.8, 1], 'black2', { noSeams: true }); // pointed tooth on the lower edge
			}
		}
	};
	// lower spire: 3 tiers
	// flared skirt: big scales leaning hard inward from a wide footing, then tapering tiers
	for (let k = 0; k < 8; k++) {
		const th = k * 45 + 22.5;
		const f = radial(th, 14.5, 6).rx(-17 + J(2)).rz(J(3));
		m.box('spire_lower', f, [-6, 0, 0], [6, 22, 2.6], k % 2 ? 'black' : 'plate2', { trim: 'b' });
		m.box('spire_lower', f.t(0, 3, 2.6), [-1.2, 0, 0], [1.2, 15, 1.2], 'steel', { noSeams: true, stripV: ['south'] });
		m.box('spire_lower', f.t(-4.5, 2, 2.6), [-0.6, 0, 0], [0.6, 12, 0.6], 'steel2', { noSeams: true });
		m.box('spire_lower', f.t(0, 0.5, 1.3).rz(45), [-2.4, -2.4, -1.1], [2.4, 2.4, 1.1], 'black2', { noSeams: true });
	}
	tier('spire_lower', 24, 9.4, 6, 9.5, 17, 0, 'black', -6, true, false);
	tier('spire_lower', 38, 8.4, 6, 8.5, 16, 30, 'plate2', -6);
	// mid spire
	tier('spire_mid', 52, 7.2, 6, 7.5, 15, 30, 'plate2', -5);
	tier('spire_mid', 64, 6.7, 6, 7, 15, 0, 'black', -5, true, false);
	tier('spire_mid', 76, 6.2, 6, 6.5, 15, 30, 'plate2', -6);
	// upper spire below the eye cage
	tier('spire_upper', 89, 5.6, 6, 6, 13, 0, 'black', -6, false);
	// tall jagged fins (3) up the lower+mid spire
	for (let k = 0; k < 3; k++) {
		const th = k * 120 + 30;
		for (const [bone, y0, y1, r0, dep] of [['spire_lower', 14, 52, 9, 7], ['spire_mid', 52, 88, 7, 5.5]]) {
			const n = Math.round((y1 - y0) / 6);
			for (let i = 0; i < n; i++) {
				const y = y0 + i * (y1 - y0) / n, d = dep * (1 - 0.35 * (i % 2)) * (1 - 0.25 * i / n);
				const f = radial(th, r0 - 1 - i * 0.12, y).rx(-3);
				m.box(bone, f, [-1, 0, 0], [1, (y1 - y0) / n + 0.6, d], 'plate2', { stripV: ['south'], noRivets: true });
			}
		}
	}
	// glowing conduit seams between fins (vertical red light strips)
	for (let k = 0; k < 3; k++) {
		const th = k * 120 + 90;
		m.box('spire_lower', radial(th, 9.6, 12).rx(-5), [-0.75, 0, 0], [0.75, 40, 1.2], 'red');
		m.box('spire_mid', radial(th, 7.4, 54).rx(-4), [-0.6, 0, 0], [0.6, 34, 1], 'red');
	}

	// ---- eye socket cage (spire_upper)
	m.oct('spire_upper', at(0, 104, 0), 15, 4, 'steel', { trim: 'b', dots: true });
	m.oct('spire_upper', at(0, 108, 0).ry(22.5), 11, 2, 'black', {});
	for (let k = 0; k < 4; k++) { // four curved armour ribs bending round the eye
		const th = 45 + k * 90;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)];
		const P = (r, y) => [dir[0] * r, y, dir[2] * r];
		m.tube('spire_upper', [P(6, 106), P(10.5, 111), P(11.5, 118), P(10, 124), P(6.5, 128.5)], 3.6, 3, 'black', { trim: 'b', stripV: ['north'] }, dir);
		// outer plate on each rib + talon
		m.box('spire_upper', orient(P(11.4, 114), [0, 1, 0], dir).t(0, 0, 0), [-2.5, 0, -1], [2.5, 1.4, 8], 'plate2', { noSeams: true });
		m.box('spire_upper', orient(P(12.6, 117), vsub(P(16, 121), P(12.6, 117)), dir), [-1, -1, 0], [1, 1, 4], 'steel', { noSeams: true });
	}
	for (let k = 0; k < 4; k++) { // smaller brow plates between the ribs
		const th = k * 90;
		m.box('spire_upper', radial(th, 7.5, 124).rx(30), [-3, 0, 0], [3, 4, 1.4], 'plate', { trim: 'b' });
		m.box('spire_upper', radial(th, 7, 109).rx(-30), [-3, 0, 0], [3, 4, 1.4], 'plate', { trim: 't' });
	}
	// ---- eye: a vertex-standing diamond with a vertical shard core and orbiting flecks
	const ef = at(0, 118, 0).ry(45).rx(35.264).rz(45);
	m.box('eye', ef, [-3.6, -3.6, -3.6], [3.6, 3.6, 3.6], 'eye');
	m.box('eye', at(0, 118, 0).ry(45), [-1.6, -8, -1.6], [1.6, 8, 1.6], 'eye');
	m.box('eye', at(0, 118, 0).rx(45), [-5.2, -1.2, -1.2], [5.2, 1.2, 1.2], 'reddim');
	m.box('eye', at(0, 118, 0).rz(45), [-1.2, -1.2, -5.2], [1.2, 1.2, 5.2], 'reddim');
	for (let k = 0; k < 6; k++) m.box('eye', radial(k * 60, 7, 118 + (k % 2 ? 2.5 : -2.5)).rx(45).rz(45), [-0.8, -0.8, -0.8], [0.8, 0.8, 0.8], 'red');

	// ---- crown: collar and jagged spikes
	m.oct('crown', at(0, 127, 0), 12, 3, 'black', { trim: 't' });
	m.oct('crown', at(0, 130, 0).ry(22.5), 8, 2, 'plate2', {});
	for (let k = 0; k < 8; k++) {
		const th = k * 45 + 22.5, big = k % 2 === 0;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)];
		const P = (r, y) => [dir[0] * r, y, dir[2] * r];
		const pts = big ? [P(4.5, 129), P(7.5, 136), P(9, 141), P(12.5, 147)] : [P(4.5, 129), P(7, 134), P(9.5, 138)];
		m.tube('crown', pts, big ? 3.6 : 2.8, big ? 3.6 : 2.8, 'black', { trim: 'b' }, dir, 0.35);
		if (big) m.box('crown', orient(P(7.2, 135), vsub(P(9, 141), P(7.5, 136)), dir).t(0, 1.6, 0), [-0.6, -0.5, 0], [0.6, 0.5, 6], 'red');
	}
	m.cbox('crown', at(0, 130, 0).ry(45), 3.4, 12, 3.4, 'black2', { stripV: true });
	m.cbox('crown', at(0, 130, 0), 2.6, 16, 2.6, 'plate2', {});
	m.cbox('crown', at(0, 146, 0).ry(45), 1.6, 3.6, 1.6, 'red');

	// ---- floating shard rings
	const ringOf = (bone, y, r, n, len, hgt, seedOff) => {
		for (let k = 0; k < n; k++) {
			const th = k * 360 / n + seedOff;
			const yy = y + (k % 2 ? 1.5 : -1.5) + J(0.8);
			const f = radial(th, r, yy).rz(J(14)).rx(J(10)).ry(J(10));
			m.box(bone, f, [-len / 2, -hgt / 2, -1], [len / 2, hgt / 2, 1.2], k % 3 ? 'black' : 'plate2', { trim: 'b', noRivets: true });
			m.box(bone, f.t(-len / 2 + 0.6, 0, 0).rz(45), [-1.4, -1.4, -0.8], [1.4, 1.4, 0.8], 'black2', { noSeams: true });   // pointed end
			m.box(bone, f.t(0, 0, 1.2), [-len * 0.3, -0.5, 0], [len * 0.3, 0.5, 0.5], 'red');                              // glowing core line
			if (k % 2 === 0) m.box(bone, f.t(len * 0.15, hgt / 2, -0.5).rz(-25), [0, 0, 0], [len * 0.35, 1.5, 1.6], 'plate', { noSeams: true });
		}
	};
	ringOf('ring_1', 68, 25, 12, 8, 4.5, 0);
	ringOf('ring_2', 88, 21, 10, 8, 4, 18);
	ringOf('ring_3', 104, 17.5, 9, 7, 3.5, 7);

	// ---- animations
	const jit = (ticks, every, amp, seed) => { const r = rng(seed), o = { 0: [0, 0, 0] }; for (let t = every; t < ticks; t += every) o[t] = LIN([(r() * 2 - 1) * amp, (r() * 2 - 1) * amp * 0.5, (r() * 2 - 1) * amp]); o[ticks] = LIN([0, 0, 0]); return o; };
	const anims = {
		'animation.ultron_nexus.idle': mkClip(80, true, {
			ring_1: { rotation: spin(80, 1) },
			ring_2: { rotation: spin(80, -1) },
			ring_3: { rotation: spin(80, 2) },
			eye: { scale: { 0: [1, 1, 1], 10: [1.2, 1.2, 1.2], 20: [1, 1, 1], 50: [1.2, 1.2, 1.2], 60: [1, 1, 1], 80: [1, 1, 1] } },
			spire_upper: { rotation: { 0: [0, 0, 0], 20: [0.6, 0, 0.9], 40: [0, 0, 0], 60: [-0.6, 0, -0.9], 80: [0, 0, 0] } },
			crown: { rotation: { 0: [0, 0, 0], 20: [0.8, 0, 1.2], 40: [0, 0, 0], 60: [-0.8, 0, -1.2], 80: [0, 0, 0] } },
			roots: { scale: { 0: [1, 1, 1], 20: [1.012, 1.03, 1.012], 40: [1, 1, 1], 60: [1.012, 1.03, 1.012], 80: [1, 1, 1] } },
		}),
		'animation.ultron_nexus.grow': mkClip(40, true, {
			spire_lower: { rotation: jit(40, 4, 1.0, 11) },
			spire_mid: { rotation: jit(40, 3, 1.4, 12) },
			spire_upper: { rotation: jit(40, 2, 1.8, 13) },
			crown: { rotation: jit(40, 2, 3, 14), scale: { 0: [1, 1, 1], 6: LIN([1.05, 0.96, 1.05]), 12: LIN([1, 1, 1]), 26: LIN([1.04, 0.97, 1.04]), 32: LIN([1, 1, 1]), 40: [1, 1, 1] } },
			eye: { scale: { 0: [1, 1, 1], 3: LIN([0.7, 0.7, 0.7]), 6: LIN([1.25, 1.25, 1.25]), 9: LIN([0.85, 0.85, 0.85]), 13: LIN([1.15, 1.15, 1.15]), 17: LIN([0.6, 0.6, 0.6]), 20: LIN([1.2, 1.2, 1.2]), 25: LIN([0.9, 0.9, 0.9]), 29: LIN([1.3, 1.3, 1.3]), 33: LIN([0.75, 0.75, 0.75]), 37: LIN([1.1, 1.1, 1.1]), 40: LIN([1, 1, 1]) } },
			ring_1: { rotation: spin(40, 2) },
			ring_2: { rotation: spin(40, -2) },
			ring_3: { rotation: spin(40, 3) },
			roots: { scale: { 0: [1, 1, 1], 5: LIN([1.03, 1.06, 1.03]), 10: LIN([1, 1, 1]), 25: LIN([1.03, 1.06, 1.03]), 30: LIN([1, 1, 1]), 40: [1, 1, 1] } },
		}),
	};
	m.previewExtra = [{ yaw: 25, pitch: 10, scale: 9, cy: 118 }, { yaw: 0, pitch: 45, scale: 4.2, cy: 30, pose: ['animation.ultron_nexus.grow', 7] }];
	emit(m, anims);
};

// ------------------------------------------------------------------------------------------------ vibranium_core
MODELS.vibranium_core = () => {
	const m = new Model('vibranium_core');
	m.bone('root', null, [0, 0, 0]);
	m.bone('platform', 'root', [0, 0, 0]);
	m.bone('cage', 'root', [0, 0, 0]);
	m.bone('core', 'root', [0, 50, 0]);
	m.bone('ring_1', 'root', [0, 46, 0]);
	m.bone('ring_2', 'root', [0, 56, 0]);

	// ---- tiered octagonal dais
	m.oct('platform', at(0, 0, 0).ry(22.5), 62, 3, 'black', { trim: 't' });
	m.oct('platform', at(0, 3, 0).ry(22.5), 54, 4, 'plate2', { trim: 'b' });
	m.oct('platform', at(0, 7, 0).ry(22.5), 44, 3, 'black2', { trim: 't' });
	m.oct('platform', at(0, 10, 0).ry(22.5), 34, 3, 'plate', { dots: true });
	m.oct('platform', at(0, 13, 0).ry(22.5), 24, 2, 'steel', { trim: 't' });
	m.oct('platform', at(0, 15, 0).ry(22.5), 16, 0.8, 'red', {});
	m.oct('platform', at(0, 15.2, 0).ry(22.5), 10, 1, 'crystal', { tone: 0.8 });
	for (let k = 0; k < 4; k++) { // stair runs on the four cardinal sides
		const th = k * 90;
		[[30, 3], [26, 7], [21.5, 10], [16.5, 13]].forEach(([r, h], i) => {
			m.box('platform', radial(th, r - 3.5, 0), [-7 + i * 0.5, 0, 0], [7 - i * 0.5, h, 3.5], i % 2 ? 'plate2' : 'steel', { trim: 't', noRivets: true });
		});
		m.box('platform', radial(th + 45, 25.5, 3), [-5, 0, 0], [5, 3.4, 1.2], 'grille', {});
		m.box('platform', radial(th + 22.5, 20.6, 7), [-3, 0.5, 0], [3, 2.5, 1], 'grille', {});
		m.box('platform', radial(th - 22.5, 20.6, 7), [-3, 0.5, 0], [3, 2.5, 1], 'grille', {});
	}

	// ---- cage: four curved claw pylons
	for (let k = 0; k < 4; k++) {
		const th = 45 + k * 90;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)], tan = [dir[2], 0, -dir[0]];
		const P = (r, y, sd = 0) => vadd(vadd(vscl(dir, r), [0, y, 0]), vscl(tan, sd));
		// foot housing + hydraulic piston
		m.box('cage', radial(th, 24, 3), [-6, 0, -6], [6, 9, 6], 'black', { trim: 'b', dots: true });
		m.box('cage', radial(th, 24, 12), [-4.5, 0, -4.5], [4.5, 3, 4.5], 'plate2', { trim: 't' });
		m.box('cage', radial(th, 18, 3).rx(-50), [-1.5, 0, -1.5], [1.5, 12, 1.5], 'steel2', { noSeams: true });
		const curve = [[24, 12], [26, 30], [25.5, 48], [22, 64], [16, 78], [9.5, 88], [4.5, 92], [2.5, 87]];
		const pts = curve.map(([r, y]) => P(r, y));
		m.tube('cage', pts, 6, 5, 'black', { stripL: ['east', 'west'] }, dir, 0.45);
		// outer armour plates per segment, side fins, joint collars
		for (let i = 0; i < pts.length - 2; i++) {
			const a = pts[i], b = pts[i + 1], d = vsub(b, a), len = vlen(d);
			const sc = 1 - 0.55 * i / (pts.length - 2);
			const f = orient(a, d, dir);
			m.box('cage', f.t(0, 2.5 * sc, 0), [-4.2 * sc, 0, 0.6], [4.2 * sc, 1.6, len - 0.6], i % 2 ? 'plate2' : 'steel', { stripL: ['up'] });
			if (i < 5) m.box('cage', f.t(0, 4.1 * sc, 1.2), [-2.4 * sc, 0, 0], [2.4 * sc, 1, Math.min(4, len * 0.3)], 'silver', { noSeams: true });
			if (i > 0) m.box('cage', f, [-4.2 * sc, -3.4 * sc, -1], [4.2 * sc, 3.4 * sc, 1], 'steel', { trim: 'tb', noSeams: true });
			if (i % 2 === 0 && i < 5) for (const sd of [-1, 1]) m.box('cage', f.t(sd * 3.2 * sc, -1.5 * sc, len * 0.5).ry(sd * 70), [-0.5, -1, 0], [0.5, 1, 4.5 * sc], 'black2', { noSeams: true });
		}
		// inner conduit running up the claw and conduits from the foot into the dais
		const inner = curve.slice(0, 5).map(([r, y]) => P(r - 4, y + 2, 0));
		m.tube('cage', inner, 2, 2, 'cable', { redBand: true }, dir);
		for (const sd of [-2.5, 2.5]) m.tube('cage', [P(19, 8, sd), P(15.5, 11, sd), P(12, 13.6, sd), P(8, 14.6, sd)], 2, 2, 'cable', { redLine: true });
		// talon tip light
		m.box('cage', orient(P(2.5, 87), [0, -1, 0], dir), [-0.8, -0.8, 0], [0.8, 0.8, 2.5], 'red');
	}

	// ---- core: vibranium crystal (4-fold symmetric so the slow 90-degree turn loops cleanly)
	const cf = at(0, 50, 0);
	m.cbox('core', cf.ry(45), 11, 22, 11, 'crystal', { tone: 0.7 }, -11);
	m.cbox('core', cf, 8, 26, 8, 'crystal', { tone: 0.3 }, -13);
	[[9, 2.6], [7, 2.6], [5, 2.6], [3, 2.4], [1.4, 2]].forEach(([w, h], i) => {
		const y = 11 + i * 2.4;
		m.cbox('core', cf.ry(45 * (i % 2)), w, h, w, 'crystal', { tone: 0.9 - i * 0.1 }, y);
		m.cbox('core', cf.ry(45 * (i % 2)), w, h, w, 'crystal', { tone: 0.5 - i * 0.05 }, -y - h);
	});
	for (let k = 0; k < 4; k++) { // leaning side shards
		m.box('core', radial(k * 90, 4.5, 42).rx(14), [-1.6, 0, -1.6], [1.6, 13, 1.6], 'crystal', { tone: 0.95 });
		m.box('core', radial(k * 90 + 45, 5, 52).rx(160), [-1.2, 0, -1.2], [1.2, 9, 1.2], 'crystal', { tone: 0.6 });
	}

	// ---- rings: segmented, tilted gyroscope-style
	const ringOf = (bone, y, r, tiltX, tiltZ, n, h, mat) => {
		const rf = at(0, y, 0).rx(tiltX).rz(tiltZ);
		const L = 2 * Math.PI * r / n * 0.78;
		for (let k = 0; k < n; k++) {
			const f = rf.ry(k * 360 / n).t(0, 0, r);
			m.box(bone, f, [-L / 2, -h / 2, -0.6], [L / 2, h / 2, 1.2], mat, { stripH: ['south'], noRivets: true });
			const g = rf.ry((k + 0.5) * 360 / n).t(0, 0, r * Math.cos(Math.PI / n));
			m.box(bone, g, [-1, -h / 2 - 0.8, -1], [1, h / 2 + 0.8, 1.6], 'black2', { noSeams: true });
			if (k % 3 === 0) m.box(bone, g.t(0, 0, 1.6), [-0.6, -0.6, 0], [0.6, 0.6, 0.8], 'red');
		}
	};
	ringOf('ring_1', 46, 15.5, 16, 0, 12, 3, 'silver');
	ringOf('ring_2', 56, 19.5, -6, 12, 14, 2.4, 'steel2');

	const anims = {
		'animation.vibranium_core.idle': mkClip(80, true, {
			ring_1: { rotation: spin(80, 1) },
			ring_2: { rotation: spin(80, -1) },
			core: {
				position: { 0: [0, 0, 0], 40: [0, 2, 0], 80: [0, 0, 0] },
				rotation: { 0: LIN([0, 0, 0]), 20: LIN([0, 22.5, 0]), 40: LIN([0, 45, 0]), 60: LIN([0, 67.5, 0]), 80: LIN([0, 90, 0]) },
			},
		}),
	};
	m.previewExtra = [{ yaw: 30, pitch: 14, scale: 8.5, cy: 52 }];
	emit(m, anims);
};

// ------------------------------------------------------------------------------------------------ lift_engine
MODELS.lift_engine = () => {
	const m = new Model('lift_engine');
	m.bone('root', null, [0, 0, 0]);
	m.bone('base', 'root', [0, 0, 0]);
	m.bone('turbine', 'base', [0, 13, 0]);
	m.bone('column', 'base', [0, 26, 0]);
	m.bone('cap', 'column', [0, 80, 0]);

	// ---- base: landing legs, shroud ring, deck, nozzle
	for (let k = 0; k < 4; k++) {
		const th = 45 + k * 90;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)];
		const P = (r, y) => [dir[0] * r, y, dir[2] * r];
		m.tube('base', [P(23, 2), P(22, 11), P(14, 26)], 5, 4, 'black', { stripL: ['up'] }, dir, 0.75);
		m.box('base', radial(th, 23, 0), [-4.5, 0, -4.5], [4.5, 2.5, 4.5], 'steel', { trim: 't' });
		m.box('base', radial(th, 26.5, 0).rx(25), [-2, 0, 0], [2, 1.5, 4], 'black2', { noSeams: true });
		m.box('base', radial(th, 17, 9).rx(-62), [-1.2, 0, -1.2], [1.2, 12, 1.2], 'steel2', { noSeams: true }); // hydraulic strut
	}
	for (let k = 0; k < 8; k++) { // shroud plates
		const th = k * 45 + 22.5;
		const f = radial(th, 14.5, 5).rx(4);
		m.box('base', f, [-6.3, 0, 0], [6.3, 15, 2.4], k % 2 ? 'plate2' : 'black', { trim: 'tb' });
		if (k % 2 === 0) m.box('base', f.t(0, 3, 2.4), [-4, 0, 0], [4, 8, 0.8], 'grille', {});
		else m.box('base', f.t(0, 3.5, 2.4), [-1, 0, 0], [1, 8, 0.8], 'steel', { stripV: ['south'], noSeams: true });
	}
	m.oct('base', at(0, 19.5, 0).ry(22.5), 32, 4, 'black', { trim: 't', dots: true });
	m.oct('base', at(0, 19, 0).ry(22.5), 26, 0.5, 'reddim', {});              // glowing turbine bay ceiling
	for (let k = 0; k < 8; k++) m.box('base', radial(k * 45 + 22.5, 13, 3.5), [-5.6, 0, 0], [5.6, 1.6, 4.4], 'steel', { trim: 'b', noSeams: true }); // lower lip ring
	for (let k = 0; k < 4; k++) m.box('base', radial(k * 90 + 45, 4, 3.5), [-1, 0, 0], [1, 1.2, 9.5], 'black2', { noSeams: true });                  // nozzle spider struts
	m.oct('base', at(0, 4, 0).ry(22.5), 9, 4, 'nozzle', {});                   // thruster nozzle (static)
	m.oct('base', at(0, 0.5, 0).ry(22.5), 12, 3.5, 'nozzle', {});
	m.oct('base', at(0, 0, 0).ry(22.5), 8, 0.5, 'red', {});

	// ---- turbine: hub, 4 big pitched blades, 8 inner vanes
	m.oct('turbine', at(0, 10, 0), 6, 7, 'steel', { trim: 'tb' });
	m.cbox('turbine', at(0, 8, 0).ry(45), 3, 2, 3, 'red', {});
	for (let k = 0; k < 4; k++) {
		const f = radial(k * 90, 2.5, 13.5).rz(28);
		m.box('turbine', f, [-2.4, -0.45, 0], [2.4, 0.45, 10.8], 'silver', { noSeams: true });
		m.box('turbine', f.t(0, 0, 10.8), [-2.4, -0.6, 0], [2.4, 0.6, 0.8], 'red', {});
		m.box('turbine', f.t(0, 0.45, 1), [-0.6, 0, 0], [0.6, 0.5, 8], 'black2', { noSeams: true });
	}
	for (let k = 0; k < 8; k++) m.box('turbine', radial(k * 45 + 22.5, 2.5, 11.5).rz(-35), [-1.2, -0.3, 0], [1.2, 0.3, 5], 'steel2', { noSeams: true });

	// ---- column: armoured housing
	m.oct('column', at(0, 23, 0).ry(22.5), 15, 57, 'black2', { vein: true });
	m.oct('column', at(0, 48, 0).ry(22.5), 19, 3, 'steel', { trim: 'tb' });
	m.oct('column', at(0, 76, 0).ry(22.5), 21, 3, 'black', { trim: 't' });
	for (let k = 0; k < 4; k++) {
		const th = k * 90;
		const f1 = radial(th, 8.4, 24).rx(-5);
		m.box('column', f1, [-6.2, 0, 0], [6.2, 23, 2.4], 'plate2', { trim: 'b' });
		m.box('column', f1.t(0, 4, 2.4), [-4.8, 0, 0], [4.8, 11, 0.8], 'steel', { noSeams: true });
		m.box('column', f1.t(0, 5, 3.2), [-4, 0, 0], [4, 9, 0.8], 'grille', {});
		m.box('column', f1.t(0, 17, 2.4), [-5, 0, 0], [5, 2, 0.6], 'black2', { dots: true, noSeams: true });
		const f2 = radial(th, 7.6, 52).rx(-4);
		m.box('column', f2, [-5.6, 0, 0], [5.6, 23, 2.4], 'black', { trim: 'b' });
		m.box('column', f2.t(0, 3, 2.4), [-1.4, 0, 0], [1.4, 17, 0.9], 'steel', { stripV: ['south'], noSeams: true });
		m.box('column', f2.t(-5.6, 0, 0).ry(-30), [-0.6, 0, 0], [0.6, 20, 3], 'plate', { noSeams: true });
		m.box('column', f2.t(5.6, 0, 0).ry(30), [-0.6, 0, 0], [0.6, 20, 3], 'plate', { noSeams: true });
	}
	for (let k = 0; k < 4; k++) { // corner fins with red light edges
		const th = 45 + k * 90;
		m.box('column', radial(th, 7.5, 27).rx(-3), [-1, 0, 0], [1, 48, 4.2], 'plate', { stripV: ['south'], noRivets: true });
		m.box('column', radial(th, 7.8, 25).rx(-3), [-1.5, 0, 0], [1.5, 4, 5.5], 'black2', { noSeams: true });
	}
	for (const th of [25, 205]) { // power cables
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)];
		const P = (r, y) => [dir[0] * r, y, dir[2] * r];
		const pts = [P(10.5, 76), P(11.2, 60), P(11.6, 44), P(12.2, 30), P(15, 22)];
		m.tube('column', pts, 1.8, 1.8, 'cable', { redBand: true }, dir);
		for (let i = 1; i < 4; i++) m.box('column', orient(pts[i], vsub(pts[i + 1], pts[i - 1]), dir), [-1.6, -1.6, -0.7], [1.6, 1.6, 0.7], 'steel', { noSeams: true });
	}

	// ---- cap: hood, beacon cage, antenna fins
	m.oct('cap', at(0, 79, 0).ry(22.5), 18, 3, 'plate2', { trim: 'b' });
	m.oct('cap', at(0, 82, 0).ry(22.5), 12, 3, 'black', { dots: true });
	for (const [x, z] of [[-3.2, -3.2], [3.2, -3.2], [-3.2, 3.2], [3.2, 3.2]]) m.box('cap', at(x, 85, z), [-0.6, 0, -0.6], [0.6, 6, 0.6], 'steel', { noSeams: true });
	m.cbox('cap', at(0, 85.3, 0).ry(45), 4.6, 5.2, 4.6, 'red', {});
	m.cbox('cap', at(0, 91, 0), 8, 1.2, 8, 'black', { trim: 'b' });
	m.cbox('cap', at(0, 92.2, 0).ry(45), 3, 1.5, 3, 'plate2', {});
	m.cbox('cap', at(0, 93.7, 0), 0.8, 2.3, 0.8, 'red', {});
	for (let k = 0; k < 4; k++) {
		const th = 45 + k * 90;
		m.box('cap', radial(th, 5.6, 81).rx(16), [-0.5, 0, 0], [0.5, 13, 3.2], 'plate', { stripV: ['south'], noRivets: true });
		m.box('cap', radial(th, 8.8, 92.5).rx(16), [-0.4, 0, 0], [0.4, 1.4, 1.4], 'red', {});
	}

	const anims = {
		'animation.lift_engine.idle': mkClip(20, true, {
			turbine: { rotation: spin(20, 1, 1, 4) },
			cap: { scale: { 0: [1, 1, 1], 5: [1.05, 1.05, 1.05], 10: [1, 1, 1], 15: [1.05, 1.05, 1.05], 20: [1, 1, 1] } },
		}),
	};
	m.previewExtra = [{ yaw: 30, pitch: -70, scale: 9, cy: 10 }];
	emit(m, anims);
};

// ------------------------------------------------------------------------------------------------ uplink_antenna
MODELS.uplink_antenna = () => {
	const m = new Model('uplink_antenna');
	m.bone('root', null, [0, 0, 0]);
	m.bone('base', 'root', [0, 0, 0]);
	m.bone('mast', 'base', [0, 8, 0]);
	const DISH = [[1, 36, 180, 6.5], [2, 62, 60, 5.5], [3, 86, 300, 4.5]];
	for (const [n, y] of DISH) m.bone(`dish_${n}`, 'mast', [0, y, 0]);
	m.bone('tip', 'mast', [0, 100, 0]);

	// ---- base plinth
	m.oct('base', at(0, 0, 0).ry(22.5), 26, 4, 'black', { trim: 't' });
	m.oct('base', at(0, 4, 0).ry(22.5), 20, 4, 'plate2', { dots: true });
	for (let k = 0; k < 4; k++) {
		const th = 45 + k * 90;
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)];
		const P = (r, y) => [dir[0] * r, y, dir[2] * r];
		m.tube('base', [P(17, 0.5), P(14, 5), P(9, 14)], 4, 3, 'black', { stripL: ['up'] }, dir, 0.7);
		m.box('base', radial(th, 17, 0), [-3, 0, -3], [3, 2, 3], 'steel', {});
	}
	for (const th of [0, 180]) {
		const f = radial(th, 10, 0);
		m.box('base', f, [-3.5, 0, 0], [3.5, 6, 4], 'steel', { trim: 't' });
		m.box('base', f.t(0, 1, 4), [-2.5, 0, 0], [2.5, 4, 0.6], 'grille', {});
	}

	// ---- lattice mast
	const Y0 = 8, Y1 = 100;
	const half = y => 7 - 5.2 * (y - Y0) / (Y1 - Y0);
	const C = (sx, sz, y) => [sx * half(y), y, sz * half(y)];
	const corners = [[-1, -1], [1, -1], [1, 1], [-1, 1]];
	const levels = []; for (let y = Y0; y <= Y1 + 0.01; y += 92 / 9) levels.push(y);
	for (const [sx, sz] of corners) m.tube('mast', levels.filter((_, i) => i % 3 === 0).map(y => C(sx, sz, y)), 1.8, 1.8, 'steel2', { noSeams: true });
	levels.forEach((y, li) => {
		for (let k = 0; k < 4; k++) {
			const a = C(...corners[k], y), b = C(...corners[(k + 1) % 4], y);
			m.tube('mast', [a, b], 1, 1, 'plate', { noSeams: true });
			if (li < levels.length - 1) { // X braces in each bay
				const y2 = levels[li + 1];
				const a2 = C(...corners[k], y2), b2 = C(...corners[(k + 1) % 4], y2);
				m.tube('mast', [a, b2], 0.7, 0.7, 'black2', { noSeams: true });
				m.tube('mast', [b, a2], 0.7, 0.7, 'black2', { noSeams: true });
			}
		}
	});
	m.cbox('mast', at(0, Y0, 0).ry(45), 1.4, Y1 - Y0, 1.4, 'red', {});   // glowing power core inside the lattice
	for (const [, y] of DISH) {
		m.oct('mast', at(0, y - 3, 0).ry(22.5), half(y) * 2 + 4, 1.5, 'black', { trim: 'b' });
		m.oct('mast', at(0, y - 1.5, 0).ry(22.5), half(y) * 2 + 1, 3, 'plate2', { dots: true });
	}
	// cable bundles down two faces
	for (const [th, sd] of [[0, -1], [0, 1.4], [90, 0]]) {
		const dir = [Math.sin(th * DEG), 0, Math.cos(th * DEG)], tan = [dir[2], 0, -dir[0]];
		const P = (y, r) => vadd(vadd(vscl(dir, r == null ? half(y) + 1.3 : r), [0, y, 0]), vscl(tan, sd));
		const pts = [P(96), P(76), P(56), P(36), P(16), P(9, 11), P(6, 13)];
		m.tube('mast', pts, 1.3, 1.3, 'cable', th === 90 ? { redLine: true } : { redBand: true }, dir);
		for (let i = 1; i < 5; i++) m.box('mast', orient(pts[i], vsub(pts[i + 1], pts[i - 1]), dir), [-1.2, -1.2, -0.5], [1.2, 1.2, 0.5], 'steel', { noSeams: true });
	}

	// ---- dishes: arm, hub, 8 angled rim plates, lip, feed spike + struts
	for (const [n, y, th, Lr] of DISH) {
		const b = `dish_${n}`;
		const a = half(y);
		const arm = at(0, y, 0).ry(th);
		m.box(b, arm, [-1, -1, a - 1], [1, 1, a + 6.5], 'steel', { stripL: ['up'], noSeams: true });
		m.box(b, arm.t(0, 0, a + 1), [-1.8, -1.8, 0], [1.8, 1.8, 1.4], 'black', {});
		const d = arm.t(0, 0, a + 8).rx(-14);   // dish frame: local +Z = boresight
		m.box(b, d, [-2.2, -2.2, -2.5], [2.2, 2.2, 0.6], 'plate2', { trim: 'tb' });
		for (let j = 0; j < 8; j++) {
			const pf = d.rz(j * 45 + 22.5).t(0, 1.6, 0).rx(38);
			const wIn = 2.2, wOut = 2 * (1.6 + Lr) * Math.tan(22.5 * DEG) + 0.3;
			m.box(b, pf, [-wIn / 2 - 0.4, 0, -0.35], [wIn / 2 + 0.4, Lr * 0.55, 0.35], j % 2 ? 'silver' : 'steel2', { noSeams: true });
			m.box(b, pf.t(0, Lr * 0.5, 0), [-wOut / 2, 0, -0.35], [wOut / 2, Lr * 0.5, 0.35], j % 2 ? 'steel2' : 'silver', { noSeams: true });
			m.box(b, pf.t(0, Lr, 0), [-wOut / 2, 0, -0.5], [wOut / 2, 0.8, 0.5], 'black', { trim: 'b', noSeams: true });
		}
		m.box(b, d, [-0.45, -0.45, 0], [0.45, 0.45, Lr + 2], 'steel', { noSeams: true });
		m.box(b, d.t(0, 0, Lr + 2), [-1, -1, 0], [1, 1, 1.6], 'red', {});
		const tipP = d.p([0, 0, Lr + 2]);
		for (let j = 0; j < 3; j++) {
			const ang = (j * 120 + 90) * DEG;
			const rim = d.rz(0).p([Math.cos(ang) * (1.6 + Lr * 0.75), Math.sin(ang) * (1.6 + Lr * 0.75), Lr * 0.55]);
			m.tube(b, [rim, tipP], 0.5, 0.5, 'black2', { noSeams: true });
		}
	}

	// ---- tip: beacon housing + needle
	m.oct('tip', at(0, 99.5, 0).ry(22.5), 7, 1.5, 'black', { trim: 'b' });
	for (const [x, z] of [[-1.8, -1.8], [1.8, -1.8], [1.8, 1.8], [-1.8, 1.8]]) m.box('tip', at(x, 101, z), [-0.45, 0, -0.45], [0.45, 4.5, 0.45], 'steel', { noSeams: true });
	m.cbox('tip', at(0, 101.2, 0).ry(45), 3, 4, 3, 'red', {});
	m.cbox('tip', at(0, 105.5, 0), 5, 1, 5, 'plate2', { trim: 'b' });
	m.cbox('tip', at(0, 106.5, 0), 0.8, 4, 0.8, 'steel2', { noSeams: true });
	m.cbox('tip', at(0, 110.5, 0).ry(45), 1.4, 1.5, 1.4, 'red', {});
	for (let k = 0; k < 4; k++) m.box('tip', radial(k * 90 + 45, 2.4, 99.5).rx(25), [-0.3, 0, 0], [0.3, 5, 1.6], 'plate', { noSeams: true });

	const sw = (amp, ph) => { const o = {}; for (let t = 0; t <= 60; t += 7.5) o[t] = [0, amp * Math.sin((t / 60 + ph) * 2 * Math.PI), 0]; return o; };
	const anims = {
		'animation.uplink_antenna.idle': mkClip(60, true, {
			dish_1: { rotation: sw(28, 0) },
			dish_2: { rotation: sw(22, 0.33) },
			dish_3: { rotation: sw(32, 0.66) },
			tip: { scale: { 0: [1, 1, 1], 15: [1.25, 1.25, 1.25], 30: [1, 1, 1], 45: [1.25, 1.25, 1.25], 60: [1, 1, 1] } },
		}),
	};
	m.previewExtra = [{ yaw: 150, pitch: 10, scale: 10, cy: 60 }];
	emit(m, anims);
};

if (require.main === module) {
	for (const [n, fn] of Object.entries(MODELS)) if (!ONLY || ONLY.split(',').includes(n)) fn();
	console.log(`gen_v0161_contagion_models2: wrote ${written.length} files`);
	for (const w of written) console.log('  ' + w);
}
