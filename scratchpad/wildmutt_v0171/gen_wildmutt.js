// v0.17.1: WILDMUTT -- the Ben 10 Vulpimancer as a GeckoLib quadruped.
// Rework 3 (user: "Make Wildmutts model look better, look at the reference material I sent to you, the previous model
// looked a bit better"): rework 2's reference shape (blocky, no tail, a boxy eyeless head carried low, long angled front
// legs, dog-like hind legs) with rework 1's bulk and finish -- thick legs and a deep chest, richly shaded mottled fur with
// strokes and speckle, bristling tapered quill tufts. All mottled orange fur. A solid torso, the hump at the shoulders
// (~1.5 blocks up) sloping a little down to the hips, NO tail. The head is as tall and wide as the torso and longer than it
// is tall, joined to the chest by a thick neck that fills the whole join (no gap or step from the side); no eyes, a black
// mouth band round the front and sides a third of the way up (the upper half laps over the lower, so a barely open jaw
// never shows a red line), chunky cream teeth of uneven heights standing over it, gill slits behind it, small spiky tufts
// down the head and the back. Thick front legs (the upper arm angled down and back, the forearm down and forward), thick
// bent hind legs, flat paws with a raised wrist step and three dark square claws each. The Omnitrix is a chunky navy cuff
// round the LEFT upper front leg near the shoulder with a pale grey-blue rim step and a dark square plate on the outside
// (the renderer draws the white symbol on it).
//
// Model pixels are real 1/16-block pixels, drawn 1:1 (WildmuttRenderer.MODEL_SCALE). Bedrock / GeckoLib conventions: +Y up,
// the model faces -Z, the entity's RIGHT side is -X.
//
// Writes:
//   src/main/resources/assets/projecthero/geo/omnitrix/wildmutt.geo.json
//   src/main/resources/assets/projecthero/textures/entity/omnitrix/wildmutt.png
//   src/main/resources/assets/projecthero/animations/omnitrix/wildmutt.animation.json     (anims.js)
//   preview sheets (preview.js) into the folder given as --sheets <dir> (not committed; the in-game shots are)
// Usage: node scratchpad/wildmutt_v0171/gen_wildmutt.js [--sheets <dir>]
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { Model, mix, mul, fbm, clamp, hash3, smooth } = L;

const ROOT = path.join(__dirname, '..', '..');
const A = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero');
const mk = d => (fs.mkdirSync(d, { recursive: true }), d);
const writeJson = (f, o) => { mk(path.dirname(f)); fs.writeFileSync(f, JSON.stringify(o, null, '\t') + '\n'); };
const writePng = (f, w, h, px) => { mk(path.dirname(f)); fs.writeFileSync(f, L.encodePng(w, h, px)); };

const M = new Model('wildmutt', { TW: 512, D: 3 });
const S = [-1, 1];
const nm = s => (s < 0 ? 'r' : 'l');
const DEG = Math.PI / 180;
// a box whose x range is given as |x| (outer side positive), mirrored by s
function sbox(b, s, xa, xb, y0, y1, z0, z1, mat, opts) {
	return M.box(b, s * xa, y0, z0, s * xb, y1, z1, mat, opts);
}
// a limb segment hanging from `piv` ([x, y, z]), `len` long below it (+ `over` above, the overlap into the joint), tipped
// `ang` degrees about X (+ swings the lower end back, - forward); half-width hw (x) and half-depth hd (z)
function limb(b, piv, len, over, ang, hw, hd, mat, seed) {
	return M.box(b, piv[0] - hw, piv[1] - len, piv[2] - hd, piv[0] + hw, piv[1] + over, piv[2] + hd, mat,
		{ seed, rot: [ang, 0, 0], pivot: piv.slice() });
}
// where a limb segment's lower end lands
const end = (piv, len, ang) => [piv[0], piv[1] - len * Math.cos(ang * DEG), piv[2] + len * Math.sin(ang * DEG)];
// v0.17.1 rework 3: rework 1's tapered quill -- a wide short base, a narrower middle and a thin light tip along the same
// direction, thinner across x than along the body (a blade of fur)
function spike(b, from, dir, len, w, seed, flat = 0.6) {
	const d = L.vnorm(dir);
	const at = k => [from[0] + d[0] * len * k, from[1] + d[1] * len * k, from[2] + d[2] * len * k];
	M.stick(b, from, d, len * 0.45, [w * flat, w], 'tuft', { seed });
	M.stick(b, at(0.38), d, len * 0.38, [w * flat * 0.72, w * 0.68], 'tuft', { seed: seed + 1 });
	M.stick(b, at(0.7), d, len * 0.3, [w * flat * 0.45, w * 0.38], 'tuftTip', { seed: seed + 2 });
}

// ================================================================ the rig
M.bone('root', null, [0, 0, 0]);
M.bone('spin', 'root', [0, 13, 0]);          // the Quill Roll turns the whole ball about here
M.bone('hurt', 'spin', [0, 15, 2]);          // the flinch layer (its own controller; nothing else keys it)
const hips = M.bone('hips', 'hurt', [0, 17, 3]);
const chest = M.bone('chest', 'hips', [0, 17, 1]);
const neck = M.bone('neck', 'chest', [0, 18.5, -9.5]);
M.bone('look', 'neck', [0, 18.5, -13]);      // where the player looks (set in code, never keyed)
const head = M.bone('head', 'look', [0, 18.5, -13]);
const jaw = M.bone('jaw', 'head', [0, 16.6, -13.5]);
const quillsF = M.bone('quills_f', 'chest', [0, 24.4, -4]);
const quillsB = M.bone('quills_b', 'hips', [0, 22, 7]);

// ---- the hips: the waist and a squared rump, the back sloping a little down to it (no tail)
M.box(hips, -5.4, 13.6, 0.5, 5.4, 21.8, 9.0, 'fur', { seed: 1 });                  // waist
M.box(hips, -5.8, 12.4, 8.0, 5.8, 20.8, 14.8, 'fur', { seed: 2 });                 // rump
M.box(hips, -5.0, 20.6, -1.0, 5.0, 23.2, 14.0, 'fur', { seed: 3, rot: [-8, 0, 0], pivot: [0, 23.2, -1.0] }); // the sloping back
M.box(hips, -4.6, 12.8, 1.2, 4.6, 13.8, 8.4, 'belly', { seed: 4 });                // the tucked belly
// ---- the chest: deep and solid, the hump over the shoulders
M.box(chest, -6.1, 11.2, -11.2, 6.1, 22.8, 3.0, 'fur', { seed: 10 });
M.box(chest, -5.3, 22.6, -10.0, 5.3, 24.4, 1.4, 'fur', { seed: 11 });             // the hump
M.box(chest, -3.8, 24.2, -8.4, 3.8, 24.8, -1.2, 'fur', { seed: 12 });             // its crown
M.box(chest, -5.4, 12.0, -12.2, 5.4, 20.6, -10.8, 'fur', { seed: 13 });           // the brisket
M.box(chest, -5.0, 10.4, -9.8, 5.0, 11.4, 1.2, 'belly', { seed: 14 });            // the chest underside
// ---- the neck: thick, filling the whole join from the head's back to the chest (rework 2's head looked detached)
M.box(neck, -5.2, 14.2, -16.0, 5.2, 22.9, -8.0, 'neck', { seed: 20 });
M.box(neck, -4.6, 21.8, -14.5, 4.6, 24.0, -6.5, 'neck', { seed: 21, rot: [-14, 0, 0], pivot: [0, 22.9, -6.5] }); // the nape
// ---- the head: a big box as tall and wide as the torso, no eyes; the jaw hinged at the back
M.box(head, -5.6, 16.6, -25.2, 5.6, 23.2, -12.4, 'skull', { seed: 30 });
// the black band: the upper half (on the skull) laps outside and below the lower half (on the jaw), so the seam is black
M.box(head, -5.82, 16.1, -25.42, 5.82, 17.8, -15.0, 'band', { tag: 'upper' });
M.box(jaw, -5.4, 13.2, -24.9, 5.4, 16.6, -12.8, 'fur', { seed: 40 });
M.box(jaw, -5.66, 15.3, -25.16, 5.66, 16.9, -15.2, 'band', { tag: 'lower' });
// the teeth: a chunky cream row of uneven heights standing over the band at the front, two more round each corner
const TEETH = [19.4, 20.4, 19.7, 20.7, 19.5, 20.5, 19.8];
for (let i = 0; i < TEETH.length; i++) {
	const x0 = -5.25 + i * 1.53;
	M.box(jaw, x0, 17.2, -25.95, x0 + 1.3, TEETH[i], -25.0, 'tooth', { seed: 32 + i });
}
for (const s of S) {
	sbox(jaw, s, 5.6, 6.35, 17.2, 19.4, -24.6, -23.2, 'tooth', { seed: 38 });
	sbox(jaw, s, 5.6, 6.25, 17.2, 18.8, -22.4, -21.2, 'tooth', { seed: 39 });
}
// ---- the quill tufts: small spikes down the top of the head, a ragged ridge over the hump, the spine to the hips
for (const [x, z, len] of [[0, -23.6, 2.6], [0, -21.0, 3.2], [0, -18.4, 3.4], [0, -15.6, 3.0], [-2.4, -20.0, 2.2], [2.4, -17.2, 2.2], [-2.4, -14.6, 2.0]]) {
	spike(head, [x, 22.9, z], [x * 0.14, 0.78, 0.62], len, 1.9, 50 + Math.round(-z * 2 + x));
}
for (const [x, y, z, len] of [[0, 24.7, -8.0, 4.0], [0, 24.7, -5.4, 4.6], [0, 24.7, -2.8, 4.4], [-2.6, 24.2, -6.6, 3.4], [2.6, 24.2, -4.2, 3.6],
	[-2.8, 24.2, -1.6, 3.2], [2.8, 24.2, -0.6, 3.0], [-4.6, 23.0, -8.6, 2.6], [4.6, 23.0, -7.4, 2.6], [0, 23.8, 0.2, 3.8]]) {
	spike(quillsF, [x, y, z], [x * 0.1, 0.8, 0.6], len, 2.2, 70 + Math.round(-z * 2 + x));
}
for (const [x, z, len] of [[0, 2.8, 3.6], [-2.2, 4.6, 2.6], [2.2, 5.6, 2.6], [0, 6.4, 3.4], [0, 9.6, 3.0], [-2.0, 11.0, 2.2], [2.0, 12.0, 2.2], [0, 12.8, 2.4]]) {
	const y = 23.2 - (z + 1.0) * Math.sin(8 * DEG) - 0.4 - (x ? 0.3 : 0);
	spike(quillsB, [x, y, z], [x * 0.1, 0.66, 0.75], len, 2.0, 90 + Math.round(z * 2 + x));
}

// ---- the front legs: long and thick; the upper arm down and back from the shoulder, the forearm down and forward
const FX = 7.0;                                  // the front legs' centre line (|x|)
const UA = 14, UL = 10.6, FA = -20;              // upper arm angle / length, forearm angle
const SHOULDER_Y = 20.8, SHOULDER_Z = -5.5, WRIST_Y = 3.4;
const ELBOW = end([0, SHOULDER_Y, SHOULDER_Z], UL, UA);
const FL = (ELBOW[1] - WRIST_Y) / Math.cos(FA * DEG);
const WRIST = end(ELBOW, FL, FA);
for (const s of S) {
	const n = nm(s);
	const sh = [s * FX, SHOULDER_Y, SHOULDER_Z];
	const arm = M.bone(`arm_${n}`, 'chest', sh);
	limb(arm, sh, UL + 1.0, 2.2, UA, 3.0, 3.55, 'fur', 80);                    // the upper arm
	limb(arm, sh, 3.6, 2.8, UA, 3.3, 3.9, 'fur', 81);                         // the shoulder
	if (s > 0) { // the Omnitrix cuff, near the shoulder, tipped with the upper arm (its own bone, so the face can sit on it)
		const badge = M.bone('badge', `arm_${n}`, sh, [UA, 0, 0]);
		// v0.17.1 (user: "move the badge a bit higher up on wildmutts arm and make the shoulder pad cover his entire shoulder ...
		// the badge in the middle of the shoulder pad"): a pad over the whole shoulder (up to its top), the rim along its lower
		// edge, the plate centred on its outer face (WildmuttRenderer.Badge puts the symbol on that plate)
		// (the shoulder's fur block reaches 23.6 -- the pad caps it, user: "still not covering the top of his shoulder")
		M.box(badge, FX - 3.6, 16.2, SHOULDER_Z - 4.15, FX + 3.6, 24.1, SHOULDER_Z + 4.15, 'cuff');
		M.box(badge, FX - 3.78, 15.5, SHOULDER_Z - 4.33, FX + 3.78, 16.4, SHOULDER_Z + 4.33, 'rim');
		M.box(badge, FX + 3.5, 18.2, SHOULDER_Z - 1.95, FX + 4.1, 22.1, SHOULDER_Z + 1.95, 'plate');
	}
	const el = [s * FX, ELBOW[1], ELBOW[2]];
	const fa = M.bone(`forearm_${n}`, `arm_${n}`, el);
	limb(fa, el, FL + 0.7, 1.6, FA, 2.65, 2.95, 'fur', 84);
	spike(fa, [s * FX, ELBOW[1] + 0.4, ELBOW[2] + 2.3], [s * 0.2, 0.15, 1], 2.4, 1.5, 85);   // the elbow tuft
	const wr = [s * FX, WRIST[1], WRIST[2]];
	const paw = M.bone(`paw_${n}`, `forearm_${n}`, wr);
	sbox(paw, s, FX - 3.0, FX + 3.0, 0, 2.2, WRIST[2] - 5.6, WRIST[2] + 2.7, 'fur', { seed: 88 });        // the flat paw
	sbox(paw, s, FX - 2.75, FX + 2.75, 2.0, 4.2, WRIST[2] - 1.5, WRIST[2] + 2.9, 'fur', { seed: 89 });      // the raised wrist step
	for (let i = -1; i <= 1; i++) {                                                                          // three dark claws
		sbox(paw, s, FX + i * 1.95 - 0.72, FX + i * 1.95 + 0.72, 0, 1.5, WRIST[2] - 6.8, WRIST[2] - 5.4, 'claw', { seed: 94 + i });
	}
}

// ---- the hind legs: dog-like and thick, a little shorter -- the thigh forward, the shin back to the hock, the same paws
const HX = 5.3;
const HIP = [0, 17.5, 10], TA = -24, TL = 8.4, SA = 34, SL = 6.4;
const KNEE = end(HIP, TL, TA);
const HOCK = end(KNEE, SL, SA);
for (const s of S) {
	const n = nm(s);
	const hp = [s * HX, HIP[1], HIP[2]];
	const th = M.bone(`thigh_${n}`, 'hips', hp);
	sbox(th, s, HX - 3.0, HX + 3.0, 12.0, 20.4, 5.8, 14.8, 'fur', { seed: 100 });   // the haunch
	limb(th, hp, TL + 0.8, 1.0, TA, 2.85, 3.35, 'fur', 101);                          // the thigh
	const kn = [s * HX, KNEE[1], KNEE[2]];
	const sh = M.bone(`shin_${n}`, `thigh_${n}`, kn);
	limb(sh, kn, SL + 0.6, 1.2, SA, 2.45, 2.7, 'fur', 102);
	const hk = [s * HX, HOCK[1], HOCK[2]];
	const ft = M.bone(`foot_${n}`, `shin_${n}`, hk);
	limb(ft, hk, HOCK[1] - 1.6, 0.8, -8, 2.3, 2.35, 'fur', 104);                     // the hock down to the paw
	sbox(ft, s, HX - 2.8, HX + 2.8, 0, 2.1, HOCK[2] - 5.8, HOCK[2] + 1.8, 'fur', { seed: 105 });
	sbox(ft, s, HX - 2.5, HX + 2.5, 1.9, 3.7, HOCK[2] - 2.3, HOCK[2] + 2.0, 'fur', { seed: 106 });
	for (let i = -1; i <= 1; i++) {
		sbox(ft, s, HX + i * 1.85 - 0.68, HX + i * 1.85 + 0.68, 0, 1.4, HOCK[2] - 6.9, HOCK[2] - 5.6, 'claw', { seed: 108 + i });
	}
}

M.ground();

// ================================================================ paint
const C = {
	fur: [243, 152, 40], furHi: [255, 192, 96], furLo: [208, 110, 24], furDeep: [168, 80, 16], furLine: [150, 68, 12], outline: [140, 64, 12],
	belly: [250, 176, 84], tuftTip: [255, 204, 116],
	claw: [70, 52, 40], clawHi: [118, 94, 72],
	band: [18, 16, 16], bandHi: [40, 36, 36], tooth: [240, 228, 194], toothLo: [194, 178, 140],
	mouth: [92, 22, 28], tongue: [186, 80, 96],
	cuff: [24, 28, 46], cuffHi: [50, 58, 86], rim: [172, 198, 212], rimLo: [124, 150, 166], plate: [12, 14, 22], plateEdge: [70, 80, 104],
	gill: [112, 46, 12],
};
// rework 1's fur strokes: long thin dark lines running down the body (anisotropic noise), a few
function furLines(p, seed) {
	const n = fbm(p[0] * 0.7 + seed * 1.7, p[1] * 0.3, p[2] * 0.7, 3);
	const gate = fbm(p[0] * 0.25 + 9, p[1] * 0.25, p[2] * 0.25 + seed, 2);
	return smooth(0.66, 0.7, n) * (1 - smooth(0.71, 0.75, n)) * smooth(0.5, 0.58, gate);
}
// v0.17.1 (user: "make the texture not so sandy or spongey ... hes supposed to be covered in fur"): FUR instead of speckle --
// fine strands that flow one way (down the sides, back along the top), shingled locks (each darker at its root where the lock
// above shades it, lighter at the tip) and only a gentle mottling under it. No per-texel grain, no random flecks.
function fur(col, p, seed, f) {
	const side = f !== 'up' && f !== 'down';
	// u runs across the flow, v along it (down the sides; head-to-tail along the top)
	const u = side ? (f === 'east' || f === 'west' ? p[2] : p[0]) : p[0];
	const v = side ? -p[1] : p[2];
	// soft, large-scale mottling (much gentler than before)
	const blot = fbm(p[0] * 0.22 + seed * 0.7, p[1] * 0.22, p[2] * 0.22, 3);
	col = mix(col, blot > 0.5 ? C.furHi : C.furLo, Math.min(0.45, Math.abs(blot - 0.5) * 0.8));
	// strands: every texel column (about 1/3 px) its own hair -- a per-column tone, broken into lengths along the flow
	const colId = Math.floor(u * 3 + 0.01);
	const tone = hash3(colId, seed * 13 + 5, 7);
	const run = fbm(colId * 0.71 + seed, v * 0.55, 3.3, 2);
	const strand = 0.5 + (tone - 0.5) * 0.8 * smooth(0.35, 0.6, run);
	// locks: short tufts whose ends come to points (a V across each tuft), every tuft row offset like shingles
	const L = 2.1, W = 1.4;
	const tuft = Math.floor(u / W + seed * 0.13);
	const across = (u / W) - Math.floor(u / W); // 0..1 across the tuft
	const point = Math.abs(across - 0.5) * 0.9; // the tip sits lower in the middle of the tuft
	const off = hash3(tuft, seed * 7 + 1, 3) * L;
	let t = ((v + off + point) / L) % 1;
	if (t < 0) t += 1;
	const lock = smooth(0, 0.8, t); // 0 at the root (shaded by the tuft above) -> 1 at the tip (lit)
	const k = (lock - 0.5) * 0.42 + (strand - 0.5) * 0.55;
	return k >= 0 ? mix(col, C.furHi, Math.min(0.5, k * 1.1)) : mix(col, C.furDeep, Math.min(0.5, -k));
}
// (the old speckle mottling, no longer used)
function mottle(col, p, seed) {
	const blot = fbm(p[0] * 0.3 + seed * 0.7, p[1] * 0.3, p[2] * 0.3, 3);
	col = mix(col, blot > 0.5 ? C.furHi : C.furLo, Math.min(1, Math.abs(blot - 0.5) * 1.1));
	const q = [Math.floor(p[0] * 1.5 + 0.01), Math.floor(p[1] * 1.5 + 0.01), Math.floor(p[2] * 1.5 + 0.01)];
	const h = hash3(q[0] + seed * 31, q[1], q[2]);
	if (h > 0.88) col = mix(col, C.furHi, 0.6);
	else if (h < 0.07) col = mix(col, C.furDeep, 0.6);
	else if (h < 0.17) col = mix(col, C.furLo, 0.5);
	const fine = hash3(Math.floor(p[0] * 3 + 0.01) + 7, Math.floor(p[1] * 3 + 0.01), Math.floor(p[2] * 3 + 0.01) + seed);
	return mul(col, 0.95 + 0.1 * fine);
}
function shade(c, f, s, t, p, info) {
	const e = info.ePx;
	switch (c.mat) {
		case 'claw': {
			const k = f === 'up' ? 0.5 : f === 'down' ? 0 : 0.15;
			return [mix(C.claw, C.clawHi, e < 0.25 ? k + 0.3 : k), 255, null];
		}
		case 'tooth': {
			let col = C.tooth;
			if (f === 'down' || f === 'south') col = C.toothLo;
			else if (e < 0.22) col = mix(C.tooth, C.toothLo, 0.65);
			const h = hash3(Math.floor(p[0] * 3), Math.floor(p[1] * 3), c.seed);
			if (h < 0.15) col = mix(col, C.toothLo, 0.5);
			if (f !== 'up' && p[1] < 17.9) col = mix(col, C.toothLo, 0.5); // the root, shadowed by the band
			return [col, 255, null];
		}
		case 'band': {
			// the faces inside the mouth (the upper band's underside is the roof, the lower band's top the floor): the mouth's
			// red behind a black lip, seen only when the jaw really opens; everything outside is all black, so no red line
			// shows along the closed mouth
			const inner = (c.tag === 'upper' && f === 'down') || (c.tag === 'lower' && f === 'up');
			if (inner && e > 0.5 && p[2] > -24.6) return [mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
			return [f === 'up' || e < 0.2 ? C.bandHi : C.band, 255, null];
		}
		case 'cuff': {
			if (e < 0.3) return [C.cuffHi, 255, null];
			const seam = Math.abs(p[1] - 23.85) < 0.25; // a dark seam round the pad's top edge
			return [f === 'up' ? C.cuffHi : seam ? C.plate : C.cuff, 255, null];
		}
		case 'rim': return [f === 'down' || e < 0.22 ? C.rimLo : C.rim, 255, null];
		case 'plate': return [e < 0.3 ? C.plateEdge : C.plate, 255, null];
		default: { // fur, skull, neck, belly, tuft, tuftTip
			// the inside of the mouth: the roof (the skull's underside) and the jaw's top with the tongue -- black for the first
			// px behind the band and round the rims, so nothing red shows unless the jaw really opens
			if (c.mat === 'skull' && f === 'down') {
				if (e < 0.4 || p[2] < -24.2) return [C.band, 255, null];
				return [mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
			}
			if (c.bone === 'jaw' && c.mat === 'fur' && f === 'up') {
				if (e < 0.4 || p[2] < -23.9) return [C.band, 255, null];
				if (Math.abs(p[0]) < 3.2 && p[2] < -14.5) return [C.tongue, 255, null];
				return [mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
			}
			let col;
			if (c.mat === 'belly') col = C.belly;
			else if (c.mat === 'tuftTip') col = mix(C.fur, C.tuftTip, 0.4 + 0.6 * (1 - t));
			else if (c.mat === 'tuft') col = mix(C.furLo, C.fur, 0.45 + 0.55 * (1 - t));
			else col = C.fur;
			// lit from above: darker low on the legs and under the body, lighter on top
			const lift = clamp((p[1] - 1) / 23, 0, 1);
			col = mix(C.furLo, col, 0.5 + 0.5 * lift);
			if (f === 'down') col = mul(col, 0.76);
			if (f === 'up') col = mix(col, C.furHi, 0.26);
			col = fur(col, p, c.seed, f);
			if (c.mat !== 'belly' && c.mat !== 'tuftTip' && !(c.mat === 'skull' && f === 'north')) col = mix(col, C.furLine, 0.12 * furLines(p, c.seed));
			// (v0.17.1: the cartoon tick marks are gone -- the strands carry the fur now)
			// the gill slits: two dark slits on each side of the head behind the mouth band, two on the neck
			if ((f === 'east' || f === 'west') && (c.mat === 'skull' || c.mat === 'neck' || (c.bone === 'jaw' && c.mat === 'fur'))) {
				const zs = c.mat === 'neck' ? [-14.2, -12.6] : [-14.4, -13.2];
				for (const zc0 of zs) {
					const zc = zc0 + (p[1] - 17) * 0.2;
					const top = c.mat === 'neck' ? 21.0 : 21.2, bot = c.mat === 'neck' ? 15.6 : 14.4;
					if (Math.abs(p[2] - zc) < 0.32 && p[1] > bot && p[1] < top) col = mix(col, C.gill, 0.88);
				}
			}
			// a soft darker rim on every face (separates the blocks, like the reference's outlined boxes)
			if (e < 0.36) col = mix(col, C.outline, 0.3 * (1 - e / 0.36));
			return [col, 255, null];
		}
	}
}

M.layout();
M.paint(shade);
const b0 = M.bounds(M.world({}));
console.log(`wildmutt: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ground shift ${(M.groundShift || 0).toFixed(2)}, ` +
	`x ${b0.lo[0].toFixed(1)}..${b0.hi[0].toFixed(1)}, y ${b0.lo[1].toFixed(1)}..${b0.hi[1].toFixed(1)} (${(b0.hi[1] / 16).toFixed(2)} blocks), ` +
	`z ${b0.lo[2].toFixed(1)}..${b0.hi[2].toFixed(1)} (${((b0.hi[2] - b0.lo[2]) / 16).toFixed(2)} long)`);
for (const n of ['arm_l', 'forearm_l', 'paw_l', 'badge', 'thigh_l', 'shin_l', 'foot_l', 'head', 'neck']) {
	console.log(`  ${n} pivot ${M.B[n].pivot.map(v => v.toFixed(2)).join(', ')}`);
}

writeJson(path.join(A, 'geo', 'omnitrix', 'wildmutt.geo.json'), M.geoJson('geometry.projecthero.wildmutt'));
writePng(path.join(A, 'textures', 'entity', 'omnitrix', 'wildmutt.png'), M.TW, M.TH, M.tex);
module.exports = { M, ROOT, A, L };

require('./anims.js')(M, A, ROOT);
const si = process.argv.indexOf('--sheets');
if (si > 0) require('./preview.js')(M, process.argv[si + 1]);
