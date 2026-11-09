// v0.17.3: Andy's Suit -- item models, recipes, recipe advancements, lang. Run from the repo root.
const fs = require('fs');
const A = 'src/main/resources/assets/projecthero/', D = 'src/main/resources/data/projecthero/';
const pieces = {
  helmet: ['IRI', 'B B'],
  chestplate: ['I I', 'BRB', 'III'],
  leggings: ['IRI', 'B B', 'I I'],
  boots: ['B B', 'IRI'],
};
for (const [p, pattern] of Object.entries(pieces)) {
  const id = 'andy_suit_' + p;
  fs.writeFileSync(A + 'models/item/' + id + '.json', JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } }, null, 2) + '\n');
  fs.writeFileSync(D + 'recipe/' + id + '.json', JSON.stringify({
    type: 'minecraft:crafting_shaped', category: 'equipment', pattern,
    key: { I: { item: 'minecraft:iron_ingot' }, B: { item: 'minecraft:bone' }, R: { item: 'minecraft:red_dye' } },
    result: { id: 'projecthero:' + id, count: 1 },
  }, null, 2) + '\n');
  fs.writeFileSync(D + 'advancement/recipes/' + id + '.json', JSON.stringify({
    parent: 'minecraft:recipes/root',
    criteria: {
      has_bone: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: ['minecraft:bone'] }] } },
      has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:' + id } },
    },
    requirements: [['has_bone', 'has_the_recipe']],
    rewards: { recipes: ['projecthero:' + id] },
  }, null, 2) + '\n');
}
// lang
const L = A + 'lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
let j = JSON.parse(t);
const raw = (k) => JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
function add(after, k, v) {
  if (k in j) throw new Error('duplicate ' + k);
  const old = raw(after);
  if (t.split(old + ',\n').length !== 2) throw new Error('anchor ' + after);
  t = t.replace(old + ',\n', old + ',\n  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n'); j[k] = v;
}
let last = 'projecthero.guide.andy.bone_sword.body';
const adds = [
  ['projecthero.guide.andy.suit', "Andy's Suit"],
  ['projecthero.guide.andy.suit.body', "His suit: helmet, chestplate, leggings and boots, with iron armour's protection. You get the whole set the first time you take the Undead Card (or craft it: iron, bones and red dye in the iron armour shapes). Worn, it mends itself, one point every 2 seconds, and while you wear the chestplate it feeds you: when you get hungry you eat plain food from your inventory on your own, the least filling first (never golden apples or food with effects). Without the chestplate you feed yourself. A piece on a part you have lost is gone with it until the part grows back."],
  ['item.projecthero.andy_suit_helmet', "Andy's Suit Helmet"],
  ['item.projecthero.andy_suit_chestplate', "Andy's Suit Chestplate"],
  ['item.projecthero.andy_suit_leggings', "Andy's Suit Leggings"],
  ['item.projecthero.andy_suit_boots', "Andy's Suit Boots"],
  ['item.projecthero.andy_suit.tooltip', 'Mends itself while worn.'],
  ['item.projecthero.andy_suit_chestplate.tooltip', 'Feeds the Undead who wears it.'],
  ['message.projecthero.andy.suit_given', "Andy's Suit has been given to you: it mends itself, and its chestplate feeds you."],
];
for (const [k, v] of adds) { add(last, k, v); last = k; }
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
