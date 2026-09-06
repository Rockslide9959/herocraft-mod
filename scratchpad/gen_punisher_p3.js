// Phase-3 Punisher assets: frag grenade sprite (16x16) + C4 charge texture (32x32, mapped by a
// small 3D block model). Hand-rolled PNG encoder -- no image lib.
const fs = require('fs');
const zlib = require('zlib');
const ITEM = 'src/main/resources/assets/projecthero/textures/item';

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

// ---- frag grenade 16x16 (dark olive body, ridged, gold pin/lever)
{
  const g = new C(16, 16);
  g.rect(5, 4, 6, 9, [58, 66, 42]);        // body
  g.rect(5, 4, 6, 1, [78, 88, 58]);
  for (let y = 5; y < 12; y += 2) g.rect(5, y, 6, 1, [40, 48, 28]);  // ridges
  g.rect(6, 2, 4, 2, [70, 72, 60]);        // fuse cap
  g.rect(9, 1, 3, 2, [190, 160, 70]);      // lever
  g.set(10, 3, [210, 190, 90]);            // pin ring
  fs.writeFileSync(`${ITEM}/frag_grenade.png`, g.png());
}

// ---- c4 charge 32x32 -- top-left 12x12 = top face, 12,0 12x12 = side, 0,12 12x12 = end
{
  const c = new C(32, 32);
  const putty = [212, 208, 176], dark = [150, 146, 118], red = [210, 40, 40];
  c.rect(0, 0, 12, 12, putty); c.rect(0, 0, 12, 1, [235, 232, 200]); c.rect(0, 11, 12, 1, dark);
  c.rect(12, 0, 12, 12, dark); c.rect(12, 0, 12, 1, putty);
  c.rect(0, 12, 12, 12, [120, 118, 96]);
  // detonator + wire + LED
  c.rect(3, 2, 3, 3, [40, 40, 44]); c.set(4, 3, red); // LED on the top
  c.rect(14, 3, 6, 1, [40, 40, 44]);                  // wire on the side
  fs.writeFileSync(`${ITEM}/c4_charge.png`, c.png());
}

console.log('phase 3 textures written');
