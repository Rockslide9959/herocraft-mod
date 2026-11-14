// v0.17.4 (user: "give blitzwolfer a bushy wolf tail please, after doing this remove the [tail] from the back of his skin please"):
// paints out the tail drawn on the back of Benwolf's skin (an outlined dark flame shape on the torso's back, texels x 32..39,
// y 22..30 on the base layer and the same shape on the jacket layer, y 38..46) now that BenwolfClient draws a real 3D tail.
// The base layer gets the back's own fur: each row's grey fur texels (rows 22..26) or its black / white stripe (rows 27..30)
// carried across; the black mane coming down from the head keeps a short rounded tip (row 22). The jacket layer's tail texels go
// transparent (the fur on the base shows). Writes a before / after back-view sheet.
//   node scratchpad/omnitrix_new/benwolf_remove_tail.js [--sheet <out.png>]
// Idempotent: it reads scratchpad/omnitrix_new/benwolf_before_tail_removal.png when that exists (the original), else saves it first.
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng, decodePng } = require('../lib_png.js');

const SKIN = 'src/main/resources/assets/projecthero/textures/entity/omnitrix/benwolf.png';
const ORIG = path.join(__dirname, 'benwolf_before_tail_removal.png');
if (!fs.existsSync(ORIG)) fs.copyFileSync(SKIN, ORIG);
const src = decodePng(fs.readFileSync(ORIG));
const W = src.w;
const px = Buffer.from(src.px);
const at = (x, y) => (y * W + x) * 4;
const lum = (x, y) => { const i = at(x, y); return px[i + 3] ? (px[i] + px[i + 1] + px[i + 2]) / 3 : -1; };
const set = (x, y, c) => { const i = at(x, y); px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = c[3] === undefined ? 255 : c[3]; };
const get = (x, y) => { const i = at(x, y); return [px[i], px[i + 1], px[i + 2], px[i + 3]]; };

// the tail's texels on the back face (x 32..39): rows 22..30, everything that is the outline (black) or its dark fill between
// the outlines, found row by row from the original
const X0 = 32, X1 = 39;
function tailSpan(y) {
	let a = -1, b = -1;
	for (let x = X0; x <= X1; x++) {
		const v = (src.px[at(x, y)] + src.px[at(x, y) + 1] + src.px[at(x, y) + 2]) / 3;
		if (src.px[at(x, y) + 3] && v < 2) { // the outline
			if (a < 0) a = x;
			b = x;
		}
	}
	return a < 0 ? null : [a, b];
}
// per row: the fur to carry across -- the texels outside the tail span that are fur (grey) or the stripe (white / black)
let hash = 7;
const rnd = () => { hash = (hash * 1103515245 + 12345) & 0x7fffffff; return hash / 0x7fffffff; };
for (let y = 22; y <= 30; y++) {
	const span = tailSpan(y);
	if (!span) continue;
	const pool = [];
	for (let x = X0; x <= X1; x++) {
		if (x >= span[0] && x <= span[1]) continue;
		const v = lum(x, y);
		if (v > 30) pool.push(get(x, y));
	}
	// the stripe rows (27, 29: black; 28, 30: white) and the grey rows; with no fur outside, borrow the row above's
	const dark = y === 27 || y === 29;
	for (let x = span[0]; x <= span[1]; x++) {
		let c;
		if (dark) {
			c = get(X0, y); // the black stripe's own black
		} else if (pool.length) {
			c = pool[Math.floor(rnd() * pool.length)];
		} else {
			c = get(x, y - 1);
		}
		set(x, y, c);
	}
}
// the mane from the head: a short rounded tip at row 22 (two texels), its black from row 21
const mane = get(35, 21);
set(35, 22, mane);
set(36, 22, mane);

// the jacket layer (y + 16): the tail's texels go transparent; its grey fur at the edges stays
for (let y = 22; y <= 30; y++) {
	const span = tailSpan(y);
	if (!span) continue;
	for (let x = span[0]; x <= span[1]; x++) {
		const jy = y + 16;
		const v = lum(x, jy);
		if (v >= 0 && v < 40) set(x, jy, [0, 0, 0, 0]);
	}
}
fs.writeFileSync(SKIN, encodePng(W, src.h, px));
console.log('wrote', SKIN);

// before / after: the back of the torso (base + jacket) with the head's back above, 24x
const sheetArg = process.argv.indexOf('--sheet');
if (sheetArg > 0) {
	const out = process.argv[sheetArg + 1];
	const K = 24, PW = 8, PH = 20, GAP = 2;
	const SW = (PW * 2 + GAP) * K, SH = PH * K;
	const sheet = Buffer.alloc(SW * SH * 4, 255);
	const draw = (img, ox) => {
		const comp = Buffer.alloc(PW * PH * 4);
		const blit = (sx, sy, w, h, dx, dy) => {
			for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
				const i = ((sy + y) * W + sx + x) * 4;
				if (!img[i + 3]) continue;
				const o = ((dy + y) * PW + dx + x) * 4;
				for (let c = 0; c < 4; c++) comp[o + c] = img[i + c];
			}
		};
		blit(24, 8, 8, 8, 0, 0); blit(56, 8, 8, 8, 0, 0); // head back + hat
		blit(32, 20, 8, 12, 0, 8); blit(32, 36, 8, 12, 0, 8); // torso back + jacket
		for (let y = 0; y < PH * K; y++) for (let x = 0; x < PW * K; x++) {
			const i = (Math.floor(y / K) * PW + Math.floor(x / K)) * 4;
			const a = comp[i + 3] / 255;
			const o = (y * SW + ox + x) * 4;
			for (let c = 0; c < 3; c++) sheet[o + c] = Math.round(comp[i + c] * a + 225 * (1 - a));
			sheet[o + 3] = 255;
		}
	};
	draw(src.px, 0);
	draw(px, (PW + GAP) * K);
	fs.writeFileSync(out, encodePng(SW, SH, sheet));
	console.log('sheet', out);
}
