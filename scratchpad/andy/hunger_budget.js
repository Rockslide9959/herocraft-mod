// v0.17.3: Andy -- a whole-body regrowth costs half / three quarters / all of the hunger bar (Normal / Fast / Burst), drained
// smoothly while each part grows. Replaces the x1.5/x2/x4 multiplier (hunger_modes.js) before it shipped.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/';
{
  const f = A + 'AndyConfig.java';
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  const a = t.indexOf('\t/**\n\t * v0.17.3 (user: "instead of making it drain flat hunger');
  const bKey = 'public static final float REGROW_UPKEEP = 0.02f;\n';
  const b = t.indexOf(bKey);
  if (a < 0 || b < 0) throw new Error('config block');
  t = t.slice(0, a) + `\t/**
\t * v0.17.3 (user: "Slow mode: regenerating your entire body should drain an amount equal to half the hunger bar, Fast mode ...
\t * 75% of the hunger bar, Burst Mode ... the full hunger bar"): food points a WHOLE body (the body and all four limbs) costs to
\t * regrow, per speed {Slow (starving), Normal, Fast, Burst}. Each part is a fifth of it, drained smoothly over that part's own
\t * regrowth (no flat cost up front). The bar is 20 points.
\t */
\tpublic static final float[] WHOLE_BODY_FOOD = { 10f, 10f, 15f, 20f };
\tpublic static final int BODY_SHARES = 5;

\t/** Food points one part costs to regrow at {@code speed}. */
\tpublic static float partFood(int speed) {
\t\treturn WHOLE_BODY_FOOD[Math.floorMod(speed, SPEEDS)] / BODY_SHARES;
\t}
` + t.slice(b + bKey.length);
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
edit(A + 'Andy.java', [
  ['\t\t// growing back has an upkeep of its own (multiplied, like all his hunger, by the speed\'s rate: AndyHungerMixin)\n\t\tif (!player.getAbilities().instabuild && regrowingAny(s, now)) {\n\t\t\tplayer.causeFoodExhaustion(AndyConfig.REGROW_UPKEEP);\n\t\t}',
   '\t\tdrainRegrowthFood(player, s, now);'],
  ['\tprivate static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();',
   '\tprivate static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();\n\t/** Food points owed by the regrowth, below one whole point (taken when it reaches one). */\n\tprivate static final Map<UUID, Float> FOOD_OWED = new ConcurrentHashMap<>();'],
  ['\t\tLAST_TICK.clear();\n', '\t\tLAST_TICK.clear();\n\t\tFOOD_OWED.clear();\n'],
  ['\t\tLAST_TICK.remove(id);\n', '\t\tLAST_TICK.remove(id);\n\t\tFOOD_OWED.remove(id);\n'],
  [`\t/**
\t * v0.17.3: how much faster he gets hungry right now -- {@link AndyConfig#HUNGER_RATE} for his speed while anything is growing
\t * back, otherwise 1. AndyHungerMixin multiplies all his food exhaustion by it.
\t */
\tpublic static float hungerRate(Player player) {
\t\tAndyState s = peek(player);
\t\tif (s == null || !s.hasPower || !regrowingAny(s, player.level().getGameTime())) {
\t\t\treturn 1f;
\t\t}
\t\treturn AndyConfig.HUNGER_RATE[Math.floorMod(s.regenSpeed, AndyConfig.SPEEDS)];
\t}`,
   `\t/**
\t * v0.17.3: food points per tick the regrowth is draining right now: each part growing back costs
\t * {@link AndyConfig#partFood} spread evenly over its own regrowth, so a whole body costs exactly
\t * {@link AndyConfig#WHOLE_BODY_FOOD} for the speed (half the bar on Normal, three quarters on Fast, all of it on Burst).
\t */
\tpublic static float regrowthFoodPerTick(AndyState s, long now) {
\t\tif (s == null || !s.hasPower) {
\t\t\treturn 0f;
\t\t}
\t\tfloat perPart = AndyConfig.partFood(s.regenSpeed);
\t\tfloat sum = 0f;
\t\tfor (int i = 0; i < AndyState.PARTS; i++) {
\t\t\tlong st = s.regrowStart(i), end = s.regrowEnd(i);
\t\t\tif (s.broken(i) && st > 0L && st <= now && now < end) {
\t\t\t\tsum += perPart / Math.max(1L, end - st);
\t\t\t}
\t\t}
\t\treturn sum;
\t}

\t/** Takes the regrowth's food: from the hunger bar itself (saturation never above it), a whole point at a time. */
\tprivate static void drainRegrowthFood(ServerPlayer player, AndyState s, long now) {
\t\tfloat rate = regrowthFoodPerTick(s, now);
\t\tif (rate <= 0f || player.getAbilities().instabuild) {
\t\t\treturn;
\t\t}
\t\tfloat owed = FOOD_OWED.getOrDefault(player.getUUID(), 0f) + rate;
\t\tint whole = (int) owed;
\t\tif (whole > 0) {
\t\t\tvar food = player.getFoodData();
\t\t\tfood.setFoodLevel(Math.max(0, food.getFoodLevel() - whole));
\t\t\tfood.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
\t\t\towed -= whole;
\t\t}
\t\tFOOD_OWED.put(player.getUUID(), owed);
\t}`],
]);
fs.unlinkSync('src/main/java/com/projecthero/mod/mixin/AndyHungerMixin.java');
edit('src/main/resources/projecthero.mixins.json', [['\n\t\t"AndyHungerMixin",', '']]);
// lang
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
let j = JSON.parse(t);
const set = (k, v) => { const old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]); if (!(k in j) || !t.includes(old)) throw new Error('raw ' + k); t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(v)); j[k] = v; };
set('projecthero.andy.ability.regen_speed.desc', 'Tap to cycle Normal, Fast and Burst. Regrowing drains hunger as it goes; growing back your whole body costs half the hunger bar on Normal (about 8.5s a limb), three quarters on Fast (about 3.4s) and the whole bar on Burst (about 1.7s).');
const g = j['projecthero.guide.andy.regen.body'];
const gi = g.indexOf('While anything is growing back you get hungry faster');
if (gi < 0) throw new Error('guide anchor');
set('projecthero.guide.andy.regen.body', g.slice(0, gi) + 'Regrowing drains your hunger bar as the part grows. Tap V to cycle the speed: Normal (about 8.5 seconds a limb; your whole body costs half the hunger bar), Fast (about 3.4 seconds; three quarters of the bar) or Burst (about 1.7 seconds; the whole bar). With an empty hunger bar it still regrows, slowly.');
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
// tests
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
{
  let t = fs.readFileSync(G, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  const r1a = 'helper.assertTrue(Andy.hungerRate(p) == 1.5f, "regrowing on Normal: hunger 50% faster, rate " + Andy.hungerRate(p));';
  if (!t.includes(r1a)) throw new Error('t1');
  t = t.replace(r1a, 'helper.assertTrue(Andy.regrowthFoodPerTick(Andy.state(p), helper.getLevel().getGameTime()) > 0f, "it drains hunger as it grows");');
  const a = t.indexOf('\t/** v0.17.3 (user): no flat hunger per part');
  const bKey = '\t// ---------------------------------------------------------------- R: finger shots';
  const b = t.indexOf(bKey);
  if (a < 0 || b < 0) throw new Error('t2');
  t = t.slice(0, a) + `\t/** v0.17.3 (user): a whole body costs half / three quarters / all of the hunger bar on Normal / Fast / Burst, drained as it grows. */
\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1200)
\tpublic void aWholeBodyCostsTheSpeedsShareOfTheHungerBar(GameTestHelper helper) {
\t\tServerPlayer p = andy(helper);
\t\thelper.assertTrue(AndyConfig.REGROW_TICKS[AndyConfig.SPEED_BURST] < AndyConfig.REGROW_TICKS[AndyConfig.SPEED_FAST], "Burst is faster than Fast");
\t\tint[] speeds = { AndyConfig.SPEED_NORMAL, AndyConfig.SPEED_FAST, AndyConfig.SPEED_BURST };
\t\tint[] want = { 10, 15, 20 };
\t\tint[] k = { 0 };
\t\tint[] startFood = { 0 };
\t\tRunnable begin = () -> {
\t\t\tAndy.restore(p);
\t\t\tAndy.setSpeed(p, speeds[k[0]]);
\t\t\tp.getFoodData().setFoodLevel(20);
\t\t\tp.getFoodData().setSaturation(20f);
\t\t\tfor (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
\t\t\t\tAndy.breakPart(p, i, AndyState.KIND_BURST);
\t\t\t}
\t\t\tstartFood[0] = p.getFoodData().getFoodLevel();
\t\t};
\t\tbegin.run();
\t\thelper.onEachTick(() -> {
\t\t\tAndy.tick(p);
\t\t\tAndyState s = Andy.state(p);
\t\t\tboolean any = false;
\t\t\tfor (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
\t\t\t\tany |= s.broken(i);
\t\t\t}
\t\t\tif (!any) {
\t\t\t\tint spent = startFood[0] - p.getFoodData().getFoodLevel();
\t\t\t\thelper.assertTrue(Math.abs(spent - want[k[0]]) <= 1, "speed " + speeds[k[0]] + ": the whole body cost " + spent + ", not " + want[k[0]]);
\t\t\t\tif (++k[0] == speeds.length) {
\t\t\t\t\thelper.succeed();
\t\t\t\t} else {
\t\t\t\t\tbegin.run();
\t\t\t\t}
\t\t\t}
\t\t});
\t}

` + t.slice(b);
  fs.writeFileSync(G, crlf ? t.replace(/\n/g, '\r\n') : t);
}
console.log('ok');
