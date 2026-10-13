// Crops a region of a PNG and scales it up (nearest neighbour) -- for inspecting small icons in harness shots.
//   node scratchpad/crop_zoom.js <in.png> <out.png> <x> <y> <w> <h> <scale>
const fs = require('fs');
const png = require('./pnglib.js');
const [inp, out, x, y, w, h, s] = process.argv.slice(2);
const img = png.read(inp);
const X = +x, Y = +y, W = +w, H = +h, S = +s;
const data = Buffer.alloc(W * S * H * S * 4);
for (let j = 0; j < H * S; j++) for (let i = 0; i < W * S; i++) {
	const sx = Math.min(img.w - 1, X + Math.floor(i / S)), sy = Math.min(img.h - 1, Y + Math.floor(j / S));
	img.data.copy(data, (j * W * S + i) * 4, (sy * img.w + sx) * 4, (sy * img.w + sx) * 4 + 4);
}
png.write(out, { w: W * S, h: H * S, data });
