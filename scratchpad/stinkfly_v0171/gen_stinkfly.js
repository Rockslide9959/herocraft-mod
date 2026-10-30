// v0.17.1: RETIRED -- Stinkfly is a player-model alien on the user's skin again (scratchpad/ben10_v0171/gen_skins.js writes
// stinkfly.png). Running this would overwrite that texture and needs the deleted client.alien.stinkfly classes.
// v0.17.1: STINKFLY -- the Ben 10 Lepidopterran as a GeckoLib body (user: a proper, good-looking custom body from the reference
// images, built like Wildmutt). An upright insect about player height: a black, rounded-boxy head with a pale grey mouth plate and
// small fangs, FOUR short green-black eye-stalks (two a side, angled up and out) ending in yellow-orange bulb eyes; a narrow chest
// and waist in a white suit with a black stripe down each side and black bands; FOUR slender segmented olive-green limbs -- two
// mantis-like arms off the shoulders, angled forward, with black three-clawed hands, and two thin legs with sharp knees and spiky
// black feet; a long, thin white tail curving down and back from the waist to a black arrowhead stinger fin with a white stripe; and
// TWO PAIRS of big dragonfly wings off the upper back (pale yellow-green, see-through, thin dark veins, pointed, swept back, round
// notches along the trailing edge; the upper pair longer and up-and-out, the lower pair shorter). The Omnitrix is a small plate on
// the chest (the renderer draws the badge on it).
//
// Model pixels are real 1/16-block pixels, drawn 1:1 (the kit's 0.9 size stat scales him, so ~1.65 blocks to the top of the head,
// ~1.95 to the eyes). Bedrock / GeckoLib conventions: +Y up, the model faces -Z, the entity's RIGHT side is -X.
//
// Writes:
//   src/main/resources/assets/projecthero/geo/omnitrix/stinkfly.geo.json
//   src/main/resources/assets/projecthero/textures/entity/omnitrix/stinkfly.png             (+ stinkfly_glowmask.png: eyes, wings)
//   src/main/resources/assets/projecthero/animations/omnitrix/stinkfly.animation.json       (anims.js)
//   preview sheets (preview.js) into the folder given as --sheets <dir> (not committed; the in-game shots are)
// Usage: node scratchpad/stinkfly_v0171/gen_stinkfly.js [--sheets <dir>]
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

const M = new Model('stinkfly', { TW: 512, D: 4 });
const S = [-1, 1];
const nm = s => (s < 0 ? 'r' : 'l');
const DEG = Math.PI / 180;
function sbox(b, s, xa, xb, y0, y1, z0, z1, mat, opts) {
	return M.box(b, s * xa, y0, z0, s * xb, y1, z1, mat, opts);
}
// a limb segment hanging from `piv`, `len` long below it (+ `over` above, into the joint), tipped `ang` degrees about X
// (+ swings the lower end back, - forward); half-width hw (x), half-depth hd (z)
function limb(b, piv, len, over, ang, hw, hd, mat, seed) {
	return M.box(b, piv[0] - hw, piv[1] - len, piv[2] - hd, piv[0] + hw, piv[1] + over, piv[2] + hd, mat,
		{ seed, rot: [ang, 0, 0], pivot: piv.slice() });
}
const end = (piv, len, ang) => [piv[0], piv[1] - len * Math.cos(ang * DEG), piv[2] + len * Math.sin(ang * DEG)];
// a tapered spike / claw: a base and a thinner tip along dir
function spike(b, from, dir, len, w, mat, seed, tipMat) {
	const d = L.vnorm(dir);
	M.stick(b, from, d, len * 0.6, [w, w], mat, { seed });
	M.stick(b, [from[0] + d[0] * len * 0.5, from[1] + d[1] * len * 0.5, from[2] + d[2] * len * 0.5], d, len * 0.5, [w * 0.55, w * 0.55], tipMat || mat, { seed: seed + 1 });
}

// ================================================================ the rig
M.bone('root', null, [0, 0, 0]);
M.bone('hurt', 'root', [0, 12, 1]);           // the flinch layer (its own controller)
const hips = M.bone('hips', 'hurt', [0, 12.5, 0.6]);
const chest = M.bone('chest', 'hips', [0, 15, 0.5]);
const neck = M.bone('neck', 'chest', [0, 22.2, 0.2]);
M.bone('look', 'neck', [0, 23.4, -0.2]);       // where the player looks (set in code, never keyed)
const head = M.bone('head', 'look', [0, 23.4, -0.2]);
const jaw = M.bone('jaw', 'head', [0, 24.6, -2.0]);

// ---- the waist: narrow, white, a black band and black hip joints
M.box(hips, -2.2, 11.2, -1.4, 2.2, 15.2, 2.3, 'suit', { seed: 1 });
M.box(hips, -2.6, 10.6, -1.7, 2.6, 12.4, 2.6, 'suitBlack', { seed: 2 });            // the pelvis (the legs' sockets)
// ---- the chest: a lower thorax, a broader upper chest and shoulders, a black wing-root plate on the back
M.box(chest, -2.8, 14.8, -1.8, 2.8, 18.0, 2.6, 'suit', { seed: 10 });
M.box(chest, -3.4, 17.6, -2.3, 3.4, 22.4, 2.9, 'suit', { seed: 11 });
for (const s of S) sbox(chest, s, 2.9, 4.1, 20.2, 22.3, -1.5, 1.5, 'suitBlack', { seed: 12 });  // shoulder sockets
M.box(chest, -2.6, 22.2, -1.7, 2.6, 22.8, 2.2, 'suit', { seed: 13 });              // the collar's shelf
M.box(chest, -2.4, 17.8, 2.8, 2.4, 22.0, 3.4, 'suitBlack', { seed: 14 });            // the wing roots' plate
// the Omnitrix plate on the chest (its own bone so the badge layer finds it)
const badge = M.bone('badge', 'chest', [0, 20, -2.3]);
M.box(badge, -1.75, 18.3, -2.6, 1.75, 21.7, -2.25, 'rim');
M.box(badge, -1.45, 18.6, -2.85, 1.45, 21.4, -2.55, 'plate');
// ---- the neck
M.box(neck, -1.3, 21.8, -1.3, 1.3, 23.8, 1.2, 'head', { seed: 20 });
// ---- the head: black and rounded-boxy (a core, a wider middle band, a top cap, a bulge at the back)
M.box(head, -2.9, 23.2, -3.4, 2.9, 28.6, 1.8, 'head', { seed: 30 });
M.box(head, -3.3, 24.0, -3.0, 3.3, 27.8, 1.4, 'head', { seed: 31 });
M.box(head, -2.4, 28.4, -2.8, 2.4, 29.2, 1.2, 'head', { seed: 32 });
M.box(head, -2.3, 23.8, 1.6, 2.3, 28.0, 2.4, 'head', { seed: 33 });
M.box(head, -2.0, 27.0, -3.75, 2.0, 28.2, -3.2, 'head', { seed: 34 });             // the brow over the mouth plate
// the pale grey mouth plate, and the small fangs along its lower edge
M.box(head, -1.95, 24.0, -3.95, 1.95, 26.4, -3.1, 'mouth', { seed: 35, tag: 'upper' }); // (a black face round it)
for (const x of [-1.75, -0.8, 0.8, 1.75]) {
	const w = Math.abs(x) > 1 ? 0.34 : 0.28;
	M.box(head, x - w, 23.55, -4.1, x + w, 24.45, -3.7, 'fang', { seed: 36 });
	M.box(head, x - w * 0.45, 23.15, -4.05, x + w * 0.45, 23.6, -3.8, 'fang', { seed: 37 });
}
// the lower jaw (opens for the spit), little pincers at its corners
M.box(jaw, -2.05, 22.7, -3.8, 2.05, 24.6, -1.6, 'mouth', { seed: 40, tag: 'jaw' });
for (const s of S) {
	sbox(jaw, s, 1.7, 2.55, 22.9, 23.9, -3.6, -2.3, 'mouth', { seed: 41 });
	sbox(jaw, s, 1.55, 2.2, 22.3, 23.1, -4.0, -3.3, 'fang', { seed: 42 });
}
// ---- the four eye-stalks: short green-black stalks angled up and out, each a yellow-orange bulb eye (its pupil to the front)
const STALKS = [
	// [name suffix, pivot |x|, y, z, rot x (+ forward), rot z (|outward|), stalk length]
	['1', 1.7, 28.7, -1.6, 14, 24, 3.6],
	['2', 3.0, 26.9, -1.9, 10, 62, 3.0],
];
for (const s of S) {
	for (const [k, px, py, pz, rx, rz, len] of STALKS) {
		const piv = [s * px, py, pz];
		const b = M.bone(`stalk_${nm(s)}${k}`, 'head', piv, [rx, 0, s * rz]);
		M.box(b, piv[0] - 0.55, py - 0.6, pz - 0.55, piv[0] + 0.55, py + len, pz + 0.55, 'stalk', { seed: 50 + Number(k) });
		M.box(b, piv[0] - 0.75, py + len * 0.55, pz - 0.75, piv[0] + 0.75, py + len * 0.55 + 0.6, pz + 0.75, 'stalk', { seed: 52 }); // a ring
		M.box(b, piv[0] - 1.25, py + len - 0.2, pz - 1.25, piv[0] + 1.25, py + len + 2.3, pz + 1.25, 'eye', { seed: 54 + Number(k) });
	}
}

// ---- the arms: mantis-like, off the shoulders -- the upper arm down and a little forward, the forearm out forward and down,
// a black hand with three pointed claws; small dark spikes at the elbow and along the forearm
const SH = [3.7, 21.2, -0.3], UL = 5.4, UA = -18, FLN = 5.8, FA = -72;
const ELBOW = end(SH, UL, UA);
const WRIST = end(ELBOW, FLN, FA);
for (const s of S) {
	const n = nm(s);
	const sh = [s * SH[0], SH[1], SH[2]];
	const arm = M.bone(`arm_${n}`, 'chest', sh);
	limb(arm, sh, UL + 0.5, 0.9, UA, 0.85, 0.95, 'limb', 60);
	M.box(arm, sh[0] - 1.15, sh[1] - 1.2, sh[2] - 1.15, sh[0] + 1.15, sh[1] + 1.1, sh[2] + 1.15, 'limbDark', { seed: 61 }); // the shoulder knob
	const el = [s * SH[0], ELBOW[1], ELBOW[2]];
	const fa = M.bone(`forearm_${n}`, `arm_${n}`, el);
	M.box(fa, el[0] - 1.0, el[1] - 1.0, el[2] - 1.0, el[0] + 1.0, el[1] + 1.0, el[2] + 1.0, 'limbDark', { seed: 62 }); // the elbow
	limb(fa, el, FLN + 0.4, 0.5, FA, 0.72, 0.8, 'limb', 63);
	spike(fa, [el[0], el[1] + 0.3, el[2] + 0.6], [s * 0.15, 0.8, 0.6], 1.9, 0.6, 'limbDark', 64, 'spike');      // the elbow spike
	for (const k of [0.35, 0.65]) { // two small spikes along the forearm's lower edge
		const q = [el[0], el[1] - (FLN * k) * Math.cos(FA * DEG) - 0.6, el[2] + (FLN * k) * Math.sin(FA * DEG)];
		spike(fa, q, [s * 0.1, -1, 0.35], 1.0, 0.42, 'limbDark', 65 + Math.round(k * 10), 'spike');
	}
	const wr = [s * SH[0], WRIST[1], WRIST[2]];
	const hand = M.bone(`hand_${n}`, `forearm_${n}`, wr);
	M.box(hand, wr[0] - 0.95, wr[1] - 0.95, wr[2] - 1.5, wr[0] + 0.95, wr[1] + 0.85, wr[2] + 0.5, 'hand', { seed: 70 });
	for (let i = -1; i <= 1; i++) { // three claws, fanned a little, pointing forward and down
		const from = [wr[0] + i * 0.6, wr[1] - 0.2 - Math.abs(i) * 0.1, wr[2] - 1.3];
		spike(hand, from, [i * 0.28, -0.42, -1], 2.4, 0.55, 'claw', 72 + i, 'clawTip');
	}
	spike(hand, [wr[0] - s * 0.2, wr[1] + 0.2, wr[2] - 0.2], [-s * 0.55, -0.3, -0.75], 1.4, 0.45, 'claw', 76, 'clawTip'); // the thumb claw
}

// ---- the legs: thin, a sharp knee forward, the shin back down to a spiky black foot
const HX = 2.2, HIP = [0, 11.6, 0.6], TA = -30, TL = 6.2, SA = 27;
const KNEE = end(HIP, TL, TA);
const ANKLE_Y = 1.5;
const SL = (KNEE[1] - ANKLE_Y) / Math.cos(SA * DEG);
const ANKLE = end(KNEE, SL, SA);
for (const s of S) {
	const n = nm(s);
	const hp = [s * HX, HIP[1], HIP[2]];
	const th = M.bone(`thigh_${n}`, 'hips', hp);
	M.box(th, hp[0] - 1.1, hp[1] - 1.1, hp[2] - 1.1, hp[0] + 1.1, hp[1] + 1.0, hp[2] + 1.1, 'limbDark', { seed: 80 }); // the hip knob
	limb(th, hp, TL + 0.4, 0.6, TA, 0.85, 0.95, 'limb', 81);
	const kn = [s * HX, KNEE[1], KNEE[2]];
	const sh = M.bone(`shin_${n}`, `thigh_${n}`, kn);
	M.box(sh, kn[0] - 0.95, kn[1] - 0.95, kn[2] - 0.95, kn[0] + 0.95, kn[1] + 0.95, kn[2] + 0.95, 'limbDark', { seed: 82 }); // the knee
	spike(sh, [kn[0], kn[1] + 0.2, kn[2] - 0.5], [s * 0.1, 0.55, -1], 1.6, 0.55, 'limbDark', 83, 'spike'); // the knee spike
	limb(sh, kn, SL + 0.3, 0.5, SA, 0.7, 0.75, 'limb', 84);
	for (const k of [0.4, 0.72]) { // spikes down the back of the shin
		const q = [kn[0], kn[1] - SL * k * Math.cos(SA * DEG), kn[2] + SL * k * Math.sin(SA * DEG) + 0.55];
		spike(sh, q, [s * 0.15, 0.35, 1], 1.1, 0.42, 'limbDark', 85 + Math.round(k * 10), 'spike');
	}
	const an = [s * HX, ANKLE[1], ANKLE[2]];
	const ft = M.bone(`foot_${n}`, `shin_${n}`, an);
	M.box(ft, an[0] - 0.85, 0.4, an[2] - 1.5, an[0] + 0.85, an[1] + 0.6, an[2] + 0.9, 'hand', { seed: 90 });
	for (let i = -1; i <= 1; i++) { // three forward toe claws, a heel spur
		spike(ft, [an[0] + i * 0.55, 0.85, an[2] - 1.3], [i * 0.3, -0.32, -1], 2.0, 0.5, 'claw', 92 + i, 'clawTip');
	}
	spike(ft, [an[0], 0.9, an[2] + 0.7], [s * 0.15, -0.35, 1], 1.4, 0.45, 'claw', 96, 'clawTip');
}

// ---- the tail: a long thin white abdomen curving down and back from the waist, in four segments (a bone each), to the fin
const TAIL = [ // [length, degrees below the horizontal, width]
	[5.0, 34, 3.1], [4.7, 44, 2.6], [4.3, 38, 2.15], [3.9, 22, 1.7],
];
let tp = [0, 12.4, 2.0];
let parent = 'hips';
const TAIL_PIVOTS = [];
TAIL.forEach(([len, ang, w], i) => {
	const b = M.bone(`tail${i + 1}`, parent, tp);
	TAIL_PIVOTS.push(tp.slice());
	const dir = [0, -Math.sin(ang * DEG), Math.cos(ang * DEG)];
	const back = 0.5; // overlap into the joint
	const from = [tp[0], tp[1] - dir[1] * back, tp[2] - dir[2] * back];
	M.stick(b, from, dir, len + back, [w, w], 'tail', { seed: 100 + i });
	if (i < TAIL.length - 1) { // a joint ring a little narrower than the next segment's root
		M.stick(b, [tp[0] + dir[0] * (len - 0.35), tp[1] + dir[1] * (len - 0.35), tp[2] + dir[2] * (len - 0.35)], dir, 0.7,
			[w * 0.92, w * 0.92], 'tailRing', { seed: 110 + i });
	}
	tp = [tp[0] + dir[0] * len, tp[1] + dir[1] * len, tp[2] + dir[2] * len];
	parent = b.name;
});
// the stinger fin: a black arrowhead blade (thin across x) pointing back and a little down, a white stripe along it
const FIN = M.bone('fin', 'tail4', tp);
const FIN_ANG = -12; // cube rot: negative tips its +Z end down
const finBox = (z0, z1, h, mat, x = 0.32, yc = 0) => M.box(FIN, -x, tp[1] + yc - h, tp[2] + z0, x, tp[1] + yc + h, tp[2] + z1, mat,
	{ rot: [FIN_ANG, 0, 0], pivot: tp.slice(), seed: 120 });
finBox(-0.6, 1.0, 0.9, 'finCollar', 0.85);
finBox(0.8, 2.3, 2.55, 'fin');
finBox(2.2, 3.5, 1.95, 'fin');
finBox(3.4, 4.6, 1.35, 'fin');
finBox(4.5, 5.5, 0.8, 'fin');
finBox(5.4, 6.3, 0.35, 'fin', 0.25);
for (const sy of [-1, 1]) finBox(0.2, 0.9, 0.35, 'fin', 0.28, sy * 2.25); // the barbs at the arrowhead's back corners

// ---- the wings: two pairs off the wing-root plate -- the upper pair longer, up and out; the lower pair shorter, out and a little
// down; both swept back. Each is a thin plate in its bone (along |x| outward), its outline, veins and notches in the texture.
const WINGS = [ // [suffix, pivot y, length, width, sweep (deg back), elevation (deg up)]
	['u', 21.3, 21, 7.4, 22, 34],
	['d', 19.2, 16, 6.0, 30, -12],
];
const WING_INFO = {};
for (const s of S) {
	for (const [k, py, len, w, sweep, elev] of WINGS) {
		const piv = [s * 1.5, py, 3.3];
		const b = M.bone(`wing_${k}${nm(s)}`, 'chest', piv, [0, -s * sweep, -s * elev]);
		const c = M.box(b, piv[0], py - w / 2, 3.25, piv[0] + s * len, py + w / 2, 3.45, 'wing', { tag: k });
		WING_INFO[b.name] = { len, w, s };
		c.wing = { len, w, s, pair: k, piv };
	}
}

M.ground(c => c.bone.startsWith('foot'));

// ================================================================ paint
const C = {
	limb: [143, 174, 58], limbHi: [182, 207, 96], limbLo: [95, 122, 34], limbDeep: [70, 92, 24], limbDark: [78, 100, 28], spot: [104, 130, 38],
	spike: [34, 40, 22], claw: [24, 24, 28], clawHi: [86, 88, 98], clawTip: [44, 44, 50],
	suit: [236, 239, 232], suitLo: [196, 202, 194], suitLine: [168, 174, 166], black: [26, 28, 32], blackHi: [58, 62, 72],
	head: [22, 23, 28], headHi: [56, 60, 72], headLo: [12, 12, 16],
	mouth: [158, 164, 166], mouthHi: [198, 202, 202], mouthLo: [112, 118, 122], fang: [236, 232, 214], fangLo: [186, 180, 160],
	stalk: [44, 58, 24], stalkHi: [74, 94, 36],
	eye: [252, 214, 40], eyeHi: [255, 246, 170], eyeLo: [238, 158, 26], pupil: [20, 16, 10],
	tail: [236, 239, 232], tailLo: [190, 196, 188], ring: [150, 156, 150],
	fin: [22, 22, 26], finHi: [62, 64, 72], finStripe: [238, 240, 234],
	rim: [170, 186, 196], rimLo: [120, 136, 148], plate: [14, 16, 22], plateEdge: [70, 80, 104],
	wing: [222, 232, 150], wingVein: [104, 120, 50], wingEdge: [84, 100, 40],
};
// the wings' outline: u along the wing (0 root .. 1 tip), v across it (-1 trailing .. +1 leading, in units of the half width);
// a narrow root, the widest part past the middle, a pointed swept tip, two round notches in the trailing edge
function wingShape(u, v, pair) {
	if (u < 0 || u > 1) return null;
	const half = 0.32 + 0.68 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.pow(u, 0.85) * 1.0)), 0.7);
	const tip = u > 0.8 ? 1 - smooth(0.8, 1.0, u) * 0.92 : 1;
	const ctr = -0.32 * u * u;                   // the tip sweeps toward the trailing edge
	const e = half * tip;
	const d = (v - ctr) / e;                     // -1 .. 1 inside
	if (Math.abs(d) > 1) return null;
	// the trailing edge's round notches
	const notches = pair === 'u' ? [[0.44, 0.1], [0.64, 0.085]] : [[0.5, 0.11]];
	for (const [nu, nr] of notches) {
		const dv = (v - (ctr - e)) / 2; // across, in wing-widths, from the trailing edge
		if (Math.hypot(u - nu, dv * 0.55) < nr) return null;
	}
	return { d, e, ctr, half };
}
function shadeWing(c, f, s, t, p) {
	const { len, w, s: side, pair } = c.wing;
	if (f !== 'north' && f !== 'south') return [C.wing, 0, null];
	const u = (p[0] - M.B[c.bone].pivot[0]) * side / len;
	const v = (p[1] - (c.from[1] + c.to[1]) / 2) / (w / 2);
	const sh = wingShape(u, v, pair);
	if (!sh) return [C.wing, 0, null];
	const { d, e } = sh;
	const px = 1 / (w / 2); // one model pixel in v units
	// the dark border, the veins (three long ones and cross veins), a little milky shading toward the root
	const edgeDist = (1 - Math.abs(d)) * e / px; // model px to the edge
	if (edgeDist < 0.28 || u * len < 0.5) return [C.wingEdge, 235, [40, 44, 22], 0];
	let vein = false;
	for (const k of [-0.42, 0.12, 0.6]) if (Math.abs(d - k) * e / px < 0.14) vein = true;
	if (u > 0.08 && Math.abs(d - 0.12) * e / px < 0.2) vein = true; // the main vein a bit heavier
	const cross = (u * len) % 2.6;
	if (cross < 0.16 && u > 0.12 && u < 0.9) vein = true;
	if (vein) return [C.wingVein, 215, [34, 38, 18], 0];
	const milky = 1 - smooth(0, 0.55, u);
	const tint = mix(C.wing, [255, 255, 236], 0.25 * (0.5 + 0.5 * fbm(u * 6, v * 3, side + (pair === 'u' ? 0 : 5), 2)));
	// v0.17.1: more solid than the first pass (96..146 all but vanished against the sky)
	const a = Math.round(158 + 52 * milky);
	return [tint, a, [44, 48, 30], 0];
}
// a soft mottling of darker green spots on the limbs (the cartoon's), with the top-lit gradient
function limbPaint(col, p, seed, f, e) {
	const blot = fbm(p[0] * 0.55 + seed * 0.37, p[1] * 0.55, p[2] * 0.55, 3);
	col = mix(col, blot > 0.5 ? C.limbHi : C.limbLo, Math.min(0.4, Math.abs(blot - 0.5) * 0.9));
	const sp = fbm(p[0] * 1.5 + 11, p[1] * 1.5 + seed, p[2] * 1.5, 2);
	if (sp > 0.64) col = mix(col, C.spot, 0.55);
	const fine = hash3(Math.floor(p[0] * 4 + 0.01), Math.floor(p[1] * 4 + 0.01), Math.floor(p[2] * 4 + 0.01) + seed);
	col = mul(col, 0.96 + 0.08 * fine);
	if (f === 'up') col = mix(col, C.limbHi, 0.25);
	if (f === 'down') col = mul(col, 0.72);
	if (e < 0.22) col = mix(col, C.limbDeep, 0.45 * (1 - e / 0.22));
	return col;
}
function shade(c, f, s, t, p, info) {
	const e = info.ePx;
	const lift = clamp(p[1] / 30, 0, 1);
	switch (c.mat) {
		case 'wing': return shadeWing(c, f, s, t, p);
		case 'limb': {
			let col = mix(C.limbLo, C.limb, 0.55 + 0.45 * lift);
			return [limbPaint(col, p, c.seed, f, e), 255, null];
		}
		case 'limbDark': {
			let col = mix(C.limbDeep, C.limbDark, 0.6 + 0.4 * lift);
			col = limbPaint(col, p, c.seed + 9, f, e);
			return [mul(col, 0.82), 255, null];
		}
		case 'spike': return [f === 'up' || e < 0.12 ? mix(C.spike, C.limbDark, 0.35) : C.spike, 255, null];
		case 'claw': case 'hand': {
			let col = C.claw;
			if (f === 'up') col = mix(C.claw, C.clawHi, 0.45);
			else if (e < 0.14) col = mix(C.claw, C.clawHi, 0.6);
			else if (c.mat === 'hand' && hash3(Math.floor(p[0] * 3), Math.floor(p[1] * 3), Math.floor(p[2] * 3)) > 0.86) col = mix(C.claw, C.clawHi, 0.25);
			return [col, 255, null];
		}
		case 'clawTip': return [f === 'up' ? C.clawHi : C.clawTip, 255, null];
		case 'suit': {
			let col = mix(C.suitLo, C.suit, 0.55 + 0.45 * clamp((p[1] - 10) / 14, 0, 1));
			if (f === 'down') col = mul(col, 0.8);
			// the black stripe down each side
			if (f === 'east' || f === 'west') {
				const zc = (c.from[2] + c.to[2]) / 2 + 0.2;
				if (Math.abs(p[2] - zc) < 0.95) col = C.black;
				else if (Math.abs(p[2] - zc) < 1.2) col = mix(col, C.black, 0.5);
			}
			// the black bands across the front and back: the belt, under the chest; a black V down from the shoulders on the front
			if (f === 'north' || f === 'south' || f === 'east' || f === 'west') {
				if ((p[1] > 13.2 && p[1] < 14.2) || (p[1] > 17.75 && p[1] < 18.45)) col = C.black;
			}
			if (f === 'north' && c.bone === 'chest' && p[1] > 18.4) {
				const vx = Math.abs(p[0]) - (p[1] - 18.4) * 0.62;
				if (Math.abs(vx - 1.9) < 0.38) col = C.black;
			}
			if (f === 'south' && c.bone === 'chest' && p[1] > 18.4 && Math.abs(p[0]) > 2.6) col = C.black;
			if (f === 'up' && Math.abs(p[0]) > 2.7) col = C.black; // the shoulders' tops dark at the sockets
			// soft panel lines, light shading at the edges
			if (col !== C.black) {
				const g = fbm(p[0] * 0.8, p[1] * 0.8 + c.seed, p[2] * 0.8, 2);
				col = mul(col, 0.95 + 0.07 * g);
				if (e < 0.25) col = mix(col, C.suitLine, 0.55 * (1 - e / 0.25));
			} else if (e < 0.18) col = C.blackHi;
			return [col, 255, null];
		}
		case 'suitBlack': {
			let col = C.black;
			if (e < 0.2 || f === 'up') col = mix(C.black, C.blackHi, 0.6);
			return [col, 255, null];
		}
		case 'head': {
			let col = mix(C.headLo, C.head, 0.6 + 0.4 * clamp((p[1] - 22) / 7, 0, 1));
			if (f === 'up') col = mix(col, C.headHi, 0.35);
			if (e < 0.22) col = mix(col, C.headHi, 0.7 * (1 - e / 0.22));
			const g = fbm(p[0] * 1.2, p[1] * 1.2, p[2] * 1.2 + c.seed, 2);
			col = mul(col, 0.92 + 0.16 * g);
			// a glossy highlight on the dome
			if ((f === 'up' || f === 'north') && p[1] > 27.6 && Math.abs(p[0] + 0.9) < 1.0 && p[2] < -0.6) col = mix(col, [110, 118, 136], 0.45);
			return [col, 255, null];
		}
		case 'mouth': {
			let col = C.mouth;
			if (f === 'up') col = C.mouthHi;
			if (f === 'down') col = C.mouthLo;
			if (e < 0.2) col = mix(col, C.mouthLo, 0.6);
			// segment grooves across the plate, a dark mouth slit under the upper plate
			if (f === 'north' && c.tag === 'upper' && (Math.abs(p[1] - 24.95) < 0.14 || Math.abs(p[1] - 25.75) < 0.12)) col = mix(col, C.mouthLo, 0.8);
			if (f === 'north' && c.tag === 'upper' && Math.abs(p[0]) < 0.12) col = mix(col, C.mouthLo, 0.7);
			if (c.tag === 'jaw' && f === 'up') col = [70, 30, 34];
			return [col, 255, null];
		}
		case 'fang': return [f === 'down' || f === 'south' ? C.fangLo : e < 0.1 ? C.fangLo : C.fang, 255, null];
		case 'stalk': {
			let col = mix(C.stalk, C.stalkHi, 0.3 + 0.4 * fbm(p[0] * 3, p[1] * 3, p[2] * 3, 2));
			if (e < 0.15) col = mul(col, 0.7);
			return [col, 255, null];
		}
		case 'eye': {
			// a yellow bulb shading to orange at its root, a hot highlight, a black pupil on the front
			let col = mix(C.eyeLo, C.eye, 0.35 + 0.65 * (f === 'up' ? 1 : 1 - t));
			if (e < 0.22) col = mix(col, C.eyeLo, 0.6);
			let glow = mix(col, [255, 220, 80], 0.3);
			if (f === 'north') {
				const ds = Math.hypot(s - 0.5, t - 0.42);
				if (ds < 0.2) { col = C.pupil; glow = [0, 0, 0]; }
				else if (Math.hypot(s - 0.3, t - 0.22) < 0.1) col = C.eyeHi;
			}
			if (f === 'up' && Math.hypot(s - 0.35, t - 0.35) < 0.14) col = C.eyeHi;
			return [col, 255, mul(glow, 0.75), 255];
		}
		case 'tail': {
			// t runs along the stick (0 at the tip end): grey toward the joints, a faint black line along the top
			let col = mix(C.tailLo, C.tail, 0.5 + 0.5 * Math.sin(Math.PI * clamp(t, 0, 1)));
			if (f === 'down') col = mul(col, 0.82);
			if (e < 0.2) col = mix(col, C.suitLine, 0.5);
			const g = fbm(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9 + c.seed, 2);
			col = mul(col, 0.95 + 0.08 * g);
			return [col, 255, null];
		}
		case 'tailRing': return [e < 0.15 ? mul(C.ring, 0.8) : C.ring, 255, null];
		case 'finCollar': return [e < 0.18 || f === 'up' ? C.finHi : C.fin, 255, null];
		case 'fin': {
			let col = e < 0.16 ? C.finHi : C.fin;
			// the white stripe along the blade's middle (both flat sides), stopping short of the point
			if ((f === 'east' || f === 'west')) {
				// distance from the blade's centre line, in the fin's own frame (undo its tip)
				const dz = p[2] - TIP[2], dy = p[1] - TIP[1];
				const a = FIN_ANG * DEG;
				const along = dz * Math.cos(a) + dy * Math.sin(a), across = -dz * Math.sin(a) + dy * Math.cos(a);
				const half = 0.5 * (1 - clamp((along - 1.0) / 4.6, 0, 1)) + 0.12;
				if (Math.abs(across) < half && along > 0.6 && along < 5.2) col = C.finStripe;
			}
			return [col, 255, null];
		}
		case 'rim': return [f === 'down' || e < 0.15 ? C.rimLo : C.rim, 255, null];
		case 'plate': return [e < 0.22 ? C.plateEdge : C.plate, 255, null];
		default: return [[255, 0, 255], 255, null];
	}
}
const TIP = M.B.fin.pivot; // the fin's pivot (already shifted by ground())

M.layout();
M.paint(shade);
const b0 = M.bounds(M.world({}));
console.log(`stinkfly: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ground shift ${(M.groundShift || 0).toFixed(2)}, ` +
	`x ${b0.lo[0].toFixed(1)}..${b0.hi[0].toFixed(1)}, y ${b0.lo[1].toFixed(1)}..${b0.hi[1].toFixed(1)} (${(b0.hi[1] / 16).toFixed(2)} blocks), ` +
	`z ${b0.lo[2].toFixed(1)}..${b0.hi[2].toFixed(1)}`);
const bh = M.bounds(M.world({}), c => c.bone === 'head');
console.log(`  head y ${bh.lo[1].toFixed(2)}..${bh.hi[1].toFixed(2)} (x0.9: ${(bh.lo[1] * 0.9 / 16).toFixed(2)}..${(bh.hi[1] * 0.9 / 16).toFixed(2)} blocks)`);
for (const n of ['arm_r', 'forearm_r', 'hand_r', 'badge', 'thigh_r', 'shin_r', 'foot_r', 'head', 'tail1', 'fin', 'wing_ul']) {
	console.log(`  ${n} pivot ${M.B[n].pivot.map(v => v.toFixed(2)).join(', ')}`);
}
const pl = M.cubes().find(c => c.mat === 'plate');
console.log(`  badge plate x ${pl.from[0]}..${pl.to[0]} y ${pl.from[1].toFixed(2)}..${pl.to[1].toFixed(2)} z ${pl.from[2]}`);

writeJson(path.join(A, 'geo', 'omnitrix', 'stinkfly.geo.json'), M.geoJson('geometry.projecthero.stinkfly'));
writePng(path.join(A, 'textures', 'entity', 'omnitrix', 'stinkfly.png'), M.TW, M.TH, M.tex);
writePng(path.join(A, 'textures', 'entity', 'omnitrix', 'stinkfly_glowmask.png'), M.TW, M.TH, M.glow);
module.exports = { M, ROOT, A, L };

require('./anims.js')(M, A, ROOT);
const si = process.argv.indexOf('--sheets');
if (si > 0) require('./preview.js')(M, process.argv[si + 1]);
