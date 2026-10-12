// v0.16.1 shared helpers for the Ultron Contagion asset scripts (gen_v0161_contagion_assets.js,
// gen_ultron_omega_placeholder.js): hand-rolled PNG encoder (no Python here), RGBA canvas, value noise,
// a GeckoLib geo builder (box UV, shelf-packed texture, per-face painter) and a keyframe/clip writer.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.join(__dirname, '..');
const RES = path.join(ROOT, 'src/main/resources/assets/projecthero');

// ================================================================ PNG (copied from gen_v01420_titan_model.js)
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePng(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ================================================================ colour + noise
const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, v));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * clamp(t, 0, 1));
const mul = (a, k) => a.map(v => v * k);
const add = (a, k) => a.map(v => v + k);
const hex = s => { s = s.replace('#', ''); return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)]; };
function hash3(x, y, z) {
	let h = (x * 374761393 + y * 668265263 + z * 2147483647) | 0;
	h = (h ^ (h >>> 13)) * 1274126177 | 0;
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

// ================================================================ canvas
class Canvas {
	constructor(w, h) { this.w = w; this.h = h; this.px = Buffer.alloc(w * h * 4); }
	in(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h; }
	set(x, y, c, a = 255) {
		x = Math.floor(x); y = Math.floor(y); if (!this.in(x, y)) return;
		const i = (y * this.w + x) * 4;
		this.px[i] = clamp(Math.round(c[0])); this.px[i + 1] = clamp(Math.round(c[1])); this.px[i + 2] = clamp(Math.round(c[2])); this.px[i + 3] = clamp(Math.round(a));
	}
	get(x, y) { if (!this.in(x, y)) return [0, 0, 0, 0]; const i = (y * this.w + x) * 4; return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]]; }
	blend(x, y, c, t) {
		const o = this.get(x, y); if (!this.in(x, y)) return;
		if (o[3] === 0) { this.set(x, y, c, 255 * t); return; }
		this.set(x, y, mix(o.slice(0, 3), c, t), o[3]);
	}
	blit(src, dx, dy) { for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) { const c = src.get(x, y); this.set(dx + x, dy + y, c, c[3]); } }
}

// ================================================================ file output
const written = [];
function ensureDir(p) { fs.mkdirSync(path.dirname(p), { recursive: true }); }
function writeJson(rel, obj) { const p = path.join(RES, rel); ensureDir(p); fs.writeFileSync(p, JSON.stringify(obj, null, '\t') + '\n'); written.push(rel); }
function writePng(rel, cv) { const p = path.join(RES, rel); ensureDir(p); fs.writeFileSync(p, encodePng(cv.w, cv.h, cv.px)); written.push(`${rel} (${cv.w}x${cv.h})`); }

// ================================================================ GeckoLib geo builder (box UV)
// Face names: up, down, right (-X, the entity's right), front (-Z, north), left (+X), back (+Z).
// (s, t) are 0..1 across a face as it appears in the texture (s left->right, t top->bottom); `point` maps them to
// model space following the standard skin wrap (right | front | left | back read left-to-right around the cube).
const FACES = ['up', 'down', 'right', 'front', 'left', 'back'];
class Geo {
	constructor(name) { this.name = name; this.bones = []; this.B = {}; this.cubes = []; }
	bone(name, parent, pivot, rot = [0, 0, 0]) {
		if (this.B[name]) throw new Error('dup bone ' + name);
		if (parent && !this.B[parent]) throw new Error('missing parent ' + parent);
		const b = { name, parent, pivot: pivot.slice(), rot: rot.slice(), cubes: [] }; this.bones.push(b); this.B[name] = b; return b;
	}
	cube(bone, from, size, mat, opts = {}) {
		for (const v of size) if (!Number.isInteger(v)) throw new Error(`${this.name}: non-integer cube size ${size} on ${bone}`);
		const c = { bone, from: from.slice(), size: size.slice(), mat, rot: opts.rot || null, pivot: opts.pivot || null, tag: opts.tag || null, id: this.cubes.length, opts };
		this.B[bone].cubes.push(c); this.cubes.push(c); return c;
	}
	// centred helper: centre x/z, bottom y
	ccube(bone, cx, y0, cz, w, h, d, mat, opts = {}) { return this.cube(bone, [cx - w / 2, y0, cz - d / 2], [w, h, d], mat, opts); }
	layout() {
		const items = this.cubes.map(c => { const [w, h, d] = c.size; return { c, fw: 2 * (d + w), fh: d + h }; });
		items.sort((a, b) => b.fh - a.fh || b.fw - a.fw);
		let best = null;
		for (const W of [32, 64, 128, 256, 512, 1024]) {
			if (items.some(it => it.fw > W)) continue;
			let x = 0, y = 0, rowH = 0; const pos = [];
			for (const it of items) {
				if (x + it.fw > W) { x = 0; y += rowH; rowH = 0; }
				pos.push([x, y]); x += it.fw; rowH = Math.max(rowH, it.fh);
			}
			const need = y + rowH; let H = 16; while (H < need) H *= 2;
			if (H > W * 2) continue;
			const area = W * H;
			if (!best || area < best.area || (area === best.area && Math.abs(W - H) < Math.abs(best.W - best.H))) best = { W, H, area, pos };
		}
		items.forEach((it, i) => { it.c.uv = best.pos[i]; });
		this.W = best.W; this.H = best.H;
		return this;
	}
	faceRects(c) {
		const [u, v] = c.uv, [w, h, d] = c.size, [x0, y0, z0] = c.from, x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
		return [
			{ face: 'up', x: u + d, y: v, w, h: d, point: (s, t) => [x0 + s * w, y1, z1 - t * d] },
			{ face: 'down', x: u + d + w, y: v, w, h: d, point: (s, t) => [x0 + s * w, y0, z1 - t * d] },
			{ face: 'right', x: u, y: v + d, w: d, h, point: (s, t) => [x0, y1 - t * h, z1 - s * d] },
			{ face: 'front', x: u + d, y: v + d, w, h, point: (s, t) => [x0 + s * w, y1 - t * h, z0] },
			{ face: 'left', x: u + d + w, y: v + d, w: d, h, point: (s, t) => [x1, y1 - t * h, z0 + s * d] },
			{ face: 'back', x: u + 2 * d + w, y: v + d, w, h, point: (s, t) => [x1 - s * w, y1 - t * h, z1] },
		];
	}
	// painter(cube, rect, i, j) -> { col, a?, glow? }
	paint(painter) {
		const tex = new Canvas(this.W, this.H), glow = new Canvas(this.W, this.H);
		for (const c of this.cubes) for (const r of this.faceRects(c)) {
			for (let j = 0; j < r.h; j++) for (let i = 0; i < r.w; i++) {
				const out = painter(c, r, i, j);
				if (!out) continue;
				tex.set(r.x + i, r.y + j, out.col, out.a == null ? 255 : out.a);
				if (out.glow) glow.set(r.x + i, r.y + j, out.glow, 255);
			}
		}
		return { tex, glow };
	}
	json(extra = {}) {
		const R3 = n => Math.round(n * 1000) / 1000;
		let minY = 0, maxY = 0, maxR = 0;
		for (const c of this.cubes) { minY = Math.min(minY, c.from[1]); maxY = Math.max(maxY, c.from[1] + c.size[1]); maxR = Math.max(maxR, Math.abs(c.from[0]), Math.abs(c.from[0] + c.size[0]), Math.abs(c.from[2]), Math.abs(c.from[2] + c.size[2])); }
		return {
			format_version: '1.12.0',
			'minecraft:geometry': [{
				description: Object.assign({
					identifier: `geometry.${this.name}`, texture_width: this.W, texture_height: this.H,
					visible_bounds_width: Math.ceil((maxR * 2 + 16) / 16), visible_bounds_height: Math.ceil((maxY - minY + 16) / 16), visible_bounds_offset: [0, (maxY + minY) / 32, 0],
				}, extra),
				bones: this.bones.map(b => {
					const o = { name: b.name };
					if (b.parent) o.parent = b.parent;
					o.pivot = b.pivot.map(R3);
					if (b.rot.some(v => v !== 0)) o.rotation = b.rot.map(R3);
					if (b.cubes.length) o.cubes = b.cubes.map(c => {
						const cc = { origin: c.from.map(R3), size: c.size.slice(), uv: c.uv.slice() };
						if (c.rot) { cc.pivot = (c.pivot || [c.from[0] + c.size[0] / 2, c.from[1] + c.size[1] / 2, c.from[2] + c.size[2] / 2]).map(R3); cc.rotation = c.rot.map(R3); }
						if (c.opts.inflate) cc.inflate = c.opts.inflate;
						return cc;
					});
					return o;
				}),
			}],
		};
	}
}

// ================================================================ animation writer
// spec: { bone: { rotation: { tick: [x,y,z] | {v:[x,y,z], e:'linear'} }, position: {...}, scale: {...} } }
// ticks -> seconds strings; default easing easeinoutsine except where `lin` is set on the clip.
function secKey(t) { let s = (+(t / 20).toFixed(4)).toString(); if (!s.includes('.')) s += '.0'; return s; }
function clip(lengthTicks, loop, spec, opts = {}) {
	const bones = {};
	for (const [bn, chans] of Object.entries(spec)) {
		bones[bn] = {};
		for (const [ch, keys] of Object.entries(chans)) {
			const out = {};
			const ticks = Object.keys(keys).map(Number).sort((a, b) => a - b);
			ticks.forEach((t, k) => {
				let val = keys[t], easing = opts.lin ? 'linear' : 'easeinoutsine';
				if (val && !Array.isArray(val)) { easing = val.e || easing; val = val.v; }
				const R = n => Math.round(n * 1000) / 1000;
				out[secKey(t)] = k === 0 || easing === 'linear' ? val.map(R) : { vector: val.map(R), easing };
			});
			bones[bn][ch] = out;
		}
	}
	const a = { animation_length: lengthTicks / 20 };
	a.loop = loop;
	a.bones = bones;
	return a;
}

module.exports = { ROOT, RES, encodePng, clamp, mix, mul, add, hex, hash3, vnoise, fbm, Canvas, written, writeJson, writePng, Geo, FACES, clip, secKey };
