// v0.12.25: Bone Claw texture (entity, 16x16) + Bone Claw / Adamantium serum item sprites. Hand-rolled PNG via node zlib.
const fs = require('fs');
const zlib = require('zlib');
const RES = 'src/main/resources/assets/projecthero/textures';

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (const x of b) c = CRC[(c ^ x) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return (h ^ (h >>> 16)) / 4294967296; };

// ---- bone texture: four 8x8 quadrants, base (darkest, tan) -> tip (pale ivory); edge pixels slightly darker ----
function boneTexture() {
  const px = Buffer.alloc(16 * 16 * 4);
  // quadrant -> base colour [r,g,b]
  const base = { '0,0': [196, 178, 140], '1,0': [214, 199, 165], '0,1': [228, 216, 188], '1,1': [238, 229, 205] };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const q = base[`${x >> 3},${y >> 3}`];
    const n = (hash(x, y) - 0.5) * 16;
    const lx = x & 7, ly = y & 7;
    const edge = lx === 0 || ly === 0 || lx === 7 || ly === 7;
    const k = edge ? -14 : 0;
    // faint growth-ring streaks along the length
    const streak = ((x + (y >> 1)) % 4 === 0) ? -6 : 0;
    const i = (y * 16 + x) * 4;
    px[i] = Math.max(0, Math.min(255, q[0] + n + k + streak));
    px[i + 1] = Math.max(0, Math.min(255, q[1] + n + k + streak));
    px[i + 2] = Math.max(0, Math.min(255, q[2] + n * 0.8 + k + streak));
    px[i + 3] = 255;
  }
  return png(16, 16, px);
}

// ---- serum vial sprite, liquid colours parametrised ----
function vial(liquid, glint) {
  const px = Buffer.alloc(16 * 16 * 4);
  const set = (x, y, c, a = 255) => { const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; };
  for (let x = 5; x <= 10; x++) { set(x, 1, [200, 200, 205]); set(x, 2, [165, 165, 172]); }
  for (let x = 4; x <= 11; x++) set(x, 3, [130, 130, 138]);
  set(4, 3, [95, 95, 102]); set(11, 3, [95, 95, 102]);
  for (let y = 4; y <= 5; y++) { set(6, y, [170, 210, 225], 200); set(9, y, [170, 210, 225], 200); }
  const half = [2, 3, 4, 4, 4, 4, 4, 3, 2];
  for (let i = 0; i < half.length; i++) {
    const y = 6 + i, hw = half[i];
    for (let x = 8 - hw; x < 8 + hw; x++) {
      const edge = x === 8 - hw || x === 8 + hw - 1 || y === 14;
      if (edge) set(x, y, [190, 225, 240], 230);
      else {
        const t = Math.floor(hash(x, y) * 3) * 8;
        const sheen = (x === 8 - hw + 1 && y > 7 && y < 12);
        const c = sheen ? glint : liquid.map(v => Math.min(255, v + t));
        set(x, y, c);
      }
    }
  }
  for (let x = 5; x <= 10; x++) set(x, 8, glint.map(v => Math.round(v * 0.8)));
  set(10, 10, glint);
  return png(16, 16, px);
}

fs.writeFileSync(`${RES}/entity/wolverine_bone_claws.png`, boneTexture());
fs.writeFileSync(`${RES}/item/bone_claw_serum.png`, vial([222, 210, 178], [255, 250, 225]));
fs.writeFileSync(`${RES}/item/adamantium_serum.png`, vial([120, 132, 150], [205, 220, 240]));
console.log('wolverine bone assets written');
