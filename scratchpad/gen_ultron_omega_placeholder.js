// v0.16.1 PLACEHOLDER assets for Ultron Omega (the final concept model is designed elsewhere; this one only has to
// satisfy the code contract). usage (from the repo/worktree root): node scratchpad/gen_ultron_omega_placeholder.js
// Writes geo/ultron/ultron_omega.geo.json, textures/entity/ultron/ultron_omega(.png|_glowmask.png),
// animations/ultron/ultron_omega.animation.json. Idempotent.
//
// Humanoid, 64 px (4 blocks) tall standing on y = 0, faces -Z; `_l` = entity's left = +X.
// Bone hierarchy (parent in brackets):
//   root; hips [root]; waist [hips]; chest [waist]; flinch [chest]; neck [flinch]; head [neck]; jaw [head];
//   arm_l, arm_r [flinch]; forearm_l [arm_l], forearm_r [arm_r]; hand_l [forearm_l], hand_r [forearm_r];
//   fingers_l [hand_l], fingers_r [hand_r]; thigh_l, thigh_r [hips]; shin_l [thigh_l], shin_r [thigh_r];
//   foot_l [shin_l], foot_r [shin_r].
//   `neck` and `flinch` are driven by code and NEVER appear in an animation.
//
// Clips (animation.ultron_omega.<name>); length in seconds = ticks / 20. The impact POSE is keyed at the clip tick
// listed below (= server hit tick - 4, the client's blend-in):
//   | clip             | length (ticks) | loop                 | impact pose keyed at clip tick                            |
//   |------------------|----------------|----------------------|-----------------------------------------------------------|
//   | idle             | 40             | true                 | - (gentle breathing, arms slightly out)                   |
//   | fly              | 20             | true                 | - (legs trailing back, arms back)                         |
//   | walk             | 20             | true                 | -                                                         |
//   | vibranium_strike | 44             | false                | 8, 18, 32 (right hook, left hook, overhead double slam)   |
//   | concussive_blast | 30             | false                | 14 (both palms thrust forward)                            |
//   | lattice          | 100            | false                | windup 0-16 (arms rising, head back), arms spread 16-100  |
//   | siphon           | 100            | true                 | - (both hands forward channelling)                        |
//   | split            | 30             | false                | 16 (arms flung wide)                                      |
//   | override         | 40             | false                | 20 (right hand raised to the sky)                         |
//   | upload           | 40             | true                 | - (head back, arms out, hovering)                         |
//   | hurt             | 10             | false                | 2 (recoil)                                                |
//   | stagger_kneel    | 30             | "hold_on_last_frame" | ends kneeling on one knee, head down                      |
//   | disintegrate     | 60             | "hold_on_last_frame" | arching back, arms out                                    |
//   | self_destruct    | 60             | "hold_on_last_frame" | hunched, then arms flung out at 56                        |
//
// Rotation conventions (as gen_v01420_titan_model.js): X > 0 tips a bone's top forward (a hanging limb's free end
// swings BACK, so raising an arm forward is X < 0); Z > 0 swings a hanging limb toward -X (the right), so the right
// arm goes out sideways with Z > 0 and the left with Z < 0.
'use strict';
const L = require('./v0161_asset_lib.js');
const { Geo, mix, mul, hex, hash3, writeJson, writePng, written, clip } = L;

const RED = hex('FF2010'), HOT = hex('FF9070'), RED_D = hex('6E0806');
const SIL = hex('C6CCD6'), SIL2 = hex('EEF1F6'), BLK = hex('1C1D22');

// ================================================================ model
const g = new Geo('ultron_omega');
const mirror = (o, s) => [-(o[0] + s[0]), o[1], o[2]];
g.bone('root', null, [0, 0, 0]);
g.bone('hips', 'root', [0, 28, 0]);
g.cube('hips', [-6, 26, -4], [12, 5, 8], 'silver', { tag: 'hips' });
g.bone('waist', 'hips', [0, 31, 0]);
g.cube('waist', [-5, 31, -3.5], [10, 6, 7], 'black', { tag: 'abs' });
g.bone('chest', 'waist', [0, 37, 0]);
g.cube('chest', [-10, 37, -5], [20, 13, 10], 'silver', { tag: 'chest' });
g.cube('chest', [-9, 42, -6], [8, 7, 1], 'silver', { tag: 'pec' });
g.cube('chest', [1, 42, -6], [8, 7, 1], 'silver', { tag: 'pec' });
g.bone('flinch', 'chest', [0, 44, 0]);
g.bone('neck', 'flinch', [0, 50, 0]);
g.cube('neck', [-2, 49, -2], [4, 3, 4], 'black');
g.bone('head', 'neck', [0, 52, 0]);
g.cube('head', [-4, 55, -4], [8, 7, 8], 'silver', { tag: 'face' });
g.cube('head', [-1, 59, -5], [2, 5, 10], 'silver', { tag: 'crest' });
g.bone('jaw', 'head', [0, 55, -1]);
g.cube('jaw', [-3, 51.5, -4.5], [6, 4, 7], 'silver', { tag: 'mouth' });
for (const side of ['r', 'l']) {
	const m = side === 'l';
	const C = (bone, o, s, mat, opts) => g.cube(bone, m ? mirror(o, s) : o, s, mat, opts);
	const px = x => m ? -x : x;
	g.bone(`arm_${side}`, 'flinch', [px(-12.5), 48, 0]);
	C(`arm_${side}`, [-15, 37, -2.5], [5, 12, 5], 'silver', { tag: 'limb' });
	C(`arm_${side}`, [-16, 45, -3.5], [7, 5, 7], 'silver', { tag: 'pauldron' });
	g.bone(`forearm_${side}`, `arm_${side}`, [px(-12.5), 37, 0]);
	C(`forearm_${side}`, [-14.5, 25, -2], [4, 12, 4], 'silver', { tag: 'limb' });
	g.bone(`hand_${side}`, `forearm_${side}`, [px(-12.5), 25, 0]);
	C(`hand_${side}`, [-14.5, 21, -2], [4, 4, 4], 'black');
	g.bone(`fingers_${side}`, `hand_${side}`, [px(-12.5), 21, 0]);
	for (const fx of [-14.5, -13, -11.5]) C(`fingers_${side}`, [fx, 13, -1.5], [1, 8, 1], 'silver', { tag: 'finger' });
	C(`fingers_${side}`, [-11, 16, -2.5], [1, 5, 1], 'silver', { tag: 'finger' });
	g.bone(`thigh_${side}`, 'hips', [px(-4), 28, 0]);
	C(`thigh_${side}`, [-6.5, 16, -2.5], [5, 13, 5], 'silver', { tag: 'limb' });
	g.bone(`shin_${side}`, `thigh_${side}`, [px(-4), 16, 0]);
	C(`shin_${side}`, [-6, 3, -2], [4, 13, 4], 'black');
	C(`shin_${side}`, [-6.5, 11, -3], [5, 5, 1], 'silver', { tag: 'knee' });
	g.bone(`foot_${side}`, `shin_${side}`, [px(-4), 3, 0]);
	C(`foot_${side}`, [-6.5, 0, -7], [5, 3, 9], 'silver', { tag: 'foot' });
}
g.layout();

// ================================================================ texture + glowmask
const { tex, glow } = g.paint((c, r, i, j) => {
	const f = r.face, s = (i + 0.5) / r.w, t = (j + 0.5) / r.h, side = f !== 'up' && f !== 'down';
	const FL = { up: 1.12, down: 0.55, right: 0.84, front: 1.0, left: 0.9, back: 0.76 }[f];
	let col;
	if (c.mat === 'black') col = mul(BLK, 0.9 + 0.2 * hash3(i, j, c.id));
	else {
		col = mix(SIL2, hex('8E96A4'), side ? t * 0.55 : 0.15);
		if (side && j === 0) col = mix(col, [255, 255, 255], 0.35);
		if (side && r.h >= 8 && j % 6 === 5) col = mul(col, 0.7);          // plate seams
		if ((i === 0 || i === r.w - 1) && r.w > 2) col = mul(col, 0.82);
		col = mul(col, 0.94 + 0.1 * hash3(i + c.id * 13, j, 3));
	}
	col = mul(col, FL);
	let gl = null;
	const tag = c.opts.tag;
	if (tag === 'face' && f === 'front') {                                 // eyes (slanted) + dark brow
		if (j === 1) col = mul(col, 0.6);
		if ((j === 2 && (i === 1 || i === 2 || i === 5 || i === 6)) || (j === 3 && (i === 2 || i === 5))) gl = (j === 2 && (i === 2 || i === 5)) ? HOT : RED;
		if (j >= 2 && j <= 3 && !gl && (i === 1 || i === 6)) col = mix(col, BLK, 0.5);
	}
	if (tag === 'mouth' && f === 'front') {
		if (j === 2 && i >= 1 && i <= 4) gl = i === 2 || i === 3 ? HOT : RED;
		if (j === 1 && (i === 1 || i === 4)) col = BLK;
	}
	if (tag === 'chest' && f === 'front' && (i === 6 || i === 13)) gl = j % 5 === 0 ? HOT : RED; // lines down the chest
	if (tag === 'chest' && f === 'front' && (i === 9 || i === 10) && j >= 3 && j <= 5) gl = j === 4 ? HOT : RED; // core
	if (tag === 'abs' && f === 'front' && (i === 3 || i === 6)) gl = RED;
	if (tag === 'limb' && f === 'front' && i === Math.floor(r.w / 2)) gl = j % 6 === 0 ? HOT : RED; // lines down arms/legs
	if (tag === 'finger' && side && j === r.h - 1) col = mul(col, 0.5);    // dark claw tips
	if (gl) col = gl;
	else if (c.mat !== 'black' && side && f === 'front' && tag === 'chest' && (i === 5 || i === 7 || i === 12 || i === 14)) col = mix(col, RED_D, 0.35);
	return { col, glow: gl };
});
writeJson('geo/ultron/ultron_omega.geo.json', g.json());
writePng('textures/entity/ultron/ultron_omega.png', tex);
writePng('textures/entity/ultron/ultron_omega_glowmask.png', glow);

// ================================================================ animations
// a pose = { bone: [rx, ry, rz], pos: { bone: [x, y, z] } }; frames = [[tick, pose], ...]. Any bone used in any frame
// of a clip is keyed in every frame (missing = 0), so poses fully define the clip.
const FORBIDDEN = ['neck', 'flinch'];
function poseClip(length, loop, frames) {
	const rotBones = new Set(), posBones = new Set();
	for (const [, p] of frames) for (const k of Object.keys(p)) { if (k === 'pos') Object.keys(p.pos).forEach(b => posBones.add(b)); else rotBones.add(k); }
	const spec = {};
	for (const b of [...rotBones, ...posBones]) {
		if (FORBIDDEN.includes(b)) throw new Error('forbidden bone ' + b);
		if (!g.B[b]) throw new Error('unknown bone ' + b);
		spec[b] = spec[b] || {};
	}
	for (const b of rotBones) { spec[b].rotation = {}; for (const [t, p] of frames) spec[b].rotation[t] = p[b] || [0, 0, 0]; }
	for (const b of posBones) { spec[b].position = {}; for (const [t, p] of frames) spec[b].position[t] = (p.pos && p.pos[b]) || [0, 0, 0]; }
	if (frames[frames.length - 1][0] !== length) throw new Error('last frame must sit at the clip end');
	return clip(length, loop, spec);
}
const REST = { arm_r: [0, 0, 8], arm_l: [0, 0, -8], forearm_r: [-8, 0, 0], forearm_l: [-8, 0, 0], fingers_r: [-10, 0, 0], fingers_l: [-10, 0, 0] };
const P = (...parts) => { const o = { pos: {} }; for (const p of parts) for (const [k, v] of Object.entries(p)) { if (k === 'pos') Object.assign(o.pos, v); else o[k] = v; } return o; };
const armsOut = (x, z, fore = 0) => ({ arm_r: [x, 0, z], arm_l: [x, 0, -z], forearm_r: [fore, 0, 0], forearm_l: [fore, 0, 0] });

const A = {};
A.idle = poseClip(40, true, [
	[0, P(REST)],
	[20, P(REST, { chest: [-2, 0, 0], head: [-2, 0, 0], arm_r: [0, 0, 12], arm_l: [0, 0, -12], pos: { chest: [0, 0.4, 0] } })],
	[40, P(REST)],
]);
const FLY = { root: [20, 0, 0], thigh_r: [25, 0, 3], thigh_l: [22, 0, -3], shin_r: [20, 0, 0], shin_l: [26, 0, 0], foot_r: [35, 0, 0], foot_l: [35, 0, 0], arm_r: [30, 0, 15], arm_l: [30, 0, -15], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], head: [-15, 0, 0] };
A.fly = poseClip(20, true, [
	[0, P(FLY)],
	[10, P(FLY, { thigh_r: [30, 0, 3], thigh_l: [17, 0, -3], arm_r: [34, 0, 17], arm_l: [34, 0, -17], pos: { root: [0, 0.6, 0] } })],
	[20, P(FLY)],
]);
A.walk = poseClip(20, true, [
	[0, P({ thigh_r: [-25, 0, 0], thigh_l: [25, 0, 0], shin_r: [5, 0, 0], shin_l: [20, 0, 0], arm_r: [20, 0, 6], arm_l: [-20, 0, -6], forearm_r: [-10, 0, 0], forearm_l: [-20, 0, 0], pos: { hips: [0, 0, 0] } })],
	[5, P({ thigh_r: [0, 0, 0], thigh_l: [0, 0, 0], shin_r: [25, 0, 0], shin_l: [5, 0, 0], arm_r: [0, 0, 6], arm_l: [0, 0, -6], forearm_r: [-15, 0, 0], forearm_l: [-15, 0, 0], pos: { hips: [0, 1, 0] } })],
	[10, P({ thigh_r: [25, 0, 0], thigh_l: [-25, 0, 0], shin_r: [20, 0, 0], shin_l: [5, 0, 0], arm_r: [-20, 0, 6], arm_l: [20, 0, -6], forearm_r: [-20, 0, 0], forearm_l: [-10, 0, 0], pos: { hips: [0, 0, 0] } })],
	[15, P({ thigh_r: [0, 0, 0], thigh_l: [0, 0, 0], shin_r: [5, 0, 0], shin_l: [25, 0, 0], arm_r: [0, 0, 6], arm_l: [0, 0, -6], forearm_r: [-15, 0, 0], forearm_l: [-15, 0, 0], pos: { hips: [0, 1, 0] } })],
	[20, P({ thigh_r: [-25, 0, 0], thigh_l: [25, 0, 0], shin_r: [5, 0, 0], shin_l: [20, 0, 0], arm_r: [20, 0, 6], arm_l: [-20, 0, -6], forearm_r: [-10, 0, 0], forearm_l: [-20, 0, 0], pos: { hips: [0, 0, 0] } })],
]);
const GUARD = { arm_r: [-30, 0, 10], arm_l: [-30, 0, -10], forearm_r: [-60, 0, 0], forearm_l: [-60, 0, 0], fingers_r: [-40, 0, 0], fingers_l: [-40, 0, 0] };
A.vibranium_strike = poseClip(44, false, [
	[0, P(REST)],
	[4, P(GUARD, { chest: [0, -25, 0], arm_r: [-20, 0, 50], forearm_r: [-80, 0, 0], thigh_r: [-8, 0, 0] })],
	[8, P(GUARD, { chest: [5, 25, 0], waist: [0, 8, 0], arm_r: [-90, 0, -15], forearm_r: [-40, 0, 0], thigh_r: [-8, 0, 0] })],          // right hook
	[13, P(GUARD, { chest: [0, 25, 0], arm_l: [-20, 0, -50], forearm_l: [-80, 0, 0] })],
	[18, P(GUARD, { chest: [5, -25, 0], waist: [0, -8, 0], arm_l: [-90, 0, 15], forearm_l: [-40, 0, 0], thigh_l: [-8, 0, 0] })],          // left hook
	[25, P({ chest: [-15, 0, 0], head: [-10, 0, 0], arm_r: [-170, 0, -10], arm_l: [-170, 0, 10], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], fingers_r: [-60, 0, 0], fingers_l: [-60, 0, 0] })],
	[32, P({ chest: [30, 0, 0], waist: [10, 0, 0], head: [10, 0, 0], arm_r: [-60, 0, -10], arm_l: [-60, 0, 10], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], fingers_r: [-60, 0, 0], fingers_l: [-60, 0, 0], thigh_r: [-20, 0, 0], thigh_l: [-20, 0, 0], shin_r: [30, 0, 0], shin_l: [30, 0, 0], foot_r: [-10, 0, 0], foot_l: [-10, 0, 0], pos: { hips: [0, -3, 0] } })], // double-fist slam
	[38, P({ chest: [20, 0, 0], waist: [5, 0, 0], arm_r: [-45, 0, -5], arm_l: [-45, 0, 5], forearm_r: [-15, 0, 0], forearm_l: [-15, 0, 0], thigh_r: [-12, 0, 0], thigh_l: [-12, 0, 0], shin_r: [18, 0, 0], shin_l: [18, 0, 0], foot_r: [-6, 0, 0], foot_l: [-6, 0, 0], pos: { hips: [0, -1.5, 0] } })],
	[44, P(REST)],
]);
A.concussive_blast = poseClip(30, false, [
	[0, P(REST)],
	[8, P({ chest: [-10, 0, 0], arm_r: [-60, 0, 20], arm_l: [-60, 0, -20], forearm_r: [-90, 0, 0], forearm_l: [-90, 0, 0], hand_r: [30, 0, 0], hand_l: [30, 0, 0], thigh_r: [-10, 0, 0], shin_r: [10, 0, 0] })],
	[14, P({ chest: [5, 0, 0], arm_r: [-90, 0, -5], arm_l: [-90, 0, 5], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], hand_r: [70, 0, 0], hand_l: [70, 0, 0], fingers_r: [10, 0, 0], fingers_l: [10, 0, 0], thigh_r: [-20, 0, 0], shin_r: [15, 0, 0], thigh_l: [10, 0, 0] })], // palms forward
	[22, P({ chest: [3, 0, 0], arm_r: [-85, 0, -5], arm_l: [-85, 0, 5], hand_r: [60, 0, 0], hand_l: [60, 0, 0], thigh_r: [-15, 0, 0], shin_r: [12, 0, 0], thigh_l: [8, 0, 0] })],
	[30, P(REST)],
]);
const SPREAD = { chest: [-5, 0, 0], head: [-10, 0, 0], arm_r: [0, 0, 95], arm_l: [0, 0, -95], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], fingers_r: [15, 0, 0], fingers_l: [15, 0, 0] };
A.lattice = poseClip(100, false, [
	[0, P(REST)],
	[16, P({ chest: [-10, 0, 0], head: [-25, 0, 0], arm_r: [-150, 0, 20], arm_l: [-150, 0, -20], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0] })],
	[24, P(SPREAD)],
	[60, P(SPREAD, { arm_r: [0, 0, 98], arm_l: [0, 0, -98], pos: { root: [0, 0.5, 0] } })],
	[100, P(SPREAD)],
]);
const SIPHON = { chest: [5, 0, 0], arm_r: [-80, 0, -10], arm_l: [-80, 0, 10], forearm_r: [-15, 0, 0], forearm_l: [-15, 0, 0], hand_r: [60, 0, 0], hand_l: [60, 0, 0], fingers_r: [20, 0, 0], fingers_l: [20, 0, 0], thigh_r: [-10, 0, 0], shin_r: [8, 0, 0] };
A.siphon = poseClip(100, true, [
	[0, P(SIPHON)],
	[25, P(SIPHON, { fingers_r: [-5, 0, 0], fingers_l: [-5, 0, 0], arm_r: [-84, 0, -8], arm_l: [-84, 0, 8] })],
	[50, P(SIPHON, { chest: [7, 0, 0] })],
	[75, P(SIPHON, { fingers_r: [-5, 0, 0], fingers_l: [-5, 0, 0], arm_r: [-76, 0, -12], arm_l: [-76, 0, 12] })],
	[100, P(SIPHON)],
]);
A.split = poseClip(30, false, [
	[0, P(REST)],
	[10, P({ chest: [15, 0, 0], head: [10, 0, 0], arm_r: [-80, 0, -40], arm_l: [-80, 0, 40], forearm_r: [-60, 0, 0], forearm_l: [-60, 0, 0], pos: { hips: [0, -1, 0] }, thigh_r: [-10, 0, 0], thigh_l: [-10, 0, 0], shin_r: [15, 0, 0], shin_l: [15, 0, 0] })],
	[16, P({ chest: [-15, 0, 0], head: [-15, 0, 0], arm_r: [-20, 0, 110], arm_l: [-20, 0, -110], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], fingers_r: [20, 0, 0], fingers_l: [20, 0, 0] })], // flung wide
	[22, P({ chest: [-10, 0, 0], head: [-10, 0, 0], arm_r: [-15, 0, 100], arm_l: [-15, 0, -100] })],
	[30, P(REST)],
]);
const SKY = { chest: [-10, 0, 0], head: [-30, 0, 0], arm_r: [-175, 0, 5], forearm_r: [0, 0, 0], hand_r: [0, 0, 0], fingers_r: [20, 0, 0], arm_l: [10, 0, -25], forearm_l: [-10, 0, 0] };
A.override = poseClip(40, false, [
	[0, P(REST)],
	[12, P({ chest: [10, 0, 0], arm_r: [20, 0, 20], forearm_r: [-30, 0, 0], arm_l: [0, 0, -15], pos: { hips: [0, -1, 0] }, thigh_r: [-8, 0, 0], thigh_l: [-8, 0, 0], shin_r: [12, 0, 0], shin_l: [12, 0, 0] })],
	[20, P(SKY)],                                                                                          // hand to the sky
	[32, P(SKY, { head: [-28, 0, 0], arm_r: [-172, 0, 6] })],
	[40, P(REST)],
]);
const UPLOAD = { head: [-35, 0, 0], chest: [-8, 0, 0], arm_r: [0, 0, 70], arm_l: [0, 0, -70], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], fingers_r: [10, 0, 0], fingers_l: [10, 0, 0], thigh_r: [10, 0, 0], thigh_l: [6, 0, 0], shin_r: [15, 0, 0], shin_l: [20, 0, 0], foot_r: [30, 0, 0], foot_l: [30, 0, 0] };
A.upload = poseClip(40, true, [
	[0, P(UPLOAD, { pos: { root: [0, 4, 0] } })],
	[20, P(UPLOAD, { arm_r: [0, 0, 74], arm_l: [0, 0, -74], pos: { root: [0, 6, 0] } })],
	[40, P(UPLOAD, { pos: { root: [0, 4, 0] } })],
]);
A.hurt = poseClip(10, false, [
	[0, P(REST)],
	[2, P({ chest: [-20, 0, 0], head: [-15, 0, 0], arm_r: [20, 0, 25], arm_l: [20, 0, -25], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0], pos: { hips: [0, 0, 1] } })], // recoil
	[10, P(REST)],
]);
A.stagger_kneel = poseClip(30, 'hold_on_last_frame', [
	[0, P(REST)],
	[10, P({ chest: [15, 0, 0], head: [15, 0, 0], arm_r: [5, 0, 15], arm_l: [5, 0, -15], thigh_r: [-15, 0, 0], thigh_l: [-15, 0, 0], shin_r: [30, 0, 0], shin_l: [30, 0, 0], foot_r: [-15, 0, 0], foot_l: [-15, 0, 0], pos: { hips: [0, -3, 0] } })],
	[30, P({ chest: [20, 0, 0], waist: [5, 0, 0], head: [30, 0, 0], arm_r: [10, 0, 10], forearm_r: [-10, 0, 0], arm_l: [-40, 0, -5], forearm_l: [-40, 0, 0], thigh_r: [0, 0, 0], shin_r: [90, 0, 0], foot_r: [0, 0, 0], thigh_l: [-80, 0, 0], shin_l: [80, 0, 0], foot_l: [0, 0, 0], pos: { hips: [0, -12, 0] } })], // kneeling on the right knee, head down
]);
A.disintegrate = poseClip(60, 'hold_on_last_frame', [
	[0, P(REST)],
	[20, P({ chest: [15, 0, 0], head: [10, 0, 0], arm_r: [-20, 0, 20], arm_l: [-20, 0, -20], forearm_r: [-40, 0, 0], forearm_l: [-40, 0, 0], pos: { hips: [0, -1, 0] } })],
	[60, P({ chest: [-30, 0, 0], waist: [-15, 0, 0], head: [-35, 0, 0], arm_r: [20, 0, 80], arm_l: [20, 0, -80], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], fingers_r: [25, 0, 0], fingers_l: [25, 0, 0], thigh_r: [-10, 0, 0], thigh_l: [-10, 0, 0], shin_r: [15, 0, 0], shin_l: [15, 0, 0], pos: { hips: [0, -2, 0] } })], // arching back
]);
const HUNCH = { chest: [35, 0, 0], waist: [15, 0, 0], head: [20, 0, 0], arm_r: [-50, 0, -35], arm_l: [-50, 0, 35], forearm_r: [-100, 0, 0], forearm_l: [-100, 0, 0], fingers_r: [-50, 0, 0], fingers_l: [-50, 0, 0], thigh_r: [-25, 0, 0], thigh_l: [-25, 0, 0], shin_r: [40, 0, 0], shin_l: [40, 0, 0], foot_r: [-15, 0, 0], foot_l: [-15, 0, 0], pos: { hips: [0, -3, 0] } };
const BURST = { chest: [-25, 0, 0], waist: [-5, 0, 0], head: [-30, 0, 0], arm_r: [-30, 0, 120], arm_l: [-30, 0, -120], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], fingers_r: [25, 0, 0], fingers_l: [25, 0, 0] };
A.self_destruct = poseClip(60, 'hold_on_last_frame', [
	[0, P(REST)],
	[20, P(HUNCH)],
	[30, P(HUNCH, { chest: [36, 3, 0] })],
	[36, P(HUNCH, { chest: [34, -3, 0] })],
	[42, P(HUNCH, { chest: [37, 3, 0] })],
	[48, P(HUNCH, { chest: [35, -3, 0] })],
	[52, P(HUNCH, { chest: [40, 0, 0], head: [25, 0, 0] })],
	[56, P(BURST)],                                                                                         // arms flung out
	[60, P(BURST)],
]);
const animations = {};
for (const [k, v] of Object.entries(A)) animations[`animation.ultron_omega.${k}`] = v;
writeJson('animations/ultron/ultron_omega.animation.json', { format_version: '1.8.0', animations });

console.log(`gen_ultron_omega_placeholder: ${g.bones.length} bones, ${g.cubes.length} cubes, texture ${g.W}x${g.H}`);
for (const w of written) console.log('  ' + w);
