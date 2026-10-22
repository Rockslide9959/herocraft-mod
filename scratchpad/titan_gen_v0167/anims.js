// v0.16.7 Titan Shifting revamp: every Titan clip, authored as whole-body poses and written per type.
// A frame is [time, pose, opts]; pose keys are bone names (rotation offsets, degrees) plus `root` (position, px) and
// `rootScale`. By default each frame's root height is SOLVED so the lowest foot (or knee / hand with ground:'all')
// rests on the ground -- no floating feet, nothing sinking through the floor. opts.lift raises a solved frame (flight
// phases); opts.air skips the solve. opts.ease sets the GeckoLib easing INTO that frame (default catmullrom).
// Strike timings match the server: punch 0.3 s, kick 0.35, heavy_punch 0.45, smash 1.0, stomp 0.5, leap launch 0.3,
// roar 0.5, bite 0.35, throw release 0.6, roundhouse 0.4.
'use strict';
const L = require('./lib_concept_models.js');
const { SKIN_E } = require('./rig.js');

const ALIAS = { b: 'body', h: 'head', j: 'jaw', ra: 'right_arm', la: 'left_arm', rf: 'right_forearm', lf: 'left_forearm',
	rl: 'right_leg', ll: 'left_leg', rs: 'right_shin', ls: 'left_shin', rt: 'right_foot', lt: 'left_foot', fl: 'nape_flap_l', fr: 'nape_flap_r' };
const ALL_BONES = ['body', 'head', 'jaw', 'right_arm', 'left_arm', 'right_forearm', 'left_forearm', 'right_leg', 'left_leg', 'right_shin', 'left_shin', 'right_foot', 'left_foot'];

const SKINNY = c => (c.layer === 2 || c.mat === 'mus') && !c.corpseOnly && !c.stump;
const FEET = c => SKINNY(c) && /_(leg|shin|foot)$/.test(c.bone);
const BODY = c => SKINNY(c);

function expand(pose) {
	const o = {};
	for (const [k, v] of Object.entries(pose)) o[ALIAS[k] || k] = typeof v === 'number' && k !== 'rootScale' ? [v, 0, 0] : v;
	return o;
}

function rotPose(pose) {
	const r = {};
	for (const [k, v] of Object.entries(pose)) if (k !== 'root' && k !== 'rootScale') r[k] = v;
	return r;
}

function groundY(M, pose, rootXZ, filter) {
	const W = M.world(rotPose(pose), [rootXZ[0], 0, rootXZ[2]]);
	return -SKIN_E - M.bounds(W, filter).lo[1];
}

function minY(M, pose, root, filter) {
	return M.bounds(M.world(rotPose(pose), root), filter).lo[1];
}

/** Bend each listed side's elbow (and a little of the shoulder) until that hand rests on the ground. */
function plantHands(M, pose, root, sides) {
	for (const side of sides) {
		const fa = `${side}_forearm`, ua = `${side}_arm`;
		const hand = c => SKINNY(c) && c.bone === fa && c.tag === 'hand';
		let best = null;
		const base = (pose[ua] || [0, 0, 0]).slice();
		const tryIt = (da, f) => {
			const q = Object.assign({}, pose, { [ua]: [base[0] + da, base[1], base[2]], [fa]: [f, (pose[fa] || [0, 0, 0])[1], (pose[fa] || [0, 0, 0])[2]] });
			const m = minY(M, q, root, hand);
			const cost = Math.abs(m + SKIN_E) * 10 + Math.abs(da) * 0.02 + Math.abs(f + 25) * 0.005;
			if (!best || cost < best.cost) best = { cost, da, f };
		};
		for (let da = -30; da <= 30; da += 6) for (let f = -110; f <= 10; f += 6) tryIt(da, f);
		const b0 = best;
		for (let da = b0.da - 5; da <= b0.da + 5; da += 1) for (let f = b0.f - 5; f <= b0.f + 5; f += 1) tryIt(da, f);
		pose[ua] = [base[0] + best.da, base[1], base[2]];
		pose[fa] = [best.f, (pose[fa] || [0, 0, 0])[1], (pose[fa] || [0, 0, 0])[2]];
	}
}

function write(M, len, loop, frames, opts = {}) {
	const bones = new Set();
	const ex = frames.map(([t, p, fo]) => [t, expand(p), fo || {}]);
	for (const [, p] of ex) for (const k of Object.keys(p)) if (k !== 'root' && k !== 'rootScale') bones.add(k);
	if (opts.allBones) ALL_BONES.forEach(b => bones.add(b));
	const out = {};
	const kf = (t, v, ease) => [t.toFixed(3), ease === 'linear' ? v : { vector: v, easing: ease }];
	const R3 = v => v.map(x => Math.round(x * 100) / 100);
	const rootPos = [];
	const hasRoot = ex.some(([, p, fo]) => p.root || opts.ground !== false || fo.lift);
	for (const [t, p, fo] of ex) {
		const ease = fo.ease || opts.ease || 'catmullrom';
		const full = {};
		for (const b of bones) full[b] = p[b] || [0, 0, 0];
		const r = (p.root || [0, 0, 0]).slice();
		const grounded = !(fo.air || opts.ground === false);
		if (grounded) {
			const filt = (fo.ground || opts.groundMode) === 'all' && !fo.hands ? BODY : FEET;
			r[1] = groundY(M, full, r, filt) + (fo.lift || 0) + (p.root ? p.root[1] : 0);
		}
		if (fo.hands) plantHands(M, full, r, fo.hands);
		for (const b of bones) {
			out[b] = out[b] || { rotation: {} };
			const [k, v] = kf(t, R3(full[b]), ease);
			out[b].rotation[k] = v;
		}
		rootPos.push([t, R3(r), ease]);
		if (p.rootScale != null) {
			out.root = out.root || {};
			out.root.scale = out.root.scale || {};
			const [k, v] = kf(t, [p.rootScale, p.rootScale, p.rootScale], ease);
			out.root.scale[k] = v;
		}
	}
	if (hasRoot) {
		out.root = out.root || {};
		out.root.position = {};
		for (const [t, v, ease] of rootPos) { const [k, vv] = kf(t, v, ease); out.root.position[k] = vv; }
	}
	const a = { animation_length: len, bones: out };
	if (loop === true) a.loop = true; else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	return a;
}

/** The final keyed pose of a written clip, as a frame pose (rotations + root position). */
function lastPose(clip) {
	const pose = {};
	for (const [bn, ch] of Object.entries(clip.bones)) {
		const pick = o => { const ks = Object.keys(o).sort((x, y) => +x - +y); const v = o[ks[ks.length - 1]]; return Array.isArray(v) ? v : v.vector; };
		if (ch.rotation) pose[bn] = pick(ch.rotation);
		if (bn === 'root' && ch.position) pose.root = pick(ch.position);
	}
	return pose;
}

function animations(M) {
	const id = M.id;
	const A = {};
	const clip = (name, len, loop, frames, opts) => { A['animation.titan.' + name] = write(M, len, loop, frames, opts); };
	const beast = id === 'titan_beast', armored = id === 'titan_armored', female = id === 'titan_female';
	const hb = beast ? 1 : 0;
	const ARM = 4; // resting arm swing-out (deg)
	const rest = { b: 0, h: 0, ra: [3, 0, ARM], la: [3, 0, -ARM], rf: -8, lf: -8 };
	const R = (o) => Object.assign({}, rest, o);

	// ---------------- idle: slow heavy breathing
	clip('idle', 4, true, [
		[0, R({ b: [2, 0, 0], h: [-1, 0, 0] })],
		[1.6, R({ b: [4.5, 0, 0], h: [-2.5, 3, 0], ra: [6, 0, ARM + 2], la: [6, 0, -ARM - 2], rf: -12, lf: -12, rl: [0, 0, 0.5], ll: [0, 0, -0.5] })],
		[2.8, R({ b: [3.5, 0, 0], h: [-1, 1, 0], ra: [5, 0, ARM + 1], la: [5, 0, -ARM - 1], rf: -10, lf: -10 })],
		[4, R({ b: [2, 0, 0], h: [-1, 0, 0] })],
	]);

	// ---------------- walk / run
	{ // v0.16.7 (user skins): every Titan walks on its feet now -- the Beast's player-length arms no longer reach the
		// ground, so its knuckle-walk became a heavy hunched walk (the rig's slight hunch plus a deeper lean, big arm swing)
		const lean = armored ? 6 : beast ? 9 : 4, sw = armored ? 22 : female ? 28 : beast ? 24 : 26, asw = armored ? 16 : female ? 22 : beast ? 28 : 20;
		const step = (s) => ({ // s = +1: right leg forward
			b: [lean, 5 * s, 0], h: [-lean * 0.5, -4 * s, 0],
			rl: [-sw * s, 0, 0], rs: s > 0 ? 6 : 26, ll: [sw * s, 0, 0], ls: s > 0 ? 26 : 6,
			rt: s > 0 ? -8 : 14, lt: s > 0 ? 14 : -8,
			ra: [asw * s, 0, ARM], la: [-asw * s, 0, -ARM], rf: s > 0 ? -10 : -26, lf: s > 0 ? -26 : -10,
		});
		const pass = (s) => ({
			b: [lean + 1, 0, 0], h: [-lean * 0.5, 0, 0],
			rl: [s > 0 ? 6 : -10, 0, 0], rs: s > 0 ? 4 : 52, ll: [s > 0 ? -10 : 6, 0, 0], ls: s > 0 ? 52 : 4,
			ra: [0, 0, ARM], la: [0, 0, -ARM], rf: -16, lf: -16,
		});
		clip('walk', 1.2, true, [[0, step(1)], [0.3, pass(-1)], [0.6, step(-1)], [0.9, pass(1)], [1.2, step(1)]]);
		const rl = armored ? 18 : beast ? 20 : 14, fl = armored ? 0.35 : female ? 1.0 : beast ? 0.6 : 0.8;
		const rstep = (s) => ({
			b: [rl, 7 * s, 0], h: [-rl * 0.55, -5 * s, 0],
			rl: [-46 * s, 0, 0], rs: s > 0 ? 14 : 64, ll: [36 * s, 0, 0], ls: s > 0 ? 64 : 14, rt: s > 0 ? -12 : 20, lt: s > 0 ? 20 : -12,
			ra: [44 * s, 0, ARM + 4], la: [-44 * s, 0, -ARM - 4], rf: s > 0 ? -50 : -78, lf: s > 0 ? -78 : -50,
		});
		const rfly = (s) => ({
			b: [rl + 2, 0, 0], h: [-rl * 0.55, 0, 0],
			rl: [s > 0 ? -14 : -24, 0, 0], rs: s > 0 ? 24 : 96, ll: [s > 0 ? -24 : -14, 0, 0], ls: s > 0 ? 96 : 24,
			ra: [0, 0, ARM + 4], la: [0, 0, -ARM - 4], rf: -64, lf: -64,
		});
		clip('run', 0.8, true, [[0, rstep(1)], [0.2, rfly(-1), { lift: fl }], [0.4, rstep(-1)], [0.6, rfly(1), { lift: fl }], [0.8, rstep(1)]]);
	}

	// ---------------- strikes
	clip('punch', 0.6, false, [
		[0, R({})],
		[0.15, R({ ra: [52, 0, 8], rf: -70, la: [-30, 0, -ARM], lf: -40, b: [-4, -24, 0], h: [0, 18, 0], rl: [6, 0, 0], ll: [-8, 0, 0], ls: 10 })],
		[0.3, R({ ra: [-90, 0, -4], rf: -2, la: [28, 0, -ARM], lf: -30, b: [12, 26, 0], h: [-6, -20, 0], rl: [16, 0, 0], rs: 18, ll: [-20, 0, 0], ls: 8 }), { ease: 'easeInQuad' }],
		[0.42, R({ ra: [-80, 0, -2], rf: -8, la: [24, 0, -ARM], lf: -30, b: [10, 22, 0], h: [-4, -16, 0], rl: [14, 0, 0], rs: 14, ll: [-18, 0, 0] })],
		[0.6, R({})],
	]);
	const kh = female ? -104 : armored ? -70 : -88;
	clip('kick', 0.7, false, [
		[0, R({})],
		[0.2, R({ rl: [30, 0, 0], rs: 70, ll: [-6, 0, 0], ls: 14, b: [8, 0, 0], ra: [10, 0, 14], la: [-24, 0, -14], lf: -30 })],
		[0.35, R({ rl: [kh, 0, 0], rs: 4, rt: -20, ll: [8, 0, 0], ls: 10, b: [-16, 0, 0], h: [8, 0, 0], ra: [30, 0, 18], la: [-40, 0, -16], lf: -40 }), { ease: 'easeInQuad' }],
		[0.5, R({ rl: [kh * 0.7, 0, 0], rs: 30, ll: [6, 0, 0], ls: 8, b: [-10, 0, 0], ra: [20, 0, 14], la: [-30, 0, -14] })],
		[0.7, R({})],
	]);
	clip('heavy_punch', 0.9, false, [
		[0, R({})],
		[0.25, R({ ra: [-160, 0, 10], rf: -70, la: [-44, 0, -ARM], lf: -30, b: [-16, -14, 0], h: [-8, 10, 0], rl: [6, 0, 0], ll: [-10, 0, 0] })],
		[0.45, R({ ra: [-88, 0, -6], rf: -4, la: [26, 0, -ARM], lf: -20, b: [26, 18, 0], h: [6, -12, 0], rl: [16, 0, 0], rs: 20, ll: [-28, 0, 0], ls: 14 }), { ease: 'easeInQuad' }],
		[0.62, R({ ra: [-70, 0, -4], rf: -10, b: [22, 14, 0], rl: [14, 0, 0], ll: [-24, 0, 0] })],
		[0.9, R({})],
	]);
	clip('smash', 1.5, false, [
		[0, R({})],
		[0.5, R({ ra: [-122, 0, 0], la: [-122, 0, 0], rf: -40, lf: -40, b: [-6, 0, 0], h: [-6, 0, 0] })],
		[0.85, R({ ra: [-172, 0, -6], la: [-172, 0, 6], rf: -30, lf: -30, b: [-18, 0, 0], h: [-14, 0, 0], rl: [-14, 0, 0], ll: [-14, 0, 0], rs: 10, ls: 10 })],
		[1.0, R({ ra: [-96, 0, -4], la: [-96, 0, 4], rf: -6, lf: -6, b: [40, 0, 0], h: [14, 0, 0], rl: [-40, 0, 0], ll: [-40, 0, 0], rs: 56, ls: 56 }), { ease: 'easeInQuad', ground: 'all' }],
		[1.15, R({ ra: [-60, 0, 0], la: [-60, 0, 0], rf: -10, lf: -10, b: [36, 0, 0], h: [10, 0, 0], rl: [-36, 0, 0], ll: [-36, 0, 0], rs: 50, ls: 50 }), { ground: 'all' }],
		[1.5, R({})],
	]);
	clip('stomp', 0.9, false, [
		[0, R({})],
		[0.3, R({ rl: [-76, 0, 0], rs: 60, ll: [4, 0, 0], ls: 8, b: [-10, 0, 0], ra: [-40, 0, 10], la: [-40, 0, -10], rf: -30, lf: -30, h: [-6, 0, 0] })],
		[0.5, R({ rl: [-6, 0, 0], rs: 8, ll: [6, 0, 0], ls: 14, b: [12, 0, 0], ra: [14, 0, 12], la: [14, 0, -12], h: [10, 0, 0] }), { ease: 'easeInQuad' }],
		[0.9, R({})],
	]);
	clip('leap', 1.4, false, [
		[0, R({})],
		[0.3, R({ rl: [-62, 0, 0], ll: [-62, 0, 0], rs: 92, ls: 92, rt: -20, lt: -20, b: [36, 0, 0], h: [-14, 0, 0], ra: [44, 0, 10], la: [44, 0, -10], rf: -20, lf: -20 })],
		[0.5, R({ rl: [14, 0, 0], ll: [8, 0, 0], rs: 10, ls: 14, rt: 20, lt: 20, b: [-8, 0, 0], h: [-14, 0, 0], ra: [-128, 0, 6], la: [-128, 0, -6], rf: -10, lf: -10 }), { ease: 'easeOutQuad', lift: 3 }],
		[0.9, R({ rl: [-30, 0, 0], ll: [-12, 0, 0], rs: 60, ls: 40, b: [6, 0, 0], ra: [-72, 0, 10], la: [-72, 0, -10], rf: -30, lf: -30 }), { air: true }],
		[1.4, R({ rl: [-20, 0, 0], ll: [-20, 0, 0], rs: 30, ls: 30, b: [6, 0, 0], ra: [-30, 0, 10], la: [-30, 0, -10] }), { air: true }],
	]);
	clip('landing', 0.7, false, [
		[0, R({ rl: [-50, 0, 0], ll: [-50, 0, 0], rs: 80, ls: 80, b: [28, 0, 0], h: [4, 0, 0], ra: [-24, 0, 14], la: [-24, 0, -14] })],
		[0.15, R({ rl: [-66, 0, 0], ll: [-66, 0, 0], rs: 110, ls: 110, rt: -30, lt: -30, b: [38, 0, 0], h: [-6, 0, 0], ra: [10, 0, 18], la: [10, 0, -18], rf: -30, lf: -30 }), { ease: 'easeOutQuad' }],
		[0.7, R({})],
	]);
	const shake = (base, t0, n, dt, amp) => Array.from({ length: n }, (_, i) => [t0 + i * dt, Object.assign({}, base, { h: [base.h[0], (i % 2 ? amp : -amp), 0] }), { ease: 'linear' }]);
	const roarPose = R({ h: [-34, 0, 0], j: [34, 0, 0], b: [-14, 0, 0], ra: [24, 0, 22], la: [24, 0, -22], rf: -40, lf: -40, rl: [-4, 0, 3], ll: [4, 0, -3] });
	clip('roar', 1.6, false, [
		[0, R({})],
		[0.4, roarPose],
		...shake(roarPose, 0.5, 12, 0.05, 4),
		[1.15, roarPose],
		[1.6, R({})],
	]);
	clip('hurt', 0.45, false, [
		[0, R({})],
		[0.1, R({ b: [-12, 0, 4], h: [-16, 6, 0], ra: [18, 0, 14], la: [18, 0, -14], rf: -30, lf: -30, j: [10, 0, 0] }), { ease: 'easeOutQuad' }],
		[0.45, R({})],
	]);
	clip('regeneration', 2.0, false, [
		[0, R({})],
		[0.3, R({ ra: [-58, 0, -20], la: [-58, 0, 20], rf: -90, lf: -90, b: [14, 0, 0], h: [18, 0, 0], rl: [-12, 0, 0], ll: [-12, 0, 0], rs: 18, ls: 18 })],
		[1.0, R({ ra: [-60, 0, -22], la: [-60, 0, 22], rf: -94, lf: -94, b: [16, 0, 0], h: [20, 0, 0], rl: [-14, 0, 0], ll: [-14, 0, 0], rs: 22, ls: 22 })],
		[1.7, R({ ra: [-58, 0, -20], la: [-58, 0, 20], rf: -90, lf: -90, b: [14, 0, 0], h: [18, 0, 0], rl: [-12, 0, 0], ll: [-12, 0, 0], rs: 18, ls: 18 })],
		[2.0, R({})],
	]);
	clip('hardening', 1.1, false, [
		[0, R({})],
		[0.35, R({ ra: [-48, 0, 0], la: [-48, 0, 0], rf: -50, lf: -50, b: [-8, 0, 0], h: [-10, 0, 0], rl: [-10, 0, 4], ll: [8, 0, -4], rs: 10 })],
		[0.85, R({ ra: [-50, 0, 0], la: [-50, 0, 0], rf: -54, lf: -54, b: [-7, 0, 0], h: [-10, 0, 0], rl: [-10, 0, 4], ll: [8, 0, -4], rs: 10 })],
		[1.1, R({})],
	]);
	clip('grab', 0.7, false, [
		[0, R({})],
		[0.25, R({ ra: [-96, 0, 4], rf: -10, la: [-30, 0, -ARM], lf: -20, b: [18, 0, 0], h: [12, 0, 0], rl: [-16, 0, 0], rs: 20, ll: [6, 0, 0] })],
		[0.7, R({ ra: [-72, 0, 0], rf: -30 })],
	]);
	clip('bite', 1.0, false, [
		[0, R({ ra: [-72, 0, 0], rf: -30 })],
		[0.2, R({ ra: [-100, 0, -10], rf: -30, h: [-20, 0, 0], j: [30, 0, 0], b: [4, 0, 0] })],
		[0.35, R({ ra: [-112, 0, -14], rf: -24, h: [30, 0, 0], j: [0, 0, 0], b: [16, 0, 0] }), { ease: 'easeInQuad' }],
		[0.5, R({ ra: [-112, 0, -14], rf: -24, h: [32, 0, 0], j: [4, 0, 0], b: [16, 0, 0] })],
		[0.7, R({ ra: [-80, 0, -4], rf: -40, h: [-8, 0, 0], b: [4, 0, 0] })],
		[1.0, R({ ra: [-72, 0, 0], rf: -30 })],
	]);
	// carry: only the right arm, so it layers over walk / run
	A['animation.titan.carry'] = write(M, 1, true, [[0, { ra: [-72, 0, 0], rf: -30 }], [0.5, { ra: [-75, 0, 0], rf: -34 }], [1, { ra: [-72, 0, 0], rf: -30 }]], { ground: false });

	// ---------------- the revamp's moves (TitanMove.NEW_ANIMS): contact frames on the server's impact ticks (t / 20 s)
	const T = tk => tk / 20;
	clip('punch_left', 0.6, false, [ // the combo's second blow, mirrored (contact t6)
		[0, R({})],
		[0.15, R({ la: [52, 0, -8], lf: -70, ra: [-30, 0, ARM], rf: -40, b: [-4, 24, 0], h: [0, -18, 0], ll: [6, 0, 0], rl: [-8, 0, 0], rs: 10 })],
		[T(6), R({ la: [-90, 0, 4], lf: -2, ra: [28, 0, ARM], rf: -30, b: [12, -26, 0], h: [-6, 20, 0], ll: [16, 0, 0], ls: 18, rl: [-20, 0, 0], rs: 8 }), { ease: 'easeInQuad' }],
		[0.42, R({ la: [-80, 0, 2], lf: -8, ra: [24, 0, ARM], rf: -30, b: [10, -22, 0], h: [-4, 16, 0], ll: [14, 0, 0], ls: 14, rl: [-18, 0, 0] })],
		[0.6, R({})],
	]);
	clip('stumble', 0.9, false, [ // a limb just came off: the body reels
		[0, R({})],
		[0.12, R({ b: [-16, 10, 12], h: [-20, -14, 8], ra: [30, 0, 30], la: [30, 0, -36], rf: -40, lf: -40, rl: [10, 0, 6], ll: [-24, 0, -4], ls: 30, j: [20, 0, 0] }), { ease: 'easeOutQuad' }],
		[0.4, R({ b: [18, -6, -6], h: [16, 8, 0], ra: [-10, 0, 18], la: [-10, 0, -18], rf: -30, lf: -30, rl: [-24, 0, 0], rs: 40, ll: [-8, 0, 0], ls: 20 })],
		[0.9, R({})],
	]);
	clip('nape_guard', 0.7, false, [ // a hand clamped over the nape (up by t2; the flag holds it, see nape_guard_hold)
		[0, R({})],
		[T(3), R({ ra: [-168, 30, -30], rf: -120, h: [22, 0, 0], b: [8, 0, 0], la: [-40, 0, -14], lf: -60 }), { ease: 'easeOutQuad' }],
		[0.7, R({ ra: [-170, 32, -32], rf: -124, h: [24, 0, 0], b: [8, 0, 0], la: [-40, 0, -14], lf: -60 })],
	]);
	clip('uppercut', 0.8, false, [ // Attack: a rising fist from the hip (contact t8)
		[0, R({})],
		[0.22, R({ ra: [30, 0, 10], rf: -100, b: [16, -20, 0], h: [10, 14, 0], rl: [-30, 0, 0], rs: 50, ll: [-20, 0, 0], ls: 40, la: [-40, 0, -ARM], lf: -70 })],
		[T(8), R({ ra: [-150, 0, -6], rf: -40, b: [-14, 20, 0], h: [-24, -12, 0], rl: [8, 0, 0], rs: 4, ll: [-4, 0, 0], ls: 6, la: [20, 0, -ARM], lf: -40 }), { ease: 'easeInQuad' }],
		[0.55, R({ ra: [-140, 0, -4], rf: -36, b: [-10, 16, 0], h: [-18, -10, 0], la: [16, 0, -ARM], lf: -40 })],
		[0.8, R({})],
	]);
	const rageP = R({ b: [-12, 0, 0], h: [-40, 0, 0], j: [40, 0, 0], ra: [10, 0, 40], la: [10, 0, -40], rf: -70, lf: -70, rl: [-6, 0, 6], ll: [6, 0, -6] });
	clip('rage', 1.4, false, [ // Attack: chest out, fists clenched, a roar of fury (peak t10)
		[0, R({})],
		[0.3, R({ b: [18, 0, 0], h: [16, 0, 0], ra: [-20, 0, 10], la: [-20, 0, -10], rf: -100, lf: -100, rl: [-20, 0, 0], ll: [-20, 0, 0], rs: 30, ls: 30 })],
		[T(10), rageP, { ease: 'easeInQuad' }],
		...shake(rageP, 0.6, 10, 0.05, 3),
		[1.1, rageP],
		[1.4, R({})],
	]);
	clip('dropkick', 1.1, false, [ // Attack: both feet into the target (contact t8), lands on its back and rolls up
		[0, R({})],
		[0.2, R({ rl: [-40, 0, 0], ll: [-40, 0, 0], rs: 80, ls: 80, b: [30, 0, 0], ra: [30, 0, 10], la: [30, 0, -10] })],
		[T(8), R({ rl: [-88, 0, 0], ll: [-80, 0, 0], rs: 0, ls: 4, rt: -20, lt: -20, b: [-50, 0, 0], h: [30, 0, 0], ra: [-40, 0, 40], la: [-40, 0, -40], root: [0, 0, 0] }), { lift: 4, ease: 'easeOutQuad' }],
		[0.65, R({ rl: [-70, 0, 0], ll: [-70, 0, 0], rs: 30, ls: 30, b: [-30, 0, 0], h: [20, 0, 0], ra: [-20, 0, 30], la: [-20, 0, -30] }), { ground: 'all' }],
		[1.1, R({})],
	]);
	clip('elbow', 0.6, false, [ // Armoured: a short elbow from the shoulder (contact t7)
		[0, R({})],
		[0.18, R({ ra: [-60, 40, 30], rf: -130, b: [-4, -24, 0], h: [0, 16, 0], rl: [6, 0, 0], ll: [-8, 0, 0] })],
		[T(7), R({ ra: [-80, -30, -10], rf: -140, b: [10, 30, 0], h: [-4, -18, 0], rl: [16, 0, 0], rs: 16, ll: [-20, 0, 0] }), { ease: 'easeInQuad' }],
		[0.45, R({ ra: [-70, -24, -8], rf: -130, b: [8, 24, 0], rl: [14, 0, 0], ll: [-18, 0, 0] })],
		[0.6, R({})],
	]);
	clip('ground_pound', 1.8, false, [ // Armoured: both fists up, a deep crouch, then down into the ground (contact t26)
		[0, R({})],
		[0.6, R({ ra: [-170, 0, -10], la: [-170, 0, 10], rf: -20, lf: -20, b: [-14, 0, 0], h: [-12, 0, 0], rl: [-4, 0, 0], ll: [-4, 0, 0] })],
		[1.1, R({ ra: [-176, 0, -4], la: [-176, 0, 4], rf: -10, lf: -10, b: [-20, 0, 0], h: [-16, 0, 0] })],
		[T(26), R({ ra: [-80, 0, 0], la: [-80, 0, 0], rf: -6, lf: -6, b: [52, 0, 0], h: [10, 0, 0], rl: [-50, 0, 0], ll: [-50, 0, 0], rs: 80, ls: 80 }), { ease: 'easeInCubic', ground: 'all' }],
		[1.5, R({ ra: [-70, 0, 0], la: [-70, 0, 0], rf: -10, lf: -10, b: [48, 0, 0], h: [8, 0, 0], rl: [-46, 0, 0], ll: [-46, 0, 0], rs: 76, ls: 76 }), { ground: 'all' }],
		[1.8, R({})],
	]);
	const crl = 32;
	const cstep = (s) => ({
		b: [crl, 4 * s, 0], h: [12, -3 * s, 0],
		rl: [-50 * s, 0, 0], rs: s > 0 ? 18 : 70, ll: [40 * s, 0, 0], ls: s > 0 ? 70 : 18,
		ra: [-34, 0, -14], la: [-34, 0, 14], rf: -96, lf: -96,
	});
	const cfly = (s) => ({
		b: [crl + 2, 0, 0], h: [12, 0, 0],
		rl: [s > 0 ? -16 : -28, 0, 0], rs: s > 0 ? 26 : 100, ll: [s > 0 ? -28 : -16, 0, 0], ls: s > 0 ? 100 : 26,
		ra: [-36, 0, -14], la: [-36, 0, 14], rf: -98, lf: -98,
	});
	clip('shoulder_charge', 0.6, false, [ // Armoured: drops the shoulder and launches (the charge starts at t8)
		[0, R({})],
		[0.25, R({ b: [20, -20, 0], h: [6, 10, 0], ra: [-20, 0, -10], la: [-30, 0, 14], rf: -90, lf: -96, rl: [-30, 0, 0], rs: 50, ll: [10, 0, 0], ls: 30 })],
		[T(8), cstep(1), { ease: 'easeInQuad' }],
		[0.6, cfly(-1), { lift: 0.4 }],
	]);
	// held while FLAG_CHARGING: upper body only, so the legs keep running
	A['animation.titan.shoulder_charge_loop'] = write(M, 0.8, true, [
		[0, { b: [crl, -10, 0], h: [12, 6, 0], ra: [-34, 0, -14], la: [-34, 0, 14], rf: -96, lf: -96 }],
		[0.4, { b: [crl + 3, -12, 0], h: [14, 6, 0], ra: [-36, 0, -14], la: [-36, 0, 14], rf: -98, lf: -98 }],
		[0.8, { b: [crl, -10, 0], h: [12, 6, 0], ra: [-34, 0, -14], la: [-34, 0, 14], rf: -96, lf: -96 }],
	], { ground: false });
	const braceP = R({ ra: [-80, 0, -40], la: [-80, 0, 40], rf: -110, lf: -110, b: [16, 0, 0], h: [20, 0, 0], rl: [-30, 0, 14], ll: [-30, 0, -14], rs: 50, ls: 50 });
	clip('brace', 0.5, false, [ // Armoured: arms crossed over the face, knees bent (set by t4)
		[0, R({})],
		[T(4), braceP, { ease: 'easeOutQuad' }],
		[0.5, braceP],
	]);
	A['animation.titan.brace_hold'] = write(M, 1, true, [[0, braceP], [0.5, Object.assign({}, braceP, { b: [17, 0, 0] })], [1, braceP]]);
	const kh2 = female ? 1 : 0.85;
	clip('roundhouse', 0.8, false, [ // Female: a spinning kick (contact t8)
		[0, R({})],
		[0.2, R({ b: [4, 40, 6], h: [0, -30, 0], rl: [-30, 0, 30], rs: 80, ll: [-4, 0, 0], ls: 12, ra: [-20, 0, 40], la: [-10, 0, -40], rf: -50, lf: -50 })],
		[T(8), R({ b: [-6, -50, -18], h: [0, 40, 10], rl: [-40, 0, 88 * kh2], rs: 6, ll: [0, 0, -6], ls: 8, ra: [10, 0, 60], la: [0, 0, -40], rf: -40, lf: -60 }), { ease: 'easeInQuad' }],
		[0.55, R({ b: [-4, -60, -12], h: [0, 40, 6], rl: [-30, 0, 60], rs: 40, ll: [0, 0, -4], ls: 10, ra: [10, 0, 40], la: [0, 0, -30] })],
		[0.8, R({})],
	]);
	clip('axe_kick', 1.0, false, [ // Female: the heel raised high over the head, then straight down (contact t12)
		[0, R({})],
		[0.4, R({ rl: [-150 * kh2, 0, 6], rs: 4, ll: [6, 0, 0], ls: 8, b: [-18, 0, 0], h: [10, 0, 0], ra: [20, 0, 30], la: [20, 0, -30], rf: -30, lf: -30 })],
		[T(12), R({ rl: [-40, 0, 0], rs: 4, rt: -10, ll: [10, 0, 0], ls: 14, b: [24, 0, 0], h: [16, 0, 0], ra: [10, 0, 20], la: [10, 0, -20], rf: -30, lf: -30 }), { ease: 'easeInCubic' }],
		[0.8, R({ rl: [-20, 0, 0], rs: 10, ll: [8, 0, 0], ls: 12, b: [14, 0, 0] })],
		[1.0, R({})],
	]);
	clip('sweep_kick', 0.9, false, [ // Female: drops low and sweeps a leg round at ankle height (contact t9)
		[0, R({})],
		[0.25, R({ ll: [-70, 0, 0], ls: 120, lt: -20, rl: [-20, 0, 40], rs: 30, b: [30, 30, 0], h: [-20, -20, 0], ra: [-30, 0, 30], la: [-50, 0, -20], rf: -30, lf: -20 }), { ground: 'all' }],
		[T(9), R({ ll: [-70, 0, 0], ls: 120, lt: -20, rl: [-10, 0, 80], rs: 6, b: [36, -40, 0], h: [-24, 30, 0], ra: [-10, 0, 50], la: [-60, 0, -10], lf: -20 }), { ground: 'all', ease: 'easeInQuad' }],
		[0.65, R({ ll: [-60, 0, 0], ls: 100, rl: [-10, 0, 40], rs: 20, b: [26, -20, 0], h: [-16, 10, 0] }), { ground: 'all' }],
		[0.9, R({})],
	]);
	clip('crystal_fists', 0.9, false, [ // Female: fists brought together before the face as the crystal forms (t6)
		[0, R({})],
		[T(6), R({ ra: [-80, 0, -30], la: [-80, 0, 30], rf: -60, lf: -60, b: [-6, 0, 0], h: [6, 0, 0], rl: [-6, 0, 4], ll: [6, 0, -4] }), { ease: 'easeOutQuad' }],
		[0.6, R({ ra: [-60, 0, -10], la: [-60, 0, 10], rf: -90, lf: -90, b: [4, 0, 0], h: [0, 0, 0] })],
		[0.9, R({})],
	]);
	const scr = R({ h: [-44, 0, 0], j: [40, 0, 0], b: [-16, 0, 0], ra: [22, 0, 30], la: [22, 0, -30], rf: -20, lf: -20, rl: [-6, 0, 4], ll: [4, 0, -4] });
	clip('scream', 1.8, false, [ // Female: head thrown back, the scream peaks at t12
		[0, R({})],
		[0.3, R({ h: [14, 0, 0], b: [10, 0, 0], ra: [10, 0, 10], la: [10, 0, -10], rf: -30, lf: -30 })],
		[T(12), scr, { ease: 'easeInQuad' }],
		...shake(scr, 0.65, 16, 0.05, 2.5),
		[1.45, scr],
		[1.8, R({})],
	]);
	clip('backhand', 0.7, false, [ // Beast: a long-armed backhand sweep (contact t8)
		[0, R({})],
		[0.2, R({ ra: [-50, -40, -60], rf: -40, b: [6, 30, 0], h: [0, -20, 0], la: [-20, 0, -ARM], rl: [-10, 0, 0], ll: [6, 0, 0] })],
		[T(8), R({ ra: [-80, 40, 70], rf: -10, b: [10, -34, 0], h: [0, 24, 0], la: [10, 0, -ARM], rl: [8, 0, 0], ll: [-14, 0, 0] }), { ease: 'easeInQuad' }],
		[0.55, R({ ra: [-60, 30, 60], rf: -16, b: [8, -28, 0], h: [0, 20, 0] })],
		[0.7, R({})],
	]);
	clip('rock_scatter', 1.1, false, [ // Beast: rakes the ground and flings the rubble (contact t12)
		[0, R({})],
		[0.35, R({ ra: [-34, 0, 10], rf: -14, la: [-34, 0, -10], lf: -14, b: [46, 0, 0], h: [-24, 0, 0], rl: [-62, 0, 0], ll: [-62, 0, 0], rs: 96, ls: 96, rt: -30, lt: -30 }), { ground: 'all' }],
		[T(12), R({ ra: [-150, 0, 20], rf: -20, la: [-150, 0, -20], lf: -20, b: [-6, 0, 0], h: [-16, 0, 0], rl: [-6, 0, 0], ll: [-6, 0, 0], rs: 10, ls: 10 }), { ease: 'easeInQuad' }],
		[0.8, R({ ra: [-100, 0, 30], la: [-100, 0, -30], rf: -30, lf: -30, b: [4, 0, 0] })],
		[1.1, R({})],
	]);
	clip('boulder_throw', 1.3, false, [ // Beast: scoops a boulder up (in hand at t6), winds up, pitches it (released at t16)
		[0, R({})],
		[T(6), R({ ra: [-30, 0, 10], rf: -20, b: [30, -10, 0], h: [-10, 6, 0], rl: [-20, 0, 0], rs: 30, ll: [-10, 0, 0], ls: 20 })],
		[0.6, R({ ra: [-160, -10, 16], rf: -70, la: [-60, 0, -ARM], lf: -20, b: [-12, -28, 0], h: [-6, 22, 0], ll: [-30, 0, 0], ls: 20, rl: [14, 0, 0], rs: 16 })],
		[T(16), R({ ra: [-70, 0, -6], rf: -6, la: [16, 0, -ARM], lf: -30, b: [22, 26, 0], h: [6, -18, 0], ll: [-26, 0, 0], ls: 14, rl: [18, 0, 0], rs: 30 }), { ease: 'easeInQuad' }],
		[1.0, R({ ra: [16, 0, -20], rf: -20, la: [20, 0, -ARM], b: [28, 30, 0], h: [10, -20, 0], ll: [-24, 0, 0], ls: 14, rl: [20, 0, 0], rs: 40 })],
		[1.3, R({})],
	]);
	clip('boulder_volley', 1.2, false, [ // Beast: an underarm spray of stones from both hands (t14)
		[0, R({})],
		[0.4, R({ ra: [30, 0, 20], la: [30, 0, -20], rf: -30, lf: -30, b: [36, 0, 0], h: [-20, 0, 0], rl: [-24, 0, 0], ll: [-24, 0, 0], rs: 40, ls: 40 })],
		[T(14), R({ ra: [-130, 0, 10], la: [-130, 0, -10], rf: -20, lf: -20, b: [-10, 0, 0], h: [-20, 0, 0], rl: [-4, 0, 0], ll: [-4, 0, 0] }), { ease: 'easeInQuad' }],
		[0.95, R({ ra: [-90, 0, 20], la: [-90, 0, -20], rf: -20, lf: -20, b: [2, 0, 0] })],
		[1.2, R({})],
	]);
	if (beast) {
		// v0.16.7 (user): Pitcher's Fastball (Shift+R, replaced the Backhand; Beast file only) -- scoops a handful of ground
		// with the right hand (t8), crushes it in both fists (t12-15), rears into a pitcher's leg kick (t18) and whips it
		// overarm (released at t22), then follows through low across the body
		const crush = { b: [12, 0, 0], h: [-6, 0, 0], ra: [-72, -24, -34], rf: -104, la: [-64, 24, 32], lf: -104, rl: [-12, 0, 0], ll: [-10, 0, 0], rs: 18, ls: 16 };
		clip('fastball', 1.6, false, [
			[0, R({})],
			[T(8), R({ b: [46, 14, 0], h: [-26, -8, 0], ra: [-46, 0, 18], rf: -8, la: [-24, 0, -12], lf: -24, rl: [-54, 0, 0], rs: 84, rt: -26, ll: [-36, 0, 0], ls: 66, lt: -24 }), { ground: 'all', hands: ['right'] }],
			[T(12), R(crush)],
			[T(14), R(Object.assign({}, crush, { b: [14, 0, 0], ra: [-76, -28, -36], rf: -114, la: [-68, 28, 34], lf: -114 }))],
			[T(15), R(Object.assign({}, crush, { b: [12, -6, 0] }))],
			[T(18), R({ b: [-16, -40, 0], h: [-8, 34, 0], ra: [-168, -12, 22], rf: -84, la: [-84, 30, -30], lf: -26, ll: [-84, 0, 0], ls: 92, lt: -10, rl: [-8, 0, 0], rs: 12 })],
			[T(20), R({ b: [-8, -30, 0], h: [-6, 28, 0], ra: [-176, -6, 18], rf: -70, la: [-60, 20, -24], lf: -30, ll: [-56, 0, 0], ls: 40, rl: [4, 0, 0], rs: 18 })],
			[T(22), R({ b: [26, 28, 0], h: [8, -20, 0], ra: [-84, 0, -10], rf: -4, la: [24, 0, -22], lf: -40, ll: [-42, 0, 0], ls: 22, rl: [22, 0, 0], rs: 38 }), { ease: 'easeInQuad' }],
			[1.3, R({ b: [36, 36, 0], h: [12, -22, 0], ra: [24, 0, -34], rf: -22, la: [26, 0, -ARM], lf: -30, ll: [-38, 0, 0], ls: 26, rl: [26, 0, 0], rs: 48 })],
			[1.6, R({})],
		]);
	}
	// held while FLAG_HOLDING_BOULDER: the right arm carries the rock at the shoulder (layers over walk / run)
	A['animation.titan.boulder_hold'] = write(M, 1, true, [[0, { ra: [-150, -10, 16], rf: -70 }], [0.5, { ra: [-154, -10, 16], rf: -74 }], [1, { ra: [-150, -10, 16], rf: -70 }]], { ground: false });
	// held while FLAG_NAPE_GUARD: the hand stays over the nape
	A['animation.titan.nape_guard_hold'] = write(M, 1, true, [[0, { ra: [-170, 32, -32], rf: -124, h: [24, 0, 0] }], [0.5, { ra: [-172, 32, -32], rf: -126, h: [25, 0, 0] }], [1, { ra: [-170, 32, -32], rf: -124, h: [24, 0, 0] }]], { ground: false });
	// a leg is gone: the Titan is down on one knee (idle) / drags itself (moving)
	const kneelIdle = R({ b: [16, 0, 0], h: [-10, 0, 0], rl: [-70, 0, 0], rs: 74, ll: [6, 0, 0], ls: 96, lt: 40, ra: [-10, 0, 10], la: [-30, 0, -10], lf: -20 });
	clip('kneel', 2, true, [[0, kneelIdle, { ground: 'all' }], [1, Object.assign({}, kneelIdle, { b: [18, 0, 0] }), { ground: 'all' }], [2, kneelIdle, { ground: 'all' }]]);
	const crawl = (s) => R({ b: [30, 6 * s, 0], h: [-24, 0, 0], rl: [-70 + 10 * s, 0, 0], rs: 80, ll: [10 - 10 * s, 0, 0], ls: 96, lt: 40, ra: [-50 - 20 * s, 0, 10], la: [-50 + 20 * s, 0, -10], rf: -10, lf: -10 });
	clip('kneel_crawl', 1.4, true, [[0, crawl(1), { ground: 'all' }], [0.7, crawl(-1), { ground: 'all' }], [1.4, crawl(1), { ground: 'all' }]]);

	// ---------------- the shift itself (4.5 s): the skeleton builds in a crouch, rises as the muscle forms, roars as the skin closes
	const kneel = {
		b: [44, 0, 0], h: [18, 0, 0], j: [6, 0, 0],
		rl: [12, 0, 0], rs: 96, rt: 40, ll: [-84, 0, 0], ls: 84, lt: -4,
		ra: [-44, 0, 8], la: [-56, 0, -10], rf: -6, lf: -46,
	};
	clip('transformation', 4.5, 'hold', [
		[0, Object.assign({}, kneel, { rootScale: 0.86 }), { ground: 'all', hands: ['right'] }],
		[1.6, Object.assign({}, kneel, { b: [40, 0, 0], h: [10, 0, 0], rootScale: 0.89 }), { ground: 'all', hands: ['right'] }],
		[2.5, { b: [24, 0, 0], h: [0, 0, 0], rl: [-24, 0, 0], rs: 50, ll: [-50, 0, 0], ls: 54, ra: [-10, 0, 10], la: [-26, 0, -10], rf: -30, lf: -40, rootScale: 0.94 }, { ground: 'all' }],
		[3.3, R({ b: [6, 0, 0], h: [-6, 0, 0], rl: [-6, 0, 0], rs: 10, ll: [-8, 0, 0], ls: 12, rootScale: 0.99 })],
		[3.85, R({ b: [-10, 0, 0], h: [-32, 0, 0], j: [36, 0, 0], ra: [22, 0, 24], la: [22, 0, -24], rf: -36, lf: -36, rootScale: 1.0 })],
		[4.25, R({ b: [-6, 0, 0], h: [-18, 0, 0], j: [14, 0, 0], ra: [14, 0, 12], la: [14, 0, -12], rf: -20, lf: -20, rootScale: 1.0 })],
		[4.5, R({ b: [2, 0, 0], h: [-1, 0, 0], rootScale: 1.0 })],
	], { allBones: true });
	const sag = R({ b: [6, 0, 0], h: [12, 0, 0], j: [6, 0, 0], ra: [6, 0, ARM + 2], la: [6, 0, -ARM - 2], rf: -12, lf: -12, rl: [-4, 0, 0], ll: [-3, 0, 0], rs: 8, ls: 6 });
	clip('reversion', 3.0, 'hold', [
		[0, R({})],
		[0.6, R({ b: [-4, 0, 0], h: [-14, 0, 0], j: [12, 0, 0] })],
		[1.6, R({ b: [3, 0, 0], h: [6, 0, 0], j: [8, 0, 0], ra: [4, 0, ARM + 1], la: [4, 0, -ARM - 1], rf: -10, lf: -10 })],
		[3.0, sag],
	], { allBones: true });

	// ---------------- the defeated Titan: the knees go, it slumps forward onto its hands
	clip('death', 3.0, 'hold', [
		[0, R({})],
		[0.5, R({ b: [-8, 0, 0], h: [-14, 0, 0], rl: [-16, 0, 0], ll: [-12, 0, 0], rs: 30, ls: 26, j: [20, 0, 0] })],
		[1.2, R({ b: [16, 0, 4], h: [20, 0, 0], rl: [-6, 0, 0], ll: [-4, 0, 0], rs: 86, ls: 84, rt: 30, lt: 30, ra: [-10, 0, 8], la: [-10, 0, -8] }), { ground: 'all', ease: 'easeInQuad' }],
		[2.2, R({ b: [52, 0, 6], h: [30, 6, 0], rl: [-18, 0, 0], ll: [-16, 0, 0], rs: 100, ls: 98, rt: 40, lt: 40, j: [12, 0, 0] }), { ground: 'all', hands: ['right', 'left'] }],
		[3.0, R({ b: [56, 0, 6], h: [36, 6, 0], rl: [-20, 0, 0], ll: [-18, 0, 0], rs: 102, ls: 100, rt: 40, lt: 40, j: [14, 0, 0] }), { ground: 'all', hands: ['right', 'left'] }],
	], { allBones: true });

	A['animation.titan.corpse_defeat'] = write(M, 0.5, 'hold', [[0, A['animation.titan.death'] ? lastPose(A['animation.titan.death']) : {}], [0.5, A['animation.titan.death'] ? lastPose(A['animation.titan.death']) : {}]], { ground: false, raw: true });

	// ---------------- the corpse: falls to its knees and looks down at the ground; the nape flaps peel open
	const flapOpen = 74;
	clip('corpse_kneel', 3.0, 'hold', [
		[0, Object.assign({}, sag, { fl: [0, 0, 0], fr: [0, 0, 0] })],
		[0.35, R({ b: [6, 0, 0], h: [6, 0, 0], rl: [-18, 0, 0], rs: 34, ll: [-14, 0, 0], ls: 30, ra: [8, 0, ARM + 2], la: [8, 0, -ARM - 2], rf: -12, lf: -12, fl: [0, -20, 0], fr: [0, 20, 0] })],
		[0.9, R({ b: [10, 0, 0], h: [14, 0, 0], rl: [-8, 0, 0], rs: 76, rt: 26, ll: [-5, 0, 0], ls: 72, lt: 26, ra: [6, 0, ARM + 4], la: [6, 0, -ARM - 4], fl: [0, -50, 0], fr: [0, 50, 0] }), { ground: 'all', ease: 'easeInQuad' }],
		[1.15, R({ b: [17, 0, 1], h: [22, 0, 0], rl: [-6, 0, 0], rs: 92, rt: 44, ll: [-4, 0, 0], ls: 90, lt: 44, ra: [2, 0, ARM + 6], la: [2, 0, -ARM - 6], rf: -6, lf: -6, fl: [0, -64, 0], fr: [0, 64, 0] }), { ground: 'all', ease: 'easeInQuad' }],
		[1.4, R({ b: [12, 0, 1], h: [16, 0, 0], rl: [-7, 0, 0], rs: 90, rt: 44, ll: [-5, 0, 0], ls: 88, lt: 44, ra: [-4, 0, ARM + 4], la: [-4, 0, -ARM - 4], fl: [0, -70, 0], fr: [0, 70, 0] }), { ground: 'all' }],
		[2.2, R({ b: [24, 0, 3], h: [42, 5, 0], j: [8, 0, 0], rl: [-8, 0, 0], rs: 92, rt: 44, ll: [-6, 0, 0], ls: 90, lt: 44, ra: [-16, 0, 8], la: [-18, 0, -6], rf: -14, lf: -10, fl: [0, -flapOpen, 0], fr: [0, flapOpen, 0] }), { ground: 'all' }],
		[3.0, R({ b: [26, 0, 3], h: [48, 5, 0], j: [10, 0, 0], rl: [-8, 0, 0], rs: 92, rt: 44, ll: [-6, 0, 0], ls: 90, lt: 44, ra: [-18, 0, 8], la: [-20, 0, -6], rf: -16, lf: -12, fl: [0, -flapOpen, 0], fr: [0, flapOpen, 0] }), { ground: 'all' }],
	], { allBones: true });
	void hb; void L;
	return A;
}

module.exports = { animations, write, ALIAS };
