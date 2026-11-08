// 16x16 steel texture for Andy's forehead shard: brushed light steel (left 8 cols), mid steel (cols 8-11), dark edge steel (cols 12-15)
const fs = require('fs'), zlib = require('zlib');
const W = 16, H = 16, px = Buffer.alloc(W * H * 4);
let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
  let base = x < 8 ? 214 : x < 12 ? 170 : 112;
  base += Math.round((rnd() - 0.5) * 14) + (x < 8 ? Math.round(8 * Math.sin(y * 1.3)) : 0);
  if (x < 8 && (x + y) % 7 === 0) base += 18; // glints
  const v = Math.max(0, Math.min(255, base));
  const i = (y * W + x) * 4; px[i] = v; px[i + 1] = v + 3 > 255 ? 255 : v + 3; px[i + 2] = Math.min(255, v + 10); px[i + 3] = 255;
}
const raw = Buffer.alloc((W * 4 + 1) * H);
for (let y = 0; y < H; y++) { raw[y * (W * 4 + 1)] = 0; px.copy(raw, y * (W * 4 + 1) + 1, y * W * 4, (y + 1) * W * 4); }
const crc = (b) => { let c = ~0; for (const x of b) { c ^= x; for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xEDB88320 & -(c & 1)); } return ~c >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
fs.writeFileSync(process.argv[2], Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
console.log('ok');
