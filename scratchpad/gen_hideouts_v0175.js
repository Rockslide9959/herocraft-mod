// v0.17.5 Tier 3 villain hideouts: every resource -- block textures / models / blockstates for the core, sealed vault,
// alarm, vent, hive biomass and pod; the five artifact item textures + models; the four mini-boss skins (recoloured
// from the factions' shipped skins: thug_a, heavy, parademon_elite, carnage); loot tables (boss drops, the tier 3
// vault artifact pools); the five structures + structure sets + biome tags; the Locator tier 3 tag; mineable tags.
// Procedural PNGs with Node zlib (no Python). Run from the repo (or worktree) root:
//   node scratchpad/gen_hideouts_v0175.js [root]
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

const FACTIONS = {
  kingpin: { col: 0xE0B23A, name: "Kingpin's Front" },
  ultron: { col: 0xE0281E },
  apokolips: { col: 0xFF7A1A },
  symbiote: { col: 0xE8E8F0 },
  frost: { col: 0x7FD8FF },
};
const STEEL = [46, 50, 58];

// ---------------------------------------------------------------- Hideout Core
function steelFrame(c, base = STEEL) {
  c.fill(base, 8);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(base, 0.55)); c.set(i, 15, shade(base, 0.45)); c.set(0, i, shade(base, 0.55)); c.set(15, i, shade(base, 0.45)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, shade(base, 1.35)); c.set(1, i, shade(base, 1.2)); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(x, y, shade(base, 1.6));
}
function core(faction, sealed) {
  const glow = hex(FACTIONS[faction].col);
  const c = canvas();
  steelFrame(c);
  // the crystal: a diamond of the faction colour, hottest in the middle
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const d = Math.abs(x - 7.5) + Math.abs(y - 7.5);
    if (d < 5.6) {
      const t = 1 - d / 5.6;
      const col = mix(shade(glow, 0.45), mix(glow, [255, 255, 255], 0.55), t);
      c.set(x, y, sealed ? shade(col, 0.6) : col);
    } else if (d < 6.4) c.set(x, y, shade(glow, sealed ? 0.25 : 0.35));
  }
  if (sealed) {
    // bars and a lock plate across it
    for (let i = 0; i < 16; i++) { c.set(i, 5, [70, 72, 80]); c.set(i, 10, [70, 72, 80]); c.set(5, i, [64, 66, 74]); c.set(10, i, [64, 66, 74]); }
    for (const v of [5, 10]) { c.set(v, 5, [140, 142, 150]); c.set(v, 10, [140, 142, 150]); }
    c.rect(6, 6, 9, 9, [36, 38, 44]); c.rect(7, 7, 8, 8, shade(glow, 0.9));
  }
  return c;
}
const BOOL = [false, true];
const coreVariants = {};
for (const f of Object.keys(FACTIONS)) for (const s of BOOL) {
  const name = `hideout_core_${f}${s ? '_sealed' : ''}`;
  tex(`block/${name}`, core(f, s));
  json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${name}` } });
  coreVariants[`faction=${f},sealed=${s}`] = { model: `projecthero:block/${name}` };
}
json(path.join(A, 'blockstates/hideout_core.json'), { variants: coreVariants });
json(path.join(A, 'models/item/hideout_core.json'), { parent: 'projecthero:block/hideout_core_kingpin' });

// ---------------------------------------------------------------- Sealed Hideout Vault
const VAULT_TRIM = [179, 92, 255]; // the tier 3 vault purple
function sealSide() { const c = canvas(); steelFrame(c, [40, 42, 52]); for (let x = 2; x < 14; x++) { c.set(x, 7, shade(VAULT_TRIM, 0.6)); c.set(x, 8, shade(VAULT_TRIM, 0.4)); } return c; }
function sealTop() { const c = canvas(); steelFrame(c, [40, 42, 52]); c.rect(5, 5, 10, 10, shade(VAULT_TRIM, 0.45)); c.rect(6, 6, 9, 9, shade(VAULT_TRIM, 0.7)); return c; }
function sealFront(faction) {
  const glow = hex(FACTIONS[faction].col);
  const c = canvas(); steelFrame(c, [40, 42, 52]);
  // the vault wheel underneath
  for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d <= 4.6 && d > 3.6) c.set(x, y, shade(VAULT_TRIM, 0.7)); }
  // bars in the faction colour, a padlock in the middle
  for (let i = 1; i < 15; i++) for (const x of [3, 6, 9, 12]) c.set(x, i, shade(glow, 0.55 + (i % 3 === 0 ? 0.25 : 0)));
  for (let x = 1; x < 15; x++) { c.set(x, 4, shade(glow, 0.7)); c.set(x, 11, shade(glow, 0.5)); }
  c.rect(6, 7, 9, 11, shade(glow, 0.95)); c.rect(7, 5, 8, 6, [0, 0, 0], 0); c.set(6, 6, shade(glow, 0.8)); c.set(9, 6, shade(glow, 0.8));
  c.set(6, 5, shade(glow, 0.8)); c.set(9, 5, shade(glow, 0.8)); c.set(7, 4, shade(glow, 0.8)); c.set(8, 4, shade(glow, 0.8));
  c.rect(7, 8, 8, 9, [20, 20, 24]);
  return c;
}
tex('block/hideout_seal_side', sealSide());
tex('block/hideout_seal_top', sealTop());
const ROT = { north: 0, east: 90, south: 180, west: 270 };
const sealVariants = {};
for (const f of Object.keys(FACTIONS)) {
  tex(`block/hideout_seal_${f}_front`, sealFront(f));
  json(path.join(A, `models/block/hideout_seal_${f}.json`), {
    parent: 'minecraft:block/orientable',
    textures: { top: 'projecthero:block/hideout_seal_top', bottom: 'projecthero:block/hideout_seal_side', side: 'projecthero:block/hideout_seal_side',
      front: `projecthero:block/hideout_seal_${f}_front`, particle: 'projecthero:block/hideout_seal_side' },
  });
  for (const d of Object.keys(ROT)) { const v = { model: `projecthero:block/hideout_seal_${f}` }; if (ROT[d]) v.y = ROT[d]; sealVariants[`facing=${d},faction=${f}`] = v; }
}
json(path.join(A, 'blockstates/hideout_seal.json'), { variants: sealVariants });
json(path.join(A, 'models/item/hideout_seal.json'), { parent: 'projecthero:block/hideout_seal_kingpin' });

// ---------------------------------------------------------------- Alarms
function alarm(faction, spent) {
  const c = canvas();
  const dim = spent ? 0.45 : 1;
  if (faction === 'kingpin') { // red siren box
    steelFrame(c, [120, 22, 22]);
    for (let y = 4; y < 12; y++) for (let x = 4; x < 12; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d < 3.6) c.set(x, y, shade(mix([255, 60, 40], [255, 230, 200], 1 - d / 3.6), dim)); }
    for (let x = 3; x < 13; x += 2) c.set(x, 13, [230, 230, 230]);
  } else if (faction === 'ultron') { // red sensor eye in steel
    steelFrame(c);
    for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d < 4.5 && d > 3.5) c.set(x, y, [90, 94, 104]); else if (d <= 3.5) c.set(x, y, shade(mix([140, 0, 0], [255, 90, 70], 1 - d / 3.5), dim)); }
  } else if (faction === 'apokolips') { // fire grille / Boom Tube beacon
    c.fill([34, 26, 24], 10);
    for (let i = 0; i < 16; i++) { c.set(i, 0, [70, 50, 30]); c.set(i, 15, [20, 14, 12]); }
    for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) if ((x + y) % 3 === 0) c.set(x, y, shade(mix([255, 120, 20], [255, 220, 120], rnd() * 0.5), dim));
    c.rect(6, 6, 9, 9, shade([255, 240, 200], dim));
  } else if (faction === 'symbiote') { // a pale egg pod with dark veins
    c.fill(spent ? [60, 60, 66] : [214, 214, 224], 14);
    for (let i = 0; i < 6; i++) { let x = 8, y = 8; for (let s = 0; s < 9; s++) { x += Math.round(rnd() * 2 - 1); y += Math.round(rnd() * 2 - 1); c.set(x, y, [22, 22, 30]); } }
    if (spent) { for (let x = 3; x < 13; x++) c.set(x, 7 + Math.round(Math.sin(x) * 1.5), [10, 10, 14]); }
    else for (let y = 6; y <= 9; y++) for (let x = 6; x <= 9; x++) c.set(x, y, mix(c.get(x, y), [255, 255, 255], 0.5));
  } else { // frost war horn on a carved plank
    c.fill([88, 62, 40], 12);
    for (let y = 0; y < 16; y += 4) for (let x = 0; x < 16; x++) c.set(x, y, [62, 42, 26]);
    const horn = spent ? [110, 120, 128] : [200, 230, 255];
    for (let x = 3; x <= 12; x++) { const y = 10 - Math.round((x - 3) * 0.55); const r = x < 6 ? 0 : x < 10 ? 1 : 2; for (let k = -r; k <= r; k++) c.set(x, y + k, shade(horn, 0.8 + (k === -r ? 0.2 : 0))); }
    c.set(3, 10, [60, 70, 80]);
  }
  return c;
}
const alarmVariants = {};
for (const f of Object.keys(FACTIONS)) for (const s of BOOL) {
  const name = `hideout_alarm_${f}${s ? '_spent' : ''}`;
  tex(`block/${name}`, alarm(f, s));
  json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${name}` } });
  alarmVariants[`faction=${f},spent=${s}`] = { model: `projecthero:block/${name}` };
}
json(path.join(A, 'blockstates/hideout_alarm.json'), { variants: alarmVariants });
json(path.join(A, 'models/item/hideout_alarm.json'), { parent: 'projecthero:block/hideout_alarm_kingpin' });

// ---------------------------------------------------------------- Vents
const VENT = { fire: [255, 110, 20], plasma: [255, 50, 40], biomass: [70, 30, 90], frost: [140, 220, 255] };
function ventTop(kind, hot) {
  const c = canvas(); steelFrame(c, [52, 52, 56]);
  const g = VENT[kind];
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const slot = y % 3 !== 1;
    if (!slot) c.set(x, y, [30, 30, 34]);
    else { const t = 1 - Math.hypot(x - 7.5, y - 7.5) / 8; c.set(x, y, shade(mix(shade(g, 0.4), mix(g, [255, 255, 255], hot ? 0.6 : 0.15), t), hot ? 1.0 : 0.65)); }
  }
  return c;
}
function ventSide() { const c = canvas(); steelFrame(c, [52, 52, 56]); for (let y = 4; y < 12; y += 2) for (let x = 3; x < 13; x++) c.set(x, y, [36, 36, 40]); return c; }
tex('block/hideout_vent_side', ventSide());
const ventVariants = {};
for (const k of Object.keys(VENT)) for (const hot of BOOL) {
  const name = `hideout_vent_${k}${hot ? '_hot' : ''}`;
  tex(`block/${name}`, ventTop(k, hot));
  json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_bottom_top',
    textures: { top: `projecthero:block/${name}`, bottom: 'projecthero:block/hideout_vent_side', side: 'projecthero:block/hideout_vent_side', particle: 'projecthero:block/hideout_vent_side' } });
}
for (const k of Object.keys(VENT)) for (const p of [0, 1, 2]) ventVariants[`kind=${k},phase=${p}`] = { model: `projecthero:block/hideout_vent_${k}${p === 1 ? '_hot' : ''}` };
json(path.join(A, 'blockstates/hideout_vent.json'), { variants: ventVariants });
json(path.join(A, 'models/item/hideout_vent.json'), { parent: 'projecthero:block/hideout_vent_fire' });

// ---------------------------------------------------------------- Hive biomass + pod
function biomass() {
  const c = canvas(); c.fill([14, 14, 20], 8);
  for (let i = 0; i < 18; i++) { const x = Math.floor(rnd() * 16), y = Math.floor(rnd() * 16); c.set(x, y, [44, 44, 60]); c.set(x + 1, y, [30, 30, 42]); }
  for (let i = 0; i < 4; i++) { let x = Math.floor(rnd() * 16), y = 0; for (let s = 0; s < 16; s++) { c.set(x, y, [26, 24, 38]); y++; x += Math.round(rnd() * 2 - 1); } }
  for (let i = 0; i < 6; i++) c.set(Math.floor(rnd() * 16), Math.floor(rnd() * 16), [110, 110, 140]); // glossy highlights
  return c;
}
function pod() {
  const c = canvas(); c.fill([16, 16, 22], 6);
  for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) { const d = Math.hypot((x - 7.5) / 1.0, (y - 7.5) / 1.15); if (d < 6.2) c.set(x, y, mix([150, 150, 165], [240, 240, 250], 1 - d / 6.2)); }
  for (let i = 0; i < 5; i++) { let x = 7 + Math.round(rnd() * 2), y = 2; for (let s = 0; s < 12; s++) { c.set(x, y, [40, 38, 52]); y++; x += Math.round(rnd() * 2 - 1); } }
  return c;
}
for (const [n, f] of [['hive_biomass', biomass], ['hive_pod', pod]]) {
  tex(`block/${n}`, f());
  json(path.join(A, `models/block/${n}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${n}` } });
  json(path.join(A, `blockstates/${n}.json`), { variants: { '': { model: `projecthero:block/${n}` } } });
  json(path.join(A, `models/item/${n}.json`), { parent: `projecthero:block/${n}` });
}

// ---------------------------------------------------------------- artifact items
function caneTex() {
  const c = canvas();
  const ivory = [236, 228, 206], gold = [232, 186, 60];
  for (let i = 0; i < 11; i++) { c.set(2 + i, 13 - i, shade(ivory, 0.9 + (i % 2) * 0.1)); c.set(3 + i, 13 - i, shade(ivory, 0.7)); }
  c.rect(12, 1, 14, 3, gold); c.set(13, 2, [255, 240, 160]); c.set(11, 3, shade(gold, 0.7)); c.set(12, 4, shade(gold, 0.7));
  c.set(1, 14, [60, 50, 40]); c.set(2, 14, [60, 50, 40]);
  c.outline([30, 24, 18]);
  return c;
}
function cortexTex() {
  const c = canvas();
  c.map([
    '................',
    '.......mm.......',
    '......mrrm......',
    '......mrrm......',
    '.....mrRRrm.....',
    '.....mrRRrm.....',
    '....mrRWWRrm....',
    '....mrRWWRrm....',
    '...mrrRRRRrrm...',
    '...mrrrRRrrrm...',
    '..mmmrrrrrrmmm..',
    '..ssmmmrrmmmss..',
    '...sssmmmmsss...',
    '.....ssssss.....',
    '................',
    '................',
  ], { m: [70, 74, 84], r: [190, 20, 16], R: [255, 70, 50], W: [255, 210, 190], s: [110, 114, 126] });
  c.outline([24, 24, 30]);
  return c;
}
function motherBoxTex() {
  const c = canvas();
  const g = [120, 124, 132], l = [150, 230, 255];
  c.rect(3, 4, 12, 13, g, 10); c.rect(4, 3, 13, 4, shade(g, 1.2)); c.rect(13, 4, 13, 12, shade(g, 0.75));
  for (const [x0, y0, x1, y1] of [[4, 6, 11, 6], [6, 6, 6, 12], [9, 8, 12, 8], [4, 10, 8, 10], [10, 8, 10, 12]]) c.line(x0, y0, x1, y1, l);
  // the crack: a jagged missing corner
  for (const [x, y] of [[12, 12], [11, 13], [12, 13], [13, 12], [13, 11], [10, 13]]) c.set(x, y, [0, 0, 0], 0);
  c.line(9, 13, 13, 9, [40, 40, 46]);
  c.outline([24, 24, 30]);
  return c;
}
function hiveHeartTex() {
  const c = canvas();
  c.map([
    '................',
    '................',
    '...bbb....bbb...',
    '..bbhbb..bbbbb..',
    '.bbhbbbbbbbbbbb.',
    '.bhbbbvbbbbbbbb.',
    '.bbbbvbbbbvbbbb.',
    '.bbbbvbbbvbbbbb.',
    '..bbbbvbvbbbbb..',
    '...bbbbvbbbbb...',
    '....bbbvbbbb....',
    '.....bbbbbb.....',
    '......bbbb......',
    '.......bb.......',
    '................',
    '................',
  ], { b: [18, 18, 26], h: [120, 120, 150], v: [230, 230, 240] });
  c.outline([4, 4, 8]);
  return c;
}
function frostbrandTex() {
  const c = canvas();
  const blade = [170, 225, 255], edge = [235, 250, 255], hilt = [70, 60, 90], guard = [120, 190, 230];
  for (let i = 0; i < 9; i++) { c.set(5 + i, 10 - i, blade); c.set(6 + i, 10 - i, edge); c.set(5 + i, 9 - i, shade(blade, 0.8)); }
  c.set(14, 1, edge);
  for (const [x, y] of [[3, 10], [4, 11], [5, 12], [6, 13], [4, 9], [6, 11]]) c.set(x, y, guard);
  c.set(3, 13, hilt); c.set(2, 14, hilt); c.set(4, 12, hilt); c.set(1, 15, guard);
  c.outline([20, 30, 50]);
  return c;
}
for (const [n, f, handheld] of [['gilded_cane', caneTex, true], ['ultron_cortex_shard', cortexTex, false], ['mother_box_fragment', motherBoxTex, false],
  ['hive_heart', hiveHeartTex, false], ['frostbrand', frostbrandTex, true]]) {
  tex(`item/${n}`, f());
  json(path.join(A, `models/item/${n}.json`), { parent: handheld ? 'minecraft:item/handheld' : 'minecraft:item/generated', textures: { layer0: `projecthero:item/${n}` } });
}

// ---------------------------------------------------------------- mini-boss skins (recolours of shipped skins)
const T = path.join(A, 'textures/entity');
function recolour(src, dst, fn) {
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
// Kingpin's Enforcer: everything but skin becomes a black suit; shirt + tie + gold chain on the body front; dark glasses
{
  const c = recolour(path.join(T, 'syndicate/thug_a.png'), null, (x, y, r, g, b) => {
    const headBase = y < 16 && x < 32;
    if (headBase) return null; // keep the face
    if (isSkinTone(r, g, b)) return null; // hands
    const l = lum(r, g, b);
    const v = 16 + l * 0.18;
    return [v, v, v + 4];
  });
  // white shirt strip + red tie down the body front (x 20..27, y 20..31)
  for (let y = 20; y < 32; y++) { c.set(23, y, [232, 232, 236]); c.set(24, y, [232, 232, 236]); }
  for (let y = 21; y < 30; y++) { c.set(23 + (y % 2), y, [150, 18, 22]); }
  c.set(23, 20, [200, 30, 30]); c.set(24, 20, [200, 30, 30]);
  // gold chain over the collar
  for (let x = 20; x <= 27; x++) c.set(x, 20 + (x === 20 || x === 27 ? 0 : x <= 21 || x >= 26 ? 1 : 2), [230, 186, 60]);
  // gold rings on the hands
  c.set(45, 31, [230, 186, 60]); c.set(37, 63, [230, 186, 60]);
  // dark glasses across the eyes (head front y 11..12)
  for (let x = 9; x <= 14; x++) c.set(x, 11, [12, 12, 14]);
  for (const x of [9, 10, 13, 14]) c.set(x, 12, [20, 20, 26]);
  c.set(11, 12, [12, 12, 14]); c.set(12, 12, [12, 12, 14]);
  write(path.join(T, 'syndicate/hideout_enforcer.png'), c.png());
}
// Forge Overseer: the Heavy's plating to graphite with gold trims; the red stays red (eyes texture copied)
{
  const c = recolour(path.join(T, 'ultron/heavy.png'), null, (x, y, r, g, b) => {
    if (r > 150 && r > g * 1.8 && r > b * 1.8) return null; // red eyes / lights
    const l = lum(r, g, b);
    if (l > 165) return mix([150, 112, 30], [255, 214, 90], (l - 165) / 90);
    return [18 + l * 0.32, 18 + l * 0.32, 22 + l * 0.36];
  });
  write(path.join(T, 'ultron/overseer.png'), c.png());
  fs.copyFileSync(path.join(T, 'ultron/heavy_eyes.png'), path.join(T, 'ultron/overseer_eyes.png'));
}
// Parademon Commander: the Elite's gold armour turned crimson-and-bronze
{
  const c = recolour(path.join(T, 'parademon_elite.png'), null, (x, y, r, g, b) => {
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    if (max - min > 40 && r > b && g > b) { // gold-ish -> crimson
      const l = lum(r, g, b);
      return mix([90, 8, 10], [230, 60, 40], Math.min(1, l / 200));
    }
    return null;
  });
  write(path.join(T, 'parademon_commander.png'), c.png());
  fs.copyFileSync(path.join(T, 'parademon_elite_glowmask.png'), path.join(T, 'parademon_commander_glowmask.png'));
}
// Hive Mother: Carnage's red goo turned glossy black; a white spider across the chest
{
  const c = recolour(path.join(T, 'carnage/carnage.png'), null, (x, y, r, g, b) => {
    const l = lum(r, g, b);
    if (r > g + 40 && r > b + 40) { const v = 10 + (r - 60) * 0.18; return [v, v, v + 10]; } // red -> black sheen
    if (l < 60) { const v = 26 + l * 0.6; return [v, v, v + 14]; } // the black -> dark slate so the folds read
    return null; // whites (eyes, teeth) stay
  });
  const W = [236, 236, 244];
  // a white spider emblem on the body front (x 20..27, y 20..31)
  for (let y = 21; y <= 26; y++) { c.set(23, y, W); c.set(24, y, W); }
  for (const [dx, dy] of [[-3, -1], [-2, 0], [-3, 2], [-2, 3], [-1, 4]]) { c.set(22 + dx + 1, 22 + dy, W); c.set(25 - dx - 1, 22 + dy, W); }
  c.set(21, 20, W); c.set(26, 20, W); c.set(20, 27, W); c.set(27, 27, W);
  write(path.join(T, 'hideout/hive_mother.png'), c.png());
}

// ---------------------------------------------------------------- loot tables
const lootDir = path.join(D, 'loot_table');
function bossLoot(extra) {
  return {
    type: 'minecraft:entity',
    pools: [
      { rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:vibranium_shard', functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: 3, max: 5 } }] }] },
      { rolls: 1, conditions: [{ condition: 'minecraft:random_chance', chance: 0.25 }],
        entries: [{ type: 'minecraft:item', name: 'projecthero:locator', functions: [{ function: 'minecraft:set_components',
          components: { 'projecthero:locator_target': { tier: 4 }, 'projecthero:hero_rarity': 'epic' } }] }] },
      { rolls: 1, entries: [{ type: 'minecraft:item', name: 'minecraft:experience_bottle', functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: 4, max: 8 } }] }] },
      ...extra,
    ],
  };
}
const shardLoot = (item, min, max) => ({ rolls: 1, entries: [{ type: 'minecraft:item', name: item, functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } }] }] });
json(path.join(lootDir, 'entities/kingpins_enforcer.json'), bossLoot([shardLoot('minecraft:emerald', 6, 12), shardLoot('minecraft:gold_ingot', 4, 8)]));
json(path.join(lootDir, 'entities/forge_overseer.json'), bossLoot([shardLoot('minecraft:redstone', 12, 24), shardLoot('minecraft:netherite_scrap', 0, 1)]));
json(path.join(lootDir, 'entities/parademon_commander.json'), bossLoot([shardLoot('minecraft:blaze_powder', 4, 8), shardLoot('minecraft:lapis_lazuli', 8, 16)]));
json(path.join(lootDir, 'entities/hive_mother.json'), bossLoot([shardLoot('minecraft:ink_sac', 6, 12), shardLoot('minecraft:ender_pearl', 2, 4)]));
json(path.join(lootDir, 'entities/frost_giant_jarl.json'), bossLoot([shardLoot('minecraft:blue_ice', 4, 8), shardLoot('minecraft:diamond', 2, 4)]));

// the tier 3 vault's artifact hook: one pool per faction; each only passes in a cleared hideout of that faction
const ART = { kingpin: ['gilded_cane', 0.2], ultron: ['ultron_cortex_shard', 0.2], apokolips: ['mother_box_fragment', 0.15],
  symbiote: ['hive_heart', 0.15], frost: ['frostbrand', 0.2] };
json(path.join(lootDir, 'chests/vault_tier3_artifacts.json'), {
  type: 'minecraft:chest',
  pools: Object.entries(ART).map(([f, [item, chance]]) => ({
    rolls: 1,
    conditions: [{ condition: 'projecthero:near_hideout', faction: f, radius: 32 }, { condition: 'minecraft:random_chance', chance }],
    entries: [{ type: 'minecraft:item', name: `projecthero:${item}` }],
  })),
});

// ---------------------------------------------------------------- structures
const STRUCT = {
  kingpins_front: { faction: 'kingpin', biomes: ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow', 'minecraft:savanna'],
    adapt: 'beard_thin', salt: 1705101, exclusion: { other_set: 'minecraft:villages', chunk_count: 3 } },
  ultron_forge: { faction: 'ultron', biomes: ['minecraft:stony_peaks', 'minecraft:jagged_peaks', 'minecraft:windswept_hills', 'minecraft:windswept_gravelly_hills',
    'minecraft:windswept_forest', 'minecraft:stony_shore'], adapt: 'none', salt: 1705102 },
  apokolips_outpost: { faction: 'apokolips', biomes: ['minecraft:badlands', 'minecraft:eroded_badlands', 'minecraft:wooded_badlands', 'minecraft:desert',
    'minecraft:savanna_plateau'], adapt: 'beard_thin', salt: 1705103 },
  symbiote_hive: { faction: 'symbiote', biomes: ['minecraft:forest', 'minecraft:birch_forest', 'minecraft:dark_forest', 'minecraft:old_growth_birch_forest',
    'minecraft:taiga', 'minecraft:old_growth_pine_taiga', 'minecraft:old_growth_spruce_taiga', 'minecraft:swamp'], adapt: 'none', salt: 1705104 },
  frost_giant_hall: { faction: 'frost', biomes: ['minecraft:snowy_plains', 'minecraft:snowy_taiga', 'minecraft:ice_spikes', 'minecraft:grove', 'minecraft:snowy_slopes'],
    adapt: 'beard_thin', salt: 1705105 },
};
for (const [id, s] of Object.entries(STRUCT)) {
  json(path.join(D, `tags/worldgen/biome/hideout/${id}.json`), { replace: false, values: s.biomes });
  json(path.join(D, `worldgen/structure/${id}.json`), {
    type: 'projecthero:villain_hideout', faction: s.faction, biomes: `#projecthero:hideout/${id}`, step: 'surface_structures',
    terrain_adaptation: s.adapt, spawn_overrides: {},
  });
  const placement = { type: 'minecraft:random_spread', salt: s.salt, spacing: 150, separation: 60, frequency: 0.85 };
  if (s.exclusion) placement.exclusion_zone = s.exclusion;
  json(path.join(D, `worldgen/structure_set/${id}.json`), { structures: [{ structure: `projecthero:${id}`, weight: 1 }], placement });
}
// Locator tier 3: merge (other branches may add)
{
  const f = path.join(D, 'tags/worldgen/structure/locator/tier3.json');
  const t = fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : { replace: false, values: [] };
  for (const id of Object.keys(STRUCT)) if (!t.values.includes(`projecthero:${id}`)) t.values.push(`projecthero:${id}`);
  json(f, t);
}
// mineable tags (merge)
function tagAdd(file, ids) {
  const t = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : { replace: false, values: [] };
  for (const id of ids) if (!t.values.includes(id)) t.values.push(id);
  json(file, t);
}
tagAdd(path.join(DM, 'tags/block/mineable/pickaxe.json'), ['projecthero:hideout_core', 'projecthero:hideout_alarm', 'projecthero:hideout_vent']);
tagAdd(path.join(DM, 'tags/block/mineable/hoe.json'), ['projecthero:hive_biomass', 'projecthero:hive_pod']);
console.log('hideout assets written');
