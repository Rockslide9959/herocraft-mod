// v0.17.7 small world places: writes the structure / structure_set / biome-tag JSON and the themed chest loot tables.
// Run from the repo root: node scratchpad/v0177/gen_world_places.js
const fs = require('fs');
const path = require('path');
const root = 'src/main/resources/data/projecthero';
const write = (p, o) => {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, JSON.stringify(o, null, 2) + '\n');
  console.log('wrote', p);
};

// ---------------------------------------------------------------- worldgen
const places = [
  { id: 'ruined_hero_base', biomes: 'ruined_hero_base_biomes', salt: 1777101, spacing: 24, separation: 8, village: 3,
    tag: ['#projecthero:hp_surface_biomes', '#minecraft:is_jungle', 'minecraft:dark_forest', 'minecraft:swamp'] },
  { id: 'crashed_parademon_pod', biomes: 'crashed_parademon_pod_biomes', salt: 1777102, spacing: 26, separation: 8, village: 3,
    tag: ['#minecraft:is_badlands', '#minecraft:is_savanna', 'minecraft:desert', 'minecraft:plains', 'minecraft:sunflower_plains',
      'minecraft:snowy_plains', 'minecraft:meadow', 'minecraft:ice_spikes'] },
  { id: 'abandoned_workshop', biomes: 'abandoned_workshop_biomes', salt: 1777103, spacing: 22, separation: 7, village: 2,
    tag: ['#minecraft:is_forest', '#minecraft:is_taiga', 'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow',
      'minecraft:cherry_grove', 'minecraft:savanna', 'minecraft:swamp', 'minecraft:snowy_plains'] },
  { id: 'smugglers_den', biomes: 'smugglers_den_biomes', salt: 1777104, spacing: 10, separation: 3, village: 2,
    tag: ['#minecraft:is_hill', '#minecraft:is_taiga', '#minecraft:is_badlands', '#minecraft:is_jungle', '#minecraft:is_forest',
      '#minecraft:is_savanna', 'minecraft:stony_shore', 'minecraft:meadow', 'minecraft:grove', 'minecraft:cherry_grove',
      'minecraft:snowy_slopes', 'minecraft:plains'] },
  { id: 'tech_meteor', biomes: 'finds_land_biomes', salt: 1777105, spacing: 20, separation: 6, village: 0, tag: null },
];
for (const p of places) {
  write(`${root}/worldgen/structure/${p.id}.json`, {
    type: `projecthero:${p.id}`,
    biomes: `#projecthero:${p.biomes}`,
    step: 'top_layer_modification',
    spawn_overrides: {},
  });
  const placement = { type: 'minecraft:random_spread', salt: p.salt, spacing: p.spacing, separation: p.separation };
  if (p.village) placement.exclusion_zone = { other_set: 'minecraft:villages', chunk_count: p.village };
  write(`${root}/worldgen/structure_set/${p.id}.json`, { structures: [{ structure: `projecthero:${p.id}`, weight: 1 }], placement });
  if (p.tag) write(`${root}/tags/worldgen/biome/${p.biomes}.json`, { values: p.tag });
}

// ---------------------------------------------------------------- loot
const item = (name, min, max, weight = 1, extra = []) => {
  const e = { type: 'minecraft:item', name, weight };
  const fns = [...extra];
  if (min !== undefined && !(min === 1 && max === 1)) fns.unshift({ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } });
  if (fns.length) e.functions = fns;
  return e;
};
const pool = (rolls, entries, chance) => {
  const p = { rolls, entries };
  if (chance !== undefined) p.conditions = [{ condition: 'minecraft:random_chance', chance }];
  return p;
};
const locator = (chance) => pool(1, [item('projecthero:locator', 1, 1, 1, [{ function: 'minecraft:set_components',
  components: { 'projecthero:locator_target': { tier: 2 }, 'projecthero:hero_rarity': 'uncommon' } }])], chance);
const range = (a, b) => ({ type: 'minecraft:uniform', min: a, max: b });
const dyed = (name, rgb) => item(name, 1, 1, 3, [{ function: 'minecraft:set_components', components: { 'minecraft:dyed_color': { rgb } } },
  { function: 'minecraft:set_damage', damage: range(0.35, 0.8) }]);
const table = (id, pools) => write(`${root}/loot_table/chests/finds/${id}.json`,
  { type: 'minecraft:chest', pools, random_sequence: `projecthero:chests/finds/${id}` });

table('ruined_hero_base', [
  pool(1, [item('projecthero:salvaged_tech', 2, 5)]),
  pool(1, [dyed('minecraft:leather_helmet', 2902964), dyed('minecraft:leather_chestplate', 11545130), dyed('minecraft:leather_boots', 3116858),
    item('minecraft:shield', 1, 1, 2, [{ function: 'minecraft:set_damage', damage: range(0.3, 0.7) }])]),
  pool(range(1, 2), [item('projecthero:med_kit', 1, 2, 5), item('projecthero:suit_repair_kit', 1, 1, 3), item('projecthero:energy_cell', 1, 1, 2)]),
  pool(range(1, 3), [item('minecraft:paper', 2, 6, 6), item('minecraft:book', 1, 2, 4), item('minecraft:iron_ingot', 1, 3, 5),
    item('minecraft:experience_bottle', 1, 3, 3), item('minecraft:golden_carrot', 1, 3, 3)]),
  locator(0.5),
]);
table('crashed_parademon_pod', [
  pool(1, [item('projecthero:salvaged_tech', 3, 6)]),
  pool(1, [item('projecthero:hero_alloy_scrap', 1, 3)], 0.4),
  pool(range(1, 2), [item('projecthero:energy_cell', 1, 2, 5), item('projecthero:cooldown_stim', 1, 1, 1), item('projecthero:med_kit', 1, 1, 3)]),
  pool(range(2, 3), [item('minecraft:redstone', 3, 8, 6), item('minecraft:blaze_powder', 1, 3, 3), item('minecraft:magma_cream', 1, 2, 3),
    item('minecraft:gunpowder', 2, 5, 4), item('minecraft:iron_nugget', 4, 10, 5), item('minecraft:gold_nugget', 3, 8, 3)]),
  locator(0.4),
]);
table('abandoned_workshop', [
  pool(1, [item('projecthero:salvaged_tech', 4, 8)]),
  pool(1, [item('projecthero:hero_alloy_scrap', 1, 2)], 0.25),
  pool(1, [item('projecthero:suit_repair_kit', 1, 1)], 0.5),
  pool(range(2, 4), [item('minecraft:copper_ingot', 2, 6, 6), item('minecraft:iron_nugget', 4, 12, 6), item('minecraft:redstone', 2, 6, 5),
    item('minecraft:gold_nugget', 2, 6, 3), item('minecraft:string', 2, 5, 3), item('minecraft:lightning_rod', 1, 1, 2),
    item('minecraft:piston', 1, 1, 2), item('minecraft:clock', 1, 1, 1)]),
  locator(0.2),
]);
table('smugglers_den', [
  pool(1, [item('minecraft:emerald', 2, 6)]),
  pool(range(1, 2), [item('projecthero:salvaged_tech', 1, 4, 5), item('projecthero:cooldown_stim', 1, 1, 1), item('projecthero:med_kit', 1, 2, 3)]),
  pool(range(2, 4), [item('minecraft:gold_ingot', 1, 3, 4), item('minecraft:gunpowder', 2, 6, 5), item('minecraft:arrow', 6, 16, 4),
    item('minecraft:sugar', 2, 6, 3), item('minecraft:leather', 2, 5, 4), item('minecraft:lead', 1, 2, 2),
    item('minecraft:spyglass', 1, 1, 1), item('minecraft:diamond', 1, 1, 1)]),
  locator(0.3),
]);
table('rooftop_stash', [
  pool(range(1, 2), [item('projecthero:med_kit', 1, 2, 5), item('projecthero:energy_cell', 1, 1, 3), item('projecthero:cooldown_stim', 1, 1, 1)]),
  pool(1, [item('projecthero:salvaged_tech', 2, 4)]),
  pool(range(2, 3), [item('minecraft:arrow', 4, 12, 5), item('minecraft:cooked_beef', 2, 4, 5), item('minecraft:spyglass', 1, 1, 2),
    item('minecraft:lead', 1, 2, 3), item('minecraft:string', 2, 6, 3), item('minecraft:ender_pearl', 1, 1, 1), item('minecraft:emerald', 1, 3, 3)]),
  locator(0.25),
]);
table('tech_meteor', [
  pool(1, [item('projecthero:salvaged_tech', 3, 7)]),
  pool(1, [item('projecthero:hero_alloy_scrap', 1, 2)], 0.5),
  pool(range(2, 3), [item('minecraft:glowstone_dust', 3, 8, 5), item('minecraft:amethyst_shard', 2, 6, 5), item('minecraft:iron_nugget', 4, 10, 5),
    item('minecraft:copper_ingot', 2, 5, 4), item('minecraft:prismarine_crystals', 1, 4, 3), item('minecraft:obsidian', 1, 2, 2)]),
]);
