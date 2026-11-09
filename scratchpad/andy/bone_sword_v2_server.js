// v0.17.3: Andy -- Bone Sword on hold V (2 s struggle), regen speed on Shift+V, Tear Off on tap V. Run from the repo root.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/';
edit(A + 'AndyConfig.java', [
  ['\tpublic static final int BONE_SWORD_COOLDOWN = 60;\n',
   `\tpublic static final int BONE_SWORD_COOLDOWN = 60;
\t/**
\t * v0.17.3 (user: "make the bone sword hold V for 2 seconds"): V held this long draws it. From {@link #BONE_SWORD_TAP} on he
\t * reaches over and struggles with the arm (the hold is the struggle); at the end it tears off, he grips it and after
\t * {@link #BONE_SWORD_REVEAL} more it bursts, leaving the sword in his hand.
\t */
\tpublic static final int BONE_SWORD_HOLD = 40;
\t/** A V released before this is a tap: Tear Off. */
\tpublic static final int BONE_SWORD_TAP = 5;
\tpublic static final int BONE_SWORD_REVEAL = 14;

\t// ---------------------------------------------------------------- V: Tear Off
\t/**
\t * v0.17.3 (user: "put another move on regular V"): he rips off his most damaged limb (the off arm when none is hurt) and with
\t * it whatever is on him: fire, poison, wither, slowness and every other harmful effect. The limb regrows as ever.
\t */
\tpublic static final int TEAR_OFF_COOLDOWN = 160;
`],
]);
edit(A + 'data/AndyState.java', [
  ['\tpublic static final int ANIM_SWORD_COUNTER = 62;\n',
   `\tpublic static final int ANIM_SWORD_COUNTER = 62;
\t/** v0.17.3 hold V: reaching over and struggling with the off arm before it tears off (animStart: when the struggle began). */
\tpublic static final int ANIM_SWORD_STRUGGLE = 63;
\t/** v0.17.3 tap V: Tear Off -- a limb wrenched off and flung aside. */
\tpublic static final int ANIM_TEAR_OFF = 64;
`],
]);
edit(A + 'AndyAbilities.java', [
  ['\tpublic static final String REGEN = "regen_speed";\n', '\tpublic static final String REGEN = "regen_speed";\n\t/** v0.17.3 tap V. */\n\tpublic static final String TEAR_OFF = "tear_off";\n'],
]);
console.log('ok');
