// v0.15.12: writes the Ultron Uprising's blockstates, models, loot tables and recipes. usage: node gen_v01512_ultron_json.js <root>
const fs = require('fs');
const path = require('path');
const root = process.argv[2];
const A = path.join(root, 'src/main/resources/assets/projecthero');
const D = path.join(root, 'src/main/resources/data/projecthero');
function w(p, o) { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 2) + '\n'); }

// the beacon (uplink): idle / active
w(path.join(A, 'blockstates/ultron_beacon.json'), { variants: {
  'active=false': { model: 'projecthero:block/ultron_beacon' }, 'active=true': { model: 'projecthero:block/ultron_beacon_active' } } });
w(path.join(A, 'models/block/ultron_beacon.json'), { parent: 'minecraft:block/cube_column',
  textures: { side: 'projecthero:block/ultron_beacon', end: 'projecthero:block/ultron_beacon_top' } });
w(path.join(A, 'models/block/ultron_beacon_active.json'), { parent: 'minecraft:block/cube_column',
  textures: { side: 'projecthero:block/ultron_beacon_active', end: 'projecthero:block/ultron_beacon_top_active' } });
w(path.join(A, 'models/item/ultron_beacon.json'), { parent: 'projecthero:block/ultron_beacon_active' });

// the core: a pedestal (the eye is the block entity renderer's)
const core = { parent: 'minecraft:block/block', textures: { particle: 'projecthero:block/ultron_core', side: 'projecthero:block/ultron_core', top: 'projecthero:block/ultron_core_top' },
  elements: [
    { from: [3, 0, 3], to: [13, 3, 13], faces: f('side', 'top', [3, 3, 13, 13]) },
    { from: [5, 3, 5], to: [11, 9, 11], faces: f('side', 'top', [5, 5, 11, 11]) },
    { from: [4, 9, 4], to: [12, 10, 12], faces: f('side', 'top', [4, 4, 12, 12]) },
  ] };
function f(side, top, uv) {
  const s = { texture: '#' + side, uv: [uv[0], 4, uv[2], 12] };
  return { north: s, south: s, east: s, west: s, up: { texture: '#' + top, uv }, down: { texture: '#' + side, uv } };
}
const variants = {};
for (const [dir, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) variants['facing=' + dir] = y ? { model: 'projecthero:block/ultron_core', y } : { model: 'projecthero:block/ultron_core' };
w(path.join(A, 'blockstates/ultron_core.json'), { variants });
w(path.join(A, 'models/block/ultron_core.json'), core);
w(path.join(A, 'models/item/ultron_core.json'), { parent: 'projecthero:block/ultron_core',
  display: { gui: { rotation: [30, 225, 0], translation: [0, 2, 0], scale: [0.9, 0.9, 0.9] } } });

// pylon parts (render-only blocks)
w(path.join(A, 'blockstates/ultron_pylon_segment.json'), { variants: { '': { model: 'projecthero:block/ultron_pylon_segment' } } });
w(path.join(A, 'models/block/ultron_pylon_segment.json'), { parent: 'minecraft:block/cube_column',
  textures: { side: 'projecthero:block/ultron_pylon_segment', end: 'projecthero:block/ultron_pylon_end' } });
w(path.join(A, 'blockstates/ultron_pylon_head.json'), { variants: { '': { model: 'projecthero:block/ultron_pylon_head' } } });
w(path.join(A, 'models/block/ultron_pylon_head.json'), { parent: 'minecraft:block/cube_column',
  textures: { side: 'projecthero:block/ultron_pylon_head', end: 'projecthero:block/ultron_pylon_end' } });

// items
for (const id of ['vibranium_plating', 'mind_stone']) w(path.join(A, 'models/item/' + id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } });
for (const id of ['ultron_drone', 'ultron_sentinel_drone', 'ultron_heavy', 'ultron_sniper', 'ultron_prime', 'ultron_sentry', 'ultron_pylon'])
  w(path.join(A, 'models/item/' + id + '_spawn_egg.json'), { parent: 'minecraft:item/template_spawn_egg' });

// loot
for (const id of ['ultron_beacon', 'ultron_core'])
  w(path.join(D, 'loot_table/blocks/' + id + '.json'), { type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:' + id }],
    conditions: [{ condition: 'minecraft:survives_explosion' }] }] });

// recipes
w(path.join(D, 'recipe/ultron_beacon.json'), { type: 'minecraft:crafting_shaped', category: 'misc',
  pattern: ['NAN', 'IMI', 'NRN'],
  key: { N: { item: 'minecraft:netherite_scrap' }, A: { item: 'projecthero:arc_reactor' }, I: { item: 'minecraft:iron_block' },
    M: { item: 'projecthero:master_mold_core' }, R: { item: 'minecraft:redstone_block' } },
  result: { id: 'projecthero:ultron_beacon', count: 1 } });
w(path.join(D, 'recipe/vibranium_plating.json'), { type: 'projecthero:vibranium_plating' });
console.log('json done');
