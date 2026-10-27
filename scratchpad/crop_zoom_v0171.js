// v0.17.1: crop_zoom.js against the current pnglib API ({W, H, px}).
//   node scratchpad/crop_zoom_v0171.js <in.png> <out.png> <x> <y> <w> <h> <scale>
const png = require('./pnglib.js');
const [inp, out, x, y, w, h, s] = process.argv.slice(2);
const img = png.read(inp);
const X = +x, Y = +y, W = +w, H = +h, S = +s;
const data = Buffer.alloc(W * S * H * S * 4);
for (let j = 0; j < H * S; j++) {
	for (let i = 0; i < W * S; i++) {
		const sx = Math.min(img.W - 1, X + Math.floor(i / S)), sy = Math.min(img.H - 1, Y + Math.floor(j / S));
		img.px.copy(data, (j * W * S + i) * 4, (sy * img.W + sx) * 4, (sy * img.W + sx) * 4 + 4);
	}
}
png.write(out, W * S, H * S, data);
