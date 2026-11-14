// node frame_sheet.js <dir> <prefix> <out.png> <first> <count> <step> [cols] -- a half-size contact sheet of film frames <prefix>NNN.png
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng, decodePng } = require('../lib_png.js');
const [, , dir, prefix, out, first, count, step, cols_] = process.argv;
const cols = +(cols_ || 4), n = +count;
const rows = Math.ceil(n / cols);
let cw = 0, ch = 0, buf = null;
for (let k = 0; k < n; k++) {
	const f = +first + k * +step;
	const im = decodePng(fs.readFileSync(path.join(dir, prefix + String(f).padStart(3, '0') + '.png')));
	if (!buf) {
		cw = im.w >> 1;
		ch = im.h >> 1;
		buf = Buffer.alloc(cw * cols * ch * rows * 4, 255);
	}
	const ox = (k % cols) * cw, oy = Math.floor(k / cols) * ch;
	for (let y = 0; y < ch; y++) for (let x = 0; x < cw; x++) {
		const si = ((y * 2) * im.w + x * 2) * 4, di = ((oy + y) * cw * cols + ox + x) * 4;
		for (let c = 0; c < 4; c++) buf[di + c] = im.px[si + c];
		buf[di + 3] = 255;
	}
}
fs.writeFileSync(out, encodePng(cw * cols, ch * rows, buf));
