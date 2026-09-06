// gunkit4 -- FINALLY correct. The bug in every prior attempt: `texture_size` in the model JSON.
// Minecraft 1.21.1 vanilla models DO NOT support `texture_size` (grep of the model classes: the
// string does not appear). So a 64px sheet with UVs like [22,8,26,12] was read as 0-16 range,
// wrapping ~1.4x around the texture => the rainbow / checkerboard the user saw.
//
// This kit uses a plain 16x16 texture, a 4x4 grid of 4x4 solid-colour swatches (16 slots -- more
// than any gun needs), and every face UV strictly inside [0,16]. One flat colour per part; MC's own
// per-face directional shading gives the 3D read. Motifs (skull / red shells / blue lens / dot /
// wood grain) are painted into the part's own swatch. Barrel -Z, grip -Y.
const fs = require('fs');
const zlib = require('zlib');

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePNG(w, h, data) { const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; data.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]); const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]); }
class Canvas { constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
  set(x, y, c) { x |= 0; y |= 0; if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c.length > 3 ? c[3] : 255; }
  fill(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  png() { return encodePNG(this.w, this.h, this.d); } }
const cl = v => Math.max(0, Math.min(255, Math.round(v)));
const sh = (c, k) => [cl(c[0] + k), cl(c[1] + k), cl(c[2] + k), 255];

const CELL = 4; // 4x4 swatches, 4x4 grid, 16x16 sheet

function drawSwatch(cv, cx, cy, base, motif) {
  cv.fill(cx, cy, CELL, CELL, base);
  cv.fill(cx, cy, CELL, 1, sh(base, 22));           // top sheen
  cv.fill(cx, cy + CELL - 1, CELL, 1, sh(base, -22)); // bottom shade
  if (motif === 'skull') {
    const W = [236, 236, 233];
    cv.set(cx + 1, cy + 1, W); cv.set(cx + 2, cy + 1, W);
    cv.set(cx, cy + 2, W); cv.set(cx + 3, cy + 2, W);
    cv.set(cx + 1, cy + 3, W); cv.set(cx + 2, cy + 3, W);
  } else if (motif === 'shells') {
    cv.fill(cx, cy, 2, CELL, [172, 46, 42]); cv.fill(cx, cy + CELL - 1, 2, 1, [182, 146, 62]);
    cv.fill(cx + 2, cy, 2, CELL, [150, 40, 38]); cv.fill(cx + 2, cy + CELL - 1, 2, 1, [170, 134, 56]);
  } else if (motif === 'lens') {
    cv.fill(cx, cy, CELL, CELL, [50, 44, 118]);
    cv.fill(cx + 1, cy + 1, 2, 2, [92, 112, 226]);
    cv.set(cx + 1, cy + 1, [214, 228, 255]);
  } else if (motif === 'dot') {
    cv.fill(cx + 1, cy + 1, 2, 2, sh(base, 70));
  } else if (motif === 'wood') {
    cv.fill(cx, cy + 1, CELL, 1, sh(base, -20)); cv.fill(cx, cy + 3, CELL, 1, sh(base, 12));
  }
}

function buildGun(id, parts, display) {
  const cv = new Canvas(16, 16);
  const slot = [];
  const elements = [];
  parts.forEach((p, i) => {
    const col = (i % 4), row = Math.floor(i / 4);
    const cx = col * CELL, cy = row * CELL;
    slot[i] = [cx, cy];
    drawSwatch(cv, cx, cy, p.base, p.motif);
  });
  parts.forEach((p, i) => {
    const [cx, cy] = slot[i];
    const uv = [cx, cy, cx + CELL, cy + CELL];
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) faces[f] = { uv, texture: '#g' };
    const el = { name: p.name, from: p.from, to: p.to, faces };
    if (p.rotation) el.rotation = p.rotation;
    elements.push(el);
  });
  fs.writeFileSync(`src/main/resources/assets/projecthero/textures/item/${id}.png`, cv.png());
  fs.writeFileSync(`src/main/resources/assets/projecthero/models/item/${id}.json`, JSON.stringify({
    credit: 'Punisher firearm -- original ProjectHero voxel model (v0.9.0). 16x16 texture (NO texture_size -- vanilla 1.21 ignores it), UVs in [0,16], flat colour + MC face shading. Barrel -Z, grip -Y.',
    textures: { g: `projecthero:item/${id}`, particle: `projecthero:item/${id}` },
    elements, display,
  }, null, 2));
  console.log('wrote', id, parts.length, 'parts (16x16 tex)');
}
module.exports = { buildGun };
