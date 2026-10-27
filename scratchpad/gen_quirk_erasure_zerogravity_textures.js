// v0.17.1 Erasure + Zero Gravity revamp: the textures the two kits added -- Aizawa's Sleeping Bag (the 16x16 item icon, a
// rolled yellow bag with its straps, and the 64x64 quilted cloth the bag is drawn with round a sleeper).
// Usage, from the repo root: node scratchpad/gen_quirk_erasure_zerogravity_textures.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encode } = require('./suits/skin_tools.js');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..'));
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures');
const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, Math.round(v)));
const hash = (x, y, s = 0) => { let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function put(im, x, y, c, a = 255) {
	if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const i = (y * im.w + x) * 4; im.px[i] = clamp(c[0]); im.px[i + 1] = clamp(c[1]); im.px[i + 2] = clamp(c[2]); im.px[i + 3] = clamp(a);
}
function save(im, rel) {
	const f = path.join(TEX, rel + '.png');
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, encode(im.w, im.h, im.px));
	console.log(rel, im.w + 'x' + im.h);
}
const shade = (c, k) => [c[0] * k, c[1] * k, c[2] * k];

// the anime bag's warm mustard yellow, its seams and the dark grey straps
const YEL = [236, 196, 58], YEL_HI = [250, 224, 120], YEL_LO = [176, 134, 30], SEAM = [128, 92, 22];
const STRAP = [54, 54, 60], STRAP_HI = [92, 92, 100];

/** The quilted cloth: puffy horizontal channels (light in the middle, dark at the stitched seams), a little fabric noise. */
function quilt() {
	const im = img(64, 64);
	for (let y = 0; y < 64; y++) {
		const ch = y % 6; // a channel every 6 px
		const puff = ch === 0 ? 0.62 : 0.82 + 0.18 * Math.sin(Math.PI * ch / 6);
		for (let x = 0; x < 64; x++) {
			const n = (hash(x, y, 3) - 0.5) * 0.08;
			let c = ch === 0 ? SEAM : shade(YEL, puff + n);
			if (ch === 2 && hash(x, y, 9) > 0.93) c = YEL_HI;
			// the zip down one side
			if (x === 31 || x === 32) c = x === 31 ? [210, 210, 214] : [120, 120, 126];
			put(im, x, y, c);
		}
	}
	return im;
}

/** The item: the bag rolled up and strapped -- seen side on, the spiral of the roll at the left end. */
function icon() {
	const im = img(16, 16);
	const x0 = 2, x1 = 14, y0 = 4, y1 = 12; // the roll's body
	for (let y = y0; y <= y1; y++) {
		for (let x = x0; x <= x1; x++) {
			const v = (y - y0) / (y1 - y0); // 0 top .. 1 bottom
			let k = 1.05 - 0.45 * Math.pow(v, 1.4) + (v < 0.2 ? 0.12 : 0);
			if ((x === x0 || x === x1) && (y === y0 || y === y1)) continue; // rounded corners
			let c = shade(YEL, k);
			if ((x - x0) % 3 === 0 && x > x0 + 2) c = shade(YEL_LO, 0.95 + 0.2 * (1 - v)); // the quilt seams
			put(im, x, y, c);
		}
	}
	// outline
	for (let x = x0 + 1; x < x1; x++) { put(im, x, y0 - 1, SEAM); put(im, x, y1 + 1, shade(SEAM, 0.8)); }
	for (let y = y0 + 1; y < y1; y++) { put(im, x1 + 1, y, shade(SEAM, 0.85)); }
	put(im, x0, y0, SEAM); put(im, x1, y0, SEAM); put(im, x0, y1, SEAM); put(im, x1, y1, SEAM);
	// the end of the roll: a spiral
	const cx = x0 + 1.5, cy = (y0 + y1) / 2;
	for (let y = y0 - 1; y <= y1 + 1; y++) {
		for (let x = x0 - 1; x <= x0 + 4; x++) {
			const dx = (x - cx) / 2.6, dy = (y - cy) / 4.6;
			const d = Math.hypot(dx, dy);
			if (d > 1.0) continue;
			const a = Math.atan2(dy, dx);
			const spiral = ((d * 3.2 + a / (2 * Math.PI)) % 1 + 1) % 1;
			put(im, x, y, spiral < 0.35 ? SEAM : spiral < 0.6 ? YEL_LO : YEL_HI);
		}
	}
	// two straps
	for (const sx of [6, 11]) {
		for (let y = y0 - 1; y <= y1 + 1; y++) {
			put(im, sx, y, y === y0 - 1 ? STRAP_HI : STRAP);
			put(im, sx + 1, y, shade(STRAP, 0.75));
		}
		put(im, sx, y1 - 2, [170, 170, 176]); // the buckle
	}
	return im;
}

save(quilt(), 'entity/quirk/sleeping_bag');
save(icon(), 'item/sleeping_bag');
