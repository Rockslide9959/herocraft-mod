// v0.14.8 Super Soldier item textures (16x16), hand-rolled PNGs via Node's zlib (no Python on this machine).
// Run from the repo root: node scratchpad/gen_supersoldier_textures.js
const fs = require("fs");
const zlib = require("zlib");
const path = require("path");

const OUT = path.join(__dirname, "..", "src", "main", "resources", "assets", "projecthero", "textures", "item");

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, px) { // px: array of [r,g,b,a] rows*cols
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const p = px[y * w + x] || [0, 0, 0, 0];
      raw.set(p, y * (w * 4 + 1) + 1 + x * 4);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const hex = (s, a = 255) => [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16), a];

// A potion-style vial. Legend: C cork, c cork shade, G glass rim, g glass shade, L liquid, D liquid dark, H highlight, S sparkle
const VIAL = [
  "................",
  "......CCCC......",
  "......CccC......",
  ".....GCCCCG.....",
  "......G..G......",
  "......GLLG......",
  ".....GLLLLG.....",
  "....GLLLLLLG....",
  "...GLHLLLLLLG...",
  "...GLHLLLDLLG...",
  "...GLLLLLLLDG...",
  "...GLLLDLLLLG...",
  "...GLLLLLLDDG...",
  "....GLLDLLDG....",
  ".....gGGGGg.....",
  "................",
];

function vial(liquid, dark, highlight, glass, sparkle) {
  const px = [];
  const pal = {
    C: hex("8a5a32"), c: hex("6b4424"), G: glass, g: hex("7a8a99", 220),
    L: liquid, D: dark, H: highlight,
  };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const ch = VIAL[y][x];
    px.push(ch === "." ? [0, 0, 0, 0] : pal[ch]);
  }
  for (const [x, y] of sparkle || []) px[y * 16 + x] = hex("ffffff");
  return px;
}

// Unrefined: murky blue-grey, clouded
const unrefined = vial(hex("4f6272"), hex("35434f"), hex("8196a6"), hex("c8d4de", 230), []);
// Refined: bright glowing blue with white sparkles
const refined = vial(hex("2f8cff"), hex("1c5fd6"), hex("bfe6ff"), hex("e6f4ff", 240), [[7, 9], [9, 11], [6, 12]]);

// The Soldier's Shield: concentric red / white / red rings, blue centre, white five-point star
function shield() {
  const px = [];
  const cx = 7.5, cy = 7.5;
  const RED = hex("c8202a"), RED_D = hex("8e141b"), WHITE = hex("eeeeee"), BLUE = hex("1f3f9a"), STAR = hex("ffffff");
  // star polygon
  const star = [];
  for (let i = 0; i < 10; i++) {
    const r = i % 2 === 0 ? 2.9 : 1.2;
    const a = -Math.PI / 2 + i * Math.PI / 5;
    star.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]);
  }
  const inStar = (x, y) => {
    let inside = false;
    for (let i = 0, j = star.length - 1; i < star.length; j = i++) {
      const [xi, yi] = star[i], [xj, yj] = star[j];
      if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5));
    let p = [0, 0, 0, 0];
    if (d <= 7.9) p = d > 7.0 ? RED_D : d > 5.6 ? RED : d > 4.4 ? WHITE : d > 3.4 ? RED : BLUE;
    if (d <= 3.4 && inStar(x + 0.5, y + 0.5)) p = STAR;
    px.push(p);
  }
  return px;
}

fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, "unrefined_super_soldier_serum.png"), png(16, 16, unrefined));
fs.writeFileSync(path.join(OUT, "refined_super_soldier_serum.png"), png(16, 16, refined));
fs.writeFileSync(path.join(OUT, "soldier_shield.png"), png(16, 16, shield()));
console.log("wrote 3 textures to", OUT);
