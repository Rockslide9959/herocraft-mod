// v0.17.3 Saiyan Blood item texture: the serum vial recoloured -- the liquid molten gold with an orange core and a white
// glint, the glass kept. Run from the repo root.
const fs = require('fs');
const P = require('./andy/png.js');
const src = P.read('src/main/resources/assets/projecthero/textures/item/refined_super_soldier_serum.png');
const out = P.blank(src.w, src.h);
src.data.copy(out.data);
function lum(c) { return (c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11) / 255; }
for (let y = 0; y < src.h; y++) {
  for (let x = 0; x < src.w; x++) {
    const c = P.get(src, x, y);
    if (c[3] === 0) continue;
    const max = Math.max(c[0], c[1], c[2]), min = Math.min(c[0], c[1], c[2]);
    const sat = max === 0 ? 0 : (max - min) / max;
    if (sat < 0.25) continue; // glass, cork and outline stay
    const l = lum(c);
    // gold ramp: deep orange -> gold -> pale yellow
    const r = Math.round(Math.min(255, 150 + 140 * l));
    const g = Math.round(Math.min(255, 60 + 200 * l));
    const b = Math.round(Math.min(255, 10 + 90 * l * l));
    P.set(out, x, y, [r, g, b, c[3]]);
  }
}
P.write('src/main/resources/assets/projecthero/textures/item/saiyan_blood.png', out);
fs.writeFileSync('src/main/resources/assets/projecthero/models/item/saiyan_blood.json',
  '{"parent":"minecraft:item/generated","textures":{"layer0":"projecthero:item/saiyan_blood"}}');
fs.writeFileSync('src/main/resources/data/projecthero/recipe/saiyan_blood.json', JSON.stringify({
  type: 'minecraft:crafting_shaped',
  category: 'misc',
  pattern: ['IAI', 'PBP', ' D '],
  key: {
    I: { item: 'minecraft:gold_ingot' },
    A: { item: 'minecraft:golden_apple' },
    P: { item: 'minecraft:blaze_powder' },
    B: { item: 'minecraft:glass_bottle' },
    D: { item: 'minecraft:diamond' }
  },
  result: { id: 'projecthero:saiyan_blood', count: 1 }
}, null, 2) + '\n');
fs.mkdirSync('src/main/resources/data/projecthero/advancement/recipes', { recursive: true });
fs.writeFileSync('src/main/resources/data/projecthero/advancement/recipes/saiyan_blood.json', JSON.stringify({
  parent: 'minecraft:recipes/root',
  criteria: {
    has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:saiyan_blood' } },
    has_golden_apple: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: ["minecraft:golden_apple"] }] } }
  },
  requirements: [['has_the_recipe', 'has_golden_apple']],
  rewards: { recipes: ['projecthero:saiyan_blood'] }
}, null, 2) + '\n');
console.log('ok', src.w, src.h);
