// node sheet.js <out.png> <scale> <png>... : the PNGs side by side, scaled up, on a checkerboard (for looking at icons)
'use strict';
const fs = require('fs');
const { encodePng, decodePng } = require('./lib_png');
const [out, sc, ...files] = process.argv.slice(2);
const S = +sc;
const ims = files.map(f => decodePng(fs.readFileSync(f)));
const W = ims.reduce((a, i) => a + i.w * S + 8, 8), H = Math.max(...ims.map(i => i.h * S)) + 16;
const P = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const o = (y * W + x) * 4, c = ((x >> 3) + (y >> 3)) & 1 ? 120 : 90; P[o] = P[o + 1] = P[o + 2] = c; P[o + 3] = 255; }
let ox = 8;
for (const im of ims) {
	for (let y = 0; y < im.h * S; y++) for (let x = 0; x < im.w * S; x++) {
		const i = ((y / S | 0) * im.w + (x / S | 0)) * 4, a = im.px[i + 3] / 255, o = ((y + 8) * W + ox + x) * 4;
		for (let k = 0; k < 3; k++) P[o + k] = Math.round(im.px[i + k] * a + P[o + k] * (1 - a));
	}
	ox += im.w * S + 8;
}
fs.writeFileSync(out, encodePng(W, H, P));
