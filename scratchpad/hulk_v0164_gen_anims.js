// v0.16.4 Hulk revamp (agent ANIMS): rebuilds hulk.animation.json for the jointed rig.
//   node gen_anims.js <hulk.animation.json> <hulk.animation.orig.json>
// Conventions (degrees, empirically from the existing clips):
//   body x+ = lean forward, y+ = turn to his left (right shoulder forward), z = side tilt
//   head x- = look up; arm x- = raise forward; right arm z+ / left arm z- = out to the side
//   forearm x- = elbow flex (hand forward / up); hand x = wrist
//   leg x- = leg forward; shin x+ = knee flex (foot back); foot x- = toes up; right leg z+ / left z- = wide
//   root position y- = lower (px)
const fs = require('fs');
const OUT = process.argv[2];
const ORIG = process.argv[3];
const src = JSON.parse(fs.readFileSync(ORIG, 'utf8'));
const anims = src.animations;
const P = 'animation.hulk.';

const ALL = ['root', 'body', 'head', 'right_arm', 'right_forearm', 'right_hand', 'left_arm', 'left_forearm', 'left_hand',
	'right_leg', 'right_shin', 'right_foot', 'left_leg', 'left_shin', 'left_foot'];
const SHORT = { r: 'root', b: 'body', h: 'head', ra: 'right_arm', rf: 'right_forearm', rh: 'right_hand', la: 'left_arm',
	lf: 'left_forearm', lh: 'left_hand', rl: 'right_leg', rs: 'right_shin', rft: 'right_foot', ll: 'left_leg', ls: 'left_shin',
	lft: 'left_foot', p: 'root@pos', bs: 'body@scale' };

const ts = t => t.toFixed(2);
const r2 = v => Math.round(v * 100) / 100;

/** The Hulk's resting stance (idle-ish), the default every clip starts from. */
const STAND = { b: [8, 0, 0], h: [-8, 0, 0], ra: [-4, 0, 12], la: [-4, 0, -12], rf: [-18, 0, 0], lf: [-18, 0, 0], rh: [0, 0, 0], lh: [0, 0, 0],
	rl: [0, 0, 3], ll: [0, 0, -3], rs: [4, 0, 0], ls: [4, 0, 0], rft: [-4, 0, 0], lft: [-4, 0, 0], r: [0, 0, 0], p: [0, 0, 0], bs: [1, 1, 1] };

/** A crouch of `deg` at the hips: thighs forward, knees bent twice as far, feet flat, hips dropped to keep the feet down. */
function crouch(deg, wide = 4) {
	const drop = -12 * (1 - Math.cos(deg * Math.PI / 180));
	return { rl: [-deg, 0, wide], ll: [-deg, 0, -wide], rs: [2 * deg, 0, 0], ls: [2 * deg, 0, 0], rft: [-deg, 0, 0], lft: [-deg, 0, 0], p: [0, r2(drop), 0] };
}
/** Merges pose fragments left to right. */
const M = (...xs) => Object.assign({}, ...xs);

/**
 * keys: [[time, pose, easing?], ...]; each key carries the previous one forward for bones it doesn't name. Every bone named
 * anywhere in the clip is keyed at every key time (so nothing drifts between keys).
 */
function clip(len, keys, opts = {}) {
	const used = new Set();
	for (const [, pose] of keys) Object.keys(pose).forEach(k => used.add(k));
	(opts.always || []).forEach(k => used.add(k));
	let cur = M(STAND, opts.from || {});
	const bones = {};
	for (const [t, pose, easing] of keys) {
		cur = M(cur, pose);
		for (const k of used) {
			const full = SHORT[k];
			let [bone, ch] = full.split('@');
			ch = ch === 'pos' ? 'position' : ch === 'scale' ? 'scale' : 'rotation';
			bones[bone] = bones[bone] || {};
			bones[bone][ch] = bones[bone][ch] || {};
			const v = cur[k].map(r2);
			bones[bone][ch][ts(t)] = easing && t > 0 ? { vector: v, easing } : v;
		}
	}
	const a = { animation_length: len, bones };
	if (opts.loop !== undefined) a.loop = opts.loop;
	return a;
}

// shorthands for the ease into a key
const IO = 'easeinoutsine', IN = 'easeinquad', IN3 = 'easeincubic', OUT_ = 'easeoutquad', OUT3 = 'easeoutcubic', BACK = 'easeoutback';

// ------------------------------------------------------------------ existing clips: add the new joints
/** Interpolated value of a channel's keyframes at time t (linear; values are arrays or {vector}). */
function sample(ch, t) {
	const ks = Object.keys(ch).map(Number).sort((a, b) => a - b);
	const val = k => { const v = ch[Object.keys(ch).find(s => Number(s) === k)]; return Array.isArray(v) ? v : v.vector || v.post; };
	if (t <= ks[0]) return val(ks[0]);
	for (let i = 1; i < ks.length; i++) {
		if (t <= ks[i]) {
			const a = val(ks[i - 1]), b = val(ks[i]), f = (t - ks[i - 1]) / (ks[i] - ks[i - 1]);
			return a.map((x, j) => x + (b[j] - x) * f);
		}
	}
	return val(ks[ks.length - 1]);
}
function forearmFor(armX, weapon) {
	let f = armX < -120 ? -22 : armX < -60 ? -12 : armX <= 20 ? -20 : -10;
	return weapon ? f * 0.4 : f;
}
/** Adds elbow / knee keys to an old clip (keyed on the arm / leg key times). */
function jointify(name, a) {
	const bones = a.bones;
	const weapon = name.startsWith('gladiator');
	for (const side of ['right', 'left']) {
		const arm = bones[side + '_arm'];
		if (arm && arm.rotation && !bones[side + '_forearm']) {
			const fr = {};
			for (const t of Object.keys(arm.rotation)) {
				fr[t] = [r2(forearmFor(sample(arm.rotation, Number(t))[0], weapon)), 0, 0];
			}
			bones[side + '_forearm'] = { rotation: fr };
		}
	}
	const rl = bones.right_leg && bones.right_leg.rotation, ll = bones.left_leg && bones.left_leg.rotation;
	if ((rl || ll) && !bones.right_shin) {
		const times = new Set([...Object.keys(rl || {}), ...Object.keys(ll || {})]);
		const sorted = [...times].sort((x, y) => Number(x) - Number(y));
		const shins = { right: {}, left: {} }, feet = { right: {}, left: {} };
		for (const t of sorted) {
			const rx = rl ? sample(rl, Number(t))[0] : 0;
			const lx = ll ? sample(ll, Number(t))[0] : 0;
			const crouching = rx < -10 && lx < -10 && Math.abs(rx - lx) < 15;
			for (const [side, x] of [['right', rx], ['left', lx]]) {
				let s, f;
				if (crouching) {
					s = -2 * x; f = x;     // thighs forward, shins back under the hips, feet flat (same hip drop)
				} else if (x < 0) {
					s = -0.45 * x; f = 0.15 * x;
				} else {
					s = 0.3 * x + 3; f = 0;
				}
				shins[side][t] = [r2(s), 0, 0];
				feet[side][t] = [r2(f), 0, 0];
			}
		}
		bones.right_shin = { rotation: shins.right };
		bones.left_shin = { rotation: shins.left };
		bones.right_foot = { rotation: feet.right };
		bones.left_foot = { rotation: feet.left };
	}
	return a;
}

const out = {};
for (const [k, v] of Object.entries(anims)) {
	out[k] = jointify(k.substring(P.length), JSON.parse(JSON.stringify(v)));
}

// ------------------------------------------------------------------ locomotion (reworked)
// idle: heavy breathing -- chest swells, shoulders rise and roll out, the head lifts; a slight brace in the knees
out[P + 'idle'] = clip(3.0, [
	[0.0, { b: [9, 0, 0], bs: [1, 1, 1], h: [-8, 0, 0], ra: [-4, 0, 12], la: [-4, 0, -12], rf: [-20, 0, 0], lf: [-20, 0, 0], rh: [0, 0, -4], lh: [0, 0, 4], p: [0, -0.3, 0] }],
	[1.25, { b: [5, 0, 0], bs: [1.06, 1.03, 1.07], h: [-12, 0, 0], ra: [-2, 0, 17], la: [-2, 0, -17], rf: [-24, 0, 0], lf: [-24, 0, 0], p: [0, 0.2, 0] }, IO],
	[1.6, { b: [5, 0, 0], bs: [1.06, 1.03, 1.07], h: [-11, 0, 0], ra: [-2, 0, 16.5], la: [-2, 0, -16.5] }, IO],
	[3.0, { b: [9, 0, 0], bs: [1, 1, 1], h: [-8, 0, 0], ra: [-4, 0, 12], la: [-4, 0, -12], rf: [-20, 0, 0], lf: [-20, 0, 0], p: [0, -0.3, 0] }, IO]
], { loop: true, always: ['rl', 'll', 'rs', 'ls', 'rft', 'lft'] });

// walk: heavy, rolling shoulders; heel strike with a straight knee, the trailing leg pushes off and swings through with a
// lifted knee; arms swing opposite with the forward elbow bending more
function walkKey(side, t) { // side = which leg is striking (forward)
	const R = side === 'r';
	const fwd = { leg: [-26, 0, R ? 3 : -3], shin: [6, 0, 0], foot: [-8, 0, 0] };
	const back = { leg: [22, 0, R ? -3 : 3], shin: [20, 0, 0], foot: [6, 0, 0] };
	const L = (o, s) => o;
	return [t, {
		rl: R ? fwd.leg : [22, 0, 3], rs: R ? fwd.shin : back.shin, rft: R ? fwd.foot : back.foot,
		ll: R ? [22, 0, -3] : [-26, 0, -3], ls: R ? back.shin : fwd.shin, lft: R ? back.foot : fwd.foot,
		ra: R ? [22, 0, 13] : [-26, 0, 12], rf: R ? [-12, 0, 0] : [-34, 0, 0],
		la: R ? [-26, 0, -12] : [22, 0, -13], lf: R ? [-34, 0, 0] : [-12, 0, 0],
		b: [12, R ? -7 : 7, R ? -3 : 3], h: [-9, R ? 4 : -4, R ? 2 : -2], p: [0, -0.7, 0]
	}, IO];
}
function walkPass(side, t) { // side = the stance leg under him; the other swings through
	const R = side === 'r';
	return [t, {
		rl: R ? [0, 0, 3] : [-8, 0, 3], rs: R ? [4, 0, 0] : [48, 0, 0], rft: R ? [-2, 0, 0] : [10, 0, 0],
		ll: R ? [-8, 0, -3] : [0, 0, -3], ls: R ? [48, 0, 0] : [4, 0, 0], lft: R ? [10, 0, 0] : [-2, 0, 0],
		ra: [-2, 0, 13], la: [-2, 0, -13], rf: [-22, 0, 0], lf: [-22, 0, 0],
		b: [10, 0, 0], h: [-8, 0, 0], p: [0, 0.4, 0]
	}, IO];
}
out[P + 'walk'] = clip(1.1, [walkKey('r', 0), walkPass('r', 0.275), walkKey('l', 0.55), walkPass('l', 0.825), walkKey('r', 1.1)], { loop: true });

// run: deep lean, high knees, arms pumping with the elbows bent
function runKey(side, t) {
	const R = side === 'r';
	return [t, {
		rl: R ? [-48, 0, 3] : [30, 0, 3], rs: R ? [22, 0, 0] : [34, 0, 0], rft: R ? [-6, 0, 0] : [12, 0, 0],
		ll: R ? [30, 0, -3] : [-48, 0, -3], ls: R ? [34, 0, 0] : [22, 0, 0], lft: R ? [12, 0, 0] : [-6, 0, 0],
		ra: R ? [44, 0, 14] : [-62, 0, 12], rf: R ? [-62, 0, 0] : [-88, 0, 0],
		la: R ? [-62, 0, -12] : [44, 0, -14], lf: R ? [-88, 0, 0] : [-62, 0, 0],
		b: [24, R ? -9 : 9, 0], h: [-26, R ? 5 : -5, 0], p: [0, -1.0, 0]
	}, IO];
}
function runFly(side, t) { // side = the leg pulling back under him
	const R = side === 'r';
	return [t, {
		rl: R ? [-6, 0, 3] : [-30, 0, 3], rs: R ? [14, 0, 0] : [95, 0, 0], rft: R ? [0, 0, 0] : [10, 0, 0],
		ll: R ? [-30, 0, -3] : [-6, 0, -3], ls: R ? [95, 0, 0] : [14, 0, 0], lft: R ? [10, 0, 0] : [0, 0, 0],
		ra: [-10, 0, 13], la: [-10, 0, -13], rf: [-75, 0, 0], lf: [-75, 0, 0],
		b: [22, 0, 0], h: [-24, 0, 0], p: [0, 0.9, 0]
	}, IO];
}
out[P + 'run'] = clip(0.6, [runKey('r', 0), runFly('r', 0.15), runKey('l', 0.3), runFly('l', 0.45), runKey('r', 0.6)], { loop: true });

// hold (arms-only layer): both arms overhead, elbows a little bent under the weight
out[P + 'hold'] = clip(2.0, [
	[0, { ra: [-168, 0, 14], la: [-168, 0, -14], rf: [-28, 0, 0], lf: [-28, 0, 0], rh: [-10, 0, 0], lh: [-10, 0, 0] }],
	[1.0, { ra: [-163, 0, 17], la: [-163, 0, -17], rf: [-36, 0, 0], lf: [-36, 0, 0] }, IO],
	[2.0, { ra: [-168, 0, 14], la: [-168, 0, -14], rf: [-28, 0, 0], lf: [-28, 0, 0] }, IO]
], { loop: true });

// ------------------------------------------------------------------ transformations (all STANDING -- user rule)
const FLEX = { ra: [-30, 0, 48], la: [-30, 0, -48], rf: [-95, 0, 0], lf: [-95, 0, 0], rh: [-15, 0, 0], lh: [-15, 0, 0] }; // arms flexed out
const ROAR_OUT = { b: [-14, 0, 0], h: [-38, 0, 0], ra: [-38, 0, 74], la: [-38, 0, -74], rf: [-45, 0, 0], lf: [-45, 0, 0], rl: [0, 0, 7], ll: [0, 0, -7], rs: [6, 0, 0], ls: [6, 0, 0] };
const WIDE = { rl: [0, 0, 6], ll: [0, 0, -6], rs: [8, 0, 0], ls: [8, 0, 0], rft: [-4, 0, 0], lft: [-4, 0, 0] };
const SETTLE = M(STAND, { p: [0, 0, 0] });

// 0 quick (1.5 s, the H change): a short braced hunch, fists clenched and flexing out, then up into the roar
out[P + 'transform'] = clip(1.5, [
	[0, M(crouch(14), { b: [36, 0, 0], h: [26, 0, 0], ra: [-28, 0, 8], la: [-28, 0, -8], rf: [-70, 0, 0], lf: [-70, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0] })],
	[0.45, M(crouch(16), { b: [30, 0, 0], h: [18, 0, 0], ra: [-20, 0, 30], la: [-20, 0, -30], rf: [-100, 0, 0], lf: [-100, 0, 0] }), IO],
	[0.75, M(crouch(10), { b: [24, 0, 0], h: [10, 0, 0] }, FLEX), IO],
	[1.0, M(WIDE, ROAR_OUT, { p: [0, 0.4, 0] }), OUT3],
	[1.25, M(ROAR_OUT, { ra: [-42, 0, 78], la: [-42, 0, -78], h: [-34, 0, 0] }), IO],
	[1.5, SETTLE, IO]
]);

// 1 forced (5.2 s): hunched, clutching his head (now with the elbows bent so the hands are ON the head), shaking, then up
{
	const a = JSON.parse(JSON.stringify(anims[P + 'transform_forced']));
	const fr = {}, fl = {}, hr = {}, hl = {};
	for (const t of Object.keys(a.bones.right_arm.rotation)) {
		const n = Number(t);
		let f = n === 0 ? -18 : n <= 3.1 ? -78 : n <= 3.3 ? -30 : n <= 3.9 ? -60 : n <= 4.05 ? -80 : n <= 4.8 ? -48 : -18;
		const j = n > 0.2 && n <= 3.1 ? Math.sin(n * 37) * (2 + n) : 0;
		fr[t] = [r2(f + j), 0, 0];
		fl[t] = [r2(f - j), 0, 0];
		hr[t] = [n > 0 && n < 4.9 ? -20 : 0, 0, 0];
		hl[t] = [n > 0 && n < 4.9 ? -20 : 0, 0, 0];
	}
	// the clutch: upper arms a bit lower and wider so the bent forearms reach the sides of the head
	for (const side of ['right_arm', 'left_arm']) {
		const rot = a.bones[side].rotation;
		for (const t of Object.keys(rot)) {
			const n = Number(t);
			if (n > 0.2 && n <= 3.1) {
				rot[t] = [r2(rot[t][0] + 28), rot[t][1], r2(rot[t][2] * 1.5)];
			}
		}
	}
	a.bones.right_forearm = { rotation: fr };
	a.bones.left_forearm = { rotation: fl };
	a.bones.right_hand = { rotation: hr };
	a.bones.left_hand = { rotation: hl };
	// braced: knees soft through the struggle, straight when he stands tall
	const sh = { '0.00': [4, 0, 0], '0.30': [10, 0, 0], '3.10': [12, 0, 0], '3.55': [16, 0, 0], '4.30': [6, 0, 0], '5.20': [4, 0, 0] };
	const ft = { '0.00': [-4, 0, 0], '0.30': [-6, 0, 0], '3.10': [-6, 0, 0], '3.55': [-8, 0, 0], '4.30': [-3, 0, 0], '5.20': [-4, 0, 0] };
	a.bones.right_shin = { rotation: sh };
	a.bones.left_shin = { rotation: JSON.parse(JSON.stringify(sh)) };
	a.bones.right_foot = { rotation: ft };
	a.bones.left_foot = { rotation: JSON.parse(JSON.stringify(ft)) };
	// the thighs carry the matching hip flex so the knees bend forward, not the feet back
	for (const side of ['right_leg', 'left_leg']) {
		const rot = a.bones[side].rotation;
		for (const t of Object.keys(rot)) {
			const n = Number(t);
			const k = n === 0 ? 0 : n <= 3.1 ? -5 : n <= 3.9 ? -8 : n <= 4.3 ? -3 : 0;
			rot[t] = [k, rot[t][1], rot[t][2]];
		}
	}
	out[P + 'transform_forced'] = a;
}

// the shared tail of the slow changes (4.0 -> 5.2 s, the Hulk solid): straighten, flex and ROAR, settle
const RISE_TAIL = [
	[4.3, M(WIDE, ROAR_OUT), OUT3],
	[4.8, M(ROAR_OUT, { ra: [-44, 0, 78], la: [-44, 0, -78], h: [-34, 0, 0] }), IO],
	[5.2, SETTLE, IO]
];

// shiver: alternating small offsets on body / head z and arm x over [t0, t1]
function shiver(base, t0, t1, step, amp0, amp1) {
	const ks = [];
	let i = 0;
	for (let t = t0; t <= t1 + 1e-6; t += step, i++) {
		const f = (t - t0) / Math.max(0.001, t1 - t0);
		const a = (amp0 + (amp1 - amp0) * f) * (i % 2 ? 1 : -1);
		const p = typeof base === 'function' ? base(f, t) : base;
		ks.push([r2(t), M(p, {
			b: [p.b[0] + Math.abs(a) * 0.3, p.b[1], p.b[2] + a],
			h: [p.h[0], p.h[1], p.h[2] - a * 1.5],
			ra: [p.ra[0] + a, p.ra[1], p.ra[2]], la: [p.la[0] - a, p.la[1], p.la[2]]
		})]);
	}
	return ks;
}
const lerpPose = (A, B, f) => {
	const o = {};
	for (const k of new Set([...Object.keys(A), ...Object.keys(B)])) {
		const a = A[k] || B[k], b = B[k] || A[k];
		o[k] = a.map((x, j) => x + (b[j] - x) * f);
	}
	return o;
};

// 2 rip (5.2 s): the chest heaves and swells, the arms pull in to a crab flex and then burst out wide as the shirt tears
{
	const hunch = M(STAND, crouch(8), { b: [26, 0, 0], h: [16, 0, 0], ra: [-30, 20, 18], la: [-30, -20, -18], rf: [-80, 0, 0], lf: [-80, 0, 0], rh: [-25, 0, 0], lh: [-25, 0, 0] });
	const crab = M(STAND, crouch(10), { b: [30, 0, 0], bs: [1.08, 1.02, 1.12], h: [6, 0, 0], ra: [-38, 35, 30], la: [-38, -35, -30], rf: [-105, 0, 0], lf: [-105, 0, 0], rh: [-30, 0, 0], lh: [-30, 0, 0] });
	const burst = M(STAND, WIDE, { b: [-16, 0, 0], bs: [1.12, 1.04, 1.16], h: [-30, 0, 0], ra: [10, 0, 82], la: [10, 0, -82], rf: [-35, 0, 0], lf: [-35, 0, 0], rh: [-10, 0, 0], lh: [-10, 0, 0] });
	const heave = (f, t) => {
		const breath = Math.sin(t * Math.PI * 2 / 0.8);
		const p = lerpPose(hunch, crab, Math.min(1, f * 1.2));
		p.b = [p.b[0] - 6 * breath, p.b[1], p.b[2]];
		p.bs = [p.bs[0] + 0.04 * breath, p.bs[1], p.bs[2] + 0.05 * breath];
		return p;
	};
	out[P + 'transform_rip'] = clip(5.2, [
		[0, M(STAND)],
		[0.4, hunch, IO],
		...shiver(heave, 0.6, 3.0, 0.2, 0.8, 2.6).map(k => [k[0], k[1], IO]),
		[3.35, M(crab, { b: [36, 0, 0], h: [14, 0, 0], rf: [-115, 0, 0], lf: [-115, 0, 0] }), IO],
		[3.7, burst, OUT3],
		[4.0, M(burst, { ra: [6, 0, 86], la: [6, 0, -86], h: [-36, 0, 0] }), IO],
		...RISE_TAIL
	], { always: ['bs', 'p', 'rl', 'll', 'rs', 'ls', 'rft', 'lft', 'rh', 'lh'] });
}

// 3 pound (5.2 s): bent over at the hips (standing, knees soft), he hammers the ground with both fists as he grows
{
	const up = M(STAND, crouch(14), { b: [62, 0, 0], h: [-28, 0, 0], ra: [-200, 0, 12], la: [-200, 0, -12], rf: [-45, 0, 0], lf: [-45, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0] });
	const down = M(STAND, crouch(18), { b: [72, 0, 0], h: [-40, 0, 0], ra: [-48, 0, 8], la: [-48, 0, -8], rf: [-12, 0, 0], lf: [-12, 0, 0], rh: [-30, 0, 0], lh: [-30, 0, 0] });
	const keys = [[0, M(STAND)], [0.5, M(STAND, crouch(10), { b: [40, 0, 0], h: [10, 0, 0], ra: [-40, 0, 20], la: [-40, 0, -20], rf: [-70, 0, 0], lf: [-70, 0, 0] }), IO]];
	// pounds at 1.05, 1.75, 2.45, 3.15, 3.75 s (the last two harder)
	const hits = [1.05, 1.75, 2.45, 3.15, 3.75];
	let prev = 0.5;
	for (const h of hits) {
		keys.push([r2(h - 0.32), up, IO]);
		keys.push([r2(h), down, IN3]);
		keys.push([r2(h + 0.12), M(down, { b: [70, 0, 0], ra: [-54, 0, 8], la: [-54, 0, -8] }), OUT_]);
		prev = h;
	}
	keys.push([4.0, M(STAND, crouch(10), { b: [30, 0, 0], h: [-10, 0, 0] }, FLEX), IO]);
	out[P + 'transform_pound'] = clip(5.2, [...keys, ...RISE_TAIL], { always: ['p', 'rh', 'lh'] });
}

// 4 arch (5.2 s): doubled over clutching his gut, then the back arches and he screams at the sky
{
	const gut = M(STAND, crouch(8), { b: [34, 0, 0], h: [22, 0, 0], ra: [-52, 40, 10], la: [-52, -40, -10], rf: [-95, 0, 0], lf: [-95, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0] });
	const arch = M(STAND, WIDE, { b: [-32, 0, 0], bs: [1.05, 1.03, 1.06], h: [-62, 0, 0], ra: [28, 0, 40], la: [28, 0, -40], rf: [-30, 0, 0], lf: [-30, 0, 0], rh: [-25, 0, 0], lh: [-25, 0, 0], rs: [12, 0, 0], ls: [12, 0, 0], rl: [-4, 0, 6], ll: [-4, 0, -6], p: [0, -0.3, 0] });
	out[P + 'transform_arch'] = clip(5.2, [
		[0, M(STAND)],
		[0.45, gut, IO],
		...shiver(gut, 0.6, 1.4, 0.2, 0.8, 1.6).map(k => [k[0], k[1], IO]),
		[2.0, lerpPose(gut, arch, 0.55), IO],
		[2.4, arch, OUT3],
		...shiver(arch, 2.6, 3.8, 0.2, 1.0, 2.8).map(k => [k[0], k[1], IO]),
		[4.0, M(arch, { b: [-26, 0, 0], h: [-55, 0, 0], ra: [10, 0, 60], la: [10, 0, -60] }), IO],
		...RISE_TAIL
	], { always: ['bs', 'p', 'rl', 'll', 'rs', 'ls', 'rft', 'lft', 'rh', 'lh'] });
}

// 5 stagger (5.2 s): dazed, a hand to his head, stumbles forward (right, then left), catches himself standing wide
{
	const daze = M(STAND, { b: [14, 0, -4], h: [12, 0, 6], ra: [-150, 0, 34], rf: [-95, 0, 0], la: [-10, 0, -16], lf: [-25, 0, 0] });
	const stepR = M(STAND, { b: [34, -8, 0], h: [-6, 0, 0], rl: [-34, 0, 4], rs: [36, 0, 0], rft: [-2, 0, 0], ll: [18, 0, -3], ls: [6, 0, 0], lft: [6, 0, 0],
		ra: [-60, 0, 30], rf: [-40, 0, 0], la: [-70, 0, -30], lf: [-35, 0, 0], p: [0, -1.2, 0] });
	const stepL = M(STAND, { b: [36, 8, 0], h: [-8, 0, 0], ll: [-36, 0, -4], ls: [38, 0, 0], lft: [-2, 0, 0], rl: [18, 0, 3], rs: [6, 0, 0], rft: [6, 0, 0],
		ra: [-75, 0, 32], rf: [-35, 0, 0], la: [-58, 0, -30], lf: [-42, 0, 0], p: [0, -1.4, 0] });
	const caught = M(STAND, WIDE, crouch(12, 7), { b: [24, 0, 0], h: [-12, 0, 0] }, FLEX);
	out[P + 'transform_stagger'] = clip(5.2, [
		[0, M(STAND)],
		[0.35, daze, IO],
		[0.8, M(daze, { b: [10, 0, 5], h: [8, 0, -5] }), IO],
		[1.15, M(daze, { b: [16, 0, -5], h: [14, 0, 6] }), IO],
		[1.55, stepR, IN],
		[1.85, M(stepR, { b: [30, -6, 0], p: [0, -0.8, 0] }), OUT_],
		[2.15, M(STAND, { b: [24, 0, 3], h: [4, 0, 0], ra: [-40, 0, 26], la: [-50, 0, -26], rf: [-40, 0, 0], lf: [-40, 0, 0] }), IO],
		[2.55, stepL, IN],
		[2.85, M(stepL, { b: [32, 6, 0], p: [0, -1.0, 0] }), OUT_],
		[3.25, M(stepR, { b: [40, -4, 0], rl: [-30, 0, 6], ll: [16, 0, -5], p: [0, -1.4, 0] }), IN],
		[3.6, caught, OUT3],
		[4.0, M(caught, { b: [18, 0, 0], h: [-20, 0, 0] }), IO],
		...RISE_TAIL
	], { always: ['p', 'rl', 'll', 'rs', 'ls', 'rft', 'lft', 'rh', 'lh'] });
}

// ------------------------------------------------------------------ the new kit
// R super_punch (0.75 s): wind up low and back, step in, a huge straight right; impact at 0.30 s
out[P + 'super_punch'] = clip(0.75, [
	[0, M(STAND)],
	[0.18, M(crouch(10), { b: [16, -38, 0], h: [-12, 20, 0], ra: [42, 0, 26], rf: [-95, 0, 0], rh: [-20, 0, 0], la: [-55, 0, -16], lf: [-70, 0, 0], rl: [12, 0, 4], rs: [24, 0, 0], ll: [-24, 0, -4], ls: [30, 0, 0] }), IO],
	[0.3, { b: [22, 34, 0], h: [-14, -14, 0], ra: [-96, 0, 2], rf: [-4, 0, 0], rh: [0, 0, 0], la: [28, 0, -18], lf: [-40, 0, 0], rl: [16, 0, 4], rs: [8, 0, 0], ll: [-30, 0, -4], ls: [34, 0, 0], lft: [-6, 0, 0], p: [0, -1.2, 0] }, IN3],
	[0.45, { b: [20, 30, 0], ra: [-92, 0, 4], rf: [-8, 0, 0] }, OUT_],
	[0.75, M(STAND), IO]
]);

// Shift+R ground_smash (1.0 s): both fists overhead, down into the ground in a deep (standing) crouch; impact at 0.42 s
out[P + 'ground_smash'] = clip(1.0, [
	[0, M(STAND)],
	[0.28, M(crouch(4), { b: [-16, 0, 0], h: [-24, 0, 0], ra: [-178, 0, 10], la: [-178, 0, -10], rf: [-40, 0, 0], lf: [-40, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0], p: [0, 0.6, 0] }), IO],
	[0.42, M(crouch(30, 7), { b: [58, 0, 0], h: [-34, 0, 0], ra: [-48, 0, 6], la: [-48, 0, -6], rf: [-10, 0, 0], lf: [-10, 0, 0], rh: [-30, 0, 0], lh: [-30, 0, 0] }), IN3],
	[0.65, M(crouch(28, 7), { b: [54, 0, 0], ra: [-52, 0, 8], la: [-52, 0, -8] }), OUT_],
	[1.0, M(STAND, crouch(0, 3)), IO]
], { always: ['rh', 'lh'] });

// G thunderclap (0.75 s): arms flung wide, a massive clap in front; impact at 0.28 s
out[P + 'thunderclap'] = clip(0.75, [
	[0, M(STAND)],
	[0.16, M(crouch(8), { b: [-10, 0, 0], h: [-16, 0, 0], ra: [-80, -70, 0], la: [-80, 70, 0], rf: [-25, 0, 0], lf: [-25, 0, 0], bs: [1.06, 1, 1.08] }), IO],
	[0.28, M(crouch(14, 6), { b: [20, 0, 0], h: [-14, 0, 0], ra: [-90, 14, 0], la: [-90, -14, 0], rf: [-8, 0, 0], lf: [-8, 0, 0], bs: [1, 1, 1] }), IN3],
	[0.42, M(crouch(13, 6), { b: [18, 0, 0], ra: [-84, 10, 0], la: [-84, -10, 0] }), OUT_],
	[0.75, M(STAND), IO]
], { always: ['bs'] });

// Shift+G fault_stomp (0.9 s): the right knee comes high, the foot drives down; impact at 0.40 s
out[P + 'fault_stomp'] = clip(0.9, [
	[0, M(STAND)],
	[0.25, { b: [-4, 0, 6], h: [-18, 0, 0], rl: [-78, 0, 6], rs: [85, 0, 0], rft: [-10, 0, 0], ll: [0, 0, -3], ls: [10, 0, 0], ra: [-30, 0, 46], la: [-30, 0, -46], rf: [-70, 0, 0], lf: [-70, 0, 0] }, IO],
	[0.4, M(crouch(14, 6), { b: [30, 0, 0], h: [-24, 0, 0], ra: [10, 0, 30], la: [10, 0, -30], rf: [-60, 0, 0], lf: [-60, 0, 0] }), IN3],
	[0.6, M(crouch(12, 6), { b: [26, 0, 0] }), OUT_],
	[0.9, M(STAND), IO]
]);

// Z bulldoze (loop 0.5 s): head down, shoulder first, arms forward like a battering ram, legs driving
function bullKey(side, t) {
	const R = side === 'r';
	return [t, {
		b: [42, R ? 12 : 6, 0], h: [-40, 0, 0], ra: [-70, 0, 26], la: [-62, 0, -22], rf: [-70, 0, 0], lf: [-80, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0],
		rl: R ? [-56, 0, 3] : [36, 0, 3], rs: R ? [40, 0, 0] : [30, 0, 0], rft: R ? [-4, 0, 0] : [10, 0, 0],
		ll: R ? [36, 0, -3] : [-56, 0, -3], ls: R ? [30, 0, 0] : [40, 0, 0], lft: R ? [10, 0, 0] : [-4, 0, 0], p: [0, -1.2, 0]
	}, IO];
}
function bullFly(side, t) {
	const R = side === 'r';
	return [t, {
		b: [40, 9, 0], rl: R ? [-10, 0, 3] : [-34, 0, 3], rs: R ? [20, 0, 0] : [90, 0, 0], ll: R ? [-34, 0, -3] : [-10, 0, -3], ls: R ? [90, 0, 0] : [20, 0, 0],
		rft: [4, 0, 0], lft: [4, 0, 0], p: [0, 0.2, 0]
	}, IO];
}
out[P + 'bulldoze'] = clip(0.5, [bullKey('r', 0), bullFly('r', 0.125), bullKey('l', 0.25), bullFly('l', 0.375), bullKey('r', 0.5)], { loop: true });

// Shift+Z uppercut (0.8 s): drop low, the right fist rises from the knee to the sky; impact at 0.32 s
out[P + 'uppercut'] = clip(0.8, [
	[0, M(STAND)],
	[0.2, M(crouch(26, 6), { b: [38, -24, 0], h: [-20, 10, 0], ra: [30, 0, 18], rf: [-100, 0, 0], rh: [-20, 0, 0], la: [-40, 0, -20], lf: [-70, 0, 0] }), IO],
	[0.32, { b: [-14, 22, 0], h: [-34, -10, 0], ra: [-168, 0, 6], rf: [-60, 0, 0], rh: [-10, 0, 0], la: [20, 0, -24], lf: [-30, 0, 0],
		rl: [0, 0, 4], rs: [6, 0, 0], rft: [6, 0, 0], ll: [8, 0, -4], ls: [10, 0, 0], lft: [8, 0, 0], p: [0, 1.0, 0] }, IN3],
	[0.5, { b: [-10, 18, 0], ra: [-172, 0, 8], rf: [-50, 0, 0], p: [0, 0.6, 0] }, OUT_],
	[0.8, M(STAND), IO]
]);

// C gamma_burst (1.0 s): curls in, gathering, then explodes outward -- arms and chest thrown wide; burst at 0.45 s
out[P + 'gamma_burst'] = clip(1.0, [
	[0, M(STAND)],
	[0.32, M(crouch(16, 5), { b: [40, 0, 0], h: [24, 0, 0], ra: [-50, 40, 10], la: [-50, -40, -10], rf: [-110, 0, 0], lf: [-110, 0, 0], rh: [-25, 0, 0], lh: [-25, 0, 0], bs: [0.97, 0.98, 0.97] }), IO],
	[0.45, M(WIDE, { b: [-22, 0, 0], h: [-40, 0, 0], ra: [-20, 0, 86], la: [-20, 0, -86], rf: [-10, 0, 0], lf: [-10, 0, 0], rh: [10, 0, 0], lh: [10, 0, 0], bs: [1.1, 1.04, 1.12], p: [0, 0.4, 0] }), OUT3],
	[0.7, M(WIDE, { b: [-16, 0, 0], h: [-34, 0, 0], ra: [-24, 0, 80], la: [-24, 0, -80], bs: [1.05, 1.02, 1.06], p: [0, 0, 0] }), IO],
	[1.0, M(STAND), IO]
], { always: ['bs', 'p'] });

// Shift+C earth_wall (1.0 s): both hands dig in low and heave UP -- the wall rises at 0.48 s
out[P + 'earth_wall'] = clip(1.0, [
	[0, M(STAND)],
	[0.3, M(crouch(30, 7), { b: [62, 0, 0], h: [-30, 0, 0], ra: [-40, 0, 12], la: [-40, 0, -12], rf: [-20, 0, 0], lf: [-20, 0, 0], rh: [-40, 0, 0], lh: [-40, 0, 0] }), IO],
	[0.48, M(WIDE, { b: [-8, 0, 0], h: [-24, 0, 0], ra: [-150, 0, 18], la: [-150, 0, -18], rf: [-70, 0, 0], lf: [-70, 0, 0], rh: [-30, 0, 0], lh: [-30, 0, 0], p: [0, 0.5, 0] }), OUT3],
	[0.7, M(WIDE, { b: [0, 0, 0], ra: [-140, 0, 20], la: [-140, 0, -20], p: [0, 0, 0] }), IO],
	[1.0, M(STAND), IO]
], { always: ['p'] });

// V grab (0.45 s): lunge-reach, the fists close, haul it overhead (then the `hold` loop takes the arms)
out[P + 'grab'] = clip(0.45, [
	[0, M(STAND)],
	[0.15, M(crouch(16, 5), { b: [36, 0, 0], h: [-24, 0, 0], ra: [-80, 0, 14], la: [-80, 0, -14], rf: [-10, 0, 0], lf: [-10, 0, 0], rh: [0, 0, 0], lh: [0, 0, 0] }), OUT_],
	[0.22, { rf: [-45, 0, 0], lf: [-45, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0] }, IO],
	[0.45, M(STAND, { b: [-6, 0, 0], h: [-14, 0, 0], ra: [-168, 0, 14], la: [-168, 0, -14], rf: [-28, 0, 0], lf: [-28, 0, 0], rh: [-10, 0, 0], lh: [-10, 0, 0] }), IO]
]);

// V throw_far (0.6 s): from overhead, rock back and hurl it with everything; release at 0.30 s
out[P + 'throw_far'] = clip(0.6, [
	[0, M(STAND, { b: [-6, 0, 0], ra: [-168, 0, 14], la: [-168, 0, -14], rf: [-28, 0, 0], lf: [-28, 0, 0], rh: [-10, 0, 0], lh: [-10, 0, 0] })],
	[0.16, { b: [-24, 0, 0], h: [-22, 0, 0], ra: [-196, 0, 10], la: [-196, 0, -10], rf: [-60, 0, 0], lf: [-60, 0, 0], rl: [8, 0, 3], ll: [-18, 0, -3], ls: [14, 0, 0] }, IO],
	[0.3, { b: [36, 0, 0], h: [-30, 0, 0], ra: [-62, 0, 8], la: [-62, 0, -8], rf: [0, 0, 0], lf: [0, 0, 0], rh: [10, 0, 0], lh: [10, 0, 0], rl: [16, 0, 3], ll: [-32, 0, -3], ls: [30, 0, 0], lft: [-6, 0, 0], p: [0, -1.4, 0] }, IN3],
	[0.6, M(STAND), IO]
]);

// Shift+V debris_rip (0.8 s): fingers dig into the ground, he tears the slab up and over his head (then `hold`)
out[P + 'debris_rip'] = clip(0.8, [
	[0, M(STAND)],
	[0.3, M(crouch(32, 8), { b: [64, 0, 0], h: [-32, 0, 0], ra: [-38, 0, 14], la: [-38, 0, -14], rf: [-14, 0, 0], lf: [-14, 0, 0], rh: [-45, 0, 0], lh: [-45, 0, 0] }), IO],
	[0.42, M(crouch(28, 8), { b: [56, 0, 0], h: [-28, 0, 0], ra: [-50, 0, 14], la: [-50, 0, -14], rf: [-40, 0, 0], lf: [-40, 0, 0] }), IN],
	[0.6, M(WIDE, { b: [-8, 0, 0], h: [-18, 0, 0], ra: [-150, 0, 16], la: [-150, 0, -16], rf: [-50, 0, 0], lf: [-50, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0] }), OUT3],
	[0.8, M(STAND, { b: [-6, 0, 0], h: [-14, 0, 0], ra: [-168, 0, 14], la: [-168, 0, -14], rf: [-28, 0, 0], lf: [-28, 0, 0], rh: [-10, 0, 0], lh: [-10, 0, 0] }), IO]
]);

// V debris_throw (0.7 s): a two-handed overhead heave; release at 0.34 s
out[P + 'debris_throw'] = clip(0.7, [
	[0, M(STAND, { b: [-6, 0, 0], ra: [-168, 0, 14], la: [-168, 0, -14], rf: [-28, 0, 0], lf: [-28, 0, 0], rh: [-10, 0, 0], lh: [-10, 0, 0] })],
	[0.2, { b: [-28, 0, 0], h: [-24, 0, 0], ra: [-200, 0, 14], la: [-200, 0, -14], rf: [-70, 0, 0], lf: [-70, 0, 0], rl: [-6, 0, 4], ll: [-6, 0, -4], rs: [12, 0, 0], ls: [12, 0, 0], p: [0, -0.4, 0] }, IO],
	[0.34, M(crouch(12, 5), { b: [40, 0, 0], h: [-30, 0, 0], ra: [-70, 0, 12], la: [-70, 0, -12], rf: [-6, 0, 0], lf: [-6, 0, 0], rh: [10, 0, 0], lh: [10, 0, 0] }), IN3],
	[0.7, M(STAND), IO]
]);

// Shift+V loki_slam (3.0 s): "puny god" -- one hand (right) lifts the victim and slams it overhead into the floor
// left-right-left-right. IMPACTS at 0.75, 1.35, 1.95, 2.55 s (ticks 15, 27, 39, 51); overhead apexes at 0.45, 1.05,
// 1.65, 2.25 s. Left slams land in front-left of him, right slams in front-right.
{
	const over = { b: [-12, 0, 0], h: [-20, 0, 0], ra: [-178, 0, 2], rf: [-12, 0, 0], rh: [-10, 0, 0], la: [-20, 0, -40], lf: [-50, 0, 0],
		rl: [0, 0, 5], ll: [0, 0, -5], rs: [8, 0, 0], ls: [8, 0, 0], p: [0, 0.2, 0] };
	const slamL = M(crouch(18, 7), { b: [40, 30, -6], h: [-30, -16, 0], ra: [-52, 0, -26], rf: [-6, 0, 0], rh: [-20, 0, 0], la: [10, 0, -40], lf: [-40, 0, 0] });
	const slamR = M(crouch(18, 7), { b: [40, -30, 6], h: [-30, 16, 0], ra: [-48, 0, 46], rf: [-6, 0, 0], rh: [-20, 0, 0], la: [-30, 0, -24], lf: [-60, 0, 0] });
	const k = [
		[0, M(STAND)],
		[0.2, M(crouch(12, 5), { b: [30, 0, 0], h: [-20, 0, 0], ra: [-70, 0, 12], rf: [-10, 0, 0], rh: [0, 0, 0], la: [-10, 0, -20], lf: [-30, 0, 0] }), OUT_],
		[0.3, { rf: [-30, 0, 0], rh: [-25, 0, 0] }, IO]
	];
	const impacts = [0.75, 1.35, 1.95, 2.55];
	impacts.forEach((t, i) => {
		k.push([r2(t - 0.3), over, IO]);
		k.push([t, i % 2 === 0 ? slamL : slamR, IN3]);
		k.push([r2(t + 0.08), M(i % 2 === 0 ? slamL : slamR, { b: [44, i % 2 === 0 ? 32 : -32, 0] }), OUT_]);
	});
	k.push([3.0, M(STAND, { b: [20, -10, 0], ra: [-30, 0, 30], rf: [-30, 0, 0], rh: [0, 0, 0] }), IO]);
	out[P + 'loki_slam'] = clip(3.0, k, { always: ['p', 'rh', 'lh'] });
}

// N roar (1.6 s): chest out, arms back, head thrust forward -- a huge roar
out[P + 'roar'] = clip(1.6, [
	[0, M(STAND)],
	[0.3, M(crouch(8), { b: [22, 0, 0], h: [16, 0, 0], ra: [-20, 0, 26], la: [-20, 0, -26], rf: [-80, 0, 0], lf: [-80, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0], bs: [0.98, 1, 0.98] }), IO],
	[0.5, M(WIDE, { b: [-6, 0, 0], h: [-6, 0, 0], ra: [48, 0, 40], la: [48, 0, -40], rf: [-30, 0, 0], lf: [-30, 0, 0], rh: [-30, 0, 0], lh: [-30, 0, 0], bs: [1.1, 1.03, 1.14], p: [0, -0.4, 0] }), OUT3],
	...[0.65, 0.8, 0.95, 1.1, 1.25].map((t, i) => [t, { h: [i % 2 ? -4 : -8, 0, i % 2 ? 1.5 : -1.5], b: [i % 2 ? -5 : -7, 0, 0], ra: [i % 2 ? 50 : 46, 0, 41], la: [i % 2 ? 50 : 46, 0, -41] }, IO]),
	[1.6, M(STAND, { bs: [1, 1, 1], p: [0, 0, 0] }), IO]
], { always: ['bs', 'p'] });

// X leap_launch (0.4 s): explode up out of the crouch, arms swung to the sky, legs trailing (then `leap` holds the air pose)
out[P + 'leap_launch'] = clip(0.4, [
	[0, M(crouch(34, 6), { b: [40, 0, 0], h: [-30, 0, 0], ra: [42, 0, 18], la: [42, 0, -18], rf: [-20, 0, 0], lf: [-20, 0, 0] })],
	[0.15, { b: [-10, 0, 0], h: [-20, 0, 0], ra: [-165, 0, 18], la: [-165, 0, -18], rf: [-20, 0, 0], lf: [-20, 0, 0], rl: [16, 0, 4], ll: [10, 0, -4], rs: [20, 0, 0], ls: [24, 0, 0], rft: [20, 0, 0], lft: [20, 0, 0], p: [0, 0.6, 0] }, OUT3],
	[0.4, { b: [-4, 0, 0], h: [-8, 0, 0], ra: [-150, 0, 22], la: [-150, 0, -22], rl: [18, 0, 6], ll: [-14, 0, -6], rs: [40, 0, 0], ls: [60, 0, 0], rft: [10, 0, 0], lft: [0, 0, 0], p: [0, 0, 0] }, IO]
]);

// landing (0.7 s): a heavy crouch to absorb it, one fist planted, then up
out[P + 'leap_land'] = clip(0.7, [
	[0, { b: [-4, 0, 0], h: [-8, 0, 0], ra: [-150, 0, 22], la: [-150, 0, -22], rl: [-10, 0, 6], ll: [-10, 0, -6], rs: [30, 0, 0], ls: [30, 0, 0] }],
	[0.1, M(crouch(42, 8), { b: [56, 0, 0], h: [-36, 0, 0], ra: [-60, 0, 20], rf: [-6, 0, 0], rh: [-40, 0, 0], la: [20, 0, -48], lf: [-40, 0, 0] }), IN],
	[0.3, M(crouch(38, 8), { b: [50, 0, 0], h: [-30, 0, 0] }), OUT_],
	[0.7, M(STAND), IO]
], { always: ['p', 'rh'] });

// death_fall (1.4 s, held): the legs go, he topples onto his back and lies there, arms out
out[P + 'death_fall'] = clip(1.4, [
	[0, M(STAND), undefined],
	[0.3, M(crouch(16, 5), { b: [26, 0, 4], h: [22, 0, 0], ra: [-10, 0, 22], la: [-10, 0, -22], rf: [-30, 0, 0], lf: [-30, 0, 0], r: [0, 0, 0] }), IO],
	[0.75, { r: [-60, 0, 0], p: [0, 1.0, 0], b: [-4, 0, 0], h: [-20, 0, 0], ra: [-120, 0, 40], la: [-120, 0, -40], rf: [-20, 0, 0], lf: [-20, 0, 0],
		rl: [-30, 0, 6], ll: [-24, 0, -6], rs: [40, 0, 0], ls: [36, 0, 0] }, IN],
	[1.0, { r: [-92, 0, 0], p: [0, 2.0, 0], b: [2, 0, 0], h: [6, 0, 0], ra: [-30, 0, 74], la: [-30, 0, -74], rf: [-14, 0, 0], lf: [-14, 0, 0],
		rl: [-6, 0, 10], ll: [-4, 0, -10], rs: [10, 0, 0], ls: [8, 0, 0], rft: [10, 0, 0], lft: [10, 0, 0] }, IN3],
	[1.15, { r: [-88, 0, 0], h: [-4, 0, 0] }, OUT_],
	[1.4, { r: [-90, 0, 0], p: [0, 2.0, 0], h: [0, 0, 0], ra: [-26, 0, 78], la: [-26, 0, -78], b: [0, 0, 0] }, IO]
], { always: ['r', 'p'] });
out[P + 'death_fall'].loop = 'hold_on_last_frame';

// rampage_roar (1.4 s): stamps, beats his chest twice, throws his head back and roars
out[P + 'rampage_roar'] = clip(1.4, [
	[0, M(STAND)],
	[0.15, M(crouch(12, 6), { b: [24, 0, 0], h: [6, 0, 0], ra: [-60, 40, 20], la: [-60, -40, -20], rf: [-110, 0, 0], lf: [-110, 0, 0], rh: [-20, 0, 0], lh: [-20, 0, 0] }), IO],
	[0.25, { ra: [-40, 50, 14], rf: [-120, 0, 0] }, IN],
	[0.35, { ra: [-60, 40, 20], rf: [-110, 0, 0], la: [-40, -50, -14], lf: [-120, 0, 0] }, IN],
	[0.45, { ra: [-40, 50, 14], rf: [-120, 0, 0], la: [-60, -40, -20], lf: [-110, 0, 0] }, IN],
	[0.55, { la: [-40, -50, -14], lf: [-120, 0, 0], ra: [-60, 40, 20], rf: [-110, 0, 0] }, IN],
	[0.75, M(WIDE, ROAR_OUT, { b: [-20, 0, 0], h: [-50, 0, 0], bs: [1.08, 1.03, 1.1], p: [0, 0, 0] }), OUT3],
	...[0.9, 1.05, 1.2].map((t, i) => [t, { h: [i % 2 ? -46 : -52, 0, i % 2 ? 2 : -2] }, IO]),
	[1.4, M(STAND, { bs: [1, 1, 1] }), IO]
], { always: ['bs', 'p'] });

// ------------------------------------------------------------------ write
const result = { format_version: src.format_version, animations: out };
fs.writeFileSync(OUT, JSON.stringify(result, null, '\t') + '\n');
console.log(Object.entries(out).map(([k, v]) => k.substring(P.length) + ' ' + v.animation_length + (v.loop ? ' ' + v.loop : '')).join('\n'));
