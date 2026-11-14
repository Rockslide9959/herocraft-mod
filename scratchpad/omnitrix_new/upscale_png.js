// node upscale_png.js <in.png> <out.png> <factor> -- nearest-neighbour upscale with a checker behind transparency and a faint 8 px grid
'use strict';
const fs = require('fs');
const { encodePng, decodePng } = require('../lib_png.js');
const [, , inp, out, fs_] = process.argv;
const k = parseInt(fs_ || '8', 10);
const img = decodePng(fs.readFileSync(inp));
const W = img.w * k, H = img.h * k;
const px = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		const sx = Math.floor(x / k), sy = Math.floor(y / k);
		const i = (sy * img.w + sx) * 4;
		const a = img.px[i + 3] / 255;
		const chk = ((Math.floor(x / 8) + Math.floor(y / 8)) & 1) ? 200 : 235;
		const grid = (sx % 8 === 0 && x % k === 0) || (sy % 8 === 0 && y % k === 0);
		const o = (y * W + x) * 4;
		for (let c = 0; c < 3; c++) px[o + c] = grid ? 120 : Math.round(img.px[i + c] * a + chk * (1 - a));
		px[o + 3] = 255;
	}
}
fs.writeFileSync(out, encodePng(W, H, px));
