// v0.16.1 Frost Giant Assault: PLACEHOLDER GeckoLib assets (simple box figures) so the raid runs before the real models
// (scratchpad/gen_frost_giant_models.js) are approved. Same file names, bone names and clip names as the real ones:
//   geo/frost_giant/<name>.geo.json, textures/entity/frost_giant/<name>.png + <name>_glowmask.png,
//   animations/frost_giant/<name>.animation.json
// The clip list, lengths and strike ticks are parsed from FrostGiantAnims.java, and every clip with a hit gets a keyframe
// on (hit - TRANSITION) / 20 s -- this script fails if one is missing. neck and flinch are never keyed.
// Run from the repo (or worktree) root: node scratchpad/gen_frost_giant_placeholders.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const assets = path.join(root, 'src/main/resources/assets/projecthero');
const animsJava = fs.readFileSync(path.join(root, 'src/main/java/com/projecthero/mod/frostgiant/FrostGiantAnims.java'), 'utf8');

// ---------------------------------------------------------------- parse the contract
const consts = {};
for (const m of animsJava.matchAll(/public static final String (\w+) = "(\w+)";/g)) consts[m[1]] = m[2];
const TRANSITION = +/int TRANSITION = (\d+);/.exec(animsJava)[1];
const clips = [];
for (const m of animsJava.matchAll(/= (clip|hold|loop)\((\w+), "(\w+)", (\d+)(?:, (-?\d+))?\);/g)) {
  clips.push({ kind: m[1], model: consts[m[2]], name: m[3], length: +m[4], hit: m[5] === undefined ? -1 : +m[5] });
}
if (clips.length < 60) throw new Error('parsed only ' + clips.length + ' clips from FrostGiantAnims.java');

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
let seed = 1234;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };

// 64x64 palette sheet: 8x8 tiles, one material each
const MATS = {
  skin: [0x3d, 0x6f, 0xa8], ridge: [0x8f, 0xc4, 0xee], fur: [0x6b, 0x5a, 0x4a], leather: [0x4a, 0x36, 0x28], ice: [0xb8, 0xec, 0xff],
  eye: [0xff, 0x20, 0x18], metal: [0x8a, 0x92, 0x9c], robe: [0x2c, 0x3a, 0x6a], bone: [0xe4, 0xdc, 0xc8], dark: [0x22, 0x2c, 0x44],
  glacier: [0x9a, 0xa8, 0xb8], casket: [0x3a, 0x9c, 0xe8], gold: [0xd8, 0xb0, 0x40], crown: [0xd0, 0xf2, 0xff],
};
const MAT_TILE = {}; Object.keys(MATS).forEach((k, i) => { MAT_TILE[k] = [(i % 8) * 8, Math.floor(i / 8) * 8]; });
const GLOW = { eye: 255, ice: 150, ridge: 70, casket: 170, crown: 120 };
function sheet(tint) {
  const w = 64, h = 64, px = Buffer.alloc(w * h * 4), gl = Buffer.alloc(w * h * 4);
  for (const [k, [tx, ty]] of Object.entries(MAT_TILE)) {
    const c = MATS[k];
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const n = (rnd() - 0.5) * 26 + (k === 'ridge' && (x + y) % 3 === 0 ? 30 : 0);
      const i = ((ty + y) * w + tx + x) * 4;
      px[i] = Math.max(0, Math.min(255, c[0] * tint[0] + n)); px[i + 1] = Math.max(0, Math.min(255, c[1] * tint[1] + n)); px[i + 2] = Math.max(0, Math.min(255, c[2] * tint[2] + n)); px[i + 3] = 255;
      if (GLOW[k] && (k !== 'ice' || x === 0 || y === 0 || x === 7 || y === 7) && (k !== 'ridge' || (x + y) % 3 === 0)) {
        gl[i] = px[i]; gl[i + 1] = px[i + 1]; gl[i + 2] = px[i + 2]; gl[i + 3] = GLOW[k];
      }
    }
  }
  return [png(w, h, px), png(w, h, gl)];
}

// ---------------------------------------------------------------- geometry helpers
const faces = (mat, size) => {
  const [u, v] = MAT_TILE[mat];
  const f = {}; for (const d of ['north', 'south', 'east', 'west', 'up', 'down']) f[d] = { uv: [u + 0.5, v + 0.5], uv_size: [7, 7] };
  return f;
};
const cube = (o, s, mat) => ({ origin: o, size: s, uv: faces(mat, s) });
const bone = (name, parent, pivot, cubes) => { const b = { name, pivot }; if (parent) b.parent = parent; if (cubes && cubes.length) b.cubes = cubes; return b; };

/** The shared Jotun rig. k = { girth, mat, robe, weapon, extra } */
function jotun(k) {
  const g = k.girth || 1, skin = k.skin || 'skin';
  const B = [];
  B.push(bone('root', null, [0, 0, 0]));
  B.push(bone('hips', 'root', [0, 16, 0], [cube([-4 * g, 14, -2.5 * g], [8 * g, 3, 5 * g], k.robe ? 'robe' : 'leather')]));
  B.push(bone('waist', 'hips', [0, 17, 0], [cube([-3.4 * g, 17, -2 * g], [6.8 * g, 3, 4 * g], skin)]));
  B.push(bone('flinch', 'waist', [0, 20, 0]));
  B.push(bone('chest', 'flinch', [0, 20, 0], [cube([-5 * g, 20, -3 * g], [10 * g, 8, 6 * g], skin), cube([-3 * g, 23, -3.2 * g], [6 * g, 3, 0.4], 'ridge')]));
  B.push(bone('neck', 'chest', [0, 28, 0], [cube([-1.5, 28, -1.5], [3, 1, 3], skin)]));
  B.push(bone('head', 'neck', [0, 29, 0], [cube([-3, 29, -3], [6, 6, 6], skin), cube([-2.2, 32, -3.15], [1.6, 0.8, 0.3], 'eye'), cube([0.6, 32, -3.15], [1.6, 0.8, 0.3], 'eye'), cube([-2.5, 33.2, -3.2], [5, 0.4, 0.4], 'ridge')]));
  B.push(bone('jaw', 'head', [0, 30, -1], [cube([-2.4, 28.6, -3.2], [4.8, 1.4, 4], skin)]));
  B.push(bone('beard', 'jaw', [0, 28.6, -3], [cube([-2, 24.5, -3.5], [4, 4.1, 1], k.beard || 'fur')]));
  B.push(bone('helm', 'head', [0, 35, 0], k.helm || [cube([-3.4, 35, -3.4], [6.8, 1.2, 6.8], 'metal'), cube([-6.5, 35.2, -0.5], [3.2, 1, 1], 'bone'), cube([3.3, 35.2, -0.5], [3.2, 1, 1], 'bone')]));
  for (const s of [1, -1]) {
    const L = s > 0 ? 'l' : 'r', x0 = s > 0 ? 5 * g : -5 * g - 3 * g;
    B.push(bone('shoulder_' + L, 'chest', [s * 5.5 * g, 27, 0], [cube([x0, 25, -2 * g], [3 * g, 3, 4 * g], skin)]));
    const ax = s > 0 ? 5.3 * g : -5.3 * g - 3 * g;
    B.push(bone('arm_' + L, 'shoulder_' + L, [s * 6.8 * g, 26, 0], [cube([ax, 19.5, -1.5 * g], [3 * g, 6.5, 3 * g], skin), cube([ax - 0.05, 21, -1.55 * g], [3 * g + 0.1, 0.5, 3 * g + 0.1], 'ridge')]));
    B.push(bone('forearm_' + L, 'arm_' + L, [s * 6.8 * g, 19.5, 0], [cube([ax, 13.5, -1.5 * g], [3 * g, 6, 3 * g], k.gauntlets ? 'ice' : skin)]));
    B.push(bone('hand_' + L, 'forearm_' + L, [s * 6.8 * g, 13.5, 0], [cube([ax, 11.5, -1.5 * g], [3 * g, 2, 3 * g], skin)]));
    B.push(bone('fingers_' + L, 'hand_' + L, [s * 6.8 * g, 11.5, 0], [cube([ax + 0.2, 9, -1.3 * g], [3 * g - 0.4, 2.5, 2.6 * g], skin)]));
    const lx = s > 0 ? 0.6 : -3.6;
    B.push(bone('thigh_' + L, 'hips', [s * 2, 15, 0], [cube([lx, 8, -1.5], [3, 7.5, 3], k.robe ? 'robe' : 'fur')]));
    B.push(bone('shin_' + L, 'thigh_' + L, [s * 2, 8, 0], [cube([lx, 1, -1.5], [3, 7, 3], skin)]));
    B.push(bone('foot_' + L, 'shin_' + L, [s * 2, 1, 0], [cube([lx, 0, -3], [3, 1, 4.5], 'leather')]));
  }
  B.push(bone('weapon_r', 'hand_r', [-6.8 * g, 12.5, 0], k.weapon || []));
  B.push(bone('crystals', 'chest', [0, 27, 2], k.crystals || [cube([-6 * g, 28, -1], [2, 3, 2], 'ice'), cube([4 * g, 28, -1], [2, 3, 2], 'ice'), cube([-1, 24, 3 * g], [2, 4, 1.5], 'ice')]));
  B.push(bone('mantle', 'chest', [0, 28, 2], k.mantle || [cube([-5.5 * g, 21, 3 * g], [11 * g, 7.5, 1], 'fur'), cube([-5.6 * g, 27.5, -3.2 * g], [11.2 * g, 1.2, 6.4 * g], 'fur')]));
  if (k.extra) k.extra(B);
  return B;
}

function hound() {
  const B = [];
  B.push(bone('root', null, [0, 0, 0]));
  B.push(bone('body', 'root', [0, 10, 0], [cube([-3, 8.5, -4], [6, 5, 10], 'skin'), cube([-3.1, 12.5, -4], [6.2, 1, 10], 'fur'), cube([-0.5, 13.5, -3], [1, 1.5, 6], 'ice')]));
  B.push(bone('chest', 'body', [0, 11, -4], [cube([-3.5, 8, -7], [7, 6.5, 4], 'fur')]));
  B.push(bone('neck', 'chest', [0, 13, -7], [cube([-1.5, 11.5, -9], [3, 3, 3], 'skin')]));
  B.push(bone('head', 'neck', [0, 14, -9], [cube([-2.5, 12, -13.5], [5, 4.5, 5], 'skin'), cube([-2, 14.5, -13.6], [1.2, 0.8, 0.3], 'eye'), cube([0.8, 14.5, -13.6], [1.2, 0.8, 0.3], 'eye'), cube([-2.2, 16.5, -10.5], [1.2, 1.6, 1], 'ice'), cube([1, 16.5, -10.5], [1.2, 1.6, 1], 'ice')]));
  B.push(bone('jaw', 'head', [0, 12.5, -11], [cube([-1.6, 11, -16], [3.2, 1.5, 4], 'skin')]));
  B.push(bone('tail', 'body', [0, 12.5, 6], [cube([-1, 11.5, 6], [2, 2, 6], 'fur')]));
  for (const [n, x, z, par] of [['fl', -2.5, -5.5, 'chest'], ['fr', 2.5, -5.5, 'chest'], ['bl', -2.5, 4.5, 'body'], ['br', 2.5, 4.5, 'body']]) {
    B.push(bone('leg_' + n, par, [x, 9, z], [cube([x - 1, 1, z - 1], [2, 8, 2], 'skin')]));
    B.push(bone('paw_' + n, 'leg_' + n, [x, 1, z], [cube([x - 1.2, 0, z - 1.8], [2.4, 1, 3], 'dark')]));
  }
  return B;
}

function ymir() {
  // a vertical arm reaching down from the rift: the palm is at the origin, the shoulder 640 px (40 blocks) up
  return [
    bone('root', null, [0, 0, 0]),
    bone('shoulder', 'root', [0, 640, 0], [cube([-26, 600, -26], [52, 40, 52], 'glacier')]),
    bone('arm', 'shoulder', [0, 600, 0], [cube([-22, 330, -22], [44, 270, 44], 'glacier'), cube([-23, 420, -23], [46, 6, 46], 'ridge')]),
    bone('forearm', 'arm', [0, 330, 0], [cube([-19, 70, -19], [38, 260, 38], 'glacier'), cube([-12, 150, -21], [24, 80, 4], 'ice')]),
    bone('hand', 'forearm', [0, 70, 0], [cube([-34, 20, -28], [68, 50, 56], 'glacier')]),
    bone('fingers', 'hand', [0, 20, 0], [cube([-34, -18, -28], [12, 38, 12], 'glacier'), cube([-17, -24, -28], [12, 44, 12], 'glacier'), cube([0, -24, -28], [12, 44, 12], 'glacier'), cube([17, -18, -28], [12, 38, 12], 'glacier')]),
  ];
}

function casket() {
  return [
    bone('root', null, [0, 0, 0]),
    bone('casket', 'root', [0, 0, 0], [cube([-12, 0, -9], [24, 18, 18], 'casket'), cube([-13, 0, -10], [26, 2, 20], 'gold'), cube([-4, 6, -9.4], [8, 6, 0.5], 'ice')]),
    bone('lid', 'casket', [0, 18, 9], [cube([-13, 18, -10], [26, 4, 20], 'gold'), cube([-3, 22, -3], [6, 4, 6], 'ice')]),
  ];
}

const S = 'ice';
const MODELS = {
  rime_hound: { bones: hound(), tint: [0.9, 1.0, 1.05], rig: 'hound' },
  jotun_raider: { bones: jotun({ weapon: [cube([-7.3, 2, -0.5], [1, 28, 1], 'metal'), cube([-7.8, 30, -0.9], [2, 4, 1.8], S)] }), tint: [1, 1, 1], rig: 'jotun' },
  frost_shaman: { bones: jotun({ robe: true, beard: 'bone', weapon: [cube([-7.3, 2, -0.5], [1, 30, 1], 'leather'), cube([-8.3, 32, -1.5], [3, 4, 3], S)], helm: [cube([-3.5, 35, -3.5], [7, 2.5, 7], 'robe'), cube([-1, 37.5, -1], [2, 3, 2], S)] }), tint: [0.85, 0.85, 1.1], rig: 'jotun' },
  ice_brute: { bones: jotun({ girth: 1.45, gauntlets: true, crystals: [cube([-9, 28, -2], [4, 5, 4], S), cube([5, 28, -2], [4, 5, 4], S), cube([-3, 22, 4.4], [6, 7, 3], S)] }), tint: [0.95, 1.05, 1.1], rig: 'jotun' },
  frost_giant_warlord: { bones: jotun({ girth: 1.15, weapon: [cube([-7.8, -2, -0.4], [2, 24, 0.8], S), cube([-9.3, 21, -0.6], [5, 1.2, 1.2], 'gold')], helm: [cube([-3.6, 35, -3.6], [7.2, 1.6, 7.2], 'metal'), cube([-7.5, 35, -0.6], [4, 1.2, 1.2], 'bone'), cube([-8.5, 36, -0.6], [1.2, 4, 1.2], 'bone'), cube([3.5, 35, -0.6], [4, 1.2, 1.2], 'bone'), cube([7.3, 36, -0.6], [1.2, 4, 1.2], 'bone')] }), tint: [0.9, 0.95, 1.05], rig: 'jotun' },
  king_laufey: { bones: jotun({ robe: true, beard: 'ice', weapon: [cube([-7.3, 0, -0.5], [1, 34, 1], 'dark'), cube([-7.8, 34, -0.9], [2, 5, 1.8], S)],
    helm: [cube([-3.4, 35, -3.4], [6.8, 1, 6.8], 'crown'), cube([-3.4, 36, -3.4], [1, 3, 1], 'crown'), cube([2.4, 36, -3.4], [1, 3, 1], 'crown'), cube([-0.5, 36, -3.6], [1, 4, 1], 'crown'), cube([-3.4, 36, 2.4], [1, 2.5, 1], 'crown'), cube([2.4, 36, 2.4], [1, 2.5, 1], 'crown')],
    mantle: [cube([-5.6, 6, 3], [11.2, 22, 1], 'robe'), cube([-5.7, 27.5, -3.3], [11.4, 1.4, 6.6], 'fur')],
    extra: B => B.push(bone('casket', 'hand_l', [6.8, 11, 0], [cube([5, 6.5, -2.2], [4, 3, 4.4], 'casket'), cube([4.8, 9.5, -2.4], [4.4, 0.8, 4.8], 'gold')])) }), tint: [0.75, 0.8, 0.95], rig: 'jotun' },
  ymir_arm: { bones: ymir(), tint: [1, 1, 1], rig: 'ymir' },
  ice_casket: { bones: casket(), tint: [1, 1, 1], rig: 'casket' },
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
  if (rig === 'jotun' || rig === 'hound') {
    const legs = rig === 'jotun' ? [['thigh_l', 1], ['thigh_r', -1]] : [['leg_fl', 1], ['leg_br', 1], ['leg_fr', -1], ['leg_bl', -1]];
    const arms = rig === 'jotun' ? [['arm_l', -1], ['arm_r', 1]] : [];
    const body = rig === 'jotun' ? 'chest' : 'body';
    if (n === 'idle') {
      addRot(bones, body, [[0, Z], [len / 2, [-2, 0, 0]], [len, Z]]);
      for (const [a] of arms) addRot(bones, a, [[0, Z], [len / 2, [0, 0, a.endsWith('l') ? -3 : 3]], [len, Z]]);
    } else if (n === 'walk' || n === 'run' || n === 'charge') {
      const amp = n === 'walk' ? 24 : 40;
      for (const [l, s] of legs) addRot(bones, l, [[0, [s * amp, 0, 0]], [len / 2, [-s * amp, 0, 0]], [len, [s * amp, 0, 0]]]);
      for (const [a, s] of arms) addRot(bones, a, [[0, [s * amp * 0.6, 0, 0]], [len / 2, [-s * amp * 0.6, 0, 0]], [len, [s * amp * 0.6, 0, 0]]]);
      addRot(bones, body, [[0, [n === 'walk' ? 2 : 10, 0, 0]], [len, [n === 'walk' ? 2 : 10, 0, 0]]]);
    } else if (n === 'death') {
      addRot(bones, 'root', [[0, Z], [len * 0.6, [0, 0, 0]], [len, [rig === 'hound' ? 0 : 85, 0, rig === 'hound' ? 90 : 0]]]);
      addPos(bones, 'root', [[0, Z], [len, [0, -1, 0]]]);
    } else if (n === 'hurt') {
      addRot(bones, body, [[0, Z], [len * 0.3, [8, 0, 0]], [len, Z]]);
    } else if (n === 'kneel' || n === 'kneel_hold' || n === 'stagger') {
      addRot(bones, 'thigh_l', [[0, Z], [Math.min(0.6, len), [-80, 0, 0]], [len, [-80, 0, 0]]]);
      addRot(bones, 'shin_l', [[0, Z], [Math.min(0.6, len), [80, 0, 0]], [len, [80, 0, 0]]]);
      addRot(bones, 'thigh_r', [[0, Z], [Math.min(0.6, len), [-20, 0, 0]], [len, [-20, 0, 0]]]);
      addPos(bones, 'hips', [[0, Z], [Math.min(0.6, len), [0, -6, 0]], [len, [0, -6, 0]]]);
      addRot(bones, 'chest', [[0, Z], [Math.min(0.6, len), [n === 'stagger' ? -18 : 20, 0, 0]], [len, [n === 'stagger' ? -12 : 20, 0, 0]]]);
    } else if (n === 'charge_windup' || n === 'javelin_windup') {
      addRot(bones, 'chest', [[0, Z], [len * 0.5, [12, 0, 0]], [len, [16, 0, 0]]]);
      addRot(bones, 'arm_r', [[0, Z], [len, [n === 'javelin_windup' ? -150 : -30, 0, 0]]]);
      if (n === 'charge_windup') addRot(bones, 'thigh_r', [[0, Z], [len * 0.33, [-30, 0, 0]], [len * 0.66, [10, 0, 0]], [len, [-30, 0, 0]]]);
    } else {
      // an attack / cast: wind up, strike on the contract tick, recover
      const s = strike >= 0 ? strike : len * 0.5;
      const wind = Math.max(0.05, Math.min(s - 0.1, s * 0.6));
      const big = ['cleave', 'sword_plant', 'avalanche_slam', 'glacier_spikes', 'brazier_smash'].includes(n);
      addRot(bones, body, [[0, Z], [wind, [big ? -25 : -12, 10, 0]], [s, [big ? 30 : 18, -12, 0]], [len, Z]]);
      if (rig === 'jotun') {
        addRot(bones, 'arm_r', [[0, Z], [wind, [big ? -160 : -110, 0, 0]], [s, [big ? -20 : -60, 0, 0]], [len, Z]]);
        addRot(bones, 'arm_l', [[0, Z], [wind, [-40, 0, -10]], [s, [-10, 0, 10]], [len, Z]]);
        if (n === 'war_cry' || n === 'howl' || n === 'rally') addRot(bones, 'jaw', [[0, Z], [s, [25, 0, 0]], [len, Z]]);
      } else {
        addRot(bones, 'jaw', [[0, Z], [wind, [10, 0, 0]], [s, [30, 0, 0]], [len, Z]]);
      }
    }
  } else if (rig === 'ymir') {
    if (n === 'idle') addRot(bones, 'fingers', [[0, Z], [len / 2, [6, 0, 0]], [len, Z]]);
    else if (n === 'reach_in') addPos(bones, 'root', [[0, [0, 640, 0]], [len, Z]]);
    else if (n === 'retract') addPos(bones, 'root', [[0, Z], [len, [0, 640, 0]]]);
    else if (n === 'palm_slam') {
      addPos(bones, 'root', [[0, [0, 60, 0]], [strike * 0.7, [0, 160, 0]], [strike, Z], [len, [0, 8, 0]]]);
      addRot(bones, 'fingers', [[0, Z], [strike, [-10, 0, 0]], [len, Z]]);
    } else if (n === 'glacial_sweep') {
      addPos(bones, 'root', [[0, [0, 80, 0]], [strike, [0, 4, 0]], [len, [0, 4, 0]]]);
      addRot(bones, 'hand', [[0, Z], [strike, [0, 0, 18]], [len, [0, 0, -18]]]);
    }
  } else if (rig === 'casket') {
    addRot(bones, 'lid', [[0, Z], [len / 2, [-12, 0, 0]], [len, Z]]);
    addPos(bones, 'casket', [[0, Z], [len / 2, [0, 1.5, 0]], [len, Z]]);
  }
  for (const nb of ['neck', 'flinch']) if (bones[nb]) throw new Error(c.name + ' keys ' + nb);
  if (strike >= 0) {
    const hit = Object.values(bones).some(b => Object.values(b).some(tr => Object.keys(tr).some(k => Math.abs(+k - strike) < 1e-4)));
    if (!hit) throw new Error(model + '.' + n + ': no keyframe on its strike time ' + strike);
  }
  return out;
}

// ---------------------------------------------------------------- write
const geoDir = path.join(assets, 'geo/frost_giant'), texDir = path.join(assets, 'textures/entity/frost_giant'), animDir = path.join(assets, 'animations/frost_giant');
for (const d of [geoDir, texDir, animDir]) fs.mkdirSync(d, { recursive: true });
for (const [name, m] of Object.entries(MODELS)) {
  let minY = 1e9, maxY = -1e9;
  for (const b of m.bones) for (const c of b.cubes || []) { minY = Math.min(minY, c.origin[1]); maxY = Math.max(maxY, c.origin[1] + c.size[1]); }
  const geo = { format_version: '1.12.0', 'minecraft:geometry': [{ description: { identifier: 'geometry.' + name, texture_width: 64, texture_height: 64, visible_bounds_width: 8, visible_bounds_height: 8, visible_bounds_offset: [0, 2, 0] }, bones: m.bones }] };
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
