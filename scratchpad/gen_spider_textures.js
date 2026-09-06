// Sprite for the Arachnid Mutagen (HeroPack 0.6.3).
//
// Reuses the hand-rolled PNG encoder and tiny canvas from gen_changes22_textures.js -- this project
// has no image library and no Python, so every sprite in the mod is drawn pixel by pixel like this.
// Nothing here is derived from Minecraft's own textures.
//
// The item reads as "a vial of something alive": a glass flask with a dark crimson fluid, a pale
// highlight down the left of the glass, and a black spider silhouette suspended in the liquid so it
// is unmistakable at 16x16 next to the mod's other flask-shaped items.
const fs = require('fs');
const zlib = require('zlib');

const OUT = 'src/main/resources/assets/projecthero/textures/item';

// ---------------------------------------------------------------- PNG encoding

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
};
function encode(w, h, data) {
  const stride = w * 4;
  const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (stride + 1)] = 0;
    data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))]);
}

class C {
  constructor(w = 16, h = 16) {
    this.w = w; this.h = h;
    this.data = Buffer.alloc(w * h * 4);
  }
  set(x, y, c) {
    x = Math.round(x); y = Math.round(y);
    if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2];
    this.data[i + 3] = c.length > 3 ? c[3] : 255;
  }
  rect(x, y, w, h, c) {
    for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, c);
  }
  line(x0, y0, x1, y1, c) {
    const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    for (let i = 0; i <= steps; i++) {
      this.set(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, c);
    }
  }
}

// ---------------------------------------------------------------- palette

const GLASS       = [178, 196, 206];
const GLASS_DARK  = [118, 134, 145];
const GLASS_LIGHT = [232, 244, 250];
const FLUID       = [138, 22, 38];
const FLUID_DEEP  = [92, 12, 26];
const FLUID_LIT   = [196, 46, 62];
const CORK        = [148, 108, 62];
const CORK_DARK   = [104, 72, 38];
const SPIDER      = [22, 16, 20];

// ---------------------------------------------------------------- the sprite

const c = new C(16, 16);

// cork stopper
c.rect(6, 1, 4, 2, CORK);
c.rect(6, 1, 4, 1, [176, 132, 78]);
c.rect(6, 3, 4, 1, CORK_DARK);

// neck
c.rect(6, 4, 4, 2, GLASS_DARK);
c.rect(7, 4, 2, 2, GLASS);

// flask body outline (a rounded bulb, widening from the neck)
const body = [
  [5, 6, 6], [4, 7, 8], [3, 8, 10], [3, 9, 10], [3, 10, 10], [3, 11, 10], [4, 12, 8], [5, 13, 6],
];
for (const [x, y, w] of body) {
  c.rect(x, y, w, 1, GLASS_DARK);
  c.rect(x + 1, y, w - 2, 1, GLASS);
}

// fluid fills the lower two thirds of the bulb
const fluid = [[4, 9, 8], [3, 10, 10], [3, 11, 10], [4, 12, 8], [5, 13, 6]];
for (const [x, y, w] of fluid) {
  c.rect(x + 1, y, w - 2, 1, FLUID);
}
c.rect(4, 12, 6, 1, FLUID_DEEP);
c.rect(6, 13, 4, 1, FLUID_DEEP);
// meniscus catches the light
c.rect(5, 9, 6, 1, FLUID_LIT);

// glass highlight down the left shoulder
c.line(4, 8, 4, 11, GLASS_LIGHT);
c.set(5, 7, GLASS_LIGHT);

// the spider, suspended in the fluid: a two-pixel body and four legs a side
c.rect(7, 10, 2, 2, SPIDER);
c.set(7, 12, SPIDER);
c.set(8, 12, SPIDER);
// left legs
c.line(6, 10, 5, 9, SPIDER);
c.line(6, 11, 4, 11, SPIDER);
c.line(6, 12, 5, 13, SPIDER);
// right legs
c.line(9, 10, 10, 9, SPIDER);
c.line(9, 11, 11, 11, SPIDER);
c.line(9, 12, 10, 13, SPIDER);

fs.writeFileSync(`${OUT}/arachnid_mutagen.png`, encode(16, 16, c.data));
console.log('wrote arachnid_mutagen.png');
