// gunkit3 -- clean Punisher firearm models. Lesson from 3 failed attempts: procedural "panel line"
// texture detail turns to mud/noise at 16px. So each part is now a SINGLE FLAT COLOUR swatch and we
// let Minecraft's own per-face directional shading (top bright, sides/bottom darker) do the 3D read.
// Deliberate detail (skull, shells, scope lens, sight dot) is painted as its own small swatch only
// where it matters. Barrel runs along -Z, grip -Y.
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

// one 6x6 swatch per part: flat base, plus a 1px lighter top row and 1px darker bottom row so even
// a face MC does not re-shade (rare) still has a hint of form. Deliberate motifs drawn over it.
const SW = 6;
function drawSwatch(cv, ox, oy, base, motif) {
  cv.fill(ox, oy, SW, SW, base);
  cv.fill(ox, oy, SW, 1, sh(base, 18));
  cv.fill(ox, oy + SW - 1, SW, 1, sh(base, -18));
  if (motif === 'skull') {
    const W = [235, 235, 232];
    cv.fill(ox + 1, oy + 1, 4, 2, W); cv.set(ox, oy + 2, W); cv.set(ox + 5, oy + 2, W);
    cv.fill(ox + 1, oy + 3, 4, 1, W); cv.set(ox + 1, oy + 4, W); cv.set(ox + 3, oy + 4, W);
    cv.set(ox + 2, oy + 2, base); cv.set(ox + 3, oy + 2, base);
  } else if (motif === 'shells') {
    for (let i = 0; i < SW; i += 2) { cv.fill(ox + i, oy, 2, SW - 1, [170, 46, 42]); cv.fill(ox + i, oy + SW - 1, 2, 1, [180, 145, 62]); }
  } else if (motif === 'lens') {
    cv.fill(ox, oy, SW, SW, [46, 40, 110]); cv.fill(ox + 1, oy + 1, SW - 2, SW - 2, [86, 108, 224]); cv.set(ox + 1, oy + 1, [210, 226, 255]);
  } else if (motif === 'dot') {
    cv.fill(ox + 2, oy + 2, 2, 2, sh(base, 60));
  } else if (motif === 'wood') {
    cv.fill(ox, oy + 2, SW, 1, sh(base, -22)); cv.fill(ox, oy + 4, SW, 1, sh(base, 14));
  }
}

function buildGun(id, parts, display, sheet) {
  // pack SW-sized swatches in a grid
  const per = Math.floor(sheet / (SW + 1));
  const cv = new Canvas(sheet, sheet);
  const elements = [];
  parts.forEach((p, i) => {
    const gx = (i % per) * (SW + 1) + 1, gy = Math.floor(i / per) * (SW + 1) + 1;
    drawSwatch(cv, gx, gy, p.base, p.motif);
    // face UV: sample the inner 4x4 of the 6x6 swatch (avoids bleeding the hi/lo rows to every face)
    const u1 = gx + 1, v1 = gy + 1, u2 = gx + SW - 1, v2 = gy + SW - 1;
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) faces[f] = { uv: [u1, v1, u2, v2], texture: '#g' };
    const el = { name: p.name, from: p.from, to: p.to, faces };
    if (p.rotation) el.rotation = p.rotation;
    // motif faces: point the "showcase" face (north = forward for barrel bits, west = right side for
    // grip) at the full 6x6 so the drawn motif is visible
    if (p.motif) {
      const showcase = p.motifFace || 'west';
      faces[showcase] = { uv: [gx, gy, gx + SW, gy + SW], texture: '#g' };
    }
    elements.push(el);
  });
  fs.writeFileSync(`src/main/resources/assets/projecthero/textures/item/${id}.png`, cv.png());
  fs.writeFileSync(`src/main/resources/assets/projecthero/models/item/${id}.json`, JSON.stringify({
    credit: 'Punisher firearm -- original ProjectHero voxel model (v0.8.9 clean rebuild). Flat per-part colour + MC face shading. Barrel -Z, grip -Y.',
    texture_size: [sheet, sheet],
    textures: { g: `projecthero:item/${id}`, particle: `projecthero:item/${id}` },
    elements, display,
  }, null, 2));
  console.log('wrote', id, parts.length, 'parts');
}
module.exports = { buildGun };
