// node scratchpad/gyro/upscale.js in.png out.png factor  (nearest-neighbour, on a checker so transparency shows)
const png = require('../pnglib.js');
const [, , inp, out, f0] = process.argv;
const f = +f0 || 8;
const { W, H, px } = png.read(inp);
const o = Buffer.alloc(W * f * H * f * 4);
for (let y = 0; y < H * f; y++) for (let x = 0; x < W * f; x++) {
  const i = (Math.floor(y / f) * W + Math.floor(x / f)) * 4, j = (y * W * f + x) * 4, a = px[i + 3] / 255;
  const bg = ((x >> 3) + (y >> 3)) % 2 ? 200 : 160;
  for (let c = 0; c < 3; c++) o[j + c] = Math.round(px[i + c] * a + bg * (1 - a));
  o[j + 3] = 255;
}
png.write(out, W * f, H * f, o);
