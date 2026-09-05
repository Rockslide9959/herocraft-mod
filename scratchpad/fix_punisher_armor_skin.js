// Recolour the stray peach/skin-tone pixels in the Punisher armour texture (part of the original
// player skin that leaked into the armour sheet) to the armour's dark tactical grey. v0.8.6.
const fs = require('fs');
const zlib = require('zlib');
const FILE = 'src/main/resources/assets/herocraft/textures/armor/punisher.png';

function decode(p) {
  const b = fs.readFileSync(p); let o = 8; const cs = [];
  while (o < b.length) { const len = b.readUInt32BE(o); const t = b.toString('ascii', o + 4, o + 8); cs.push({ t, d: b.slice(o + 8, o + 8 + len) }); o += 12 + len; }
  const ih = cs.find(c => c.t === 'IHDR'); const w = ih.d.readUInt32BE(0), h = ih.d.readUInt32BE(4), ct = ih.d[9];
  const idat = Buffer.concat(cs.filter(c => c.t === 'IDAT').map(c => c.d)); const raw = zlib.inflateSync(idat);
  const ch = ct === 6 ? 4 : (ct === 2 ? 3 : 1); const st = w * ch; const px = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(st);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (st + 1)]; const line = raw.slice(y * (st + 1) + 1, y * (st + 1) + 1 + st); const cur = Buffer.alloc(st);
    for (let x = 0; x < st; x++) {
      const A = x >= ch ? cur[x - ch] : 0, B = prev[x], C = x >= ch ? prev[x - ch] : 0; let v = line[x];
      if (f === 1) v = (v + A) & 255; else if (f === 2) v = (v + B) & 255; else if (f === 3) v = (v + ((A + B) >> 1)) & 255;
      else if (f === 4) { const p = A + B - C; const pa = Math.abs(p - A), pb = Math.abs(p - B), pc = Math.abs(p - C); v = (v + (pa <= pb && pa <= pc ? A : pb <= pc ? B : C)) & 255; }
      cur[x] = v;
    }
    for (let x = 0; x < w; x++) { const s = x * ch; px[(y * w + x) * 4] = cur[s]; px[(y * w + x) * 4 + 1] = cur[s + 1]; px[(y * w + x) * 4 + 2] = cur[s + 2]; px[(y * w + x) * 4 + 3] = ch === 4 ? cur[s + 3] : 255; }
    prev = cur;
  }
  return { w, h, px };
}

const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0);
  const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encode(w, h, px) {
  const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

const { w, h, px } = decode(FILE);
const DARK = [28, 30, 34];
let n = 0;
for (let i = 0; i < w * h; i++) {
  const r = px[i * 4], g = px[i * 4 + 1], b = px[i * 4 + 2], a = px[i * 4 + 3];
  if (a < 8) continue;
  // skin / peach: warm, red-dominant, not grey
  const skinish = r > 140 && r > g + 18 && g >= b && (r - b) > 28 && b < 190 && !(Math.abs(r - g) < 12 && Math.abs(g - b) < 12);
  if (skinish) { px[i * 4] = DARK[0]; px[i * 4 + 1] = DARK[1]; px[i * 4 + 2] = DARK[2]; n++; }
}
fs.writeFileSync(FILE, encode(w, h, px));
console.log('recoloured', n, 'skin-tone pixels ->', DARK);
