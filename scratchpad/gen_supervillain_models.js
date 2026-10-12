// v0.16.1 Supervillain Raid bosses: the Chimera, the Arsenal and the Omega Mage as real GeckoLib models (the same pipeline
// as the Frost Giants: lib_concept_models.js builds + paints them at 3 texels per model pixel, lib_geckogen_anim.js authors
// the clips with spring-simulated follow-through and checks every strike keyframe against the server timing table).
//
// Original comic-villain designs:
//   CHIMERA    - an escaped bio-lab hybrid: hunched lion-ram beast-man, mane, curling ram horns, a serpent tail with its own
//                snake head, a massive clawed lion arm (right) and a long scaled reptile arm (left), goat legs, a glowing
//                red core (the Chimera Core trophy) set in a cage of grafted ribs, red veins spreading from it, broken
//                lab shackles and chains.
//   ARSENAL    - a hulking power-armoured weapons platform: gunmetal plate with hazard-orange trim, an amber arc reactor
//                (the Arsenal Reactor trophy) in the chest, a T-visor, a twin shoulder cannon, a rotary gun right arm,
//                a heat-edged arm blade on the left gauntlet, a back power pack with glowing vents and exhaust stacks.
//   OMEGA MAGE - a floating sorcerer: high-collared violet and black robes with gold trim, a hood with a gold spiked crown,
//                a shadowed face with burning violet eyes, an omega-ring staff holding the Omega Crystal, an amulet,
//                glowing runes on the hem, a ring of orbiting rune tablets and two floating crystal shards.
//
// Usage (from the repo root of a checkout):
//   node scratchpad/gen_supervillain_models.js --concept [<outDir>] [--only chimera,arsenal,mage]
//   node scratchpad/gen_supervillain_models.js --game [--no-previews]
// --game writes src/main/resources/assets/projecthero/{geo,textures/entity,animations}/supervillain/<model>.* and
// scratchpad/supervillain_anim_timing.json, then renders preview sheets from the WRITTEN files. The clips live in
// supervillain_clips.js; their timing comes from SupervillainAnims.java (parsed).
//
// Conventions (Bedrock geo / GeckoLib): +Y up, the model faces -Z, the entity's RIGHT side is -X. Rotation X > 0 tips a
// bone's top forward; rotation Z > 0 swings a hanging limb toward -X (the right). Pose rotations are ADDED to the rest.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, seeded, vnorm, vadd, vsub, vsc } = L;

const args = process.argv.slice(2);
const ci = args.indexOf('--concept');
const GAME = args.includes('--game');
const REPO = path.join(__dirname, '..');
const MAIN_SCRATCH = fs.existsSync(path.join(REPO, '..', '..', '..', 'scratchpad')) ? path.join(REPO, '..', '..', '..', 'scratchpad') : path.join(REPO, 'scratchpad');
const SHOTS_DIR = path.join(MAIN_SCRATCH, 'shots_v0161', 'supervillain_models');
const OUT = ci >= 0 && args[ci + 1] && !args[ci + 1].startsWith('--') ? args[ci + 1] : SHOTS_DIR;
const oi = args.indexOf('--only');
const ONLY = oi >= 0 ? args[oi + 1].split(',') : null;
const WANT = n => !GAME && (!ONLY || ONLY.includes(n));

const SIDES = [[-1, 'r'], [1, 'l']];
const sxr = (s, a, b) => s > 0 ? [a, b] : [-b, -a];

// ================================================================ glow colours
const CORE_RED = [255, 46, 34], CORE_HOT = [255, 196, 150];
const AMBER = [255, 160, 40], AMBER_HOT = [255, 232, 170];
const VIOLET = [190, 80, 255], VIOLET_HOT = [242, 206, 255];
const EYE_GOLD = [255, 196, 40];

// ================================================================ material painters
const bevel = (col, info, k1 = 0.8, k2 = 0.93) => info.ePx < 0.3 ? mul(col, k1) : info.ePx < 0.62 ? mul(col, k2) : col;
const vgrad = (col, f, t, k = 0.2) => f === 'up' ? mul(col, 1.08) : f === 'down' ? mul(col, 0.6) : mul(col, 1.05 - k * t);

function fur(p, f, t, info, base, tip, dark) {
	const strand = fbm(p[0] * 3.4 + 3, p[1] * 1.4, p[2] * 3.4, 3);
	const clump = fbm(p[0] * 0.9, p[1] * 0.45 + 7, p[2] * 0.9, 3);
	let col = mix(dark || mul(base, 0.42), base, 0.25 + strand * 1.0);
	col = mix(col, tip, Math.pow(Math.max(0, strand - 0.42) * 1.9, 1.5) * (0.4 + clump));
	if (f !== 'up' && f !== 'down') col = mul(col, 1.1 - 0.32 * t);
	if (f === 'down') col = mul(col, 0.55);
	return bevel(col, info, 0.72, 0.9);
}
// overlapping scales in face-texel space: offset rows, dark rims, a light upper edge per scale
function scales(p, f, s, t, info, base, hi, belly) {
	const sz = info.D * 1.35;
	const row = Math.floor(info.j / sz);
	const off = (row & 1) ? sz * 0.5 : 0;
	const u = ((info.i + off) % sz) / sz, v = (info.j % sz) / sz;
	const r = Math.hypot(u - 0.5, (v - 0.35) * 1.1);
	const n = fbm(p[0] * 0.7, p[1] * 0.7, p[2] * 0.7 + 4, 3);
	let col = mix(mul(base, 0.75), mul(base, 1.2), n);
	if (belly && (f === 'north' || f === 'down')) col = mix(col, belly, 0.6);
	if (r > 0.48) col = mul(col, 0.55);
	else if (v < 0.3) col = mix(col, hi, 0.35 * (1 - v / 0.3));
	if (hash3(row, Math.floor((info.i + off) / sz), 9) > 0.93) col = mix(col, hi, 0.4);
	return bevel(vgrad(col, f, t, 0.18), info, 0.7, 0.9);
}
function flesh(p, f, t, info, base) {
	const n = fbm(p[0] * 0.35 + 3, p[1] * 0.35, p[2] * 0.35, 4);
	let col = mix(mul(base, 0.72), mul(base, 1.18), n);
	const v = Math.abs(fbm(p[0] * 0.5 + 40, p[1] * 0.25, p[2] * 0.5, 3) - 0.5);
	if (v < 0.025) col = mix(col, [86, 50, 74], 0.7); // dark veins
	col = mul(col, 0.94 + 0.1 * fbm(p[0] * 2.5, p[1] * 2.5, p[2] * 2.5, 2));
	return bevel(vgrad(col, f, t, 0.2), info, 0.76, 0.92);
}
function boneMat(p, f, t, info, tint) {
	const n = fbm(p[0] * 1.6, p[1] * 1.6 + 3, p[2] * 1.6, 3);
	let col = mix([140, 124, 100], [236, 226, 200], 0.25 + 0.75 * n);
	if (tint) col = mix(col, tint, 0.25);
	if (vnoise(p[0] * 5, p[1] * 5, p[2] * 5) > 0.84) col = mix(col, [100, 86, 70], 0.6);
	return bevel(vgrad(col, f, t, 0.15), info, 0.72, 0.92);
}
function hornMat(p, f, t, info, tipK) {
	let col = boneMat(p, f, t, info, [190, 160, 120]);
	const ring = Math.abs(Math.sin((p[0] * 0.7 + p[1] * 1.1 + p[2] * 0.9) * 2.4));
	col = mix(col, [84, 70, 56], (1 - ring) * 0.45);
	col = mix(col, [70, 58, 50], 0.35 * (1 - tipK));
	return col;
}
function metal(p, f, s, t, info, base, opts = {}) {
	const n = fbm(p[0] * 0.8 + 5, p[1] * 0.8, p[2] * 0.8, 3);
	let col = mix(mul(base, 0.78), mul(base, 1.18), n);
	col = mul(col, 0.95 + 0.07 * Math.sin((f === 'up' || f === 'down' ? p[0] : p[1]) * 9 + fbm(p[0] * 3, p[1] * 0.3, p[2] * 3, 2) * 4)); // brushed
	col = mix(col, [44, 40, 38], Math.max(0, fbm(p[0] * 0.5 + 90, p[1] * 0.5, p[2] * 0.5, 2) - 0.58) * 1.8); // grime
	if (vnoise(p[0] * 7 + p[1] * 2, p[1] * 0.6, p[2] * 7) > 0.88) col = mix(col, [200, 206, 214], 0.35); // scratches
	if (opts.panels !== false && info.fw > 3 && info.fh > 3) { // panel seams, world-locked
		const a = f === 'east' || f === 'west' ? p[2] : p[0];
		const sx = Math.abs(((a + 100) / (opts.panelW || 5.5)) % 1 - 0.5), sy = Math.abs(((p[1] + 100) / (opts.panelH || 4.5)) % 1 - 0.5);
		if (f !== 'up' && f !== 'down' && (sx > 0.47 || sy > 0.47)) col = mul(col, 0.6);
	}
	if (info.ePx < 0.28) col = mix(col, [214, 220, 228], 0.5); // bright worn edge
	else if (info.ePx < 0.6) col = mul(col, 0.78);
	if (opts.rivets !== false && info.fw > 2.4 && info.fh > 2.4) {
		const sx = Math.min(info.i + 0.5, info.w - info.i - 0.5) / info.D, sy = Math.min(info.j + 0.5, info.h - info.j - 0.5) / info.D;
		if (Math.abs(sx - 0.75) < 0.26 && Math.abs(sy - 0.75) < 0.26) col = [186, 190, 198];
		else if (Math.abs(sx - 0.75) < 0.36 && Math.abs(sy - 0.75) < 0.36) col = mul(col, 0.6);
	}
	return vgrad(col, f, t, 0.16);
}
function hazard(p, f, s, t, info) {
	const k = ((p[0] + p[1] + p[2]) * 0.55 + 100) % 2;
	let col = k < 1 ? [228, 150, 34] : [34, 32, 32];
	col = mix(col, [70, 60, 50], Math.max(0, fbm(p[0] * 0.8, p[1] * 0.8, p[2] * 0.8 + 3, 3) - 0.55) * 2);
	if (vnoise(p[0] * 6, p[1] * 6, p[2] * 6) > 0.86) col = mix(col, [150, 150, 150], 0.5); // chipped paint
	return bevel(vgrad(col, f, t, 0.15), info, 0.75, 0.92);
}
function cloth(p, f, t, info, base, foldK = 1) {
	const weave = ((info.i + info.j) & 1) ? 0.96 : 1.04;
	const n = fbm(p[0] * 0.8 + 21, p[1] * 0.4, p[2] * 0.8, 3);
	let col = mul(mix(mul(base, 0.66), mul(base, 1.24), n), weave);
	const fold = Math.sin((p[0] + p[2]) * 1.6 + fbm(p[0] * 0.4, p[1] * 0.2, p[2] * 0.4, 2) * 6);
	col = mul(col, 1 - 0.1 * foldK + 0.1 * foldK * fold);
	return bevel(vgrad(col, f, t, 0.14), info, 0.7, 0.9);
}
function goldMat(p, f, t, info, dark) {
	const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2);
	let col = mix(dark ? [96, 64, 22] : [128, 88, 30], dark ? [196, 150, 62] : [240, 196, 96], n);
	if (info.ePx < 0.28) col = [255, 236, 160];
	else if (info.ePx < 0.55) col = mul(col, 0.82);
	return vgrad(col, f, t, 0.2);
}
// angular glyph per cell (as the Frost casket's runes)
function runeGlyph(info, cell, seed, rowOnly = true) {
	const ci = Math.floor(info.i / cell), cj = Math.floor(info.j / cell);
	const u = (info.i % cell) / cell, v = (info.j % cell) / cell;
	if (info.i < cell * 0.5 || info.w - info.i < cell * 0.5) return false;
	const h = hash3(ci, cj, seed);
	if (h < 0.22 || (rowOnly && cj !== Math.floor(info.h / cell / 2))) return false;
	const w = 1.5 / cell;
	const vert = Math.abs(u - 0.5) < w;
	const d1 = Math.abs(u - v) < w * 1.2 && h > 0.5, d2 = Math.abs(u - (1 - v)) < w * 1.2 && h > 0.72 && v < 0.6;
	const bar = Math.abs(v - (h > 0.5 ? 0.28 : 0.72)) < w && u > 0.2 && u < 0.8 && h < 0.66;
	const circ = h > 0.86 && Math.abs(Math.hypot(u - 0.5, v - 0.5) - 0.3) < w;
	return (vert && v > 0.1 && v < 0.9) || d1 || d2 || bar || circ;
}

// ================================================================ CHIMERA
function buildChimera() {
	const M = new Model('chimera', { TW: 1024 });
	const A = M.A;
	const CH = [24, 0, 0], CP = [0, 40, 1]; // the hunched chest: built upright, every chest cube tipped forward 24 degrees about CP
	const tipM = L.pivotRot(CP, CH);
	const tip = q => L.affApply(tipM, q);
	A.core = [0, 45.3, -7.6];

	M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, 31, 1]);
	M.box(hips, -7.5, 27, -4, 7.5, 35, 6.5, 'flesh', { paint: 'pelvis' });
	M.box(hips, -6.5, 29, 5.5, 6.5, 35.5, 8.5, 'fur', { paint: 'rump' });
	// belt of lab chains + a torn hide loincloth
	M.box(hips, -8.1, 33, -4.6, 8.1, 34.6, 7.1, 'iron', { paint: 'chain' });
	M.box(hips, -8.3, 31.2, -4.8, 8.3, 32.4, 7.3, 'iron', { paint: 'chain' });
	M.cbox(hips, [0, 32.6, -5.1], [3.2, 3.2, 1.1], 'iron', { paint: 'lock' });
	const flapF = M.bone('flap_f', 'hips', [0, 31, -4.8]);
	M.box(flapF, -3.6, 19, -5.3, 3.6, 31.2, -4.6, 'hide', { paint: 'flap' });
	const flapB = M.bone('flap_b', 'hips', [0, 31, 7]);
	M.box(flapB, -4.2, 20, 6.8, 4.2, 31.2, 7.5, 'hide', { paint: 'flap' });

	// ---- goat legs (digitigrade): hip > knee (forward) > hock (back) > hoof
	for (const [s, n] of SIDES) {
		const x = s * 5.2, xx = x + s * 0.4;
		const thigh = M.bone(`thigh_${n}`, 'hips', [x, 31, 1]);
		M.tube(thigh, [[x, 33, 1.5], [xx, 20, -3.2]], [[7.6, 8.4]], 'fur', { paint: 'thigh' });
		M.tube(thigh, [[x + s * 0.4, 31, 4.2], [x + s * 0.2, 23, 0.5]], [[6.4, 4]], 'fur', { paint: 'thigh', seed: 3 });
		const shin = M.bone(`shin_${n}`, `thigh_${n}`, [xx, 20.5, -3]);
		M.tube(shin, [[xx, 21.5, -3.6], [xx, 9.5, 4.2]], [[5.2, 5.4]], 'fur', { paint: 'shin' });
		M.cbox(shin, [xx, 20.3, -4.6], [4.6, 3.4, 2.4], 'fur', { paint: 'knee' });
		const foot = M.bone(`foot_${n}`, `shin_${n}`, [xx, 9.5, 4.2]);
		M.tube(foot, [[xx, 10.2, 4.6], [xx, 3.0, 1.4]], [[3.6, 3.8]], 'hide', { paint: 'cannon' });
		M.cbox(foot, [xx, 9.6, 5.6], [4.6, 3.6, 3.2], 'fur', { paint: 'tuft' });
		M.cbox(foot, [xx, 3.6, 1.6], [5.0, 2.6, 4.8], 'fur', { paint: 'tuft' });
		for (const dx of [-1.25, 1.25]) M.box(foot, xx + dx - 1.15, 0, -2.6, xx + dx + 1.15, 2.9, 3.0, 'hoof', { tag: 'sole' });
		M.box(foot, xx - 1.0, 0.3, 2.8, xx + 1.0, 2.0, 4.2, 'hoof', { tag: 'sole' });
	}

	// ---- torso
	const waist = M.bone('waist', 'hips', [0, 35, 1]);
	M.box(waist, -6.8, 34, -4.6, 6.8, 41.5, 5.6, 'flesh', { paint: 'belly' });
	for (let i = 0; i < 3; i++) M.box(waist, -4.6, 34.6 + i * 2.3, -5.3, 4.6, 36.4 + i * 2.3, -4.4, 'scale', { paint: 'plate', seed: i });
	const flinch = M.bone('flinch', 'waist', [0, 40, 1]);
	const chest = M.bone('chest', 'flinch', [0, 40, 1]);
	const cb = (x0, y0, z0, x1, y1, z1, mat, o = {}) => M.box(chest, x0, y0, z0, x1, y1, z1, mat, Object.assign({ rot: CH, pivot: CP }, o));
	cb(-9.5, 39.5, -6.5, 9.5, 52, 7, 'flesh', { paint: 'ribcage' });
	cb(-12.5, 50, -7.2, 12.5, 59.5, 7.5, 'flesh', { paint: 'yoke' });
	cb(-11, 51, -8.2, -1.6, 57.4, -7, 'flesh', { paint: 'pec' });
	cb(1.6, 51, -8.2, 11, 57.4, -7, 'flesh', { paint: 'pec' });
	cb(-8, 40, 6.5, 8, 57, 9, 'fur', { paint: 'back' });
	for (const [y, w] of [[42.2, 7.6], [44.6, 8.4], [47.0, 8.8], [49.4, 8.6], [51.8, 7.6]]) for (const [s] of SIDES)
		cb(sxr(s, 2.9, w)[0], y - 0.65, -7.6, sxr(s, 2.9, w)[1], y + 0.65, -6.2, 'bone', { paint: 'rib', seed: Math.round(y * 3) });
	cb(-1.2, 41.2, -7.3, 1.2, 54.5, -6.2, 'bone', { paint: 'sternum' });
	// the Chimera Core: a cut ruby in a bolted steel lab socket
	const cy = -3.2; // the core sits low on the chest, below the mane
	cb(-3.6, 45.3 + cy, -8.4, 3.6, 52.2 + cy, -6.6, 'iron', { paint: 'socket' });
	cb(-2.7, 46.0 + cy, -9.4, 2.7, 51.5 + cy, -8.0, 'core', { paint: 'core' });
	cb(-1.8, 45.2 + cy, -9.0, 1.8, 52.3 + cy, -8.3, 'core', { paint: 'core' });
	cb(-3.4, 46.9 + cy, -9.0, 3.4, 50.6 + cy, -8.3, 'core', { paint: 'core' });
	cb(-1.3, 47.4 + cy, -10.1, 1.3, 50.1 + cy, -9.2, 'core_hot', { paint: 'core' });
	for (const [x, y] of [[-3.1, 45.6], [3.1, 45.6], [-3.1, 51.9], [3.1, 51.9]]) cb(x - 0.55, y - 0.55 + cy, -8.9, x + 0.55, y + 0.55 + cy, -8.1, 'iron', { paint: 'bolt' });
	// dorsal bone spikes, biggest over the shoulders
	[[56, 3.4, 2.0], [53, 4.6, 2.1], [49.6, 4.2, 1.9], [46.2, 3.4, 1.7], [43, 2.6, 1.4]].forEach(([y, len, w], i) => {
		M.stick(chest, tip([0, y, 8.6]), L.mv3(tipM.m, [0, 0.7, 1]), len, w, 'bone', { paint: 'spike', seed: 50 + i, tag: 'tipb' });
	});

	// ---- mane: a heavy ruff over the shoulders and behind the head, tufts down the chest (the core left open)
	const mane = M.bone('mane', 'chest', tip([0, 56, -2]));
	const mb = (x0, y0, z0, x1, y1, z1, o = {}) => M.box(mane, x0, y0, z0, x1, y1, z1, 'mane', Object.assign({ rot: CH, pivot: CP }, o));
	mb(-13.6, 52.5, -6, 13.6, 63.5, 9.5, { paint: 'ruff' });
	mb(-11.5, 60.5, -4, 11.5, 66, 8, { paint: 'ruff' });
	for (const [s] of SIDES) {
		mb(sxr(s, 4.6, 13.2)[0], 48.5, -9.6, sxr(s, 4.6, 13.2)[1], 55, -7.4, { paint: 'bib' });
		mb(sxr(s, 7.0, 12.5)[0], 45.0, -9.2, sxr(s, 7.0, 12.5)[1], 49, -7.6, { paint: 'bib' });
		mb(sxr(s, 11.5, 15.0)[0], 50, -5, sxr(s, 11.5, 15.0)[1], 61, 7, { paint: 'ruff' });
	}
	for (let i = 0; i < 7; i++) { const x = -10.5 + i * 3.5; mb(x - 1.6, 49 - (i % 2) * 2, 8.8, x + 1.6, 58, 10.6, { paint: 'lock', seed: i }); }

	// ---- neck (code-driven look) > head: ram-lion skull, heavy brow, fangs, amber eyes, curling ram horns
	const NB = tip([0, 59.5, -2]);
	const neck = M.bone('neck', 'chest', NB);
	M.tube(neck, [NB, [0, 58.6, -13.5]], [[7.6, 7.2]], 'mane', { paint: 'neckfur' });
	const HS = 1.24, HC = [0, 58.5, -14];
	const hq = q => [HC[0] + (q[0] - HC[0]) * HS, HC[1] + (q[1] - HC[1]) * HS, HC[2] + (q[2] - HC[2]) * HS];
	const hbx = (b, x0, y0, z0, x1, y1, z1, mat, o) => { const a = hq([x0, y0, z0]), c = hq([x1, y1, z1]); return M.box(b, a[0], a[1], a[2], c[0], c[1], c[2], mat, o); };
	const head = M.bone('head', 'neck', hq([0, 58.5, -13]));
	A.eyeY = hq([0, 61.1, 0])[1]; A.eyeX = 2.35 * HS; A.eyeK = HS; A.faceZ = hq([0, 0, -21.0])[2];
	hbx(head, -4.6, 55.4, -21.0, 4.6, 63.6, -12.6, 'flesh', { paint: 'skull' });
	hbx(head, -4.9, 61.9, -21.9, 4.9, 63.4, -18.6, 'flesh', { paint: 'brow' });
	hbx(head, -3.2, 54.8, -25.2, 3.2, 59.6, -20.4, 'flesh', { paint: 'muzzle' });
	hbx(head, -1.6, 57.6, -25.9, 1.6, 59.8, -24.6, 'nose');
	hbx(head, -2.2, 59.4, -24.0, 2.2, 61.6, -20.6, 'flesh', { paint: 'muzzle' });
	for (const [s] of SIDES) {
		hbx(head, s * 2.3 - 0.4, 53.4, -24.9, s * 2.3 + 0.4, 55.4, -24.3, 'tooth');
		hbx(head, sxr(s, 4.2, 5.4)[0], 56.2, -19.2, sxr(s, 4.2, 5.4)[1], 60.4, -15.0, 'flesh', { paint: 'cheek' });
	}
	A.mouthY = hq([0, 55.4, 0])[1];
	const jaw = M.bone('jaw', 'head', hq([0, 56.0, -14.5]));
	hbx(jaw, -3.3, 52.2, -24.6, 3.3, 55.2, -14.2, 'flesh', { paint: 'jaw' });
	hbx(jaw, -2.6, 54.6, -24.2, 2.6, 55.6, -21.0, 'tooth');
	for (const [s] of SIDES) hbx(jaw, s * 2.2 - 0.38, 55.2, -24.0, s * 2.2 + 0.38, 57.0, -23.4, 'tooth');
	hbx(jaw, -2.4, 49.4, -21.6, 2.4, 52.4, -16.4, 'mane', { paint: 'beard' });
	// lion mane framing the face: a base ruff and shaggy locks radiating out of it
	const ruff = M.bone('ruff', 'head', hq([0, 59, -14]));
	hbx(ruff, -6.8, 51.8, -16.2, 6.8, 65.8, -9.8, 'mane', { paint: 'faceruff' });
	for (let i = 0; i < 14; i++) {
		const a = (i / 14) * Math.PI * 2 + 0.2;
		const r0 = hq([Math.sin(a) * 5.2, 58.8 + Math.cos(a) * 5.6, -14.8]);
		const dir = [Math.sin(a), Math.cos(a) * 0.95 - 0.25, 0.45];
		const len = (6.2 + 2.2 * hash3(i, 3, 5)) * (Math.cos(a) < -0.3 ? 1.25 : 1);
		M.stick(ruff, r0, dir, len, [3.6 - 0.4 * hash3(i, 1, 1), 3.0], 'mane', { paint: 'lock', seed: i });
		M.stick(ruff, vadd(r0, vsc(vnorm(dir), len - 1.0)), vadd(dir, [0, -0.6, 0.2]), 3.2, [2.2, 2.0], 'mane', { paint: 'locktip', seed: i + 20 });
	}
	const horns = M.bone('horns', 'head', hq([0, 62, -16]));
	for (const [s] of SIDES) {
		const P = [[s * 4.0, 62.6, -16.8], [s * 6.6, 66.6, -14.2], [s * 10.6, 68.0, -10.6], [s * 14.0, 64.6, -8.6], [s * 14.8, 59.4, -10.8], [s * 13.4, 55.8, -14.8], [s * 11.0, 56.2, -18.4], [s * 9.8, 59.2, -20.4]].map(hq);
		const cubes = M.tube(horns, P, [[4.6, 4.6], [4.4, 4.4], [4.0, 4.0], [3.5, 3.5], [2.9, 2.9], [2.2, 2.2], [1.4, 1.4]], 'horn', { paint: 'horn' });
		cubes.forEach((c, i) => { c.hk = i / (cubes.length - 1); });
	}

	// ---- arms. Right: a massive lion arm with a clawed paw. Left: a long scaled reptile arm with talons.
	const SH = { r: tip([-12.6, 56, -0.5]), l: tip([12.6, 56, -0.5]) };
	A.SH = SH;
	{
		const sx = SH.r;
		const sh = M.bone('shoulder_r', 'chest', sx);
		M.cbox(sh, vadd(sx, [-0.6, 0.6, 0]), [8.6, 7.6, 9.4], 'fur', { paint: 'delt' });
		const arm = M.bone('arm_r', 'shoulder_r', sx);
		const E = [-15.4, 40.5, -8.6], W = [-15.6, 27.0, -11.4];
		M.tube(arm, [vadd(sx, [0, 1, 0]), E], [[7.4, 8.0]], 'fur', { paint: 'arm' });
		const fore = M.bone('forearm_r', 'arm_r', E);
		M.tube(fore, [vadd(E, [0, 1.2, 0.4]), W], [[8.4, 8.6]], 'fur', { paint: 'fore' });
		M.cbox(fore, vadd(E, [0, -2.2, 1.6]), [7.0, 5.2, 5.2], 'fur', { paint: 'fore' });
		M.tube(fore, [vadd(E, [-1.8, 0.2, 3.4]), vadd(W, [-1.2, 3.0, 3.4])], [[2.4, 1.6]], 'mane', { paint: 'elbowtuft' });
		M.cbox(fore, vadd(W, [0, 3.8, 0.4]), [9.4, 2.8, 9.4], 'iron', { paint: 'shackle' });
		const chain = M.bone('chain_r', 'forearm_r', vadd(W, [0, 3.0, 4.6]));
		for (let i = 0; i < 4; i++) M.cbox(chain, vadd(W, [0, 2.2 - i * 2.1, 5.0]), i % 2 ? [0.7, 2.4, 1.8] : [1.8, 2.4, 0.7], 'iron', { paint: 'link', seed: i });
		const hand = M.bone('hand_r', 'forearm_r', W);
		M.box(hand, W[0] - 4.3, W[1] - 5.4, W[2] - 4.6, W[0] + 4.3, W[1] + 0.6, W[2] + 4.2, 'fur', { paint: 'paw' });
		M.box(hand, W[0] - 3.6, W[1] - 6.0, W[2] - 3.8, W[0] + 3.6, W[1] - 5.0, W[2] + 3.6, 'pad');
		const fing = M.bone('fingers_r', 'hand_r', vadd(W, [0, -5, -2.4]));
		[-3.0, -1.0, 1.0, 3.0].forEach((dx, i) => {
			const b = vadd(W, [dx, -4.6, -4.4]);
			M.cbox(fing, vadd(b, [0, 0.2, 0.6]), [1.8, 2.6, 2.4], 'fur', { paint: 'toe', seed: i });
			const cl = M.tube(fing, [vadd(b, [0, 0.4, -0.4]), vadd(b, [0, -1.4, -3.2]), vadd(b, [0, -4.4, -4.4])], [[1.3, 1.5], [0.8, 1.0]], 'claw', { seed: i });
			cl[1].tag = 'tip';
		});
		M.tube(fing, [vadd(W, [3.9, -2.6, -2.4]), vadd(W, [5.2, -4.6, -4.6]), vadd(W, [5.0, -6.6, -5.6])], [[1.2, 1.3], [0.7, 0.9]], 'claw', { seed: 9 });
	}
	{
		const sx = SH.l;
		const sh = M.bone('shoulder_l', 'chest', sx);
		M.cbox(sh, vadd(sx, [0.4, 0.4, 0]), [7.0, 6.8, 7.6], 'scale', { paint: 'delt' });
		M.stick(sh, vadd(sx, [1.6, 3.0, 1.0]), [0.55, 1, 0.5], 4.2, 1.8, 'bone', { paint: 'spike', tag: 'tipb', seed: 70 });
		M.stick(sh, vadd(sx, [2.8, 1.4, 2.4]), [0.9, 0.7, 0.6], 3.4, 1.5, 'bone', { paint: 'spike', tag: 'tipb', seed: 71 });
		for (const dz of [-2.2, 0, 2.2]) M.cbox(sh, vadd(sx, [-2.9, -1.8, dz]), [1.0, 1.0, 1.0], 'iron', { paint: 'bolt' });
		const arm = M.bone('arm_l', 'shoulder_l', sx);
		const E = [15.2, 41.0, -4.0], W = [15.8, 26.4, -8.4];
		M.tube(arm, [vadd(sx, [0, 0.6, 0]), E], [[5.0, 5.4]], 'scale', { paint: 'arm' });
		const fore = M.bone('forearm_l', 'arm_l', E);
		M.tube(fore, [vadd(E, [0, 0.8, 0.3]), W], [[4.6, 4.8]], 'scale', { paint: 'fore' });
		M.stick(fore, vadd(E, [0.6, -0.2, 2.2]), [0.2, -0.3, 1], 3.6, 1.4, 'bone', { paint: 'spike', tag: 'tipb', seed: 72 });
		for (let i = 0; i < 3; i++) M.stick(fore, vadd(vadd(E, vsc(vsub(W, E), 0.25 + i * 0.22)), [2.0, 0, 0.6]), [1, 0.35, 0.15], 2.4 - i * 0.4, 1.0, 'bone', { paint: 'spike', tag: 'tipb', seed: 73 + i });
		const hand = M.bone('hand_l', 'forearm_l', W);
		M.box(hand, W[0] - 1.6, W[1] - 3.8, W[2] - 2.6, W[0] + 1.6, W[1] + 0.6, W[2] + 2.6, 'scale', { paint: 'hand' });
		const fing = M.bone('fingers_l', 'hand_l', vadd(W, [0, -3.6, 0]));
		const tips = M.bone('fingertips_l', 'fingers_l', vadd(W, [0, -7.6, -1]));
		[[-1.6, 1.0], [0, 1.1], [1.6, 0.95]].forEach(([dz, k], i) => {
			const b = vadd(W, [0, -3.6, dz - 0.4]);
			M.tube(fing, [b, vadd(b, [0.3, -4.2 * k, -0.6])], [[1.2, 1.2]], 'scale', { paint: 'finger', seed: i });
			const t0 = vadd(b, [0.3, -4.2 * k, -0.6]);
			const cl = M.tube(tips, [vadd(t0, [0, 0.3, 0]), vadd(t0, [0, -2.6, -1.2]), vadd(t0, [0, -4.4, -0.6])], [[0.9, 1.0], [0.55, 0.6]], 'claw', { seed: 20 + i });
			cl[1].tag = 'tip';
		});
		M.tube(fing, [vadd(W, [-1.2, -1.6, -2.4]), vadd(W, [-2.0, -3.6, -4.0]), vadd(W, [-1.6, -5.6, -4.6])], [[1.0, 1.0], [0.6, 0.7]], 'claw', { seed: 30 });
	}

	// ---- serpent tail with its own snake head
	const TP = [[0, 31.5, 7.5], [0, 26.5, 13.5], [-0.6, 20, 18.5], [-2.4, 13.5, 22.0], [-5.2, 8.6, 25.4], [-8.2, 6.6, 29.6], [-9.6, 8.4, 33.6]];
	const TWD = [[5.6, 5.6], [4.9, 4.9], [4.2, 4.2], [3.6, 3.6], [3.1, 3.1], [2.7, 2.7]];
	let parent = 'hips';
	for (let i = 0; i < 6; i++) {
		const b = M.bone(`tail${i + 1}`, parent, TP[i]);
		M.tube(b, [TP[i], TP[i + 1]], [TWD[i]], 'scale', { paint: 'tail', seed: i });
		M.stick(b, vadd(vsc(vadd(TP[i], TP[i + 1]), 0.5), [0, TWD[i][1] * 0.4, 0]), vadd(vsub(TP[i + 1], TP[i]), [0, 6, 0]), 1.8 - i * 0.18, 1.0, 'bone', { paint: 'spike', tag: 'tipb', seed: 90 + i });
		parent = b.name;
	}
	const H0 = TP[6];
	const th = M.bone('tail_head', 'tail6', H0);
	M.box(th, H0[0] - 1.9, H0[1] - 1.2, H0[2] - 0.6, H0[0] + 1.9, H0[1] + 1.6, H0[2] + 4.4, 'scale', { paint: 'snake' });
	M.box(th, H0[0] - 1.4, H0[1] - 0.6, H0[2] + 4.2, H0[0] + 1.4, H0[1] + 1.2, H0[2] + 6.2, 'scale', { paint: 'snake' });
	M.box(th, H0[0] - 2.2, H0[1] + 0.6, H0[2] + 1.0, H0[0] + 2.2, H0[1] + 1.9, H0[2] + 3.0, 'scale', { paint: 'snakebrow' });
	const tj = M.bone('tail_jaw', 'tail_head', vadd(H0, [0, -0.8, 0.6]));
	M.box(tj, H0[0] - 1.5, H0[1] - 2.2, H0[2] + 0.4, H0[0] + 1.5, H0[1] - 1.0, H0[2] + 5.8, 'scale', { paint: 'snakejaw' });
	for (const dx of [-0.9, 0.9]) M.box(th, H0[0] + dx - 0.25, H0[1] - 2.0, H0[2] + 5.0, H0[0] + dx + 0.25, H0[1] - 0.6, H0[2] + 5.6, 'tooth');
	A.snakeEye = [H0[0], H0[1] + 1.2, H0[2] + 2.0];
	M.ignoreFit = ['horns', 'tail1', 'ruff'];
	return M;
}

function chimeraShade(M) {
	const A = M.A;
	const FUR = [184, 134, 70], FUR_T = [236, 204, 140];
	const MANE = [104, 54, 28], MANE_T = [196, 118, 56], MANE_D = [42, 20, 14];
	const FLESH = [134, 104, 116];
	const SCALE = [60, 94, 56], SCALE_HI = [168, 186, 96], BELLY = [176, 168, 110];
	return (c, f, s, t, p, info) => {
		const P = c.paint;
		const vein = (col) => { // red veins spreading from the core across the flesh
			const d = Math.hypot(p[0] - A.core[0], (p[1] - A.core[1]) * 0.9, (p[2] - A.core[2]) * 0.6);
			if (d > 13) return [col, null];
			const r = Math.abs(fbm(p[0] * 0.55 + 7, p[1] * 0.55, p[2] * 0.55, 3) - 0.5);
			const w = 0.012 + 0.02 * (1 - d / 13);
			if (r < w) { const k = 1 - d / 13; return [mix(col, [255, 90, 70], 0.4 + 0.6 * k), mul(CORE_RED, 0.45 + 0.55 * k)]; }
			if (r < w * 1.8) return [mix(col, [120, 30, 40], 0.5), null];
			return [col, null];
		};
		switch (c.mat) {
			case 'fur': {
				const goat = P === 'shin' || P === 'tuft' || P === 'knee';
				let col = goat ? fur(p, f, t, info, [92, 66, 48], [170, 138, 104], [30, 22, 18]) : fur(p, f, t, info, FUR, FUR_T);
				if (P === 'thigh' && Math.abs(Math.sin(p[1] * 0.9 + fbm(p[0] * 0.3, p[1] * 0.2, p[2] * 0.3, 2) * 5)) < 0.12) col = mul(col, 0.85);
				if (P === 'paw' && f === 'down') col = [60, 40, 44];
				
				return [col, 255, null];
			}
			case 'mane': {
				let col = fur(p, f, t, info, MANE, MANE_T, MANE_D);
				if (P === 'beard' || P === 'elbowtuft') col = mul(col, 0.85);
				if (P === 'locktip') col = mix(col, MANE_T, 0.3);
				if (P === 'lock') col = mul(col, 0.92 + 0.16 * hash3(c.seed, 2, 2));
				return [col, 255, null];
			}
			case 'flesh': {
				let col = flesh(p, f, t, info, FLESH);
				let g = null;
				if (P === 'pec' && f === 'down') col = mul(col, 0.65);
				if (P === 'ribcage' || P === 'pec' || P === 'yoke' || P === 'belly') { const r = vein(col); col = r[0]; g = r[1]; }
				if ((P === 'yoke' || P === 'ribcage') && f !== 'up' && f !== 'down') { // surgical stitching where the grafts meet
					const d = Math.abs(Math.abs(p[0]) - 9.2);
					if (d < 0.25 && (info.j % 3) === 0) col = [40, 26, 30];
					else if (d < 0.7 && d > 0.4 && (info.j % 3) === 1) col = mul(col, 0.6);
				}
				if (P === 'skull' || P === 'muzzle' || P === 'brow' || P === 'cheek' || P === 'jaw') {
					col = mix(col, [150, 112, 96], 0.45);
					if (f === 'north' && P === 'skull') {
						const ex = Math.abs(Math.abs(p[0]) - A.eyeX), ey = p[1] - A.eyeY;
						if (ex < 1.6 * A.eyeK && Math.abs(ey) < 1.0 * A.eyeK) col = mul(col, 0.35);
						const e = Math.pow(ex / (1.2 * A.eyeK), 2) + Math.pow((ey + (Math.abs(p[0]) - A.eyeX) * 0.3) / (0.62 * A.eyeK), 2);
						if (e < 1) { col = mix([255, 240, 170], EYE_GOLD, Math.sqrt(e)); g = EYE_GOLD; if (Math.abs(Math.abs(p[0]) - A.eyeX) < 0.2) { col = [40, 10, 0]; g = null; } }
					}
					if (P === 'brow') col = mul(col, 0.8);
					if (P === 'muzzle' && f === 'north' && p[1] < 55.9) col = mul(col, 0.75);
					if (P === 'jaw' && f === 'north' && p[1] > 53.4) col = [40, 16, 22];
				}
				return [col, 255, g];
			}
			case 'scale': {
				let col = scales(p, f, s, t, info, SCALE, SCALE_HI, (P === 'tail' || P === 'snakejaw' || P === 'plate') ? BELLY : null);
				let g = null;
				if (P === 'plate') col = mix(col, BELLY, 0.55);
				if (P === 'snake' && (f === 'east' || f === 'west')) {
					const ez = p[2] - A.snakeEye[2], ey = p[1] - A.snakeEye[1];
					if (Math.hypot(ez * 0.8, ey) < 0.62) { col = EYE_GOLD; g = EYE_GOLD; }
				}
				if (P === 'snakejaw' && f === 'up') col = [70, 20, 30];
				return [col, 255, g];
			}
			case 'hide': {
				const n = fbm(p[0] * 1.4 + 11, p[1] * 1.4, p[2] * 1.4, 3);
				let col = mix([58, 40, 32], [112, 82, 58], n);
				if (P === 'flap' && t > 0.82 && hash3(info.i >> 1, 0, 3) > 0.5) return [[0, 0, 0], 0, null];
				if (P === 'cannon') col = mix(col, [70, 52, 44], 0.4);
				return [bevel(vgrad(col, f, t, 0.15), info, 0.7, 0.9), 255, null];
			}
			case 'hoof': {
				const n = fbm(p[0] * 2, p[1] * 3, p[2] * 2, 2);
				let col = mix([30, 26, 28], [76, 66, 60], n);
				if (f !== 'up' && f !== 'down' && Math.abs(Math.sin(p[1] * 4)) < 0.2) col = mul(col, 0.75);
				return [bevel(col, info, 0.7, 0.9), 255, null];
			}
			case 'pad': return [bevel([62, 44, 50], info), 255, null];
			case 'nose': return [bevel([34, 22, 26], info, 0.7, 0.9), 255, null];
			case 'tooth': return [mix([214, 206, 180], [252, 248, 236], t), 255, null];
			case 'claw': {
				let col = mix([30, 28, 32], [220, 210, 190], c.tag === 'tip' ? 0.55 : 0.08);
				if (info.ePx < 0.25) col = mix(col, [240, 232, 214], 0.4);
				return [vgrad(col, f, t, 0.1), 255, null];
			}
			case 'horn': return [hornMat(p, f, t, info, c.hk || 0), 255, null];
			case 'bone': {
				let col = boneMat(p, f, t, info);
				if (c.tag === 'tipb') col = mix(col, [242, 236, 220], 0.2);
				if (P === 'rib') col = mix(col, [210, 190, 170], 0.3);
				return [col, 255, null];
			}
			case 'iron': {
				let col = metal(p, f, s, t, info, [96, 100, 108], { panels: false, rivets: P === 'shackle' || P === 'socket' });
				col = mix(col, [110, 64, 40], Math.max(0, fbm(p[0] * 1.2, p[1] * 1.2, p[2] * 1.2 + 9, 2) - 0.5) * 1.5); // rust
				if (P === 'lock' && f === 'north' && Math.hypot(s - 0.5, t - 0.55) < 0.18) col = [20, 18, 18];
				return [col, 255, null];
			}
			case 'core': case 'core_hot': {
				const hot = c.mat === 'core_hot';
				const r = Math.max(Math.abs(s - 0.5), Math.abs(t - 0.5)) * 2;
				let col = mix(hot ? CORE_HOT : [255, 120, 96], CORE_RED, Math.pow(r, 1.2));
				if (info.ePx < 0.28) col = mix(col, [255, 220, 200], 0.6);
				if (Math.abs(s - t) < 0.06 || Math.abs(s - (1 - t)) < 0.06) col = mix(col, [255, 230, 210], 0.5);
				return [col, 255, hot ? CORE_HOT : CORE_RED];
			}
			default: return [[255, 0, 255], 255, null];
		}
	};
}

// ================================================================ ARSENAL
function buildArsenal() {
	const M = new Model('arsenal', { TW: 1024 });
	const A = M.A;
	M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, 28, 0]);
	M.box(hips, -8.2, 25, -5.2, 8.2, 32.5, 5.2, 'plate_d', { paint: 'pelvis' });
	M.box(hips, -3.4, 24, -6.4, 3.4, 31, -4.9, 'plate', { paint: 'cod' });
	M.box(hips, -8.8, 30.6, -5.8, 8.8, 33, 5.8, 'hazard', { paint: 'belt' });
	for (const [s] of SIDES) M.box(hips, sxr(s, 4.8, 10.2)[0], 22.5, -5.4, sxr(s, 4.8, 10.2)[1], 31, 5.4, 'plate', { paint: 'tasset', rot: [0, 0, -s * 8], pivot: [s * 8.5, 31, 0] });
	M.box(hips, -6.4, 23, 5.0, 6.4, 31, 6.6, 'plate', { paint: 'tasset' });

	for (const [s, n] of SIDES) {
		const x = s * 5.4;
		const thigh = M.bone(`thigh_${n}`, 'hips', [x, 28, 0]);
		M.box(thigh, x - 3.6, 16.4, -3.8, x + 3.6, 27.5, 3.8, 'suit', { paint: 'thigh' });
		M.box(thigh, x - 3.9, 17.4, -4.4, x + 3.9, 26.0, -1.0, 'plate', { paint: 'thighplate' });
		M.box(thigh, sxr(s, 8.0, 9.6)[0], 17.8, -3.2, sxr(s, 8.0, 9.6)[1], 26.6, 3.2, 'plate', { paint: 'thighside' });
		const shin = M.bone(`shin_${n}`, `thigh_${n}`, [x, 16.5, 0]);
		M.box(shin, x - 3.4, 4.2, -3.4, x + 3.4, 16.8, 3.6, 'suit', { paint: 'shin' });
		M.box(shin, x - 3.8, 5.0, -4.6, x + 3.8, 15.0, -0.2, 'plate', { paint: 'greave' });
		M.box(shin, x - 3.2, 14.2, -5.4, x + 3.2, 19.0, -2.6, 'livery', { paint: 'kneepad' });
		M.box(shin, x - 0.7, 16.0, -5.8, x + 0.7, 17.0, -5.2, 'glow_red', { paint: 'light' });
		M.box(shin, x - 1.0, 6, 3.4, x + 1.0, 14.5, 5.2, 'barrel', { paint: 'piston' });
		M.box(shin, x - 1.5, 12.5, 3.2, x + 1.5, 15.6, 5.6, 'plate_d', { paint: 'pistoncap' });
		const foot = M.bone(`foot_${n}`, `shin_${n}`, [x, 4.2, 0]);
		M.box(foot, x - 4.0, 0, -7.6, x + 4.0, 4.6, 4.6, 'plate_d', { paint: 'boot', tag: 'sole' });
		M.box(foot, x - 3.7, 0.4, -9.2, x + 3.7, 3.6, -7.4, 'plate', { paint: 'toecap', tag: 'sole' });
		M.box(foot, x - 4.3, 0, -9.4, x + 4.3, 1.0, 5.0, 'barrel', { paint: 'sole', tag: 'sole' });
		M.box(foot, x - 3.0, 3.8, -6.6, x + 3.0, 5.6, -2.0, 'hazard', { paint: 'bootstripe' });
	}

	const waist = M.bone('waist', 'hips', [0, 32, 0]);
	M.box(waist, -6.6, 31.5, -4.6, 6.6, 38.5, 4.6, 'suit', { paint: 'waist' });
	for (let i = 0; i < 3; i++) M.box(waist, -5.2 + i * 0.3, 32.2 + i * 2.1, -5.4, 5.2 - i * 0.3, 34.0 + i * 2.1, -4.2, 'plate', { paint: 'ab', seed: i });
	const flinch = M.bone('flinch', 'waist', [0, 38, 0]);
	const chest = M.bone('chest', 'flinch', [0, 38, 0]);
	M.box(chest, -11, 37.5, -7.4, 11, 50, 7.2, 'plate_d', { paint: 'torso' });
	M.box(chest, -13.6, 46, -8.6, 13.6, 57.2, 7.8, 'plate', { paint: 'chest' });
	M.box(chest, -10.4, 39, -8.4, 10.4, 47, -6.8, 'plate', { paint: 'ribplate' });
	M.box(chest, -12.0, 54.5, -7.6, 12.0, 58.6, 6.8, 'livery', { paint: 'yoke' });
	for (const [s] of SIDES) M.box(chest, sxr(s, 6.4, 13.8)[0], 49.6, -9.6, sxr(s, 6.4, 13.8)[1], 56.2, -8.2, 'plate', { paint: 'pecplate', rot: [0, s * -10, 0], pivot: [s * 6.4, 52, -9] });
	M.box(chest, -7.4, 56.5, -6.6, 7.4, 59.0, 5.8, 'plate_d', { paint: 'collar' });
	M.box(chest, -7.8, 58.4, -7.0, 7.8, 59.4, 6.2, 'hazard', { paint: 'collarstripe' });
	A.reactor = [0, 50.4, -9.4];
	const R = A.reactor;
	M.cbox(chest, vadd(R, [0, 0, 0.6]), [9.6, 9.6, 1.6], 'plate_d', { paint: 'housing' });
	M.cbox(chest, vadd(R, [0, 0, -0.2]), [7.6, 3.6, 1.2], 'reactor', { paint: 'ring' });
	M.cbox(chest, vadd(R, [0, 0, -0.2]), [3.6, 7.6, 1.2], 'reactor', { paint: 'ring' });
	M.cbox(chest, vadd(R, [0, 0, -0.2]), [6.2, 6.2, 1.2], 'reactor', { paint: 'ring' });
	M.cbox(chest, vadd(R, [0, 0, -0.7]), [3.4, 3.4, 1.0], 'reactor_hot', { paint: 'core' });
	for (const a of [45, 135, 225, 315]) { const q = vadd(R, [Math.cos(a * Math.PI / 180) * 4.4, Math.sin(a * Math.PI / 180) * 4.4, -0.4]); M.cbox(chest, q, [1.4, 1.4, 1.4], 'plate', { paint: 'clamp' }); }
	M.box(chest, -9.6, 40, 6.6, 9.6, 56, 12.6, 'plate_d', { paint: 'pack' });
	for (let i = 0; i < 5; i++) M.box(chest, -7.4, 42 + i * 2.4, 12.4, 7.4, 43.2 + i * 2.4, 13.2, 'plate', { paint: 'slat', seed: i });
	M.box(chest, -7.0, 41.6, 12.2, 7.0, 54, 12.6, 'vent', { paint: 'ventglow' });
	for (const [s] of SIDES) {
		const x = s * 6.4;
		M.box(chest, x - 2.0, 50, 9.2, x + 2.0, 63, 13.2, 'barrel', { paint: 'stack' });
		M.box(chest, x - 2.4, 61, 8.8, x + 2.4, 62.4, 13.6, 'hazard', { paint: 'stackband' });
		M.box(chest, x - 1.4, 62.8, 9.8, x + 1.4, 63.3, 12.6, 'vent', { paint: 'exhaust' });
	}
	M.tube(chest, [[-8, 44, 9], [-12.5, 42.5, 6], [-15.5, 40, 3.6]], [[2.2, 1.6], [2.2, 1.6]], 'ammo', { paint: 'belt' });

	const neck = M.bone('neck', 'chest', [0, 57, -1]);
	M.box(neck, -3.4, 55.5, -3.6, 3.4, 59.4, 2.4, 'suit', { paint: 'neck' });
	const head = M.bone('head', 'neck', [0, 58, -1]);
	M.box(head, -4.6, 57.6, -6.4, 4.6, 66.0, 2.6, 'plate', { paint: 'helm' });
	M.box(head, -4.9, 63.4, -6.9, 4.9, 66.6, 2.4, 'livery', { paint: 'dome' });
	M.box(head, -3.8, 57.0, -7.4, 3.8, 60.8, -5.6, 'plate', { paint: 'jaw' });
	M.box(head, -4.1, 61.2, -7.1, 4.1, 62.6, -6.0, 'visor', { paint: 'visor' });
	M.box(head, -0.75, 58.6, -7.2, 0.75, 62.4, -6.0, 'visor', { paint: 'visor' });
	M.box(head, -1.0, 64.0, -7.5, 1.0, 67.4, 1.6, 'hazard', { paint: 'crest' });
	M.box(head, 4.6, 59.0, -4.4, 5.6, 63.4, 0.4, 'plate_d', { paint: 'earpiece' });
	M.box(head, -5.6, 59.0, -4.4, -4.6, 63.4, 0.4, 'plate_d', { paint: 'earpiece' });
	const ant = M.bone('antenna', 'head', [5.2, 63, -1]);
	M.box(ant, 4.9, 62.6, -1.4, 5.5, 72, -0.8, 'barrel', { paint: 'antenna' });
	M.box(ant, 4.8, 71.4, -1.5, 5.6, 72.4, -0.7, 'glow_red', { paint: 'light' });

	const SH = { r: [-13.8, 54, 0], l: [13.8, 54, 0] };
	for (const [s, n] of SIDES) {
		const sx = SH[n];
		const sh = M.bone(`shoulder_${n}`, 'chest', sx);
		M.box(sh, sx[0] - 5.4, 49.2, -6.6, sx[0] + 5.4, 59.6, 6.6, 'livery', { paint: 'pauldron' });
		M.box(sh, sxr(s, 11.4, 20.2)[0], 46.2, -6.2, sxr(s, 11.4, 20.2)[1], 51.8, 6.2, 'plate', { paint: 'pauldron2', rot: [0, 0, s * 18], pivot: [s * 15, 51, 0] });
		M.box(sh, sxr(s, 8.0, 19.6)[0], 58.8, -7.0, sxr(s, 8.0, 19.6)[1], 60.6, 7.0, 'hazard', { paint: 'paulstripe' });
		M.box(sh, sxr(s, 9.6, 18.0)[0], 60.4, -5.2, sxr(s, 9.6, 18.0)[1], 61.8, 5.2, 'plate_d', { paint: 'paultop' });
		M.cbox(sh, [sx[0] + s * 2.2, 57.0, -6.9], [1.6, 1.6, 0.8], 'glow_red', { paint: 'light' });
	}
	{ // right arm: rotary cannon forearm
		const sx = SH.r;
		const arm = M.bone('arm_r', 'shoulder_r', sx);
		M.box(arm, sx[0] - 3.4, 38.4, -3.4, sx[0] + 3.4, 50.5, 3.4, 'suit', { paint: 'arm' });
		M.box(arm, sx[0] - 3.8, 41.0, -3.9, sx[0] + 3.8, 47.6, 3.9, 'plate', { paint: 'bicep' });
		const E = [sx[0], 38.6, 0];
		const fore = M.bone('forearm_r', 'arm_r', E);
		M.box(fore, E[0] - 4.6, 27.2, -4.8, E[0] + 4.6, 39.4, 4.6, 'plate_d', { paint: 'gunbody' });
		M.box(fore, E[0] - 5.0, 33.0, -5.2, E[0] + 5.0, 37.6, 5.0, 'livery', { paint: 'gunshroud' });
		M.box(fore, E[0] - 5.2, 35.6, -5.4, E[0] + 5.2, 36.8, 5.2, 'hazard', { paint: 'gunstripe' });
		M.box(fore, E[0] - 3.2, 25.2, -3.2, E[0] + 3.2, 27.4, 3.2, 'barrel', { paint: 'gunring' });
		M.box(fore, E[0] - 6.0, 29.5, -2.0, E[0] - 4.4, 34.5, 2.0, 'barrel', { paint: 'drum' });
		M.box(fore, E[0] - 6.6, 30.2, -1.4, E[0] - 4.2, 33.8, 1.4, 'ammo', { paint: 'feed' });
		const bar = M.bone('barrels', 'forearm_r', [E[0], 26, 0]);
		for (let i = 0; i < 6; i++) {
			const a = i * Math.PI / 3, r = 1.9;
			M.box(bar, E[0] + Math.cos(a) * r - 0.65, 15.4, Math.sin(a) * r - 0.65, E[0] + Math.cos(a) * r + 0.65, 26, Math.sin(a) * r + 0.65, 'barrel', { paint: 'barrel', seed: i });
		}
		M.box(bar, E[0] - 2.9, 16.2, -2.9, E[0] + 2.9, 17.6, 2.9, 'plate_d', { paint: 'clampring' });
		M.box(bar, E[0] - 2.9, 21.6, -2.9, E[0] + 2.9, 23.0, 2.9, 'plate_d', { paint: 'clampring' });
		M.box(bar, E[0] - 1.0, 15.0, -1.0, E[0] + 1.0, 15.8, 1.0, 'reactor', { paint: 'muzzle' });
	}
	{ // left arm: gauntlet, heavy fist, heat-edged blade
		const sx = SH.l;
		const arm = M.bone('arm_l', 'shoulder_l', sx);
		M.box(arm, sx[0] - 3.4, 38.4, -3.4, sx[0] + 3.4, 50.5, 3.4, 'suit', { paint: 'arm' });
		M.box(arm, sx[0] - 3.8, 41.0, -3.9, sx[0] + 3.8, 47.6, 3.9, 'plate', { paint: 'bicep' });
		const E = [sx[0], 38.6, 0];
		const fore = M.bone('forearm_l', 'arm_l', E);
		M.box(fore, E[0] - 4.4, 28.6, -4.4, E[0] + 4.4, 39.4, 4.4, 'livery', { paint: 'gauntlet' });
		M.box(fore, E[0] - 4.7, 36.0, -4.7, E[0] + 4.7, 37.2, 4.7, 'hazard', { paint: 'gauntstripe' });
		M.box(fore, E[0] + 3.8, 29.4, -2.6, E[0] + 5.2, 38.2, 2.6, 'plate_d', { paint: 'blademount' });
		const W = [E[0], 28.8, 0];
		const hand = M.bone('hand_l', 'forearm_l', W);
		M.box(hand, W[0] - 3.0, 23.8, -3.4, W[0] + 3.0, 29.4, 3.4, 'plate_d', { paint: 'fist' });
		M.box(hand, W[0] - 3.3, 26.2, -3.6, W[0] + 3.3, 28.6, -1.2, 'plate', { paint: 'knuckles' });
		const fing = M.bone('fingers_l', 'hand_l', [W[0], 24.2, -1.2]);
		[-2.2, -0.7, 0.8, 2.3].forEach((dz, i) => M.box(fing, W[0] - 1.4, 20.8, dz - 0.65, W[0] + 0.6, 24.4, dz + 0.65, 'plate_d', { paint: 'finger', seed: i, rivets: false }));
		M.box(hand, W[0] - 2.6, 23.0, -3.2, W[0] - 0.8, 27.0, -1.4, 'plate_d', { paint: 'thumb' });
		const blade = M.bone('blade_l', 'forearm_l', [E[0] + 4.6, 34, 0]);
		const bw = [5.2, 5.6, 5.6, 5.2, 4.6, 3.8, 2.6, 1.2];
		bw.forEach((w, i) => M.box(blade, E[0] + 4.9, 30 - (i + 1) * 3.0 - 0.05, -w / 2 - 0.6, E[0] + 6.0, 30 - i * 3.0 + 0.05, w / 2 - 0.6, 'blade', { seed: i, tag: i > 5 ? 'tip' : null }));
		M.box(blade, E[0] + 4.7, 26, -1.8, E[0] + 6.2, 37.6, 0.6, 'plate_d', { paint: 'spine' });
	}
	const can = M.bone('cannon', 'chest', [-12.6, 61.5, 3]);
	const cx = -5.6, cyy = 2.6; // shifted out over the right pauldron
	M.box(can, -10.6 + cx, 56.0 + cyy, 0.0, -6.6 + cx, 60.2 + cyy, 5.2, 'plate_d', { paint: 'mount' });
	M.box(can, -13.2 + cx, 59.4 + cyy, -1.6, -4.8 + cx, 65.2 + cyy, 8.8, 'plate', { paint: 'cannonbody' });
	M.box(can, -13.6 + cx, 62.0 + cyy, -2.0, -4.4 + cx, 63.2 + cyy, 9.2, 'hazard', { paint: 'cannonstripe' });
	for (const x of [-11.0, -7.0]) {
		M.box(can, x - 1.5 + cx, 60.8 + cyy, -11.6, x + 1.5 + cx, 63.8 + cyy, -1.4, 'barrel', { paint: 'cbarrel' });
		M.box(can, x - 1.9 + cx, 60.4 + cyy, -12.6, x + 1.9 + cx, 64.2 + cyy, -10.8, 'plate_d', { paint: 'cmuzzle' });
		M.box(can, x - 0.8 + cx, 61.6 + cyy, -12.8, x + 0.8 + cx, 63.0 + cyy, -12.4, 'reactor', { paint: 'cbore' });
	}
	M.box(can, -12.0 + cx, 65.0 + cyy, 1.0, -8.0 + cx, 66.2 + cyy, 6.0, 'vent', { paint: 'cvent' });
	M.ignoreFit = ['cannon', 'antenna'];
	return M;
}

function arsenalShade(M) {
	const GUN = [86, 92, 102], GUN_D = [48, 52, 60];
	return (c, f, s, t, p, info) => {
		const P = c.paint;
		switch (c.mat) {
			case 'plate': {
				let col = metal(p, f, s, t, info, GUN, { panelW: 6, panelH: 5, rivets: c.paint !== 'clamp' });
				if ((P === 'pauldron') && (f === 'east' || f === 'west') && Math.abs(p[1] - 54) < 2.2 && Math.abs(p[2]) < 4.5) { // stencilled chevrons
					const k = ((Math.abs(p[2]) * 0.9 - (p[1] - 54) * 0.9 + 100) % 1.8);
					if (k < 0.9) col = mix(col, [214, 218, 224], 0.75);
				}
				if (P === 'helm' && f === 'north' && p[1] < 61.2 && p[1] > 59.5 && Math.abs(p[0]) > 1.4) col = mul(col, 0.5);
				if (P === 'clamp') col = mix(col, [150, 130, 96], 0.35);
				return [col, 255, null];
			}
			case 'livery': { // war-paint orange over gunmetal, chipped to bare steel at the edges
				let col = metal(p, f, s, t, info, [176, 78, 26], { panelW: 6, panelH: 5 });
				if (vnoise(p[0] * 2.5, p[1] * 2.5, p[2] * 2.5) > 0.74 || info.ePx < 0.35) col = mix(col, metal(p, f, s, t, info, GUN), 0.75);
				if (P === 'pauldron' && (f === 'east' || f === 'west') && Math.abs(p[1] - 54) < 2.2 && Math.abs(p[2]) < 4.5 && ((Math.abs(p[2]) * 0.9 - (p[1] - 54) * 0.9 + 100) % 1.8) < 0.9) col = mix(col, [30, 30, 32], 0.8);
				return [col, 255, null];
			}
			case 'plate_d': return [metal(p, f, s, t, info, GUN_D, { panelW: 4, panelH: 3.5, rivets: P !== 'finger' }), 255, null];
			case 'suit': {
				let col = mix([30, 32, 36], [62, 64, 72], fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
				if (((info.j / info.D) * 2 | 0) % 2 === 0) col = mul(col, 0.78);
				if (((info.i + (info.j >> 1)) & 3) === 0) col = mul(col, 0.88);
				return [bevel(vgrad(col, f, t, 0.15), info, 0.7, 0.9), 255, null];
			}
			case 'hazard': return [hazard(p, f, s, t, info), 255, null];
			case 'barrel': {
				let col = mix([26, 28, 32], [92, 96, 104], 0.3 + 0.5 * fbm(p[0] * 3, p[1] * 1, p[2] * 3, 2));
				if (info.ePx < 0.25) col = mix(col, [170, 176, 184], 0.45);
				if (P === 'barrel' && f === 'down') col = [10, 8, 8];
				if (P === 'barrel' && f !== 'up' && f !== 'down' && t > 0.85) col = mix(col, [120, 70, 40], 0.5);
				if (P === 'cbarrel' && f !== 'north' && f !== 'south' && Math.abs(Math.sin(p[2] * 2.2)) < 0.18) col = mul(col, 0.6);
				return [vgrad(col, f, t, 0.12), 255, null];
			}
			case 'ammo': {
				const k = ((p[0] + p[1] + p[2]) * 1.4 + 100) % 1;
				const col = k < 0.62 ? mix([150, 110, 40], [236, 194, 90], fbm(p[0] * 3, p[1] * 3, p[2] * 3, 2)) : [44, 44, 48];
				return [bevel(col, info, 0.75, 0.92), 255, null];
			}
			case 'blade': {
				let col = mix([110, 116, 126], [196, 202, 212], fbm(p[0] * 2, p[1] * 0.6, p[2] * 2, 2));
				let g = null;
				const edge = f === 'north' || (c.tag === 'tip' && f === 'down');
				if (edge) { col = mix(AMBER_HOT, AMBER, t * 0.6); g = AMBER; }
				else if ((f === 'east' || f === 'west') && (f === 'east' ? 1 - s : s) < 0.2) { const k = 1 - (f === 'east' ? 1 - s : s) / 0.2; col = mix(col, [255, 140, 60], 0.6 * k); g = mul(AMBER, 0.6 * k); }
				if ((f === 'east' || f === 'west') && Math.abs(t - 0.5) < 0.05) col = mul(col, 0.7);
				return [bevel(col, info, 0.85, 0.95), 255, g];
			}
			case 'reactor': case 'reactor_hot': {
				const hot = c.mat === 'reactor_hot';
				const r = Math.hypot(s - 0.5, t - 0.5) * 2;
				let col = hot ? mix([255, 252, 236], AMBER_HOT, r) : mix(AMBER_HOT, AMBER, Math.min(1, r));
				if (!hot && P === 'ring' && f === 'north' && ((info.i + info.j) % 4 === 0)) col = mul(col, 0.86);
				return [col, 255, hot ? AMBER_HOT : AMBER];
			}
			case 'visor': return [mix(AMBER_HOT, AMBER, Math.abs(t - 0.5) * 2), 255, AMBER];
			case 'vent': {
				const k = Math.abs(Math.sin(p[1] * 2.6 + p[0] * 0.4));
				return [mix([255, 110, 30], AMBER_HOT, k * 0.6), 255, mix([255, 90, 20], AMBER, k)];
			}
			case 'glow_red': return [[255, 70, 50], 255, [255, 40, 30]];
			default: return [[255, 0, 255], 255, null];
		}
	};
}

// ================================================================ OMEGA MAGE
function buildMage() {
	const M = new Model('omega_mage', { TW: 1024 });
	const A = M.A;
	M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, 30, 0]);
	const skirt = M.bone('skirt', 'hips', [0, 30, 0]);
	M.box(skirt, -6.4, 21.5, -4.6, 6.4, 31, 4.6, 'robe', { paint: 'skirt' });
	M.box(skirt, -7.8, 12.0, -5.8, 7.8, 22.0, 5.8, 'robe', { paint: 'skirt' });
	M.box(skirt, -9.0, 3.5, -6.8, 9.0, 12.5, 6.8, 'robe', { paint: 'skirt' });
	M.box(skirt, -9.4, 3.2, -7.2, 9.4, 4.8, 7.2, 'trim', { paint: 'hemtrim' });
	const rnd = seeded(77);
	for (let i = 0; i < 10; i++) {
		const a = i / 10 * Math.PI * 2;
		const x = Math.sin(a) * 7.6, z = -Math.cos(a) * 5.8;
		M.box(skirt, x - 1.3, 0.2 + rnd() * 1.6, z - 1.0, x + 1.3, 4.0, z + 1.0, 'robe', { paint: 'tatter', seed: i });
	}
	const panel = (name, x0, x1, z0, z1) => {
		const b = M.bone(name, 'hips', [(x0 + x1) / 2, 29, (z0 + z1) / 2]);
		M.box(b, x0, 1.5, z0, x1, 29.5, z1, 'robe2', { paint: 'panel' });
		M.box(b, x0 - 0.1, 1.2, z0 - 0.1, x1 + 0.1, 3.0, z1 + 0.1, 'trim', { paint: 'paneltrim' });
		return b;
	};
	panel('robe_f', -3.2, 3.2, -7.6, -6.9);
	panel('robe_b', -4.6, 4.6, 6.9, 7.6);
	M.box('robe_f', -1.6, 5.0, -7.75, 1.6, 27.5, -7.55, 'runecloth', { paint: 'runecol' });

	const waist = M.bone('waist', 'hips', [0, 30, 0]);
	M.box(waist, -5.4, 29.5, -4.0, 5.4, 36.5, 4.0, 'robe2', { paint: 'waist' });
	M.box(waist, -5.8, 30.5, -4.4, 5.8, 32.6, 4.4, 'trim', { paint: 'sash' });
	M.cbox(waist, [0, 31.6, -4.6], [2.6, 2.6, 0.8], 'gold', { paint: 'buckle' });
	const flinch = M.bone('flinch', 'waist', [0, 36, 0]);
	const chest = M.bone('chest', 'flinch', [0, 36, 0]);
	M.box(chest, -6.8, 35.5, -4.4, 6.8, 49.5, 4.4, 'robe', { paint: 'torso' });
	M.box(chest, -7.6, 44.0, -4.9, 7.6, 50.2, 4.9, 'robe2', { paint: 'mantle' });
	for (const [s] of SIDES) M.box(chest, sxr(s, 0.6, 2.2)[0], 37, -5.15, sxr(s, 0.6, 2.2)[1], 49.6, -4.75, 'trim', { paint: 'vtrim', rot: [0, 0, s * -14], pivot: [s * 1.4, 49.6, -4.9] });
	A.amulet = [0, 43.6, -5.6];
	M.cbox(chest, A.amulet, [3.8, 3.8, 0.9], 'gold', { paint: 'amulet' });
	M.cbox(chest, vadd(A.amulet, [0, 0, -0.6]), [2.0, 2.0, 0.7], 'crystal', { paint: 'gem' });
	M.cbox(chest, vadd(A.amulet, [0, 0, -0.6]), [1.0, 2.8, 0.6], 'crystal', { paint: 'gem' });
	for (const [s, n] of SIDES) {
		const sh = M.bone(`shoulder_${n}`, 'chest', [s * 7.2, 48, 0]);
		M.box(sh, sxr(s, 4.6, 11.2)[0], 45.2, -5.6, sxr(s, 4.6, 11.2)[1], 50.4, 5.6, 'robe2', { paint: 'spaul', rot: [0, 0, s * 22], pivot: [s * 6, 50, 0] });
		M.box(sh, sxr(s, 6.4, 12.2)[0], 43.4, -5.0, sxr(s, 6.4, 12.2)[1], 46.4, 5.0, 'robe', { paint: 'spaul2', rot: [0, 0, s * 30], pivot: [s * 7, 47, 0] });
		M.box(sh, sxr(s, 4.4, 11.4)[0], 49.6, -5.8, sxr(s, 4.4, 11.4)[1], 50.6, 5.8, 'trim', { paint: 'spaultrim', rot: [0, 0, s * 22], pivot: [s * 6, 50, 0] });
	}
	const collar = M.bone('collar', 'chest', [0, 49, 2]);
	for (const [s] of SIDES) {
		const o = { rot: [-14, s * 30, s * -10], pivot: [s * 1.0, 48, 3.0] };
		M.box(collar, sxr(s, 1.0, 8.6)[0], 47.6, 2.6, sxr(s, 1.0, 8.6)[1], 63.5, 3.6, 'robe2', Object.assign({ paint: 'collar' }, o));
		M.box(collar, sxr(s, 1.2, 8.2)[0], 48.6, 2.1, sxr(s, 1.2, 8.2)[1], 62.6, 2.65, 'runecloth', Object.assign({ paint: 'collarlining' }, o));
		M.box(collar, sxr(s, 8.4, 9.2)[0], 47.6, 2.4, sxr(s, 8.4, 9.2)[1], 64.4, 3.8, 'gold', Object.assign({ paint: 'collaredge' }, o));
	}
	const cape = M.bone('cape', 'chest', [0, 49, 5]);
	M.box(cape, -7.8, 6, 5.0, 7.8, 49.2, 6.0, 'robe2', { paint: 'cape' });
	for (let i = 0; i < 6; i++) { const x = -6.5 + i * 2.6; M.box(cape, x - 1.2, 2.5 + (i % 3) * 1.2, 5.1, x + 1.2, 6.5, 5.9, 'robe2', { paint: 'capetatter', seed: i }); }

	const neck = M.bone('neck', 'chest', [0, 49.5, 0]);
	M.box(neck, -2.6, 48, -2.6, 2.6, 52, 2.6, 'shadow', { paint: 'neck' });
	const head = M.bone('head', 'neck', [0, 51, 0]);
	A.eyeY = 54.6; A.eyeX = 1.5;
	M.box(head, -4.6, 49.8, -4.6, 4.6, 59.6, 4.8, 'robe', { paint: 'hood' });
	M.box(head, -3.4, 59.0, -3.4, 3.4, 61.2, 5.2, 'robe', { paint: 'hood' });
	M.box(head, -2.0, 60.0, 1.5, 2.0, 62.2, 7.0, 'robe', { paint: 'hoodpeak', rot: [-20, 0, 0], pivot: [0, 60, 3] });
	M.box(head, -3.5, 50.4, -4.75, 3.5, 58.0, -4.5, 'shadow', { paint: 'face' });
	M.box(head, -4.8, 57.6, -6.6, 4.8, 60.0, -4.4, 'robe', { paint: 'brim', rot: [-12, 0, 0], pivot: [0, 59, -4.6] });
	for (const [s] of SIDES) M.box(head, sxr(s, 3.6, 5.2)[0], 50.0, -6.2, sxr(s, 3.6, 5.2)[1], 58.6, -4.4, 'robe', { paint: 'brim' });
	M.box(head, -2.6, 50.0, -4.9, 2.6, 52.2, -4.6, 'silver', { paint: 'mask' });
	M.box(head, -4.8, 49.6, -5.0, 4.8, 50.6, 4.9, 'trim', { paint: 'hoodtrim' });
	for (const [s] of SIDES) M.box(head, sxr(s, 3.4, 4.8)[0], 50.4, -5.0, sxr(s, 3.4, 4.8)[1], 59.6, -4.4, 'trim', { paint: 'hoodtrim' });
	const crown = M.bone('crown', 'head', [0, 58.5, 0]);
	M.box(crown, -4.9, 57.8, -4.9, 4.9, 59.6, 4.9, 'gold', { paint: 'circlet' });
	[[0, 7.0, 1.3], [-2.4, 5.0, 1.0], [2.4, 5.0, 1.0], [-4.6, 3.6, 0.9], [4.6, 3.6, 0.9]].forEach(([x, len, w], i) => M.stick(crown, [x, 59.2, -4.4 + Math.abs(x) * 0.6], [x * 0.05, 1, 0.12], len, w, 'gold', { paint: 'spike', seed: i }));
	M.cbox(crown, [0, 59.0, -5.1], [1.6, 1.6, 0.6], 'crystal', { paint: 'gem' });

	for (const [s, n] of SIDES) {
		const sx = [s * 8.4, 47, 0];
		const arm = M.bone(`arm_${n}`, `shoulder_${n}`, sx);
		M.box(arm, sx[0] - 2.4, 36, -2.6, sx[0] + 2.4, 48, 2.6, 'robe', { paint: 'sleeve' });
		const E = [sx[0] + s * 0.4, 36.4, 0];
		const fore = M.bone(`forearm_${n}`, `arm_${n}`, E);
		M.box(fore, E[0] - 2.8, 30, -3.0, E[0] + 2.8, 37.4, 3.0, 'robe', { paint: 'sleeve' });
		M.box(fore, E[0] - 3.8, 25.8, -4.0, E[0] + 3.8, 31, 4.0, 'robe', { paint: 'cuff' });
		M.box(fore, E[0] - 4.0, 25.4, -4.2, E[0] + 4.0, 26.8, 4.2, 'trim', { paint: 'cufftrim' });
		M.box(fore, E[0] - 3.9, 27.4, -4.1, E[0] + 3.9, 28.8, 4.1, 'runecloth', { paint: 'cuffrune' });
		const W = [E[0], 26.6, 0];
		const hand = M.bone(`hand_${n}`, `forearm_${n}`, W);
		M.box(hand, W[0] - 1.0, 23.4, -1.6, W[0] + 1.0, 27.0, 1.6, 'skin', { paint: 'hand' });
		const fing = M.bone(`fingers_${n}`, `hand_${n}`, [W[0], 23.6, 0]);
		const tips = M.bone(`fingertips_${n}`, `fingers_${n}`, [W[0], 20.8, 0]);
		[-1.2, -0.4, 0.4, 1.2].forEach((z, i) => {
			const l = [3.0, 3.4, 3.2, 2.6][i];
			M.box(fing, W[0] - 0.4, 23.8 - l, z - 0.32, W[0] + 0.4, 23.9, z + 0.32, 'skin', { paint: 'finger' });
			M.box(tips, W[0] - 0.36, 20.9 - l * 0.8, z - 0.3, W[0] + 0.36, 21.1, z + 0.3, 'skin', { paint: 'fingertip' });
		});
		M.box(hand, W[0] - s * 0.6 - 0.35, 22.6, -2.2, W[0] - s * 0.6 + 0.35, 25.2, -1.5, 'skin', { paint: 'thumb' });
	}
	// the Omega staff: an ebony shaft banded in gold, an omega ring holding the floating Omega Crystal
	const WR = M.B.hand_r.pivot;
	const sx = WR[0];
	const st = M.bone('staff', 'hand_r', [sx, 24.6, -0.2]);
	M.box(st, sx - 0.75, 0.0, -0.95, sx + 0.75, 60, 0.55, 'wood', { paint: 'shaft' });
	for (const y of [2, 20.5, 28, 46, 58.5]) M.box(st, sx - 1.05, y, -1.25, sx + 1.05, y + 1.4, 0.85, 'gold', { paint: 'band' });
	A.crystal = [sx, 68.2, -0.2];
	const OC = A.crystal;
	const ring = [];
	for (let i = 0; i <= 10; i++) { const a = (-140 + i * 28) * Math.PI / 180; ring.push([sx + Math.sin(a) * 5.4, OC[1] + Math.cos(a) * 5.4, -0.2]); }
	M.tube(st, ring, [[1.3, 1.5]], 'gold', { paint: 'omega', overlap: 0.5 });
	for (const k of [-1, 1]) M.tube(st, [ring[k < 0 ? 0 : 10], [sx + k * 4.6, OC[1] - 6.6, -0.2], [sx + k * 6.6, OC[1] - 6.2, -0.2]], [[1.3, 1.5], [1.1, 1.3]], 'gold', { paint: 'omega' });
	M.tube(st, [[sx, 59.6, -0.2], [sx, OC[1] - 5.0, -0.2]], [[1.6, 1.6]], 'gold', { paint: 'omega' });
	for (const k of [-1, 1]) M.tube(st, [[sx + k * 1.2, 60.0, -0.2], [sx + k * 4.6, OC[1] - 6.4, -0.2]], [[0.8, 0.8]], 'gold', { paint: 'omega' });
	const cry = M.bone('crystal', 'staff', OC);
	M.cbox(cry, OC, [2.6, 4.0, 2.6], 'crystal_hot', { paint: 'omegacrystal' });
	M.cbox(cry, OC, [1.6, 6.6, 1.6], 'crystal', { paint: 'omegacrystal' });
	M.cbox(cry, OC, [3.6, 1.8, 3.6], 'crystal', { paint: 'omegacrystal' });
	const WL = M.B.hand_l.pivot;
	const spell = M.bone('spell_l', 'hand_l', [WL[0], 19, -3]);
	M.cbox(spell, [WL[0], 19, -3.4], [3.2, 3.2, 3.2], 'crystal_hot', { paint: 'orb' });
	M.cbox(spell, [WL[0], 19, -3.4], [5.0, 1.0, 1.0], 'crystal', { paint: 'orb' });
	M.cbox(spell, [WL[0], 19, -3.4], [1.0, 5.0, 1.0], 'crystal', { paint: 'orb' });
	M.cbox(spell, [WL[0], 19, -3.4], [1.0, 1.0, 5.0], 'crystal', { paint: 'orb' });
	const orbit = M.bone('orbit', 'root', [0, 30, 0]);
	for (let i = 0; i < 6; i++) {
		const a = i * Math.PI / 3 + 0.3;
		const x = Math.sin(a) * 14, z = -Math.cos(a) * 14;
		M.box(orbit, x - 1.8, 29.0 + (i % 2) * 2.6, z - 0.4, x + 1.8, 33.4 + (i % 2) * 2.6, z + 0.4, 'tablet', { paint: 'tablet', seed: i, rot: [0, -a * 180 / Math.PI, 0], pivot: [x, 31, z] });
	}
	for (const [s, n] of SIDES) {
		const b = M.bone(`shard_${n}`, 'chest', [s * 14.5, 55, 3]);
		M.cbox(b, [s * 14.5, 55, 3], [1.8, 4.6, 1.8], 'crystal', { paint: 'shard', rot: [0, 45, s * 10], pivot: [s * 14.5, 55, 3] });
		M.cbox(b, [s * 14.5, 55, 3], [1.0, 6.4, 1.0], 'crystal_hot', { paint: 'shard', rot: [0, 45, s * 10], pivot: [s * 14.5, 55, 3] });
	}
	M.ignoreFit = ['staff', 'crown', 'collar', 'orbit', 'shard_r', 'shard_l', 'spell_l'];
	M.floats = true;
	return M;
}

function mageShade(M) {
	const A = M.A;
	const ROBE = [58, 32, 92], ROBE2 = [26, 20, 40];
	return (c, f, s, t, p, info) => {
		const P = c.paint;
		switch (c.mat) {
			case 'robe': {
				let col = cloth(p, f, t, info, ROBE, P === 'skirt' ? 1.4 : 1);
				if (P === 'tatter' && t > 0.55 && hash3(info.i >> 1, c.seed, 5) > 0.55) return [[0, 0, 0], 0, null];
				let g = null;
				if (P === 'skirt' && f !== 'up' && f !== 'down' && p[1] > 5.0 && p[1] < 9.4) { // a band of glowing runes near the hem
					const cell = Math.max(6, Math.round(info.D * 2.6));
					const jj = Math.round((p[1] - 5.0) / 4.4 * cell);
					const ii = Math.round((f === 'north' || f === 'south' ? p[0] : p[2]) * info.D) + 400;
					if (runeGlyph({ i: ii, j: jj, w: 100000, h: cell }, cell, 31 + f.length * 7)) { col = mix(VIOLET_HOT, VIOLET, 0.4); g = VIOLET; }
					else col = mul(col, 0.62);
				}
				if (P === 'hood' && f === 'north' && Math.abs(p[0]) < 3.4 && p[1] < 58.4) col = mul(col, 0.4);
				return [col, 255, g];
			}
			case 'robe2': {
				let col = cloth(p, f, t, info, ROBE2, 1.2);
				if (P === 'capetatter' && t > 0.5 && hash3(info.i >> 1, c.seed, 7) > 0.5) return [[0, 0, 0], 0, null];
				if (P === 'cape' && f === 'north') col = mix(col, [90, 22, 40], 0.6);
				if (P === 'collar' && f === 'north') col = mul(col, 0.7);
				return [col, 255, null];
			}
			case 'trim': {
				let col = goldMat(p, f, t, info, true);
				if (f !== 'up' && f !== 'down' && Math.abs(((info.i + Math.round(Math.sin(info.j * 0.9) * 2)) % 6) - 3) < 1) col = mul(col, 0.72);
				return [col, 255, null];
			}
			case 'gold': {
				let col = goldMat(p, f, t, info, false);
				if (P === 'amulet' && f === 'north' && Math.abs(Math.hypot(s - 0.5, t - 0.5) - 0.42) < 0.05) col = [255, 240, 180];
				return [col, 255, null];
			}
			case 'runecloth': {
				let col = cloth(p, f, t, info, [30, 18, 50]);
				let g = null;
				const cell = Math.max(5, Math.round(info.D * 1.7));
				if (f !== 'up' && f !== 'down' && Math.min(info.w, info.h) >= cell && runeGlyph(info, cell, 41 + (c.seed || 0) + f.length, P !== 'runecol' && P !== 'collarlining')) { col = mix(VIOLET_HOT, VIOLET, 0.35); g = VIOLET; }
				else if (P === 'collarlining' && f === 'north') { col = mix(col, [90, 40, 140], 0.4); g = mul(VIOLET, 0.25); }
				return [col, 255, g];
			}
			case 'tablet': {
				let col = mix([50, 46, 62], [104, 98, 120], fbm(p[0] * 1.5, p[1] * 1.5, p[2] * 1.5 + c.seed, 3));
				if (info.ePx < 0.28) col = mix(col, [150, 140, 170], 0.4);
				let g = null;
				if ((f === 'north' || f === 'south') && info.ePx > 0.4) {
					const cell = info.w;
					if (runeGlyph({ i: info.i, j: Math.min(info.j, cell - 1), w: cell * 3, h: cell }, cell, 11 + c.seed * 13, false) || Math.abs(Math.hypot(s - 0.5, (t - 0.5) * 0.8) - 0.32) < 0.06) { col = VIOLET_HOT; g = VIOLET; }
				}
				return [bevel(col, info, 0.8, 0.95), 255, g];
			}
			case 'shadow': {
				let col = [12, 8, 18];
				let g = null;
				if (P === 'face' && f === 'north') {
					col = mix([8, 4, 14], [30, 18, 44], Math.max(0, 1 - Math.hypot(p[0] / 4, (p[1] - 52) / 4)));
					const ex = Math.abs(Math.abs(p[0]) - A.eyeX), ey = p[1] - A.eyeY + (Math.abs(p[0]) - A.eyeX) * 0.35;
					const e = Math.pow(ex / 1.05, 2) + Math.pow(ey / 0.45, 2);
					if (e < 1) { col = mix([255, 240, 255], VIOLET, Math.sqrt(e)); g = mix(VIOLET_HOT, VIOLET, Math.sqrt(e)); }
				}
				return [col, 255, g];
			}
			case 'silver': {
				let col = mix([130, 128, 140], [210, 210, 222], fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
				if (f === 'north' && Math.abs(p[1] - 51.1) < 0.18) col = [20, 16, 26];
				return [bevel(col, info), 255, null];
			}
			case 'skin': {
				let col = mix([110, 100, 120], [170, 160, 178], fbm(p[0] * 1.4, p[1] * 1.4, p[2] * 1.4, 3));
				if (P === 'fingertip' && t > 0.7) col = [40, 26, 50];
				return [bevel(vgrad(col, f, t, 0.15), info, 0.75, 0.92), 255, null];
			}
			case 'wood': {
				const grain = Math.sin(p[1] * 0.6 + fbm(p[0], p[1] * 0.2, p[2], 3) * 8);
				return [bevel(mix([16, 12, 20], [56, 44, 62], 0.5 + 0.4 * grain), info, 0.7, 0.9), 255, null];
			}
			case 'crystal': case 'crystal_hot': {
				const hot = c.mat === 'crystal_hot';
				const r = Math.max(Math.abs(s - 0.5), Math.abs(t - 0.5)) * 2;
				let col = mix(hot ? VIOLET_HOT : [214, 150, 255], VIOLET, Math.pow(r, 1.3) * (hot ? 0.6 : 1));
				if (info.ePx < 0.25) col = mix(col, [250, 236, 255], 0.6);
				if (Math.abs(s - t) < 0.07) col = mix(col, [250, 236, 255], 0.4);
				return [col, 255, hot ? VIOLET_HOT : VIOLET];
			}
			default: return [[255, 0, 255], 255, null];
		}
	};
}

// ================================================================ rest poses (shown on the sheets; also the clips' base)
const FIST = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 95], [`fingertips_${n}`]: [0, 0, s * 100] }; };
const OPEN = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 14 * k], [`fingertips_${n}`]: [0, 0, s * 18 * k] }; };

function chimeraPoses(M) {
	const stand = { head: [-6, 0, 0], jaw: [6, 0, 0], tail1: [0, 6, 0], tail3: [0, -8, 0], tail5: [0, 10, 0], tail_head: [-20, 30, 0], fingers_l: [0, 0, 18], fingertips_l: [0, 0, 20] };
	const action = {
		chest: [-14, 0, 0], waist: [-6, 0, 0], head: [-34, 0, 0], jaw: [44, 0, 0], arm_r: [-150, 10, 34], forearm_r: [-30, 0, 0], fingers_r: [-30, 0, 0],
		arm_l: [-40, -30, -60], forearm_l: [-50, 0, 0], fingers_l: [0, 0, 40], fingertips_l: [0, 0, 50],
		thigh_r: [-20, 0, 8], shin_r: [10, 0, 0], thigh_l: [10, 0, -8], shin_l: [8, 0, 0], foot_l: [-10, 0, 0],
		tail1: [-30, 20, 0], tail2: [-20, 10, 0], tail3: [-10, 0, 0], tail4: [10, -10, 0], tail5: [20, -30, 0], tail_head: [-30, 20, 0], tail_jaw: [40, 0, 0], mane: [-6, 0, 0],
	};
	return { stand, action };
}
function arsenalPoses(M) {
	const stand = { arm_r: [-4, 0, 6], forearm_r: [-14, 0, 0], arm_l: [-2, 0, -6], forearm_l: [-12, 0, 0], ...FIST('l'), head: [-2, 0, 0], thigh_r: [0, 0, 4], thigh_l: [0, 0, -4], foot_r: [0, 0, -4], foot_l: [0, 0, 4] };
	const action = {
		arm_r: [-86, 6, 4], forearm_r: [-4, 0, 0], arm_l: [-30, 0, -24], forearm_l: [-40, 0, 0], ...FIST('l'), chest: [4, 14, 0], waist: [0, 8, 0], head: [-2, -14, 0],
		cannon: [-8, 0, 0], thigh_r: [-24, 0, 6], shin_r: [20, 0, 0], foot_r: [4, 0, 0], thigh_l: [16, 0, -6], shin_l: [14, 0, 0], foot_l: [-24, 0, 0], barrels: [0, 40, 0],
	};
	return { stand, action };
}
function magePoses(M) {
	const stand = { $root: [0, 9, 0], arm_r: [-34, 0, 6], forearm_r: [-46, 0, 0], hand_r: [6, 0, 0], ...FIST('r'), arm_l: [-16, 0, -14], forearm_l: [-40, 0, 0], ...OPEN('l', 2), head: [4, 0, 0], skirt: [3, 0, 0] };
	L.aimBone(M, stand, 'staff', [0, 1, 0], [0.05, 1, 0.04]);
	const action = {
		$root: [0, 14, 0], arm_r: [-150, 0, 18], forearm_r: [-10, 0, 0], ...FIST('r'), arm_l: [-84, -30, -20], forearm_l: [-8, 0, 0], ...OPEN('l', 3), chest: [-12, 0, 0], waist: [-4, 0, 0], head: [-16, 0, 0],
		skirt: [-8, 0, 0], robe_f: [-20, 0, 0], robe_b: [14, 0, 0], cape: [24, 0, 0], collar: [-4, 0, 0], orbit: [0, 40, 0],
	};
	L.aimBone(M, action, 'staff', [0, 1, 0], [0.15, 1, -0.25]);
	return { stand, action };
}

// ================================================================ sheets
function planted(M, pose) {
	if (M.floats) return pose.$root || [0, 0, 0];
	const W = M.world(pose);
	const lo = M.bounds(W, c => c.tag === 'sole').lo[1];
	return [0, -lo, 0];
}
function fitFilter(M) {
	const skip = new Set();
	for (const b of M.bones) if ((M.ignoreFit || []).includes(b.name) || (b.parent && skip.has(b.parent))) skip.add(b.name);
	return c => !skip.has(c.bone);
}
function fitHeightPx(M) { const bb = M.bounds(M.world({}), fitFilter(M)); return bb.hi[1] - bb.lo[1]; }

function render(M, file, title, sub, poses) {
	const pitch = 6;
	const it = (pose, ex = {}) => Object.assign({ model: M, pose, root: planted(M, pose), hide: c => c.bone === 'spell_l' }, ex);
	const panels = [
		{ label: 'FRONT', items: [it(poses.stand)], cam: { yaw: 0, pitch } },
		{ label: 'THREE-QUARTER', items: [it(poses.stand)], cam: { yaw: 35, pitch } },
		{ label: 'SIDE', items: [it(poses.stand)], cam: { yaw: 90, pitch } },
		{ label: 'BACK', items: [it(poses.stand)], cam: { yaw: 200, pitch } },
		{ label: poses.actionLabel || 'ACTION', items: [it(poses.action, { hide: null })], cam: { yaw: 28, pitch }, ownScale: true },
	];
	L.sheet(path.join(OUT, file), title, sub, panels, { minW: 2200, panelH: 1100, maxW: 3600, bg: { top: [124, 128, 138], bottom: [70, 74, 84] } });
}

const VISUAL = { chimera: 2.15 * 1.95, arsenal: 1.95 * 1.95, omega_mage: 1.75 * 1.95 }; // SupervillainVariant.scale() x the zombie height
function prepare(id, build, shade, posesFn, paint = true) {
	const M = build();
	M.ground(c => M.floats || c.tag === 'sole');
	const fit = fitHeightPx(M);
	M.k = VISUAL[id] / fit; M.MODEL_SCALE = M.k * 16; M.heightBlocks = VISUAL[id];
	M.shadeFn = shade(M);
	if (paint) { M.layout(); M.paint(M.shadeFn); }
	M.poses = posesFn(M);
	console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH || '?'}, fit ${fit.toFixed(1)} px -> ${VISUAL[id].toFixed(2)} blocks`);
	return M;
}

const BUILD = { chimera: [buildChimera, chimeraShade, chimeraPoses], arsenal: [buildArsenal, arsenalShade, arsenalPoses], omega_mage: [buildMage, mageShade, magePoses] };
module.exports = { BUILD, prepare, fitHeightPx, fitFilter, planted, VISUAL, FIST, OPEN, CORE_RED, CORE_HOT, AMBER, AMBER_HOT, VIOLET, VIOLET_HOT, EYE_GOLD, OUT, SHOTS_DIR, REPO };

if (require.main === module && !GAME) {
	fs.mkdirSync(OUT, { recursive: true });
	const sub = { chimera: 'LION-RAM HYBRID, SERPENT TAIL, CHIMERA CORE', arsenal: 'POWER ARMOUR, SHOULDER CANNON, ARSENAL REACTOR', omega_mage: 'FLOATING SORCERER, OMEGA CRYSTAL STAFF' };
	const label = { chimera: 'ACTION: ROAR + CLAW', arsenal: 'ACTION: ROTARY GUN', omega_mage: 'ACTION: CRYSTAL RAISED' };
	for (const id of Object.keys(BUILD)) {
		if (!WANT(id === 'omega_mage' ? 'mage' : id)) continue;
		const M = prepare(id, ...BUILD[id]);
		M.poses.actionLabel = label[id];
		render(M, `concept_${id}.png`, 'THE ' + id.toUpperCase().replace('_', ' '), `${VISUAL[id].toFixed(1)} BLOCKS - ${sub[id]}`, M.poses);
	}
}
if (require.main === module && GAME) require('./supervillain_clips.js').gameExport();
