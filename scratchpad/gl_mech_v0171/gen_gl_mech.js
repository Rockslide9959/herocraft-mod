// v0.17.1: the HARD-LIGHT MECH -- Green Lantern's ultimate construct. A ~6.4-block armoured mech of translucent GL-green
// hard light: bright edge lines, a darker inner frame showing through the plates, a lantern-symbol chest plate round a
// glass cockpit (the pilot sits inside, visible), huge layered pauldrons with missile pods, thick forearms with knuckled
// fists, a backpack with two thrusters and swept fins, heavy armoured legs and a visor head with a V-fin.
// Model pixels are real 1/16-block pixels (no render scale). Bedrock/GeckoLib conventions: +Y up, the model faces -Z,
// the mech's RIGHT side is -X.
//
// Writes:
//   src/main/resources/assets/projecthero/geo/green_lantern_mech.geo.json
//   src/main/resources/assets/projecthero/textures/entity/green_lantern/mech.png          (translucent base)
//   src/main/resources/assets/projecthero/textures/entity/green_lantern/mech_glow.png     (additive edge glow)
//   src/main/resources/assets/projecthero/animations/green_lantern_mech.animation.json
//   docs/screenshots/v0171/gl_mech_sheet.png (+ gl_mech_poses.png)
// Usage: node scratchpad/gl_mech_v0171/gen_gl_mech.js
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { Model, mix, mul, fbm, clamp } = L;

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

// ================================================================ geometry
M.bone('root', null, [0, 0, 0]);
const pelvis = M.bone('pelvis', 'root', [0, 46, 0]);
M.cbox(pelvis, [0, 46, 0], [18, 10, 12], 'frame');
M.box(pelvis, -10, 40, -9.5, 10, 50, -6, 'plate', { seed: 1 });          // hip armour front
M.box(pelvis, -5, 35, -9, 5, 41, -6, 'plate', { seed: 2 });              // crotch plate
M.box(pelvis, -12.5, 49, -8.5, 12.5, 53, 8.5, 'plate', { seed: 3 });     // belt
M.box(pelvis, -6, 50, -9.1, 6, 52, -8.5, 'glow');                         // belt light
M.box(pelvis, -10, 37, 6, 10, 50, 9.5, 'plate', { seed: 4 });            // rear skirt
for (const s of S) { // side skirts, flared out
	sbox(pelvis, s, 12, 19, 34, 50, -7, 7, 'plate', { rot: [0, 0, -s * 12], pivot: [s * 12, 50, 0], seed: 5 });
	sbox(pelvis, s, 18.6, 19.4, 37, 47, -4, 4, 'glow', { rot: [0, 0, -s * 12], pivot: [s * 12, 50, 0] });
}

for (const s of S) {
	const n = nm(s);
	// ---- thigh
	const leg = M.bone(`leg_${n}`, 'pelvis', [s * 12, 44, 0]);
	M.cbox(leg, [s * 12, 44, 0], [8, 8, 8], 'frame');
	sbox(leg, s, 9, 15, 28, 44, -3, 3, 'frame');
	sbox(leg, s, 6.5, 17.5, 30, 43, -6.5, 5.5, 'plate', { seed: 10 });
	sbox(leg, s, 17.4, 18.8, 32, 41, -4, 3, 'plate', { seed: 11 });     // outer vent plate
	sbox(leg, s, 18.6, 19.0, 33.5, 39.5, -3, 2, 'glow');
	// ---- shin
	const shin = M.bone(`shin_${n}`, `leg_${n}`, [s * 12, 29, -1]);
	M.cbox(shin, [s * 12, 29, -1], [7.5, 6, 7.5], 'frame');
	sbox(shin, s, 6.5, 17.5, 24, 35, -9.5, -4, 'plate', { rot: [-10, 0, 0], pivot: [s * 12, 29, -6], seed: 12 }); // knee pad
	M.cbox(shin, [s * 12, 34.5, -9], [5, 4, 3], 'plate', { rot: [-25, 0, 0], pivot: [s * 12, 33, -8], seed: 13 });  // knee spike
	sbox(shin, s, 9.5, 14.5, 8, 28, -3, 3, 'frame');
	sbox(shin, s, 7, 17, 11, 26, -6.5, 4, 'plate', { seed: 14 });
	sbox(shin, s, 6, 18, 6.5, 13, -7.5, 5, 'plate', { seed: 15 });      // ankle flare
	sbox(shin, s, 8, 16, 13, 25, 4, 8.5, 'plate', { seed: 16 });        // calf thruster housing
	sbox(shin, s, 9.5, 14.5, 11.5, 13.2, 4.6, 8, 'glow');                // calf nozzle
	sbox(shin, s, 11.3, 12.7, 14, 24, -6.9, -6.5, 'glow');               // shin light strip
	// ---- foot
	const foot = M.bone(`foot_${n}`, `shin_${n}`, [s * 12, 6, 0]);
	M.cbox(foot, [s * 12, 6, 0], [6, 5, 6], 'frame');
	sbox(foot, s, 6, 18, 0, 5, -10, 7, 'plate', { seed: 20 });
	sbox(foot, s, 6.5, 11.6, 0, 4, -16, -9.5, 'plate', { seed: 21 });   // toe (inner)
	sbox(foot, s, 12.4, 17.5, 0, 4, -15, -9.5, 'plate', { seed: 22 });  // toe (outer)
	sbox(foot, s, 6.4, 11.7, 0, 1.2, -16.6, -15.4, 'glow');
	sbox(foot, s, 12.3, 17.6, 0, 1.2, -15.6, -14.4, 'glow');
	sbox(foot, s, 9, 15, 0, 4, 6.5, 11.5, 'plate', { seed: 23 });       // heel spur
	sbox(foot, s, 5.5, 18.5, 4, 7.5, -7, 5, 'plate', { seed: 24 });     // ankle guard
}

// ---- torso + cockpit
const torso = M.bone('torso', 'pelvis', [0, 53, 1]);
M.box(torso, -9, 52, -5, 9, 58, 6, 'frame');                             // abdomen core
for (let i = 0; i < 3; i++) M.box(torso, -8 + i * 0.5, 52 + i * 2.2, -8.5, 8 - i * 0.5, 54 + i * 2.2, -5, 'plate', { seed: 30 + i }); // abs
M.box(torso, -9, 55, -11, 9, 57, 6, 'frame');                             // cockpit floor / seat base
M.box(torso, -9, 82.5, -11, 9, 85.5, 6, 'plate', { seed: 33 });           // cockpit ceiling
M.box(torso, -21, 56, 6, 21, 86, 13, 'plate', { seed: 34 });              // back armour
M.box(torso, -8, 58, 5, 8, 82, 6, 'frame');                               // back wall (inside)
M.cbox(torso, [0, 73, 5.0], [5, 13, 0.6], 'glow');                        // core light behind the pilot
for (const s of S) {
	sbox(torso, s, 9, 21, 56, 86, -11, 6, 'plate', { seed: 35 });                                              // cockpit side wall
	sbox(torso, s, 9, 23.5, 72, 86.5, -14.5, -10, 'plate', { rot: [0, s * 14, 0], pivot: [s * 9, 79, -11], seed: 36 }); // upper chest
	sbox(torso, s, 10, 20.5, 57, 71, -13.5, -10, 'plate', { rot: [0, s * 10, 0], pivot: [s * 10, 64, -11], seed: 37 });  // ribs
	sbox(torso, s, 21, 22.5, 60, 82, -8, 10, 'frame');                                                         // flank frame
}
M.box(torso, -10, 85, -8, 10, 88.5, 8, 'plate', { seed: 38 });            // collar
// the lantern symbol round the canopy: an octagonal ring + the two bars
const EC = [0, 73.2, -14];
for (let i = 0; i < 8; i++) {
	M.box(torso, -4.7, EC[1] + 8.3, -15.3, 4.7, EC[1] + 11.3, -12.6, 'emblem', { rot: [0, 0, i * 45], pivot: EC.slice() });
}
M.box(torso, -14.5, EC[1] + 11.3, -15.3, 14.5, EC[1] + 14.0, -12.6, 'emblem');  // top bar
M.box(torso, -14.5, EC[1] - 14.0, -15.3, 14.5, EC[1] - 11.3, -12.6, 'emblem');  // bottom bar
// glass canopy (a stepped disc inside the ring)
M.cbox(torso, [0, EC[1], -13.6], [16.6, 9, 0.8], 'glass');
M.cbox(torso, [0, EC[1], -13.6], [9, 16.6, 0.8], 'glass');
M.cbox(torso, [0, EC[1], -13.6], [13.4, 13.4, 0.8], 'glass');
for (const c of torso.cubes) if (c.mat === 'glass') c.glassRim = false;

// ---- backpack + thrusters
const pack = M.bone('thrusters', 'torso', [0, 72, 13]);
M.box(pack, -13, 60, 13, 13, 84, 18, 'plate', { seed: 40 });
for (const s of S) {
	sbox(pack, s, 3.5, 12.5, 58, 83, 17, 24.5, 'plate', { seed: 41 });
	sbox(pack, s, 4.5, 11.5, 57, 82, 24, 25.5, 'frame');
	sbox(pack, s, 4.2, 11.8, 53.5, 58.5, 17.5, 24, 'plate', { seed: 42 }); // nozzle bell
	sbox(pack, s, 5.5, 10.5, 53, 54.5, 18.5, 23, 'glow');                  // nozzle fire
	sbox(pack, s, 7.3, 8.7, 62, 80, 24.6, 25.8, 'glow');                   // thruster light strip
	// swept back fins
	M.stick(pack, [s * 11, 80, 19], [s * 0.55, 0.75, 0.42], 16, [2, 7], 'plate', { seed: 43 });
	M.stick(pack, [s * 11.2, 80.3, 19.2], [s * 0.55, 0.75, 0.42], 15.5, [0.6, 1.0], 'glow', { seed: 44 });
}

// ---- head
const head = M.bone('head', 'torso', [0, 87, -2]);
M.box(head, -4, 85, -5, 4, 89.5, 2, 'frame');                              // neck
M.box(head, -6, 88.5, -9, 6, 97, 3, 'plate', { seed: 50 });
M.box(head, -4.5, 86.5, -10, 4.5, 90.5, -6, 'plate', { seed: 51 });       // chin guard
M.box(head, -6.8, 93.5, -10.4, 6.8, 95.5, -7, 'plate', { seed: 52 });     // brow
M.box(head, -5.6, 90.8, -9.7, 5.6, 93.4, -8.9, 'visor');                  // visor slit
M.box(head, -1.1, 96, -7.5, 1.1, 100.5, 3.5, 'plate', { seed: 53 });      // crest
M.cbox(head, [0, 94.6, -10.7], [2.6, 2.6, 1.0], 'glow');                  // brow gem
for (const s of S) {
	sbox(head, s, 6, 7.4, 89, 95, -7, 1.5, 'plate', { seed: 54 });          // cheek vents
	sbox(head, s, 7.2, 7.6, 90, 94, -5.5, 0, 'glow');
	M.stick(head, [s * 0.9, 94.6, -10.6], [s * 0.95, 1.0, -0.12], 10, [1.3, 0.8], 'emblem', { seed: 55 }); // V-fin
}
// the head rides 3.5 px higher on a longer neck (it sank between the pauldrons), a touch bigger
for (const c of head.cubes) {
	const k = 1.12, cy = 92, cz = -3;
	for (const P of [c.from, c.to]) { P[0] *= k; P[1] = cy + (P[1] - cy) * k + 3.5; P[2] = cz + (P[2] - cz) * k; }
	if (c.pivot) { c.pivot[0] *= k; c.pivot[1] = cy + (c.pivot[1] - cy) * k + 3.5; c.pivot[2] = cz + (c.pivot[2] - cz) * k; }
}
M.box(head, -3.6, 85, -4.5, 3.6, 91, 1.5, 'frame');                        // the longer neck

// ---- shoulders, pods, arms
for (const s of S) {
	const n = nm(s);
	const sh = M.bone(`shoulder_${n}`, 'torso', [s * 24, 82, 1]);
	sbox(sh, s, 20, 36, 74, 89, -10, 11, 'plate', { seed: 60 });
	sbox(sh, s, 21, 38.5, 88.5, 92.5, -11.5, 12.5, 'plate', { seed: 61 });     // top layer
	sbox(sh, s, 34.5, 39.5, 71.5, 87, -9, 10, 'plate', { rot: [0, 0, -s * 10], pivot: [s * 36, 87, 0], seed: 62 }); // outer flare
	sbox(sh, s, 21, 37, 76, 89, -12.5, -10, 'plate', { seed: 63 });            // front armour
	sbox(sh, s, 25, 34, 81, 83, -13.1, -12.4, 'glow');
	sbox(sh, s, 37.5, 39.2, 89, 92, -11, 12, 'glow');                          // top-layer rim light
	const pod = M.bone(`pod_${n}`, `shoulder_${n}`, [s * 29, 92.5, 6]);
	sbox(pod, s, 23, 35, 92.5, 98.5, -7, 9, 'plate', { seed: 64 });
	for (let i = 0; i < 3; i++) for (let j = 0; j < 2; j++) {
		M.cbox(pod, [s * (25.4 + i * 3.6), 94.1 + j * 2.8, -7.35], [2.6, 2.0, 0.8], 'glow');
	}

	const arm = M.bone(`arm_${n}`, 'torso', [s * 28, 79, 1]);
	M.cbox(arm, [s * 28, 79, 1], [9, 9, 9], 'frame');
	sbox(arm, s, 25, 31, 60, 76, -2.5, 4.5, 'frame');
	sbox(arm, s, 23.5, 32.5, 62.5, 74, -4.5, 6.5, 'plate', { seed: 70 });
	sbox(arm, s, 23.1, 32.9, 66.4, 67.8, -4.9, 6.9, 'glow');
	const fa = M.bone(`forearm_${n}`, `arm_${n}`, [s * 28, 61, 1]);
	M.cbox(fa, [s * 28, 61, 1], [8, 7, 8], 'frame');
	sbox(fa, s, 23, 33, 56, 64.5, 5.5, 9.5, 'plate', { seed: 71 });             // elbow guard
	sbox(fa, s, 21.5, 34.5, 41, 59.5, -7, 8.5, 'plate', { seed: 72 });          // forearm
	sbox(fa, s, 20.5, 35.5, 40.5, 47.5, -8, 9.5, 'plate', { seed: 73 });        // wrist flare
	sbox(fa, s, 34.3, 37.5, 44, 58, -5, 6, 'plate', { seed: 74 });              // outer blade
	sbox(fa, s, 37.3, 37.8, 46, 56, 0.2, 1.4, 'glow');
	sbox(fa, s, 24, 32, 50.5, 51.8, -7.6, -6.9, 'glow');
	sbox(fa, s, 24, 32, 53.5, 54.8, -7.6, -6.9, 'glow');
	const fist = M.bone(`fist_${n}`, `forearm_${n}`, [s * 28, 40.5, 1]);
	M.cbox(fist, [s * 28, 40.5, 1], [9, 3, 9], 'frame');
	sbox(fist, s, 22, 34, 29, 40, -6, 8, 'plate', { seed: 80 });                // palm block
	sbox(fist, s, 21.5, 34.5, 30.5, 39, -9, -6, 'plate', { seed: 81 });         // finger plate
	for (let i = 0; i < 4; i++) M.cbox(fist, [s * (23.6 + i * 3.0), 37.3, -9.6], [2.6, 3.0, 1.6], 'emblem');
	sbox(fist, s, 20.2, 23, 31.5, 37, -7.5, -1, 'plate', { seed: 82 });         // thumb
}

M.ground();

// ================================================================ paint
const C = {
	plate: [28, 178, 76], plateHi: [90, 230, 130], plateLo: [12, 110, 50],
	edge: [214, 255, 226], bevel: [86, 236, 136], seam: [10, 92, 44],
	frame: [9, 54, 28], frameEdge: [52, 160, 92],
	glow: [226, 255, 234], visor: [240, 255, 246], glass: [150, 255, 196], emblem: [205, 255, 220],
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
		case 'glow': return [C.glow, 255, [110, 240, 150], 255];
		case 'visor': {
			const k = 0.75 + 0.25 * Math.sin(u * 2.2);
			return [mix(C.visor, [170, 255, 200], 1 - k), 255, [190, 255, 210], 255];
		}
		case 'emblem': {
			if (e < 0.4) return [[250, 255, 250], 255, [200, 255, 215], 255];
			return [C.emblem, 255, [70, 200, 110], 255];
		}
		case 'glass': {
			if (f !== 'north') return [C.glass, 40, null];
			if (e < 0.3 && c.glassRim) return [C.edge, 255, [140, 255, 180], 255];
			const scan = (Math.floor(v * 2) % 3 === 0) ? 1.15 : 1;
			return [mul(C.glass, scan), A_GLASS, [40, 120, 70], A_GLASS];
		}
		default: { // plate
			if (e < 0.42) return [C.edge, A_EDGE, [90, 230, 130], 255];
			if (e < 0.95) return [C.bevel, A_BEVEL, null];
			// panel seams every ~8 px on the bigger faces (the dark inner frame lines)
			const off = (c.seed * 3.7) % 8;
			const seamU = info.fw > 9 && Math.abs(((u + off) % 8) - 4) > 3.72 && u > 1.5 && u < info.fw - 1.5;
			const seamV = info.fh > 9 && Math.abs(((v + off * 0.6) % 8) - 4) > 3.72 && v > 1.5 && v < info.fh - 1.5;
			if (seamU || seamV) return [C.seam, 228, null];
			// a vertical gradient (lit from above), faint scanlines and a willpower shimmer
			const g = clamp((p[1] - 0) / 104, 0, 1);
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
