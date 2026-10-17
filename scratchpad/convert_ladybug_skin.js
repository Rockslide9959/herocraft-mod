// v0.16.6 Ladybug: the user's chosen skin (minecraftskins.com 23799204, a palette PNG) -> 32-bit RGBA suit texture,
// plus an 8x preview and a report of whether the arms are slim (3 px) or wide (4 px).
// node convert_ladybug_skin.js <src.png> <out.png> [preview.png]
'use strict';
const fs = require('fs');
const { encodePng, decodePng } = require('./lib_png');

const [src, out, preview] = process.argv.slice(2);
const img = decodePng(fs.readFileSync(src));
if (img.w !== 64 || img.h !== 64) throw new Error('expected 64x64, got ' + img.w + 'x' + img.h);
fs.writeFileSync(out, encodePng(64, 64, img.px));
const a = (x, y) => img.px[(y * 64 + x) * 4 + 3];
// right arm front face is x 44..47, y 20..31: column 47 empty on a slim skin
let col47 = 0, col54 = 0;
for (let y = 20; y < 32; y++) { col47 += a(47, y) > 0 ? 1 : 0; }
for (let y = 16; y < 20; y++) { col54 += a(54, y) > 0 ? 1 : 0; }
console.log('right arm column 47 opaque rows:', col47, ' top-cap column 54:', col54, col47 === 0 ? '=> SLIM' : '=> WIDE');
let overlay = 0;
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
	const ov = (y < 16 && x >= 32) || (y >= 32 && y < 48) || (y >= 48 && (x < 16 || x >= 48));
	if (ov && a(x, y) > 0) overlay++;
}
console.log('overlay texels:', overlay);
if (preview) {
	const S = 8, P = Buffer.alloc(64 * S * 64 * S * 4);
	for (let y = 0; y < 64 * S; y++) for (let x = 0; x < 64 * S; x++) {
		const o = (y * 64 * S + x) * 4, i = ((y / S | 0) * 64 + (x / S | 0)) * 4;
		const chk = ((x >> 3) + (y >> 3)) & 1 ? 200 : 160; const al = img.px[i + 3] / 255;
		for (let k = 0; k < 3; k++) P[o + k] = Math.round(img.px[i + k] * al + chk * (1 - al));
		P[o + 3] = 255;
	}
	fs.writeFileSync(preview, encodePng(64 * S, 64 * S, P));
}
