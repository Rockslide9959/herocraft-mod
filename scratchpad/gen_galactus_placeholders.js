// v0.16.1 Galactus: The Devourer -- PLACEHOLDER assets (simple box figures) so the event runs before the real models
// (designed separately) are exported. THE CONTRACT the real assets must keep:
//   geo/galactus/<name>.geo.json, textures/entity/galactus/<name>.png + <name>_glowmask.png,
//   animations/galactus/<name>.animation.json   for name in GalactusAnims.MODELS
// Clip names, lengths and strike ticks are parsed from GalactusAnims.java; every clip with a hit gets a keyframe on
// (hit - TRANSITION) / 20 s -- this script fails if one is missing. The bones neck / head / flinch are never keyed (code
// poses them). The Silver Surfer is NOT GeckoLib (user decision): a player model with his own skin
// (scratchpad/gen_galactus_surfer_skin.js installs it) standing on the surfboard_mount board. Galactus is fitted 60 blocks
// from the top of his head to his toes: bones starting crest / prong / antenna / horn are not measured.
// Also writes the event's block / item placeholders (textures, models, blockstates), loot tables, the Herald's Beacon
// recipe and the consumable-block tag. Idempotent.
// Run from the repo (or worktree) root: node scratchpad/gen_galactus_placeholders.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const assets = path.join(root, 'src/main/resources/assets/projecthero');
const data = path.join(root, 'src/main/resources/data/projecthero');
const animsJava = fs.readFileSync(path.join(root, 'src/main/java/com/projecthero/mod/galactus/GalactusAnims.java'), 'utf8');

// ---------------------------------------------------------------- parse the contract
const consts = {};
for (const m of animsJava.matchAll(/public static final String (\w+) = "(\w+)";/g)) consts[m[1]] = m[2];
const TRANSITION = +/int TRANSITION = (\d+);/.exec(animsJava)[1];
const clips = [];
for (const m of animsJava.matchAll(/= (clip|hold|loop)\((\w+), "(\w+)", (\d+)(?:, (-?\d+))?\);/g)) {
  clips.push({ kind: m[1], model: consts[m[2]], name: m[3], length: +m[4], hit: m[5] === undefined ? -1 : +m[5] });
}
if (clips.length < 25) throw new Error('parsed only ' + clips.length + ' clips from GalactusAnims.java');
const CODE_BONES = ['neck', 'head', 'flinch'];

// ---------------------------------------------------------------- PNG
const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
let seed = 4242;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));

// 64x64 palette sheet: 8x8 tiles, one material each
const MATS = {
  purple: [0x6a, 0x2c, 0xa8], violet: [0x9b, 0x5c, 0xe8], blue: [0x2c, 0x3e, 0x9a], magenta: [0xc8, 0x3c, 0xb0], dark: [0x1c, 0x12, 0x2c],
  face: [0x8a, 0x86, 0x9a], eye: [0xf0, 0xe8, 0xff], silver: [0xd8, 0xde, 0xe8], chrome: [0xa8, 0xb4, 0xc4], cosmic: [0xb8, 0x7c, 0xff],
  metal: [0x5a, 0x4e, 0x72], gold: [0xd8, 0xb0, 0x40], hull: [0x3c, 0x34, 0x58], core: [0xff, 0x9a, 0xf0], grey: [0x6e, 0x6c, 0x74],
};
const MAT_TILE = {}; Object.keys(MATS).forEach((k, i) => { MAT_TILE[k] = [(i % 8) * 8, Math.floor(i / 8) * 8]; });
const GLOW = { eye: 255, cosmic: 200, core: 255, magenta: 60 };
function sheet(tint) {
  const w = 64, h = 64, px = Buffer.alloc(w * h * 4), gl = Buffer.alloc(w * h * 4);
  for (const [k, [tx, ty]] of Object.entries(MAT_TILE)) {
    const c = MATS[k];
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const n = (rnd() - 0.5) * 22 + ((x === 0 || y === 0) ? -18 : 0);
      const i = ((ty + y) * w + tx + x) * 4;
      px[i] = clamp(c[0] * tint[0] + n); px[i + 1] = clamp(c[1] * tint[1] + n); px[i + 2] = clamp(c[2] * tint[2] + n); px[i + 3] = 255;
      if (GLOW[k] && (k !== 'magenta' || (x + y) % 3 === 0)) { gl[i] = px[i]; gl[i + 1] = px[i + 1]; gl[i + 2] = px[i + 2]; gl[i + 3] = GLOW[k]; }
    }
  }
  return [png(w, h, px), png(w, h, gl)];
}

// ---------------------------------------------------------------- geometry helpers
const faces = mat => { const [u, v] = MAT_TILE[mat]; const f = {}; for (const d of ['north', 'south', 'east', 'west', 'up', 'down']) f[d] = { uv: [u + 0.5, v + 0.5], uv_size: [7, 7] }; return f; };
const cube = (o, s, mat) => ({ origin: o, size: s, uv: faces(mat) });
const bone = (name, parent, pivot, cubes) => { const b = { name, pivot }; if (parent) b.parent = parent; if (cubes && cubes.length) b.cubes = cubes; return b; };

/** A humanoid rig (32 px tall at g = 1): root, hips, waist, flinch, chest, neck, head, arms, legs. k.extra adds bones. */
function humanoid(k) {
  const g = k.girth || 1, body = k.body || 'blue', armor = k.armor || 'purple';
  const B = [];
  B.push(bone('root', null, [0, 0, 0]));
  B.push(bone('hips', 'root', [0, 12, 0], [cube([-4 * g, 10, -2 * g], [8 * g, 3, 4 * g], armor)]));
  B.push(bone('waist', 'hips', [0, 13, 0], [cube([-3.5 * g, 13, -2 * g], [7 * g, 3, 4 * g], body)]));
  B.push(bone('flinch', 'waist', [0, 16, 0]));
  B.push(bone('chest', 'flinch', [0, 16, 0], [cube([-4.5 * g, 16, -2.5 * g], [9 * g, 8, 5 * g], armor), ...(k.chest || [])]));
  B.push(bone('neck', 'chest', [0, 24, 0], [cube([-1.5, 24, -1.5], [3, 1, 3], body)]));
  B.push(bone('head', 'neck', [0, 25, 0], k.head || [cube([-3, 25, -3], [6, 6, 6], body)]));
  for (const s of [1, -1]) {
    const L = s > 0 ? 'l' : 'r';
    const x0 = s > 0 ? 4.5 * g : -4.5 * g - 3 * g;
    B.push(bone('shoulder_' + L, 'chest', [s * 6 * g, 23, 0], [cube([x0, 21, -2 * g], [3 * g, 3.5, 4 * g], armor)]));
    B.push(bone('arm_' + L, 'shoulder_' + L, [s * 6 * g, 22, 0], [cube([x0 + 0.3 * s, 16.5, -1.3 * g], [2.6 * g, 5.5, 2.6 * g], body)]));
    B.push(bone('forearm_' + L, 'arm_' + L, [s * 6 * g, 16.5, 0], [cube([x0, 11.5, -1.5 * g], [3 * g, 5, 3 * g], k.gauntlet || armor)]));
    B.push(bone('hand_' + L, 'forearm_' + L, [s * 6 * g, 11.5, 0], [cube([x0 + 0.2, 9.5, -1.3 * g], [2.6 * g, 2, 2.6 * g], k.handMat || body)]));
    const lx = s > 0 ? 0.4 : -3.6 * g + 0.4;
    B.push(bone('thigh_' + L, 'hips', [s * 2 * g, 11, 0], [cube([lx, 5.5, -1.5 * g], [3.2 * g, 5.5, 3 * g], body)]));
    B.push(bone('shin_' + L, 'thigh_' + L, [s * 2 * g, 5.5, 0], [cube([lx, 1, -1.5 * g], [3.2 * g, 4.5, 3 * g], k.boot || armor)]));
    B.push(bone('foot_' + L, 'shin_' + L, [s * 2 * g, 1, 0], [cube([lx, 0, -2.6 * g], [3.2 * g, 1, 4.2 * g], k.boot || armor)]));
  }
  if (k.extra) k.extra(B);
  return B;
}

function galactus() {
  return humanoid({
    girth: 1.15, body: 'blue', armor: 'purple', gauntlet: 'violet', handMat: 'violet', boot: 'violet',
    chest: [cube([-2.4, 19, -3.0], [4.8, 3.8, 0.6], 'magenta'), cube([-1.2, 19.6, -3.2], [2.4, 2.6, 0.4], 'cosmic')],
    head: [cube([-2.6, 25, -2.6], [5.2, 5.2, 5.2], 'face'), cube([-1.9, 27.6, -2.75], [1.4, 0.7, 0.3], 'eye'), cube([0.5, 27.6, -2.75], [1.4, 0.7, 0.3], 'eye'),
      cube([-3.4, 26, -3.2], [6.8, 6.2, 6.4], 'purple'), cube([-1, 31.6, -2.4], [2, 1.2, 5], 'violet')],
    extra: B => {
      // the helmet's famous crest fins, and the shoulder flares
      B.push(bone('crest', 'head', [0, 31, 0], [cube([-5.6, 27, -0.6], [2.2, 7.5, 1.2], 'violet'), cube([3.4, 27, -0.6], [2.2, 7.5, 1.2], 'violet'),
        cube([-6.6, 33, -0.5], [1.2, 3, 1], 'violet'), cube([5.4, 33, -0.5], [1.2, 3, 1], 'violet')]));
      B.push(bone('emblem', 'chest', [0, 20, -3], [cube([-2, 18.6, -3.4], [4, 0.6, 0.4], 'cosmic')]));
    },
  });
}

function surfer() {
  return humanoid({
    girth: 0.9, body: 'silver', armor: 'chrome', gauntlet: 'silver', handMat: 'silver', boot: 'silver',
    head: [cube([-2.8, 25, -2.8], [5.6, 6, 5.6], 'silver'), cube([-1.6, 27.6, -2.95], [1.2, 0.6, 0.3], 'eye'), cube([0.4, 27.6, -2.95], [1.2, 0.6, 0.3], 'eye')],
    extra: B => {
      // he stands on the board: its bone sits under his feet (the model's lowest point)
      for (const b of B) { if (b.cubes) for (const c of b.cubes) c.origin[1] += 1.5; b.pivot = [b.pivot[0], b.pivot[1] + 1.5, b.pivot[2]]; }
      B.push(bone('board', 'root', [0, 0.75, 0], [cube([-4, 0, -11], [8, 1.5, 22], 'silver'), cube([-2.5, 0.2, -13.5], [5, 1.1, 2.5], 'silver'),
        cube([-0.5, 1.5, -9], [1, 0.2, 18], 'cosmic')]));
    },
  });
}

function punisher() {
  return humanoid({
    girth: 1.5, body: 'metal', armor: 'hull', gauntlet: 'violet', handMat: 'violet', boot: 'hull',
    chest: [cube([-3, 18, -3.9], [6, 4, 0.5], 'cosmic')],
    head: [cube([-2.5, 25, -2.5], [5, 4.5, 5], 'hull'), cube([-2, 26.8, -2.65], [4, 0.8, 0.3], 'core')],
    extra: B => B.push(bone('blaster', 'forearm_r', [-9, 13, 0], [cube([-12.4, 10, -2.6], [4.4, 3, 5.2], 'dark'), cube([-11.2, 9.4, -3.4], [2, 1.2, 1.2], 'core')])),
  });
}

function converter() {
  return [
    bone('root', null, [0, 0, 0]),
    bone('base', 'root', [0, 0, 0], [cube([-8, 0, -8], [16, 4, 16], 'hull'), cube([-6, 4, -6], [12, 2, 12], 'metal')]),
    bone('column', 'base', [0, 6, 0], [cube([-3.5, 6, -3.5], [7, 40, 7], 'hull'), cube([-4, 20, -4], [8, 1, 8], 'magenta'), cube([-4, 34, -4], [8, 1, 8], 'magenta')]),
    bone('ring_a', 'column', [0, 30, 0], [cube([-9, 29, -1], [18, 2, 2], 'violet'), cube([-1, 29, -9], [2, 2, 18], 'violet')]),
    bone('ring_b', 'column', [0, 42, 0], [cube([-7, 41, -7], [14, 1.5, 1.5], 'violet'), cube([-7, 41, 5.5], [14, 1.5, 1.5], 'violet')]),
    bone('core', 'column', [0, 50, 0], [cube([-3, 46, -3], [6, 9, 6], 'core'), cube([-1.5, 55, -1.5], [3, 5, 3], 'cosmic')]),
  ];
}

function worldship() {
  return [
    bone('root', null, [0, 0, 0]),
    bone('hull', 'root', [0, 8, 0], [cube([-40, 4, -40], [80, 8, 80], 'hull'), cube([-28, 12, -28], [56, 6, 56], 'metal'), cube([-16, 18, -16], [32, 8, 32], 'purple'),
      cube([-6, -4, -6], [12, 8, 12], 'core')]),
    bone('ring', 'hull', [0, 8, 0], [cube([-52, 6, -4], [104, 3, 8], 'violet'), cube([-4, 6, -52], [8, 3, 104], 'violet')]),
    bone('spires', 'hull', [0, 26, 0], [cube([-3, 26, -3], [6, 30, 6], 'violet'), cube([-30, 12, -3], [5, 18, 5], 'violet'), cube([25, 12, -3], [5, 18, 5], 'violet'),
      cube([-3, 12, 25], [5, 18, 5], 'violet'), cube([-3, 12, -30], [5, 18, 5], 'violet')]),
  ];
}

function hand() {
  return [
    bone('root', null, [0, 0, 0]),
    bone('palm', 'root', [0, 16, 0], [cube([-12, 12, -4], [24, 22, 8], 'violet'), cube([-10, 34, -4.5], [20, 10, 9], 'purple')]),
    bone('fingers', 'palm', [0, 12, 0], [cube([-12, -6, -3.5], [5, 18, 7], 'violet'), cube([-6, -9, -3.5], [5, 21, 7], 'violet'), cube([0, -9, -3.5], [5, 21, 7], 'violet'),
      cube([6, -6, -3.5], [5, 18, 7], 'violet')]),
    bone('thumb', 'palm', [12, 26, 0], [cube([12, 14, -3], [6, 14, 6], 'violet')]),
  ];
}

function board() {
  return [
    bone('root', null, [0, 0, 0]),
    bone('board', 'root', [0, 2, 0], [cube([-6, 0, -16], [12, 2, 32], 'silver'), cube([-4, 0.2, -19.5], [8, 1.6, 3.5], 'silver'), cube([-0.6, 2, -14], [1.2, 0.3, 28], 'cosmic')]),
    bone('fin', 'board', [0, 0, 12], [cube([-0.5, -2, 10], [1, 2, 4], 'chrome')]),
  ];
}

function nullifier() {
  return [
    bone('root', null, [0, 0, 0]),
    bone('body', 'root', [0, 4, 0], [cube([-1.5, 0, -1.5], [3, 10, 3], 'gold'), cube([-2.2, 9, -2.2], [4.4, 1.2, 4.4], 'gold'), cube([-2.2, 2, -2.2], [4.4, 1, 4.4], 'dark')]),
    bone('crystal', 'body', [0, 12, 0], [cube([-1.4, 10.5, -1.4], [2.8, 4, 2.8], 'cosmic'), cube([-0.6, 14.5, -0.6], [1.2, 2, 1.2], 'core')]),
  ];
}

const MODELS = {
  galactus: { bones: galactus(), tint: [1, 1, 1], rig: 'humanoid' },
  punisher: { bones: punisher(), tint: [1, 1, 1], rig: 'humanoid' },
  element_converter: { bones: converter(), tint: [1, 1, 1], rig: 'tower' },
  worldship: { bones: worldship(), tint: [1, 1, 1], rig: 'ship' },
  galactus_hand: { bones: hand(), tint: [1, 1, 1], rig: 'hand' },
  surfboard_mount: { bones: board(), tint: [1, 1, 1], rig: 'board' },
  nullifier: { bones: nullifier(), tint: [1, 1, 1], rig: 'item' },
};

// ---------------------------------------------------------------- animations
const r3 = v => v.map(x => Math.round(x * 100) / 100);
const t4 = s => (Math.round(s * 10000) / 10000).toFixed(4);
function track(keys) { const o = {}; for (const [t, v] of keys) o[t4(t)] = r3(v); return o; }
function addRot(bones, b, keys) { (bones[b] = bones[b] || {}).rotation = track(keys); }
function addPos(bones, b, keys) { (bones[b] = bones[b] || {}).position = track(keys); }

function animFor(model, rig, c) {
  const len = c.kind === 'loop' ? c.length / 20 : Math.max(1, c.length - TRANSITION) / 20;
  const strike = c.hit >= TRANSITION ? (c.hit - TRANSITION) / 20 : -1;
  const bones = {};
  const out = { animation_length: len, bones };
  if (c.kind === 'loop') out.loop = true;
  if (c.kind === 'hold') out.loop = 'hold_on_last_frame';
  const n = c.name;
  const Z = [0, 0, 0];
  const s = strike >= 0 ? strike : len * 0.5;
  const wind = Math.max(0.05, Math.min(s - 0.05, s * 0.6));
  if (rig === 'humanoid') {
    if (n === 'idle' || n === 'ally_idle') {
      addRot(bones, 'chest', [[0, Z], [len / 2, [-2, 0, 0]], [len, Z]]);
      addRot(bones, 'arm_l', [[0, [0, 0, -4]], [len / 2, [0, 0, -7]], [len, [0, 0, -4]]]);
      addRot(bones, 'arm_r', [[0, [0, 0, 4]], [len / 2, [0, 0, 7]], [len, [0, 0, 4]]]);
      if (n === 'ally_idle') addRot(bones, 'shoulder_r', [[0, [-20, 0, 0]], [len, [-20, 0, 0]]]);
    } else if (n === 'walk') {
      for (const [l, sg] of [['thigh_l', 1], ['thigh_r', -1]]) addRot(bones, l, [[0, [sg * 22, 0, 0]], [len / 2, [-sg * 22, 0, 0]], [len, [sg * 22, 0, 0]]]);
      for (const [a, sg] of [['arm_l', -1], ['arm_r', 1]]) addRot(bones, a, [[0, [sg * 14, 0, 0]], [len / 2, [-sg * 14, 0, 0]], [len, [sg * 14, 0, 0]]]);
    } else if (n === 'fly') {
      addRot(bones, 'root', [[0, [12, 0, 0]], [len / 2, [14, 0, 2]], [len, [12, 0, 0]]]);
      addRot(bones, 'arm_l', [[0, [20, 0, -25]], [len, [20, 0, -25]]]);
      addRot(bones, 'arm_r', [[0, [20, 0, 25]], [len, [20, 0, 25]]]);
      addRot(bones, 'thigh_l', [[0, [-10, 0, 0]], [len, [-10, 0, 0]]]);
    } else if (n === 'death') {
      addRot(bones, 'root', [[0, Z], [len * 0.7, [80, 0, 0]], [len, [88, 0, 0]]]);
    } else if (n === 'turn_ally') {
      addRot(bones, 'chest', [[0, Z], [len * 0.3, [18, 0, 0]], [len * 0.7, [10, 0, 0]], [len, Z]]);
      addRot(bones, 'arm_r', [[0, Z], [len * 0.5, [-40, 0, 20]], [len, [-90, 0, 10]]]);
      addRot(bones, 'arm_l', [[0, Z], [len * 0.4, [-20, 0, -10]], [len, Z]]);
    } else if (n === 'retreat') {
      // the hand rises on the strike; the figure stays up until the burst
      addRot(bones, 'arm_r', [[0, Z], [s, [-170, 0, 12]], [len, [-170, 0, 12]]]);
      addRot(bones, 'chest', [[0, Z], [s, [-8, 0, 0]], [len, [-6, 0, 0]]]);
    } else if (n === 'grab_lift') {
      addRot(bones, 'arm_r', [[0, [-70, 0, 0]], [len * 0.6, [-120, 0, 10]], [len, [-130, 0, 14]]]);
      addRot(bones, 'forearm_r', [[0, Z], [len, [-30, 0, 0]]]);
    } else if (n === 'reach' || n === 'transmute') {
      const a = n === 'reach' ? 'arm_r' : 'arm_l';
      addRot(bones, 'chest', [[0, Z], [wind, [12, 0, 0]], [s, [28, 0, 0]], [len, Z]]);
      addRot(bones, a, [[0, Z], [wind, [-40, 0, 0]], [s, [-75, 0, 0]], [len, [-20, 0, 0]]]);
    } else if (n === 'weakpoint_hurt') {
      addRot(bones, 'chest', [[0, Z], [len * 0.25, [-14, 0, 6]], [len, Z]]);
    } else if (n === 'shield') {
      addRot(bones, 'arm_l', [[0, Z], [s, [-90, 0, -30]], [len, [-90, 0, -30]]]);
      addRot(bones, 'arm_r', [[0, Z], [s, [-90, 0, 30]], [len, [-90, 0, 30]]]);
    } else {
      // an attack / cast: wind up, strike on the contract tick, recover
      const big = ['slam', 'disintegrate', 'eye_beam', 'fling', 'summon'].includes(n);
      addRot(bones, 'chest', [[0, Z], [wind, [big ? -18 : -8, 8, 0]], [s, [big ? 22 : 10, -8, 0]], [len, Z]]);
      addRot(bones, 'arm_r', [[0, Z], [wind, [big ? -160 : -100, 0, 0]], [s, [big ? -20 : -80, 0, 0]], [len, Z]]);
      addRot(bones, 'arm_l', [[0, Z], [wind, [big ? -150 : -30, 0, -10]], [s, [-10, 0, 10]], [len, Z]]);
    }
  } else if (rig === 'tower') {
    const spin = n === 'consume' ? 360 : n === 'shielded' ? 90 : n === 'idle' ? 45 : 0;
    if (n === 'destroyed') {
      addRot(bones, 'column', [[0, Z], [len * 0.6, [8, 0, 14]], [len, [20, 0, 35]]]);
      addPos(bones, 'core', [[0, Z], [len, [0, -20, 0]]]);
    } else {
      addRot(bones, 'ring_a', [[0, Z], [len, [0, spin, 0]]]);
      addRot(bones, 'ring_b', [[0, Z], [len, [0, -spin, 0]]]);
      addPos(bones, 'core', [[0, Z], [len / 2, [0, 1.5, 0]], [len, Z]]);
    }
  } else if (rig === 'ship') {
    if (n === 'leave') addPos(bones, 'root', [[0, Z], [len * 0.3, [0, 10, 0]], [len, [0, 900, 0]]]);
    else addRot(bones, 'ring', [[0, Z], [len, [0, 90, 0]]]);
  } else if (rig === 'hand') {
    if (n === 'grip') addRot(bones, 'fingers', [[0, [70, 0, 0]], [len / 2, [76, 0, 0]], [len, [70, 0, 0]]]);
    else addRot(bones, 'fingers', [[0, [8, 0, 0]], [len / 2, [14, 0, 0]], [len, [8, 0, 0]]]);
  } else if (rig === 'board') {
    addPos(bones, 'board', [[0, Z], [len / 2, [0, 0.6, 0]], [len, Z]]);
  } else if (rig === 'item') {
    addRot(bones, 'crystal', [[0, Z], [len, [0, 360, 0]]]);
  }
  for (const nb of CODE_BONES) if (bones[nb]) throw new Error(c.name + ' keys ' + nb);
  if (strike >= 0) {
    const hit = Object.values(bones).some(b => Object.values(b).some(tr => Object.keys(tr).some(k => Math.abs(+k - strike) < 1e-4)));
    if (!hit) throw new Error(model + '.' + n + ': no keyframe on its strike time ' + strike);
  }
  return out;
}

// ---------------------------------------------------------------- write the entity / item models
const geoDir = path.join(assets, 'geo/galactus'), texDir = path.join(assets, 'textures/entity/galactus'), animDir = path.join(assets, 'animations/galactus');
for (const d of [geoDir, texDir, animDir]) fs.mkdirSync(d, { recursive: true });
const NAMES = /MODELS = \{([^}]+)\}/.exec(animsJava)[1].split(',').map(s => consts[s.trim()]);
for (const name of NAMES) {
  const m = MODELS[name];
  if (!m) throw new Error('no placeholder for ' + name);
  const names = new Set(m.bones.map(b => b.name));
  if (m.rig === 'humanoid' && !['neck', 'head', 'flinch'].every(b => names.has(b))) throw new Error(name + ' lacks neck/head/flinch');
  let minY = 1e9, maxY = -1e9;
  for (const b of m.bones) for (const c of b.cubes || []) { minY = Math.min(minY, c.origin[1]); maxY = Math.max(maxY, c.origin[1] + c.size[1]); }
  const geo = { format_version: '1.12.0', 'minecraft:geometry': [{ description: { identifier: 'geometry.' + name, texture_width: 64, texture_height: 64,
    visible_bounds_width: 16, visible_bounds_height: 16, visible_bounds_offset: [0, 4, 0] }, bones: m.bones }] };
  fs.writeFileSync(path.join(geoDir, name + '.geo.json'), JSON.stringify(geo, null, '\t'));
  const [tex, glow] = sheet(m.tint);
  fs.writeFileSync(path.join(texDir, name + '.png'), tex);
  fs.writeFileSync(path.join(texDir, name + '_glowmask.png'), glow);
  const animations = {};
  for (const c of clips.filter(c => c.model === name)) animations['animation.' + name + '.' + c.name] = animFor(name, m.rig, c);
  if (!Object.keys(animations).length) throw new Error('no clips for ' + name);
  fs.writeFileSync(path.join(animDir, name + '.animation.json'), JSON.stringify({ format_version: '1.8.0', animations }, null, '\t'));
  console.log(name + ': ' + m.bones.length + ' bones, ' + Object.keys(animations).length + ' clips, rest height ' + (maxY - minY).toFixed(2) + ' px');
}

// ---------------------------------------------------------------- blocks and items
function tex16(fn) {
  const w = 16, h = 16, px = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = fn(x, y); const i = (y * w + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = c[3] === undefined ? 255 : c[3]; }
  return png(w, h, px);
}
const noise = (base, amt) => { const n = (rnd() - 0.5) * amt; return [base[0] + n, base[1] + n, base[2] + n]; };
const write = (p, content) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, typeof content === 'string' || Buffer.isBuffer(content) ? content : JSON.stringify(content, null, 2)); };
const blockTex = path.join(assets, 'textures/block'), itemTex = path.join(assets, 'textures/item');

write(path.join(blockTex, 'consumed_ground.png'), tex16((x, y) => { const c = noise([0x74, 0x72, 0x7a], 26); if ((x * 7 + y * 3) % 11 === 0) return [0x4a, 0x46, 0x52]; return c; }));
write(path.join(blockTex, 'cosmic_hazard.png'), tex16((x, y) => { const c = noise([0x7a, 0x2c, 0xc8], 30); if ((x + y) % 5 === 0 || (x * 3 - y) % 7 === 0) return [0xe8, 0x9a, 0xff]; return c; }));
write(path.join(blockTex, 'heralds_beacon_side.png'), tex16((x, y) => { if (x === 0 || x === 15 || y === 0 || y === 15) return [0xc8, 0xd0, 0xdc]; if (x > 4 && x < 11 && y > 3 && y < 12) return noise([0x6a, 0x2c, 0xa8], 20); return noise([0x3c, 0x34, 0x58], 18); }));
write(path.join(blockTex, 'heralds_beacon_side_active.png'), tex16((x, y) => { if (x === 0 || x === 15 || y === 0 || y === 15) return [0xf0, 0xf4, 0xff]; if (x > 4 && x < 11 && y > 3 && y < 12) return noise([0xe8, 0x9a, 0xff], 20); return noise([0x5a, 0x4e, 0x72], 18); }));
write(path.join(blockTex, 'heralds_beacon_top.png'), tex16((x, y) => { const d = Math.hypot(x - 7.5, y - 7.5); if (d < 3) return [0xff, 0xd0, 0xff]; if (d < 5) return noise([0x9b, 0x5c, 0xe8], 16); return x === 0 || y === 0 || x === 15 || y === 15 ? [0xc8, 0xd0, 0xdc] : noise([0x3c, 0x34, 0x58], 16); }));
write(path.join(blockTex, 'galactus_helmet_crest.png'), tex16((x, y) => { if ((x + y) % 6 === 0) return [0xb8, 0x7c, 0xff]; return noise(y < 6 ? [0x9b, 0x5c, 0xe8] : [0x6a, 0x2c, 0xa8], 20); }));
write(path.join(itemTex, 'ultimate_nullifier_part.png'), tex16((x, y) => { const d = Math.abs(x - 7.5) + Math.abs(y - 7.5); if (d < 3) return [0xff, 0xd0, 0xff]; if (d < 6) return noise([0xd8, 0xb0, 0x40], 30); if (d < 7.5) return [0x40, 0x30, 0x10]; return [0, 0, 0, 0]; }));
write(path.join(itemTex, 'power_cosmic.png'), tex16((x, y) => { const d = Math.hypot(x - 7.5, y - 7.5); if (d < 2.5) return [0xff, 0xf0, 0xff]; if (d < 5) return noise([0xb8, 0x7c, 0xff], 30); if (d < 6.5 && (x + y) % 2 === 0) return [0x7a, 0x2c, 0xc8]; return [0, 0, 0, 0]; }));
write(path.join(itemTex, 'silver_surfer_board.png'), tex16((x, y) => { const u = x - 7.5, v = y - 7.5; const on = Math.abs(u + v) < 2.6 && Math.abs(u - v) < 13; if (!on) return [0, 0, 0, 0]; if (Math.abs(u + v) < 0.6) return [0xb8, 0x7c, 0xff]; return noise([0xd8, 0xde, 0xe8], 20); }));
write(path.join(itemTex, 'ultimate_nullifier.png'), tex16((x, y) => { if (x >= 6 && x <= 9 && y >= 5 && y <= 15) return noise([0xd8, 0xb0, 0x40], 30); if (x >= 5 && x <= 10 && y >= 1 && y <= 4) return [0xe8, 0x9a, 0xff]; return [0, 0, 0, 0]; }));

write(path.join(assets, 'blockstates/consumed_ground.json'), { variants: { '': { model: 'projecthero:block/consumed_ground' } } });
write(path.join(assets, 'blockstates/cosmic_hazard.json'), { variants: { '': { model: 'projecthero:block/cosmic_hazard' } } });
write(path.join(assets, 'blockstates/heralds_beacon.json'), { variants: { 'active=false': { model: 'projecthero:block/heralds_beacon' }, 'active=true': { model: 'projecthero:block/heralds_beacon_active' } } });
write(path.join(assets, 'blockstates/galactus_helmet_crest.json'), { variants: { 'facing=north': { model: 'projecthero:block/galactus_helmet_crest' },
  'facing=east': { model: 'projecthero:block/galactus_helmet_crest', y: 90 }, 'facing=south': { model: 'projecthero:block/galactus_helmet_crest', y: 180 },
  'facing=west': { model: 'projecthero:block/galactus_helmet_crest', y: 270 } } });
write(path.join(assets, 'models/block/consumed_ground.json'), { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/consumed_ground' } });
write(path.join(assets, 'models/block/cosmic_hazard.json'), { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/cosmic_hazard' } });
write(path.join(assets, 'models/block/heralds_beacon.json'), { parent: 'minecraft:block/cube_bottom_top', textures: { side: 'projecthero:block/heralds_beacon_side',
  top: 'projecthero:block/heralds_beacon_top', bottom: 'projecthero:block/heralds_beacon_side' } });
write(path.join(assets, 'models/block/heralds_beacon_active.json'), { parent: 'minecraft:block/cube_bottom_top', textures: { side: 'projecthero:block/heralds_beacon_side_active',
  top: 'projecthero:block/heralds_beacon_top', bottom: 'projecthero:block/heralds_beacon_side_active' } });
const el = (from, to) => ({ from, to, faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, { texture: '#crest' }])) });
write(path.join(assets, 'models/block/galactus_helmet_crest.json'), { parent: 'minecraft:block/block', textures: { crest: 'projecthero:block/galactus_helmet_crest',
  particle: 'projecthero:block/galactus_helmet_crest' }, elements: [el([3, 0, 4], [13, 3, 12]), el([4, 3, 6], [12, 7, 10]), el([2, 3, 7], [4, 15, 9]), el([12, 3, 7], [14, 15, 9]),
  el([1, 12, 7], [2, 16, 9]), el([14, 12, 7], [15, 16, 9]), el([7, 7, 7], [9, 11, 9])] });
write(path.join(assets, 'models/item/heralds_beacon.json'), { parent: 'projecthero:block/heralds_beacon_active' });
write(path.join(assets, 'models/item/galactus_helmet_crest.json'), { parent: 'projecthero:block/galactus_helmet_crest' });
for (const it of ['ultimate_nullifier_part', 'power_cosmic', 'silver_surfer_board']) write(path.join(assets, 'models/item/' + it + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + it } });
// the Nullifier draws its GeckoLib model in hand; the flat sprite is kept for the inventory icon
write(path.join(assets, 'models/item/ultimate_nullifier.json'), { parent: 'builtin/entity', gui_light: 'front', textures: { particle: 'projecthero:item/ultimate_nullifier' },
  display: { thirdperson_righthand: { rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55] }, thirdperson_lefthand: { rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55] },
    firstperson_righthand: { rotation: [0, -20, 0], translation: [1, 2, 0], scale: [0.5, 0.5, 0.5] }, firstperson_lefthand: { rotation: [0, 20, 0], translation: [1, 2, 0], scale: [0.5, 0.5, 0.5] },
    ground: { translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] }, gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, fixed: { scale: [0.6, 0.6, 0.6] } } });
write(path.join(assets, 'models/item/ultimate_nullifier_icon.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/ultimate_nullifier' } });
for (const egg of ['silver_surfer_spawn_egg', 'punisher_spawn_egg']) write(path.join(assets, 'models/item/' + egg + '.json'), { parent: 'minecraft:item/template_spawn_egg' });

const dropSelf = name => ({ type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:' + name }], conditions: [{ condition: 'minecraft:survives_explosion' }] }] });
write(path.join(data, 'loot_table/blocks/heralds_beacon.json'), dropSelf('heralds_beacon'));
write(path.join(data, 'loot_table/blocks/galactus_helmet_crest.json'), dropSelf('galactus_helmet_crest'));
write(path.join(data, 'loot_table/blocks/consumed_ground.json'), { type: 'minecraft:block', pools: [] });
write(path.join(data, 'loot_table/blocks/cosmic_hazard.json'), { type: 'minecraft:block', pools: [] });
write(path.join(data, 'recipe/heralds_beacon.json'), { type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['ENE', 'ASA', 'OBO'],
  key: { E: { item: 'minecraft:end_crystal' }, N: { item: 'minecraft:netherite_ingot' }, A: { item: 'minecraft:amethyst_block' }, S: { item: 'minecraft:nether_star' },
    O: { item: 'minecraft:crying_obsidian' }, B: { item: 'minecraft:beacon' } }, result: { id: 'projecthero:heralds_beacon', count: 1 } });
write(path.join(data, 'tags/block/galactus_consumable.json'), { replace: false, values: ['#minecraft:dirt', '#minecraft:base_stone_overworld', '#minecraft:sand', '#minecraft:terracotta',
  'minecraft:gravel', 'minecraft:snow_block', 'minecraft:clay', 'minecraft:sandstone', 'minecraft:red_sandstone', 'minecraft:mud', 'minecraft:packed_mud', 'minecraft:dirt_path',
  'minecraft:farmland', 'minecraft:calcite', 'minecraft:netherrack', 'minecraft:soul_sand', 'minecraft:soul_soil', 'minecraft:end_stone', 'minecraft:powder_snow'] });
// the event's blocks are never mineable for drops, but pickaxe speed keeps breaking them sane
const mineable = path.join(root, 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json');
if (fs.existsSync(mineable)) {
  const j = JSON.parse(fs.readFileSync(mineable, 'utf8'));
  for (const id of ['projecthero:heralds_beacon', 'projecthero:galactus_helmet_crest', 'projecthero:consumed_ground', 'projecthero:cosmic_hazard']) if (!j.values.includes(id)) j.values.push(id);
  fs.writeFileSync(mineable, JSON.stringify(j, null, 2) + String.fromCharCode(10));
}
console.log('blocks, items, loot tables, recipe and tag written');
