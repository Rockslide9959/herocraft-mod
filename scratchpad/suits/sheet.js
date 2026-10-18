// node sheet.js <out.png> [scale] [subset...] -> contact sheet of the suit PNGs (2 per row: slim | wide), checkerboard behind
// transparency, magenta 8-texel grid, and every transparent texel inside a BASE-layer face marked red (holes) so gaps pop.
'use strict';
const T = require('./skin_tools.js'), path = require('path'), fs = require('fs');
const C = require('./check.js');
const out = process.argv[2], S = +(process.argv[3] || 6), only = process.argv.slice(4);
const list = C.SUITS.filter(([n]) => !only.length || only.some(o => n.startsWith(o)));
const cols = 2, rows = Math.ceil(list.length / cols), G = 8, W = cols * (64 * S + G), H = rows * (64 * S + G);
const px = Buffer.alloc(W * H * 4, 255);
const baseFace = (slim) => { const P = C.parts(slim); const m = new Uint8Array(64 * 64);
	for (const pn of ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']) for (const [fx, fy, fw, fh] of Object.values(P[pn])) for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) m[y * 64 + x] = 1; return m; };
list.forEach(([n, slim], k) => {
	const im = T.decode(path.join(C.A, n + '.png')), m = baseFace(!!slim);
	const ox = (k % cols) * (64 * S + G), oy = Math.floor(k / cols) * (64 * S + G);
	for (let y = 0; y < 64 * S; y++) for (let x = 0; x < 64 * S; x++) {
		const sx = x / S | 0, sy = y / S | 0, si = (sy * 64 + sx) * 4, d = ((oy + y) * W + ox + x) * 4, a = im.px[si + 3] / 255;
		const chk = ((x >> 3) + (y >> 3)) & 1 ? 200 : 170;
		let c = [0, 1, 2].map(i => Math.round(im.px[si + i] * a + chk * (1 - a)));
		if (a === 0 && m[sy * 64 + sx] && !n.includes('xx') && !(sy < 16)) c = [255, 40, 40]; // body / limb base hole (the head is optional)
		if (x % (S * 8) === 0 || y % (S * 8) === 0) c = [255, 0, 255];
		px[d] = c[0]; px[d + 1] = c[1]; px[d + 2] = c[2]; px[d + 3] = 255;
	}
});
fs.writeFileSync(out, T.encode(W, H, px));
console.log(list.map(([n]) => n).join(', '));
