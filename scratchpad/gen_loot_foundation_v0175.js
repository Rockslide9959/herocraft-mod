// v0.17.5 loot framework: every resource for the Hero Vault, the gear-tier materials, the hero supplies and the Locator --
// procedural 16x16 textures (Node zlib, no Python), item/block models, the vault blockstate, the four vault loot tables
// (+ empty "artifacts" / "gear" hook tables), recipes and the locator structure tags.
// Run from the repo (or worktree) root:   node scratchpad/gen_loot_foundation_v0175.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const A = path.join(root, 'src/main/resources/assets/projecthero');
const D = path.join(root, 'src/main/resources/data/projecthero');

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
let seed = 1705;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255];
const shade = (c, f) => c.map(v => clamp(v * f));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
function canvas(w = 16, h = 16) {
  const px = Buffer.alloc(w * h * 4);
  const c = {
    w, h, px,
    set(x, y, col, a = 255) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = a; },
    get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
    fill(col, noise = 0) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const n = (rnd() - 0.5) * noise; c.set(x, y, col.map(v => v + n)); } },
    rect(x0, y0, x1, y1, col, noise = 0) { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const n = (rnd() - 0.5) * noise; c.set(x, y, col.map(v => v + n)); } },
    line(x0, y0, x1, y1, col) { const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1; for (let i = 0; i <= n; i++) c.set(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, col); },
    map(rows, pal) { rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) c.set(x, y, pal[ch]); })); },
    outline(col) { // dark outline around every opaque pixel, on transparent neighbours
      const op = (x, y) => x >= 0 && y >= 0 && x < w && y < h && px[(y * w + x) * 4 + 3] > 0;
      const add = [];
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (!op(x, y) && (op(x - 1, y) || op(x + 1, y) || op(x, y - 1) || op(x, y + 1))) add.push([x, y]);
      add.forEach(([x, y]) => c.set(x, y, col));
    },
    png() { return png(w, h, px); },
  };
  return c;
}
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');
const tex = (rel, c) => write(path.join(A, 'textures', rel + '.png'), c.png());

// ---------------------------------------------------------------- Hero Vault (4 tiers: iron, gold, purple, aqua trims)
const TRIMS = { 1: 0xC9D1D9, 2: 0xF2C14E, 3: 0xB35CFF, 4: 0x3FE6E0 };
const BODY = [52, 57, 66];
function vaultBase(trim) {
  const c = canvas();
  c.fill(BODY, 10);
  // brushed panel streaks
  for (let y = 0; y < 16; y += 3) for (let x = 0; x < 16; x++) if (rnd() < 0.35) c.set(x, y, shade(BODY, 1.12));
  // frame: dark bevel + trim border
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(trim, 0.55)); c.set(i, 15, shade(trim, 0.4)); c.set(0, i, shade(trim, 0.5)); c.set(15, i, shade(trim, 0.4)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, trim); c.set(1, i, shade(trim, 0.85)); c.set(i, 14, shade(trim, 0.7)); c.set(14, i, shade(trim, 0.7)); }
  for (let i = 2; i < 14; i++) { c.set(i, 2, shade(BODY, 0.6)); c.set(2, i, shade(BODY, 0.65)); }
  // rivets in the corners
  for (const [x, y] of [[3, 3], [12, 3], [3, 12], [12, 12]]) { c.set(x, y, shade(trim, 1.15)); c.set(x + 1, y + 1, shade(BODY, 0.55)); }
  return c;
}
function vaultFront(tier) {
  const trim = hex(TRIMS[tier]);
  const c = vaultBase(trim);
  const cx = 7.5, cy = 7.5;
  for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) {
    const d = Math.hypot(x - cx, y - cy);
    if (d <= 4.6 && d > 3.6) c.set(x, y, mix(trim, [255, 255, 255], (y < cy ? 0.25 : 0)).map((v, i) => v * (y < cy ? 1 : 0.75)));
    else if (d <= 3.6) c.set(x, y, shade(BODY, 0.75 + (cy - y) * 0.03));
  }
  // spokes of the wheel
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) for (let r = 1; r <= 3; r++) c.set(Math.round(cx + dx * r - (dx < 0 ? 0 : 0)) - (dx === -1 ? 0 : 0), Math.round(cy + dy * r), shade(trim, 0.9));
  c.rect(7, 7, 8, 8, mix(trim, [255, 255, 255], 0.55)); // glowing hub
  // tier pips under the wheel
  for (let i = 0; i < tier; i++) c.set(8 - tier + i * 2, 13, mix(trim, [255, 255, 255], 0.4));
  return c;
}
function vaultSide(tier) {
  const trim = hex(TRIMS[tier]);
  const c = vaultBase(trim);
  // reinforcing band across the middle + bolts
  for (let x = 2; x < 14; x++) { c.set(x, 7, shade(trim, 0.8)); c.set(x, 8, shade(trim, 0.55)); }
  for (const x of [4, 7, 11]) c.set(x, 7, mix(trim, [255, 255, 255], 0.35));
  return c;
}
function vaultTop(tier) {
  const trim = hex(TRIMS[tier]);
  const c = vaultBase(trim);
  // a diamond emblem in the trim colour
  for (let y = 4; y <= 11; y++) for (let x = 4; x <= 11; x++) {
    const d = Math.abs(x - 7.5) + Math.abs(y - 7.5);
    if (d <= 3.6 && d > 2.4) c.set(x, y, shade(trim, 0.95));
    else if (d <= 2.4) c.set(x, y, shade(trim, 0.55 + (11 - y) * 0.04));
  }
  return c;
}
for (let t = 1; t <= 4; t++) {
  tex(`block/hero_vault_tier${t}_front`, vaultFront(t));
  tex(`block/hero_vault_tier${t}_side`, vaultSide(t));
  tex(`block/hero_vault_tier${t}_top`, vaultTop(t));
  json(path.join(A, `models/block/hero_vault_tier${t}.json`), {
    parent: 'minecraft:block/orientable',
    textures: {
      top: `projecthero:block/hero_vault_tier${t}_top`,
      bottom: `projecthero:block/hero_vault_tier${t}_side`,
      front: `projecthero:block/hero_vault_tier${t}_front`,
      side: `projecthero:block/hero_vault_tier${t}_side`,
      particle: `projecthero:block/hero_vault_tier${t}_side`,
    },
  });
}
const ROT = { north: 0, east: 90, south: 180, west: 270 };
const variants = {};
for (const f of Object.keys(ROT)) for (let t = 1; t <= 4; t++) {
  const v = { model: `projecthero:block/hero_vault_tier${t}` };
  if (ROT[f]) v.y = ROT[f];
  variants[`facing=${f},tier=${t}`] = v;
}
json(path.join(A, 'blockstates/hero_vault.json'), { variants });
// the item shows tier 1 by default; creative stacks override the model by their block_state component
json(path.join(A, 'models/item/hero_vault.json'), {
  parent: 'projecthero:block/hero_vault_tier1',
  overrides: [2, 3, 4].map(t => ({ predicate: { 'projecthero:vault_tier': t / 4 }, model: `projecthero:item/hero_vault_tier${t}` })),
});
for (const t of [2, 3, 4]) json(path.join(A, `models/item/hero_vault_tier${t}.json`), { parent: `projecthero:block/hero_vault_tier${t}` });

// ---------------------------------------------------------------- materials
const K = [20, 22, 28];
function salvagedTech() {
  const c = canvas();
  c.map([
    '................',
    '................',
    '..........gg....',
    '.....ggggGGgg...',
    '....gGGGGGGGgg..',
    '...gGcccGGkkkg..',
    '..gGGGGcGGkkkg..',
    '..gGmmGcGGkkkGg.',
    '..gGmmGccccGGGg.',
    '..gGGGGGGGcGGg..',
    '...gGGcccGcGgg..',
    '...ggGGGcGGg....',
    '.....ggGGGg.....',
    '.......ggg......',
    '................',
    '................',
  ], { g: [38, 92, 52], G: [58, 132, 72], c: [214, 150, 72], k: [30, 30, 36], m: [170, 176, 186] });
  // chip pins + a torn wire
  for (const x of [10, 11, 12]) c.set(x, 4, [160, 165, 175]);
  c.line(12, 10, 14, 13, [200, 60, 50]); c.set(14, 14, [220, 200, 120]);
  c.outline(K);
  return c;
}
function ingot(body, light, dark, stripe) {
  const c = canvas();
  const rows = [
    '................',
    '................',
    '................',
    '..........LLL...',
    '........LLHHHL..',
    '......LLHHBBBD..',
    '....LLHHBBBBDD..',
    '..LLHHBBBBSDD...',
    '.LHHBBBBSSDD....',
    '.LBBBBSSBDD.....',
    '.LBBSSBBDD......',
    '.DSSBBDDD.......',
    '..DDDDD.........',
    '................',
    '................',
    '................',
  ];
  c.map(rows, { L: light, H: mix(light, body, 0.4), B: body, D: dark, S: stripe || body });
  c.outline(K);
  return c;
}
function heroAlloyScrap() {
  const c = canvas();
  const B = [104, 132, 168], L = [176, 200, 228], Dk = [60, 76, 104], G = [232, 190, 86];
  c.map([
    '................',
    '................',
    '.........LL.....',
    '........LBBD....',
    '.......LBBGD....',
    '........DDD.....',
    '...LL...........',
    '..LBBL......L...',
    '..BBGBD....LBD..',
    '...DBBD...LBBD..',
    '....DD....BGD...',
    '..........DD....',
    '.....LLL........',
    '.....BBBD.......',
    '......DD........',
    '................',
  ], { L, B, D: Dk, G });
  c.outline(K);
  return c;
}
function vibraniumShard() {
  const c = canvas();
  // purple-grey like the existing Vibranium Ingot
  const H = [222, 206, 255], L = [176, 150, 226], M = [128, 100, 186], Dk = [78, 58, 124];
  c.map([
    '................',
    '............H...',
    '...........HLM..',
    '..........HLMM..',
    '.........HLMMD..',
    '........HLMMD...',
    '.......HLMMD....',
    '......HLMMD.....',
    '.....HLMMD......',
    '....HLMMD.......',
    '..H.LMMD........',
    '.HLDMMD.........',
    '.LMDDD..........',
    '.MD.............',
    '................',
    '................',
  ], { H, L, M, D: Dk });
  c.set(11, 3, [255, 255, 255]); c.set(7, 7, [240, 230, 255]);
  c.outline([36, 24, 58]);
  return c;
}
function celestialCore() {
  const c = canvas();
  const cx = 7.5, cy = 7.5;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x - cx, y - cy);
    if (d <= 5.2) {
      const t = d / 5.2;
      const col = t < 0.35 ? mix([255, 255, 255], [190, 250, 255], t / 0.35) : t < 0.75 ? mix([190, 250, 255], [60, 200, 240], (t - 0.35) / 0.4) : mix([60, 200, 240], [40, 70, 170], (t - 0.75) / 0.25);
      c.set(x, y, col);
    }
  }
  // a gold orbit band (tilted ellipse) crossing the core
  for (let a = 0; a < Math.PI * 2; a += 0.05) {
    const ex = cx + Math.cos(a) * 7.0, ey = cy + Math.sin(a) * 2.4;
    const rx = cx + (ex - cx) * Math.cos(-0.5) - (ey - cy) * Math.sin(-0.5);
    const ry = cy + (ex - cx) * Math.sin(-0.5) + (ey - cy) * Math.cos(-0.5);
    const behind = Math.sin(a) < 0 && Math.hypot(rx - cx, ry - cy) < 5.2;
    if (!behind) c.set(rx, ry, Math.sin(a) > 0 ? [250, 214, 110] : [200, 150, 60]);
  }
  c.set(6, 5, [255, 255, 255]); c.set(5, 6, [255, 255, 255]);
  // sparkles
  for (const [x, y] of [[1, 2], [14, 3], [13, 13], [2, 12]]) c.set(x, y, [210, 250, 255]);
  return c;
}

// ---------------------------------------------------------------- supplies
function energyCell() {
  const c = canvas();
  const metal = [150, 158, 170], metalD = [86, 92, 104], glass = [36, 60, 72];
  c.rect(6, 1, 9, 2, metal); c.rect(7, 1, 8, 1, [200, 206, 214]);
  c.rect(4, 3, 11, 14, metalD);
  c.rect(5, 3, 10, 4, metal); c.rect(5, 13, 10, 14, metal);
  c.rect(5, 5, 10, 12, glass);
  // glowing charge bars (bottom four lit)
  const lit = [[120, 255, 140], [150, 255, 120], [200, 250, 110], [230, 240, 100]];
  for (let i = 0; i < 4; i++) { c.rect(6, 11 - i * 2, 9, 11 - i * 2, lit[i]); }
  for (let y = 5; y <= 12; y++) c.set(5, y, [80, 120, 130]);
  c.set(10, 5, [120, 170, 180]);
  c.outline(K);
  return c;
}
function cooldownStim() {
  const c = canvas();
  // syringe from bottom-left (needle) to top-right (plunger)
  for (let i = 0; i < 8; i++) { // barrel
    const x = 4 + i, y = 11 - i;
    c.set(x, y, [210, 220, 230]); c.set(x + 1, y, i < 6 ? [70, 170, 255] : [210, 220, 230]); c.set(x, y - 1, i < 6 ? [120, 200, 255] : [210, 220, 230]);
    c.set(x + 1, y - 1, i < 6 ? [40, 120, 220] : [190, 200, 210]);
  }
  c.line(1, 14, 4, 11, [180, 186, 196]); c.set(1, 14, [230, 236, 246]); // needle
  c.line(12, 3, 14, 1, [120, 126, 140]); c.rect(13, 0, 15, 1, [90, 96, 110]); // plunger
  c.line(10, 6, 12, 8, [90, 96, 110]); c.line(9, 3, 11, 5, [90, 96, 110]); // finger flange
  c.outline(K);
  return c;
}
function suitRepairKit() {
  const c = canvas();
  const red = [190, 44, 44], redL = [226, 86, 76], redD = [120, 24, 30];
  c.rect(5, 3, 10, 4, [70, 70, 78]); c.rect(6, 4, 9, 4, [0, 0, 0], 0); // handle
  c.set(5, 5, [70, 70, 78]); c.set(10, 5, [70, 70, 78]);
  c.rect(1, 6, 14, 13, red);
  c.rect(1, 6, 14, 6, redL); c.rect(1, 13, 14, 13, redD); c.rect(14, 7, 14, 12, redD);
  c.rect(1, 8, 14, 8, [70, 70, 78]); // lid seam
  c.rect(7, 8, 8, 9, [230, 200, 90]); // latch
  // a wrench across the front
  c.line(3, 12, 10, 10, [200, 206, 214]); c.set(11, 10, [200, 206, 214]); c.set(12, 9, [200, 206, 214]); c.set(12, 11, [200, 206, 214]);
  c.set(2, 12, [200, 206, 214]); c.set(2, 13, [160, 166, 176]);
  // clear the handle hole
  c.set(6, 4, [0, 0, 0], 0); c.set(7, 4, [0, 0, 0], 0); c.set(8, 4, [0, 0, 0], 0); c.set(9, 4, [0, 0, 0], 0);
  c.outline(K);
  return c;
}
function medKit() {
  const c = canvas();
  const w = [236, 238, 242], wD = [180, 186, 196], red = [214, 40, 44];
  c.rect(5, 2, 10, 3, [90, 94, 104]); c.set(6, 3, [0, 0, 0], 0); c.set(7, 3, [0, 0, 0], 0); c.set(8, 3, [0, 0, 0], 0); c.set(9, 3, [0, 0, 0], 0);
  c.set(5, 4, [90, 94, 104]); c.set(10, 4, [90, 94, 104]);
  c.rect(1, 5, 14, 14, w);
  c.rect(1, 14, 14, 14, wD); c.rect(14, 5, 14, 14, wD); c.rect(1, 5, 13, 5, [255, 255, 255]);
  c.rect(7, 7, 8, 12, red); c.rect(5, 9, 10, 10, red); // the cross
  c.rect(7, 7, 8, 7, [240, 90, 90]); c.rect(5, 9, 6, 9, [240, 90, 90]);
  c.outline(K);
  return c;
}

// ---------------------------------------------------------------- Locator (body + 32 needle layers)
function locatorBase() {
  const c = canvas();
  const cx = 7.5, cy = 8.0;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x - cx, y - cy);
    if (d <= 6.9 && d > 5.6) c.set(x, y, y < cy ? [120, 128, 142] : [74, 80, 94]); // bezel
    else if (d <= 5.6 && d > 4.9) c.set(x, y, [60, 214, 222]); // cyan tracking ring
    else if (d <= 4.9) c.set(x, y, mix([24, 40, 58], [14, 22, 34], (y - 3) / 10)); // glass face
  }
  // antenna nub + tick marks
  c.rect(7, 0, 8, 1, [150, 158, 170]); c.set(7, 0, [230, 80, 70]);
  for (const [x, y] of [[7, 4], [12, 8], [7, 12], [3, 8]]) c.set(x, y, [90, 150, 170]);
  c.set(5, 5, [70, 100, 130]);
  c.outline(K);
  return c;
}
function locatorNeedle(i) {
  // vanilla compass convention: frame 16 points up (target ahead), clockwise as the index grows
  const c = canvas();
  const a = (i - 16) / 32 * Math.PI * 2;
  const cx = 7.5, cy = 8.0;
  const dx = Math.sin(a), dy = -Math.cos(a);
  for (let s = 0; s <= 4.2; s += 0.25) c.set(cx + dx * s, cy + dy * s, s > 2.2 ? [255, 92, 70] : [255, 170, 120]);
  for (let s = 0.25; s <= 2.4; s += 0.25) c.set(cx - dx * s, cy - dy * s, [210, 220, 230]);
  c.set(cx, cy, [255, 255, 255]);
  return c;
}

const items = {
  salvaged_tech: salvagedTech(),
  hero_alloy_scrap: heroAlloyScrap(),
  hero_alloy_ingot: ingot([112, 140, 178], [196, 216, 240], [62, 78, 108], [236, 192, 84]),
  vibranium_shard: vibraniumShard(),
  celestial_core: celestialCore(),
  energy_cell: energyCell(),
  cooldown_stim: cooldownStim(),
  suit_repair_kit: suitRepairKit(),
  med_kit: medKit(),
};
for (const [id, c] of Object.entries(items)) {
  tex(`item/${id}`, c);
  json(path.join(A, `models/item/${id}.json`), { parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/${id}` } });
}
tex('item/locator_base', locatorBase());
const overrides = [];
const frame = i => `projecthero:item/locator_${String(i).padStart(2, '0')}`;
for (let i = 0; i < 32; i++) {
  tex(`item/locator_needle_${String(i).padStart(2, '0')}`, locatorNeedle(i));
  json(path.join(A, `models/item/locator_${String(i).padStart(2, '0')}.json`), {
    parent: 'minecraft:item/generated',
    textures: { layer0: 'projecthero:item/locator_base', layer1: `projecthero:item/locator_needle_${String(i).padStart(2, '0')}` },
  });
}
overrides.push({ predicate: { angle: 0.0 }, model: frame(16) });
for (let k = 1; k < 32; k++) overrides.push({ predicate: { angle: +(k / 32 - 1 / 64).toFixed(6) }, model: frame((16 + k) % 32) });
overrides.push({ predicate: { angle: +(1 - 1 / 64).toFixed(6) }, model: frame(16) });
json(path.join(A, 'models/item/locator.json'), {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'projecthero:item/locator_base', layer1: 'projecthero:item/locator_needle_16' },
  overrides,
});

// ---------------------------------------------------------------- loot tables
const item = (name, weight, min, max, extra) => {
  const e = { type: 'minecraft:item', name, weight };
  const fns = [];
  if (min !== undefined && (min !== 1 || max !== 1)) fns.push({ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } });
  if (extra) fns.push(...extra);
  if (fns.length) e.functions = fns;
  return e;
};
const pool = (rolls, entries, chance) => {
  const p = { rolls: typeof rolls === 'number' ? rolls : { type: 'minecraft:uniform', min: rolls[0], max: rolls[1] }, entries };
  if (chance !== undefined) p.conditions = [{ condition: 'minecraft:random_chance', chance }];
  return p;
};
const locator = (tier, chance) => pool(1, [item('projecthero:locator', 1, 1, 1, [{ function: 'minecraft:set_components', components: {
  'projecthero:locator_target': { tier }, 'projecthero:hero_rarity': tier === 2 ? 'uncommon' : tier === 3 ? 'rare' : 'epic' } }])], chance);
const hook = (t, kind) => pool(1, [{ type: 'minecraft:loot_table', value: `projecthero:chests/vault_tier${t}_${kind}` }]);
const book = (levels) => item('minecraft:book', 1, 1, 1, [levels ? { function: 'minecraft:enchant_with_levels', levels } : { function: 'minecraft:enchant_randomly' }]);
const supplies = (rolls, w) => pool(rolls, [
  item('projecthero:med_kit', w.med, 1, 2), item('projecthero:energy_cell', w.cell, 1, 2),
  item('projecthero:suit_repair_kit', w.repair, 1, 1), item('projecthero:cooldown_stim', w.stim, 1, 1)]);
const serums = (chance) => pool(1, [item('projecthero:mutagenic_serum', 6), item('projecthero:heroic_serum', 3), item('projecthero:prismatic_serum', 2)], chance);

const tables = {
  1: [
    pool(1, [item('projecthero:salvaged_tech', 1, 3, 7)]),
    supplies(1, { med: 6, cell: 4, repair: 2, stim: 0 }),
    pool([2, 4], [item('minecraft:iron_ingot', 10, 1, 4), item('minecraft:gold_nugget', 8, 3, 9), item('minecraft:bread', 8, 2, 4),
      item('minecraft:arrow', 6, 4, 10), item('minecraft:torch', 6, 4, 8), item('minecraft:copper_ingot', 6, 2, 6), item('minecraft:redstone', 5, 2, 6)]),
    locator(2, 0.35),
    hook(1, 'artifacts'), hook(1, 'gear'),
  ],
  2: [
    pool(1, [item('projecthero:salvaged_tech', 1, 4, 9)]),
    pool(1, [item('projecthero:hero_alloy_scrap', 3, 3, 8), item('projecthero:hero_alloy_ingot', 1, 1, 2)]),
    supplies([1, 2], { med: 5, cell: 5, repair: 3, stim: 2 }),
    pool([2, 4], [item('minecraft:iron_ingot', 8, 2, 6), item('minecraft:gold_ingot', 8, 1, 4), item('minecraft:emerald', 5, 1, 3),
      item('minecraft:lapis_lazuli', 5, 3, 8), item('minecraft:diamond', 2, 1, 2), item('minecraft:experience_bottle', 4, 1, 3)]),
    pool(1, [book()], 0.35),
    locator(3, 0.45),
    hook(2, 'artifacts'), hook(2, 'gear'),
  ],
  3: [
    pool(1, [item('projecthero:hero_alloy_ingot', 1, 2, 4)]),
    pool(1, [item('projecthero:vibranium_shard', 1, 1, 3)]),
    supplies([2, 3], { med: 4, cell: 5, repair: 4, stim: 3 }),
    pool([2, 4], [item('minecraft:diamond', 6, 2, 4), item('minecraft:gold_ingot', 6, 2, 6), item('minecraft:emerald', 5, 2, 5),
      item('minecraft:experience_bottle', 5, 2, 5), item('minecraft:obsidian', 3, 2, 4)]),
    pool(1, [item('minecraft:golden_apple', 1, 1, 2)], 0.5),
    pool(1, [book({ type: 'minecraft:uniform', min: 20, max: 30 })], 0.6),
    serums(0.08),
    locator(4, 0.55),
    hook(3, 'artifacts'), hook(3, 'gear'),
  ],
  4: [
    pool(1, [item('projecthero:vibranium_shard', 1, 3, 6)]),
    pool(1, [item('projecthero:celestial_core', 1)], 0.6),
    pool(1, [item('projecthero:hero_alloy_ingot', 1, 3, 6)]),
    supplies([3, 4], { med: 4, cell: 5, repair: 4, stim: 4 }),
    pool([3, 5], [item('minecraft:diamond', 6, 4, 8), item('minecraft:netherite_scrap', 3, 1, 2), item('minecraft:ancient_debris', 2, 1, 2),
      item('minecraft:emerald', 4, 4, 8), item('minecraft:experience_bottle', 4, 4, 8)]),
    pool(1, [item('minecraft:golden_apple', 1, 2, 3)]),
    pool(1, [item('minecraft:enchanted_golden_apple', 1)], 0.08),
    pool(2, [book({ type: 'minecraft:constant', value: 30 })]),
    serums(0.3),
    hook(4, 'artifacts'), hook(4, 'gear'),
  ],
};
for (const [t, pools] of Object.entries(tables)) {
  json(path.join(D, `loot_table/chests/vault_tier${t}.json`), { type: 'minecraft:chest', pools, random_sequence: `projecthero:chests/vault_tier${t}` });
  for (const kind of ['artifacts', 'gear']) {
    const p = path.join(D, `loot_table/chests/vault_tier${t}_${kind}.json`);
    if (!fs.existsSync(p)) json(p, { type: 'minecraft:chest', pools: [] }); // hook point: later features add pools here
  }
}

// ---------------------------------------------------------------- recipes
json(path.join(D, 'recipe/hero_alloy_ingot.json'), {
  type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: [{ item: 'projecthero:salvaged_tech' }, { item: 'projecthero:salvaged_tech' }, { item: 'projecthero:salvaged_tech' },
    { item: 'projecthero:salvaged_tech' }, { item: 'minecraft:iron_ingot' }],
  result: { id: 'projecthero:hero_alloy_ingot', count: 1 },
});
json(path.join(D, 'recipe/hero_alloy_ingot_from_scrap.json'), {
  type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['###', '###', '###'], key: { '#': { item: 'projecthero:hero_alloy_scrap' } },
  result: { id: 'projecthero:hero_alloy_ingot', count: 1 },
});
json(path.join(D, 'recipe/hero_alloy_scrap.json'), {
  type: 'minecraft:crafting_shapeless', category: 'misc', ingredients: [{ item: 'projecthero:hero_alloy_ingot' }],
  result: { id: 'projecthero:hero_alloy_scrap', count: 9 },
});
json(path.join(D, 'recipe/vibranium_ingot_from_shards.json'), {
  type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: Array(4).fill({ item: 'projecthero:vibranium_shard' }),
  result: { id: 'projecthero:vibranium_ingot', count: 1 },
});

// ---------------------------------------------------------------- locator structure tags
const TIER2 = ['dig_site', 'fallen_lantern_site', 'gamma_lab', 'geological_site', 'government_site', 'graveyard', 'hydrostatic_facility',
  'kryptonite_crater', 'meteor_impact', 'mjolnir_crater', 'nova_pod_site', 'power_station', 'research_facility', 'steel_crash_site',
  'symbiote_lab', 'symbiote_meteor', 'temple_of_khonshu', 'tomb_of_nabu', 'vigilante_safehouse'];
const tagDir = path.join(D, 'tags/worldgen/structure/locator');
json(path.join(tagDir, 'tier2.json'), { replace: false, values: TIER2.map(s => 'projecthero:' + s) });
for (const t of [3, 4]) { const p = path.join(tagDir, `tier${t}.json`); if (!fs.existsSync(p)) json(p, { replace: false, values: [] }); }

console.log('loot foundation resources written under', root);
