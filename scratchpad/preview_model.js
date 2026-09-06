// Tiny offline previewer for a ProjectHero item model: projects every element face isometrically,
// samples the average colour of its UV rect from the texture, z-buffers, and writes a PNG so the
// geometry + UV mapping can be eyeballed without a client. Usage: node preview_model.js <id>
const fs = require('fs');
const zlib = require('zlib');
const id = process.argv[2] || 'punisher_pistol';
const MODEL = `src/main/resources/assets/projecthero/models/item/${id}.json`;

// ---- png decode/encode (shared shape with the other scratchpad scripts) ----
function decode(p) {
  const b = fs.readFileSync(p); let o = 8; const cs = [];
  while (o < b.length) { const len = b.readUInt32BE(o); const t = b.toString('ascii', o + 4, o + 8); cs.push({ t, d: b.slice(o + 8, o + 8 + len) }); o += 12 + len; }
  const ih = cs.find(c => c.t === 'IHDR'); const w = ih.d.readUInt32BE(0), h = ih.d.readUInt32BE(4), ct = ih.d[9];
  const idat = Buffer.concat(cs.filter(c => c.t === 'IDAT').map(c => c.d)); const raw = zlib.inflateSync(idat);
  const ch = ct === 6 ? 4 : 3; const st = w * ch; const px = Buffer.alloc(w * h * 4); let prev = Buffer.alloc(st);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (st + 1)]; const line = raw.slice(y * (st + 1) + 1, y * (st + 1) + 1 + st); const cur = Buffer.alloc(st);
    for (let x = 0; x < st; x++) { const A = x >= ch ? cur[x - ch] : 0, B = prev[x], C = x >= ch ? prev[x - ch] : 0; let v = line[x];
      if (f === 1) v = (v + A) & 255; else if (f === 2) v = (v + B) & 255; else if (f === 3) v = (v + ((A + B) >> 1)) & 255;
      else if (f === 4) { const pp = A + B - C; const pa = Math.abs(pp - A), pb = Math.abs(pp - B), pc = Math.abs(pp - C); v = (v + (pa <= pb && pa <= pc ? A : pb <= pc ? B : C)) & 255; } cur[x] = v; }
    for (let x = 0; x < w; x++) { const s = x * ch; px[(y * w + x) * 4] = cur[s]; px[(y * w + x) * 4 + 1] = cur[s + 1]; px[(y * w + x) * 4 + 2] = cur[s + 2]; px[(y * w + x) * 4 + 3] = ch === 4 ? cur[s + 3] : 255; } prev = cur;
  } return { w, h, px };
}
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function enc(w, h, px) { const st = w * 4; const raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]); const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]); }

const model = JSON.parse(fs.readFileSync(MODEL, 'utf8'));
const tw = model.texture_size ? model.texture_size[0] : 16, th = model.texture_size ? model.texture_size[1] : 16;
const tex = decode(`src/main/resources/assets/projecthero/textures/item/${id}.png`);

function avgColor(uv) {
  const [u1, v1, u2, v2] = uv;
  let r = 0, g = 0, b = 0, a = 0, n = 0;
  for (let v = Math.min(v1, v2); v < Math.max(v1, v2); v++) for (let u = Math.min(u1, u2); u < Math.max(u1, u2); u++) {
    const tx = Math.floor(u / tw * tex.w), ty = Math.floor(v / th * tex.h);
    if (tx < 0 || ty < 0 || tx >= tex.w || ty >= tex.h) continue;
    const i = (ty * tex.w + tx) * 4; if (tex.px[i + 3] < 8) continue;
    r += tex.px[i]; g += tex.px[i + 1]; b += tex.px[i + 2]; a += tex.px[i + 3]; n++;
  }
  return n ? [r / n, g / n, b / n, a / n / 255] : [255, 0, 255, 0]; // magenta = nothing sampled
}

// isometric projection
const S = 320, img = Buffer.alloc(S * S * 4), zbuf = new Float32Array(S * S).fill(1e9);
for (let i = 0; i < S * S; i++) { img[i * 4] = 24; img[i * 4 + 1] = 24; img[i * 4 + 2] = 28; img[i * 4 + 3] = 255; }
// near-side view: look mostly along +X so a -Z-barrel gun shows its profile, with a slight yaw so
// depth still reads. Barrel (-Z) ends up pointing LEFT.
const yaw = 0.35, ca = Math.cos(yaw), sa = Math.sin(yaw);
function project(x, y, z) {
  x -= 8; y -= 8; z -= 8;
  const sx = (-z) * ca - x * sa;      // -z (barrel forward) -> screen +x (right); flip so barrel points left
  const sy = -y;
  const depth = x * ca + (-z) * sa;   // +x (right side of gun) toward viewer
  return [S / 2 - sx * 13, S / 2 + sy * 13 + 10, -depth];
}
function tri(p0, p1, p2, col, shade) {
  const minX = Math.max(0, Math.floor(Math.min(p0[0], p1[0], p2[0]))), maxX = Math.min(S - 1, Math.ceil(Math.max(p0[0], p1[0], p2[0])));
  const minY = Math.max(0, Math.floor(Math.min(p0[1], p1[1], p2[1]))), maxY = Math.min(S - 1, Math.ceil(Math.max(p0[1], p1[1], p2[1])));
  const area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1]);
  if (Math.abs(area) < 1e-6) return;
  for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
    const w0 = ((p1[0] - x) * (p2[1] - y) - (p2[0] - x) * (p1[1] - y)) / area;
    const w1 = ((p2[0] - x) * (p0[1] - y) - (p0[0] - x) * (p2[1] - y)) / area;
    const w2 = 1 - w0 - w1;
    if (w0 < -0.01 || w1 < -0.01 || w2 < -0.01) continue;
    const z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2];
    const idx = y * S + x; if (z >= zbuf[idx]) continue; zbuf[idx] = z;
    img[idx * 4] = Math.min(255, col[0] * shade); img[idx * 4 + 1] = Math.min(255, col[1] * shade); img[idx * 4 + 2] = Math.min(255, col[2] * shade); img[idx * 4 + 3] = 255;
  }
}
const FACES = {
  north: { c: [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]], s: 0.8 },
  south: { c: [[0, 0, 1], [0, 1, 1], [1, 1, 1], [1, 0, 1]], s: 0.8 },
  west:  { c: [[1, 0, 0], [1, 0, 1], [1, 1, 1], [1, 1, 0]], s: 0.6 },
  east:  { c: [[0, 0, 0], [0, 1, 0], [0, 1, 1], [0, 0, 1]], s: 0.6 },
  up:    { c: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]], s: 1.0 },
  down:  { c: [[0, 0, 0], [0, 0, 1], [1, 0, 1], [1, 0, 0]], s: 0.45 },
};
function elRot(el, x, y, z) {
  if (!el.rotation) return [x, y, z];
  const o = el.rotation.origin, a = el.rotation.angle * Math.PI / 180;
  const ca = Math.cos(a), sa = Math.sin(a);
  let px = x - o[0], py = y - o[1], pz = z - o[2];
  if (el.rotation.axis === 'x') { const ny = py * ca - pz * sa, nz = py * sa + pz * ca; py = ny; pz = nz; }
  else if (el.rotation.axis === 'y') { const nx = px * ca + pz * sa, nz = -px * sa + pz * ca; px = nx; pz = nz; }
  else { const nx = px * ca - py * sa, ny = px * sa + py * ca; px = nx; py = ny; }
  return [px + o[0], py + o[1], pz + o[2]];
}
let magenta = 0, painted = 0;
for (const el of model.elements) {
  const [fx, fy, fz] = el.from, [tx2, ty2, tz2] = el.to;
  for (const fn in el.faces) {
    const face = FACES[fn]; if (!face) continue;
    const col = avgColor(el.faces[fn].uv);
    if (col[3] < 0.05) { magenta++; } else { painted++; }
    const pts = face.c.map(([a, b, c]) => project(...elRot(el, fx + a * (tx2 - fx), fy + b * (ty2 - fy), fz + c * (tz2 - fz))));
    tri(pts[0], pts[1], pts[2], col, face.s);
    tri(pts[0], pts[2], pts[3], col, face.s);
  }
}
fs.writeFileSync(`scratchpad/preview_${id}.png`, enc(S, S, img));
console.log(`preview_${id}.png  faces painted=${painted} nothing-sampled=${magenta}`);
