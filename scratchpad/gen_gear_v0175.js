// v0.17.5 gear tiers I-IV: every resource -- procedural item icons (16x16, the warhammer 32x32), 64x32 armour layers for
// Hero Alloy / Vibranium / Celestial (+ Celestial glow layers), the grapple hook + cable entity textures, item models,
// recipes (crafting + smithing), the four vault "gear" loot sub-tables and the vanilla equipment tags (merged, never
// overwritten). Node + zlib only.
// Run from the repo (or worktree) root:   node scratchpad/gen_gear_v0175.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const A = path.join(root, 'src/main/resources/assets/projecthero');
const D = path.join(root, 'src/main/resources/data/projecthero');
const MC = path.join(root, 'src/main/resources/data/minecraft');

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
let seed = 1775;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => c.map(v => clamp(v * f));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
function canvas(w = 16, h = 16) {
  const px = Buffer.alloc(w * h * 4);
  const c = {
    w, h, px,
    set(x, y, col, a = 255) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= w || y >= h || !col) return; const i = (y * w + x) * 4; px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = a; },
    get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
    opaque(x, y) { return x >= 0 && y >= 0 && x < w && y < h && px[(y * w + x) * 4 + 3] > 0; },
    map(rows, pal, ox = 0, oy = 0) { rows.forEach((r, y) => [...r].forEach((ch, x) => { const p = pal[ch]; if (p) c.set(ox + x, oy + y, p, p[3] === undefined ? 255 : p[3]); })); },
    outline(col) {
      const add = [];
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (!c.opaque(x, y) && (c.opaque(x - 1, y) || c.opaque(x + 1, y) || c.opaque(x, y - 1) || c.opaque(x, y + 1))) add.push([x, y]);
      add.forEach(([x, y]) => c.set(x, y, col));
    },
    png() { return png(w, h, px); },
  };
  return c;
}
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');
const tex = (rel, c) => write(path.join(A, 'textures', rel + '.png'), c.png());

// ------------------------------------------------------------------------------------------------ palettes per tier
const P = {
  // Tier I: scrappy salvage -- gunmetal, rust, copper, bolts
  salvaged: { o: [22, 20, 20], B: [96, 98, 100], L: [150, 152, 150], D: [58, 58, 60], H: [205, 205, 196], A: [176, 100, 48], a: [118, 62, 30], R: [150, 76, 36], C: [196, 120, 70], Y: [236, 206, 72], y: [255, 246, 170], W: [215, 215, 215, 200], w: [180, 180, 180, 150], G: [120, 118, 96] },
  // Tier II: sleek dark steel with a blue accent
  alloy: { o: [14, 16, 22], B: [62, 68, 80], L: [112, 122, 138], D: [36, 40, 50], H: [176, 188, 204], A: [58, 160, 255], a: [28, 92, 176], G: [150, 214, 255], R: [62, 68, 80], C: [100, 70, 44], P: [70, 58, 46], Y: [58, 160, 255], y: [150, 214, 255], W: [215, 215, 215, 200], w: [180, 180, 180, 150] },
  // Tier III: near-black purple with silver
  vibranium: { o: [8, 4, 14], B: [38, 26, 52], L: [76, 54, 104], D: [20, 13, 30], H: [232, 234, 244], A: [200, 205, 220], a: [124, 128, 146], S: [104, 58, 156], G: [196, 150, 255], R: [104, 58, 156], C: [150, 110, 210], Y: [196, 150, 255], y: [236, 220, 255], W: [190, 170, 220, 200], w: [150, 120, 190, 150] },
  // Tier IV: pearl white + gold with a cosmic glow
  celestial: { o: [40, 30, 60], B: [222, 218, 234], L: [252, 252, 255], D: [160, 152, 184], H: [255, 255, 255], A: [242, 192, 58], a: [176, 120, 26], C: [30, 20, 70], '*': [150, 230, 255], G: [255, 230, 140], S: [190, 160, 255] },
};

// ------------------------------------------------------------------------------------------------ item icons
const ICON = {
  helmet: [
    '................',
    '................',
    '................',
    '.....oooooo.....',
    '....oLLHHLLo....',
    '...oLBBHHBBDo...',
    '...oBBBBBBBDo...',
    '...oAAAAAAAAo...',
    '...oaGGGGGGao...',
    '...oBBDooDBDo...',
    '...oBBo..oBDo...',
    '...oDDo..oDDo...',
    '....oo....oo....',
    '................',
    '................',
    '................'],
  chestplate: [
    '................',
    '..ooo......ooo..',
    '.oLLBo....oBDDo.',
    '.oLBBBooooBBBDo.',
    '.oBBBLLHHBBBBDo.',
    '.oAoBBAAAABBoAo.',
    '..o.oBAGGABo.o..',
    '....oBBAABBDo...',
    '....oLBBBBBDo...',
    '....oBBBBBBDo...',
    '....oBDBBDBDo...',
    '....oaaaaaaao...',
    '....oDBBBBDDo...',
    '.....oooooooo...',
    '................',
    '................'],
  leggings: [
    '................',
    '................',
    '...oooooooooo...',
    '...oaaaAAaaao...',
    '...oBBBBBBBDo...',
    '...oLBBooBBDo...',
    '...oBAo..oADo...',
    '...oBAo..oADo...',
    '...oLLo..oLDo...',
    '...oBAo..oADo...',
    '...oBBo..oBDo...',
    '...oBBo..oBDo...',
    '...oDDo..oDDo...',
    '....oo....oo....',
    '................',
    '................'],
  boots: [
    '................',
    '................',
    '................',
    '................',
    '................',
    '...oooo..oooo...',
    '...oAAo..oAAo...',
    '...oBBo..oBDo...',
    '...oLBo..oBDo...',
    '..oLBBo..oBBDo..',
    '..oBBBo..oBBDo..',
    '..oaaao..oaaao..',
    '..oDDDo..oDDDo..',
    '...ooo....ooo...',
    '................',
    '................'],
  grapple: [
    '................',
    '..........o..o..',
    '.........oHooHo.',
    '..........oHHo..',
    '....oooooooHHo..',
    '...oLLLLLLBoHo..',
    '...oBBRBBBBo.o..',
    '...oDBBBCBDo....',
    '...ooooBBBDo....',
    '...oWo.oBDo.....',
    '..oWwo.oBDDo....',
    '..oWwo.oDDo.....',
    '..oWo..oDDo.....',
    '...o...oooo.....',
    '................',
    '................'],
  smoke: [
    '................',
    '..ww............',
    '.wWWw..oo.......',
    '..wWWwoHHo......',
    '....wooooooo....',
    '....oLLBBBBDo...',
    '...oLBBBBBBDDo..',
    '...oLBBBBBBBDo..',
    '...oWWWWWWWWWo..',
    '...owGwwGwwGwo..',
    '...oLBBBBBBBDo..',
    '...oBBBBBBBDDo..',
    '....oDDDDDDDo...',
    '.....ooooooo....',
    '................',
    '................'],
  flash: [
    '................',
    '.......ooo......',
    '......oHHHo.....',
    '.....ooooooo....',
    '.....oLBBBDoHo..',
    '.....oLBBBDoHo..',
    '.....oYYYYYooHo.',
    '.....oYyyYYo.o..',
    '.....oLBBBDo....',
    '.....oLBBBDo....',
    '.....oYYyYYo....',
    '.....oLBBBDo....',
    '.....oDDDDDo....',
    '......ooooo.....',
    '................',
    '................'],
  belt: [
    '................',
    '................',
    '................',
    '................',
    'oooooooooooooooo',
    'oLLLLLLooLLLLLLo',
    'oCCCCCoAAoCCCCCo',
    'oPPPPPoaGoPPPPPo',
    'ooCCooooooooCCoo',
    '.oCCo.oCCo.oCCo.',
    '.oPPo.oPPo.oPPo.',
    '.oPPo.oaAo.oPPo.',
    '.oooo.oooo.oooo.',
    '................',
    '................',
    '................'],
  template: [
    '................',
    '...oooooooooo...',
    '..oLLLLLLLLLLo..',
    '..oLCCCCCCCCDo..',
    '..oLCC*CC*CCDo..',
    '..oLCCCAACCCDo..',
    '..oLCCAGGACCDo..',
    '..oLCAGGGGACDo..',
    '..oLCCAGGACCDo..',
    '..oLCCCAACCCDo..',
    '..oLC*CCCC*CDo..',
    '..oLCCCCCCCCDo..',
    '..oDDDDDDDDDDo..',
    '...oooooooooo...',
    '................',
    '................'],
};
const icon = (map, pal) => { const c = canvas(); c.map(map, pal); return c; };

// a sword along the 16x16 diagonal: blade (edge / face / spine), guard, grip, pommel
function sword(pal, { len = 9, guard = 'A', grip = 'P', pom = 'A', edge = 'H', face = 'L', spine = 'B', slim = false } = {}) {
  const c = canvas();
  const col = k => pal[k];
  for (let t = 0; t <= len; t++) {
    const x = 13 - t, y = 2 + t;
    c.set(x, y, col(edge));
    if (!slim || t % 3) c.set(x + 1, y, col(face));
    c.set(x, y + 1, col(spine));
  }
  c.set(14, 1, col(edge));
  const gx = 13 - len - 1, gy = 2 + len + 1; // guard centre
  for (let k = -2; k <= 2; k++) c.set(gx + k, gy + k, k === 0 ? pal.G || col(guard) : col(guard));
  c.set(gx - 1, gy + 1, col(guard));
  for (let k = 1; k <= 3; k++) c.set(gx - k, gy + k, k % 2 ? col(grip) : shade(col(grip), 0.7));
  c.set(gx - 4, gy + 4, col(pom));
  c.outline(pal.o);
  return c;
}

// the Alloy Baton: a straight escrima stick with blue rings and a grip wrap
function baton(pal) {
  const c = canvas();
  for (let t = 0; t <= 10; t++) {
    const x = 12 - t, y = 3 + t;
    const ring = t === 1 || t === 6;
    const grip = t >= 8;
    c.set(x, y, ring ? pal.A : grip ? pal.P : pal.L);
    c.set(x + 1, y, ring ? pal.a : grip ? shade(pal.P, 0.7) : pal.B);
    c.set(x, y + 1, ring ? pal.a : grip ? shade(pal.P, 0.6) : pal.D);
  }
  c.set(13, 2, pal.H); c.set(13, 3, pal.A); c.set(12, 2, pal.A);
  c.set(1, 14, pal.a); c.set(2, 14, pal.A);
  c.outline(pal.o);
  return c;
}

// the Celestial Warhammer, 32x32: a rotated pearl head with gold caps and a cosmic core, gold-wrapped haft
function warhammer() {
  const pal = P.celestial;
  const c = canvas(32, 32);
  const ox = 3.5, oy = 28.5; // butt of the haft
  const ux = Math.SQRT1_2, uy = -Math.SQRT1_2; // along the haft
  const vx = Math.SQRT1_2, vy = Math.SQRT1_2; // across
  const uh = 26; // head centre along the haft
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const dx = x + 0.5 - ox, dy = y + 0.5 - oy;
    const u = dx * ux + dy * uy, v = dx * vx + dy * vy;
    const hu = u - uh;
    if (Math.abs(hu) <= 4.6 && Math.abs(v) <= 8.6) { // head
      const cap = Math.abs(v) >= 6.4;
      const band = Math.abs(hu) >= 3.6;
      const core = Math.hypot(hu, v) <= 2.6;
      let col = cap ? (Math.abs(v) >= 7.8 ? pal.a : pal.A) : band ? pal.A : pal.B;
      if (!cap && !band) col = mix(pal.B, pal.L, Math.max(0, -hu / 4)); // light from the top-left
      if (core) col = Math.hypot(hu, v) <= 1.3 ? pal['*'] : pal.C;
      if (!cap && !band && !core && (x * 7 + y * 3) % 11 === 0) col = pal.D;
      c.set(x, y, col);
    } else if (Math.abs(v) <= 1.25 && u >= 0 && u < uh - 4) { // haft
      const wrap = Math.floor(u) % 4 === 0;
      let col = v < 0 ? pal.A : pal.a;
      if (wrap) col = pal.o;
      if (u < 3.2) col = v < 0 ? pal.L : pal.D; // pommel
      c.set(x, y, col);
    }
  }
  c.outline(pal.o);
  return c;
}

// gadgets per palette (Tier I salvaged, Tier III vibranium)
const gadgetPal = p => Object.assign({}, p);
const vibGadget = Object.assign({}, P.vibranium, { W: [190, 170, 220, 210], w: [150, 120, 190, 160] });

const ICONS = {
  grapple_line: icon(ICON.grapple, P.salvaged),
  smoke_bomb: icon(ICON.smoke, P.salvaged),
  flashbang: icon(ICON.flash, P.salvaged),
  vibranium_grapple: icon(ICON.grapple, vibGadget),
  vibranium_smoke_bomb: icon(ICON.smoke, vibGadget),
  vibranium_flashbang: icon(ICON.flash, vibGadget),
  hero_alloy_helmet: icon(ICON.helmet, P.alloy),
  hero_alloy_chestplate: icon(ICON.chestplate, P.alloy),
  hero_alloy_leggings: icon(ICON.leggings, P.alloy),
  hero_alloy_boots: icon(ICON.boots, P.alloy),
  hero_alloy_sword: sword(P.alloy, { guard: 'A', grip: 'D', pom: 'A' }),
  alloy_baton: baton(Object.assign({}, P.alloy, { P: [40, 34, 30] })),
  utility_belt: icon(ICON.belt, Object.assign({}, P.alloy, { C: [92, 66, 42], P: [66, 46, 30], L: [124, 92, 60] })),
  vibranium_helmet: icon(ICON.helmet, Object.assign({}, P.vibranium, { G: [236, 238, 248] })),
  vibranium_chestplate: icon(ICON.chestplate, P.vibranium),
  vibranium_leggings: icon(ICON.leggings, P.vibranium),
  vibranium_boots: icon(ICON.boots, P.vibranium),
  vibranium_blade: sword(Object.assign({}, P.vibranium, { P: [30, 20, 40] }), { len: 10, edge: 'H', face: 'S', spine: 'B', guard: 'A', grip: 'P', pom: 'A' }),
  celestial_helmet: icon(ICON.helmet, Object.assign({}, P.celestial, { G: [120, 210, 255] })),
  celestial_chestplate: icon(ICON.chestplate, Object.assign({}, P.celestial, { G: [120, 210, 255] })),
  celestial_leggings: icon(ICON.leggings, P.celestial),
  celestial_boots: icon(ICON.boots, P.celestial),
  celestial_upgrade_template: icon(ICON.template, P.celestial),
  celestial_warhammer: warhammer(),
};
for (const [id, c] of Object.entries(ICONS)) tex('item/' + id, c);
void gadgetPal;

// ------------------------------------------------------------------------------------------------ armour layers 64x32
// face rects of a box at (u, v) with size w x h x d, in the vanilla cube UV layout
function faces(u, v, w, h, d) {
  return {
    top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
    right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h],
  };
}
const HEAD = faces(0, 0, 8, 8, 8), BODY = faces(16, 16, 8, 12, 4), ARM = faces(40, 16, 4, 12, 4), LEG = faces(0, 16, 4, 12, 4);

// the surface of each set: base colour + per-set texture, bevelled face edges
function surface(set, pal, x, y, w, h, gx, gy) {
  let col = pal.B;
  const n = (rnd() - 0.5);
  if (set === 'alloy') { // brushed steel: horizontal streaks
    col = shade(col, 1 + n * 0.08 + ((gy * 7 + Math.floor(gx / 3)) % 5 === 0 ? 0.08 : 0));
  } else if (set === 'vibranium') { // woven hex sheen
    const hx = (gx + (gy % 2) * 2) % 4, on = hx === 0 && gy % 2 === 0;
    col = on ? mix(pal.B, pal.S, 0.3) : shade(col, 1 + n * 0.1);
  } else { // pearl: soft vertical gradient
    col = mix(pal.L, pal.B, Math.min(1, y / Math.max(1, h - 1)) * 0.8 + n * 0.1);
  }
  if (y === 0 || x === 0) col = mix(col, pal.L, 0.45);
  if (y === h - 1 || x === w - 1) col = mix(col, pal.D, 0.6);
  return col;
}

// hand-drawn front motifs (chars from the set palette; '.' = keep the surface)
const MOTIF = {
  alloy: {
    helm: ['DLLHHLLD', 'LBBHHBBL', '...DD...', 'DGGGGGGD', 'DaAAAAaD', '...DD...', '.D.DD.D.', 'DD....DD'],
    chest: ['D......D', '..HHHH..', '.AHLLHA.', '.AAGGAA.', '..AGGA..', '...AA...', 'D......D', '.D....D.', '..D..D..', '...DD...', 'aaaaaaaa', 'D..AA..D'],
    belt: ['DDDDDDDD', 'aaaAAaaa', '...GG...', 'D......D', '.D....D.'],
  },
  vibranium: {
    helm: ['DSSDDSSD', 'S..LL..S', '...LL...', '.AA..AA.', '..AHHA..', 'D..LL..D', 'D.L..L.D', 'DD....DD'],
    chest: ['D......D', 'AA....AA', 'HAA..AAH', '.AAAAAA.', '..AHHA..', '.D.LL.D.', '..DLLD..', '.D.LL.D.', '.D....D.', '..D..D..', 'DAAAAAAD', 'D......D'],
    belt: ['DDDDDDDD', 'DAAAAAAD', '...HH...', 'D......D', '.S....S.'],
  },
  celestial: {
    helm: ['DAAAAAAD', '.LLAALL.', '.L.AA.L.', 'ACCCCCCA', 'AC*CC*CA', '.ACCCCA.', '..ACCA..', 'D..AA..D'],
    chest: ['AA....AA', '.A....A.', '..ACCA..', '.ACC*CA.', '.AC*CCA.', '..ACCA..', '...AA...', '........', 'D......D', '.D....D.', 'AAAAAAAA', 'D..GG..D'],
    belt: ['aaaaaaaa', 'AAAGGAAA', '...AA...', 'D......D', '.D....D.'],
  },
};

function paintFace(c, glow, set, pal, rect, fn) {
  const [u, v, w, h] = rect;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const r = fn(x, y, w, h);
    if (r === null) continue;
    let col = r || surface(set, pal, x, y, w, h, u + x, v + y);
    c.set(u + x, v + y, col);
    if (glow && r && (r === pal.A || r === pal['*'] || r === pal.G || r === pal.C)) glow.set(u + x, v + y, r === pal.C ? [52, 34, 120] : r);
  }
}
const motifAt = (rows, pal) => (x, y) => { const ch = rows[y] && rows[y][x]; return ch && ch !== '.' ? pal[ch] : undefined; };

function armorLayers(set) {
  const pal = P[set];
  const glow1 = set === 'celestial' ? canvas(64, 32) : null, glow2 = set === 'celestial' ? canvas(64, 32) : null;
  const m = MOTIF[set];
  const stripe = set === 'alloy' ? pal.A : set === 'vibranium' ? pal.A : pal.A;
  const l1 = canvas(64, 32), l2 = canvas(64, 32);

  // ---- layer 1: helmet
  paintFace(l1, glow1, set, pal, HEAD.front, motifAt(m.helm, pal));
  for (const side of ['right', 'left']) paintFace(l1, glow1, set, pal, HEAD[side], (x, y, w) => {
    const toFront = side === 'right' ? x === w - 1 : x === 0; // the visor wraps round the front edge
    if ((y === 3 || y === 4) && (toFront || x === (side === 'right' ? w - 2 : 1))) return y === 3 ? (pal.G || pal.A) : pal.A;
    const d = Math.hypot(x - 3.5, y - 4.5);
    if (d > 1.6 && d <= 2.5) return pal.A; // ear disc
    if (d <= 1.6) return set === 'celestial' ? pal.C : pal.D;
    return undefined;
  });
  paintFace(l1, glow1, set, pal, HEAD.back, (x, y) => (x === 3 || x === 4) ? (y < 6 ? stripe : pal.D) : (y === 7 ? pal.D : undefined));
  paintFace(l1, glow1, set, pal, HEAD.top, (x) => (x === 3 || x === 4) ? stripe : undefined);
  paintFace(l1, glow1, set, pal, HEAD.bottom, () => pal.D);

  // ---- layer 1: chestplate (body + arm; the left arm mirrors the right)
  paintFace(l1, glow1, set, pal, BODY.front, motifAt(m.chest, pal));
  paintFace(l1, glow1, set, pal, BODY.back, (x, y) => {
    if (x === 3 || x === 4) return y < 10 ? (y % 3 === 0 ? pal.D : stripe) : pal.D;
    if (y === 10) return set === 'alloy' ? pal.a : pal.A;
    if ((x === 1 || x === 6) && y < 4) return pal.L;
    return undefined;
  });
  for (const side of ['right', 'left']) paintFace(l1, glow1, set, pal, BODY[side], (x, y) => y === 10 ? (set === 'alloy' ? pal.a : pal.A) : undefined);
  paintFace(l1, glow1, set, pal, BODY.top, (x) => x === 0 || x === 7 ? pal.L : undefined);
  paintFace(l1, glow1, set, pal, BODY.bottom, () => pal.D);
  for (const f of ['front', 'back', 'right', 'left']) paintFace(l1, glow1, set, pal, ARM[f], (x, y) => {
    if (y <= 2) return y === 2 ? stripe : (y === 0 ? pal.L : undefined); // shoulder pad
    if (y === 3) return pal.D;
    if (y === 9 || y === 10) return y === 9 ? stripe : (set === 'celestial' ? pal.a : pal.a); // gauntlet band
    if (y === 11) return pal.D;
    if (f === 'right' && (x === 1 || x === 2) && y > 3 && y < 9) return set === 'celestial' ? pal.C : pal.D; // outer panel
    return undefined;
  });
  paintFace(l1, glow1, set, pal, ARM.top, (x, y) => (x + y) % 3 === 0 ? pal.L : undefined);
  paintFace(l1, glow1, set, pal, ARM.bottom, () => pal.D);

  // ---- layer 1: boots (the lower five rows of the leg box)
  for (const f of ['front', 'back', 'right', 'left']) paintFace(l1, glow1, set, pal, LEG[f], (x, y) => {
    if (y < 7) return null;
    if (y === 7) return stripe;
    if (y === 8) return f === 'front' && (x === 1 || x === 2) ? (pal.G || pal.A) : undefined;
    if (y === 11) return pal.D;
    if (y === 10) return f === 'front' ? pal.L : undefined;
    return undefined;
  });
  paintFace(l1, glow1, set, pal, LEG.bottom, () => pal.D);

  // ---- layer 2: leggings (belt on the body's lower rows, legs down to the boots)
  for (const f of ['front', 'back', 'right', 'left']) paintFace(l2, glow2, set, pal, BODY[f], (x, y, w) => {
    if (y < 7) return null;
    const rows = m.belt;
    if (f === 'front') return motifAt(rows, pal)(x, y - 7);
    const ch = rows[y - 7][Math.min(x, 7)];
    return ch === '.' ? undefined : (y - 7 === 2 ? undefined : pal[ch]);
  });
  for (const f of ['front', 'back', 'right', 'left']) paintFace(l2, glow2, set, pal, LEG[f], (x, y) => {
    if (y > 9) return null;
    if (f === 'right' && (x === 1 || x === 2)) return y % 4 === 3 ? pal.D : stripe; // outer side stripe
    if (f === 'front' && (y === 5 || y === 6)) return y === 5 ? pal.L : (set === 'celestial' ? pal.A : pal.D); // knee guard
    if (f === 'front' && y === 6 && (x === 1 || x === 2)) return stripe;
    if (y === 0) return pal.D;
    return undefined;
  });
  paintFace(l2, glow2, set, pal, LEG.top, () => pal.D);

  tex('models/armor/' + (set === 'alloy' ? 'hero_alloy' : set) + '_layer_1', l1);
  tex('models/armor/' + (set === 'alloy' ? 'hero_alloy' : set) + '_layer_2', l2);
  if (glow1) {
    tex('models/armor/' + set + '_layer_1_glow', glow1);
    tex('models/armor/' + set + '_layer_2_glow', glow2);
  }
  return { l1, l2, glow1, glow2 };
}
const LAYERS = { alloy: armorLayers('alloy'), vibranium: armorLayers('vibranium'), celestial: armorLayers('celestial') };

// ------------------------------------------------------------------------------------------------ entity textures
{
  const pal = P.salvaged;
  const hook = canvas();
  hook.map([
    '................',
    '................',
    '...o........o...',
    '..oHo......oHo..',
    '..oLo..oo..oLo..',
    '..oLLo.oHo.oLo..',
    '...oLLooLooLLo..',
    '....oLLLLLLLo...',
    '.....oBBBBBo....',
    '......oBBBo.....',
    '......oDDDo.....',
    '......oAaAo.....',
    '......oDDDo.....',
    '.......ooo......',
    '................',
    '................'], pal);
  tex('entity/grapple_hook', hook);
  const cable = canvas();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const twist = ((x + y) % 8) < 4;
    const edge = x < 3 || x > 12;
    cable.set(x, y, edge ? [40, 40, 44] : twist ? [150, 150, 158] : [96, 96, 104]);
  }
  tex('entity/grapple_cable', cable);
}

// ------------------------------------------------------------------------------------------------ item models
const ID = s => 'projecthero:' + s;
const generated = ['grapple_line', 'smoke_bomb', 'flashbang', 'vibranium_grapple', 'vibranium_smoke_bomb', 'vibranium_flashbang',
  'hero_alloy_helmet', 'hero_alloy_chestplate', 'hero_alloy_leggings', 'hero_alloy_boots', 'utility_belt',
  'vibranium_helmet', 'vibranium_chestplate', 'vibranium_leggings', 'vibranium_boots',
  'celestial_helmet', 'celestial_chestplate', 'celestial_leggings', 'celestial_boots', 'celestial_upgrade_template'];
for (const id of generated) json(path.join(A, 'models/item', id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: ID('item/' + id) } });
for (const id of ['hero_alloy_sword', 'alloy_baton', 'vibranium_blade']) json(path.join(A, 'models/item', id + '.json'), { parent: 'minecraft:item/handheld', textures: { layer0: ID('item/' + id) } });
json(path.join(A, 'models/item/celestial_warhammer.json'), {
  parent: 'minecraft:item/handheld', textures: { layer0: ID('item/celestial_warhammer') },
  display: {
    thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 6.5, 0.5], scale: [1.45, 1.45, 0.9] },
    thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 6.5, 0.5], scale: [1.45, 1.45, 0.9] },
    firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 4.2, 1.13], scale: [1.0, 1.0, 0.8] },
    firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 4.2, 1.13], scale: [1.0, 1.0, 0.8] },
  },
});

// ------------------------------------------------------------------------------------------------ recipes
const R = path.join(D, 'recipe');
const key = o => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v.startsWith('#') ? { tag: v.slice(1) } : { item: v }]));
const shaped = (name, pattern, keys, result, count = 1, category = 'equipment') =>
  json(path.join(R, name + '.json'), { type: 'minecraft:crafting_shaped', category, pattern, key: key(keys), result: { id: ID(result), count } });
const smith = (name, base, addition, result, template = null) =>
  json(path.join(R, name + '.json'), { type: 'minecraft:smithing_transform', template: template ? { item: template } : [], base: { item: base }, addition: { item: addition }, result: { id: ID(result), count: 1 } });

const T = ID('salvaged_tech'), AI = ID('hero_alloy_ingot'), VS = ID('vibranium_shard'), VI = ID('vibranium_ingot'), CC = ID('celestial_core'), TPL = ID('celestial_upgrade_template');
// Tier I
shaped('grapple_line', [' TH', ' ST', 'I  '], { T, H: 'minecraft:tripwire_hook', S: 'minecraft:string', I: 'minecraft:iron_ingot' }, 'grapple_line');
shaped('smoke_bomb', [' S ', 'TGT', ' C '], { S: 'minecraft:string', T, G: 'minecraft:gunpowder', C: '#minecraft:coals' }, 'smoke_bomb', 4);
shaped('flashbang', [' S ', 'TGT', ' D '], { S: 'minecraft:string', T, G: 'minecraft:gunpowder', D: 'minecraft:glowstone_dust' }, 'flashbang', 4);
// Tier II
shaped('hero_alloy_helmet', ['AAA', 'A A'], { A: AI }, 'hero_alloy_helmet');
shaped('hero_alloy_chestplate', ['A A', 'AAA', 'AAA'], { A: AI }, 'hero_alloy_chestplate');
shaped('hero_alloy_leggings', ['AAA', 'A A', 'A A'], { A: AI }, 'hero_alloy_leggings');
shaped('hero_alloy_boots', ['A A', 'A A'], { A: AI }, 'hero_alloy_boots');
shaped('hero_alloy_sword', ['A', 'A', 'S'], { A: AI, S: 'minecraft:stick' }, 'hero_alloy_sword');
shaped('alloy_baton', ['  A', ' A ', 'T  '], { A: AI, T }, 'alloy_baton');
shaped('utility_belt', ['LLL', 'TAT'], { L: 'minecraft:leather', T, A: AI }, 'utility_belt');
// Tier III: smithing upgrades (no template) -- Alloy piece + Vibranium Ingot, gadget + Vibranium Shard
for (const p of ['helmet', 'chestplate', 'leggings', 'boots']) smith('vibranium_' + p + '_smithing', ID('hero_alloy_' + p), VI, 'vibranium_' + p);
smith('vibranium_blade_smithing', ID('hero_alloy_sword'), VI, 'vibranium_blade');
smith('vibranium_grapple_smithing', ID('grapple_line'), VS, 'vibranium_grapple');
smith('vibranium_smoke_bomb_smithing', ID('smoke_bomb'), VS, 'vibranium_smoke_bomb');
smith('vibranium_flashbang_smithing', ID('flashbang'), VS, 'vibranium_flashbang');
// thrown gadgets in fours: one shard upgrades four bombs at the crafting table
shaped('vibranium_smoke_bomb', [' B ', 'BSB', ' B '], { B: ID('smoke_bomb'), S: VS }, 'vibranium_smoke_bomb', 4);
shaped('vibranium_flashbang', [' B ', 'BSB', ' B '], { B: ID('flashbang'), S: VS }, 'vibranium_flashbang', 4);
// Tier IV: Vibranium piece + Celestial Core + Celestial Upgrade template
for (const p of ['helmet', 'chestplate', 'leggings', 'boots']) smith('celestial_' + p + '_smithing', ID('vibranium_' + p), CC, 'celestial_' + p, TPL);
smith('celestial_warhammer_smithing', ID('vibranium_blade'), CC, 'celestial_warhammer', TPL);
shaped('celestial_upgrade_template_duplicate', ['SCS', 'STS', 'SSS'], { S: VS, C: CC, T: TPL }, 'celestial_upgrade_template', 2, 'misc');

// ------------------------------------------------------------------------------------------------ vault gear sub-tables
const L = path.join(D, 'loot_table/chests');
const it = (name, weight, extra = {}) => {
  const e = { type: 'minecraft:item', name: ID(name), weight };
  const fns = [];
  if (extra.count) fns.push({ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: extra.count[0], max: extra.count[1] } });
  if (extra.worn) fns.push({ function: 'minecraft:set_damage', damage: { type: 'minecraft:uniform', min: extra.worn[0], max: extra.worn[1] } });
  if (fns.length) e.functions = fns;
  return e;
};
const empty = weight => ({ type: 'minecraft:empty', weight });
const pieces = (set, weight, worn) => ['helmet', 'chestplate', 'leggings', 'boots'].map(p => it(set + '_' + p, p === 'chestplate' ? Math.max(1, weight - 1) : weight, { worn }));
const table = pools => ({ type: 'minecraft:chest', pools: pools.map(entries => ({ rolls: 1, entries: entries.e, ...(entries.chance ? { conditions: [{ condition: 'minecraft:random_chance', chance: entries.chance }] } : {}) })) });
json(path.join(L, 'vault_tier1_gear.json'), table([{ e: [empty(80), it('grapple_line', 6, { worn: [0.5, 0.9] }), it('smoke_bomb', 7, { count: [1, 2] }), it('flashbang', 7, { count: [1, 2] })] }]));
json(path.join(L, 'vault_tier2_gear.json'), table([{
  e: [empty(55), it('smoke_bomb', 10, { count: [1, 3] }), it('flashbang', 10, { count: [1, 3] }), it('grapple_line', 6, { worn: [0.6, 1.0] }),
    ...pieces('hero_alloy', 3, [0.45, 0.85]), it('hero_alloy_sword', 3, { worn: [0.5, 0.9] }), it('alloy_baton', 3, { worn: [0.5, 0.9] }), it('utility_belt', 2)],
}]));
json(path.join(L, 'vault_tier3_gear.json'), table([{
  e: [empty(50), ...pieces('hero_alloy', 4, [0.7, 1.0]), ...pieces('vibranium', 2, [0.45, 0.8]), it('vibranium_blade', 1, { worn: [0.5, 0.85] }),
    it('vibranium_grapple', 3, { worn: [0.6, 1.0] }), it('vibranium_smoke_bomb', 4, { count: [1, 2] }), it('vibranium_flashbang', 4, { count: [1, 2] }), it('utility_belt', 3)],
}]));
json(path.join(L, 'vault_tier4_gear.json'), table([
  { e: [it('celestial_upgrade_template', 1)], chance: 0.35 },
  { e: [empty(40), ...pieces('vibranium', 6, [0.6, 1.0]), it('vibranium_blade', 4, { worn: [0.7, 1.0] }), it('vibranium_grapple', 4),
    it('vibranium_smoke_bomb', 5, { count: [2, 4] }), it('vibranium_flashbang', 5, { count: [2, 4] })] },
]));

// ------------------------------------------------------------------------------------------------ vanilla equipment tags (merge)
function mergeTag(rel, values) {
  const f = path.join(MC, 'tags/item', rel + '.json');
  let obj = { replace: false, values: [] }, eol = '\n';
  if (fs.existsSync(f)) { const t = fs.readFileSync(f, 'utf8'); eol = t.includes('\r\n') ? '\r\n' : '\n'; obj = JSON.parse(t); }
  for (const v of values) if (!obj.values.includes(v)) obj.values.push(v);
  write(f, JSON.stringify(obj, null, 2).replace(/\n/g, eol) + eol);
}
const sets = ['hero_alloy', 'vibranium', 'celestial'];
mergeTag('head_armor', sets.map(s => ID(s + '_helmet')));
mergeTag('chest_armor', sets.map(s => ID(s + '_chestplate')));
mergeTag('leg_armor', sets.map(s => ID(s + '_leggings')));
mergeTag('foot_armor', sets.map(s => ID(s + '_boots')));
mergeTag('trimmable_armor', sets.flatMap(s => ['helmet', 'chestplate', 'leggings', 'boots'].map(p => ID(s + '_' + p))));
mergeTag('swords', ['hero_alloy_sword', 'alloy_baton', 'vibranium_blade', 'celestial_warhammer'].map(ID));
mergeTag('enchantable/durability', ['grapple_line', 'vibranium_grapple'].map(ID));

// ------------------------------------------------------------------------------------------------ preview sheet (scratch only)
const out = process.env.GEAR_PREVIEW;
if (out) {
  const S = 6, ids = Object.keys(ICONS);
  const W = 16 * S * 8 + 9 * 4, H = (Math.ceil(ids.length / 8)) * (16 * S + 4) + 4 + 3 * (32 * S / 2 + 4);
  const sheet = canvas(W, H);
  for (let i = 0; i < W * H; i++) { sheet.px[i * 4] = 40; sheet.px[i * 4 + 1] = 42; sheet.px[i * 4 + 2] = 48; sheet.px[i * 4 + 3] = 255; }
  const blit = (src, ox, oy, s) => { for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) { const p = src.get(x, y); if (!p[3]) continue; for (let k = 0; k < s * s; k++) sheet.set(ox + x * s + (k % s), oy + y * s + Math.floor(k / s), p); } };
  ids.forEach((id, i) => { const c = ICONS[id]; blit(c, 4 + (i % 8) * (16 * S + 4), 4 + Math.floor(i / 8) * (16 * S + 4), c.w === 32 ? S / 2 : S); });
  let y0 = 4 + Math.ceil(ids.length / 8) * (16 * S + 4);
  for (const s of Object.keys(LAYERS)) { blit(LAYERS[s].l1, 4, y0, S / 2); blit(LAYERS[s].l2, 4 + 64 * S / 2 + 8, y0, S / 2); if (LAYERS[s].glow1) blit(LAYERS[s].glow1, 4 + 2 * (64 * S / 2 + 8), y0, S / 2); y0 += 32 * S / 2 + 4; }
  write(out, sheet.png());
  console.log('preview ->', out);
}
console.log('gear assets written');
