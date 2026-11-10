// v0.17.3 NBC-style regeneration: AndyConfig's regeneration block rewritten (free, one speed, 10 segments, frame timing).
const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/andy/AndyConfig.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const a = t.indexOf('\t/** A part that has not been hit for this long knits back');
const bKey = '\t// ---------------------------------------------------------------- Undead\n';
const b = t.indexOf(bKey);
if (a < 0 || b < 0) throw new Error('markers');
const block = `\t/**
\t * v0.17.3 (user: regrowth free and fast like NBC Heroes' Regenerative Segmentation): one speed, so the healing has one rate
\t * too (the old Fast numbers): a hurt part starts knitting {@link #KNIT_DELAY} after the last hit at {@link #KNIT_PER_SECOND}
\t * HP a second, and his health comes back {@link #HEAL_DELAY} after the last hit at {@link #HEAL_PER_SECOND} a second (on top
\t * of vanilla's food healing). Free: no hunger.
\t */
\tpublic static final int KNIT_DELAY = 40;
\tpublic static final float KNIT_PER_SECOND = 2.5f;
\tpublic static final int HEAL_DELAY = 30;
\tpublic static final float HEAL_PER_SECOND = 1.5f;
\t/** An explosion lands this share of its damage on every part. */
\tpublic static final float BLAST_SHARE = 0.55f;

\t// ---------------------------------------------------------------- regeneration (NBC Heroes' Regenerative Segmentation)
\t/**
\t * v0.17.3 (user: "can we build our andy power the same way theyve built theirs? ... the regeneration to work the same ... at
\t * the same speed"): ten segments ({@code AndyState}: head, body, both arms down to the wrist, both legs down to the ankle,
\t * both hands, both feet), each growing one FRAME a tick from 0 (gone) to its full count, starting the tick it may, free.
\t * Frames to whole, by segment (NBC's: head 17, body 20, arm 18, legs 20, hand 12, feet 11).
\t */
\tpublic static final int[] FRAMES = { 17, 20, 18, 18, 20, 20, 12, 12, 11, 11 };
\t/**
\t * The first frame of the flesh stage, by segment: below it the segment is bone, from it to {@link #FRAMES} flesh, at
\t * {@link #FRAMES} skin (NBC's stage boundaries: head 10, body 13, arm 11, legs 12, hand 7, feet 5).
\t */
\tpublic static final int[] FLESH_FRAME = { 10, 13, 11, 11, 12, 12, 7, 7, 5, 5 };
\t/**
\t * NBC's order, checked every tick: a segment may start growing only once the one it grows from has this many frames (or is
\t * whole): the body waits for the head to reach 4, the arms and legs for the body to reach 7, each hand for its arm to reach 5,
\t * each foot for its leg to reach 5. The head grows from nothing.
\t */
\tpublic static final int BODY_NEEDS_HEAD = 4;
\tpublic static final int LIMB_NEEDS_BODY = 7;
\tpublic static final int HAND_NEEDS_ARM = 5;
\tpublic static final int FOOT_NEEDS_LEG = 5;
\t/**
\t * The Regen Menu's steps (NBC's segmentation): with auto-regrowth off, each press of a segment's button grows it to its next
\t * target -- the end of the bone stage, then the end of the flesh stage, then whole. Frames, by segment.
\t */
\tpublic static final int[][] MENU_TARGETS = { { 4, 10, 17 }, { 7, 13, 20 }, { 5, 11, 18 }, { 5, 11, 18 }, { 5, 12, 20 }, { 5, 12, 20 },
\t\t\t{ 3, 7, 12 }, { 3, 7, 12 }, { 2, 5, 11 }, { 2, 5, 11 } };
\t/** Regenerate Whole Body (Shift in the Regen Menu) is held this long. */
\tpublic static final int WHOLE_BODY_HOLD = 40;
\t/**
\t * The three layers of the 3D look, as {start, length} shares of a segment's frames: the bone pushes out over the bone stage,
\t * the muscle builds round it and is in by the flesh stage, the skin closes over in the flesh stage. A segment held at a stage
\t * by the Regen Menu shows that stage.
\t */
\tpublic static final float[] LAYER_BONE = { 0f, 0.45f };
\tpublic static final float[] LAYER_MUSCLE = { 0.25f, 0.35f };
\tpublic static final float[] LAYER_SKIN = { 0.6f, 0.4f };

\t/** 0..1 through one layer at regrowth progress {@code p}. */
\tpublic static float layer(float[] layer, float p) {
\t\treturn Math.max(0f, Math.min(1f, (p - layer[0]) / layer[1]));
\t}

\tpublic static float skinShare(float p) {
\t\treturn layer(LAYER_SKIN, p);
\t}

\t/**
\t * The bit of an arm the finger shots used up grows back like an arm segment (one frame a tick) over the share of its frames
\t * that is missing, at least {@link #CUT_MIN_SHARE}.
\t */
\tpublic static final float CUT_MIN_SHARE = 0.35f;

\tpublic static float cutRegrowTicks(float missing) {
\t\treturn FRAMES[com.projecthero.mod.andy.data.AndyState.RIGHT_ARM] * Math.max(CUT_MIN_SHARE, Math.min(1f, missing));
\t}

`;
t = t.slice(0, a) + block + t.slice(b);
const reformOld = "\t * once. Every part that is missing or regrowing is whole on the spot, he heals {@link #REFORM_HEAL} and the fire and harmful\n\t * effects on him go. It costs the food the regrowth still owed at his speed ({@link #partFood} a part not yet paid for) plus\n\t * {@link #REFORM_FOOD}, saturation first. Works with regeneration Off too (it is a deliberate choice).\n";
if (!t.includes(reformOld)) throw new Error('reform doc');
t = t.replace(reformOld, "\t * once. Every part that is missing or regrowing is whole on the spot, he heals {@link #REFORM_HEAL} and the fire and harmful\n\t * effects on him go. Free (v0.17.3: regrowth is free, like NBC's); it works with auto-regrowth off too.\n");
t = t.replace('\tpublic static final float REFORM_FOOD = 4f;\n', '');
const ae = "\t/** v0.17.3 (user: passive auto-eat): he eats from his inventory once his food falls below this (food + saturation, points). */\n\tpublic static final int AUTO_EAT_BELOW = 14;\n\tpublic static final int AUTO_EAT_EVERY = 40; // Iron Man's 2 s\n";
if (!t.includes(ae)) throw new Error('autoeat');
t = t.replace(ae, '');
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
