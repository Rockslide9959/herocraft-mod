// v0.17.1: the HARD-LIGHT MECH -- Green Lantern's ultimate construct, of translucent GL-green hard light: bright edge
// lines, a darker inner frame showing through the plates, the pilot seated in a cockpit inside the chest (visible).
// v0.17.1 Gurren rework (user: "make the green lantern mech look cooler please, make it slightly taller and a bit thinner
// please, make it look like gurren lagann"): an original hard-light take on the drill-mech style -- ~7.5 blocks (was
// 6.7), long legs, a narrow waist and slimmer limbs under broad spiky pauldrons; the chest IS a huge grinning face (fanged
// mouth with a hinged jaw, angry slanted eyes and brows, the Lantern emblem as its forehead gem round the cockpit glass);
// a separate bright V-shaped "shades" crest across the upper chest out over the shoulders; a small helmeted head with a
// drill horn; spiral-grooved drill cones on the knees and elbows; spiky forearm guards; pointed toes.
// Model pixels are real 1/16-block pixels (no render scale). Bedrock/GeckoLib conventions: +Y up, the model faces -Z,
// the mech's RIGHT side is -X.
//
// Writes:
//   src/main/resources/assets/projecthero/geo/green_lantern_mech.geo.json
//   src/main/resources/assets/projecthero/textures/entity/green_lantern/mech.png          (translucent base)
//   src/main/resources/assets/projecthero/textures/entity/green_lantern/mech_glow.png     (additive edge glow)
//   src/main/resources/assets/projecthero/animations/green_lantern_mech.animation.json
//   docs/screenshots/v0171/gl_gurren_sheet.png (+ gl_gurren_poses*.png)
// Usage: node scratchpad/gl_mech_v0171/gen_gl_mech.js [--no-sheet]
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { Model, mix, mul, fbm, clamp, vnorm, vadd, vsc } = L;

const ROOT = path.join(__dirname, '..', '..');
const A = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero');
const mk = d => (fs.mkdirSync(d, { recursive: true }), d);
const writeJson = (f, o) => { mk(path.dirname(f)); fs.writeFileSync(f, JSON.stringify(o, null, '\t') + '\n'); };
const writePng = (f, w, h, px) => { mk(path.dirname(f)); fs.writeFileSync(f, L.encodePng(w, h, px)); };

const M = new Model('green_lantern_mech', { TW: 1024, D: 2 });
const S = [-1, 1];
const nm = s => (s < 0 ? 'r' : 'l');
// box by x range given as |x| (outer side positive), mirrored by s
const sx = (s, a, b) => [s * a, s * b];
function sbox(b, s, xa, xb, y0, y1, z0, z1, mat, opts) {
	const [x0, x1] = sx(s, xa, xb);
	return M.box(b, x0, y0, z0, x1, y1, z1, mat, opts);
}

// v0.17.1 polish: a small Lantern emblem (octagonal ring + two bars) on a face. 'front' faces -Z at z (depth into +Z),
// 'side' faces outward along s*X at x. R = ring radius, t = ring thickness, bw = bar half-width.
function emblem(b, kind, c, R, t, bw, depth, s) {
	const hw = R * 0.57;
	if (kind === 'front') {
		const z = c[2];
		for (let i = 0; i < 8; i++) {
			M.box(b, c[0] - hw, c[1] + R - t, z, c[0] + hw, c[1] + R, z + depth, 'emblem', { rot: [0, 0, i * 45], pivot: c.slice() });
		}
		if (bw > 0) {
			M.box(b, c[0] - bw, c[1] + R + 0.15 * R, z, c[0] + bw, c[1] + R + 0.15 * R + t, z + depth, 'emblem');
			M.box(b, c[0] - bw, c[1] - R - 0.15 * R - t, z, c[0] + bw, c[1] - R - 0.15 * R, z + depth, 'emblem');
		}
	} else {
		const x = c[0];
		const [xa, xb] = s > 0 ? [x - depth, x] : [x, x + depth];
		for (let i = 0; i < 8; i++) {
			M.box(b, xa, c[1] + R - t, c[2] - hw, xb, c[1] + R, c[2] + hw, 'emblem', { rot: [i * 45, 0, 0], pivot: c.slice() });
		}
		M.box(b, xa, c[1] + R + 0.15 * R, c[2] - bw, xb, c[1] + R + 0.15 * R + t, c[2] + bw, 'emblem');
		M.box(b, xa, c[1] - R - 0.15 * R - t, c[2] - bw, xb, c[1] - R - 0.15 * R, c[2] + bw, 'emblem');
	}
}

// v0.17.1 Gurren rework: a tapered spike -- stacked prisms along dir, each thinner than the last, to a point.
// w0 = base width (across, in the stick's local x), d0 = base depth.
function spike(b, base, dir, len, w0, d0, n, mat, seed) {
	const d = vnorm(dir);
	const out = [];
	for (let i = 0; i < n; i++) {
		const k = i / n;
		const from = vadd(base, vsc(d, len * k - (i > 0 ? 0.3 : 0)));
		const f = 1 - k * 0.82;
		out.push(M.stick(b, from, d, len / n + (i > 0 ? 0.3 : 0), [Math.max(0.5, w0 * f), Math.max(0.5, d0 * f)], mat || 'plate', { seed: (seed || 0) + i }));
	}
	return out;
}

// v0.17.1 Gurren rework: a drill -- a stepped cone of square sections, each painted with a two-start helical groove
// that runs continuously round and along the whole cone (the 'drill' material reads c.drill: the section's offset along
// the axis), with a collar ring at the base.
function drill(b, base, dir, len, r0, n, seed) {
	const d = vnorm(dir);
	M.stick(b, vadd(base, vsc(d, -1.2)), d, 1.6, [2 * r0 + 1.4, 2 * r0 + 1.4], 'plate', { seed: seed || 0 });
	for (let i = 0; i < n; i++) {
		const k = i / n;
		const off = len * k - (i > 0 ? 0.25 : 0);
		const w = Math.max(0.55, 2 * r0 * (1 - k * 0.92));
		const c = M.stick(b, vadd(base, vsc(d, off)), d, len / n + (i > 0 ? 0.25 : 0), [w, w], 'drill', { seed: (seed || 0) + i + 1 });
		c.drill = { off, len };
	}
}

// ================================================================ geometry
M.bone('root', null, [0, 0, 0]);
const pelvis = M.bone('pelvis', 'root', [0, 56, 0]);
M.cbox(pelvis, [0, 55, 0], [14, 10, 10], 'frame');
M.box(pelvis, -8.5, 48, -8.5, 8.5, 58, -5.5, 'plate', { seed: 1 });         // hip armour front
M.box(pelvis, -4, 43.5, -8, 4, 49, -5.5, 'plate', { seed: 2 });             // crotch plate
M.cbox(pelvis, [0, 43.8, -6.8], [4.2, 4.2, 2.4], 'plate', { rot: [0, 0, 45], pivot: [0, 43.8, -6.8], seed: 3 }); // its point
M.box(pelvis, -10.5, 57, -7.5, 10.5, 61, 7.5, 'plate', { seed: 4 });        // belt
M.box(pelvis, -8, 46, 5.5, 8, 58, 8.5, 'plate', { seed: 5 });               // rear skirt
for (const s of S) { // side skirts, flared out
	sbox(pelvis, s, 9.5, 14, 44, 58, -6, 6, 'plate', { rot: [0, 0, -s * 14], pivot: [s * 9.5, 58, 0], seed: 6 });
	sbox(pelvis, s, 13.7, 14.4, 47, 55, -3.5, 3.5, 'glow', { rot: [0, 0, -s * 14], pivot: [s * 9.5, 58, 0] });
	// front hip plates angled out over the thighs, a vent in each
	sbox(pelvis, s, 3.5, 10, 46, 56, -10, -8.2, 'plate', { rot: [-8, s * 10, 0], pivot: [s * 3.5, 56, -8.5], seed: 7 });
	sbox(pelvis, s, 5, 8.5, 48, 53.5, -10.5, -9.8, 'vent', { rot: [-8, s * 10, 0], pivot: [s * 3.5, 56, -8.5] });
}
// the Lantern emblem as the belt buckle
emblem(pelvis, 'front', [0, 58.4, -8.4], 2.3, 0.9, 3.6, 0.9);

for (const s of S) {
	const n = nm(s);
	// ---- thigh (long and slim)
	const leg = M.bone(`leg_${n}`, 'pelvis', [s * 10, 53, 0]);
	M.cbox(leg, [s * 10, 53, 0], [7, 7, 7], 'frame');
	sbox(leg, s, 8, 12, 37, 53, -2.5, 2.5, 'frame');
	sbox(leg, s, 6.5, 13.5, 39, 51.5, -5, 4.5, 'plate', { seed: 10 });
	sbox(leg, s, 7.2, 12.8, 40, 50.5, -6.6, -4.8, 'plate', { rot: [4, 0, 0], pivot: [s * 10, 50.5, -5], seed: 11 });
	sbox(leg, s, 9.6, 10.4, 41, 49.5, -7, -6.5, 'glow', { rot: [4, 0, 0], pivot: [s * 10, 50.5, -5] });
	sbox(leg, s, 13.3, 14.6, 41, 50, -3, 2.5, 'plate', { seed: 12 });        // outer thigh plate
	sbox(leg, s, 14.4, 14.8, 42.5, 48.5, -0.5, 0.5, 'glow');
	M.stick(leg, [s * 10, 50, 4.2], [0, -1, 0.1], 11, [2, 2], 'frame', { seed: 13 }); // hydraulic at the back
	// ---- shin
	const shin = M.bone(`shin_${n}`, `leg_${n}`, [s * 10, 37, -1]);
	M.cbox(shin, [s * 10, 37, -1], [6, 6, 6], 'frame');
	sbox(shin, s, 6.5, 13.5, 31, 40, -7.5, -3.5, 'plate', { rot: [-10, 0, 0], pivot: [s * 10, 37, -5], seed: 14 }); // knee pad
	// the knee drill: a spiral cone pointing forward off the kneecap
	drill(shin, [s * 10, 36, -7.6], [0, 0.3, -1], 8, 2.9, 5, 15);
	sbox(shin, s, 8, 12, 10, 34, -2.5, 2.5, 'frame');
	sbox(shin, s, 6.8, 13.2, 14, 32, -5.5, 3.5, 'plate', { seed: 21 });
	sbox(shin, s, 6, 14, 8.5, 14.5, -6.5, 4.5, 'plate', { seed: 22 });        // ankle flare
	sbox(shin, s, 7.5, 12.5, 16, 30.5, 3, 7, 'plate', { seed: 23 });          // calf thruster housing
	sbox(shin, s, 8.5, 11.5, 14.5, 16.2, 3.6, 6.5, 'glow');                   // calf nozzle
	sbox(shin, s, 9.4, 10.6, 16, 30, -5.9, -5.5, 'glow');                     // shin light strip
	// swept shin fins on the outside, a vent slot in each
	sbox(shin, s, 13, 15.6, 14, 32, -4, 2.5, 'plate', { rot: [0, 0, -s * 6], pivot: [s * 13.5, 32, 0], seed: 24 });
	sbox(shin, s, 15.4, 16, 17, 28, -2.5, 1, 'vent', { rot: [0, 0, -s * 6], pivot: [s * 13.5, 32, 0] });
	// a spike off the top of each shin fin, raking up and back
	spike(shin, [s * 14.6, 31, -0.5], [s * 0.35, 1, 0.55], 6, 2.4, 2.4, 3, 'plate', 25);
	// ---- foot (pointed toe)
	const foot = M.bone(`foot_${n}`, `shin_${n}`, [s * 10, 8, 0]);
	M.cbox(foot, [s * 10, 8, 0], [5, 5, 5], 'frame');
	sbox(foot, s, 5.5, 14.5, 0, 5.5, -8, 6, 'plate', { seed: 30 });
	sbox(foot, s, 6.2, 13.8, 0, 4.5, -13, -7.5, 'plate', { seed: 31 });     // toe
	M.cbox(foot, [s * 10, 2, -13], [5.4, 4, 5.4], 'plate', { rot: [0, 45, 0], pivot: [s * 10, 2, -13], seed: 32 }); // toe point
	M.cbox(foot, [s * 10, 0.6, -13], [5.8, 1.2, 5.8], 'glow', { rot: [0, 45, 0], pivot: [s * 10, 0.6, -13] });
	sbox(foot, s, 7.5, 12.5, 0, 4.5, 5.5, 10, 'plate', { seed: 33 });       // heel spur
	sbox(foot, s, 5, 15, 5, 9, -6.5, 5, 'plate', { seed: 34 });             // ankle guard
	sbox(foot, s, 6.6, 13.4, 4.2, 5.6, -12.5, -7.5, 'plate', { rot: [-10, 0, 0], pivot: [s * 10, 5.5, -7.5], seed: 35 }); // toe cap
	sbox(foot, s, 9.2, 10.8, 5.4, 6, -12, -8, 'glow', { rot: [-10, 0, 0], pivot: [s * 10, 5.5, -7.5] });
}

// ---- torso: a narrow waist under a broad chest, and the chest is a FACE
const torso = M.bone('torso', 'pelvis', [0, 62, 1]);
M.box(torso, -7, 60, -5, 7, 69, 5, 'frame');                               // waist core
for (let i = 0; i < 3; i++) M.box(torso, -6.5 + i * 0.3, 60.5 + i * 2.8, -7.6, 6.5 - i * 0.3, 63 + i * 2.8, -5, 'plate', { seed: 40 + i }); // abs
M.box(torso, -0.5, 60.5, -7.95, 0.5, 69, -7.55, 'glow');
M.box(torso, -9, 64, -10, 9, 66, 6, 'frame');                              // cockpit floor / seat base
M.box(torso, -10, 98, -10, 10, 100.5, 6, 'plate', { seed: 43 });           // cockpit ceiling
M.box(torso, -13, 66, 6, 13, 82, 11.5, 'plate', { seed: 44 });             // back armour (lower, narrow)
M.box(torso, -19.5, 81, 6, 19.5, 100, 12, 'plate', { seed: 45 });          // back armour (upper, broad)
M.box(torso, -8, 66, 5, 8, 97, 6, 'frame');                                // back wall (inside)
M.cbox(torso, [0, 84, 5.0], [5, 16, 0.6], 'glow');                         // core light behind the pilot
for (const s of S) {
	sbox(torso, s, 9, 14.5, 66, 81, -9.5, 6, 'plate', { seed: 46 });                                             // cockpit side wall
	sbox(torso, s, 9, 19.5, 80, 100, -10.5, 6, 'plate', { seed: 47 });
	sbox(torso, s, 14.2, 20, 66, 82, -6, 8, 'plate', { rot: [0, 0, -s * 22], pivot: [s * 14.2, 82, 0], seed: 48 }); // V-taper flank
	sbox(torso, s, 19.8, 20.6, 70, 80, -4, 5, 'vent', { rot: [0, 0, -s * 22], pivot: [s * 14.2, 82, 0] });
}
M.box(torso, -9, 99.5, -7, 9, 103, 7, 'plate', { seed: 49 });              // collar

// the FACE: lower chest = the mouth housing, upper chest = the eyes and brows
M.box(torso, -14.5, 69, -12.2, 14.5, 84.5, -9, 'plate', { seed: 50 });     // mouth housing
for (const s of S) {
	// cheeks: chamfered plates either side of the mouth, swept back
	sbox(torso, s, 12.5, 18, 68.5, 85, -12, -8, 'plate', { rot: [0, s * 20, 0], pivot: [s * 12.5, 76, -12], seed: 51 });
}
// the mouth: a dark recess, wide, its corners rising into a grin
M.box(torso, -9.5, 72.2, -12.9, 9.5, 80.8, -12.15, 'mouth');
for (const s of S) {
	sbox(torso, s, 8.5, 14.2, 73.5, 81, -12.9, -12.15, 'mouth', { rot: [0, 0, -s * 24], pivot: [s * 8.5, 77, -12.5] });
}
// the upper lip and its rising corners (in front, so the upper fangs hang out from under it)
M.box(torso, -10, 80.6, -13.7, 10, 84, -12.2, 'plate', { seed: 52 });
for (const s of S) {
	sbox(torso, s, 9.2, 16.2, 80.2, 83.4, -13.7, -12.2, 'plate', { rot: [0, 0, -s * 24], pivot: [s * 9.2, 81.8, -13] , seed: 53 });
}
// upper fangs: diamonds on the lip line (the lip hides their top halves, so they read as triangles)
for (let i = -3; i <= 3; i++) {
	M.cbox(torso, [i * 2.75, 80.9, -12.75], [2.6, 2.6, 0.7], 'teeth', { rot: [0, 0, 45], pivot: [i * 2.75, 80.9, -12.75] });
}
for (const s of S) { // the big canines at the grin's corners
	M.cbox(torso, [s * 10.6, 81.5, -12.75], [3.0, 3.0, 0.7], 'teeth', { rot: [0, 0, 45 + s * 8], pivot: [s * 10.6, 81.5, -12.75] });
}
// the eyes: angry slants (outer ends up) in recessed sockets, a dark slit pupil in each
for (const s of S) {
	sbox(torso, s, 3.4, 16.2, 83.8, 92.6, -12.95, -11.8, 'mouth', { rot: [0, 0, -s * 14], pivot: [s * 3.6, 88, -12.4] }); // dark socket (contrast for the eye)
	sbox(torso, s, 4.4, 15.4, 85, 91.4, -13.5, -12.85, 'eye', { rot: [0, 0, -s * 14], pivot: [s * 3.6, 88, -12.4] });
	sbox(torso, s, 9.8, 11.8, 85.8, 90.6, -13.75, -13.4, 'mouth', { rot: [0, 0, -s * 14], pivot: [s * 3.6, 88, -12.4] });
	// heavy brows, low at the middle, raking up and out (a frown)
	sbox(torso, s, 1.8, 17.5, 92, 95.4, -14.6, -12, 'plate', { rot: [0, 0, -s * 21], pivot: [s * 1.8, 92.6, -13], seed: 55 });
	sbox(torso, s, 3, 16.5, 91.8, 92.5, -14.75, -14.3, 'glow', { rot: [0, 0, -s * 21], pivot: [s * 1.8, 92.6, -13] });
}
M.box(torso, -20, 92, -12, 20, 100, -8, 'plate', { seed: 56 });            // upper chest (behind the brows)
for (const s of S) {
	sbox(torso, s, 15, 21.5, 82, 100, -12, -7.5, 'plate', { rot: [0, s * 16, 0], pivot: [s * 15, 90, -12], seed: 57 });
	sbox(torso, s, 13, 19.5, 98.8, 99.6, -12.7, -12.1, 'glow');
}
// the forehead gem: the Lantern emblem round the cockpit glass, between the eyes
const EC = [0, 88.4, -13.6];
emblem(torso, 'front', [EC[0], EC[1], -14.4], 3.3, 1.0, 0, 1.0);
M.cbox(torso, [0, EC[1], -13.4], [4.6, 2.6, 0.6], 'glass');
M.cbox(torso, [0, EC[1], -13.4], [2.6, 4.6, 0.6], 'glass');
M.cbox(torso, [0, EC[1], -13.4], [3.8, 3.8, 0.6], 'glass');
for (const c of torso.cubes) if (c.mat === 'glass') c.glassRim = false;
M.box(torso, -1.2, 92.2, -14.9, 1.2, 97.2, -13.6, 'emblem');                // the gem's upper bar (rises to the shades)
M.box(torso, -2.2, 82.6, -14.3, 2.2, 83.8, -13.6, 'emblem');                // and its lower bar (the nose bridge)

// ---- the jaw (hinged: it drops for the Chest Lantern Beam)
const jaw = M.bone('jaw', 'torso', [0, 73, -9]);
M.box(jaw, -10.5, 68.6, -13.7, 10.5, 72.6, -11.8, 'plate', { seed: 58 });  // chin
for (const s of S) {
	sbox(jaw, s, 9.6, 15, 68.9, 72.6, -13.7, -11.8, 'plate', { rot: [0, 0, -s * 22], pivot: [s * 9.6, 72.4, -12.8], seed: 59 });
}
for (let i = -2; i <= 2; i++) { // lower fangs pointing up
	M.cbox(jaw, [i * 3.2 + (i === 0 ? 0 : Math.sign(i) * 0.3), 72.4, -12.6], [2.5, 2.5, 0.7], 'teeth', { rot: [0, 0, 45], pivot: [i * 3.2 + (i === 0 ? 0 : Math.sign(i) * 0.3), 72.4, -12.6] });
}
M.box(jaw, -6, 69.2, -13.95, 6, 69.9, -13.6, 'glow');                      // chin light

// ---- the shades: a bright V-crest from the brow out and up over the shoulders
const shades = M.bone('shades', 'torso', [0, 98, -14]);
for (const s of S) {
	const C0 = [s * 1.0, 97.6, -15.2], T = [s * 38, 111.5, -13.4];
	const dir = [T[0] - C0[0], T[1] - C0[1], T[2] - C0[2]];
	const len = Math.hypot(...dir);
	const ws = [6.2, 5.6, 4.6, 3.4, 2.2, 1.2];
	for (let i = 0; i < ws.length; i++) {
		const k = i / ws.length;
		const from = vadd(C0, vsc(vnorm(dir), len * k - (i > 0 ? 0.35 : 0)));
		M.stick(shades, from, dir, len / ws.length + (i > 0 ? 0.35 : 0), [ws[i], 1.7], 'shades', { seed: 60 + i });
		// the bright rim along its top edge
		const top = vadd(from, [0, ws[i] / 2 * 0.95, 0]);
		M.stick(shades, vadd(top, [-s * 0.3 * ws[i] / 2, 0, -0.95]), dir, len / ws.length + 0.35, [0.7, 0.5], 'emblem', { seed: 70 + i });
	}
}
M.cbox(shades, [0, 97, -15.2], [5.4, 5.4, 1.9], 'shades', { rot: [0, 0, 45], pivot: [0, 97, -15.2] });   // the centre point
M.cbox(shades, [0, 97, -16.2], [2.2, 2.2, 0.5], 'emblem', { rot: [0, 0, 45], pivot: [0, 97, -16.2] });

// ---- backpack + thrusters
const pack = M.bone('thrusters', 'torso', [0, 85, 12]);
M.box(pack, -11, 70, 12, 11, 96, 16.5, 'plate', { seed: 80 });
for (const s of S) {
	sbox(pack, s, 3, 10.5, 68, 95, 16, 22.5, 'plate', { seed: 81 });
	sbox(pack, s, 4, 9.5, 67, 94, 22, 23.5, 'frame');
	sbox(pack, s, 3.8, 10, 63.5, 68.5, 16.5, 22, 'plate', { seed: 82 });      // nozzle bell
	sbox(pack, s, 5, 9, 63, 64.5, 17.5, 21, 'glow');                          // nozzle fire
	sbox(pack, s, 6.3, 7.7, 72, 92, 23.4, 24.4, 'glow');                       // thruster light strip
	// swept back fins, with a drill-tipped spike on top
	M.stick(pack, [s * 10, 92, 17], [s * 0.55, 0.75, 0.42], 18, [2, 6.5], 'plate', { seed: 83 });
	M.stick(pack, [s * 10.2, 92.3, 17.2], [s * 0.55, 0.75, 0.42], 17.5, [0.6, 1.0], 'glow', { seed: 84 });
}

// ---- head: small, helmeted, a drill for a horn
const head = M.bone('head', 'torso', [0, 102, -1]);
M.box(head, -3, 100, -3.5, 3, 105, 2.5, 'frame');                          // neck
M.box(head, -4.6, 104, -5.5, 4.6, 112, 3.5, 'plate', { seed: 90 });        // helmet
M.box(head, -3.6, 104.3, -6.4, 3.6, 107.8, -5.2, 'plate', { seed: 91 });   // face mask
M.box(head, -3.3, 108, -6.35, 3.3, 109.2, -5.6, 'visor');                  // visor slit
M.box(head, -5, 109.4, -6.9, 5, 111.6, -5.2, 'plate', { seed: 92 });       // brow
M.cbox(head, [0, 110.5, -7.1], [1.8, 1.8, 0.6], 'glow');                   // brow gem
M.box(head, -3, 111.5, -4.5, 3, 113, 2.5, 'plate', { seed: 93 });          // crown
for (const s of S) {
	sbox(head, s, 4.4, 5.6, 105, 110.5, -4, 2, 'plate', { seed: 94 });       // cheek vents
	sbox(head, s, 5.5, 5.8, 106, 109.5, -3, 1, 'glow');
	spike(head, [s * 5.2, 109.5, -1.5], [s * 1, 0.85, 0.4], 6.5, 2.6, 1.2, 3, 'plate', 95); // ear fins
	sbox(head, s, 1.6, 4.4, 103.6, 106, -6.8, -5, 'plate', { rot: [0, -s * 14, 0], pivot: [s * 1.6, 105, -6.8], seed: 96 }); // jaw plates
}
drill(head, [0, 112.6, -2.2], [0, 1, 0.22], 8.5, 2.1, 5, 97);              // the drill horn

// ---- shoulders: broad spiky pauldrons, the missile pods behind their spikes
for (const s of S) {
	const n = nm(s);
	const sh = M.bone(`shoulder_${n}`, 'torso', [s * 23, 92, 1]);
	sbox(sh, s, 18, 32, 85, 98, -9, 10, 'plate', { seed: 100 });
	sbox(sh, s, 19, 33.5, 97.5, 100.5, -10, 11, 'plate', { seed: 101 });        // top layer
	sbox(sh, s, 31, 34.8, 82, 97.5, -8, 9, 'plate', { rot: [0, 0, -s * 12], pivot: [s * 32, 97.5, 0], seed: 102 }); // outer flare
	sbox(sh, s, 19, 32, 86, 97, -11, -9, 'plate', { seed: 103 });               // front armour
	sbox(sh, s, 34.4, 35.2, 84, 95, 0, 1.2, 'glow', { rot: [0, 0, -s * 12], pivot: [s * 32, 97.5, 0] });
	emblem(sh, 'front', [s * 25.5, 91.5, -11.8], 2.6, 1.0, 4.2, 0.8);
	// the spikes: a big one raking up and out, one straight out, a small one down the back
	spike(sh, [s * 30, 99.5, -1], [s * 0.6, 1, 0.08], 17, 6, 5, 5, 'plate', 104);
	spike(sh, [s * 33.5, 92.5, -0.5], [s * 1, 0.35, 0.05], 12, 5, 4, 4, 'plate', 110);
	spike(sh, [s * 30, 86.5, 6], [s * 0.6, -0.4, 0.9], 8, 3.4, 3, 3, 'plate', 115);
	const pod = M.bone(`pod_${n}`, `shoulder_${n}`, [s * 23.5, 100.5, 6]);
	sbox(pod, s, 19.5, 27.5, 100.5, 105, -2, 8, 'plate', { seed: 120 });
	for (let i = 0; i < 3; i++) for (let j = 0; j < 2; j++) {
		M.cbox(pod, [s * (21.1 + i * 2.4), 101.8 + j * 2.1, -2.3], [1.8, 1.5, 0.6], 'glow');
	}

	// ---- arms: slimmer, longer
	const arm = M.bone(`arm_${n}`, 'torso', [s * 26.5, 88, 1]);
	M.cbox(arm, [s * 26.5, 88, 1], [8, 8, 8], 'frame');
	sbox(arm, s, 24, 29, 71, 85, -1.5, 3.5, 'frame');
	sbox(arm, s, 23, 30, 73, 84, -3, 5, 'plate', { seed: 130 });
	sbox(arm, s, 22.6, 30.4, 77.4, 78.6, -3.4, 5.4, 'glow');
	sbox(arm, s, 30, 30.6, 79.5, 83, -1.5, 3.5, 'vent');
	const fa = M.bone(`forearm_${n}`, `arm_${n}`, [s * 26.5, 71, 1]);
	M.cbox(fa, [s * 26.5, 71, 1], [7, 6, 7], 'frame');
	drill(fa, [s * 26.5, 71, 4.8], [0, 0.25, 1], 6.5, 2.4, 4, 131);           // elbow drill, pointing back
	sbox(fa, s, 22, 31, 55, 69, -5, 6, 'plate', { seed: 136 });               // forearm
	sbox(fa, s, 21.3, 31.7, 52, 57.5, -6, 7, 'plate', { seed: 137 });         // wrist flare
	sbox(fa, s, 30.8, 33, 55, 68, -3.5, 4.5, 'plate', { seed: 138 });         // outer guard
	sbox(fa, s, 32.8, 33.3, 57, 66, 0, 1.2, 'glow');
	// spiky forearm guard: two big blades raking up, out and back along the outer side
	for (let i = 0; i < 2; i++) {
		spike(fa, [s * 32.4, 56.5 + i * 6.5, 0.5], [s * 0.8, 1, 0.5], 10 - i * 2, 4, 2.4, 4, 'plate', 139 + i * 4);
	}
	sbox(fa, s, 23.5, 29.5, 60.5, 61.6, -5.6, -4.9, 'glow');
	sbox(fa, s, 23.5, 29.5, 63.5, 64.6, -5.6, -4.9, 'glow');
	const fist = M.bone(`fist_${n}`, `forearm_${n}`, [s * 26.5, 51.5, 1]);
	M.cbox(fist, [s * 26.5, 51.5, 1], [8, 3, 8], 'frame');
	sbox(fist, s, 21.5, 31.5, 41, 51.5, -5, 7, 'plate', { seed: 150 });        // palm block
	sbox(fist, s, 21, 32, 42, 50, -8, -5, 'plate', { seed: 151 });             // finger plate
	for (let i = 0; i < 4; i++) M.cbox(fist, [s * (22.9 + i * 2.75), 48.4, -8.5], [2.3, 2.8, 1.4], 'emblem');
	sbox(fist, s, 19.8, 22.3, 43, 48.5, -6.5, -0.5, 'plate', { seed: 152 });   // thumb
}

M.ground();

// ================================================================ paint
const C = {
	plate: [28, 178, 76], plateHi: [90, 230, 130], plateLo: [12, 110, 50],
	// v0.17.1 Gurren rework (user: "maybe remove all the white outlines for the green lantern mech"): no near-white anywhere
	// once built -- the edge lines are a bright saturated green (not the old pale 214/255/226), and every "white-hot" detail
	// (lights, emblem, visor, fangs, eyes, shades, drill grooves) is a pure bright green, so it reads as green hard light
	// without a white wireframe
	edge: [64, 232, 116], edgeGlow: [26, 150, 66], bevel: [70, 218, 116], seam: [10, 92, 44],
	groove: [8, 70, 36], seamGlow: [52, 188, 100], // v0.17.1 polish
	frame: [9, 54, 28], frameEdge: [44, 148, 82],
	glow: [76, 246, 126], glowGlow: [30, 150, 70], hot: [100, 255, 146], hotGlow: [36, 168, 80],
	visor: [120, 255, 164], glass: [120, 240, 170], emblem: [62, 222, 112],
};
// translucency (the in-game texture is drawn translucent + emissive): plates let the inner frame and the pilot show through
const A_PLATE = 150, A_BEVEL = 214, A_EDGE = 255, A_FRAME = 236, A_GLASS = 64;
function shade(c, f, s, t, p, info) {
	const e = info.ePx;
	const u = s * info.fw, v = t * info.fh;
	switch (c.mat) {
		case 'frame': {
			if (e < 0.35) return [C.frameEdge, 255, null];
			const n = fbm(p[0] * 0.5, p[1] * 0.5, p[2] * 0.5, 2);
			return [mul(C.frame, 0.85 + 0.3 * n), A_FRAME, null];
		}
		case 'glow': return [C.glow, 255, C.glowGlow, 255];
		case 'visor': {
			const k = 0.75 + 0.25 * Math.sin(u * 2.2);
			return [mix(C.visor, C.glow, 1 - k), 255, C.hotGlow, 255];
		}
		case 'emblem': {
			if (e < 0.4) return [C.hot, 255, C.hotGlow, 255];
			return [C.emblem, 255, [24, 130, 60], 255];
		}
		case 'glass': {
			if (f !== 'north') return [C.glass, 40, null];
			if (e < 0.3 && c.glassRim) return [C.edge, 255, C.edgeGlow, 255];
			const scan = (Math.floor(v * 2) % 3 === 0) ? 1.15 : 1;
			return [mul(C.glass, scan), A_GLASS, [30, 110, 60], A_GLASS];
		}
		case 'vent': { // v0.17.1 polish: slatted cooling vents, light glowing between the slats
			if (e < 0.35) return [C.edge, 255, C.edgeGlow, 255];
			const slat = Math.floor(v * 2) % 3 !== 0;
			return slat ? [C.frame, 245, null] : [C.glow, 255, [60, 210, 110], 255];
		}
		// ---- v0.17.1 Gurren rework materials
		case 'mouth': { // the mouth / pupils / eye sockets: near-black hard light with a faint glowing inner rim
			if (e < 0.3) return [[50, 184, 98], 255, [24, 124, 60], 255];
			return [[4, 26, 14], 250, null];
		}
		case 'teeth': { // fangs: the brightest green on the body
			if (e < 0.3) return [C.hot, 255, C.hotGlow, 255];
			return [[92, 250, 140], 255, [34, 160, 76], 255];
		}
		case 'eye': { // the chest face's eyes: blazing, a hotter streak along the middle
			const mid = Math.abs(t - 0.5) < 0.18;
			if (e < 0.3) return [C.glow, 255, C.glowGlow, 255];
			return mid ? [C.hot, 255, C.hotGlow, 255] : [[80, 244, 132], 255, [30, 150, 70], 255];
		}
		case 'shades': { // the V-crest: a glassy bright lens with a diagonal sheen and a bright green rim
			if (e < 0.35) return [C.hot, 255, C.hotGlow, 255];
			if (f !== 'north' && f !== 'south') return [[90, 240, 140], 230, [44, 180, 92], 255];
			const sheen = Math.abs(((u * 0.7 + v) % 7) - 3.5) < 0.6;
			const g = clamp(t, 0, 1);
			const col = sheen ? C.hot : mix([112, 255, 160], [50, 206, 110], g);
			return [col, sheen ? 255 : 215, sheen ? C.hotGlow : mix([76, 230, 124], [30, 150, 72], g), 255];
		}
		case 'drill': { // spiral grooves: a two-start helix running round and along the whole cone
			if (e < 0.3) return [C.edge, 255, C.edgeGlow, 255];
			const cx = (c.from[0] + c.to[0]) / 2, cz = (c.from[2] + c.to[2]) / 2;
			const h = (p[1] - c.from[1]) + (c.drill ? c.drill.off : 0);
			const ang = Math.atan2(p[2] - cz, p[0] - cx) / (Math.PI * 2);
			const ph = ((ang * 2 + h / 3.2) % 1 + 1) % 1;
			if (ph < 0.3) return [C.glow, 255, C.glowGlow, 255];
			if (ph < 0.4) return [C.groove, 240, null];
			return [mix(C.plate, C.plateHi, ph), A_BEVEL, null];
		}
		default: { // plate
			if (e < 0.42) return [C.edge, A_EDGE, C.edgeGlow, 255];
			if (e < 0.95) return [C.bevel, A_BEVEL, null];
			const big = info.fw > 6.5 && info.fh > 6.5;
			// v0.17.1 polish: an inset groove round the bigger panels (glowing faintly, like light in the joins) ...
			if (big && e >= 1.45 && e < 1.95) return [C.groove, 236, [22, 108, 56], 255];
			// ... and bright rivets just inside its corners
			if (info.fw > 10 && info.fh > 10) {
				const du = Math.min(u, info.fw - u), dv = Math.min(v, info.fh - v);
				if (Math.abs(du - 3.0) < 0.55 && Math.abs(dv - 3.0) < 0.55) return [C.glow, 255, C.edgeGlow, 255];
			}
			// panel seams every ~8 px on the bigger faces: hard-light joins that glow
			const off = (c.seed * 3.7) % 8;
			const inner = !big || e >= 1.95;
			const seamU = inner && info.fw > 9 && Math.abs(((u + off) % 8) - 4) > 3.72 && u > 2.5 && u < info.fw - 2.5;
			const seamV = inner && info.fh > 9 && Math.abs(((v + off * 0.6) % 8) - 4) > 3.72 && v > 2.5 && v < info.fh - 2.5;
			if (seamU || seamV) return [C.seamGlow, 236, [34, 150, 80], 255];
			// a vertical gradient (lit from above), faint scanlines and a willpower shimmer
			const g = clamp((p[1] - 0) / 118, 0, 1);
			let col = mix(C.plateLo, C.plate, 0.55 + 0.45 * g);
			const n = fbm(p[0] * 0.18 + c.seed, p[1] * 0.18, p[2] * 0.18, 3);
			col = mix(col, C.plateHi, Math.max(0, n - 0.55) * 1.4);
			if (Math.floor(v * 2) % 4 === 0) col = mul(col, 1.08);
			if (f === 'down') col = mul(col, 0.8);
			if (f === 'up') col = mix(col, C.plateHi, 0.18);
			return [col, A_PLATE, null];
		}
	}
}

M.layout();
M.paint(shade);
const b0 = M.bounds(M.world({}));
console.log(`mech: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ` +
	`x ${b0.lo[0].toFixed(1)}..${b0.hi[0].toFixed(1)}, y ${b0.lo[1].toFixed(1)}..${b0.hi[1].toFixed(1)} (${(b0.hi[1] / 16).toFixed(2)} blocks), ` +
	`z ${b0.lo[2].toFixed(1)}..${b0.hi[2].toFixed(1)}`);

const geo = M.geoJson('geometry.green_lantern_mech');
writeJson(path.join(A, 'geo', 'green_lantern_mech.geo.json'), geo);
writePng(path.join(A, 'textures', 'entity', 'green_lantern', 'mech.png'), M.TW, M.TH, M.tex);
writePng(path.join(A, 'textures', 'entity', 'green_lantern', 'mech_glow.png'), M.TW, M.TH, M.glow);
module.exports = { M, ROOT, A, L };

// ================================================================ animations
require('./anims.js')(M, A, ROOT);

// ================================================================ preview sheets
if (!process.argv.includes('--no-sheet')) require('./preview.js')(M, ROOT);
