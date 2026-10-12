// v0.16.x Ultron Contagion: Ultron Omega CONCEPT model (not wired into the game yet; the user approves the look first).
// Based on Ultron from Age of Ultron and the comics: sleek, inhuman, silver-white vibranium plating over a black
// frame, a narrow face with a sharp jaw, glowing red eyes and mouth, red energy seams down the chest and arms, long
// fingers. 4 blocks tall. Everything is painted procedurally (no copied textures).
//
// Usage (from the repo root):
//   node scratchpad/gen_ultron_omega_model.js --concept <outDir> [--no-assets]
// Writes <outDir>/08_ultron_omega.png (front / three-quarter / side + hovering, arms out) and the geo / texture /
// glowmask into <outDir>/assets (never into src/). gen_frost_giant_models.js requires this file for the lineup.
//
// Conventions as in gen_v01420_titan_model.js: +Y up, the model faces -Z, the entity's RIGHT is -X, model pixels.
'use strict';
const path = require('path');
const L = require('./lib_concept_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth } = L;

const RED = [255, 36, 30], RED_HOT = [255, 190, 170];
const SIDES = [[-1, 'r'], [1, 'l']];
const sxr = (s, a, b) => s > 0 ? [a, b] : [-b, -a];

// ================================================================ model
// Bones: root > hips > (thigh > shin > foot) x2, hips > waist > chest > neck > head > jaw,
// chest > shoulder > upper_arm > forearm > hand > fingers > fingertips (x2).
function buildOmega() {
	const M = new Model('ultron_omega', { TW: 512 });
	const A = M.A;
	const legX = 2.7, ankle = 2.0, knee = 17.5, hip = 32.5;
	const waistTop = 39.0, chestTop = 48.5, shX = 7.6, shY = 46.4, elbow = 33.4, wrist = 21.6, knuck = 18.6;
	Object.assign(A, { legX, ankle, knee, hip, waistTop, chestTop, shX, shY, elbow, wrist, knuck, faceZ: -3.0 });
	const root = M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, hip, 0]);
	M.box(hips, -3.6, hip - 1.6, -2.0, 3.6, hip + 1.8, 2.0, 'frame');
	M.box(hips, -4.5, hip - 0.4, -2.45, 4.5, hip + 1.9, 2.45, 'plate', { paint: 'pelvis' });
	M.box(hips, -1.5, hip - 2.6, -2.75, 1.5, hip - 0.2, -2.0, 'plate', { paint: 'cod' });
	M.box(hips, -0.8, hip - 3.4, -2.7, 0.8, hip - 2.4, -2.05, 'plate_d');
	for (const [s, n] of SIDES) {
		const lx = s * legX;
		const th = M.bone(`thigh_${n}`, 'hips', [lx, hip, 0], [0, 0, -s * 1.5]);
		M.box(th, lx - 1.15, knee, -1.15, lx + 1.15, hip + 0.5, 1.15, 'frame', { paint: 'core' });
		M.box(th, lx - 1.95, knee + 7.6, -2.25, lx + 1.95, hip - 0.6, 0.9, 'plate', { paint: 'thigh' });
		M.box(th, lx - 1.45, knee + 2.4, -1.95, lx + 1.45, knee + 7.8, 0.7, 'plate', { paint: 'thigh' });
		M.box(th, lx - 0.35, knee + 8.6, -2.4, lx + 0.35, hip - 1.8, -2.2, 'red_core');
		M.box(th, ...[sxr(s, legX + 0.6, legX + 2.3)[0], knee + 4, -1.6, sxr(s, legX + 0.6, legX + 2.3)[1], hip - 1.4, 1.7], 'plate_d');
		M.box(th, lx - 1.5, knee + 4.5, 0.8, lx + 1.5, hip - 3, 1.9, 'plate_d');
		const sh = M.bone(`shin_${n}`, `thigh_${n}`, [lx, knee, 0]);
		M.box(sh, lx - 0.95, ankle, -0.95, lx + 0.95, knee + 0.4, 0.95, 'frame', { paint: 'core' });
		M.box(sh, lx - 1.6, ankle + 2.0, -2.15, lx + 1.6, knee - 1.4, -0.5, 'plate', { paint: 'shin' });
		M.box(sh, lx - 1.35, ankle + 7.5, 0.4, lx + 1.35, knee - 1.0, 1.95, 'plate_d');
		M.box(sh, lx - 1.25, knee - 1.6, -2.65, lx + 1.25, knee + 1.4, -1.0, 'plate', { paint: 'knee' });
		const ft = M.bone(`foot_${n}`, `shin_${n}`, [lx, ankle, 0]);
		M.box(ft, lx - 1.35, 0, -4.4, lx + 1.35, ankle + 0.4, 1.6, 'plate', { paint: 'foot', tag: 'sole' });
		M.box(ft, lx - 0.9, 0, -6.4, lx + 0.9, 1.1, -4.3, 'plate', { tag: 'sole' });
		M.box(ft, lx - 0.4, 0, -7.2, lx + 0.4, 0.6, -6.3, 'plate_d', { tag: 'sole' });
		M.box(ft, lx - 1.0, 0.1, 1.5, lx + 1.0, 1.4, 2.6, 'plate_d', { tag: 'sole' });
	}
	const waist = M.bone('waist', 'hips', [0, hip + 1.5, 0]);
	M.box(waist, -2.5, hip + 1.2, -1.8, 2.5, waistTop + 0.4, 1.8, 'frame', { paint: 'spine' });
	[[hip + 2.0, 2.3], [hip + 3.9, 2.6], [hip + 5.8, 2.9]].forEach(([y, w], i) => {
		M.box(waist, -w, y, -2.45, w, y + 1.45, -1.5, 'plate', { paint: 'abs', seed: i });
		M.box(waist, -w * 0.8, y, 1.5, w * 0.8, y + 1.45, 2.3, 'plate_d');
	});
	for (const [s] of SIDES) M.box(waist, ...[sxr(s, 2.0, 3.0)[0], hip + 2.2, -1.4, sxr(s, 2.0, 3.0)[1], waistTop - 0.4, 1.4], 'plate_d', { rot: [0, 0, s * 8], pivot: [s * 2.5, waistTop, 0] });
	M.bone('flinch', 'waist', [0, waistTop, 0]); // code-driven hurt flinch (never keyed)
	const chest = M.bone('chest', 'flinch', [0, waistTop, 0]);
	M.box(chest, -4.6, waistTop - 0.5, -2.4, 4.6, chestTop, 2.5, 'frame', { paint: 'core' });
	M.box(chest, -4.9, waistTop - 0.2, -3.0, 4.9, waistTop + 2.6, -1.5, 'plate', { paint: 'ribs' });
	for (const [s] of SIDES) {
		// pectoral plates, angled like a keel; the red seam runs between them
		M.box(chest, ...[sxr(s, 0.55, 6.4)[0], waistTop + 2.3, -3.55, sxr(s, 0.55, 6.4)[1], chestTop - 0.6, -2.1], 'plate', { paint: 'pec', rot: [0, -s * 10, 0], pivot: [s * 0.5, 44, -3.4] });
		M.box(chest, ...[sxr(s, 0.8, 6.6)[0], waistTop + 2, 1.9, sxr(s, 0.8, 6.6)[1], chestTop - 0.4, 3.0], 'plate', { paint: 'back' });
		M.box(chest, ...[sxr(s, 1.2, 5.8)[0], chestTop - 1.1, -2.2, sxr(s, 1.2, 5.8)[1], chestTop + 0.5, 2.4], 'plate_d', { rot: [0, 0, s * 16], pivot: [s * 1.2, chestTop, 0] }); // trapezius plates
		M.box(chest, ...[sxr(s, 3.9, 5.6)[0], waistTop + 0.6, -2.0, sxr(s, 3.9, 5.6)[1], chestTop - 2.0, 2.0], 'plate_d', { rot: [0, 0, -s * 6], pivot: [s * 4.6, chestTop, 0] }); // lats
	}
	M.box(chest, -0.55, waistTop + 1.0, -3.9, 0.55, chestTop - 0.9, -2.6, 'red_core'); // keel seam
	M.box(chest, -1.2, chestTop - 4.6, -4.05, 1.2, chestTop - 2.4, -3.2, 'plate_d', { paint: 'core_ring' });
	const neck = M.bone('neck', 'chest', [0, chestTop, 0]);
	M.box(neck, -1.25, chestTop - 1.0, -1.3, 1.25, chestTop + 2.9, 1.2, 'frame', { paint: 'neck' });
	for (const [s] of SIDES) M.stick(neck, [s * 1.0, chestTop - 0.5, 0.6], [s * 0.15, 1, -0.25], 3.4, 0.55, 'plate_d');
	const head = M.bone('head', 'neck', [0, chestTop + 2.2, 0]);
	const hb = chestTop + 2.2;
	A.hb = hb; A.eyeY = hb + 4.1; A.mouthY = hb + 1.7;
	M.box(head, -2.35, hb + 1.4, -2.6, 2.35, hb + 8.4, 2.4, 'plate', { paint: 'cranium' });
	M.box(head, -2.1, hb + 1.0, -3.05, 2.1, hb + 5.6, -2.4, 'plate', { paint: 'face' });
	M.box(head, -2.2, hb + 5.4, -3.35, 2.2, hb + 6.3, -2.3, 'plate_d', { paint: 'brow' });
	M.box(head, -0.55, hb + 6.0, -2.9, 0.55, hb + 9.3, 2.9, 'plate_d', { paint: 'crest' });
	M.box(head, -1.6, hb + 8.0, -1.8, 1.6, hb + 8.9, 2.2, 'plate');
	for (const [s] of SIDES) {
		M.box(head, ...[sxr(s, 2.0, 2.75)[0], hb + 2.0, -2.5, sxr(s, 2.0, 2.75)[1], hb + 5.2, 0.4], 'plate_d', { rot: [0, s * 8, 0], pivot: [s * 2.3, hb + 3, -1] }); // cheek blades
		M.stick(head, [s * 2.3, hb + 5.0, 1.0], [s * 0.3, 0.55, 1], 3.4, [0.45, 1.6], 'plate_d'); // swept ear fins
	}
	const jaw = M.bone('jaw', 'head', [0, hb + 2.0, 0.8]);
	M.box(jaw, -1.9, hb + 0.2, -2.95, 1.9, hb + 2.0, 0.6, 'plate', { paint: 'jaw' });
	M.box(jaw, -1.2, hb - 0.6, -2.85, 1.2, hb + 0.4, -0.6, 'plate', { paint: 'chin' });
	M.box(jaw, -0.55, hb - 1.3, -2.75, 0.55, hb - 0.4, -1.5, 'plate', { paint: 'chin' });
	for (const [s, n] of SIDES) {
		const sx = s * shX;
		const sh = M.bone(`shoulder_${n}`, 'chest', [s * 3, chestTop - 1, 0]);
		M.box(sh, ...[sxr(s, 5.0, 10.0)[0], shY - 1.2, -2.7, sxr(s, 5.0, 10.0)[1], shY + 2.4, 2.7], 'plate', { paint: 'pauldron', rot: [0, 0, s * 12], pivot: [sx, shY + 1, 0] });
		M.box(sh, ...[sxr(s, 6.5, 10.6)[0], shY - 3.0, -2.4, sxr(s, 6.5, 10.6)[1], shY - 0.6, 2.4], 'plate_d', { rot: [0, 0, s * 20], pivot: [sx, shY - 1, 0] });
		const ua = M.bone(`arm_${n}`, `shoulder_${n}`, [sx, shY, 0], [0, 0, -s * 6]);
		M.box(ua, sx - 1.0, elbow - 0.4, -1.0, sx + 1.0, shY + 0.6, 1.0, 'frame', { paint: 'core' });
		M.box(ua, sx - 1.6, elbow + 1.6, -1.65, sx + 1.6, shY - 2.6, 1.65, 'plate', { paint: 'arm' });
		M.cbox(ua, [sx, elbow, 0], [1.9, 1.9, 1.9], 'frame', { paint: 'joint' });
		const fa = M.bone(`forearm_${n}`, `arm_${n}`, [sx, elbow, 0], [-8, 0, 0]);
		M.box(fa, sx - 0.9, wrist - 0.2, -0.9, sx + 0.9, elbow + 0.4, 0.9, 'frame', { paint: 'core' });
		M.box(fa, sx - 1.7, wrist + 1.2, -1.75, sx + 1.7, elbow - 0.9, 1.75, 'plate', { paint: 'forearm' });
		M.stick(fa, [sx + s * 1.4, elbow - 2.0, 0.6], [s * 0.4, 1, 0.5], 2.6, [0.5, 1.2], 'plate_d'); // elbow blade
		const hand = M.bone(`hand_${n}`, `forearm_${n}`, [sx, wrist, 0]);
		M.box(hand, sx - 0.6, knuck, -1.35, sx + 0.6, wrist + 0.3, 1.35, 'plate', { paint: 'hand' });
		M.box(hand, sx - s * 0.62 - 0.02, knuck + 0.6, -0.8, sx - s * 0.6 + 0.02, wrist - 0.4, 0.8, 'red_core', { paint: 'palm' }); // palm emitter
		M.stick(hand, [sx - s * 0.3, wrist - 0.7, -1.2], [-s * 0.35, -0.8, -0.5], 3.2, 0.55, 'frame', { paint: 'finger' });
		const fi = M.bone(`fingers_${n}`, `hand_${n}`, [sx, knuck + 0.2, 0]);
		const tp = M.bone(`fingertips_${n}`, `fingers_${n}`, [sx, knuck - 2.8, 0]);
		[-1.05, -0.35, 0.35, 1.05].forEach((z, i) => {
			const len = [4.6, 5.2, 5.0, 4.2][i];
			M.box(fi, sx - 0.3, knuck - 2.95, z - 0.27, sx + 0.3, knuck + 0.25, z + 0.27, 'frame', { paint: 'finger' });
			M.box(fi, sx - 0.36, knuck - 2.4, z - 0.32, sx + 0.36, knuck - 0.4, z + 0.32, 'plate_d');
			M.box(tp, sx - 0.26, knuck - 2.8 - (len - 2.8), z - 0.24, sx + 0.26, knuck - 2.6, z + 0.24, 'frame', { paint: 'finger' });
			M.box(tp, sx - 0.18, knuck - 2.8 - (len - 2.8) - 0.6, z - 0.16, sx + 0.18, knuck - 2.8 - (len - 2.8) + 0.2, z + 0.16, 'plate', { tag: 'claw' });
		});
	}
	return M;
}

// ================================================================ painter
function metal(p, f, t, info, base, sheenK = 1) {
	const n = fbm(p[0] * 0.6 + 3, p[1] * 0.6, p[2] * 0.6, 3);
	let col = mix(mul(base, 0.84), mul(base, 1.08), n);
	// brushed vertical sheen bands (reflection streaks of polished vibranium)
	const streak = Math.sin((p[0] * 0.9 + p[2] * 0.7) * 1.3 + p[1] * 0.15);
	col = mul(col, 1 + 0.07 * streak * sheenK);
	if (f === 'up') col = mul(col, 1.12); else if (f === 'down') col = mul(col, 0.6); else col = mul(col, 1.08 - 0.22 * t);
	// recessed panel line just inside the edge, bright chamfer on the edge itself
	if (info.ePx < 0.18) col = mix(col, [255, 255, 255], 0.35);
	else if (info.ePx > 0.32 && info.ePx < 0.46 && Math.min(info.fw, info.fh) > 1.8) col = mul(col, 0.55);
	if (vnoise(p[0] * 7, p[1] * 7, p[2] * 7) > 0.9) col = mul(col, 0.9); // fine scuffs
	return col;
}
function shadeOmega(M) {
	const A = M.A;
	return (c, f, s, t, p, info) => {
		const P = c.paint;
		switch (c.mat) {
			case 'plate': case 'plate_d': {
				let col = metal(p, f, t, info, c.mat === 'plate' ? [184, 190, 202] : [104, 110, 124], 2.2);
				let g = null;
				const red = (hit) => { if (hit) { col = mix(RED, RED_HOT, 0.25); g = RED; } };
				// red energy lines: down the arms, across the chest and along the thigh / shin plates
				if ((P === 'arm' || P === 'forearm' || P === 'shin') && f === 'north') {
					red(Math.abs(info.i + 0.5 - info.w / 2) < 0.75 && t > 0.1 && t < 0.9);
				}
				if (P === 'pec' && f === 'north') {
					red(Math.abs(t - (0.18 + s * 0.4)) < 0.022 && s > 0.08 && s < 0.92);  // diagonal seam across each plate
					red(t > 0.86 && t < 0.92 && s > 0.15 && s < 0.85);
				}
				if (P === 'abs' && f === 'north') red(Math.abs(s - 0.5) < 0.06);
				if (P === 'core_ring' && f === 'north') { const d = Math.hypot(s - 0.5, (t - 0.5) * 0.9); if (d < 0.3) { col = d < 0.17 ? [255, 220, 210] : RED; g = RED; } }
				if (P === 'hand' && (f === 'north')) red(Math.abs(s - 0.5) < 0.08 && t < 0.7);
				if (P === 'face' && f === 'north') {
					const x = p[0], y = p[1], ax = Math.abs(x);
					// narrow angled eyes: slits that slope down toward the nose
					const ey = A.eyeY + (ax - 1.2) * 0.32;
					if (ax > 0.42 && ax < 1.9 && Math.abs(y - ey) < 0.34) { col = ax < 1.6 ? RED_HOT : RED; g = RED; }
					else if (ax > 0.3 && ax < 2.0 && Math.abs(y - ey) < 0.6) col = [20, 18, 22];
					// cheek lines and the nose ridge
					if (Math.abs(ax - 1.45 + (A.eyeY - y) * 0.12) < 0.1 && y < ey - 0.6 && y > A.mouthY + 0.5) col = mul(col, 0.55);
					if (ax < 0.18 && y > A.mouthY + 0.8 && y < ey) col = mul(col, 0.8);
				}
				if (P === 'jaw' && f === 'north') {
					// the mouth: a glowing slit with vertical teeth-like bars
					const y = p[1], ax = Math.abs(p[0]);
					if (Math.abs(y - A.mouthY) < 0.42 && ax < 1.45 - Math.abs(y - A.mouthY) * 0.8) {
						if ((Math.floor((p[0] + 2) * 3.2) & 1) === 0) { col = mix(RED, RED_HOT, 0.4); g = RED; } else { col = [60, 10, 12]; g = [140, 14, 10]; }
					}
				}
				if (P === 'crest' && f === 'up') red(Math.abs(s - 0.5) < 0.12 && t < 0.45);
				if (c.tag === 'claw') col = mix(col, [240, 244, 250], 0.4);
				return [col, 255, g];
			}
			case 'frame': {
				// black frame / under-suit: fine horizontal ribbing with faint red light leaking from the seams
				const rib = ((p[1] * 3.0 + (P === 'finger' ? 0 : p[0] * 0.15)) % 1 + 1) % 1;
				let col = rib < 0.22 ? [58, 58, 66] : [22, 22, 27];
				col = mul(col, 0.9 + 0.2 * fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
				let g = null;
				if ((P === 'core' || P === 'spine' || P === 'neck') && rib > 0.5 && rib < 0.62 && hash3(Math.floor(p[0] * 2), Math.floor(p[1] * 3), Math.floor(p[2] * 2)) > 0.55) { col = [200, 30, 26]; g = [150, 18, 14]; }
				if (P === 'joint') col = mix(col, [90, 90, 100], 0.3);
				if (info.ePx < 0.18) col = mul(col, 1.5);
				return [col, 255, g];
			}
			case 'red_core': {
				const k = 0.5 + 0.5 * Math.sin(p[1] * 3);
				return [mix(RED, RED_HOT, 0.3 + 0.3 * k), 255, mix(RED, [255, 120, 100], 0.2 * k)];
			}
			default: return [[255, 0, 255], 255, null];
		}
	};
}

function omegaPoses(M) {
	const fist = (n, k) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 30 * k], [`fingertips_${n}`]: [0, 0, s * 40 * k] }; };
	const stand = Object.assign({
		arm_r: [-4, 0, 6], forearm_r: [-14, 0, 0], arm_l: [-4, 0, -6], forearm_l: [-14, 0, 0],
		head: [6, 0, 0], thigh_r: [0, 0, 4], thigh_l: [0, 0, -4], foot_r: [0, 0, -4], foot_l: [0, 0, 4],
	}, fist('r', 1), fist('l', 1));
	const hover = Object.assign({
		$root: [0, 14, 0],
		arm_r: [-8, 0, 74], forearm_r: [-22, -20, 0], hand_r: [0, 0, 18],
		arm_l: [-8, 0, -74], forearm_l: [-22, 20, 0], hand_l: [0, 0, -18],
		chest: [-6, 0, 0], head: [10, 0, 0],
		thigh_r: [-10, 0, 6], shin_r: [26, 0, 0], foot_r: [44, 0, 0],
		thigh_l: [6, 0, -4], shin_l: [14, 0, 0], foot_l: [40, 0, 0],
	}, fist('r', -0.4), fist('l', -0.4));
	return { stand, hover };
}

function buildOmegaFinished(assetDir, noAssets) {
	const M = buildOmega();
	M.setHeight(4, c => c.bone !== 'head' || c.paint !== 'crest');
	M.layout();
	M.paint(shadeOmega(M));
	if (assetDir && !noAssets) M.writeAssets(assetDir, 'ultron_omega');
	M.poses = omegaPoses(M);
	M.poses.stand.$root = planted(M, M.poses.stand);
	console.log(`ultron_omega: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, scale ${M.MODEL_SCALE.toFixed(3)}`);
	return M;
}
function planted(M, pose) {
	const lo = M.bounds(M.world(pose), c => c.tag === 'sole').lo[1];
	return [0, -lo, 0];
}

module.exports = { buildOmega, buildOmegaFinished, omegaPoses };

if (require.main === module && !process.argv.includes('--game')) {
	const args = process.argv.slice(2);
	const ci = args.indexOf('--concept');
	const OUT = ci >= 0 ? args[ci + 1] : null;
	if (!OUT) { console.error('usage: node scratchpad/gen_ultron_omega_model.js --concept <outDir> [--no-assets]'); process.exit(1); }
	const M = buildOmegaFinished(path.join(OUT, 'assets'), args.includes('--no-assets'));
	const it = pose => ({ model: M, pose, root: pose.$root, floorR: 2.4 });
	const dark = { top: [96, 98, 108], bottom: [52, 54, 62] };
	L.sheet(path.join(OUT, '08_ultron_omega.png'), 'ULTRON OMEGA', '4 BLOCKS - VIBRANIUM PLATING OVER A BLACK FRAME, RED EYES / MOUTH / SEAMS', [
		{ label: 'FRONT', items: [it(M.poses.stand)], cam: { yaw: 0, pitch: 8 } },
		{ label: 'THREE-QUARTER', items: [it(M.poses.stand)], cam: { yaw: 35, pitch: 8 } },
		{ label: 'SIDE', items: [it(M.poses.stand)], cam: { yaw: 90, pitch: 8 } },
		{ label: 'ACTION: HOVERING, ARMS OUT', items: [it(M.poses.hover)], cam: { yaw: 20, pitch: 4 }, ownScale: true },
	], { minW: 1900, panelH: 1100, bg: dark, bloom: 0.55, light: Object.assign({}, L.DEFAULT_LIGHT, { rimCol: [1.0, 0.55, 0.5], rimK: 0.55 }) });
}

// ================================================================ GAME EXPORT (--game)
// TIMING TABLE (source of truth until the Contagion gameplay is merged; mirror it in OmegaAnims): server ticks, the
// Titan / FrostGiantAnims convention -- TRANSITION 4; a play-once / held clip is (length - 4) ticks long and keys each
// strike on (hit - 4); loops are `length` ticks and carry no hits. kind: once | hold | loop.
const OMEGA_TRANSITION = 4;
const OMEGA_TIMING = {
	idle: { kind: 'loop', length: 60 },
	fly: { kind: 'loop', length: 30 },
	walk: { kind: 'loop', length: 32, speed: 0.15 },
	vibranium_strike: { kind: 'once', length: 44, hits: [12, 22, 34] }, // 3-hit combo, 8 / 8 / 14 damage
	concussive_blast: { kind: 'once', length: 30, hits: [18] },
	lattice: { kind: 'once', length: 50, hits: [24] },                   // the rotating beams switch on at the hit
	siphon: { kind: 'loop', length: 40 },
	split: { kind: 'once', length: 30, hits: [18] },                     // the decoys appear on the hit
	override: { kind: 'once', length: 40, hits: [26] },                  // the Legion suits are hijacked on the hit
	upload: { kind: 'loop', length: 60 },
	hurt: { kind: 'once', length: 10 },
	stagger_kneel: { kind: 'hold', length: 40 },
	disintegrate: { kind: 'hold', length: 60 },
	self_destruct: { kind: 'hold', length: 80, hits: [70] },            // the blast on the hit
};

function omegaGame() {
	const fs = require('fs');
	const A = require('./lib_geckogen_anim.js');
	const { addPose: ap, mirrorPose: mirP } = A;
	const REPO = path.join(__dirname, '..');
	const M = buildOmega();
	M.setHeight(4, c => c.bone !== 'head' || c.paint !== 'crest');
	M.layout(); M.paint(shadeOmega(M));
	const P = omegaPoses(M);
	const an = new A.AnimSet(M, 'ultron_omega', { transition: OMEGA_TRANSITION, feet: c => c.tag === 'sole' });
	const fist = (n, k) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 30 * k], [`fingertips_${n}`]: [0, 0, s * 40 * k] }; };
	const S = P.stand, H = Object.assign({}, P.hover); delete H.$root;
	const HY = y => ({ $root: [0, y, 0], $ground: false });
	const strike = p => Object.assign({}, p, { $strike: true });
	const mk = (name, build, opts = {}) => {
		const t = OMEGA_TIMING[name];
		const len = t.kind === 'loop' ? t.length : t.length - OMEGA_TRANSITION;
		const hits = (t.hits || []).map(h => h - OMEGA_TRANSITION);
		an.clip(name, len, t.kind === 'loop' ? true : t.kind === 'hold' ? 'hold' : false, build(len, hits).map(([k, p, e]) => [Math.round(k), p, e]), Object.assign({ strikes: t.hits || [] }, opts));
	};
	// a fluid hover: slow bob, a lazy figure-eight sway, the hands drifting, the head tilting like it is curious
	mk('idle', len => [
		[0, ap(H, HY(12))], [len * 0.25, ap(H, { chest: [-3, 4, 2], head: [4, -12, 6], arm_r: [-6, 0, -8], arm_l: [4, 0, 4], forearm_r: [-10, 0, 0], shin_r: [6, 0, 0] }, HY(15)), 'easeinoutsine'],
		[len * 0.5, ap(H, { chest: [1, 0, -1], head: [-2, 0, 0], arm_r: [4, 0, 4], arm_l: [-6, 0, -8] }, HY(11)), 'easeinoutsine'],
		[len * 0.75, ap(H, { chest: [-2, -4, -2], head: [6, 14, -6], forearm_l: [-10, 0, 0], shin_l: [8, 0, 0] }, HY(14)), 'easeinoutsine'], [len, ap(H, HY(12)), 'easeinoutsine'],
	]);
	// flying: a forward dive, arms swept back, legs trailing
	const dive = ap(S, { root: [40, 0, 0], chest: [10, 0, 0], head: [-36, 0, 0], arm_r: [30, 0, 20], arm_l: [30, 0, -20], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], thigh_r: [10, 0, 0], thigh_l: [16, 0, 0], shin_r: [20, 0, 0], shin_l: [10, 0, 0], foot_r: [40, 0, 0], foot_l: [40, 0, 0] });
	mk('fly', len => [[0, ap(dive, HY(14))], [len * 0.5, ap(dive, { chest: [2, 0, 3], thigh_r: [6, 0, 0], thigh_l: [-6, 0, 0], arm_r: [4, 0, 4] }, HY(16)), 'easeinoutsine'], [len, ap(dive, HY(14)), 'easeinoutsine']]);
	// walking: a long, smooth, slightly predatory stride
	{
		const t = OMEGA_TIMING.walk, q = t.length / 4;
		const C = ap(S, { thigh_r: [-26, 0, 0], shin_r: [6, 0, 0], foot_r: [20, 0, 0], thigh_l: [20, 0, 0], shin_l: [16, 0, 0], foot_l: [-30, 0, 0], arm_r: [18, 0, 0], arm_l: [-18, 0, 0], hips: [0, -6, 0], chest: [4, 6, 0], head: [-4, -6, 0] });
		const Pp = ap(S, { thigh_l: [-24, 0, 0], shin_l: [40, 0, 0], foot_l: [-10, 0, 0], chest: [3, 0, 0], $y: 0.4 });
		mk('walk', () => [[0, C], [q, Pp, 'easeinoutsine'], [2 * q, ap(S, mirP({ thigh_r: [-26, 0, 0], shin_r: [6, 0, 0], foot_r: [20, 0, 0], thigh_l: [20, 0, 0], shin_l: [16, 0, 0], foot_l: [-30, 0, 0], arm_r: [18, 0, 0], arm_l: [-18, 0, 0], hips: [0, -6, 0], chest: [4, 6, 0], head: [-4, -6, 0] })), 'easeinoutsine'],
			[3 * q, ap(S, mirP({ thigh_l: [-24, 0, 0], shin_l: [40, 0, 0], foot_l: [-10, 0, 0] }), { chest: [3, 0, 0], $y: 0.4 }), 'easeinoutsine'], [4 * q, C, 'easeinoutsine']], { speed: t.speed });
	}
	// vibranium strike: right hook, left hook, then a two-handed hammer blow
	{
		const g = ap(S, { thigh_l: [-20, 0, -4], shin_l: [16, 0, 0], thigh_r: [10, 0, 4], shin_r: [10, 0, 0] });
		const r1 = ap(g, { waist: [0, 30, 0], chest: [0, 20, 0], arm_r: [20, 30, 50], forearm_r: [-80, 0, 0], ...fist('r', 3), arm_l: [-40, 0, -20], forearm_l: [-60, 0, 0], ...fist('l', 3) });
		const h1 = ap(g, { waist: [6, -30, 0], chest: [6, -20, 0], arm_r: [-90, -20, 0], forearm_r: [-10, 0, 0], ...fist('r', 3), arm_l: [-30, 0, -30], forearm_l: [-70, 0, 0], ...fist('l', 3), head: [0, 16, 0] });
		const h2 = ap(g, mirP({ waist: [6, -30, 0], chest: [6, -20, 0], arm_r: [-90, -20, 0], forearm_r: [-10, 0, 0], arm_l: [-30, 0, -30], forearm_l: [-70, 0, 0], head: [0, 16, 0] }), fist('r', 3), fist('l', 3));
		const up = ap(g, { arm_r: [-170, 0, -10], arm_l: [-170, 0, 10], forearm_r: [-40, 0, 0], forearm_l: [-40, 0, 0], chest: [-12, 0, 0], head: [-10, 0, 0], ...fist('r', 3), ...fist('l', 3) });
		const h3 = ap(g, { arm_r: [-70, 0, -20], arm_l: [-70, 0, 20], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], chest: [30, 0, 0], waist: [10, 0, 0], head: [-14, 0, 0], thigh_l: [-30, 0, 0], shin_l: [40, 0, 0], ...fist('r', 3), ...fist('l', 3) });
		mk('vibranium_strike', (len, [a, b, c]) => [[0, S], [a - 4, r1, 'easeinoutsine'], [a, strike(h1), 'easeinquad'], [b - 4, ap(h1, { waist: [0, 10, 0] }), 'easeoutquad'], [b, strike(h2), 'easeinquad'], [c - 6, up, 'easeinoutsine'], [c, strike(h3), 'easeinquad'], [c + 3, ap(h3, { chest: [-4, 0, 0] }), 'easeoutquad'], [len, S, 'easeinoutsine']]);
	}
	// concussive blast: drawn back, both palms thrust forward (the red cone fires on the hit)
	{
		const load = ap(H, { chest: [-14, 0, 0], arm_r: [20, 0, 30], arm_l: [20, 0, -30], forearm_r: [-90, 0, 0], forearm_l: [-90, 0, 0], head: [-6, 0, 0] });
		const push = ap(H, { chest: [12, 0, 0], arm_r: [-86, 0, -60], arm_l: [-86, 0, 60], forearm_r: [10, 0, 0], forearm_l: [10, 0, 0], hand_r: [-60, 0, -18], hand_l: [-60, 0, 18], head: [6, 0, 0], ...fist('r', -1), ...fist('l', -1) });
		mk('concussive_blast', (len, [h]) => [[0, ap(H, HY(12))], [h * 0.65, ap(load, HY(14)), 'easeinoutsine'], [h, strike(ap(push, HY(12))), 'easeoutback'], [h + 3, ap(push, { chest: [-6, 0, 0] }, HY(13)), 'easeoutquad'], [len, ap(H, HY(12)), 'easeinoutsine']]);
	}
	// lattice: rises, arms spread wide and raised, body arched (beams on from the hit)
	{
		const arch = ap(H, { chest: [-20, 0, 0], head: [-30, 0, 0], jaw: [16, 0, 0], arm_r: [-40, 0, 40], arm_l: [-40, 0, -40], forearm_r: [10, 0, 0], forearm_l: [10, 0, 0], ...fist('r', -1), ...fist('l', -1) });
		mk('lattice', (len, [h]) => [[0, ap(H, HY(12))], [h * 0.6, ap(H, { chest: [14, 0, 0], arm_r: [20, 0, -40], arm_l: [20, 0, 40] }, HY(10)), 'easeinoutsine'], [h, strike(ap(arch, HY(22))), 'easeoutback'], [h + 10, ap(arch, { chest: [0, 20, 0] }, HY(22)), 'easeinoutsine'], [len, ap(H, HY(12)), 'easeinoutsine']]);
	}
	// siphon: one hand reaching out, fingers spread and trembling, drawing power in
	{
		const reachP = ap(H, { arm_r: [-80, -20, -30], forearm_r: [-6, 0, 0], ...fist('r', -1.3), arm_l: [-20, 0, -40], forearm_l: [-60, 0, 0], chest: [8, -14, 0], head: [4, 14, 0] });
		mk('siphon', len => [[0, ap(reachP, HY(12))], [len * 0.25, ap(reachP, { forearm_r: [-4, 0, 2], ...fist('r', 0.3) }, HY(13)), 'easeinoutsine'], [len * 0.5, ap(reachP, { chest: [-3, 0, 0] }, HY(12)), 'easeinoutsine'], [len * 0.75, ap(reachP, { forearm_r: [4, 0, -2], ...fist('r', 0.3) }, HY(13)), 'easeinoutsine'], [len, ap(reachP, HY(12)), 'easeinoutsine']]);
	}
	// split: a violent glitch-jerk, arms flung out (the decoys peel off on the hit)
	{
		const jerk = ap(H, { chest: [10, 20, 10], head: [20, -30, 20], arm_r: [-30, 0, 100], arm_l: [-60, 0, -60], forearm_r: [-40, 0, 0] });
		mk('split', (len, [h]) => [[0, ap(H, HY(12))], [h - 6, ap(H, { chest: [16, 0, 0], arm_r: [10, 0, -20], arm_l: [10, 0, 20], head: [20, 0, 0] }, HY(11)), 'easeinoutsine'], [h - 2, ap(jerk, HY(12)), 'linear'], [h, strike(ap(H, { arm_r: [-20, 0, 30], arm_l: [-20, 0, -30], chest: [-10, 0, 0], head: [-10, 0, 0] }, HY(13))), 'linear'], [h + 3, ap(jerk, { head: [-30, 50, -30] }, HY(12)), 'linear'], [len, ap(H, HY(12)), 'easeinoutsine']]);
	}
	// override: two fingers to the temple, head cocked, then the hand sweeps out commanding
	{
		const temple = ap(H, { arm_r: [-150, 30, 30], forearm_r: [-110, 0, 0], ...fist('r', 1.5), head: [6, -10, -16], chest: [-4, 6, 0] });
		const command = ap(H, { arm_r: [-80, -40, -20], forearm_r: [-4, 0, 0], ...fist('r', -1), head: [-6, 20, 0], chest: [6, -16, 0], arm_l: [-10, 0, -50] });
		mk('override', (len, [h]) => [[0, ap(H, HY(12))], [h * 0.5, ap(temple, HY(12)), 'easeinoutsine'], [h - 4, ap(temple, { head: [4, 0, -6] }, HY(12)), 'easeinoutsine'], [h, strike(ap(command, HY(13))), 'easeoutback'], [len, ap(H, HY(12)), 'easeinoutsine']]);
	}
	// upload: rising, arms raised, head back, the body juddering as he streams into the network
	{
		const up = ap(H, { arm_r: [-150, 0, 20], arm_l: [-150, 0, -20], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], ...fist('r', -1.4), ...fist('l', -1.4), chest: [-18, 0, 0], head: [-40, 0, 0], jaw: [20, 0, 0] });
		mk('upload', len => [[0, ap(up, HY(16))], [len * 0.2, ap(up, { chest: [2, 4, 2], head: [4, 6, 4] }, HY(18)), 'linear'], [len * 0.35, ap(up, { chest: [-2, -4, -2], head: [-4, -6, -4] }, HY(17)), 'linear'], [len * 0.6, ap(up, { chest: [3, 0, 3] }, HY(19)), 'easeinoutsine'], [len * 0.8, ap(up, { head: [6, 10, 0] }, HY(17)), 'linear'], [len, ap(up, HY(16)), 'easeinoutsine']]);
	}
	mk('hurt', len => [[0, ap(H, HY(12))], [2, ap(H, { chest: [-16, 10, 8], head: [-20, -14, 10], arm_r: [-10, 0, 20], arm_l: [-10, 0, -20] }, HY(13)), 'easeoutquad'], [len, ap(H, HY(12)), 'easeinoutsine']]);
	// stagger kneel: drops out of the air onto one knee, a hand on the ground, sparking
	const kneel = ap(S, { thigh_r: [-80, 0, 6], shin_r: [96, 0, 0], foot_r: [-16, 0, 0], thigh_l: [10, 0, -4], shin_l: [90, 0, 0], foot_l: [-100, 0, 0], chest: [26, 0, 6], head: [20, 10, 8], arm_l: [-40, 0, -10], forearm_l: [-10, 0, 0], arm_r: [20, 0, 20], $ground: 'knees' });
	mk('stagger_kneel', len => [[0, ap(H, HY(12))], [8, ap(kneel, { chest: [-10, 0, 0] }), 'easeinquad'], [14, kneel, 'easeoutquad'], [24, ap(kneel, { head: [-10, -10, 0] }), 'easeinoutsine'], [len, kneel, 'easeinoutsine']]);
	// disintegrate: convulses, sags to his knees and slumps forward, arms hanging
	const slump = ap(kneel, { thigh_l: [-80, 0, -6], shin_l: [96, 0, 0], foot_l: [-16, 0, 0], chest: [40, 0, 0], head: [40, 0, 0], arm_r: [10, 0, 10], arm_l: [10, 0, -10], jaw: [24, 0, 0] });
	mk('disintegrate', len => [[0, ap(H, HY(12))], [6, ap(H, { chest: [-20, 10, 0], head: [-30, -20, 0], arm_r: [-60, 0, 60], arm_l: [-60, 0, -60] }, HY(12)), 'easeoutquad'], [12, ap(H, { chest: [10, -10, 0], head: [10, 20, 0] }, HY(10)), 'linear'], [26, ap(kneel, { chest: [10, 0, 0] }), 'easeinquad'], [40, slump, 'easeinoutsine'], [len, slump]]);
	// self destruct: arms spread, back arched, rising and shaking harder until the blast on the hit
	{
		const arc = ap(H, { arm_r: [-40, 0, 80], arm_l: [-40, 0, -80], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], ...fist('r', -1.5), ...fist('l', -1.5), chest: [-26, 0, 0], head: [-36, 0, 0], jaw: [30, 0, 0] });
		mk('self_destruct', (len, [h]) => {
			const f = [[0, ap(H, HY(12))], [16, ap(arc, HY(16)), 'easeinoutsine']];
			for (let t = 22, i = 0; t < h - 2; t += 4, i++) f.push([t, ap(arc, { chest: [(i % 2 ? 3 : -3) * (1 + t / h), (i % 2 ? 4 : -4), 0], head: [(i % 2 ? -4 : 4), (i % 2 ? 6 : -6), 0] }, HY(16 + t / 10)), 'linear']);
			f.push([h, strike(ap(arc, { chest: [-10, 0, 0], arm_r: [-20, 0, 10], arm_l: [-20, 0, -10] }, HY(24))), 'easeoutquad'], [len, ap(arc, HY(24))]);
			return f;
		});
	}
	const json = an.build();
	const RES = path.join(REPO, 'src/main/resources/assets/projecthero');
	const files = { geo: path.join(RES, 'geo/ultron/ultron_omega.geo.json'), tex: path.join(RES, 'textures/entity/ultron/ultron_omega.png'), glow: path.join(RES, 'textures/entity/ultron/ultron_omega_glowmask.png'), anim: path.join(RES, 'animations/ultron/ultron_omega.animation.json') };
	for (const f of Object.values(files)) fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(files.geo, JSON.stringify(M.geoJson('geometry.ultron_omega'), null, '\t') + '\n');
	fs.writeFileSync(files.tex, L.encodePng(M.TW, M.TH, M.tex));
	fs.writeFileSync(files.glow, L.encodePng(M.TW, M.TH, M.glow));
	fs.writeFileSync(files.anim, JSON.stringify(json, null, '\t') + '\n');
	const rest = M.bounds(M.world({}));
	const timing = { transition: OMEGA_TRANSITION, note: 'server ticks; a play-once/held clip is (length - 4) ticks long and keys each hit on (hit - 4); loops are length ticks. Hover clips lift the root ~12 px (0.75 block x model scale) above the hit-box floor.', model_scale_for_4_blocks: +M.MODEL_SCALE.toFixed(4), rest_height_px: +(rest.hi[1] - rest.lo[1]).toFixed(2), texture: [M.TW, M.TH], cubes: M.cubes().length, clips: {} };
	for (const [n, t] of Object.entries(OMEGA_TIMING)) timing.clips[n] = Object.assign({ server_length: t.length, kind: t.kind, clip_ticks: an.clips[n].len }, t.hits ? { hits: t.hits, strike_clip_ticks: t.hits.map(h => h - OMEGA_TRANSITION) } : {}, t.speed ? { move_speed: t.speed } : {});
	fs.writeFileSync(path.join(REPO, 'scratchpad/ultron_omega_anim_timing.json'), JSON.stringify(timing, null, '\t') + '\n');
	console.log(`ultron_omega: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ${Object.keys(an.clips).length} clips; wrote scratchpad/ultron_omega_anim_timing.json`);
	// previews from the written files
	const G = A.loadGameModel(files.geo, files.tex, files.glow, M.MODEL_SCALE);
	G.heightBlocks = 4;
	const anims = A.loadAnimations(files.anim);
	const frames = [['IDLE (HOVER)', 'idle', 0], ['IDLE', 'idle', 15], ['FLY', 'fly', 0], ['WALK', 'walk', 0]];
	for (const [n, t] of Object.entries(OMEGA_TIMING)) {
		if (['idle', 'fly', 'walk'].includes(n)) continue;
		if (t.hits) t.hits.forEach((h, i) => frames.push([`${n.toUpperCase().replace(/_/g, ' ')} AT HIT ${h}`, n, h - OMEGA_TRANSITION]));
		else frames.push([`${n.toUpperCase().replace(/_/g, ' ')}`, n, t.kind === 'loop' ? t.length / 4 : t.length - OMEGA_TRANSITION]);
	}
	const dir = path.join(REPO, '..', '..', '..', 'scratchpad', 'concepts_v0161', 'anim');
	fs.mkdirSync(dir, { recursive: true });
	for (let i = 0; i * 5 < frames.length; i++) {
		const panels = frames.slice(i * 5, i * 5 + 5).map(([label, n, t]) => { const pose = A.poseAt(anims, `animation.ultron_omega.${n}`, t); return { label, items: [{ model: G, pose, root: pose.$root, floorR: 2.4 }], cam: { yaw: 25, pitch: 8 } }; });
		L.sheet(path.join(dir, `ultron_omega_${i + 1}.png`), 'ULTRON OMEGA - GAME ASSETS', `PAGE ${i + 1} - POSES READ BACK FROM THE ANIMATION JSON`, panels, { minW: 1800, panelH: 1000, maxW: 3400, ss: 1, bg: { top: [96, 98, 108], bottom: [52, 54, 62] }, bloom: 0.55 });
	}
}
if (require.main === module && process.argv.includes('--game')) omegaGame();
