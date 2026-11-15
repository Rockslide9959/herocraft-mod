// v0.17.5 Target Dummy: every resource -- the 64x64 entity texture (burlap head, straw torso + arms with a red target,
// wooden post, plank base plate; UVs match TargetDummyModel), the 16x16 item icon, the item model, the recipe and its
// unlock advancement. Node + zlib only.
//   node scratchpad/gen_target_dummy_v0175.js [root]
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
let seed = 1775;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => c.map(v => clamp(v * f));
function canvas(w, h) {
  const px = Buffer.alloc(w * h * 4);
  return {
    w, h, px,
    set(x, y, col, a = 255) { if (x < 0 || y < 0 || x >= w || y >= h || !col) return; const i = (y * w + x) * 4; px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = a; },
    png() { return png(w, h, px); },
  };
}
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');

// ------------------------------------------------------------------------------------------------ materials
const BURLAP = [192, 164, 114], BURLAP_D = [150, 124, 82], STITCH = [58, 38, 24];
const STRAW = [224, 188, 86], STRAW_D = [176, 136, 52], STRAW_L = [246, 220, 128];
const ROPE = [128, 88, 46], ROPE_D = [92, 60, 30];
const WOOD = [138, 98, 58], WOOD_D = [104, 72, 40], WOOD_L = [164, 122, 76];
const PLANK = [160, 128, 78], PLANK_D = [118, 92, 52];
const RED = [196, 38, 38], RED_D = [150, 24, 24], WHITE = [236, 230, 214];

const burlap = (x, y) => shade((x + y) % 2 ? BURLAP : BURLAP_D.map((v, i) => (v + BURLAP[i]) / 2), 0.94 + rnd() * 0.12);
const straw = (x, y) => { const r = rnd(); const base = r < 0.22 ? STRAW_D : r > 0.85 ? STRAW_L : STRAW; return shade(base, ((x * 7 + 3) % 5 === 0) ? 0.88 : 1.0); };
const wood = (x, y) => shade(((y * 3 + x) % 7 === 0) ? WOOD_D : rnd() > 0.85 ? WOOD_L : WOOD, 0.95 + rnd() * 0.08);

// box UV painter: faces of a w x h x d box at (u, v) -- Minecraft's standard layout
function box(c, u, v, w, h, d, fn) {
  const faces = {
    top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
    east: [u, v + d, d, h], front: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h],
  };
  for (const [face, [fx, fy, fw, fh]] of Object.entries(faces)) {
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) c.set(fx + x, fy + y, fn(face, x, y, fw, fh));
  }
}

// ------------------------------------------------------------------------------------------------ entity texture
const T = canvas(64, 64);
// head: a burlap sack, stitched X eyes and a stitched smile on the front
box(T, 0, 0, 8, 8, 8, (face, x, y) => {
  if (face === 'front') {
    // button eyes (2x2, a lighter stitch in one corner)
    if (y >= 2 && y <= 3 && ((x >= 1 && x <= 2) || (x >= 5 && x <= 6))) return (y === 2 && (x === 1 || x === 5)) ? [96, 70, 48] : STITCH;
    if (y === 5 && x >= 2 && x <= 5) return x % 2 ? STITCH : shade(BURLAP_D, 0.9);
    if (y === 4 && (x === 1 || x === 6)) return STITCH;
  }
  if (face === 'top' && x >= 3 && x <= 4 && y >= 3 && y <= 4) return BURLAP_D; // gathered top
  if (face !== 'top' && face !== 'bottom' && y === 7) return shade(BURLAP_D, 0.85); // rolled hem at the neck
  return burlap(x, y);
});
// the tied-off top of the sack: burlap with a rope band
box(T, 32, 0, 3, 2, 3, (face, x, y) => (face !== 'top' && face !== 'bottom' && y === 1) ? ROPE : burlap(x, y));
// torso: straw, a red-and-white target on the front, a rope belt
box(T, 16, 16, 8, 12, 4, (face, x, y, fw) => {
  if ((face !== 'top' && face !== 'bottom') && (y === 10)) return (x % 3 === 0) ? ROPE_D : ROPE;
  if (face === 'front') {
    const dx = x - 3.5, dy = y - 4.5, r = Math.sqrt(dx * dx + dy * dy);
    if (r < 1.1) return RED;
    if (r < 2.2) return WHITE;
    if (r < 3.3) return (x + y) % 5 ? RED : RED_D;
  }
  return straw(x, y);
});
// arms: straw with a rope tie near the hands
const arm = (face, x, y) => {
  if (face !== 'top' && face !== 'bottom' && y === 8) return ROPE;
  if (face === 'bottom') return shade(STRAW_L, 0.95 + rnd() * 0.1); // bristly ends
  return straw(x, y);
};
box(T, 40, 16, 4, 11, 4, arm);
box(T, 32, 48, 4, 11, 4, arm);
// the post
box(T, 0, 16, 2, 12, 2, (face, x, y) => face === 'top' || face === 'bottom' ? WOOD_D : wood(x, y));
// the straw tuft where the torso is tied to the post
box(T, 0, 52, 6, 3, 3, (face, x, y) => (face !== 'top' && face !== 'bottom' && y === 0) ? ROPE : straw(x, y));
// the base plate: oak planks, a darker rim
box(T, 0, 32, 12, 2, 12, (face, x, y, fw, fh) => {
  if (face === 'top' || face === 'bottom') {
    if (x === 0 || y === 0 || x === fw - 1 || y === fh - 1) return PLANK_D;
    return shade(y % 4 === 3 ? PLANK_D : PLANK, 0.95 + rnd() * 0.08);
  }
  return shade(y === 0 ? PLANK : PLANK_D, 0.95 + rnd() * 0.08);
});
write(path.join(A, 'textures/entity/target_dummy.png'), T.png());

// ------------------------------------------------------------------------------------------------ item icon (16x16)
const I = canvas(16, 16);
const pal = { o: [40, 28, 18], b: BURLAP, B: BURLAP_D, k: STITCH, s: STRAW, S: STRAW_D, l: STRAW_L, r: RED, w: WHITE, p: ROPE, W: WOOD, P: PLANK, Q: PLANK_D };
const icon = [
  '......oooo......',
  '.....obbbbo.....',
  '.....okbkbo.....',
  '.....obbbbo.....',
  '.....oBkkBo.....',
  '.oooooBBBBooooo.',
  'olssSoslrlsoSssl',
  'ossspsrwrwsspsso',
  '.ooooslrrrloooo.',
  '.....osrwrso....',
  '.....opppppo....',
  '......osSso.....',
  '.......oWo......',
  '.......oWo......',
  '...ooooWWWoooo..',
  '...oPPPQPPPQPo..',
];
icon.forEach((row, y) => [...row].forEach((ch, x) => { if (pal[ch]) I.set(x, y, pal[ch]); }));
write(path.join(A, 'textures/item/target_dummy.png'), I.png());

json(path.join(A, 'models/item/target_dummy.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/target_dummy' } });

// ------------------------------------------------------------------------------------------------ recipe + unlock
json(path.join(D, 'recipe/target_dummy.json'), {
  type: 'minecraft:crafting_shaped',
  category: 'misc',
  pattern: [' W ', 'SHS', ' F '],
  key: {
    W: { tag: 'minecraft:wool' },
    S: { item: 'minecraft:stick' },
    H: { item: 'minecraft:hay_block' },
    F: { tag: 'minecraft:wooden_fences' },
  },
  result: { id: 'projecthero:target_dummy', count: 1 },
});
json(path.join(D, 'advancement/recipes/target_dummy.json'), {
  parent: 'minecraft:recipes/root',
  criteria: {
    has_ingredient: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: 'minecraft:hay_block' }] } },
    has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:target_dummy' } },
  },
  requirements: [['has_ingredient', 'has_the_recipe']],
  rewards: { recipes: ['projecthero:target_dummy'] },
});
console.log('target dummy resources written');
