// Generates every PNG the Zombie Raid needs: six entity skins and the item/block sprites.
//
// Same hand-rolled PNG encoder the earlier asset scripts in this folder use (Node's zlib plus a
// CRC32 table) -- this project has no image library and no Python, and adding a dependency for a
// dozen 16x16 sprites is not worth it.
//
// Everything here is drawn from scratch: flat palettes, a little deterministic noise, and simple
// shapes. Nothing is derived from Minecraft's own texture files.
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const ASSETS = 'src/main/resources/assets/projecthero/textures';

// ---------------------------------------------------------------- PNG encoding

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
};
function encode(w, h, data) {
  const stride = w * 4;
  const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (stride + 1)] = 0;
    data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))]);
}

// ---------------------------------------------------------------- tiny canvas

class Canvas {
  constructor(w, h) {
    this.w = w;
    this.h = h;
    this.data = Buffer.alloc(w * h * 4); // transparent
  }
  set(x, y, [r, g, b, a = 255]) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.data[i] = r; this.data[i + 1] = g; this.data[i + 2] = b; this.data[i + 3] = a;
  }
  rect(x, y, w, h, colour) {
    for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, colour);
  }
  save(file) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, encode(this.w, this.h, this.data));
    console.log('wrote ' + file);
  }
}

// Deterministic per-pixel jitter so flat fills read as cloth/flesh rather than plastic.
function noisy(base, x, y, amount, seed) {
  const n = Math.sin((x * 12.9898 + y * 78.233 + seed) * 43758.5453);
  const d = Math.round((n - Math.floor(n) - 0.5) * 2 * amount);
  return [clamp(base[0] + d), clamp(base[1] + d), clamp(base[2] + d), 255];
}
const clamp = (v) => Math.max(0, Math.min(255, v));

function fillNoisy(canvas, x, y, w, h, colour, amount, seed) {
  for (let dy = 0; dy < h; dy++) {
    for (let dx = 0; dx < w; dx++) {
      canvas.set(x + dx, y + dy, noisy(colour, x + dx, y + dy, amount, seed));
    }
  }
}

// ---------------------------------------------------------------- entity skins

// Base (non-overlay) regions of the standard 64x64 humanoid layout.
const HUMANOID_64 = {
  head: [0, 0, 32, 16],
  body: [16, 16, 24, 16],
  rightArm: [40, 16, 16, 16],
  rightLeg: [0, 16, 16, 16],
  leftLeg: [16, 48, 16, 16],
  leftArm: [32, 48, 16, 16],
};

/** Head-front face within the head region: x 8..15, y 8..15. */
function drawFace(canvas, eyes, mouth) {
  // eye sockets
  canvas.rect(9, 10, 2, 2, [12, 10, 14, 255]);
  canvas.rect(13, 10, 2, 2, [12, 10, 14, 255]);
  // glowing pupils
  canvas.set(9, 11, eyes);
  canvas.set(10, 11, eyes);
  canvas.set(13, 11, eyes);
  canvas.set(14, 11, eyes);
  // mouth
  canvas.rect(10, 13, 4, 1, mouth);
  canvas.set(11, 14, mouth);
}

function zombieSkin(file, { skin, shirt, trousers, eyes, mouth = [20, 14, 18, 255], seed = 1 }) {
  const c = new Canvas(64, 64);
  fillNoisy(c, ...HUMANOID_64.head, skin, 10, seed);
  fillNoisy(c, ...HUMANOID_64.body, shirt, 8, seed + 1);
  fillNoisy(c, ...HUMANOID_64.rightArm, skin, 10, seed + 2);
  fillNoisy(c, ...HUMANOID_64.leftArm, skin, 10, seed + 3);
  fillNoisy(c, ...HUMANOID_64.rightLeg, trousers, 8, seed + 4);
  fillNoisy(c, ...HUMANOID_64.leftLeg, trousers, 8, seed + 5);
  drawFace(c, eyes, mouth);
  c.save(path.join(ASSETS, 'entity', file));
}

function skeletonSkin(file, { bone, shade, eyes, seed = 9 }) {
  const c = new Canvas(64, 32);
  // Fill the whole sheet; the skeleton model samples only from within it and there is no overlay.
  fillNoisy(c, 0, 0, 64, 32, bone, 12, seed);
  // darker ribs across the body region for a bit of definition
  for (let y = 18; y < 30; y += 3) c.rect(16, y, 24, 1, shade);
  drawFace(c, eyes, shade);
  c.save(path.join(ASSETS, 'entity', file));
}

zombieSkin('cursed_zombie.png', {
  skin: [74, 46, 90], shirt: [42, 24, 54], trousers: [30, 18, 40],
  eyes: [190, 120, 255, 255], seed: 3,
});
zombieSkin('armoured_zombie.png', {
  skin: [86, 104, 74], shirt: [58, 58, 62], trousers: [44, 44, 48],
  eyes: [200, 210, 190, 255], seed: 11,
});
zombieSkin('acid_zombie.png', {
  skin: [110, 168, 58], shirt: [62, 104, 34], trousers: [46, 76, 28],
  eyes: [205, 255, 76, 255], seed: 17,
});
zombieSkin('juggernaut_zombie.png', {
  skin: [92, 84, 72], shirt: [48, 42, 36], trousers: [38, 34, 30],
  eyes: [255, 136, 68, 255], seed: 23,
});
zombieSkin('empowered_zombie.png', {
  skin: [62, 38, 92], shirt: [32, 18, 52], trousers: [26, 14, 44],
  eyes: [255, 214, 96, 255], seed: 29,
});
skeletonSkin('sword_skeleton.png', {
  bone: [201, 196, 180], shade: [126, 122, 110, 255], eyes: [255, 80, 80, 255], seed: 31,
});

// ---------------------------------------------------------------- item sprites

const ITEMS = path.join(ASSETS, 'item');
const BLOCKS = path.join(ASSETS, 'block');

function sprite(file, draw, dir = ITEMS) {
  const c = new Canvas(16, 16);
  draw(c);
  c.save(path.join(dir, file));
}

const OUTLINE = [18, 14, 22, 255];

// Grave Essence: a rising violet wisp.
sprite('grave_essence.png', (c) => {
  const core = [176, 112, 240, 255];
  const glow = [222, 180, 255, 255];
  const dim = [104, 60, 152, 255];
  const shape = [
    [7, 2], [8, 2],
    [6, 3], [7, 3], [8, 3], [9, 3],
    [6, 4], [7, 4], [8, 4], [9, 4],
    [5, 5], [6, 5], [7, 5], [8, 5], [9, 5], [10, 5],
    [5, 6], [6, 6], [7, 6], [8, 6], [9, 6], [10, 6],
    [5, 7], [6, 7], [7, 7], [8, 7], [9, 7], [10, 7],
    [6, 8], [7, 8], [8, 8], [9, 8],
    [6, 9], [7, 9], [8, 9], [9, 9],
    [7, 10], [8, 10],
    [7, 11], [8, 11],
    [6, 12], [7, 12], [8, 12], [9, 12],
  ];
  for (const [x, y] of shape) c.set(x, y, core);
  for (const [x, y] of [[7, 5], [8, 5], [7, 6], [8, 6]]) c.set(x, y, glow);
  for (const [x, y] of [[5, 7], [10, 7], [6, 12], [9, 12], [6, 9], [9, 9]]) c.set(x, y, dim);
});

// Corrupted Power Core: a cracked crystal with a bright centre.
sprite('corrupted_power_core.png', (c) => {
  const shell = [58, 40, 78, 255];
  const edge = [120, 92, 158, 255];
  const core = [214, 128, 255, 255];
  for (let y = 3; y <= 12; y++) {
    const half = 5 - Math.abs(y - 7.5) / 1.6;
    for (let x = 8 - half; x <= 7 + half; x++) c.set(Math.round(x), y, shell);
  }
  for (let y = 4; y <= 11; y++) {
    c.set(4 + Math.floor(Math.abs(y - 7.5) / 2), y, edge);
    c.set(11 - Math.floor(Math.abs(y - 7.5) / 2), y, edge);
  }
  c.rect(7, 6, 2, 4, core);
  c.set(6, 7, core);
  c.set(9, 8, core);
});

// Gravewalker Charm: a bone ring on a cord.
sprite('gravewalker_charm.png', (c) => {
  const cord = [92, 74, 58, 255];
  const bone = [220, 214, 198, 255];
  const shade = [148, 142, 126, 255];
  for (let y = 1; y <= 4; y++) c.set(8, y, cord);
  const ring = [
    [6, 5], [7, 5], [8, 5], [9, 5],
    [5, 6], [10, 6], [5, 7], [10, 7], [4, 8], [11, 8], [4, 9], [11, 9],
    [5, 10], [10, 10], [5, 11], [10, 11],
    [6, 12], [7, 12], [8, 12], [9, 12],
  ];
  for (const [x, y] of ring) c.set(x, y, bone);
  for (const [x, y] of [[5, 6], [10, 11], [4, 9], [11, 8]]) c.set(x, y, shade);
  c.rect(7, 7, 2, 3, shade);
});

// Undying Totem: a squat idol, greener and heavier than the vanilla totem.
sprite('undying_totem.png', (c) => {
  const wood = [76, 118, 68, 255];
  const light = [128, 176, 108, 255];
  const gold = [226, 186, 84, 255];
  c.rect(5, 2, 6, 5, wood);       // head
  c.rect(4, 7, 8, 6, wood);       // body
  c.rect(6, 13, 4, 2, wood);      // base
  c.rect(5, 3, 6, 1, light);
  c.rect(4, 8, 8, 1, light);
  c.set(6, 4, gold);
  c.set(9, 4, gold);              // eyes
  c.rect(7, 9, 2, 3, gold);       // inlay
  for (const [x, y] of [[4, 7], [11, 7], [4, 12], [11, 12], [5, 2], [10, 2]]) c.set(x, y, OUTLINE);
});

// Necrotic Blade: dark blade, violet edge, bone hilt.
sprite('necrotic_blade.png', (c) => {
  const blade = [56, 48, 70, 255];
  const edge = [178, 116, 236, 255];
  const hilt = [206, 200, 184, 255];
  const grip = [64, 50, 44, 255];
  for (let i = 0; i < 10; i++) {
    const x = 3 + i;
    const y = 12 - i;
    c.set(x, y, blade);
    c.set(x + 1, y, blade);
    c.set(x, y - 1, edge);
  }
  c.set(13, 2, edge);
  c.set(12, 2, edge);
  // guard
  c.set(3, 11, hilt); c.set(4, 12, hilt); c.set(2, 12, hilt); c.set(3, 13, hilt);
  // grip
  c.set(2, 13, grip); c.set(1, 14, grip); c.set(2, 14, grip);
});

// Gravekeeper Shield: iron-bound with a bone sigil.
sprite('gravekeeper_shield.png', (c) => {
  const face = [82, 86, 92, 255];
  const rim = [46, 48, 54, 255];
  const bone = [216, 210, 194, 255];
  for (let y = 1; y <= 14; y++) {
    const inset = y > 10 ? y - 10 : 0;
    c.rect(3 + inset, y, 10 - inset * 2, 1, face);
  }
  for (let y = 1; y <= 14; y++) {
    const inset = y > 10 ? y - 10 : 0;
    c.set(3 + inset, y, rim);
    c.set(12 - inset, y, rim);
  }
  c.rect(3, 1, 10, 1, rim);
  c.rect(7, 3, 2, 8, bone);
  c.rect(5, 5, 6, 2, bone);
});

// Heart of the Grave: a dark heart with a violet glow.
sprite('heart_of_the_grave.png', (c) => {
  const flesh = [122, 32, 54, 255];
  const deep = [70, 18, 34, 255];
  const glow = [206, 128, 255, 255];
  const shape = [
    [5, 4], [6, 4], [9, 4], [10, 4],
    [4, 5], [5, 5], [6, 5], [7, 5], [8, 5], [9, 5], [10, 5], [11, 5],
    [4, 6], [5, 6], [6, 6], [7, 6], [8, 6], [9, 6], [10, 6], [11, 6],
    [4, 7], [5, 7], [6, 7], [7, 7], [8, 7], [9, 7], [10, 7], [11, 7],
    [5, 8], [6, 8], [7, 8], [8, 8], [9, 8], [10, 8],
    [6, 9], [7, 9], [8, 9], [9, 9],
    [7, 10], [8, 10],
  ];
  for (const [x, y] of shape) c.set(x, y, flesh);
  for (const [x, y] of [[4, 7], [11, 7], [7, 10], [8, 10]]) c.set(x, y, deep);
  c.rect(6, 6, 2, 2, glow);
});

// Grave Ritual Totem: a skull lashed to a stake.
sprite('grave_ritual_totem.png', (c) => {
  const bone = [214, 208, 190, 255];
  const shade = [140, 134, 120, 255];
  const stake = [88, 68, 50, 255];
  const soul = [128, 216, 224, 255];
  for (let y = 9; y <= 15; y++) c.set(8, y, stake);
  c.set(7, 12, stake);
  c.set(9, 13, stake);
  c.rect(5, 2, 6, 6, bone);
  c.rect(6, 8, 4, 1, bone);
  c.set(6, 4, soul);
  c.set(9, 4, soul);
  c.rect(7, 6, 2, 1, shade);
  for (const [x, y] of [[5, 2], [10, 2], [5, 7], [10, 7]]) c.set(x, y, shade);
});

// Boss trophy: a mounted zombie head, plain and gilded variants.
function trophy(file, rim) {
  sprite(file, (c) => {
    const skin = [62, 38, 92, 255];
    const dark = [38, 22, 58, 255];
    const eyes = [255, 214, 96, 255];
    c.rect(3, 3, 10, 10, skin);
    c.rect(3, 3, 10, 1, rim);
    c.rect(3, 12, 10, 1, rim);
    c.rect(3, 3, 1, 10, rim);
    c.rect(12, 3, 1, 10, rim);
    c.rect(5, 6, 2, 2, dark);
    c.rect(9, 6, 2, 2, dark);
    c.set(5, 7, eyes); c.set(6, 7, eyes); c.set(9, 7, eyes); c.set(10, 7, eyes);
    c.rect(6, 10, 4, 1, dark);
  });
}
trophy('boss_trophy.png', [96, 88, 78, 255]);
trophy('final_boss_trophy.png', [230, 190, 88, 255]);

// Cursed Grave: block faces plus the inventory sprite.
function graveFace(lit) {
  return (c) => {
    fillNoisy(c, 0, 0, 16, 16, [58, 56, 64], 10, lit ? 51 : 47);
    // carved arch
    const carve = lit ? [176, 120, 232, 255] : [36, 34, 42, 255];
    c.rect(4, 4, 8, 1, carve);
    c.rect(3, 5, 1, 8, carve);
    c.rect(12, 5, 1, 8, carve);
    c.rect(4, 12, 8, 1, carve);
    // rune
    c.rect(7, 6, 2, 5, carve);
    c.rect(5, 8, 6, 1, carve);
    // chipped corners
    c.set(0, 0, [40, 38, 46, 255]);
    c.set(15, 0, [40, 38, 46, 255]);
    c.set(0, 15, [40, 38, 46, 255]);
    c.set(15, 15, [40, 38, 46, 255]);
  };
}
sprite('cursed_grave.png', graveFace(false), BLOCKS);
sprite('cursed_grave_lit.png', graveFace(true), BLOCKS);
sprite('cursed_grave_top.png', (c) => {
  fillNoisy(c, 0, 0, 16, 16, [48, 46, 54], 8, 61);
  c.rect(2, 2, 12, 12, [40, 38, 46, 255]);
  fillNoisy(c, 3, 3, 10, 10, [34, 30, 40], 6, 67);
}, BLOCKS);
