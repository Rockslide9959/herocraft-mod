// node scratchpad/doctorfate/dump_px.js <png> x y w h -- hex dump of a texel rectangle ("......" = transparent)
'use strict';
const P = require('../pnglib.js');
const r = P.read(process.argv[2]);
const [x0, y0, w, h] = process.argv.slice(3).map(Number);
let hdr = '   ';
for (let x = x0; x < x0 + w; x++) hdr += String(x).padStart(6) + ' ';
console.log(hdr);
for (let y = y0; y < y0 + h; y++) {
	let s = String(y).padStart(2) + ' ';
	for (let x = x0; x < x0 + w; x++) {
		const i = (y * r.W + x) * 4;
		s += r.px[i + 3] ? r.px.slice(i, i + 3).toString('hex') + ' ' : '...... ';
	}
	console.log(s);
}
