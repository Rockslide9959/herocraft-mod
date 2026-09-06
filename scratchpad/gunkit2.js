// gunkit2 -- detailed Punisher firearm models + textures (v0.8.7), based on the user's reference
// sheet. Barrel runs along -Z (into the screen), grip down -Y. Proper Blockbench box-UV unwrap,
// fully-painted gunmetal sheet with edge highlights / recessed panel lines / per-part accents
// (wood, brass+red shells, blue scope glass, white skull).
const fs = require('fs');
const zlib = require('zlib');

// ---- PNG ----
const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0);
  const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePNG(w, h, data) {
  const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; data.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
  set(x, y, c) { x |= 0; y |= 0; if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c.length > 3 ? c[3] : 255; }
  fill(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  hline(x, y, w, c) { this.fill(x, y, w, 1, c); }
  vline(x, y, h, c) { this.fill(x, y, 1, h, c); }
  png() { return encodePNG(this.w, this.h, this.d); }
}
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const sh = (c, k) => [clamp(c[0] + k), clamp(c[1] + k), clamp(c[2] + k), 255];

// ---- box unwrap (barrel along -Z; faces named by MC convention) ----
function boxUV(ox, oy, w, h, d) {
  return {
    up:    [ox + d,       oy,     ox + d + w,     oy + d],
    down:  [ox + d + w,   oy,     ox + d + w + w, oy + d],
    east:  [ox,           oy + d, ox + d,         oy + d + h],
    north: [ox + d,       oy + d, ox + d + w,     oy + d + h],
    west:  [ox + d + w,   oy + d, ox + d + w + d, oy + d + h],
    south: [ox + d + w + d, oy + d, ox + d + w + d + w, oy + d + h],
  };
}
const footprint = (w, h, d) => [2 * d + 2 * w, d + h];

// paint one part's unwrap: base fill, top light, bottom dark, edge lines, recessed panel grooves,
// optional accent stripe / dot pattern / skull.
function paintPart(cv, ox, oy, w, h, d, part) {
  const base = part.base;
  const [fw, fh] = footprint(w, h, d);
  cv.fill(ox, oy, fw, fh, base);
  // up = top (lit), down = bottom (shadow)
  cv.fill(ox + d, oy, w, d, sh(base, 26));
  cv.fill(ox + d + w, oy, w, d, sh(base, -30));
  // horizontal edge lines across the side band
  cv.hline(ox, oy + d, fw, sh(base, 40));
  cv.hline(ox, oy + fh - 1, fw, sh(base, -38));
  // subtle top-of-side sheen + bottom shade on the long faces
  cv.hline(ox + d, oy + d + 1, w, sh(base, 14));
  cv.hline(ox + d + w + d, oy + d + 1, w, sh(base, 14));
  // vertical seams where the four side faces meet
  for (const cx of [ox, ox + d, ox + d + w, ox + d + w + d]) cv.vline(cx, oy + d, h, sh(base, -18));
  // recessed panel grooves on the long faces (north = col d.., south = col 2d+w..)
  if (!part.smooth) {
    for (const cx of [ox + d, ox + d + w + d]) {
      const step = Math.max(3, Math.round(w / 4));
      for (let k = step; k < w; k += step) { cv.vline(cx + k, oy + d + 1, h - 2, sh(base, -26)); cv.vline(cx + k + 1, oy + d + 1, h - 2, sh(base, 12)); }
    }
  }
  if (part.serrate) { // slide serrations: closely-spaced vertical lines near the back of the top faces
    for (const cx of [ox + d, ox + d + w + d]) for (let k = 1; k < Math.min(w, 7); k++) cv.vline(cx + k, oy + d + 1, h - 2, sh(base, k % 2 ? -30 : 16));
  }
  if (part.ribs) { // magazine / grip ribs: horizontal lines
    for (const cx of [ox + d, ox + d + w + d]) for (let k = 2; k < h - 1; k += 2) { cv.hline(cx, oy + d + k, w, sh(base, -22)); cv.hline(cx, oy + d + k + 1, w, sh(base, 10)); }
  }
  if (part.accent) {
    const ay = oy + d + Math.max(1, Math.floor(h * (part.accentAt ?? 0.5)));
    cv.fill(ox, ay, fw, Math.max(1, part.accentH ?? 1), part.accent);
  }
  if (part.shells) { // shotgun tube: red shells with brass rims along the north face
    const nx = ox + d, ny = oy + d;
    for (let k = 0; k < w; k += 2) {
      cv.fill(nx + k, ny, 2, h, [168, 44, 40]);
      cv.hline(nx + k, ny + h - 1, 2, [176, 140, 60]);
    }
  }
  if (part.glass) { // scope front: blue-purple lens on the north (forward) face
    const nx = ox + d, ny = oy + d;
    cv.fill(nx, ny, w, h, [58, 46, 128]);
    cv.fill(nx + 1, ny + 1, Math.max(1, w - 2), Math.max(1, h - 2), [92, 110, 220]);
    if (w > 2 && h > 2) cv.set(nx + 1, ny + 1, [200, 220, 255]);
  }
  if (part.skull) {
    const sx = ox + d + Math.floor(w / 2) - 1, sy = oy + d + Math.floor(h / 2) - 1;
    const W = [232, 232, 230];
    cv.fill(sx, sy, 3, 2, W); cv.set(sx - 1, sy + 1, W); cv.set(sx + 3, sy + 1, W);
    cv.fill(sx, sy + 2, 3, 1, W); cv.set(sx, sy + 3, W); cv.set(sx + 2, sy + 3, W);
  }
}

// ---- assembly ----
function buildGun(id, parts, display, sheetW, sheetH) {
  let px = 1, py = 1, rowH = 0;
  const elements = [];
  for (const p of parts) {
    const w = Math.max(1, Math.round(p.to[0] - p.from[0]));
    const h = Math.max(1, Math.round(p.to[1] - p.from[1]));
    const d = Math.max(1, Math.round(p.to[2] - p.from[2]));
    const [fw, fh] = footprint(w, h, d);
    if (px + fw + 1 > sheetW) { px = 1; py += rowH + 1; rowH = 0; }
    p._pack = { px, py, w, h, d };
    const uv = boxUV(px, py, w, h, d);
    px += fw + 1; rowH = Math.max(rowH, fh);
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) faces[f] = { uv: uv[f], texture: '#gun' };
    const el = { name: p.name, from: p.from, to: p.to, faces };
    if (p.rotation) el.rotation = p.rotation;
    elements.push(el);
  }
  const cv = new Canvas(sheetW, sheetH);
  for (const p of parts) { const { px, py, w, h, d } = p._pack; paintPart(cv, px, py, w, h, d, p); }
  fs.writeFileSync(`src/main/resources/assets/projecthero/textures/item/${id}.png`, cv.png());
  fs.writeFileSync(`src/main/resources/assets/projecthero/models/item/${id}.json`, JSON.stringify({
    credit: 'Punisher firearm -- original ProjectHero voxel model (v0.8.7). Barrel along -Z, grip -Y. Based on the user reference sheet. Display transforms tuned by eye; expect a screenshot pass.',
    texture_size: [sheetW, sheetH],
    textures: { gun: `projecthero:item/${id}`, particle: `projecthero:item/${id}` },
    elements, display,
  }, null, 2));
  console.log('wrote', id, '(', parts.length, 'parts,', elements.length, 'elements )');
}

module.exports = { Canvas, buildGun, boxUV, footprint, sh };
