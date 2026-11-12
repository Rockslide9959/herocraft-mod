// node scratchpad/doctorfate/preview_skin.js <skin.png> <out.png> [scale] -- upscaled skin sheet on a checkerboard with an 8 px grid
'use strict';
const P = require('../pnglib.js');
const [, , inp, out, sc] = process.argv;
const S = +(sc || 12);
const r = P.read(inp), W = r.W * S, H = r.H * S, o = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const sx = x / S | 0, sy = y / S | 0, i = (sy * r.W + sx) * 4, d = (y * W + x) * 4;
	const chk = ((x >> 2) + (y >> 2)) & 1 ? 120 : 90, a = r.px[i + 3] / 255;
	for (let k = 0; k < 3; k++) o[d + k] = Math.round(r.px[i + k] * a + chk * (1 - a));
	if (x % (S * 4) === 0 || y % (S * 4) === 0) { o[d] = 255; o[d + 1] = 0; o[d + 2] = 255; }
	o[d + 3] = 255;
}
P.write(out, W, H, o);
