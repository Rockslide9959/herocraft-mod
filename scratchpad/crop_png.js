// Crop (and optionally nearest-neighbour scale) a PNG: node scratchpad/crop_png.js in.png out.png x y w h [scale]
'use strict';
const PNG = require('./pnglib.js');
const [, , inp, out, xs, ys, ws, hs, ss] = process.argv;
const x0 = +xs, y0 = +ys, w = +ws, h = +hs, s = +(ss || 1);
const src = PNG.read(inp);
const px = Buffer.alloc(w * s * h * s * 4);
for (let y = 0; y < h * s; y++) for (let x = 0; x < w * s; x++) {
	const sx = x0 + Math.floor(x / s), sy = y0 + Math.floor(y / s);
	const i = (sy * src.W + sx) * 4, o = (y * w * s + x) * 4;
	src.px.copy(px, o, i, i + 4);
	px[o + 3] = 255;
}
PNG.write(out, w * s, h * s, px);
