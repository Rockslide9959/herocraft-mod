// v0.14.21 Iron Man texture pass.
//
//   node scratchpad/gen_v01421_ironman_textures.js [previewDir]
//
// Regenerates, deterministically, from the repo root:
//   1. the 17 Iron Man crafting-material item sprites (16x16)
//   2. one blueprint sprite per blueprint item (blank + 7 marks)
//   3. the 28 Iron Man armour item icons + the Mark V suitcase icon
//   4. the Iron Man missile entity texture (textures/entity/iron_man_missile.png)
//   5. the clean repaint of the Mark III / 4 / V / 6 / VII armour skins (USER APPROVED in v0.14.21),
//      done to match the user's own hand-painted mark_1.png / mark_2.png: tiny palette, flat 2-3 tone
//      shading per material, clear panel lines, no stray speckle, cyan glow on eyes / reactors.
//      The opaque-pixel mask of every skin is preserved EXACTLY (so geometry/UV coverage is untouched)
//      except that isolated single overlay pixels (no 4-neighbour) are dropped as noise. The inputs are
//      the pre-repaint skins frozen in scratchpad/v01421_armor_src/, so re-running is idempotent.
//
// If a previewDir is given, 8x nearest-neighbour contact sheets are written there for eyeballing.
// No image libraries exist in this project (no python, no canvas): hand-rolled PNG codec on Node zlib.
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/projecthero');
const ITEM = path.join(ASSETS, 'textures/item');
const ARMOR = path.join(ASSETS, 'textures/armor');
const ENTITY = path.join(ASSETS, 'textures/entity');
const SRC = path.join(__dirname, 'v01421_armor_src');
const PREVIEW = process.argv[2] || null;

// ================================================================ PNG codec
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(t, 'ascii'), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, c]); };
function encode(w, h, data) {
  const s = w * 4, raw = Buffer.alloc((s + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (s + 1)] = 0; data.copy(raw, y * (s + 1) + 1, y * s, y * s + s); }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
/** Decodes 8-bit RGBA / RGB / palette PNGs (all the inputs this script reads). */
function decode(file) {
  const b = fs.readFileSync(file); let o = 8, w, h, bd, ct, idat = [], plte = null, trns = null;
  while (o < b.length) {
    const len = b.readUInt32BE(o), type = b.toString('ascii', o + 4, o + 8), d = b.subarray(o + 8, o + 8 + len);
    if (type === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); bd = d[8]; ct = d[9]; if (d[12]) throw new Error('interlaced ' + file); }
    else if (type === 'IDAT') idat.push(d); else if (type === 'PLTE') plte = d; else if (type === 'tRNS') trns = d;
    o += 12 + len;
  }
  if (bd !== 8) throw new Error('bit depth ' + bd + ' ' + file);
  const chs = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct], st = w * chs, raw = zlib.inflateSync(Buffer.concat(idat)), px = Buffer.alloc(st * h);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (st + 1)];
    for (let x = 0; x < st; x++) {
      const a = x >= chs ? px[y * st + x - chs] : 0, up = y ? px[(y - 1) * st + x] : 0, c = (x >= chs && y) ? px[(y - 1) * st + x - chs] : 0;
      let v = raw[y * (st + 1) + 1 + x];
      if (f === 1) v += a; else if (f === 2) v += up; else if (f === 3) v += (a + up) >> 1;
      else if (f === 4) { const p = a + up - c, pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c); v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? up : c); }
      px[y * st + x] = v & 255;
    }
  }
  const out = Buffer.alloc(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    let r, g, bl, al = 255; const k = i * chs;
    if (ct === 6) { r = px[k]; g = px[k + 1]; bl = px[k + 2]; al = px[k + 3]; }
    else if (ct === 2) { r = px[k]; g = px[k + 1]; bl = px[k + 2]; }
    else if (ct === 3) { const j = px[k]; r = plte[j * 3]; g = plte[j * 3 + 1]; bl = plte[j * 3 + 2]; if (trns && j < trns.length) al = trns[j]; }
    else if (ct === 0) { r = g = bl = px[k]; } else { r = g = bl = px[k]; al = px[k + 1]; }
    out[i * 4] = r; out[i * 4 + 1] = g; out[i * 4 + 2] = bl; out[i * 4 + 3] = al;
  }
  return { w, h, data: out };
}

// ================================================================ colours + canvas
const hex = s => { s = s.replace('#', ''); return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16), 255]; };
/** palette string 'aabbcc ddeeff ...' -> array of colours, darkest first */
const mix = (a, b, t) => [0, 1, 2].map(k => Math.round(a[k] + (b[k] - a[k]) * t)).concat(255);
const ramp = s => s.trim().split(/\s+/).map(hex);

class Canvas {
  constructor(w = 16, h = 16) { this.w = w; this.h = h; this.data = Buffer.alloc(w * h * 4); }
  inb(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h; }
  get(x, y) { if (!this.inb(x, y)) return null; const i = (y * this.w + x) * 4; return this.data[i + 3] ? [this.data[i], this.data[i + 1], this.data[i + 2], this.data[i + 3]] : null; }
  set(x, y, c) { x = Math.round(x); y = Math.round(y); if (!c || !this.inb(x, y)) return; const i = (y * this.w + x) * 4; this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2]; this.data[i + 3] = c.length > 3 ? c[3] : 255; }
  clear(x, y) { if (this.inb(x, y)) this.data[(y * this.w + x) * 4 + 3] = 0; }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  hline(x0, x1, y, c) { for (let x = x0; x <= x1; x++) this.set(x, y, c); }
  vline(x, y0, y1, c) { for (let y = y0; y <= y1; y++) this.set(x, y, c); }
  /** Draws from an ASCII map: each char looks up a colour in `key` ('.' / ' ' = skip). */
  map(x0, y0, rows, key) { rows.forEach((r, j) => [...r].forEach((ch, i) => { if (key[ch]) this.set(x0 + i, y0 + j, key[ch]); })); }
  disc(cx, cy, r, c) { for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++) for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++) if ((x - cx) ** 2 + (y - cy) ** 2 <= r * r) this.set(x, y, c); }
  /** Adds a 1-px outline around every opaque pixel: `dark` on the bottom/right (shadow) side, `lite` (or dark) top/left. */
  outline(dark, lite) {
    const src = Buffer.from(this.data), op = (x, y) => this.inb(x, y) && src[(y * this.w + x) * 4 + 3] > 0;
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      if (op(x, y)) continue;
      const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(([dx, dy]) => op(x + dx, y + dy));
      if (!n.length) continue;
      const shadowSide = op(x - 1, y) || op(x, y - 1); // the pixel sits right of / below the sprite
      this.set(x, y, shadowSide ? dark : (lite || dark));
    }
  }
  save(file) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, encode(this.w, this.h, this.data)); }
}

// ================================================================ preview sheets
function sheet(imgs, S, cols, file, gap = 6) {
  if (!PREVIEW) return;
  const cw = Math.max(...imgs.map(i => i.w)) * S + gap, chh = Math.max(...imgs.map(i => i.h)) * S + gap;
  const rows = Math.ceil(imgs.length / cols), W = cols * cw + gap, H = rows * chh + gap, d = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const k = (y * W + x) * 4, v = ((x >> 3) + (y >> 3)) & 1 ? 66 : 78; d[k] = v; d[k + 1] = v; d[k + 2] = v + 6; d[k + 3] = 255; }
  imgs.forEach((im, n) => {
    const ox = gap + (n % cols) * cw, oy = gap + Math.floor(n / cols) * chh;
    for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) {
      const s = (y * im.w + x) * 4, a = im.data[s + 3] / 255; if (!a) continue;
      for (let dy = 0; dy < S; dy++) for (let dx = 0; dx < S; dx++) { const k = ((oy + y * S + dy) * W + ox + x * S + dx) * 4; for (let c = 0; c < 3; c++) d[k + c] = Math.round(im.data[s + c] * a + d[k + c] * (1 - a)); }
    }
  });
  fs.mkdirSync(PREVIEW, { recursive: true });
  fs.writeFileSync(path.join(PREVIEW, file), encode(W, H, d));
}

module.exports = { Canvas, ramp, hex, decode, encode };
// ================================================================ shared palettes (darkest -> lightest)
const P = {
  iron: ramp('2b2e33 4d5259 7b8189 a9afb6 d6dadf'),
  gun: ramp('1a1c21 2b2f36 41464f 5b616b 7b828d'),
  copper: ramp('3e1c0c 6e3417 a5541f d27a35 f2a965'),
  gold: ramp('5a3a0c 8f6116 c4911f e6bc3c fbe38a'),
  red: ramp('3d080b 6b1014 9c1c1f c3302b e0564a'),
  silver: ramp('3a3f47 6a717b 9aa2ac c6ccd3 eef1f4'),
  glow: ramp('0f5f73 1aa6c4 5ff6ff b8fdff f2ffff'),
  pcb: ramp('0f2a14 1c4a22 2b6b31 3f8f45 63b265'),
  navy: ramp('0b1426 142443 1f3666 2e4e8c 4a72b8'),
  flame: ramp('7a2a06 c5520e f28a1e ffc04a fff1b0'),
  leather: ramp('2e1a0e 4f2e18 70452a 8f5e3c b07f58'),
};
const OUT_DARK = hex('18191d');

/** A shaded disc: rim lit top-left, shaded bottom-right; tones index into ramp r (2 = flat base). */
function shadedDisc(c, cx, cy, rad, r, opts = {}) {
  const inner = opts.inner || 0;
  for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
    const dx = x + 0.5 - cx, dy = y + 0.5 - cy, d = Math.hypot(dx, dy);
    if (d > rad || d < inner) continue;
    let t = 2;
    const rim = d > rad - 1.1, inRim = inner && d < inner + 1.1;
    const lit = (dx + dy) < -0.3, shad = (dx + dy) > 0.3;
    if (rim) t = lit ? 3 : shad ? 1 : 2;
    if (inRim) t = lit ? 1 : shad ? 3 : 2; // the hole's far wall catches the light
    c.set(x, y, r[t + (opts.shift || 0)]);
  }
}
/** Gear: radius rad body, `teeth` teeth of depth td. */
function gear(c, cx, cy, rad, teeth, td, r, hole) {
  for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
    const dx = x + 0.5 - cx, dy = y + 0.5 - cy, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    const tooth = Math.cos(a * teeth + 0.4) > -0.1;
    const R = tooth ? rad : rad - td;
    if (d > R || d < hole) continue;
    const lit = (dx + dy) < -0.4, shad = (dx + dy) > 0.4;
    let t = 2;
    if (d > R - 1.05) t = lit ? 3 : shad ? 1 : 2;
    if (d < hole + 1.05) t = lit ? 1 : shad ? 3 : 2;
    c.set(x, y, r[t]);
  }
}
const itemOut = (name, c) => { c.save(path.join(ITEM, name + '.png')); return c; };

// ================================================================ 1. crafting materials
const MATERIALS = {};
function mat(name, fn) { const c = new Canvas(); fn(c); MATERIALS[name] = itemOut(name, c); }

mat('copper_wiring', c => {
  // a coiled bundle of copper wire: three wound strands, loose end with a bare tinned tip
  // two wound strands with one clean groove between them; light from the top-left
  const cu = P.copper;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x + 0.5 - 7, dy = y + 0.5 - 7, d = Math.hypot(dx, dy);
    if (d > 6.3 || d < 2.9) continue;
    const lit = (dx + dy) < -1, shad = (dx + dy) > 1.5;
    let t = lit ? 3 : shad ? 1 : 2;
    if (d > 4.45 && d < 5.15) t = shad ? 0 : 1;           // groove between the strands
    else if (lit && (d > 5.6 || (d > 3.6 && d < 4.2))) t = 4; // specular on each strand
    c.set(x, y, cu[t]);
  }
  // loose end trailing off bottom-right, bare silver tip
  c.map(10, 11, ['C', '.C', '.cC', '..cS'], { c: cu[1], C: cu[3], S: P.silver[4] });
  c.outline(cu[0], cu[0]);
});

mat('metal_plating', c => {
  // a thick iron plate seen at an angle: lit top face, front edge, shaded side; four rivets
  const r = P.iron;
  c.map(1, 3, [
    '....LLLLLLLLLL',
    '...LMMMMMMMMMLs',
    '..LMMMMMMMMMMLs',
    '.LMMMMMMMMMMLss',
    'LMMMMMMMMMMMLss',
    'BBBBBBBBBBBBss.',
    'bbbbbbbbbbbbs..',
  ], { L: r[4], M: r[3], B: r[2], b: r[1], s: r[1] });
  // panel seam + rivets
  for (let i = 0; i < 9; i++) c.set(4 + i, 5, r[2]);
  c.set(6, 4, r[1]); c.set(12, 4, r[1]); c.set(4, 7, r[1]); c.set(10, 7, r[1]);
  c.set(5, 4, r[4]); c.set(11, 4, r[4]); c.set(3, 7, r[4]); c.set(9, 7, r[4]);
  // a second plate stacked underneath
  c.map(1, 10, ['LMMMMMMMMMMMLss', 'BBBBBBBBBBBBBs.', 'bbbbbbbbbbbbs..'], { L: r[3], M: r[2], B: r[2], b: r[1], s: r[1] });
  c.hline(1, 12, 10, r[3]);
  c.outline(r[0], r[0]);
});

mat('basic_circuit', c => {
  const g = P.pcb, au = P.gold;
  c.rect(2, 3, 12, 10, g[2]);
  c.hline(2, 13, 3, g[3]); c.vline(2, 3, 12, g[3]); c.hline(2, 13, 12, g[1]); c.vline(13, 3, 12, g[1]);
  // traces
  c.hline(3, 5, 5, au[2]); c.vline(5, 5, 7, au[2]); c.hline(10, 12, 10, au[2]); c.vline(10, 8, 10, au[2]);
  c.hline(3, 4, 10, au[2]); c.set(12, 5, au[2]); c.set(12, 6, au[2]);
  // chip
  c.rect(6, 6, 4, 4, P.gun[1]); c.hline(6, 9, 6, P.gun[3]);
  for (const x of [6, 8]) { c.set(x + 1, 5, P.silver[2]); c.set(x + 1, 10, P.silver[2]); }
  c.set(5, 8, P.silver[2]); c.set(10, 7, P.silver[2]);
  // copper pads + one cyan LED
  c.set(3, 4, au[3]); c.set(12, 11, au[3]); c.set(3, 11, au[3]);
  c.set(12, 4, P.glow[3]); c.set(11, 4, P.glow[1]);
  c.outline(g[0], g[0]);
});

mat('mechanical_parts', c => {
  // big iron gear + a brass pinion meshed into it, plus a bolt
  gear(c, 6.5, 6.5, 6.2, 8, 1.5, P.iron, 1.9);
  c.outline(P.iron[0], P.iron[0]);
  const c2 = new Canvas();
  gear(c2, 12, 12, 3.9, 6, 1.2, P.copper.slice(1), 1.0);
  c2.outline(P.copper[0]);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const v = c2.get(x, y); if (v) c.set(x, y, v); }
});

mat('titanium_gold_alloy', c => {
  // ingot: gold-titanium (pale gold) with a red Stark stripe
  const au = P.gold, rd = P.red;
  c.map(1, 4, [
    '....UUUUUUUUU..',
    '...UTTTTTTTTTt.',
    '..UTTRRRRRRTTtt',
    '.UTTTTTTTTTTttt',
    'UUUUUUUUUUUttt.',
    'TTTTTTTTTTTtt..',
    'TTRRRRRRRRRt...',
    'tttttttttttt...',
  ], { U: au[4], T: au[3], t: au[1], R: rd[3] });
  c.set(3, 9, au[4]); c.set(4, 9, au[4]);
  c.outline(au[0], au[0]);
});

mat('titanium_gold_plate', c => {
  // curved armour plate: gold rim, red centre panel -- Stark colours
  const au = P.gold, rd = P.red;
  c.map(2, 1, [
    'UUUUUUUUUUUt',
    'UTTTTTTTTTTt',
    'UTQRRRRRRTTt',
    'UTRRRRRRRpTt',
    'UTRRRRRRRpTt',
    'UTRRRRRRRpTt',
    'UTRRRRRRRpTt',
    'UTRRRRRRRpTt',
    '.UTRRRRRppTt',
    '.UTTRRRRpTt.',
    '..UTTRRpTt..',
    '...UTTTTt...',
    '....Uttt....',
  ], { U: au[4], T: au[3], t: au[1], R: rd[2], Q: rd[4], p: rd[1] });
  c.set(4, 4, rd[4]); c.set(4, 5, rd[3]); c.set(4, 6, rd[3]);
  c.outline(au[0], au[0]);
});

mat('servo_motor', c => {
  const s = P.iron, cu = P.copper;
  // cylindrical body (horizontal), end cap on the left, shaft + pinion on the right
  c.map(1, 4, [
    '.LLLLLLLLLL',
    'LMMMMMMMMMM',
    'MMCCCCCCMMM',
    'MMcccccccMM',
    'MMMMMMMMMMM',
    'BBBBBBBBBBB',
    'BBBBBBBBBBB',
    '.bbbbbbbbbb',
  ], { L: s[4], M: s[3], B: s[2], b: s[1], C: cu[3], c: cu[2] });
  // end cap ring
  c.vline(1, 5, 10, s[2]); c.vline(2, 5, 10, s[1]);
  // red stripe label
  c.vline(9, 5, 11, P.red[2]); c.set(9, 4, P.red[3]);
  // shaft
  c.hline(12, 13, 7, s[3]); c.hline(12, 13, 8, s[1]);
  // pinion gear
  c.map(13, 5, ['.G', 'GG', 'GG', 'Gg', 'Gg', '.g'], { G: P.gold[3], g: P.gold[1] });
  // mounting foot
  c.hline(3, 9, 12, s[1]); c.hline(3, 9, 13, s[0]);
  c.outline(s[0], s[0]);
});

mat('micro_thruster', c => {
  const s = P.silver, f = P.flame, g = P.glow;
  c.map(3, 0, [
    '...LLLL...',
    '...MMMb...',
    '..LMMMbb..',
    '..RRRRRr..',
    '..MMMMMb..',
    '.LMMMMMbb.',
    '.MMMMMMMb.',
    'LMMMMMMMbb',
    'bbbbbbbbbb',
  ], { L: s[4], M: s[3], b: s[1], R: P.red[2], r: P.red[1] });
  // nozzle mouth + exhaust
  c.map(4, 9, [
    '.ggGGgg.',
    '..gGGg..',
    '..fGGf..',
    '...ff...',
    '...F....',
  ], { g: g[2], G: g[4], f: f[3], F: f[2] });
  c.outline(s[0], s[0]);
});

mat('repulsor', c => {
  // palm repulsor: gunmetal housing, ring of vents, bright cyan emitter
  shadedDisc(c, 8, 8, 7, P.gun.slice(1).concat([P.gun[4]]));
  shadedDisc(c, 8, 8, 5.2, P.silver);
  for (let k = 0; k < 8; k++) { const a = k * Math.PI / 4; c.set(Math.floor(8 + Math.cos(a) * 4.4), Math.floor(8 + Math.sin(a) * 4.4), P.gun[1]); }
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    if (d < 3.4) c.set(x, y, d < 1.5 ? P.glow[4] : d < 2.5 ? P.glow[3] : P.glow[2]);
  }
  c.outline(OUT_DARK, OUT_DARK);
});

mat('flight_stabilizer', c => {
  // gyroscope: horizontal + vertical silver gimbal rings round a gold rotor with a cyan hub
  const s = P.silver;
  // outer gimbal: a full silver ring
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy);
    if (d <= 7 && d > 5.4) c.set(x, y, (dx + dy) < -1 ? s[4] : (dx + dy) > 1 ? s[1] : s[3]);
  }
  // inner gimbal: a tilted gold ellipse, front half drawn over the rotor later
  const ell = (x, y) => { const dx = x + 0.5 - 8, dy = y + 0.5 - 8; const e = Math.hypot(dx / 5.3, dy / 2.4); return e > 0.72 && e <= 1.0; };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (ell(x, y) && y < 8) c.set(x, y, P.gold[2]);
  // spin axis + rotor
  c.vline(7, 1, 14, s[3]); c.vline(8, 1, 14, s[1]);
  shadedDisc(c, 8, 8, 2.8, P.iron);
  c.set(7, 7, P.glow[4]); c.set(8, 7, P.glow[3]); c.set(7, 8, P.glow[3]); c.set(8, 8, P.glow[2]);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (ell(x, y) && y >= 8) c.set(x, y, P.gold[3]);
  // axle pins top/bottom
  c.set(8, 1, s[4]); c.set(7, 1, s[4]); c.set(7, 14, s[1]); c.set(8, 14, s[1]);
  c.outline(s[0], s[0]);
});

mat('targeting_module', c => {
  const g = P.gun;
  c.rect(1, 3, 14, 10, g[2]);
  c.hline(1, 14, 3, g[4]); c.vline(1, 3, 12, g[3]); c.hline(1, 14, 12, g[1]); c.vline(14, 3, 12, g[1]);
  // lens
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 6.5, y + 0.5 - 8);
    if (d < 4.2) c.set(x, y, d > 3.3 ? P.silver[3] : P.navy[d < 1.8 ? 3 : 2]);
  }
  // red reticle
  const R = P.red[4];
  c.set(6, 5, R); c.set(6, 6, R); c.set(6, 9, R); c.set(6, 10, R); c.set(3, 7, R); c.set(4, 7, R); c.set(8, 7, R); c.set(9, 7, R);
  c.set(6, 7, P.flame[4]);
  c.set(5, 6, P.navy[4]);
  // side readout + status LEDs
  c.vline(12, 5, 9, P.glow[2]); c.set(11, 5, P.glow[1]); c.set(11, 7, P.glow[1]);
  c.set(12, 11, P.red[3]);
  // antenna
  c.vline(12, 1, 2, P.silver[2]); c.set(12, 0, P.red[3]);
  c.outline(OUT_DARK, OUT_DARK);
});

mat('stark_circuit', c => {
  const n = P.navy, au = P.gold;
  c.rect(1, 2, 14, 12, n[2]);
  c.hline(1, 14, 2, n[3]); c.vline(1, 2, 13, n[3]); c.hline(1, 14, 13, n[1]); c.vline(14, 2, 13, n[1]);
  // gold traces radiating out of the core chip
  c.hline(2, 4, 7, au[3]); c.hline(11, 13, 8, au[3]); c.vline(7, 3, 4, au[3]); c.vline(8, 11, 12, au[3]);
  c.hline(3, 5, 4, au[2]); c.vline(3, 4, 5, au[2]); c.hline(10, 12, 11, au[2]); c.vline(12, 10, 11, au[2]);
  // core chip: gold frame + glowing cyan die
  c.rect(5, 5, 6, 6, au[3]); c.hline(5, 10, 10, au[1]); c.vline(10, 5, 10, au[1]); c.hline(5, 9, 5, au[4]);
  c.rect(6, 6, 4, 4, P.glow[2]); c.rect(7, 7, 2, 2, P.glow[4]);
  // LED dots
  c.set(13, 3, P.glow[3]); c.set(2, 12, P.glow[3]); c.set(13, 12, P.red[4]);
  c.outline(n[0], n[0]);
});

mat('suit_computer', c => {
  const g = P.gun;
  c.rect(2, 1, 12, 14, g[2]);
  c.hline(2, 13, 1, g[4]); c.vline(2, 1, 14, g[3]); c.hline(2, 13, 14, g[1]); c.vline(13, 1, 14, g[1]);
  // HUD screen
  c.rect(3, 2, 10, 8, P.navy[1]);
  c.hline(4, 9, 3, P.glow[3]); c.hline(4, 7, 5, P.glow[2]); c.hline(4, 10, 7, P.glow[2]); c.hline(4, 6, 8, P.glow[1]);
  c.set(11, 3, P.red[4]); c.set(10, 5, P.glow[1]); c.set(11, 5, P.glow[1]);
  // red + gold Stark stripe and gold keys
  c.hline(3, 12, 11, P.red[2]); c.hline(3, 12, 10, P.red[3]);
  for (const x of [4, 6, 8, 10]) c.set(x, 13, P.gold[3]);
  c.set(12, 13, P.glow[3]);
  c.outline(OUT_DARK, OUT_DARK);
});

function arcReactor(c, advanced) {
  // casing
  shadedDisc(c, 8, 8, 7.2, advanced ? [P.gold[0], P.gold[1], P.gold[2], P.gold[4]] : [P.iron[0], P.iron[1], P.iron[2], P.iron[4]]);
  // ring of coils (copper on the Mark I, red-and-silver on the advanced one)
  for (let k = 0; k < 10; k++) {
    const a = k * Math.PI / 5 + 0.3;
    const x = Math.floor(8 + Math.cos(a) * 4.9), y = Math.floor(8 + Math.sin(a) * 4.9);
    c.set(x, y, advanced ? P.silver[3] : P.copper[3]);
  }
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    if (d < 4.1 && d >= 3.2) c.set(x, y, advanced ? P.red[2] : P.copper[1]);
    if (d < 3.2) c.set(x, y, d < 1.3 ? P.glow[4] : d < 2.3 ? P.glow[3] : P.glow[2]);
  }
  if (advanced) { // brighter: glow spills into the coil gaps
    for (let k = 0; k < 10; k++) { const a = k * Math.PI / 5 + 0.3 + Math.PI / 10; c.set(Math.floor(8 + Math.cos(a) * 4.9), Math.floor(8 + Math.sin(a) * 4.9), P.glow[1]); }
    c.set(6, 6, P.glow[4]);
  }
  c.outline(OUT_DARK, OUT_DARK);
}
mat('arc_reactor', c => arcReactor(c, false));
mat('advanced_arc_reactor', c => arcReactor(c, true));

mat('reactor_core', c => {
  // energy cell: glass cylinder with a glowing palladium core between steel caps
  const s = P.iron, g = P.glow;
  c.map(4, 0, [
    '.LLLLLL.',
    'LMMMMMMb',
    'BBBBBBBb',
    '.gGGGGg.',
    '.gGWWGg.',
    '.gGWWGg.',
    '.gGWWGg.',
    '.gGWWGg.',
    '.gGWWGg.',
    '.gGWWGg.',
    '.gGGGGg.',
    'LMMMMMMb',
    'BBBBBBBb',
    '.bbbbbb.',
  ], { L: s[4], M: s[3], B: s[2], b: s[1], g: g[1], G: g[2], W: g[4] });
  c.set(5, 4, g[4]); c.set(5, 5, g[3]);
  // red band on the caps
  c.hline(5, 10, 2, P.red[2]); c.hline(5, 10, 12, P.red[2]);
  // steel bars over the glass
  c.vline(5, 4, 9, s[2]);
  c.outline(s[0], s[0]);
});

mat('missile_module', c => {
  // shoulder micro-missile pod: gunmetal launcher box with three red-tipped missiles poking out the top
  const s = P.silver, rd = P.red, g = P.gun;
  c.map(1, 1, [
    '.RR..RR..RR...',
    'RRrRRRrRRRr...',
    'SSsSSSsSSSs...',
    'SSsSSSsSSSs...',
    'SSsSSSsSSSs...',
    'LLLLLLLLLLLLL.',
    'MMMMMMMMMMMMbb',
    'MKKKMKKKMKKKbb',
    'MKFKMKFKMKFKbb',
    'MKKKMKKKMKKKbb',
    'MMMMMMMMMMMMbb',
    'RRRRRRRRRRRRbb',
    'BBBBBBBBBBBBb.',
  ], { R: rd[3], r: rd[1], S: s[3], s: s[1], L: g[4], M: g[3], b: g[1], B: g[1], K: g[0], F: P.flame[2] });
  c.set(2, 1, rd[4]); c.set(5, 1, rd[4]); c.set(8, 1, rd[4]);
  c.hline(1, 12, 12, rd[2]);
  c.outline(OUT_DARK, OUT_DARK);
});

// ================================================================ 2. blueprints
// Blue paper, white grid, a white bust of the mark (head + shoulders + reactor) and its roman numeral
// in the bottom-right corner. Blank = grid only, with a folded corner like the others.
const BP = { line: hex('0e2550'), paper: hex('1f56a8'), paper2: hex('1a4b94'), grid: hex('3d78c6'), edge: hex('6f9fdc'), ink: hex('eef6ff'), ink2: hex('b9d4f5') };
const BUSTS = {
  // 9 wide; x = ink, o = soft ink (glow / reactor), . = paper
  mark_1: ['.xxxxxx..', '.x.xx.x..', '.xxxxxx..', '.x.x.xx..', '.xxxxxx..', 'xxxxxxxx.', 'xx.xx.xx.', 'xxxooxxx.', 'xxxxxxxx.'],
  mark_2: ['..xxxx...', '.xxxxxx..', '.x.xx.x..', '.xxxxxx..', '..xxxx...', '.xxxxxx..', 'xxxxxxxx.', 'xxxooxxx.', 'xxxooxxx.'],
  mark_iii: ['..xxxx...', '.x.xx.x..', '.o.xx.o..', '.xx..xx..', '..xxxx...', 'xx.xx.xx.', 'xxxxxxxx.', 'xxxooxxx.', 'xx.xx.xx.'],
  mark_4: ['..xxxx...', '.x.xx.x..', '.o.xx.o..', '.xx..xx..', '..xxxx...', 'x.xxxx.x.', 'xxxxxxxx.', 'x.xoox.x.', 'x.xxxx.x.'],
  mark_v: ['..xxxx...', '.xxxxxx..', '.o.xx.o..', '.xxxxxx..', '.xxxxxx..', 'xxx..xxx.', 'xxxxxxxx.', 'xx.oo.xx.', 'xxxxxxxx.'],
  mark_6: ['..xxxx...', '.x.xx.x..', '.o.xx.o..', '.xx..xx..', '..xxxx...', 'xxxxxxxx.', 'xxoooxxx.', 'xxxoxxxx.', 'xxxxxxxx.'],
  mark_vii: ['..xxxx...', '.x.xx.x..', '.o.xx.o..', '.xx..xx..', 'x.xxxx.x.', 'xxxxxxxx.', 'xxxooxxx.', 'xxxooxxx.', 'xx.xx.xx.'],
};
const NUMERAL = { mark_1: 'I', mark_2: 'II', mark_iii: 'III', mark_4: 'IV', mark_v: 'V', mark_6: 'VI', mark_vii: 'VII' };
const GLYPH = { I: ['x', 'x', 'x', 'x'], V: ['x.x', 'x.x', 'x.x', '.x.'] };
const BLUEPRINTS = {};
function blueprint(name, bust, numeral) {
  const c = new Canvas();
  // sheet with a folded top-right corner
  for (let y = 1; y <= 14; y++) for (let x = 1; x <= 14; x++) {
    if (x - y > 10) continue; // folded corner cut
    let col = BP.paper;
    if (x % 3 === 1 || y % 3 === 1) col = BP.grid;
    if (x === 1 || y === 1) col = BP.edge;
    if (x === 14 || y === 14) col = BP.paper2;
    c.set(x, y, col);
  }
  // the fold itself: a lighter triangle
  c.set(11, 1, BP.edge); c.set(12, 2, BP.edge); c.set(13, 3, BP.edge); c.set(14, 4, BP.edge);
  c.set(11, 2, BP.ink2); c.set(11, 3, BP.ink2); c.set(12, 3, BP.ink2); c.set(11, 4, BP.edge); c.set(12, 4, BP.edge); c.set(13, 4, BP.edge);
  if (bust) c.map(3, 3, bust, { x: BP.ink, o: BP.ink2 });
  if (numeral) {
    const glyphs = [...numeral].map(ch => GLYPH[ch]);
    const w = glyphs.reduce((s, g) => s + g[0].length, 0) + glyphs.length - 1;
    let x = 13 - w + 1;
    // clear a paper box behind the numeral so it reads over the grid
    c.rect(x - 1, 9, w + 2, 6, BP.paper);
    for (const g of glyphs) { c.map(x, 10, g, { x: BP.ink }); x += g[0].length + 1; }
  }
  c.outline(BP.line, BP.line);
  BLUEPRINTS[name] = itemOut(name, c);
}
blueprint('blank_blueprint', null, null);
for (const m of Object.keys(BUSTS)) blueprint(m + '_blueprint', BUSTS[m], NUMERAL[m]);

// ================================================================ 3. armour item icons
const T_HELMET = [
  '................',
  '.....OOOOOO.....',
  '...OOQQQQPPOO...',
  '..OQQPPPPPPPpO..',
  '..OQPPPPPPPPpO..',
  '.OQPPUTTTTTPPpO.',
  '.OQPUTTTTTTTPpO.',
  '.OQPTGGTTGGTPpO.',
  '.OPPTTTTTTTtPpO.',
  '.OpPTTTTTTTtPpO.',
  '.OppPTTttTTPppO.',
  '..OpPTKKKKtPpO..',
  '..OppTTTTTtppO..',
  '...OppTTTTppO...',
  '....OOttttOO....',
  '.....OOOOOO.....',
];
const T_HELMET_MK1 = [
  '................',
  '...OOOOOOOOOO...',
  '..OQQQQQQQQPpO..',
  '..OQrPPPPPPrpO..',
  '..OQPPPPPPPPpO..',
  '..OQPPPPPPPPpO..',
  '..OKKKKPPKKKKO..',
  '..OQPPPPPPPPpO..',
  '..OQPPPPPPPPpO..',
  '..OQPKPKPKPKpO..',
  '..OQPKPKPKPKpO..',
  '..OQrPPPPPPrpO..',
  '..OQPPPPPPPPpO..',
  '..OTTTTTTTTTTO..',
  '..OttttttttttO..',
  '...OOOOOOOOOO...',
];
const T_CHEST = [
  '................',
  '.OOOO......OOOO.',
  'OQQPPOOOOOOPPPpO',
  'OQPPPQQQQQQPPPpO',
  'OQTPPPPPPPPPPTpO',
  'OQTPPPgGGgPPPTpO',
  'OAAOPPGWWGPPOAaO',
  'OAAOPPgGGgPPOAaO',
  'OAAOPPPPPPPPOAaO',
  'OAAOPTTTTTTPOAaO',
  'OOOOPPPPPPPPOOOO',
  '...OQPPTTPPpO...',
  '...OQPPTTPPpO...',
  '...OPTTTTTTpO...',
  '...OOOOOOOOOO...',
  '................',
];
const T_LEGS = [
  '................',
  '.OOOOOOOOOOOOOO.',
  '.OUUUUUUUUUUUUO.',
  '.OTTTTTttTTTTtO.',
  '.OQPPPPPPPPPPpO.',
  '.OQPPpO..OQPPpO.',
  '.OQPPpO..OQPPpO.',
  '.OQPPpO..OQPPpO.',
  '.OTTTtO..OTTTtO.',
  '.OQPPpO..OQPPpO.',
  '.OQPPpO..OQPPpO.',
  '.OQPPpO..OQPPpO.',
  '.OQPPpO..OQPPpO.',
  '.OppppO..OppppO.',
  '.OOOOOO..OOOOOO.',
  '................',
];
const T_BOOTS = [
  '................',
  '................',
  '................',
  '................',
  '................',
  '.OOOOO....OOOOO.',
  '.OQPpO....OQPpO.',
  '.OQPpO....OQPpO.',
  '.OTTtO....OTTtO.',
  '.OQPpO....OQPpO.',
  'OQPPpO...OQPPpO.',
  'OQPPPpO..OQPPPpO',
  'OQPPPpO..OQPPPpO',
  'OpppppO..OpppppO',
  'OKKgKKO..OKKgKKO',
  'OOOOOOO..OOOOOOO',
];
const MARKS = {
  mark_1: { P: P.iron.slice(1, 4), T: P.leather.slice(1, 4), out: P.iron[0], glow: [hex('9fb4bf'), hex('d8eef5'), hex('f2fbff')], helmet: T_HELMET_MK1, rivet: P.iron[4] },
  mark_2: { P: ramp('6f7884 9aa3ae c4cbd3'), T: ramp('8a939e c9d0d8 eef2f6'), out: hex('2c3138') },
  mark_iii: { P: P.red.slice(1, 4), T: P.gold.slice(1, 4).concat([]), out: P.red[0] },
  mark_4: { P: ramp('5e0e18 8a1824 b02a35'), T: ramp('a07c2e cfa84f efd58c'), out: hex('300509') },
  mark_v: { P: ramp('6e1414 a1201e c8372e'), T: ramp('6a717b a9b0ba e2e6eb'), out: hex('380909') },
  mark_6: { P: P.red.slice(1, 4), T: P.gold.slice(1, 4), out: P.red[0], A: P.silver.slice(1, 4), triangle: true },
  mark_vii: { P: ramp('7a1416 b0201e d8392e'), T: ramp('a5761b e0ad33 fbd968'), out: hex('3a080a'), A: P.silver.slice(1, 4), heavy: true },
};
const ARMOR_ICONS = [];
function armourIcon(mark, piece) {
  const m = MARKS[mark];
  const tmpl = piece === 'helmet' ? (m.helmet || T_HELMET) : piece === 'chestplate' ? T_CHEST : piece === 'leggings' ? T_LEGS : T_BOOTS;
  tmpl.forEach((r, i) => { if (r.length !== 16) throw new Error(`${piece} row ${i} is ${r.length} wide`); });
  const glow = m.glow || [P.glow[1], P.glow[3], P.glow[4]];
  const A = m.A || m.P;
  const key = {
    O: m.out, p: m.P[0], P: m.P[1], Q: m.P[2], t: m.T[0], T: m.T[1], U: m.T[2],
    a: A[0], A: A[1], g: glow[0], G: glow[1], W: glow[2], K: hex('1b1d22'), r: m.rivet || m.P[2],
  };
  const c = new Canvas();
  c.map(0, 0, tmpl, key);
  if (piece === 'chestplate') {
    if (m.triangle) { // Mark 6: triangular reactor in a silver housing
      c.map(5, 5, ['SgWWgS', 'PSggSP', 'PPSSPP'], { S: P.silver[3], g: glow[0], W: glow[2], P: m.P[1] });
    }
    if (m.heavy) { c.map(1, 2, ['UU', 'TT'], { U: m.T[2], T: m.T[1] }); c.map(12, 2, ['TTt'], { T: m.T[1], t: m.T[0] }); }
    if (mark === 'mark_1') { // leather harness straps crossing the chest + dim reactor
      c.map(4, 3, ['T.......', '.T.....T', '..T...T.'], { T: m.T[1] });
      [[1, 3], [14, 3], [5, 11], [10, 11]].forEach(([x, y]) => c.set(x, y, m.rivet));
    }
  }
  if (piece === 'boots' && m.A) { c.map(1, 8, ['STTs'], { S: P.silver[4], T: P.silver[3], s: P.silver[2] }); c.map(10, 8, ['STTs'], { S: P.silver[4], T: P.silver[3], s: P.silver[2] }); }
  if (piece === 'leggings' && mark === 'mark_6') { c.map(2, 8, ['UUU'], { U: m.T[2] }); c.map(10, 8, ['UUU'], { U: m.T[2] }); }
  if (mark === 'mark_4') { // Mark 4's own trim: gold pinstripes (forehead, torso flanks, outer legs)
    if (piece === 'helmet') c.hline(6, 9, 3, m.T[1]);
    if (piece === 'chestplate') { c.vline(4, 11, 12, m.T[1]); c.vline(11, 11, 12, m.T[0]); }
    if (piece === 'leggings') { c.vline(2, 9, 12, m.T[1]); c.vline(13, 9, 12, m.T[0]); }
  }
  const name = 'iron_man_' + mark + '_' + piece;
  ARMOR_ICONS.push(itemOut(name, c));
}
for (const mark of Object.keys(MARKS)) for (const piece of ['helmet', 'chestplate', 'leggings', 'boots']) armourIcon(mark, piece);

// Mark V suitcase: red briefcase, silver band + handle, gold latches, a small cyan status light
const SUITCASE = new Canvas();
SUITCASE.map(0, 0, [
  '................',
  '................',
  '......OOOO......',
  '.....OSssSO.....',
  '.....OS..sO.....',
  '.OOOOOOOOOOOOOO.',
  '.OQQQQQQQQQQQrO.',
  '.ORRRRRRRRRRRrO.',
  '.OUUUGUUUUGUUsO.',
  '.OsssgsssssgssO.',
  '.ORRRRRRRRRRRrO.',
  '.ORRRRRRRRRRRrO.',
  '.ORRRRRcRRRRRrO.',
  '.OrrrrrrrrrrrrO.',
  '.OOOOOOOOOOOOOO.',
  '................',
], { O: hex('2a0709'), Q: P.red[4], R: P.red[3], r: P.red[1], S: P.silver[4], s: P.silver[2], U: P.silver[4], G: P.gold[4], g: P.gold[2], c: P.glow[3] });
itemOut('mark_v_suitcase', SUITCASE);

// ================================================================ 4. missile entity texture (32x16)
const MISSILE = new Canvas(32, 16);
{
  const s = P.silver, rd = P.red;
  // body strip: x 0..15 = tail..nose; rows 0..3 = light..shadow
  const shade = [s[4], s[3], s[3], s[1]], redS = [rd[4], rd[3], rd[3], rd[1]];
  for (let x = 0; x < 16; x++) for (let y = 0; y < 4; y++) {
    let col = shade[y];
    if (x < 2) col = [P.gun[3], P.gun[2], P.gun[2], P.gun[1]][y];
    else if (x === 5) col = [s[2], s[1], s[1], s[0]][y]; // panel seam
    else if (x >= 11 && x <= 12) col = redS[y];
    MISSILE.set(x, y, col);
  }
  // nose cone (u 16..19)
  for (let x = 16; x < 20; x++) for (let y = 0; y < 4; y++) MISSILE.set(x, y, redS[y]);
  MISSILE.set(19, 1, rd[4]); MISSILE.set(19, 2, rd[4]);
  // nozzle cap (u 20..23)
  MISSILE.rect(20, 0, 4, 4, P.gun[1]); MISSILE.rect(21, 1, 2, 2, P.flame[3]);
  // fin (u 0..5, v 4..7)
  MISSILE.rect(0, 4, 6, 4, rd[2]); MISSILE.hline(0, 5, 4, rd[3]); MISSILE.hline(0, 5, 7, rd[1]); MISSILE.vline(5, 4, 7, rd[1]);
  // exhaust plume (u 0..15, v 8..15), u 0 = at the nozzle
  for (let x = 0; x < 16; x++) for (let y = 8; y < 16; y++) {
    const t = x / 15, dy = Math.abs(y + 0.5 - 12) / 4; // 0 centre .. 1 edge
    const width = 1 - 0.7 * t;
    if (dy > width) continue;
    const k = dy / width;
    let col = k < 0.35 && t < 0.45 ? P.glow[4] : k < 0.6 && t < 0.7 ? P.flame[4] : t < 0.85 ? P.flame[2] : P.flame[1];
    if (k < 0.35 && t < 0.2) col = hex('ffffff');
    const a = Math.round(255 * Math.max(0, Math.min(1, (1 - t) * 1.25)) * (1 - 0.5 * k));
    if (a < 12) continue;
    MISSILE.set(x, y, [col[0], col[1], col[2], a]);
  }
  MISSILE.save(path.join(ENTITY, 'iron_man_missile.png'));
}

// ================================================================ 5. armour skin repaint
// Standard 64x64 player-skin layout: base boxes + their overlay (second-layer) boxes. The mark geos
// draw the base boxes at inflate 0.2, the overlay boxes at ~0.5, and the accent plates (faceplate,
// chest plate, gauntlets, thigh plates, knees, boots) sample sub-rects of the overlay regions.
const BOXES = [
  ['head', 0, 0, 8, 8, 8, 'base'], ['head', 32, 0, 8, 8, 8, 'over'],
  ['body', 16, 16, 8, 12, 4, 'base'], ['body', 16, 32, 8, 12, 4, 'over'],
  ['rarm', 40, 16, 4, 12, 4, 'base'], ['rarm', 40, 32, 4, 12, 4, 'over'],
  ['rleg', 0, 16, 4, 12, 4, 'base'], ['rleg', 0, 32, 4, 12, 4, 'over'],
  ['lleg', 16, 48, 4, 12, 4, 'base'], ['lleg', 0, 48, 4, 12, 4, 'over'],
  ['larm', 32, 48, 4, 12, 4, 'base'], ['larm', 48, 48, 4, 12, 4, 'over'],
];
const REGION = new Array(64 * 64).fill(null);
for (const [part, u, v, w, h, d, layer] of BOXES) {
  const f = { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h] };
  for (const [face, [x0, y0, fw, fh]] of Object.entries(f))
    for (let y = y0; y < y0 + fh; y++) for (let x = x0; x < x0 + fw; x++)
      REGION[y * 64 + x] = { part, layer, face, x0, y0, w: fw, h: fh, fx: x - x0, fy: y - y0, id: part + layer + face };
}
const reg = (x, y) => (x < 0 || y < 0 || x > 63 || y > 63) ? null : REGION[y * 64 + x];

function hsv(r, g, b) {
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), dl = mx - mn; let hh = 0;
  if (dl) { if (mx === r) hh = ((g - b) / dl) % 6; else if (mx === g) hh = (b - r) / dl + 2; else hh = (r - g) / dl + 4; hh *= 60; if (hh < 0) hh += 360; }
  return { h: hh, s: mx ? dl / mx : 0, v: mx / 255 };
}
/** Material class of an old skin pixel. */
function classify(r, g, b) {
  const { h, s, v } = hsv(r, g, b);
  if ((b > 170 && g > 170 && b >= r + 25) || (r > 215 && g > 225 && b > 225)) return 'glow';
  if (v < 0.17) return 'dark';
  if (s < 0.2) return v < 0.36 ? 'dark' : 'silver';
  if (h >= 28 && h <= 70) return s < 0.3 && v > 0.6 ? 'silver' : 'gold';
  if (h < 28 || h > 320) return v < 0.22 ? 'dark' : 'red';
  if (h > 170 && h < 260) return v > 0.55 ? 'glow' : 'silver';
  return 'silver';
}
const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const N8 = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];

/**
 * Per-mark scheme. Ramps run darkest -> lightest: [panel line, shadow, base, light].
 *   gold  = the trim material (silver for Mark V)
 *   forearm = material forced onto both forearms (Mark 6 / VII)
 */
const SCHEMES = {
  iii: { red: ramp('4a0d10 7c1418 a3201f c03a2f'), gold: ramp('7a5414 b7861f e0b43a f6dc7a'), metal: ramp('3a3d44 5e636c 8d939c b9bec6') },
  4:   { red: ramp('420a12 70131d 951b25 b23440'), gold: ramp('76531d b08532 d6ad52 f0d78d'), metal: ramp('35383f 585d66 868c95 b3b8c0'), pinstripe: true },
  v:   { red: ramp('4c0e0e 7f1716 a82420 c43d32'), gold: ramp('4a4f58 7a818c a9b0ba d4d9e0'), metal: ramp('3a3d44 5e636c 8d939c b9bec6') },
  6:   { red: ramp('4a0c10 7d1419 a61f22 c43a33'), gold: ramp('7a5416 b98a24 e2b941 f8e086'), metal: ramp('454a53 747b86 a3aab4 d0d5dc'), forearm: 'metal', triangle: true, knee: true },
  vii: { red: ramp('520d0f 8a1618 b5221f d34336'), gold: ramp('82591a c4922a ebc24c fde798'), metal: ramp('474c55 777e89 a8afb9 d6dbe2'), forearm: 'metal', heavy: true },
};
const GLOW = ramp('1f8fa6 5ff6ff c4ffff');

function repaintMark(id) {
  const sc = SCHEMES[id];
  const src = decode(path.join(SRC, 'mark_' + id + '.png'));
  const op = new Uint8Array(4096), cls = new Array(4096).fill(null), lum = new Float32Array(4096);
  for (let i = 0; i < 4096; i++) {
    if (!src.data[i * 4 + 3]) continue;
    const [r, g, b] = [src.data[i * 4], src.data[i * 4 + 1], src.data[i * 4 + 2]];
    op[i] = 1; cls[i] = classify(r, g, b); lum[i] = 0.3 * r + 0.59 * g + 0.11 * b;
  }
  const isOp = (x, y) => x >= 0 && y >= 0 && x < 64 && y < 64 && op[y * 64 + x];
  const same = (x, y, x2, y2) => { const a = reg(x, y), b = reg(x2, y2); return a && b && a.id === b.id; };
  // (a) drop isolated overlay pixels -- the only mask change allowed
  let dropped = 0;
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const i = y * 64 + x, r = reg(x, y);
    if (!op[i] || !r || r.layer !== 'over') continue;
    if (!N4.some(([dx, dy]) => isOp(x + dx, y + dy))) { op[i] = 0; cls[i] = null; dropped++; }
  }
  // (b) majority-clean the material map inside each face (2 passes); glow pixels are left alone
  for (let pass = 0; pass < 2; pass++) {
    const next = cls.slice();
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const i = y * 64 + x; if (!op[i] || cls[i] === 'glow') continue;
      const cnt = {}; let tot = 0;
      for (const [dx, dy] of N8) { const j = (y + dy) * 64 + x + dx; if (!isOp(x + dx, y + dy) || !same(x, y, x + dx, y + dy) || cls[j] === 'glow') continue; cnt[cls[j]] = (cnt[cls[j]] || 0) + 1; tot++; }
      if (!tot) continue;
      const mine = cnt[cls[i]] || 0;
      const best = Object.entries(cnt).sort((a, b) => b[1] - a[1])[0];
      const n4same = N4.filter(([dx, dy]) => isOp(x + dx, y + dy) && same(x, y, x + dx, y + dy) && cls[(y + dy) * 64 + x + dx] === cls[i]).length;
      if (best[0] !== cls[i] && mine === 0 && n4same === 0) next[i] = best[0];
    }
    for (let i = 0; i < 4096; i++) cls[i] = next[i];
  }
  // (c) identity overrides
  const mat = cls.map(c => c === 'gold' ? 'gold' : c);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const i = y * 64 + x, r = reg(x, y); if (!op[i] || !r) continue;
    const arm = r.part === 'rarm' || r.part === 'larm';
    if (sc.forearm && arm && ((r.face !== 'top' && r.face !== 'bottom' && r.fy >= 6) || r.face === 'bottom') && mat[i] !== 'glow' && mat[i] !== 'dark') mat[i] = 'forearm';
  }
  // (d) panel lines: old pixels clearly darker than their material's median are kept as line work,
  //     but only when they form a line (>=1 same-tone 4-neighbour) -- single dark specks vanish.
  const med = {};
  for (const m of ['red', 'gold', 'silver']) { const ls = []; for (let i = 0; i < 4096; i++) if (op[i] && cls[i] === m) ls.push(lum[i]); ls.sort((a, b) => a - b); med[m] = ls.length ? ls[ls.length >> 1] : 128; }
  const tone = new Int8Array(4096).fill(2); // index into ramp: 0 line, 1 shadow, 2 base, 3 light
  for (let i = 0; i < 4096; i++) if (op[i] && med[cls[i]]) { const q = lum[i] / med[cls[i]]; if (q < 0.74) tone[i] = 1; else if (q > 1.22) tone[i] = 3; }
  // Connected runs of one tone (same material, same face) survive only when they are LINE-shaped
  // (area small relative to their length); blotches and single specks fall back to the flat base.
  const tone2 = Int8Array.from(tone), seen = new Uint8Array(4096);
  for (let s0 = 0; s0 < 4096; s0++) {
    if (!op[s0] || tone[s0] === 2 || seen[s0]) continue;
    const comp = [s0], q = [s0]; seen[s0] = 1;
    while (q.length) {
      const i = q.pop(), x = i % 64, y = (i / 64) | 0;
      for (const [dx, dy] of N4) {
        const j = (y + dy) * 64 + x + dx;
        if (!isOp(x + dx, y + dy) || seen[j] || tone[j] !== tone[s0] || cls[j] !== cls[s0] || !same(x, y, x + dx, y + dy)) continue;
        seen[j] = 1; comp.push(j); q.push(j);
      }
    }
    const xs = comp.map(i => i % 64), ys = comp.map(i => (i / 64) | 0);
    const len = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)) + 1;
    const lineLike = comp.length >= 2 && comp.length <= len * 1.5;
    if (!lineLike) for (const i of comp) tone2[i] = 2;
  }
  // (e) structural shading: trim gets a lit top edge / shaded bottom edge, red against trim gets a
  //     dark seam, every face's bottom row is a step darker (reads as a plate edge), side faces' last
  //     column too.
  const out = new Canvas(64, 64);
  const rampOf = m => m === 'red' ? sc.red : m === 'gold' ? sc.gold : m === 'forearm' ? sc[sc.forearm] : m === 'silver' ? sc.metal : m === 'dark' ? [sc.red[0], sc.red[0], sc.metal[0], sc.metal[1]] : null;
  const at = (x, y) => isOp(x, y) ? mat[y * 64 + x] : null;
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const i = y * 64 + x; if (!op[i]) continue;
    const r = reg(x, y), m = mat[i];
    if (m === 'glow') {
      const edge = N4.some(([dx, dy]) => at(x + dx, y + dy) !== 'glow');
      out.set(x, y, edge ? GLOW[1] : GLOW[2]);
      continue;
    }
    const rp = rampOf(m); let t = tone2[i];
    if (m === 'dark') { out.set(x, y, rp[t >= 2 ? 2 : 0]); continue; }
    const nb = (dx, dy) => (r && same(x, y, x + dx, y + dy)) ? at(x + dx, y + dy) : null;
    const up = nb(0, -1), dn = nb(0, 1), lf = nb(-1, 0), rt = nb(1, 0);
    const trimLike = mm => mm === 'gold' || mm === 'forearm' || mm === 'silver';
    if (m === 'red' && [up, dn, lf, rt].some(trimLike) && t >= 2) t = sc.heavy ? 0 : 1;
    if (trimLike(m) && t === 2) { if (up && up !== m) t = 3; else if (dn && dn !== m) t = 1; }
    if (r && r.fy === r.h - 1 && r.face !== 'top' && r.face !== 'bottom' && t === 2) t = 1;
    let c = rp[Math.max(0, Math.min(3, t))];
    // soft top light: the upper third of every side face is a half-step brighter (two flat tones, like mark_2)
    if (t === 2 && r && r.face !== 'top' && r.face !== 'bottom' && r.fy < Math.ceil(r.h / 3)) c = mix(rp[2], rp[3], 0.38);
    out.set(x, y, c);
  }
  // (f) mark-specific details painted on top (only ever onto already-opaque pixels)
  const paint = (x, y, c) => { if (isOp(x, y)) out.set(x, y, c); };
  if (sc.triangle) {
    // Mark 6's triangular arc reactor, centred where the old round one glowed on the chest front
    let sx = 0, sy = 0, n = 0;
    for (let y = 20; y < 32; y++) for (let x = 20; x < 28; x++) if (op[y * 64 + x] && cls[y * 64 + x] === 'glow') { sx += x; sy += y; n++; }
    const cx = n ? Math.round(sx / n - 0.5) : 23, cy = n ? Math.round(sy / n) - 1 : 22;
    const tri = ['ooooo', 'occco', '.oco.', '..o..'];
    const key = { o: GLOW[1], c: GLOW[2] };
    for (let y = 20; y < 32; y++) for (let x = 20; x < 28; x++) if (op[y * 64 + x] && cls[y * 64 + x] === 'glow') out.set(x, y, sc.metal[1]);
    tri.forEach((row, j) => [...row].forEach((ch, k) => { if (key[ch]) paint(cx - 2 + k, cy + j, key[ch]); }));
    // housing ring
    [[-3, 0], [3, 0], [-2, 2], [2, 2], [-1, 3], [1, 3], [0, 4]].forEach(([dx, dy]) => paint(cx + dx, cy + dy, sc.metal[2]));
  }
  if (sc.knee) for (const [x0, y0] of [[4, 26], [20, 54]]) for (let k = 0; k < 4; k++) paint(x0 + k, y0, sc.gold[2]); // knee line on both leg fronts
  if (sc.pinstripe) {
    // Mark 4: thin gold pinstripe down the outer side of each upper arm and thigh
    for (const [x, y0] of [[40, 20], [52 - 1, 20], [0, 20], [16 + 8 + 3, 52]]) for (let y = y0; y < y0 + 6; y++) if (isOp(x, y) && mat[y * 64 + x] === 'red') out.set(x, y, sc.gold[1]);
  }
  const colours = new Set(); for (let i = 0; i < 4096; i++) if (out.data[i * 4 + 3]) colours.add(out.data.readUInt32BE(i * 4));
  return { out, dropped, colours: colours.size, cls };
}

const MARK_IDS = ['iii', '4', 'v', '6', 'vii'];
const repainted = {};
for (const id of MARK_IDS) {
  const r = repaintMark(id);
  r.out.save(path.join(ARMOR, 'mark_' + id + '.png'));
  repainted[id] = r.out;
  console.log('armor/mark_' + id + '.png  colours ' + r.colours + '  dropped stray overlay px ' + r.dropped);
}
if (PREVIEW) {
  const m2 = decode(path.join(ARMOR, 'mark_2.png')), m1 = decode(path.join(ARMOR, 'mark_1.png'));
  for (const id of MARK_IDS) sheet([decode(path.join(SRC, 'mark_' + id + '.png')), repainted[id], m2], 8, 3, 'armor_' + id + '.png');
  sheet([m1, m2, ...MARK_IDS.map(id => repainted[id])], 5, 4, 'armor_all.png');
}
sheet(Object.values(MATERIALS), 8, 6, 'materials.png');
sheet(Object.values(BLUEPRINTS), 8, 8, 'blueprints.png');
sheet(ARMOR_ICONS.concat([SUITCASE]), 8, 8, 'armour_icons.png');
sheet([MISSILE], 12, 1, 'missile_texture.png');

