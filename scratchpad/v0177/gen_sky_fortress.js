// v0.17.7 Sky Fortress: every generated resource -- six player-model skins (trooper, Pad Sergeant, gunner, Deck Officer,
// two prisoners; painted over the shipped Syndicate skins thug_a / thug_c), block textures (launch pad, animated turbine,
// animated bridge shield, vault seal, parachute crate, wind vent), item textures, models, blockstates, loot tables, the
// structure + structure set + biome tag, the Locator tier 4 tag and mineable tags. Admiral Kestrel is gen_kestrel.js.
//   node scratchpad/v0177/gen_sky_fortress.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const A = path.join(root, 'src/main/resources/assets/projecthero');
const D = path.join(root, 'src/main/resources/data/projecthero');
const DM = path.join(root, 'src/main/resources/data/minecraft');

// ---------------------------------------------------------------- PNG in / out
const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePng(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
function decodePng(file) {
  const b = fs.readFileSync(file);
  let o = 8, w, h, depth, type, plte = null, trns = null; const idat = [];
  while (o < b.length) {
    const len = b.readUInt32BE(o); const ty = b.toString('ascii', o + 4, o + 8); const d = b.subarray(o + 8, o + 8 + len);
    if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; if (d[12]) throw new Error('interlaced ' + file); }
    else if (ty === 'PLTE') plte = d; else if (ty === 'tRNS') trns = d; else if (ty === 'IDAT') idat.push(d);
    o += 12 + len;
  }
  if (depth !== 8) throw new Error('bit depth ' + depth + ' ' + file);
  const ch = { 6: 4, 2: 3, 3: 1, 0: 1, 4: 2 }[type];
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * ch; const out = Buffer.alloc(w * h * 4); const prev = Buffer.alloc(stride); const cur = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x]; const a = x >= ch ? cur[x - ch] : 0; const up = prev[x]; const c = x >= ch ? prev[x - ch] : 0;
      let r;
      if (f === 0) r = v; else if (f === 1) r = v + a; else if (f === 2) r = v + up; else if (f === 3) r = v + ((a + up) >> 1);
      else { const p = a + up - c; const pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c); r = v + (pa <= pb && pa <= pc ? a : pb <= pc ? up : c); }
      cur[x] = r & 255;
    }
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      if (type === 6) { cur.copy(out, i, x * 4, x * 4 + 4); }
      else if (type === 2) { out[i] = cur[x * 3]; out[i + 1] = cur[x * 3 + 1]; out[i + 2] = cur[x * 3 + 2]; out[i + 3] = 255; }
      else if (type === 3) { const p = cur[x]; out[i] = plte[p * 3]; out[i + 1] = plte[p * 3 + 1]; out[i + 2] = plte[p * 3 + 2]; out[i + 3] = trns && p < trns.length ? trns[p] : 255; }
      else if (type === 0) { out[i] = out[i + 1] = out[i + 2] = cur[x]; out[i + 3] = 255; }
      else { out[i] = out[i + 1] = out[i + 2] = cur[x * 2]; out[i + 3] = cur[x * 2 + 1]; }
    }
    cur.copy(prev);
  }
  return { w, h, px: out };
}

let seed = 1777;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255];
const shade = (c, f) => c.map(v => clamp(v * f));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const lum = (r, g, b) => 0.299 * r + 0.587 * g + 0.114 * b;
function canvas(w = 16, h = 16, px = null) {
  px = px || Buffer.alloc(w * h * 4);
  const c = {
    w, h, px,
    set(x, y, col, a = 255) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = a; },
    get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
    fill(col, noise = 0) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const n = (rnd() - 0.5) * noise; c.set(x, y, col.map(v => v + n)); } },
    rect(x0, y0, x1, y1, col, noise = 0) { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const n = (rnd() - 0.5) * noise; c.set(x, y, col.map(v => v + n)); } },
    line(x0, y0, x1, y1, col) { const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1; for (let i = 0; i <= n; i++) c.set(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, col); },
    map(rows, pal) { rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) c.set(x, y, pal[ch]); })); },
    outline(col) {
      const op = (x, y) => x >= 0 && y >= 0 && x < w && y < h && px[(y * w + x) * 4 + 3] > 0;
      const add = [];
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (!op(x, y) && (op(x - 1, y) || op(x + 1, y) || op(x, y - 1) || op(x, y + 1))) add.push([x, y]);
      add.forEach(([x, y]) => c.set(x, y, col));
    },
    png() { return encodePng(w, h, px); },
  };
  return c;
}
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');
const tex = (rel, c) => write(path.join(A, 'textures', rel + '.png'), c.png());
const T = path.join(A, 'textures/entity');
const isSkinTone = (r, g, b) => r > 120 && r > g && g > b && r - b > 30 && r - b < 130 && g > 70;
const inHead = (x, y) => y < 16 && x < 32;
const inHat = (x, y) => y < 16 && x >= 32;
const clearRect = (c, x0, y0, x1, y1) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) c.set(x, y, [0, 0, 0], 0); };
const tone = (base, l, lo = 0.45, hi = 1.25) => shade(base, lo + (hi - lo) * Math.min(1, l / 200));

// ================================================================ v0.17.7 Sky Fortress
const SKY = path.join(T, 'sky_fortress');
const skyTex = (name, c) => write(path.join(SKY, name + '.png'), c.png());

// ---------------------------------------------------------------- skin helpers (64x64 player layout)
// each face as [x0, y0, w, h]
const F = {
  head: { top: [8, 0, 8, 8], bottom: [16, 0, 8, 8], right: [0, 8, 8, 8], front: [8, 8, 8, 8], left: [16, 8, 8, 8], back: [24, 8, 8, 8] },
  hat: { top: [40, 0, 8, 8], bottom: [48, 0, 8, 8], right: [32, 8, 8, 8], front: [40, 8, 8, 8], left: [48, 8, 8, 8], back: [56, 8, 8, 8] },
  body: { top: [20, 16, 8, 4], bottom: [28, 16, 8, 4], right: [16, 20, 4, 12], front: [20, 20, 8, 12], left: [28, 20, 4, 12], back: [32, 20, 8, 12] },
  jacket: { top: [20, 32, 8, 4], bottom: [28, 32, 8, 4], right: [16, 36, 4, 12], front: [20, 36, 8, 12], left: [28, 36, 4, 12], back: [32, 36, 8, 12] },
  rarm: { top: [44, 16, 4, 4], bottom: [48, 16, 4, 4], right: [40, 20, 4, 12], front: [44, 20, 4, 12], left: [48, 20, 4, 12], back: [52, 20, 4, 12] },
  larm: { top: [36, 48, 4, 4], bottom: [40, 48, 4, 4], right: [32, 52, 4, 12], front: [36, 52, 4, 12], left: [40, 52, 4, 12], back: [44, 52, 4, 12] },
  rleg: { top: [4, 16, 4, 4], bottom: [8, 16, 4, 4], right: [0, 20, 4, 12], front: [4, 20, 4, 12], left: [8, 20, 4, 12], back: [12, 20, 4, 12] },
  lleg: { top: [20, 48, 4, 4], bottom: [24, 48, 4, 4], right: [16, 52, 4, 12], front: [20, 52, 4, 12], left: [24, 52, 4, 12], back: [28, 52, 4, 12] },
};
const SIDES = ['right', 'front', 'left', 'back'];
// light from the front-left: side faces darker, back darkest
const FACE_LIGHT = { top: 1.12, bottom: 0.7, right: 0.86, front: 1.0, left: 0.9, back: 0.8 };
/** Paints a face with fn(u, v, w, h) -> colour or null (u/v local), applying the face light. */
function paint(c, part, face, fn) {
  const [x0, y0, w, h] = F[part][face];
  for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) {
    const col = fn(u, v, w, h, face);
    if (col === null || col === undefined) continue;
    if (col.length === 4 && col[3] === 0) { c.set(x0 + u, y0 + v, [0, 0, 0], 0); continue; }
    c.set(x0 + u, y0 + v, shade(col, FACE_LIGHT[face]));
  }
}
function paintAll(c, part, fn, faces = ['top', 'bottom', ...SIDES]) { for (const f of faces) paint(c, part, f, fn); }
const clearPart = (c, part) => { for (const f of Object.keys(F[part])) { const [x0, y0, w, h] = F[part][f]; clearRect(c, x0, y0, x0 + w - 1, y0 + h - 1); } };
const grain = (col, n = 10) => col.map(v => v + (rnd() - 0.5) * n);
/** An armour plate inside a face: bevel highlight top-left, shadow bottom-right, a little grain. */
function plateFn(base, x0, y0, x1, y1) {
  return (u, v) => {
    if (u < x0 || u > x1 || v < y0 || v > y1) return null;
    if (v === y0 || u === x0) return shade(base, 1.22);
    if (v === y1 || u === x1) return shade(base, 0.68);
    return grain(base, 8);
  };
}
function base64(srcName) {
  const img = decodePng(path.join(T, 'syndicate', srcName));
  return canvas(img.w, img.h, Buffer.from(img.px));
}

const GUN = [88, 94, 106];      // gunmetal armour
const GUN_D = [56, 60, 70];
const NAVY = [30, 40, 70];      // undersuit / livery
const NAVY_D = [20, 26, 46];
const ORANGE = [242, 132, 34];  // visor / Kestrel accent
const BLACK = [22, 22, 26];
const GOLD = [214, 176, 64];

/** The fortress crew kit over thug_c's body: navy undersuit with folds kept, gunmetal plates, gloves, boots, a helmet. */
function crewKit(opts = {}) {
  const c = base64('thug_c.png');
  // undersuit: recolour every non-head pixel to navy, keeping the source's light/dark folds
  for (const part of ['body', 'rarm', 'larm', 'rleg', 'lleg']) {
    paintAll(c, part, (u, v, w, h, face) => {
      const [x0, y0] = F[part][face];
      const [r, g, b] = c.get(x0 + u, y0 + v);
      const l = lum(r, g, b);
      return tone(NAVY, l, 0.7, 1.45);
    });
  }
  for (const part of ['jacket', 'hat']) clearPart(c, part);
  clearPart(c, 'rarm'.replace('rarm', 'rarm')); // keep
  // overlay layers of arms / legs cleared (sleeves, pants)
  for (const [x0, y0, x1, y1] of [[40, 32, 55, 47], [48, 48, 63, 63], [0, 32, 15, 47], [0, 48, 15, 63]]) clearRect(c, x0, y0, x1, y1);
  // repaint arms / legs (the clearPart above removed rarm) with the navy suit
  for (const part of ['rarm', 'larm']) paintAll(c, part, (u, v) => grain(v < 2 ? NAVY : shade(NAVY, 0.92 + (v % 4 === 0 ? -0.06 : 0)), 8));
  // chest plate (body front) with a centre ridge and the Kestrel chevron
  const vest = opts.vest || GUN;
  paint(c, 'body', 'front', plateFn(vest, 0, 1, 7, 8));
  paint(c, 'body', 'front', (u, v) => (v >= 2 && v <= 7 && (u === 3 || u === 4)) ? shade(vest, 1.08) : null);
  if (!opts.noEmblem) {
    paint(c, 'body', 'front', (u, v) => ((v === 3 && (u === 2 || u === 5)) || (v === 4 && (u === 3 || u === 4))) ? ORANGE : null);
  }
  paint(c, 'body', 'back', plateFn(vest, 0, 1, 7, 8));
  paint(c, 'body', 'back', (u, v) => (v >= 2 && v <= 6 && u >= 2 && u <= 5) ? (u === 2 || u === 5 ? shade(GUN_D, 1.1) : grain(GUN_D, 6)) : null); // jet-pack mount
  for (const f of ['right', 'left']) paint(c, 'body', f, plateFn(vest, 0, 1, 3, 8));
  paint(c, 'body', 'top', (u, v) => grain(vest, 6));
  // collar
  paint(c, 'body', 'front', (u, v) => v === 0 ? (u >= 2 && u <= 5 ? NAVY_D : shade(vest, 0.9)) : null);
  // belt with buckle and pouches
  for (const f of SIDES) paint(c, 'body', f, (u, v, w) => v === 9 ? BLACK : v === 10 ? ((f === 'front' && (u === 3 || u === 4)) ? GOLD : [46, 44, 40]) : null);
  paint(c, 'body', 'front', (u, v) => (v === 11 && (u === 1 || u === 6)) ? [60, 56, 48] : null);
  // shoulder pads + upper arm plates; gloves; elbow line
  for (const part of ['rarm', 'larm']) {
    paint(c, part, 'top', (u, v) => grain(opts.shoulder || GUN, 6));
    for (const f of SIDES) {
      paint(c, part, f, plateFn(opts.shoulder || GUN, 0, 0, 3, 2));
      paint(c, part, f, (u, v) => v >= 10 ? (v === 10 ? [44, 44, 48] : grain(BLACK, 6)) : v === 6 ? shade(NAVY, 0.7) : null);
    }
    paint(c, part, 'bottom', () => BLACK);
  }
  // legs: knee pads, thigh straps, boots
  for (const part of ['rleg', 'lleg']) {
    for (const f of SIDES) {
      paint(c, part, f, (u, v) => v >= 8 ? (v === 8 ? [70, 72, 78] : v === 11 ? [34, 34, 38] : grain([40, 40, 44], 6)) : v === 2 ? shade(NAVY, 0.65) : null);
    }
    paint(c, part, 'front', plateFn(GUN, 0, 4, 3, 6));
    paint(c, part, 'bottom', () => [28, 28, 30]);
  }
  // the helmet (hat layer): gunmetal shell, navy crest stripe, a wraparound visor
  const visor = opts.visor || ORANGE;
  const shell = opts.helmet || GUN;
  paint(c, 'hat', 'top', (u, v) => (u === 3 || u === 4) ? (opts.crest || NAVY) : (u === 0 || u === 7 || v === 0 || v === 7) ? shade(shell, 0.85) : grain(shell, 8));
  for (const f of SIDES) {
    paint(c, 'hat', f, (u, v) => {
      if (v === 0) return shade(shell, 1.15);
      if (v >= 2 && v <= 4 && f !== 'back') {
        // the visor wraps the front and both sides
        if (f === 'front') return v === 2 ? mix(visor, [255, 240, 200], 0.55) : v === 3 ? (u === 1 || u === 2 ? [255, 236, 200] : visor) : shade(visor, 0.6);
        return v === 3 ? shade(visor, 0.75) : shade(visor, 0.55);
      }
      if (v >= 5) {
        if (f === 'front') return (v === 6 && u >= 2 && u <= 5) ? BLACK : (v === 7 ? shade(shell, 0.7) : grain(shell, 6)); // breather
        return grain(shell, 6);
      }
      if (f === 'back' && (u === 3 || u === 4)) return opts.crest || NAVY;
      return grain(shell, 8);
    });
  }
  paint(c, 'hat', 'bottom', () => [0, 0, 0, 0]);
  return c;
}

// the deck trooper
{
  const c = crewKit();
  skyTex('sky_trooper', c);
}
// the Pad Sergeant: high-vis deck vest over the kit, orange helmet, ear defenders
{
  const c = crewKit({ helmet: [226, 120, 36], crest: [240, 240, 236], visor: [70, 110, 160] });
  const HV = [236, 236, 228], REF = [230, 214, 60];
  for (const f of ['front', 'back', 'right', 'left']) {
    paint(c, 'jacket', f, (u, v, w) => {
      if (v > 9) return [0, 0, 0, 0];
      if (f === 'front' && (u === 3 || u === 4) && v < 9) return [0, 0, 0, 0]; // open vest
      if (v === 3 || v === 7) return REF;
      return grain(HV, 6);
    });
  }
  paint(c, 'jacket', 'top', (u) => (u <= 1 || u >= 6) ? HV : [0, 0, 0, 0]);
  // ear defenders on the helmet sides
  for (const f of ['right', 'left']) paint(c, 'hat', f, (u, v) => (u >= 2 && u <= 5 && v >= 3 && v <= 6) ? (v === 3 || u === 2 ? [70, 70, 76] : [36, 36, 40]) : null);
  // sergeant stripes on the sleeves
  for (const part of ['rarm', 'larm']) paint(c, part, 'right', (u, v) => (v === 3 || v === 4) && u > 0 && u < 3 ? GOLD : null);
  skyTex('pad_sergeant', c);
}
// the gunner: dark flak vest, red shoulder stripes, ammo pouches, red crest
{
  const RED = [176, 34, 32];
  const c = crewKit({ vest: [64, 68, 74], shoulder: [74, 78, 86], crest: RED });
  for (const part of ['rarm', 'larm']) for (const f of SIDES) paint(c, part, f, (u, v) => v === 1 ? RED : null);
  paint(c, 'body', 'front', (u, v) => (v >= 5 && v <= 7 && u !== 3 && u !== 4) ? (v === 5 ? [96, 90, 70] : [74, 70, 54]) : null);
  paint(c, 'body', 'front', (u, v) => (v >= 1 && v <= 8 && (u + v) % 7 === 0 && u < 3) ? [40, 38, 34] : null); // bandolier
  skyTex('sky_gunner', c);
}
// the Deck Officer: navy dress tunic, gold braid and epaulettes, white gloves and belt, peaked cap with goggles
{
  const c = base64('thug_c.png');
  const TUNIC = [26, 34, 62], TROU = [22, 26, 44], WHITE = [232, 232, 226];
  for (const part of ['body', 'rarm', 'larm', 'rleg', 'lleg']) {
    paintAll(c, part, (u, v, w, h, face) => {
      const [x0, y0] = F[part][face];
      const [r, g, b] = c.get(x0 + u, y0 + v);
      return tone(part.endsWith('leg') ? TROU : TUNIC, lum(r, g, b), 0.75, 1.45);
    });
  }
  for (const part of ['jacket', 'hat']) clearPart(c, part);
  for (const [x0, y0, x1, y1] of [[40, 32, 55, 47], [48, 48, 63, 63], [0, 32, 15, 47], [0, 48, 15, 63]]) clearRect(c, x0, y0, x1, y1);
  // double-breasted front: gold buttons, a white belt, medal ribbons, gold braid (aiguillette) from the right shoulder
  paint(c, 'body', 'front', (u, v) => {
    if (v === 0) return (u >= 3 && u <= 4) ? WHITE : GOLD; // high collar with gold edge
    if (v === 9) return u === 3 || u === 4 ? GOLD : WHITE;
    if ((u === 2 || u === 5) && (v === 3 || v === 5 || v === 7)) return GOLD;
    if (v === 2 && u >= 5 && u <= 7) return [[170, 30, 30], [40, 90, 180], [220, 200, 60]][u - 5];
    if ((u === 0 && v >= 1 && v <= 5) || (u === 1 && v === 6)) return mix(GOLD, [255, 240, 160], (v % 2) * 0.4);
    if (v >= 10) return grain(shade(TUNIC, 0.85), 6); // tunic skirt
    return null;
  });
  paint(c, 'body', 'back', (u, v) => v === 9 ? WHITE : null);
  for (const f of ['right', 'left']) paint(c, 'body', f, (u, v) => v === 9 ? WHITE : null);
  for (const part of ['rarm', 'larm']) {
    paint(c, part, 'top', () => GOLD); // epaulettes
    for (const f of SIDES) paint(c, part, f, (u, v) => v === 0 ? mix(GOLD, [120, 90, 20], 0.3) : v === 9 ? GOLD : v >= 10 ? WHITE : null);
    paint(c, part, 'bottom', () => WHITE);
  }
  for (const part of ['rleg', 'lleg']) {
    for (const f of SIDES) paint(c, part, f, (u, v) => v >= 9 ? (v === 9 ? [50, 50, 54] : grain(BLACK, 4)) : null);
    paint(c, part, 'right', (u, v) => v < 9 && u === 1 ? GOLD : null); // trouser stripe
    paint(c, part, 'left', (u, v) => v < 9 && u === 2 ? GOLD : null);
    paint(c, part, 'bottom', () => [16, 16, 18]);
  }
  // a sharp moustache + a scar on the face (head front)
  for (const [x, y] of [[10, 14], [11, 14], [12, 14], [13, 14]]) c.set(x, y, [70, 46, 34]);
  for (const [x, y] of [[10, 11], [10, 12], [11, 13]]) c.set(x, y, [176, 112, 104]);
  // peaked cap with goggles strapped on
  paint(c, 'hat', 'top', (u, v) => grain(TUNIC, 6));
  for (const f of SIDES) {
    paint(c, 'hat', f, (u, v) => {
      if (v <= 1) return grain(TUNIC, 6);
      if (v === 2) return f === 'front' && (u === 3 || u === 4) ? [255, 226, 120] : GOLD; // band + badge
      if (v === 3 && f === 'front') return [12, 12, 14]; // the peak
      return [0, 0, 0, 0];
    });
  }
  paint(c, 'hat', 'front', (u, v) => (v === 1 && (u === 1 || u === 2 || u === 5 || u === 6)) ? (u === 1 || u === 6 ? [60, 40, 26] : ORANGE) : null); // goggles
  paint(c, 'hat', 'bottom', () => [0, 0, 0, 0]);
  skyTex('deck_officer', c);
}
// prisoners: a downed pilot in a torn flight suit, an engineer in a stained lab coat
{
  const c = base64('thug_a.png');
  const SUIT = [176, 112, 52];
  for (const part of ['body', 'rarm', 'larm', 'rleg', 'lleg']) {
    paintAll(c, part, (u, v, w, h, face) => {
      const [x0, y0] = F[part][face];
      const [r, g, b] = c.get(x0 + u, y0 + v);
      if (isSkinTone(r, g, b) && part.endsWith('arm') && v >= 9) return null;
      const t = tone(SUIT, lum(r, g, b), 0.65, 1.3);
      return rnd() < 0.08 ? shade(t, 0.6) : t; // grime
    });
  }
  for (const part of ['jacket', 'hat']) clearPart(c, part);
  for (const [x0, y0, x1, y1] of [[40, 32, 55, 47], [48, 48, 63, 63], [0, 32, 15, 47], [0, 48, 15, 63]]) clearRect(c, x0, y0, x1, y1);
  paint(c, 'body', 'front', (u, v) => (u === 3 && v < 9) ? [130, 130, 136] : (v === 1 && (u === 1 || u === 6)) ? [60, 90, 170] : null); // zip + patches
  paint(c, 'body', 'front', (u, v) => (v === 5 && u >= 5 && u <= 7) ? [150, 104, 84] : null); // torn: skin showing
  for (const part of ['rleg', 'lleg']) for (const f of SIDES) paint(c, part, f, (u, v) => v >= 10 ? grain([50, 40, 32], 6) : null);
  for (const [x, y] of [[9, 11], [10, 11], [9, 12]]) c.set(x, y, [120, 80, 110]); // a bruise
  skyTex('prisoner_pilot', c);
}
{
  const c = base64('thug_c.png');
  const COAT = [222, 222, 214], SHIRT = [70, 100, 150], TROU = [60, 58, 54];
  for (const part of ['body', 'rarm', 'larm', 'rleg', 'lleg']) {
    paintAll(c, part, (u, v, w, h, face) => {
      const [x0, y0] = F[part][face];
      const [r, g, b] = c.get(x0 + u, y0 + v);
      if (isSkinTone(r, g, b) && part.endsWith('arm') && v >= 10) return null;
      const base = part.endsWith('leg') ? TROU : COAT;
      const t = tone(base, lum(r, g, b), 0.8, 1.15);
      return rnd() < 0.05 ? mix(t, [120, 100, 70], 0.5) : t; // oil stains
    });
  }
  for (const part of ['jacket', 'hat']) clearPart(c, part);
  for (const [x0, y0, x1, y1] of [[40, 32, 55, 47], [48, 48, 63, 63], [0, 32, 15, 47], [0, 48, 15, 63]]) clearRect(c, x0, y0, x1, y1);
  paint(c, 'body', 'front', (u, v) => (u >= 3 && u <= 4) ? (v === 0 ? COAT : v < 6 ? (u === 3 ? [40, 40, 44] : SHIRT) : null) : (v === 3 && u === 6 ? [60, 120, 200] : null));
  // glasses
  for (const [x, y] of [[9, 11], [10, 11], [13, 11], [14, 11]]) c.set(x, y, [30, 30, 34]);
  c.set(11, 11, [30, 30, 34]); c.set(12, 11, [30, 30, 34]);
  for (const part of ['rleg', 'lleg']) for (const f of SIDES) paint(c, part, f, (u, v) => v >= 11 ? [36, 30, 26] : null);
  skyTex('prisoner_engineer', c);
}

// ---------------------------------------------------------------- block textures
function steel(base = [70, 76, 86], noise = 8) {
  const c = canvas(); c.fill(base, noise);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(base, 0.62)); c.set(i, 15, shade(base, 0.5)); c.set(0, i, shade(base, 0.62)); c.set(15, i, shade(base, 0.5)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, shade(base, 1.25)); c.set(1, i, shade(base, 1.15)); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) { c.set(x, y, shade(base, 1.6)); c.set(x + 1, y + 1, shade(base, 0.6)); }
  return c;
}
const SKYBLUE = [111, 168, 220];
const BT = path.join(A, 'textures/block');
const btex = (name, c) => write(path.join(BT, name + '.png'), c.png());
// Launch Pad: hazard rim, a blue ring with three up-chevrons, glowing core
{
  const top = canvas(); top.fill([44, 48, 56], 6);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const rim = x < 2 || y < 2 || x > 13 || y > 13;
    if (rim) top.set(x, y, (((x + y) >> 1) % 2) ? BLACK : [236, 196, 36]);
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (!rim && d > 4.3 && d < 5.4) top.set(x, y, mix(SKYBLUE, [255, 255, 255], 0.2));
  }
  for (const oy of [3, 6, 9]) for (let i = 0; i < 4; i++) { top.set(7 - i, oy + i, [180, 230, 255]); top.set(8 + i, oy + i, [180, 230, 255]); }
  btex('launch_pad_top', top);
  const side = steel([66, 70, 78]);
  for (let x = 0; x < 16; x++) for (let y = 4; y < 7; y++) side.set(x, y, (((x + y) >> 1) % 2) ? BLACK : [236, 196, 36]);
  for (let x = 3; x < 13; x++) side.set(x, 11, SKYBLUE);
  btex('launch_pad_side', side);
  btex('launch_pad_bottom', steel([50, 54, 60]));
}
// Turbine (running): a four-frame spinning fan behind a grille, blue core; wrecked: blackened, bent, ember glow
{
  const strip = canvas(16, 64);
  for (let f = 0; f < 4; f++) {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const yy = f * 16 + y;
      const dx = x - 7.5, dy = y - 7.5, d = Math.hypot(dx, dy);
      let col;
      if (x === 0 || y === 0 || x === 15 || y === 15) col = [40, 44, 50];
      else if (d > 6.6) col = grain([78, 84, 96], 6);
      else if (d > 6.0) col = [30, 32, 38];
      else if (d < 1.6) col = d < 0.9 ? [230, 250, 255] : [120, 200, 255];
      else {
        const a = Math.atan2(dy, dx) + f * (Math.PI / 8);
        const blade = Math.cos(a * 4) > 0.35;
        col = blade ? mix([170, 180, 196], [90, 100, 116], d / 6) : mix([30, 80, 140], [8, 20, 40], d / 6);
      }
      if ((y === 4 || y === 11) && d < 6.4 && d > 1.6) col = shade(col, 0.6); // grille bars
      strip.set(x, yy, col);
    }
  }
  btex('sky_turbine_on', strip);
  write(path.join(BT, 'sky_turbine_on.png.mcmeta'), JSON.stringify({ animation: { frametime: 1 } }, null, 2) + '\n');
  const off = canvas();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x - 7.5, dy = y - 7.5, d = Math.hypot(dx, dy);
    let col;
    if (x === 0 || y === 0 || x === 15 || y === 15) col = [26, 26, 28];
    else if (d > 6.6) col = grain([46, 44, 44], 12);
    else if (d > 6.0) col = [18, 18, 20];
    else {
      const a = Math.atan2(dy, dx) + 0.4;
      const blade = Math.cos(a * 4 + d * 0.5) > 0.5 && rnd() > 0.15;
      col = blade ? mix([70, 66, 64], [36, 34, 34], d / 6) : (rnd() < 0.12 ? [255, 120 + rnd() * 60, 30] : [14, 12, 12]);
    }
    off.set(x, y, col);
  }
  for (const [x, y] of [[4, 3], [5, 4], [5, 5], [6, 6], [10, 10], [11, 11], [11, 12]]) off.set(x, y, [10, 8, 8]); // cracks
  btex('sky_turbine_off', off);
}
// Bridge Shield: translucent hex lattice, 8-frame shimmer
{
  const n = 8;
  const strip = canvas(16, 16 * n);
  for (let f = 0; f < n; f++) {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const hx = (x + ((Math.floor(y / 4) % 2) * 2)) % 4;
      const edge = hx === 0 || y % 4 === 0;
      const wave = 0.5 + 0.5 * Math.sin((x + y) * 0.6 - f * (Math.PI * 2 / n));
      const a = edge ? 150 + wave * 80 : 60 + wave * 40;
      const col = edge ? mix([150, 230, 255], [255, 255, 255], wave * 0.5) : mix([60, 150, 230], [120, 210, 255], wave);
      strip.set(x, f * 16 + y, col, Math.round(a));
    }
  }
  btex('bridge_shield', strip);
  write(path.join(BT, 'bridge_shield.png.mcmeta'), JSON.stringify({ animation: { frametime: 3, interpolate: true } }, null, 2) + '\n');
}
// sealed Fortress Vault: navy steel door, orange chevron lock, sky-blue light bar
{
  const front = steel([36, 44, 66]);
  for (let i = 2; i < 14; i++) { front.set(i, 3, SKYBLUE); front.set(i, 12, shade(SKYBLUE, 0.6)); }
  for (let y = 4; y < 12; y++) for (let x = 4; x < 12; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d < 3.7) front.set(x, y, mix([60, 66, 80], [26, 30, 40], d / 3.7));
    else if (d < 4.4) front.set(x, y, [20, 22, 28]);
  }
  for (let i = 0; i < 3; i++) { front.set(6 - i, 6 + i, ORANGE); front.set(9 + i, 6 + i, ORANGE); front.set(7, 6, ORANGE); front.set(8, 6, ORANGE); }
  for (const [x, y] of [[3, 7], [3, 8], [12, 7], [12, 8]]) front.set(x, y, [160, 166, 176]);
  btex('sky_vault_seal_front', front);
  const side = steel([36, 44, 66]);
  for (let i = 2; i < 14; i++) { side.set(i, 7, shade(SKYBLUE, 0.6)); side.set(i, 8, shade(SKYBLUE, 0.4)); }
  btex('sky_vault_seal_side', side);
  const top = steel([36, 44, 66]);
  for (let i = 3; i < 13; i++) { top.set(i, i, shade(SKYBLUE, 0.5)); top.set(15 - i, i, shade(SKYBLUE, 0.5)); }
  btex('sky_vault_seal_top', top);
}
// Parachute Crate: olive wood crate, stencilled white canopy, orange straps; lid with a pull ring
{
  const OL = [88, 96, 54];
  const side = canvas(); side.fill(OL, 10);
  for (let i = 0; i < 16; i++) { side.set(i, 0, shade(OL, 0.6)); side.set(i, 15, shade(OL, 0.5)); side.set(0, i, shade(OL, 0.6)); side.set(15, i, shade(OL, 0.5)); }
  for (let y = 1; y < 15; y++) { side.set(3, y, [210, 110, 30]); side.set(12, y, [210, 110, 30]); }
  for (let x = 4; x < 12; x++) for (let y = 3; y < 9; y++) {
    const d = Math.hypot((x - 7.5) / 3.8, (y - 7) / 3.2);
    if (d < 1 && y < 7) side.set(x, y, [232, 232, 226]);
  }
  for (const [x0, x1] of [[5, 7], [10, 8]]) side.line(x0, 7, x1, 11, [232, 232, 226]);
  side.rect(7, 11, 8, 12, [232, 232, 226]);
  btex('parachute_crate_side', side);
  const top = canvas(); top.fill(shade(OL, 1.05), 10);
  for (let i = 0; i < 16; i++) { top.set(i, 0, shade(OL, 0.6)); top.set(i, 15, shade(OL, 0.5)); top.set(0, i, shade(OL, 0.6)); top.set(15, i, shade(OL, 0.5)); top.set(i, 7, shade(OL, 0.7)); }
  for (let x = 6; x < 10; x++) for (let y = 9; y < 13; y++) if (Math.abs(Math.hypot(x - 7.5, y - 10.5) - 1.6) < 0.6) top.set(x, y, [230, 60, 40]);
  btex('parachute_crate_top', top);
}
// Wind Vent: a grille with an arrow toward the block's facing (north in the texture)
{
  const top = canvas();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const rim = x < 1 || y < 1 || x > 14 || y > 14;
    top.set(x, y, rim ? [60, 64, 70] : (y % 2 === 0 ? [24, 26, 30] : [92, 98, 108]));
  }
  for (let i = 0; i < 4; i++) { top.set(7 - i, 3 + i, [200, 230, 255]); top.set(8 + i, 3 + i, [200, 230, 255]); }
  for (let y = 5; y < 13; y++) { top.set(7, y, [200, 230, 255]); top.set(8, y, [200, 230, 255]); }
  btex('wind_vent_top', top);
  btex('wind_vent_side', steel([60, 64, 70]));
}

// ---------------------------------------------------------------- item textures
const IT = path.join(A, 'textures/item');
const itex = (name, c) => write(path.join(IT, name + '.png'), c.png());
// Fortress Pass: a navy clearance card with a gold chip, the orange chevron and a blue stripe
{
  const c = canvas();
  c.rect(2, 4, 13, 12, [34, 44, 74]);
  c.rect(2, 4, 13, 4, [60, 72, 110]);
  c.rect(2, 12, 13, 12, [20, 26, 46]);
  c.rect(3, 6, 5, 8, GOLD); c.set(4, 7, [150, 120, 40]);
  for (let i = 0; i < 3; i++) { c.set(8 + i, 6 + i, ORANGE); c.set(12 - i, 6 + i, ORANGE); }
  c.rect(3, 10, 12, 10, SKYBLUE);
  c.outline([12, 14, 22]);
  itex('fortress_pass', c);
}
// Parachute: a folded pack with orange straps and a red pull ring
{
  const c = canvas();
  c.rect(4, 3, 11, 13, [96, 104, 60]);
  c.rect(4, 3, 11, 4, [120, 128, 76]);
  for (let y = 3; y <= 13; y++) { c.set(5, y, [214, 112, 34]); c.set(10, y, [214, 112, 34]); }
  c.rect(4, 8, 11, 8, [214, 112, 34]);
  c.set(7, 10, [220, 40, 30]); c.set(8, 10, [220, 40, 30]); c.set(7, 11, [220, 40, 30]); c.set(8, 11, [150, 20, 20]);
  c.outline([30, 32, 20]);
  itex('parachute', c);
}
// Kestrel's Thruster: a gunmetal jet pod with a navy band, an orange-hot nozzle and a flame
{
  const c = canvas();
  for (let y = 2; y <= 10; y++) for (let x = 5; x <= 10; x++) {
    const edge = x === 5 || x === 10;
    c.set(x, y, edge ? GUN_D : (x <= 6 ? shade(GUN, 1.25) : GUN));
  }
  c.rect(6, 2, 9, 2, shade(GUN, 1.4));
  c.rect(5, 5, 10, 6, NAVY);
  c.set(7, 5, ORANGE); c.set(8, 5, ORANGE);
  c.rect(6, 11, 9, 11, [40, 40, 46]);
  c.rect(6, 12, 9, 12, [255, 170, 60]);
  c.set(7, 13, [255, 210, 120]); c.set(8, 13, [255, 120, 30]); c.set(7, 14, [255, 90, 20]); c.set(8, 15, [200, 60, 10]);
  c.outline([14, 16, 22]);
  itex('kestrel_thruster', c);
}

// ---------------------------------------------------------------- models + blockstates
const M = path.join(A, 'models');
const BS = path.join(A, 'blockstates');
const P = 'projecthero:block/';
json(path.join(M, 'block/launch_pad.json'), { parent: 'minecraft:block/cube_bottom_top', textures: { top: P + 'launch_pad_top', side: P + 'launch_pad_side', bottom: P + 'launch_pad_bottom' } });
json(path.join(BS, 'launch_pad.json'), { variants: { '': { model: P + 'launch_pad' } } });
json(path.join(M, 'block/sky_turbine_on.json'), { parent: 'minecraft:block/cube_all', textures: { all: P + 'sky_turbine_on' } });
json(path.join(M, 'block/sky_turbine_off.json'), { parent: 'minecraft:block/cube_all', textures: { all: P + 'sky_turbine_off' } });
json(path.join(BS, 'sky_turbine.json'), { variants: { 'running=true': { model: P + 'sky_turbine_on' }, 'running=false': { model: P + 'sky_turbine_off' } } });
json(path.join(M, 'block/bridge_shield.json'), { parent: 'minecraft:block/cube_all', render_type: 'minecraft:translucent', textures: { all: P + 'bridge_shield' } });
json(path.join(BS, 'bridge_shield.json'), { variants: { '': { model: P + 'bridge_shield' } } });
json(path.join(M, 'block/sky_vault_seal.json'), { parent: 'minecraft:block/orientable', textures: { front: P + 'sky_vault_seal_front', side: P + 'sky_vault_seal_side', top: P + 'sky_vault_seal_top' } });
const facing4 = model => ({ variants: { 'facing=north': { model }, 'facing=east': { model, y: 90 }, 'facing=south': { model, y: 180 }, 'facing=west': { model, y: 270 } } });
json(path.join(BS, 'sky_vault_seal.json'), facing4(P + 'sky_vault_seal'));
json(path.join(M, 'block/parachute_crate.json'), { parent: 'minecraft:block/cube_column', textures: { end: P + 'parachute_crate_top', side: P + 'parachute_crate_side' } });
json(path.join(BS, 'parachute_crate.json'), { variants: { '': { model: P + 'parachute_crate' } } });
json(path.join(M, 'block/wind_vent.json'), { parent: 'minecraft:block/cube_bottom_top', textures: { top: P + 'wind_vent_top', side: P + 'wind_vent_side', bottom: P + 'wind_vent_side' } });
json(path.join(BS, 'wind_vent.json'), facing4(P + 'wind_vent'));
for (const [item, model] of [['launch_pad', 'launch_pad'], ['sky_turbine', 'sky_turbine_on'], ['bridge_shield', 'bridge_shield'], ['sky_vault_seal', 'sky_vault_seal'],
  ['parachute_crate', 'parachute_crate'], ['wind_vent', 'wind_vent']]) {
  json(path.join(M, 'item', item + '.json'), { parent: P + model });
}
for (const item of ['fortress_pass', 'parachute', 'kestrel_thruster']) {
  json(path.join(M, 'item', item + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + item } });
}

// ---------------------------------------------------------------- loot tables
const item = (name, min, max, extra = {}) => Object.assign({ type: 'minecraft:item', name, functions: min === undefined ? [] : [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } }] }, extra);
const pool = (entries, rolls = 1, conditions) => Object.assign({ rolls, entries }, conditions ? { conditions } : {});
const chance = c => [{ condition: 'minecraft:random_chance', chance: c }];
const killed = [{ condition: 'minecraft:killed_by_player' }];
const LT = path.join(D, 'loot_table');
json(path.join(LT, 'entities/sky_trooper.json'), { type: 'minecraft:entity', pools: [
  pool([item('projecthero:salvaged_tech', 1, 2)], 1, chance(0.35).concat(killed)),
  pool([item('minecraft:iron_nugget', 2, 5)], 1, chance(0.5)),
] });
json(path.join(LT, 'entities/sky_gunner.json'), { type: 'minecraft:entity', pools: [
  pool([item('projecthero:salvaged_tech', 1, 2)], 1, chance(0.35).concat(killed)),
  pool([item('minecraft:gunpowder', 1, 3)], 1, chance(0.5)),
] });
json(path.join(LT, 'entities/sky_prisoner.json'), { type: 'minecraft:entity', pools: [] });
json(path.join(LT, 'entities/deck_officer.json'), { type: 'minecraft:entity', pools: [
  pool([item('projecthero:hero_alloy_ingot', 2, 3)]),
  pool([item('projecthero:vibranium_shard', 1, 2)]),
  pool([item('minecraft:experience_bottle', 3, 6)]),
  pool([item('minecraft:golden_apple', 1, 1)], 1, chance(0.5)),
] });
json(path.join(LT, 'entities/admiral_kestrel.json'), { type: 'minecraft:entity', pools: [
  pool([item('projecthero:celestial_core', 2, 4)]),
  pool([item('projecthero:vibranium_shard', 3, 5)]),
  pool([item('minecraft:experience_bottle', 8, 12)]),
  pool([item('minecraft:golden_apple', 2, 2)]),
  pool([item('minecraft:netherite_scrap', 1, 1)], 1, chance(0.35)),
  pool([item('minecraft:elytra')], 1, chance(0.15)),
] });
json(path.join(LT, 'chests/sky_prisoner_reward.json'), { type: 'minecraft:chest', pools: [
  pool([
    item('projecthero:med_kit', 1, 2, { weight: 3 }),
    item('projecthero:energy_cell', 1, 1, { weight: 3 }),
    item('projecthero:cooldown_stim', 1, 1, { weight: 2 }),
    item('projecthero:hero_alloy_scrap', 3, 6, { weight: 3 }),
    item('minecraft:emerald', 3, 6, { weight: 3 }),
    item('minecraft:experience_bottle', 2, 4, { weight: 2 }),
  ]),
  pool([item('projecthero:vibranium_shard', 1, 1)], 1, chance(0.25)),
] });
json(path.join(LT, 'blocks/launch_pad.json'), { type: 'minecraft:block', pools: [pool([item('projecthero:launch_pad')], 1, [{ condition: 'minecraft:survives_explosion' }])] });

// ---------------------------------------------------------------- worldgen + tags
json(path.join(D, 'worldgen/structure/sky_fortress.json'), { type: 'projecthero:sky_fortress', biomes: '#projecthero:sky_fortress_biomes', step: 'top_layer_modification',
  terrain_adaptation: 'none', spawn_overrides: {} });
json(path.join(D, 'worldgen/structure_set/sky_fortress.json'), { structures: [{ structure: 'projecthero:sky_fortress', weight: 1 }],
  placement: { type: 'minecraft:random_spread', salt: 1707701, spacing: 260, separation: 110, exclusion_zone: { other_set: 'minecraft:villages', chunk_count: 6 } } });
json(path.join(D, 'tags/worldgen/biome/sky_fortress_biomes.json'), { replace: false, values: ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:savanna',
  'minecraft:savanna_plateau', 'minecraft:ocean', 'minecraft:deep_ocean', 'minecraft:warm_ocean', 'minecraft:lukewarm_ocean', 'minecraft:deep_lukewarm_ocean',
  'minecraft:cold_ocean', 'minecraft:deep_cold_ocean'] });
function addTag(file, value) {
  const p = path.join(root, file);
  const o = fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : { replace: false, values: [] };
  if (!o.values.includes(value)) o.values.push(value);
  json(p, o);
}
addTag('src/main/resources/data/projecthero/tags/worldgen/structure/locator/tier4.json', 'projecthero:sky_fortress');
addTag('src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json', 'projecthero:launch_pad');
addTag('src/main/resources/data/minecraft/tags/block/mineable/axe.json', 'projecthero:parachute_crate');
console.log('sky fortress assets written');
