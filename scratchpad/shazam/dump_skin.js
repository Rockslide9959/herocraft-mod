// Dumps a 64x64 skin: an upscaled grid image (x12, grid line every texel, darker every 8) and a per-texel colour key in text.
// node scratchpad/shazam/dump_skin.js in.png out_prefix
'use strict';
const fs = require('fs');
const PNG = require('../pnglib.js');
const [, , inp, outp] = process.argv;
const s = PNG.read(inp);
const S = 12, W = s.W * S, H = s.H * S;
const px = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const sx = Math.floor(x / S), sy = Math.floor(y / S), i = (sy * s.W + sx) * 4, o = (y * W + x) * 4;
	const a = s.px[i + 3];
	const chk = ((sx + sy) & 1) ? 200 : 160;
	for (let k = 0; k < 3; k++) px[o + k] = a ? s.px[i + k] : chk;
	px[o + 3] = 255;
	if (x % S === 0 || y % S === 0) { const g = (x % (S * 8) === 0 || y % (S * 8) === 0) ? 0 : 90; px[o] = px[o + 1] = px[o + 2] = g; }
}
PNG.write(outp + '_x12.png', W, H, px);
const pal = new Map(); const rows = [];
for (let y = 0; y < s.H; y++) {
	let r = '';
	for (let x = 0; x < s.W; x++) {
		const i = (y * s.W + x) * 4;
		if (!s.px[i + 3]) { r += ' .'; continue; }
		const key = [s.px[i], s.px[i + 1], s.px[i + 2], s.px[i + 3]].join(',');
		if (!pal.has(key)) pal.set(key, pal.size < 26 ? String.fromCharCode(65 + pal.size) : pal.size < 52 ? String.fromCharCode(97 + pal.size - 26) : String.fromCharCode(48 + pal.size - 52));
		r += ' ' + pal.get(key);
	}
	rows.push(String(y).padStart(2) + r);
}
let txt = '   ' + [...Array(s.W).keys()].map(i => ' ' + (i % 10)).join('') + '\n' + rows.join('\n') + '\n';
for (const [k, v] of pal) txt += v + ' = ' + k + '\n';
fs.writeFileSync(outp + '_grid.txt', txt);
