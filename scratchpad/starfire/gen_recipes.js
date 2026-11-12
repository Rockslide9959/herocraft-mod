// v0.17.3 Starfire: the costume recipes. node scratchpad/starfire/gen_recipes.js
const fs = require('fs');
const D = 'src/main/resources/data/projecthero/recipe/';
const r = (id, pattern, key) => fs.writeFileSync(D + id + '.json', JSON.stringify({
  type: 'minecraft:crafting_shaped', category: 'equipment', pattern,
  key: Object.fromEntries(Object.entries(key).map(([k, v]) => [k, { item: v }])),
  result: { id: 'projecthero:' + id, count: 1 } }, null, 2) + '\n');
r('starfire_suit_chestplate', ['P P', 'IEI', 'PPP'], { P: 'minecraft:purple_wool', I: 'minecraft:iron_ingot', E: 'minecraft:emerald' });
r('starfire_suit_leggings', ['PIP', 'P P', 'P P'], { P: 'minecraft:purple_wool', I: 'minecraft:iron_ingot' });
r('starfire_suit_boots', ['P P', 'L L'], { P: 'minecraft:purple_wool', L: 'minecraft:leather' });
console.log('recipes written');
