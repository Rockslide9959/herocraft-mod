// v0.17.1: WILDMUTT -- the Ben 10 Vulpimancer as a GeckoLib quadruped.
// Rework 2 (user: "you made the model too wide and bulky, also wildmutt doesnt have a tail, the omnitrix badge doesnt look
// good, can you try to copy this model please"): rebuilt after the user's reference -- a blocky, slim and LEGGY beast, all
// mottled orange fur. A narrow torso on long legs, the back ~1.45 blocks up at the shoulders and sloping down to the hips,
// NO tail. A big boxy head carried low and forward (its top level with the shoulders) on a short thick neck; no eyes, a black
// mouth band wrapped round the front and sides a third of the way up, chunky cream teeth of uneven heights standing over
// it, gill slits behind it, and small fin-like fur tufts down the top of the head and the spine. Long front legs (the upper
// arm angled down and back, the forearm down and forward), dog-like bent hind legs (thigh forward, shin back, hock), flat
// paws with a raised wrist step and three dark square claws each. The Omnitrix is a chunky navy cuff round the LEFT upper
// front leg with a pale grey-blue rim and a dark square plate on the outside (the renderer draws the white symbol on it).
//
// Model pixels are real 1/16-block pixels, drawn 1:1 (WildmuttRenderer.MODEL_SCALE): ~23.6 px to the shoulders, 38 px nose
// to rump. Bedrock / GeckoLib conventions: +Y up, the model faces -Z, the entity's RIGHT side is -X.
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
const { Model, mix, mul, fbm, clamp, hash3 } = L;

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
// a small fin-like fur tuft: a short blade, thinner across x than along the body, and a smaller tip
function tuft(b, from, dir, len, w, seed) {
	const d = L.vnorm(dir);
	M.stick(b, from, d, len * 0.62, [w * 0.55, w], 'tuft', { seed });
	M.stick(b, [from[0] + d[0] * len * 0.5, from[1] + d[1] * len * 0.5, from[2] + d[2] * len * 0.5], d, len * 0.5, [w * 0.4, w * 0.6], 'tuftTip', { seed: seed + 1 });
}

// ================================================================ the rig
M.bone('root', null, [0, 0, 0]);
M.bone('spin', 'root', [0, 13, 0]);          // the Quill Roll turns the whole ball about here
M.bone('hurt', 'spin', [0, 15, 2]);          // the flinch layer (its own controller; nothing else keys it)
const hips = M.bone('hips', 'hurt', [0, 17, 3]);
const chest = M.bone('chest', 'hips', [0, 17, 1]);
const neck = M.bone('neck', 'chest', [0, 18, -8.5]);
M.bone('look', 'neck', [0, 18.5, -12]);      // where the player looks (set in code, never keyed)
const head = M.bone('head', 'look', [0, 18.5, -12]);
const jaw = M.bone('jaw', 'head', [0, 16.4, -12.5]);
const quillsF = M.bone('quills_f', 'chest', [0, 23.6, -3]);
const quillsB = M.bone('quills_b', 'hips', [0, 21.6, 8]);

// ---- the hips: a tucked waist and a narrow rump, the back sloping down to it (no tail)
M.box(hips, -4.2, 14.2, 1.5, 4.2, 21.6, 8.2, 'fur', { seed: 1 });                 // waist
M.box(hips, -4.4, 13.0, 7.6, 4.4, 20.4, 14.3, 'fur', { seed: 2 });                // rump
M.box(hips, -3.8, 20.4, -0.5, 3.8, 22.4, 14.0, 'fur', { seed: 3, rot: [-9, 0, 0], pivot: [0, 22.4, -0.5] }); // the sloping back
M.box(hips, -3.4, 13.6, 2.0, 3.4, 14.4, 8.0, 'belly', { seed: 4 });               // the tucked belly
// ---- the chest: deep and narrow, the shoulders at the top
M.box(chest, -4.6, 12.4, -9.6, 4.6, 22.6, 2.8, 'fur', { seed: 10 });
M.box(chest, -4.0, 22.4, -8.8, 4.0, 23.6, 1.6, 'fur', { seed: 11 });              // the withers
M.box(chest, -3.8, 11.7, -8.4, 3.8, 12.6, 0.6, 'belly', { seed: 14 });            // the chest underside
// ---- the neck: short and thick, gill slits on its sides
M.box(neck, -3.9, 14.0, -13.6, 3.9, 21.6, -8.0, 'neck', { seed: 20 });
// ---- the head: a big box, no eyes; the black band at mouth level, the lower jaw hinged at the back
M.box(head, -5.2, 16.4, -23.2, 5.2, 23.4, -11.4, 'skull', { seed: 30 });
M.box(head, -5.3, 16.4, -23.8, 5.3, 17.3, -14.4, 'band', { tag: 'upper' });     // the band's upper half
M.box(jaw, -5.0, 13.2, -23.6, 5.0, 16.4, -11.8, 'fur', { seed: 40 });
M.box(jaw, -5.3, 15.6, -23.8, 5.3, 16.4, -14.4, 'band', { tag: 'lower' });       // the band's lower half
// (the inside of the mouth is painted on the skull's underside and the jaw's top: no extra plates to z-fight the band)
// the teeth: a chunky cream row of uneven heights standing over the band at the front, two more round each corner
const TEETH = [18.3, 19.3, 18.6, 19.5, 18.4, 19.1];
for (let i = 0; i < TEETH.length; i++) {
	const x0 = -4.8 + i * 1.65;
	M.box(jaw, x0, 16.9, -24.15, x0 + 1.35, TEETH[i], -23.1, 'tooth', { seed: 32 + i });
}
for (const s of S) {
	sbox(jaw, s, 5.0, 5.95, 16.9, 18.5, -22.9, -21.6, 'tooth', { seed: 38 });
	sbox(jaw, s, 5.0, 5.85, 16.9, 18.0, -20.9, -19.8, 'tooth', { seed: 39 });
}
// ---- the fur tufts: little fins down the top of the head and the spine, leaning back
for (const [z, len] of [[-21.4, 2.0], [-18.8, 2.6], [-16.2, 2.4], [-13.6, 2.2]]) tuft(head, [0, 23.1, z], [0, 0.75, 0.66], len, 1.5, 50 + Math.round(-z));
for (const [x, z, len] of [[0, -9.6, 2.2], [0, -7.0, 2.6], [0, -4.4, 2.8], [0, -1.8, 2.6], [0, 0.8, 2.4], [-1.9, -5.6, 1.7], [1.9, -3.0, 1.7], [-1.9, -0.4, 1.6]]) {
	tuft(quillsF, [x, x === 0 ? 23.3 : 23.0, z], [x * 0.12, 0.72, 0.7], len, 1.5, 70 + Math.round(-z * 2 + x));
}
for (const [z, len] of [[3.2, 2.4], [6.0, 2.2], [8.8, 2.0], [11.6, 1.8]]) {
	const y = 22.4 - (z + 0.5) * Math.sin(9 * DEG) - 0.3;
	tuft(quillsB, [0, y, z], [0, 0.66, 0.75], len, 1.4, 90 + Math.round(z));
}

// ---- the front legs: long; the upper arm down and back from the shoulder, the forearm down and forward to the paw
const FX = 6.0;                                  // the front legs' centre line (|x|)
const UA = 14, UL = 11.2, FA = -20;              // upper arm angle / length, forearm angle
const SHOULDER_Y = 20.5, SHOULDER_Z = -5, WRIST_Y = 3.2;
const ELBOW = end([0, SHOULDER_Y, SHOULDER_Z], UL, UA);
const FL = (ELBOW[1] - WRIST_Y) / Math.cos(FA * DEG);
const WRIST = end(ELBOW, FL, FA);
for (const s of S) {
	const n = nm(s);
	const sh = [s * FX, SHOULDER_Y, SHOULDER_Z];
	const arm = M.bone(`arm_${n}`, 'chest', sh);
	limb(arm, sh, UL + 0.8, 2.0, UA, 2.0, 2.4, 'fur', 80);                    // the upper arm
	limb(arm, sh, 3.0, 2.6, UA, 2.25, 2.75, 'fur', 81);                         // the shoulder
	if (s > 0) { // the Omnitrix cuff, near the shoulder, tipped with the upper arm (its own bone, so the face can sit on it)
		const badge = M.bone('badge', `arm_${n}`, sh, [UA, 0, 0]);
		M.box(badge, FX - 2.25, 15.4, SHOULDER_Z - 2.65, FX + 2.25, 18.2, SHOULDER_Z + 2.65, 'cuff');
		M.box(badge, FX - 2.4, 14.7, SHOULDER_Z - 2.8, FX + 2.4, 15.5, SHOULDER_Z + 2.8, 'rim');
		M.box(badge, FX + 2.2, 15.0, SHOULDER_Z - 1.8, FX + 2.7, 18.6, SHOULDER_Z + 1.8, 'plate');
	}
	const el = [s * FX, ELBOW[1], ELBOW[2]];
	const fa = M.bone(`forearm_${n}`, `arm_${n}`, el);
	limb(fa, el, FL + 0.6, 1.0, FA, 1.8, 2.1, 'fur', 84);
	const wr = [s * FX, WRIST[1], WRIST[2]];
	const paw = M.bone(`paw_${n}`, `forearm_${n}`, wr);
	sbox(paw, s, FX - 2.1, FX + 2.1, 0, 2.0, WRIST[2] - 4.6, WRIST[2] + 2.0, 'fur', { seed: 88 });          // the flat paw
	sbox(paw, s, FX - 1.9, FX + 1.9, 1.9, 3.7, WRIST[2] - 0.8, WRIST[2] + 2.2, 'fur', { seed: 89 });        // the raised wrist step
	for (let i = -1; i <= 1; i++) {                                                                          // three dark claws
		sbox(paw, s, FX + i * 1.45 - 0.58, FX + i * 1.45 + 0.58, 0, 1.3, WRIST[2] - 5.7, WRIST[2] - 4.4, 'claw', { seed: 94 + i });
	}
}

// ---- the hind legs: dog-like, a little shorter -- the thigh forward, the shin back to the hock, the same paws
const HX = 4.6;
const HIP = [0, 17.5, 10], TA = -24, TL = 8.4, SA = 34, SL = 6.6;
const KNEE = end(HIP, TL, TA);
const HOCK = end(KNEE, SL, SA);
for (const s of S) {
	const n = nm(s);
	const hp = [s * HX, HIP[1], HIP[2]];
	const th = M.bone(`thigh_${n}`, 'hips', hp);
	sbox(th, s, HX - 2.4, HX + 2.4, 12.6, 20.0, 6.6, 13.8, 'fur', { seed: 100 });   // the haunch
	limb(th, hp, TL + 0.6, 1.0, TA, 2.1, 2.6, 'fur', 101);                          // the thigh
	const kn = [s * HX, KNEE[1], KNEE[2]];
	const sh = M.bone(`shin_${n}`, `thigh_${n}`, kn);
	limb(sh, kn, SL + 0.5, 0.9, SA, 1.7, 1.9, 'fur', 102);
	const hk = [s * HX, HOCK[1], HOCK[2]];
	const ft = M.bone(`foot_${n}`, `shin_${n}`, hk);
	limb(ft, hk, HOCK[1] - 1.5, 0.6, -8, 1.6, 1.7, 'fur', 104);                     // the hock down to the paw
	sbox(ft, s, HX - 2.0, HX + 2.0, 0, 1.9, HOCK[2] - 5.4, HOCK[2] + 1.2, 'fur', { seed: 105 });
	sbox(ft, s, HX - 1.8, HX + 1.8, 1.8, 3.2, HOCK[2] - 2.0, HOCK[2] + 1.4, 'fur', { seed: 106 });
	for (let i = -1; i <= 1; i++) {
		sbox(ft, s, HX + i * 1.4 - 0.55, HX + i * 1.4 + 0.55, 0, 1.2, HOCK[2] - 6.4, HOCK[2] - 5.2, 'claw', { seed: 108 + i });
	}
}

M.ground();

// ================================================================ paint
const C = {
	fur: [243, 154, 42], furHi: [255, 190, 92], furLo: [214, 116, 26], furDeep: [182, 92, 20], outline: [170, 84, 18],
	belly: [248, 172, 78], tuftTip: [255, 196, 104],
	claw: [74, 58, 46], clawHi: [112, 92, 74],
	band: [20, 18, 18], tooth: [238, 226, 192], toothLo: [196, 182, 146],
	mouth: [96, 24, 30], tongue: [190, 82, 98],
	cuff: [26, 30, 48], cuffHi: [48, 56, 82], rim: [169, 196, 208], rimLo: [128, 154, 168], plate: [14, 16, 24], plateEdge: [64, 74, 96],
	gill: [120, 52, 14],
};
// the mottling: soft blotches plus a sparse speckle of lighter and darker texels (blocky, like the reference's fur)
function mottle(col, p, seed) {
	const blot = fbm(p[0] * 0.32 + seed * 0.7, p[1] * 0.32, p[2] * 0.32, 3);
	col = mix(col, blot > 0.5 ? C.furHi : C.furLo, Math.abs(blot - 0.5) * 0.9);
	const q = [Math.floor(p[0] * 3 + 0.01), Math.floor(p[1] * 3 + 0.01), Math.floor(p[2] * 3 + 0.01)];
	const h = hash3(q[0] + seed * 31, q[1], q[2]);
	if (h > 0.9) col = mix(col, C.furHi, 0.55);
	else if (h < 0.08) col = mix(col, C.furDeep, 0.45);
	else if (h < 0.16) col = mix(col, C.furLo, 0.4);
	return col;
}
function shade(c, f, s, t, p, info) {
	const e = info.ePx;
	switch (c.mat) {
		case 'claw': {
			const k = f === 'up' ? 0.5 : f === 'down' ? 0 : 0.15;
			return [mix(C.claw, C.clawHi, e < 0.22 ? k + 0.25 : k), 255, null];
		}
		case 'tooth': {
			let col = C.tooth;
			if (f === 'down' || f === 'south') col = C.toothLo;
			else if (e < 0.2) col = mix(C.tooth, C.toothLo, 0.6);
			const h = hash3(Math.floor(p[0] * 3), Math.floor(p[1] * 3), c.seed);
			if (h < 0.15) col = mix(col, C.toothLo, 0.5);
			return [col, 255, null];
		}
		case 'mouth': {
			if (c.tag === 'tongue' && f === 'up' && Math.abs(p[0]) < 3.0 && p[2] > -21.5) return [C.tongue, 255, null];
			return [mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
		}
		case 'band': return [C.band, 255, null]; // all black (a red inner face bled a red line along the closed mouth)
		case 'cuff': {
			if (e < 0.3) return [C.cuffHi, 255, null];
			return [f === 'up' ? C.cuffHi : C.cuff, 255, null];
		}
		case 'rim': return [f === 'down' || e < 0.2 ? C.rimLo : C.rim, 255, null];
		case 'plate': return [e < 0.3 ? C.plateEdge : C.plate, 255, null];
		default: { // fur, skull, neck, belly, tuft, tuftTip
			// the inside of the mouth: the roof (the skull's underside) and the jaw's top with the tongue
			// (inset from the faces' edges: the rims stay dark, so nothing red shows along the closed mouth)
			if (c.mat === 'skull' && f === 'down') return [e < 0.4 ? C.band : mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
			if (c.bone === 'jaw' && c.mat === 'fur' && f === 'up') {
				if (e < 0.4) return [C.band, 255, null];
				if (Math.abs(p[0]) < 3.0 && p[2] > -21.5 && p[2] < -13) return [C.tongue, 255, null];
				return [mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
			}
			let col;
			if (c.mat === 'belly') col = C.belly;
			else if (c.mat === 'tuftTip') col = mix(C.fur, C.tuftTip, 0.4 + 0.5 * (1 - t));
			else if (c.mat === 'tuft') col = mix(C.furLo, C.fur, 0.5 + 0.5 * (1 - t));
			else col = C.fur;
			// lit from above: a little darker low on the legs and under the body, lighter on top
			const lift = clamp((p[1] - 1) / 22, 0, 1);
			col = mix(C.furLo, col, 0.6 + 0.4 * lift);
			if (f === 'down') col = mul(col, 0.8);
			if (f === 'up') col = mix(col, C.furHi, 0.22);
			col = mottle(col, p, c.seed);
			// the gill slits: three dark slits on each side of the head, behind the mouth band, and two on the neck
			if ((f === 'east' || f === 'west') && (c.mat === 'skull' || c.mat === 'neck' || (c.bone === 'jaw' && c.mat === 'fur'))) {
				const zs = c.mat === 'neck' ? [-12.2, -10.6] : [-13.8, -12.6];
				for (const zc0 of zs) {
					const zc = zc0 + (p[1] - 16) * 0.18;
					const top = c.mat === 'neck' ? 19.6 : 20.2, bot = c.mat === 'neck' ? 15.4 : 14.6;
					if (Math.abs(p[2] - zc) < 0.3 && p[1] > bot && p[1] < top) col = mix(col, C.gill, 0.85);
				}
			}
			// a soft darker rim on every face (separates the blocks, like the reference's outlined boxes)
			if (e < 0.3) col = mix(col, C.outline, 0.35 * (1 - e / 0.3));
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
