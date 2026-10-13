// v0.16.1 raid undead: the Powered Zombie Boss (the raid's other mobs moved to player-rig skins: gen_raid_player_skins.js).
'use strict';
const L = require('./lib_concept_models.js');
const G = require('./gen_raid_undead_models.js');
const U = require('./raid_undead_units.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, vnorm, vadd, vsub, vsc, vlen } = L;
const { rig, BASE, CLAW, FIST, OPEN, SIDES, sxr, bevel, vgrad, blob } = G;
const { shell, glowBody } = U;

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

module.exports = { ACCENTS, buildBoss, bossPoses, more, accentOf };
