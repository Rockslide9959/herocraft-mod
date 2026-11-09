// v0.17.3 (user: "make it so that the player doesnt automatically get the undead suit when acquiring the power, it should just
// be craftable"): no grant on gaining the power; the recipes stay.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
edit('src/main/java/com/projecthero/mod/andy/Andy.java', [['\t\tcom.projecthero.mod.andy.item.AndySuitItem.giveFirstTime(player); // v0.17.3: his suit, the first time only\n', '']]);
{
  const f = 'src/main/java/com/projecthero/mod/andy/item/AndySuitItem.java';
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  const a = t.indexOf('\t/** v0.17.3: the whole suit, given once');
  const bKey = '\tpublic static final String GIVEN_TAG = "projecthero.andy_suit_given";\n';
  const b = t.indexOf(bKey);
  if (a < 0 || b < 0) throw new Error('grant block');
  t = t.slice(0, a).replace(/\n+$/, '\n') + t.slice(b + bKey.length);
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
edit('src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java', [[
`	/** v0.17.3 (user): the whole suit is given the first time he takes the power, and only then. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void andysSuitIsGivenOnce(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(p.getInventory().countItem(AndyItems.SUIT_CHESTPLATE) == 1
				&& p.getInventory().countItem(AndyItems.SUIT_LEGGINGS) == 1 && p.getInventory().countItem(AndyItems.SUIT_BOOTS) == 1, "the full set on first gaining the power");
		Andy.revoke(p);
		Andy.grant(p);
		helper.assertTrue(p.getInventory().countItem(AndyItems.SUIT_CHESTPLATE) == 1, "not a second time");
		helper.succeed();
	}`,
`	/** v0.17.3 (user): the suit is not given with the power -- it is only crafted. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void andysSuitIsCraftedNotGiven(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(p.getInventory().countItem(AndyItems.SUIT_CHESTPLATE) == 0 && p.getInventory().countItem(AndyItems.SUIT_LEGGINGS) == 0
				&& p.getInventory().countItem(AndyItems.SUIT_BOOTS) == 0, "gaining the power gives no suit");
		var recipes = helper.getLevel().getRecipeManager();
		for (String id : new String[] { "andy_suit_chestplate", "andy_suit_leggings", "andy_suit_boots" }) {
			helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("projecthero", id)).isPresent(), "craftable: " + id);
		}
		helper.succeed();
	}`]]);
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
const line = '  "message.projecthero.andy.suit_given": ' + JSON.stringify(j['message.projecthero.andy.suit_given']) + ',\n';
if (!t.includes(line)) throw new Error('given line');
t = t.replace(line, '');
const k = 'projecthero.guide.andy.suit.body', old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
const frag = 'You get the whole set the first time you take the Undead Card (or craft it: iron, bones and red dye in the iron armour shapes).';
if (!j[k].includes(frag)) throw new Error('guide frag: ' + j[k]);
t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(j[k].replace(frag, 'Craft it: iron, bones and red dye in the iron armour shapes.')));
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
