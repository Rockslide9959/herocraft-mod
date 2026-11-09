// v0.17.3: Andy -- three V modes (Normal / Fast / Burst), hunger drains x1.5 / x2 / x4 while regrowing instead of flat costs.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b, all] of pairs) {
    if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90));
    t = all ? t.split(a).join(b) : t.replace(a, b);
  }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/';
edit(A + 'AndyConfig.java', [
  ['\t// V toggles Normal and Fast (SPEED_BURST). SPEED_SLOW is no longer picked -- it is what a starving Andy falls back to.\n\tpublic static final int SPEED_SLOW = 0;\n\tpublic static final int SPEED_NORMAL = 1;\n\tpublic static final int SPEED_BURST = 2;',
   '\t// V cycles Normal, Fast and Burst. SPEED_SLOW is never picked -- it is what a starving Andy (empty hunger bar) falls back to.\n\tpublic static final int SPEED_SLOW = 0;\n\tpublic static final int SPEED_NORMAL = 1;\n\tpublic static final int SPEED_FAST = 2;\n\tpublic static final int SPEED_BURST = 3;\n\tpublic static final int SPEEDS = 4;'],
  ['public static final int[] REGROW_DELAY = { 100, 50, 14 };', 'public static final int[] REGROW_DELAY = { 100, 50, 14, 6 };'],
  ['public static final int[] REGROW_TICKS = { 238, 170, 68 };', 'public static final int[] REGROW_TICKS = { 238, 170, 68, 34 };'],
  ['\t/** Hunger (food points) a regrown part costs; the body costs one more. */\n\tpublic static final int[] REGROW_FOOD = { 1, 2, 4 };\n\t/** Fast regeneration also burns hunger every tick a part is growing back (food exhaustion; 4 = one food point). */\n\tpublic static final float FAST_EXHAUSTION_PER_TICK = 0.1f;',
   '\t/**\n\t * v0.17.3 (user: "instead of making it drain flat hunger make your hunger drain quicker while you are regrowing your limbs,\n\t * regrowth on slow mode makes you lose hunger 50% faster, regrowth of fast mode makes you lose hunger twice as fast ... a burst\n\t * speed ... makes you lose hunger 4 times as fast"): no flat cost per part any more. While anything is growing back, every bit\n\t * of hunger he spends (running, jumping, fighting, healing, and the regrowth\'s own upkeep below) is multiplied by this, per speed.\n\t */\n\tpublic static final float[] HUNGER_RATE = { 1f, 1.5f, 2f, 4f };\n\t/** The regrowth\'s own upkeep while standing still: food exhaustion a tick before {@link #HUNGER_RATE} (4 = one food point). */\n\tpublic static final float REGROW_UPKEEP = 0.02f;'],
  ['public static final int[] STAGE_DELAY = { 28, 20, 8 };', 'public static final int[] STAGE_DELAY = { 28, 20, 8, 4 };'],
  ['public static final int[] STAGE_ARM_LEAD = { 22, 16, 7 };', 'public static final int[] STAGE_ARM_LEAD = { 22, 16, 7, 4 };'],
  ['public static final int[] STAGE_LIMB = { 154, 110, 44 };', 'public static final int[] STAGE_LIMB = { 154, 110, 44, 22 };'],
  ['public static final int[] STAGE_LEG_GAP = { 28, 20, 8 };', 'public static final int[] STAGE_LEG_GAP = { 28, 20, 8, 4 };'],
  ['return REGROW_TICKS[Math.floorMod(speed, 3)]', 'return REGROW_TICKS[Math.floorMod(speed, SPEEDS)]'],
]);
edit(A + 'data/AndyState.java', [
  ['this.regenSpeed = Math.floorMod(regenSpeed, 3);', 'this.regenSpeed = Math.floorMod(regenSpeed, AndyConfig.SPEEDS);'],
]);
edit(A + 'AndyMod.java', [
  ['.then(Commands.literal("fast").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_BURST))));',
   '.then(Commands.literal("fast").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_FAST)))\n\t\t\t\t\t\t.then(Commands.literal("burst").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_BURST))));'],
]);
edit(A + 'Andy.java', [
  ['n.regenSpeed = n.regenSpeed == AndyConfig.SPEED_BURST ? AndyConfig.SPEED_NORMAL : AndyConfig.SPEED_BURST; // V: Normal <-> Fast',
   'n.regenSpeed = n.regenSpeed >= AndyConfig.SPEED_BURST ? AndyConfig.SPEED_NORMAL : n.regenSpeed + 1; // V: Normal -> Fast -> Burst'],
  ['String[] names = { "slow", "normal", "burst" };', 'String[] names = { "slow", "normal", "fast", "burst" };'],
  ['n.regenSpeed = Math.floorMod(speed, 3);', 'n.regenSpeed = Math.floorMod(speed, AndyConfig.SPEEDS);'],
  ['\t\t// Fast regeneration eats hunger the whole time something is growing back\n\t\tif (s.regenSpeed == AndyConfig.SPEED_BURST && !player.getAbilities().instabuild && regrowingAny(s, now)) {\n\t\t\tplayer.causeFoodExhaustion(AndyConfig.FAST_EXHAUSTION_PER_TICK);\n\t\t}',
   '\t\t// growing back has an upkeep of its own (multiplied, like all his hunger, by the speed\'s rate: AndyHungerMixin)\n\t\tif (!player.getAbilities().instabuild && regrowingAny(s, now)) {\n\t\t\tplayer.causeFoodExhaustion(AndyConfig.REGROW_UPKEEP);\n\t\t}'],
  ['\t\t\t\t\tint speed = starving(player, cur, i) ? AndyConfig.SPEED_SLOW : cur.regenSpeed;\n\t\t\t\t\tpayFood(player, cur, i);\n',
   '\t\t\t\t\tint speed = starving(player) ? AndyConfig.SPEED_SLOW : cur.regenSpeed;\n'],
  ['\t\tif (!shattered && !player.getAbilities().instabuild) {\n\t\t\t// it costs what the five parts would (the Undead shatter is free); short of food it all goes at the slow speed\n\t\t\tint cost = 0;\n\t\t\tfor (int i = AndyState.BODY; i < AndyState.PARTS; i++) {\n\t\t\t\tcost += foodCost(n, i);\n\t\t\t}\n\t\t\tvar food = player.getFoodData();\n\t\t\tif (food.getFoodLevel() < cost) {\n\t\t\t\tspeed = AndyConfig.SPEED_SLOW;\n\t\t\t}\n\t\t\tfood.setFoodLevel(Math.max(0, food.getFoodLevel() - cost));\n\t\t\tfood.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));\n\t\t}',
   '\t\tif (!shattered && starving(player)) {\n\t\t\tspeed = AndyConfig.SPEED_SLOW; // an empty hunger bar: it all goes at the slow speed (the Undead shatter never starves)\n\t\t}'],
  ['int speed = starving(player, s, part) ? AndyConfig.SPEED_SLOW : s.regenSpeed;\n\t\tfloat delay', 'int speed = starving(player) ? AndyConfig.SPEED_SLOW : s.regenSpeed;\n\t\tfloat delay'],
  ['\tprivate static int foodCost(AndyState s, int part) {\n\t\treturn AndyConfig.REGROW_FOOD[s.regenSpeed] + (part == AndyState.BODY ? 1 : 0);\n\t}\n\n\t/** Not enough food for this speed: the part still regrows, at the slow speed, for whatever food is left. */\n\tprivate static boolean starving(ServerPlayer player, AndyState s, int part) {\n\t\treturn !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() < foodCost(s, part);\n\t}\n\n\tprivate static void payFood(ServerPlayer player, AndyState s, int part) {\n\t\tif (player.getAbilities().instabuild) {\n\t\t\treturn;\n\t\t}\n\t\tvar food = player.getFoodData();\n\t\tfood.setFoodLevel(Math.max(0, food.getFoodLevel() - foodCost(s, part)));\n\t\tfood.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));\n\t}',
   '\t/** An empty hunger bar: parts still regrow, at the slow speed. */\n\tprivate static boolean starving(ServerPlayer player) {\n\t\treturn !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= 0;\n\t}\n\n\t/**\n\t * v0.17.3: how much faster he gets hungry right now -- {@link AndyConfig#HUNGER_RATE} for his speed while anything is growing\n\t * back, otherwise 1. AndyHungerMixin multiplies all his food exhaustion by it.\n\t */\n\tpublic static float hungerRate(Player player) {\n\t\tAndyState s = peek(player);\n\t\tif (s == null || !s.hasPower || !regrowingAny(s, player.level().getGameTime())) {\n\t\t\treturn 1f;\n\t\t}\n\t\treturn AndyConfig.HUNGER_RATE[Math.floorMod(s.regenSpeed, AndyConfig.SPEEDS)];\n\t}'],
]);
// the mixin
fs.writeFileSync('src/main/java/com/projecthero/mod/mixin/AndyHungerMixin.java', `package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.andy.Andy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 (user: "make your hunger drain quicker while you are regrowing your limbs"): while one of Andy's parts is growing
 * back, all the food exhaustion he takes -- running, jumping, fighting, healing, the regrowth's own upkeep -- is multiplied by
 * his regeneration speed's rate (Normal x1.5, Fast x2, Burst x4; {@link com.projecthero.mod.andy.AndyConfig#HUNGER_RATE}).
 */
@Mixin(Player.class)
public abstract class AndyHungerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float projecthero$andyRegrowthHunger(float exhaustion) {
		if (exhaustion <= 0f || !((Object) this instanceof ServerPlayer sp)) {
			return exhaustion;
		}
		return exhaustion * Andy.hungerRate(sp);
	}
}
`);
edit('src/main/resources/projecthero.mixins.json', [
  ['"SpeedsterHungerMixin",', '"SpeedsterHungerMixin",\n\t\t"AndyHungerMixin",'],
]);
edit('src/client/java/com/projecthero/mod/client/andy/AndyHud.java', [
  ['private static final String[] SPEEDS = { "slow", "normal", "burst" };', 'private static final String[] SPEEDS = { "slow", "normal", "fast", "burst" };'],
  ['SPEEDS[Math.max(0, Math.min(2, s.regenSpeed))]', 'SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, s.regenSpeed))]'],
]);
// lang
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
let j = JSON.parse(t);
const set = (k, v) => { const old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]); if (!(k in j) || !t.includes(old)) throw new Error('raw ' + k); t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(v)); j[k] = v; };
const add = (after, k, v) => { if (k in j) throw new Error('dup ' + k); const old = JSON.stringify(after) + ': ' + JSON.stringify(j[after]) + ','; if (!t.includes(old)) throw new Error('anchor ' + after); t = t.replace(old, old + '\n  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ','); j[k] = v; };
set('projecthero.andy.speed.burst', 'Burst');
add('projecthero.andy.speed.normal', 'projecthero.andy.speed.fast', 'Fast');
set('projecthero.andy.ability.regen_speed', 'Regeneration Speed');
set('projecthero.andy.ability.regen_speed.desc', 'Tap to cycle Normal, Fast and Burst. While a part grows back you get hungry faster: Normal takes about 8.5s a limb and you lose hunger 50% faster, Fast about 3.4s and twice as fast, Burst about 1.7s and four times as fast.');
const g = j['projecthero.guide.andy.regen.body'];
const gi = g.indexOf('Each regrown part costs hunger');
if (gi < 0) throw new Error('guide anchor');
set('projecthero.guide.andy.regen.body', g.slice(0, gi) + 'While anything is growing back you get hungry faster: everything that costs hunger (running, jumping, fighting, healing, and the regrowth itself) costs more. Tap V to cycle the speed: Normal (about 8.5 seconds a limb, hunger 50% faster), Fast (about 3.4 seconds, twice as fast) or Burst (about 1.7 seconds, four times as fast). With an empty hunger bar it still regrows, slowly.');
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
// tests
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
edit(G, [
  ['helper.assertTrue(p.getFoodData().getFoodLevel() == 20 - AndyConfig.REGROW_FOOD[1], "it cost hunger: " + p.getFoodData().getFoodLevel());',
   'helper.assertTrue(Andy.hungerRate(p) == 1.5f, "regrowing on Normal: hunger 50% faster, rate " + Andy.hungerRate(p));'],
  ['\t\tAndy.setSpeed(p, AndyConfig.SPEED_BURST);\n\t\tAndy.breakPart(p, AndyState.BODY, AndyState.KIND_BURST);', '\t\tAndy.setSpeed(p, AndyConfig.SPEED_FAST);\n\t\tAndy.breakPart(p, AndyState.BODY, AndyState.KIND_BURST);'],
  ['helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "then Fast");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and back to Normal (no Slow to pick)");',
   'helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "then Fast");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "then Burst");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and back to Normal (no Slow to pick)");'],
  ['helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "a press of V cycles the speed (there is no style to hold for any more)");',
   'helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "a press of V cycles the speed (there is no style to hold for any more)");'],
  ['helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "the release does nothing");', 'helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "the release does nothing");'],
  ['\t\tfor (int k = 0; k < 3; k++) {\n\t\t\tint speed = k;\n\t\t\thelper.runAfterDelay(start + k, () -> {\n\t\t\t\tAndy.restore(p);',
   '\t\tfor (int k = 0; k < AndyConfig.SPEEDS; k++) {\n\t\t\tint speed = k;\n\t\t\thelper.runAfterDelay(start + k, () -> {\n\t\t\t\tAndy.restore(p);'],
  ['\t\thelper.runAfterDelay(start + 4, helper::succeed);\n\t}\n\n\t// ---------------------------------------------------------------- R: finger shots',
   `\t\thelper.runAfterDelay(start + 5, helper::succeed);
\t}

\t/** v0.17.3 (user): no flat hunger per part -- while regrowing, hunger drains x1.5 / x2 / x4 (Normal / Fast / Burst), else x1. */
\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
\tpublic void regrowingMultipliesHungerBySpeed(GameTestHelper helper) {
\t\tServerPlayer p = andy(helper);
\t\thelper.assertTrue(AndyConfig.REGROW_TICKS[AndyConfig.SPEED_BURST] < AndyConfig.REGROW_TICKS[AndyConfig.SPEED_FAST], "Burst is faster than Fast");
\t\thelper.assertTrue(Andy.hungerRate(p) == 1f, "whole: normal hunger");
\t\tfloat[] want = { 1.5f, 2f, 4f };
\t\tint[] speeds = { AndyConfig.SPEED_NORMAL, AndyConfig.SPEED_FAST, AndyConfig.SPEED_BURST };
\t\tlong now = helper.getLevel().getGameTime();
\t\tAndy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
\t\tedit(p, n -> n.setTimes(AndyState.LEFT_ARM, now - 10, now - 5, now + 500));
\t\tfor (int k = 0; k < 3; k++) {
\t\t\tAndy.setSpeed(p, speeds[k]);
\t\t\thelper.assertTrue(Andy.hungerRate(p) == want[k], "speed " + speeds[k] + ": x" + want[k] + ", got " + Andy.hungerRate(p));
\t\t\tp.getFoodData().setFoodLevel(20);
\t\t\tp.getFoodData().setSaturation(0f);
\t\t\tp.getFoodData().setExhaustion(0f);
\t\t\tp.causeFoodExhaustion(1f);
\t\t\thelper.assertTrue(Math.abs(p.getFoodData().getExhaustionLevel() - want[k]) < 1.0e-4f, "the exhaustion is multiplied: " + p.getFoodData().getExhaustionLevel());
\t\t}
\t\tint food = p.getFoodData().getFoodLevel();
\t\tAndy.tick(p);
\t\thelper.assertTrue(p.getFoodData().getFoodLevel() == food, "no flat food taken any more");
\t\thelper.succeed();
\t}

\t// ---------------------------------------------------------------- R: finger shots`],
]);
console.log('ok');
