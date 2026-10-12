// v0.16.1 Frost Giant Assault: every GeckoLib clip of the real game models (used by gen_frost_giant_models.js --game).
// Clip lengths and strike ticks come from FrostGiantAnims.java (parsed by the generator, passed in as `timing`):
// a play-once / held clip is (length - TRANSITION) ticks long, its strike keyframe sits on (hit - TRANSITION), and
// loops are `length` ticks. `neck` and `flinch` are code-driven and never keyed (sanitize() folds them away).
// Poses are rotation offsets in degrees on top of the rest pose (X > 0 tips a bone's top forward, Z > 0 swings a
// hanging limb toward the model's right / -X). Dangle bones (cloth, fur, hair, tails, ears) get spring-simulated
// follow-through from lib_geckogen_anim.js on top of what is authored here.
'use strict';
const L = require('./lib_concept_models.js');
const { AnimSet, addPose: ap, mirrorPose: mirP, scalePose } = require('./lib_geckogen_anim.js');

const T = 4; // FrostGiantAnims.TRANSITION
const FIST = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 95], [`fingertips_${n}`]: [0, 0, s * 100] }; };
const OPEN = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 14 * k], [`fingertips_${n}`]: [0, 0, s * 18 * k] }; };
const CLAW = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 40], [`fingertips_${n}`]: [0, 0, s * 55] }; };

// fold the code-driven bones into their neighbours so a pose can never key them
function sanitize(p) {
	const o = Object.assign({}, p);
	if (o.neck) { o.head = ap({ head: o.head || [0, 0, 0] }, { head: o.neck }).head; delete o.neck; }
	if (o.flinch) { o.chest = ap({ chest: o.chest || [0, 0, 0] }, { chest: o.flinch }).chest; delete o.flinch; }
	return o;
}

// One timing-checked clip: `L` = server length, `hit` = server strike tick (or -1). Frames use CLIP ticks; the
// helper `S` (strike clip tick) is passed to the frame builder.
function mk(an, timing, name, build, opts = {}) {
	const t = timing[name];
	if (!t) throw new Error(`${an.id}: ${name} is not in FrostGiantAnims`);
	const len = t.kind === 'loop' ? t.length : Math.max(1, t.length - T);
	const S = t.hit >= T ? t.hit - T : -1;
	const keep = p => { const o = {}; for (const k of Object.keys(p)) if (k.startsWith('$') || an.M.B[k]) o[k] = p[k]; return o; }; // shared poses may name gear bones a unit lacks
	const frames = build(len, S).map(([tk, p, e]) => [Math.round(tk), keep(sanitize(p)), e]);
	an.clip(name, len, t.kind === 'loop' ? true : t.kind === 'hold' ? 'hold' : false, frames,
		Object.assign({}, opts, { strikes: t.hit >= T ? [t.hit] : [] }));
	an.timing[name] = Object.assign({}, an.timing[name] || {});
}
const strike = p => Object.assign({}, p, { $strike: true });

// ================================================================ the shared Jotun clips
// cfg: { base (stand pose), weapon: 'r'|'l'|null, walkSpeed, runSpeed, amp: {...}, heavy (0..1), roarArms }
function jotunGaitPose(M, base, a, k) {
	const th = a.th * k, lift = a.lift * k;
	const contact = {
		thigh_r: [-th, 0, 0], shin_r: [a.kc, 0, 0], foot_r: [th - a.kc + 4, 0, 0],
		thigh_l: [th * 0.8, 0, 0], shin_l: [a.kb, 0, 0], foot_l: [-th * 0.8 - a.kb + 8, 0, 0],
		hips: [0, -a.yaw, 2], waist: [a.lean, a.yaw * 0.5, 0], chest: [a.lean * 0.6, a.yaw * 0.8, -1.5], head: [-a.lean, -a.yaw * 1.1, 1],
		arm_l: [-a.arm, 0, 0], forearm_l: [-a.fa, 0, 0], arm_r: [a.arm * a.wArm, 0, 0], forearm_r: [-a.fa * a.wArm, 0, 0],
		flap_f: [-5, 0, 0], flap_b: [4, 0, 0],
	};
	const down = { thigh_r: [-th + 5, 0, 0], shin_r: [a.kc + 12, 0, 0], foot_r: [th - a.kc - 10, 0, 0], chest: [a.lean * 0.6 + 3, 0, 0], head: [-3, 0, 0] };
	const passing = {
		thigh_r: [2, 0, 0], shin_r: [3, 0, 0], foot_r: [-4, 0, 0],
		thigh_l: [-lift, 0, 0], shin_l: [lift * 1.7, 0, 0], foot_l: [-lift * 0.5, 0, 0],
		hips: [0, 0, -1], waist: [a.lean, 0, 0], chest: [a.lean * 0.6 - 1, 0, 0], head: [-a.lean + 1, 0, 0],
	};
	return { contact, down, passing };
}
function stepOf(M, pose) {
	const W = M.world(pose);
	const pr = L.affApply(W.foot_r, [-M.A.legX, 0.3, -3]), pl = L.affApply(W.foot_l, [M.A.legX, 0.3, -3]);
	return Math.abs(pr[2] - pl[2]) * M.k;
}
function jotunGait(an, timing, name, cfg, a, speed) {
	const M = an.M, base = cfg.base;
	const cycle = timing[name].length;
	const want = speed * cycle / 2; // blocks per step so the planted foot doesn't skate
	let lo = 0.15, hi = 3;
	for (let i = 0; i < 30; i++) {
		const k = (lo + hi) / 2;
		const g = jotunGaitPose(M, base, a, k);
		if (stepOf(M, ap(base, g.contact)) < want) lo = k; else hi = k;
	}
	const g = jotunGaitPose(M, base, a, (lo + hi) / 2);
	const q = cycle / 4;
	const C = ap(base, g.contact), D = ap(base, g.contact, g.down), Pp = ap(base, g.passing, { $y: a.rise });
	const mC = ap(base, mirP(g.contact)), mD = ap(base, mirP(g.contact), mirP(g.down)), mP = ap(base, mirP(g.passing), { $y: a.rise });
	mk(an, timing, name, () => [
		[0, C], [q * 0.45, D, 'easeoutquad'], [q, Pp, 'easeinoutsine'],
		[2 * q, mC, 'easeinoutsine'], [2 * q + q * 0.45, mD, 'easeoutquad'], [3 * q, mP, 'easeinoutsine'], [4 * q, C, 'easeinoutsine'],
	], { speed });
	an.timing[name].stride_blocks = +(want * 2).toFixed(2);
}
function jotunCommon(an, timing, cfg) {
	const M = an.M, b = cfg.base, w = cfg.weapon;
	const other = w === 'r' ? 'l' : 'r';
	const noArms = p => { const o = Object.assign({}, p); for (const k of Object.keys(o)) if (/^(arm|forearm|hand|fingers|fingertips|weapon)_/.test(k)) delete o[k]; return o; };
	const post = p => (cfg.post ? cfg.post(p) : p);
	const aimW = (p, dir) => { if (w && M.B['weapon_' + w].cubes.length) L.aimBone(M, p, 'weapon_' + w, cfg.weaponAxis || [0, 0, -1], dir); return p; };
	// idle: breathing, a weight shift, a look around, a weapon shift, frost breath on the exhale
	mk(an, timing, 'idle', len => {
		const q = len / 4;
		return [
			[0, ap(b, { chest: [-1.5, 0, 0], head: [1, 0, 0] })],
			[q, ap(b, { chest: [-3.5, 0, 0], waist: [-1, 0, 0], arm_r: [0, 0, 2], arm_l: [0, 0, -2], head: [-2, 0, 0], jaw: [2, 0, 0] }), 'easeinoutsine'],
			[q * 1.6, ap(b, { chest: [1.5, -3, 0], hips: [0, 0, 2.5], thigh_r: [0, 0, -2.5], thigh_l: [0, 0, -2.5], head: [1, 20, -2], jaw: [8, 0, 0] }), 'easeinoutsine'],
			[q * 2.4, ap(b, { chest: [-3, -2, 0], hips: [0, 0, 2.5], thigh_r: [0, 0, -2.5], thigh_l: [0, 0, -2.5], head: [-2, 14, -1], [`forearm_${w || 'r'}`]: [-6, 0, 0] }), 'easeinoutsine'],
			[q * 3.2, ap(b, { chest: [1, 2, 0], hips: [0, 0, -1.5], thigh_r: [0, 0, 1.5], thigh_l: [0, 0, 1.5], head: [2, -10, 1], jaw: [6, 0, 0] }), 'easeinoutsine'],
			[len, ap(b, { chest: [-1.5, 0, 0], head: [1, 0, 0] }), 'easeinoutsine'],
		];
	});
	jotunGait(an, timing, 'walk', cfg, cfg.walkAmp, cfg.walkSpeed);
	jotunGait(an, timing, 'run', cfg, cfg.runAmp, cfg.runSpeed);
	// war cry: gather, then chest out, head thrown back, arms flung wide, a shaking roar
	const roarArms = cfg.roarArms || {
		[`arm_${other}`]: [-30, 0, other === 'r' ? 70 : -70], [`forearm_${other}`]: [-40, 0, 0], ...CLAW(other),
		...(w ? { [`arm_${w}`]: [-150, 0, w === 'r' ? 20 : -20], [`forearm_${w}`]: [-20, 0, 0], ...FIST(w) } : {}),
	};
	mk(an, timing, 'war_cry', (len, S) => {
		const gather = ap(b, { chest: [14, 0, 0], waist: [6, 0, 0], head: [16, 0, 0], arm_r: [10, 0, -8], arm_l: [10, 0, 8], thigh_r: [-12, 0, 6], shin_r: [16, 0, 0], foot_r: [-4, 0, 0], thigh_l: [8, 0, -6], shin_l: [8, 0, 0] });
		const roar = post(aimW(ap(noArms(b), roarArms, { chest: [-16, 0, 0], waist: [-6, 0, 0], head: [-30, 0, 0], jaw: [36, 0, 0], thigh_r: [-16, 0, 8], shin_r: [18, 0, 0], foot_r: [-2, 0, 0], thigh_l: [10, 0, -8], shin_l: [10, 0, 0], foot_l: [-20, 0, 0] }), cfg.roarAim || [0.15, 1, -0.2]));
		return [
			[0, b], [S * 0.6, gather, 'easeinoutsine'], [S, strike(roar), 'easeoutback'],
			[S + 3, ap(roar, { head: [-2, 8, 4], chest: [0, 4, 0] }), 'easeinoutsine'], [S + 6, ap(roar, { head: [2, -8, -4], chest: [0, -4, 0] }), 'easeinoutsine'],
			[S + 9, ap(roar, { head: [-1, 5, 2] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
		];
	});
	// hurt: a jolt back and to the side, then recover
	mk(an, timing, 'hurt', len => [
		[0, b], [2, ap(b, { chest: [-14, 10, 6], waist: [-4, 0, 0], head: [-16, -12, 8], arm_r: [-14, 0, 14], arm_l: [-10, 0, -16], jaw: [16, 0, 0] }), 'easeoutquad'],
		[len, b, 'easeinoutsine'],
	]);
	// death: stagger, knees buckle, kneel and sway, topple forward face-down, a bounce, still
	mk(an, timing, 'death', len => {
		const st = ap(b, { chest: [-12, 6, 4], head: [-22, 10, 0], jaw: [24, 0, 0], arm_r: [-30, 0, 30], arm_l: [-20, 0, -30], thigh_r: [-10, 0, 0], shin_r: [10, 0, 0] });
		const kneel = ap(b, { thigh_r: [-70, 0, 4], shin_r: [100, 0, 0], foot_r: [-30, 0, 0], thigh_l: [10, 0, -4], shin_l: [80, 0, 0], foot_l: [-90, 0, 0],
			chest: [18, 0, 0], waist: [8, 0, 0], head: [24, 0, 0], jaw: [18, 0, 0], arm_r: [10, 0, 10], arm_l: [10, 0, -10], $ground: 'knees' });
		const fallen = ap(noArms(b), { root: [84, 0, 0], thigh_r: [-10, 0, 4], shin_r: [20, 0, 0], thigh_l: [6, 0, -4], shin_l: [14, 0, 0], chest: [4, 0, 0], head: [-10, 20, 0],
			arm_r: [-10, 0, 70], arm_l: [-10, 0, -70], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], jaw: [20, 0, 0], ...OPEN('r'), ...OPEN('l'), $ground: 'body' });
		for (const k of Object.keys(fallen)) if (/^weapon_/.test(k)) delete fallen[k];
		aimW(fallen, cfg.deathAim || [0.6, 0.02, -1]); post(fallen);
		const f = len / 46;
		return [
			[0, b], [8 * f, st, 'easeoutquad'], [18 * f, kneel, 'easeinquad'], [24 * f, ap(kneel, { chest: [4, 6, 4], head: [8, -10, 6] }), 'easeinoutsine'],
			[34 * f, fallen, 'easeinquad'], [38 * f, ap(fallen, { root: [-6, 0, 0], $y: 0.6 }), 'easeoutquad'], [42 * f, fallen, 'easeinquad'], [len, ap(fallen, { head: [0, 4, 0] })],
		];
	});
}

// ================================================================ per unit
function raiderClips(an, timing, poses) {
	const M = an.M, b = poses.stand;
	jotunCommon(an, timing, {
		base: b, weapon: 'r', walkSpeed: 0.135, runSpeed: 0.27,
		walkAmp: { th: 22, kc: -6, kb: 14, lift: 24, yaw: 6, lean: 3, arm: 16, wArm: 0.25, fa: 6, rise: 0.5 },
		runAmp: { th: 32, kc: -10, kb: 26, lift: 40, yaw: 9, lean: 12, arm: 34, wArm: 0.3, fa: 26, rise: 1.4 },
	});
	// spear thrust: draw the spear back, step in and drive it forward (strike), recover
	const aim = (p, dir) => L.aimBone(M, p, 'weapon_r', [0, 0, -1], dir);
	const back = ap(b, { waist: [0, 24, 0], chest: [-4, 18, 0], head: [0, -30, 0], arm_r: [20, 20, 20], forearm_r: [-30, 0, 0], arm_l: [-50, 0, -20], forearm_l: [-30, 0, 0], thigh_l: [-20, 0, -4], shin_l: [14, 0, 0], thigh_r: [12, 0, 4], shin_r: [10, 0, 0], foot_r: [-14, 0, 0] });
	const thrust = ap(b, { waist: [10, -20, 0], chest: [12, -14, 0], head: [-10, 22, 0], arm_r: [-74, -10, -6], forearm_r: [44, 0, 0], arm_l: [20, 0, -30], forearm_l: [-40, 0, 0], thigh_l: [-46, 0, -4], shin_l: [40, 0, 0], foot_l: [4, 0, 0], thigh_r: [30, 0, 4], shin_r: [16, 0, 0], foot_r: [-30, 0, 0], jaw: [20, 0, 0] });
	aim(back, [0, 0.15, -1]); aim(thrust, [0, -0.05, -1]);
	mk(an, timing, 'spear_thrust', (len, S) => [[0, b], [S * 0.6, back, 'easeinoutsine'], [S, strike(thrust), 'easeoutback'], [S + 3, ap(thrust, { chest: [2, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// javelin: wind the arm back over the shoulder while the ice spear forms (held), then hurl it
	const wind = ap(poses.action, { arm_r: [8, 0, 6], forearm_r: [14, 0, 0] });
	aim(wind, [0.05, 0.3, -1]);
	mk(an, timing, 'javelin_windup', len => [[0, b], [len * 0.55, ap(wind, { chest: [-4, 0, 0] }), 'easeinoutsine'], [len, wind, 'easeinoutsine']]);
	const release = ap(b, { waist: [12, -26, 0], chest: [16, -18, 0], head: [-8, 24, 0], arm_r: [-110, -20, 8], forearm_r: [-10, 0, 0], arm_l: [30, 0, -24], forearm_l: [-30, 0, 0], thigh_l: [-50, 0, -4], shin_l: [44, 0, 0], thigh_r: [34, 0, 6], shin_r: [20, 0, 0], foot_r: [-36, 0, 0], jaw: [24, 0, 0], ...OPEN('r', 2) });
	aim(release, [0, -0.2, -1]);
	mk(an, timing, 'javelin_throw', (len, S) => [[0, wind], [S, strike(release), 'easeinback'], [S + 4, ap(release, { chest: [6, -6, 0], arm_r: [16, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
}
function shamanClips(an, timing, poses) {
	const M = an.M, b = poses.stand;
	jotunCommon(an, timing, {
		base: b, weapon: 'l', walkSpeed: 0.125, runSpeed: 0.25,
		walkAmp: { th: 18, kc: -4, kb: 12, lift: 20, yaw: 4, lean: 4, arm: 10, wArm: 0.2, fa: 6, rise: 0.4 },
		runAmp: { th: 28, kc: -8, kb: 22, lift: 34, yaw: 7, lean: 12, arm: 26, wArm: 0.3, fa: 20, rise: 1.0 },
		roarArms: { arm_r: [-110, 0, 40], forearm_r: [-20, 0, 0], ...CLAW('r'), arm_l: [-150, 0, -10], forearm_l: [-10, 0, 0], ...FIST('l') },
	});
	const staffUp = (p, dir) => L.aimBone(M, p, 'weapon_l', [0, 0, -1], dir);
	// ice wall: raise the staff high, then bring its butt down hard (the wall rises on the strike)
	const raise = ap(b, { arm_l: [-120, 0, -10], forearm_l: [-30, 0, 0], arm_r: [-60, 0, 40], forearm_r: [-20, 0, 0], ...OPEN('r', 2), chest: [-10, 0, 0], head: [-14, 0, 0], waist: [-4, 0, 0] });
	staffUp(raise, [0.1, 1, 0.2]);
	const slam = ap(b, { arm_l: [-40, 0, -6], forearm_l: [-50, 0, 0], arm_r: [-20, 0, 50], forearm_r: [-10, 0, 0], ...OPEN('r', 2), chest: [14, 0, 0], waist: [6, 0, 0], head: [10, 0, 0], thigh_r: [-20, 0, 4], shin_r: [24, 0, 0], thigh_l: [10, 0, -4], shin_l: [14, 0, 0], jaw: [14, 0, 0] });
	staffUp(slam, [0.1, 1, -0.1]);
	mk(an, timing, 'ice_wall', (len, S) => [[0, b], [S * 0.65, raise, 'easeinoutsine'], [S, strike(slam), 'easeinquad'], [S + 4, ap(slam, { chest: [-3, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// rime ward: staff thrust toward the wounded ally, the free hand drawing a ward in the air
	const ward = ap(b, { arm_l: [-80, -20, -10], forearm_l: [-10, 0, 0], arm_r: [-70, 20, 30], forearm_r: [-40, 0, 0], ...OPEN('r', 2.4), chest: [4, -10, 0], head: [0, -12, 0], thigh_l: [-18, 0, -4], shin_l: [16, 0, 0] });
	staffUp(ward, [0.2, 0.5, -1]);
	const gatherW = ap(b, { arm_l: [-40, 0, -14], forearm_l: [-60, 0, 0], arm_r: [-40, 0, 20], forearm_r: [-70, 0, 0], chest: [-6, 8, 0], head: [-8, 10, 0] });
	staffUp(gatherW, [0, 1, 0.1]);
	mk(an, timing, 'rime_ward', (len, S) => [[0, b], [S * 0.55, gatherW, 'easeinoutsine'], [S, strike(ward), 'easeoutback'], [S + 6, ap(ward, { arm_r: [6, 0, -6] }), 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	// frost nova: crouch and pull the cold in, then explode outward with both arms
	const crouch = ap(b, { chest: [24, 0, 0], waist: [10, 0, 0], head: [18, 0, 0], arm_l: [-20, 0, 20], forearm_l: [-70, 0, 0], arm_r: [-20, 0, -20], forearm_r: [-70, 0, 0], thigh_r: [-40, 0, 6], shin_r: [60, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-40, 0, -6], shin_l: [60, 0, 0], foot_l: [-20, 0, 0] });
	staffUp(crouch, [0, 1, 0]);
	const burst = ap(b, { chest: [-18, 0, 0], waist: [-4, 0, 0], head: [-24, 0, 0], jaw: [30, 0, 0], arm_l: [-30, 0, -90], forearm_l: [-10, 0, 0], arm_r: [-30, 0, 90], forearm_r: [-10, 0, 0], ...OPEN('r', 2.5), thigh_r: [-10, 0, 14], thigh_l: [-10, 0, -14] });
	staffUp(burst, [0.6, 1, 0]);
	mk(an, timing, 'frost_nova', (len, S) => [[0, b], [S * 0.7, crouch, 'easeinoutsine'], [S, strike(burst), 'easeoutback'], [S + 3, ap(burst, { chest: [2, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
}
function bruteClips(an, timing, poses) {
	const b = poses.stand;
	jotunCommon(an, timing, {
		base: b, weapon: null, walkSpeed: 0.12, runSpeed: 0.3,
		walkAmp: { th: 18, kc: -4, kb: 12, lift: 22, yaw: 7, lean: 4, arm: 18, wArm: -1, fa: 8, rise: 0.5 },
		runAmp: { th: 30, kc: -8, kb: 24, lift: 38, yaw: 10, lean: 16, arm: 36, wArm: -1, fa: 30, rise: 1.2 },
		roarArms: { arm_r: [-40, 0, 80], forearm_r: [-60, 0, 0], arm_l: [-40, 0, -80], forearm_l: [-60, 0, 0], chest: [-6, 0, 0], ...FIST('r'), ...FIST('l') },
	});
	// charge wind-up: head down, one foot pawing the ground, fists back (held), then the charge loop
	const set = ap(b, { chest: [26, 0, 0], waist: [10, 0, 0], head: [-10, 0, 0], arm_r: [40, 0, 14], forearm_r: [-40, 0, 0], arm_l: [40, 0, -14], forearm_l: [-40, 0, 0], thigh_l: [-30, 0, -4], shin_l: [40, 0, 0], thigh_r: [20, 0, 4], shin_r: [16, 0, 0], foot_r: [-30, 0, 0] });
	mk(an, timing, 'charge_windup', len => [[0, b], [len * 0.3, set, 'easeinoutsine'], [len * 0.5, ap(set, { thigh_r: [-30, 0, 0], shin_r: [40, 0, 0], foot_r: [10, 0, 0], $y: 0.4 }), 'easeoutquad'], [len * 0.7, set, 'easeinquad'], [len * 0.85, ap(set, { thigh_r: [-24, 0, 0], shin_r: [34, 0, 0] }), 'easeoutquad'], [len, set, 'easeinquad']]);
	{
		const len = timing.charge.length, q = len / 4;
		const lean = { chest: [30, 0, 0], waist: [12, 0, 0], head: [-14, 0, 0], arm_r: [50, 0, 18], forearm_r: [-30, 0, 0], arm_l: [50, 0, -18], forearm_l: [-30, 0, 0] };
		const c1 = ap(b, lean, { thigh_r: [-50, 0, 0], shin_r: [10, 0, 0], foot_r: [30, 0, 0], thigh_l: [40, 0, 0], shin_l: [40, 0, 0], foot_l: [-30, 0, 0], arm_r: [-10, 0, 0], arm_l: [10, 0, 0] });
		const p1 = ap(b, lean, { thigh_r: [0, 0, 0], shin_r: [20, 0, 0], thigh_l: [-50, 0, 0], shin_l: [90, 0, 0], $y: 1.4 });
		mk(an, timing, 'charge', () => [[0, c1], [q, p1, 'easeinoutsine'], [2 * q, ap(b, lean, mirP({ thigh_r: [-50, 0, 0], shin_r: [10, 0, 0], foot_r: [30, 0, 0], thigh_l: [40, 0, 0], shin_l: [40, 0, 0], foot_l: [-30, 0, 0], arm_r: [-10, 0, 0], arm_l: [10, 0, 0] })), 'easeinoutsine'],
			[3 * q, ap(b, lean, mirP({ thigh_r: [0, 0, 0], shin_r: [20, 0, 0], thigh_l: [-50, 0, 0], shin_l: [90, 0, 0] }), { $y: 1.4 }), 'easeinoutsine'], [len, c1, 'easeinoutsine']], { speed: 0.42 });
	}
	// glacier spikes: both fists raised high (the frost line cracks toward the target), then slammed into the ground
	const high = ap(poses.action, { chest: [-6, 0, 0] });
	const slam = ap(b, { chest: [50, 0, 0], waist: [20, 0, 0], head: [-30, 0, 0], jaw: [24, 0, 0], arm_r: [-80, 0, -16], forearm_r: [-10, 0, 0], arm_l: [-80, 0, 16], forearm_l: [-10, 0, 0],
		thigh_r: [-50, 0, 10], shin_r: [70, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-50, 0, -10], shin_l: [70, 0, 0], foot_l: [-20, 0, 0] });
	mk(an, timing, 'glacier_spikes', (len, S) => [[0, b], [S * 0.45, high, 'easeinoutsine'], [S * 0.85, ap(high, { chest: [-6, 0, 0], arm_r: [-6, 0, 0], arm_l: [-6, 0, 0], $y: 0.6 }), 'easeinoutsine'], [S, strike(slam), 'easeinquad'], [S + 3, ap(slam, { chest: [-4, 0, 0], $y: 0.3 }), 'easeoutquad'], [S + 6, slam, 'easeinquad'], [len, b, 'easeinoutsine']]);
	// brazier smash: a hammer-fist overhead and down, aimed low in front
	const up = ap(b, { arm_r: [-170, 0, -4], forearm_r: [-40, 0, 0], arm_l: [-170, 0, 4], forearm_l: [-40, 0, 0], chest: [-14, 0, 0], head: [-10, 0, 0], thigh_l: [-20, 0, -4], shin_l: [14, 0, 0] });
	const down = ap(b, { arm_r: [-60, 0, -24], forearm_r: [-20, 0, 0], arm_l: [-60, 0, 24], forearm_l: [-20, 0, 0], chest: [40, 0, 0], waist: [16, 0, 0], head: [-20, 0, 0], jaw: [26, 0, 0], thigh_l: [-40, 0, -4], shin_l: [50, 0, 0], thigh_r: [10, 0, 4], shin_r: [30, 0, 0] });
	mk(an, timing, 'brazier_smash', (len, S) => [[0, b], [S * 0.6, up, 'easeinoutsine'], [S, strike(down), 'easeinquad'], [S + 3, ap(down, { chest: [-4, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
}
function warlordClips(an, timing, poses) {
	const M = an.M, b = poses.stand;
	// carried: the greatsword rests on the right shoulder for locomotion
	const carry = ap(b, {});
	for (const k of ['arm_r', 'forearm_r', 'arm_l', 'forearm_l', 'weapon_r']) delete carry[k];
	Object.assign(carry, { arm_r: [-30, 0, 20], forearm_r: [-100, 0, 0], arm_l: [4, 0, -6], forearm_l: [-12, 0, 0] });
	L.aimBone(M, carry, 'weapon_r', [0, 0, -1], [0, 0.7, 1]);
	const aim = (p, dir) => L.aimBone(M, p, 'weapon_r', [0, 0, -1], dir);
	jotunCommon(an, timing, {
		base: carry, weapon: 'r', walkSpeed: 0.11, runSpeed: 0.22,
		walkAmp: { th: 16, kc: -4, kb: 12, lift: 20, yaw: 5, lean: 2, arm: 12, wArm: 0.1, fa: 4, rise: 0.6 },
		runAmp: { th: 26, kc: -8, kb: 20, lift: 32, yaw: 7, lean: 10, arm: 26, wArm: 0.1, fa: 18, rise: 1.2 },
	});
	// the idle keeps the iconic stance: both hands on the planted sword's pommel (overrides the carried idle)
	mk(an, timing, 'idle', len => {
		const q = len / 4;
		return [
			[0, ap(b, { chest: [-1.5, 0, 0] })],
			[q, ap(b, { chest: [-4, 0, 0], head: [-3, 0, 0], jaw: [2, 0, 0] }), 'easeinoutsine'],
			[q * 1.8, ap(b, { chest: [1.5, -4, 0], hips: [0, 0, 2], thigh_r: [0, 0, -2], thigh_l: [0, 0, -2], head: [2, 22, -2], jaw: [8, 0, 0] }), 'easeinoutsine'],
			[q * 2.8, ap(b, { chest: [-3, 3, 0], head: [-1, -16, 2] }), 'easeinoutsine'],
			[len, ap(b, { chest: [-1.5, 0, 0] }), 'easeinoutsine'],
		];
	});
	// cleave: sword dragged back to the right (telegraph), then a wide arc across the front
	const coil = ap(carry, { waist: [0, 40, 0], chest: [-6, 26, 0], head: [0, -40, 0], arm_r: [-30, 50, 60], forearm_r: [-30, 0, 0], arm_l: [-60, 30, -10], forearm_l: [-50, 0, 0], thigh_r: [10, 0, 6], shin_r: [10, 0, 0], thigh_l: [-26, 0, -6], shin_l: [20, 0, 0] });
	aim(coil, [-1, 0.25, 0.4]);
	const arc = ap(carry, { waist: [8, -40, 0], chest: [10, -26, 0], head: [-6, 30, 0], arm_r: [-80, -40, -10], forearm_r: [-6, 0, 0], arm_l: [-40, -20, -40], forearm_l: [-30, 0, 0], thigh_l: [-40, 0, -6], shin_l: [36, 0, 0], thigh_r: [24, 0, 6], shin_r: [16, 0, 0], foot_r: [-20, 0, 0], jaw: [24, 0, 0] });
	aim(arc, [1, -0.05, -0.5]);
	mk(an, timing, 'cleave', (len, S) => [[0, carry], [S * 0.55, coil, 'easeinoutsine'], [S * 0.85, ap(coil, { waist: [0, 6, 0], chest: [-3, 4, 0] }), 'easeinoutsine'], [S, strike(arc), 'easeinquad'], [S + 4, ap(arc, { waist: [0, -8, 0], chest: [4, 0, 0] }), 'easeoutquad'], [len, carry, 'easeinoutsine']]);
	// sword plant: both hands raise the sword point-down overhead, then drive it into the ground
	const lift = ap(b, { arm_r: [-150, 0, -10], forearm_r: [-30, 0, 0], arm_l: [-150, 0, 10], forearm_l: [-30, 0, 0], chest: [-14, 0, 0], head: [-16, 0, 0], thigh_r: [-10, 0, 6], thigh_l: [-10, 0, -6] });
	aim(lift, [0, -1, -0.12]);
	const plant = ap(b, { arm_r: [-60, 0, -20], forearm_r: [-20, 0, 0], arm_l: [-60, 0, 20], forearm_l: [-20, 0, 0], chest: [36, 0, 0], waist: [14, 0, 0], head: [-18, 0, 0], jaw: [24, 0, 0], thigh_r: [-50, 0, 8], shin_r: [74, 0, 0], foot_r: [-24, 0, 0], thigh_l: [-30, 0, -8], shin_l: [80, 0, 0], foot_l: [-50, 0, 0] });
	aim(plant, [0, -1, -0.02]);
	mk(an, timing, 'sword_plant', (len, S) => [[0, b], [S * 0.6, lift, 'easeinoutsine'], [S * 0.85, ap(lift, { $y: 0.8 }), 'easeoutquad'], [S, strike(plant), 'easeinquad'], [S + 4, ap(plant, { chest: [-4, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// rally: the sword thrust skyward, head back, a roar (the buff lands on the hit)
	const sky = ap(poses.action, {});
	mk(an, timing, 'rally', (len, S) => [[0, b], [S * 0.6, ap(b, { chest: [12, 0, 0], head: [14, 0, 0] }), 'easeinoutsine'], [S, strike(sky), 'easeoutback'], [S + 4, ap(sky, { head: [-4, 6, 3] }), 'easeinoutsine'], [S + 8, ap(sky, { head: [2, -6, -3] }), 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	// kneel: down on one knee, leaning on the planted sword (held at 30%)
	const kneel = ap(b, { thigh_r: [-80, 0, 4], shin_r: [94, 0, 0], foot_r: [-14, 0, 0], thigh_l: [16, 0, -4], shin_l: [86, 0, 0], foot_l: [-100, 0, 0], chest: [22, 0, 0], waist: [8, 0, 0], head: [20, 0, 0], jaw: [14, 0, 0], $ground: 'knees' });
	mk(an, timing, 'kneel', len => [[0, b], [len * 0.25, ap(kneel, { chest: [8, 0, 0] }), 'easeinquad'], [len * 0.35, kneel, 'easeoutquad'], [len * 0.7, ap(kneel, { chest: [-4, 0, 0], head: [-6, 0, 0] }), 'easeinoutsine'], [len, kneel, 'easeinoutsine']]);
}
function laufeyClips(an, timing, poses) {
	const M = an.M, b = poses.stand;
	const staff = (p, dir) => L.aimBone(M, p, 'weapon_r', [0, 0, -1], dir);
	const box = p => L.aimBone(M, p, 'weapon_l', [0, 1, 0], [0, 1, 0]);
	const S0 = s => { staff(s, [0, 1, -0.04]); box(s); return s; };
	// a slow, regal stride: small arm swing, the staff kept upright, head held high and still
	jotunCommon(an, timing, {
		base: b, weapon: 'r', walkSpeed: 0.09, runSpeed: 0.18,
		walkAmp: { th: 14, kc: -3, kb: 10, lift: 16, yaw: 3, lean: -1, arm: 4, wArm: 0.2, fa: 2, rise: 0.4 },
		runAmp: { th: 22, kc: -6, kb: 18, lift: 26, yaw: 5, lean: 6, arm: 10, wArm: 0.2, fa: 8, rise: 0.8 },
		roarArms: { arm_r: [-40, 0, 30], forearm_r: [-50, 0, 0], arm_l: [-165, 0, -12], forearm_l: [-10, 0, 0], ...FIST('r'), ...FIST('l') },
		post: p => { box(p); return p; }, roarAim: [0.1, 1, 0.1],
	});
	// override the idle: still and regal, the slow breath of something ancient, the Casket glancing
	mk(an, timing, 'idle', len => {
		const q = len / 4;
		return [
			[0, b], [q, ap(b, { chest: [-2.5, 0, 0], head: [-3, 0, 0] }), 'easeinoutsine'],
			[q * 2, ap(b, { chest: [1, -3, 0], head: [6, -18, 0], arm_l: [-8, 0, 0], jaw: [4, 0, 0] }), 'easeinoutsine'],
			[q * 3, ap(b, { chest: [-2, 2, 0], head: [-2, 10, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
		];
	});
	// spear fan: the staff raised, three spears form above his hand (ticks 8/16/24), then a sweeping cast
	const raise = S0(ap(b, { arm_r: [-150, 0, 10], forearm_r: [-20, 0, 0], chest: [-8, 0, 0], head: [-14, 0, 0] }));
	staff(raise, [0.1, 1, 0.3]);
	const cast = ap(b, { arm_r: [-80, -30, -10], forearm_r: [-6, 0, 0], chest: [10, -14, 0], waist: [4, -10, 0], head: [0, 14, 0], jaw: [14, 0, 0], thigh_r: [-20, 0, 4], shin_r: [16, 0, 0] });
	staff(cast, [0.4, 0.1, -1]); box(cast);
	mk(an, timing, 'spear_fan', (len, S) => [[0, b], [4, ap(raise, { chest: [2, 0, 0] }), 'easeinoutsine'], [12, ap(raise, { arm_r: [-6, 0, 0] }), 'easeinoutsine'], [20, ap(raise, { arm_r: [-10, 0, 0], head: [-4, 0, 0] }), 'easeinoutsine'], [S - 4, ap(raise, { arm_r: [-16, 0, 6], chest: [-6, 0, 0] }), 'easeinoutsine'], [S, strike(cast), 'easeinquad'], [S + 4, ap(cast, { chest: [4, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// summon: the staff lifted and pointed at the rift, the free hand opening the way
	const point = ap(b, { arm_r: [-160, 0, 20], forearm_r: [-6, 0, 0], arm_l: [-4, 0, -30], forearm_l: [0, 0, 0], chest: [-12, 0, 0], head: [-34, 0, 0], jaw: [22, 0, 0] });
	staff(point, [0.2, 1, 0.1]); box(point);
	mk(an, timing, 'summon', (len, S) => [[0, b], [S * 0.6, ap(point, { arm_r: [20, 0, 0], head: [10, 0, 0] }), 'easeinoutsine'], [S, strike(point), 'easeoutback'], [S + 6, ap(point, { head: [4, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	// frozen grasp: a long bony finger pointed at the victim, then the hand closes (the prison closes on the hit)
	const reachOut = ap(b, { arm_r: [-80, -20, 0], forearm_r: [-10, 0, 0], ...OPEN('r', 1.5), chest: [6, -12, 0], head: [6, 16, 0], thigh_r: [-14, 0, 4], shin_r: [12, 0, 0] });
	staff(reachOut, [0.3, 0.7, -1]); box(reachOut);
	const close = ap(reachOut, { forearm_r: [-20, 0, 0], ...FIST('r'), chest: [-4, 0, 0], jaw: [12, 0, 0] });
	mk(an, timing, 'frozen_grasp', (len, S) => [[0, b], [10, reachOut, 'easeinoutsine'], [S - 6, ap(reachOut, { arm_r: [-6, 0, 0] }), 'easeinoutsine'], [S, strike(close), 'easeinquad'], [S + 4, ap(close, { arm_r: [10, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// casket open: the Casket lifted overhead, the lid thrown back on the hit
	const lift = ap(poses.action, { casket_lid: [0, 0, 0] });
	const opened = ap(poses.action, {});
	mk(an, timing, 'casket_open', (len, S) => [[0, b], [S * 0.6, lift, 'easeinoutsine'], [S, strike(opened), 'easeoutback'], [S + 6, ap(opened, { head: [-6, 0, 0] }), 'easeinoutsine'], [len, opened, 'easeinoutsine']]);
	// winter's breath: frost gathers in the open lid (2 s), then the cone pours out for 3 s, sweeping slightly
	const aimBox = ap(b, { arm_l: [-90, 10, -6], forearm_l: [-10, 0, 0], chest: [6, 8, 0], head: [4, -10, 0], casket_lid: [-100, 0, 0], thigh_l: [-14, 0, -4], shin_l: [12, 0, 0] });
	L.aimBone(M, aimBox, 'weapon_l', [0, 1, 0], [0, 0.4, 1]);
	mk(an, timing, "winters_breath", (len, S) => [[0, b], [S * 0.5, ap(aimBox, { arm_l: [20, 0, 0], casket_lid: [60, 0, 0] }), 'easeinoutsine'], [S * 0.85, ap(aimBox, { casket_lid: [20, 0, 0], chest: [-4, 0, 0] }), 'easeinoutsine'], [S, strike(aimBox), 'easeoutback'],
		[S + 20, ap(aimBox, { chest: [0, 10, 0], arm_l: [0, 10, 0] }), 'easeinoutsine'], [S + 40, ap(aimBox, { chest: [0, -10, 0], arm_l: [0, -10, 0] }), 'easeinoutsine'], [len, aimBox, 'easeinoutsine']]);
	// ice pillars: the staff stamped on the ground
	const up = ap(b, { arm_r: [-110, 0, 6], forearm_r: [-40, 0, 0], chest: [-8, 0, 0], head: [-8, 0, 0] });
	staff(up, [0, 1, -0.1]); box(up);
	const stamp = ap(b, { arm_r: [-30, 0, 10], forearm_r: [-60, 0, 0], chest: [16, 0, 0], waist: [6, 0, 0], head: [6, 0, 0], jaw: [16, 0, 0], thigh_r: [-16, 0, 4], shin_r: [24, 0, 0] });
	staff(stamp, [0, 1, -0.02]); box(stamp);
	mk(an, timing, 'ice_pillars', (len, S) => [[0, b], [S * 0.65, up, 'easeinoutsine'], [S, strike(stamp), 'easeinquad'], [S + 4, ap(stamp, { chest: [-4, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// avalanche slam: the staff raised in both... the right hand high, then smashed down in front
	const high = ap(b, { arm_r: [-175, 0, -6], forearm_r: [-20, 0, 0], chest: [-14, 0, 0], head: [-18, 0, 0], waist: [-4, 0, 0] });
	staff(high, [0, 0.4, 1]); box(high);
	const smash = ap(b, { arm_r: [-70, 0, -10], forearm_r: [-10, 0, 0], chest: [30, 0, 0], waist: [12, 0, 0], head: [-10, 0, 0], jaw: [24, 0, 0], thigh_r: [-36, 0, 6], shin_r: [44, 0, 0], foot_r: [-8, 0, 0], thigh_l: [12, 0, -4], shin_l: [20, 0, 0] });
	staff(smash, [0, -0.6, -1]); box(smash);
	mk(an, timing, 'avalanche_slam', (len, S) => [[0, b], [S * 0.6, high, 'easeinoutsine'], [S * 0.85, ap(high, { $y: 0.6 }), 'easeoutquad'], [S, strike(smash), 'easeinquad'], [S + 4, ap(smash, { chest: [-4, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// stagger: dazed, hunched and swaying, the staff dragged low (held for 5 s)
	const daze = ap(b, { chest: [20, 0, 6], waist: [8, 0, 0], head: [24, 10, 10], jaw: [18, 0, 0], arm_r: [10, 0, 10], forearm_r: [-30, 0, 0], arm_l: [-10, 0, -20], forearm_l: [-40, 0, 0], thigh_r: [-20, 0, 6], shin_r: [30, 0, 0], thigh_l: [-6, 0, -4], shin_l: [16, 0, 0] });
	staff(daze, [0.2, 0.8, -0.4]); box(daze);
	mk(an, timing, 'stagger', len => [[0, b], [10, ap(daze, { chest: [-10, -10, 0], head: [-20, -10, 0] }), 'easeoutquad'], [24, daze, 'easeinoutsine'], [44, ap(daze, { chest: [0, 6, -8], head: [0, 14, -14] }), 'easeinoutsine'], [64, ap(daze, { chest: [0, -6, 6], head: [0, -10, 10] }), 'easeinoutsine'], [len, daze, 'easeinoutsine']]);
	// kneel hold: on both knees, refusing to fall (held at 1 HP)
	const kneel = ap(b, { thigh_r: [-84, 0, 6], shin_r: [100, 0, 0], foot_r: [-16, 0, 0], thigh_l: [-84, 0, -6], shin_l: [100, 0, 0], foot_l: [-16, 0, 0], chest: [20, 0, 0], head: [16, 0, 0], arm_r: [-40, 0, 10], forearm_r: [-30, 0, 0], $ground: 'knees' });
	staff(kneel, [0, 1, -0.1]); box(kneel);
	mk(an, timing, 'kneel_hold', len => [[0, b], [len * 0.6, ap(kneel, { chest: [6, 0, 0], $y: 0.4 }), 'easeinquad'], [len, kneel, 'easeoutquad']]);
	// death (overrides the shared one): kneel, the head bowed, the crown cracks, frozen still, shatters at the end
	mk(an, timing, 'death', len => [[0, b], [10, ap(b, { chest: [-14, 0, 0], head: [-26, 0, 0], jaw: [26, 0, 0], arm_r: [-20, 0, 20], arm_l: [-40, 0, -20] }), 'easeoutquad'], [26, kneel, 'easeinquad'], [36, ap(kneel, { chest: [10, 0, 0], head: [30, 0, 0], jaw: [10, 0, 0] }), 'easeinoutsine'], [len, ap(kneel, { chest: [12, 0, 0], head: [34, 0, 0] })]]);
}
function houndClips(an, timing, poses) {
	const M = an.M, b = sanitize(poses.stand);
	const legs = (fr, fl, br, bl, k = 1) => ({
		leg_fr: [fr[0] * k, 0, 0], lower_fr: [fr[1] * k, 0, 0], paw_fr: [fr[2] * k, 0, 0], leg_fl: [fl[0] * k, 0, 0], lower_fl: [fl[1] * k, 0, 0], paw_fl: [fl[2] * k, 0, 0],
		leg_br: [br[0] * k, 0, 0], lower_br: [br[1] * k, 0, 0], paw_br: [br[2] * k, 0, 0], leg_bl: [bl[0] * k, 0, 0], lower_bl: [bl[1] * k, 0, 0], paw_bl: [bl[2] * k, 0, 0],
	});
	mk(an, timing, 'idle', len => [
		[0, ap(b, { body: [0, 0, 0] })], [len * 0.25, ap(b, { body: [-1.5, 0, 0], head: [-4, 8, 0], ear_l: [-14, 0, 0], jaw: [10, 0, 0] }), 'easeinoutsine'],
		[len * 0.5, ap(b, { body: [0.5, 0, 0], head: [2, -12, 4], ear_r: [-18, 0, 0], tail: [0, 18, 0] }), 'easeinoutsine'],
		[len * 0.75, ap(b, { body: [-1, 0, 0], head: [-2, 2, 0], jaw: [16, 0, 0], tail: [0, -16, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
	]);
	// walk: diagonal pairs (front-right with back-left), stride matched to 0.19 b/t
	const walkLen = timing.walk.length, wq = walkLen / 4;
	const wA = legs([-26, 8, 14], [20, 22, -24], [24, 10, -6], [-22, 20, 10]);
	const wP = legs([0, 26, 0], [0, 0, 0], [0, 0, 0], [0, 30, 0]);
	mk(an, timing, 'walk', () => [[0, ap(b, wA, { head: [2, -4, 0] })], [wq, ap(b, wP, { $y: 0.4 }), 'easeinoutsine'], [2 * wq, ap(b, mirP(wA), { head: [2, 4, 0] }), 'easeinoutsine'], [3 * wq, ap(b, mirP(wP), { $y: 0.4 }), 'easeinoutsine'], [walkLen, ap(b, wA, { head: [2, -4, 0] }), 'easeinoutsine']], { speed: 0.19 });
	// run: a bounding gallop, front pair then back pair, spine flexing
	const runLen = timing.run.length, rq = runLen / 4;
	const gather = ap(b, legs([30, 30, 10], [36, 24, 14], [-50, 40, 10], [-44, 36, 14]), { body: [6, 0, 0], head: [-6, 0, 0], $y: 0.6 });
	const extend = ap(b, legs([-60, 10, 30], [-50, 14, 24], [50, 30, 30], [44, 26, 36]), { body: [-6, 0, 0], head: [6, 0, 0], jaw: [16, 0, 0], ear_l: [20, 0, 0], ear_r: [20, 0, 0], $y: 2.6 });
	mk(an, timing, 'run', () => [[0, gather], [rq, ap(gather, extend, scalePose(gather, -1), { $y: 1.6 }), 'easeinoutsine'], [2 * rq, extend, 'easeoutquad'], [3 * rq, ap(extend, scalePose(extend, -0.5), scalePose(gather, 0.5), { $y: 1.2 }), 'easeinoutsine'], [runLen, gather, 'easeinquad']], { speed: 0.26 });
	// war cry: a snarl, hackles up, head low
	const snarl = ap(b, { body: [6, 0, 0], head: [10, 0, 0], jaw: [26, 0, 0], ear_l: [30, 0, 0], ear_r: [30, 0, 0], tail: [-20, 0, 0], ...legs([-10, 20, 0], [-10, 20, 0], [10, 20, -6], [10, 20, -6]) });
	mk(an, timing, 'war_cry', (len, S) => [[0, b], [S * 0.6, ap(b, { head: [-8, 0, 0] }), 'easeinoutsine'], [S, strike(snarl), 'easeoutback'], [S + 4, ap(snarl, { head: [0, 8, 0] }), 'easeinoutsine'], [S + 8, ap(snarl, { head: [0, -8, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { body: [-8, 6, 6], head: [-16, -14, 0], jaw: [20, 0, 0], ear_l: [30, 0, 0], ear_r: [30, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	mk(an, timing, 'death', len => [[0, b], [6, ap(b, { body: [-10, 0, 8], head: [-20, 0, 0], jaw: [24, 0, 0] }), 'easeoutquad'], [16, ap(b, { root: [0, 0, 84], head: [10, 0, 0], ...legs([-20, 10, 0], [-30, 20, 0], [30, 10, 0], [20, 20, 0]), $ground: 'all' }), 'easeinquad'], [20, ap(b, { root: [0, 0, 78], $ground: 'all', $y: 0.3 }), 'easeoutquad'], [len, ap(b, { root: [0, 0, 84], head: [16, 0, 0], jaw: [16, 0, 0], ...legs([-20, 10, 0], [-30, 20, 0], [30, 10, 0], [20, 20, 0]), $ground: 'all' }), 'easeinquad']]);
	// bite: crouch back, lunge, jaws snap shut on the hit
	const coil = ap(b, { body: [6, 0, 0], head: [-10, 0, 0], jaw: [40, 0, 0], ...legs([20, 10, 0], [20, 10, 0], [-20, 30, 0], [-20, 30, 0]) });
	const snap = ap(b, { body: [-6, 0, 0], head: [20, 0, 0], jaw: [0, 0, 0], ...legs([-40, 10, 20], [-36, 12, 18], [30, 14, 10], [26, 14, 10]), $y: 0.5 });
	mk(an, timing, 'bite', (len, S) => [[0, b], [S * 0.5, coil, 'easeinoutsine'], [S - 2, ap(snap, { jaw: [40, 0, 0] }), 'easeinquad'], [S, strike(snap), 'easeinquad'], [S + 2, ap(snap, { head: [0, 10, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// howl: sit back on the haunches, muzzle to the sky
	const sit = ap(b, { body: [-30, 0, 0], head: [-50, 0, 0], jaw: [30, 0, 0], ear_l: [-10, 0, 0], ear_r: [-10, 0, 0], tail: [30, 0, 0], ...legs([30, 0, -30], [30, 0, -30], [-70, 110, -40], [-70, 110, -40]), $ground: 'all' });
	mk(an, timing, 'howl', (len, S) => [[0, b], [S * 0.7, ap(sit, { head: [20, 0, 0], jaw: [-30, 0, 0] }), 'easeinoutsine'], [S, strike(sit), 'easeoutquad'], [S + 10, ap(sit, { head: [-6, 0, 0] }), 'easeinoutsine'], [S + 18, ap(sit, { head: [4, 0, 0], jaw: [-10, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	// pounce: crouch, spring (the leap starts on the hit), front paws reaching
	const crouch = ap(b, { body: [8, 0, 0], head: [-6, 0, 0], ...legs([30, 30, 10], [30, 30, 10], [-40, 50, 0], [-40, 50, 0]), tail: [-10, 0, 0] });
	const leap = ap(sanitize(poses.action), {});
	mk(an, timing, 'pounce', (len, S) => [[0, b], [S * 0.7, crouch, 'easeinoutsine'], [S, strike(ap(leap, { $root: [0, 0, 0], $ground: 'feet' })), 'easeoutquad'], [S + 4, ap(leap, { $ground: false, $root: [0, 22, 0] }), 'easeoutquad'], [len, ap(leap, { $ground: false, $root: [0, 8, 0] }), 'easeinquad']]);
}
// Ymir's arm: palm at the origin, the forearm rising to the rift. Root position (px) lifts the whole arm.
function ymirClips(an, timing) {
	const fingers = (k1, k2, k3, spread) => { const o = {}; [9.8, 3.3, -3.3, -9.9].forEach((x, i) => { o[`f${i}_1`] = [k1, 0, -x / 9.8 * spread]; o[`f${i}_2`] = [k2, 0, 0]; o[`f${i}_3`] = [k3, 0, 0]; }); return o; };
	const flat = Object.assign({ hand: [-90, 0, 0], fore: [78, 0, 0], thumb_1: [-6, 0, -10], thumb_2: [4, 0, 0], thumb_3: [6, 0, 0] }, fingers(4, 6, 8, 9));
	const hover = Object.assign({}, flat, { hand: [-70, 0, 0], fore: [64, 0, 0] }, fingers(16, 20, 18, 12));
	const claw = Object.assign({}, flat, { hand: [-55, 0, 0], fore: [50, 0, 0], thumb_1: [14, 0, -24], thumb_2: [24, 0, 0], thumb_3: [28, 0, 0] }, fingers(26, 36, 30, 14));
	const fist = Object.assign({}, flat, { hand: [-40, 0, 0], fore: [36, 0, 0], thumb_1: [40, 0, 10], thumb_2: [40, 0, 0], thumb_3: [30, 0, 0] }, fingers(80, 90, 70, 0));
	const R = y => ({ $root: [0, y, 0], $ground: false });
	// whatever the hand does, the forearm keeps rising (leaning back a little) to the rift
	const up = (p, lean = 0.2) => { L.aimBone(an.M, p, 'fore', [0, 1, 0], [0, 1, lean]); return p; };
	for (const p of [flat, hover, claw, fist]) up(p);
	mk(an, timing, 'idle', len => [[0, ap(hover, R(160))], [len * 0.3, ap(hover, fingers(8, 10, 6, 4), { hand: [4, 0, 4] }, R(176)), 'easeinoutsine'], [len * 0.6, ap(hover, fingers(-4, -6, 0, -3), { hand: [-3, 0, -4] }, R(150)), 'easeinoutsine'], [len, ap(hover, R(160)), 'easeinoutsine']]);
	mk(an, timing, 'reach_in', len => [[0, ap(claw, R(760))], [len * 0.6, ap(claw, { hand: [10, 0, 0] }, R(240)), 'easeoutquad'], [len * 0.8, ap(hover, R(150)), 'easeinoutsine'], [len, ap(hover, R(160)), 'easeinoutsine']]);
	mk(an, timing, 'palm_slam', (len, S) => [[0, ap(hover, R(160))], [S * 0.55, ap(claw, { hand: [-16, 0, 0] }, R(340)), 'easeinoutsine'], [S - 6, ap(claw, { hand: [-24, 0, 0] }, R(380)), 'easeinoutsine'], [S - 2, ap(flat, fingers(-6, -4, 0, 14), R(60)), 'easeincubic'],
		[S, strike(ap(flat, fingers(-2, 0, 4, 16), R(0))), 'easeinquad'], [S + 2, ap(flat, R(10)), 'easeoutquad'], [len, ap(flat, fingers(10, 14, 12, 10), R(4)), 'easeinoutsine']]);
	const edge = up(Object.assign({}, flat, { hand: [-86, 0, 70] }, fingers(10, 14, 12, 4)));
	const edgeY = y => up(ap(edge, { hand: [0, y, 0] }));
	mk(an, timing, 'glacial_sweep', (len, S) => [[0, ap(hover, R(160))], [S * 0.5, ap(edgeY(40), R(120)), 'easeinoutsine'], [S - 6, ap(edgeY(50), R(20)), 'easeinoutsine'],
		[S, strike(ap(edgeY(40), R(4))), 'easeinquad'], [S + (len - S) * 0.5, ap(edgeY(0), R(4)), 'linear'], [len, ap(edgeY(-40), R(8)), 'easeoutquad']]);
	mk(an, timing, 'retract', len => [[0, ap(hover, R(160))], [len * 0.3, ap(fist, R(200)), 'easeinoutsine'], [len, ap(fist, R(780)), 'easeinquad']]);
}
function casketClips(an, timing) {
	mk(an, timing, 'idle', len => [[0, { casket: [0, 0, 0], lid: [0, 0, 0], $root: [0, 0, 0], $ground: false }], [len * 0.5, { casket: [0, 8, 0], lid: [-14, 0, 0], $root: [0, 1.5, 0], $ground: false }, 'easeinoutsine'], [len, { casket: [0, 0, 0], lid: [0, 0, 0], $root: [0, 0, 0], $ground: false }, 'easeinoutsine']]);
}

module.exports = { raiderClips, shamanClips, bruteClips, warlordClips, laufeyClips, houndClips, ymirClips, casketClips, sanitize, T };
