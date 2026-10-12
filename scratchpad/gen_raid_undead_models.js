// v0.16.1 Zombie Raid / Gravebound undead: custom GeckoLib models + animations for the raid's mobs, the Pillager Spy and
// the Cursed Zombie (replacing the vanilla zombie / skeleton / pillager meshes with re-skinned textures).
//   raid_zombie        Risen / Baby / Armoured (one rig; the iron armour bones are shown for the Armoured variant only)
//   acid_zombie        bloated, glowing acid sacs, unhinged dripping jaw            (+ acid_glob projectile model)
//   juggernaut_zombie  hulking, scrap-iron plates bolted on, a chain and shackle
//   sword_skeleton     cracked bones, rusted notched sword, round shield, helm, tattered tabard
//   cursed_zombie      grave-shroud, Gravebound purple runes carved in the skin, spectral wisps
//   pillager_spy       lean hooded scout, dark cloak, crossbow, spyglass
//   powered_zombie     the Powered Zombie Boss: a huge grave champion with power accent sets (shards / horns / coils /
//                      vines / orbit stones / wings / spines) toggled per power and tinted with the power's colour
//
// Built with the v0.16.x concept pipeline (scratchpad/lib_concept_models.js: boxes in model pixels, procedural painting
// at 3 texels per model pixel, the software renderer for sheets) and lib_geckogen_anim.js (clip builder, spring
// follow-through, strike checks). Timing comes from src/main/java/com/projecthero/mod/event/entity/RaidAnims.java.
//
// Usage (repo root):
//   node scratchpad/gen_raid_undead_models.js --concept [--only raid_zombie,...]   concept sheets only
//   node scratchpad/gen_raid_undead_models.js --game [--no-previews]               writes the game assets + anim previews
// Previews go to <main checkout>/scratchpad/shots_v0161/raid_models/.
//
// Conventions (Bedrock geo / GeckoLib): +Y up, the model faces -Z, the entity's RIGHT side is -X. Rotation X > 0 tips a
// bone's top forward; rotation Z > 0 swings a hanging limb toward -X (the right). Pose values are degrees ADDED to rest.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const A = require('./lib_geckogen_anim.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, seeded, vnorm, vadd, vsub, vsc, vlen } = L;
const { addPose: ap, mirrorPose: mirP, scalePose } = A;

const args = process.argv.slice(2);
const GAME = args.includes('--game');
const CONCEPT = args.includes('--concept');
const REPO = path.join(__dirname, '..');
const MAIN_ROOT = fs.existsSync(path.join(REPO, '..', '..', '..', 'scratchpad')) ? path.join(REPO, '..', '..', '..') : REPO;
const PREVIEW = path.join(MAIN_ROOT, 'scratchpad', 'shots_v0161', 'raid_models');
const oi = args.indexOf('--only');
const ONLY = oi >= 0 ? args[oi + 1].split(',') : null;
const WANT = n => !ONLY || ONLY.includes(n);
const SIDES = [[-1, 'r'], [1, 'l']];
const sxr = (s, a, b) => s > 0 ? [a, b] : [-b, -a];

// ================================================================ material helpers
const bevel = (col, info, k1 = 0.8, k2 = 0.93) => info.ePx < 0.22 ? mul(col, k1) : info.ePx < 0.5 ? mul(col, k2) : col;
const vgrad = (col, f, t, k = 0.2) => f === 'up' ? mul(col, 1.08) : f === 'down' ? mul(col, 0.6) : mul(col, 1.05 - k * t);
const lum = c => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
const sat = (c, k) => { const l = lum(c); return c.map(v => clamp(l + (v - l) * k)); };
function inSpheres(list, p, jitter = 0.35, seed = 0) {
	let best = -1e9, hit = null;
	for (const w of list || []) {
		const d = vlen(vsub(p, w.c)) / w.r;
		const n = (fbm(p[0] * 0.9 + seed, p[1] * 0.9, p[2] * 0.9, 2) - 0.5) * 2 * jitter;
		const k = 1 - (d + n);
		if (k > best) { best = k; hit = w; }
	}
	return { k: best, w: hit };
}

// rotting flesh: mottled grey-green, bruises, rot, dark veins, open wounds (gore, sometimes bone underneath)
function flesh(M, c, f, s, t, p, info) {
	const pal = M.pal;
	const n1 = fbm(p[0] * 0.32 + 3, p[1] * 0.32, p[2] * 0.32, 4);
	const n2 = fbm(p[0] * 1.7 + 50, p[1] * 1.7, p[2] * 1.7, 2);
	let col = mix(pal.dark, pal.base, 0.25 + 0.75 * n1);
	col = mul(col, 0.9 + 0.18 * n2);
	const br = fbm(p[0] * 0.42 + 40, p[1] * 0.42, p[2] * 0.42, 3);
	if (br > 0.58) col = mix(col, pal.bruise, Math.min(0.75, (br - 0.58) * 5));
	const rot = fbm(p[0] * 0.5 + 80, p[1] * 0.5, p[2] * 0.5, 3);
	if (rot > 0.62) col = mix(col, pal.rot, Math.min(0.8, (rot - 0.62) * 5));
	const vein = Math.abs(fbm(p[0] * 0.55 + 9, p[1] * 0.3, p[2] * 0.55, 3) - 0.5);
	if (vein < 0.018) col = mix(col, pal.vein, 0.65 * (1 - vein / 0.018));
	if (hash3(Math.floor(p[0] * 3), Math.floor(p[1] * 3), Math.floor(p[2] * 3) + 7) > 0.97) col = mul(col, 0.72); // pores / grime
	let g = null, ga = null;
	// open wounds
	const W = inSpheres(M.wounds, p, 0.45, 3);
	if (W.k > 0) {
		const k = W.k;
		if (k > 0.55 && W.w.bone) col = boneCol(p, f, t, 1.0);
		else if (k > 0.22) col = mix([120, 30, 30], [62, 14, 18], fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2) + (k - 0.22));
		else col = mix(col, [70, 30, 34], 0.55 + k * 1.5); // swollen dark rim
		if (W.w.glow && k > 0.25) { col = W.w.glow; g = W.w.glow; ga = 255; }
	}
	// stitches
	for (const st of M.stitches || []) {
		const d = vsub(p, st.a), ab = vsub(st.b, st.a); const L2 = ab[0] ** 2 + ab[1] ** 2 + ab[2] ** 2;
		const u = clamp((d[0] * ab[0] + d[1] * ab[1] + d[2] * ab[2]) / L2, 0, 1);
		const q = vsub(p, vadd(st.a, vsc(ab, u))); const dist = vlen(q);
		if (dist < 0.22) col = mix(col, [40, 22, 26], 0.8);
		else if (dist < 0.75 && Math.abs(((u * Math.sqrt(L2)) % 1.2) - 0.6) < 0.16) col = [24, 20, 18];
	}
	col = vgrad(col, f, t, 0.18);
	col = bevel(col, info, 0.84, 0.95);
	if (M.extraFlesh) { const r = M.extraFlesh(c, f, s, t, p, info, col); if (r) { col = r[0]; if (r[1]) { g = r[1]; ga = r[2] == null ? 255 : r[2]; } } }
	if (c.paint === 'head' || c.paint === 'jaw' || c.paint === 'face') { const r = face(M, c, f, s, t, p, col, info); col = r[0]; if (r[1]) { g = r[1]; ga = r[2] == null ? 255 : r[2]; } }
	if (c.paint === 'finger' && p[1] < c.from[1] + 0.55 && f !== 'up') col = [30, 26, 24];
	return [col, 255, g, ga];
}
function boneCol(p, f, t, k = 1) {
	const n = fbm(p[0] * 1.8, p[1] * 1.8 + 3, p[2] * 1.8, 3);
	let col = mix([136, 120, 92], [226, 214, 184], 0.25 + 0.75 * n);
	if (vnoise(p[0] * 2.2, p[1] * 2.2, p[2] * 2.2) > 0.84) col = mix(col, [100, 86, 64], 0.35);
	return mul(vgrad(col, f, t, 0.15), k);
}
function boneMat(M, c, f, s, t, p, info) {
	let col = boneCol(p, f, t);
	// hairline cracks
	const cr = Math.abs(fbm(p[0] * 0.9 + 21 + c.seed, p[1] * 0.7, p[2] * 0.9, 3) - 0.5);
	if (cr < 0.012) col = mix(col, [52, 40, 30], 0.85);
	else if (cr < 0.024) col = mul(col, 0.86);
	// grime in the creases / toward the ends
	col = mix(col, [86, 72, 52], 0.3 * smooth(0.55, 0.8, fbm(p[0] * 0.6 + 2, p[1] * 0.6, p[2] * 0.6, 2)));
	if (M.boneTint) col = mix(col, M.boneTint, 0.18);
	return [bevel(col, info, 0.74, 0.9), 255, null];
}
function clothMat(M, c, f, s, t, p, info, base) {
	const weave = ((info.i + info.j) & 1) ? 0.96 : 1.04;
	const n = fbm(p[0] * 0.8 + 21, p[1] * 0.45, p[2] * 0.8, 3);
	let col = mul(mix(mul(base, 0.66), mul(base, 1.18), n), weave);
	const fold = Math.sin((p[0] + p[2]) * 1.9 + fbm(p[0] * 0.4, p[1] * 0.2, p[2] * 0.4, 2) * 6);
	col = mul(col, 0.9 + 0.1 * fold);
	// filth: grave dirt toward the hem, blood spatters
	col = mix(col, [58, 46, 34], 0.45 * smooth(0.55, 0.85, fbm(p[0] * 0.5 + 7, p[1] * 0.5, p[2] * 0.5, 3)));
	if (!M.noBlood && fbm(p[0] * 1.3 + 60, p[1] * 1.3, p[2] * 1.3, 2) > 0.7) col = mix(col, [76, 22, 22], 0.55);
	// tears: holes (cut out), with frayed dark edges
	let a = 255;
	if (c.tag !== 'solid') {
		const H = inSpheres(M.holes, p, 0.5, 11);
		if (H.k > 0.08) a = 0; else if (H.k > -0.12) col = mix(col, [40, 32, 26], 0.55);
		// ragged hems: the low edge of a hem cube eaten away
		if (c.tag === 'hem' && f !== 'up' && f !== 'down') {
			const rag = 0.62 + 0.38 * fbm(p[0] * 1.6 + p[2] * 1.6, 3.3, c.seed, 2) + 0.12 * Math.sin((p[0] + p[2]) * 3.1);
			if (t > rag) a = 0; else if (t > rag - 0.08) col = mul(col, 0.7);
		}
		if (c.tag === 'hem' && f === 'down') a = 0;
	}
	col = vgrad(col, f, t, 0.12);
	return [bevel(col, info, 0.75, 0.9), a, null];
}
function leatherMat(M, c, f, s, t, p, info, base = [74, 52, 36]) {
	const n = fbm(p[0] * 1.5 + 11, p[1] * 1.5, p[2] * 1.5, 3);
	let col = mix(mul(base, 0.6), mul(base, 1.25), n);
	if (vnoise(p[0] * 6, p[1] * 1.2, p[2] * 6) > 0.8) col = mul(col, 0.8);
	if (info.ePx > 0.25 && info.ePx < 0.45 && ((info.i + info.j) % 4 === 0)) col = mix(col, [130, 104, 78], 0.3);
	return [bevel(vgrad(col, f, t, 0.15), info, 0.7, 0.88), 255, null];
}
function ironMat(M, c, f, s, t, p, info, base = [92, 96, 104], rustK = 1) {
	const n = fbm(p[0] * 2.0 + 5, p[1] * 2.0, p[2] * 2.0, 3);
	let col = mix(mul(base, 0.7), mul(base, 1.2), n);
	const rust = fbm(p[0] * 0.7 + 90, p[1] * 0.7, p[2] * 0.7, 3);
	col = mix(col, mix([110, 54, 26], [160, 84, 40], fbm(p[0] * 3, p[1] * 3, p[2] * 3, 2)), clamp((rust - 0.42) * 2.6 * rustK, 0, 0.92));
	if (fbm(p[0] * 2.5 + 30, p[1] * 2.5, p[2] * 2.5, 2) > 0.74) col = mix(col, [60, 34, 22], 0.6); // pitting
	if (info.ePx < 0.22) col = mix(col, [176, 170, 160], 0.4); // worn edge
	else if (info.ePx < 0.45) col = mul(col, 0.8);
	// rivets / bolts near the corners of big faces
	if (info.fw > 3.6 && info.fh > 3.6 && !M.noRivets) {
		const sx = Math.min(info.i + 0.5, info.w - info.i - 0.5) / info.D, sy = Math.min(info.j + 0.5, info.h - info.j - 0.5) / info.D;
		if (Math.abs(sx - 0.7) < 0.24 && Math.abs(sy - 0.7) < 0.24) col = [150, 142, 130];
		else if (Math.abs(sx - 0.7) < 0.36 && Math.abs(sy - 0.7) < 0.36) col = mul(col, 0.7);
	}
	// dents
	if (vnoise(p[0] * 1.2 + 4, p[1] * 1.2, p[2] * 1.2) > 0.8) col = mul(col, 0.82);
	return [vgrad(col, f, t, 0.2), 255, null];
}
function woodMat(M, c, f, s, t, p, info, base = [92, 66, 42]) {
	const grain = Math.sin((p[1] * 0.4 + p[0] * 2.6 + p[2] * 0.3) * 3 + fbm(p[0], p[1] * 0.3, p[2], 3) * 7);
	let col = mix(mul(base, 0.6), mul(base, 1.15), 0.5 + 0.4 * grain);
	col = mix(col, [44, 50, 36], 0.4 * smooth(0.6, 0.8, fbm(p[0] * 0.8 + 8, p[1] * 0.8, p[2] * 0.8, 2))); // moss / rot
	return [bevel(col, info, 0.7, 0.9), 255, null];
}
function hairMat(M, c, f, s, t, p, info, base = [30, 28, 26], hi = [74, 70, 60]) {
	const strand = fbm(p[0] * 6 + 1, p[1] * 0.6, p[2] * 6, 2);
	let col = mix(base, hi, Math.pow(strand, 2.2) * 1.4);
	let a = 255;
	if (c.tag === 'hem' && f !== 'up' && f !== 'down' && t > 0.55 + 0.45 * fbm(p[0] * 3 + p[2] * 3, 1, c.seed, 2)) a = 0;
	return [bevel(vgrad(col, f, t, 0.15), info, 0.7, 0.9), a, null];
}

// ---------------------------------------------------------------- the face
function face(M, c, f, s, t, p, col, info) {
	const F = M.A;
	if (f !== 'north') return [col, null];
	const x = p[0], y = p[1], ax = Math.abs(x);
	let g = null, ga = null;
	if (c.paint === 'head') {
		// sunken sockets, a pinpoint of light deep in each
		const ex = (ax - F.eyeX) / F.eyeW, ey = (y - F.eyeY) / F.eyeH;
		const e = ex * ex + ey * ey;
		if (e < 2.4) col = mix(col, [20, 16, 18], clamp(1.2 - e * 0.45, 0, 0.92));
		if (e < 1.6 && e > 0.9 && ey < 0) col = mix(col, [70, 50, 52], 0.4); // lower lid sag
		const px = (ax - F.eyeX - F.pupilDX) / (F.eyeW * 0.42), py = (y - F.eyeY) / (F.eyeH * 0.5);
		const pe = px * px + py * py;
		if (pe < 1) { col = mix(F.eyeHot, F.eyeCol, Math.sqrt(pe)); g = col; ga = 255; }
		else if (pe < 2.2 && M.eyeHaze) { col = mix(col, F.eyeCol, 0.35 * (2.2 - pe)); }
		// brow shadow, nose cavity (rotted away)
		if (Math.abs(y - (F.eyeY + F.eyeH * 1.5)) < 0.18 && ax < F.eyeX + F.eyeW * 1.2) col = mul(col, 0.6);
		if (F.noseHole && ax < 0.55 && y < F.eyeY - F.eyeH * 1.2 && y > F.eyeY - F.eyeH * 1.2 - 1.3) col = mix([18, 12, 12], col, ax / 0.55 * 0.6);
		if (M.cheekTear && Math.abs(x - M.cheekTear.x) < M.cheekTear.w && y < F.eyeY - 1.0 && y > F.mouthY - 0.3) {
			const row = Math.floor((x - M.cheekTear.x + 10) * 2.2);
			col = (y < F.mouthY + 0.9 && y > F.mouthY - 0.2) ? (row % 2 ? [214, 200, 168] : [150, 130, 100]) : [96, 26, 30];
		}
	}
	if (c.paint === 'jaw') {
		const top = c.to[1];
		// a row of teeth along the top edge, dark gums, black gaps
		if (y > top - 0.95 && ax < F.mouthW) {
			const k = Math.floor((x + 10) * 2.4);
			const missing = hash3(k, 3, M.seed || 1) > 0.78;
			col = missing ? [22, 12, 12] : (k % 2 ? [208, 196, 160] : [176, 160, 124]);
			if (y > top - 0.3 && !missing) col = mix(col, [110, 50, 50], 0.4);
		} else if (y > top - 1.4 && ax < F.mouthW + 0.3) col = mix(col, [60, 18, 20], 0.7);
		if (M.jawGlow && y > top - 1.6 && ax < F.mouthW + 0.2 && y < top - 0.9) { col = M.jawGlow; g = M.jawGlow; ga = 255; }
	}
	return [col, g, ga];
}

// ================================================================ the shared humanoid rig
// Bones: root > hips > thigh > shin > foot, hips > waist > chest > neck (code-driven) > head > jaw,
// chest > arm > forearm > hand > fingers (+ item locator on the right hand). Units add gear bones.
const BASE = {
	footH: 1.4, footLen: 4.6, footW: 2.7,
	shinLen: 6.0, shinW: 2.4, shinD: 2.5,
	thighLen: 6.2, thighW: 3.0, thighD: 3.1, legX: 1.85,
	pelvisH: 3.0, pelvisW: 7.0, pelvisD: 3.6,
	waistH: 3.2, waistW: 5.6, waistD: 3.2,
	chestH: 7.4, chestW: 8.6, ribW: 6.6, chestD: 4.2,
	neckH: 1.8, neckW: 2.4,
	headW: 6.0, headH: 6.6, headD: 6.0,
	armW: 2.3, armD: 2.4, upperLen: 6.0, foreLen: 5.8, foreW: 2.2, handLen: 2.3, fingerLen: 2.6,
	armOut: 4, fingers: 3, bulk: 1,
};
function rig(M, P, opt = {}) {
	const A = M.A, b = P.bulk;
	const ankleY = P.footH, kneeY = ankleY + P.shinLen, hipY = kneeY + P.thighLen;
	const pelvisTop = hipY + P.pelvisH - 1.5;
	const chestBot = pelvisTop + P.waistH, chestTop = chestBot + P.chestH;
	const hb = chestTop + P.neckH - 0.6;
	const shX = P.chestW / 2 * b + P.armW / 2 - 0.6, shY = chestTop - 1.2;
	const elbowY = shY - P.upperLen, wristY = elbowY - P.foreLen, knuckY = wristY - P.handLen;
	const legX = P.legX;
	const hw = P.headW / 2, hd = P.headD, faceZ = -hd / 2;
	Object.assign(A, { ankleY, kneeY, hipY, pelvisTop, chestBot, chestTop, hb, shX, shY, elbowY, wristY, knuckY, legX, faceZ, hw, hd,
		eyeY: hb + P.headH * 0.56, eyeX: hw * 0.42, eyeW: hw * 0.24, eyeH: 0.62, pupilDX: 0, mouthY: hb + 1.2, mouthW: hw * 0.55,
		headTop: hb + P.headH, eyeCol: [255, 226, 120], eyeHot: [255, 252, 220] });
	const skinMat = opt.skin || 'flesh';
	const root = M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, hipY, 0]);
	if (!opt.noPelvis) M.box(hips, -P.pelvisW / 2 * b, hipY - 1.6, -P.pelvisD / 2 * b, P.pelvisW / 2 * b, pelvisTop + 0.4, P.pelvisD / 2 * b, skinMat, { paint: 'pelvis' });
	for (const [s, n] of SIDES) {
		const lx = s * legX;
		const thigh = M.bone(`thigh_${n}`, 'hips', [lx, hipY, 0], [0, 0, -s * 1.5]);
		const tw = P.thighW / 2 * b, td = P.thighD / 2 * b;
		const shin = M.bone(`shin_${n}`, `thigh_${n}`, [lx, kneeY, 0]);
		const foot = M.bone(`foot_${n}`, `shin_${n}`, [lx, ankleY, 0]);
		const sw = P.shinW / 2 * b, sd = P.shinD / 2 * b, fw = P.footW / 2 * b;
		if (!opt.noLegs) {
			M.box(thigh, lx - tw, kneeY - 0.3, -td, lx + tw, hipY + 0.6, td, skinMat, { paint: 'thigh' });
			M.box(shin, lx - sw, ankleY - 0.2, -sd, lx + sw, kneeY + 0.3, sd, skinMat, { paint: 'shin' });
			M.box(shin, lx - sw * 0.8, kneeY - 1.1, -sd - 0.35, lx + sw * 0.8, kneeY + 0.7, -sd + 0.3, skinMat, { paint: 'knee' });
			M.box(foot, lx - fw, 0, -P.footLen * 0.68, lx + fw, ankleY, P.footLen * 0.32, skinMat, { paint: 'foot', tag: 'sole' });
		}
	}
	const waist = M.bone('waist', 'hips', [0, pelvisTop, 0]);
	const chest = M.bone('chest', 'waist', [0, chestBot, 0]);
	if (!opt.noTorso) {
		M.box(waist, -P.waistW / 2 * b, pelvisTop - 0.5, -P.waistD / 2 * b, P.waistW / 2 * b, chestBot + 0.5, P.waistD / 2 * b, skinMat, { paint: 'belly' });
		const cw = P.chestW / 2 * b, rw = P.ribW / 2 * b, cd = P.chestD / 2 * b;
		M.box(chest, -rw, chestBot - 0.3, -cd * 0.9, rw, chestBot + P.chestH * 0.5, cd * 0.9, skinMat, { paint: 'ribs' });
		M.box(chest, -cw, chestBot + P.chestH * 0.42, -cd, cw, chestTop, cd, skinMat, { paint: 'chest' });
	}
	const neck = M.bone('neck', 'chest', [0, chestTop, 0]);
	if (!opt.noNeck) M.box(neck, -P.neckW / 2, chestTop - 0.8, -P.neckW / 2 - 0.2, P.neckW / 2, hb + 1.2, P.neckW / 2 + 0.3, skinMat, { paint: 'neck' });
	const head = M.bone('head', 'neck', [0, chestTop + 0.8, 0]);
	const jaw = M.bone('jaw', 'head', [0, hb + 1.6, 0.6]);
	if (!opt.noHead) {
		M.box(head, -hw, hb + 1.5, -hd / 2, hw, hb + P.headH, hd / 2, skinMat, { paint: 'head' });
		M.box(jaw, -hw + 0.5, hb - 0.4, faceZ + 0.15, hw - 0.5, hb + 1.65, hd / 2 - 1.0, skinMat, { paint: 'jaw' });
	}
	for (const [s, n] of SIDES) {
		const sx = s * shX;
		const ua = M.bone(`arm_${n}`, 'chest', [sx, shY, 0], [0, 0, -s * P.armOut]);
		const fa = M.bone(`forearm_${n}`, `arm_${n}`, [sx, elbowY, 0], [-6, 0, 0]);
		const hand = M.bone(`hand_${n}`, `forearm_${n}`, [sx, wristY, 0]);
		const fing = M.bone(`fingers_${n}`, `hand_${n}`, [sx, knuckY + 0.2, 0]);
		const aw = P.armW / 2 * b, ad = P.armD / 2 * b, fw2 = P.foreW / 2 * b;
		if (!opt.noArms) {
			M.box(ua, sx - aw, elbowY - 0.4, -ad, sx + aw, shY + 1.0, ad, skinMat, { paint: 'arm' });
			M.box(fa, sx - fw2, wristY - 0.2, -ad * 0.92, sx + fw2, elbowY + 0.5, ad * 0.92, skinMat, { paint: 'fore' });
			M.box(hand, sx - 0.8 * b, knuckY, -1.3 * b, sx + 0.8 * b, wristY + 0.3, 1.3 * b, skinMat, { paint: 'hand' });
			// long bony fingers with black nails, and a thumb
			const zs = P.fingers === 4 ? [-0.95, -0.32, 0.32, 0.95] : [-0.85, 0, 0.85];
			zs.forEach((z, i) => M.box(fing, sx - 0.42 * b, knuckY - P.fingerLen * (i === 1 ? 1.05 : 0.92), z * b - 0.33, sx + 0.42 * b, knuckY + 0.3, z * b + 0.33, skinMat, { paint: 'finger' }));
			M.stick(hand, [sx - s * 0.4, wristY - 0.6, -1.0 * b], [-s * 0.3, -0.8, -0.5], P.fingerLen * 0.8, 0.7 * b, skinMat, { paint: 'finger' });
		}
		if (n === 'r') M.bone('item_r', `hand_${n}`, [sx, knuckY + 0.6, 0]);
	}
	if (opt.anatomy) anatomy(M, P, skinMat);
	return M;
}
// Bony landmarks that break up the box silhouette: a sculpted skull (brow ridge, cheekbones, occipital bulge, nose
// stub, chin), clavicles, sternum, scapulae and spine knuckles, iliac crests, elbow / wrist / knee knobs.
function anatomy(M, P, mat) {
	const A = M.A, b = P.bulk, hw = A.hw, hd = A.hd, fz = A.faceZ, hb = A.hb;
	const k = P.anatomyK || 1;
	// skull
	M.box('head', -hw + 0.25, A.eyeY + A.eyeH + 0.15, fz - 0.45, hw - 0.25, A.eyeY + A.eyeH + 0.95, fz + 0.4, mat, { paint: 'brow' });
	for (const [s] of SIDES) M.box('head', ...[sxr(s, hw - 1.7, hw + 0.15)[0], A.eyeY - A.eyeH - 1.0, fz - 0.35, sxr(s, hw - 1.7, hw + 0.15)[1], A.eyeY - A.eyeH + 0.05, fz + 1.6], mat, { paint: 'cheek' });
	M.box('head', -hw + 0.6, hb + 2.6, hd / 2 - 0.5, hw - 0.6, A.headTop - 0.8, hd / 2 + 0.55, mat, { paint: 'skull' });
	M.box('head', -hw + 0.7, A.headTop - 0.2, -hd / 2 + 0.8, hw - 0.7, A.headTop + 0.4, hd / 2 - 0.4, mat, { paint: 'skull' });
	M.box('head', -0.45, A.eyeY - A.eyeH - 1.1, fz - 0.55, 0.45, A.eyeY + 0.2, fz + 0.2, mat, { paint: 'nosebridge' });
	M.box('jaw', -1.1, hb - 0.75, fz - 0.05, 1.1, hb + 0.6, fz + 1.4, mat, { paint: 'jaw' });
	// torso landmarks
	const cd = P.chestD / 2 * b, cw = P.chestW / 2 * b;
	if (!P.noTorsoAnatomy) {
		for (const [s] of SIDES) {
			M.stick('chest', [s * 0.4, A.chestTop - 0.55, -cd - 0.05], [s, 0.18, 0.15], cw * 0.85, [0.55, 0.5], mat, { paint: 'clavicle' });
			M.box('chest', ...[sxr(s, 0.9, cw * 0.78)[0], A.chestTop - 3.8, cd - 0.1, sxr(s, 0.9, cw * 0.78)[1], A.chestTop - 0.6, cd + 0.45], mat, { paint: 'scapula', rot: [0, 0, s * 8], pivot: [s * 2, A.chestTop - 2, cd] });
			M.box('hips', ...[sxr(s, P.pelvisW / 2 * b - 1.3, P.pelvisW / 2 * b + 0.25)[0], A.pelvisTop - 1.1, -P.pelvisD / 2 * b - 0.3, sxr(s, P.pelvisW / 2 * b - 1.3, P.pelvisW / 2 * b + 0.25)[1], A.pelvisTop + 0.5, -P.pelvisD / 2 * b + 0.8], mat, { paint: 'iliac' });
		}
		M.box('chest', -0.4, A.chestBot + 1.6, -cd - 0.3, 0.4, A.chestTop - 1.0, -cd + 0.2, mat, { paint: 'sternum' });
		for (let i = 0; i < 4; i++) M.cbox(i < 2 ? 'chest' : 'waist', [0, (i < 2 ? A.chestTop - 1.5 - i * 2.4 : A.chestBot - 0.4 - (i - 2) * 1.6), (i < 2 ? cd : P.waistD / 2 * b) + 0.15], [0.9, 0.8, 0.6], mat, { paint: 'spine' });
	}
	// joints
	for (const [s, n] of SIDES) {
		const sx = s * A.shX;
		if (!P.noArmAnatomy) {
			M.box(`forearm_${n}`, sx - 0.7, A.elbowY - 0.6, P.armD / 2 * b - 0.4, sx + 0.7, A.elbowY + 0.9, P.armD / 2 * b + 0.5, mat, { paint: 'elbow' });
			M.box(`forearm_${n}`, sx - P.foreW / 2 * b - 0.25, A.wristY + 0.1, -0.6, sx + P.foreW / 2 * b + 0.25, A.wristY + 1.0, 0.6, mat, { paint: 'wrist' });
			M.box(`arm_${n}`, sx - P.armW / 2 * b - 0.3, A.shY - 2.4, -P.armD / 2 * b - 0.2, sx + P.armW / 2 * b + 0.3, A.shY + 1.25, P.armD / 2 * b + 0.2, mat, { paint: 'delt' });
		}
	}
	return M;
}
const CLAW = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 38 * k] }; };
const FIST = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 95] }; };
const OPEN = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 8 * k] }; };

// ================================================================ the shared material switch
function shadeFor(M) {
	return (c, f, s, t, p, info) => {
		if (M.customMat) { const r = M.customMat(c, f, s, t, p, info); if (r) return r; }
		switch (c.mat) {
			case 'flesh': return flesh(M, c, f, s, t, p, info);
			case 'bone': return boneMat(M, c, f, s, t, p, info);
			case 'gore': { const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2); return [bevel(mix([132, 34, 32], [60, 12, 16], n), info), 255, null]; }
			case 'cloth': return clothMat(M, c, f, s, t, p, info, M.clothCol || [92, 100, 110]);
			case 'cloth2': return clothMat(M, c, f, s, t, p, info, M.cloth2Col || [86, 66, 48]);
			case 'cloth3': return clothMat(M, c, f, s, t, p, info, M.cloth3Col || [60, 50, 70]);
			case 'leather': return leatherMat(M, c, f, s, t, p, info, M.leatherCol);
			case 'leather_d': return leatherMat(M, c, f, s, t, p, info, [44, 34, 30]);
			case 'iron': return ironMat(M, c, f, s, t, p, info, M.ironCol);
			case 'iron_clean': return ironMat(M, c, f, s, t, p, info, [110, 114, 122], 0.35);
			case 'iron_d': return ironMat(M, c, f, s, t, p, info, [58, 58, 62]);
			case 'wood': return woodMat(M, c, f, s, t, p, info, M.woodCol);
			case 'wood_d': return woodMat(M, c, f, s, t, p, info, [50, 36, 28]);
			case 'hair': return hairMat(M, c, f, s, t, p, info, M.hairCol, M.hairHi);
			case 'chain': { // riveted mail: rings of 3x3 texels, offset every row, dark holes
				const row = Math.floor(info.j / 2), cx = (info.i + (row & 1 ? 1.5 : 0)) % 3, cy = info.j % 2;
				let col = mix([70, 70, 74], [150, 146, 138], fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
				col = mix(col, [120, 62, 30], clamp((fbm(p[0] * 0.8 + 5, p[1] * 0.8, p[2] * 0.8, 2) - 0.45) * 2.5, 0, 0.8));
				if (cx > 0.9 && cx < 2.1 && cy === 1) col = [26, 22, 20];
				else if (cy === 0) col = mul(col, 1.15);
				let a = 255;
				if (c.tag === 'hem' && f !== 'up' && f !== 'down' && t > 0.8 + 0.2 * hash3(info.i, 1, c.seed) ) a = 0;
				return [vgrad(col, f, t, 0.2), a, null];
			}
			case 'eye': return [M.A.eyeCol, 255, M.A.eyeCol, 255];
			case 'sil': return [[48, 52, 60], 255, null];
			default: return [[255, 0, 255], 255, null];
		}
	};
}

// ================================================================ preview sheets (concept mode)
function planted(M, pose, extra = 0) {
	const W = M.world(A.normPose(pose));
	const lo = M.bounds(W, c => /^(foot|toe)/.test(c.bone) || c.tag === 'sole').lo[1];
	return [0, -lo + extra, 0];
}
function conceptSheet(M, id, title, sub, stand, actions, opts = {}) {
	const pitch = opts.pitch == null ? 6 : opts.pitch;
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: pose.$root || planted(M, pose), hide: opts.hide || null }, extra);
	const panels = [
		{ label: 'FRONT', items: [it(stand)], cam: { yaw: 0, pitch } },
		{ label: 'THREE-QUARTER', items: [it(stand)], cam: { yaw: 35, pitch } },
		{ label: 'SIDE', items: [it(stand)], cam: { yaw: 90, pitch } },
		{ label: 'BACK', items: [it(stand)], cam: { yaw: 200, pitch } },
	];
	for (const a of actions) panels.push({ label: a.label, items: [it(a.pose, { hide: a.hide || opts.hide || null })], cam: { yaw: a.yaw == null ? 30 : a.yaw, pitch: a.pitch == null ? pitch : a.pitch } });
	fs.mkdirSync(path.join(PREVIEW, 'concepts'), { recursive: true });
	L.sheet(path.join(PREVIEW, 'concepts', `${id}.png`), title, sub, panels, Object.assign({ minW: 2400, panelH: 1000, maxW: 4200, ss: 2 }, opts.sheet || {}));
}

// a rounded lump: three crossed boxes (full size along one axis each, 70 % on the other two)
function blob(M, bone, c, sz, mat, opts = {}) {
	const k = opts.k || 0.7;
	return [[1, k, k], [k, 1, k], [k, k, 1]].map(m => M.cbox(bone, c, [sz[0] * m[0], sz[1] * m[1], sz[2] * m[2]], mat, Object.assign({}, opts)));
}
module.exports = { blob, flesh, boneMat, clothMat, ironMat, leatherMat, woodMat, hairMat, face, rig, shadeFor, BASE, CLAW, FIST, OPEN, SIDES, sxr, bevel, vgrad, sat, lum, inSpheres, planted, conceptSheet, PREVIEW, REPO, args, GAME, CONCEPT, WANT };
module.exports.ONLY = ONLY;
if (require.main === module) {
	if (GAME) require('./raid_undead_export.js').run();
	else require('./raid_undead_units.js').main();
}
