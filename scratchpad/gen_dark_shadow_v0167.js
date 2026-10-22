// v0.16.7 (user) Quirk revamp -- Dark Shadow: the living shadow-bird spirit (and its Black Ankh armour) as GeckoLib models.
// User: "make the dark shadow model look much better ... more like they are in the show and manga".
//
// Run from the repo root:  node scratchpad/gen_dark_shadow_v0167.js [previewDir]
// Writes (assets/projecthero/...):
//   geo/quirk/dark_shadow.geo.json            the creature
//   animations/quirk/dark_shadow.animation.json
//   textures/entity/quirk/dark_shadow/creature.png (+ _glowmask: eyes, eye bloom, talon tips, berserk spike tips)
//   geo/quirk/dark_shadow_ankh.geo.json       Black Ankh parts worn over the player model (helmet, plumes, gauntlets,
//   animations/quirk/dark_shadow_ankh.animation.json   back ridge, wings) -- each part is drawn on its own player limb
//   textures/entity/quirk/dark_shadow/ankh_parts.png (+ _glowmask)
//   textures/entity/quirk/dark_shadow/ankh.png the feathered shell over the body (player-skin layout)
//   textures/entity/quirk/dark_shadow/tendril.png  the stretched tail (Java)
// With a preview dir: offline contact sheets (software renderer from lib_concept_models).
//
// THE CREATURE (model px, +Y up, faces -Z, root = where it grows out of its user's back). Built as a vertical column and
// bent by REST rotations into the anime silhouette: a long serpentine body rising from the back, arcing forward over the
// shoulder; a smooth teardrop head with a long pointed beak and two big almond eyes; two long thin arms with big
// four-taloned hands; a frayed, smoky tail where it joins him.
//   root > float > tail (frayed wisps)
//               > spine1 > spine2 > spine3 > chest > { arm_l > forearm_l > hand_l > finger_l1..3 > talon_l1..3, thumb_l,
//                                                     arm_r ..., wings > wing_l > wing_l2 ..., spikes (berserk) }
//                                           chest > neck1 > neck2 > look (NEVER keyed: Java aims it) > head > beak, jaw,
//                                                                   eye_l, eye_r, hood, head_spikes (berserk)
//   spine2 > spikes_mid (berserk). Clips hide extras with scale 0.01; Java hides any bone scaled under 0.05.
// Clips (animation.dark_shadow.<name>): idle, timid, strike, bite, grab, slam, shield, berserk, nova, scout, fetch,
// recall, summon, calm (the whimper).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');

const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/projecthero');
const TEXDIR = path.join(ASSETS, 'textures/entity/quirk/dark_shadow');
const PREVIEW = process.argv[2] || null;
const { aff, affMul, pivotRot, fbm, clamp, mix, smooth } = L;
const I = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];

class DSModel extends L.Model {
	// pose[bone] = { r: [deg], p: [px], s: [k] } (clip values ADD to the rest rotation, like GeckoLib)
	world(pose = {}, rootPos = [0, 0, 0]) {
		const W = {};
		for (const b of this.bones) {
			const q = pose[b.name] || {};
			const off = q.r || [0, 0, 0];
			let local = pivotRot(b.pivot, [b.rot[0] + off[0], b.rot[1] + off[1], b.rot[2] + off[2]]);
			if (q.s) {
				local = affMul(local, affMul(aff(I, b.pivot), affMul(aff([[q.s[0], 0, 0], [0, q.s[1], 0], [0, 0, q.s[2]]], [0, 0, 0]),
					aff(I, b.pivot.map(v => -v)))));
			}
			if (q.p) local = affMul(aff(I, q.p), local);
			if (b === this.bones[0]) local = affMul(aff(I, rootPos), local);
			W[b.name] = b.parent ? affMul(W[b.parent], local) : local;
		}
		return W;
	}
	/** An octagonal "lathe" slab: two crossed boxes, so round things read round. */
	oct(bone, cx, y0, y1, cz, rx, rz, mat, opts = {}) {
		rz = rz == null ? rx : rz;
		this.box(bone, cx - rx, y0, cz - rz * 0.62, cx + rx, y1, cz + rz * 0.62, mat, opts);
		this.box(bone, cx - rx * 0.62, y0, cz - rz, cx + rx * 0.62, y1, cz + rz, mat, Object.assign({}, opts, { seed: (opts.seed || 0) + 1 }));
	}
	/** A tapering spike / talon along +Y from (x,y,z): n stacked boxes, each thinner. */
	taper(bone, x, y, z, len, w, mat, opts = {}, n = 3) {
		const seg = len / n;
		for (let i = 0; i < n; i++) {
			const ww = w * (1 - i / (n + 0.6));
			this.box(bone, x - ww / 2, y + i * seg - (i ? 0.15 : 0), z - ww / 2, x + ww / 2, y + (i + 1) * seg, z + ww / 2, mat,
				Object.assign({}, opts, { seed: (opts.seed || 0) + i, tag: (opts.tag || '') + (i === n - 1 ? 'tip' : '') }));
		}
	}
}

// ================================================================ the creature
// The arms' rest rotations (s = +1 its left, -1 its right, the side toward its user). OLD_* are the pre-fix rests: the
// reaching clips were authored against them, so keep() re-bases their arm keys and those poses look as they did.
const ARM_REST = s => [12, 0, s > 0 ? -14 : 6];
const FOREARM_REST = s => [-20, 0, 4 * s];
const OLD_ARM_REST = s => [-28, 0, -16 * s];
const OLD_FOREARM_REST = s => [-38, 0, 4 * s];
const M = new DSModel('dark_shadow', { D: 2, TW: 256 });
M.k = 1 / 16;
function buildCreature() {
	M.bone('root', null, [0, 0, 0]);
	M.bone('float', 'root', [0, 0, 0]);
	// ---- the frayed tail: smoky wisps fanning down and back into its user
	M.bone('tail', 'float', [0, 1, 0]);
	const wisps = [[0, 0, 0, 0, 7, 2.2], [1.6, 0.4, 25, 30, 6, 1.6], [-1.6, 0.4, 25, -30, 6, 1.6], [0.9, -1, -25, 18, 5, 1.5],
		[-0.9, -1, -25, -18, 5, 1.5], [0, 1.4, 45, 0, 5.5, 1.5], [2.2, -0.6, 0, 50, 4.5, 1.2], [-2.2, -0.6, 0, -50, 4.5, 1.2]];
	for (const [x, z, ax, az, len, w] of wisps) {
		// point DOWN from y = 2: build up then flip with a 180 rotation about the base
		M.taper('tail', x, 2, z, len, w, 'wisp', { rot: [180 + ax, 0, az], pivot: [x, 2, z], seed: 10 }, 3);
	}
	// ---- the serpentine body: three spine segments, then a narrow chest with the shoulders
	M.bone('spine1', 'float', [0, 0, 0], [-30, 0, 0]);
	M.oct('spine1', 0, -0.5, 6.4, 0, 3.0, 2.8, 'shadow', { seed: 20 });
	M.bone('spine2', 'spine1', [0, 6, 0], [-6, 0, 0]);
	M.oct('spine2', 0, 5.6, 12.4, 0, 3.2, 2.9, 'shadow', { seed: 22 });
	M.bone('spikes_mid', 'spine2', [0, 9, 2.6]);
	for (const [y, az, len] of [[7, 0, 5], [10, 0, 6], [8.5, 30, 4], [8.5, -30, 4]]) M.taper('spikes_mid', az ? Math.sign(az) * 1.8 : 0, y, 2.4, len, 1.4, 'spike', { rot: [-60, 0, az], pivot: [0, y, 2.4], seed: 24 });
	M.bone('spine3', 'spine2', [0, 12, 0], [16, 0, 0]);
	M.oct('spine3', 0, 11.6, 17.4, 0, 3.4, 3.0, 'shadow', { seed: 26 });
	M.bone('chest', 'spine3', [0, 17, 0], [20, 0, 0]);
	M.oct('chest', 0, 16.6, 21.6, 0, 4.0, 3.0, 'shadow', { seed: 28 });
	M.box('chest', -5.2, 19.4, -1.6, 5.2, 22.2, 1.8, 'shadow', { seed: 30 });                      // the shoulder bar
	M.box('chest', -5.6, 20, -1.2, -3.6, 22.8, 1.4, 'shadow', { rot: [0, 0, 18], pivot: [-4.6, 22, 0], seed: 31 }); // hunched shoulder tops
	M.box('chest', 3.6, 20, -1.2, 5.6, 22.8, 1.4, 'shadow', { rot: [0, 0, -18], pivot: [4.6, 22, 0], seed: 32 });
	M.bone('spikes', 'chest', [0, 20, 2]);
	for (const [x, y, ax, az, len] of [[0, 20, -50, 0, 7], [-3.6, 21, -30, 40, 6], [3.6, 21, -30, -40, 6], [-1.8, 18, -65, 15, 5], [1.8, 18, -65, -15, 5]]) {
		M.taper('spikes', x, y, 2, len, 1.6, 'spike', { rot: [ax, 0, az], pivot: [x, y, 2], seed: 34 });
	}
	// ---- long thin arms with big clawed hands (+X = its left)
	for (const [side, s] of [['l', 1], ['r', -1]]) {
		const x = 4.6 * s;
		// v0.16.7 (user) "dark shadows hands are in his face": the arms used to rest reaching forward (-28 / -38), which put
		// both clawed hands right beside Tokoyami's face. They now HANG: down from the shoulders, a little back and out, the
		// elbows softly bent -- the hands rest behind its user's shoulder, never in front of his face (ARM_REST / FOREARM_REST).
		M.bone('arm_' + side, 'chest', [x, 21, 0], ARM_REST(s));
		M.oct('arm_' + side, x, 13, 21.4, 0, 1.1, 1.1, 'shadow', { seed: 40 });
		M.bone('forearm_' + side, 'arm_' + side, [x, 13, 0], FOREARM_REST(s));
		M.oct('forearm_' + side, x, 5.2, 13.3, 0, 0.95, 0.95, 'shadow', { seed: 42 });
		M.box('forearm_' + side, x - 0.5, 9, 0.6, x + 0.5, 12.5, 2.2, 'wisp', { rot: [-30, 0, 0], pivot: [x, 12.5, 1], seed: 43 }); // a trailing wisp off the elbow
		M.bone('hand_' + side, 'forearm_' + side, [x, 5.2, 0], [12, 0, 0]);
		M.box('hand_' + side, x - 1.8, 2.6, -1.1, x + 1.8, 5.6, 1.0, 'shadow', { seed: 44 }); // the palm
		// three long talon fingers (two bones each so they curl) and a thumb
		for (const [k, dx] of [[1, -1.25], [2, 0], [3, 1.25]]) {
			const fx = x + dx * s;
			M.bone(`finger_${side}${k}`, 'hand_' + side, [fx, 2.8, -0.1], [22, 0, (k - 2) * -9 * s]);
			M.box(`finger_${side}${k}`, fx - 0.45, -0.6, -0.55, fx + 0.45, 3.0, 0.45, 'shadow', { seed: 46 + k });
			M.bone(`talon_${side}${k}`, `finger_${side}${k}`, [fx, -0.5, -0.1], [26, 0, 0]);
			M.taper(`talon_${side}${k}`, fx, -0.5, -0.1, 4.2, 0.85, 'claw', { rot: [180, 0, 0], pivot: [fx, -0.5, -0.1], seed: 50 + k });
		}
		M.bone('thumb_' + side, 'hand_' + side, [x - 1.6 * s, 3.6, 0.4], [-35, 0, 30 * s]);
		M.taper('thumb_' + side, x - 1.6 * s, 3.6, 0.4, 3.4, 0.8, 'claw', { rot: [180, 0, 0], pivot: [x - 1.6 * s, 3.6, 0.4], seed: 55 });
	}
	// ---- shadow wings (Scout, Shield, the nova): a feathered arm and a tip, primaries hanging
	M.bone('wings', 'chest', [0, 20.5, 2]);
	for (const [side, s] of [['l', 1], ['r', -1]]) {
		M.bone('wing_' + side, 'wings', [2.4 * s, 20.5, 2]);
		M.box('wing_' + side, Math.min(2 * s, 12 * s), 19.4, 1.4, Math.max(2 * s, 12 * s), 21.6, 2.6, 'shadow', { seed: 60 });
		for (let k = 0; k < 5; k++) {
			const fx = (2.6 + k * 1.9) * s;
			M.taper('wing_' + side, fx + 0.9 * s, 19.8, 2, 4.5 + k, 1.9, 'feather', { rot: [180, 0, 4 * s], pivot: [fx + 0.9 * s, 19.8, 2], seed: 61 + k }, 2);
		}
		M.bone('wing_' + side + '2', 'wing_' + side, [12 * s, 20.5, 2]);
		M.box('wing_' + side + '2', Math.min(12 * s, 21 * s), 19.7, 1.5, Math.max(12 * s, 21 * s), 21.3, 2.5, 'shadow', { seed: 70 });
		for (let k = 0; k < 6; k++) {
			const fx = (12.3 + k * 1.6) * s;
			M.taper('wing_' + side + '2', fx + 0.8 * s, 20, 2, 9 + k * 1.2, 1.7, 'feather', { rot: [180, 0, (10 + k * 5) * s], pivot: [fx + 0.8 * s, 20, 2], seed: 71 + k }, 2);
		}
	}
	// ---- the neck and the head
	M.bone('neck1', 'chest', [0, 21.5, 0], [24, 0, 0]);
	M.oct('neck1', 0, 21.2, 26.2, 0, 2.5, 2.3, 'shadow', { seed: 80 });
	M.bone('neck2', 'neck1', [0, 26, 0], [22, 0, 0]);
	M.oct('neck2', 0, 25.8, 29.8, 0, 2.2, 2.1, 'shadow', { seed: 82 });
	M.bone('look', 'neck2', [0, 29.5, 0]);
	M.bone('head', 'look', [0, 29.5, 0], [-40, 0, 0]);
	// The rest rotation -62 swings the column-up head to face forward: in the head's own frame the face is +Y... no -- the
	// head is authored FACING -Z with its crown up, and the -62 rest tilt cancels the chain's bend (-30-6+16+20+24+22 = 46)
	// plus a slight downward glare.
	// teardrop hood: a round dome, swept to a point up and back
	M.oct('head', 0, 29.6, 35.2, -0.4, 3.7, 3.6, 'shadow', { seed: 90 });
	M.oct('head', 0, 35.0, 36.8, 0.2, 2.9, 2.9, 'shadow', { seed: 92 });
	M.oct('head', 0, 36.6, 37.6, 0.8, 1.8, 1.8, 'shadow', { seed: 94 });
	M.bone('hood', 'head', [0, 34, 2.6]);
	M.oct('hood', 0, 31, 35.6, 3.6, 2.6, 1.6, 'shadow', { seed: 96 });
	M.taper('hood', 0, 33.4, 4.6, 6.5, 2.6, 'shadow', { rot: [-62, 0, 0], pivot: [0, 33.4, 4.6], seed: 98 }, 4);
	M.box('head', -3.0, 29.0, -3.4, 3.0, 30.4, 2.6, 'shadow', { seed: 99 }); // the soft underside of the jaw line
	// the beak: long, pointed, slightly hooked
	M.bone('beak', 'head', [0, 31.6, -3.4], [8, 0, 0]);
	M.box('beak', -1.55, 30.6, -6.8, 1.55, 33.0, -3.0, 'beak', { seed: 100 });
	M.box('beak', -1.05, 30.8, -9.6, 1.05, 32.5, -6.6, 'beak', { seed: 101 });
	M.box('beak', -0.6, 30.6, -11.6, 0.6, 31.8, -9.4, 'beak', { rot: [14, 0, 0], pivot: [0, 31.4, -9.5], seed: 102, tag: 'tip' });
	M.bone('jaw', 'head', [0, 30.6, -3.2]);
	M.box('jaw', -1.3, 29.7, -8.4, 1.3, 30.7, -3.2, 'beak', { seed: 104 });
	M.box('jaw', -0.7, 29.8, -10, 0.7, 30.5, -8.2, 'beak', { seed: 105 });
	// big almond eyes, slanted, glowing -- each: a long slit, a taller centre, a bloom shell (glow only)
	for (const [side, s] of [['l', 1], ['r', -1]]) {
		const ex = 1.85 * s;
		M.bone('eye_' + side, 'head', [ex, 33.6, -3.55]);
		const rot = [0, 12 * s, 18 * s], piv = [ex, 33.6, -3.55];
		M.box('eye_' + side, ex - 2.1, 32.9, -3.95, ex + 2.1, 34.4, -3.5, 'eye', { rot, pivot: piv, seed: 110 });
		M.box('eye_' + side, ex - 1.25, 32.5, -4.0, ex + 1.25, 34.8, -3.55, 'eye', { rot, pivot: piv, seed: 111, tag: 'core' });
		M.box('eye_' + side, ex - 3.0, 31.9, -4.15, ex + 3.0, 35.4, -4.05, 'bloom', { rot, pivot: piv, seed: 112 });
	}
	M.bone('head_spikes', 'head', [0, 35, 0]);
	for (const [x, z, ax, az, len] of [[-2.6, -1.5, -15, 55, 5], [2.6, -1.5, -15, -55, 5], [-2.2, 1.6, -45, 40, 5.5], [2.2, 1.6, -45, -40, 5.5], [0, 2.6, -70, 0, 6]]) {
		M.taper('head_spikes', x, 34.5, z, len, 1.4, 'spike', { rot: [ax, 0, az], pivot: [x, 34.5, z], seed: 120 });
	}
}
buildCreature();

// ================================================================ the Black Ankh parts (worn over the player model)
// Every part's coordinates are its PLAYER LIMB's local space, y flipped up (Java: limb.translateAndRotate, then a
// 180 degree turn about Z): head 0..8 up from the neck; body -12..0; right arm x -3..1 (outward -X), y -10..2; left arm
// x -1..3 (outward +X). Each part is drawn on its own limb with the others hidden.
const A = new DSModel('dark_shadow_ankh', { D: 3, TW: 256 });
A.k = 1 / 16;
function buildAnkh() {
	A.bone('root', null, [0, 0, 0]);
	// ---- the bird-skull helmet (on the head): a hood over the whole head, the beak jutting from the brow, glowing eyes
	A.bone('helmet', 'root', [0, 0, 0]);
	A.oct('helmet', 0, -0.3, 7.8, 0, 4.75, 4.75, 'shell', { seed: 1 });
	A.oct('helmet', 0, 7.6, 9.4, 0.4, 3.9, 3.9, 'shell', { seed: 3 });
	A.oct('helmet', 0, 9.2, 10.2, 1.0, 2.6, 2.6, 'shell', { seed: 5 });
	A.taper('helmet', 0, 7.6, 3.4, 7.5, 3.0, 'shell', { rot: [-58, 0, 0], pivot: [0, 7.6, 3.4], seed: 7 }, 4); // the hood's point, swept back
	A.box('helmet', -4.3, 0.2, -5.15, 4.3, 3.2, -4.6, 'mask', { seed: 9 });  // the dark lower face
	A.bone('helm_beak', 'helmet', [0, 5.6, -4.7], [12, 0, 0]);
	A.box('helm_beak', -1.9, 4.4, -8.8, 1.9, 7.2, -4.4, 'beak', { seed: 11 });
	A.box('helm_beak', -1.3, 4.6, -11.8, 1.3, 6.6, -8.6, 'beak', { seed: 12 });
	A.box('helm_beak', -0.7, 4.4, -13.8, 0.7, 5.8, -11.6, 'beak', { rot: [16, 0, 0], pivot: [0, 5.2, -11.7], seed: 13, tag: 'tip' });
	for (const s of [1, -1]) {
		const ex = 2.2 * s, rot = [0, 14 * s, 14 * s], piv = [ex, 5.6, -4.8];
		A.box('helmet', ex - 1.9, 5.0, -5.2, ex + 1.9, 6.2, -4.75, 'eye', { rot, pivot: piv, seed: 15 });
		A.box('helmet', ex - 1.1, 4.6, -5.25, ex + 1.1, 6.6, -4.8, 'eye', { rot, pivot: piv, seed: 16 });
		A.box('helmet', ex - 2.1, 4.5, -5.4, ex + 2.1, 6.7, -5.3, 'bloom', { rot, pivot: piv, seed: 17 });
		// crest plumes off the temples
		A.taper('helmet', 3.6 * s, 6.5, 1.5, 5.5, 1.6, 'feather', { rot: [-55, 0, -35 * s], pivot: [3.6 * s, 6.5, 1.5], seed: 18 }, 3);
	}
	// ---- shoulder plumes (on each arm): layered feathers sweeping up and out over the shoulder
	for (const [side, s] of [['r', -1], ['l', 1]]) {
		const b = 'plume_' + side;
		A.bone(b, 'root', [0, 0, 0]);
		const cx = s * 1; // the arm's centre in its own space
		A.box(b, cx - 2.7, -0.6, -2.7, cx + 2.7, 2.8, 2.7, 'shell', { seed: 30 });
		for (const [k, dz, az, len] of [[0, -1.8, 50, 6], [1, 0, 62, 7], [2, 1.8, 50, 6], [3, 0.9, 35, 5]]) {
			A.taper(b, cx + 1.6 * s, 1.6, dz, len, 1.8, 'feather', { rot: [k === 3 ? -20 : 0, 0, -az * s], pivot: [cx + 1.6 * s, 1.6, dz], seed: 31 + k }, 3);
		}
	}
	// ---- clawed gauntlets (on each arm): a cuff over the forearm, a knuckle plate and four long talons
	for (const [side, s] of [['r', -1], ['l', 1]]) {
		const b = 'gauntlet_' + side;
		A.bone(b, 'root', [0, 0, 0]);
		const cx = s * 1;
		A.box(b, cx - 2.6, -10.3, -2.6, cx + 2.6, -5.0, 2.6, 'shell', { seed: 40 });
		A.taper(b, cx + 2.2 * s, -5.6, 0, 4, 1.2, 'feather', { rot: [0, 0, -150 * s], pivot: [cx + 2.2 * s, -5.6, 0], seed: 41 }, 2); // a fin off the cuff
		A.bone(b + '_claws', b, [cx, -10, 0]);
		for (const [k, dx, dz] of [[0, -1.6, -1.6], [1, -0.55, -1.9], [2, 0.55, -1.9], [3, 1.6, -1.6]]) {
			A.taper(b + '_claws', cx + dx, -10, dz, 5.2 - Math.abs(dx) * 0.5, 0.9, 'claw', { rot: [160, 0, dx * -6], pivot: [cx + dx, -10, dz], seed: 42 + k }, 3);
		}
	}
	// ---- the back ridge (on the body): a spine of feathers and a short ragged mantle
	A.bone('back', 'root', [0, 0, 0]);
	A.box('back', -4.4, -6, 2.1, 4.4, 0.4, 2.9, 'shell', { seed: 50 });
	for (let k = 0; k < 4; k++) A.taper('back', 0, -1.5 - k * 2.6, 2.6, 4 - k * 0.5, 1.4, 'feather', { rot: [-55 - k * 6, 0, 0], pivot: [0, -1.5 - k * 2.6, 2.6], seed: 51 + k }, 2);
	for (const [x, len] of [[-3.4, 7], [-1.7, 8.5], [0, 9], [1.7, 8.5], [3.4, 7]]) A.taper('back', x, -0.5, 3.1, len, 1.8, 'feather', { rot: [172, 0, x * 2], pivot: [x, -0.5, 3.1], seed: 56 }, 2);
	// ---- the wings (on the body, Abyssal Black Ankh): great feathered shadow wings from the shoulder blades
	A.bone('wings', 'root', [0, -3, 3]);
	for (const [side, s] of [['l', 1], ['r', -1]]) {
		A.bone('wing_' + side, 'wings', [1.5 * s, -3, 3]);
		A.box('wing_' + side, Math.min(1.5 * s, 14 * s), -4.4, 2.4, Math.max(1.5 * s, 14 * s), -1.6, 3.8, 'shell', { seed: 60 });
		for (let k = 0; k < 6; k++) {
			const fx = (2 + k * 2.1) * s;
			A.taper('wing_' + side, fx + 1 * s, -3.2, 3.1, 6 + k * 1.1, 2.1, 'feather', { rot: [180, 0, 4 * s], pivot: [fx + 1 * s, -3.2, 3.1], seed: 61 + k }, 2);
		}
		A.bone('wing_' + side + '2', 'wing_' + side, [14 * s, -3, 3]);
		A.box('wing_' + side + '2', Math.min(14 * s, 26 * s), -3.9, 2.6, Math.max(14 * s, 26 * s), -2.1, 3.6, 'shell', { seed: 70 });
		for (let k = 0; k < 7; k++) {
			const fx = (14.3 + k * 1.75) * s;
			A.taper('wing_' + side + '2', fx + 0.85 * s, -3, 3.1, 11 + k * 1.5, 1.8, 'feather', { rot: [180, 0, (8 + k * 5) * s], pivot: [fx + 0.85 * s, -3, 3.1], seed: 71 + k }, 2);
		}
	}
}
buildAnkh();

// ================================================================ textures
const BASE = [30, 14, 48], BASE_HI = [64, 34, 98], DEEP = [8, 3, 14], VIOLET = [104, 58, 160];
function put(col, a, glow, ga) { return [col.map(v => clamp(Math.round(v))), a, glow, ga]; }
function shade(c, f, s, t, p, info) {
	const n = fbm(p[0] * 0.42 + c.seed * 1.7, p[1] * 0.42, p[2] * 0.42 + c.seed * 0.3, 4);
	const edge = info.ePx;
	const up = f === 'up', down = f === 'down';
	const tip = (c.tag || '').endsWith('tip');
	switch (c.mat) {
		case 'eye': {
			const core = c.tag === 'core';
			const d = Math.min(1, Math.abs(t - 0.5) * 2 * (core ? 0.8 : 1.2) + Math.abs(s - 0.5) * (core ? 0.6 : 1.1));
			const col = mix([255, 255, 236], [255, 206, 84], smooth(0.2, 1.0, d));
			return put(col, 255, col, 255);
		}
		case 'bloom': { // glow-only halo: base texel cleared by GeckoLib, a soft additive ring here
			const d = Math.hypot((s - 0.5) * 2, (t - 0.5) * 2);
			const a = Math.round(150 * (1 - smooth(0.35, 1.0, d)));
			return put([255, 228, 120], 0, a > 8 ? [255, 228, 120] : null, a);
		}
		case 'beak': {
			let col = mix(DEEP, [36, 22, 52], 0.5 + 0.5 * n);
			if (up) col = mix(col, [80, 56, 110], 0.35 * (1 - Math.abs(s - 0.5) * 2));
			if (tip) col = mix(col, [70, 50, 96], 0.4);
			if (edge < 0.3) col = mix(col, DEEP, 0.5);
			return put(col, 255);
		}
		case 'mask': return put(mix(DEEP, [20, 10, 30], n), 255);
		case 'claw': {
			const col = tip ? mix([120, 90, 170], [226, 206, 255], t) : mix([22, 12, 32], [70, 48, 104], n);
			return put(col, 255, tip ? [170, 130, 240] : null, tip ? 150 : 0);
		}
		case 'spike': {
			const col = tip ? mix([180, 40, 80], [255, 120, 150], 1 - t) : mix([26, 6, 22], [90, 20, 50], n);
			return put(col, 255, tip ? [255, 70, 110] : null, tip ? 200 : 0);
		}
		case 'feather': {
			let col = mix(DEEP, BASE, 0.4 + 0.6 * n);
			if ((f === 'north' || f === 'south') && Math.abs(s - 0.5) < 0.1) col = mix(col, VIOLET, 0.35); // the quill
			if (tip) col = mix(col, VIOLET, 0.35);
			if (edge < 0.35) col = mix(col, DEEP, 0.5);
			return put(col, tip ? 215 : 240);
		}
		case 'wisp': {
			// smoke: darker and thinner toward the frayed end (the last segment is the "tip")
			let col = mix(DEEP, BASE, n * 0.8);
			const a = tip ? 120 + 60 * n : 205;
			return put(col, Math.round(a));
		}
		case 'shell': {
			// Black Ankh: glossier, layered
			let col = mix(BASE, BASE_HI, smooth(0.3, 0.85, n));
			if (up) col = mix(col, BASE_HI, 0.35);
			if (down) col = mix(col, DEEP, 0.6);
			if (edge < 0.4) col = mix(col, DEEP, 0.55);
			if (Math.floor(p[1] * 1.3 + (Math.floor(p[0] * 0.8) % 2) * 0.5) % 2 === 0 && !up && !down) col = mix(col, VIOLET, 0.12); // scallops
			return put(col, 255);
		}
		default: { // living shadow: soft purple gradients, darker toward the edges, a touch see-through
			let col = mix(BASE, BASE_HI, smooth(0.32, 0.9, n));
			col = mix(col, VIOLET, smooth(0.78, 0.98, n) * 0.35); // drifting lighter swirls
			if (up) col = mix(col, BASE_HI, 0.25);
			if (down) col = mix(col, DEEP, 0.55);
			const e = smooth(0.0, 0.9, edge);
			col = mix(DEEP, col, 0.45 + 0.55 * e);
			return put(col, Math.round(200 + 45 * e));
		}
	}
}
for (const m of [M, A]) { m.layout(); m.paint(shade); }

// ================================================================ clips
const HIDE = [[0, [0.01, 0.01, 0.01]]];
const EXTRAS_C = ['wings', 'spikes', 'spikes_mid', 'head_spikes'];
function clip(len, loop, bones, opts = {}, extras = EXTRAS_C) {
	const b = Object.assign({}, bones);
	for (const e of extras) if (!b[e] || !b[e].s) b[e] = Object.assign({}, b[e] || {}, { s: HIDE });
	return { len, loop, bones: b, hold: opts.hold };
}
/**
 * Re-bases a reaching clip's arm / forearm keys from the old forward rest onto the new hanging one (ARM_REST), so its
 * poses are unchanged. In a one-shot clip ({@code settle}) the closing [0,0,0] key stays 0, so it eases back to the NEW
 * rest -- the hands drop back down at its sides instead of returning to his face.
 */
function keep(bones, len, settle) {
	for (const [side, s] of [['l', 1], ['r', -1]]) {
		for (const [b, o, n] of [['arm_' + side, OLD_ARM_REST(s), ARM_REST(s)], ['forearm_' + side, OLD_FOREARM_REST(s), FOREARM_REST(s)]]) {
			const ch = bones[b];
			if (!ch || !ch.r) continue;
			const d = o.map((v, j) => v - n[j]);
			ch.r = ch.r.map(([t, v]) => (settle && t >= len - 1e-6 && v.every(q => q === 0)) ? [t, v] : [t, v.map((q, j) => q + d[j])]);
		}
	}
	return bones;
}
/** A travelling sway: one channel per chain bone, phase-shifted, so the body moves like a snake. */
function wave(bones, len, amp, axis, phaseStep, extra = {}) {
	const out = {};
	bones.forEach((b, i) => {
		const keys = [];
		for (let k = 0; k <= 8; k++) {
			const t = len * k / 8;
			const v = [0, 0, 0];
			v[axis] = amp[i] * Math.sin(2 * Math.PI * (k / 8) - i * phaseStep);
			if (extra[b]) for (let j = 0; j < 3; j++) v[j] += extra[b][j];
			keys.push([t, v]);
		}
		out[b] = { r: keys };
	});
	return out;
}
function merge(...xs) {
	const o = {};
	for (const x of xs) for (const [b, ch] of Object.entries(x)) {
		o[b] = Object.assign({}, o[b] || {});
		for (const [k, v] of Object.entries(ch)) {
			if (o[b][k] && k === 'r') { // add two rotation channels with the same key times
				o[b][k] = o[b][k].map(([t, a], i) => [t, a.map((q, j) => q + ((v[i] || v[v.length - 1])[1][j]))]);
			} else o[b][k] = v;
		}
	}
	return o;
}
const CHAIN = ['spine1', 'spine2', 'spine3', 'chest', 'neck1', 'neck2'];
const C = {};
const fingers = (side, v) => Object.fromEntries([1, 2, 3].flatMap(k => [[`finger_${side}${k}`, { r: v.f }], [`talon_${side}${k}`, { r: v.t }]]));
// idle: the slow serpentine sway, arms drifting, claws flexing, a low mutter of the beak
C.idle = clip(3.2, true, merge(
	wave(CHAIN, 3.2, [5, 6, 6, 5, 6, 6], 2, 0.7),
	wave(CHAIN, 3.2, [2, 3, 3, 3, 4, 3], 0, 0.9),
	{
		float: { p: [[0, [0, 0, 0]], [1.6, [0, 1.0, 0.2]], [3.2, [0, 0, 0]]] },
		tail: { r: [[0, [0, 0, -6]], [1.6, [6, 0, 6]], [3.2, [0, 0, -6]]] },
		arm_l: { r: [[0, [0, 0, 0]], [1.6, [-8, 6, -4]], [3.2, [0, 0, 0]]] },
		arm_r: { r: [[0, [-6, 0, 3]], [1.6, [2, -6, 0]], [3.2, [-6, 0, 3]]] },
		forearm_l: { r: [[0, [0, 0, 0]], [1.6, [-10, 0, 0]], [3.2, [0, 0, 0]]] },
		forearm_r: { r: [[0, [-8, 0, 0]], [1.6, [0, 0, 0]], [3.2, [-8, 0, 0]]] },
		jaw: { r: [[0, [0, 0, 0]], [0.4, [9, 0, 0]], [0.6, [0, 0, 0]], [2.2, [0, 0, 0]], [2.4, [6, 0, 0]], [2.6, [0, 0, 0]], [3.2, [0, 0, 0]]] },
		hood: { r: [[0, [0, 0, 0]], [1.6, [-6, 0, 0]], [3.2, [0, 0, 0]]] },
	},
	fingers('l', { f: [[0, [0, 0, 0]], [1.0, [18, 0, 0]], [1.8, [0, 0, 0]], [3.2, [0, 0, 0]]], t: [[0, [0, 0, 0]], [1.0, [20, 0, 0]], [1.8, [0, 0, 0]], [3.2, [0, 0, 0]]] }),
	fingers('r', { f: [[0, [0, 0, 0]], [2.0, [0, 0, 0]], [2.6, [18, 0, 0]], [3.2, [0, 0, 0]]], t: [[0, [0, 0, 0]], [2.0, [0, 0, 0]], [2.6, [20, 0, 0]], [3.2, [0, 0, 0]]] }),
));
// timid (daylight): shrunk down, hunched behind its user's shoulder, peeking, arms hugging itself
C.timid = clip(2.4, true, merge(
	wave(CHAIN, 2.4, [2, 2, 2, 2, 3, 3], 2, 0.7),
	{
		float: { p: [[0, [0, -1, 2]], [1.2, [0, -0.5, 2]], [2.4, [0, -1, 2]]] },
		spine1: { r: [[0, [-12, 0, 0]], [2.4, [-12, 0, 0]]] },
		chest: { r: [[0, [14, 0, 0]], [2.4, [14, 0, 0]]] },
		neck1: { r: [[0, [10, 0, 0]], [2.4, [10, 0, 0]]] },
		neck2: { r: [[0, [6, -12, 0]], [1.2, [6, 12, 0]], [2.4, [6, -12, 0]]] },
		// v0.16.7 (user): the hands clutch its own chest (elbows tucked in at its sides), never reaching round his face
		arm_l: { r: [[0, [-14, 0, 14]], [1.2, [-18, 0, 12]], [2.4, [-14, 0, 14]]] },
		arm_r: { r: [[0, [-14, 0, -4]], [1.2, [-10, 0, -6]], [2.4, [-14, 0, -4]]] },
		forearm_l: { r: [[0, [-95, -20, 0]], [1.2, [-100, -20, 0]], [2.4, [-95, -20, 0]]] },
		forearm_r: { r: [[0, [-95, 20, 0]], [1.2, [-90, 20, 0]], [2.4, [-95, 20, 0]]] },
		hood: { r: [[0, [14, 0, 0]], [2.4, [14, 0, 0]]] },
	},
	fingers('l', { f: [[0, [35, 0, 0]]], t: [[0, [35, 0, 0]]] }), fingers('r', { f: [[0, [35, 0, 0]]], t: [[0, [35, 0, 0]]] }),
));
// strike: rears, the claw wound back overhead, then a long lunging rake (Java stretches it out to the target)
C.strike = clip(0.6, false, merge({
	float: { p: [[0, [0, 2, 3]], [0.12, [0, 0, -6]], [0.3, [0, -0.5, -5]], [0.6, [0, 0, 0]]] },
	spine1: { r: [[0, [-14, 0, 0]], [0.12, [8, 0, 0]], [0.6, [0, 0, 0]]] },
	spine3: { r: [[0, [-10, 6, 0]], [0.12, [6, -6, 0]], [0.6, [0, 0, 0]]] },
	chest: { r: [[0, [-12, 12, 0]], [0.12, [8, -16, 0]], [0.3, [4, -8, 0]], [0.6, [0, 0, 0]]] },
	arm_r: { r: [[0, [-140, 0, 40]], [0.12, [-55, 0, -10]], [0.3, [20, 0, -20]], [0.6, [0, 0, 0]]] },
	forearm_r: { r: [[0, [-45, 0, 0]], [0.12, [30, 0, 0]], [0.3, [10, 0, 0]], [0.6, [0, 0, 0]]] },
	arm_l: { r: [[0, [-20, 0, -30]], [0.12, [-40, 0, -10]], [0.6, [0, 0, 0]]] },
	neck1: { r: [[0, [-18, 0, 0]], [0.12, [-4, 0, 0]], [0.6, [0, 0, 0]]] },
	jaw: { r: [[0, [6, 0, 0]], [0.1, [40, 0, 0]], [0.3, [30, 0, 0]], [0.6, [0, 0, 0]]] },
	tail: { r: [[0, [18, 0, 0]], [0.12, [-25, 0, 0]], [0.6, [0, 0, 0]]] },
	hood: { r: [[0, [10, 0, 0]], [0.12, [-22, 0, 0]], [0.6, [0, 0, 0]]] },
}, fingers('r', { f: [[0, [-30, 0, 0]], [0.12, [-35, 0, 0]], [0.25, [40, 0, 0]], [0.6, [0, 0, 0]]], t: [[0, [-20, 0, 0]], [0.12, [-25, 0, 0]], [0.25, [45, 0, 0]], [0.6, [0, 0, 0]]] })));
// bite (Dark Fang): four savage snaps, the head driving forward each time, both hands clutching
const biteK = (a, b) => { const o = []; for (let i = 0; i < 4; i++) { o.push([i * 0.2, a(i)], [i * 0.2 + 0.08, b(i)]); } o.push([0.8, a(0)]); return o; };
C.bite = clip(0.8, true, merge({
	float: { p: biteK(() => [0, 0.5, -2], () => [0, -0.6, -6]) },
	neck1: { r: biteK(i => [-8, i % 2 ? 6 : -6, 0], i => [4, i % 2 ? -4 : 4, 0]) },
	neck2: { r: biteK(() => [-16, 0, 0], () => [8, 0, 0]) },
	jaw: { r: biteK(() => [48, 0, 0], () => [0, 0, 0]) },
	beak: { r: biteK(() => [-12, 0, 0], () => [0, 0, 0]) },
	chest: { r: [[0, [6, 0, 0]], [0.8, [6, 0, 0]]] },
	arm_l: { r: [[0, [-80, 0, -10]], [0.4, [-72, 0, -16]], [0.8, [-80, 0, -10]]] },
	arm_r: { r: [[0, [-72, 0, 16]], [0.4, [-80, 0, 10]], [0.8, [-72, 0, 16]]] },
	forearm_l: { r: [[0, [10, 0, 0]], [0.8, [10, 0, 0]]] },
	forearm_r: { r: [[0, [10, 0, 0]], [0.8, [10, 0, 0]]] },
	tail: { r: [[0, [-15, 0, 6]], [0.4, [-15, 0, -6]], [0.8, [-15, 0, 6]]] },
	hood: { r: [[0, [-20, 0, 0]], [0.8, [-20, 0, 0]]] },
}, fingers('l', { f: [[0, [40, 0, 0]], [0.8, [40, 0, 0]]], t: [[0, [40, 0, 0]], [0.8, [40, 0, 0]]] }), fingers('r', { f: [[0, [40, 0, 0]], [0.8, [40, 0, 0]]], t: [[0, [40, 0, 0]], [0.8, [40, 0, 0]]] })));
// grab: both huge hands thrown open at the prey, then clamped shut round it -- held while it holds on
C.grab = clip(0.6, false, merge({
	float: { p: [[0, [0, 1, 1]], [0.15, [0, 0, -5]], [0.6, [0, 0.5, -3]]] },
	chest: { r: [[0, [-12, 0, 0]], [0.15, [24, 0, 0]], [0.6, [14, 0, 0]]] },
	arm_l: { r: [[0, [-60, -30, -40]], [0.15, [-100, -20, -10]], [0.35, [-85, 14, 10]], [0.6, [-80, 16, 12]]] },
	arm_r: { r: [[0, [-60, 30, 40]], [0.15, [-100, 20, 10]], [0.35, [-85, -14, -10]], [0.6, [-80, -16, -12]]] },
	forearm_l: { r: [[0, [-10, 0, 0]], [0.15, [30, 0, 0]], [0.35, [-20, 0, 0]], [0.6, [-24, 0, 0]]] },
	forearm_r: { r: [[0, [-10, 0, 0]], [0.15, [30, 0, 0]], [0.35, [-20, 0, 0]], [0.6, [-24, 0, 0]]] },
	jaw: { r: [[0, [32, 0, 0]], [0.25, [12, 0, 0]], [0.6, [10, 0, 0]]] },
	neck1: { r: [[0, [-10, 0, 0]], [0.15, [12, 0, 0]], [0.6, [8, 0, 0]]] },
}, fingers('l', { f: [[0, [-40, 0, 0]], [0.2, [-40, 0, 0]], [0.3, [55, 0, 0]], [0.6, [55, 0, 0]]], t: [[0, [-30, 0, 0]], [0.2, [-30, 0, 0]], [0.3, [50, 0, 0]], [0.6, [50, 0, 0]]] }),
	fingers('r', { f: [[0, [-40, 0, 0]], [0.2, [-40, 0, 0]], [0.3, [55, 0, 0]], [0.6, [55, 0, 0]]], t: [[0, [-30, 0, 0]], [0.2, [-30, 0, 0]], [0.3, [50, 0, 0]], [0.6, [50, 0, 0]]] })), { hold: true });
// slam: hoisted overhead, then driven into the ground
C.slam = clip(0.55, false, merge({
	float: { p: [[0, [0, 3, 0]], [0.18, [0, 5, 1]], [0.3, [0, -3, -4]], [0.55, [0, 0, 0]]] },
	spine1: { r: [[0, [-12, 0, 0]], [0.18, [-18, 0, 0]], [0.3, [24, 0, 0]], [0.55, [0, 0, 0]]] },
	chest: { r: [[0, [-20, 0, 0]], [0.18, [-28, 0, 0]], [0.3, [40, 0, 0]], [0.55, [0, 0, 0]]] },
	arm_l: { r: [[0, [-160, 0, -10]], [0.18, [-178, 0, -5]], [0.3, [-25, 0, -8]], [0.55, [0, 0, 0]]] },
	arm_r: { r: [[0, [-160, 0, 10]], [0.18, [-178, 0, 5]], [0.3, [-25, 0, 8]], [0.55, [0, 0, 0]]] },
	neck1: { r: [[0, [-20, 0, 0]], [0.3, [25, 0, 0]], [0.55, [0, 0, 0]]] },
	jaw: { r: [[0, [10, 0, 0]], [0.3, [44, 0, 0]], [0.55, [0, 0, 0]]] },
	tail: { r: [[0, [20, 0, 0]], [0.3, [-25, 0, 0]], [0.55, [0, 0, 0]]] },
}, fingers('l', { f: [[0, [55, 0, 0]], [0.55, [0, 0, 0]]], t: [[0, [50, 0, 0]], [0.55, [0, 0, 0]]] }), fingers('r', { f: [[0, [55, 0, 0]], [0.55, [0, 0, 0]]], t: [[0, [50, 0, 0]], [0.55, [0, 0, 0]]] })));
// shield: it coils round its user, wings swept closed in front like a cloak, eyes peering over the top
C.shield = clip(1.6, true, {
	float: { p: [[0, [0, -2, -2]], [0.8, [0, -1.6, -2]], [1.6, [0, -2, -2]]] },
	spine1: { r: [[0, [10, 0, 0]], [1.6, [10, 0, 0]]] },
	chest: { r: [[0, [6, 0, 0]], [1.6, [6, 0, 0]]] },
	wings: { s: [[0, [1.35, 1.35, 1.35]]] },
	wing_l: { r: [[0, [0, -78, -10]], [0.8, [0, -82, -6]], [1.6, [0, -78, -10]]] },
	wing_r: { r: [[0, [0, 78, 10]], [0.8, [0, 82, 6]], [1.6, [0, 78, 10]]] },
	wing_l2: { r: [[0, [0, -75, 0]], [1.6, [0, -75, 0]]] },
	wing_r2: { r: [[0, [0, 75, 0]], [1.6, [0, 75, 0]]] },
	// v0.16.7 (user): the arms wrap LOW round his waist (they were level with his face)
	arm_l: { r: [[0, [-40, -35, 10]], [0.8, [-44, -38, 10]], [1.6, [-40, -35, 10]]] },
	arm_r: { r: [[0, [-40, 35, -10]], [0.8, [-44, 38, -10]], [1.6, [-40, 35, -10]]] },
	forearm_l: { r: [[0, [-35, 0, 0]], [1.6, [-35, 0, 0]]] },
	forearm_r: { r: [[0, [-35, 0, 0]], [1.6, [-35, 0, 0]]] },
	neck1: { r: [[0, [14, 0, 0]], [1.6, [14, 0, 0]]] },
	hood: { r: [[0, [-20, 0, 0]], [1.6, [-20, 0, 0]]] },
});
// berserk: towering, spiked, flailing -- claws raking every way, the beak snapping, the head whipping about
C.berserk = clip(1.0, true, merge(
	wave(CHAIN, 1.0, [10, 12, 12, 10, 14, 14], 2, 1.1),
	{
		spikes: { s: [[0, [1, 1, 1]], [0.5, [1.15, 1.15, 1.15]], [1.0, [1, 1, 1]]] },
		spikes_mid: { s: [[0, [1, 1, 1]], [0.5, [1.15, 1.15, 1.15]], [1.0, [1, 1, 1]]] },
		head_spikes: { s: [[0, [1, 1, 1]], [0.5, [1.2, 1.2, 1.2]], [1.0, [1, 1, 1]]] },
		float: { p: [[0, [0, 1, 0]], [0.25, [-2, 2.5, -1]], [0.5, [1, 0, -2]], [0.75, [2, 2, 0]], [1.0, [0, 1, 0]]] },
		head: { r: [[0, [-10, 0, 10]], [0.25, [15, 0, -14]], [0.5, [-15, 0, 14]], [0.75, [10, 0, -10]], [1.0, [-10, 0, 10]]] },
		jaw: { r: [[0, [50, 0, 0]], [0.12, [8, 0, 0]], [0.25, [50, 0, 0]], [0.5, [50, 0, 0]], [0.62, [8, 0, 0]], [0.75, [50, 0, 0]], [1.0, [50, 0, 0]]] },
		// v0.16.7 (user): the claws rake overhead and out to the SIDES (the low forward swipes crossed his face)
		arm_l: { r: [[0, [-150, 0, -40]], [0.3, [-25, 0, -70]], [0.6, [-120, 0, -75]], [1.0, [-150, 0, -40]]] },
		arm_r: { r: [[0, [-25, 0, 70]], [0.35, [-155, 0, 40]], [0.7, [-15, 0, 75]], [1.0, [-25, 0, 70]]] },
		forearm_l: { r: [[0, [-50, 0, 0]], [0.3, [-10, 0, 0]], [1.0, [-50, 0, 0]]] },
		forearm_r: { r: [[0, [-10, 0, 0]], [0.35, [-50, 0, 0]], [1.0, [-10, 0, 0]]] },
		tail: { r: [[0, [-10, 0, 14]], [0.5, [-10, 0, -14]], [1.0, [-10, 0, 14]]] },
		hood: { r: [[0, [-30, 0, 0]], [0.5, [-10, 0, 0]], [1.0, [-30, 0, 0]]] },
	},
	fingers('l', { f: [[0, [-35, 0, 0]], [0.3, [50, 0, 0]], [1.0, [-35, 0, 0]]], t: [[0, [-20, 0, 0]], [0.3, [45, 0, 0]], [1.0, [-20, 0, 0]]] }),
	fingers('r', { f: [[0, [50, 0, 0]], [0.35, [-35, 0, 0]], [1.0, [50, 0, 0]]], t: [[0, [45, 0, 0]], [0.35, [-20, 0, 0]], [1.0, [45, 0, 0]]] }),
));
// nova: it curls tight round itself, then bursts open -- wings and claws flung wide, a scream
C.nova = clip(0.9, false, {
	float: { p: [[0, [0, 0, 0]], [0.3, [0, -2, 0]], [0.42, [0, 2, 0]], [0.9, [0, 0, 0]]] },
	chest: { r: [[0, [0, 0, 0]], [0.3, [40, 0, 0]], [0.42, [-25, 0, 0]], [0.9, [0, 0, 0]]] },
	spine1: { r: [[0, [0, 0, 0]], [0.3, [20, 0, 0]], [0.42, [-15, 0, 0]], [0.9, [0, 0, 0]]] },
	wings: { s: [[0, [0.01, 0.01, 0.01]], [0.3, [0.3, 0.3, 0.3]], [0.42, [1.5, 1.5, 1.5]], [0.75, [1.4, 1.4, 1.4]], [0.9, [0.01, 0.01, 0.01]]] },
	wing_l: { r: [[0, [0, 0, -40]], [0.42, [0, 0, 30]], [0.9, [0, 0, 10]]] },
	wing_r: { r: [[0, [0, 0, 40]], [0.42, [0, 0, -30]], [0.9, [0, 0, -10]]] },
	arm_l: { r: [[0, [0, 0, 0]], [0.3, [-60, -40, 30]], [0.42, [-40, 0, -95]], [0.9, [0, 0, 0]]] },
	arm_r: { r: [[0, [0, 0, 0]], [0.3, [-60, 40, -30]], [0.42, [-40, 0, 95]], [0.9, [0, 0, 0]]] },
	neck1: { r: [[0, [0, 0, 0]], [0.3, [35, 0, 0]], [0.42, [-35, 0, 0]], [0.9, [0, 0, 0]]] },
	jaw: { r: [[0, [0, 0, 0]], [0.3, [0, 0, 0]], [0.42, [50, 0, 0]], [0.7, [38, 0, 0]], [0.9, [0, 0, 0]]] },
	spikes: { s: [[0, [0.01, 0.01, 0.01]], [0.42, [0.9, 0.9, 0.9]], [0.75, [0.6, 0.6, 0.6]], [0.9, [0.01, 0.01, 0.01]]] },
	hood: { r: [[0, [0, 0, 0]], [0.42, [-35, 0, 0]], [0.9, [0, 0, 0]]] },
});
// scout: it flies off on its own wings, body streaming out behind
C.scout = clip(0.8, true, merge(wave(CHAIN, 0.8, [6, 8, 8, 6, 4, 4], 2, 0.9), {
	float: { p: [[0, [0, 0, 0]], [0.4, [0, 1.2, 0]], [0.8, [0, 0, 0]]] },
	spine1: { r: [[0, [60, 0, 0]], [0.8, [60, 0, 0]]] },
	neck1: { r: [[0, [-30, 0, 0]], [0.8, [-30, 0, 0]]] },
	tail: { r: [[0, [-35, 0, 6]], [0.4, [-35, 0, -6]], [0.8, [-35, 0, 6]]] },
	arm_l: { r: [[0, [40, 0, -10]], [0.8, [40, 0, -10]]] },
	arm_r: { r: [[0, [40, 0, 10]], [0.8, [40, 0, 10]]] },
	wings: { s: [[0, [1.2, 1.2, 1.2]]] },
	wing_l: { r: [[0, [0, 0, 30]], [0.35, [0, 0, -25]], [0.8, [0, 0, 30]]] },
	wing_r: { r: [[0, [0, 0, -30]], [0.35, [0, 0, 25]], [0.8, [0, 0, -30]]] },
	wing_l2: { r: [[0, [0, 0, -8]], [0.2, [0, 0, 18]], [0.5, [0, 0, -20]], [0.8, [0, 0, -8]]] },
	wing_r2: { r: [[0, [0, 0, 8]], [0.2, [0, 0, -18]], [0.5, [0, 0, 20]], [0.8, [0, 0, 8]]] },
	hood: { r: [[0, [-30, 0, 0]], [0.8, [-30, 0, 0]]] },
}));
// fetch: one long arm shot out, the claw snapping shut on the prize, hauled back
C.fetch = clip(0.7, false, merge({
	float: { p: [[0, [0, 0, 0]], [0.15, [0, -0.5, -5]], [0.7, [0, 0, 0]]] },
	chest: { r: [[0, [0, 0, 0]], [0.15, [24, -12, 0]], [0.45, [10, 8, 0]], [0.7, [0, 0, 0]]] },
	arm_r: { r: [[0, [-40, 0, 20]], [0.15, [-105, -10, 0]], [0.3, [-105, -10, 0]], [0.5, [-40, 20, 20]], [0.7, [0, 0, 0]]] },
	forearm_r: { r: [[0, [-40, 0, 0]], [0.15, [30, 0, 0]], [0.35, [30, 0, 0]], [0.5, [-60, 0, 0]], [0.7, [0, 0, 0]]] },
	neck1: { r: [[0, [0, 0, 0]], [0.15, [15, 0, 0]], [0.7, [0, 0, 0]]] },
}, fingers('r', { f: [[0, [0, 0, 0]], [0.15, [-40, 0, 0]], [0.25, [55, 0, 0]], [0.5, [55, 0, 0]], [0.7, [0, 0, 0]]], t: [[0, [0, 0, 0]], [0.15, [-30, 0, 0]], [0.25, [50, 0, 0]], [0.5, [50, 0, 0]], [0.7, [0, 0, 0]]] })));
// recall (v0.16.7 user: N hides it): a startled flinch, then it folds up -- arms hugged in, the head ducked, the body
// coiling down -- and pours back into its user's back, the smoky tail drawn in last
C.recall = clip(0.6, false, merge({
	float: { p: [[0, [0, 0, 0]], [0.12, [0, 1.2, 0.5]], [0.6, [0, -7, 1.5]]], s: [[0, [1, 1, 1]], [0.12, [1.06, 1.08, 1.06]], [0.3, [0.85, 0.7, 0.85]], [0.6, [0.01, 0.01, 0.01]]] },
	spine1: { r: [[0, [0, 0, 0]], [0.12, [-8, 0, 0]], [0.45, [28, 0, 0]], [0.6, [32, 0, 0]]] },
	spine3: { r: [[0, [0, 0, 0]], [0.45, [22, 0, 0]], [0.6, [24, 0, 0]]] },
	chest: { r: [[0, [0, 0, 0]], [0.12, [-10, 0, 0]], [0.4, [32, 0, 0]], [0.6, [36, 0, 0]]] },
	neck1: { r: [[0, [0, 0, 0]], [0.12, [-14, 0, 0]], [0.4, [42, 0, 0]], [0.6, [46, 0, 0]]] },
	neck2: { r: [[0, [0, 0, 0]], [0.4, [20, 0, 0]], [0.6, [24, 0, 0]]] },
	arm_l: { r: [[0, [0, 0, 0]], [0.12, [-6, 0, -12]], [0.4, [-12, 0, 16]], [0.6, [-12, 0, 18]]] },
	arm_r: { r: [[0, [0, 0, 0]], [0.12, [-6, 0, 12]], [0.4, [-12, 0, -6]], [0.6, [-12, 0, -8]]] },
	forearm_l: { r: [[0, [0, 0, 0]], [0.4, [-100, -20, 0]], [0.6, [-110, -20, 0]]] },
	forearm_r: { r: [[0, [0, 0, 0]], [0.4, [-100, 20, 0]], [0.6, [-110, 20, 0]]] },
	jaw: { r: [[0, [0, 0, 0]], [0.08, [26, 0, 0]], [0.25, [0, 0, 0]], [0.6, [0, 0, 0]]] },
	hood: { r: [[0, [0, 0, 0]], [0.12, [-18, 0, 0]], [0.45, [20, 0, 0]], [0.6, [22, 0, 0]]] },
	tail: { r: [[0, [0, 0, 0]], [0.3, [20, 0, 0]], [0.6, [40, 0, 0]]] },
}, fingers('l', { f: [[0, [0, 0, 0]], [0.4, [50, 0, 0]], [0.6, [55, 0, 0]]], t: [[0, [0, 0, 0]], [0.4, [45, 0, 0]], [0.6, [50, 0, 0]]] }),
	fingers('r', { f: [[0, [0, 0, 0]], [0.4, [50, 0, 0]], [0.6, [55, 0, 0]]], t: [[0, [0, 0, 0]], [0.4, [45, 0, 0]], [0.6, [50, 0, 0]]] })), { hold: true });
// summon: it boils up out of its user's back and unfurls with a cry
C.summon = clip(0.7, false, {
	float: { p: [[0, [0, -6, 1]], [0.35, [0, 1.5, 0]], [0.7, [0, 0, 0]]], s: [[0, [0.01, 0.01, 0.01]], [0.3, [1.15, 1.15, 1.15]], [0.7, [1, 1, 1]]] },
	chest: { r: [[0, [30, 0, 0]], [0.3, [-20, 0, 0]], [0.7, [0, 0, 0]]] },
	arm_l: { r: [[0, [-40, 0, 0]], [0.35, [-120, 0, -50]], [0.7, [0, 0, 0]]] },
	arm_r: { r: [[0, [-40, 0, 0]], [0.35, [-120, 0, 50]], [0.7, [0, 0, 0]]] },
	neck1: { r: [[0, [40, 0, 0]], [0.35, [-30, 0, 0]], [0.7, [0, 0, 0]]] },
	jaw: { r: [[0, [0, 0, 0]], [0.3, [44, 0, 0]], [0.55, [30, 0, 0]], [0.7, [0, 0, 0]]] },
	hood: { r: [[0, [0, 0, 0]], [0.35, [-30, 0, 0]], [0.7, [0, 0, 0]]] },
});
// calm: the light hits it -- it recoils, turns its head away and hugs itself small with a whimper (v0.16.7 user: the
// hands no longer come up over its face -- they clutch its chest, then sink back to its sides)
C.calm = clip(1.4, false, merge({
	float: { p: [[0, [0, 2, 1]], [0.25, [0, -1, 4]], [1.4, [0, -0.5, 2]]] },
	spine1: { r: [[0, [-10, 0, 0]], [0.25, [-30, 0, 0]], [1.4, [-15, 0, 0]]] },
	chest: { r: [[0, [-25, 0, 0]], [0.25, [30, 0, 0]], [1.4, [12, 0, 0]]] },
	neck1: { r: [[0, [-35, 0, 0]], [0.25, [35, 0, 0]], [0.9, [28, 0, 0]], [1.4, [10, 0, 0]]] },
	neck2: { r: [[0, [0, 0, 0]], [0.25, [0, 30, 0]], [0.5, [0, 18, 0]], [0.8, [0, 34, 0]], [1.1, [0, 20, 0]], [1.4, [0, 0, 0]]] },
	arm_l: { r: [[0, [-30, 0, -30]], [0.25, [-18, 0, 16]], [0.9, [-16, 0, 14]], [1.4, [0, 0, 0]]] },
	arm_r: { r: [[0, [-30, 0, 30]], [0.25, [-18, 0, -6]], [0.9, [-16, 0, -4]], [1.4, [0, 0, 0]]] },
	forearm_l: { r: [[0, [-20, 0, 0]], [0.25, [-105, -20, 0]], [0.9, [-100, -20, 0]], [1.4, [0, 0, 0]]] },
	forearm_r: { r: [[0, [-20, 0, 0]], [0.25, [-105, 20, 0]], [0.9, [-100, 20, 0]], [1.4, [0, 0, 0]]] },
	jaw: { r: [[0, [40, 0, 0]], [0.2, [40, 0, 0]], [0.4, [4, 0, 0]], [0.6, [10, 0, 0]], [0.8, [3, 0, 0]], [1.4, [0, 0, 0]]] },
	hood: { r: [[0, [-35, 0, 0]], [0.25, [18, 0, 0]], [1.4, [10, 0, 0]]] },
	tail: { r: [[0, [0, 0, 0]], [0.25, [25, 0, 0]], [1.4, [10, 0, 0]]] },
}, fingers('l', { f: [[0, [20, 0, 0]], [1.4, [30, 0, 0]]], t: [[0, [20, 0, 0]], [1.4, [30, 0, 0]]] }), fingers('r', { f: [[0, [20, 0, 0]], [1.4, [30, 0, 0]]], t: [[0, [20, 0, 0]], [1.4, [30, 0, 0]]] })));

// the reaching / one-shot clips keep their authored arm poses on the new hanging rest (see keep())
for (const n of ['strike', 'bite', 'grab', 'slam', 'nova', 'scout', 'fetch', 'summon']) keep(C[n].bones, C[n].len, !C[n].loop && !C[n].hold);

// ---- the Black Ankh parts' clips (wings hidden unless Abyssal)
const AE = ['wings'];
const CA = {};
CA.idle = clip(2.4, true, {
	plume_l: { r: [[0, [0, 0, 0]], [1.2, [0, 0, 4]], [2.4, [0, 0, 0]]] },
	plume_r: { r: [[0, [0, 0, 0]], [1.2, [0, 0, -4]], [2.4, [0, 0, 0]]] },
	gauntlet_l_claws: { r: [[0, [0, 0, 0]], [1.0, [12, 0, 0]], [1.6, [0, 0, 0]], [2.4, [0, 0, 0]]] },
	gauntlet_r_claws: { r: [[0, [0, 0, 0]], [1.6, [0, 0, 0]], [2.0, [12, 0, 0]], [2.4, [0, 0, 0]]] },
}, {}, AE);
CA.wings = clip(1.1, true, {
	wings: { s: [[0, [1.45, 1.45, 1.45]]] },
	wing_l: { r: [[0, [0, 20, 30]], [0.5, [0, -6, -28]], [1.1, [0, 20, 30]]] },
	wing_r: { r: [[0, [0, -20, -30]], [0.5, [0, 6, 28]], [1.1, [0, -20, -30]]] },
	wing_l2: { r: [[0, [0, 0, -8]], [0.22, [0, 0, 20]], [0.6, [0, 0, -24]], [1.1, [0, 0, -8]]] },
	wing_r2: { r: [[0, [0, 0, 8]], [0.22, [0, 0, -20]], [0.6, [0, 0, 24]], [1.1, [0, 0, 8]]] },
	plume_l: { r: [[0, [0, 0, 6]], [1.1, [0, 0, 6]]] },
	plume_r: { r: [[0, [0, 0, -6]], [1.1, [0, 0, -6]]] },
}, {}, AE);
// gliding: wings held wide and still, tips trembling
CA.glide = clip(1.6, true, {
	wings: { s: [[0, [1.45, 1.45, 1.45]]] },
	wing_l: { r: [[0, [0, 8, 4]], [0.8, [0, 6, 7]], [1.6, [0, 8, 4]]] },
	wing_r: { r: [[0, [0, -8, -4]], [0.8, [0, -6, -7]], [1.6, [0, -8, -4]]] },
	wing_l2: { r: [[0, [0, 0, -4]], [0.4, [0, 0, 2]], [0.8, [0, 0, -4]], [1.2, [0, 0, 2]], [1.6, [0, 0, -4]]] },
	wing_r2: { r: [[0, [0, 0, 4]], [0.4, [0, 0, -2]], [0.8, [0, 0, 4]], [1.2, [0, 0, -2]], [1.6, [0, 0, 4]]] },
}, {}, AE);

// ================================================================ writers
const fmt = n => Math.round(n * 1000) / 1000;
function animJson(model, clips, prefix) {
	const out = { format_version: '1.8.0', animations: {} };
	for (const [name, c] of Object.entries(clips)) {
		const a = { animation_length: c.len };
		if (c.loop) a.loop = true;
		else if (c.hold) a.loop = 'hold_on_last_frame';
		a.bones = {};
		for (const [bone, ch] of Object.entries(c.bones)) {
			if (!model.B[bone]) throw new Error(`clip ${name}: no bone ${bone}`);
			if (bone === 'look') throw new Error('look is never keyed');
			const o = {};
			for (const [k, key] of [['r', 'rotation'], ['p', 'position'], ['s', 'scale']]) {
				if (!ch[k]) continue;
				const frames = {};
				for (const [t, v] of ch[k]) frames[fmt(t).toFixed(3)] = { vector: v.map(fmt), easing: 'easeInOutSine' };
				o[key] = frames;
			}
			a.bones[bone] = o;
		}
		out.animations[`animation.${prefix}.${name}`] = a;
	}
	return out;
}
function sample(c, t) {
	const pose = {};
	const ev = ch => {
		if (t <= ch[0][0]) return ch[0][1];
		for (let i = 0; i < ch.length - 1; i++) if (t <= ch[i + 1][0]) {
			const a = ch[i][1], b = ch[i + 1][1], u = (t - ch[i][0]) / (ch[i + 1][0] - ch[i][0]);
			const e = 0.5 - 0.5 * Math.cos(Math.PI * u);
			return a.map((x, j) => x + (b[j] - x) * e);
		}
		return ch[ch.length - 1][1];
	};
	for (const [bone, ch] of Object.entries(c.bones)) {
		pose[bone] = {};
		if (ch.r) pose[bone].r = ev(ch.r);
		if (ch.p) pose[bone].p = ev(ch.p);
		if (ch.s) pose[bone].s = ev(ch.s);
	}
	return pose;
}
function tendril() {
	const W = 16, H = 16, px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const n = fbm(x * 0.4, y * 0.12, 3.3, 4);
		let col = mix(BASE, BASE_HI, smooth(0.3, 0.9, n));
		const e = Math.min(x, W - 1 - x) / 3;
		col = mix(DEEP, col, 0.45 + 0.55 * Math.min(1, e));
		const i = (y * W + x) * 4;
		px[i] = clamp(Math.round(col[0])); px[i + 1] = clamp(Math.round(col[1])); px[i + 2] = clamp(Math.round(col[2])); px[i + 3] = Math.round(190 + 50 * Math.min(1, e));
	}
	return L.encodePng(W, H, px);
}
function ankhShell() {
	const W = 64, H = 64, px = Buffer.alloc(W * H * 4);
	const regions = [[16, 16, 24, 16], [40, 16, 16, 16], [32, 48, 16, 16], [0, 16, 16, 16], [16, 48, 16, 16]];
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		if (!regions.some(([u, v, w, h]) => x >= u && x < u + w && y >= v && y < v + h)) continue;
		// overlapping feather scallops, three rows deep, offset every other row; a violet top edge to each
		const row = Math.floor(y / 3), cx = (x + (row % 2) * 2) % 4, cy = y % 3;
		const n = fbm(x * 0.3, y * 0.3, 9.1, 3);
		let col = mix(BASE, BASE_HI, n);
		if (cy === 0) col = mix(col, VIOLET, 0.4);
		if (cy === 2) col = mix(col, DEEP, 0.5);
		if (cx === 0) col = mix(col, DEEP, 0.35);
		const i = (y * W + x) * 4;
		px[i] = clamp(Math.round(col[0])); px[i + 1] = clamp(Math.round(col[1])); px[i + 2] = clamp(Math.round(col[2])); px[i + 3] = 255;
	}
	return L.encodePng(W, H, px);
}
function write(model, id, clips, prefix) {
	const geo = model.geoJson('geometry.' + id);
	const d = geo['minecraft:geometry'][0].description;
	d.visible_bounds_width = 8; d.visible_bounds_height = 6; d.visible_bounds_offset = [0, 1.5, 0];
	fs.mkdirSync(path.join(ASSETS, 'geo/quirk'), { recursive: true });
	fs.mkdirSync(path.join(ASSETS, 'animations/quirk'), { recursive: true });
	fs.writeFileSync(path.join(ASSETS, `geo/quirk/${id}.geo.json`), JSON.stringify(geo, null, '\t') + '\n');
	fs.writeFileSync(path.join(ASSETS, `animations/quirk/${id}.animation.json`), JSON.stringify(animJson(model, clips, prefix), null, '\t') + '\n');
}
fs.mkdirSync(TEXDIR, { recursive: true });
write(M, 'dark_shadow', C, 'dark_shadow');
write(A, 'dark_shadow_ankh', CA, 'dark_shadow_ankh');
fs.writeFileSync(path.join(TEXDIR, 'creature.png'), L.encodePng(M.TW, M.TH, M.tex));
fs.writeFileSync(path.join(TEXDIR, 'creature_glowmask.png'), L.encodePng(M.TW, M.TH, M.glow));
fs.writeFileSync(path.join(TEXDIR, 'ankh_parts.png'), L.encodePng(A.TW, A.TH, A.tex));
fs.writeFileSync(path.join(TEXDIR, 'ankh_parts_glowmask.png'), L.encodePng(A.TW, A.TH, A.glow));
fs.writeFileSync(path.join(TEXDIR, 'tendril.png'), tendril());
fs.writeFileSync(path.join(TEXDIR, 'ankh.png'), ankhShell());
console.log(`creature: ${M.bones.length} bones, ${M.cubes().length} cubes, ${M.TW}x${M.TH}, ${Object.keys(C).length} clips; ankh: ${A.bones.length} bones, ${A.cubes().length} cubes, ${A.TW}x${A.TH}`);

if (PREVIEW) {
	fs.mkdirSync(PREVIEW, { recursive: true });
	const tiny = (model, pose) => c => {
		if (c.mat === 'bloom') return true;
		let b = model.B[c.bone];
		while (b) { const q = pose[b.name]; if (q && q.s && q.s[0] < 0.05) return true; b = b.parent ? model.B[b.parent] : null; }
		return false;
	};
	const item = (model, pose) => ({ model, pose, scale: 1 / 16, pos: [0, 0, 0], yaw: 0, hide: tiny(model, pose) });
	const idle0 = sample(C.idle, 0);
	const views = [['front', 0, 8], ['three-quarter', 35, 10], ['side', 90, 6], ['back', 160, 10]];
	L.sheet(path.join(PREVIEW, 'ds_rest.png'), 'Dark Shadow', 'idle frame 0',
		views.map(([lab, yaw, pitch]) => ({ label: lab, items: [item(M, idle0)], cam: { yaw, pitch } })), { panelH: 760, minW: 1800 });
	const shots = (process.env.CLIPS || 'idle:0.8,timid:0,strike:0,strike:0.12,strike:0.3,bite:0,bite:0.08,grab:0.15,grab:0.6,slam:0.18,slam:0.3,shield:0,berserk:0,berserk:0.35,nova:0.3,nova:0.42,scout:0,fetch:0.15,recall:0.3,summon:0.35,calm:0.25,calm:0.9').split(',');
	const panels = shots.map(sx => {
		const [a, t] = sx.split(':');
		const pose = sample(C[a], +t);
		return { label: `${a} ${t}`, items: [item(M, pose)], cam: { yaw: +(process.env.YAW || 35), pitch: 10 }, ownScale: true };
	});
	for (let k = 0; k < panels.length; k += 8) L.sheet(path.join(PREVIEW, `ds_clips_${k / 8}.png`), 'Dark Shadow clips', '', panels.slice(k, k + 8), { panelH: 560, minW: 2400, maxW: 3600 });
	// v0.16.7 (user) "hands in his face" check: each pose WITH its user -- a plain player-sized stand-in placed exactly as
	// DarkShadowRender places him relative to the creature's root (size s; the shield / berserk roots differ)
	const U = new DSModel('user_standin', { D: 1, TW: 64 });
	U.bone('root', null, [0, 0, 0]);
	U.box('root', -4, 0, -1, -0.1, 12, 1, 'stand'); U.box('root', 0.1, 0, -1, 4, 12, 1, 'stand');
	U.box('root', -4, 12, -2, 4, 24, 2, 'stand'); U.box('root', -8, 12, -2, -4, 24, 2, 'stand'); U.box('root', 4, 12, -2, 8, 24, 2, 'stand');
	U.box('root', -4, 24, -4, 4, 32, 4, 'stand');
	U.box('root', -3, 27, -4.3, -1, 28, -4, 'eye'); U.box('root', 1, 27, -4.3, 3, 28, -4, 'eye');
	U.layout(); U.paint((c, f) => c.mat === 'eye' ? put([255, 220, 90], 255) : put([150 + (f === 'up' ? 40 : 0), 150, 140], 255));
	const userAt = (name, sz) => name === 'shield' ? [0, -0.55, -0.35] : name === 'berserk' ? [-0.3, -1.22, -0.52] : [-(0.34 + 0.16 * sz), -1.22, -(0.22 + 0.12 * sz)];
	const sizeOf = { idle: 1, timid: 0.45, shield: 0.85, berserk: 2.2, calm: 0.5, recall: 1, summon: 1, nova: 1, strike: 1, grab: 1, slam: 1, bite: 1, fetch: 1, scout: 1 };
	const withUser = (process.env.USER_CLIPS || 'idle:0,idle:1.6,timid:0,timid:1.2,shield:0,calm:0.5,calm:1.4,berserk:0,berserk:0.3,berserk:0.7,recall:0.12,recall:0.4,summon:0.35,summon:0.7,strike:0.6,slam:0.55').split(',');
	for (const [vlab, vyaw] of [['front', 0], ['three-quarter', 35], ['side', 90]]) {
		const ps = withUser.map(sx => {
			const [a, t] = sx.split(':');
			const sz = sizeOf[a] || 1;
			const pose = sample(C[a], +t);
			return { label: `${a} ${t}`, cam: { yaw: vyaw, pitch: 8 }, ownScale: true,
				items: [{ model: M, pose, scale: sz / 16, pos: [0, 0, 0], yaw: 0, hide: tiny(M, pose) },
					{ model: U, pose: {}, scale: 1 / 16, pos: userAt(a, sz), yaw: 0, floorR: 0.6 }] };
		});
		for (let k = 0; k < ps.length; k += 8) L.sheet(path.join(PREVIEW, `ds_user_${vlab}_${k / 8}.png`), 'Dark Shadow with its user', vlab, ps.slice(k, k + 8), { panelH: 560, minW: 2400, maxW: 3600 });
	}
	const aw = sample(CA.wings, 0.2);
	L.sheet(path.join(PREVIEW, 'ds_ankh.png'), 'Black Ankh parts', 'all parts at their limb origins (not on a body)',
		views.map(([lab, yaw, pitch]) => ({ label: lab, items: [item(A, aw)], cam: { yaw, pitch } })), { panelH: 700, minW: 1800 });
}
