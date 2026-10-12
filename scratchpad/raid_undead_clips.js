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
function raidZombieClips(an, timing, poses) {
	zombieKit(an, timing, {
		base: poses.stand, reach: poses.reach, walkSpeed: 0.075, chaseSpeed: 0.13,
		walk: { th: 18, kc: -4, kb: 12, lift: 20, yaw: 5, lean: 3, arm: 10, wArm: 0.7, fa: 6, rise: 0.25, roll: 3, dip: 0.6, limp: 0.85 },
		chase: { th: 26, kc: -6, kb: 22, lift: 32, yaw: 8, lean: 8, arm: 0, wArm: 0, fa: 0, rise: 0.6, roll: 5, dip: 1.0, limp: 0.4 },
	});
}
function babyClips(an, timing, poses) {
	zombieKit(an, timing, {
		base: poses.stand, reach: poses.reach, walkSpeed: 0.09, chaseSpeed: 0.2, twitch: 1.3,
		walk: { th: 22, kc: -4, kb: 16, lift: 26, yaw: 8, lean: 4, arm: 16, wArm: 0.8, fa: 8, rise: 0.3, roll: 6, dip: 0.4 },
		chase: { th: 34, kc: -8, kb: 30, lift: 44, yaw: 12, lean: 14, arm: 0, wArm: 0, fa: 0, rise: 0.8, roll: 8, dip: 0.6 },
		chaseLean: { waist: [16, 0, 0], chest: [12, 0, 0], head: [-22, 0, 0] },
	});
}
function cursedClips(an, timing, poses) {
	zombieKit(an, timing, {
		base: poses.stand, reach: poses.lunge, walkSpeed: 0.08, chaseSpeed: 0.15, twitch: 1.2,
		walk: { th: 16, kc: -4, kb: 12, lift: 18, yaw: 4, lean: 2, arm: 6, wArm: 0.6, fa: 4, rise: 0.4, roll: 2, dip: 0.3 },
		chase: { th: 30, kc: -6, kb: 26, lift: 38, yaw: 6, lean: 10, arm: 0, wArm: 0, fa: 0, rise: 0.8, roll: 3, dip: 0.6 },
		chaseLean: { waist: [14, 0, 0], chest: [12, 0, 0], head: [-24, 0, 0] },
	});
}
function acidClips(an, timing, poses) {
	const b = poses.stand;
	mk(an, timing, 'idle', len => {
		const q = len / 4;
		return [
			[0, ap(b, {})],
			[q, ap(b, { chest: [-4, 0, 0], waist: [-3, 0, 0], head: [-6, 0, 0], jaw: [-6, 0, 0], throat: [-6, 0, 0] }), 'easeinoutsine'],
			[q * 1.6, ap(b, { chest: [3, 0, 0], head: [6, 0, 0], jaw: [10, 0, 0] }), 'easeinoutsine'],
			[q * 2.6, ap(b, { chest: [-2, 3, 2], hips: [0, 0, -3], head: [-2, -12, 4], arm_r: [-4, 0, 4] }), 'easeinoutsine'],
			[len, ap(b, {}), 'easeinoutsine'],
		];
	}, { scaleKeys: { sacs: [[0, 1], [15, 1.08, 'easeinoutsine'], [30, 0.97, 'easeinoutsine'], [45, 1.06, 'easeinoutsine'], [60, 1, 'easeinoutsine']], belly_sac: [[0, 1], [20, 1.1, 'easeinoutsine'], [40, 0.96, 'easeinoutsine'], [60, 1, 'easeinoutsine']] } });
	gait(an, timing, 'walk', b, { th: 14, kc: -2, kb: 10, lift: 16, yaw: 6, lean: -2, arm: 8, wArm: 0.8, fa: 6, rise: 0.15, roll: 9, dip: 1.2 }, 0.07);
	// spit: rear back while the belly heaves and the throat sac swells, lunge forward, jaw wide -- the glob leaves on hit
	const rear = ap(b, { waist: [-12, 0, 0], chest: [-18, 0, 0], head: [-26, 0, 0], jaw: [-10, 0, 0], arm_r: [20, 0, 26], forearm_r: [-40, 0, 0], arm_l: [20, 0, -26], forearm_l: [-40, 0, 0], thigh_r: [8, 0, 6], shin_r: [6, 0, 0], thigh_l: [8, 0, -6], shin_l: [6, 0, 0] });
	const heave = ap(rear, { chest: [6, 0, 0], waist: [4, 0, 0] });
	const spit = ap(b, poses.spit);
	const spitFollow = ap(spit, { chest: [8, 0, 0], head: [10, 0, 0], jaw: [-10, 0, 0] });
	const swell = (S, len) => ({ throat: [[0, 1], [Math.round(S * 0.5), 1.25, 'easeinoutsine'], [Math.max(1, S - 1), 1.55, 'easeinquad'], [S, 0.9, 'easeoutquad'], [len, 1, 'easeinoutsine']], belly_sac: [[0, 1], [Math.round(S * 0.6), 1.2, 'easeinoutsine'], [S, 0.92, 'easeoutquad'], [len, 1]] });
	mk(an, timing, 'spit', (len, S) => [[0, b], [S * 0.45, rear, 'easeinoutsine'], [S * 0.75, heave, 'easeinoutsine'], [S - 1, ap(rear, { chest: [-4, 0, 0], head: [-6, 0, 0] }), 'easeinoutsine'], [S, strike(spit), 'easeincubic'], [S + 3, spitFollow, 'easeoutquad'], [len, b, 'easeinoutsine']],
		{ scaleKeys: swell(timing.spit.hit - T, timing.spit.length - T) });
	mk(an, timing, 'spit_snap', (len, S) => [[0, strike(spit)], [3, spitFollow, 'easeoutquad'], [len, b, 'easeinoutsine']]);
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-12, 8, 8], head: [-18, -10, 8], jaw: [20, 0, 0], arm_r: [-16, 0, 20], arm_l: [-12, 0, -20] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// death: the sacs burst (they swell and vanish), it sags backward onto its rump and flops back
	const sag = ap(b, { thigh_r: [-70, 0, 14], shin_r: [80, 0, 0], thigh_l: [-70, 0, -14], shin_l: [80, 0, 0], chest: [-20, 0, 0], head: [-30, 0, 0], jaw: [30, 0, 0], arm_r: [-10, 0, 40], arm_l: [-10, 0, -40], $ground: 'all' });
	const flat = ap(b, { root: [-80, 0, 0], thigh_r: [-60, 0, 20], shin_r: [50, 0, 0], thigh_l: [-50, 0, -20], shin_l: [40, 0, 0], head: [-20, 20, 0], jaw: [40, 0, 0], arm_r: [-10, 0, 70], arm_l: [-10, 0, -70], $ground: 'all' });
	mk(an, timing, 'death', len => [[0, b], [4, ap(b, { chest: [-14, 0, 0], head: [-20, 0, 0], jaw: [30, 0, 0] }), 'easeoutquad'], [9, sag, 'easeinquad'], [14, flat, 'easeinquad'], [len, flat]],
		{ scaleKeys: { sacs: [[0, 1], [6, 1.35, 'easeinquad'], [8, 0, 'easeoutquad'], [17, 0]], belly_sac: [[0, 1], [5, 1.4, 'easeinquad'], [7, 0], [17, 0]], throat: [[0, 1], [6, 1.3], [8, 0], [17, 0]] } });
}
function juggernautClips(an, timing, poses) {
	const b = poses.stand, M = an.M;
	mk(an, timing, 'idle', len => {
		const q = len / 4;
		return [
			[0, b], [q, ap(b, { chest: [-5, 0, 0], waist: [-2, 0, 0], arm_r: [0, 0, 3], arm_l: [0, 0, -3], head: [3, 0, 0] }), 'easeinoutsine'],
			[q * 2, ap(b, { chest: [2, 6, 0], head: [-2, 16, 0], arm_r: [-6, 0, 0], forearm_r: [-10, 0, 0] }), 'easeinoutsine'],
			[q * 2.4, ap(b, { chest: [2, 6, 0], head: [-2, 16, 0], arm_r: [-6, 0, 0], forearm_r: [-10, 0, 0], arm_l: [-8, 0, -6] }), 'easeoutquad'],
			[q * 3.2, ap(b, { chest: [-4, -4, 0], head: [0, -12, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine'],
		];
	});
	gait(an, timing, 'walk', b, { th: 16, kc: -4, kb: 12, lift: 20, yaw: 9, lean: 3, arm: 14, wArm: 1, fa: 10, rise: 0.4, roll: 6, dip: 1.6 }, 0.065);
	gait(an, timing, 'chase', ap(b, { waist: [6, 0, 0], chest: [8, 0, 0] }), { th: 24, kc: -6, kb: 18, lift: 30, yaw: 11, lean: 6, arm: 22, wArm: 1, fa: 16, rise: 0.6, roll: 7, dip: 2.0 }, 0.09);
	// attack: both fists raised overhead and a double hammer-blow down
	const up = ap(b, { arm_r: [-165, 0, -6], forearm_r: [-50, 0, 0], arm_l: [-165, 0, 6], forearm_l: [-50, 0, 0], chest: [-14, 0, 0], waist: [-6, 0, 0], head: [-8, 0, 0] });
	const down = ap(b, { arm_r: [-60, 0, -14], forearm_r: [-14, 0, 0], arm_l: [-60, 0, 14], forearm_l: [-14, 0, 0], chest: [34, 0, 0], waist: [14, 0, 0], head: [-26, 0, 0] });
	const after = ap(down, { chest: [-6, 0, 0], arm_r: [10, 0, 0], arm_l: [10, 0, 0] });
	mk(an, timing, 'attack', (len, S) => [[0, b], [S * 0.7, up, 'easeinoutsine'], [S - 1, ap(up, { chest: [-4, 0, 0] }), 'easeinoutsine'], [S, strike(down), 'easeincubic'], [S + 3, after, 'easeoutquad'], [len, b, 'easeinoutsine']]);
	mk(an, timing, 'attack_ready', len => [[0, b], [len, ap(up, { arm_r: [40, 0, 0], arm_l: [40, 0, 0] }), 'easeoutquad']]);
	mk(an, timing, 'attack_snap', (len, S) => [[0, strike(down)], [3, after, 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// charge: the tell (head down, a foot pawing, fists back, held), the rush loop, the impact
	const set = ap(b, { chest: [24, 0, 0], waist: [10, 0, 0], head: [-14, 0, 0], arm_r: [40, 0, 14], forearm_r: [-50, 0, 0], arm_l: [40, 0, -14], forearm_l: [-50, 0, 0], thigh_l: [-26, 0, -4], shin_l: [36, 0, 0], thigh_r: [18, 0, 4], shin_r: [16, 0, 0], foot_r: [-26, 0, 0] });
	mk(an, timing, 'charge_windup', len => [[0, b], [len * 0.3, set, 'easeinoutsine'], [len * 0.5, ap(set, { thigh_r: [-30, 0, 0], shin_r: [40, 0, 0], foot_r: [10, 0, 0], $y: 0.4 }), 'easeoutquad'], [len * 0.68, set, 'easeinquad'], [len * 0.84, ap(set, { thigh_r: [-24, 0, 0], shin_r: [34, 0, 0] }), 'easeoutquad'], [len, set, 'easeinquad']]);
	{
		const len = timing.charge.length, q = len / 4;
		const lean = { chest: [28, 0, 0], waist: [12, 0, 0], head: [-18, 0, 0], arm_r: [-40, 0, 20], forearm_r: [-80, 0, 0], arm_l: [-40, 0, -20], forearm_l: [-80, 0, 0] };
		const c1 = ap(b, lean, { thigh_r: [-50, 0, 0], shin_r: [10, 0, 0], foot_r: [30, 0, 0], thigh_l: [40, 0, 0], shin_l: [40, 0, 0], foot_l: [-30, 0, 0] });
		const p1 = ap(b, lean, { thigh_r: [0, 0, 0], shin_r: [20, 0, 0], thigh_l: [-50, 0, 0], shin_l: [90, 0, 0], $y: 1.0 });
		mk(an, timing, 'charge', () => [[0, c1], [q, p1, 'easeinoutsine'], [2 * q, ap(b, lean, mirP({ thigh_r: [-50, 0, 0], shin_r: [10, 0, 0], foot_r: [30, 0, 0], thigh_l: [40, 0, 0], shin_l: [40, 0, 0], foot_l: [-30, 0, 0] })), 'easeinoutsine'],
			[3 * q, ap(b, lean, mirP({ thigh_r: [0, 0, 0], shin_r: [20, 0, 0], thigh_l: [-50, 0, 0], shin_l: [90, 0, 0] }), { $y: 1.0 }), 'easeinoutsine'], [len, c1, 'easeinoutsine']], { speed: 0.72 });
	}
	const impact = ap(b, { chest: [-10, 0, 0], waist: [-6, 0, 0], head: [-30, 0, 0], arm_r: [-100, 0, 30], forearm_r: [-20, 0, 0], arm_l: [-100, 0, -30], forearm_l: [-20, 0, 0], thigh_r: [-40, 0, 6], shin_r: [30, 0, 0], thigh_l: [20, 0, -6], shin_l: [30, 0, 0] });
	mk(an, timing, 'charge_impact', (len, S) => [[0, strike(impact)], [4, ap(impact, { chest: [16, 0, 0], head: [10, 0, 0] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-8, 6, 4], head: [-12, -8, 6], arm_r: [-8, 0, 10], arm_l: [-6, 0, -10] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	const kneel = ap(b, { thigh_r: [-70, 0, 4], shin_r: [100, 0, 0], foot_r: [-30, 0, 0], thigh_l: [-20, 0, -4], shin_l: [80, 0, 0], foot_l: [-50, 0, 0], chest: [26, 0, 0], head: [20, 0, 0], arm_r: [-20, 0, 20], arm_l: [-20, 0, -20], $ground: 'knees' });
	const fallen = ap(b, { root: [86, 0, 0], thigh_r: [-10, 0, 6], shin_r: [20, 0, 0], thigh_l: [6, 0, -6], shin_l: [10, 0, 0], chest: [4, 0, 0], head: [-10, 20, 0], arm_r: [-20, 0, 60], arm_l: [-20, 0, -60], $ground: 'all' });
	mk(an, timing, 'death', len => [[0, b], [5, ap(b, { chest: [-10, 0, 0], head: [-20, 0, 0], arm_r: [-30, 0, 20], arm_l: [-30, 0, -20] }), 'easeoutquad'], [10, kneel, 'easeinquad'], [15, fallen, 'easeinquad'], [len, fallen]]);
}
function skeletonClips(an, timing, poses) {
	const b = poses.stand, M = an.M;
	const aim = (p, dir) => L.aimBone(M, p, 'weapon_r', [0, 0, -1], dir);
	// idle: an eerie stillness, a rattle, the skull turning, the sword point tapping the ground
	mk(an, timing, 'idle', len => {
		const q = len / 6;
		const look = aim(ap(b, { head: [0, 30, 0], chest: [0, 6, 0], jaw: [6, 0, 0] }), [0.1, -0.35, -1]);
		const look2 = aim(ap(b, { head: [-6, -26, 4], chest: [0, -4, 0] }), [0.1, -0.35, -1]);
		return [[0, b], [q, look, 'easeinoutsine'], [q * 1.5, ap(look, { jaw: [-4, 0, 0] }), 'linear'], [q * 1.7, look, 'linear'], [q * 3, b, 'easeinoutsine'],
			[q * 3.3, ap(b, { chest: [2, 0, 3], arm_l: [0, 0, -4] }), 'linear'], [q * 3.5, ap(b, { chest: [-1, 0, -2] }), 'linear'], [q * 3.7, b, 'linear'],
			[q * 4.4, look2, 'easeinoutsine'], [len, b, 'easeinoutsine']];
	});
	const carry = aim(ap(b, { arm_r: [-10, 0, 0], forearm_r: [-20, 0, 0] }), [0.1, 0.1, -1]);
	gait(an, timing, 'walk', b, { th: 20, kc: -4, kb: 14, lift: 24, yaw: 6, lean: 2, arm: 14, wArm: 0.15, fa: 6, rise: 0.4, roll: 2, dip: 0.3 }, 0.1);
	gait(an, timing, 'chase', ap(carry, { waist: [10, 0, 0], chest: [8, 0, 0], head: [-14, 0, 0] }), { th: 32, kc: -8, kb: 28, lift: 42, yaw: 8, lean: 10, arm: 24, wArm: 0.1, fa: 12, rise: 0.9, roll: 3, dip: 0.5 }, 0.18);
	// slash A: a rising diagonal from low right; slash B: a flat backhand from the left -- alternated by the server
	const guard = aim(ap(b, { arm_r: [-60, -10, 10], forearm_r: [-40, 0, 0], arm_l: [-60, 30, -10], forearm_l: [-60, 0, 0], chest: [0, 10, 0], head: [-4, -10, 0] }), [0.2, 0.4, -1]);
	const aWind = aim(ap(b, { chest: [6, 34, 0], waist: [2, 20, 0], head: [-8, -30, 0], arm_r: [10, 40, 30], forearm_r: [-20, 0, 0], arm_l: [-60, 30, -10], forearm_l: [-60, 0, 0] }), [-0.6, -0.6, 0.3]);
	const aHit = aim(ap(b, { chest: [-4, -30, 0], waist: [0, -18, 0], head: [-6, 26, 0], arm_r: [-100, -30, -10], forearm_r: [-10, 0, 0], arm_l: [-40, 10, -30], forearm_l: [-60, 0, 0] }), [0.6, 0.12, -1]);
	const aFol = aim(ap(aHit, { chest: [-4, -8, 0], arm_r: [-50, -10, 0] }), [0.8, 0.8, -0.2]);
	const bWind = aim(ap(b, { chest: [4, -40, 0], waist: [2, -22, 0], head: [-6, 34, 0], arm_r: [-80, -60, -60], forearm_r: [-60, 0, 0], arm_l: [-50, 10, -30], forearm_l: [-60, 0, 0] }), [0.9, 0.1, 0.4]);
	const bHit = aim(ap(b, { chest: [6, 30, 0], waist: [2, 20, 0], head: [-8, -24, 0], arm_r: [-90, 60, 10], forearm_r: [-6, 0, 0], arm_l: [-40, 0, -40], forearm_l: [-60, 0, 0] }), [-0.55, 0.05, -1]);
	const bFol = aim(ap(bHit, { chest: [0, 10, 0], arm_r: [0, 14, 0] }), [-1, -0.1, -0.1]);
	mk(an, timing, 'slash_a', (len, S) => [[0, guard], [S * 0.7, aWind, 'easeoutquad'], [S, strike(aHit), 'easeincubic'], [S + 3, aFol, 'easeoutquad'], [len, guard, 'easeinoutsine']]);
	mk(an, timing, 'slash_b', (len, S) => [[0, guard], [S * 0.7, bWind, 'easeoutquad'], [S, strike(bHit), 'easeincubic'], [S + 3, bFol, 'easeoutquad'], [len, guard, 'easeinoutsine']]);
	mk(an, timing, 'attack_ready', len => [[0, b], [len, aWind, 'easeoutquad']]);
	mk(an, timing, 'slash_snap', (len, S) => [[0, strike(aHit)], [3, aFol, 'easeoutquad'], [len, guard, 'easeinoutsine']]);
	// block: the shield brought across in front, sword cocked behind it
	const block = aim(ap(b, { arm_l: [-80, 60, -10], forearm_l: [-70, 0, 0], arm_r: [-40, -20, 20], forearm_r: [-70, 0, 0], chest: [6, 16, 0], head: [-10, -14, 0], waist: [4, 0, 0] }), [0.2, 0.7, -0.6]);
	mk(an, timing, 'block', len => [[0, b], [len, block, 'easeoutquad']]);
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-14, 10, 8], head: [-20, -16, 12], jaw: [20, 0, 0], arm_l: [-10, 0, -20] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	// death: it falls apart -- the skull drops, the knees go, the bones collapse into a heap
	const heap = ap(b, { thigh_r: [-90, 0, 30], shin_r: [140, 0, 0], thigh_l: [-90, 0, -30], shin_l: [140, 0, 0], waist: [30, 0, 0], chest: [40, 0, 20], head: [60, 30, 30], jaw: [40, 0, 0], arm_r: [-30, 0, 80], arm_l: [-30, 0, -80], $ground: 'all' });
	mk(an, timing, 'death', len => [[0, b], [3, ap(b, { head: [-30, 0, 0], jaw: [40, 0, 0], chest: [-10, 0, 0] }), 'easeoutquad'], [10, ap(heap, { $y: 2 }), 'easeinquad'], [13, heap, 'easeinquad'], [15, ap(heap, { $y: 0.4, chest: [-6, 0, 0] }), 'easeoutquad'], [len, heap, 'easeinquad']]);
}
function spyClips(an, timing, poses) {
	const b = poses.stand, M = an.M;
	const cbAim = (p, dir) => L.aimBone(M, p, 'crossbow', [0, 0, -1], dir);
	// idle: watchful; around the middle he raises the spyglass to his eye for a long look, then stows it
	const look = ap(b, { arm_l: [-140, 34, -10], forearm_l: [-80, 0, 0], head: [-4, 12, 0], chest: [-2, 8, 0], ...FIST('l') });
	const loopLen = timing.idle.length;
	mk(an, timing, 'idle', len => {
		const s = [[0, b], [10, ap(b, { head: [0, -20, 0] }), 'easeinoutsine'], [22, ap(b, { head: [0, 18, 0] }), 'easeinoutsine'], [30, b, 'easeinoutsine'], [38, look, 'easeinoutsine'],
			[52, ap(look, { head: [0, 14, 0], chest: [0, 6, 0], arm_l: [0, 8, 0] }), 'easeinoutsine'], [64, ap(look, { head: [0, -4, 0], chest: [0, -2, 0], arm_l: [0, -4, 0] }), 'easeinoutsine'],
			[74, b, 'easeinoutsine'], [86, ap(b, { head: [0, -10, 0], chest: [0, -4, 0] }), 'easeinoutsine'], [len, b, 'easeinoutsine']];
		return s.map(([t, p, e]) => [t * len / 100, p, e]);
	}, { scaleKeys: { spyglass_hand: [[0, 0], [33, 0], [34, 1], [74, 1], [75, 0], [loopLen, 0]].map(([t, v]) => [t * loopLen / 100, v]), spyglass_belt: [[0, 1], [33, 1], [34, 0], [74, 0], [75, 1], [loopLen, 1]].map(([t, v]) => [t * loopLen / 100, v]) } });
	// walk: a sneaking creep -- low, knees bent, long careful steps, the head scanning
	const crouch = ap(b, { hips: [0, 0, 0], waist: [10, 0, 0], chest: [6, 0, 0], head: [-14, 0, 0], thigh_r: [-24, 0, 0], shin_r: [40, 0, 0], foot_r: [-16, 0, 0], thigh_l: [-24, 0, 0], shin_l: [40, 0, 0], foot_l: [-16, 0, 0] });
	gait(an, timing, 'walk', crouch, { th: 22, kc: 10, kb: 22, lift: 18, yaw: 3, lean: 2, arm: 6, wArm: 0.1, fa: 4, rise: 0.1, roll: 2, dip: 0.2 }, 0.09, { c: { head: [0, 14, 0] }, mc: { head: [0, -14, 0] } });
	gait(an, timing, 'run', ap(b, { waist: [6, 0, 0], chest: [4, 0, 0], head: [-10, 0, 0] }), { th: 34, kc: -8, kb: 30, lift: 46, yaw: 8, lean: 6, arm: 30, wArm: 0.1, fa: 30, rise: 0.9, roll: 2, dip: 0.3 }, 0.2);
	// crossbow: braced against the ground with a foot, the string drawn back two-handed (held while charging)
	const draw1 = cbAim(ap(b, { chest: [30, 0, 0], waist: [10, 0, 0], head: [-10, 0, 0], arm_r: [-30, 0, 10], forearm_r: [-30, 0, 0], arm_l: [-60, 10, -10], forearm_l: [-20, 0, 0], ...CLAW('l', 2) }), [0, -1, -0.2]);
	const draw2 = cbAim(ap(b, { chest: [16, 0, 0], waist: [6, 0, 0], head: [-6, 0, 0], arm_r: [-30, 0, 10], forearm_r: [-30, 0, 0], arm_l: [-20, 30, -20], forearm_l: [-90, 0, 0], ...FIST('l') }), [0, -1, -0.2]);
	mk(an, timing, 'crossbow_charge', len => [[0, b], [len * 0.3, draw1, 'easeinoutsine'], [len * 0.75, draw2, 'easeinoutsine'], [len, cbAim(ap(b, { arm_r: [-50, 0, 6], forearm_r: [-40, 0, 0] }), [0, -0.3, -1]), 'easeinoutsine']]);
	const aim = ap(poses.aim, {});
	mk(an, timing, 'crossbow_aim', len => [[0, b], [len, aim, 'easeoutquad']]);
	const kick = cbAim(ap(aim, { arm_r: [-14, 0, 0], arm_l: [-10, 0, 0], chest: [-8, 0, 0], head: [-6, 0, 0] }), [0, 0.35, -1]);
	mk(an, timing, 'crossbow_fire', (len, S) => [[0, strike(kick)], [4, ap(aim, { chest: [-3, 0, 0] }), 'easeoutquad'], [len, aim, 'easeinoutsine']]);
	mk(an, timing, 'hurt', len => [[0, b], [2, ap(b, { chest: [-12, 10, 8], head: [-16, -12, 8], arm_l: [-10, 0, -24] }), 'easeoutquad'], [len, b, 'easeinoutsine']]);
	const kneel = ap(b, { thigh_r: [-70, 0, 4], shin_r: [100, 0, 0], foot_r: [-30, 0, 0], thigh_l: [-10, 0, -4], shin_l: [80, 0, 0], foot_l: [-60, 0, 0], chest: [10, 0, 0], head: [20, 0, 0], arm_r: [10, 0, 10], arm_l: [-40, 0, -10], forearm_l: [-80, 0, 0], $ground: 'knees' });
	const fallen = ap(b, { root: [-84, 0, 0], thigh_r: [-20, 0, 10], shin_r: [30, 0, 0], thigh_l: [-30, 0, -10], shin_l: [50, 0, 0], head: [10, -30, 0], arm_r: [-10, 0, 70], arm_l: [-10, 0, -60], $ground: 'all' });
	mk(an, timing, 'death', len => [[0, b], [4, ap(b, { chest: [-16, 0, 0], head: [-20, 0, 0] }), 'easeoutquad'], [9, kneel, 'easeinquad'], [14, fallen, 'easeinquad'], [len, fallen]]);
}
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

module.exports = { raidZombieClips, babyClips, cursedClips, acidClips, juggernautClips, skeletonClips, spyClips, bossClips, T };
