// Shared pipeline for the v0.16.x concept models (Frost Giants, Ultron Omega).
// Copied and generalised from scratchpad/gen_v01420_titan_model.js:
//   - a Model holds bones + boxes in model pixels (Bedrock/GeckoLib conventions: +Y up, the model faces -Z,
//     the entity's RIGHT side is -X; rotation X > 0 tips a bone's top forward),
//   - layout() packs every cube face into a texture at D texels per model pixel, paint() fills it with a
//     procedural shader and a glowmask, writeAssets() writes geo.json + texture + glowmask,
//   - renderPanel() is a supersampled software renderer (key + ambient + rim light, ground shadow, outlines,
//     cheap screen-space AO and a bloom pass that composites the glowmask bright), and sheet() lays several
//     panels out side by side with labels in a little 5x7 bitmap font.
// Hand-rolled PNG via Node zlib (there is no Python on this machine).
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// ================================================================ PNG
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePng(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 6 })), chunk('IEND', Buffer.alloc(0))]);
}

// ================================================================ noise / colour
function hash3(x, y, z) {
	let h = (x * 374761393 + y * 668265263 + z * 2147483647) | 0;
	h = Math.imul(h ^ (h >>> 13), 1274126177);
	h = h ^ (h >>> 16);
	return (h >>> 0) / 4294967296;
}
const sm = t => t * t * (3 - 2 * t);
function vnoise(x, y, z) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
	const xf = sm(x - xi), yf = sm(y - yi), zf = sm(z - zi);
	let r = 0;
	for (let dz = 0; dz < 2; dz++) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
		const w = (dx ? xf : 1 - xf) * (dy ? yf : 1 - yf) * (dz ? zf : 1 - zf);
		r += w * hash3(xi + dx, yi + dy, zi + dz);
	}
	return r;
}
function fbm(x, y, z, oct = 4) {
	let a = 0.5, f = 1, s = 0, n = 0;
	for (let i = 0; i < oct; i++) { s += a * vnoise(x * f, y * f, z * f); n += a; a *= 0.5; f *= 2.03; }
	return s / n;
}
const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, v));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * clamp(t, 0, 1));
const mul = (a, k) => a.map(v => v * k);
const add = (a, b) => a.map((v, i) => v + b[i]);
const smooth = (e0, e1, x) => { const t = clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); };
function seeded(seed) { let s = seed >>> 0 || 1; return () => { s = (Math.imul(s, 1103515245) + 12345) & 0x7fffffff; return s / 0x7fffffff; }; }

// ================================================================ 3D math (json space)
const DEG = Math.PI / 180;
const I3 = () => [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
const mm3 = (a, b) => a.map(r => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const mv3 = (m, v) => [0, 1, 2].map(i => m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2]);
const Rx = a => { const c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; };
const Ry = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; };
const Rz = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; };
const MIR = [[-1, 0, 0], [0, 1, 0], [0, 0, 1]];
const transpose = m => [[m[0][0], m[1][0], m[2][0]], [m[0][1], m[1][1], m[2][1]], [m[0][2], m[1][2], m[2][2]]];
// GeckoLib: baked x = -json x, baked rotation = (-rx, -ry, rz), applied as Rz * Ry * Rx.
function rotJ(r) { return mm3(MIR, mm3(Rz(r[2] * DEG), mm3(Ry(-r[1] * DEG), mm3(Rx(-r[0] * DEG), MIR)))); }
const aff = (m, t) => ({ m, t });
const affMul = (a, b) => ({ m: mm3(a.m, b.m), t: [0, 1, 2].map(i => a.m[i][0] * b.t[0] + a.m[i][1] * b.t[1] + a.m[i][2] * b.t[2] + a.t[i]) });
const affApply = (a, p) => [0, 1, 2].map(i => a.m[i][0] * p[0] + a.m[i][1] * p[1] + a.m[i][2] * p[2] + a.t[i]);
function pivotRot(piv, r) {
	const m = rotJ(r);
	const mp = mv3(m, piv);
	return aff(m, [piv[0] - mp[0], piv[1] - mp[1], piv[2] - mp[2]]);
}
const vlen = v => Math.hypot(v[0], v[1], v[2]);
const vnorm = v => { const l = vlen(v) || 1; return [v[0] / l, v[1] / l, v[2] / l]; };
const vadd = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const vsub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const vsc = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const vdot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

// Cube rotation [rx, 0, rz] whose local +Y axis points along dir (closed form of rotJ([rx,0,rz]) * +Y =
// (sin rz cos rx, cos rz cos rx, -sin rx)).
function aimRot(dir) {
	const d = vnorm(dir);
	const rx = -Math.asin(clamp(d[2], -1, 1)) / DEG;
	const rz = Math.atan2(d[0], d[1]) / DEG;
	return [Math.round(rx * 100) / 100, 0, Math.round(rz * 100) / 100];
}

// ================================================================ model
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
const FACE_N = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
// 3D rest-space point of face texel (s, t in 0..1, t = 0 at the top for side faces); same mapping as the Titan generator
// (verified in game: GeckoLib maps a north face's u from -X to +X in json space).
function facePoint(c, f, s, t) {
	const [x0, y0, z0] = c.from, [x1, y1, z1] = c.to;
	const X = a => x0 + a * (x1 - x0), Y = a => y1 - a * (y1 - y0), Z = a => z0 + a * (z1 - z0);
	switch (f) {
		case 'north': return [X(s), Y(t), z0];
		case 'south': return [x1 - s * (x1 - x0), Y(t), z1];
		case 'east': return [x1, Y(t), Z(s)];
		case 'west': return [x0, Y(t), z1 - s * (z1 - z0)];
		case 'up': return [x1 - s * (x1 - x0), y1, Z(t)];
		default: return [x1 - s * (x1 - x0), y0, z1 - t * (z1 - z0)];
	}
}
function faceDims(c, f) {
	const sz = [c.to[0] - c.from[0], c.to[1] - c.from[1], c.to[2] - c.from[2]];
	return (f === 'north' || f === 'south') ? [sz[0], sz[1]] : (f === 'east' || f === 'west') ? [sz[2], sz[1]] : [sz[0], sz[2]];
}

class Model {
	constructor(name, opts = {}) {
		this.name = name; this.bones = []; this.B = {};
		this.D = opts.D || 3; this.TW = opts.TW || 512;
		this.k = 1 / 16; // blocks per model pixel (set by setHeight)
		this.A = {};      // anchors for the painter
	}
	bone(name, parent, pivot, rot = [0, 0, 0]) {
		if (this.B[name]) throw new Error(`duplicate bone ${name}`);
		const b = { name, parent, pivot: pivot.slice(), rot: rot.slice(), cubes: [] };
		this.bones.push(b); this.B[name] = b; return b;
	}
	box(b, x0, y0, z0, x1, y1, z1, mat, opts = {}) {
		if (typeof b === 'string') b = this.B[b];
		if (x1 < x0) [x0, x1] = [x1, x0];
		if (y1 < y0) [y0, y1] = [y1, y0];
		if (z1 < z0) [z0, z1] = [z1, z0];
		const c = { from: [x0, y0, z0], to: [x1, y1, z1], mat, rot: opts.rot || null, pivot: opts.pivot || null, paint: opts.paint || mat, bone: b.name, tag: opts.tag || null, seed: opts.seed || 0 };
		b.cubes.push(c); return c;
	}
	// centred box helper: centre [x,y,z], size [w,h,d]
	cbox(b, ctr, size, mat, opts = {}) {
		return this.box(b, ctr[0] - size[0] / 2, ctr[1] - size[1] / 2, ctr[2] - size[2] / 2, ctr[0] + size[0] / 2, ctr[1] + size[1] / 2, ctr[2] + size[2] / 2, mat, opts);
	}
	// A prism of length len and cross-section w x d (or w) starting at `from`, pointing along dir.
	stick(b, from, dir, len, w, mat, opts = {}) {
		const d = Array.isArray(w) ? w : [w, w];
		const r = aimRot(dir, opts.spin || 0);
		return this.box(b, from[0] - d[0] / 2, from[1], from[2] - d[1] / 2, from[0] + d[0] / 2, from[1] + len, from[2] + d[1] / 2, mat,
			Object.assign({}, opts, { rot: r, pivot: from.slice() }));
	}
	// A chain of sticks through the points, widths per segment (number or [w,d]); returns the cubes.
	tube(b, pts, widths, mat, opts = {}) {
		const out = [];
		for (let i = 0; i < pts.length - 1; i++) {
			const a = pts[i], z = pts[i + 1];
			const dir = vsub(z, a); const len = vlen(dir);
			const w = Array.isArray(widths) ? widths[Math.min(i, widths.length - 1)] : widths;
			const ov = opts.overlap == null ? 0.25 : opts.overlap; // overlap so the joints do not open up
			const st = i > 0 ? vsub(a, vsc(vnorm(dir), Math.min(ov, len * 0.3))) : a;
			out.push(this.stick(b, st, dir, len + (i > 0 ? Math.min(ov, len * 0.3) : 0), w, mat, Object.assign({}, opts, { seed: (opts.seed || 0) + i })));
		}
		return out;
	}
	cubes() { return this.bones.flatMap(b => b.cubes); }
	world(pose = {}, rootPos = [0, 0, 0]) {
		const W = {};
		for (const b of this.bones) {
			let off = pose[b.name] || [0, 0, 0];
			if (typeof off === 'number') off = [off, 0, 0];
			const r = [b.rot[0] + off[0], b.rot[1] + (off[1] || 0), b.rot[2] + (off[2] || 0)];
			let local = pivotRot(b.pivot, r);
			if (b.name === this.bones[0].name) local = affMul(aff(I3(), rootPos), local);
			W[b.name] = b.parent ? affMul(W[b.parent], local) : local;
		}
		return W;
	}
	cubeWorld(c, W) { let a = W[c.bone]; if (c.rot) a = affMul(a, pivotRot(c.pivot, c.rot)); return a; }
	bounds(W, filter) {
		const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
		for (const c of this.cubes()) {
			if (filter && !filter(c)) continue;
			const a = this.cubeWorld(c, W);
			for (const x of [c.from[0], c.to[0]]) for (const y of [c.from[1], c.to[1]]) for (const z of [c.from[2], c.to[2]]) {
				const q = affApply(a, [x, y, z]);
				for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], q[i]); hi[i] = Math.max(hi[i], q[i]); }
			}
		}
		return { lo, hi };
	}
	// shift everything so the lowest point of the filtered cubes sits on y = 0
	ground(filter) {
		const m = this.bounds(this.world({}), filter).lo[1];
		for (const b of this.bones) b.pivot[1] -= m;
		for (const c of this.cubes()) { c.from[1] -= m; c.to[1] -= m; if (c.pivot) c.pivot[1] -= m; }
		for (const k of Object.keys(this.A)) if (Array.isArray(this.A[k]) && this.A[k].length === 3) this.A[k][1] -= m;
		this.groundShift = m;
		return m;
	}
	setHeight(blocks, filter) {
		const h = this.bounds(this.world({}), filter).hi[1];
		this.restHeightPx = h; this.heightBlocks = blocks;
		this.k = blocks / h; this.MODEL_SCALE = blocks * 16 / h;
		return this.k;
	}
	layout() {
		const rects = [];
		for (const c of this.cubes()) {
			c.uv = {};
			for (const f of FACES) {
				const [fw, fh] = faceDims(c, f);
				const r = { c, f, w: Math.max(1, Math.round(fw * this.D)), h: Math.max(1, Math.round(fh * this.D)), fw, fh };
				c.uv[f] = r; rects.push(r);
			}
		}
		const TW = this.TW;
		const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
		let x = 0, y = 0, rowH = 0;
		for (const r of order) {
			if (r.w > TW) throw new Error(`${this.name}: face ${r.w} texels wider than the texture`);
			if (x + r.w > TW) { x = 0; y += rowH; rowH = 0; }
			r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
		}
		this.TH = Math.ceil((y + rowH) / 16) * 16;
		this.rects = rects;
	}
	// shadeFn(c, f, s, t, p, info) -> [rgb, alpha, glowRgb|null]
	paint(shadeFn) {
		if (!this.rects) this.layout();
		const TW = this.TW, TH = this.TH;
		this.tex = Buffer.alloc(TW * TH * 4); this.glow = Buffer.alloc(TW * TH * 4);
		const put = (buf, x, y, c, a = 255) => { const i = (y * TW + x) * 4; buf[i] = clamp(Math.round(c[0])); buf[i + 1] = clamp(Math.round(c[1])); buf[i + 2] = clamp(Math.round(c[2])); buf[i + 3] = a; };
		for (const r of this.rects) {
			const { c, f, w, h, u, v } = r;
			for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
				const s = (i + 0.5) / w, t = (j + 0.5) / h;
				const p = facePoint(c, f, s, t);
				const ePx = Math.min(Math.min(s, 1 - s) * r.fw, Math.min(t, 1 - t) * r.fh); // distance to the face border, px
				const [col, a, g] = shadeFn(c, f, s, t, p, { i, j, w, h, fw: r.fw, fh: r.fh, ePx, D: this.D });
				put(this.tex, u + i, v + j, col, a == null ? 255 : a);
				if (g && (a == null || a > 0)) put(this.glow, u + i, v + j, g);
			}
		}
	}
	geoJson(identifier) {
		const R3 = n => Math.round(n * 1000) / 1000;
		const b0 = this.bounds(this.world({}));
		return {
			format_version: '1.12.0',
			'minecraft:geometry': [{
				description: {
					identifier, texture_width: this.TW, texture_height: this.TH,
					visible_bounds_width: Math.ceil((b0.hi[0] - b0.lo[0]) * this.k * 1.5 + 2), visible_bounds_height: Math.ceil(b0.hi[1] * this.k * 1.2 + 2),
					visible_bounds_offset: [0, Math.ceil(b0.hi[1] * this.k / 2), 0],
				},
				bones: this.bones.map(b => {
					const o = { name: b.name };
					if (b.parent) o.parent = b.parent;
					o.pivot = b.pivot.map(R3);
					if (b.rot.some(v => v !== 0)) o.rotation = b.rot.map(R3);
					if (b.cubes.length) o.cubes = b.cubes.map(c => {
						const cc = { origin: c.from.map(R3), size: [0, 1, 2].map(i => R3(c.to[i] - c.from[i])) };
						if (c.rot) { cc.pivot = c.pivot.map(R3); cc.rotation = c.rot.map(R3); }
						cc.uv = {};
						for (const f of FACES) { const r = c.uv[f]; cc.uv[f] = { uv: [r.u, r.v], uv_size: [r.w, r.h] }; }
						return cc;
					});
					return o;
				}),
			}],
		};
	}
	writeAssets(dir, id) {
		fs.mkdirSync(dir, { recursive: true });
		fs.writeFileSync(path.join(dir, `${id}.geo.json`), JSON.stringify(this.geoJson(`geometry.${id}`), null, '\t') + '\n');
		fs.writeFileSync(path.join(dir, `${id}.png`), encodePng(this.TW, this.TH, this.tex));
		fs.writeFileSync(path.join(dir, `${id}_glowmask.png`), encodePng(this.TW, this.TH, this.glow));
		fs.writeFileSync(path.join(dir, `${id}_poses.json`), JSON.stringify({ model_scale: this.MODEL_SCALE, height_blocks: this.heightBlocks, poses: this.poses || {} }, null, '\t') + '\n');
	}
	texel(r, s, t) {
		const x = Math.min(r.w - 1, Math.max(0, Math.floor(s * r.w))), y = Math.min(r.h - 1, Math.max(0, Math.floor(t * r.h)));
		return ((r.v + y) * this.TW + r.u + x) * 4;
	}
}

// ================================================================ pose solvers
// Coordinate descent over pose angles. vars: [[bone, axis(0..2)]]; cost(W) -> number. Mutates and returns pose.
function solvePose(M, pose, vars, cost, iters = 60) {
	const get = (b, a) => { let v = pose[b]; if (v == null) v = [0, 0, 0]; if (typeof v === 'number') v = [v, 0, 0]; pose[b] = v.slice(); return pose[b][a]; };
	vars.forEach(([b, a]) => get(b, a));
	const rp = () => pose.$root || [0, 0, 0];
	let best = cost(M.world(pose, rp()));
	for (let step = 24; step > 0.2; step /= 2) {
		for (let it = 0; it < iters; it++) {
			let improved = false;
			for (const [b, a] of vars) for (const d of [-step, step]) {
				pose[b][a] += d;
				const c = cost(M.world(pose, rp()));
				if (c < best - 1e-9) { best = c; improved = true; } else pose[b][a] -= d;
			}
			if (!improved) break;
		}
	}
	return pose;
}
// Rotate a bone (by its own pose offset) so its local axis points along a world direction (json space).
function aimBone(M, pose, bone, axis, dir, axes = [0, 1, 2]) {
	const d = vnorm(dir);
	return solvePose(M, pose, axes.map(a => [bone, a]), W => 1 - vdot(vnorm(mv3(W[bone].m, axis)), d));
}
// Move a point (in a bone's rest coordinates) to a target (json space) by turning the given vars.
function reach(M, pose, bone, point, target, vars, aimCost) {
	return solvePose(M, pose, vars, W => { const q = affApply(W[bone], point); return vlen(vsub(q, target)) + (aimCost ? aimCost(W) : 0); });
}

// ================================================================ renderer
const DEFAULT_LIGHT = {
	key: vnorm([-0.55, 0.62, 0.56]), keyCol: [1.0, 0.97, 0.92], keyK: 0.78,
	rim: vnorm([0.78, 0.42, -0.46]), rimCol: [0.62, 0.78, 1.0], rimK: 0.62,
	fill: vnorm([0.7, -0.05, 0.6]), fillK: 0.14,
	amb: 0.36, sky: 0.1,
};

// camera: { yaw, pitch } degrees; view space x right, y up, z toward the viewer
function camMatrix(yaw, pitch) {
	const cy = Math.cos(yaw * DEG), sy = Math.sin(yaw * DEG);
	const V = [[cy, 0, -sy], [0, 1, 0], [-sy, 0, -cy]]; // looks at the model's front (-Z); det -1 (json space is mirrored)
	const P = [[1, 0, 0], [0, Math.cos(pitch * DEG), -Math.sin(pitch * DEG)], [0, Math.sin(pitch * DEG), Math.cos(pitch * DEG)]];
	return mm3(P, V);
}

// Gather every face of every item in world space (blocks).
function gather(items) {
	const faces = [];
	items.forEach((it, idx) => {
		const M = it.model;
		const W = M.world(it.pose || {}, it.root || [0, 0, 0]);
		const R = Ry((it.yaw || 0) * DEG);
		const k = it.scale || M.k;
		const pos = it.pos || [0, 0, 0];
		const toW = q => vadd(mv3(R, vsc(q, k)), pos);
		for (const c of M.cubes()) {
			if (it.hide && it.hide(c)) continue;
			const a = M.cubeWorld(c, W);
			for (const f of FACES) {
				const P = [[0, 0], [1, 0], [0, 1], [1, 1]].map(([s, t]) => toW(affApply(a, facePoint(c, f, s, t))));
				const n = vnorm(mv3(R, mv3(a.m, FACE_N[f])));
				faces.push({ M, r: c.uv[f], P, n, id: idx + 1, c, tint: it.tint || null, glowK: it.glowK == null ? 1 : it.glowK, silhouette: it.silhouette || null });
			}
		}
	});
	return faces;
}

function projector(cam, ppu, ox, oy) {
	const C = camMatrix(cam.yaw, cam.pitch);
	return { C, p: q => { const v = mv3(C, q); return [ox + v[0] * ppu, oy - v[1] * ppu, v[2] * ppu]; } };
}
function projBounds(faces, cam, extraPts = []) {
	const C = camMatrix(cam.yaw, cam.pitch);
	const lo = [Infinity, Infinity], hi = [-Infinity, -Infinity];
	const acc = q => { const v = mv3(C, q); lo[0] = Math.min(lo[0], v[0]); hi[0] = Math.max(hi[0], v[0]); lo[1] = Math.min(lo[1], v[1]); hi[1] = Math.max(hi[1], v[1]); };
	for (const f of faces) for (const q of f.P) acc(q);
	for (const q of extraPts) acc(q);
	return { lo, hi };
}

function boxBlur(src, w, h, r, ch) { // in-place-ish separable box blur on a Float32Array with ch channels
	if (r < 1) return src;
	const tmp = new Float32Array(src.length);
	const inv = 1 / (2 * r + 1);
	for (let y = 0; y < h; y++) for (let c = 0; c < ch; c++) {
		let acc = 0;
		for (let x = -r; x <= r; x++) acc += src[(y * w + clamp(x, 0, w - 1)) * ch + c];
		for (let x = 0; x < w; x++) {
			tmp[(y * w + x) * ch + c] = acc * inv;
			acc += src[(y * w + Math.min(w - 1, x + r + 1)) * ch + c] - src[(y * w + Math.max(0, x - r)) * ch + c];
		}
	}
	for (let x = 0; x < w; x++) for (let c = 0; c < ch; c++) {
		let acc = 0;
		for (let y = -r; y <= r; y++) acc += tmp[(clamp(y, 0, h - 1) * w + x) * ch + c];
		for (let y = 0; y < h; y++) {
			src[(y * w + x) * ch + c] = acc * inv;
			acc += tmp[(Math.min(h - 1, y + r + 1) * w + x) * ch + c] - tmp[(Math.max(0, y - r) * w + x) * ch + c];
		}
	}
	return src;
}

function rasterTri(W, H, A, B, C, cb) {
	const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), x1 = Math.min(W - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
	const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), y1 = Math.min(H - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
	const den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]);
	if (Math.abs(den) < 1e-9) return;
	for (let y = y0; y <= y1; y++) {
		const py = y + 0.5;
		for (let x = x0; x <= x1; x++) {
			const px = x + 0.5;
			const l1 = ((B[1] - C[1]) * (px - C[0]) + (C[0] - B[0]) * (py - C[1])) / den;
			if (l1 < -1e-6) continue;
			const l2 = ((C[1] - A[1]) * (px - C[0]) + (A[0] - C[0]) * (py - C[1])) / den;
			if (l2 < -1e-6) continue;
			const l3 = 1 - l1 - l2;
			if (l3 < -1e-6) continue;
			cb(x, y, l1, l2, l3);
		}
	}
}

// opts: { items, W, H, cam:{yaw,pitch}, ppu (px per block), origin:[sx,sy] (screen px of world (0,0,0)), ss,
//         bg:{top,bottom}, light, floor:[{x,z,r}] , pre(ctx), post(ctx), shadow:true }
function renderPanel(o) {
	const ss = o.ss || 2;
	const W = o.W * ss, H = o.H * ss;
	const ppu = o.ppu * ss;
	const L = o.light || DEFAULT_LIGHT;
	const { C, p: proj } = projector(o.cam, ppu, o.origin[0] * ss, o.origin[1] * ss);
	const Ct = transpose(C);
	const faces = o.faces || gather(o.items);
	const col = new Float32Array(W * H * 3);
	const zb = new Float32Array(W * H).fill(-Infinity);
	const idb = new Uint8Array(W * H);
	const emi = new Float32Array(W * H * 3);
	// ---- background: vertical gradient + a soft lighter floor pool
	const bgT = (o.bg && o.bg.top) || [128, 132, 140], bgB = (o.bg && o.bg.bottom) || [84, 88, 96];
	for (let y = 0; y < H; y++) {
		const t = y / (H - 1);
		const c0 = mix(bgT, bgB, Math.pow(t, 1.1));
		for (let x = 0; x < W; x++) {
			const i = (y * W + x) * 3;
			const vig = 1 - 0.18 * Math.pow(Math.abs(x / W - 0.5) * 2, 2.2);
			col[i] = c0[0] * vig; col[i + 1] = c0[1] * vig; col[i + 2] = c0[2] * vig;
		}
	}
	const ctx = { W, H, ss, ppu, proj, C, col, zb, emi, o };
	for (const fl of (o.floor || [])) { // floor pool: an ellipse on y = 0
		const ctr = proj([fl.x, 0, fl.z]);
		const rx = fl.r * ppu, ry = fl.r * ppu * Math.max(0.18, Math.sin(o.cam.pitch * DEG));
		const fc = fl.col || [150, 154, 162];
		for (let y = Math.max(0, Math.floor(ctr[1] - ry * 1.6)); y < Math.min(H, ctr[1] + ry * 1.6); y++) for (let x = Math.max(0, Math.floor(ctr[0] - rx * 1.6)); x < Math.min(W, ctr[0] + rx * 1.6); x++) {
			const d = Math.hypot((x - ctr[0]) / rx, (y - ctr[1]) / ry);
			const k = (fl.k || 0.55) * (1 - smooth(0.2, 1.5, d));
			if (k <= 0) continue;
			const i = (y * W + x) * 3;
			col[i] += (fc[0] - col[i]) * k; col[i + 1] += (fc[1] - col[i + 1]) * k; col[i + 2] += (fc[2] - col[i + 2]) * k;
		}
	}
	if (o.pre) o.pre(ctx);
	// ---- ground shadow: project every face onto y = 0 along the key light, blur, darken the background
	if (o.shadow !== false) {
		const Lw = mv3(Ct, L.key); // world-space direction toward the light
		const mask = new Float32Array(W * H);
		const sh = q => { const t = q[1] / Math.max(0.2, Lw[1]); return proj([q[0] - Lw[0] * t, 0, q[2] - Lw[2] * t]); };
		for (const f of faces) {
			if (f.noShadow) continue;
			const S = f.P.map(sh);
			const fill = (x, y) => { mask[y * W + x] = 1; };
			rasterTri(W, H, S[0], S[1], S[2], fill); rasterTri(W, H, S[1], S[3], S[2], fill);
		}
		// contact shadow: darker right under low geometry
		const contact = new Float32Array(W * H);
		for (const f of faces) {
			const low = f.P.every(q => q[1] < (o.contactH || 0.6));
			if (!low) continue;
			const S = f.P.map(q => proj([q[0], 0, q[2]]));
			const fill = (x, y) => { contact[y * W + x] = 1; };
			rasterTri(W, H, S[0], S[1], S[2], fill); rasterTri(W, H, S[1], S[3], S[2], fill);
		}
		const r = Math.max(2, Math.round(ppu * (o.shadowSoft || 0.18)));
		boxBlur(mask, W, H, r, 1); boxBlur(mask, W, H, r, 1);
		const r2 = Math.max(2, Math.round(ppu * 0.12));
		boxBlur(contact, W, H, r2, 1); boxBlur(contact, W, H, r2, 1);
		for (let i = 0; i < W * H; i++) {
			const k = 1 - Math.min(0.62, 0.42 * mask[i] + 0.4 * contact[i]);
			col[i * 3] *= k; col[i * 3 + 1] *= k; col[i * 3 + 2] *= k;
		}
	}
	// ---- faces
	for (const f of faces) {
		const nv = mv3(C, f.n);
		if (nv[2] <= 1e-4) continue; // back face
		const S = f.P.map(proj);
		const r = f.r, M = f.M;
		const diff = Math.max(0, vdot(nv, L.key));
		const rim = Math.pow(Math.max(0, vdot(nv, L.rim)), 1.6);
		const fill = Math.max(0, vdot(nv, L.fill));
		const lk = L.amb + L.sky * Math.max(0, nv[1]) + L.keyK * diff + L.fillK * fill;
		const lit = [lk * L.keyCol[0], lk * L.keyCol[1], lk * L.keyCol[2]];
		const rimAdd = [L.rimK * rim * L.rimCol[0] * 255 * 0.45, L.rimK * rim * L.rimCol[1] * 255 * 0.45, L.rimK * rim * L.rimCol[2] * 255 * 0.45];
		const UV = [[0, 0], [1, 0], [0, 1], [1, 1]];
		for (const tri of [[0, 1, 2], [1, 3, 2]]) {
			const [A, B, Cc] = tri.map(i => S[i]);
			const [ua, ub, uc] = tri.map(i => UV[i]);
			rasterTri(W, H, A, B, Cc, (x, y, l1, l2, l3) => {
				const z = l1 * A[2] + l2 * B[2] + l3 * Cc[2];
				const idx = y * W + x;
				if (z <= zb[idx]) return;
				const s = l1 * ua[0] + l2 * ub[0] + l3 * uc[0];
				const t = l1 * ua[1] + l2 * ub[1] + l3 * uc[1];
				const ti = M.texel(r, s, t);
				if (M.tex[ti + 3] < 128) return;
				zb[idx] = z; idb[idx] = f.id;
				let rr = M.tex[ti], gg = M.tex[ti + 1], bb = M.tex[ti + 2];
				if (f.silhouette) { rr = f.silhouette[0]; gg = f.silhouette[1]; bb = f.silhouette[2]; }
				const j = idx * 3;
				if (f.tint) { rr *= f.tint[0]; gg *= f.tint[1]; bb *= f.tint[2]; }
				col[j] = rr * lit[0] + rimAdd[0] * (f.silhouette ? 0.4 : 1); col[j + 1] = gg * lit[1] + rimAdd[1] * (f.silhouette ? 0.4 : 1); col[j + 2] = bb * lit[2] + rimAdd[2] * (f.silhouette ? 0.4 : 1);
				if (M.glow[ti + 3] && !f.silhouette) {
					const gk = f.glowK;
					const g = [M.glow[ti], M.glow[ti + 1], M.glow[ti + 2]];
					// emissive: the texel shows at full brightness and feeds the bloom
					col[j] = Math.max(col[j], (rr * 0.35 + g[0] * 0.95) * gk + col[j] * (1 - gk));
					col[j + 1] = Math.max(col[j + 1], (gg * 0.35 + g[1] * 0.95) * gk + col[j + 1] * (1 - gk));
					col[j + 2] = Math.max(col[j + 2], (bb * 0.35 + g[2] * 0.95) * gk + col[j + 2] * (1 - gk));
					emi[j] = g[0] * gk; emi[j + 1] = g[1] * gk; emi[j + 2] = g[2] * gk;
				} else { emi[j] = 0; emi[j + 1] = 0; emi[j + 2] = 0; }
			});
		}
	}
	// ---- post: outlines at depth breaks + cheap screen-space AO
	{
		const out = new Float32Array(W * H);
		const thr = Math.max(1.5, ppu * (o.edgeThr || 0.09));
		const aoR = Math.max(2, Math.round(ppu * (o.aoR || 0.22)));
		const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1], [0.7, 0.7], [-0.7, 0.7], [0.7, -0.7], [-0.7, -0.7]];
		for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
			const i = y * W + x;
			if (zb[i] === -Infinity) continue;
			let edge = 0;
			for (const [dx, dy] of [[ss, 0], [-ss, 0], [0, ss], [0, -ss]]) {
				const nx = x + dx, ny = y + dy;
				if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
				const n = ny * W + nx;
				if (zb[n] === -Infinity || zb[n] > zb[i] + thr) { edge = 1; break; }
			}
			let occ = 0;
			for (const [dx, dy] of dirs) {
				const nx = Math.round(x + dx * aoR), ny = Math.round(y + dy * aoR);
				if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
				const dz = zb[ny * W + nx] - zb[i];
				if (dz > aoR * 0.15 && dz < aoR * 3) occ++;
			}
			out[i] = (edge ? (o.edgeK || 0.55) : 1) * (1 - 0.38 * occ / 8);
		}
		for (let i = 0; i < W * H; i++) if (zb[i] !== -Infinity) { col[i * 3] *= out[i]; col[i * 3 + 1] *= out[i]; col[i * 3 + 2] *= out[i]; }
	}
	if (o.post) o.post(ctx);
	// ---- downsample + bloom
	const w = o.W, h = o.H;
	const img = new Float32Array(w * h * 3), em = new Float32Array(w * h * 3);
	const inv = 1 / (ss * ss);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const d = (y * w + x) * 3;
		for (let yy = 0; yy < ss; yy++) for (let xx = 0; xx < ss; xx++) {
			const s = ((y * ss + yy) * W + x * ss + xx) * 3;
			img[d] += col[s] * inv; img[d + 1] += col[s + 1] * inv; img[d + 2] += col[s + 2] * inv;
			em[d] += emi[s] * inv; em[d + 1] += emi[s + 1] * inv; em[d + 2] += emi[s + 2] * inv;
		}
	}
	const bloomK = o.bloom == null ? 1 : o.bloom;
	if (bloomK > 0) {
		const e1 = Float32Array.from(em), e2 = Float32Array.from(em);
		const r1 = Math.max(2, Math.round(o.ppu * 0.12)), r2 = Math.max(4, Math.round(o.ppu * 0.45));
		for (let k = 0; k < 3; k++) boxBlur(e1, w, h, r1, 3);
		for (let k = 0; k < 3; k++) boxBlur(e2, w, h, r2, 3);
		for (let i = 0; i < w * h * 3; i++) img[i] += bloomK * (1.5 * e1[i] + 1.1 * e2[i]);
	}
	const res = Buffer.alloc(w * h * 4);
	for (let i = 0; i < w * h; i++) {
		for (let c = 0; c < 3; c++) { const v = img[i * 3 + c]; res[i * 4 + c] = clamp(Math.round(v > 235 ? 235 + (v - 235) * 0.25 : v)); }
		res[i * 4 + 3] = 255;
	}
	return res;
}

// ================================================================ image canvas + text
class Canvas {
	constructor(w, h, bg = [40, 42, 48]) { this.w = w; this.h = h; this.px = Buffer.alloc(w * h * 4); for (let i = 0; i < w * h; i++) { this.px[i * 4] = bg[0]; this.px[i * 4 + 1] = bg[1]; this.px[i * 4 + 2] = bg[2]; this.px[i * 4 + 3] = 255; } }
	blit(buf, bw, bh, x0, y0) { for (let y = 0; y < bh; y++) { const ty = y0 + y; if (ty < 0 || ty >= this.h) continue; for (let x = 0; x < bw; x++) { const tx = x0 + x; if (tx < 0 || tx >= this.w) continue; const s = (y * bw + x) * 4, d = (ty * this.w + tx) * 4; this.px[d] = buf[s]; this.px[d + 1] = buf[s + 1]; this.px[d + 2] = buf[s + 2]; } } }
	rect(x0, y0, w, h, c, a = 1) { x0 = Math.round(x0); y0 = Math.round(y0); w = Math.round(w); h = Math.round(h); for (let y = Math.max(0, y0); y < Math.min(this.h, y0 + h); y++) for (let x = Math.max(0, x0); x < Math.min(this.w, x0 + w); x++) { const d = (y * this.w + x) * 4; for (let k = 0; k < 3; k++) this.px[d + k] = Math.round(this.px[d + k] * (1 - a) + c[k] * a); } }
	text(str, x, y, scale, c, opts = {}) {
		str = String(str).toUpperCase(); x = Math.round(x); y = Math.round(y);
		const adv = 6 * scale;
		if (opts.align === 'center') x -= Math.round((str.length * adv - scale) / 2);
		if (opts.align === 'right') x -= str.length * adv - scale;
		if (opts.shadow !== false) this._text(str, x + Math.max(1, scale >> 1), y + Math.max(1, scale >> 1), scale, [12, 14, 18], adv, 0.7);
		this._text(str, x, y, scale, c, adv, 1);
	}
	_text(str, x, y, scale, c, adv, a) {
		for (let i = 0; i < str.length; i++) {
			const g = FONT[str[i]] || FONT['?'];
			for (let r = 0; r < 7; r++) for (let q = 0; q < 5; q++) if (g[r][q] === '#') this.rect(x + i * adv + q * scale, y + r * scale, scale, scale, c, a);
		}
	}
	save(file) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, encodePng(this.w, this.h, this.px)); }
}
const FONT = (() => {
	const g = {
		A: [' ### ', '#   #', '#   #', '#####', '#   #', '#   #', '#   #'], B: ['#### ', '#   #', '#   #', '#### ', '#   #', '#   #', '#### '],
		C: [' ####', '#    ', '#    ', '#    ', '#    ', '#    ', ' ####'], D: ['#### ', '#   #', '#   #', '#   #', '#   #', '#   #', '#### '],
		E: ['#####', '#    ', '#    ', '#### ', '#    ', '#    ', '#####'], F: ['#####', '#    ', '#    ', '#### ', '#    ', '#    ', '#    '],
		G: [' ####', '#    ', '#    ', '#  ##', '#   #', '#   #', ' ####'], H: ['#   #', '#   #', '#   #', '#####', '#   #', '#   #', '#   #'],
		I: ['#####', '  #  ', '  #  ', '  #  ', '  #  ', '  #  ', '#####'], J: ['  ###', '   # ', '   # ', '   # ', '#  # ', '#  # ', ' ##  '],
		K: ['#   #', '#  # ', '# #  ', '##   ', '# #  ', '#  # ', '#   #'], L: ['#    ', '#    ', '#    ', '#    ', '#    ', '#    ', '#####'],
		M: ['#   #', '## ##', '# # #', '# # #', '#   #', '#   #', '#   #'], N: ['#   #', '##  #', '# # #', '#  ##', '#   #', '#   #', '#   #'],
		O: [' ### ', '#   #', '#   #', '#   #', '#   #', '#   #', ' ### '], P: ['#### ', '#   #', '#   #', '#### ', '#    ', '#    ', '#    '],
		Q: [' ### ', '#   #', '#   #', '#   #', '# # #', '#  # ', ' ## #'], R: ['#### ', '#   #', '#   #', '#### ', '# #  ', '#  # ', '#   #'],
		S: [' ####', '#    ', '#    ', ' ### ', '    #', '    #', '#### '], T: ['#####', '  #  ', '  #  ', '  #  ', '  #  ', '  #  ', '  #  '],
		U: ['#   #', '#   #', '#   #', '#   #', '#   #', '#   #', ' ### '], V: ['#   #', '#   #', '#   #', '#   #', '#   #', ' # # ', '  #  '],
		W: ['#   #', '#   #', '#   #', '# # #', '# # #', '## ##', '#   #'], X: ['#   #', '#   #', ' # # ', '  #  ', ' # # ', '#   #', '#   #'],
		Y: ['#   #', '#   #', ' # # ', '  #  ', '  #  ', '  #  ', '  #  '], Z: ['#####', '    #', '   # ', '  #  ', ' #   ', '#    ', '#####'],
		0: [' ### ', '#   #', '#  ##', '# # #', '##  #', '#   #', ' ### '], 1: ['  #  ', ' ##  ', '  #  ', '  #  ', '  #  ', '  #  ', ' ### '],
		2: [' ### ', '#   #', '    #', '   # ', '  #  ', ' #   ', '#####'], 3: ['#### ', '    #', '    #', ' ### ', '    #', '    #', '#### '],
		4: ['#   #', '#   #', '#   #', '#####', '    #', '    #', '    #'], 5: ['#####', '#    ', '#    ', '#### ', '    #', '    #', '#### '],
		6: [' ### ', '#    ', '#    ', '#### ', '#   #', '#   #', ' ### '], 7: ['#####', '    #', '   # ', '  #  ', '  #  ', '  #  ', '  #  '],
		8: [' ### ', '#   #', '#   #', ' ### ', '#   #', '#   #', ' ### '], 9: [' ### ', '#   #', '#   #', ' ####', '    #', '    #', ' ### '],
		' ': ['     ', '     ', '     ', '     ', '     ', '     ', '     '], '.': ['     ', '     ', '     ', '     ', '     ', ' ##  ', ' ##  '],
		'-': ['     ', '     ', '     ', ' ### ', '     ', '     ', '     '], ':': ['     ', ' ##  ', ' ##  ', '     ', ' ##  ', ' ##  ', '     '],
		'(': ['   # ', '  #  ', ' #   ', ' #   ', ' #   ', '  #  ', '   # '], ')': [' #   ', '  #  ', '   # ', '   # ', '   # ', '  #  ', ' #   '],
		'/': ['    #', '    #', '   # ', '  #  ', ' #   ', '#    ', '#    '], ',': ['     ', '     ', '     ', '     ', '  ## ', '  #  ', ' #   '],
		"'": ['  #  ', '  #  ', '     ', '     ', '     ', '     ', '     '], '+': ['     ', '  #  ', '  #  ', '#####', '  #  ', '  #  ', '     '],
		'?': [' ### ', '#   #', '    #', '   # ', '  #  ', '     ', '  #  '], 'X2': [],
	};
	return g;
})();

// ================================================================ sheets
// panels: [{ label, items, cam:{yaw,pitch}, floor? }]; all share one scale unless panel.ownScale.
// Each panel's width is fitted to its content; the sheet is at least minW wide; the model fills ~86 % of the height.
function sheet(file, title, subtitle, panels, opts = {}) {
	const PH = opts.panelH || 1120, TITLE = 84, PAD = 0.07;
	const minW = opts.minW || 1700;
	const fits = panels.map(pn => {
		pn.faces = gather(pn.items);
		const extra = pn.items.map(it => it.pos || [0, 0, 0]).concat(pn.extraPts || []);
		const b = projBounds(pn.faces, pn.cam, extra);
		return b;
	});
	// shared scale: fit every (non-ownScale) panel's height
	let ppu = Infinity;
	fits.forEach((b, i) => { if (!panels[i].ownScale) ppu = Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])); });
	if (!isFinite(ppu)) ppu = PH * (1 - 2 * PAD) / Math.max(...fits.map(b => b.hi[1] - b.lo[1]));
	if (opts.maxPpu) ppu = Math.min(ppu, opts.maxPpu);
	const widths = fits.map((b, i) => {
		const k = panels[i].ownScale ? Math.min(ppu * 1.0, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])) : ppu;
		panels[i].ppu = k;
		return Math.max(opts.minPanelW || 360, Math.round((b.hi[0] - b.lo[0]) * k + PH * 2 * PAD * 0.9));
	});
	let total = widths.reduce((a, b) => a + b, 0);
	if (total < minW) { const extra = (minW - total) / widths.length; for (let i = 0; i < widths.length; i++) widths[i] = Math.round(widths[i] + extra); total = widths.reduce((a, b) => a + b, 0); }
	const maxW = opts.maxW || 3400;
	if (total > maxW) { // too wide: shrink everything
		const f = maxW / total; ppu *= f;
		for (let i = 0; i < widths.length; i++) { widths[i] = Math.round(widths[i] * f); panels[i].ppu *= f; }
		total = widths.reduce((a, b) => a + b, 0);
	}
	const cv = new Canvas(total, PH + TITLE, [34, 36, 42]);
	let x = 0;
	panels.forEach((pn, i) => {
		const b = fits[i], k = pn.ppu, w = widths[i];
		const cxw = (b.lo[0] + b.hi[0]) / 2, cyw = (b.lo[1] + b.hi[1]) / 2;
		const origin = [w / 2 - cxw * k, PH / 2 + cyw * k + (pn.dy || 0)];
		const floors = pn.floor || pn.items.map(it => ({ x: (it.pos || [0, 0, 0])[0], z: (it.pos || [0, 0, 0])[2], r: (it.floorR || it.model.heightBlocks * 0.42 + 0.8) }));
		const img = renderPanel({ items: pn.items, faces: pn.faces, W: w, H: PH, cam: pn.cam, ppu: k, origin, ss: opts.ss || 2, floor: floors, bg: pn.bg || opts.bg, pre: pn.pre, post: pn.post, light: pn.light || opts.light, bloom: pn.bloom == null ? opts.bloom : pn.bloom, aoR: opts.aoR, edgeThr: opts.edgeThr, shadowSoft: opts.shadowSoft, contactH: opts.contactH });
		cv.blit(img, w, PH, x, TITLE);
		if (i > 0) cv.rect(x, TITLE, 2, PH, [30, 32, 38]);
		cv.text(pn.label, x + w / 2, TITLE + 18, 3, [236, 240, 246], { align: 'center' });
		x += w;
	});
	cv.rect(0, 0, total, TITLE, [26, 28, 34]);
	cv.text(title, 28, 18, 5, [240, 244, 250]);
	if (subtitle) cv.text(subtitle, 28 + (title.length * 30) + 28, 30, 3, [160, 190, 220]);
	if (opts.footer) cv.text(opts.footer, total - 24, TITLE + PH - 34, 2, [210, 216, 226], { align: 'right' });
	cv.save(file);
	console.log(`wrote ${file} (${total}x${PH + TITLE})`);
}

module.exports = {
	encodePng, hash3, vnoise, fbm, clamp, mix, mul, add, smooth, seeded,
	DEG, mm3, mv3, Rx, Ry, Rz, rotJ, aff, affMul, affApply, pivotRot, vlen, vnorm, vadd, vsub, vsc, vdot, aimRot,
	FACES, facePoint, Model, solvePose, aimBone, reach, renderPanel, gather, projBounds, camMatrix, Canvas, sheet, DEFAULT_LIGHT, boxBlur, rasterTri,
};
