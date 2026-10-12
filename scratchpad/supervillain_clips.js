// v0.16.1 Supervillain bosses: every GeckoLib clip of the Chimera, the Arsenal and the Omega Mage, plus the --game export
// (called by gen_supervillain_models.js --game).
//
// TIMING: SupervillainAnims.java is the contract (parsed below). A play-once / held clip is (length - TRANSITION) ticks long
// with its strike keyframe on (hit - TRANSITION); loops are `length` ticks. Moves that hit the moment the server starts them
// (hit = TRANSITION) open on their strike pose, then follow through; telegraphed casts wind up for cycles x 10 ticks and
// release on the strike keyframe. `neck` and `flinch` are code-driven and never keyed (sanitize() folds them away).
// Poses are rotation offsets in degrees on top of the rest pose (X > 0 tips a bone's top forward; Z > 0 swings a hanging
// limb toward the model's right / -X). Dangle bones (mane, tail, robes, cape, flaps, chain, antenna) get spring-simulated
// follow-through on top.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const A = require('./lib_geckogen_anim.js');
const G = require('./gen_supervillain_models.js');
const { addPose: ap, mirrorPose: mirP, scalePose } = A;

const T = 4;
const REPO = G.REPO;
const strike = p => Object.assign({}, p, { $strike: true });
const FIST = G.FIST, OPEN = G.OPEN;

function sanitize(p) {
	const o = Object.assign({}, p);
	if (o.neck) { o.head = ap({ head: o.head || [0, 0, 0] }, { head: o.neck }).head; delete o.neck; }
	if (o.flinch) { o.chest = ap({ chest: o.chest || [0, 0, 0] }, { chest: o.flinch }).chest; delete o.flinch; }
	return o;
}

function parseTiming() {
	const src = fs.readFileSync(path.join(REPO, 'src/main/java/com/projecthero/mod/event/entity/SupervillainAnims.java'), 'utf8');
	const t = {};
	for (const m of src.matchAll(/= (clip|hold|loop)\("(\w+)", (\d+)(?:, (-?\d+), (\d+))?\);/g))
		t[m[2]] = { kind: m[1] === 'clip' ? 'once' : m[1], length: +m[3], hit: m[4] != null ? +m[4] : -1 };
	const tr = /TRANSITION = (\d+);/.exec(src);
	if (!tr || +tr[1] !== T) throw new Error('SupervillainAnims.TRANSITION is not 4');
	return t;
}

// one timing-checked clip; frames use CLIP ticks, `S` = the strike clip tick (or -1)
function mk(an, timing, name, build, opts = {}) {
	const t = timing[name];
	if (!t) throw new Error(`${an.id}: ${name} is not in SupervillainAnims`);
	const len = t.kind === 'loop' ? t.length : Math.max(1, t.length - T);
	const S = t.hit >= T ? t.hit - T : -1;
	const keep = p => { const o = {}; for (const k of Object.keys(p)) if (k.startsWith('$') || an.M.B[k]) o[k] = p[k]; return o; };
	const hov = an.hover; // floating model: every frame gets an explicit root height
	const frames = build(len, S).map(([tk, p, e]) => {
		let q = keep(sanitize(p));
		if (hov && !q.$root) q = Object.assign(q, { $root: [0, (q.$h == null ? hov : q.$h), q.$z || 0], $ground: false });
		delete q.$h; if (hov) delete q.$z;
		return [Math.round(tk), q, e];
	});
	// collapse duplicate ticks (keep the last)
	const ded = [];
	for (const f of frames) { if (ded.length && ded[ded.length - 1][0] === f[0]) ded[ded.length - 1] = f; else ded.push(f); }
	an.clip(name, len, t.kind === 'loop' ? true : t.kind === 'hold' ? 'hold' : false, ded,
		Object.assign({}, opts, { strikes: t.hit >= T ? [t.hit] : [] }));
}

// stride-matched gait for the two walkers (Chimera's goat legs, Arsenal's armoured legs)
function stepOf(M, pose) {
	const W = M.world(pose);
	const pr = L.affApply(W.foot_r, M.B.foot_r.pivot), pl = L.affApply(W.foot_l, M.B.foot_l.pivot);
	return Math.abs(pr[2] - pl[2]) * M.k;
}
function gaitPoses(a, k) {
	const th = a.th * k, lift = a.lift * k;
	const contact = {
		thigh_r: [-th, 0, 0], shin_r: [a.kc, 0, 0], foot_r: [th - a.kc + a.fc, 0, 0],
		thigh_l: [th * 0.8, 0, 0], shin_l: [a.kb, 0, 0], foot_l: [-th * 0.8 - a.kb + a.fb, 0, 0],
		hips: [0, -a.yaw, 2], waist: [a.lean * 0.5, a.yaw * 0.5, 0], chest: [a.lean * 0.5, a.yaw * 0.8, -1.5],
		arm_l: [-a.arm, 0, 0], forearm_l: [-a.fa, 0, 0], arm_r: [a.arm * a.wArm, 0, 0], forearm_r: [-a.fa * Math.abs(a.wArm), 0, 0],
	};
	const down = { thigh_r: [-th + 5, 0, 0], shin_r: [a.kc + 10, 0, 0], foot_r: [th - a.kc - 8, 0, 0], chest: [a.lean * 0.5 + 3, 0, 0], $y: -a.rise * 0.6 };
	const passing = {
		thigh_r: [2, 0, 0], shin_r: [3, 0, 0], foot_r: [-4, 0, 0],
		thigh_l: [-lift, 0, 0], shin_l: [lift * 1.6, 0, 0], foot_l: [-lift * 0.5, 0, 0],
		hips: [0, 0, -1], waist: [a.lean * 0.5, 0, 0], chest: [a.lean * 0.5 - 1, 0, 0], $y: a.rise,
	};
	return { contact, down, passing };
}
function gait(an, timing, name, base, a, speed, extra, maxK = 3) {
	const M = an.M;
	const cycle = timing[name].length;
	const want = speed * cycle / 2;
	let lo = 0.1, hi = maxK;
	for (let i = 0; i < 30; i++) { const k = (lo + hi) / 2; if (stepOf(M, ap(base, gaitPoses(a, k).contact)) < want) lo = k; else hi = k; }
	const g = gaitPoses(a, (lo + hi) / 2);
	const q = cycle / 4;
	const ex = extra || (() => ({}));
	const C = ap(base, g.contact, ex(0)), D = ap(base, g.contact, g.down, ex(0.12)), P = ap(base, g.passing, ex(0.25));
	const mC = ap(base, mirP(g.contact), ex(0.5)), mD = ap(base, mirP(g.contact), mirP(g.down), ex(0.62)), mPp = ap(base, mirP(g.passing), ex(0.75));
	mk(an, timing, name, () => [[0, C], [q * 0.45, D, 'easeoutquad'], [q, P, 'easeinoutsine'], [2 * q, mC, 'easeinoutsine'], [2 * q + q * 0.45, mD, 'easeoutquad'], [3 * q, mPp, 'easeinoutsine'], [4 * q, ap(base, g.contact, ex(1)), 'easeinoutsine']], { speed });
}
const sway = (bones, amp, phase = 0, axis = 1) => t => { const o = {}; bones.forEach((b, i) => { const v = [0, 0, 0]; v[axis] = amp * Math.sin((t + phase + i * 0.12) * Math.PI * 2); o[b] = v; }); return o; };

// ================================================================ CHIMERA
function chimeraClips(an, timing, poses) {
	const M = an.M, b = poses.stand;
	const TAIL = ['tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6'];
	const tailSway = (t, k = 1) => { const o = {}; TAIL.forEach((n, i) => { o[n] = [3 * k * Math.sin((t * 2 + i * 0.15) * Math.PI), 9 * k * Math.sin((t + i * 0.13) * Math.PI * 2), 0]; }); o.tail_head = [-6 * k * Math.sin(t * Math.PI * 2), 14 * k * Math.sin((t + 0.8) * Math.PI * 2), 0]; return o; };
	const CLAWS_OUT = { fingers_r: [-26, 0, 0], fingers_l: [0, 0, 36], fingertips_l: [0, 0, 44] };

	// idle: heavy breathing, the mane rising with it, the snake head looking about, the tail swaying, a growl
	mk(an, timing, 'idle', len => {
		const f = i => ap(b, tailSway(i / 8));
		return [
			[0, ap(f(0), { chest: [-1, 0, 0] })],
			[len * 0.25, ap(f(2), { chest: [-4, 0, 0], waist: [-1, 0, 0], arm_r: [-2, 0, 2], arm_l: [-2, 0, -2], head: [-4, 6, 0], jaw: [4, 0, 0], mane: [-3, 0, 0] }), 'easeinoutsine'],
			[len * 0.45, ap(f(3.6), { chest: [1, -4, 0], head: [2, 22, -3], jaw: [10, 0, 0], tail_head: [10, -30, 0], tail_jaw: [30, 0, 0], hips: [0, 0, 1.5] }), 'easeinoutsine'],
			[len * 0.6, ap(f(4.8), { chest: [-3, -2, 0], head: [-2, 16, -2], tail_jaw: [0, 0, 0], ...CLAWS_OUT }), 'easeinoutsine'],
			[len * 0.8, ap(f(6.4), { chest: [0, 3, 0], head: [3, -14, 2], jaw: [6, 0, 0], hips: [0, 0, -1.5] }), 'easeinoutsine'],
			[len, ap(f(8), { chest: [-1, 0, 0] }), 'easeinoutsine'],
		];
	});
	// idle variation: sniffs the air, then the serpent tail rears up over his shoulder and hisses
	mk(an, timing, 'fidget', len => {
		const sniff = ap(b, { head: [-24, 0, 0], neck: [0, 0, 0], chest: [-6, 0, 0], jaw: [2, 0, 0] });
		const rear = ap(b, { tail1: [34, -10, 0], tail2: [30, -10, 0], tail3: [26, 0, 0], tail4: [20, 10, 0], tail5: [-10, 20, 0], tail_head: [-30, 40, 0], tail_jaw: [40, 0, 0], head: [0, 40, 0], chest: [0, 10, 0] });
		return [[0, b], [len * 0.15, sniff, 'easeinoutsine'], [len * 0.22, ap(sniff, { head: [-18, 0, 0] }), 'easeinoutsine'], [len * 0.3, sniff, 'easeinoutsine'],
			[len * 0.5, rear, 'easeoutback'], [len * 0.6, ap(rear, { tail_head: [-20, 30, 0], tail_jaw: [10, 0, 0] }), 'easeinoutsine'], [len * 0.7, ap(rear, { tail_jaw: [45, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']];
	});
	const walkAmp = { th: 22, kc: 6, kb: 18, fc: 2, fb: 6, lift: 26, yaw: 6, lean: 4, arm: 16, wArm: -1, fa: 6, rise: 0.8 };
	gait(an, timing, 'walk', b, walkAmp, 0.15, t => ap(tailSway(t, 0.8), { head: [3 * Math.sin(t * Math.PI * 4), 0, 0] }));
	// run: a bounding knuckle-gallop on all fours -- both arms reach and plant, the hind legs drive
	{
		const len = timing.run.length;
		const lean = { chest: [34, 0, 0], waist: [14, 0, 0], head: [-34, 0, 0], tail1: [10, 0, 0], tail2: [8, 0, 0] };
		const reach = ap(b, lean, { arm_r: [-70, 0, 6], forearm_r: [-10, 0, 0], arm_l: [-74, 0, -6], forearm_l: [-6, 0, 0], thigh_r: [30, 0, 0], shin_r: [10, 0, 0], thigh_l: [24, 0, 0], shin_l: [12, 0, 0], $y: 2, ...CLAWS_OUT });
		const plant = ap(b, lean, { chest: [42, 0, 0], arm_r: [-20, 0, 6], forearm_r: [-20, 0, 0], arm_l: [-24, 0, -6], forearm_l: [-20, 0, 0], thigh_r: [-10, 0, 0], shin_r: [20, 0, 0], thigh_l: [-4, 0, 0], shin_l: [24, 0, 0], $ground: 'all' });
		const gather = ap(b, lean, { arm_r: [20, 0, 6], forearm_r: [-40, 0, 0], arm_l: [16, 0, -6], forearm_l: [-40, 0, 0], thigh_r: [-50, 0, 0], shin_r: [40, 0, 0], foot_r: [10, 0, 0], thigh_l: [-44, 0, 0], shin_l: [40, 0, 0], $y: 1 });
		mk(an, timing, 'run', () => [[0, reach], [len * 0.3, plant, 'easeinquad'], [len * 0.65, gather, 'easeinoutsine'], [len, reach, 'easeoutquad']], { speed: 0.36 });
	}
	// air: arms flung out, legs tucked, the tail streaming behind
	mk(an, timing, 'air', len => {
		const p = ap(b, { arm_r: [-40, 0, 40], arm_l: [-40, 0, -40], forearm_r: [-20, 0, 0], forearm_l: [-30, 0, 0], thigh_r: [-40, 0, 6], shin_r: [50, 0, 0], thigh_l: [-30, 0, -6], shin_l: [44, 0, 0], chest: [-6, 0, 0], head: [-10, 0, 0], jaw: [20, 0, 0], tail1: [20, 0, 0], tail2: [10, 0, 0], tail3: [6, 0, 0], ...CLAWS_OUT, $ground: false, $root: [0, 4, 0] });
		return [[0, p], [len / 2, ap(p, { arm_r: [-8, 0, 6], arm_l: [-8, 0, -6], chest: [3, 0, 0], tail4: [10, 10, 0] }), 'easeinoutsine'], [len, p, 'easeinoutsine']];
	}, { ground: false });
	// intro: hunched low and coiled, then he rears up, flings his arms wide and ROARS (the core flaring behind the ribs)
	mk(an, timing, 'intro', (len, S) => {
		const coil = ap(b, { chest: [24, 0, 0], waist: [10, 0, 0], head: [20, 0, 0], arm_r: [10, 0, -10], arm_l: [10, 0, 10], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], thigh_r: [-36, 0, 6], shin_r: [36, 0, 0], foot_r: [-6, 0, 0], thigh_l: [-30, 0, -6], shin_l: [34, 0, 0], tail1: [-10, 0, 0], mane: [8, 0, 0] });
		const roar = ap(b, { chest: [-30, 0, 0], waist: [-10, 0, 0], head: [-34, 0, 0], jaw: [48, 0, 0], arm_r: [-46, 0, 66], forearm_r: [-30, 0, 0], arm_l: [-46, 0, -66], forearm_l: [-30, 0, 0], thigh_r: [-12, 0, 8], thigh_l: [8, 0, -8], foot_l: [-8, 0, 0], mane: [-14, 0, 0], tail1: [30, 0, 0], tail2: [20, 0, 0], tail_head: [-30, 0, 0], tail_jaw: [40, 0, 0], ...CLAWS_OUT });
		return [[0, b], [S * 0.4, coil, 'easeinoutsine'], [S * 0.75, ap(coil, { chest: [6, 0, 0], head: [6, 0, 0] }), 'easeinoutsine'], [S, strike(roar), 'easeoutback'],
			[S + 4, ap(roar, { head: [-3, 8, 4], chest: [0, 5, 0] }), 'easeinoutsine'], [S + 8, ap(roar, { head: [3, -8, -4], chest: [0, -5, 0] }), 'easeinoutsine'],
			[S + 12, ap(roar, { head: [-2, 5, 2] }), 'easeinoutsine'], [S + 18, ap(roar, { jaw: [-30, 0, 0], arm_r: [40, 0, -30], arm_l: [40, 0, 30] }), 'easeinoutsine'], [len, b, 'easeinoutsine']];
	});
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-16, 10, 6], waist: [-4, 0, 0], head: [-16, -14, 8], arm_r: [-14, 0, 14], arm_l: [-10, 0, -16], jaw: [24, 0, 0], tail1: [16, 0, 0], tail_jaw: [30, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// death: staggers, the core sputters (a clutch at the chest), knees buckle, crashes onto his side, the tail flops
	mk(an, timing, 'death', len => {
		const st = ap(b, { chest: [-14, 8, 6], head: [-24, 10, 0], jaw: [30, 0, 0], arm_r: [-40, 0, -20], forearm_r: [-60, 0, 0], arm_l: [-20, 0, -30], thigh_r: [-10, 0, 0], shin_r: [10, 0, 0], tail_jaw: [30, 0, 0] });
		const kneel = ap(b, { thigh_r: [-60, 0, 4], shin_r: [70, 0, 0], foot_r: [-10, 0, 0], thigh_l: [-50, 0, -4], shin_l: [72, 0, 0], chest: [20, 0, 0], head: [24, 0, 0], arm_r: [10, 0, 10], arm_l: [10, 0, -10], $ground: 'knees' });
		const fallen = ap(b, { root: [0, 0, 82], chest: [8, 0, 0], head: [-6, 10, 0], jaw: [24, 0, 0], arm_r: [-60, 0, 20], arm_l: [-20, 0, -20], thigh_r: [-30, 0, 0], shin_r: [30, 0, 0], thigh_l: [-20, 0, 0], shin_l: [40, 0, 0], tail1: [0, 20, -20], tail3: [0, -20, 0], tail_head: [0, 0, 0], tail_jaw: [10, 0, 0], $ground: 'all' });
		const f = len / 36;
		return [[0, b], [6 * f, st, 'easeoutquad'], [14 * f, kneel, 'easeinquad'], [19 * f, ap(kneel, { chest: [6, 8, 4], head: [8, -10, 6] }), 'easeinoutsine'],
			[27 * f, fallen, 'easeinquad'], [30 * f, ap(fallen, { root: [0, 0, -5], $y: 0.6 }), 'easeoutquad'], [33 * f, fallen, 'easeinquad'], [len, ap(fallen, { tail_head: [0, -10, 0] })]];
	});
	// melee: the lion arm rakes across (opens on the strike), follows through past the body, recovers
	mk(an, timing, 'melee', (len, S) => {
		const hit = ap(b, { chest: [10, -26, 0], waist: [4, -12, 0], head: [-6, 18, 0], jaw: [26, 0, 0], arm_r: [-96, -30, 30], forearm_r: [-10, 0, 0], fingers_r: [-30, 0, 0], arm_l: [-10, 0, -20], thigh_r: [-22, 0, 4], shin_r: [16, 0, 0], thigh_l: [12, 0, -4] });
		const thru = ap(b, { chest: [14, -40, 0], waist: [6, -18, 0], head: [-2, 24, 0], jaw: [12, 0, 0], arm_r: [-60, -60, 10], forearm_r: [-30, 0, 0], arm_l: [-4, 0, -26], thigh_r: [-22, 0, 4], shin_r: [16, 0, 0], tail1: [0, 20, 0], tail3: [0, 20, 0] });
		return [[0, strike(hit)], [3, thru, 'easeoutcubic'], [6, ap(thru, { chest: [-2, 4, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']];
	});
	// melee_alt: the serpent tail whips over his shoulder and bites while the scaled arm slashes up
	mk(an, timing, 'melee_alt', () => {
		const hit = ap(b, { chest: [12, 20, 0], waist: [4, 8, 0], head: [-6, -16, 0], arm_l: [-110, 20, -30], forearm_l: [-10, 0, 0], fingers_l: [0, 0, 40], fingertips_l: [0, 0, 50], tail1: [50, 0, 0], tail2: [44, 0, 0], tail3: [30, 10, 0], tail4: [10, 10, 0], tail5: [-30, 0, 0], tail_head: [-60, 0, 0], tail_jaw: [50, 0, 0], thigh_l: [-20, 0, -4], shin_l: [16, 0, 0] });
		const thru = ap(hit, { arm_l: [40, 0, 10], forearm_l: [-30, 0, 0], tail_jaw: [-40, 0, 0], tail5: [10, 0, 0], chest: [4, 10, 0] });
		return [[0, strike(hit)], [3, thru, 'easeoutcubic'], [7, ap(thru, { tail1: [-14, 0, 0] }), 'easeinoutsine'], [12, b, 'easeinoutsine']];
	});
	// repel: hammers both fists into the ground and roars the crowd away
	mk(an, timing, 'repel', () => {
		const pound = ap(b, { chest: [40, 0, 0], waist: [12, 0, 0], head: [-30, 0, 0], jaw: [46, 0, 0], arm_r: [-60, 0, -10], forearm_r: [-10, 0, 0], arm_l: [-64, 0, 10], forearm_l: [-10, 0, 0], thigh_r: [-36, 0, 8], shin_r: [40, 0, 0], thigh_l: [-30, 0, -8], shin_l: [40, 0, 0], tail1: [30, 0, 0], tail2: [20, 0, 0], mane: [-10, 0, 0] });
		return [[0, strike(pound)], [3, ap(pound, { chest: [4, 0, 0], arm_r: [6, 0, 0], arm_l: [6, 0, 0] }), 'easeoutquad'], [8, ap(pound, { jaw: [-20, 0, 0], chest: [-10, 0, 0] }), 'easeinoutsine'], [14, b, 'easeinoutsine']];
	});
	// burst: rears up, chest thrust out so the core blazes, a short roar
	mk(an, timing, 'burst', () => {
		const p = ap(b, { chest: [-26, 0, 0], waist: [-8, 0, 0], head: [-30, 0, 0], jaw: [40, 0, 0], arm_r: [-20, 0, 38], arm_l: [-20, 0, -38], forearm_r: [-40, 0, 0], forearm_l: [-40, 0, 0], mane: [-12, 0, 0], tail1: [24, 0, 0], ...CLAWS_OUT });
		return [[0, strike(p)], [4, ap(p, { chest: [-4, 0, 0], head: [-4, 6, 0] }), 'easeoutquad'], [9, ap(p, { head: [2, -6, 0] }), 'easeinoutsine'], [16, b, 'easeinoutsine']];
	});
	// slam: the lion arm comes down like a hammer on the ground
	mk(an, timing, 'slam', () => {
		const p = ap(b, { chest: [36, -10, 0], waist: [12, -6, 0], head: [-26, 10, 0], jaw: [30, 0, 0], arm_r: [-50, 0, -6], forearm_r: [-6, 0, 0], arm_l: [-20, 0, -30], thigh_r: [-40, 0, 6], shin_r: [42, 0, 0], thigh_l: [-10, 0, -6], shin_l: [26, 0, 0], tail1: [26, 0, 0] });
		return [[0, strike(p)], [3, ap(p, { chest: [4, 0, 0], arm_r: [4, 0, 0] }), 'easeoutquad'], [8, ap(p, { chest: [-6, 0, 0] }), 'easeinoutsine'], [16, b, 'easeinoutsine']];
	});
	// shoot: hurls the power forward from the open lion paw while the snake head spits over his shoulder
	mk(an, timing, 'shoot', () => {
		const p = ap(b, { chest: [16, -18, 0], waist: [6, -10, 0], head: [-14, 16, 0], jaw: [30, 0, 0], arm_r: [-94, -10, 4], forearm_r: [0, 0, 0], fingers_r: [-40, 0, 0], arm_l: [20, 0, -20], forearm_l: [-30, 0, 0], thigh_r: [-30, 0, 4], shin_r: [20, 0, 0], thigh_l: [16, 0, -4], tail1: [40, 10, 0], tail2: [30, 0, 0], tail4: [-10, 0, 0], tail_head: [-50, 0, 0], tail_jaw: [50, 0, 0] });
		return [[0, strike(p)], [3, ap(p, { arm_r: [16, 0, 0], chest: [-6, 4, 0] }), 'easeoutquad'], [7, ap(p, { tail_jaw: [-30, 0, 0] }), 'easeinoutsine'], [12, b, 'easeinoutsine']];
	});
	// lunge: a low pounce, both claws leading
	mk(an, timing, 'lunge', () => {
		const p = ap(b, { chest: [40, 0, 0], waist: [16, 0, 0], head: [-36, 0, 0], jaw: [40, 0, 0], arm_r: [-100, 0, 10], arm_l: [-100, 0, -10], forearm_r: [-6, 0, 0], forearm_l: [-6, 0, 0], thigh_r: [30, 0, 4], shin_r: [20, 0, 0], thigh_l: [36, 0, -4], shin_l: [16, 0, 0], tail1: [-20, 0, 0], tail2: [-10, 0, 0], ...CLAWS_OUT, $ground: 'all' });
		return [[0, strike(p)], [4, ap(p, { chest: [-6, 0, 0], arm_r: [10, 0, 0], arm_l: [10, 0, 0] }), 'easeoutquad'], [12, b, 'easeinoutsine']];
	});
	// blink: lands crouched out of nowhere and rises, shaking the mane
	mk(an, timing, 'blink', () => {
		const p = ap(b, { chest: [24, 0, 0], head: [10, 0, 0], arm_r: [-20, 0, 20], arm_l: [-20, 0, -20], thigh_r: [-40, 0, 6], shin_r: [44, 0, 0], thigh_l: [-36, 0, -6], shin_l: [44, 0, 0], mane: [10, 0, 0] });
		return [[0, strike(p)], [4, ap(p, { head: [-6, 20, 0], chest: [-6, 0, 0] }), 'easeoutquad'], [7, ap(b, { head: [0, -16, 0] }), 'easeinoutsine'], [10, b, 'easeinoutsine']];
	});
	// casts: crouch and gather (the core pulsing under splayed claws), then release -- arms flung forward and a roar.
	// cast_4 (the ultimates): rears up to full height first, arms high, then brings everything down on the release.
	const gatherP = ap(b, { chest: [22, 0, 0], waist: [8, 0, 0], head: [6, 0, 0], jaw: [16, 0, 0], arm_r: [-40, 30, -6], forearm_r: [-70, 0, 0], arm_l: [-40, -30, 6], forearm_l: [-70, 0, 0], thigh_r: [-30, 0, 6], shin_r: [34, 0, 0], thigh_l: [-26, 0, -6], shin_l: [32, 0, 0], tail1: [10, 0, 0], mane: [6, 0, 0], ...CLAWS_OUT });
	const releaseP = ap(b, { chest: [-14, 0, 0], waist: [-4, 0, 0], head: [-26, 0, 0], jaw: [46, 0, 0], arm_r: [-68, -10, 22], forearm_r: [-6, 0, 0], arm_l: [-72, 10, -22], forearm_l: [-6, 0, 0], thigh_r: [-24, 0, 8], shin_r: [20, 0, 0], thigh_l: [10, 0, -8], foot_l: [-8, 0, 0], tail1: [30, 0, 0], tail2: [20, 0, 0], tail_jaw: [40, 0, 0], mane: [-12, 0, 0], ...CLAWS_OUT });
	for (const [n, big] of [['cast_1', 0], ['cast_2', 0], ['cast_3', 0.5], ['cast_4', 1]]) {
		mk(an, timing, n, (len, S) => {
			const fr = [[0, b]];
			if (big) {
				const rise = ap(b, { chest: [-30, 0, 0], waist: [-10, 0, 0], head: [-30, 0, 0], jaw: [30, 0, 0], arm_r: [-130, 0, 24], forearm_r: [-20, 0, 0], arm_l: [-130, 0, -24], forearm_l: [-20, 0, 0], thigh_r: [-8, 0, 4], thigh_l: [-8, 0, -4], tail1: [30, 0, 0], tail2: [24, 0, 0], tail_head: [-30, 0, 0], tail_jaw: [40, 0, 0], ...CLAWS_OUT });
				const smash = ap(b, { chest: [44, 0, 0], waist: [14, 0, 0], head: [-34, 0, 0], jaw: [48, 0, 0], arm_r: [-64, 0, -8], forearm_r: [-6, 0, 0], arm_l: [-64, 0, 8], forearm_l: [-6, 0, 0], thigh_r: [-40, 0, 8], shin_r: [44, 0, 0], thigh_l: [-34, 0, -8], shin_l: [44, 0, 0], tail1: [36, 0, 0], tail2: [24, 0, 0] });
				fr.push([S * 0.3, gatherP, 'easeinoutsine'], [S * 0.45, ap(gatherP, { chest: [4, 3, 0] }), 'easeinoutsine'], [S * 0.6, ap(gatherP, { chest: [-2, -3, 0] }), 'easeinoutsine'],
					[S * 0.85, rise, 'easeinoutsine'], [S - 2, ap(rise, { chest: [-6, 0, 0], arm_r: [-8, 0, 0], arm_l: [-8, 0, 0] }), 'easeoutquad'], [S, strike(big === 1 ? smash : releaseP), 'easeinquad'],
					[S + 4, ap(big === 1 ? smash : releaseP, { chest: [4, 0, 0] }), 'easeoutquad'], [S + 9, ap(big === 1 ? smash : releaseP, { chest: [-6, 0, 0], head: [-4, 8, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']);
			} else {
				fr.push([S * 0.55, gatherP, 'easeinoutsine'], [S * 0.8, ap(gatherP, { chest: [6, 0, 0], head: [6, 0, 0], arm_r: [10, 0, 0], arm_l: [10, 0, 0] }), 'easeinoutsine'],
					[S, strike(releaseP), 'easeoutback'], [S + 4, ap(releaseP, { chest: [-4, 0, 0], head: [-2, 6, 0] }), 'easeinoutsine'], [S + 8, ap(releaseP, { head: [2, -6, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']);
			}
			return fr;
		});
	}
}

// ================================================================ ARSENAL
function arsenalClips(an, timing, poses) {
	const M = an.M, b = poses.stand;
	const GUNUP = { arm_r: [-88, 4, 4], forearm_r: [-2, 0, 0] };
	mk(an, timing, 'idle', len => [
		[0, ap(b, { chest: [-0.5, 0, 0] })],
		[len * 0.2, ap(b, { chest: [-2, 0, 0], head: [-3, -16, 0], cannon: [0, -14, 0] }), 'easeinoutsine'],
		[len * 0.35, ap(b, { chest: [-2, 0, 0], head: [-3, -16, 0], cannon: [-4, -18, 0], antenna: [0, 0, 4] }), 'easeinoutsine'],
		[len * 0.55, ap(b, { chest: [0, 4, 0], head: [0, 22, 0], cannon: [-2, 20, 0], hips: [0, 0, 1.5], thigh_r: [0, 0, -1.5], thigh_l: [0, 0, -1.5] }), 'easeinoutsine'],
		[len * 0.7, ap(b, { chest: [0, 4, 0], head: [2, 18, 0], cannon: [-6, 16, 0], barrels: [0, 60, 0] }), 'easeinoutsine'],
		[len * 0.9, ap(b, { chest: [-1, -1, 0], head: [0, -4, 0], cannon: [0, -4, 0], barrels: [0, 120, 0] }), 'easeinoutsine'],
		[len, ap(b, { chest: [-0.5, 0, 0], barrels: [0, 120, 0] }), 'easeinoutsine'],
	]);
	// idle variation: lifts the rotary gun to check it, spins it up, then cycles the shoulder cannon's breech
	mk(an, timing, 'fidget', len => {
		const look = ap(b, { arm_r: [-70, 30, -10], forearm_r: [-60, 0, 0], head: [16, -30, 0], chest: [4, -10, 0] });
		return [[0, b], [len * 0.2, look, 'easeinoutsine'], [len * 0.45, ap(look, { barrels: [0, 720, 0] }), 'easeinquad'], [len * 0.55, ap(look, { barrels: [0, 900, 0], forearm_r: [6, 0, 0] }), 'easeoutquad'],
			[len * 0.7, ap(b, { barrels: [0, 900, 0], cannon: [16, 0, 0], head: [0, -20, 0] }), 'easeinoutsine'], [len * 0.8, ap(b, { barrels: [0, 900, 0], cannon: [-10, 0, 0] }), 'easeoutback'], [len, ap(b, { barrels: [0, 900, 0] }), 'easeinoutsine']];
	});
	const walkAmp = { th: 20, kc: 4, kb: 18, fc: 4, fb: 8, lift: 26, yaw: 5, lean: 3, arm: 12, wArm: -1, fa: 4, rise: 0.6 };
	gait(an, timing, 'walk', b, walkAmp, 0.15, t => ({ chest: [0, 0, 2 * Math.sin(t * Math.PI * 2)], head: [0, 0, -2 * Math.sin(t * Math.PI * 2)], cannon: [2 * Math.sin(t * Math.PI * 4), 0, 0] }));
	gait(an, timing, 'run', ap(b, { chest: [12, 0, 0], waist: [6, 0, 0], head: [-14, 0, 0] }), { th: 34, kc: 8, kb: 30, fc: 4, fb: 10, lift: 42, yaw: 8, lean: 8, arm: 30, wArm: -1, fa: 20, rise: 1.4 }, 0.36, null, 1.0);
	mk(an, timing, 'air', len => {
		const p = ap(b, { arm_r: [-20, 0, 30], arm_l: [-20, 0, -30], forearm_r: [-20, 0, 0], forearm_l: [-30, 0, 0], thigh_r: [-30, 0, 4], shin_r: [40, 0, 0], thigh_l: [-10, 0, -4], shin_l: [30, 0, 0], head: [-6, 0, 0], $ground: false, $root: [0, 3, 0] });
		return [[0, p], [len / 2, ap(p, { arm_r: [-6, 0, 4], arm_l: [-6, 0, -4], thigh_r: [10, 0, 0] }), 'easeinoutsine'], [len, p, 'easeinoutsine']];
	}, { ground: false });
	// intro: powered down (slumped, cannon folded back) -> systems online: straightens joint by joint, the cannon swings over the
	// shoulder and locks, the gun spins up, fists out wide on the strike
	mk(an, timing, 'intro', (len, S) => {
		const off = ap(b, { chest: [30, 0, 0], waist: [10, 0, 0], head: [30, 0, 0], arm_r: [10, 0, -6], arm_l: [10, 0, 6], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0], thigh_r: [-20, 0, 0], shin_r: [26, 0, 0], thigh_l: [-20, 0, 0], shin_l: [26, 0, 0], cannon: [60, 0, 0], antenna: [30, 0, 0] });
		const up = ap(b, { chest: [-14, 0, 0], waist: [-4, 0, 0], head: [-14, 0, 0], arm_r: [-30, 0, 50], forearm_r: [-40, 0, 0], arm_l: [-30, 0, -50], forearm_l: [-40, 0, 0], ...FIST('l'), cannon: [-14, 0, 0], barrels: [0, 900, 0], thigh_r: [-6, 0, 10], thigh_l: [-6, 0, -10], foot_r: [0, 0, -8], foot_l: [0, 0, 8] });
		return [[0, off], [S * 0.3, ap(off, { head: [-20, 0, 0] }), 'easeoutback'], [S * 0.55, ap(off, { chest: [-20, 0, 0], waist: [-6, 0, 0], head: [-26, 0, 0], thigh_r: [16, 0, 0], shin_r: [-20, 0, 0], thigh_l: [16, 0, 0], shin_l: [-20, 0, 0] }), 'easeinoutsine'],
			[S * 0.8, ap(b, { cannon: [30, 0, 0], arm_r: [-10, 0, 20], arm_l: [-10, 0, -20], head: [-6, 0, 0] }), 'easeinoutsine'], [S, strike(up), 'easeoutback'],
			[S + 5, ap(up, { chest: [3, 0, 0], cannon: [6, 0, 0] }), 'easeoutquad'], [S + 14, ap(up, { barrels: [0, 360, 0] }), 'easeinoutsine'], [len, ap(b, { barrels: [0, 1260, 0] }), 'easeinoutsine']];
	});
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-12, 8, 6], head: [-14, -12, 8], arm_r: [-14, 0, 14], arm_l: [-10, 0, -16], antenna: [-20, 0, 0], cannon: [10, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// death: systems fail -- sparks (jerks), the cannon droops, knees lock and he topples backward like a felled tower
	mk(an, timing, 'death', len => {
		const jerk = ap(b, { chest: [-10, 10, 4], head: [20, -10, 0], arm_r: [-30, 0, 20], arm_l: [-20, 0, -30], cannon: [30, 0, 0] });
		const knees = ap(b, { thigh_r: [-50, 0, 4], shin_r: [70, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-46, 0, -4], shin_l: [70, 0, 0], foot_l: [-20, 0, 0], chest: [10, 0, 0], head: [30, 0, 0], arm_r: [10, 0, 4], arm_l: [10, 0, -4], cannon: [50, 0, 0], $ground: 'knees' });
		const fallen = ap(b, { root: [-84, 0, 0], chest: [4, 0, 0], head: [20, 20, 0], arm_r: [-20, 0, 40], arm_l: [-10, 0, -50], thigh_r: [-20, 0, 6], shin_r: [20, 0, 0], thigh_l: [-6, 0, -6], cannon: [60, 0, 0], antenna: [40, 0, 0], $ground: 'all' });
		const f = len / 36;
		return [[0, b], [3 * f, jerk, 'easeoutquad'], [6 * f, ap(jerk, { chest: [14, -16, -6], head: [-30, 20, 0] }), 'easeoutquad'], [9 * f, jerk, 'easeoutquad'], [17 * f, knees, 'easeinquad'],
			[22 * f, ap(knees, { chest: [-14, 0, 0], head: [-10, 0, 0] }), 'easeinoutsine'], [29 * f, fallen, 'easeinquad'], [32 * f, ap(fallen, { root: [6, 0, 0], $y: 0.8 }), 'easeoutquad'], [len, fallen, 'easeinquad']];
	});
	// melee: the heat-blade arm cuts across (opens on the strike)
	mk(an, timing, 'melee', () => {
		const hit = ap(b, { chest: [6, 30, 0], waist: [2, 14, 0], head: [-4, -24, 0], arm_l: [-90, 40, -20], forearm_l: [-6, 0, 0], arm_r: [-20, 0, 20], forearm_r: [-40, 0, 0], thigh_l: [-24, 0, -4], shin_l: [18, 0, 0], thigh_r: [12, 0, 4] });
		const thru = ap(hit, { chest: [4, 14, 0], arm_l: [10, 40, 10], forearm_l: [-16, 0, 0] });
		return [[0, strike(hit)], [3, thru, 'easeoutcubic'], [6, ap(thru, { chest: [-2, -4, 0] }), 'easeinoutsine'], [12, b, 'easeinoutsine']];
	});
	// melee_alt: a backhand bash with the rotary gun's housing
	mk(an, timing, 'melee_alt', () => {
		const hit = ap(b, { chest: [6, -30, 0], waist: [2, -14, 0], head: [-4, 24, 0], arm_r: [-90, -40, 20], forearm_r: [-6, 0, 0], arm_l: [-30, 0, -20], forearm_l: [-50, 0, 0], thigh_r: [-24, 0, 4], shin_r: [18, 0, 0], thigh_l: [12, 0, -4] });
		const thru = ap(hit, { chest: [4, -14, 0], arm_r: [10, -40, -10] });
		return [[0, strike(hit)], [3, thru, 'easeoutcubic'], [6, ap(thru, { chest: [-2, 4, 0] }), 'easeinoutsine'], [12, b, 'easeinoutsine']];
	});
	// repel: a two-fisted ground pound (the shockwave)
	mk(an, timing, 'repel', () => {
		const p = ap(b, { chest: [34, 0, 0], waist: [12, 0, 0], head: [-24, 0, 0], arm_r: [-56, 0, -6], forearm_r: [-10, 0, 0], arm_l: [-56, 0, 6], forearm_l: [-10, 0, 0], thigh_r: [-40, 0, 10], shin_r: [50, 0, 0], thigh_l: [-40, 0, -10], shin_l: [50, 0, 0], cannon: [10, 0, 0] });
		return [[0, strike(p)], [3, ap(p, { chest: [4, 0, 0] }), 'easeoutquad'], [8, ap(p, { chest: [-8, 0, 0] }), 'easeinoutsine'], [14, b, 'easeinoutsine']];
	});
	// burst: chest out, arms back -- the reactor vents a pulse
	mk(an, timing, 'burst', () => {
		const p = ap(b, { chest: [-20, 0, 0], waist: [-6, 0, 0], head: [-16, 0, 0], arm_r: [20, 0, 40], forearm_r: [-20, 0, 0], arm_l: [20, 0, -40], forearm_l: [-20, 0, 0], cannon: [-14, 0, 0], antenna: [-10, 0, 0] });
		return [[0, strike(p)], [4, ap(p, { chest: [-4, 0, 0] }), 'easeoutquad'], [9, ap(p, { chest: [3, 0, 0] }), 'easeinoutsine'], [16, b, 'easeinoutsine']];
	});
	// slam: both fists hammered down from overhead
	mk(an, timing, 'slam', () => {
		const p = ap(b, { chest: [40, 0, 0], waist: [14, 0, 0], head: [-30, 0, 0], arm_r: [-60, 0, -14], forearm_r: [-4, 0, 0], arm_l: [-60, 0, 14], forearm_l: [-4, 0, 0], thigh_r: [-36, 0, 8], shin_r: [44, 0, 0], thigh_l: [-30, 0, -8], shin_l: [40, 0, 0] });
		return [[0, strike(p)], [3, ap(p, { chest: [4, 0, 0], arm_r: [6, 0, 0], arm_l: [6, 0, 0] }), 'easeoutquad'], [8, ap(p, { chest: [-8, 0, 0] }), 'easeinoutsine'], [16, b, 'easeinoutsine']];
	});
	// shoot: the rotary gun up and firing -- barrels spinning, hard recoil, the shoulder cannon tracking
	mk(an, timing, 'shoot', () => {
		const p = ap(b, GUNUP, { chest: [4, 16, 0], waist: [0, 8, 0], head: [-2, -16, 0], arm_l: [-20, 0, -24], forearm_l: [-40, 0, 0], thigh_r: [-20, 0, 6], shin_r: [16, 0, 0], thigh_l: [14, 0, -6], foot_l: [-14, 0, 0], cannon: [-6, 0, 0] });
		return [[0, strike(ap(p, { barrels: [0, 0, 0] }))], [2, ap(p, { arm_r: [10, 0, 0], chest: [-4, 0, 0], barrels: [0, 240, 0] }), 'easeoutquad'], [4, ap(p, { barrels: [0, 480, 0] })], [6, ap(p, { arm_r: [8, 0, 0], barrels: [0, 720, 0] }), 'easeoutquad'], [8, ap(p, { barrels: [0, 900, 0] })], [12, ap(b, { barrels: [0, 1080, 0] }), 'easeinoutsine']];
	});
	// lunge: a thruster-boosted shoulder charge
	mk(an, timing, 'lunge', () => {
		const p = ap(b, { chest: [30, -20, 0], waist: [10, -6, 0], head: [-20, 16, 0], arm_r: [-10, 0, 30], forearm_r: [-60, 0, 0], arm_l: [-40, 0, -10], forearm_l: [-60, 0, 0], thigh_r: [-40, 0, 4], shin_r: [30, 0, 0], thigh_l: [30, 0, -4], shin_l: [20, 0, 0], $ground: 'all' });
		return [[0, strike(p)], [4, ap(p, { chest: [-6, 0, 0] }), 'easeoutquad'], [12, b, 'easeinoutsine']];
	});
	mk(an, timing, 'blink', () => {
		const p = ap(b, { chest: [20, 0, 0], head: [10, 0, 0], arm_r: [-20, 0, 30], arm_l: [-20, 0, -30], thigh_r: [-50, 0, 8], shin_r: [70, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-46, 0, -8], shin_l: [66, 0, 0], foot_l: [-20, 0, 0] });
		return [[0, strike(p)], [4, ap(p, { chest: [-8, 0, 0], head: [-6, 16, 0] }), 'easeoutquad'], [10, b, 'easeinoutsine']];
	});
	// casts: the targeting wind-up -- braced stance, gun raised, cannon lowered onto the target, barrels spinning faster
	// and faster -- then the release (recoil). cast_4: drops to one knee, braces with the fist, everything fires at once.
	for (const [n, big] of [['cast_1', 0], ['cast_2', 0], ['cast_3', 0], ['cast_4', 1]]) {
		mk(an, timing, n, (len, S) => {
			const brace = ap(b, GUNUP, { chest: [8, 14, 0], waist: [2, 8, 0], head: [2, -14, 0], arm_l: [-30, 0, -20], forearm_l: [-50, 0, 0], thigh_r: [-20, 0, 8], shin_r: [20, 0, 0], thigh_l: [20, 0, -8], shin_l: [10, 0, 0], foot_l: [-20, 0, 0], cannon: [6, 0, 0] });
			const kneel = ap(b, GUNUP, { thigh_r: [-70, 0, 6], shin_r: [70, 0, 0], foot_r: [0, 0, 0], thigh_l: [10, 0, -6], shin_l: [86, 0, 0], foot_l: [-86, 0, 0], chest: [10, 10, 0], head: [-4, -10, 0], arm_l: [-24, 0, -10], forearm_l: [-10, 0, 0], cannon: [4, 0, 0], $ground: 'knees' });
			const pose = big ? kneel : brace;
			const fire = ap(pose, { arm_r: [16, 0, 0], chest: [-8, 0, 0], cannon: [-14, 0, 0], head: [-4, 0, 0] });
			const spins = Math.max(2, Math.round(S / 4));
			const fr = [[0, b], [Math.max(2, S * 0.35), ap(pose, { barrels: [0, 0, 0] }), 'easeinoutsine']];
			for (let i = 1; i <= spins; i++) { const tk = S * 0.35 + (S * 0.65 - 1) * i / spins; if (tk < S - 0.5) fr.push([tk, ap(pose, { barrels: [0, 120 * i * i / spins, 0], chest: [i % 2 ? 0.8 : -0.8, 0, 0] }), 'linear']); }
			const r0 = 120 * spins;
			fr.push([S, strike(ap(fire, { barrels: [0, r0 + 120, 0] })), 'easeoutquad'], [S + 3, ap(pose, { barrels: [0, r0 + 400, 0] }), 'easeoutquad'], [S + 6, ap(fire, { barrels: [0, r0 + 700, 0] }), 'easeoutquad'], [S + 10, ap(pose, { barrels: [0, r0 + 1000, 0] }), 'easeinoutsine'], [len, ap(b, { barrels: [0, r0 + 1200, 0] }), 'easeinoutsine']);
			return fr;
		});
	}
}

// ================================================================ OMEGA MAGE (floating: every frame carries its own root height)
function mageClips(an, timing, poses) {
	const M = an.M;
	const H = 9; // hover height (model px)
	an.hover = H;
	const staff = (p, dir) => { L.aimBone(M, p, 'staff', [0, 1, 0], dir); return p; };
	const base = Object.assign({}, poses.stand); delete base.$root;
	const b = base;
	const orbit = (deg, p) => ap(p, { orbit: [0, deg, 0] });
	// idle: bobbing on the air, robes drifting, the rune tablets circling once, shards turning, the free hand weaving
	mk(an, timing, 'idle', len => {
		const fr = [];
		for (let i = 0; i <= 8; i++) {
			const t = i / 8;
			const p = ap(b, { $h: H + 1.6 * Math.sin(t * Math.PI * 2), orbit: [0, 360 * t, 0], shard_r: [8 * Math.sin(t * Math.PI * 2), 90 * t, 0], shard_l: [-8 * Math.sin(t * Math.PI * 2), -90 * t, 0],
				chest: [-1.5 * Math.sin(t * Math.PI * 2), 0, 0], head: [2 * Math.sin(t * Math.PI * 2 + 1), 12 * Math.sin(t * Math.PI * 2 * 0.5), 0], forearm_l: [-6 * Math.sin(t * Math.PI * 4), 0, 0], fingers_l: [0, 0, 10 * Math.sin(t * Math.PI * 4)] });
			fr.push([len * t, p, 'linear']);
		}
		return fr;
	});
	// idle variation: conjures an orb of Omega energy in the left hand, turns it over, crushes it out
	mk(an, timing, 'fidget', len => {
		const hold = ap(b, { arm_l: [-50, -20, -10], forearm_l: [-50, 0, 0], ...OPEN('l', 2.5), head: [16, -20, 0], chest: [4, -8, 0] });
		return [[0, b], [len * 0.2, hold, 'easeinoutsine'], [len * 0.45, ap(hold, { hand_l: [0, 120, 0], head: [12, -26, 0] }), 'easeinoutsine'], [len * 0.65, ap(hold, { hand_l: [0, 240, 0] }), 'easeinoutsine'],
			[len * 0.75, ap(hold, { ...FIST('l') }), 'easeinquad'], [len, b, 'easeinoutsine']].map(([t, p, e]) => [t, ap(p, { orbit: [0, 360 * t / len, 0] }), e]);
	}, { scaleKeys: { spell_l: [[0, 0], [Math.round(0.12 * 60), 0], [Math.round(0.25 * 60), 1, 'easeoutback'], [Math.round(0.72 * 60), 1], [Math.round(0.78 * 60), 0, 'easeinquad']] } });
	// walk / run: no legs -- he glides, leaning into it, the robe and cape trailing
	mk(an, timing, 'walk', len => [0, 0.5, 1].map(t => [len * t, ap(b, { $h: H + 1.2 * Math.cos(t * Math.PI * 2), chest: [8, 0, 0], waist: [4, 0, 0], head: [-8, 0, 0], skirt: [-6, 0, 0], orbit: [0, 360 * t, 0], arm_l: [10, 0, -16] }), 'easeinoutsine']), { speed: 0.15 });
	mk(an, timing, 'run', len => [0, 0.5, 1].map(t => [len * t, ap(b, { $h: H + 2 + 1.2 * Math.cos(t * Math.PI * 2), chest: [20, 0, 0], waist: [8, 0, 0], head: [-18, 0, 0], skirt: [-14, 0, 0], arm_l: [30, 0, -30], forearm_l: [-10, 0, 0], orbit: [0, 360 * t, 0] }), 'easeinoutsine']), { speed: 0.36 });
	mk(an, timing, 'air', len => [0, 0.5, 1].map(t => [len * t, ap(b, { $h: H + 3 + 2 * Math.sin(t * Math.PI * 2), arm_l: [-30, 0, -50], forearm_l: [-10, 0, 0], ...OPEN('l', 3), chest: [-6, 0, 0], skirt: [6, 0, 0], orbit: [0, 360 * t, 0] }), 'easeinoutsine']));
	// intro: descends out of the sky, robes billowing, then spreads his arms and raises the Omega Crystal high (strike)
	mk(an, timing, 'intro', (len, S) => {
		const desc = ap(b, { $h: H + 40, arm_r: [-10, 0, 30], forearm_r: [-10, 0, 0], arm_l: [-10, 0, -30], forearm_l: [-10, 0, 0], head: [20, 0, 0], chest: [10, 0, 0], skirt: [10, 0, 0] });
		const reveal = staff(ap(b, { $h: H + 4, arm_r: [-160, 0, 16], forearm_r: [-6, 0, 0], arm_l: [-40, 0, -70], forearm_l: [-6, 0, 0], ...OPEN('l', 3), chest: [-14, 0, 0], waist: [-4, 0, 0], head: [-20, 0, 0], collar: [-6, 0, 0], orbit: [0, 540, 0] }), [0.1, 1, -0.1]);
		return [[0, desc], [S * 0.6, ap(desc, { $h: H + 6, head: [0, 0, 0], orbit: [0, 180, 0] }), 'easeoutquad'], [S * 0.85, ap(b, { $h: H, chest: [8, 0, 0], head: [10, 0, 0], arm_r: [-20, 0, 0], orbit: [0, 360, 0] }), 'easeinoutsine'],
			[S, strike(reveal), 'easeoutback'], [S + 8, ap(reveal, { $h: H + 5, orbit: [0, 720, 0] }), 'easeinoutsine'], [S + 18, ap(reveal, { $h: H + 4, orbit: [0, 900, 0] }), 'easeinoutsine'], [len, orbit(1080, b), 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { $h: H + 2, $z: 2, chest: [-14, 8, 6], head: [-14, -12, 8], arm_l: [-20, 0, -30], skirt: [14, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// death: the crystal's light gutters -- he convulses in the air, drops, the staff slips from his hand, he sinks into a
	// heap of robes on the ground
	mk(an, timing, 'death', len => {
		const conv = ap(b, { $h: H + 4, chest: [-24, 0, 0], head: [-30, 0, 0], arm_r: [-60, 0, 30], arm_l: [-60, 0, -40], ...OPEN('r', 2), ...OPEN('l', 3) });
		const drop = ap(b, { $h: 0, chest: [20, 0, 0], waist: [10, 0, 0], head: [30, 0, 0], arm_r: [10, 0, 10], arm_l: [10, 0, -10], ...OPEN('r', 2), staff: [70, 0, -20], skirt: [-6, 0, 0] });
		const heap = ap(b, { $h: -10, chest: [50, 0, 0], waist: [30, 0, 0], head: [40, 0, 0], arm_r: [30, 0, 30], arm_l: [30, 0, -30], ...OPEN('r', 2), staff: [86, 0, -30], skirt: [0, 0, 0], collar: [20, 0, 0] });
		const f = len / 36;
		return [[0, b], [5 * f, conv, 'easeoutquad'], [9 * f, ap(conv, { chest: [10, 10, 0], head: [20, -10, 0] }), 'easeinoutsine'], [13 * f, conv, 'easeinoutsine'], [20 * f, drop, 'easeinquad'], [23 * f, ap(drop, { $h: 1 }), 'easeoutquad'], [32 * f, heap, 'easeinoutsine'], [len, heap]];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// melee: a sweeping blow with the crystal-headed staff
	mk(an, timing, 'melee', () => {
		const hit = staff(ap(b, { chest: [6, -30, 0], waist: [2, -14, 0], head: [-6, 20, 0], arm_r: [-90, -40, 20], forearm_r: [-10, 0, 0], arm_l: [-30, 0, -30], skirt: [0, -10, 0], $z: -2 }), [0.7, 0.1, -1]);
		const thru = staff(ap(hit, { chest: [0, -14, 0], arm_r: [10, -30, 0] }), [1, -0.1, -0.2]);
		return [[0, strike(hit)], [3, thru, 'easeoutcubic'], [6, ap(thru, { chest: [0, 4, 0] }), 'easeinoutsine'], [12, b, 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// melee_alt: a palm thrust of raw force from the left hand
	mk(an, timing, 'melee_alt', () => {
		const hit = ap(b, { chest: [8, 24, 0], waist: [2, 10, 0], head: [-4, -16, 0], arm_l: [-92, 10, -6], forearm_l: [0, 0, 0], hand_l: [-40, 0, 0], ...OPEN('l', 3), $z: -3 });
		return [[0, strike(hit)], [4, ap(hit, { arm_l: [8, 0, 0], chest: [-4, 0, 0] }), 'easeoutquad'], [12, b, 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 1], [5, 0, 'easeinquad']] } });
	// repel: drives the staff's foot down -- a ring of force
	mk(an, timing, 'repel', () => {
		const p = staff(ap(b, { $h: H - 3, chest: [20, 0, 0], waist: [8, 0, 0], head: [10, 0, 0], arm_r: [-50, 0, 10], forearm_r: [-40, 0, 0], arm_l: [-40, 0, -50], forearm_l: [-10, 0, 0], ...OPEN('l', 3), orbit: [0, 120, 0], skirt: [10, 0, 0] }), [0, 1, 0.1]);
		return [[0, strike(p)], [3, ap(p, { $h: H - 2, orbit: [0, 200, 0] }), 'easeoutquad'], [8, ap(p, { $h: H + 2, chest: [-8, 0, 0], orbit: [0, 300, 0] }), 'easeinoutsine'], [14, orbit(360, b), 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// burst: arms flung up and out, the tablets whipping round
	mk(an, timing, 'burst', () => {
		const p = staff(ap(b, { $h: H + 4, chest: [-16, 0, 0], head: [-20, 0, 0], arm_r: [-130, 0, 40], arm_l: [-130, 0, -40], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], ...OPEN('l', 3), orbit: [0, 180, 0], collar: [-6, 0, 0] }), [0.4, 1, 0]);
		return [[0, strike(p)], [4, ap(p, { orbit: [0, 300, 0] }), 'easeoutquad'], [10, ap(p, { $h: H + 3, orbit: [0, 360, 0], chest: [4, 0, 0] }), 'easeinoutsine'], [16, orbit(360, b), 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// slam: the staff brought down overhead like a hammer of light
	mk(an, timing, 'slam', () => {
		const p = staff(ap(b, { $h: H - 2, chest: [30, 0, 0], waist: [10, 0, 0], head: [-10, 0, 0], arm_r: [-70, 0, 4], forearm_r: [-10, 0, 0], arm_l: [-70, 0, -4], forearm_l: [-10, 0, 0], skirt: [10, 0, 0] }), [0, -0.4, -1]);
		return [[0, strike(p)], [3, ap(p, { chest: [4, 0, 0] }), 'easeoutquad'], [8, ap(p, { $h: H, chest: [-8, 0, 0] }), 'easeinoutsine'], [16, b, 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// shoot: the free hand hurls a bolt; the crystal staff braced behind
	mk(an, timing, 'shoot', () => {
		const p = ap(b, { chest: [6, 26, 0], waist: [2, 12, 0], head: [-4, -20, 0], arm_l: [-94, 16, -4], forearm_l: [0, 0, 0], hand_l: [-20, 0, 0], ...OPEN('l', 3), arm_r: [-20, 0, 20], $z: -1 });
		return [[0, strike(p)], [3, ap(p, { arm_l: [14, 0, 0], chest: [-4, -4, 0] }), 'easeoutquad'], [8, ap(p, { arm_l: [6, 0, 0] }), 'easeinoutsine'], [12, b, 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 1], [3, 0, 'easeinquad']] } });
	// lunge: a surge forward through the air, robes streaming
	mk(an, timing, 'lunge', () => {
		const p = staff(ap(b, { $h: H + 2, chest: [30, 0, 0], waist: [10, 0, 0], head: [-26, 0, 0], arm_r: [-100, 0, 10], arm_l: [20, 0, -30], skirt: [-20, 0, 0], robe_f: [-20, 0, 0], cape: [30, 0, 0] }), [0, 0.2, -1]);
		return [[0, strike(p)], [4, ap(p, { chest: [-8, 0, 0] }), 'easeoutquad'], [12, b, 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// blink: reforms out of folded arms, robes snapping open
	mk(an, timing, 'blink', () => {
		const p = ap(b, { $h: H + 3, arm_l: [-70, -50, 30], forearm_l: [-60, 0, 0], chest: [10, 0, 0], head: [16, 0, 0], skirt: [6, 0, 0], collar: [6, 0, 0] });
		return [[0, strike(p)], [4, ap(b, { arm_l: [-30, 0, -50], ...OPEN('l', 3), chest: [-8, 0, 0], head: [-10, 0, 0] }), 'easeoutback'], [10, b, 'easeinoutsine']];
	}, { scaleKeys: { spell_l: [[0, 0]] } });
	// casts: rises, raises the Omega Crystal while the free hand draws the spell (the orb grows), the tablets spin faster;
	// release = the staff thrust forward. cast_4: rises high, both arms up, the orb huge, then the staff brought down.
	for (const [n, big] of [['cast_1', 0], ['cast_2', 0], ['cast_3', 0.5], ['cast_4', 1]]) {
		const t = timing[n];
		const S = t.hit - T, len = t.length - T;
		mk(an, timing, n, () => {
			const lift = big ? 10 : big === 0.5 ? 6 : 4;
			const draw = staff(ap(b, { $h: H + lift, arm_r: [-150, 0, 20], forearm_r: [-6, 0, 0], arm_l: [-70, -30, -30], forearm_l: [-30, 0, 0], ...OPEN('l', 3), chest: [-10, 0, 0], head: [-14, 0, 0], skirt: [6, 0, 0], collar: [-4, 0, 0] }), [0.15, 1, 0]);
			const thrust = staff(ap(b, { $h: H + lift * 0.6, arm_r: [-96, -6, 6], forearm_r: [0, 0, 0], arm_l: [-90, 20, -10], forearm_l: [0, 0, 0], ...OPEN('l', 3), chest: [14, 0, 0], waist: [4, 0, 0], head: [-12, 0, 0], skirt: [-10, 0, 0], robe_f: [-14, 0, 0], cape: [20, 0, 0], $z: -2 }), [0, 0.7, -1]);
			const down = staff(ap(b, { $h: H, arm_r: [-70, 0, 4], arm_l: [-70, 0, -4], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], ...OPEN('l', 3), chest: [30, 0, 0], waist: [10, 0, 0], head: [-10, 0, 0], skirt: [12, 0, 0] }), [0, -0.3, -1]);
			const rel = big === 1 ? down : thrust;
			const turns = Math.round(1 + S / 10);
			const fr = [[0, b], [S * 0.4, orbit(180 * turns * 0.3, draw), 'easeinoutsine'], [S * 0.7, orbit(180 * turns * 0.7, ap(draw, { $h: H + lift + 1, chest: [-4, 0, 0] })), 'easeinoutsine'],
				[S - 1, orbit(180 * turns, ap(draw, { $h: H + lift + 2, arm_r: [-8, 0, 0] })), 'easeinoutsine'], [S, strike(orbit(180 * turns + 60, rel)), 'easeinquad'],
				[S + 4, orbit(180 * turns + 180, ap(rel, { chest: [-6, 0, 0] })), 'easeoutquad'], [len, orbit(180 * turns + 360, b), 'easeinoutsine']];
			return fr;
		}, { scaleKeys: { spell_l: [[0, 0], [Math.round(S * 0.25), 0.3], [Math.max(Math.round(S * 0.25) + 1, S - 1), big === 1 ? 2.2 : 1.3, 'easeinquad'], [S, 0.2, 'easeinquad'], [S + 2, 0]] } });
	}
}

// ================================================================ game export
const GLOW_MATS = new Set(['core', 'core_hot', 'reactor', 'reactor_hot', 'visor', 'vent', 'glow_red', 'crystal', 'crystal_hot', 'blade']);
const sat = (c, k) => { const l = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]; return c.map(v => L.clamp(l + (v - l) * k)); };
const contrast = (c, k, mid = 118) => c.map(v => L.clamp(mid + (v - mid) * k));
function addHalos(M, eyes) {
	for (const c of M.cubes().slice()) {
		if (!GLOW_MATS.has(c.mat) || c.mat === 'blade') continue;
		const sz = [0, 1, 2].map(i => c.to[i] - c.from[i]);
		const h = Math.max(0.8, Math.min(2.6, Math.min(...sz) * 0.8 + 0.5));
		const hc = M.box(M.B[c.bone], c.from[0] - h, c.from[1] - h, c.from[2] - h, c.to[0] + h, c.to[1] + h, c.to[2] + h, 'halo', { rot: c.rot, pivot: c.pivot, tag: c.mat });
		hc.haloOf = c.mat;
	}
	for (const e of eyes) M.box(e.bone, e.c[0] - e.s[0] / 2, e.c[1] - e.s[1] / 2, e.c[2] - e.s[2] / 2, e.c[0] + e.s[0] / 2, e.c[1] + e.s[1] / 2, e.c[2] + e.s[2] / 2, 'halo', { tag: 'eye', rot: e.rot, pivot: e.pivot });
}
// GeckoLib's AutoGlowingTexture uses the glowmask only as a MASK: the emissive COLOUR is the base texture's, so glowing texels
// are painted bright in the base texture; the glowmask carries "where" (+ alpha). Halo shells: transparent in the base
// texture, a soft radial colour in the glowmask RGB that FrostHaloLayer's additive pass turns into a bloom.
function gameShade(M, haloCol) {
	const base = M.shadeFn;
	const W = [255, 255, 255];
	return (c, f, s, t, p, info) => {
		if (c.mat === 'halo') {
			const r = Math.max(Math.abs(s - 0.5), Math.abs(t - 0.5)) * 2;
			const fall = Math.pow(Math.max(0, 1 - r), 1.3);
			const col = haloCol(c.tag);
			const k = c.tag === 'blade' ? 0.5 : 1;
			return [col.map(v => v * fall * k), 255, W, Math.max(1, Math.round(70 * fall * k))];
		}
		let [col, a, g] = base(c, f, s, t, p, info);
		let ga = null;
		if (a === 0) return [col, 0, null];
		if (g) {
			const gl = 0.3 * g[0] + 0.59 * g[1] + 0.11 * g[2];
			if (GLOW_MATS.has(c.mat) && c.mat !== 'blade') { col = L.mix(col, L.mul(g, 1.0), 0.25); ga = 255; } // the whole body glows
			else { col = L.mix(col, g, 0.65); col = L.mul(col, 1.08); ga = Math.round(L.clamp(120 + gl * 0.6, 0, 255)); }
			g = W;
		} else {
			col = contrast(sat(col, 1.12), 1.06);
			col = L.mul(col, 1.05);
		}
		return [col, a, g, ga];
	};
}
const HALO = {
	chimera: tag => tag === 'eye' ? [255, 190, 50] : tag === 'core_hot' ? [255, 170, 130] : [255, 50, 36],
	arsenal: tag => tag === 'glow_red' ? [255, 50, 30] : tag === 'reactor_hot' ? [255, 220, 150] : [255, 150, 40],
	omega_mage: tag => tag === 'crystal_hot' ? [230, 180, 255] : [180, 70, 255],
};
function eyesFor(id, M) {
	const A = M.A;
	if (id === 'chimera') return [-1, 1].map(s => ({ bone: 'head', c: [s * A.eyeX, A.eyeY, A.faceZ - 0.2], s: [2.6, 1.6, 0.2] }));
	if (id === 'arsenal') return [{ bone: 'head', c: [0, 61.9, -7.3], s: [9.4, 2.6, 0.2] }];
	return [-1, 1].map(s => ({ bone: 'head', c: [s * A.eyeX, A.eyeY, -4.95], s: [2.6, 1.4, 0.2] }));
}
const DANGLE = {
	chimera: [
		{ bone: 'tail1', dir: [0, -0.6, 1], period: 18, damp: 0.25, limit: 22, drag: 2, stiff: 0.6 }, { bone: 'tail2', dir: [0, -0.8, 0.7], period: 16, damp: 0.24, limit: 24, drag: 2 },
		{ bone: 'tail3', dir: [-0.2, -0.9, 0.5], period: 14, damp: 0.22, limit: 26, drag: 2 }, { bone: 'tail4', dir: [-0.5, -0.8, 0.6], period: 13, damp: 0.22, limit: 28 },
		{ bone: 'tail5', dir: [-0.6, -0.4, 0.8], period: 12, damp: 0.2, limit: 30 }, { bone: 'tail6', dir: [-0.3, 0.4, 0.9], period: 11, damp: 0.2, limit: 30 },
		{ bone: 'mane', dir: [0, 1, 0.3], period: 12, stiff: 0.25, limit: 10, acc: 0.8 }, { bone: 'flap_f', period: 12, limit: 30 }, { bone: 'flap_b', period: 12, limit: 30 },
		{ bone: 'chain_r', period: 9, damp: 0.18, limit: 40 }, { bone: 'ruff', dir: [0, 1, 0.3], period: 10, stiff: 0.25, limit: 8, acc: 0.7 },
	],
	arsenal: [{ bone: 'antenna', dir: [0, 1, 0], period: 6, stiff: 0.3, limit: 25, damp: 0.15 }],
	omega_mage: [{ bone: 'robe_f', period: 20, stiff: 0.5, limit: 28, drag: 3 }, { bone: 'robe_b', period: 22, stiff: 0.5, limit: 30, drag: 3 },
		{ bone: 'cape', period: 24, damp: 0.28, limit: 35, drag: 4 }, { bone: 'skirt', period: 26, stiff: 0.6, limit: 10, acc: 0.6 }],
};
const CLIPS = { chimera: chimeraClips, arsenal: arsenalClips, omega_mage: mageClips };
const HIDDEN = { omega_mage: ['spell_l'] };

function writeGame(M, id, an) {
	const RES = path.join(REPO, 'src/main/resources/assets/projecthero');
	const geoDir = path.join(RES, 'geo', 'supervillain'), texDir = path.join(RES, 'textures/entity/supervillain'), animDir = path.join(RES, 'animations', 'supervillain');
	for (const d of [geoDir, texDir, animDir]) fs.mkdirSync(d, { recursive: true });
	fs.writeFileSync(path.join(geoDir, `${id}.geo.json`), JSON.stringify(M.geoJson(`geometry.${id}`), null, '\t') + '\n');
	fs.writeFileSync(path.join(texDir, `${id}.png`), L.encodePng(M.TW, M.TH, M.tex));
	fs.writeFileSync(path.join(texDir, `${id}_glowmask.png`), L.encodePng(M.TW, M.TH, M.glow));
	fs.writeFileSync(path.join(animDir, `${id}.animation.json`), JSON.stringify(an.build(), null, '\t') + '\n');
	return { geo: path.join(geoDir, `${id}.geo.json`), tex: path.join(texDir, `${id}.png`), glow: path.join(texDir, `${id}_glowmask.png`), anim: path.join(animDir, `${id}.animation.json`) };
}

function gameExport() {
	const timing = parseTiming();
	const out = { transition: T, source: 'src/main/java/com/projecthero/mod/event/entity/SupervillainAnims.java', note: 'length/hit = server ticks; a play-once/held clip is (length - 4) ticks long and keys its strike on (hit - 4); loops are length ticks.', models: {} };
	const previews = [];
	for (const id of Object.keys(G.BUILD)) {
		const M = G.prepare(id, ...G.BUILD[id], false);
		addHalos(M, eyesFor(id, M));
		M.layout(); M.paint(gameShade(M, HALO[id]));
		const an = new A.AnimSet(M, id, { dangles: DANGLE[id], hidden: HIDDEN[id] || [] });
		CLIPS[id](an, timing, M.poses);
		for (const n of Object.keys(timing)) if (!an.clips[n]) throw new Error(`${id}: clip ${n} from SupervillainAnims was not authored`);
		const files = writeGame(M, id, an);
		out.models[id] = { texture: [M.TW, M.TH], bones: M.bones.length, cubes: M.cubes().length, rest_fit_height_px: +G.fitHeightPx(M).toFixed(2), height_blocks: +G.VISUAL[id].toFixed(3), clips: {} };
		for (const [n, t] of Object.entries(timing)) out.models[id].clips[n] = { server_length: t.length, hit: t.hit, kind: t.kind, clip_ticks: an.clips[n].len, strike_clip_tick: t.hit >= T ? t.hit - T : null };
		previews.push({ id, files, k: M.k, clips: an.clips, height: G.VISUAL[id] });
		console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ${Object.keys(an.clips).length} clips`);
	}
	fs.writeFileSync(path.join(REPO, 'scratchpad/supervillain_anim_timing.json'), JSON.stringify(out, null, '\t') + '\n');
	console.log('wrote scratchpad/supervillain_anim_timing.json');
	if (!process.argv.includes('--no-previews')) for (const p of previews) gamePreview(p, timing);
}

// preview sheets rendered from the WRITTEN game files
function gamePreview(p, timing) {
	const Gm = A.loadGameModel(p.files.geo, p.files.tex, p.files.glow, p.k * 16, true);
	Gm.heightBlocks = p.height;
	const anims = A.loadAnimations(p.files.anim);
	const frames = [];
	const add = (label, n, t) => frames.push([label, n, Math.max(0, Math.min(p.clips[n].len, Math.round(t)))]);
	add('IDLE', 'idle', 0); add('IDLE (LOOK)', 'idle', p.clips.idle.len * 0.45); add('FIDGET', 'fidget', p.clips.fidget.len * 0.6);
	add('WALK', 'walk', 0); add('RUN', 'run', 0); add('AIR', 'air', 0);
	for (const [n, t] of Object.entries(timing)) {
		if (['idle', 'walk', 'run', 'air', 'fidget'].includes(n)) continue;
		if (t.hit >= T) { if (t.hit - T > 3) add(`${n.toUpperCase().replace(/_/g, ' ')} WIND-UP`, n, (t.hit - T) * 0.7); add(`${n.toUpperCase().replace(/_/g, ' ')} AT HIT ${t.hit}`, n, t.hit - T); }
		else add(`${n.toUpperCase()} ${t.kind === 'hold' ? '(END)' : '(MID)'}`, n, t.kind === 'hold' ? p.clips[n].len : p.clips[n].len * 0.25);
	}
	const dir = path.join(G.SHOTS_DIR, 'anim');
	fs.mkdirSync(dir, { recursive: true });
	const per = 6;
	for (let i = 0; i * per < frames.length; i++) {
		const chunk = frames.slice(i * per, i * per + per);
		const panels = chunk.map(([label, n, t]) => {
			const pose = A.poseAt(anims, `animation.${p.id}.${n}`, t);
			const hidden = new Set(pose.$hidden || []);
			for (const bn of Gm.bones) if (bn.parent && hidden.has(bn.parent)) hidden.add(bn.name);
			return { label, items: [{ model: Gm, pose, root: pose.$root, hide: c => hidden.has(c.bone), floorR: p.height * 0.45 + 0.8 }], cam: { yaw: 28, pitch: 8 } };
		});
		L.sheet(path.join(dir, `${p.id}_${i + 1}.png`), `${p.id.toUpperCase().replace(/_/g, ' ')} - GAME ASSETS`, `PAGE ${i + 1} - POSES READ BACK FROM THE ANIMATION JSON`, panels, { minW: 1800, panelH: 900, maxW: 3400, ss: 1 });
	}
}

module.exports = { gameExport, parseTiming };
