// v0.16.1 raid undead: the unit models (see gen_raid_undead_models.js for the pipeline and conventions).
'use strict';
const path = require('path');
const L = require('./lib_concept_models.js');
const G = require('./gen_raid_undead_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, vnorm, vadd, vsub, vsc, vlen } = L;
const { rig, BASE, CLAW, FIST, OPEN, SIDES, sxr, bevel, vgrad } = G;
const ap = require('./lib_geckogen_anim.js').addPose;

// under-wrap a list of cubes in a cloth shell (inflated by `k` px)
function shell(M, bone, c, k, mat, opts = {}) {
	return M.box(bone, c.from[0] - k, c.from[1] - (opts.kb == null ? k : opts.kb), c.from[2] - k, c.to[0] + k, c.to[1] + (opts.kt == null ? k : opts.kt), c.to[2] + k, mat, Object.assign({ rot: c.rot, pivot: c.pivot }, opts));
}
const cubesOf = (M, bone, paint) => M.B[bone].cubes.filter(c => !paint || c.paint === paint);

// ================================================================ RAID ZOMBIE (Risen / Baby / Armoured)
function buildRaidZombie() {
	const M = new Model('raid_zombie', { TW: 256 });
	M.pal = { base: [126, 138, 108], dark: [62, 70, 58], bruise: [96, 78, 104], rot: [86, 74, 46], vein: [56, 44, 74] };
	M.clothCol = [92, 104, 116]; M.cloth2Col = [74, 60, 46]; M.leatherCol = [70, 50, 34]; M.hairCol = [34, 30, 28]; M.hairHi = [80, 74, 64];
	M.ironCol = [96, 98, 104]; M.seed = 3;
	const P = Object.assign({}, BASE, { chestW: 8.0, ribW: 5.6, waistW: 4.4, waistD: 2.6, upperLen: 6.8, foreLen: 6.6, armW: 2.0, foreW: 1.8, thighW: 2.7, shinW: 2.1, headW: 5.4, headH: 6.8, headD: 5.8, neckH: 2.6, neckW: 2.0, fingerLen: 3.0 });
	rig(M, P, { anatomy: true });
	const A = M.A;
	A.eyeCol = [236, 226, 150]; A.eyeHot = [255, 252, 220]; A.noseHole = true;
	M.cheekTear = { x: 1.9, w: 0.9 };
	M.wounds = [
		{ c: [3.1, A.chestBot + 2.6, -1.4], r: 2.0, bone: true },      // left flank torn open to the ribs
		{ c: [A.shX + 0.3, A.wristY + 3.0, -0.9], r: 1.5, bone: true }, // left forearm stripped to the bone
		{ c: [-A.legX, A.ankleY + 3.2, -1.3], r: 1.2, bone: true },     // right shin
		{ c: [-1.6, A.hb + 5.2, -2.6], r: 0.9 },                         // gash over the right eye
		{ c: [-2.4, A.chestTop - 1.6, -2.2], r: 0.8 },                   // bite at the collar
		{ c: [0.6, A.pelvisTop + 0.8, -1.6], r: 0.7 },
	];
	M.stitches = [];
	// exposed ribs over the flank wound, a little proud of the skin
	for (let i = 0; i < 3; i++) M.stick('chest', [2.0, A.chestBot + 1.4 + i * 1.05, -1.95], [1, 0.25, 0.55], 2.6, [0.42, 0.42], 'bone', { seed: 40 + i });
	// stripped forearm: the two forearm bones show through the wound
	for (const dz of [-0.45, 0.4]) M.box('forearm_l', A.shX - 0.25 + dz * 0.2, A.wristY + 1.6, -1.25 + dz * 0.3, A.shX + 0.25 + dz * 0.2, A.wristY + 4.4, -0.9 + dz * 0.3, 'bone', { seed: 50 });
	// dislocated jaw, matted hair
	M.B.jaw.rot = [9, 0, -5];
	const hb = A.hb, top = A.headTop;
	// a few long lank strands from a balding scalp (they swing: the `hair` bone is a dangle)
	M.box('head', 0.3, top + 0.35, -2.2, 2.4, top + 0.75, 2.6, 'hair', { seed: 3 });
	const hair = M.bone('hair', 'head', [0, top, 1.8]);
	[[-2.75, 0.4, 4.6, 0.7], [-2.4, 2.2, 5.6, 0.6], [2.0, -0.6, 3.8, 0.6], [2.7, 1.2, 6.2, 0.8], [0.9, 3.0, 4.8, 0.7], [-0.9, 3.1, 3.6, 0.6]].forEach(([x, z, len, w], i) => {
		const outX = Math.abs(x) > 2 ? Math.sign(x) * 0.35 : 0, outZ = z > 2.5 ? 0.35 : 0;
		M.box(hair, x - w / 2 + outX, top - len, z - w / 2 + outZ, x + w / 2 + outX, top + 0.55, z + w / 2 + outZ, 'hair', { tag: 'hem', seed: 20 + i });
	});
	// ---- clothes: a torn linen shirt, one ragged sleeve, trousers rotted off at the knee, a rope belt
	M.holes = [
		{ c: [3.1, A.chestBot + 2.6, -1.6], r: 2.3 }, { c: [2.6, A.chestBot + 1.0, 2.2], r: 1.6 }, { c: [-2.4, A.chestTop - 1.6, -2.4], r: 1.3 },
		{ c: [-A.shX, A.elbowY + 2.0, 1.3], r: 1.0 }, { c: [A.legX + 0.8, A.kneeY + 3.4, -1.6], r: 1.0 }, { c: [-1.2, A.chestBot + 4.8, 2.4], r: 0.9 },
	];
	for (const c of cubesOf(M, 'chest').filter(c => ['ribs', 'chest', 'scapula', 'clavicle'].includes(c.paint) && c.mat === 'flesh')) shell(M, 'chest', c, 0.28, 'cloth', { paint: 'shirt' });
	shell(M, 'waist', M.B.waist.cubes[0], 0.3, 'cloth', { paint: 'shirt', tag: 'hem', kb: 0.9, seed: 7 });
	const tb = M.bone('tatter_b', 'waist', [0, A.pelvisTop, 1.8]);
	M.box(tb, -2.2, A.pelvisTop - 4.6, 1.55, 1.6, A.pelvisTop + 0.6, 1.95, 'cloth', { paint: 'shirt', tag: 'hem', seed: 8 });
	const tf = M.bone('tatter_f', 'waist', [0, A.pelvisTop, -1.8]);
	M.box(tf, -2.6, A.pelvisTop - 3.4, -1.95, -0.4, A.pelvisTop + 0.6, -1.55, 'cloth', { paint: 'shirt', tag: 'hem', seed: 9 });
	shell(M, 'arm_r', M.B.arm_r.cubes[0], 0.3, 'cloth', { paint: 'sleeve', tag: 'hem', seed: 10, kb: -2.2 });
	shell(M, 'arm_l', M.B.arm_l.cubes[0], 0.3, 'cloth', { paint: 'sleeve', tag: 'hem', seed: 11, kb: -5.0 });
	shell(M, 'hips', M.B.hips.cubes[0], 0.25, 'cloth2', { paint: 'trousers', tag: 'solid' });
	for (const [s, n] of SIDES) {
		const th = M.B[`thigh_${n}`].cubes[0];
		shell(M, `thigh_${n}`, th, 0.3, 'cloth2', { paint: 'trousers', tag: s < 0 ? 'hem' : 'solid', kb: s < 0 ? -0.6 : 0.3, seed: 12 + s });
		if (s > 0) shell(M, `shin_${n}`, M.B[`shin_${n}`].cubes[0], 0.32, 'cloth2', { paint: 'trousers', tag: 'hem', kb: -2.8, seed: 14 });
	}
	M.box('hips', -3.8, A.pelvisTop - 0.9, -2.15, 3.8, A.pelvisTop + 0.1, 2.15, 'leather', { paint: 'belt' });
	M.stick('hips', [-1.6, A.pelvisTop - 0.6, -2.2], [-0.2, -1, -0.15], 2.6, [0.45, 0.35], 'leather');
	// ---- the Armoured variant's kit: a dented kettle helm, breastplate, one big pauldron, vambraces, greaves, a mail skirt
	const helm = M.bone('armour_head', 'head', [0, hb + 3, 0]);
	// a dented sallet: domed cap, a ridge, a nasal bar and a flared neck guard
	M.box(helm, -A.hw - 0.5, A.eyeY + 1.0, -A.hd / 2 - 0.75, A.hw + 0.5, top + 0.9, A.hd / 2 + 0.6, 'iron', { paint: 'helm' });
	M.box(helm, -A.hw + 0.4, top + 0.85, -A.hd / 2 + 0.3, A.hw - 0.4, top + 1.5, A.hd / 2 - 0.2, 'iron', { paint: 'helm' });
	M.box(helm, -0.3, top + 0.8, -A.hd / 2 - 0.9, 0.3, top + 1.9, A.hd / 2 + 0.4, 'iron_d');
	M.box(helm, -0.35, A.eyeY - 1.6, A.faceZ - 1.25, 0.35, A.eyeY + 1.2, A.faceZ - 0.65, 'iron_clean');
	M.box(helm, -A.hw - 0.9, A.eyeY - 0.8, A.hd / 2 - 0.4, A.hw + 0.9, A.eyeY + 1.2, A.hd / 2 + 1.0, 'iron', { rot: [-24, 0, 0], pivot: [0, A.eyeY + 1, A.hd / 2] });
	for (const [s] of SIDES) M.box(helm, ...[sxr(s, A.hw + 0.3, A.hw + 0.7)[0], A.eyeY - 2.2, -1.4, sxr(s, A.hw + 0.3, A.hw + 0.7)[1], A.eyeY + 1.2, 1.6], 'iron');
	const cu = M.bone('armour_chest', 'chest', [0, A.chestBot, 0]);
	M.box(cu, -4.6, A.chestBot + 0.4, -2.75, 4.6, A.chestTop + 0.2, -1.55, 'iron', { paint: 'breast' });
	M.box(cu, -4.3, A.chestBot + 0.8, 1.55, 4.3, A.chestTop - 0.2, 2.6, 'iron', { paint: 'back' });
	M.box(cu, -0.35, A.chestBot + 1.0, -3.0, 0.35, A.chestTop - 0.2, -2.6, 'iron_clean');
	for (const [s] of SIDES) M.box(cu, ...[sxr(s, 3.2, 4.8)[0], A.chestBot + 1.2, -2.7, sxr(s, 3.2, 4.8)[1], A.chestTop - 1.0, 2.6], 'leather_d');
	const ps = M.bone('armour_pauldron_r', 'arm_r', [-A.shX, A.shY, 0]);
	M.box(ps, -A.shX - 2.2, A.shY - 0.6, -2.0, -A.shX + 1.4, A.shY + 1.7, 2.0, 'iron', { rot: [0, 0, -18], pivot: [-A.shX, A.shY, 0] });
	M.box(ps, -A.shX - 2.6, A.shY - 2.3, -1.9, -A.shX + 0.4, A.shY - 0.3, 1.9, 'iron', { rot: [0, 0, -26], pivot: [-A.shX, A.shY - 1, 0] });
	for (const [s, n] of SIDES) {
		const vb = M.bone(`armour_fore_${n}`, `forearm_${n}`, [s * A.shX, A.wristY, 0]);
		M.box(vb, s * A.shX - 1.5, A.wristY + 0.6, -1.5, s * A.shX + 1.5, A.wristY + 3.8, 1.5, 'iron', { paint: 'vambrace' });
		const gr = M.bone(`armour_shin_${n}`, `shin_${n}`, [s * A.legX, A.ankleY, 0]);
		M.box(gr, s * A.legX - 1.6, A.ankleY + 0.6, -1.9, s * A.legX + 1.6, A.kneeY + 0.4, 0.5, 'iron', { paint: 'greave' });
		M.box(gr, s * A.legX - 1.2, A.kneeY - 1.0, -2.3, s * A.legX + 1.2, A.kneeY + 1.0, -1.6, 'iron_clean');
	}
	const mail = M.bone('armour_mail', 'hips', [0, A.pelvisTop, 0]);
	M.box(mail, -3.9, A.hipY - 3.4, -2.3, 3.9, A.pelvisTop - 0.6, 2.3, 'chain', { paint: 'mail' });
	return M;
}
const ARMOUR = c => /^armour_/.test(c.bone);
function raidZombiePoses(M) {
	const stand = Object.assign({
		waist: [12, 0, 0], chest: [16, 8, 7], head: [-24, -10, 9],
		arm_r: [-18, 0, 4], forearm_r: [-26, 0, 0], arm_l: [-6, 0, -2], forearm_l: [-12, 0, 0],
		thigh_r: [-6, 0, 3], shin_r: [12, 0, 0], foot_r: [-6, 0, 0], thigh_l: [4, 0, -4], shin_l: [4, 0, 0], foot_l: [-8, 0, 0], hips: [0, 4, -2],
	}, CLAW('r'), CLAW('l', 0.6));
	const reach = Object.assign({
		waist: [16, 0, 0], chest: [14, 0, 0], head: [-26, 0, 0], jaw: [24, 0, 0],
		arm_r: [-112, 0, 8], forearm_r: [-14, 0, 0], arm_l: [-100, 0, -12], forearm_l: [-24, 0, 0],
		thigh_r: [-36, 0, 3], shin_r: [30, 0, 0], foot_r: [6, 0, 0], thigh_l: [20, 0, -3], shin_l: [26, 0, 0], foot_l: [-26, 0, 0],
	}, CLAW('r', 1.3), CLAW('l', 1.3));
	return { stand, reach };
}

// emissive helper: a glowing body with darker mottling; `edge` brightens the rims
function glowBody(p, f, info, deep, bright, seed = 0, edge = true) {
	const n = fbm(p[0] * 0.9 + seed, p[1] * 0.9, p[2] * 0.9, 3);
	let col = mix(deep, bright, 0.25 + 0.75 * n);
	if (edge && info.ePx < 0.25) col = mix(col, [255, 255, 255], 0.35);
	if (f === 'down') col = mul(col, 0.8);
	return col;
}

// ================================================================ RAID ZOMBIE BABY (its own small model: a grave-child)
function buildBabyZombie() {
	const M = new Model('raid_zombie_baby', { TW: 256 });
	M.pal = { base: [140, 146, 116], dark: [70, 74, 62], bruise: [104, 84, 110], rot: [90, 80, 52], vein: [60, 48, 80] };
	M.clothCol = [176, 168, 150]; M.hairCol = [40, 34, 30]; M.hairHi = [92, 82, 70]; M.seed = 9;
	// child proportions: a big skull on a thin little body, long dangling arms, short legs
	const P = Object.assign({}, BASE, {
		footH: 1.0, footLen: 3.4, footW: 2.0, shinLen: 3.6, shinW: 1.6, shinD: 1.7, thighLen: 3.6, thighW: 2.0, thighD: 2.1, legX: 1.25,
		pelvisH: 2.2, pelvisW: 4.6, pelvisD: 2.8, waistH: 2.2, waistW: 3.8, waistD: 2.6, chestH: 4.6, chestW: 5.6, ribW: 4.4, chestD: 3.2,
		neckH: 1.2, neckW: 1.6, headW: 6.6, headH: 7.0, headD: 6.6, armW: 1.4, armD: 1.5, upperLen: 4.4, foreLen: 4.2, foreW: 1.3, handLen: 1.6, fingerLen: 1.9,
		noTorsoAnatomy: true, anatomyK: 0.7,
	});
	rig(M, P, { anatomy: true });
	const A = M.A;
	A.eyeW = A.hw * 0.3; A.eyeH = 0.9; A.eyeCol = [236, 226, 150]; A.noseHole = true;
	M.eyeHaze = true;
	M.B.jaw.rot = [6, 0, 3];
	M.wounds = [{ c: [-1.5, A.chestBot + 1.4, -1.6], r: 1.0 }, { c: [A.shX, A.wristY + 2.0, -0.6], r: 0.8, bone: true }, { c: [1.8, A.headTop - 1.2, -2.6], r: 1.0, bone: true }];
	// thin wisps of hair
	const top = A.headTop;
	const hair = M.bone('hair', 'head', [0, top, 2]);
	[[-2.6, 1.2, 4.4, 0.6], [-1.0, 3.0, 3.2, 0.6], [2.4, 2.0, 5.0, 0.7]].forEach(([x, z, len, w], i) => M.box(hair, x - w / 2, top - len, z - w / 2, x + w / 2, top + 0.4, z + w / 2, 'hair', { tag: 'hem', seed: 30 + i }));
	// a grave-stained nightshirt down to the knees, one sleeve torn off
	M.holes = [{ c: [-1.6, A.chestBot + 1.4, -1.8], r: 1.2 }, { c: [1.2, A.chestBot + 3.0, 1.8], r: 0.9 }];
	for (const c of M.B.chest.cubes.filter(c => c.mat === 'flesh' && (c.paint === 'ribs' || c.paint === 'chest'))) shell(M, 'chest', c, 0.25, 'cloth', { paint: 'gown' });
	shell(M, 'waist', M.B.waist.cubes[0], 0.3, 'cloth', { paint: 'gown' });
	const skirt = M.bone('skirt', 'hips', [0, A.pelvisTop, 0]);
	M.box(skirt, -2.9, A.kneeY - 0.6, -1.95, 2.9, A.pelvisTop + 0.5, 1.95, 'cloth', { paint: 'gown', tag: 'hem', seed: 33 });
	shell(M, 'arm_l', M.B.arm_l.cubes[0], 0.25, 'cloth', { paint: 'sleeve', tag: 'hem', kb: -1.6, seed: 34 });
	// a stitched rag doll clutched in the left hand (button eyes)
	const doll = M.bone('doll', 'hand_l', [A.shX, A.knuckY, 0]);
	M.box(doll, A.shX - 0.6, A.knuckY - 3.6, -0.9, A.shX + 0.6, A.knuckY - 0.6, 0.9, 'doll', { paint: 'doll_body' });
	M.box(doll, A.shX - 0.75, A.knuckY - 0.8, -1.0, A.shX + 0.75, A.knuckY + 0.9, 1.0, 'doll', { paint: 'doll_head' });
	M.box(doll, A.shX - 0.3, A.knuckY - 5.0, -0.6, A.shX + 0.3, A.knuckY - 3.4, -0.1, 'doll', { paint: 'doll_leg' });
	M.box(doll, A.shX - 0.3, A.knuckY - 4.6, 0.15, A.shX + 0.3, A.knuckY - 3.4, 0.65, 'doll', { paint: 'doll_leg' });
	M.customMat = (c, f, s, t, p, info) => {
		if (c.mat !== 'doll') return null;
		let col = G.clothMat(M, Object.assign({}, c, { tag: 'solid' }), f, s, t, p, info, [150, 92, 84])[0];
		if (c.paint === 'doll_head') {
			col = mix(col, [196, 170, 140], 0.6);
			if (f === 'west' && Math.abs(t - 0.4) < 0.14 && (Math.abs(s - 0.3) < 0.12 || Math.abs(s - 0.7) < 0.12)) col = [20, 18, 22]; // button eyes (faces -X... the doll faces the side)
			if (f === 'west' && Math.abs(t - 0.75) < 0.06 && s > 0.25 && s < 0.75 && info.i % 2) col = [40, 20, 20];
		}
		return [col, 255, null];
	};
	return M;
}
function babyPoses(M) {
	const stand = Object.assign({
		waist: [8, 0, 0], chest: [10, -6, -4], head: [-12, 14, -10],
		arm_r: [-10, 0, 2], forearm_r: [-16, 0, 0], arm_l: [-26, 10, -6], forearm_l: [-40, 0, 0],
		thigh_r: [-4, 0, 6], shin_r: [8, 0, 0], thigh_l: [2, 0, -2], shin_l: [4, 0, 0], foot_l: [-4, 0, 0],
	}, CLAW('r'));
	const reach = Object.assign({ waist: [18, 0, 0], chest: [16, 0, 0], head: [-30, 0, 0], jaw: [26, 0, 0], arm_r: [-112, 0, 10], forearm_r: [-10, 0, 0], arm_l: [-40, 0, -20], forearm_l: [-50, 0, 0], thigh_r: [-40, 0, 3], shin_r: [34, 0, 0], thigh_l: [24, 0, -3], shin_l: [30, 0, 0], foot_l: [-26, 0, 0] }, CLAW('r', 1.4));
	return { stand, reach };
}

// ================================================================ ACID ZOMBIE
const ACID = [130, 240, 60], ACID_DEEP = [26, 110, 16], ACID_HOT = [196, 255, 120];
function buildAcidZombie() {
	const M = new Model('acid_zombie', { TW: 256 });
	M.pal = { base: [150, 156, 112], dark: [58, 70, 46], bruise: [118, 96, 112], rot: [92, 104, 40], vein: [52, 96, 30] };
	M.leatherCol = [66, 56, 40]; M.cloth2Col = [70, 74, 60]; M.seed = 5;
	const P = Object.assign({}, BASE, {
		chestW: 9.0, ribW: 8.0, chestD: 5.2, chestH: 6.6, waistW: 9.0, waistD: 6.6, waistH: 3.8, pelvisW: 8.0, pelvisD: 5.0,
		headW: 4.8, headH: 5.4, headD: 5.0, neckH: 1.0, neckW: 3.8, armW: 1.9, foreW: 1.8, upperLen: 6.2, foreLen: 6.0, thighW: 3.4, thighD: 3.4, legX: 2.3, shinW: 2.5,
		noTorsoAnatomy: true,
	});
	rig(M, P, { anatomy: true });
	const A = M.A;
	A.eyeCol = [190, 255, 110]; A.eyeHot = [240, 255, 200]; A.eyeW = 0.7; A.eyeH = 0.5;
	M.jawGlow = ACID;
	// an unhinged jaw: longer, hanging open, the throat glowing behind the teeth
	const jaw = M.B.jaw; jaw.rot = [30, 0, 0];
	M.box(jaw, -A.hw + 0.4, A.hb - 1.8, A.faceZ + 0.1, A.hw - 0.4, A.hb - 0.3, A.hd / 2 - 1.2, 'flesh', { paint: 'jaw' });
	M.box('head', -A.hw + 0.7, A.hb + 1.2, A.faceZ + 0.4, A.hw - 0.7, A.hb + 1.6, A.hd / 2 - 1.0, 'acid', { paint: 'mouth', seed: 2 });
	// a lumpy swelling on the right side of the skull with a sac bursting out of it
	G.blob(M, 'head', [-A.hw - 0.2, A.hb + 3.0, 0.6], [2.2, 3.2, 3.4], 'flesh', { paint: 'lump' });
	G.blob(M, 'head', [-A.hw - 0.9, A.hb + 3.6, 0.2], [1.5, 1.7, 1.8], 'acid', { paint: 'sac', seed: 3 });
	// a distended belly sagging over the belt: rounded layered slabs
	G.blob(M, 'waist', [0, A.pelvisTop + 0.6, -3.3], [9.6, 6.4, 4.8], 'flesh', { paint: 'belly' });
	G.blob(M, 'waist', [0, A.pelvisTop - 0.6, -4.5], [7.0, 4.6, 3.2], 'flesh', { paint: 'belly' });
	// a throat goiter that swells before the spit
	const throat = M.bone('throat', 'neck', [0, A.chestTop + 0.5, -2]);
	G.blob(M, throat, [0, A.chestTop + 0.4, -2.5], [3.4, 2.6, 2.2], 'acid', { paint: 'sac', seed: 1 });
	// acid sacs: a cluster on the back, one on the right shoulder, two on the belly
	const sacs = M.bone('sacs', 'chest', [0, A.chestBot + 3, 3]);
	[[-1.8, A.chestTop - 2.0, 3.0, 3.6, 3.4, 2.6], [2.0, A.chestTop - 3.6, 2.9, 3.2, 3.8, 2.4], [0.2, A.chestBot + 1.0, 2.8, 4.2, 2.8, 2.4], [-3.6, A.chestBot + 2.4, 2.4, 2.2, 2.2, 1.8], [3.4, A.chestTop - 0.6, 2.2, 1.8, 1.8, 1.4]]
		.forEach(([x, y, z, w, h, d], i) => G.blob(M, sacs, [x, y, z + d / 2], [w, h, d], 'acid', { paint: 'sac', seed: 10 + i }));
	G.blob(M, 'arm_r', [-A.shX - 0.5, A.shY + 0.6, 0.2], [2.6, 2.4, 2.6], 'acid', { paint: 'sac', seed: 20 });
	const belly = M.bone('belly_sac', 'waist', [3.0, A.pelvisTop, -5]);
	G.blob(M, belly, [3.0, A.pelvisTop + 0.4, -5.6], [2.4, 2.8, 2.0], 'acid', { paint: 'sac', seed: 21 });
	G.blob(M, belly, [-2.4, A.pelvisTop + 2.2, -5.4], [1.7, 1.7, 1.4], 'acid', { paint: 'sac', seed: 22 });
	// dripping strands from the jaw and the belly sac
	for (const [i, x] of [[0, -0.9], [1, 1.0]]) {
		const d = M.bone(`drip_${i}`, 'jaw', [x, A.hb - 1.6, A.faceZ - 0.2]);
		M.box(d, x - 0.22, A.hb - 4.6 - i * 1.0, A.faceZ - 0.45, x + 0.22, A.hb - 1.5, A.faceZ - 0.05, 'acid', { paint: 'drip', seed: 30 + i });
		M.cbox(d, [x, A.hb - 4.7 - i * 1.0, A.faceZ - 0.25], [0.65, 0.75, 0.65], 'acid', { paint: 'drip', seed: 32 + i });
	}
	const d2 = M.bone('drip_2', 'belly_sac', [3.0, A.pelvisTop - 1.0, -5.6]);
	M.box(d2, 2.8, A.pelvisTop - 3.4, -5.8, 3.2, A.pelvisTop - 0.9, -5.4, 'acid', { paint: 'drip', seed: 34 });
	M.cbox(d2, [3.0, A.pelvisTop - 3.5, -5.6], [0.6, 0.7, 0.6], 'acid', { paint: 'drip', seed: 35 });
	// sewer-worker kit: a stained leather apron on straps, rotted trousers, wrapped boots
	M.holes = [{ c: [-2.0, A.kneeY + 2, -1.8], r: 0.9 }, { c: [2.6, A.kneeY - 1, -1.4], r: 0.8 }];
	for (const [s] of SIDES) M.stick('chest', [s * 2.6, A.chestBot + 0.6, -3.05], [0, 1, 0.12], A.chestTop - A.chestBot - 0.2, [0.6, 0.3], 'leather');
	M.box('hips', -4.4, A.pelvisTop - 0.8, -2.9, 4.4, A.pelvisTop + 0.2, 2.9, 'leather_d');
	shell(M, 'hips', M.B.hips.cubes[0], 0.25, 'cloth2', { tag: 'solid' });
	for (const [s, n] of SIDES) {
		shell(M, `thigh_${n}`, M.B[`thigh_${n}`].cubes[0], 0.3, 'cloth2', { tag: 'hem', kb: -0.8, seed: 40 + s });
		shell(M, `shin_${n}`, M.B[`shin_${n}`].cubes[0], 0.3, 'leather_d', { kt: -2.6 });
		shell(M, `foot_${n}`, M.B[`foot_${n}`].cubes[0], 0.25, 'leather_d', { tag: 'sole' });
	}
	// green veins spread from every sac; the skin around them blisters
	const sacCentres = M.cubes().filter(c => c.mat === 'acid' && c.paint === 'sac').map(c => ({ c: [0, 1, 2].map(i => (c.from[i] + c.to[i]) / 2), r: Math.max(...[0, 1, 2].map(i => c.to[i] - c.from[i])) * 0.5 + 2.4 }));
	M.extraFlesh = (c, f, s, t, p, info, col) => {
		let near = 0;
		for (const sc of sacCentres) near = Math.max(near, 1 - vlen(vsub(p, sc.c)) / sc.r);
		const v = Math.abs(fbm(p[0] * 0.9 + 4, p[1] * 0.9, p[2] * 0.9, 3) - 0.5);
		if (near > 0.3 && v < 0.016 * near) return [mix(col, ACID, 0.85), ACID, 200];
		if (near > 0.2 && v < 0.03 * near) return [mix(col, [60, 110, 30], 0.6)];

		return null;
	};
	M.customMat = (c, f, s, t, p, info) => {
		if (c.mat !== 'acid') return null;
		let col = glowBody(p, f, info, ACID_DEEP, ACID_HOT, c.seed * 3.3, c.paint === 'sac');
		// bubbles
		if (hash3(Math.floor(p[0] * 2.5), Math.floor(p[1] * 2.5), Math.floor(p[2] * 2.5) + c.seed) > 0.86) col = mix(col, [255, 255, 230], 0.6);
		// the membrane: a darker veined skin over the sac edges
		if (c.paint === 'sac' && info.ePx < 0.35 && f !== 'up') col = mix(col, [90, 120, 40], 0.45);
		return [col, 255, mix(col, ACID, 0.4), 255];
	};
	return M;
}
function acidPoses(M) {
	const stand = Object.assign({
		hips: [0, 0, 2], waist: [-6, 0, 0], chest: [8, 0, -3], head: [-10, 6, 4],
		arm_r: [-6, 0, 12], forearm_r: [-20, 0, 0], arm_l: [-6, 0, -14], forearm_l: [-24, 0, 0],
		thigh_r: [-4, 0, 8], shin_r: [6, 0, 0], foot_r: [-2, 0, -6], thigh_l: [2, 0, -8], shin_l: [4, 0, 0], foot_l: [-6, 0, 6],
	}, CLAW('r', 0.7), CLAW('l', 0.7));
	const spit = Object.assign({ waist: [10, 0, 0], chest: [24, 0, 0], head: [6, 0, 0], jaw: [44, 0, 0], arm_r: [20, 0, 30], forearm_r: [-30, 0, 0], arm_l: [20, 0, -30], forearm_l: [-30, 0, 0], thigh_r: [-24, 0, 8], shin_r: [24, 0, 0], thigh_l: [12, 0, -8], shin_l: [10, 0, 0], foot_l: [-18, 0, 0] }, CLAW('r', 1.4), CLAW('l', 1.4));
	return { stand, spit };
}

module.exports = { buildRaidZombie, raidZombiePoses, ARMOUR, shell, cubesOf, buildBabyZombie, babyPoses, buildAcidZombie, acidPoses, glowBody, ACID };

function main() {
	const out = {};
	const sheet = (id, M, poses, title, sub, actions, opts) => {
		M.layout(); M.paint(G.shadeFor(M));
		G.conceptSheet(M, id, title, sub, poses.stand, actions, opts);
		console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}`);
	};
	if (G.WANT('raid_zombie')) {
		const M = buildRaidZombie(); const P = raidZombiePoses(M);
		sheet('raid_zombie', M, P, 'RISEN (RAID ZOMBIE)', 'TORN LINEN, EXPOSED BONE, LOOSE JAW', [{ label: 'CHASE REACH', pose: P.reach, yaw: 40 }, { label: 'ARMOURED VARIANT', pose: P.stand, yaw: 30, hide: () => false }], { hide: ARMOUR });
	}
	if (G.WANT('raid_zombie_baby')) {
		const M = buildBabyZombie(); const P = babyPoses(M);
		sheet('raid_zombie_baby', M, P, 'GRAVE-CHILD (BABY RAID ZOMBIE)', 'NIGHTSHIRT, RAG DOLL, BIG SKULL', [{ label: 'CHASE REACH', pose: P.reach, yaw: 40 }]);
	}
	if (G.WANT('acid_zombie')) {
		const M = buildAcidZombie(); const P = acidPoses(M);
		sheet('acid_zombie', M, P, 'ACID ZOMBIE', 'BLOATED, GLOWING ACID SACS, UNHINGED DRIPPING JAW', [{ label: 'SPIT', pose: P.spit, yaw: 60 }]);
	}
	const U2 = require('./raid_undead_units2.js');
	if (G.WANT('juggernaut_zombie')) {
		const M = U2.buildJuggernaut(); const P = U2.juggernautPoses(M);
		sheet('juggernaut_zombie', M, P, 'JUGGERNAUT', 'SCRAP-IRON PLATES, CHAIN AND SHACKLE, FACE MASK', [{ label: 'CHARGE', pose: P.charge, yaw: 60 }]);
	}
	if (G.WANT('sword_skeleton')) {
		const M = U2.buildSkeleton(); const P = U2.skeletonPoses(M);
		sheet('sword_skeleton', M, P, 'SWORD SKELETON', 'CRACKED BONE, RUSTED NOTCHED BLADE, ROUND SHIELD, TABARD', [{ label: 'SLASH', pose: P.slash, yaw: 40 }]);
	}
	const U3 = require('./raid_undead_units3.js');
	if (G.WANT('cursed_zombie')) {
		const M = U3.buildCursedZombie(); const P = U3.cursedPoses(M);
		sheet('cursed_zombie', M, P, 'CURSED ZOMBIE', 'GRAVE SHROUD, GRAVEBOUND RUNES, SPECTRAL WISPS', [{ label: 'LUNGE', pose: P.lunge, yaw: 40 }]);
	}
	if (G.WANT('pillager_spy')) {
		const M = U3.buildSpy(); const P = U3.spyPoses(M);
		sheet('pillager_spy', M, P, 'PILLAGER SPY', 'HOODED SCOUT, DARK CLOAK, CROSSBOW, SPYGLASS', [{ label: 'AIM', pose: P.aim, yaw: 50 }], { hide: c => c.bone === 'spyglass_hand' });
	}
	if (U3.more) U3.more(sheet);
}
module.exports.main = main;
