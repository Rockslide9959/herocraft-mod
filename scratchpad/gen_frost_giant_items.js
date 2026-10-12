// v0.16.1 Frost Giant Assault: the block / item resources that are NOT the creature models -- the Jotunheim Rune Stone
// (dormant + Rift Anchor textures), the Jotun Skull (an elements model built from vanilla textures), Rime Shard, the
// Casket of Ancient Winters item, the ice javelin, spawn eggs, blockstates, loot tables, recipes and the pickaxe tag.
// Procedural 16x16 textures (Node zlib, no Python). Run from the repo (or worktree) root:
//   node scratchpad/gen_frost_giant_items.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const A = path.join(root, 'src/main/resources/assets/projecthero');
const D = path.join(root, 'src/main/resources/data');

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
let seed = 77;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
function canvas(w = 16, h = 16) {
  const px = Buffer.alloc(w * h * 4);
  return {
    px, set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; },
    fill(c, noise = 0) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const n = (rnd() - 0.5) * noise; this.set(x, y, c.map(v => Math.max(0, Math.min(255, v + n)))); } },
    png() { return png(w, h, px); },
  };
}
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, typeof data === 'string' ? data : data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');

// ---------------------------------------------------------------- textures
// rune stone: dark chiseled stone with carved runes (dormant: dim blue; active: blue-white glow)
const RUNE = ['....X....X......', '...X.X...X......', '..X...X..XXX....', '.........X..X...', '....XXX..X..X...', '....X.........X.', '....XX...X...X..', '....X...X.X.X...', '.......X...X....', '..XXX...........', '..X.X....XXXX...', '..XXX....X......', '..X......XXX....', '..X......X......', '.........XXXX...', '................'];
function runeStone(active, top) {
  const c = canvas();
  c.fill([58, 62, 74], 18);
  for (let i = 0; i < 16; i++) { c.set(i, 0, [38, 40, 50]); c.set(i, 15, [38, 40, 50]); c.set(0, i, [38, 40, 50]); c.set(15, i, [38, 40, 50]); }
  if (top) {
    for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) c.set(x, y, active ? [170, 230, 255] : [70, 110, 150]);
    for (let y = 5; y < 11; y++) for (let x = 5; x < 11; x++) c.set(x, y, active ? [240, 252, 255] : [96, 150, 196]);
  } else {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (RUNE[y][x] === 'X') c.set(x, y, active ? [200, 245, 255] : [80, 130, 180]);
  }
  return c.png();
}
write(path.join(A, 'textures/block/jotunheim_rune_stone.png'), runeStone(false, false));
write(path.join(A, 'textures/block/jotunheim_rune_stone_top.png'), runeStone(false, true));
write(path.join(A, 'textures/block/jotunheim_rune_stone_active.png'), runeStone(true, false));
write(path.join(A, 'textures/block/jotunheim_rune_stone_top_active.png'), runeStone(true, true));

// rime shard: a pale cyan crystal
{
  const c = canvas();
  const shape = ['................', '..........X.....', '.........XX.....', '........XXXX....', '.......XXXXX....', '......XXXXXX....', '.....XXXXXX.....', '....XXXXXXX.....', '....XXXXXX......', '...XXXXXX.......', '...XXXXX........', '..XXXXX.........', '..XXXX..........', '..XXX...........', '..X.............', '................'];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (shape[y][x] === 'X') {
    const edge = shape[y][x - 1] !== 'X' || shape[y][x + 1] !== 'X' || shape[y - 1]?.[x] !== 'X';
    const light = x - y > -6;
    c.set(x, y, edge ? [230, 252, 255] : light ? [150, 225, 250] : [90, 170, 220]);
  }
  write(path.join(A, 'textures/item/rime_shard.png'), c.png());
}
// the Casket: an ornate glowing blue box with a gold rim
{
  const c = canvas();
  for (let y = 4; y < 14; y++) for (let x = 2; x < 14; x++) c.set(x, y, [40 + (rnd() * 20 | 0), 130 + (rnd() * 30 | 0), 220]);
  for (let x = 1; x < 15; x++) { c.set(x, 3, [214, 176, 64]); c.set(x, 4, [180, 140, 40]); c.set(x, 14, [180, 140, 40]); }
  for (let y = 3; y < 15; y++) { c.set(1, y, [214, 176, 64]); c.set(14, y, [180, 140, 40]); }
  for (let y = 7; y < 11; y++) for (let x = 6; x < 10; x++) c.set(x, y, [210, 245, 255]);
  c.set(7, 2, [214, 176, 64]); c.set(8, 2, [214, 176, 64]); c.set(7, 1, [230, 250, 255]); c.set(8, 1, [230, 250, 255]);
  write(path.join(A, 'textures/item/casket_of_ancient_winters.png'), c.png());
}
// ice javelin: a long diagonal ice spear
{
  const c = canvas();
  for (let i = 1; i < 15; i++) { c.set(i, 15 - i, i > 11 ? [235, 252, 255] : [150, 220, 250]); if (i < 12) c.set(i + 1, 15 - i, [90, 170, 220]); }
  c.set(13, 1, [255, 255, 255]); c.set(14, 0, [255, 255, 255]);
  write(path.join(A, 'textures/item/ice_javelin.png'), c.png());
}

// ---------------------------------------------------------------- models / blockstates
json(path.join(A, 'blockstates/jotunheim_rune_stone.json'), { variants: { 'active=false': { model: 'projecthero:block/jotunheim_rune_stone' }, 'active=true': { model: 'projecthero:block/jotunheim_rune_stone_active' } } });
json(path.join(A, 'models/block/jotunheim_rune_stone.json'), { parent: 'minecraft:block/cube_column', textures: { side: 'projecthero:block/jotunheim_rune_stone', end: 'projecthero:block/jotunheim_rune_stone_top' } });
json(path.join(A, 'models/block/jotunheim_rune_stone_active.json'), { parent: 'minecraft:block/cube_column', textures: { side: 'projecthero:block/jotunheim_rune_stone_active', end: 'projecthero:block/jotunheim_rune_stone_top_active' } });
json(path.join(A, 'models/item/jotunheim_rune_stone.json'), { parent: 'projecthero:block/jotunheim_rune_stone_active' });

// the Jotun Skull: a horned skull out of vanilla textures (packed ice bone, bone-block horns, redstone eyes)
const face = (t, uv) => ({ texture: t, uv: uv || [0, 0, 16, 16] });
const all = (t) => ({ north: face(t), south: face(t), east: face(t), west: face(t), up: face(t), down: face(t) });
json(path.join(A, 'models/block/jotun_skull.json'), {
  parent: 'minecraft:block/block',
  textures: { particle: 'minecraft:block/packed_ice', bone: 'minecraft:block/packed_ice', horn: 'minecraft:block/bone_block_side', eye: 'minecraft:block/redstone_block', jaw: 'minecraft:block/blue_ice' },
  elements: [
    { from: [3, 3, 3], to: [13, 11, 13], faces: all('#bone') },
    { from: [4, 0, 2], to: [12, 3, 10], faces: all('#jaw') },
    { from: [4.5, 6, 2.9], to: [7, 7.5, 3.1], faces: { north: face('#eye') } },
    { from: [9, 6, 2.9], to: [11.5, 7.5, 3.1], faces: { north: face('#eye') } },
    { from: [0, 8, 6], to: [3, 10, 8], faces: all('#horn') },
    { from: [0, 10, 6], to: [1.5, 14, 8], faces: all('#horn') },
    { from: [13, 8, 6], to: [16, 10, 8], faces: all('#horn') },
    { from: [14.5, 10, 6], to: [16, 14, 8], faces: all('#horn') },
  ],
});
json(path.join(A, 'blockstates/jotun_skull.json'), { variants: { 'facing=north': { model: 'projecthero:block/jotun_skull' }, 'facing=east': { model: 'projecthero:block/jotun_skull', y: 90 }, 'facing=south': { model: 'projecthero:block/jotun_skull', y: 180 }, 'facing=west': { model: 'projecthero:block/jotun_skull', y: 270 } } });
json(path.join(A, 'models/item/jotun_skull.json'), { parent: 'projecthero:block/jotun_skull' });
for (const it of ['rime_shard', 'casket_of_ancient_winters', 'ice_javelin']) json(path.join(A, 'models/item/' + it + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + it } });
for (const e of ['rime_hound', 'jotun_raider', 'frost_shaman', 'ice_brute', 'frost_giant_warlord', 'king_laufey']) json(path.join(A, 'models/item/' + e + '_spawn_egg.json'), { parent: 'minecraft:item/template_spawn_egg' });

// ---------------------------------------------------------------- data: loot, recipes, tags
const selfDrop = (id) => ({ type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:' + id }], conditions: [{ condition: 'minecraft:survives_explosion' }] }] });
json(path.join(D, 'projecthero/loot_table/blocks/jotunheim_rune_stone.json'), selfDrop('jotunheim_rune_stone'));
json(path.join(D, 'projecthero/loot_table/blocks/jotun_skull.json'), selfDrop('jotun_skull'));
const runeRecipe = (s) => ({ type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['BPB', 'PSP', 'CLC'], key: { B: { item: 'minecraft:blue_ice' }, P: { item: 'minecraft:packed_ice' }, S: { item: s }, L: { item: 'minecraft:lodestone' }, C: { item: 'minecraft:chiseled_stone_bricks' } }, result: { id: 'projecthero:jotunheim_rune_stone', count: 1 } });
json(path.join(D, 'projecthero/recipe/jotunheim_rune_stone.json'), runeRecipe('minecraft:heart_of_the_sea'));
json(path.join(D, 'projecthero/recipe/jotunheim_rune_stone_from_shard.json'), runeRecipe('projecthero:rime_shard'));
json(path.join(D, 'projecthero/recipe/rime_forging.json'), { type: 'projecthero:rime_forging' });
// pickaxe tag: add the rune stone (keep every existing entry)
{
  const f = path.join(D, 'minecraft/tags/block/mineable/pickaxe.json');
  const t = JSON.parse(fs.readFileSync(f, 'utf8'));
  for (const id of ['projecthero:jotunheim_rune_stone', 'projecthero:jotun_skull']) if (!t.values.includes(id)) t.values.push(id);
  let out = JSON.stringify(t, null, 2) + '\n';
  if (fs.readFileSync(f, 'utf8').includes('\r\n')) out = out.replace(/\n/g, '\r\n');
  fs.writeFileSync(f, out);
}
console.log('frost giant items/blocks written');
