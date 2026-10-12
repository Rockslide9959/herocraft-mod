// v0.16.1 raid undead: Juggernaut, Sword Skeleton, Cursed Zombie, Pillager Spy, Powered Zombie Boss (+ the acid glob).
'use strict';
const L = require('./lib_concept_models.js');
const G = require('./gen_raid_undead_models.js');
const U = require('./raid_undead_units.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, vnorm, vadd, vsub, vsc, vlen } = L;
const { rig, BASE, CLAW, FIST, OPEN, SIDES, sxr, bevel, vgrad, blob } = G;
const { shell, glowBody } = U;

// ================================================================ JUGGERNAUT
const EMBER = [255, 120, 40];
function buildJuggernaut() {
	const M = new Model('juggernaut_zombie', { TW: 512 });
	M.pal = { base: [158, 124, 118], dark: [76, 50, 56], bruise: [112, 76, 104], rot: [104, 82, 54], vein: [80, 40, 62] };
	M.leatherCol = [62, 44, 32]; M.ironCol = [88, 86, 84]; M.seed = 7;
	const P = Object.assign({}, BASE, {
		footH: 2.0, footLen: 6.2, footW: 4.2, shinLen: 6.0, shinW: 4.0, shinD: 4.2, thighLen: 6.2, thighW: 4.8, thighD: 5.0, legX: 3.0,
		pelvisH: 4.0, pelvisW: 10.4, pelvisD: 6.2, waistH: 3.4, waistW: 10.4, waistD: 6.8, chestH: 9.4, chestW: 15.6, ribW: 12.4, chestD: 8.2,
		neckH: 0.2, neckW: 4.6, headW: 5.4, headH: 5.8, headD: 5.6, armW: 4.4, armD: 4.6, upperLen: 8.4, foreLen: 8.6, foreW: 5.4, handLen: 3.2, fingerLen: 3.2,
		fingers: 4, armOut: 9, noTorsoAnatomy: true,
	});
	rig(M, P, { anatomy: true });
	const A = M.A;
	A.eyeCol = EMBER; A.eyeHot = [255, 220, 150];
	// a trapezius hump the head sinks into; heavy pecs and a gut
	M.box('chest', -6.4, A.chestTop - 3.0, -1.8, 6.4, A.chestTop + 2.6, 4.6, 'flesh', { paint: 'trap' });
	M.box('chest', -6.6, A.chestBot + 4.2, -4.7, 6.6, A.chestTop - 1.2, -3.6, 'flesh', { paint: 'pec' });
	blob(M, 'waist', [0, A.pelvisTop + 1.2, -2.6], [9.6, 4.6, 3.2], 'flesh', { paint: 'gut' });
	M.stitches = [
		{ a: [-4.2, A.pelvisTop + 2.6, -4.2], b: [3.8, A.pelvisTop + 0.4, -4.2] },
		{ a: [-A.shX - 2.3, A.elbowY + 1, -1.0], b: [-A.shX - 2.3, A.shY - 3, -1.0] },
		{ a: [A.shX + 2.7, A.wristY + 1, 0.4], b: [A.shX + 2.7, A.elbowY - 1, 0.4] },
		{ a: [-2.6, A.chestBot + 1, 4.2], b: [3.4, A.chestTop - 2, 4.2] },
	];
	M.wounds = [{ c: [-2.4, A.chestBot + 1.4, -3.6], r: 1.3 }, { c: [-A.legX, A.kneeY + 3, -2.5], r: 1.1 }];
	// iron face-mask riveted to the skull: two burning slits
	const mask = M.bone('mask', 'head', [0, A.hb + 2, A.faceZ]);
	M.box(mask, -A.hw - 0.35, A.hb - 0.6, A.faceZ - 0.75, A.hw + 0.35, A.headTop - 0.6, A.faceZ + 0.2, 'iron', { paint: 'mask' });
	M.box(mask, -A.hw - 0.5, A.headTop - 1.4, A.faceZ - 0.85, A.hw + 0.5, A.headTop - 0.5, A.hd / 2 + 0.4, 'iron_d', { paint: 'band' });
	for (const [s] of SIDES) M.box(mask, ...[sxr(s, A.hw - 0.2, A.hw + 0.55)[0], A.hb + 0.5, A.faceZ - 0.6, sxr(s, A.hw - 0.2, A.hw + 0.55)[1], A.headTop - 0.6, 1.2], 'iron');
	// iron collar
	M.box('chest', -3.6, A.chestTop - 0.8, -3.4, 3.6, A.chestTop + 1.4, 3.0, 'iron_d', { paint: 'collar' });
	// scrap plates bolted over the chest, one with nails driven through
	const plates = M.bone('plates', 'chest', [0, A.chestBot, 0]);
	M.box(plates, -6.2, A.chestBot + 3.6, -5.3, 0.6, A.chestTop - 1.0, -4.5, 'iron', { rot: [0, 0, -7], pivot: [-3, A.chestBot + 6, -5] });
	M.box(plates, -0.8, A.chestBot + 1.0, -5.1, 5.8, A.chestBot + 6.6, -4.3, 'iron', { rot: [0, 0, 9], pivot: [2.5, A.chestBot + 4, -5] });
	M.box(plates, -3.2, A.chestBot - 0.4, -5.0, 2.0, A.chestBot + 2.6, -4.2, 'iron_d', { rot: [0, 0, -4], pivot: [0, A.chestBot + 1, -5] });
	for (const [x, y] of [[-5.4, A.chestTop - 1.8], [-0.4, A.chestTop - 2.2], [-5.0, A.chestBot + 4.2], [5.0, A.chestBot + 1.6], [0.2, A.chestBot + 5.8], [-2.6, A.chestBot]]) M.cbox(plates, [x, y, -5.45], [0.8, 0.8, 0.5], 'iron_clean');
	for (const [x, y] of [[2.2, A.chestBot + 4.6], [4.0, A.chestBot + 2.6], [1.0, A.chestBot + 2.4]]) M.stick(plates, [x, y, -5.0], [0.15, 0.25, -1], 1.8, 0.35, 'iron_d');
	// back: one great plate and a hanging chain
	M.box(plates, -5.6, A.chestBot + 1.4, 4.0, 5.2, A.chestTop - 0.4, 5.0, 'iron', { rot: [0, 0, 3], pivot: [0, A.chestBot + 5, 4.5] });
	// a chain wrapped from the right shoulder across to the left hip, front and back
	for (const z of [-4.9, 5.1]) M.stick('chest', [-6.4, A.chestTop - 0.4, z], [10, -(A.chestTop - A.chestBot) + 0.8, 0], 15.0, [1.2, 0.9], 'chain');
	// left pauldron: three stacked plates and spikes; right shoulder bare (the chain over it)
	const pl = M.bone('pauldron_l', 'arm_l', [A.shX, A.shY, 0]);
	M.box(pl, A.shX - 2.6, A.shY + 0.6, -3.4, A.shX + 3.4, A.shY + 3.2, 3.4, 'iron', { rot: [0, 0, 12], pivot: [A.shX, A.shY + 2, 0] });
	M.box(pl, A.shX - 1.2, A.shY - 1.6, -3.2, A.shX + 3.8, A.shY + 1.0, 3.2, 'iron', { rot: [0, 0, 22], pivot: [A.shX, A.shY, 0] });
	M.box(pl, A.shX + 0.2, A.shY - 3.6, -3.0, A.shX + 3.8, A.shY - 1.4, 3.0, 'iron_d', { rot: [0, 0, 30], pivot: [A.shX, A.shY - 2, 0] });
	for (const z of [-2, 0, 2]) M.stick(pl, [A.shX + 0.8, A.shY + 3.0, z], [0.35, 1, 0], 2.6 - Math.abs(z) * 0.3, 0.7, 'iron_d');
	// gauntlet rams over both forearms, a shackle and dragging chain on the left wrist
	for (const [s, n] of SIDES) {
		const sx = s * A.shX;
		M.box(`forearm_${n}`, sx - 3.1, A.wristY + 0.4, -3.0, sx + 3.1, A.wristY + 5.6, 3.0, 'iron', { paint: 'ram' });
		M.box(`forearm_${n}`, sx - 3.3, A.wristY + 1.6, -3.2, sx + 3.3, A.wristY + 2.4, 3.2, 'iron_d');
		M.box(`hand_${n}`, sx - 1.9, A.knuckY + 0.6, -2.4, sx + 1.9, A.wristY + 0.6, 2.4, 'leather_d');
		// knee plates, leg wraps
		const lx = s * A.legX;
		M.box(`shin_${n}`, lx - 2.2, A.kneeY - 1.8, -3.0, lx + 2.2, A.kneeY + 1.4, -1.6, 'iron');
		shell(M, `shin_${n}`, M.B[`shin_${n}`].cubes[0], 0.3, 'cloth2', { kt: -3.2 });
	}
	const sh = M.bone('shackle', 'forearm_l', [A.shX, A.wristY + 0.2, 0]);
	M.box(sh, A.shX - 3.0, A.wristY - 0.6, -3.0, A.shX + 3.0, A.wristY + 0.6, 3.0, 'iron_d');
	const ch = M.bone('chain', 'shackle', [A.shX + 2.4, A.wristY - 0.4, 0]);
	for (let i = 0; i < 6; i++) M.cbox(ch, [A.shX + 2.4, A.wristY - 1.0 - i * 1.5, 0], i % 2 ? [0.5, 1.8, 1.3] : [1.3, 1.8, 0.5], 'iron_d');
	// belt, leather loin flaps
	M.box('hips', -5.8, A.pelvisTop - 1.6, -3.7, 5.8, A.pelvisTop + 0.4, 3.7, 'leather', { paint: 'belt' });
	M.cbox('hips', [0, A.pelvisTop - 0.6, -3.9], [2.4, 2.2, 0.6], 'iron');
	shell(M, 'hips', M.B.hips.cubes[0], 0.3, 'cloth2', { tag: 'solid' });
	const ff = M.bone('flap_f', 'hips', [0, A.pelvisTop - 1.4, -3.6]);
	M.box(ff, -2.6, A.hipY - 6.2, -3.95, 2.6, A.pelvisTop - 1.4, -3.55, 'leather', { paint: 'flap', tag: 'hem' });
	const fb = M.bone('flap_b', 'hips', [0, A.pelvisTop - 1.4, 3.6]);
	M.box(fb, -3.4, A.hipY - 6.8, 3.55, 3.4, A.pelvisTop - 1.4, 3.95, 'leather', { paint: 'flap' });
	M.cloth2Col = [72, 62, 52];
	M.customMat = (c, f, s, t, p, info) => {
		if (c.paint === 'mask' && f === 'north') {
			let col = G.ironMat(M, c, f, s, t, p, info, M.ironCol)[0];
			const ey = A.eyeY - 0.1, ax = Math.abs(p[0]);
			if (Math.abs(p[1] - ey) < 0.32 && ax > 0.6 && ax < 2.3) { const k = 1 - Math.abs(p[1] - ey) / 0.32; return [mix([255, 90, 20], [255, 220, 150], k), 255, EMBER, 255]; }
			if (Math.abs(p[1] - ey) < 0.7 && ax > 0.4 && ax < 2.6) col = mul(col, 0.4);
			if (Math.abs(p[1] - (A.hb + 0.6)) < 0.25 && ax < 1.8 && Math.floor(ax * 3) % 2) col = [20, 16, 14]; // breathing holes
			return [col, 255, null];
		}
		return null;
	};
	return M;
}
function juggernautPoses(M) {
	const stand = Object.assign({
		waist: [10, 0, 0], chest: [14, 0, 0], head: [-20, 0, 0],
		arm_r: [-6, 0, 4], forearm_r: [-24, 0, 0], arm_l: [-4, 0, -4], forearm_l: [-20, 0, 0],
		thigh_r: [-10, 0, 8], shin_r: [16, 0, 0], foot_r: [-6, 0, -6], thigh_l: [-6, 0, -8], shin_l: [12, 0, 0], foot_l: [-6, 0, 6],
	}, FIST('r'), FIST('l'));
	const charge = Object.assign({
		waist: [24, 0, 0], chest: [26, 0, 0], head: [-34, 0, 0],
		arm_r: [-40, 0, 20], forearm_r: [-70, 0, 0], arm_l: [-40, 0, -20], forearm_l: [-70, 0, 0],
		thigh_r: [-50, 0, 6], shin_r: [30, 0, 0], foot_r: [10, 0, 0], thigh_l: [30, 0, -6], shin_l: [40, 0, 0], foot_l: [-30, 0, 0],
	}, FIST('r'), FIST('l'));
	return { stand, charge };
}

// ================================================================ SWORD SKELETON
function buildSkeleton() {
	const M = new Model('sword_skeleton', { TW: 256 });
	M.clothCol = [110, 34, 34]; M.cloth3Col = [110, 34, 34]; M.leatherCol = [72, 50, 34]; M.ironCol = [94, 94, 96]; M.woodCol = [98, 72, 46]; M.seed = 11;
	M.boneTint = [140, 130, 120];
	const P = Object.assign({}, BASE, { headW: 5.0, headH: 5.4, headD: 5.6, neckH: 2.2, upperLen: 6.4, foreLen: 6.0, thighLen: 6.6, shinLen: 6.4, legX: 1.7, chestH: 7.0 });
	rig(M, P, { noLegs: true, noArms: true, noTorso: true, noHead: true, noPelvis: true, noNeck: true });
	const A = M.A;
	A.eyeCol = [150, 200, 255]; A.eyeHot = [235, 245, 255];
	const bone = 'bone';
	// pelvis + spine
	M.box('hips', -2.6, A.hipY - 0.6, -1.2, 2.6, A.hipY + 1.0, 1.0, bone, { paint: 'pelvis' });
	for (const [s] of SIDES) M.box('hips', ...[sxr(s, 1.0, 3.3)[0], A.hipY + 0.4, -1.4, sxr(s, 1.0, 3.3)[1], A.pelvisTop + 0.2, 0.6], bone, { rot: [0, 0, s * 14], pivot: [s * 1.5, A.hipY + 1, 0], paint: 'iliac' });
	for (let i = 0; i < 3; i++) M.cbox('waist', [0, A.pelvisTop + 0.4 + i * 1.05, 0.4], [1.3, 0.75, 1.3], bone, { paint: 'vert', seed: i });
	M.box('waist', -0.35, A.pelvisTop - 0.3, 0.1, 0.35, A.chestBot + 0.6, 0.7, bone);
	// ribcage: sternum, four rib hoops (front arcs + sides + back), the spine behind
	const cb = A.chestBot, ct = A.chestTop;
	M.box('chest', -0.45, cb + 2.4, -2.1, 0.45, ct - 0.8, -1.6, bone, { paint: 'sternum' });
	M.box('chest', -0.4, cb - 0.2, 0.3, 0.4, ct + 0.2, 0.9, bone, { paint: 'spine' });
	for (let i = 0; i < 4; i++) {
		const y = cb + 1.6 + i * 1.35, w = 3.0 + i * 0.35, d = 1.9 + i * 0.1;
		for (const [s] of SIDES) {
			M.box('chest', ...[sxr(s, 0.4, w - 0.3)[0], y, -d - 0.05, sxr(s, 0.4, w - 0.3)[1], y + 0.55, -d + 0.5], bone, { rot: [0, s * -8, s * 6], pivot: [s * 0.4, y, -d], seed: i });
			M.box('chest', ...[sxr(s, w - 0.6, w)[0], y + 0.2, -d + 0.2, sxr(s, w - 0.6, w)[1], y + 0.75, 1.0], bone, { seed: i + 5 });
			M.box('chest', ...[sxr(s, 0.3, w - 0.4)[0], y + 0.35, 0.8, sxr(s, 0.3, w - 0.4)[1], y + 0.9, 1.3], bone, { seed: i + 9 });
		}
	}
	// shoulder girdle: clavicles, scapulae
	for (const [s] of SIDES) {
		M.stick('chest', [s * 0.5, ct - 0.4, -1.8], [s, 0.05, 0.25], A.shX - 0.6, [0.55, 0.55], bone, { paint: 'clavicle' });
		M.box('chest', ...[sxr(s, 1.0, 3.6)[0], ct - 3.4, 1.0, sxr(s, 1.0, 3.6)[1], ct - 0.2, 1.5], bone, { paint: 'scapula', rot: [0, 0, s * 10], pivot: [s * 2, ct - 2, 1.2] });
	}
	// neck vertebrae, skull, mandible
	for (let i = 0; i < 2; i++) M.cbox('neck', [0, ct + 0.4 + i * 0.9, 0.2], [1.0, 0.7, 1.0], bone, { paint: 'vert' });
	const hb = A.hb, hw = A.hw, hd = A.hd, fz = A.faceZ;
	M.box('head', -hw, hb + 1.8, -hd / 2 + 0.2, hw, A.headTop, hd / 2, bone, { paint: 'skull' });
	M.box('head', -hw + 0.6, A.headTop - 0.1, -hd / 2 + 0.8, hw - 0.6, A.headTop + 0.45, hd / 2 - 0.5, bone, { paint: 'skull' });
	M.box('head', -hw + 0.5, hb + 0.6, fz - 0.05, hw - 0.5, hb + 2.0, fz + 2.2, bone, { paint: 'maxilla' });
	for (const [s] of SIDES) M.box('head', ...[sxr(s, hw - 1.4, hw + 0.2)[0], A.eyeY - 1.3, fz - 0.15, sxr(s, hw - 1.4, hw + 0.2)[1], A.eyeY - 0.6, fz + 1.8], bone, { paint: 'cheek' });
	M.box('jaw', -hw + 0.7, hb - 0.3, fz + 0.1, hw - 0.7, hb + 0.8, fz + 1.6, bone, { paint: 'mandible' });
	for (const [s] of SIDES) M.box('jaw', ...[sxr(s, hw - 1.2, hw - 0.6)[0], hb - 0.3, fz + 1.0, sxr(s, hw - 1.2, hw - 0.6)[1], hb + 1.8, fz + 3.6], bone);
	M.B.jaw.rot = [5, 0, 0];
	// limbs: long bones with knobbly joints
	for (const [s, n] of SIDES) {
		const lx = s * A.legX;
		M.box(`thigh_${n}`, lx - 0.6, A.kneeY + 0.2, -0.6, lx + 0.6, A.hipY + 0.2, 0.6, bone, { seed: 20 });
		M.cbox(`thigh_${n}`, [lx, A.hipY + 0.2, 0], [1.4, 1.2, 1.4], bone);
		M.cbox(`shin_${n}`, [lx, A.kneeY, -0.1], [1.5, 1.3, 1.5], bone, { paint: 'knee' });
		M.box(`shin_${n}`, lx - 0.55, A.ankleY + 0.2, -0.75, lx + 0.15, A.kneeY - 0.3, -0.05, bone, { seed: 21 });
		M.box(`shin_${n}`, lx + 0.15, A.ankleY + 0.4, 0.0, lx + 0.55, A.kneeY - 0.5, 0.45, bone, { seed: 22 });
		M.box(`foot_${n}`, lx - 1.0, 0, -2.6, lx + 1.0, 0.9, 1.0, bone, { tag: 'sole' });
		M.box(`foot_${n}`, lx - 0.9, 0, -3.6, lx + 0.9, 0.55, -2.4, bone, { tag: 'sole', paint: 'toes' });
		const sx = s * A.shX;
		M.cbox(`arm_${n}`, [sx, A.shY + 0.2, 0], [1.6, 1.5, 1.6], bone);
		M.box(`arm_${n}`, sx - 0.55, A.elbowY + 0.2, -0.55, sx + 0.55, A.shY, 0.55, bone, { seed: 23 });
		M.cbox(`forearm_${n}`, [sx, A.elbowY, 0], [1.3, 1.2, 1.3], bone);
		M.box(`forearm_${n}`, sx - 0.5, A.wristY + 0.2, -0.6, sx - 0.05, A.elbowY - 0.3, -0.1, bone, { seed: 24 });
		M.box(`forearm_${n}`, sx + 0.05, A.wristY + 0.2, 0.05, sx + 0.5, A.elbowY - 0.3, 0.55, bone, { seed: 25 });
		M.box(`hand_${n}`, sx - 0.5, A.knuckY + 0.2, -0.9, sx + 0.5, A.wristY + 0.3, 0.9, bone);
		[-0.65, 0, 0.65].forEach((z, i) => M.box(`fingers_${n}`, sx - 0.25, A.knuckY - 2.0, z - 0.22, sx + 0.25, A.knuckY + 0.4, z + 0.22, bone, { paint: 'finger', seed: 26 + i }));
	}
	// gear: a cracked nasal helm, a leather pauldron, a tattered tabard, belt, sword and shield
	const helm = M.bone('helm', 'head', [0, A.eyeY, 0]);
	M.box(helm, -hw - 0.4, A.eyeY + 0.5, -hd / 2 - 0.2, hw + 0.4, A.headTop + 0.7, hd / 2 + 0.4, 'iron', { paint: 'helm' });
	M.box(helm, -hw + 0.4, A.headTop + 0.6, -hd / 2 + 0.6, hw - 0.4, A.headTop + 1.2, hd / 2 - 0.2, 'iron', { paint: 'helm' });
	M.box(helm, -0.35, A.eyeY - 1.4, fz - 0.7, 0.35, A.eyeY + 0.9, fz - 0.1, 'iron_clean');
	M.box(helm, -hw - 0.6, A.eyeY + 0.3, -hd / 2 - 0.4, hw + 0.6, A.eyeY + 0.9, hd / 2 + 0.6, 'iron_d');
	M.box('arm_r', -A.shX - 1.6, A.shY - 2.0, -1.6, -A.shX + 1.2, A.shY + 1.4, 1.6, 'leather', { rot: [0, 0, -14], pivot: [-A.shX, A.shY, 0] });
	M.box('hips', -2.9, A.pelvisTop - 0.6, -1.6, 2.9, A.pelvisTop + 0.4, 1.4, 'leather', { paint: 'belt' });
	M.holes = [{ c: [1.4, A.hipY - 4.0, -1.8], r: 0.8 }, { c: [-1.0, A.hipY - 2.0, 1.8], r: 0.7 }];
	const tf = M.bone('tabard_f', 'hips', [0, A.pelvisTop, -1.7]);
	M.box(tf, -1.9, A.hipY - 5.6, -1.95, 1.9, A.pelvisTop + 0.2, -1.65, 'cloth', { paint: 'tabard', tag: 'hem', seed: 30 });
	const tb = M.bone('tabard_b', 'hips', [0, A.pelvisTop, 1.5]);
	M.box(tb, -2.1, A.hipY - 6.2, 1.5, 2.1, A.pelvisTop + 0.2, 1.8, 'cloth', { paint: 'tabard', tag: 'hem', seed: 31 });
	// a rag of the tabard still on the chest, hanging off the ribs
	M.box('chest', -2.4, cb + 1.2, -2.6, 1.6, ct - 0.4, -2.3, 'cloth', { paint: 'tabard_top', tag: 'hem', seed: 32 });
	// sword along -Z from the fist (rusted, notched)
	const w = M.bone('weapon_r', 'hand_r', [-A.shX, A.knuckY + 0.6, 0]);
	const [wx, wy] = [-A.shX, A.knuckY + 0.6];
	M.box(w, wx - 0.4, wy - 0.4, -1.6, wx + 0.4, wy + 0.4, 1.8, 'leather');
	M.cbox(w, [wx, wy, 2.2], [0.9, 0.9, 0.9], 'iron');
	M.box(w, wx - 2.0, wy - 0.45, -2.3, wx + 2.0, wy + 0.45, -1.6, 'iron');
	M.box(w, wx - 0.75, wy - 0.18, -14.5, wx + 0.75, wy + 0.18, -2.3, 'iron', { paint: 'blade' });
	M.box(w, wx - 0.45, wy - 0.18, -15.6, wx + 0.45, wy + 0.18, -14.4, 'iron', { paint: 'blade_tip' });
	// round shield strapped to the left forearm (planks, iron rim, boss), facing outward (+X)
	const sx = A.shX + 0.95;
	const sh = M.bone('shield', 'forearm_l', [sx, A.wristY + 3, 0]);
	const sc = [sx, A.wristY + 3.0, 0];
	M.box(sh, sx, sc[1] - 4.0, -2.5, sx + 0.5, sc[1] + 4.0, 2.5, 'wood', { paint: 'planks' });
	M.box(sh, sx, sc[1] - 2.5, -4.0, sx + 0.5, sc[1] + 2.5, 4.0, 'wood', { paint: 'planks' });
	M.box(sh, sx, sc[1] - 3.4, -3.4, sx + 0.5, sc[1] + 3.4, 3.4, 'wood', { paint: 'planks' });
	M.box(sh, sx - 0.05, sc[1] - 4.2, -0.6, sx + 0.6, sc[1] + 4.2, 0.6, 'iron');
	M.box(sh, sx - 0.05, sc[1] - 0.6, -4.2, sx + 0.6, sc[1] + 0.6, 4.2, 'iron');
	M.cbox(sh, [sx + 0.75, sc[1], 0], [0.8, 1.6, 1.6], 'iron_clean');
	M.noRivets = true;
	M.customMat = (c, f, s, t, p, info) => {
		if (c.mat === 'bone' && c.paint === 'skull' && f === 'north') {
			let col = G.boneMat(M, c, f, s, t, p, info)[0];
			const ex = (Math.abs(p[0]) - 1.3) / 0.95, ey = (p[1] - A.eyeY) / 0.95;
			const e = ex * ex + ey * ey;
			if (e < 1) {
				const pe = (Math.abs(p[0]) - 1.3) ** 2 / 0.09 + (p[1] - A.eyeY + 0.05) ** 2 / 0.09;
				if (pe < 1) return [mix(A.eyeHot, A.eyeCol, Math.sqrt(pe)), 255, A.eyeCol, 255];
				return [[14, 12, 14], 255, null];
			}
			return [col, 255, null];
		}
		if (c.mat === 'bone' && c.paint === 'maxilla' && f === 'north') {
			let col = G.boneMat(M, c, f, s, t, p, info)[0];
			if (Math.abs(p[0]) < 0.55 && t < 0.55) col = [16, 12, 12]; // nasal cavity
			if (t > 0.62) { const k = Math.floor((p[0] + 10) * 2.6); col = k % 2 ? [214, 204, 176] : [120, 104, 80]; }
			return [col, 255, null];
		}
		if (c.mat === 'bone' && c.paint === 'mandible' && f === 'north' && t < 0.4) { const k = Math.floor((p[0] + 10) * 2.6); return [k % 2 ? [214, 204, 176] : [110, 96, 74], 255, null]; }
		if (c.paint === 'blade' || c.paint === 'blade_tip') {
			let col = G.ironMat(M, c, f, s, t, p, info, [120, 118, 116], 1.4)[0];
			if ((f === 'east' || f === 'west') && hash3(Math.floor(p[2] * 1.4), 1, 3) > 0.75) return [[0, 0, 0], 0, null]; // notches in the edge
			if (f === 'up' || f === 'down') { if (Math.abs(s - 0.5) < 0.08) col = mul(col, 0.7); }
			return [col, 255, null];
		}
		if (c.paint === 'tabard' && (f === 'north' || f === 'south')) {
			let r = G.clothMat(M, c, f, s, t, p, info, M.clothCol);
			// a faded gold skull-and-crossbones emblem
			const u = s - 0.5, v = t - 0.32;
			if (Math.hypot(u * 1.4, v) < 0.14 || (Math.abs(Math.abs(u) - Math.abs(v - 0.05)) < 0.03 && Math.abs(u) < 0.3 && Math.abs(v) < 0.3)) r[0] = mix(r[0], [176, 140, 70], 0.75);
			return r;
		}
		return null;
	};
	return M;
}
function skeletonPoses(M) {
	const stand = Object.assign({
		waist: [4, 0, 0], chest: [6, 4, 0], head: [-6, -6, 4],
		arm_r: [-20, 0, 6], forearm_r: [-30, 0, 0], arm_l: [-30, -10, -16], forearm_l: [-50, 0, 0],
		thigh_r: [-6, 0, 4], shin_r: [10, 0, 0], foot_r: [-4, 0, 0], thigh_l: [4, 0, -4], shin_l: [4, 0, 0], foot_l: [-8, 0, 0],
	}, FIST('r'), FIST('l'));
	L.aimBone(M, stand, 'weapon_r', [0, 0, -1], [0.1, -0.35, -1]);
	const slash = Object.assign({
		waist: [8, -30, 0], chest: [10, -24, 0], head: [-6, 30, 0],
		arm_r: [-80, -30, -20], forearm_r: [-10, 0, 0], arm_l: [-40, 20, -30], forearm_l: [-60, 0, 0],
		thigh_r: [10, 0, 6], shin_r: [16, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-30, 0, -6], shin_l: [30, 0, 0], foot_l: [0, 0, 0],
	}, FIST('r'), FIST('l'));
	L.aimBone(M, slash, 'weapon_r', [0, 0, -1], [0.8, -0.1, -0.6]);
	return { stand, slash };
}

module.exports = { buildJuggernaut, juggernautPoses, buildSkeleton, skeletonPoses, EMBER };
