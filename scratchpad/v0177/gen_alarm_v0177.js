// v0.17.7 alarm & stealth framework assets: textures / models / blockstates for the Alarm Panel, Alarm Lamp, Blast Door,
// Security Camera, Laser Tripwire, spawn point and Keycard; the Ghost advancement; the pickaxe tag. Procedural PNGs (Node zlib).
// Run from the repo / worktree root:  node scratchpad/v0177/gen_alarm_v0177.js
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const root = process.argv[2] || process.cwd();
const A = path.join(root, "src/main/resources/assets/projecthero");
const D = path.join(root, "src/main/resources/data/projecthero");
const DM = path.join(root, "src/main/resources/data/minecraft");
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

let seed = 177;
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

// ==================================================================== v0.17.7 alarm blocks
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');
const tex = (rel, c) => write(path.join(A, 'textures', rel + '.png'), c.png());
const STEEL = [58, 62, 70];
const STONE = [112, 108, 100];

function frame(c, base) {
  c.fill(base, 10);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(base, 0.6)); c.set(i, 15, shade(base, 0.5)); c.set(0, i, shade(base, 0.6)); c.set(15, i, shade(base, 0.5)); }
  for (let i = 1; i < 15; i++) { c.set(i, 1, shade(base, 1.3)); c.set(1, i, shade(base, 1.2)); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(x, y, shade(base, 1.6));
}
function carved(c) {
  c.fill(STONE, 14);
  for (let i = 0; i < 16; i++) { c.set(i, 0, shade(STONE, 0.7)); c.set(i, 15, shade(STONE, 0.6)); c.set(0, i, shade(STONE, 0.7)); c.set(15, i, shade(STONE, 0.6)); }
  for (let x = 1; x < 15; x += 5) for (let y = 1; y < 15; y++) if (rnd() < 0.25) c.set(x, y, shade(STONE, 0.8));
}

// ---------------------------------------------------------------- Alarm Panel
function panelFront(style, mode) { // mode: idle / alert / dead
  const c = canvas();
  if (style === 'tech') {
    frame(c, STEEL);
    c.rect(3, 3, 12, 9, [12, 16, 18]);
    const scr = mode === 'alert' ? [230, 40, 30] : mode === 'dead' ? [24, 24, 26] : [40, 210, 110];
    if (mode !== 'dead') {
      for (let y = 4; y <= 8; y += 2) for (let x = 4; x <= 11; x++) if (rnd() < 0.7) c.set(x, y, shade(scr, 0.7 + rnd() * 0.5));
      if (mode === 'alert') { c.rect(6, 4, 9, 8, shade(scr, 0.6)); c.rect(7, 4, 8, 6, [255, 230, 220]); c.set(7, 8, [255, 230, 220]); c.set(8, 8, [255, 230, 220]); }
    } else {
      c.line(4, 4, 10, 9, [90, 90, 96]); c.line(11, 4, 7, 9, [70, 70, 76]);
    }
    // buttons and a big red switch
    for (let x = 3; x <= 9; x += 3) c.rect(x, 11, x + 1, 12, mode === 'dead' ? [50, 50, 54] : [200, 190, 60]);
    c.rect(11, 11, 12, 13, mode === 'dead' ? [60, 30, 30] : [200, 30, 30]);
  } else {
    carved(c);
    c.rect(3, 3, 12, 12, shade(STONE, 0.75));
    const rune = mode === 'alert' ? [255, 60, 40] : mode === 'dead' ? [60, 58, 54] : [80, 230, 255];
    for (const [x0, y0, x1, y1] of [[7, 4, 7, 11], [8, 4, 8, 11], [5, 6, 10, 6], [5, 9, 10, 9], [4, 4, 6, 6], [11, 4, 9, 6]]) c.line(x0, y0, x1, y1, rune);
    if (mode === 'dead') { c.line(3, 12, 12, 3, [40, 38, 34]); }
  }
  return c;
}
function panelSide(style) { const c = canvas(); if (style === 'tech') { frame(c, STEEL); for (let y = 4; y < 12; y += 2) c.rect(4, y, 11, y, shade(STEEL, 0.6)); } else carved(c); return c; }
function panelTop(style) { const c = canvas(); if (style === 'tech') frame(c, shade(STEEL, 0.9)); else carved(c); return c; }
const STYLES = ['tech', 'ancient'];
const panelVariants = {};
const FACE_Y = { north: 0, east: 90, south: 180, west: 270 };
for (const st of STYLES) {
  tex(`block/alarm_panel_${st}_side`, panelSide(st));
  tex(`block/alarm_panel_${st}_top`, panelTop(st));
  for (const mode of ['idle', 'alert', 'dead']) {
    tex(`block/alarm_panel_${st}_front_${mode}`, panelFront(st, mode));
    json(path.join(A, `models/block/alarm_panel_${st}_${mode}.json`), { parent: 'minecraft:block/orientable', textures: {
      top: `projecthero:block/alarm_panel_${st}_top`, front: `projecthero:block/alarm_panel_${st}_front_${mode}`, side: `projecthero:block/alarm_panel_${st}_side` } });
  }
  for (const [f, y] of Object.entries(FACE_Y)) for (const sab of [false, true]) for (const al of [false, true]) {
    const mode = sab ? 'dead' : al ? 'alert' : 'idle';
    const v = { model: `projecthero:block/alarm_panel_${st}_${mode}` };
    if (y) v.y = y;
    panelVariants[`alert=${al},facing=${f},sabotaged=${sab},style=${st}`] = v;
  }
}
json(path.join(A, 'blockstates/alarm_panel.json'), { variants: panelVariants });
json(path.join(A, 'models/item/alarm_panel.json'), { parent: 'projecthero:block/alarm_panel_tech_idle' });

// ---------------------------------------------------------------- Alarm Lamp
function lamp(style, lit) {
  const c = canvas();
  if (style === 'tech') {
    frame(c, [40, 40, 44]);
    const g = lit ? [255, 60, 40] : [90, 18, 14];
    for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) {
      const d = Math.hypot(x - 7.5, y - 7.5);
      if (d < 5) c.set(x, y, lit ? mix(g, [255, 220, 200], Math.max(0, 1 - d / 3)) : shade(g, 0.8 + rnd() * 0.3));
    }
    for (let x = 3; x < 13; x += 3) for (let y = 3; y < 13; y++) c.set(x, y, [30, 30, 34]);
  } else {
    carved(c);
    c.rect(2, 2, 13, 13, shade(STONE, 0.6));
    for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) {
      const coal = lit ? mix([255, 120, 20], [255, 240, 120], rnd() * 0.6) : shade([40, 36, 34], 0.8 + rnd() * 0.4);
      if (rnd() < (lit ? 0.85 : 0.7)) c.set(x, y, coal);
    }
  }
  return c;
}
const lampVariants = {};
for (const st of STYLES) for (const lit of [false, true]) {
  const n = `alarm_lamp_${st}${lit ? '_lit' : ''}`;
  tex(`block/${n}`, lamp(st, lit));
  json(path.join(A, `models/block/${n}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${n}` } });
  lampVariants[`lit=${lit},style=${st}`] = { model: `projecthero:block/${n}` };
}
json(path.join(A, 'blockstates/alarm_lamp.json'), { variants: lampVariants });
json(path.join(A, 'models/item/alarm_lamp.json'), { parent: 'projecthero:block/alarm_lamp_tech_lit' });

// ---------------------------------------------------------------- Blast Door
function door(style, locked) {
  const c = canvas();
  if (style === 'tech') {
    frame(c, [70, 74, 82]);
    for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) if (((x + y) >> 2) % 2 === 0 && (y < 4 || y > 11)) c.set(x, y, [220, 180, 30]);
    for (let y = 4; y <= 11; y++) for (let x = 2; x < 14; x++) if (((x + y) >> 2) % 2 === 0 && (y < 4 || y > 11)) c.set(x, y, [30, 30, 30]);
    c.rect(2, 4, 13, 11, [78, 82, 90], 6);
    for (let y = 2; y < 14; y++) if (((y) >> 1) % 2 === 0) { c.set(2, y, [30, 30, 30]); c.set(13, y, [30, 30, 30]); }
    c.rect(2, 7, 13, 8, shade([78, 82, 90], 0.7));
    c.rect(6, 9, 9, 10, locked ? [210, 30, 30] : [50, 200, 90]);
  } else {
    carved(c);
    c.rect(2, 2, 13, 13, shade(STONE, 0.85), 10);
    const rune = locked ? [255, 80, 50] : [90, 220, 255];
    for (const [x0, y0, x1, y1] of [[7, 3, 7, 12], [8, 3, 8, 12], [4, 5, 11, 10], [11, 5, 4, 10]]) c.line(x0, y0, x1, y1, rune);
  }
  return c;
}
const doorVariants = {};
json(path.join(A, 'models/block/blast_door_open.json'), { textures: { particle: 'projecthero:block/blast_door_tech' }, elements: [] });
for (const st of STYLES) for (const locked of [false, true]) {
  const n = `blast_door_${st}${locked ? '_locked' : ''}`;
  tex(`block/${n}`, door(st, locked));
  json(path.join(A, `models/block/${n}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${n}` } });
  for (const open of [false, true]) doorVariants[`locked=${locked},open=${open},style=${st}`] = { model: open ? 'projecthero:block/blast_door_open' : `projecthero:block/${n}` };
}
json(path.join(A, 'blockstates/blast_door.json'), { variants: doorVariants });
json(path.join(A, 'models/item/blast_door.json'), { parent: 'projecthero:block/blast_door_tech_locked' });

// ---------------------------------------------------------------- Security Camera (bracket model; the head is drawn by its renderer)
{
  const b = canvas(); frame(b, [90, 94, 100]); tex('block/security_camera', b);
  const h = canvas(); h.fill([150, 154, 160], 12); for (let i = 0; i < 16; i++) { h.set(i, 0, [80, 84, 90]); h.set(i, 15, [80, 84, 90]); h.set(0, i, [100, 104, 110]); h.set(15, i, [100, 104, 110]); }
  h.rect(4, 6, 11, 8, [60, 64, 70]); h.rect(12, 3, 13, 4, [200, 40, 30]);
  tex('block/security_camera_head', h);
  json(path.join(A, 'models/block/security_camera.json'), {
    textures: { particle: 'projecthero:block/security_camera', all: 'projecthero:block/security_camera' },
    elements: [
      { from: [5, 6, 15], to: [11, 12, 16], faces: { north: { texture: '#all' }, south: { texture: '#all' }, east: { texture: '#all' }, west: { texture: '#all' }, up: { texture: '#all' }, down: { texture: '#all' } } },
      { from: [7, 7, 8], to: [9, 9, 15], faces: { north: { texture: '#all' }, east: { texture: '#all' }, west: { texture: '#all' }, up: { texture: '#all' }, down: { texture: '#all' } } },
    ],
  });
  const camVariants = {};
  for (const [f, y] of Object.entries(FACE_Y)) for (const live of [false, true]) { const v = { model: 'projecthero:block/security_camera' }; if (y) v.y = y; camVariants[`facing=${f},live=${live}`] = v; }
  json(path.join(A, 'blockstates/security_camera.json'), { variants: camVariants });
  const icon = canvas();
  icon.rect(2, 4, 11, 9, [150, 154, 160]); icon.rect(11, 5, 13, 8, [70, 74, 80]); icon.set(12, 6, [220, 40, 30]); icon.rect(5, 10, 6, 13, [90, 94, 100]); icon.rect(3, 13, 8, 14, [90, 94, 100]);
  icon.outline([30, 30, 34]);
  tex('item/security_camera', icon);
  json(path.join(A, 'models/item/security_camera.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/security_camera' } });
}

// ---------------------------------------------------------------- Laser Tripwire (model faces up; piston rotations)
{
  const t = canvas(); frame(t, [44, 46, 52]); t.rect(5, 5, 10, 10, [230, 30, 25]); t.rect(7, 7, 8, 8, [255, 200, 190]); tex('block/laser_tripwire', t);
  const td = canvas(); frame(td, [44, 46, 52]); td.rect(5, 5, 10, 10, [60, 20, 20]); tex('block/laser_tripwire_dead', td);
  for (const live of [false, true]) {
    const n = live ? 'laser_tripwire' : 'laser_tripwire_dead';
    json(path.join(A, `models/block/${n}.json`), {
      textures: { particle: `projecthero:block/${n}`, lens: `projecthero:block/${n}`, side: 'projecthero:block/security_camera' },
      elements: [{ from: [5, 0, 5], to: [11, 5, 11], faces: {
        up: { texture: '#lens', uv: [3, 3, 13, 13] }, down: { texture: '#side' }, north: { texture: '#side', uv: [0, 0, 6, 5] },
        south: { texture: '#side', uv: [0, 0, 6, 5] }, east: { texture: '#side', uv: [0, 0, 6, 5] }, west: { texture: '#side', uv: [0, 0, 6, 5] } } }],
    });
  }
  const ROT = { down: { x: 180 }, up: {}, north: { x: 90 }, south: { x: 90, y: 180 }, west: { x: 90, y: 270 }, east: { x: 90, y: 90 } };
  const lv = {};
  for (const [f, r] of Object.entries(ROT)) for (const live of [false, true]) lv[`facing=${f},live=${live}`] = Object.assign({ model: `projecthero:block/${live ? 'laser_tripwire' : 'laser_tripwire_dead'}` }, r);
  json(path.join(A, 'blockstates/laser_tripwire.json'), { variants: lv });
  const icon = canvas();
  icon.rect(1, 6, 4, 10, [60, 62, 70]); icon.set(4, 8, [255, 60, 40]);
  for (let x = 5; x < 15; x++) icon.set(x, 8, x % 2 ? [255, 40, 30] : [255, 140, 120]);
  icon.rect(12, 6, 14, 10, [60, 62, 70]);
  tex('item/laser_tripwire', icon);
  json(path.join(A, 'models/item/laser_tripwire.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/laser_tripwire' } });
}

// ---------------------------------------------------------------- spawn point (invisible) and the Keycard
{
  json(path.join(A, 'models/block/alarm_spawn_point.json'), { textures: { particle: 'projecthero:item/alarm_spawn_point' }, elements: [] });
  json(path.join(A, 'blockstates/alarm_spawn_point.json'), { variants: { '': { model: 'projecthero:block/alarm_spawn_point' } } });
  const icon = canvas();
  for (let i = 1; i < 15; i++) if (i % 3 !== 0) { icon.set(i, 1, [255, 80, 60]); icon.set(i, 14, [255, 80, 60]); icon.set(1, i, [255, 80, 60]); icon.set(14, i, [255, 80, 60]); }
  icon.rect(6, 4, 9, 6, [230, 230, 235]); icon.rect(5, 7, 10, 11, [230, 230, 235]); icon.rect(7, 12, 8, 12, [230, 230, 235]);
  tex('item/alarm_spawn_point', icon);
  json(path.join(A, 'models/item/alarm_spawn_point.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/alarm_spawn_point' } });

  const k = canvas();
  k.rect(1, 4, 14, 12, [220, 222, 228]);
  k.rect(1, 4, 14, 5, [200, 40, 40]);
  k.rect(3, 7, 6, 10, [230, 180, 50]); k.line(3, 8, 6, 8, [170, 120, 30]); k.line(4, 7, 4, 10, [170, 120, 30]);
  for (let x = 8; x <= 13; x++) { k.set(x, 7, [120, 124, 132]); if (x < 12) k.set(x, 9, [150, 154, 162]); }
  k.rect(1, 11, 14, 12, [40, 40, 46]);
  k.outline([30, 30, 34]);
  tex('item/keycard', k);
  json(path.join(A, 'models/item/keycard.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/keycard' } });
}

// ---------------------------------------------------------------- the Ghost advancement and the pickaxe tag
json(path.join(D, 'advancement/ghost.json'), {
  display: { icon: { id: 'minecraft:ender_eye' }, title: { translate: 'advancement.projecthero.ghost.title' },
    description: { translate: 'advancement.projecthero.ghost.description' }, frame: 'challenge', show_toast: true, announce_to_chat: true, hidden: false },
  criteria: { code_trigger: { trigger: 'minecraft:impossible' } }, requirements: [['code_trigger']], parent: 'minecraft:adventure/root',
});
{
  const f = path.join(DM, 'tags/block/mineable/pickaxe.json');
  const t = JSON.parse(fs.readFileSync(f, 'utf8'));
  for (const id of ['projecthero:alarm_lamp', 'projecthero:security_camera', 'projecthero:laser_tripwire']) if (!t.values.includes(id)) t.values.push(id);
  const eol = fs.readFileSync(f, 'utf8').includes('\r\n') ? '\r\n' : '\n';
  write(f, JSON.stringify(t, null, 2).replace(/\n/g, eol) + eol);
}
console.log('alarm assets written');
