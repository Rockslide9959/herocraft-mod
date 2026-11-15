// v0.17.5 Tier 4 dungeon, The Vault: every resource -- the five player-model skins (inmates x2, riot warden, riot
// gunner, Warden Kross; recoloured / painted over the shipped Syndicate skins thug_a and thug_c), block textures, models
// and blockstates (sealed vault, lockdown shutter, laser alarm, turret, console, lockdown track), the three artifact item
// textures + models, loot tables (Kross + guard drops, the tier 4 artifact pools), the structure + structure set + biome
// tag, the Locator tier 4 tag, mineable tags. Procedural PNGs with Node zlib (PNG helpers copied from gen_hideouts_v0175.js).
//   node scratchpad/gen_vault_v0175.js [root]
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

let seed = 175;
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

// ---------------------------------------------------------------- skins
function recolour(src, fn) {
  const img = decodePng(src);
  const c = canvas(img.w, img.h, Buffer.from(img.px));
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
    const [r, g, b, a] = c.get(x, y);
    if (a === 0) continue;
    const out = fn(x, y, r, g, b, a);
    if (out) c.set(x, y, out, out[3] === undefined ? a : out[3]);
  }
  return c;
}
const isSkinTone = (r, g, b) => r > 120 && r > g && g > b && r - b > 30 && r - b < 130 && g > 70;
const inHead = (x, y) => y < 16 && x < 32;
const inHat = (x, y) => y < 16 && x >= 32;
const clearRect = (c, x0, y0, x1, y1) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) c.set(x, y, [0, 0, 0], 0); };
// skin regions (64x64): front faces
const BODY_FRONT = [20, 20, 27, 31];
const tone = (base, l, lo = 0.45, hi = 1.25) => shade(base, lo + (hi - lo) * Math.min(1, l / 200));

// an orange jumpsuit over everything that isn't the head or bare skin; white sneakers; a number patch on the chest
function inmate(src, dst, base, patchX) {
  const c = recolour(path.join(T, src), (x, y, r, g, b) => {
    if (inHead(x, y) || isSkinTone(r, g, b)) return null;
    if (inHat(x, y)) return [0, 0, 0, 0];
    return tone(base, lum(r, g, b), 0.55, 1.15);
  });
  // sneakers: bottom two rows of both legs (base + overlay), all faces
  for (const [x0, y0] of [[0, 16], [16, 48], [0, 32], [0, 48]]) for (let y = y0 + 14; y <= y0 + 15; y++) for (let x = x0; x < x0 + 16; x++) {
    const [, , , a] = c.get(x, y); if (a > 0) c.set(x, y, y === y0 + 15 ? [200, 200, 205] : [235, 235, 238]);
  }
  // the number patch (white with black "digits")
  c.rect(patchX, 22, patchX + 2, 23, [238, 238, 240]);
  c.set(patchX, 23, [20, 20, 20]); c.set(patchX + 2, 22, [20, 20, 20]); c.set(patchX + 1, 23, [20, 20, 20]);
  // a darker seam down the front
  for (let y = 20; y < 30; y++) c.set(23, y, shade(base, 0.7));
  write(path.join(T, 'syndicate/vault', dst + '.png'), c.png());
}
inmate('syndicate/thug_a.png', 'inmate_a', [236, 112, 24], 25);
inmate('syndicate/thug_c.png', 'inmate_b', [214, 92, 28], 21);

// black tactical kit + a visored riot helmet; vest = the gunner's grey ballistic vest
function riot(dst, vest) {
  const KIT = [30, 32, 38];
  const c = recolour(path.join(T, 'syndicate/thug_c.png'), (x, y, r, g, b) => {
    if (inHead(x, y)) return null;
    if (inHat(x, y)) return [0, 0, 0, 0];
    if (isSkinTone(r, g, b) && !(y >= 16 && y < 20)) return tone(KIT, 60); // gloves
    return tone(KIT, lum(r, g, b), 0.75, 1.35);
  });
  // chest plate panels (front + back), shoulder pads (arm tops)
  for (const [x0, x1] of [[20, 27], [32, 39]]) for (let y = 21; y <= 27; y++) for (let x = x0; x <= x1; x++) {
    const edge = y === 21 || y === 27 || x === x0 || x === x1;
    c.set(x, y, vest ? (edge ? [70, 72, 76] : [96, 98, 102]) : (edge ? [22, 24, 30] : [52, 58, 70]));
  }
  if (vest) {
    for (const px of [21, 24]) c.rect(px, 25, px + 2, 26, [64, 66, 70]); // pouches
    c.rect(22, 22, 25, 22, [230, 230, 232]); // a white POLICE-style tab (blank)
  }
  for (const [x0, y0] of [[44, 16], [36, 48]]) c.rect(x0, y0, x0 + 3, y0 + 3, [58, 64, 78]);
  // the WARDEN armband (yellow) on the right upper arm, belt with a buckle
  c.rect(44, 22, 47, 23, [222, 180, 40]); c.rect(40, 22, 43, 23, [200, 160, 34]); c.rect(48, 22, 51, 23, [200, 160, 34]);
  c.rect(16, 30, 39, 31, [16, 16, 18]); c.set(23, 30, [190, 190, 196]); c.set(24, 30, [190, 190, 196]);
  // boots
  for (const [x0, y0] of [[0, 16], [16, 48]]) c.rect(x0, y0 + 12, x0 + 15, y0 + 15, [14, 14, 16]);
  // the riot helmet in the hat layer
  const H = [22, 24, 28], HL = [52, 56, 64], VIS = [34, 54, 84], VH = [120, 150, 190];
  clearRect(c, 32, 0, 63, 15);
  c.rect(40, 0, 47, 7, H, 6); c.rect(48, 0, 55, 7, H); // top, bottom
  for (let x = 40; x <= 47; x++) c.set(x, 3, HL); // a ridge
  for (const x0 of [32, 48, 56]) c.rect(x0, 8, x0 + 7, 13, H, 6); // right, left, back
  c.rect(40, 8, 47, 9, H); // the brow
  c.rect(40, 10, 47, 13, VIS); // the visor
  for (let x = 41; x <= 45; x++) c.set(x, 10, VH);
  c.set(46, 11, VH);
  write(path.join(T, 'syndicate/vault', dst + '.png'), c.png());
}
riot('riot_warden', false);
riot('riot_gunner', true);

// Warden Kross: charcoal greatcoat, red trim, gold epaulettes, peaked cap, a scar
{
  const COAT = [44, 44, 50], TROUSER = [30, 30, 34], RED = [168, 22, 26], GOLD = [214, 172, 58];
  const c = recolour(path.join(T, 'syndicate/thug_a.png'), (x, y, r, g, b) => {
    if (inHead(x, y)) return null;
    if (inHat(x, y)) return [0, 0, 0, 0];
    if (isSkinTone(r, g, b)) return null; // bare hands
    const l = lum(r, g, b);
    const legs = (y >= 16 && y < 32 && x < 16) || (y >= 48 && x >= 16 && x < 32);
    return tone(legs ? TROUSER : COAT, l, 0.8, 1.3);
  });
  // coat skirt over the thighs (leg overlays, rows 4..10 of each leg face)
  for (const [x0, y0] of [[0, 32], [0, 48]]) for (let y = y0 + 4; y <= y0 + 10; y++) for (let x = x0; x < x0 + 16; x++) {
    const n = (rnd() - 0.5) * 8; c.set(x, y, COAT.map(v => v + n));
  }
  // red trim: collar, front placket, cuffs, coat hem
  c.rect(20, 20, 27, 20, RED); c.rect(16, 20, 19, 20, RED); c.rect(28, 20, 39, 20, RED);
  for (let y = 21; y <= 31; y++) { c.set(23, y, RED); c.set(24, y, shade(COAT, 0.7)); }
  for (const [x0, y0] of [[40, 30], [32, 62]]) c.rect(x0, y0, x0 + 15, y0 + 1, RED);
  for (const y0 of [32, 48]) c.rect(0, y0 + 10, 15, y0 + 10, RED);
  // gold buttons, epaulettes on the arm tops, a medal bar
  for (const y of [22, 25, 28]) c.set(25, y, GOLD);
  for (const [x0, y0] of [[44, 16], [36, 48]]) c.rect(x0, y0, x0 + 3, y0 + 3, GOLD);
  c.rect(20, 22, 22, 22, [40, 90, 170]); c.set(21, 22, GOLD);
  // belt
  c.rect(16, 29, 39, 29, [18, 16, 14]); c.set(23, 29, GOLD); c.set(24, 29, GOLD);
  // boots
  for (const [x0, y0] of [[0, 16], [16, 48]]) { c.rect(x0, y0 + 12, x0 + 15, y0 + 15, [12, 12, 14]); c.rect(x0 + 4, y0 + 12, x0 + 7, y0 + 12, [60, 60, 66]); }
  // the scar across the left eye and some stubble (head front x 8..15, y 8..15)
  for (const [x, y] of [[13, 8], [13, 9], [12, 10], [12, 11], [11, 12]]) c.set(x, y, [196, 128, 120]);
  for (let x = 9; x <= 14; x++) for (let y = 14; y <= 15; y++) { const [r, g, b] = c.get(x, y); c.set(x, y, [r * 0.82, g * 0.8, b * 0.8]); }
  // the peaked cap in the hat layer
  const CAP = [38, 38, 44];
  clearRect(c, 32, 0, 63, 15);
  c.rect(40, 0, 47, 7, CAP, 6);
  for (const x0 of [32, 48, 56]) { c.rect(x0, 8, x0 + 7, 9, CAP); c.rect(x0, 10, x0 + 7, 10, RED); }
  c.rect(40, 8, 47, 9, CAP); c.rect(40, 10, 47, 10, [10, 10, 12]); // the peak
  c.set(43, 8, GOLD); c.set(44, 8, GOLD); c.set(43, 9, GOLD); c.set(44, 9, GOLD); // the badge
  write(path.join(T, 'syndicate/vault/warden_kross.png'), c.png());
}

// ---------------------------------------------------------------- block textures
const STEEL = [58, 62, 70];
function plate(base = STEEL, noise = 8) {
  const c = canvas(); c.fill(base, noise);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(base, 0.6)); c.set(i, 15, shade(base, 0.5)); c.set(0, i, shade(base, 0.6)); c.set(15, i, shade(base, 0.5)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, shade(base, 1.3)); c.set(1, i, shade(base, 1.18)); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(x, y, shade(base, 1.6));
  return c;
}
const AQUA = [95, 232, 232];
// sealed Dungeon Vault: dark steel, aqua trim, a round mag-lock in the middle, bolts
{
  const front = plate([40, 44, 52]);
  for (let i = 2; i < 14; i++) { front.set(i, 3, AQUA); front.set(i, 12, shade(AQUA, 0.7)); }
  for (let y = 4; y < 12; y++) for (let x = 4; x < 12; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d < 3.6) front.set(x, y, d < 1.5 ? [255, 255, 255] : mix(AQUA, [20, 60, 70], (d - 1.5) / 2.1));
    else if (d < 4.4) front.set(x, y, [24, 26, 30]);
  }
  for (const [x, y] of [[3, 7], [3, 8], [12, 7], [12, 8]]) front.set(x, y, [150, 156, 166]);
  tex('block/vault_seal_front', front);
  const side = plate([40, 44, 52]);
  for (let i = 2; i < 14; i++) { side.set(i, 7, shade(AQUA, 0.6)); side.set(i, 8, shade(AQUA, 0.45)); }
  tex('block/vault_seal_side', side);
  const top = plate([40, 44, 52]);
  for (let i = 3; i < 13; i++) { top.set(i, i, shade(AQUA, 0.5)); top.set(15 - i, i, shade(AQUA, 0.5)); }
  tex('block/vault_seal_top', top);
}
// Lockdown Shutter: heavy red slats, a hazard band at the bottom
{
  const c = canvas(); c.fill([120, 20, 22], 10);
  for (let y = 0; y < 16; y += 3) for (let x = 0; x < 16; x++) c.set(x, y, [70, 10, 12]);
  for (let y = 12; y < 16; y++) for (let x = 0; x < 16; x++) c.set(x, y, ((x + y) >> 1) % 2 ? [20, 20, 20] : [230, 190, 30]);
  for (const x of [1, 14]) for (let y = 0; y < 12; y++) c.set(x, y, [60, 62, 68]);
  tex('block/lockdown_shutter', c);
}
// lockdown track: hazard stripes on dark metal
{
  const c = canvas();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) c.set(x, y, (((x + y) >> 2) % 2) ? [24, 24, 26] : [226, 186, 34]);
  for (let i = 0; i < 16; i++) { c.set(i, 0, [80, 80, 86]); c.set(i, 15, [50, 50, 54]); }
  tex('block/lockdown_track', c);
}
// laser alarm: steel box, a red emitter lens on the front
{
  const side = plate(STEEL);
  for (let x = 3; x < 13; x++) side.set(x, 11, [150, 20, 20]);
  tex('block/vault_laser_alarm_side', side);
  const front = plate(STEEL);
  for (let y = 5; y < 11; y++) for (let x = 5; x < 11; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d < 2.9) front.set(x, y, d < 1.2 ? [255, 200, 190] : mix([255, 40, 30], [90, 0, 0], (d - 1.2) / 1.7));
    else if (d < 3.5) front.set(x, y, [20, 20, 24]);
  }
  tex('block/vault_laser_alarm_front', front);
}
// turret: gunmetal sides with vents, a lens with a red dot underneath
{
  const side = plate([50, 52, 58]);
  for (const y of [5, 7, 9]) for (let x = 3; x < 13; x++) side.set(x, y, [20, 20, 24]);
  tex('block/vault_turret_side', side);
  tex('block/vault_turret_top', plate([50, 52, 58]));
  const bottom = plate([50, 52, 58]);
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d < 5.2) bottom.set(x, y, d < 1.4 ? [255, 40, 30] : d < 2.4 ? [40, 10, 10] : mix([30, 34, 44], [10, 10, 14], (d - 2.4) / 2.8));
    else if (d < 6) bottom.set(x, y, [90, 94, 104]);
  }
  bottom.set(6, 6, [200, 220, 255]);
  tex('block/vault_turret_bottom', bottom);
}
// security console: a screen with cyan readouts over a keyboard
{
  const front = plate([70, 76, 86]);
  front.rect(2, 2, 13, 9, [10, 22, 40]);
  for (const [y, w] of [[3, 8], [5, 6], [7, 9]]) for (let x = 3; x < 3 + w; x++) front.set(x, y, rnd() < 0.8 ? [80, 220, 255] : [30, 120, 160]);
  front.set(12, 3, [80, 255, 120]);
  front.rect(2, 11, 13, 13, [34, 36, 42]);
  for (let x = 3; x < 13; x += 2) { front.set(x, 12, [150, 154, 164]); }
  front.set(12, 12, [255, 60, 50]);
  tex('block/vault_console_front', front);
  tex('block/vault_console_side', plate([70, 76, 86]));
  const top = plate([70, 76, 86]);
  top.rect(3, 3, 12, 12, [44, 48, 56]);
  tex('block/vault_console_top', top);
}

// models + blockstates
const bm = (n, o) => json(path.join(A, `models/block/${n}.json`), o);
const im = (n, parent) => json(path.join(A, `models/item/${n}.json`), { parent });
const facingState = (model, extra = '') => {
  const v = {};
  for (const [f, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
    const k = `facing=${f}${extra}`;
    v[k] = y ? { model, y } : { model };
  }
  return v;
};
bm('vault_seal', { parent: 'minecraft:block/orientable', textures: { front: 'projecthero:block/vault_seal_front', side: 'projecthero:block/vault_seal_side', top: 'projecthero:block/vault_seal_top' } });
json(path.join(A, 'blockstates/vault_seal.json'), { variants: facingState('projecthero:block/vault_seal') });
bm('lockdown_shutter', { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/lockdown_shutter' } });
json(path.join(A, 'blockstates/lockdown_shutter.json'), { variants: { '': { model: 'projecthero:block/lockdown_shutter' } } });
bm('lockdown_track', { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/lockdown_track' } });
json(path.join(A, 'blockstates/lockdown_track.json'), { variants: { '': { model: 'projecthero:block/lockdown_track' } } });
bm('vault_laser_alarm', { parent: 'minecraft:block/orientable', textures: { front: 'projecthero:block/vault_laser_alarm_front', side: 'projecthero:block/vault_laser_alarm_side', top: 'projecthero:block/vault_laser_alarm_side' } });
{
  const v = {};
  for (const spent of ['false', 'true']) Object.assign(v, facingState('projecthero:block/vault_laser_alarm', `,spent=${spent}`));
  json(path.join(A, 'blockstates/vault_laser_alarm.json'), { variants: v });
}
bm('vault_turret', { parent: 'minecraft:block/cube_bottom_top', textures: { top: 'projecthero:block/vault_turret_top', bottom: 'projecthero:block/vault_turret_bottom', side: 'projecthero:block/vault_turret_side' } });
json(path.join(A, 'blockstates/vault_turret.json'), { variants: { '': { model: 'projecthero:block/vault_turret' } } });
bm('vault_console', { parent: 'minecraft:block/orientable', textures: { front: 'projecthero:block/vault_console_front', side: 'projecthero:block/vault_console_side', top: 'projecthero:block/vault_console_top' } });
json(path.join(A, 'blockstates/vault_console.json'), { variants: facingState('projecthero:block/vault_console') });
for (const n of ['vault_seal', 'lockdown_shutter', 'lockdown_track', 'vault_laser_alarm', 'vault_turret', 'vault_console']) im(n, `projecthero:block/${n}`);

// ---------------------------------------------------------------- artifact items
function keyringTex() {
  const c = canvas();
  const ring = [150, 156, 166], key = [214, 172, 58], keyD = [150, 110, 30];
  for (let a = 0; a < 40; a++) { const t = a / 40 * Math.PI * 2; c.set(5 + Math.cos(t) * 3.2, 5 + Math.sin(t) * 3.2, ring); }
  // three keys hanging off the ring
  for (const [x0, len, col] of [[5, 8, key], [8, 7, [180, 186, 196]], [3, 6, [120, 200, 210]]]) {
    c.line(x0, 8, x0 + 1, 8 + len - 1, col);
    c.set(x0 + 2, 8 + len - 2, shade(col, 0.7)); c.set(x0 + 2, 8 + len - 4, shade(col, 0.7));
  }
  c.set(11, 4, keyD); c.set(12, 3, key); c.set(13, 3, key); c.set(12, 4, keyD); // the warden's tag
  c.rect(10, 2, 14, 6, [168, 22, 26]); c.set(12, 4, [240, 240, 240]);
  c.outline([20, 20, 24]);
  return c;
}
function mjolnirTex() {
  const c = canvas();
  c.map([
    '................',
    '.......ww.......',
    '......wSSw......',
    '.....wSSSSw.....',
    '....wSSSSSSw....',
    '...wSSsSSSSSw...',
    '...wSSSsSSSSw...',
    '..wSSSSSsSSSw...',
    '..wSSSSSSsSSSw..',
    '...wSSSSSSsSSw..',
    '....wSSSSSSSw...',
    '.....wSSSSSw....',
    '......wSSSw.....',
    '.......www......',
    '................',
    '................',
  ], { S: [150, 158, 172], s: [255, 240, 140], w: [96, 102, 116] });
  // the glowing crack + sparks
  for (const [x, y] of [[13, 2], [14, 4], [2, 11]]) c.set(x, y, [180, 230, 255]);
  c.outline([24, 26, 34]);
  return c;
}
function capacitorTex() {
  const c = canvas();
  c.rect(4, 3, 11, 13, [44, 48, 58], 6);
  c.rect(5, 2, 10, 2, [150, 154, 164]); c.rect(6, 1, 9, 1, [190, 194, 204]);
  for (let y = 5; y <= 11; y++) for (let x = 6; x <= 9; x++) c.set(x, y, y >= 11 - ((x * 3) % 4) ? [80, 200, 255] : [16, 30, 46]);
  c.line(7, 5, 8, 8, [230, 250, 255]); c.line(8, 8, 7, 11, [230, 250, 255]);
  c.rect(4, 13, 11, 13, [222, 180, 40]);
  c.outline([18, 18, 22]);
  return c;
}
for (const [n, f, handheld] of [['wardens_keyring', keyringTex, false], ['cracked_mjolnir_shard', mjolnirTex, false], ['riot_capacitor', capacitorTex, false]]) {
  tex(`item/${n}`, f());
  json(path.join(A, `models/item/${n}.json`), { parent: handheld ? 'minecraft:item/handheld' : 'minecraft:item/generated', textures: { layer0: `projecthero:item/${n}` } });
}

// ---------------------------------------------------------------- loot tables
const lootDir = path.join(D, 'loot_table');
const pool = (item, min, max, chance) => {
  const p = { rolls: 1, entries: [{ type: 'minecraft:item', name: item, functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } }] }] };
  if (chance !== undefined) p.conditions = [{ condition: 'minecraft:random_chance', chance }];
  return p;
};
json(path.join(lootDir, 'entities/warden_kross.json'), { type: 'minecraft:entity', pools: [
  pool('projecthero:celestial_core', 2, 3),
  pool('projecthero:vibranium_shard', 2, 4),
  pool('minecraft:experience_bottle', 6, 10),
  pool('minecraft:golden_apple', 1, 2),
  pool('minecraft:netherite_scrap', 1, 1, 0.3),
] });
json(path.join(lootDir, 'entities/vault_inmate.json'), { type: 'minecraft:entity', pools: [pool('projecthero:salvaged_tech', 1, 2, 0.3), pool('minecraft:bread', 1, 2, 0.25)] });
json(path.join(lootDir, 'entities/riot_warden.json'), { type: 'minecraft:entity', pools: [pool('projecthero:salvaged_tech', 1, 3, 0.4), pool('projecthero:hero_alloy_scrap', 1, 2, 0.15)] });
json(path.join(lootDir, 'entities/riot_gunner.json'), { type: 'minecraft:entity', pools: [pool('projecthero:salvaged_tech', 1, 3, 0.4), pool('projecthero:hero_alloy_scrap', 1, 2, 0.15)] });
// the tier 4 vault's artifact hook: each artifact only rolls in a cleared Vault (near_vault_dungeon), per player
const ART = [['wardens_keyring', 0.22], ['cracked_mjolnir_shard', 0.15], ['riot_capacitor', 0.22]];
json(path.join(lootDir, 'chests/vault_tier4_artifacts.json'), {
  type: 'minecraft:chest',
  pools: ART.map(([item, chance]) => ({
    rolls: 1,
    conditions: [{ condition: 'projecthero:near_vault_dungeon', radius: 32 }, { condition: 'minecraft:random_chance', chance }],
    entries: [{ type: 'minecraft:item', name: `projecthero:${item}` }],
  })),
});

// ---------------------------------------------------------------- structure
json(path.join(D, 'tags/worldgen/biome/vault_dungeon_biomes.json'), { replace: false, values: ['#projecthero:finds_land_biomes'] });
json(path.join(D, 'worldgen/structure/the_vault.json'), {
  type: 'projecthero:vault_dungeon', biomes: '#projecthero:vault_dungeon_biomes', step: 'top_layer_modification',
  terrain_adaptation: 'none', spawn_overrides: {},
});
json(path.join(D, 'worldgen/structure_set/the_vault.json'), {
  structures: [{ structure: 'projecthero:the_vault', weight: 1 }],
  placement: { type: 'minecraft:random_spread', salt: 1705201, spacing: 200, separation: 80,
    exclusion_zone: { other_set: 'minecraft:villages', chunk_count: 4 } },
});
function tagAdd(file, ids) {
  const t = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : { replace: false, values: [] };
  for (const id of ids) if (!t.values.includes(id)) t.values.push(id);
  json(file, t);
}
tagAdd(path.join(D, 'tags/worldgen/structure/locator/tier4.json'), ['projecthero:the_vault']);
tagAdd(path.join(DM, 'tags/block/mineable/pickaxe.json'), ['projecthero:vault_laser_alarm', 'projecthero:vault_turret', 'projecthero:vault_console', 'projecthero:lockdown_track']);
console.log('vault assets written');
