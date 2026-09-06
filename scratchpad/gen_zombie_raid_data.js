// Writes the JSON half of the Zombie Raid: item/block models, the blockstate, worldgen, loot tables,
// recipes and advancements. Plain data generation -- kept as a script so the whole set can be
// regenerated consistently rather than hand-edited file by file.
const fs = require('fs');
const path = require('path');

const ASSETS = 'src/main/resources/assets/projecthero';
const DATA = 'src/main/resources/data/projecthero';

function write(file, obj) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
  console.log('wrote ' + file);
}

// ---------------------------------------------------------------- item models

const FLAT_ITEMS = [
  'grave_essence', 'corrupted_power_core', 'gravewalker_charm', 'undying_totem',
  'heart_of_the_grave', 'grave_ritual_totem', 'boss_trophy', 'final_boss_trophy',
];
for (const id of FLAT_ITEMS) {
  write(path.join(ASSETS, 'models/item', id + '.json'), {
    parent: 'minecraft:item/generated',
    textures: { layer0: 'projecthero:item/' + id },
  });
}

// Held items use the handheld parent so they are gripped like a tool rather than shown flat.
for (const id of ['necrotic_blade', 'gravekeeper_shield']) {
  write(path.join(ASSETS, 'models/item', id + '.json'), {
    parent: 'minecraft:item/handheld',
    textures: { layer0: 'projecthero:item/' + id },
  });
}

// ---------------------------------------------------------------- cursed grave block

// A carved headstone: a box inset from the full cube, so it reads as a grave marker rather than a
// full block, with a lit variant once it has been activated.
function graveModel(lit) {
  const side = lit ? 'projecthero:block/cursed_grave_lit' : 'projecthero:block/cursed_grave';
  return {
    parent: 'minecraft:block/block',
    textures: {
      particle: 'projecthero:block/cursed_grave',
      top: 'projecthero:block/cursed_grave_top',
      side: side,
    },
    elements: [
      {
        from: [1, 0, 1],
        to: [15, 14, 15],
        faces: {
          north: { uv: [1, 2, 15, 16], texture: '#side' },
          south: { uv: [1, 2, 15, 16], texture: '#side' },
          west: { uv: [1, 2, 15, 16], texture: '#side' },
          east: { uv: [1, 2, 15, 16], texture: '#side' },
          up: { uv: [1, 1, 15, 15], texture: '#top' },
          down: { uv: [1, 1, 15, 15], texture: '#top', cullface: 'down' },
        },
      },
    ],
  };
}
write(path.join(ASSETS, 'models/block/cursed_grave.json'), graveModel(false));
write(path.join(ASSETS, 'models/block/cursed_grave_lit.json'), graveModel(true));
write(path.join(ASSETS, 'models/item/cursed_grave.json'), { parent: 'projecthero:block/cursed_grave' });

// Variant keys deliberately omit `waterlogged`: a blockstate key only has to name the properties
// that change the model, and waterlogging does not.
const graveVariants = {};
for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
  for (const lit of [false, true]) {
    const key = `facing=${facing},lit=${lit}`;
    const variant = { model: 'projecthero:block/cursed_grave' + (lit ? '_lit' : '') };
    if (y !== 0) variant.y = y;
    graveVariants[key] = variant;
  }
}
write(path.join(ASSETS, 'blockstates/cursed_grave.json'), { variants: graveVariants });

// ---------------------------------------------------------------- worldgen

write(path.join(DATA, 'worldgen/structure/graveyard.json'), {
  type: 'projecthero:graveyard',
  biomes: '#projecthero:graveyard_biomes',
  step: 'surface_structures',
  spawn_overrides: {},
});

// Rarer than any of the research sites: a Graveyard is a major progression trigger, not scenery.
write(path.join(DATA, 'worldgen/structure_set/graveyard.json'), {
  structures: [{ structure: 'projecthero:graveyard', weight: 1 }],
  placement: {
    type: 'minecraft:random_spread',
    salt: 41627320,
    spacing: 96,
    separation: 32,
  },
});

// Dry-ish, open, temperate land -- somewhere a graveyard reads as deliberately sited.
write(path.join(DATA, 'tags/worldgen/biome/graveyard_biomes.json'), {
  replace: false,
  values: [
    'minecraft:plains',
    'minecraft:sunflower_plains',
    'minecraft:meadow',
    'minecraft:forest',
    'minecraft:birch_forest',
    'minecraft:dark_forest',
    'minecraft:old_growth_birch_forest',
    'minecraft:taiga',
    'minecraft:old_growth_pine_taiga',
    'minecraft:old_growth_spruce_taiga',
    'minecraft:swamp',
    'minecraft:savanna',
    'minecraft:savanna_plateau',
  ],
});

// ---------------------------------------------------------------- loot tables

const item = (name, extra = {}) => ({ type: 'minecraft:item', name, ...extra });
const count = (min, max) => ({
  function: 'minecraft:set_count',
  count: { type: 'minecraft:uniform', min, max },
});

// Graveyard crypt chest: atmosphere and a leg-up, never raid rewards.
write(path.join(DATA, 'loot_table/chests/graveyard.json'), {
  type: 'minecraft:chest',
  pools: [
    {
      rolls: { type: 'minecraft:uniform', min: 2, max: 4 },
      entries: [
        item('minecraft:bone', { weight: 10, functions: [count(2, 6)] }),
        item('minecraft:rotten_flesh', { weight: 10, functions: [count(2, 6)] }),
        item('minecraft:soul_sand', { weight: 6, functions: [count(2, 5)] }),
        item('minecraft:gunpowder', { weight: 6, functions: [count(1, 4)] }),
        item('minecraft:skeleton_skull', { weight: 2 }),
        item('projecthero:grave_essence', { weight: 5, functions: [count(1, 3)] }),
      ],
    },
    {
      rolls: 1,
      bonus_rolls: 0.2,
      entries: [
        item('minecraft:golden_apple', { weight: 3 }),
        item('minecraft:iron_ingot', { weight: 6, functions: [count(2, 5)] }),
        item('minecraft:emerald', { weight: 4, functions: [count(1, 3)] }),
        item('minecraft:enchanted_book', { weight: 2 }),
        item('projecthero:research_note', { weight: 3 }),
      ],
    },
  ],
});

// The Cursed Grave Chest. The guaranteed Grave Essence bundle and the final boss's Corrupted Power
// Core are added in code (ZombieRaidRewards) because the core has to carry that boss's actual power;
// everything else is here so it can be retuned without recompiling.
write(path.join(DATA, 'loot_table/chests/cursed_grave_chest.json'), {
  type: 'minecraft:chest',
  pools: [
    {
      rolls: { type: 'minecraft:uniform', min: 3, max: 5 },
      entries: [
        item('minecraft:diamond', { weight: 10, functions: [count(2, 6)] }),
        item('minecraft:emerald', { weight: 10, functions: [count(4, 10)] }),
        item('minecraft:golden_apple', { weight: 8, functions: [count(1, 3)] }),
        item('minecraft:enchanted_book', { weight: 6 }),
        item('minecraft:experience_bottle', { weight: 8, functions: [count(4, 10)] }),
      ],
    },
    {
      rolls: { type: 'minecraft:uniform', min: 1, max: 2 },
      entries: [
        item('minecraft:netherite_scrap', { weight: 6, functions: [count(1, 2)] }),
        item('minecraft:ancient_debris', { weight: 2 }),
        item('minecraft:diamond_block', { weight: 2 }),
      ],
    },
    // Rare artifact pool. The empty entry is what makes the individual chances land near the design's
    // targets: one roll over these weights, most of which produces nothing.
    {
      rolls: 1,
      entries: [
        { type: 'minecraft:empty', weight: 37 },
        item('minecraft:enchanted_golden_apple', { weight: 20 }),
        item('projecthero:gravewalker_charm', { weight: 15 }),
        item('projecthero:gravekeeper_shield', { weight: 10 }),
        item('projecthero:boss_trophy', { weight: 10 }),
        item('projecthero:necrotic_blade', { weight: 8 }),
      ],
    },
    {
      rolls: 1,
      entries: [
        { type: 'minecraft:empty', weight: 97 },
        item('projecthero:undying_totem', { weight: 3 }),
      ],
    },
  ],
});

// Block drop.
write(path.join(DATA, 'loot_table/blocks/cursed_grave.json'), {
  type: 'minecraft:block',
  pools: [
    {
      rolls: 1,
      entries: [item('projecthero:cursed_grave')],
      conditions: [{ condition: 'minecraft:survives_explosion' }],
    },
  ],
});

// Entity loot. Grave Essence is dropped in code (config-driven per tier); these tables cover the
// ordinary undead drops so nothing resolves to a missing table.
function undeadTable(entries) {
  return { type: 'minecraft:entity', pools: [{ rolls: 1, entries }] };
}
const zombieDrops = [
  item('minecraft:rotten_flesh', {
    functions: [count(0, 2), { function: 'minecraft:enchanted_count_increase', enchantment: 'minecraft:looting', count: { min: 0, max: 1 } }],
  }),
];
const boneDrops = [
  item('minecraft:bone', {
    functions: [count(0, 2), { function: 'minecraft:enchanted_count_increase', enchantment: 'minecraft:looting', count: { min: 0, max: 1 } }],
  }),
];
write(path.join(DATA, 'loot_table/entities/raid_zombie.json'), undeadTable(zombieDrops));
write(path.join(DATA, 'loot_table/entities/acid_zombie.json'), undeadTable([
  item('minecraft:rotten_flesh', { weight: 6, functions: [count(0, 2)] }),
  item('minecraft:slime_ball', { weight: 4, functions: [count(0, 2)] }),
]));
write(path.join(DATA, 'loot_table/entities/juggernaut_zombie.json'), undeadTable([
  item('minecraft:rotten_flesh', { functions: [count(2, 5)] }),
]));
write(path.join(DATA, 'loot_table/entities/sword_skeleton.json'), undeadTable(boneDrops));
write(path.join(DATA, 'loot_table/entities/cursed_zombie.json'), undeadTable([
  item('minecraft:rotten_flesh', { weight: 8, functions: [count(1, 3)] }),
  item('minecraft:soul_sand', { weight: 3 }),
]));
write(path.join(DATA, 'loot_table/entities/empowered_zombie.json'), undeadTable([
  item('minecraft:rotten_flesh', { functions: [count(3, 6)] }),
]));

// ---------------------------------------------------------------- recipes

// Deliberately expensive: this is the price of skipping the raid entirely.
write(path.join(DATA, 'recipe/enchanted_golden_apple.json'), {
  type: 'minecraft:crafting_shaped',
  category: 'misc',
  pattern: ['NGN', 'GAG', 'NGN'],
  key: {
    N: { item: 'minecraft:netherite_scrap' },
    G: { item: 'minecraft:gold_block' },
    A: { item: 'minecraft:golden_apple' },
  },
  result: { id: 'minecraft:enchanted_golden_apple', count: 1 },
});

// Repeatable raids. Gated behind the Heart of the Grave, which only a first clear produces.
write(path.join(DATA, 'recipe/grave_ritual_totem.json'), {
  type: 'minecraft:crafting_shaped',
  category: 'misc',
  pattern: ['ESE', 'RHR', 'SKS'],
  key: {
    E: { item: 'projecthero:grave_essence' },
    S: { item: 'minecraft:soul_sand' },
    R: { item: 'minecraft:rotten_flesh' },
    H: { item: 'projecthero:heart_of_the_grave' },
    K: { item: 'minecraft:skeleton_skull' },
  },
  result: { id: 'projecthero:grave_ritual_totem', count: 1 },
});

// ---------------------------------------------------------------- advancements

// All code-triggered, exactly like the existing mutation advancements.
function advancement(icon, key, parent, frame = 'task', hidden = false) {
  const body = {
    display: {
      icon: { id: icon },
      title: { translate: `advancement.projecthero.gravebound.${key}.title` },
      description: { translate: `advancement.projecthero.gravebound.${key}.description` },
      frame,
      show_toast: true,
      announce_to_chat: true,
      hidden,
    },
    criteria: { code_trigger: { trigger: 'minecraft:impossible' } },
    requirements: [['code_trigger']],
  };
  if (parent) body.parent = `projecthero:gravebound/${parent}`;
  write(path.join(DATA, 'advancement/gravebound', key + '.json'), body);
}

advancement('minecraft:soul_lantern', 'cursed', null, 'task');
advancement('minecraft:enchanted_golden_apple', 'curse_broken', 'cursed', 'task');
advancement('projecthero:grave_essence', 'zombie_slayer', 'cursed', 'goal');
advancement('minecraft:totem_of_undying', 'deathless', 'zombie_slayer', 'challenge');
advancement('minecraft:iron_sword', 'one_man_army', 'zombie_slayer', 'challenge');
advancement('minecraft:shield', 'last_stand', 'zombie_slayer', 'goal');
advancement('projecthero:corrupted_power_core', 'power_breaker', 'zombie_slayer', 'challenge');
advancement('projecthero:heart_of_the_grave', 'gravewalker', 'zombie_slayer', 'challenge');
advancement('projecthero:boss_trophy', 'power_analysis', 'power_breaker', 'goal');
