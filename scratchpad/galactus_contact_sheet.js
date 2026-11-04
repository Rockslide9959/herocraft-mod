// contact sheet: node sheet.js <dir> <out.png> <step> <cols>
const fs = require('fs'); const path = require('path');
const { encodePng, decodePng } = require(process.env.LIBPNG);
const [dir, out, step, cols] = [process.argv[2], process.argv[3], +process.argv[4], +process.argv[5]];
const files = fs.readdirSync(dir).filter(f => f.endsWith('.png')).sort().filter((f, i) => i % step === 0);
const imgs = files.map(f => decodePng(fs.readFileSync(path.join(dir, f))));
const sc = 3; const w = Math.floor(imgs[0].w / sc), h = Math.floor(imgs[0].h / sc);
const rows = Math.ceil(imgs.length / cols); const W = w * cols, H = h * rows; const px = Buffer.alloc(W * H * 4);
imgs.forEach((im, k) => { const ox = (k % cols) * w, oy = Math.floor(k / cols) * h;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const si = ((y * sc) * im.w + x * sc) * 4, di = ((oy + y) * W + ox + x) * 4; im.px.copy(px, di, si, si + 4); px[di + 3] = 255; } });
fs.writeFileSync(out, encodePng(W, H, px)); console.log(files.length, W, H);
