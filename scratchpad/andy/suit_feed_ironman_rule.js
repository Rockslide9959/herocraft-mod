// v0.17.3 (user: "the undead suit should feed the player when they get hungry enough to eat same as the iron man armour"):
// Andy's Suit uses Iron Man's feeding rule -- every 2 s, whenever the hunger bar is below full, the best safe food goes in.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/AndyAutoEat.java';
{
  let t = fs.readFileSync(A, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  const a = t.indexOf('\t/** Every tick for an Andy');
  const b = t.lastIndexOf('}');
  if (a < 0) throw new Error('tick');
  t = t.slice(0, a) + `\t/**
\t * Every tick for an Andy wearing his suit's chestplate (from {@link Andy#tick}). v0.17.3 (user: "feed the player when they get
\t * hungry enough to eat same as the iron man armour"): Iron Man's rule -- every {@link AndyConfig#AUTO_EAT_EVERY} ticks, whenever
\t * the hunger bar is below full, the best safe food in the inventory goes in ({@link IronManAutoFeed#feedNow}: most nutrition first,
\t * never anything with an effect, raw meat, chorus fruit or golden apples; bowls and bottles come back).
\t */
\tpublic static void tick(ServerPlayer p) {
\t\tif (p.getAbilities().instabuild || p.isSpectator() || !p.isAlive()) {
\t\t\treturn;
\t\t}
\t\tlong now = p.level().getGameTime();
\t\tLong next = NEXT.get(p.getUUID());
\t\tif (next != null && now < next) {
\t\t\treturn;
\t\t}
\t\tif (p.getFoodData().getFoodLevel() >= 20) {
\t\t\treturn;
\t\t}
\t\tNEXT.put(p.getUUID(), now + AndyConfig.AUTO_EAT_EVERY);
\t\tcom.projecthero.mod.ironman.IronManAutoFeed.feedNow(p, "message.projecthero.andy.auto_feed");
\t}
}
`;
  t = t.replace(/^import net\.minecraft\.(core\.component\.DataComponents|sounds\.SoundEvents|sounds\.SoundSource|world\.food\.FoodProperties|world\.item\.ItemStack|world\.item\.Items);\n/gm, '');
  fs.writeFileSync(A, crlf ? t.replace(/\n/g, '\r\n') : t);
}
edit('src/main/java/com/projecthero/mod/andy/AndyConfig.java', [['public static final int AUTO_EAT_EVERY = 30;', 'public static final int AUTO_EAT_EVERY = 40; // Iron Man\'s 2 s']]);
edit('src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java', [
  ['helper.assertTrue(p.getInventory().getItem(2).getCount() == 1, "the bread (least filling) went first");',
   'helper.assertTrue(p.getInventory().getItem(1).getCount() == 1, "the steak (most filling, Iron Man\'s rule) went first");'],
  ['\t\tint food = p.getFoodData().getFoodLevel();\n\t\tcom.projecthero.mod.andy.AndyAutoEat.tick(p);\n\t\thelper.assertTrue(p.getFoodData().getFoodLevel() == food, "not again straight away");',
   '\t\tint food = p.getFoodData().getFoodLevel();\n\t\tcom.projecthero.mod.andy.AndyAutoEat.tick(p);\n\t\thelper.assertTrue(p.getFoodData().getFoodLevel() == food, "not again straight away");\n\t\t// like the Iron Man suit: a full bar is the only thing that stops it (19/20 still eats)\n\t\tcom.projecthero.mod.andy.AndyAutoEat.forget(p.getUUID());\n\t\tp.getFoodData().setFoodLevel(19);\n\t\tp.getFoodData().setSaturation(19f);\n\t\tcom.projecthero.mod.andy.AndyAutoEat.tick(p);\n\t\thelper.assertTrue(p.getFoodData().getFoodLevel() == 20, "at 19 the suit still feeds him");'],
]);
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
if ('message.projecthero.andy.auto_feed' in j) throw new Error('dup');
const anchor = '  "message.projecthero.andy.suit_given": ';
if (!t.includes(anchor)) throw new Error('anchor');
t = t.replace(anchor, '  "message.projecthero.andy.auto_feed": "Your suit feeds you: %s",\n' + anchor);
const k = 'projecthero.guide.andy.suit.body', old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
const frag = 'when you get hungry you eat plain food from your inventory on your own, the least filling first (never golden apples or food with effects)';
if (!j[k].includes(frag)) throw new Error('guide frag');
t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(j[k].replace(frag, 'like an Iron Man suit, whenever your hunger bar is below full it feeds you the best safe food in your inventory every 2 seconds (never golden apples, raw meat or food with effects)')));
const g = 'projecthero.guide.andy.regen.body', gold = JSON.stringify(g) + ': ' + JSON.stringify(j[g]);
const gfrag = 'you eat plain food from your inventory on your own when you get hungry';
if (j[g].includes(gfrag)) t = t.replace(gold, JSON.stringify(g) + ': ' + JSON.stringify(j[g].replace(gfrag, 'the suit feeds you from your inventory whenever your hunger bar is below full, like an Iron Man suit')));
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
