// Contact sheet: scales PNGs (S=6 by default) side by side on a grey background. Reuses the PNG helpers of
// gen_vault_v0175.js. Usage: node scratchpad/png_sheet_v0175.js out.png a.png b.png ...
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');
const src = fs.readFileSync(path.join(__dirname, 'gen_vault_v0175.js'), 'utf8');
eval(src.slice(src.indexOf('const CRC'), src.indexOf('let seed')));
const out = process.argv[2];
const files = process.argv.slice(3);
const S = parseInt(process.env.S || '6', 10);
const imgs = files.map(f => decodePng(f));
const W = imgs.reduce((a, i) => a + i.w * S + 8, 8);
const H = Math.max(...imgs.map(i => i.h * S)) + 16;
const px = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) { px[i * 4] = 110; px[i * 4 + 1] = 110; px[i * 4 + 2] = 120; px[i * 4 + 3] = 255; }
let ox = 8;
for (const im of imgs) {
  for (let y = 0; y < im.h * S; y++) for (let x = 0; x < im.w * S; x++) {
    const s = ((y / S | 0) * im.w + (x / S | 0)) * 4;
    if (im.px[s + 3] === 0) continue;
    const d = ((y + 8) * W + ox + x) * 4;
    im.px.copy(px, d, s, s + 4);
    px[d + 3] = 255;
  }
  ox += im.w * S + 8;
}
fs.writeFileSync(out, encodePng(W, H, px));
console.log('sheet', out, W + 'x' + H);
