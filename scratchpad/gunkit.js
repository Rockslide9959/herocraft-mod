// Shared kit for the Punisher firearm models + textures (v0.8.6 rebuild).
//
// The v0.8.1/0.8.2 guns "looked like missing textures" because the generator only painted small
// noisy patches of a 64x64 sheet and left most of it transparent, and every face of a cuboid was
// mapped to the same flat rect. This kit does it properly: each cuboid gets a real box unwrap, all
// unwraps are packed into a fully-painted sheet, and the paint is solid gunmetal with clean panel
// lines + accents (no harsh per-pixel noise).
const fs = require('fs');
const zlib = require('zlib');

// ---------------------------------------------------------------- PNG encode
const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0);
  const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePNG(w, h, data) {
  const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; data.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
  set(x, y, c) { x |= 0; y |= 0; if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c.length > 3 ? c[3] : 255; }
  fill(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  png() { return encodePNG(this.w, this.h, this.d); }
}
const shade = (c, k) => [Math.max(0, Math.min(255, c[0] + k)), Math.max(0, Math.min(255, c[1] + k)), Math.max(0, Math.min(255, c[2] + k)), 255];

// ---------------------------------------------------------------- box unwrap
// Standard Minecraft/Blockbench box unwrap for a cuboid of pixel size (w,h,d) with its unwrap
// origin at texture (ox,oy). Footprint = (2d+2w) x (d+h). Returns per-face uv rects [u1,v1,u2,v2].
function boxUV(ox, oy, w, h, d) {
  return {
    up:    [ox + d,         oy,     ox + d + w,         oy + d],
    down:  [ox + d + w,     oy,     ox + d + w + w,     oy + d],
    east:  [ox,             oy + d, ox + d,             oy + d + h],
    north: [ox + d,         oy + d, ox + d + w,         oy + d + h],
    west:  [ox + d + w,     oy + d, ox + d + w + d,     oy + d + h],
    south: [ox + d + w + d, oy + d, ox + d + w + d + w, oy + d + h],
  };
}
const footprint = (w, h, d) => [2 * d + 2 * w, d + h];

// Paint one box's unwrap footprint: solid base, top highlight, bottom shadow, a couple of panel
// lines down the long faces, plus optional accent stripes.
function paintBox(cv, ox, oy, w, h, d, base, opts = {}) {
  const [fw, fh] = footprint(w, h, d);
  cv.fill(ox, oy, fw, fh, base);
  // up face lighter, down face darker
  cv.fill(ox + d, oy, w, d, shade(base, 14));
  cv.fill(ox + d + w, oy, w, d, shade(base, -18));
  // side band highlight / shadow
  cv.fill(ox, oy + d, fw, 1, shade(base, 22));
  cv.fill(ox, oy + fh - 1, fw, 1, shade(base, -22));
  // panel lines on the two long (w) faces (north at col d.., south at col 2d+w..)
  for (const cx of [ox + d, ox + d + w + d]) {
    for (let k = 1; k < w; k += Math.max(2, Math.round(w / 3))) {
      cv.fill(cx + k, oy + d, 1, h, shade(base, -14));
    }
  }
  if (opts.accent) {
    // a horizontal accent stripe across every face (e.g. a wooden strip / rail)
    const ay = oy + d + Math.floor(h * (opts.accentAt ?? 0.5));
    cv.fill(ox, ay, fw, Math.max(1, opts.accentH ?? 1), opts.accent);
  }
  if (opts.skull) {
    // small white skull on the north face
    const sx = ox + d + Math.floor(w / 2) - 1, sy = oy + d + Math.floor(h / 2) - 1;
    cv.fill(sx, sy, 3, 2, [230, 230, 228]); cv.set(sx - 1, sy + 1, [230, 230, 228]);
    cv.set(sx + 3, sy + 1, [230, 230, 228]); cv.fill(sx, sy + 2, 3, 1, [230, 230, 228]);
  }
}

// ---------------------------------------------------------------- model assembly
// A part = { name, from:[x,y,z], to:[x,y,z], base, opts }. Sizes are in model units (= px).
function buildGun(id, parts, display, sheetW = 128, sheetH = 64) {
  // pack unwraps left->right, wrap rows
  let px = 1, py = 1, rowH = 0;
  const elements = [];
  for (const p of parts) {
    // UV-region sizes -- clamp to >= 1px so a geometrically thin element still has real faces to
    // sample (a 0-width UV rect samples nothing and renders as the missing-texture magenta).
    const w = Math.max(1, Math.round(p.to[0] - p.from[0]));
    const h = Math.max(1, Math.round(p.to[1] - p.from[1]));
    const d = Math.max(1, Math.round(p.to[2] - p.from[2]));
    const [fw, fh] = footprint(w, h, d);
    if (px + fw + 1 > sheetW) { px = 1; py += rowH + 1; rowH = 0; }
    p._uv = boxUV(px, py, w, h, d);
    p._pack = { px, py, w, h, d };
    px += fw + 1; rowH = Math.max(rowH, fh);
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
      faces[f] = { uv: p._uv[f], texture: '#gun' };
    }
    const el = { name: p.name, from: p.from, to: p.to, faces };
    if (p.rotation) el.rotation = p.rotation;
    elements.push(el);
  }
  // paint
  const cv = new Canvas(sheetW, sheetH);
  for (const p of parts) {
    const { px, py, w, h, d } = p._pack;
    paintBox(cv, px, py, w, h, d, p.base, p.opts || {});
  }
  fs.writeFileSync(`src/main/resources/assets/herocraft/textures/item/${id}.png`, cv.png());

  const model = {
    credit: 'Punisher firearm -- original HeroCraft voxel model (v0.8.6 rebuild, proper box UV unwrap). Barrel along +X. Display block tuned by eye; expect a screenshot pass.',
    texture_size: [sheetW, sheetH],
    textures: { gun: `herocraft:item/${id}`, particle: `herocraft:item/${id}` },
    elements,
    display,
  };
  fs.writeFileSync(`src/main/resources/assets/herocraft/models/item/${id}.json`, JSON.stringify(model, null, 2));
  console.log('wrote', id, '(', parts.length, 'parts )');
}

module.exports = { Canvas, buildGun, boxUV, footprint, paintBox, shade };
