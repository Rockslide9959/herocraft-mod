// crop + nearest-neighbour upscale a PNG: node crop.js in.png out.png x y w h scale
'use strict';
const fs = require('fs');
const T = require('./skin_tools.js');
const [inp, out, x0, y0, w, h, sc] = process.argv.slice(2);
const img = T.decode(inp);
const X = +x0, Y = +y0, W = +w, H = +h, S = +(sc || 3);
const px = Buffer.alloc(W * S * H * S * 4);
for (let y = 0; y < H * S; y++) for (let x = 0; x < W * S; x++) {
	const sx = Math.min(img.w - 1, X + Math.floor(x / S)), sy = Math.min(img.h - 1, Y + Math.floor(y / S));
	img.px.copy(px, (y * W * S + x) * 4, (sy * img.w + sx) * 4, (sy * img.w + sx) * 4 + 4);
	px[(y * W * S + x) * 4 + 3] = 255;
}
fs.writeFileSync(out, T.encode(W * S, H * S, px));
