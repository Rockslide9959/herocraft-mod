// GALACTUS: THE DEVOURER -- every GeckoLib clip of the game models (used by gen_galactus_models.js --game).
// Clip lengths and strike ticks come from the timing table at the top of the generator (GAL_TIMING, mirrored by
// com.projecthero.mod.galactus.GalactusAnims): a play-once / held clip is (length - TRANSITION) ticks long, its strike
// keyframe sits on (hit - TRANSITION), loops are `length` ticks. `neck`, `head` (code-driven look) and `flinch`
// (code-driven hit flinch) are never keyed: sanitize() folds head/neck motion into the keyed `nod` bone above `head`
// and flinch into `chest`.
// Poses are rotation offsets in degrees on top of the rest pose (X > 0 tips a bone's top forward, Z > 0 swings a
// hanging limb toward the model's right / -X). Galactus is 60 blocks tall: everything is slow and heavy -- long
// anticipation, a committed strike, follow-through that settles over many ticks.
'use strict';
const L = require('./lib_concept_models.js');
const { addPose: ap, mirrorPose: mirP } = require('./lib_geckogen_anim.js');

const T = 4; // GalactusAnims.TRANSITION
const FIST = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 95], [`fingertips_${n}`]: [0, 0, s * 100], [`thumb_${n}`]: [0, 0, s * 40] }; };
const OPEN = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 12 * k], [`fingertips_${n}`]: [0, 0, s * 16 * k], [`thumb_${n}`]: [0, 0, 0] }; };
const CLAW = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 45], [`fingertips_${n}`]: [0, 0, s * 60], [`thumb_${n}`]: [0, 0, s * 20] }; };
const SPLAY = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, -s * 14], [`fingertips_${n}`]: [0, 0, -s * 6], [`thumb_${n}`]: [0, 0, -s * 30] }; };

function sanitize(p) {
	const o = {};
	for (const k of Object.keys(p)) if (k !== '$root' || p.$keepRoot) o[k] = p[k];
	delete o.$keepRoot;
	const fold = (from, to) => { if (o[from]) { o[to] = ap({ [to]: o[to] || [0, 0, 0] }, { [to]: o[from] })[to]; delete o[from]; } };
	fold('neck', 'nod'); fold('head', 'nod'); fold('flinch', 'chest');
	return o;
}
function mk(an, timing, name, build, opts = {}) {
	const t = timing[name];
	if (!t) throw new Error(`${an.id}: ${name} is not in the timing table`);
	const len = t.kind === 'loop' ? t.length : Math.max(1, t.length - T);
	const S = t.hit >= T ? t.hit - T : -1;
	const keep = p => { const o = {}; for (const k of Object.keys(p)) if (k.startsWith('$') || an.M.B[k]) o[k] = p[k]; return o; };
	const frames = build(len, S).map(([tk, p, e]) => [Math.round(tk), keep(sanitize(p)), e]);
	an.clip(name, len, t.kind === 'loop' ? true : t.kind === 'hold' ? 'hold' : false, frames, Object.assign({}, opts, { strikes: t.hit >= T ? [t.hit] : [] }));
	an.timing[name] = Object.assign({}, an.timing[name] || {});
}
const strike = p => Object.assign({}, p, { $strike: true });

// ================================================================ GALACTUS
function galactusClips(an, timing, poses) {
	const M = an.M, A = M.A, b = poses.stand;
	const legsBrace = { thigh_r: [-12, 0, 7], shin_r: [14, 0, 0], foot_r: [-2, 0, 0], thigh_l: [10, 0, -6], shin_l: [6, 0, 0], foot_l: [-16, 0, 0] };
	// idle: a slow, vast breath; the weight rolls from foot to foot; the gauntlets flex; the gaze drifts
	mk(an, timing, 'idle', len => {
		const q = len / 6;
		return [
			[0, ap(b, { chest: [-1, 0, 0] })],
			[q, ap(b, { chest: [-3, 0, 0], waist: [-1, 0, 0], arm_r: [0, 0, 2], arm_l: [0, 0, -2], nod: [-2, 0, 0] }), 'easeinoutsine'],
			[2 * q, ap(b, { chest: [0, -4, 0], hips: [0, 0, 2], thigh_r: [0, 0, -2], thigh_l: [0, 0, -2], nod: [1, 10, 0], ...OPEN('r', 0.6) }), 'easeinoutsine'],
			[3 * q, ap(b, { chest: [-2.5, -3, 0], hips: [0, 0, 2], thigh_r: [0, 0, -2], thigh_l: [0, 0, -2], nod: [-1, 8, 0], ...OPEN('r', 0.6) }), 'easeinoutsine'],
			[4 * q, ap(b, { chest: [0, 3, 0], hips: [0, 0, -2], thigh_r: [0, 0, 2], thigh_l: [0, 0, 2], nod: [3, -8, 0] }), 'easeinoutsine'],
			[5 * q, ap(b, { chest: [-3, 2, 0], hips: [0, 0, -1], thigh_r: [0, 0, 1], thigh_l: [0, 0, 1], nod: [0, -4, 0], ...CLAW('l') }), 'easeinoutsine'],
			[len, ap(b, { chest: [-1, 0, 0] }), 'easeinoutsine'],
		];
	});
	// eye beams: he draws himself up, fists pulled back, then leans in and the glare comes down (strike), held while the
	// beams burn, a heavy settle back
	const draw = ap(b, { chest: [-8, 0, 0], waist: [-3, 0, 0], nod: [-10, 0, 0], arm_r: [14, 0, 16], arm_l: [14, 0, -16], forearm_r: [-24, 0, 0], forearm_l: [-24, 0, 0], thigh_r: [-4, 0, 4], thigh_l: [4, 0, -4] });
	const glare = ap(b, legsBrace, { waist: [6, 0, 0], chest: [9, 0, 0], nod: [16, 0, 0], arm_r: [22, 0, 30], arm_l: [22, 0, -30], forearm_r: [-44, 0, 0], forearm_l: [-44, 0, 0] });
	mk(an, timing, 'eye_beam', (len, S) => [
		[0, b], [S * 0.65, draw, 'easeinoutsine'], [S, strike(glare), 'easeoutback'],
		[S + 8, ap(glare, { chest: [1, 2, 0], nod: [1, -3, 0] }), 'easeinoutsine'], [S + 16, ap(glare, { chest: [-1, -2, 0], nod: [2, 3, 0] }), 'easeinoutsine'],
		[S + 26, ap(glare, { chest: [1, 1, 0] }), 'easeinoutsine'], [S + 32, ap(draw, { nod: [6, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine'],
	]);
	// reach: the shoulder rolls back, then the whole body bends and the open hand comes down on the target (strike =
	// the grab check); the fingers close; the clip ends low with the hand shut (grab_lift continues from here)
	const reach = poses.reach;
	const wind = ap(b, { chest: [-6, 10, 0], waist: [-2, 6, 0], nod: [6, -6, 0], arm_r: [-30, 0, 34], forearm_r: [-50, 0, 0], ...OPEN('r', 1.6), thigh_r: [-8, 0, 4] });
	const closed = ap(reach, CLAW('r'));
	const shut = ap(reach, FIST('r'), { waist: [-2, 0, 0], chest: [-2, 0, 0] });
	mk(an, timing, 'reach', (len, S) => [
		[0, b], [S * 0.45, wind, 'easeinoutsine'], [S * 0.8, ap(reach, { arm_r: [-14, 0, 6], chest: [-4, 0, 0] }), 'easeinquad'], [S, strike(reach), 'easeoutquad'],
		[S + 6, closed, 'easeinoutsine'], [len, shut, 'easeinoutsine'],
	]);
	// grab lift: he straightens with the fist raised before his face to inspect what he caught (held)
	const lifted = ap(b, { chest: [-4, -10, 0], waist: [-2, -4, 0], nod: [10, 18, 0], arm_r: [-92, -10, 22], forearm_r: [-56, 0, 0], hand_r: [-10, 0, 0], ...FIST('r'), arm_l: [-6, 0, -8], forearm_l: [-20, 0, 0] });
	mk(an, timing, 'grab_lift', len => [
		[0, shut], [len * 0.4, ap(reach, FIST('r'), { waist: [-10, 0, 0], chest: [-8, 0, 0], arm_r: [-40, 0, 0], thigh_r: [20, 0, 0], shin_r: [-30, 0, 0] }), 'easeinoutsine'],
		[len * 0.85, ap(lifted, { arm_r: [-4, 0, 0], nod: [-2, 0, 0] }), 'easeoutquad'], [len, lifted, 'easeinoutsine'],
	]);
	// fling: the fist cocks back over the shoulder, the torso uncoils and the hand opens (strike = release)
	const cock = ap(b, { chest: [-8, 26, 0], waist: [-4, 14, 0], nod: [-6, -20, 0], arm_r: [-150, 0, 40], forearm_r: [-70, 0, 0], ...FIST('r'), arm_l: [-40, 0, -30], forearm_l: [-30, 0, 0], thigh_r: [10, 0, 6], thigh_l: [-14, 0, -6], shin_l: [14, 0, 0] });
	const throwP = ap(b, { chest: [12, -24, 0], waist: [6, -14, 0], nod: [10, 18, 0], arm_r: [-66, 0, -14], forearm_r: [-6, 0, 0], ...SPLAY('r'), arm_l: [24, 0, -26], forearm_l: [-20, 0, 0], thigh_l: [-22, 0, -6], shin_l: [22, 0, 0], foot_l: [0, 0, 0], thigh_r: [16, 0, 8], shin_r: [8, 0, 0], foot_r: [-24, 0, 0] });
	mk(an, timing, 'fling', (len, S) => [
		[0, lifted], [S * 0.7, cock, 'easeinoutsine'], [S, strike(throwP), 'easeinback'],
		[S + 5, ap(throwP, { chest: [4, -6, 0], arm_r: [10, 0, -6] }), 'easeoutquad'], [len, b, 'easeinoutsine'],
	]);
	// transmute: both palms come together before the emblem, power gathers between them, then the arms sweep wide and
	// the palms turn up (strike = the transmutation burst), held, lowered
	const gather = ap(b, { chest: [4, 0, 0], nod: [8, 0, 0], arm_r: [-60, -30, -12], forearm_r: [-62, 0, 0], hand_r: [0, 0, 0], arm_l: [-60, 30, 12], forearm_l: [-62, 0, 0], ...CLAW('r'), ...CLAW('l'), thigh_r: [-6, 0, 4], thigh_l: [6, 0, -4] });
	const spread = ap(b, legsBrace, { chest: [-10, 0, 0], waist: [-3, 0, 0], nod: [-12, 0, 0], arm_r: [-14, 0, 84], forearm_r: [-34, 0, 0], arm_l: [-14, 0, -84], forearm_l: [-34, 0, 0], ...SPLAY('r'), ...SPLAY('l') });
	mk(an, timing, 'transmute', (len, S) => [
		[0, b], [S * 0.5, gather, 'easeinoutsine'], [S * 0.85, ap(gather, { chest: [3, 0, 0], arm_r: [4, 0, -4], arm_l: [4, 0, 4] }), 'easeinoutsine'],
		[S, strike(spread), 'easeoutback'], [S + 10, ap(spread, { chest: [-2, 0, 0], nod: [-2, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
	]);
	// disintegrate: he levels an open palm at the target and the Power Cosmic unmakes it (strike); recoil through the arm
	const aim = ap(b, { chest: [-4, 18, 0], waist: [0, 8, 0], nod: [4, -16, 0], arm_r: [-34, 0, 30], forearm_r: [-70, 0, 0], ...CLAW('r'), arm_l: [-10, 0, -10] });
	const point = ap(b, legsBrace, { chest: [8, -14, 0], waist: [3, -6, 0], nod: [8, 14, 0], arm_r: [-92, -8, -4], forearm_r: [-4, 0, 0], hand_r: [-50, 0, 0], ...SPLAY('r'), arm_l: [20, 0, -24], forearm_l: [-30, 0, 0], ...FIST('l') });
	mk(an, timing, 'disintegrate', (len, S) => [
		[0, b], [S * 0.6, aim, 'easeinoutsine'], [S, strike(point), 'easeoutback'],
		[S + 4, ap(point, { arm_r: [10, 0, 0], chest: [-4, 0, 0], hand_r: [14, 0, 0] }), 'easeoutquad'], [S + 12, ap(point, { arm_r: [4, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
	]);
	// summon: the right arm rises to the sky, the head tips back, the hand clenches (charge) and bursts open (strike =
	// the Worldship answers / Punishers drop); the left fist drives down
	const raise = ap(b, { chest: [-10, 0, 0], waist: [-4, 0, 0], nod: [-18, 0, 0], arm_r: [-168, 0, 8], forearm_r: [-12, 0, 0], ...FIST('r'), arm_l: [10, 0, -16], forearm_l: [-10, 0, 0], ...FIST('l') });
	const call = ap(raise, legsBrace, { chest: [-4, 0, 0], nod: [-8, 0, 0], arm_r: [-6, 0, 2], forearm_r: [8, 0, 0], ...SPLAY('r'), arm_l: [16, 0, -22] });
	mk(an, timing, 'summon', (len, S) => [
		[0, b], [S * 0.55, raise, 'easeinoutsine'], [S * 0.85, ap(raise, { chest: [2, 0, 0], arm_r: [4, 0, 0] }), 'easeinoutsine'], [S, strike(call), 'easeoutback'],
		[S + 10, ap(call, { chest: [2, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
	]);
	// weak point hit: a jolt back, the left hand claps over the emblem, a knee gives, he recovers his height
	mk(an, timing, 'weakpoint_hurt', len => [
		[0, b], [3, ap(b, { chest: [-14, 10, 6], waist: [-5, 0, 0], nod: [-14, -12, 6], arm_r: [-18, 0, 28], forearm_r: [-30, 0, 0], ...SPLAY('r'), arm_l: [-56, 26, -4], forearm_l: [-88, 0, 0], ...CLAW('l'), thigh_r: [-10, 0, 4], shin_r: [16, 0, 0] }), 'easeoutquad'],
		[len * 0.55, ap(b, { chest: [6, 4, 2], nod: [10, -4, 0], arm_l: [-40, 20, 0], forearm_l: [-70, 0, 0], thigh_r: [-14, 0, 4], shin_r: [26, 0, 0], foot_r: [-10, 0, 0], thigh_l: [-6, 0, 0], shin_l: [10, 0, 0] }), 'easeinoutsine'],
		[len, b, 'easeinoutsine'],
	]);
	// retreat: he gathers himself low, arms sweep up and he rises off the ground toward the Worldship (held, high)
	const crouch = ap(b, { waist: [10, 0, 0], chest: [10, 0, 0], nod: [10, 0, 0], arm_r: [20, 0, 20], arm_l: [20, 0, -20], thigh_r: [-36, 0, 6], shin_r: [56, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-36, 0, -6], shin_l: [56, 0, 0], foot_l: [-20, 0, 0] });
	const rise = ap(b, { chest: [-10, 0, 0], nod: [-28, 0, 0], arm_r: [-150, 0, 30], forearm_r: [-6, 0, 0], arm_l: [-150, 0, -30], forearm_l: [-6, 0, 0], ...SPLAY('r'), ...SPLAY('l'), thigh_r: [6, 0, 3], shin_r: [8, 0, 0], foot_r: [30, 0, 0], thigh_l: [6, 0, -3], shin_l: [8, 0, 0], foot_l: [30, 0, 0], $ground: false });
	mk(an, timing, 'retreat', len => [
		[0, b], [len * 0.25, crouch, 'easeinoutsine'], [len * 0.4, ap(rise, { $root: [0, 4, 0], $keepRoot: true }), 'easeoutquad'],
		[len, ap(rise, { $root: [0, 140, 0], $keepRoot: true, arm_r: [-10, 0, 0], arm_l: [-10, 0, 0] }), 'easeinquad'],
	]);
}

// The render-only giant hand for the grab (Galactus's right gauntlet + hand, rooted at the palm).
function handClips(an, timing) {
	mk(an, timing, 'idle', len => [[0, OPEN('r', 1.4)], [len / 2, ap(OPEN('r', 2.2), { hand_r: [-4, 0, 3] }), 'easeinoutsine'], [len, OPEN('r', 1.4), 'easeinoutsine']], { ground: false });
	mk(an, timing, 'grab', (len, S) => [[0, SPLAY('r')], [S * 0.6, ap(SPLAY('r'), { hand_r: [8, 0, 0] }), 'easeinoutsine'], [S, strike(ap(FIST('r'), { hand_r: [-6, 0, 0] })), 'easeinback'], [len, ap(FIST('r'), { hand_r: [-2, 0, 0] }), 'easeoutquad']], { ground: false });
	mk(an, timing, 'release', len => [[0, FIST('r')], [len * 0.4, ap(SPLAY('r'), { hand_r: [-12, 0, 0] }), 'easeoutback'], [len, OPEN('r', 1.4), 'easeinoutsine']], { ground: false });
}

// ================================================================ THE PUNISHER
function punisherClips(an, timing, poses, speed) {
	const M = an.M, b = poses.stand;
	mk(an, timing, 'idle', len => {
		const q = len / 4;
		return [
			[0, b], [q, ap(b, { chest: [-2, 6, 0], nod: [0, -10, 0], arm_r: [-4, 0, 0], arm_l: [2, 0, 0] }), 'easeinoutsine'],
			[2 * q, ap(b, { chest: [1, 0, 0], waist: [1, 0, 0], thigh_r: [-3, 0, 0], shin_r: [5, 0, 0], thigh_l: [-3, 0, 0], shin_l: [5, 0, 0] }), 'easeinoutsine'],
			[3 * q, ap(b, { chest: [-1, -8, 0], nod: [0, 14, 0], arm_l: [-6, 0, 0], forearm_l: [-6, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
		];
	});
	// walk: a heavy stomp; stride solved so the planted foot does not skate at `speed` blocks/tick
	const cycle = timing.walk.length, want = speed * cycle / 2;
	const gait = k => ({
		contact: { thigh_r: [-24 * k, 0, 0], shin_r: [8 * k, 0, 0], foot_r: [16 * k, 0, 0], thigh_l: [20 * k, 0, 0], shin_l: [14 * k, 0, 0], foot_l: [-30 * k, 0, 0], hips: [0, -6, 0], chest: [4, 8, -2], arm_r: [12, 0, 0], arm_l: [-14, 0, 0], nod: [0, -6, 0] },
		passing: { thigh_r: [2, 0, 0], shin_r: [4, 0, 0], thigh_l: [-34 * k, 0, 0], shin_l: [50 * k, 0, 0], foot_l: [-12 * k, 0, 0], chest: [6, 0, 0], hips: [0, 0, -2] },
	});
	const stepOf = pose => { const W = M.world(pose); const a = L.affApply(W.foot_r, [-M.A.legX, 0.3, -3]), c = L.affApply(W.foot_l, [M.A.legX, 0.3, -3]); return Math.abs(a[2] - c[2]) * M.k; };
	let lo = 0.2, hi = 2.5;
	for (let i = 0; i < 30; i++) { const k = (lo + hi) / 2; if (stepOf(ap(b, gait(k).contact)) < want) lo = k; else hi = k; }
	const g = gait((lo + hi) / 2), q = cycle / 4;
	const C = ap(b, g.contact), D = ap(b, g.contact, { chest: [8, 0, 0], shin_r: [10, 0, 0], thigh_r: [4, 0, 0], foot_r: [-14, 0, 0] }), Pp = ap(b, g.passing, { $y: 0.8 });
	const mC = ap(b, mirP(g.contact)), mD = ap(b, mirP(g.contact), mirP({ chest: [8, 0, 0], shin_r: [10, 0, 0], thigh_r: [4, 0, 0], foot_r: [-14, 0, 0] })), mP = ap(b, mirP(g.passing), { $y: 0.8 });
	mk(an, timing, 'walk', () => [[0, C], [q * 0.4, D, 'easeoutquad'], [q, Pp, 'easeinoutsine'], [2 * q, mC, 'easeinoutsine'], [2 * q + q * 0.4, mD, 'easeoutquad'], [3 * q, mP, 'easeinoutsine'], [4 * q, C, 'easeinoutsine']], { speed });
	an.timing.walk.stride_blocks = +(want * 2).toFixed(2);
	// blast: both gauntlets come up level, a brace, the recoil kicks the arms up (strike = the shot), held, lowered
	const level = ap(poses.blast, { arm_r: [6, 0, 0], arm_l: [6, 0, 0] });
	const kick = ap(poses.blast, { arm_r: [-10, 0, 0], arm_l: [-10, 0, 0], chest: [-6, 0, 0], waist: [-2, 0, 0] });
	mk(an, timing, 'blast', (len, S) => [
		[0, b], [S * 0.6, level, 'easeinoutsine'], [S - 2, ap(level, { chest: [3, 0, 0] }), 'easeinoutsine'], [S, strike(kick), 'easeoutquad'],
		[S + 4, level, 'easeinoutsine'], [S + 10, ap(kick, { arm_r: [6, 0, 0], arm_l: [6, 0, 0] }), 'easeoutquad'], [S + 14, level, 'easeinoutsine'], [len, b, 'easeinoutsine'],
	]);
	// ground slam: both fists high, then down into the ground (strike = shockwave), the robot folds over them, rises
	const up = ap(b, { chest: [-14, 0, 0], waist: [-6, 0, 0], nod: [-10, 0, 0], arm_r: [-170, 0, -14], forearm_r: [-20, 0, 0], arm_l: [-170, 0, 14], forearm_l: [-20, 0, 0], thigh_r: [-6, 0, 6], thigh_l: [-6, 0, -6] });
	const down = ap(b, { chest: [34, 0, 0], waist: [16, 0, 0], nod: [10, 0, 0], arm_r: [-56, 0, -8], forearm_r: [-10, 0, 0], arm_l: [-56, 0, 8], forearm_l: [-10, 0, 0], thigh_r: [-40, 0, 8], shin_r: [52, 0, 0], foot_r: [-12, 0, 0], thigh_l: [-40, 0, -8], shin_l: [52, 0, 0], foot_l: [-12, 0, 0] });
	mk(an, timing, 'slam', (len, S) => [
		[0, b], [S * 0.65, up, 'easeinoutsine'], [S, strike(down), 'easeinquad'], [S + 3, ap(down, { chest: [-4, 0, 0], $y: 0.6 }), 'easeoutquad'], [S + 6, down, 'easeinquad'], [len, b, 'easeinoutsine'],
	]);
	// death: a jolt, the arms go dead, the knees fold, it kneels sparking, then pitches face-first into the ground
	const jolt = ap(b, { chest: [-16, 14, 6], nod: [-20, 10, 0], arm_r: [-40, 0, 30], arm_l: [-20, 0, -40], thigh_r: [-8, 0, 0], shin_r: [10, 0, 0] });
	const kneel = ap(b, { thigh_r: [-70, 0, 6], shin_r: [100, 0, 0], foot_r: [-30, 0, 0], thigh_l: [-60, 0, -6], shin_l: [100, 0, 0], foot_l: [-40, 0, 0], chest: [16, 0, 0], waist: [6, 0, 0], nod: [26, 0, 0], arm_r: [10, 0, 6], arm_l: [10, 0, -6], forearm_r: [0, 0, 0], forearm_l: [0, 0, 0], $ground: 'knees' });
	const fallen = ap(b, { root: [80, 0, 0], thigh_r: [-12, 0, 6], shin_r: [16, 0, 0], thigh_l: [-6, 0, -6], shin_l: [12, 0, 0], chest: [6, 0, 0], nod: [-10, 18, 0], arm_r: [-4, 0, 16], arm_l: [-4, 0, -16], forearm_r: [-4, 0, 0], forearm_l: [-4, 0, 0], $ground: 'body' });
	mk(an, timing, 'death', len => {
		const f = len / 50;
		return [[0, b], [5 * f, jolt, 'easeoutquad'], [10 * f, ap(jolt, { chest: [6, -20, -4], nod: [10, -14, 0] }), 'easeinoutsine'], [20 * f, kneel, 'easeinquad'],
			[28 * f, ap(kneel, { chest: [4, 6, 4], nod: [6, -10, 4] }), 'easeinoutsine'], [38 * f, fallen, 'easeinquad'], [42 * f, ap(fallen, { root: [-5, 0, 0], $y: 0.8 }), 'easeoutquad'], [46 * f, fallen, 'easeinquad'], [len, fallen]];
	});
}

// ================================================================ ELEMENT CONVERTER (a machine: no ground snapping)
function converterClips(an, timing) {
	const spin = (len, turns, extra = {}) => [[0, Object.assign({ core: [0, 0, 0], core_ring: [0, 0, 0], crown: [0, 0, 0] }, extra.a || {})], [len / 2, Object.assign({ core: [0, 180 * turns, 0], core_ring: [0, -180 * turns * 1.5, 0], crown: [0, -90 * turns, 0] }, extra.b || {}), 'linear'], [len, Object.assign({ core: [0, 360 * turns, 0], core_ring: [0, -360 * turns * 1.5, 0], crown: [0, -180 * turns, 0] }, extra.a || {}), 'linear']];
	const pulse = (len, k, n = 2) => { const o = []; for (let i = 0; i <= n * 2; i++) o.push([len * i / (n * 2), i % 2 ? 1 + k : 1, 'easeinoutsine']); return o; };
	mk(an, timing, 'idle', len => spin(len, 1), { ground: false, scaleKeys: { core: pulse(len(timing, 'idle'), 0.06, 2) } });
	const prongs = k => { const o = {}; [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([sx, sz], i) => { o['prong_' + i] = [-sz * k, 0, sx * k]; }); return o; }; // lean each prong outward
	mk(an, timing, 'consume', len => spin(len, 2, { a: prongs(4), b: prongs(14) }), { ground: false, scaleKeys: { core: pulse(len(timing, 'consume'), 0.16, 4) } });
	mk(an, timing, 'shielded', len => spin(len, 0.5), { ground: false, scaleKeys: { core: pulse(len(timing, 'shielded'), 0.04, 1), shield: [[0, 1], [len(timing, 'shielded') / 2, 1.03, 'easeinoutsine'], [len(timing, 'shielded'), 1, 'easeinoutsine']] } });
	// destroyed: the core flares and collapses, the orbit ring drops and jams, the spire cracks over and the crown falls
	const D = len(timing, 'destroyed');
	mk(an, timing, 'destroyed', l => [
		[0, {}], [6, { core: [0, 40, 0], core_ring: [0, -60, 0], spire: [-3, 0, 2] }, 'easeoutquad'],
		[18, { core: [0, 60, 0], core_ring: [28, -70, 16], spire: [6, 0, -4], crown: [0, 0, 0] }, 'easeinquad'],
		[30, { core_ring: [36, -70, 22], spire: [24, 0, -10], crown: [20, 0, -12] }, 'easeinquad'],
		[42, { core_ring: [34, -70, 20], spire: [40, 0, -14], crown: [64, 0, -30] }, 'easeinquad'],
		[46, { core_ring: [34, -70, 20], spire: [37, 0, -13], crown: [58, 0, -28] }, 'easeoutquad'], [l, { core_ring: [34, -70, 20], spire: [40, 0, -14], crown: [62, 0, -30] }, 'easeinoutsine'],
	], { ground: false, scaleKeys: { core: [[0, 1], [5, 1.35, 'easeoutquad'], [16, 0, 'easeinback']] } });
	void D;
}
function len(timing, n) { const t = timing[n]; return t.kind === 'loop' ? t.length : Math.max(1, t.length - T); }

// ================================================================ WORLDSHIP (sky prop)
function worldshipClips(an, timing) {
	mk(an, timing, 'idle', l => [
		[0, { ring: [0, 0, 0], crown: [0, 0, 0], $root: [0, 0, 0], $keepRoot: true }],
		[l / 4, { ring: [0, 90, 0], crown: [0, -30, 0], $root: [0, 2, 0], $keepRoot: true }, 'linear'],
		[l / 2, { ring: [0, 180, 0], crown: [0, -60, 0], $root: [0, 0, 0], $keepRoot: true }, 'linear'],
		[3 * l / 4, { ring: [0, 270, 0], crown: [0, -90, 0], $root: [0, -2, 0], $keepRoot: true }, 'linear'],
		[l, { ring: [0, 360, 0], crown: [0, -120, 0], $root: [0, 0, 0], $keepRoot: true }, 'linear'],
	], { ground: false });
	mk(an, timing, 'leave', l => [
		[0, { ring: [0, 0, 0], $root: [0, 0, 0], $keepRoot: true }],
		[l * 0.3, { ring: [0, 160, 0], root: [-4, 0, 0], $root: [0, -6, 0], $keepRoot: true }, 'easeinoutsine'],
		[l, { ring: [0, 900, 0], root: [-10, 0, 4], $root: [0, 220, 0], $keepRoot: true }, 'easeinquad'],
	], { ground: false });
}

// ================================================================ the small props
function boardClips(an, timing) {
	mk(an, timing, 'idle', l => [
		[0, { board: [0, 0, 0], $root: [0, 0, 0], $keepRoot: true }], [l / 4, { board: [1.5, 0, 2], $root: [0, 0.6, 0], $keepRoot: true }, 'easeinoutsine'],
		[l / 2, { board: [0, 0, 0], $root: [0, 1.0, 0], $keepRoot: true }, 'easeinoutsine'], [3 * l / 4, { board: [-1.5, 0, -2], $root: [0, 0.6, 0], $keepRoot: true }, 'easeinoutsine'],
		[l, { board: [0, 0, 0], $root: [0, 0, 0], $keepRoot: true }, 'easeinoutsine'],
	], { ground: false });
}
function nullifierClips(an, timing) {
	const pulse = (l, k, n) => { const o = []; for (let i = 0; i <= n * 2; i++) o.push([l * i / (n * 2), i % 2 ? 1 + k : 1, 'easeinoutsine']); return o; };
	mk(an, timing, 'idle', l => [[0, {}], [l, {}]], { ground: false, scaleKeys: { core: pulse(len(timing, 'idle'), 0.06, 1) } });
	mk(an, timing, 'armed', l => [[0, { trigger: [0, 0, 0] }], [l / 2, { trigger: [-14, 0, 0] }, 'easeinoutsine'], [l, { trigger: [0, 0, 0] }, 'easeinoutsine']], { ground: false, scaleKeys: { core: pulse(len(timing, 'armed'), 0.18, 2) } });
}

module.exports = { galactusClips, handClips, punisherClips, converterClips, worldshipClips, boardClips, nullifierClips, T };
