// v0.17.3 (user: "remove the helmet from the undead suit since it doesnt show up anyway"): Andy's Suit is chest, legs, boots.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const I = 'src/main/java/com/projecthero/mod/andy/item/';
edit(I + 'AndyItems.java', [
  ['public static AndySuitItem SUIT_HELMET, SUIT_CHESTPLATE,', 'public static AndySuitItem SUIT_CHESTPLATE,'],
  ['\t\tSUIT_HELMET = suit("andy_suit_helmet", ArmorItem.Type.HELMET);\n', ''],
  ['\t\toutput.accept(SUIT_HELMET);\n', ''],
]);
edit(I + 'AndySuitItem.java', [['List.of(AndyItems.SUIT_HELMET, AndyItems.SUIT_CHESTPLATE,', 'List.of(AndyItems.SUIT_CHESTPLATE,']]);
edit('src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java', [
  ['AndyItems.SUIT_CHESTPLATE.getDefense() == 6 && AndyItems.SUIT_HELMET.getDefense() == 2\n\t\t\t\t&& AndyItems.SUIT_LEGGINGS.getDefense() == 5 && AndyItems.SUIT_BOOTS.getDefense() == 2, "iron protection 2/6/5/2");',
   'AndyItems.SUIT_CHESTPLATE.getDefense() == 6\n\t\t\t\t&& AndyItems.SUIT_LEGGINGS.getDefense() == 5 && AndyItems.SUIT_BOOTS.getDefense() == 2, "iron protection 6/5/2 (no helmet)");'],
  ['p.getInventory().countItem(AndyItems.SUIT_CHESTPLATE) == 1 && p.getInventory().countItem(AndyItems.SUIT_HELMET) == 1\n', 'p.getInventory().countItem(AndyItems.SUIT_CHESTPLATE) == 1\n'],
]);
for (const f of ['src/main/resources/assets/projecthero/models/item/andy_suit_helmet.json', 'src/main/resources/data/projecthero/advancement/recipes/andy_suit_helmet.json',
  'src/main/resources/data/projecthero/recipe/andy_suit_helmet.json', 'src/main/resources/assets/projecthero/textures/item/andy_suit_helmet.png']) fs.unlinkSync(f);
// the icon script no longer makes a helmet icon
edit('scratchpad/andy/andy_suit_textures.js', [["icon('andy_suit_helmet',", "if (false) icon('andy_suit_helmet',"]]);
// lang
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
const line = '  "item.projecthero.andy_suit_helmet": ' + JSON.stringify(j['item.projecthero.andy_suit_helmet']) + ',\n';
if (!t.includes(line)) throw new Error('helmet lang line');
t = t.replace(line, '');
const k = 'projecthero.guide.andy.suit.body', old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
if (!t.includes(old) || !j[k].includes('helmet, chestplate, leggings and boots')) throw new Error('guide');
t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(j[k].replace('helmet, chestplate, leggings and boots', 'chestplate, leggings and boots (no helmet: your own face and hair show)')));
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
