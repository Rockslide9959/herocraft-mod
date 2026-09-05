// Phase-4 Punisher item icons: the three tactical armour pieces + the Vigilante Training Manual
// (16x16). Hand-rolled PNG encoder -- no image lib. Icons only; the armour renders as the GeckoLib
// model in-world.
const fs = require('fs');
const zlib = require('zlib');
const ITEM = 'src/main/resources/assets/herocraft/textures/item';

const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0);
  const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, c]); };
function encode(w, h, data) {
  const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; data.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
class C {
  constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
  set(x, y, c) { x |= 0; y |= 0; if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c.length > 3 ? c[3] : 255; }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  png() { return encode(this.w, this.h, this.d); }
}

const BLK = [30, 30, 34], DGRY = [50, 51, 56], GRY = [76, 78, 84], WHT = [228, 228, 226], STRAP = [40, 34, 28];

// tactical vest -- black plate carrier + white skull
{
  const v = new C(16, 16);
  v.rect(3, 2, 10, 12, DGRY); v.rect(3, 2, 10, 1, GRY); v.rect(3, 13, 10, 1, BLK);
  v.rect(2, 3, 1, 8, STRAP); v.rect(13, 3, 1, 8, STRAP);        // straps
  v.rect(5, 4, 6, 5, BLK);                                      // chest panel
  v.rect(6, 5, 4, 3, WHT); v.set(5, 6, WHT); v.set(10, 6, WHT); // skull dome
  v.rect(6, 8, 4, 1, WHT); v.set(6, 9, WHT); v.set(9, 9, WHT);  // jaw
  fs.writeFileSync(`${ITEM}/punisher_tactical_vest.png`, v.png());
}
// leggings -- dark tactical trousers with knee pads
{
  const l = new C(16, 16);
  l.rect(3, 1, 10, 4, DGRY);
  l.rect(3, 5, 4, 10, GRY); l.rect(9, 5, 4, 10, GRY);
  l.rect(3, 8, 4, 2, BLK); l.rect(9, 8, 4, 2, BLK);   // knee pads
  l.rect(3, 1, 10, 1, [90, 92, 98]);
  fs.writeFileSync(`${ITEM}/punisher_tactical_leggings.png`, l.png());
}
// boots -- black combat boots
{
  const b = new C(16, 16);
  b.rect(3, 3, 4, 8, GRY); b.rect(9, 3, 4, 8, GRY);
  b.rect(2, 11, 6, 3, BLK); b.rect(8, 11, 6, 3, BLK);   // soles/toes
  b.rect(3, 3, 4, 1, [90, 92, 98]); b.rect(9, 3, 4, 1, [90, 92, 98]);
  fs.writeFileSync(`${ITEM}/punisher_tactical_boots.png`, b.png());
}
// training manual -- dark book, white skull stamp
{
  const m = new C(16, 16);
  m.rect(3, 2, 10, 12, [40, 32, 26]); m.rect(3, 2, 2, 12, [26, 20, 16]);   // spine
  m.rect(5, 3, 8, 10, [58, 48, 40]);
  m.rect(8, 6, 3, 3, WHT); m.set(7, 7, WHT); m.set(11, 7, WHT); m.rect(8, 9, 3, 1, WHT);
  fs.writeFileSync(`${ITEM}/vigilante_training_manual.png`, m.png());
}

console.log('phase 4 icons written');
