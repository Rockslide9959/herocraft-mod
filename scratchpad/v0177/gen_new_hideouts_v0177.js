// v0.17.7 three new Tier 3 hideouts (Gravebound Crypt, Carnage Nest, Oathbreaker Keep): every resource --
// core / sealed vault / alarm / vent variants (merged into the existing blockstates), crimson biomass + pulsing egg,
// the Oath Ballista model, the three artifact icons, the soldier and mini-boss skins (detailed 64x64 player skins:
// thralls are recolours of shipped human skins half-swallowed by red goo; knights, the Crypt Lord and the Champion are
// painted part by part), loot tables (boss + soldier drops, the tier 3 artifact pools), structures + sets + biome tags,
// the Locator tier 3 tag, mineable tags. Procedural PNGs with Node zlib. Run from the worktree root:
//   node scratchpad/v0177/gen_new_hideouts_v0177.js
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
    if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; }
    else if (ty === 'PLTE') plte = d; else if (ty === 'tRNS') trns = d; else if (ty === 'IDAT') idat.push(d);
    o += 12 + len;
  }
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
      if (type === 6) cur.copy(out, i, x * 4, x * 4 + 4);
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
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2246822519) >>> 0; h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
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
    clear(x, y) { const i = (y * w + x) * 4; px[i] = px[i + 1] = px[i + 2] = px[i + 3] = 0; },
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
const readJson = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const tex = (rel, c) => write(path.join(A, 'textures', rel + '.png'), c.png());

const FACTIONS = { gravebound: 0x9A6BE0, carnage: 0xD8222C, oathbreaker: 0x4FC9D9 };
const STEEL = [46, 50, 58];
const BOOL = [false, true];

// ---------------------------------------------------------------- core / seal (same look as v0.17.5, faction colour)
function steelFrame(c, base = STEEL) {
  c.fill(base, 8);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(base, 0.55)); c.set(i, 15, shade(base, 0.45)); c.set(0, i, shade(base, 0.55)); c.set(15, i, shade(base, 0.45)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, shade(base, 1.35)); c.set(1, i, shade(base, 1.2)); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(x, y, shade(base, 1.6));
}
function core(faction, sealed) {
  const glow = hex(FACTIONS[faction]);
  const c = canvas();
  steelFrame(c);
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const d = Math.abs(x - 7.5) + Math.abs(y - 7.5);
    if (d < 5.6) { const t = 1 - d / 5.6; const col = mix(shade(glow, 0.45), mix(glow, [255, 255, 255], 0.55), t); c.set(x, y, sealed ? shade(col, 0.6) : col); }
    else if (d < 6.4) c.set(x, y, shade(glow, sealed ? 0.25 : 0.35));
  }
  if (sealed) {
    for (let i = 0; i < 16; i++) { c.set(i, 5, [70, 72, 80]); c.set(i, 10, [70, 72, 80]); c.set(5, i, [64, 66, 74]); c.set(10, i, [64, 66, 74]); }
    c.rect(6, 6, 9, 9, [36, 38, 44]); c.rect(7, 7, 8, 8, shade(glow, 0.9));
  }
  return c;
}
const VAULT_TRIM = [179, 92, 255];
function sealFront(faction) {
  const glow = hex(FACTIONS[faction]);
  const c = canvas(); steelFrame(c, [40, 42, 52]);
  for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d <= 4.6 && d > 3.6) c.set(x, y, shade(VAULT_TRIM, 0.7)); }
  for (let i = 1; i < 15; i++) for (const x of [3, 6, 9, 12]) c.set(x, i, shade(glow, 0.55 + (i % 3 === 0 ? 0.25 : 0)));
  for (let x = 1; x < 15; x++) { c.set(x, 4, shade(glow, 0.7)); c.set(x, 11, shade(glow, 0.5)); }
  c.rect(6, 7, 9, 11, shade(glow, 0.95)); c.rect(7, 8, 8, 9, [20, 20, 24]);
  return c;
}
{
  const bsCore = readJson(path.join(A, 'blockstates/hideout_core.json'));
  const bsSeal = readJson(path.join(A, 'blockstates/hideout_seal.json'));
  const ROT = { north: 0, east: 90, south: 180, west: 270 };
  for (const f of Object.keys(FACTIONS)) {
    for (const s of BOOL) {
      const name = `hideout_core_${f}${s ? '_sealed' : ''}`;
      tex(`block/${name}`, core(f, s));
      json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${name}` } });
      bsCore.variants[`faction=${f},sealed=${s}`] = { model: `projecthero:block/${name}` };
    }
    tex(`block/hideout_seal_${f}_front`, sealFront(f));
    json(path.join(A, `models/block/hideout_seal_${f}.json`), {
      parent: 'minecraft:block/orientable',
      textures: { top: 'projecthero:block/hideout_seal_top', bottom: 'projecthero:block/hideout_seal_side', side: 'projecthero:block/hideout_seal_side',
        front: `projecthero:block/hideout_seal_${f}_front`, particle: 'projecthero:block/hideout_seal_side' },
    });
    for (const d of Object.keys(ROT)) { const v = { model: `projecthero:block/hideout_seal_${f}` }; if (ROT[d]) v.y = ROT[d]; bsSeal.variants[`facing=${d},faction=${f}`] = v; }
  }
  json(path.join(A, 'blockstates/hideout_core.json'), bsCore);
  json(path.join(A, 'blockstates/hideout_seal.json'), bsSeal);
}

// ---------------------------------------------------------------- alarms (the tripwire trap block)
function alarm(f, spent) {
  const c = canvas();
  const dim = spent ? 0.45 : 1;
  if (f === 'gravebound') { // a cursed headstone: weathered stone, a glowing purple rune
    c.fill([96, 96, 102], 16);
    for (let i = 0; i < 20; i++) c.set(Math.floor(rnd() * 16), Math.floor(rnd() * 16), [70, 92, 66]); // moss
    for (let i = 0; i < 16; i++) { c.set(i, 0, [60, 60, 66]); c.set(0, i, [120, 120, 126]); c.set(15, i, [58, 58, 64]); c.set(i, 15, [54, 54, 60]); }
    const g = shade([190, 120, 255], dim);
    c.map([
      '................', '................', '................', '.......gg.......', '......g..g......', '.......gg.......',
      '....gggggggg....', '.......gg.......', '.......gg.......', '......g.gg......', '.....g..g.g.....', '.......gg.......',
      '................', '................', '................', '................',
    ], { g });
  } else if (f === 'carnage') { // a red egg sac with black veins
    c.fill(spent ? [70, 30, 32] : [200, 40, 44], 18);
    for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) { const d = Math.hypot((x - 7.5), (y - 7.5) * 1.1); if (d < 6) c.set(x, y, mix(c.get(x, y), spent ? [90, 40, 40] : [255, 150, 130], (1 - d / 6) * 0.6)); }
    for (let i = 0; i < 6; i++) { let x = 8, y = 8; for (let s = 0; s < 9; s++) { x += Math.round(rnd() * 2 - 1); y += Math.round(rnd() * 2 - 1); c.set(x, y, [30, 4, 8]); } }
  } else { // oathbreaker: a dark iron war horn on carved stone
    c.fill([86, 88, 92], 12);
    for (let y = 0; y < 16; y += 4) for (let x = 0; x < 16; x++) c.set(x, y, [62, 64, 68]);
    const horn = spent ? [70, 80, 84] : [60, 64, 72];
    for (let x = 3; x <= 12; x++) { const y = 10 - Math.round((x - 3) * 0.55); const r = x < 6 ? 0 : x < 10 ? 1 : 2; for (let k = -r; k <= r; k++) c.set(x, y + k, shade(horn, 0.9 + (k === -r ? 0.4 : 0))); }
    c.set(12, 4, shade([80, 220, 235], dim)); c.set(12, 5, shade([80, 220, 235], dim)); c.set(12, 6, shade([60, 180, 200], dim));
    c.set(3, 10, [30, 30, 34]);
  }
  return c;
}
{
  const bs = readJson(path.join(A, 'blockstates/hideout_alarm.json'));
  for (const f of Object.keys(FACTIONS)) for (const s of BOOL) {
    const name = `hideout_alarm_${f}${s ? '_spent' : ''}`;
    tex(`block/${name}`, alarm(f, s));
    json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${name}` } });
    bs.variants[`faction=${f},spent=${s}`] = { model: `projecthero:block/${name}` };
  }
  json(path.join(A, 'blockstates/hideout_alarm.json'), bs);
}

// ---------------------------------------------------------------- vents (curse: soul fire; crimson: red goo)
const VENT = { curse: [150, 110, 255], crimson: [210, 24, 34] };
function ventTop(kind, hot) {
  const c = canvas(); steelFrame(c, [52, 52, 56]);
  const g = VENT[kind];
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    if (y % 3 === 1) c.set(x, y, [30, 30, 34]);
    else { const t = 1 - Math.hypot(x - 7.5, y - 7.5) / 8; c.set(x, y, shade(mix(shade(g, 0.4), mix(g, [255, 255, 255], hot ? 0.6 : 0.15), t), hot ? 1.0 : 0.65)); }
  }
  return c;
}
{
  const bs = readJson(path.join(A, 'blockstates/hideout_vent.json'));
  for (const k of Object.keys(VENT)) {
    for (const hot of BOOL) {
      const name = `hideout_vent_${k}${hot ? '_hot' : ''}`;
      tex(`block/${name}`, ventTop(k, hot));
      json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_bottom_top',
        textures: { top: `projecthero:block/${name}`, bottom: 'projecthero:block/hideout_vent_side', side: 'projecthero:block/hideout_vent_side', particle: 'projecthero:block/hideout_vent_side' } });
    }
    for (const p of [0, 1, 2]) bs.variants[`kind=${k},phase=${p}`] = { model: `projecthero:block/hideout_vent_${k}${p === 1 ? '_hot' : ''}` };
  }
  json(path.join(A, 'blockstates/hideout_vent.json'), bs);
}

// ---------------------------------------------------------------- crimson biomass + the pulsing egg (animated)
function crimsonBiomass() {
  const c = canvas(); c.fill([120, 12, 20], 14);
  for (let i = 0; i < 18; i++) { const x = Math.floor(rnd() * 16), y = Math.floor(rnd() * 16); c.set(x, y, [170, 30, 36]); c.set(x + 1, y, [90, 8, 14]); }
  for (let i = 0; i < 4; i++) { let x = Math.floor(rnd() * 16), y = 0; for (let s = 0; s < 16; s++) { c.set(x, y, [40, 4, 8]); y++; x += Math.round(rnd() * 2 - 1); } }
  for (let i = 0; i < 6; i++) c.set(Math.floor(rnd() * 16), Math.floor(rnd() * 16), [240, 110, 110]);
  return c;
}
function crimsonEgg() {
  // three egg sacs packed in red membrane (no single central shape: it tiles over whole walls), pulsing brighter
  const frames = 4; const c = canvas(16, 16 * frames);
  const eggs = [[5, 5, 3.6], [11.5, 9, 3.2], [4.5, 12.5, 2.8]];
  for (let f = 0; f < frames; f++) {
    const pulse = [0.0, 0.55, 1.0, 0.45][f];
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let col = [96 + hash(x, y, 90) * 20, 10, 16];
      for (const [ex, ey, r] of eggs) {
        for (const ox of [-16, 0, 16]) for (const oy of [-16, 0, 16]) {
          const d = Math.hypot(x - ex - ox, (y - ey - oy) * 1.1);
          if (d < r) col = mix([180, 34, 38], [255, 150 + pulse * 60, 120 + pulse * 50], (1 - d / r) * (0.45 + pulse * 0.5));
          else if (d < r + 0.8) col = [70, 6, 12];
        }
      }
      c.set(x, y + f * 16, col);
    }
  }
  return c;
}
for (const [n, f] of [['carnage_biomass', crimsonBiomass], ['crimson_egg', crimsonEgg]]) {
  tex(`block/${n}`, f());
  json(path.join(A, `models/block/${n}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${n}` } });
  json(path.join(A, `blockstates/${n}.json`), { variants: { '': { model: `projecthero:block/${n}` } } });
  json(path.join(A, `models/item/${n}.json`), { parent: `projecthero:block/${n}` });
}
json(path.join(A, 'textures/block/crimson_egg.png.mcmeta'), { animation: { frametime: 6, interpolate: true, frames: [0, 1, 2, 3, 2, 1] } });

// ---------------------------------------------------------------- the Oath Ballista (a block model of elements)
{
  const P = 'minecraft:block/dark_oak_planks', L = 'minecraft:block/stripped_spruce_log', I = 'minecraft:block/iron_block', S = 'minecraft:block/cyan_wool';
  const box = (from, to, t) => ({ from, to, faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, { texture: t }])) });
  json(path.join(A, 'models/block/oath_ballista.json'), {
    parent: 'minecraft:block/block', ambientocclusion: false,
    textures: { particle: P, planks: P, log: L, iron: I, cloth: S },
    elements: [
      box([2, 0, 3], [14, 2, 13], '#planks'), // the base cart
      box([3, 2, 6], [13, 3, 10], '#log'), // turntable
      box([7, 3, 7], [9, 7, 9], '#log'), // the post
      box([7, 7, 1], [9, 9, 15], '#log'), // the stock (points north)
      box([1, 8, 2], [15, 9, 4], '#planks'), // the bow arms
      box([0, 7, 3], [2, 10, 4], '#iron'), box([14, 7, 3], [16, 10, 4], '#iron'), // arm tips
      box([2, 8.5, 9], [14, 8.75, 9.25], '#cloth'), // the drawn string
      box([7.5, 9, 0], [8.5, 10, 12], '#iron'), // the loaded bolt
      box([6, 2, 12], [10, 6, 14], '#planks'), // the windlass
    ].map(e => { for (const f of Object.values(e.faces)) f.texture = f.texture; return e; }),
  });
  const ROT = { north: 0, east: 90, south: 180, west: 270 };
  json(path.join(A, 'blockstates/oath_ballista.json'), { variants: Object.fromEntries(Object.entries(ROT).map(([d, r]) => [`facing=${d}`, r ? { model: 'projecthero:block/oath_ballista', y: r } : { model: 'projecthero:block/oath_ballista' }])) });
  json(path.join(A, 'models/item/oath_ballista.json'), { parent: 'projecthero:block/oath_ballista' });
}

// ---------------------------------------------------------------- artifact icons
function bellTex() {
  const c = canvas();
  c.map([
    '................',
    '.......hh.......',
    '......h..h......',
    '.......gg.......',
    '......gGGg......',
    '.....gGGGGg.....',
    '.....gGGGGg.....',
    '.....gGPGGg.....',
    '....gGGPGGGg....',
    '....gGGGGGGg....',
    '...gGGGGGGGGg...',
    '...ggggggggggg..',
    '......dPd.......',
    '.......P........',
    '................',
    '................',
  ], { h: [70, 66, 60], g: [120, 104, 60], G: [176, 156, 92], P: [190, 130, 255], d: [80, 60, 120] });
  c.outline([24, 20, 26]);
  return c;
}
function fangTex() {
  const c = canvas();
  const red = [200, 24, 30], hi = [255, 120, 110], dk = [90, 6, 12];
  for (let i = 0; i < 10; i++) { c.set(5 + i, 10 - i, red); c.set(6 + i, 10 - i, hi); c.set(5 + i, 9 - i, dk); }
  c.set(15, 0, hi);
  for (const [x, y] of [[3, 10], [4, 11], [5, 12], [4, 9], [6, 11]]) c.set(x, y, [40, 30, 34]);
  c.set(3, 13, [26, 20, 22]); c.set(2, 14, [26, 20, 22]); c.set(4, 12, [26, 20, 22]); c.set(1, 15, [60, 10, 14]);
  c.outline([20, 4, 6]);
  return c;
}
function sigilTex() {
  const c = canvas();
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d < 5.8) c.set(x, y, d > 4.8 ? [70, 72, 80] : mix([30, 32, 40], [60, 64, 74], d / 5)); }
  const s = [80, 220, 235];
  for (let y = 4; y <= 11; y++) c.set(7, y, s), c.set(8, y, s); // the sword
  c.set(6, 6, s); c.set(9, 6, s); c.set(5, 6, s); c.set(10, 6, s);
  c.set(9, 9, [20, 20, 26]); c.set(8, 9, [20, 20, 26]); // the break in the blade
  c.set(7, 3, [200, 250, 255]); c.set(8, 3, [200, 250, 255]);
  c.outline([14, 16, 22]);
  return c;
}
for (const [n, f, handheld] of [['gravecaller_bell', bellTex, false], ['brood_fang', fangTex, true], ['oath_sigil', sigilTex, false]]) {
  tex(`item/${n}`, f());
  json(path.join(A, `models/item/${n}.json`), { parent: handheld ? 'minecraft:item/handheld' : 'minecraft:item/generated', textures: { layer0: `projecthero:item/${n}` } });
}

// ---------------------------------------------------------------- skins
// box-UV regions of a 64x64 player skin: [u, v, w, h, d] for the inner and outer (layer 2) boxes
const PARTS = {
  head: [0, 0, 8, 8, 8], hat: [32, 0, 8, 8, 8],
  body: [16, 16, 8, 12, 4], jacket: [16, 32, 8, 12, 4],
  rarm: [40, 16, 4, 12, 4], rsleeve: [40, 32, 4, 12, 4], larm: [32, 48, 4, 12, 4], lsleeve: [48, 48, 4, 12, 4],
  rleg: [0, 16, 4, 12, 4], rpants: [0, 32, 4, 12, 4], lleg: [16, 48, 4, 12, 4], lpants: [0, 48, 4, 12, 4],
};
// faces of a box: name -> [du, dv, width, height] relative to (u, v)
function faces(w, h, d) {
  return { top: [d, 0, w, d], bottom: [d + w, 0, w, d], right: [0, d, d, h], front: [d, d, w, h], left: [d + w, d, d, h], back: [d + w + d, d, w, h] };
}
/** Paints a part: fn(face, x, y, fw, fh) -> colour [r,g,b] / [r,g,b,a] / null (transparent). */
function paint(c, part, fn) {
  const [u, v, w, h, d] = PARTS[part];
  for (const [face, [du, dv, fw, fh]] of Object.entries(faces(w, h, d))) {
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
      const col = fn(face, x, y, fw, fh);
      if (col == null) c.clear(u + du + x, v + dv + y);
      else c.set(u + du + x, v + dv + y, col, col[3] === undefined ? 255 : col[3]);
    }
  }
}
const n2 = (x, y, s, amt) => (hash(x, y, s) - 0.5) * amt;
const noisy = (col, x, y, s, amt = 14) => col.map(v => v + n2(x, y, s, amt));
const blank = () => canvas(64, 64);
const T = path.join(A, 'textures/entity');

/** Plate armour: a gradient top-lit metal with seams and rivets. */
function plate(base, face, x, y, fw, fh, seed2, seams = [4, 8]) {
  let col = noisy(base, x, y, seed2, 10);
  if (face === 'top') col = shade(col, 1.25);
  else if (face === 'bottom') col = shade(col, 0.6);
  else {
    col = shade(col, 1.15 - y / fh * 0.35);
    if (x === 0 || x === fw - 1) col = shade(col, 0.82);
    if (seams.includes(y)) col = shade(col, 0.6);
    if (seams.includes(y - 1)) col = shade(col, 1.3);
  }
  return col;
}
function knightSkin(o) {
  const c = blank();
  const M = o.metal, M2 = shade(o.metal, 0.75), TAB = o.tabard, TAB2 = o.tabard2, EYE = o.eye, TRIM = o.trim;
  const isFace = o.face; // [skin colour] for open helmets
  // head: helm (closed or open-faced)
  paint(c, 'head', (f, x, y, fw, fh) => {
    let col = plate(M, f, x, y, fw, fh, 1, []);
    if (f === 'front') {
      if (isFace && y >= 2 && y <= 6 && x >= 1 && x <= 6) {
        col = noisy(isFace, x, y, 2, 8);
        if (y === 3 && (x === 2 || x === 5)) col = [40, 32, 30];
        if (y === 3 && (x === 1 || x === 6)) col = shade(isFace, 1.05);
        if (y === 5 && x >= 3 && x <= 4) col = shade(isFace, 0.7);
        if (y === 2) col = shade(isFace, 0.82);
      } else if (!isFace) {
        if (y === 3 && x >= 1 && x <= 6) col = (x === 2 || x === 5) ? EYE : [16, 16, 20];
        if (y === 4 && x >= 1 && x <= 6) col = shade(M, 0.5);
        if (y >= 5 && y <= 6 && (x === 2 || x === 5) ) col = [24, 24, 28]; // breathing holes
        if (x === 3 || x === 4) col = shade(col, y < 3 ? 1.3 : 1.0); // the brow ridge
      }
    }
    if (f === 'top' && (x === 3 || x === 4)) col = o.crest || shade(M, 1.35);
    return col;
  });
  paint(c, 'hat', (f, x, y, fw, fh) => {
    if (o.horns && (f === 'right' || f === 'left') && y <= 3 && x >= 2 && x <= 5) return shade([220, 214, 196], 1 - y * 0.08); // horns
    if (o.crown && (f === 'front' || f === 'back' || f === 'right' || f === 'left') && y <= 1) return (x % 2 === 0 || y === 1) ? o.crown : null;
    if (o.brim && f !== 'top' && f !== 'bottom' && y === 2) return shade(M, 1.2); // a kettle-hat brim
    if (o.hood && f !== 'front' && f !== 'bottom') return noisy(o.hood, x, y, 3, 18);
    if (o.hood && f === 'front' && (x <= 0 || x >= 7 || y >= 7)) return noisy(o.hood, x, y, 3, 18);
    return null;
  });
  // body: breastplate / jerkin under a tabard
  paint(c, 'body', (f, x, y, fw, fh) => {
    let col = o.chest ? noisy(o.chest, x, y, 4, 16) : plate(M, f, x, y, fw, fh, 4, [3, 7]);
    if ((f === 'front' || f === 'back') && x >= 2 && x <= 5 && y >= 2) col = noisy(y > 9 ? TAB2 : TAB, x, y, 5, 10);
    if ((f === 'front') && TRIM && (x === 2 || x === 5) && y >= 2) col = TRIM;
    if (y === 8 && f !== 'top' && f !== 'bottom') col = (f === 'front' && (x === 3 || x === 4)) ? [212, 176, 70] : [72, 46, 28]; // belt + buckle
    if (f === 'front' && o.sigil && y >= 3 && y <= 6 && (x === 3 || x === 4)) col = o.sigil; // the oath sigil (a sword)
    if (f === 'front' && o.sigil && y === 4 && (x === 2 || x === 5)) col = o.sigil;
    if (o.strap && f === 'front' && x === Math.min(7, Math.max(0, 7 - Math.floor(y * 0.7)))) col = [80, 52, 30];
    return col;
  });
  paint(c, 'jacket', (f, x, y, fw, fh) => {
    if (o.cape && f === 'back' && y >= 1) return noisy(y > 9 ? TAB2 : TAB, x, y, 6, 12); // a short cape
    if (o.pauldron === false) return null;
    if (y <= 1 && f !== 'bottom') return plate(M, f, x, y, fw, fh, 7, []);
    return null;
  });
  for (const [arm, sleeve, s] of [['rarm', 'rsleeve', 8], ['larm', 'lsleeve', 9]]) {
    paint(c, arm, (f, x, y, fw, fh) => {
      let col = (o.chest && y < 7) ? noisy(o.chest, x, y, s, 14) : plate(y >= 9 ? M2 : M, f, x, y, fw, fh, s, [4, 9]);
      if (y >= 10) col = noisy(o.glove || shade(M, 0.6), x, y, s + 20, 10);
      return col;
    });
    paint(c, sleeve, (f, x, y, fw, fh) => {
      if (o.pauldron === false) return null;
      if (y <= 3 && f !== 'bottom') { const col = plate(M, f, x, y, fw, fh, s + 40, [3]); return f === 'top' ? shade(col, 1.15) : col; }
      if (y === 4 && f !== 'top' && f !== 'bottom' && TRIM) return TRIM;
      return null;
    });
  }
  for (const [leg, pants, s] of [['rleg', 'rpants', 11], ['lleg', 'lpants', 12]]) {
    paint(c, leg, (f, x, y, fw, fh) => {
      let col = plate(y >= 9 ? M2 : M, f, x, y, fw, fh, s, [5, 9]);
      if (o.trousers && y < 9) col = noisy(o.trousers, x, y, s + 3, 14);
      if (f === 'front' && y === 5) col = shade(M, 1.3); // the knee cop
      return col;
    });
    paint(c, pants, (f, x, y, fw, fh) => {
      if (y <= 3 && (f === 'front' || f === 'back')) return noisy(TAB2, x, y, s + 30, 10); // the tabard skirt
      return null;
    });
  }
  if (o.cracks) { // soul-fire cracks glowing through dark plate
    for (const part of ['body', 'rarm', 'larm', 'rleg', 'lleg', 'head']) {
      const [u, v, w, h, d] = PARTS[part];
      for (let i = 0; i < 3; i++) { let x = u + d + Math.floor(hash(i, u, 77) * w), y = v + d + Math.floor(hash(i, v, 78) * (h - 2)); for (let k = 0; k < 4; k++) { c.set(x, y, o.cracks); x += Math.round(hash(k, i, u) * 2 - 1); y++; } }
    }
  }
  return c;
}

const SKIN_TONE = [196, 150, 118];
const KNIGHTS = {
  knight: { metal: [150, 156, 166], tabard: [40, 150, 168], tabard2: [26, 30, 38], eye: [90, 230, 240], trim: [26, 30, 38], sigil: [210, 240, 250] },
  squire: { metal: [130, 134, 140], tabard: [40, 150, 168], tabard2: [26, 30, 38], eye: [40, 30, 30], face: SKIN_TONE, hood: [126, 130, 136],
    chest: [112, 74, 44], trousers: [70, 52, 36], glove: [80, 56, 34], pauldron: false },
  arbalist: { metal: [120, 124, 132], tabard: [110, 40, 36], tabard2: [26, 30, 38], eye: [40, 30, 30], face: [176, 132, 104], brim: true,
    chest: [92, 50, 38], trousers: [56, 44, 36], glove: [70, 48, 30], strap: true, trim: [190, 160, 70] },
  oathsworn: { metal: [52, 54, 62], tabard: [30, 120, 136], tabard2: [16, 16, 20], eye: [120, 250, 255], trim: [80, 220, 235], horns: true,
    cracks: [80, 220, 235], sigil: [120, 250, 255], glove: [30, 30, 36] },
};
for (const [k, o] of Object.entries(KNIGHTS)) write(path.join(T, `hideout/soldier/${k}.png`), knightSkin(o).png());
// the Oathbound Champion: blackened plate with gold trim, a cyan cape, a crowned helm with soul-fire eyes
write(path.join(T, 'hideout/oathbound_champion.png'), knightSkin({ metal: [44, 46, 54], tabard: [36, 160, 178], tabard2: [20, 22, 28], eye: [150, 255, 255],
  trim: [212, 176, 70], crown: [212, 176, 70], cape: true, cracks: [90, 230, 240], sigil: [212, 176, 70], glove: [26, 26, 30], crest: [212, 176, 70] }).png());

// ---- the Crypt Lord: a dead king (painted)
{
  const c = blank();
  const SK = [134, 150, 120], SK2 = [96, 110, 86], ROBE = [84, 36, 120], ROBE2 = [54, 20, 80], GOLD = [196, 160, 72], ERM = [236, 232, 222], EYE = [210, 140, 255];
  paint(c, 'head', (f, x, y, fw, fh) => {
    let col = noisy(y < 2 ? SK2 : SK, x, y, 21, 18);
    if (f === 'front') {
      if (y === 3 && (x === 2 || x === 5)) col = EYE;
      if (y === 3 && (x === 1 || x === 6)) col = [30, 20, 40];
      if (y === 4 && (x === 2 || x === 5)) col = shade(EYE, 0.5);
      if (y === 6 && x >= 2 && x <= 5) col = (x % 2) ? [220, 214, 190] : [30, 20, 26]; // a rictus of teeth
      if (y === 7 && x >= 1 && x <= 6) col = noisy([200, 196, 186], x, y, 22, 20); // a ragged grey beard
      if (x === 4 && y === 5) col = shade(SK, 0.6);
    }
    if (f === 'top') col = noisy([60, 56, 52], x, y, 23, 10);
    return col;
  });
  paint(c, 'hat', (f, x, y, fw, fh) => {
    if (f !== 'top' && f !== 'bottom' && y <= 2) { // the crown
      if (y === 2) return noisy(GOLD, x, y, 24, 18);
      if (y === 1 && x % 2 === 0) return f === 'front' && x === 4 ? [140, 60, 220] : noisy(GOLD, x, y, 25, 18);
      if (y === 0 && x % 4 === 0) return shade(GOLD, 1.2);
      return null;
    }
    if (f !== 'top' && f !== 'bottom' && f !== 'front' && y >= 3) return noisy([180, 176, 166], x, y, 26, 26); // long grey hair
    return null;
  });
  paint(c, 'body', (f, x, y, fw, fh) => {
    let col = noisy(y > 8 ? ROBE2 : ROBE, x, y, 27, 14);
    if (x % 3 === 0 && f !== 'top' && f !== 'bottom') col = shade(col, 0.82); // folds
    if (f === 'front' && (x === 3 || x === 4)) col = noisy(GOLD, x, y, 28, 20);
    // gold embroidery: a diamond lattice either side of the clasp line, a hem band
    if ((f === 'front' || f === 'back') && y >= 1 && y <= 6 && (x === 1 || x === 6) && y % 2 === 1) col = shade(GOLD, 0.85);
    if ((f === 'front' || f === 'back') && y >= 1 && y <= 6 && (x === 0 || x === 7 || x === 2 || x === 5) && y % 2 === 0) col = shade(GOLD, 0.7);
    if (y === fh - 1 && f !== 'top' && f !== 'bottom') col = GOLD;
    if (f === 'front' && y === 2 && (x === 3 || x === 4)) col = [150, 60, 230]; // an amethyst brooch
    if (y === 7 && f !== 'top' && f !== 'bottom') col = [40, 26, 18];
    if (f === 'front' && y === 7 && (x === 3 || x === 4)) col = GOLD;
    if (f === 'front' && x >= 2 && x <= 5 && y >= 9 && hash(x, y, 29) < 0.3) col = [30, 12, 44]; // rot holes
    return col;
  });
  paint(c, 'jacket', (f, x, y, fw, fh) => {
    if (y <= 2 && f !== 'bottom') return (hash(x, y, 30) < 0.18) ? [26, 24, 28] : noisy(ERM, x, y, 31, 10); // the ermine collar
    if (f === 'back' && y >= 3) return noisy(y > 9 ? ROBE2 : [110, 46, 150], x, y, 32, 14); // the mantle
    return null;
  });
  for (const [arm, sleeve, s] of [['rarm', 'rsleeve', 33], ['larm', 'lsleeve', 34]]) {
    paint(c, arm, (f, x, y, fw, fh) => y >= 9 ? noisy([200, 196, 180], x, y, s, 22) : noisy(ROBE, x, y, s, 14)); // bony hands
    paint(c, sleeve, (f, x, y, fw, fh) => {
      if (y === 8 && f !== 'top' && f !== 'bottom') return GOLD;
      if (y >= 5 && y <= 7 && f !== 'top' && f !== 'bottom') return noisy(ROBE2, x, y, s + 9, 12); // hanging sleeve
      return null;
    });
  }
  for (const [leg, pants, s] of [['rleg', 'rpants', 35], ['lleg', 'lpants', 36]]) {
    paint(c, leg, (f, x, y, fw, fh) => y >= 10 ? noisy([40, 30, 26], x, y, s, 10) : noisy(ROBE2, x, y, s, 12));
    paint(c, pants, (f, x, y, fw, fh) => (y <= 8 && f !== 'top' && f !== 'bottom' && hash(x, y, s + 3) > 0.12) ? noisy(ROBE, x, y, s + 4, 14) : null);
  }
  write(path.join(T, 'hideout/crypt_lord.png'), c.png());
}

// ---- Crimson Thralls: shipped human skins half-swallowed by Carnage's red goo
/** Smooth value noise in 0..1 (bilinear, smoothstep) on a grid of {@code scale} pixels. */
function vnoise(x, y, s, scale) {
  const gx = Math.floor(x / scale), gy = Math.floor(y / scale);
  const fx = x / scale - gx, fy = y / scale - gy;
  const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
  const a = hash(gx, gy, s), b = hash(gx + 1, gy, s), c2 = hash(gx, gy + 1, s), d = hash(gx + 1, gy + 1, s);
  return (a + (b - a) * sx) * (1 - sy) + (c2 + (d - c2) * sx) * sy;
}
function goo(c, coverage, s, opts = {}) {
  const RED = [150, 16, 24], DARK = [58, 4, 9], HI = [238, 96, 96];
  // the goo field: smooth patches; the threshold is picked so roughly {@code coverage} of the skin is swallowed
  const field = [];
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    if (c.get(x, y)[3] === 0) continue;
    const head = y < 16 && x < 32;
    let v = vnoise(x, y, s, 7) * 0.65 + vnoise(x, y, s + 1, 3) * 0.35;
    if (head && opts.faceKeep) v += 0.18; // keep more of the face
    if (y >= 52 || (y >= 20 && y < 32 && (x < 16 || x >= 40))) v -= 0.08; // hands and feet go first
    field.push([x, y, v]);
  }
  const sorted = field.map(f => f[2]).sort((p, q) => p - q);
  const cut = sorted[Math.floor(sorted.length * coverage)];
  for (const [x, y, v] of field) {
    const [r, g, b] = c.get(x, y);
    if (v < cut) {
      const fold = vnoise(x, y, s + 5, 2.5);
      let col = mix(DARK, RED, Math.min(1, fold * 1.4));
      if (fold > 0.72 && hash(x, y, s + 6) < 0.35) col = HI; // glossy highlights on the bulges
      c.set(x, y, col);
    } else if (v < cut + 0.035) {
      c.set(x, y, mix([r, g, b], DARK, 0.55)); // the edge, darkening into the skin
    } else if (v < cut + 0.07 && hash(x, y, s + 7) < 0.25) {
      c.set(x, y, mix([r, g, b], RED, 0.5)); // splatter
    }
  }
  // black tendril veins running out over the skin
  for (let i = 0; i < 10; i++) {
    let x = Math.floor(hash(i, 11, s) * 64), y = Math.floor(hash(i, 12, s) * 64);
    for (let k = 0; k < 7; k++) {
      if (x >= 0 && x < 64 && y >= 0 && y < 64 && c.get(x, y)[3]) c.set(x, y, [30, 2, 6]);
      x += Math.round(hash(k, i, s + 8) * 2 - 1); y += 1;
    }
  }
  if (opts.eyes) { // Carnage's white eye patches on the head front (8..15, 8..15)
    for (const [x, y] of [[9, 11], [10, 11], [10, 12], [13, 11], [14, 11], [13, 12]]) c.set(x, y, [240, 240, 236]);
  }
  if (opts.mouth) { // a fanged maw
    for (let x = 10; x <= 13; x++) { c.set(x, 13, [30, 2, 6]); c.set(x, 14, x % 2 ? [240, 236, 220] : [30, 2, 6]); }
  }
  return c;
}
function fromSkin(rel) { const img = decodePng(path.join(T, rel)); return canvas(64, 64, Buffer.from(img.px)); }
const THRALLS = {
  thrall: ['syndicate/site_soldier.png', 0.42, 501, { faceKeep: true }],
  brute: ['syndicate/thug_c.png', 0.6, 502, { faceKeep: true, mouth: true }],
  stalker: ['syndicate/site_agent.png', 0.82, 503, { eyes: true }],
  spitter: ['syndicate/gunman.png', 0.5, 504, { faceKeep: true, mouth: true }],
};
for (const [k, [src, cov, s, opts]] of Object.entries(THRALLS)) write(path.join(T, `hideout/soldier/${k}.png`), goo(fromSkin(src), cov, s, opts).png());

// ---- the Brood Spawn: Carnage's skin, darker, studded with egg sacs and black veins
{
  const c = fromSkin('carnage/carnage.png');
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const [r, g, b, a] = c.get(x, y);
    if (a === 0) continue;
    if (r > g + 40 && r > b + 40) c.set(x, y, [r * 0.62 + 10, g * 0.3, b * 0.35]); // deeper crimson
  }
  for (let i = 0; i < 70; i++) { // egg sacs
    const x = Math.floor(hash(i, 1, 600) * 64), y = 16 + Math.floor(hash(i, 2, 600) * 48);
    if (c.get(x, y)[3] === 0) continue;
    c.set(x, y, [255, 196, 150]); if (x + 1 < 64 && c.get(x + 1, y)[3]) c.set(x + 1, y, [220, 120, 96]);
  }
  for (let i = 0; i < 26; i++) { let x = Math.floor(hash(i, 3, 601) * 64), y = 16 + Math.floor(hash(i, 4, 601) * 40); for (let k = 0; k < 6; k++) { if (x >= 0 && x < 64 && y < 64 && c.get(x, y)[3]) c.set(x, y, [16, 2, 4]); y++; x += Math.round(hash(k, i, 602) * 2 - 1); } }
  write(path.join(T, 'hideout/brood_spawn.png'), c.png());
}

// ---------------------------------------------------------------- loot tables
const lootDir = path.join(D, 'loot_table');
const shardLoot = (item, min, max) => ({ rolls: 1, entries: [{ type: 'minecraft:item', name: item, functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } }] }] });
function bossLoot(extra) {
  return {
    type: 'minecraft:entity',
    pools: [
      shardLoot('projecthero:vibranium_shard', 3, 5),
      { rolls: 1, conditions: [{ condition: 'minecraft:random_chance', chance: 0.25 }],
        entries: [{ type: 'minecraft:item', name: 'projecthero:locator', functions: [{ function: 'minecraft:set_components',
          components: { 'projecthero:locator_target': { tier: 4 }, 'projecthero:hero_rarity': 'epic' } }] }] },
      shardLoot('minecraft:experience_bottle', 4, 8),
      ...extra,
    ],
  };
}
json(path.join(lootDir, 'entities/crypt_lord.json'), bossLoot([shardLoot('minecraft:gold_ingot', 4, 8), shardLoot('minecraft:echo_shard', 1, 2)]));
json(path.join(lootDir, 'entities/brood_spawn.json'), bossLoot([shardLoot('minecraft:redstone', 12, 24), shardLoot('minecraft:ender_pearl', 2, 4)]));
json(path.join(lootDir, 'entities/oathbound_champion.json'), bossLoot([shardLoot('minecraft:iron_ingot', 8, 14), shardLoot('minecraft:diamond', 2, 4)]));
json(path.join(lootDir, 'entities/crimson_thrall.json'), { type: 'minecraft:entity', pools: [shardLoot('minecraft:redstone', 0, 2), shardLoot('minecraft:rotten_flesh', 0, 1)] });
json(path.join(lootDir, 'entities/oath_knight.json'), { type: 'minecraft:entity', pools: [shardLoot('minecraft:iron_nugget', 1, 4), shardLoot('minecraft:arrow', 0, 2)] });
{
  const f = path.join(lootDir, 'chests/vault_tier3_artifacts.json');
  const t = readJson(f);
  const ART = { gravebound: ['gravecaller_bell', 0.2], carnage: ['brood_fang', 0.2], oathbreaker: ['oath_sigil', 0.15] };
  t.pools = t.pools.filter(p => !Object.keys(ART).includes(p.conditions[0].faction));
  for (const [fac, [item, chance]] of Object.entries(ART)) {
    t.pools.push({ rolls: 1, conditions: [{ condition: 'projecthero:near_hideout', faction: fac, radius: 32 }, { condition: 'minecraft:random_chance', chance }],
      entries: [{ type: 'minecraft:item', name: `projecthero:${item}` }] });
  }
  json(f, t);
}

// ---------------------------------------------------------------- structures
const STRUCT = {
  gravebound_crypt: { faction: 'gravebound', biomes: ['minecraft:dark_forest', 'minecraft:swamp', 'minecraft:mangrove_swamp', 'minecraft:old_growth_spruce_taiga'],
    salt: 1707731, exclusion: { other_set: 'projecthero:symbiote_hive', chunk_count: 6 } },
  carnage_nest: { faction: 'carnage', biomes: ['minecraft:plains', 'minecraft:forest', 'minecraft:flower_forest', 'minecraft:birch_forest', 'minecraft:meadow',
    'minecraft:taiga', 'minecraft:cherry_grove'], salt: 1707732, exclusion: { other_set: 'projecthero:kingpins_front', chunk_count: 6 } },
  oathbreaker_keep: { faction: 'oathbreaker', biomes: ['minecraft:windswept_hills', 'minecraft:windswept_gravelly_hills', 'minecraft:windswept_forest', 'minecraft:windswept_savanna',
    'minecraft:stony_peaks'], salt: 1707733, exclusion: { other_set: 'projecthero:ultron_forge', chunk_count: 6 } },
};
for (const [id, s] of Object.entries(STRUCT)) {
  json(path.join(D, `tags/worldgen/biome/hideout/${id}.json`), { replace: false, values: s.biomes });
  json(path.join(D, `worldgen/structure/${id}.json`), {
    type: 'projecthero:villain_hideout', faction: s.faction, biomes: `#projecthero:hideout/${id}`, step: 'surface_structures',
    terrain_adaptation: 'none', spawn_overrides: {},
  });
  const placement = { type: 'minecraft:random_spread', salt: s.salt, spacing: 150, separation: 60, frequency: 0.85, exclusion_zone: s.exclusion };
  json(path.join(D, `worldgen/structure_set/${id}.json`), { structures: [{ structure: `projecthero:${id}`, weight: 1 }], placement });
}
function tagAdd(file, ids) {
  const t = fs.existsSync(file) ? readJson(file) : { replace: false, values: [] };
  for (const id of ids) if (!t.values.includes(id)) t.values.push(id);
  json(file, t);
}
tagAdd(path.join(D, 'tags/worldgen/structure/locator/tier3.json'), Object.keys(STRUCT).map(id => `projecthero:${id}`));
tagAdd(path.join(DM, 'tags/block/mineable/hoe.json'), ['projecthero:carnage_biomass', 'projecthero:crimson_egg']);
tagAdd(path.join(DM, 'tags/block/mineable/axe.json'), ['projecthero:oath_ballista']);
console.log('new hideout assets written');
