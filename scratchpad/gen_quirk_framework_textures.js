// v0.17.1 Quirk framework: every texture the shared Quirk VFX toolkit draws -- the effect textures (soft glow, smoke,
// ribbon, the three 8-frame flipbooks, the four ground decals, a white texel) and the seven quirk_* particle sprites
// (+ their particles/*.json). Usage, from the repo root: node scratchpad/gen_quirk_framework_textures.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encode } = require('./suits/skin_tools.js');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..'));
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures');
const FX = path.join(TEX, 'entity/quirk/fx');
const PART = path.join(TEX, 'particle');
const PDEF = path.join(ROOT, 'src/main/resources/assets/projecthero/particles');
for (const d of [FX, PART, PDEF]) fs.mkdirSync(d, { recursive: true });

const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, Math.round(v)));
const hash = (x, y, s = 0) => { let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t);
/** Value noise. */
function vnoise(x, y, s) {
	const xi = Math.floor(x), yi = Math.floor(y), fx = smooth(x - xi), fy = smooth(y - yi);
	const a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
	return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}
function fbm(x, y, s, oct = 4) {
	let v = 0, amp = 0.5, f = 1;
	for (let i = 0; i < oct; i++) { v += amp * vnoise(x * f, y * f, s + i * 17); amp *= 0.5; f *= 2; }
	return v / (1 - Math.pow(0.5, oct));
}
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function put(im, x, y, r, g, b, a) {
	if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const i = (y * im.w + x) * 4; im.px[i] = clamp(r); im.px[i + 1] = clamp(g); im.px[i + 2] = clamp(b); im.px[i + 3] = clamp(a);
}
/** Additive (eyes) textures: the blend ignores alpha, so the falloff must be in the colour too. */
function premul(im) { for (let i = 0; i < im.px.length; i += 4) { const a = im.px[i + 3] / 255; im.px[i] = Math.round(im.px[i] * a); im.px[i + 1] = Math.round(im.px[i + 1] * a); im.px[i + 2] = Math.round(im.px[i + 2] * a); } return im; }
function save(im, dir, name) { fs.writeFileSync(path.join(dir, name + '.png'), encode(im.w, im.h, im.px)); console.log(name, im.w + 'x' + im.h); }

// ---------------- effect textures ----------------
/** Soft radial glow (white; additive billboards, flashes, cores). */
function glow() {
	const n = 64, im = img(n, n), c = (n - 1) / 2;
	for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
		const d = Math.hypot(x - c, y - c) / c;
		const a = Math.pow(Math.max(0, 1 - d), 2.0);
		put(im, x, y, 255, 255, 255, a * 255);
	}
	return im;
}
/** A soft, cloudy smoke blob (white, the renderer tints it; lit translucent). */
function smoke(seed) {
	const n = 64, im = img(n, n), c = (n - 1) / 2;
	for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
		const d = Math.hypot(x - c, y - c) / c;
		const nz = fbm(x / 12, y / 12, seed, 4);
		const a = Math.max(0, 1 - d * (0.75 + nz * 0.5)) * (0.55 + nz * 0.6);
		const v = 190 + nz * 65;
		put(im, x, y, v, v, v, Math.min(1, a * 1.7) * 235);
	}
	return im;
}
/** The ribbon strip: u along the ribbon (streaky), v across (bright core, soft edges). 64 x 16. */
function ribbon() {
	const w = 64, h = 16, im = img(w, h);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const v = (y + 0.5) / h * 2 - 1;
		const core = Math.pow(Math.max(0, 1 - Math.abs(v)), 1.4);
		const streak = 0.7 + 0.3 * fbm(x / 5, y / 2.5, 41, 3);
		put(im, x, y, 255, 255, 255, Math.min(1, core * streak * 1.15) * 255);
	}
	return im;
}
/** An 8-frame flipbook: 8 frames of 32 x 32 in a row. */
function flipbook(kind) {
	const f = 32, im = img(f * 8, f), c = (f - 1) / 2;
	for (let k = 0; k < 8; k++) {
		const t = k / 7;
		for (let y = 0; y < f; y++) for (let x = 0; x < f; x++) {
			const dx = (x - c) / c, dy = (y - c) / c, d = Math.hypot(dx, dy);
			const nz = fbm(x / 7 + k * 0.9, y / 7 - k * 0.6, kind === 'ink' ? 77 : 23, 4);
			let r, g, b, a;
			if (kind === 'fireball') {
				// grows; the hot core cools and the edge breaks up
				const rad = 0.45 + 0.5 * t;
				const edge = d / rad + (nz - 0.5) * (0.5 + t * 0.6);
				if (edge > 1) continue;
				const heat = Math.max(0, 1 - edge) * (1 - t * 0.7);
				r = 255; g = 120 + 135 * Math.min(1, heat * 1.6); b = 40 + 215 * Math.max(0, heat * 1.4 - 0.5);
				a = Math.min(1, (1 - edge) * 3) * (1 - t * t * 0.8) * 255;
			} else if (kind === 'smoke') {
				const rad = 0.4 + 0.55 * t;
				const edge = d / rad + (nz - 0.5) * 0.7;
				if (edge > 1) continue;
				const v = 170 + nz * 70;
				r = g = b = v;
				a = Math.min(1, (1 - edge) * 2.2) * (1 - t * 0.85) * 230;
			} else {
				// ink: a splash that bursts into droplets and thins
				const rad = 0.35 + 0.6 * smooth(Math.min(1, t * 1.4));
				const ang = Math.atan2(dy, dx);
				const spikes = 0.18 * Math.max(0, Math.sin(ang * 7 + 1.3)) * Math.min(1, t * 3);
				const edge = d / (rad + spikes) + (nz - 0.5) * 0.45;
				const drop = t > 0.3 && hash(Math.floor((ang + 4) * 3), k, 5) > 0.6 && Math.abs(d - rad * 1.25) < 0.08;
				if (edge > 1 && !drop) continue;
				r = 24 + nz * 18; g = 8; b = 40 + nz * 40;
				a = (drop ? 0.9 : Math.min(1, (1 - edge) * 3)) * (1 - Math.max(0, t - 0.6) * 2.2) * 255;
			}
			put(im, k * f + x, y, r, g, b, Math.max(0, a));
		}
	}
	return im;
}
/** Ground decals, 64 x 64 (white so the renderer tints them; the alpha carries the shape). */
function decal(kind) {
	const n = 64, im = img(n, n), c = (n - 1) / 2;
	for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
		const dx = (x - c) / c, dy = (y - c) / c, d = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
		const nz = fbm(x / 9, y / 9, 61, 4);
		let a = 0;
		if (kind === 'scorch') {
			const e = d + (nz - 0.5) * 0.6;
			a = Math.max(0, 1 - e) * 1.7 * (0.75 + nz * 0.4);
		} else if (kind === 'crack') {
			// a web: jagged radial cracks plus broken rings
			let line = 0;
			for (let i = 0; i < 9; i++) {
				const ca = i / 9 * Math.PI * 2 + hash(i, 0, 3) * 0.5;
				let da = Math.abs(((ang - ca + Math.PI * 3) % (Math.PI * 2)) - Math.PI);
				const wob = (vnoise(d * 9, i, 9) - 0.5) * 0.16;
				da = Math.abs(da + wob) * d * c;
				if (da < 1.6 && d < 0.95 - hash(i, 1, 3) * 0.3) line = Math.max(line, 1 - da / 1.6);
			}
			for (const rr of [0.3, 0.58]) {
				const dr = Math.abs(d - rr - (vnoise(ang * 3, rr * 10, 4) - 0.5) * 0.08) * c;
				if (dr < 1.2 && hash(Math.floor((ang + 4) * 4), Math.floor(rr * 10), 8) > 0.35) line = Math.max(line, 1 - dr / 1.2);
			}
			a = line * (1 - Math.max(0, d - 0.75) * 3);
			if (d < 0.12) a = Math.max(a, 1 - d / 0.12);
		} else if (kind === 'pool') {
			const e = d + (nz - 0.5) * 0.35;
			a = Math.min(1, Math.max(0, 1 - e) * 2.5) * 0.95;
		} else {
			// print: five soft fingertip pads in an arc
			for (let i = 0; i < 5; i++) {
				const pa = -Math.PI * 0.85 + i * Math.PI * 0.175 + Math.PI * 0.075;
				const px = Math.cos(pa) * 0.55, py = Math.sin(pa) * 0.55 + 0.25;
				const rr = i === 0 ? 0.17 : 0.14;
				const dd = Math.hypot(dx - px, dy - py) / rr;
				a = Math.max(a, Math.pow(Math.max(0, 1 - dd), 0.6));
			}
		}
		put(im, x, y, 255, 255, 255, Math.min(1, Math.max(0, a)) * 255);
	}
	return im;
}

save(premul(glow()), FX, 'glow');
save(smoke(5), FX, 'smoke');
save(premul(ribbon()), FX, 'ribbon');
save(premul(flipbook('fireball')), FX, 'book_fireball');
save(flipbook('smoke'), FX, 'book_smoke');
save(flipbook('ink'), FX, 'book_ink');
for (const k of ['scorch', 'crack', 'pool', 'print']) save(k === 'print' ? premul(decal(k)) : decal(k), FX, 'decal_' + k);
{ const w = img(4, 4); w.px.fill(255); save(w, FX, 'white'); }

// ---------------- particle sprites (8 x 8, a few frames each; white so the particle tints them) ----------------
function sprite(fn, frames, name) {
	const names = [];
	for (let k = 0; k < frames; k++) {
		const im = img(8, 8);
		for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
			const o = fn(x - 3.5, y - 3.5, k / Math.max(1, frames - 1), x, y, k);
			if (o) put(im, x, y, o[0], o[1], o[2], o[3]);
		}
		save(im, PART, name + '_' + k);
		names.push('projecthero:' + name + '_' + k);
	}
	fs.writeFileSync(path.join(PDEF, name + '.json'), JSON.stringify({ textures: names }, null, 2) + '\n');
}
// ember: a bright core with a soft rim, shrinking frame by frame
sprite((x, y, t) => { const d = Math.hypot(x, y), r = 3.6 - t * 1.8; if (d > r) return null; const k = 1 - d / r; return [255, 255, 255, Math.min(1, k * 2.2) * 255]; }, 4, 'quirk_ember');
// spark: a short streak with a hot head
sprite((x, y, t) => { if (Math.abs(y) > 0.9 + (x > 1.5 ? 0.6 : 0)) return null; const along = (x + 3.5) / 7; return [255, 255, 255, (0.25 + 0.75 * along) * 255 * (1 - t * 0.3)]; }, 2, 'quirk_spark');
// smoke puff: a lumpy soft disc
sprite((x, y, t, px, py, k) => { const d = Math.hypot(x, y) / 3.9 + (hash(px, py, 90 + k) - 0.5) * 0.35; if (d > 1) return null; const v = 205 + hash(px, py, k) * 40; return [v, v, v, Math.min(1, (1 - d) * 1.6) * 210]; }, 4, 'quirk_smoke_puff');
// shadow mote: a wispy dark dot with a violet edge
sprite((x, y, t) => { const d = Math.hypot(x, y) / (3.8 - t); if (d > 1) return null; return [40 + d * 90, 10 + d * 20, 70 + d * 130, (1 - d * d) * 240]; }, 3, 'quirk_shadow_mote');
// steam: a pale wisp, stretched upward
sprite((x, y, t, px, py, k) => { const d = Math.hypot(x * 1.2, y * 0.8) / 3.9 + (hash(px, py, 50 + k) - 0.5) * 0.4; if (d > 1) return null; return [235, 240, 245, Math.min(1, (1 - d) * 1.3) * 180]; }, 4, 'quirk_steam');
// pink sparkle: a four-pointed twinkle
sprite((x, y, t) => { const ax = Math.abs(x), ay = Math.abs(y); const arm = (ax < 0.6 && ay < 3.6 - t) || (ay < 0.6 && ax < 3.6 - t); const core = Math.hypot(x, y) < 1.4; if (!arm && !core) return null; return [255, 255, 255, core ? 255 : 200]; }, 3, 'quirk_pink_sparkle');
// dust chunk: a small angular grit lump
sprite((x, y, t, px, py, k) => { const r = 2.4 + hash(k, 0, 7) * 0.8; if (Math.abs(x) + Math.abs(y) * 1.2 > r + hash(px, py, k) * 0.8) return null; const v = 150 + hash(px, py, 20 + k) * 90; return [v, v * 0.94, v * 0.86, 255]; }, 3, 'quirk_dust_chunk');
