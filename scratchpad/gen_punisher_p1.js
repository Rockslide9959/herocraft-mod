// Phase-1 Punisher textures: the pistol (64x64, mapped by punisher_pistol.json), plus the
// 16x16 sprites for Pistol Ammunition and Weapon Parts. Hand-rolled PNG encoder -- this project
// has no image library (see other scratchpad/gen_*.js). Nothing here is derived from a MC texture.
//
// Visual direction (spec 15/16): dark tactical -- black slide, gunmetal frame, dark grey / subtle
// brown grip, small white skull motif on the grip.
const fs = require('fs');
const zlib = require('zlib');
const OUT = 'src/main/resources/assets/herocraft/textures/item';

const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const l = Buffer.alloc(4); l.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([l, td, c]); };
function encode(w, h, data) {
  const stride = w * 4; const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
class C {
  constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
  set(x, y, c) { x |= 0; y |= 0; if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c.length > 3 ? c[3] : 255; }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  noise(x, y, w, h, base, amp) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
      const n = ((Math.sin((x + i) * 12.9898 + (y + j) * 78.233) * 43758.5453) % 1 + 1) % 1;
      const k = (n - 0.5) * amp; this.set(x + i, y + j, [clamp(base[0] + k), clamp(base[1] + k), clamp(base[2] + k), 255]); } }
  png() { return encode(this.w, this.h, this.d); }
}
const clamp = v => Math.max(0, Math.min(255, v | 0));

const BLACK = [22, 22, 24], SLIDE = [38, 39, 43], SLIDE_HI = [66, 68, 74];
const FRAME = [52, 50, 47], GRIP = [40, 33, 28], GRIP_HI = [58, 48, 40];
const STEEL = [120, 122, 128], WHITE = [232, 232, 230];

// ---------------------------------------------------------------- pistol 64x64
// Region layout (x,y,w,h) each element in punisher_pistol.json samples:
//   slide      (0,0,24,6)
//   frame      (0,8,20,6)
//   barrel     (0,16,8,4)
//   grip       (0,22,8,14)
//   trigger    (12,16,4,4)
//   sight      (18,16,3,2)
{
  const p = new C(64, 64);
  p.noise(0, 0, 24, 6, SLIDE, 10); p.rect(0, 0, 24, 1, SLIDE_HI); p.rect(2, 2, 3, 1, BLACK); p.rect(7, 2, 3, 1, BLACK);
  p.noise(0, 8, 20, 6, FRAME, 8); p.rect(0, 8, 20, 1, [70, 68, 64]);
  p.noise(0, 16, 8, 4, BLACK, 6); p.rect(0, 18, 8, 1, [40, 40, 44]);
  p.noise(0, 22, 8, 14, GRIP, 12);
  for (let y = 24; y < 34; y += 2) p.rect(1, y, 6, 1, GRIP_HI); // grip checkering
  // small white skull motif on the grip
  p.rect(3, 26, 2, 2, WHITE); p.set(2, 27, WHITE); p.set(5, 27, WHITE); p.rect(3, 28, 2, 1, WHITE); p.set(3, 29, WHITE); p.set(4, 29, WHITE);
  p.noise(12, 16, 4, 4, FRAME, 6);
  p.rect(18, 16, 3, 2, STEEL);
  fs.writeFileSync(`${OUT}/punisher_pistol.png`, p.png());
}

// ---------------------------------------------------------------- pistol ammo 16x16 (box of rounds)
{
  const a = new C(16, 16);
  a.noise(2, 6, 12, 8, [64, 52, 38], 10); a.rect(2, 6, 12, 1, [92, 76, 54]); // cardboard box
  for (let i = 0; i < 4; i++) { const x = 3 + i * 3;
    a.rect(x, 3, 2, 4, [190, 150, 70]);   // brass casing
    a.rect(x, 2, 2, 1, [150, 120, 55]);   // bullet tip
  }
  a.rect(2, 13, 12, 1, [40, 33, 24]);
  fs.writeFileSync(`${OUT}/pistol_ammo.png`, a.png());
}

// ---------------------------------------------------------------- weapon parts 16x16
{
  const w = new C(16, 16);
  w.noise(3, 3, 10, 10, STEEL, 14);
  w.rect(3, 3, 10, 1, [160, 162, 168]); w.rect(3, 12, 10, 1, [70, 72, 78]);
  w.rect(6, 1, 4, 3, [90, 60, 40]);     // a bit of redstone-red + a screw
  w.set(7, 7, [200, 40, 40]); w.set(9, 5, [200, 40, 40]);
  w.rect(4, 5, 2, 2, [40, 40, 44]); w.rect(10, 9, 2, 2, [40, 40, 44]);
  fs.writeFileSync(`${OUT}/weapon_parts.png`, w.png());
}

console.log('wrote punisher_pistol.png, pistol_ammo.png, weapon_parts.png');
