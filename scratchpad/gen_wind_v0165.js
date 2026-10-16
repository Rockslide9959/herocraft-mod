// Wind Manipulation v0.16.5 rework textures: p24_streak.png (tileable wispy streaks, alpha) -- hand-rolled PNG
const fs = require('fs'), zlib = require('zlib');
function png(w, h, rgba) {
  const crcT = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcT[n] = c >>> 0; }
  const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
let seed = 24;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const W = 64, H = 64;
const a = new Float32Array(W * H);
for (let k = 0; k < 22; k++) {
  const y0 = rnd() * H, sig = 0.6 + rnd() * 1.8, x0 = rnd() * W, len = 18 + rnd() * 40, peak = 0.35 + rnd() * 0.65;
  for (let y = 0; y < H; y++) for (let dyw = -1; dyw <= 1; dyw++) {
    const dy = y - y0 + dyw * H; const g = Math.exp(-dy * dy / (2 * sig * sig)); if (g < 0.01) continue;
    for (let i = 0; i < len; i++) {
      const t = i / len; const taper = Math.sin(Math.PI * t) * (0.6 + 0.4 * t);
      const x = Math.floor(x0 + i) % W;
      a[y * W + x] = Math.min(1, a[y * W + x] + peak * g * taper);
    }
  }
}
const out = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) {
  const v = Math.min(1, 0.3 + a[i] * 0.8);
  out[i * 4] = 236; out[i * 4 + 1] = 246; out[i * 4 + 2] = 255; out[i * 4 + 3] = Math.round(v * 255);
}
fs.writeFileSync(process.argv[2] + '/p24_streak.png', png(W, H, out));
console.log('wrote');
