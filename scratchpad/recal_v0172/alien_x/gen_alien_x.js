// v0.17.2 Recalibrated Omnitrix: Alien X (Celestialsapien) -- a basic body: the player model with a generated 64x64 WIDE skin,
// a few AlienParts.box extras (the white horns) and glow passes drawn with RenderType.eyes.
//   * entity/omnitrix/alien_x.png         the skin: black, a starfield all over (faint blue / violet nebula), green eyes
//   * entity/omnitrix/alien_x_stars.png   the stars only (additive pass; black = nothing) -- the client pulses it
//   * entity/omnitrix/alien_x_eyes.png    the eyes only (additive, full bright)
//   * entity/omnitrix/alien_x_extras.png  horns (0,0)-(16,16): ivory, shaded; horn tips (16,0)-(24,8): white;
//                                         starfield patch (32,0)-(64,32): dense stars + nebula for the rifts / beams (additive)
//                                         dark core (0,32)-(16,48): star-black for the rift's solid backing
// Usage: node scratchpad/recal_v0172/alien_x/gen_alien_x.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../../pnglib.js');

const TEX = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
const PREVIEW = process.argv[2];

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function clamp(v) { return Math.max(0, Math.min(255, Math.round(v))); }
function put(im, x, y, c, a = 255) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = clamp(c[0]); im.px[i + 1] = clamp(c[1]); im.px[i + 2] = clamp(c[2]); im.px[i + 3] = a; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function rect(im, x, y, w, h, fn) { for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) { const c = fn(u, v, w, h, x + u, y + v); if (c) put(im, x + u, y + v, c.slice(0, 3), c[3] === undefined ? 255 : c[3]); } }
function save(im, name) {
	fs.mkdirSync(TEX, { recursive: true });
	PNG.write(path.join(TEX, name), im.w, im.h, im.px);
	if (PREVIEW) {
		const s = 8;
		const p = img(im.w * s, im.h * s);
		for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
			const c = get(im, Math.floor(x / s), Math.floor(y / s));
			const chk = ((Math.floor(x / 8) + Math.floor(y / 8)) % 2) ? 90 : 60;
			const a = c[3] / 255;
			put(p, x, y, [0, 1, 2].map(k => Math.round(c[k] * a + chk * (1 - a))));
		}
		fs.mkdirSync(PREVIEW, { recursive: true });
		PNG.write(path.join(PREVIEW, name), p.w, p.h, p.px);
	}
}
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
// only the base layer: the overlay (hat / jacket / sleeves / pants) stays empty
const SKIN = {
	head: faces(0, 0, 8, 8, 8), body: faces(16, 16, 8, 12, 4),
	rArm: faces(40, 16, 4, 12, 4), lArm: faces(32, 48, 4, 12, 4),
	rLeg: faces(0, 16, 4, 12, 4), lLeg: faces(16, 48, 4, 12, 4),
};

// the starfield: a near-black base with a soft blue / violet nebula and scattered stars (some bright, a few with a cross)
const BASE = hex('#04050b');
const NEB_A = hex('#1a2a6a');
const NEB_B = hex('#3a1660');
function nebula(x, y) {
	// two blobby low-frequency fields from hashed lattice noise
	const n = (sx, sy, seed) => {
		const fx = x / sx, fy = y / sy, ix = Math.floor(fx), iy = Math.floor(fy), tx = fx - ix, ty = fy - iy;
		const s = t => t * t * (3 - 2 * t);
		const a = hash(ix, iy, seed), b = hash(ix + 1, iy, seed), c = hash(ix, iy + 1, seed), d = hash(ix + 1, iy + 1, seed);
		return (a + (b - a) * s(tx)) + ((c + (d - c) * s(tx)) - (a + (b - a) * s(tx))) * s(ty);
	};
	return [Math.max(0, n(9, 7, 11) - 0.55) * 1.6, Math.max(0, n(7, 10, 23) - 0.6) * 1.6];
}
function starAt(x, y, seed, density) {
	const r = hash(x, y, seed);
	if (r > density) return null;
	const k = r / density; // 0 brightest
	const tint = hash(x, y, seed + 5);
	const col = tint < 0.2 ? hex('#bcd6ff') : tint < 0.3 ? hex('#ffe9c8') : hex('#ffffff');
	return { col, b: k < 0.18 ? 1.0 : k < 0.55 ? 0.75 : 0.45, big: k < 0.08 };
}

const skin = img(64, 64);
const stars = img(64, 64);
const eyes = img(64, 64);

for (const [part, box] of Object.entries(SKIN)) {
	for (const [face, f] of Object.entries(box)) {
		rect(skin, f[0], f[1], f[2], f[3], (u, v, w, h, x, y) => {
			const [na, nb] = nebula(x, y);
			let c = [0, 1, 2].map(i => BASE[i] + NEB_A[i] * na * 0.55 + NEB_B[i] * nb * 0.5);
			// the edges of each face a touch darker, so the limbs read as shapes
			if ((u === 0 || u === w - 1 || v === h - 1) && face !== 'top') c = c.map(q => q * 0.7);
			return c;
		});
		// stars
		const density = part === 'head' ? 0.07 : 0.1;
		for (let v = 0; v < f[3]; v++) for (let u = 0; u < f[2]; u++) {
			const x = f[0] + u, y = f[1] + v;
			const s = starAt(x, y, 7, density);
			if (!s) continue;
			const c = s.col.map(q => q * s.b);
			put(skin, x, y, c.map((q, i) => Math.max(q, get(skin, x, y)[i])));
			put(stars, x, y, c);
			if (s.big) {
				for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
					if (u + dx < 0 || v + dy < 0 || u + dx >= f[2] || v + dy >= f[3]) continue;
					const cc = s.col.map(q => q * 0.35);
					put(skin, x + dx, y + dy, cc.map((q, i) => Math.max(q, get(skin, x + dx, y + dy)[i])));
					put(stars, x + dx, y + dy, cc.map((q, i) => Math.max(q, get(stars, x + dx, y + dy)[i])));
				}
			}
		}
	}
}

// the face: two slanted green eyes (no mouth), clear of stars
{
	const [fx, fy] = SKIN.head.front;
	rect(skin, fx + 1, fy + 2, 6, 4, (u, v, w, h, x, y) => [4, 5, 11]);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (x >= fx + 1 && x < fx + 7 && y >= fy + 2 && y < fy + 6) put(stars, x, y, [0, 0, 0], 0);
	const CORE = hex('#b8ffaa'), MID = hex('#5cf04a'), RIM = hex('#1f9a22');
	const eye = [[1, 3, MID], [2, 3, CORE], [2, 4, MID], [1, 2, RIM], [6, 3, MID], [5, 3, CORE], [5, 4, MID], [6, 2, RIM]];
	for (const [u, v, c] of eye) {
		put(skin, fx + u, fy + v, c);
		put(eyes, fx + u, fy + v, c);
	}
}

save(skin, 'alien_x.png');
save(stars, 'alien_x_stars.png');
save(eyes, 'alien_x_eyes.png');

// the extras sheet
const ex = img(64, 64);
const IVORY = hex('#f2f0e6'), SHADE = hex('#b9b6aa');
rect(ex, 0, 0, 16, 16, (u, v) => {
	const t = v / 15;
	const n = hash(u, v, 3) * 0.06;
	return [0, 1, 2].map(i => IVORY[i] + (SHADE[i] - IVORY[i]) * (t * 0.7 + n) + (u === 0 || u === 15 ? -25 : 0));
});
rect(ex, 16, 0, 8, 8, () => [255, 255, 255]);
rect(ex, 32, 0, 32, 32, (u, v, w, h, x, y) => {
	const [na, nb] = nebula(x * 1.7, y * 1.7);
	let c = [0, 1, 2].map(i => NEB_A[i] * na * 0.9 + NEB_B[i] * nb * 0.8);
	const s = starAt(x, y, 41, 0.12);
	if (s) c = c.map((q, i) => Math.max(q, s.col[i] * s.b));
	return c;
});
rect(ex, 0, 32, 16, 16, (u, v, w, h, x, y) => {
	const s = starAt(x, y, 77, 0.08);
	return s ? s.col.map(q => q * s.b * 0.8) : [3, 4, 9];
});
save(ex, 'alien_x_extras.png');
console.log('alien_x textures written to', TEX);
