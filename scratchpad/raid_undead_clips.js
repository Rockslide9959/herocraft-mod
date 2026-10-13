// v0.16.1 raid undead: every GeckoLib clip (used by gen_raid_undead_models.js --game). Lengths and strike ticks come from
// RaidAnims.java (parsed, passed in as `timing`): a play-once / held clip is (length - T) ticks long, its strike keyframe
// sits on (hit - T); loops are `length` ticks. UPPER clips key only the upper body (they play over the walk on the
// action controller). `neck` is code-driven (head look) and never keyed.
// Pose values: degrees added to rest (X > 0 tips a bone's top forward; Z > 0 swings a hanging limb toward the model's
// right / -X). Dangle bones (hair, cloth, chains, wisps, drips) get spring follow-through on the FULL clips.
'use strict';
const L = require('./lib_concept_models.js');
const { addPose: ap, mirrorPose: mirP, scalePose } = require('./lib_geckogen_anim.js');
const G = require('./gen_raid_undead_models.js');
const { CLAW, FIST, OPEN } = G;

const T = 3; // RaidAnims.TRANSITION
const UPPER_RE = /^(waist|chest|head|jaw|arm_|forearm_|hand_|fingers_|item_|weapon_|crossbow|shield|throat|sacs|spyglass|acc_|wing_|hood)/;
const sanitize = p => { const o = Object.assign({}, p); if (o.neck) { o.head = ap({ head: o.head || [0, 0, 0] }, { head: o.neck }).head; delete o.neck; } return o; };
const upperOnly = p => { const o = {}; for (const k of Object.keys(p)) if (k.startsWith('$') ? k === '$strike' : UPPER_RE.test(k)) o[k] = p[k]; return o; };
const strike = p => Object.assign({}, p, { $strike: true });

// One timing-checked clip. build(len, S) -> frames in CLIP ticks; S = the strike's clip tick (or -1).
function mk(an, timing, name, build, opts = {}) {
	const t = timing[name];
	if (!t) throw new Error(`${an.id}: ${name} is not in RaidAnims`);
	const len = t.kind === 'loop' ? t.length : Math.max(1, t.length - T);
	const S = t.hit >= T ? t.hit - T : -1;
	const upper = t.layer === 'UPPER';
	const keep = p => { const o = {}; for (const k of Object.keys(p)) if (k.startsWith('$') || an.M.B[k]) o[k] = p[k]; return o; };
	const frames = build(len, S).map(([tk, p, e]) => [Math.round(tk), upper ? upperOnly(keep(sanitize(p))) : keep(sanitize(p)), e]);
	an.clip(name, len, t.kind === 'loop' ? true : t.kind === 'hold' ? 'hold' : false, frames,
		Object.assign({ ground: upper ? false : 'feet' }, opts, { strikes: t.hit >= T ? [t.hit] : [], noSecondary: upper || opts.noSecondary }));
	an.timing[name] = Object.assign({}, an.timing[name] || {}, { layer: t.layer });
}

// ---------------------------------------------------------------- gait (stride matched to the nominal ground speed)
function gaitPoses(a, k) {
	const th = a.th * k, lift = a.lift * k;
	const contact = {
		thigh_r: [-th, 0, 0], shin_r: [a.kc, 0, 0], foot_r: [th - a.kc + 4, 0, 0],
		thigh_l: [th * 0.8, 0, 0], shin_l: [a.kb, 0, 0], foot_l: [-th * 0.8 - a.kb + 8, 0, 0],
		hips: [0, -a.yaw, a.roll], waist: [a.lean, a.yaw * 0.5, -a.roll * 0.6], chest: [a.lean * 0.6, a.yaw * 0.8, -a.roll * 0.5], head: [-a.lean * 1.1, -a.yaw * 1.1, a.roll * 0.4],
		arm_l: [-a.arm, 0, 0], forearm_l: [-a.fa, 0, 0], arm_r: [a.arm * a.wArm, 0, 0], forearm_r: [-a.fa * Math.abs(a.wArm), 0, 0],
	};
	const down = { thigh_r: [-th + 5, 0, 0], shin_r: [a.kc + 12 + a.dip * 10, 0, 0], foot_r: [th - a.kc - 10, 0, 0], chest: [a.lean * 0.6 + 3 + a.dip * 6, 0, 0], head: [-3 - a.dip * 6, 0, 0], jaw: [a.dip * 8, 0, 0] };
	const passing = {
		thigh_r: [2, 0, 0], shin_r: [3, 0, 0], foot_r: [-4, 0, 0],
		thigh_l: [-lift, 0, 0], shin_l: [lift * 1.7, 0, 0], foot_l: [-lift * 0.5, 0, 0],
		hips: [0, 0, -a.roll], waist: [a.lean, 0, a.roll * 0.4], chest: [a.lean * 0.6 - 1, 0, a.roll * 0.3], head: [-a.lean + 1, 0, 0],
	};
	return { contact, down, passing };
}
function stepOf(M, pose) {
	const W = M.world(pose);
	const pr = L.affApply(W.foot_r, [-M.A.legX, 0.3, -1.5]), pl = L.affApply(W.foot_l, [M.A.legX, 0.3, -1.5]);
	return Math.abs(pr[2] - pl[2]) * M.k;
}
// a: { th, kc, kb, lift, yaw, lean, arm, wArm, fa, rise, roll, dip, limp (0..1: right leg drags), arms (fixed arm pose, optional) }
function gait(an, timing, name, base, a, speed, extra = {}) {
	const M = an.M;
	const cycle = timing[name].length;
	const want = speed * cycle / 2;
	let lo = 0.1, hi = 3;
	for (let i = 0; i < 30; i++) { const k = (lo + hi) / 2; if (stepOf(M, ap(base, gaitPoses(a, k).contact)) < want) lo = k; else hi = k; }
	const g = gaitPoses(a, (lo + hi) / 2);
	const q = cycle / 4;
	const armsFix = p => a.arms ? Object.assign(p, a.arms(p)) : p;
	const limp = a.limp || 0;
	// the right foot drags: less lift when it passes, the hip drops onto it, the body jerks over it
	const lP = ap(g.passing, { thigh_l: [0, 0, 0] });
	const C = armsFix(ap(base, g.contact, extra.c || {}));
	const D = armsFix(ap(base, g.contact, g.down, extra.d || {}));
	const Pp = armsFix(ap(base, lP, { $y: a.rise }, extra.p || {}));
	const mC = armsFix(ap(base, mirP(g.contact), extra.mc || {}, limp ? { hips: [0, 0, -4 * limp], chest: [4 * limp, 0, 6 * limp], head: [-4 * limp, 0, -6 * limp] } : {}));
	const mD = armsFix(ap(base, mirP(g.contact), mirP(g.down), extra.md || {}, limp ? { hips: [0, 0, -6 * limp], chest: [6 * limp, 0, 8 * limp], shin_l: [10 * limp, 0, 0] } : {}));
	const mPpose = ap(mirP(g.passing), limp ? { thigh_r: [a.lift * limp * 0.7, 0, 0], shin_r: [-a.lift * 1.4 * limp, 0, 0], foot_r: [20 * limp, 0, 0] } : {});
	const mP = armsFix(ap(base, mPpose, { $y: a.rise * (1 - limp * 0.6) }, extra.mp || {}));
	mk(an, timing, name, () => [
		[0, C], [q * 0.45, D, 'easeoutquad'], [q, Pp, 'easeinoutsine'],
		[2 * q, mC, 'easeinoutsine'], [2 * q + q * 0.45, mD, 'easeoutquad'], [3 * q, mP, limp ? 'linear' : 'easeinoutsine'], [4 * q, C, 'easeinoutsine'],
	], { speed });
	an.timing[name].stride_blocks = +(want * 2).toFixed(2);
	an.timing[name].move_speed = speed;
}

// ---------------------------------------------------------------- the shared zombie kit (Risen, grave-child, Cursed, boss melee)
// cfg: { base, reach (chase arm pose), walk, chase (gait amps), walkSpeed, chaseSpeed, twitch (0..1), swing (attack style) }
function zombieKit(an, timing, cfg) {
	const M = an.M, b = cfg.base;
	const reachArms = p => { const o = {}; for (const k of Object.keys(cfg.reach)) if (/^(arm_|forearm_|fingers_|jaw)/.test(k)) o[k] = cfg.reach[k]; return o; };
	const tw = cfg.twitch == null ? 1 : cfg.twitch;
	// idle: slow laboured breathing, a sway, jerky head twitches (fast linear snaps), the jaw working
	mk(an, timing, 'idle', len => {
		const q = len / 12;
		return [
			[0, ap(b, { chest: [-1, 0, 0] })],
			[q * 2, ap(b, { chest: [-3, 0, 1], waist: [-1, 0, 0], head: [2, 0, 0], jaw: [3, 0, 0] }), 'easeinoutsine'],
			[q * 3, ap(b, { chest: [-3, 0, 1], head: [6 * tw, 18 * tw, -14 * tw], jaw: [8, 0, 0] }), 'linear'],
			[q * 3.6, ap(b, { chest: [-2, 0, 1], head: [4 * tw, 14 * tw, -10 * tw], jaw: [2, 0, 0] }), 'easeoutquad'],
			[q * 5.5, ap(b, { chest: [1, -2, -1], hips: [0, 0, 2], thigh_r: [0, 0, -2], thigh_l: [0, 0, -2], head: [-2, -6, 4], arm_r: [-4, 0, 2], fingers_r: [0, 0, -20 * tw] }), 'easeinoutsine'],
			[q * 6.1, ap(b, { chest: [1, -2, -1], hips: [0, 0, 2], thigh_r: [0, 0, -2], thigh_l: [0, 0, -2], head: [-10 * tw, -22 * tw, 12 * tw], arm_r: [-4, 0, 2], fingers_r: [0, 0, 10 * tw] }), 'linear'],
			[q * 7.5, ap(b, { chest: [-3, 0, 0], head: [-4, -10, 6], jaw: [10, 0, 0] }), 'easeinoutsine'],
			[q * 8.0, ap(b, { chest: [-3, 0, 0], head: [-4, -8, 5], jaw: [0, 0, 0] }), 'linear'],
			[q * 8.4, ap(b, { chest: [-3, 0, 0], head: [-4, -8, 5], jaw: [9, 0, 0] }), 'linear'],
			[q * 10, ap(b, { chest: [0, 2, 0], head: [2, 4, -2], arm_l: [-6, 0, -3], forearm_l: [-8, 0, 0] }), 'easeinoutsine'],
			[len, ap(b, { chest: [-1, 0, 0] }), 'easeinoutsine'],
		];
	});
	gait(an, timing, 'walk', b, cfg.walk, cfg.walkSpeed);
	gait(an, timing, 'chase', ap(b, cfg.chaseLean || { waist: [8, 0, 0], chest: [6, 0, 0], head: [-12, 0, 0] }), Object.assign({}, cfg.chase, { arms: reachArms }), cfg.chaseSpeed);
	// attack (predictive): claw raised back over the shoulder, the torso twists, a raking diagonal swipe across the body
	// (the strike), follow-through low across, recover into the reaching chase pose
	const rr = reachArms();
	const ready = ap(b, rr, { chest: [4, 14, 0], waist: [2, 8, 0], head: [-14, -10, 0], jaw: [20, 0, 0], arm_r: [-150, 20, 30], forearm_r: [-40, 0, 0], arm_l: [-60, 0, -20], forearm_l: [-30, 0, 0] }, CLAW('r', 1.5), CLAW('l', 1.2));
	const hit = ap(b, rr, { chest: [18, -22, 0], waist: [8, -14, 0], head: [-18, 16, 0], jaw: [34, 0, 0], arm_r: [-56, -40, -26], forearm_r: [-10, 0, 0], arm_l: [-30, 0, -40], forearm_l: [-40, 0, 0] }, CLAW('r', 1.7), CLAW('l', 1.2));
	const follow = ap(b, rr, { chest: [22, -30, 0], waist: [10, -18, 0], head: [-14, 20, 0], jaw: [26, 0, 0], arm_r: [-20, -50, -36], forearm_r: [-20, 0, 0], arm_l: [-40, 0, -30], forearm_l: [-30, 0, 0] }, CLAW('r', 1.4), CLAW('l', 1.2));
	const endP = ap(b, rr);
	mk(an, timing, 'attack', (len, S) => [[0, endP], [Math.max(1, S * 0.75), ready, 'easeoutquad'], [S, strike(hit), 'easeincubic'], [S + 3, follow, 'easeoutquad'], [len, endP, 'easeinoutsine']]);
	mk(an, timing, 'attack_ready', len => [[0, endP], [len, ready, 'easeoutquad']]);
	mk(an, timing, 'attack_snap', (len, S) => [[0, strike(hit)], [3, follow, 'easeoutquad'], [len, endP, 'easeinoutsine']]);
	// hurt: a jolt back and to the side, the jaw drops, a recovering twitch
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-16, 10, 8], waist: [-6, 0, 0], head: [-20, -14, 10], jaw: [24, 0, 0], arm_r: [-20, 0, 20], arm_l: [-14, 0, -24] }), 'easeoutquad'],
		[5, ap(b, { chest: [-6, 4, 2], head: [-6, -4, 4], jaw: [10, 0, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	// death: knees buckle, a lurch, topple forward onto the face (17 clip ticks: the vanilla corpse goes at 20)
	const st = ap(b, { chest: [-14, 8, 6], head: [-24, 12, 0], jaw: [30, 0, 0], arm_r: [-36, 0, 30], arm_l: [-24, 0, -30], thigh_r: [-12, 0, 0], shin_r: [16, 0, 0] });
	const kneel = ap(b, { thigh_r: [-60, 0, 4], shin_r: [96, 0, 0], foot_r: [-30, 0, 0], thigh_l: [-30, 0, -4], shin_l: [70, 0, 0], foot_l: [-30, 0, 0], chest: [20, 0, 0], waist: [8, 0, 0], head: [24, 0, 0], jaw: [26, 0, 0], arm_r: [10, 0, 10], arm_l: [10, 0, -10], $ground: 'knees' });
	const fallen = ap(b, { root: [84, 0, 0], thigh_r: [-10, 0, 6], shin_r: [24, 0, 0], thigh_l: [8, 0, -6], shin_l: [10, 0, 0], chest: [4, 0, 6], head: [-10, 24, 0], arm_r: [-150, 0, 20], arm_l: [-10, 0, -60], forearm_r: [-10, 0, 0], jaw: [30, 0, 0], $ground: 'all' });
	for (const k of Object.keys(fallen)) if (/^(item|weapon|crossbow|shield)/.test(k)) delete fallen[k];
	mk(an, timing, 'death', len => [[0, b], [4, st, 'easeoutquad'], [9, kneel, 'easeinquad'], [14, fallen, 'easeinquad'], [16, ap(fallen, { root: [-5, 0, 0], $y: 0.5 }), 'easeoutquad'], [len, fallen, 'easeinquad']]);
}

// ---------------------------------------------------------------- per unit
function bossClips(an, timing, poses) {
	const b = poses.stand;
	zombieKit(an, timing, {
		base: b, reach: Object.assign({ arm_r: [-70, 0, 14], forearm_r: [-30, 0, 0], arm_l: [-60, 0, -18], forearm_l: [-36, 0, 0], jaw: [16, 0, 0] }, CLAW('r', 1.4), CLAW('l', 1.4)),
		walkSpeed: 0.075, chaseSpeed: 0.14, twitch: 0.4,
		walk: { th: 16, kc: -4, kb: 12, lift: 20, yaw: 8, lean: 3, arm: 14, wArm: 1, fa: 10, rise: 0.35, roll: 5, dip: 1.2 },
		chase: { th: 26, kc: -6, kb: 20, lift: 32, yaw: 10, lean: 8, arm: 0, wArm: 0, fa: 0, rise: 0.7, roll: 6, dip: 1.4 },
	});
	// hover (Flight): legs hanging, slow wingbeats, arms spread
	{
		const len = timing.hover.length;
		const hang = ap(b, { thigh_r: [10, 0, 6], shin_r: [30, 0, 0], foot_r: [30, 0, 0], thigh_l: [16, 0, -6], shin_l: [36, 0, 0], foot_l: [30, 0, 0], arm_r: [-20, 0, 40], arm_l: [-20, 0, -40], chest: [4, 0, 0], head: [-6, 0, 0] });
		mk(an, timing, 'hover', () => [[0, ap(hang, { wing_r: [0, -30, -30], wing_l: [0, 30, 30], $y: 0 })], [len / 2, ap(hang, { wing_r: [10, 20, 30], wing_l: [10, -20, -30], chest: [-4, 0, 0], $y: 1.2 }), 'easeinoutsine'], [len, ap(hang, { wing_r: [0, -30, -30], wing_l: [0, 30, 30], $y: 0 }), 'easeinoutsine']], { ground: false });
	}
	// telegraphed cast: rise up, gather power between the hands (held) ... then thrust it out
	const gather = ap(b, { chest: [-16, 0, 0], waist: [-6, 0, 0], head: [-20, 0, 0], jaw: [20, 0, 0], arm_r: [-60, -30, 30], forearm_r: [-70, 0, 0], arm_l: [-60, 30, -30], forearm_l: [-70, 0, 0], ...OPEN('r', 2), ...OPEN('l', 2), thigh_r: [-10, 0, 8], shin_r: [16, 0, 0], thigh_l: [-10, 0, -8], shin_l: [16, 0, 0] });
	mk(an, timing, 'cast_windup', len => [[0, b], [len * 0.5, gather, 'easeinoutsine'], [len * 0.75, ap(gather, { chest: [-4, 0, 0], head: [-4, 0, 0] }), 'easeinoutsine'], [len, gather, 'easeinoutsine']]);
	const release = ap(b, poses.cast);
	mk(an, timing, 'cast_release', (len, S) => [[0, strike(release)], [4, ap(release, { chest: [-6, 0, 0], arm_r: [-6, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	const slam = ap(b, { arm_r: [-60, 0, -14], forearm_r: [-10, 0, 0], arm_l: [-60, 0, 14], forearm_l: [-10, 0, 0], chest: [40, 0, 0], waist: [16, 0, 0], head: [-30, 0, 0], jaw: [26, 0, 0], thigh_r: [-50, 0, 10], shin_r: [70, 0, 0], foot_r: [-20, 0, 0], thigh_l: [-50, 0, -10], shin_l: [70, 0, 0], foot_l: [-20, 0, 0], ...FIST('r'), ...FIST('l') });
	mk(an, timing, 'ability_slam', (len, S) => [[0, strike(slam)], [3, ap(slam, { chest: [-4, 0, 0], $y: 0.3 }), 'easeoutquad'], [6, slam, 'easeinquad'], [len, b, 'easeinoutsine']]);
	const punch = ap(b, { chest: [10, -30, 0], waist: [4, -20, 0], head: [-10, 24, 0], arm_r: [-92, -10, -6], forearm_r: [-4, 0, 0], arm_l: [20, 0, -30], forearm_l: [-60, 0, 0], ...FIST('r'), ...FIST('l') });
	mk(an, timing, 'ability_punch', (len, S) => [[0, strike(punch)], [4, ap(punch, { chest: [4, -6, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	const burst = ap(b, { chest: [-26, 0, 0], waist: [-8, 0, 0], head: [-34, 0, 0], jaw: [40, 0, 0], arm_r: [-40, 0, 80], forearm_r: [-20, 0, 0], arm_l: [-40, 0, -80], forearm_l: [-20, 0, 0], ...OPEN('r', 2.5), ...OPEN('l', 2.5), thigh_r: [-6, 0, 14], thigh_l: [-6, 0, -14] });
	mk(an, timing, 'ability_burst', (len, S) => [[0, strike(burst)], [3, ap(burst, { head: [-2, 6, 4] }), 'easeinoutsine'], [6, ap(burst, { head: [2, -6, -4] }), 'easeinoutsine'], [9, burst, 'easeinoutsine'], [len, b, 'easeinoutsine']]);
	const dash = ap(b, { chest: [34, 0, 0], waist: [14, 0, 0], head: [-30, 0, 0], arm_r: [50, 0, 20], forearm_r: [-20, 0, 0], arm_l: [50, 0, -20], forearm_l: [-20, 0, 0], thigh_r: [-60, 0, 0], shin_r: [40, 0, 0], thigh_l: [40, 0, 0], shin_l: [60, 0, 0] });
	mk(an, timing, 'ability_dash', (len, S) => [[0, strike(dash)], [5, ap(dash, { chest: [-8, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	const shove = ap(b, { chest: [-8, 0, 0], arm_r: [-90, 0, 50], forearm_r: [-10, 0, 0], arm_l: [-90, 0, -50], forearm_l: [-10, 0, 0], ...OPEN('r', 2.4), ...OPEN('l', 2.4), head: [-14, 0, 0], jaw: [30, 0, 0], thigh_r: [-20, 0, 12], shin_r: [24, 0, 0], thigh_l: [-20, 0, -12], shin_l: [24, 0, 0] });
	mk(an, timing, 'shove', (len, S) => [[0, strike(shove)], [4, ap(shove, { chest: [4, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
}

module.exports = { bossClips, T };
