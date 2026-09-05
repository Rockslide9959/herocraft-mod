// First-person view previewer -- replicates the vanilla 1.21.1 first-person right-hand item
// transform chain so a firearm's `firstperson_righthand` display block can be tuned offline.
//
// Chain (re-confirmed from ItemInHandRenderer.renderArmWithItem + ItemRenderer.render +
// ItemTransform.apply/Deserializer bytecode -- there is NO center translate before apply):
//   armT   = translate(0.56*f, -0.52, -0.72)                   [right hand rest; applyItemArmTransform]
//   disp   = translate(dt) * rotateXYZ(dr) * scale(ds)         [ItemTransform.apply; dt = json * 0.0625, clamped +/-5]
//   uncntr = translate(-0.5, -0.5, -0.5)                       [ItemRenderer.render, right after apply]
//   vertex is v_elem / 16   (FaceBakery divides element coords by 16)
// so  world = armT * disp * uncntr * (v_elem/16)
// Camera at origin looking down -Z, +X right, +Y up. Perspective project with a ~70deg vertical FOV.
const fs = require('fs');
const zlib = require('zlib');
const id = process.argv[2] || 'punisher_pistol';

function decode(p) {
  const b = fs.readFileSync(p); let o = 8; const cs = [];
  while (o < b.length) { const len = b.readUInt32BE(o); const t = b.toString('ascii', o + 4, o + 8); cs.push({ t, d: b.slice(o + 8, o + 8 + len) }); o += 12 + len; }
  const ih = cs.find(c => c.t === 'IHDR'); const w = ih.d.readUInt32BE(0), h = ih.d.readUInt32BE(4), ct = ih.d[9];
  const idat = Buffer.concat(cs.filter(c => c.t === 'IDAT').map(c => c.d)); const raw = zlib.inflateSync(idat);
  const ch = ct === 6 ? 4 : 3; const st = w * ch; const px = Buffer.alloc(w * h * 4); let prev = Buffer.alloc(st);
  for (let y = 0; y < h; y++) { const f = raw[y * (st + 1)]; const line = raw.slice(y * (st + 1) + 1, y * (st + 1) + 1 + st); const cur = Buffer.alloc(st);
    for (let x = 0; x < st; x++) { const A = x >= ch ? cur[x - ch] : 0, B = prev[x], C = x >= ch ? prev[x - ch] : 0; let v = line[x];
      if (f === 1) v = (v + A) & 255; else if (f === 2) v = (v + B) & 255; else if (f === 3) v = (v + ((A + B) >> 1)) & 255;
      else if (f === 4) { const pp = A + B - C; const pa = Math.abs(pp - A), pb = Math.abs(pp - B), pc = Math.abs(pp - C); v = (v + (pa <= pb && pa <= pc ? A : pb <= pc ? B : C)) & 255; } cur[x] = v; }
    for (let x = 0; x < w; x++) { const s = x * ch; px[(y * w + x) * 4] = cur[s]; px[(y * w + x) * 4 + 1] = cur[s + 1]; px[(y * w + x) * 4 + 2] = cur[s + 2]; px[(y * w + x) * 4 + 3] = ch === 4 ? cur[s + 3] : 255; } prev = cur; }
  return { w, h, px };
}
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const ch2 = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function enc(w, h, px) { const st = w * 4; const raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]); const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, ch2('IHDR', ihdr), ch2('IDAT', zlib.deflateSync(raw)), ch2('IEND', Buffer.alloc(0))]); }

const model = JSON.parse(fs.readFileSync(`src/main/resources/assets/herocraft/models/item/${id}.json`, 'utf8'));
const tw = model.texture_size ? model.texture_size[0] : 16, th = model.texture_size ? model.texture_size[1] : 16;
const tex = decode(`src/main/resources/assets/herocraft/textures/item/${id}.png`);
const D = model.display.firstperson_righthand || { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
const dt = D.translation.map(v => v / 16), ds = D.scale, dr = D.rotation.map(v => v * Math.PI / 180);

function matVec(fn, x, y, z) { return fn(x, y, z); }
function disp(x, y, z) {
  // uncenter
  x -= 0.5; y -= 0.5; z -= 0.5;
  // scale
  x *= ds[0]; y *= ds[1]; z *= ds[2];
  // rotateXYZ (intrinsic X then Y then Z == matrix Rz*Ry*Rx applied to vector)
  let cx = Math.cos(dr[0]), sx = Math.sin(dr[0]); let ny = y * cx - z * sx, nz = y * sx + z * cx; y = ny; z = nz;
  let cy = Math.cos(dr[1]), sy = Math.sin(dr[1]); let nx = x * cy + z * sy; nz = -x * sy + z * cy; x = nx; z = nz;
  let cz = Math.cos(dr[2]), sz = Math.sin(dr[2]); nx = x * cz - y * sz; ny = x * sz + y * cz; x = nx; y = ny;
  // translate(dt)
  x += dt[0]; y += dt[1]; z += dt[2];
  // arm transform (right hand, rest pose) -- NO center-back before this
  x += 0.56; y += -0.52; z += -0.72;
  return [x, y, z];
}
function elRot(el, x, y, z) {
  if (!el.rotation) return [x, y, z];
  const o = el.rotation.origin, a = el.rotation.angle * Math.PI / 180, ca = Math.cos(a), sa = Math.sin(a);
  let px = x - o[0], py = y - o[1], pz = z - o[2];
  if (el.rotation.axis === 'x') { const ny = py * ca - pz * sa, nz = py * sa + pz * ca; py = ny; pz = nz; }
  else if (el.rotation.axis === 'y') { const nx = px * ca + pz * sa, nz = -px * sa + pz * ca; px = nx; pz = nz; }
  else { const nx = px * ca - py * sa, ny = px * sa + py * ca; px = nx; py = ny; }
  return [px + o[0], py + o[1], pz + o[2]];
}

const W = 428, H = 240; // MC gui-ish aspect
const img = Buffer.alloc(W * H * 4); const zb = new Float32Array(W * H).fill(1e9);
for (let i = 0; i < W * H; i++) { img[i * 4] = 120; img[i * 4 + 1] = 150; img[i * 4 + 2] = 100; img[i * 4 + 3] = 255; } // grass-ish bg
// crosshair
for (let k = -4; k <= 4; k++) { const cx = W / 2 | 0, cy = H / 2 | 0; img[(cy * W + cx + k) * 4] = img[(cy * W + cx + k) * 4 + 1] = img[(cy * W + cx + k) * 4 + 2] = 255; img[((cy + k) * W + cx) * 4] = img[((cy + k) * W + cx) * 4 + 1] = img[((cy + k) * W + cx) * 4 + 2] = 255; }

const fovY = 70 * Math.PI / 180, f = 1 / Math.tan(fovY / 2), aspect = W / H;
function project(x, y, z) {
  if (z >= -0.05) return null;
  const sx = (x / -z) * f / aspect, sy = (y / -z) * f;
  return [W / 2 + sx * W / 2, H / 2 - sy * H / 2, -z];
}
function avgCol(uv) { const [u1, v1, u2, v2] = uv; let r = 0, g = 0, b = 0, a = 0, n = 0;
  for (let v = Math.min(v1, v2); v < Math.max(v1, v2); v++) for (let u = Math.min(u1, u2); u < Math.max(u1, u2); u++) {
    const tx = Math.floor(u / tw * tex.w), ty = Math.floor(v / th * tex.h); if (tx < 0 || ty < 0 || tx >= tex.w || ty >= tex.h) continue;
    const i = (ty * tex.w + tx) * 4; if (tex.px[i + 3] < 8) continue; r += tex.px[i]; g += tex.px[i + 1]; b += tex.px[i + 2]; n++; a += tex.px[i + 3]; }
  return n ? [r / n, g / n, b / n] : null; }
function tri(p0, p1, p2, col, sh) {
  if (!p0 || !p1 || !p2) return;
  const minX = Math.max(0, Math.floor(Math.min(p0[0], p1[0], p2[0]))), maxX = Math.min(W - 1, Math.ceil(Math.max(p0[0], p1[0], p2[0])));
  const minY = Math.max(0, Math.floor(Math.min(p0[1], p1[1], p2[1]))), maxY = Math.min(H - 1, Math.ceil(Math.max(p0[1], p1[1], p2[1])));
  const area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1]); if (Math.abs(area) < 1e-6) return;
  for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
    const w0 = ((p1[0] - x) * (p2[1] - y) - (p2[0] - x) * (p1[1] - y)) / area;
    const w1 = ((p2[0] - x) * (p0[1] - y) - (p0[0] - x) * (p2[1] - y)) / area; const w2 = 1 - w0 - w1;
    if (w0 < -0.01 || w1 < -0.01 || w2 < -0.01) continue;
    const z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]; const idx = y * W + x; if (z >= zb[idx]) continue; zb[idx] = z;
    img[idx * 4] = Math.min(255, col[0] * sh); img[idx * 4 + 1] = Math.min(255, col[1] * sh); img[idx * 4 + 2] = Math.min(255, col[2] * sh); img[idx * 4 + 3] = 255;
  }
}
const FACES = { north: { c: [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]], s: 0.85 }, south: { c: [[0, 0, 1], [0, 1, 1], [1, 1, 1], [1, 0, 1]], s: 0.85 },
  west: { c: [[1, 0, 0], [1, 0, 1], [1, 1, 1], [1, 1, 0]], s: 0.7 }, east: { c: [[0, 0, 0], [0, 1, 0], [0, 1, 1], [0, 0, 1]], s: 0.7 },
  up: { c: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]], s: 1.0 }, down: { c: [[0, 0, 0], [0, 0, 1], [1, 0, 1], [1, 0, 0]], s: 0.5 } };
let minX = 9, maxX = -9, minY = 9, maxY = -9, minZ = 0, maxZ = -9, onScreen = 0, total = 0;
for (const el of model.elements) {
  const [fx, fy, fz] = el.from, [tx2, ty2, tz2] = el.to;
  for (const fn in el.faces) {
    const face = FACES[fn]; if (!face) continue; const col = avgCol(el.faces[fn].uv) || [255, 0, 255];
    const world = face.c.map(([a, b, c]) => { let [wx, wy, wz] = elRot(el, fx + a * (tx2 - fx), fy + b * (ty2 - fy), fz + c * (tz2 - fz));
      return disp(wx / 16, wy / 16, wz / 16); });
    for (const [wx, wy, wz] of world) { minX = Math.min(minX, wx); maxX = Math.max(maxX, wx); minY = Math.min(minY, wy); maxY = Math.max(maxY, wy); minZ = Math.min(minZ, wz); maxZ = Math.max(maxZ, wz); }
    const pj = world.map(w => project(...w));
    total += 2; for (const p of pj) if (p && p[0] >= 0 && p[0] < W && p[1] >= 0 && p[1] < H) onScreen++;
    tri(pj[0], pj[1], pj[2], col, face.s); tri(pj[0], pj[2], pj[3], col, face.s);
  }
}
fs.writeFileSync(`scratchpad/fp_${id}.png`, enc(W, H, img));
console.log(`fp_${id}.png  worldbox x[${minX.toFixed(2)},${maxX.toFixed(2)}] y[${minY.toFixed(2)},${maxY.toFixed(2)}] z[${minZ.toFixed(2)},${maxZ.toFixed(2)}]  onscreen-verts ${onScreen}`);
