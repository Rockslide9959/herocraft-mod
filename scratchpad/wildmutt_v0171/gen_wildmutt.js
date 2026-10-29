// v0.17.1: WILDMUTT -- the first "real" Omnitrix alien body (user: "build a good wildmutt model with good animations").
// The classic Ben 10 Vulpimancer: a big orange, furry, ape / dog-like quadruped, hunched and heavy at the front with a huge
// back and shoulders; NO eyes -- a low head that is mostly a wide jaw with a black band and a row of white teeth across the
// front (two tusks at the corners), gill slits on the neck, long thick front arms with big hands and four dark claws each,
// short powerful hind legs, spiky fur tufts along the back, the elbows and the haunches, a stubby tail tuft, and the
// Omnitrix strapped round the left upper arm (the renderer draws the watch face on the strap's plate).
// An original model (built from the user's reference stills), not the Planet Minecraft community model.
//
// Model pixels are real 1/16-block pixels (no render scale): 26 px to the top of the hump (1.6 blocks), 21 px at the
// shoulder, 35 px nose to rump. Bedrock / GeckoLib conventions: +Y up, the model faces -Z, the entity's RIGHT side is -X.
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
const { Model, mix, mul, fbm, clamp, smooth } = L;

const ROOT = path.join(__dirname, '..', '..');
const A = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero');
const mk = d => (fs.mkdirSync(d, { recursive: true }), d);
const writeJson = (f, o) => { mk(path.dirname(f)); fs.writeFileSync(f, JSON.stringify(o, null, '\t') + '\n'); };
const writePng = (f, w, h, px) => { mk(path.dirname(f)); fs.writeFileSync(f, L.encodePng(w, h, px)); };

const M = new Model('wildmutt', { TW: 512, D: 3 });
const S = [-1, 1];
const nm = s => (s < 0 ? 'r' : 'l');
// a box whose x range is given as |x| (outer side positive), mirrored by s
function sbox(b, s, xa, xb, y0, y1, z0, z1, mat, opts) {
	return M.box(b, s * xa, y0, z0, s * xb, y1, z1, mat, opts);
}
// a tapered fur spike: a wide short base and a thin tip along the same direction
function spike(b, from, dir, len, w, opts = {}) {
	const d = L.vnorm(dir);
	const at = k => [from[0] + d[0] * len * k, from[1] + d[1] * len * k, from[2] + d[2] * len * k];
	const flat = opts.flat == null ? 0.6 : opts.flat; // thinner across x than along the body: a blade of fur
	M.stick(b, from, d, len * 0.45, [w * flat, w], 'tuft', Object.assign({ seed: 7 }, opts));
	M.stick(b, at(0.38), d, len * 0.38, [w * flat * 0.72, w * 0.68], 'tuft', Object.assign({ seed: 8 }, opts));
	M.stick(b, at(0.7), d, len * 0.3, [w * flat * 0.45, w * 0.38], 'tuftTip', Object.assign({ seed: 9 }, opts));
}

// ================================================================ the rig
M.bone('root', null, [0, 0, 0]);
M.bone('spin', 'root', [0, 11, 2]);          // the Quill Roll turns the whole ball about here
M.bone('hurt', 'spin', [0, 13, 4]);          // the flinch layer (its own controller; nothing else keys it)
const hips = M.bone('hips', 'hurt', [0, 14, 7]);
const chest = M.bone('chest', 'hips', [0, 15, 3]);
const neck = M.bone('neck', 'chest', [0, 15, -8]);
M.bone('look', 'neck', [0, 14, -11]);        // where the player looks (set in code, never keyed)
const head = M.bone('head', 'look', [0, 14, -11]);
const jaw = M.bone('jaw', 'head', [0, 11.5, -10]);
const quillsF = M.bone('quills_f', 'chest', [0, 24, -2]);
const quillsB = M.bone('quills_b', 'hips', [0, 20, 8]);
const tail = M.bone('tail', 'hips', [0, 16, 15]);

// ---- the hips and rump: lower and narrower than the shoulders
M.box(hips, -6.8, 10, 4, 6.8, 18, 13, 'fur', { seed: 1 });
M.box(hips, -6.2, 10.5, 12.5, 6.2, 16.6, 15.4, 'fur', { seed: 2 });    // rump
M.box(hips, -5.2, 16.4, 9.5, 5.2, 17.6, 15, 'fur', { seed: 5 });        // its rounded top
M.box(hips, -6.4, 14.5, 1.5, 6.4, 21, 12.5, 'fur', { seed: 3, rot: [-17, 0, 0], pivot: [0, 21, 2] }); // the back sloping down to the rump
M.box(hips, -5.2, 20.6, 1.5, 5.2, 22, 10, 'fur', { seed: 6, rot: [-17, 0, 0], pivot: [0, 21, 2] });
M.box(hips, -5.2, 9, 4.5, 5.2, 10.5, 11.5, 'belly', { seed: 4 });       // belly
// ---- the chest: the huge hunched front -- a deep barrel with the hump on top and the shoulders bulging out
M.box(chest, -8, 9.5, -9, 8, 21.5, 4.5, 'fur', { seed: 10 });
M.box(chest, -7.4, 21.3, -8.2, 7.4, 24.2, 4.2, 'fur', { seed: 11 });    // the hump
M.box(chest, -6.2, 24, -6.8, 6.2, 25.9, 3.2, 'fur', { seed: 12 });      // its crown
M.box(chest, -4.4, 25.7, -5.2, 4.4, 26.7, 1.6, 'fur', { seed: 17 });
M.box(chest, -6.8, 16.5, -13, 6.8, 23, -7, 'fur', { seed: 13, rot: [28, 0, 0], pivot: [0, 22, -7.5] }); // the hunch sloping down to the head
M.box(chest, -6.5, 8, -7.5, 6.5, 10, 3.5, 'belly', { seed: 14 });       // chest underside
for (const s of S) {
	sbox(chest, s, 7.5, 9.6, 12.5, 21.5, -9.5, -0.5, 'fur', { seed: 15 });   // shoulder mass
	sbox(chest, s, 6.6, 8.2, 21, 24.5, -6.5, 3, 'fur', { seed: 16 });        // the hump's flank
}
// ---- the neck: wide and short, the gill slits painted on its sides (the "nostrils" he smells and hears with)
M.box(neck, -6.6, 8.6, -12.5, 6.6, 18.8, -6.5, 'neck', { seed: 20 });
// ---- the head: no eyes -- a blunt skull over the mouth band, the lower jaw hinged at the back
M.box(head, -6, 11.5, -19, 6, 17.5, -10, 'fur', { seed: 30 });
M.box(head, -5, 17.3, -17.6, 5, 18.8, -10, 'fur', { seed: 31 });        // skull top
M.box(head, -6.35, 11.0, -19.35, 6.35, 14.3, -12.4, 'band', { tag: 'upper' }); // the black band + the upper teeth
M.box(head, -5.4, 11.2, -18.9, 5.4, 11.6, -10.4, 'mouth');               // the roof of the mouth (seen when it opens)
for (const s of S) {
	M.stick(head, [s * 5.75, 13.4, -18.9], [s * 0.12, 1, -0.25], 3.4, [1.4, 1.2], 'tooth', { seed: 32 }); // the tusks
	M.stick(head, [s * 5.8, 16.6, -19.4], [s * 0.1, 0.6, -1], 1.1, [1.0, 0.9], 'tooth', { seed: 34 });   // their hooked tips
	sbox(head, s, 5.4, 6.4, 14, 17, -16.5, -11, 'fur', { seed: 33 });                                  // cheek
}
M.box(jaw, -5.7, 7.5, -18.7, 5.7, 11.4, -9.5, 'fur', { seed: 40 });
M.box(jaw, -6.05, 9.6, -19.05, 6.05, 11.3, -12.4, 'band', { tag: 'lower' });
M.box(jaw, -5.0, 11.1, -18.4, 5.0, 11.45, -10.2, 'mouth', { tag: 'tongue' });
for (let i = 0; i < 5; i++) {                                           // the shaggy beard under the jaw
	const x = -4.4 + i * 2.2;
	M.stick(jaw, [x, 8.2, -16.5 + (i % 2) * 2.2], [x * 0.04, -1, 0.35], 3.2 + (i % 2) * 0.9, [1.7, 1.4], 'tuft', { seed: 41 + i });
}
// ---- the back spikes (quills): tufts along the hump and the spine, leaning back
// [x, y, z, length]: a ragged ridge of fur blades over the hump, two rows splaying out, the front ones over the brow
const QF = [[0, 26.7, -4.4, 5.6], [0, 26.7, -1.6, 6.2], [0, 26.7, 1.0, 5.4], [-2.4, 26.2, -3.2, 4.8], [2.4, 26.2, -2.6, 5.0],
	[-2.6, 26.2, 0.2, 4.6], [2.6, 26.2, 0.8, 4.4], [-4.6, 25.4, -5.2, 3.8], [4.6, 25.4, -4.6, 3.9], [-5.0, 25.2, -1.0, 3.4],
	[5.0, 25.2, -0.4, 3.4], [-1.6, 23.6, -9.4, 3.2], [1.6, 23.6, -9.0, 3.2], [-6.4, 23.6, 2.4, 2.8], [6.4, 23.6, 2.8, 2.8]];
for (const [x, y, z, len] of QF) spike(quillsF, [x, y - 0.7, z], [x * 0.09, 0.82, 0.58], len, 2.2, { seed: 50 + Math.round(z * 3 + x) });
const QB = [[0, 21.5, 3.8, 4.4], [-2.2, 21.1, 5.4, 3.6], [2.2, 21.1, 6.2, 3.6], [0, 20.6, 7.4, 3.8], [0, 19.6, 10.4, 3.0],
	[-2.0, 18.8, 11.6, 2.6], [2.0, 18.8, 12.2, 2.6]];
for (const [x, y, z, len] of QB) spike(quillsB, [x, y - 0.7, z], [x * 0.09, 0.66, 0.75], len, 2.0, { seed: 60 + Math.round(z * 3 + x) });
// ---- the stubby tail tuft
M.stick(tail, [0, 16, 15], [0, 0.35, 1], 2.6, [2.6, 2.4], 'fur', { seed: 70 });
spike(tail, [0, 16.8, 17.2], [0, 0.4, 1], 3.2, 2.2, { seed: 71 });
spike(tail, [-0.9, 16.2, 17.0], [-0.4, -0.1, 1], 2.6, 1.6, { seed: 72 });
spike(tail, [0.9, 16.2, 17.0], [0.4, -0.1, 1], 2.6, 1.6, { seed: 73 });

// ---- the front arms: long, thick, a big hand with four dark claws
for (const s of S) {
	const n = nm(s);
	const arm = M.bone(`arm_${n}`, 'chest', [s * 9.5, 18.5, -5], [0, 0, -s * 5]);
	sbox(arm, s, 6.6, 12.6, 9.5, 20.5, -8.6, -1.4, 'fur', { seed: 80 });
	sbox(arm, s, 7.2, 12.2, 19.5, 22, -8, -2, 'fur', { seed: 81 });          // the shoulder cap
	spike(arm, [s * 12.4, 20.2, -5], [s * 0.85, 0.5, 0.3], 3.2, 1.6, { seed: 82 });  // shoulder tufts
	spike(arm, [s * 12.4, 18.2, -3], [s * 0.9, 0.2, 0.55], 2.8, 1.4, { seed: 83 });
	if (s > 0) { // the Omnitrix: a grey strap round the left upper arm, a plate on the outside for the watch face
		const badge = M.bone('badge', `arm_${n}`, [s * 13.4, 16.6, -5]);
		sbox(badge, s, 6.3, 12.95, 14.8, 18.4, -8.95, -1.05, 'strap');
		sbox(badge, s, 12.8, 13.7, 14.0, 19.2, -7.6, -2.4, 'plate');
	}
	const fa = M.bone(`forearm_${n}`, `arm_${n}`, [s * 9.6, 10.2, -5], [0, 0, s * 7]);
	sbox(fa, s, 6.9, 12.4, 2.4, 10.8, -8.3, -1.9, 'fur', { seed: 84 });
	sbox(fa, s, 6.5, 12.8, 2.0, 5.0, -8.8, -1.5, 'fur', { seed: 85 });       // the heavy wrist
	spike(fa, [s * 9.6, 9.6, -2.2], [s * 0.25, 0.3, 1], 3.0, 1.6, { seed: 86 }); // elbow tufts
	spike(fa, [s * 11.6, 8.4, -2.4], [s * 0.6, 0.1, 0.9], 2.5, 1.3, { seed: 87 });
	const paw = M.bone(`paw_${n}`, `forearm_${n}`, [s * 9.6, 2.6, -5], [0, 0, -s * 2]);
	sbox(paw, s, 6.2, 13.1, 0, 3.0, -10, -1.4, 'fur', { seed: 88 });          // palm
	sbox(paw, s, 6.6, 12.7, 2.6, 3.8, -8.5, -2, 'fur', { seed: 89 });         // back of the hand
	for (let i = 0; i < 4; i++) {
		const x0 = 6.45 + i * 1.66;
		sbox(paw, s, x0, x0 + 1.52, 0, 2.5, -12.2, -9.6, 'fur', { seed: 90 + i }); // knuckles / fingers
		// the claws: dark, hooked down onto the ground
		M.stick(paw, [s * (x0 + 0.76), 1.7, -12.0], [0, -0.5, -1], 2.9, [1.05, 1.0], 'claw', { seed: 94 + i });
	}
	sbox(paw, s, 5.4, 6.8, 0.2, 2.2, -7.5, -4.5, 'fur', { seed: 98 });       // thumb
	M.stick(paw, [s * 5.7, 1.0, -7.4], [-s * 0.3, -0.4, -1], 1.9, [0.9, 0.9], 'claw', { seed: 99 });
}

// ---- the hind legs: short and powerful, a dog-like hock
for (const s of S) {
	const n = nm(s);
	const th = M.bone(`thigh_${n}`, 'hips', [s * 6.4, 15, 9.5]);
	sbox(th, s, 3.6, 9.6, 11, 16.4, 5.5, 14.2, 'fur', { seed: 100 });     // the haunch
	sbox(th, s, 4.0, 9.2, 7.5, 11.2, 8.2, 14.2, 'fur', { seed: 110 });    // tapering back into the knee
	sbox(th, s, 4.2, 9.2, 12, 15.6, 13.8, 15.2, 'fur', { seed: 109 });
	spike(th, [s * 9.2, 13, 12.5], [s * 0.8, 0.25, 0.6], 2.6, 1.4, { seed: 101 }); // haunch tufts
	const sh = M.bone(`shin_${n}`, `thigh_${n}`, [s * 6.6, 8.6, 12.4]);
	sbox(sh, s, 4.2, 9.0, 2.2, 9.6, 10.0, 14.6, 'fur', { seed: 102 });
	spike(sh, [s * 6.6, 6.4, 14.4], [s * 0.2, 0.1, 1], 2.2, 1.3, { seed: 103 }); // hock tuft
	const ft = M.bone(`foot_${n}`, `shin_${n}`, [s * 6.6, 2.4, 12]);
	sbox(ft, s, 3.8, 9.4, 0, 2.6, 6.8, 14, 'fur', { seed: 104 });
	for (let i = 0; i < 3; i++) {
		const x0 = 4.0 + i * 1.8;
		sbox(ft, s, x0, x0 + 1.6, 0, 2.0, 5.4, 7.2, 'fur', { seed: 105 + i });
		M.stick(ft, [s * (x0 + 0.8), 1.3, 5.6], [0, -0.55, -1], 2.0, [0.95, 0.9], 'claw', { seed: 108 + i });
	}
}

M.ground();

// ================================================================ paint
const C = {
	fur: [236, 128, 30], furHi: [252, 170, 64], furLo: [186, 88, 18], furLine: [150, 66, 14], outline: [128, 58, 12],
	belly: [246, 168, 84], tuftTip: [255, 190, 96],
	claw: [58, 58, 64], clawHi: [112, 112, 122],
	band: [18, 16, 16], tooth: [246, 242, 222], toothGap: [176, 170, 150],
	mouth: [104, 26, 34], tongue: [196, 86, 104],
	strap: [124, 130, 138], strapHi: [176, 182, 190], strapLo: [72, 76, 84], plate: [40, 42, 46],
	gill: [120, 50, 12],
};
// a soft fur stroke field: long thin dark lines running down the body (anisotropic noise), a few, like the cartoon's
function furLines(p, seed) {
	const n = fbm(p[0] * 0.7 + seed * 1.7, p[1] * 0.3, p[2] * 0.7, 3);
	const gate = fbm(p[0] * 0.25 + 9, p[1] * 0.25, p[2] * 0.25 + seed, 2);
	return smooth(0.66, 0.7, n) * (1 - smooth(0.71, 0.75, n)) * smooth(0.52, 0.6, gate);
}
function shade(c, f, s, t, p, info) {
	const e = info.ePx;
	switch (c.mat) {
		case 'claw': {
			const k = f === 'up' ? 1.25 : f === 'down' ? 0.7 : 1;
			const tip = 0.85 + 0.3 * (1 - t);
			return [mix(C.claw, C.clawHi, e < 0.25 ? 0.6 : 0.15 * tip * k), 255, null];
		}
		case 'tooth': {
			const g = e < 0.2 ? 0.85 : 1;
			return [mul(C.tooth, (f === 'down' ? 0.86 : 1) * g), 255, null];
		}
		case 'mouth': {
			if (c.tag === 'tongue' && f === 'up' && Math.abs(p[0]) < 3.2 && p[2] > -17.4) return [C.tongue, 255, null];
			return [mul(C.mouth, 0.8 + 0.2 * fbm(p[0], p[1], p[2], 2)), 255, null];
		}
		case 'band': {
			// the upper band: a row of square white teeth sitting on a black lip line, across the front and round the sides
			if (c.tag === 'upper' && f !== 'up' && f !== 'down') {
				const y = p[1];
				if (y < 14.0 && y > 11.75) {
					const along = f === 'north' ? p[0] + 6.35 : f === 'east' ? 12.7 + (p[2] + 19.35) : f === 'west' ? -(p[2] + 19.35) - 6.35 : 0;
					const cell = ((along % 2.1) + 2.1) % 2.1;
					if (cell > 0.2) return [mul(C.tooth, y > 13.6 ? 0.88 : y < 12.1 ? 0.92 : 1), 255, null];
					return [C.toothGap, 255, null];
				}
			}
			return [mul(C.band, f === 'up' ? 1.6 : 1), 255, null];
		}
		case 'strap': {
			if (e < 0.35) return [C.strapLo, 255, null];
			const bandY = Math.abs(p[1] - 16.6);
			return [bandY < 0.35 ? C.strapLo : mix(C.strap, C.strapHi, f === 'up' ? 0.6 : 0.2), 255, null];
		}
		case 'plate': return [e < 0.35 ? C.strapHi : C.plate, 255, null];
		default: { // fur, neck, belly, tuft, tuftTip
			let col;
			if (c.mat === 'belly') col = C.belly;
			else if (c.mat === 'tuftTip') col = mix(C.fur, C.tuftTip, 0.35 + 0.65 * (1 - t));
			else if (c.mat === 'tuft') col = mix(C.furLo, C.fur, 0.55 + 0.45 * (1 - t));
			else col = C.fur;
			// lit from above: darker under the body and low on the limbs, lighter on top
			const lift = clamp((p[1] - 2) / 24, 0, 1);
			col = mix(mix(C.furLo, col, 0.55 + 0.45 * lift), col, c.mat === 'belly' ? 0.6 : 0);
			if (f === 'down') col = mul(col, c.mat === 'belly' ? 0.92 : 0.78);
			if (f === 'up') col = mix(col, C.furHi, 0.28);
			// fur grain + mottling
			const n = fbm(p[0] * 0.55 + c.seed, p[1] * 0.55, p[2] * 0.55, 3);
			col = mix(col, n > 0.5 ? C.furHi : C.furLo, Math.abs(n - 0.5) * 0.3);
			const grain = fbm(p[0] * 3.1, p[1] * 1.1, p[2] * 3.1, 2);
			col = mul(col, 0.96 + 0.07 * grain);
			if (c.mat === 'fur' || c.mat === 'neck') col = mix(col, C.furLine, 0.38 * furLines(p, c.seed));
			// the gill slits on the neck's sides: three dark curved slits
			if (c.mat === 'neck' && (f === 'east' || f === 'west')) {
				for (let i = 0; i < 3; i++) {
					const zc = -11.2 + i * 1.6 + 0.35 * Math.sin((p[1] - 13) * 0.6);
					if (Math.abs(p[2] - zc) < 0.32 && p[1] > 10.8 && p[1] < 16.5) col = mix(col, C.gill, 0.9);
				}
			}
			// the cartoon's fur strokes on the shoulders / hump: a few short dark ticks
			if (c.mat === 'fur' && (f === 'east' || f === 'west') && (c.bone === 'chest' || c.bone.startsWith('arm'))) {
				const k = Math.sin(p[2] * 1.3 + p[1] * 0.4 + c.seed) ;
				if (k > 0.965 && fbm(p[1] * 0.4, p[2] * 0.4, c.seed, 2) > 0.5) col = mix(col, C.furLine, 0.8);
			}
			// a soft darker rim on every face (reads as the cartoon outline, separates the blocks)
			if (e < 0.34) col = mix(col, C.outline, 0.4 * (1 - e / 0.34));
			return [col, 255, null];
		}
	}
}

M.layout();
M.paint(shade);
const b0 = M.bounds(M.world({}));
console.log(`wildmutt: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ` +
	`x ${b0.lo[0].toFixed(1)}..${b0.hi[0].toFixed(1)}, y ${b0.lo[1].toFixed(1)}..${b0.hi[1].toFixed(1)} (${(b0.hi[1] / 16).toFixed(2)} blocks), ` +
	`z ${b0.lo[2].toFixed(1)}..${b0.hi[2].toFixed(1)} (${((b0.hi[2] - b0.lo[2]) / 16).toFixed(2)} long)`);

writeJson(path.join(A, 'geo', 'omnitrix', 'wildmutt.geo.json'), M.geoJson('geometry.projecthero.wildmutt'));
writePng(path.join(A, 'textures', 'entity', 'omnitrix', 'wildmutt.png'), M.TW, M.TH, M.tex);
module.exports = { M, ROOT, A, L };

require('./anims.js')(M, A, ROOT);
const si = process.argv.indexOf('--sheets');
if (si > 0) require('./preview.js')(M, process.argv[si + 1]);
