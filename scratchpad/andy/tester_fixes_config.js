// v0.17.3: Andy tester fixes -- numbers (spammy DPS kit), healing per speed, Dead Line variance. Run from the repo root.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const C = 'src/main/java/com/projecthero/mod/andy/AndyConfig.java';
edit(C, [
  ['\tpublic static final int KNIT_DELAY = 100;\n\tpublic static final float PART_KNIT = 1.0f;',
   `\tpublic static final int KNIT_DELAY = 100;
\tpublic static final float PART_KNIT = 1.0f;
\t/**
\t * v0.17.3 (tester: "Fast regen doesnt feel fast enough when being hurt and damaged by mobs"): the regen speed drives the
\t * healing too, per speed {starved, Normal, Fast, Burst}: how long after the last hit a hurt part starts knitting and how much
\t * HP a second it knits, and how long after the last hit his health starts coming back and how much a second (on top of
\t * vanilla's own food healing; each point healed costs {@link #HEAL_EXHAUSTION} food exhaustion). Off and empty-stomach: none.
\t */
\tpublic static final int[] KNIT_DELAY_BY_SPEED = { 200, 100, 40, 16 };
\tpublic static final float[] KNIT_PER_SECOND = { 0.5f, 1.0f, 2.5f, 5.0f };
\tpublic static final int[] HEAL_DELAY = { 120, 80, 30, 12 };
\tpublic static final float[] HEAL_PER_SECOND = { 0f, 0.5f, 1.5f, 3.0f };
\tpublic static final float HEAL_EXHAUSTION = 0.5f;`],
  ['\tpublic static final float PARTS_BULLET_DAMAGE = 7f;', '\t// v0.17.3 (tester: "have it be a spammy moveset with weak abilities (DPS)"): every hit a little lighter, the cooldowns shorter\n\tpublic static final float PARTS_BULLET_DAMAGE = 6f;'],
  ['\tpublic static final float CRIMSON_DAMAGE = 12f;\n\tpublic static final int CRIMSON_COOLDOWN = 20;', '\tpublic static final float CRIMSON_DAMAGE = 10f;\n\tpublic static final int CRIMSON_COOLDOWN = 15;'],
  ['\tpublic static final float PUNCH_DAMAGE = 20f;\n\tpublic static final int PUNCH_COOLDOWN = 20;', '\t/** Parts Bullet: Arm (tester: "Nerf arm throw, i think 20 is a bit much"). */\n\tpublic static final float PUNCH_DAMAGE = 14f;\n\tpublic static final int PUNCH_COOLDOWN = 15;'],
  ['\tpublic static final float CRESCENT_DAMAGE = 13f;', '\tpublic static final float CRESCENT_DAMAGE = 11f;'],
  ['\tpublic static final int CRESCENT_COOLDOWN = 50;', '\tpublic static final int CRESCENT_COOLDOWN = 35;'],
  ['\tpublic static final int GREAT_CRESCENT_COOLDOWN = 400;', '\tpublic static final int GREAT_CRESCENT_COOLDOWN = 300;'],
  ['\tpublic static final int LEG_LAUNCH_COOLDOWN = 60;\n\tpublic static final int HEAD_LAUNCH_COOLDOWN = 200;', '\tpublic static final int LEG_LAUNCH_COOLDOWN = 40;\n\tpublic static final int HEAD_LAUNCH_COOLDOWN = 160;'],
  ['\tpublic static final float VORTEX_DAMAGE = 16f;', '\tpublic static final float VORTEX_DAMAGE = 13f;'],
  ['\tpublic static final int VORTEX_COOLDOWN = 120;', '\tpublic static final int VORTEX_COOLDOWN = 80;'],
  ['\tpublic static final float HEAD_BULLET_DAMAGE = 12f;', '\t/** The head hits hardest of the thrown parts (tester: "the head throw makes sense for more damage"); the Head Launch too. */\n\tpublic static final float HEAD_BULLET_DAMAGE = 18f;'],
  ['\tpublic static final int HEAD_BULLET_COOLDOWN = 200;', '\tpublic static final int HEAD_BULLET_COOLDOWN = 160;'],
  ['\tpublic static final float DEAD_LINE_DAMAGE = 4f;', `\t/**
\t * v0.17.3 (tester: "Make deadline do damage less consistently"): each tick of contact rolls {@link #DEAD_LINE_MIN}..
\t * {@link #DEAD_LINE_MAX_ROLL} times this, and {@link #DEAD_LINE_MISS} of them miss outright: about 4 a hit on average, as before.
\t */
\tpublic static final float DEAD_LINE_DAMAGE = 4.7f;
\tpublic static final float DEAD_LINE_MIN = 0.5f, DEAD_LINE_MAX_ROLL = 1.5f, DEAD_LINE_MISS = 0.15f;`],
  ['\tpublic static final int DEAD_LINE_COOLDOWN = 60;', '\tpublic static final int DEAD_LINE_COOLDOWN = 50;'],
]);
console.log('ok');
