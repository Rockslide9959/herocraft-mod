// v0.17.3: Andy -- V toggles Normal / Fast regeneration (Fast drains hunger), infinite flight. Run from the repo root.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 80)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/';
edit(A + 'AndyConfig.java', [
  ['// ---------------------------------------------------------------- regeneration (Slow, Normal, Burst)',
   '// ---------------------------------------------------------------- regeneration (Normal, or Fast on V; Slow only when starving)\n\t// v0.17.3 (user: "the regen is slow but player can press V to speed it up at the cost of losing hunger a lot faster"):\n\t// V toggles Normal and Fast (SPEED_BURST). SPEED_SLOW is no longer picked -- it is what a starving Andy falls back to.'],
  ['public static final int[] REGROW_TICKS = { 238, 170, 102 };', 'public static final int[] REGROW_TICKS = { 238, 170, 68 };'],
  ['public static final int[] REGROW_FOOD = { 1, 2, 3 };', 'public static final int[] REGROW_FOOD = { 1, 2, 4 };\n\t/** Fast regeneration also burns hunger every tick a part is growing back (food exhaustion; 4 = one food point). */\n\tpublic static final float FAST_EXHAUSTION_PER_TICK = 0.1f;'],
  ['public static final int[] STAGE_DELAY = { 28, 20, 12 };', 'public static final int[] STAGE_DELAY = { 28, 20, 8 };'],
  ['public static final int[] STAGE_ARM_LEAD = { 22, 16, 10 };', 'public static final int[] STAGE_ARM_LEAD = { 22, 16, 7 };'],
  ['public static final int[] STAGE_LIMB = { 154, 110, 66 };', 'public static final int[] STAGE_LIMB = { 154, 110, 44 };'],
  ['public static final int[] STAGE_LEG_GAP = { 28, 20, 12 };', 'public static final int[] STAGE_LEG_GAP = { 28, 20, 8 };'],
  ['public static final int PROPULSION_MAX = 200;', '/** v0.17.3 (user: "remove the time limit on the flight, player should be able to fly infinitely"): no limit. */\n\tpublic static final int PROPULSION_MAX = Integer.MAX_VALUE;'],
]);
edit(A + 'AndyFlight.java', [
  ['if (now - s.holdStart > AndyConfig.PROPULSION_MAX || !p.isAlive()', 'if (!p.isAlive()'],
]);
edit(A + 'Andy.java', [
  ['n.regenSpeed = (n.regenSpeed + 1) % 3;', 'n.regenSpeed = n.regenSpeed == AndyConfig.SPEED_BURST ? AndyConfig.SPEED_NORMAL : AndyConfig.SPEED_BURST; // V: Normal <-> Fast'],
  ['\t\tfor (int i = 0; i < AndyState.PARTS; i++) {\n\t\t\tAndyState cur = n != null ? n : s;\n\t\t\tif (!cur.broken(i)) {',
   '\t\t// Fast regeneration eats hunger the whole time something is growing back\n\t\tif (s.regenSpeed == AndyConfig.SPEED_BURST && !player.getAbilities().instabuild && regrowingAny(s, now)) {\n\t\t\tplayer.causeFoodExhaustion(AndyConfig.FAST_EXHAUSTION_PER_TICK);\n\t\t}\n\t\tfor (int i = 0; i < AndyState.PARTS; i++) {\n\t\t\tAndyState cur = n != null ? n : s;\n\t\t\tif (!cur.broken(i)) {'],
  ['\tpublic static void setSpeed(ServerPlayer player, int speed) {', '\tstatic boolean regrowingAny(AndyState s, long now) {\n\t\tfor (int i = 0; i < AndyState.PARTS; i++) {\n\t\t\tif (s.broken(i) && s.regrowStart(i) > 0L && s.regrowStart(i) <= now && now < s.regrowEnd(i)) {\n\t\t\t\treturn true;\n\t\t\t}\n\t\t}\n\t\treturn false;\n\t}\n\n\tpublic static void setSpeed(ServerPlayer player, int speed) {'],
]);
edit(A + 'AndyMod.java', [
  ['.then(Commands.literal("slow").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_SLOW)))\n', ''],
  ['.then(Commands.literal("burst").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_BURST))));', '.then(Commands.literal("fast").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_BURST))));'],
]);
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
const set = (k, v) => { if (!(k in j)) throw new Error('no key ' + k); const old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]); if (!t.includes(old)) throw new Error('raw ' + k); t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(v)); };
set('projecthero.andy.speed.burst', 'Fast');
set('projecthero.andy.ability.regen_speed', 'Fast Regeneration');
set('projecthero.andy.ability.regen_speed.desc', 'Toggle Fast regeneration on and off. Normal: a part waits 2.5s, then takes about 8.5s to grow back, and costs 2 hunger. Fast: under a second, then about 3.4s, costs 4 hunger and keeps draining your hunger the whole time a part is growing back.');
const fl = j['projecthero.andy.ability.blood_propulsion.desc'];
if (!fl.includes(' (up to 10s)')) throw new Error('flight text');
set('projecthero.andy.ability.blood_propulsion.desc', fl.replace(' (up to 10s)', ' for as long as you hold it'));
const g = j['projecthero.guide.andy.regen.body'];
const gOld = 'Tap V to set the speed: Slow, Normal or Burst (Slow and Burst stretch or squeeze the whole regrowth). With too little food it still regrows, at the slow speed.';
if (!g.includes(gOld)) throw new Error('guide text');
set('projecthero.guide.andy.regen.body', g.replace(gOld, 'Tap V to switch Fast regeneration on or off: Fast squeezes the whole regrowth to about 3.4 seconds, but costs more hunger and keeps draining it the whole time a part is growing back. With too little food it still regrows, slowly.'));
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
edit(G, [
  ['public void regenSpeedCyclesSlowNormalBurst(GameTestHelper helper) {\n\t\tServerPlayer p = andy(helper);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "starts on Normal");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "then Burst");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_SLOW, "then Slow");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and round");',
   'public void vTogglesFastRegeneration(GameTestHelper helper) {\n\t\tServerPlayer p = andy(helper);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "starts on Normal");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "then Fast");\n\t\tAndy.cycleSpeed(p);\n\t\thelper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and back to Normal (no Slow to pick)");'],
]);
console.log('ok');
