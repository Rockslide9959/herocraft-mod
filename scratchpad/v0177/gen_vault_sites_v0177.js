// v0.17.7 The Vault new floors + Tier 2 site upgrades: Riot Captain skin, security field / breaker / ward stone textures,
// models, blockstates, item models, the Riot Captain loot table. PNG helpers copied from gen_vault_v0175.js.
//   node scratchpad/v0177/gen_vault_sites_v0177.js [root]
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


// ================================================================ v0.17.7 additions
const STEEL = [58, 62, 70];
function plate(base = STEEL, noise = 8) {
  const c = canvas(); c.fill(base, noise);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(base, 0.6)); c.set(i, 15, shade(base, 0.5)); c.set(0, i, shade(base, 0.6)); c.set(15, i, shade(base, 0.5)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, shade(base, 1.3)); c.set(1, i, shade(base, 1.18)); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(x, y, shade(base, 1.6));
  return c;
}

// ---------------------------------------------------------------- the Riot Captain skin
// the riot warden kit (thug_c) recoloured to a white-and-navy command kit: gold captain chevrons on both arms, a gold
// shoulder rope, a white-striped helmet with a gold visor rim, a red CAPTAIN band on the chest plate, black gloves
{
  const KIT = [36, 44, 66];
  const c = recolour(path.join(T, 'syndicate/thug_c.png'), (x, y, r, g, b) => {
    if (inHead(x, y)) return null;
    if (inHat(x, y)) return [0, 0, 0, 0];
    if (isSkinTone(r, g, b) && !(y >= 16 && y < 20)) return [24, 24, 28]; // gloves
    return tone(KIT, lum(r, g, b), 0.75, 1.35);
  });
  const WHITE = [226, 228, 232], WSH = [190, 194, 202], GOLD = [222, 178, 52], GOLDD = [170, 128, 30], RED = [176, 28, 32];
  // chest plate (front + back) in white with a navy rim, a red band across
  for (const [x0, x1] of [[20, 27], [32, 39]]) for (let y = 21; y <= 28; y++) for (let x = x0; x <= x1; x++) {
    const edge = y === 21 || y === 28 || x === x0 || x === x1;
    c.set(x, y, edge ? [28, 34, 52] : (y === 24 ? RED : (x + y) % 5 === 0 ? WSH : WHITE));
  }
  // a gold badge on the left breast
  c.set(25, 22, GOLD); c.set(26, 22, GOLD); c.set(25, 23, GOLDD); c.set(26, 23, GOLD);
  // shoulder pads white with gold trim; chevrons (three gold V) on both upper arms (outer faces)
  for (const [x0, y0] of [[44, 16], [36, 48]]) { c.rect(x0, y0, x0 + 3, y0 + 3, WHITE); c.rect(x0, y0 + 3, x0 + 3, y0 + 3, GOLD); }
  for (const [x0] of [[40], [48], [32], [40]]) void x0;
  for (const [xo, yo] of [[40, 20], [32, 52]]) { // right arm outer (x 40..43), left arm outer (x 32..35)
    for (let k = 0; k < 3; k++) {
      const y = yo + 1 + k * 2;
      c.set(xo, y, GOLD); c.set(xo + 1, y + 1, GOLD); c.set(xo + 2, y + 1, GOLD); c.set(xo + 3, y, GOLD);
    }
  }
  // a gold shoulder rope over the right shoulder (front body column)
  for (let y = 20; y <= 24; y++) c.set(20 + ((y - 20) >> 1), y, GOLD);
  // belt with a gold buckle, holster pouches
  c.rect(16, 30, 39, 31, [16, 16, 18]); c.set(23, 30, GOLD); c.set(24, 30, GOLD);
  for (const px of [21, 26]) c.rect(px, 29, px + 1, 29, [44, 44, 50]);
  // boots, knee pads
  for (const [x0, y0] of [[0, 16], [16, 48]]) { c.rect(x0, y0 + 12, x0 + 15, y0 + 15, [14, 14, 16]); c.rect(x0 + 4, y0 + 5, x0 + 7, y0 + 6, WSH); }
  // the helmet in the hat layer: white with a navy stripe over the crown, a gold-rimmed dark visor
  const H = WHITE, HS = [36, 44, 66], VIS = [30, 40, 60], VH = [140, 170, 210];
  clearRect(c, 32, 0, 63, 15);
  c.rect(40, 0, 47, 7, H, 6); c.rect(48, 0, 55, 7, H);
  for (let y = 0; y <= 7; y++) { c.set(43, y, HS); c.set(44, y, HS); }
  for (const x0 of [32, 48, 56]) { c.rect(x0, 8, x0 + 7, 13, H, 6); for (let y = 8; y <= 13; y++) if (x0 === 56) { c.set(59, y, HS); c.set(60, y, HS); } }
  c.rect(40, 8, 47, 9, H); for (let x = 40; x <= 47; x++) c.set(x, 9, GOLD);
  c.rect(40, 10, 47, 13, VIS);
  for (let x = 41; x <= 45; x++) c.set(x, 10, VH);
  c.set(46, 11, VH);
  for (const x of [43, 44]) c.set(x, 8, HS);
  write(path.join(T, 'syndicate/vault/riot_captain.png'), c.png());
}

// ---------------------------------------------------------------- block textures
// security field: a see-through energy lattice (blue = power, red = Riot Captain)
function field(col) {
  const c = canvas();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const grid = x % 4 === 0 || y % 4 === 0;
    const edge = x === 0 || y === 0 || x === 15 || y === 15;
    const a = edge ? 200 : grid ? 150 : 60 + Math.round(rnd() * 25);
    const f = edge ? 1.15 : grid ? 1.0 : 0.75;
    c.set(x, y, col.map(v => v * f), a);
  }
  for (let i = 0; i < 6; i++) c.set(Math.floor(rnd() * 16), Math.floor(rnd() * 16), [255, 255, 255], 200);
  return c;
}
tex('block/vault_barrier_power', field([90, 170, 255]));
tex('block/vault_barrier_captain', field([255, 70, 60]));
// cell-block breaker: a yellow-and-black breaker cabinet, a big lever slot and status lights (green on / red off)
function breaker(on) {
  const front = plate([66, 66, 60]);
  for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) if (x < 3 || x > 12) front.set(x, y, (((x + y) >> 1) % 2) ? [24, 24, 26] : [226, 186, 34]);
  front.rect(4, 3, 11, 12, [30, 30, 34]);
  front.rect(7, 4, 8, 11, [12, 12, 14]);
  const hy = on ? 4 : 9; // the lever handle up (on) or down (off)
  front.rect(5, hy, 10, hy + 2, on ? [200, 40, 30] : [110, 30, 26]);
  front.set(5, 13, on ? [60, 255, 90] : [20, 60, 26]);
  front.set(10, 13, on ? [80, 20, 20] : [255, 50, 40]);
  tex(`block/vault_breaker_front${on ? '' : '_off'}`, front);
}
breaker(true); breaker(false);
{
  const side = plate([66, 66, 60]);
  for (const y of [4, 6, 8, 10]) for (let x = 3; x < 13; x++) side.set(x, y, [30, 30, 32]);
  tex('block/vault_breaker_side', side);
  const top = plate([66, 66, 60]);
  for (let i = 3; i < 13; i++) top.set(i, 7, [226, 186, 34]);
  tex('block/vault_breaker_top', top);
}
// ward stone: dark carved stone with a rune eye (glows faintly), trimmed sides
{
  const base = [70, 70, 78];
  const front = canvas(); front.fill(base, 14);
  for (let i = 0; i < 16; i++) { front.set(i, 0, shade(base, 0.6)); front.set(i, 15, shade(base, 0.55)); front.set(0, i, shade(base, 0.65)); front.set(15, i, shade(base, 0.55)); }
  // the rune: an eye in a diamond
  const RUNE = [120, 220, 255], RD = [40, 90, 120];
  for (let k = 0; k <= 5; k++) { front.set(7 - k, 2 + k, RD); front.set(8 + k, 2 + k, RD); front.set(7 - k, 13 - k, RD); front.set(8 + k, 13 - k, RD); }
  for (let x = 4; x <= 11; x++) { const dy = Math.round(Math.sqrt(Math.max(0, 16 - (x - 7.5) * (x - 7.5))) * 0.55); front.set(x, 7 - dy, RUNE); front.set(x, 8 + dy, RUNE); }
  front.rect(7, 7, 8, 8, [230, 250, 255]);
  tex('block/site_ward_front', front);
  const side = canvas(); side.fill(base, 14);
  for (let i = 0; i < 16; i++) { side.set(i, 0, shade(base, 0.6)); side.set(i, 15, shade(base, 0.55)); }
  for (let x = 2; x < 14; x += 3) for (let y = 5; y < 11; y++) side.set(x, y, shade(base, 0.7));
  tex('block/site_ward_side', side);
}

// ---------------------------------------------------------------- models + blockstates
const bm = (n, o) => json(path.join(A, `models/block/${n}.json`), o);
const im = (n, parent) => json(path.join(A, `models/item/${n}.json`), { parent });
for (const k of ['power', 'captain']) {
  bm(`vault_barrier_${k}`, { parent: 'minecraft:block/cube_all', render_type: 'minecraft:translucent', textures: { all: `projecthero:block/vault_barrier_${k}` } });
}
json(path.join(A, 'blockstates/vault_barrier.json'), { variants: {
  'kind=power': { model: 'projecthero:block/vault_barrier_power' },
  'kind=captain': { model: 'projecthero:block/vault_barrier_captain' } } });
im('vault_barrier', 'projecthero:block/vault_barrier_power');
for (const on of [true, false]) {
  bm(`vault_breaker${on ? '' : '_off'}`, { parent: 'minecraft:block/orientable', textures: {
    front: `projecthero:block/vault_breaker_front${on ? '' : '_off'}`, side: 'projecthero:block/vault_breaker_side', top: 'projecthero:block/vault_breaker_top' } });
}
{
  const v = {};
  for (const on of [true, false]) for (const [f, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
    const model = `projecthero:block/vault_breaker${on ? '' : '_off'}`;
    v[`facing=${f},powered=${on}`] = y ? { model, y } : { model };
  }
  json(path.join(A, 'blockstates/vault_breaker.json'), { variants: v });
}
im('vault_breaker', 'projecthero:block/vault_breaker');
bm('site_ward', { parent: 'minecraft:block/cube', textures: { particle: 'projecthero:block/site_ward_side',
  north: 'projecthero:block/site_ward_front', south: 'projecthero:block/site_ward_front', east: 'projecthero:block/site_ward_front',
  west: 'projecthero:block/site_ward_front', up: 'projecthero:block/site_ward_side', down: 'projecthero:block/site_ward_side' } });
json(path.join(A, 'blockstates/site_ward.json'), { variants: { 'powered=false': { model: 'projecthero:block/site_ward' }, 'powered=true': { model: 'projecthero:block/site_ward' } } });
im('site_ward', 'projecthero:block/site_ward');

// ---------------------------------------------------------------- loot: the Riot Captain
const pool = (item, min, max, chance) => {
  const p = { rolls: 1, entries: [{ type: 'minecraft:item', name: item, functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } }] }] };
  if (chance !== undefined && chance < 1) p.conditions = [{ condition: 'minecraft:random_chance', chance }];
  return p;
};
json(path.join(D, 'loot_table/entities/riot_captain.json'), { type: 'minecraft:entity', pools: [
  pool('projecthero:hero_alloy_ingot', 2, 4), pool('projecthero:vibranium_shard', 1, 2), pool('minecraft:experience_bottle', 3, 6),
  pool('projecthero:energy_cell', 1, 1, 0.5), pool('minecraft:golden_apple', 1, 1, 0.35)] });
console.log('v0.17.7 vault + sites assets written');
