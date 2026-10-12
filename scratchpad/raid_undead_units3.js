// v0.16.1 raid undead: Cursed Zombie, Pillager Spy, Powered Zombie Boss, acid glob.
'use strict';
const L = require('./lib_concept_models.js');
const G = require('./gen_raid_undead_models.js');
const U = require('./raid_undead_units.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, vnorm, vadd, vsub, vsc, vlen } = L;
const { rig, BASE, CLAW, FIST, OPEN, SIDES, sxr, bevel, vgrad, blob } = G;
const { shell, glowBody } = U;

const lineD = (p, a, b) => { const ab = vsub(b, a), ap = vsub(p, a); const u = clamp((ap[0] * ab[0] + ap[1] * ab[1] + ap[2] * ab[2]) / (ab[0] ** 2 + ab[1] ** 2 + ab[2] ** 2), 0, 1); return vlen(vsub(p, vadd(a, vsc(ab, u)))); };

// ================================================================ CURSED ZOMBIE
const CURSE = [184, 84, 255], CURSE_HOT = [238, 200, 255];
function buildCursedZombie() {
	const M = new Model('cursed_zombie', { TW: 256 });
	M.pal = { base: [150, 140, 158], dark: [64, 56, 76], bruise: [110, 80, 128], rot: [90, 76, 70], vein: [90, 50, 130] };
	M.clothCol = [150, 144, 128]; M.cloth3Col = [52, 42, 66]; M.hairCol = [26, 22, 30]; M.seed = 13;
	const P = Object.assign({}, BASE, { chestW: 8.0, ribW: 5.6, waistW: 4.2, waistD: 2.6, upperLen: 7.0, foreLen: 6.8, armW: 1.9, foreW: 1.7, thighW: 2.6, shinW: 2.0, headW: 5.4, headH: 7.0, headD: 5.8, neckH: 2.6, neckW: 1.9, fingerLen: 3.4, fingers: 4 });
	rig(M, P, { anatomy: true });
	const A = M.A;
	A.eyeCol = CURSE; A.eyeHot = CURSE_HOT; A.eyeW = 0.7; A.noseHole = true;
	M.eyeHaze = true; M.jawGlow = CURSE;
	M.B.jaw.rot = [14, 0, 0];
	M.wounds = [{ c: [-2.0, A.chestBot + 1.0, -1.6], r: 1.0, bone: true }, { c: [A.shX, A.elbowY - 2, -1.0], r: 0.9, bone: true }];
	// Gravebound runes carved into the skin and burning purple: a ringed sigil over the heart, glyph bars down the arms
	// and shins, tear-lines under the eyes, a band round the neck
	const sig = [1.4, A.chestBot + 4.4, -2.3];
	M.extraFlesh = (c, f, s, t, p, info, col) => {
		let I = 0;
		const P2 = c.paint;
		if ((P2 === 'chest' || P2 === 'ribs' || P2 === 'clavicle') && p[2] < 0) {
			const r = Math.hypot(p[0] - sig[0], p[1] - sig[1]);
			if (Math.abs(r - 1.7) < 0.16) I = 1;
			if (r < 1.4 && (Math.abs(p[0] - sig[0]) < 0.14 || Math.abs((p[1] - sig[1]) + Math.abs(p[0] - sig[0]) * 0.9 - 0.4) < 0.15)) I = 1;
			for (let k = 0; k < 6; k++) { const a = k * Math.PI / 3; const q = [sig[0] + Math.cos(a) * 1.7, sig[1] + Math.sin(a) * 1.7]; if (Math.hypot(p[0] - q[0], p[1] - q[1]) < 0.3) I = 1; }
		}
		if ((P2 === 'fore' || P2 === 'arm' || P2 === 'shin') && (f === 'north' || f === 'east' || f === 'west')) {
			// a column of distinct glyphs (a vertical stroke with one or two angled ticks), spaced out, not stripes
			const cellH = 1.9, cell = Math.floor(p[1] / cellH), v = (p[1] - cell * cellH) / cellH;
			const u = (f === 'north' ? p[0] - (c.from[0] + c.to[0]) / 2 : p[2] - (c.from[2] + c.to[2]) / 2);
			const h = hash3(cell, P2.length * 3 + (p[0] > 0 ? 1 : 0), f.length);
			if (h > 0.45 && v > 0.15 && v < 0.85) {
				if (Math.abs(u) < 0.12) I = 1;
				if (Math.abs(u - (v - 0.5) * (h > 0.7 ? 0.9 : -0.9)) < 0.12 && Math.abs(u) < 0.45) I = 1;
				if (h > 0.8 && Math.abs(v - 0.3) < 0.07 && Math.abs(u) < 0.4) I = 1;
			}
		}
		if (P2 === 'neck' && Math.abs(p[1] - (A.chestTop + 0.6)) < 0.18) I = 1;
		if ((P2 === 'head' || P2 === 'cheek') && f === 'north' && Math.abs(Math.abs(p[0]) - A.eyeX) < 0.16 && p[1] < A.eyeY - A.eyeH && p[1] > A.mouthY + 0.4) I = 1;
		if (I > 0) return [mix(col, CURSE_HOT, 0.75), CURSE, 255];
		if (I === 0 && (P2 === 'chest' || P2 === 'ribs') && p[2] < 0 && Math.abs(Math.hypot(p[0] - sig[0], p[1] - sig[1]) - 1.7) < 0.45) return [mix(col, [60, 30, 80], 0.6)]; // scorched around the cuts
		return null;
	};
	// a grave shroud: a hooded cowl thrown back, the linen torn into a drape, bandage wraps on the limbs
	const top = A.headTop;
	const cowl = M.bone('cowl', 'neck', [0, A.chestTop + 0.4, 2]);
	M.box(cowl, -3.4, A.chestTop - 0.8, 1.0, 3.4, A.hb + 3.4, 4.4, 'cloth3', { paint: 'cowl' });
	M.box(cowl, -2.6, A.hb + 3.2, 2.0, 2.6, A.hb + 5.4, 4.2, 'cloth3', { paint: 'cowl' });
	M.box('head', -A.hw - 0.3, A.eyeY + 1.0, -A.hd / 2 - 0.3, A.hw + 0.3, A.eyeY + 1.9, A.hd / 2 + 0.3, 'cloth', { paint: 'band', tag: 'solid' });
	const shroud = M.bone('shroud', 'chest', [0, A.chestTop - 0.5, 2.3]);
	M.box(shroud, -4.6, A.hipY - 9.5, 2.2, 4.6, A.chestTop + 0.2, 2.75, 'cloth3', { paint: 'shroud', tag: 'hem', seed: 40 });
	for (const [s] of SIDES) M.box('chest', ...[sxr(s, 2.6, 4.9)[0], A.chestTop - 2.6, -2.8, sxr(s, 2.6, 4.9)[1], A.chestTop + 0.4, 2.8], 'cloth3', { paint: 'mantle' });
	M.holes = [{ c: [3, A.hipY - 6, 2.5], r: 1.2 }, { c: [-2, A.hipY - 2, 2.5], r: 0.8 }];
	// a ragged burial wrap round the hips and loose bandages on the forearms / shins
	shell(M, 'hips', M.B.hips.cubes[0], 0.3, 'cloth', { tag: 'solid' });
	const wrap = M.bone('wrap_f', 'hips', [0, A.pelvisTop, -2]);
	M.box(wrap, -2.6, A.hipY - 5.2, -2.25, 2.6, A.pelvisTop + 0.3, -1.85, 'cloth', { paint: 'wrap', tag: 'hem', seed: 41 });
	for (const [s, n] of SIDES) {
		for (let i = 0; i < 3; i++) M.box(`forearm_${n}`, s * A.shX - 1.2, A.wristY + 0.6 + i * 1.6, -1.3, s * A.shX + 1.2, A.wristY + 1.2 + i * 1.6, 1.3, 'cloth', { tag: 'solid', rot: [0, 0, (i - 1) * 9 * s], pivot: [s * A.shX, A.wristY + 0.9 + i * 1.6, 0] });
		M.box(`shin_${n}`, s * A.legX - 1.35, A.ankleY + 0.3, -1.4, s * A.legX + 1.35, A.ankleY + 2.6, 1.4, 'cloth', { tag: 'solid' });
		// broken shackles on the wrists, a link or two dangling
		M.box(`forearm_${n}`, s * A.shX - 1.4, A.wristY - 0.1, -1.5, s * A.shX + 1.4, A.wristY + 0.8, 1.5, 'iron_d');
		M.cbox(`forearm_${n}`, [s * (A.shX + 1.3), A.wristY - 0.6, 0], [0.4, 1.2, 0.9], 'iron_d');
	}
	// spectral wisps trailing off the shoulders and back (dangles; emissive, fading out)
	[[-2.8, A.chestTop - 0.4, 2.6, 9], [2.2, A.chestTop - 1.6, 2.7, 11], [0.2, A.chestBot + 1.8, 2.8, 8]].forEach(([x, y, z, len], i) => {
		const w = M.bone(`wisp_${i}`, i < 2 ? 'chest' : 'waist', [x, y, z]);
		M.box(w, x - 0.3, y - len, z + 0.1, x + 0.3, y + 0.3, z + 0.4, 'wisp', { seed: 50 + i });
		M.box(w, x - 0.18 + (i - 1) * 0.5, y - len * 1.3, z + 0.3, x + 0.18 + (i - 1) * 0.5, y - len * 0.55, z + 0.5, 'wisp', { seed: 60 + i });
	});
	const hair = M.bone('hair', 'head', [0, top, 2]);
	[[-2.4, 1.8, 6.2, 0.7], [2.3, 1.4, 7.0, 0.7], [0.4, 2.8, 5.6, 0.7]].forEach(([x, z, len, w], i) => M.box(hair, x - w / 2, top - len, z - w / 2, x + w / 2, top + 0.4, z + w / 2, 'hair', { tag: 'hem', seed: 70 + i }));
	M.customMat = (c, f, s, t, p, info) => {
		if (c.mat !== 'wisp') return null;
		const along = clamp((c.to[1] - p[1]) / (c.to[1] - c.from[1]), 0, 1);
		const n = fbm(p[0] * 0.8 + c.seed, p[1] * 0.5, p[2] * 0.8, 3);
		const col = mix(CURSE_HOT, CURSE, along * 0.8 + n * 0.3);
		const ga = Math.round(clamp(130 * (1 - along * 0.85) * (0.6 + 0.6 * n), 20, 150));
		return [col, 255, col, ga];
	};
	return M;
}
function cursedPoses(M) {
	const stand = Object.assign({
		waist: [6, 0, 0], chest: [10, -6, -5], head: [-8, 12, -5], jaw: [6, 0, 0],
		arm_r: [-8, 0, 2], forearm_r: [-16, 0, 0], arm_l: [-14, 0, -6], forearm_l: [-24, 0, 0],
		thigh_r: [-2, 0, 3], shin_r: [4, 0, 0], thigh_l: [2, 0, -3], shin_l: [2, 0, 0],
	}, CLAW('r', 1.2), CLAW('l', 1.2));
	const lunge = Object.assign({
		waist: [20, 0, 0], chest: [18, 0, 0], head: [-34, 0, 0], jaw: [34, 0, 0],
		arm_r: [-150, 0, 12], forearm_r: [-20, 0, 0], arm_l: [-60, 0, -30], forearm_l: [-30, 0, 0],
		thigh_r: [-50, 0, 3], shin_r: [40, 0, 0], foot_r: [6, 0, 0], thigh_l: [26, 0, -3], shin_l: [30, 0, 0], foot_l: [-30, 0, 0],
	}, CLAW('r', 1.6), CLAW('l', 1.4));
	return { stand, lunge };
}

// ================================================================ PILLAGER SPY
const SPY_EYE = [196, 120, 255];
function buildSpy() {
	const M = new Model('pillager_spy', { TW: 256 });
	M.pal = { base: [150, 156, 158], dark: [86, 90, 94], bruise: [120, 120, 130], rot: [120, 124, 120], vein: [110, 114, 120] };
	M.cloth3Col = [44, 40, 58]; M.cloth2Col = [52, 50, 56]; M.clothCol = [70, 64, 82]; M.leatherCol = [76, 54, 38]; M.woodCol = [82, 58, 40]; M.seed = 17;
	M.noWounds = true; M.noBlood = true;
	const P = Object.assign({}, BASE, { chestW: 7.6, ribW: 6.4, waistW: 5.2, upperLen: 6.6, foreLen: 6.2, armW: 2.1, foreW: 2.0, thighLen: 6.8, shinLen: 6.6, thighW: 2.7, shinW: 2.3, headW: 5.0, headH: 7.0, headD: 5.4, neckH: 1.4, fingers: 4 });
	rig(M, P, { anatomy: true });
	const A = M.A;
	A.eyeCol = SPY_EYE; A.eyeHot = [240, 220, 255]; A.eyeW = 0.65; A.eyeH = 0.42; A.eyeY = A.hb + 4.2;
	// the illager face: a long hooked nose, one heavy brow, a narrow scowl
	M.box('head', -0.55, A.eyeY - 3.4, A.faceZ - 1.25, 0.55, A.eyeY - 0.3, A.faceZ + 0.2, 'flesh', { paint: 'nose' });
	M.box('head', -0.45, A.eyeY - 3.7, A.faceZ - 1.05, 0.45, A.eyeY - 3.2, A.faceZ - 0.3, 'flesh', { paint: 'nose' });
	M.box('head', -A.hw + 0.3, A.eyeY + 0.45, A.faceZ - 0.55, A.hw - 0.3, A.eyeY + 1.0, A.faceZ + 0.2, 'hair', { paint: 'brow' });
	// hood: sides, crown and back around the head, the point falling down the back; a scarf over the mouth
	const hood = M.bone('hood', 'head', [0, A.headTop, 1]);
	M.box(hood, -A.hw - 0.55, A.hb + 0.4, -A.hd / 2 - 0.6, A.hw + 0.55, A.headTop + 0.7, A.hd / 2 + 0.6, 'cloth3', { paint: 'hood' });
	// the hood's peak juts forward over the brow (the face sits in its shadow), the sides drape onto the shoulders
	M.box(hood, -A.hw - 0.3, A.headTop - 0.6, -A.hd / 2 - 1.5, A.hw + 0.3, A.headTop + 0.9, -A.hd / 2 + 0.6, 'cloth3', { paint: 'hood_peak', tag: 'solid', rot: [-12, 0, 0], pivot: [0, A.headTop, -A.hd / 2] });
	M.box(hood, -0.6, A.headTop + 0.6, -A.hd / 2 - 1.2, 0.6, A.headTop + 1.4, A.hd / 2 - 0.4, 'cloth3', { paint: 'hood_peak', tag: 'solid' });
	for (const [s] of SIDES) M.box(hood, ...[sxr(s, A.hw + 0.2, A.hw + 1.0)[0], A.hb - 1.6, -A.hd / 2 - 0.2, sxr(s, A.hw + 0.2, A.hw + 1.0)[1], A.eyeY + 1.0, A.hd / 2 + 0.4], 'cloth3', { paint: 'hood_side', tag: 'solid' });
	const hoodTip = M.bone('hood_tip', 'hood', [0, A.headTop - 0.6, A.hd / 2 + 0.5]);
	M.box(hoodTip, -1.6, A.headTop - 5.0, A.hd / 2 + 0.3, 1.6, A.headTop - 0.3, A.hd / 2 + 1.2, 'cloth3', { paint: 'hood', tag: 'hem', seed: 80 });
	M.box('head', -A.hw - 0.25, A.hb - 0.4, A.faceZ - 0.5, A.hw + 0.25, A.eyeY - 1.6, A.hd / 2 - 0.4, 'cloth2', { paint: 'scarf', tag: 'solid' });
	M.box('jaw', -A.hw + 0.2, A.hb - 0.7, A.faceZ - 0.1, A.hw - 0.2, A.hb + 1.7, A.hd / 2 - 0.7, 'cloth2', { paint: 'scarf', tag: 'solid' });
	// cloak: a mantle over the shoulders, a long ragged cape (dangle), front panels open over the jerkin
	M.box('chest', -4.6, A.chestTop - 2.4, -2.8, 4.6, A.chestTop + 0.9, 2.9, 'cloth3', { paint: 'mantle' });
	const cape = M.bone('cape', 'chest', [0, A.chestTop - 0.4, 2.6]);
	M.box(cape, -4.2, A.kneeY - 3.0, 2.4, 4.2, A.chestTop + 0.4, 3.0, 'cloth3', { paint: 'cape', tag: 'hem', seed: 81 });
	for (const [s, n] of SIDES) {
		const fl = M.bone(`cloak_${n}`, 'chest', [s * 3.4, A.chestTop - 0.4, -2.2]);
		M.box(fl, ...[sxr(s, 2.6, 4.3)[0], A.hipY - 3.4, -2.6, sxr(s, 2.6, 4.3)[1], A.chestTop + 0.2, -2.15], 'cloth3', { paint: 'cloak', tag: 'hem', seed: 82 + s });
	}
	// leather jerkin, bandolier of bolts, belt, pouches, the spyglass at the hip
	for (const c of M.B.chest.cubes.filter(c => c.mat === 'flesh' && (c.paint === 'ribs' || c.paint === 'chest'))) shell(M, 'chest', c, 0.22, 'leather_d', { paint: 'jerkin' });
	shell(M, 'waist', M.B.waist.cubes[0], 0.25, 'leather_d', { paint: 'jerkin' });
	M.stick('chest', [-3.3, A.chestTop - 0.4, -2.65], [6.0, -(A.chestTop - A.chestBot) + 0.6, 0], 9.0, [1.0, 0.4], 'leather');
	for (let i = 0; i < 4; i++) {
		const q = [-2.6 + i * 1.3, A.chestTop - 1.4 - i * 1.25, -2.95];
		M.box('chest', q[0] - 0.2, q[1] - 0.2, q[2] - 0.2, q[0] + 0.2, q[1] + 1.8, q[2] + 0.1, 'wood_d');
		M.box('chest', q[0] - 0.3, q[1] + 1.6, q[2] - 0.25, q[0] + 0.3, q[1] + 2.2, q[2] + 0.15, 'fletch');
	}
	M.box('hips', -3.8, A.pelvisTop - 1.0, -2.1, 3.8, A.pelvisTop + 0.1, 2.1, 'leather', { paint: 'belt' });
	M.cbox('hips', [-2.9, A.pelvisTop - 1.6, -1.6], [1.4, 1.6, 1.0], 'leather', { paint: 'pouch' });
	M.cbox('hips', [2.6, A.pelvisTop - 1.6, 1.4], [1.3, 1.4, 1.0], 'leather', { paint: 'pouch' });
	const sgb = M.bone('spyglass_belt', 'hips', [3.4, A.pelvisTop - 1, 0]);
	M.box(sgb, 3.4, A.pelvisTop - 4.2, -0.5, 4.2, A.pelvisTop - 0.4, 0.5, 'brass', { paint: 'spyglass' });
	M.box(sgb, 3.3, A.pelvisTop - 1.6, -0.6, 4.3, A.pelvisTop - 0.9, 0.6, 'brass_d');
	const sgh = M.bone('spyglass_hand', 'hand_l', [A.shX, A.knuckY + 0.6, 0]);
	M.box(sgh, A.shX - 0.42, A.knuckY + 0.2, -4.0, A.shX + 0.42, A.knuckY + 1.0, 0.8, 'brass', { paint: 'spyglass' });
	M.box(sgh, A.shX - 0.52, A.knuckY + 0.1, -4.3, A.shX + 0.52, A.knuckY + 1.1, -3.6, 'brass_d');
	// trousers, wrapped boots, bracers
	shell(M, 'hips', M.B.hips.cubes[0], 0.25, 'cloth2', { tag: 'solid' });
	for (const [s, n] of SIDES) {
		shell(M, `thigh_${n}`, M.B[`thigh_${n}`].cubes[0], 0.25, 'cloth2', { tag: 'solid' });
		shell(M, `shin_${n}`, M.B[`shin_${n}`].cubes[0], 0.3, 'leather', { paint: 'boot' });
		shell(M, `foot_${n}`, M.B[`foot_${n}`].cubes[0], 0.25, 'leather', { tag: 'sole', paint: 'boot' });
		M.box(`shin_${n}`, s * A.legX - 1.5, A.kneeY - 2.4, -1.6, s * A.legX + 1.5, A.kneeY - 1.6, 1.6, 'leather_d');
		M.box(`forearm_${n}`, s * A.shX - 1.3, A.wristY + 0.1, -1.4, s * A.shX + 1.3, A.wristY + 3.6, 1.4, 'leather', { paint: 'bracer' });
		for (const c of M.B[`arm_${n}`].cubes.filter(c => c.mat === 'flesh')) shell(M, `arm_${n}`, c, 0.25, 'cloth', { tag: 'solid' });
		shell(M, `hand_${n}`, M.B[`hand_${n}`].cubes[0], 0.15, 'leather_d', { tag: 'solid' });
	}
	// a quiver on the back hip
	const qv = M.bone('quiver', 'hips', [-2.6, A.pelvisTop, 2.0]);
	M.box(qv, -3.6, A.hipY - 3.0, 1.6, -1.8, A.pelvisTop + 2.0, 3.2, 'leather', { rot: [0, 0, -20], pivot: [-2.6, A.pelvisTop, 2.4] });
	// crossbow in the right hand: stock along -Z, the prod across the front, string, stirrup and a loaded bolt
	const cb = M.bone('crossbow', 'hand_r', [-A.shX, A.knuckY + 0.6, 0]);
	const [x, y] = [-A.shX, A.knuckY + 0.6];
	M.box(cb, x - 0.45, y - 0.55, -6.4, x + 0.45, y + 0.35, 2.6, 'wood', { paint: 'stock' });
	M.box(cb, x - 0.55, y - 1.4, 1.4, x + 0.55, y + 0.35, 3.2, 'wood', { paint: 'stock' });
	M.box(cb, x - 0.3, y - 1.3, -1.4, x + 0.3, y - 0.5, -0.4, 'iron_d');
	for (const [s] of SIDES) M.stick(cb, [x, y + 0.2, -5.6], [s, 0, 0.32], 4.0, [0.5, 0.5], 'wood_d', { seed: 90 });
	for (const [s] of SIDES) M.stick(cb, [x + s * 3.85, y + 0.2, -4.4], [-s * 3.85, 0, 3.6], 5.3, [0.14, 0.14], 'string');
	M.box(cb, x - 0.9, y - 0.35, -7.4, x + 0.9, y + 0.2, -6.3, 'iron_d');
	M.box(cb, x - 0.12, y + 0.35, -6.8, x + 0.12, y + 0.6, -1.0, 'wood_d');
	M.box(cb, x - 0.2, y + 0.32, -7.2, x + 0.2, y + 0.65, -6.6, 'iron_clean');
	M.customMat = (c, f, s, t, p, info) => {
		if (c.mat === 'brass' || c.mat === 'brass_d') {
			const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2);
			let col = mix(c.mat === 'brass' ? [130, 96, 36] : [80, 58, 26], c.mat === 'brass' ? [226, 186, 96] : [150, 110, 50], n);
			if (info.ePx < 0.2) col = mix(col, [255, 230, 160], 0.5);
			if (c.paint === 'spyglass' && Math.abs(((p[1] + p[2]) * 1.4) % 1.0) < 0.12) col = mul(col, 0.6);
			return [vgrad(col, f, t, 0.2), 255, null];
		}
		if (c.mat === 'string') return [[200, 190, 170], 255, null];
		if (c.mat === 'fletch') return [mix([60, 50, 50], [150, 140, 130], fbm(p[0] * 3, p[1] * 3, p[2] * 3, 2)), 255, null];
		if (c.paint === 'hood' && c.bone === 'hood' && f !== 'up') {
			// open face: cut the hood away in front of the face, dark lining inside the opening
			const r = G.clothMat(M, Object.assign({}, c, { tag: 'solid' }), f, s, t, p, info, M.cloth3Col);
			if (f === 'north' && Math.abs(p[0]) < A.hw - 0.15 && p[1] < A.headTop - 0.4) return [[0, 0, 0], 0, null];
			if (f === 'down' && Math.abs(p[0]) < A.hw - 0.15 && p[2] < 0) return [[0, 0, 0], 0, null];
			return r;
		}
		if (c.paint === 'hood' && f === 'south' && c.bone === 'hood') return null;
		if (c.paint === 'brow') return [[34, 32, 36], 255, null];
		if (c.mat === 'flesh' && (c.paint === 'head' || c.paint === 'nose' || c.paint === 'cheek') && f === 'north' && p[1] > A.eyeY - 1.2) {
			// the hood's shadow over the upper face (the eyes still glint)
			const r = G.flesh(M, c, f, s, t, p, info);
			if (r[2]) return r;
			return [mul(r[0], 0.5 - 0.25 * clamp((p[1] - A.eyeY) / 2, 0, 1)), 255, null];
		}
		if (c.mat === 'flesh' && M.noWounds && c.paint === 'nose') { const r = G.flesh(M, c, f, s, t, p, info); return [mul(r[0], 0.95), 255, null]; }
		return null;
	};
	M.wounds = [];
	return M;
}
function spyPoses(M) {
	const stand = Object.assign({
		waist: [4, 0, 0], chest: [4, 0, 0], head: [-4, 0, 0],
		arm_r: [-34, 0, 4], forearm_r: [-38, 0, 0], arm_l: [-6, 0, -4], forearm_l: [-16, 0, 0],
		thigh_r: [-4, 0, 3], shin_r: [6, 0, 0], thigh_l: [3, 0, -3], shin_l: [3, 0, 0], foot_l: [-4, 0, 0],
	}, FIST('r'), CLAW('l', 0.5));
	L.aimBone(M, stand, 'crossbow', [0, 0, -1], [0.15, -0.55, -1]);
	const aim = Object.assign({
		waist: [0, -26, 0], chest: [6, -22, 0], head: [-4, 40, 0],
		arm_r: [-86, 30, -6], forearm_r: [-26, 0, 0], arm_l: [-80, 48, 40], forearm_l: [-56, 0, 0],
		thigh_r: [10, 0, 6], shin_r: [16, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-24, 0, -6], shin_l: [24, 0, 0],
	}, FIST('r'), FIST('l'));
	L.aimBone(M, aim, 'crossbow', [0, 0, -1], [0, 0.02, -1]);
	return { stand, aim };
}

// ================================================================ POWERED ZOMBIE BOSS
// A huge grave champion. Everything that glows (eyes, mouth, the fissures in its skin, every accent) is painted in WHITE
// luminance only: the renderer tints the emissive pass with the power's colour, so one texture serves all 26 powers.
// Accent sets (bones acc_*, hidden unless the power uses them; the Java side maps power -> set + colour):
//   acc_shards (crystal / ice / earth / energy / density), acc_horns (fire / shadow / strength / sonic),
//   acc_coils (lightning / magnetism / laser), acc_vines (plants / regeneration / water), acc_orbit (telekinesis /
//   gravity / teleport / wind / light: stones orbiting the shoulders), acc_wings (flight), acc_spines (speed / spider /
//   elasticity / size / shockwave).
const ACCENTS = ['acc_shards', 'acc_horns', 'acc_coils', 'acc_vines', 'acc_orbit', 'acc_wings', 'acc_spines'];
function buildBoss() {
	const M = new Model('powered_zombie', { TW: 512 });
	M.pal = { base: [120, 116, 126], dark: [48, 44, 56], bruise: [92, 70, 104], rot: [86, 74, 60], vein: [60, 40, 76] };
	M.ironCol = [84, 82, 84]; M.leatherCol = [56, 40, 32]; M.clothCol = [96, 88, 76]; M.cloth3Col = [46, 36, 52]; M.seed = 23;
	const P = Object.assign({}, BASE, {
		footH: 1.8, footLen: 5.6, footW: 3.6, shinLen: 7.0, shinW: 3.4, shinD: 3.6, thighLen: 7.2, thighW: 4.2, thighD: 4.4, legX: 2.7,
		pelvisH: 3.6, pelvisW: 9.6, pelvisD: 5.4, waistH: 3.6, waistW: 8.0, waistD: 5.0, chestH: 10.0, chestW: 13.6, ribW: 10.4, chestD: 7.0,
		neckH: 1.4, neckW: 3.6, headW: 5.8, headH: 6.6, headD: 6.0, armW: 3.6, armD: 3.8, upperLen: 8.6, foreLen: 8.6, foreW: 3.8, handLen: 3.0, fingerLen: 3.8,
		fingers: 4, armOut: 8,
	});
	rig(M, P, { anatomy: true });
	const A = M.A;
	const W = [255, 255, 255];
	A.eyeCol = [235, 235, 235]; A.eyeHot = [255, 255, 255]; A.eyeW = 0.9; A.eyeH = 0.5;
	M.jawGlow = [230, 230, 230];
	M.B.jaw.rot = [10, 0, 0];
	// heavy muscle: traps, pecs, lats, forearm mass
	M.box('chest', -5.6, A.chestTop - 2.4, -1.6, 5.6, A.chestTop + 2.0, 3.6, 'flesh', { paint: 'trap' });
	M.box('chest', -6.0, A.chestBot + 5.0, -4.1, 6.0, A.chestTop - 1.4, -3.0, 'flesh', { paint: 'pec' });
	for (const [s, n] of SIDES) M.box(`forearm_${n}`, s * A.shX - 2.4, A.elbowY - 5.0, -2.2, s * A.shX + 2.4, A.elbowY + 0.4, 2.2, 'flesh', { paint: 'fore' });
	M.wounds = [{ c: [2.6, A.chestBot + 2.0, -3.2], r: 1.6, bone: true }, { c: [-A.shX, A.elbowY + 3, 1.8], r: 1.0 }];
	// glowing fissures: a branching crack network through the skin (white, tinted in game)
	M.extraFlesh = (c, f, s, t, p, info, col) => {
		const cr = Math.abs(fbm(p[0] * 0.12 + 13, p[1] * 0.09, p[2] * 0.12, 3) - 0.5);
		const mask = fbm(p[0] * 0.12 + 40, p[1] * 0.12, p[2] * 0.12, 2);
		if (mask > 0.42 && cr < 0.022) return [mix([255, 255, 255], [190, 190, 190], cr / 0.022), W, 255];
		if (mask > 0.42 && cr < 0.05) return [mix(col, [30, 22, 30], 0.6 * (1 - (cr - 0.022) / 0.028))];
		return null;
	};
	// grave-champion kit: an iron crown, a gorget, one great pauldron with a skull, chains, a burial-shroud loincloth, greaves
	const hb = A.hb, top = A.headTop;
	const crown = M.bone('crown', 'head', [0, top - 1, 0]);
	M.box(crown, -A.hw - 0.4, top - 1.6, -A.hd / 2 - 0.4, A.hw + 0.4, top - 0.4, A.hd / 2 + 0.4, 'iron', { paint: 'crown' });
	[[-2.4, -A.hd / 2 - 0.2, 2.2], [0, -A.hd / 2 - 0.3, 3.0], [2.4, -A.hd / 2 - 0.2, 2.2], [-A.hw - 0.2, 0, 1.8], [A.hw + 0.2, 0, 1.8], [-1.6, A.hd / 2, 1.6], [1.6, A.hd / 2, 1.6]]
		.forEach(([x, z, h], i) => M.stick(crown, [x, top - 0.6, z], [x * 0.08, 1, z * 0.06], h, 0.7, 'iron_d', { seed: 100 + i }));
	M.box('chest', -4.2, A.chestTop - 1.2, -4.0, 4.2, A.chestTop + 1.6, 3.4, 'iron', { paint: 'gorget' });
	const pl = M.bone('pauldron_l', 'arm_l', [A.shX, A.shY, 0]);
	M.box(pl, A.shX - 2.8, A.shY + 0.4, -3.2, A.shX + 3.0, A.shY + 3.0, 3.2, 'iron', { rot: [0, 0, 14], pivot: [A.shX, A.shY + 2, 0] });
	M.box(pl, A.shX - 1.0, A.shY - 2.0, -3.0, A.shX + 3.4, A.shY + 0.6, 3.0, 'iron_d', { rot: [0, 0, 26], pivot: [A.shX, A.shY, 0] });
	M.cbox(pl, [A.shX + 2.6, A.shY + 0.2, -1.6], [1.8, 2.0, 1.8], 'bone', { paint: 'skull', rot: [0, 0, 26], pivot: [A.shX, A.shY, 0] });
	for (const z of [-4.2, 4.4]) M.stick('chest', [A.shX * 0.9, A.chestTop - 0.6, z], [-(A.shX * 1.8), -(A.chestTop - A.chestBot) + 1, 0], 15, [1.1, 0.8], 'chain');
	M.box('hips', -5.4, A.pelvisTop - 1.6, -3.2, 5.4, A.pelvisTop + 0.4, 3.2, 'leather', { paint: 'belt' });
	shell(M, 'hips', M.B.hips.cubes[0], 0.3, 'cloth3', { tag: 'solid' });
	const lf = M.bone('loin_f', 'hips', [0, A.pelvisTop - 1.4, -3.2]);
	M.box(lf, -2.6, A.hipY - 7.4, -3.55, 2.6, A.pelvisTop - 1.4, -3.15, 'cloth', { paint: 'loin', tag: 'hem', seed: 110 });
	const lb = M.bone('loin_b', 'hips', [0, A.pelvisTop - 1.4, 3.2]);
	M.box(lb, -3.6, A.hipY - 8.4, 3.15, 3.6, A.pelvisTop - 1.4, 3.55, 'cloth3', { paint: 'loin', tag: 'hem', seed: 111 });
	M.holes = [{ c: [1.2, A.hipY - 5, -3.4], r: 0.8 }];
	for (const [s, n] of SIDES) {
		M.box(`shin_${n}`, s * A.legX - 2.0, A.ankleY + 0.8, -2.2, s * A.legX + 2.0, A.kneeY + 0.4, 0.2, 'iron', { paint: 'greave' });
		M.box(`forearm_${n}`, s * A.shX - 2.6, A.wristY + 0.2, -2.6, s * A.shX + 2.6, A.wristY + 1.3, 2.6, 'iron_d');
		M.cbox(`forearm_${n}`, [s * (A.shX + 2.4), A.wristY - 0.6, 0], [0.6, 1.6, 1.2], 'iron_d');
	}
	// ---- accent sets (all glow white = power colour)
	const g = (bone, parent, pivot, rot) => M.bone(bone, parent, pivot, rot || [0, 0, 0]);
	const shard = (bone, base, dir, len, w, seed) => {
		const d = vnorm(dir);
		M.stick(bone, base, d, len * 0.7, w, 'pglow', { seed, tag: 'core' });
		M.stick(bone, vadd(base, vsc(d, len * 0.6)), d, len * 0.42, w * 0.55, 'pglow', { seed: seed + 1, tag: 'core' });
	};
	// shards: from the back, shoulders and forearms
	g('acc_shards', 'chest', [0, A.chestTop, 3]);
	[[-3.2, A.chestTop - 1.0, 3.2, [-0.5, 1, 0.8], 6.5, 1.8], [3.0, A.chestTop - 2.0, 3.2, [0.5, 1, 0.8], 5.5, 1.6], [0, A.chestBot + 4.5, 3.4, [0, 0.6, 1], 4.5, 1.4], [-1.6, A.chestBot + 1.8, 3.2, [-0.3, 0.3, 1], 3.2, 1.0], [4.6, A.chestTop + 0.6, 1.0, [0.8, 1, 0.2], 4.0, 1.2], [-4.8, A.chestTop + 0.4, 0.8, [-0.8, 1, 0.2], 4.6, 1.3]]
		.forEach(([x, y, z, d, len, w], i) => shard('acc_shards', [x, y, z], d, len * 1.45, w * 1.35, 200 + i * 3));
	for (const [s, n] of SIDES) { const b = g(`acc_shards_${n}`, `forearm_${n}`, [s * A.shX, A.elbowY, 0]); shard(b, [s * (A.shX + 1.6), A.elbowY - 2.5, 0.4], [s, 0.5, 0.4], 3.6, 1.0, 230 + s); shard(b, [s * (A.shX + 1.4), A.elbowY - 5, -0.3], [s, 0.3, -0.2], 2.6, 0.8, 240 + s); }
	// horns: two great curling horns from the temples, a ridge of spikes down the spine
	g('acc_horns', 'head', [0, top - 2, 0]);
	for (const [s] of SIDES) {
		const pts = [[s * (A.hw - 0.4), top - 2.0, -0.6], [s * (A.hw + 1.6), top - 0.6, -0.2], [s * (A.hw + 2.6), top + 1.6, 0.6], [s * (A.hw + 2.4), top + 3.8, 1.6], [s * (A.hw + 1.4), top + 5.2, 1.4]];
		const cubes = M.tube('acc_horns', pts, [[1.7, 1.7], [1.4, 1.4], [1.1, 1.1], [0.7, 0.7]], 'horn', { seed: 250 });
		cubes[cubes.length - 1].mat = 'pglow';
	}
	const hs = g('acc_horns_back', 'chest', [0, A.chestTop, 3]);
	for (let i = 0; i < 4; i++) M.stick(hs, [0, A.chestTop - 1.0 - i * 2.4, 3.4], [0, 0.6, 1], 2.6 - i * 0.3, 0.9, i % 2 ? 'horn' : 'pglow', { seed: 260 + i });
	// coils: three rods on the back with glowing rings, rings on the forearms
	g('acc_coils', 'chest', [0, A.chestTop, 3.5]);
	[[-3.0, 0.15], [0, 0], [3.0, -0.15]].forEach(([x, lean], i) => {
		const h = 7.0 - Math.abs(x) * 0.5;
		M.box('acc_coils', x - 0.5, A.chestBot + 3, 3.4, x + 0.5, A.chestBot + 3 + h, 4.4, 'iron_d', { rot: [-10, 0, lean * 40], pivot: [x, A.chestBot + 3, 3.9] });
		for (let k = 0; k < 3; k++) M.box('acc_coils', x - 1.0, A.chestBot + 4.5 + k * 1.8, 2.9, x + 1.0, A.chestBot + 5.1 + k * 1.8, 4.9, 'pglow', { rot: [-10, 0, lean * 40], pivot: [x, A.chestBot + 3, 3.9], seed: 270 + k, tag: 'core' });
		M.cbox('acc_coils', [x + lean * 4, A.chestBot + 3.4 + h, 4.6], [1.3, 1.3, 1.3], 'pglow', { seed: 280 + i, tag: 'core' });
	});
	for (const [s, n] of SIDES) { const b = g(`acc_coils_${n}`, `forearm_${n}`, [s * A.shX, A.elbowY, 0]); for (let k = 0; k < 2; k++) M.box(b, s * A.shX - 2.7, A.elbowY - 3 - k * 2.2, -2.7, s * A.shX + 2.7, A.elbowY - 2.4 - k * 2.2, 2.7, 'pglow', { seed: 290 + k }); }
	// vines: thorned vines spiralling the arms and across the chest, glowing buds
	g('acc_vines', 'chest', [0, A.chestBot, 0]);
	M.tube('acc_vines', [[-5.6, A.chestTop - 1, -3.8], [-2.0, A.chestTop - 3.5, -4.2], [2.0, A.chestBot + 3.0, -4.0], [5.0, A.chestBot + 0.5, -3.0], [5.6, A.chestBot + 2, 2.6], [1.0, A.chestBot + 6, 3.8], [-4, A.chestTop - 1, 3.6]], 0.8, 'vine', { seed: 300 });
	[[-2.0, A.chestTop - 3.5, -4.6], [5.2, A.chestBot + 0.6, -3.3], [1.0, A.chestBot + 6, 4.2]].forEach((q, i) => blob(M, 'acc_vines', q, [1.2, 1.2, 1.2], 'pglow', { seed: 310 + i }));
	for (const [s, n] of SIDES) {
		const b = g(`acc_vines_${n}`, `arm_${n}`, [s * A.shX, A.shY, 0]);
		const sx = s * A.shX;
		M.tube(b, [[sx + s * 1.9, A.shY + 0.5, -1.5], [sx - s * 0.5, A.shY - 3, -2.1], [sx - s * 2.0, A.elbowY + 2, 0.5], [sx + s * 0.5, A.elbowY - 0.2, 2.1]], 0.7, 'vine', { seed: 320 + s });
		for (let k = 0; k < 3; k++) M.stick(b, [sx + s * 1.9, A.shY - 1.5 - k * 2.2, -0.5 + k], [s, 0.3, -0.3], 1.1, 0.35, 'horn', { seed: 330 + k });
		blob(M, b, [sx - s * 2.1, A.elbowY + 2, 0.5], [1.0, 1.0, 1.0], 'pglow', { seed: 335 + s });
	}
	// orbit: rune stones floating around the shoulders on a rotating ring
	const orb = g('acc_orbit', 'chest', [0, A.chestTop + 1.5, 0]);
	for (let i = 0; i < 6; i++) {
		const a = i * Math.PI / 3 + 0.3, r = 9.5;
		const c = [Math.cos(a) * r, A.chestTop + 1.5 + Math.sin(i * 2.1) * 1.2, Math.sin(a) * r];
		M.cbox(orb, c, [2.8, 2.4, 2.8], 'stone', { seed: 340 + i, rot: [i * 17, i * 31, i * 11], pivot: c });
		M.cbox(orb, c, [1.2, 1.2, 3.2], 'pglow', { seed: 350 + i, rot: [i * 17, i * 31, i * 11], pivot: c, tag: 'core' });
	}
	// wings: tattered bone-and-membrane wings folded on the back
	g('acc_wings', 'chest', [0, A.chestTop - 2, 3.4]);
	for (const [s, n] of SIDES) {
		const wb = g(`wing_${n}`, 'acc_wings', [s * 2.0, A.chestTop - 2, 3.6], [6, s * -38, s * -26]);
		const root = [s * 2.0, A.chestTop - 2, 3.6];
		M.stick(wb, root, [s, 0.9, 0.3], 9, 0.9, 'bone', { seed: 360 });
		const tip = vadd(root, vsc(vnorm([s, 0.9, 0.3]), 9));
		for (let k = 0; k < 3; k++) M.stick(wb, tip, [s * (0.4 - k * 0.15), -1, 0.15], 8 - k * 1.5, 0.45, 'bone', { seed: 361 + k });
		// tattered membrane panels fanning between the finger bones (thin flat sticks hanging from the wrist)
		for (let k = 0; k < 4; k++) {
			const a = k / 3, len = 7.5 - k * 1.2;
			const dir = [s * (0.45 - a * 0.65), -1, 0.15];
			M.stick(wb, vadd(tip, [-s * 0.6 * k, -0.3, 0]), dir, len, [2.6, 0.22], 'membrane', { seed: 370 + s * 10 + k });
		}
		M.stick(wb, root, vnorm(vsub(vadd(tip, [s * 1, -6, 0]), root)), 8.5, [3.0, 0.22], 'membrane', { seed: 390 + s });
	}
	// spines: swept-back blades on the forearms, shoulders and the back of the head
	for (const [s, n] of SIDES) {
		const b = g(`acc_spines_${n}`, `forearm_${n}`, [s * A.shX, A.elbowY, 0]);
		for (let k = 0; k < 3; k++) M.stick(b, [s * (A.shX + 1.6), A.elbowY - 1 - k * 2.4, 1.0], [s * 0.5, 0.6, 1], 3.2 - k * 0.6, [0.3, 1.0], 'pglow', { seed: 380 + k, tag: 'core' });
		const sb = g(`acc_spines_sh_${n}`, `arm_${n}`, [s * A.shX, A.shY, 0]);
		for (let k = 0; k < 2; k++) M.stick(sb, [s * (A.shX + 0.8), A.shY + 1.2, -0.5 + k * 1.6], [s * 0.6, 0.5, 1], 4 - k, [0.3, 1.1], 'pglow', { seed: 390 + k, tag: 'core' });
	}
	const hsb = g('acc_spines_head', 'head', [0, top, 0]);
	for (let k = 0; k < 3; k++) M.stick(hsb, [(k - 1) * 1.6, top - 0.6, 1.5], [(k - 1) * 0.3, 0.5, 1], 4 - Math.abs(k - 1), [0.3, 0.9], 'pglow', { seed: 395 + k, tag: 'core' });
	M.customMat = (c, f, s, t, p, info) => {
		const tint = M.previewTint || W;
		const tc = col => col.map((v, i) => v * tint[i] / 255);
		if (c.mat === 'pglow') {
			const n = fbm(p[0] * 0.9 + c.seed, p[1] * 0.9, p[2] * 0.9, 3);
			let col = mix([150, 150, 150], [255, 255, 255], 0.3 + 0.7 * n);
			if (info.ePx < 0.25) col = [255, 255, 255];
			return [tc(col), 255, tc(col), 255];
		}
		if (c.mat === 'horn') {
			const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2);
			let col = mix([40, 34, 34], [96, 84, 76], n);
			if (Math.abs(Math.sin((p[0] + p[1] + p[2]) * 2.2)) < 0.25) col = mul(col, 0.7);
			return [bevel(col, info, 0.7, 0.9), 255, null];
		}
		if (c.mat === 'vine') {
			let col = mix([34, 50, 26], [80, 104, 52], fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
			return [bevel(col, info, 0.7, 0.9), 255, null];
		}
		if (c.mat === 'stone') {
			let col = mix([56, 54, 60], [120, 116, 120], fbm(p[0] * 1.5, p[1] * 1.5, p[2] * 1.5, 3));
			if (info.ePx > 0.4 && Math.abs(((info.i + info.j * 2) % 5) - 2) < 0.6 && f !== 'down') return [tc([255, 255, 255]), 255, tc([255, 255, 255]), 255];
			return [bevel(col, info, 0.7, 0.9), 255, null];
		}
		if (c.mat === 'membrane') {
			const n = fbm(p[0] * 0.6 + 3, p[1] * 0.6, p[2] * 0.6, 3);
			if (n > 0.66) return [[0, 0, 0], 0, null]; // tears in the membrane
			let col = mix([46, 34, 40], [92, 70, 72], n);
			if (Math.abs(fbm(p[0] * 0.5, p[1] * 0.5 + 9, 1, 2) - 0.5) < 0.02) return [tc([235, 235, 235]), 255, tc([255, 255, 255]), 200]; // glowing veins
			return [col, 255, null];
		}
		return null;
	};
	return M;
}
function bossPoses(M) {
	const stand = Object.assign({
		waist: [8, 0, 0], chest: [14, 0, 0], head: [-18, 0, 0],
		arm_r: [-10, 0, 6], forearm_r: [-26, 0, 0], arm_l: [-8, 0, -6], forearm_l: [-22, 0, 0],
		thigh_r: [-8, 0, 7], shin_r: [12, 0, 0], foot_r: [-4, 0, -6], thigh_l: [2, 0, -7], shin_l: [6, 0, 0], foot_l: [-8, 0, 6],
	}, CLAW('r', 1.3), CLAW('l', 1.3));
	const cast = Object.assign({
		waist: [-4, -16, 0], chest: [-6, -18, 0], head: [-6, 22, 0], jaw: [24, 0, 0],
		arm_r: [-90, 30, 0], forearm_r: [-6, 0, 0], arm_l: [-40, -10, -50], forearm_l: [-60, 0, 0],
		thigh_r: [8, 0, 10], shin_r: [16, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-28, 0, -8], shin_l: [26, 0, 0],
	}, OPEN('r', 2), CLAW('l', 1.4));
	return { stand, cast };
}

// concept sheets for the boss: the base body, then each accent set in a sample power colour
const ACCENT_PREVIEW = [
	['acc_shards', [120, 220, 255], 'SHARDS - CRYO/CRYSTAL/GEO'], ['acc_horns', [255, 120, 30], 'HORNS - PYRO/SHADOW/STRENGTH'],
	['acc_coils', [130, 190, 255], 'COILS - ELECTRO/MAGNET/LASER'], ['acc_vines', [120, 255, 90], 'VINES - PLANT/REGEN/WATER'],
	['acc_orbit', [200, 120, 255], 'ORBIT - TELEKINESIS/GRAVITY'], ['acc_wings', [255, 240, 170], 'WINGS - FLIGHT'], ['acc_spines', [255, 230, 60], 'SPINES - SPEED/SPIDER/SIZE'],
];
const accentOf = c => { const m = /^(acc_[a-z]+)/.exec(c.bone); return m ? m[1] : (/^wing_/.test(c.bone) ? 'acc_wings' : null); };
function more(sheet) {
	if (!G.WANT('powered_zombie')) return;
	const path = require('path');
	const panels = [];
	const base = (tint, show, label, poseName, yaw) => {
		const M = buildBoss(); M.previewTint = tint; M.layout(); M.paint(G.shadeFor(M));
		const P = bossPoses(M);
		const pose = P[poseName];
		panels.push({ label, items: [{ model: M, pose, root: G.planted(M, pose), hide: c => { const a = accentOf(c); return a && a !== show; } }], cam: { yaw, pitch: 6 } });
		return M;
	};
	const M0 = base([255, 255, 255], null, 'BASE - FRONT', 'stand', 0);
	console.log(`powered_zombie: ${M0.bones.length} bones, ${M0.cubes().length} cubes, tex ${M0.TW}x${M0.TH}`);
	base([255, 255, 255], null, 'BASE - CAST', 'cast', 35);
	ACCENT_PREVIEW.forEach(([a, tint, label], i) => base(tint, a, label, 'stand', i % 2 ? 150 : 30));
	for (let i = 0; i < panels.length; i += 5) {
		L.sheet(path.join(G.PREVIEW, 'concepts', `powered_zombie_${i / 5 + 1}.png`), 'POWERED ZOMBIE BOSS', 'GRAVE CHAMPION, ACCENT SETS TINTED PER POWER', panels.slice(i, i + 5), { minW: 2400, panelH: 1000, maxW: 4200, ss: 2 });
	}
}

module.exports = { ACCENTS, buildBoss, bossPoses, buildCursedZombie, cursedPoses, buildSpy, spyPoses, CURSE, SPY_EYE, lineD, more, accentOf };
