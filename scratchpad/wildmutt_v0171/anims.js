// v0.17.1 Wildmutt clips (GeckoLib). Each clip is a function t -> { bone: { rot, pos, scale } } sampled into keyframes.
// Rotations use the geometry convention (X > 0 tips a bone's top forward: a hanging limb swings BACK, a point in front
// of the pivot goes DOWN -- the jaw opens with +X; X < 0 reaches a limb forward / lifts the nose); positions are model
// pixels in json space (forward = -Z).
//
// Controllers (WildmuttAnimatable): "main" plays the locomotion / move clips on every bone but hurt / look / badge;
// "swing" layers the claw swipes over one front arm; "hurt" keys only the hurt bone (the flinch).
'use strict';
const fs = require('fs');
const path = require('path');

const TAU = Math.PI * 2;
const r3 = v => Math.round(v * 1000) / 1000;
const clamp01 = t => Math.max(0, Math.min(1, t));
const ease = t => { t = clamp01(t); return t * t * (3 - 2 * t); };
// keys like "1" would be array-index-like: JS lists them FIRST in the object, and GeckoLib reads keyframes in file order
const tkey = t => { const v = Math.round(t * 1000) / 1000; return Number.isInteger(v) ? v.toFixed(1) : String(v); };
const lerp = (a, b, k) => a + (b - a) * k;
const lerp3 = (a, b, k) => a.map((v, i) => lerp(v, b[i], k));
const fr = x => x - Math.floor(x);

// the bones the main controller owns (every clip keys all of them, so nothing pops between clips)
const MAIN = ['root', 'spin', 'hips', 'chest', 'neck', 'head', 'jaw', 'quills_f', 'quills_b', 'tail',
	'arm_r', 'forearm_r', 'paw_r', 'arm_l', 'forearm_l', 'paw_l', 'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];
const base = () => { const o = {}; for (const b of MAIN) o[b] = { rot: [0, 0, 0] }; o.root.pos = [0, 0, 0]; o.quills_f.scale = [1, 1, 1]; o.quills_b.scale = [1, 1, 1]; return o; };
const add = (o, b, rot, pos, scale) => {
	o[b] = o[b] || { rot: [0, 0, 0] };
	if (rot) o[b].rot = (o[b].rot || [0, 0, 0]).map((v, i) => v + rot[i]);
	if (pos) o[b].pos = (o[b].pos || [0, 0, 0]).map((v, i) => v + pos[i]);
	if (scale) o[b].scale = scale;
	return o;
};
// piecewise keys: [[t, value], ...] -> value at t (smoothstepped between keys)
function keys(list, t) {
	if (t <= list[0][0]) return list[0][1];
	for (let i = 0; i < list.length - 1; i++) {
		const [t0, v0] = list[i], [t1, v1] = list[i + 1];
		if (t <= t1) {
			const k = ease((t - t0) / Math.max(1e-6, t1 - t0));
			return Array.isArray(v0) ? lerp3(v0, v1, k) : lerp(v0, v1, k);
		}
	}
	return list[list.length - 1][1];
}
// a pose-keyed clip: frames [[t, { bone: rot | {rot,pos,scale} }]], every bone missing from a frame is at rest
function kclip(frames) {
	const norm = frames.map(([t, f]) => {
		const o = base();
		for (const [b, v] of Object.entries(f)) {
			if (Array.isArray(v)) o[b].rot = v.slice();
			else { o[b] = Object.assign({ rot: [0, 0, 0] }, o[b], v); }
		}
		return [t, o];
	});
	return t => {
		const out = base();
		for (const b of Object.keys(out)) {
			for (const ch of ['rot', 'pos', 'scale']) {
				if (out[b][ch] === undefined) continue;
				out[b][ch] = keys(norm.map(([tt, o]) => [tt, o[b][ch] || out[b][ch]]), t);
			}
		}
		return out;
	};
}

// one limb's step cycle: u in 0..1; stance (on the ground, sweeping back at constant speed) for the first `duty`,
// then the swing (lifted, eased forward). Returns the hip / shoulder angle (fwd = -A .. back = +B) and the lift 0..1.
function step(u, A, B, duty) {
	u = fr(u);
	if (u < duty) return { a: lerp(-A, B, u / duty), lift: 0 };
	const k = (u - duty) / (1 - duty);
	return { a: lerp(B, -A, ease(k)), lift: Math.sin(Math.PI * k) };
}
// a front arm: the elbow folds and the hand curls on the lift; the hand stays flat on the ground in stance
function frontLeg(o, n, u, A, B, duty, fold = 1) {
	const { a, lift } = step(u, A, B, duty);
	const fa = 38 * lift * fold;
	add(o, `arm_${n}`, [a, 0, 0]);
	add(o, `forearm_${n}`, [fa, 0, 0]);
	add(o, `paw_${n}`, [-(a + fa) + 46 * lift * fold, 0, 0]);
	return lift;
}
function hindLeg(o, n, u, A, B, duty, fold = 1) {
	const { a, lift } = step(u, A, B, duty);
	const sh = 34 * lift * fold;
	add(o, `thigh_${n}`, [a, 0, 0]);
	add(o, `shin_${n}`, [sh, 0, 0]);
	add(o, `foot_${n}`, [-(a + sh) + 34 * lift * fold, 0, 0]);
	return lift;
}
// rearing up about the hind feet (z = +12): the root's pitch plus the offset that keeps the feet on the ground
function rear(o, deg) {
	const th = deg * Math.PI / 180, Z = 11.5;
	add(o, 'root', [-deg, 0, 0], [0, Z * Math.sin(th), Z * (1 - Math.cos(th))]);
}

const CLIPS = {};
const clipDef = (name, len, loop, fn, step = 0.04) => { CLIPS[name] = { len, loop, fn, step }; };

// ---- idle: slow heavy breathing, the head swaying as he listens, the tail flicking
clipDef('idle', 4.0, true, t => {
	const w = TAU * t / 4.0;
	const o = base();
	const br = Math.sin(w * 2);
	add(o, 'root', null, [0, 0.25 * br - 0.25, 0]);
	add(o, 'chest', [-1.2 * br, 0, 0]);
	add(o, 'hips', [0.5 * br, 0, 0]);
	add(o, 'neck', [1.5 * Math.sin(w * 2 + 0.8), 0, 0]);
	add(o, 'head', [1.2 * Math.sin(w * 2 + 1.4), 7 * Math.sin(w), 2 * Math.sin(w)]);
	add(o, 'jaw', [1.5 + 1.5 * Math.sin(w * 2 + 2), 0, 0]);
	add(o, 'arm_r', [1.2 * br, 0, 0]); add(o, 'arm_l', [1.2 * br, 0, 0]);
	add(o, 'paw_r', [-1.2 * br, 0, 0]); add(o, 'paw_l', [-1.2 * br, 0, 0]);
	add(o, 'quills_f', [1.5 * br, 0, 0]);
	add(o, 'tail', [3 * Math.sin(w * 2), 12 * Math.sin(w * 3), 0]);
	return o;
});

// ---- idle_sniff: the nose (gills) dropped to the ground, three sharp sniffs, a look left and right
clipDef('idle_sniff', 2.6, false, t => {
	const o = kclip([
		[0, {}],
		[0.45, { neck: [16, 0, 0], head: [10, 0, 0], chest: [4, 0, 0] }],
		[1.35, { neck: [16, 0, 0], head: [10, -18, 4], chest: [4, 0, 0] }],
		[1.85, { neck: [14, 0, 0], head: [8, 18, -4], chest: [4, 0, 0] }],
		[2.6, {}],
	])(t);
	for (const s of [0.6, 0.78, 0.96, 1.6, 2.05]) {
		const k = Math.exp(-Math.pow((t - s) / 0.05, 2));
		add(o, 'head', [-4 * k, 0, 0]);
		add(o, 'jaw', [3 * k, 0, 0]);
	}
	add(o, 'tail', [0, 10 * Math.sin(TAU * t * 1.5), 0]);
	return o;
});

// a heavy lateral-sequence walk (LH, LF, RH, RF), the shoulders rolling with each front step. Clip speeds are fixed (GeckoLib
// rescales a clip's whole elapsed time when its speed changes, so it would jump): walk / sneak / gallop are authored for his
// walking (5.8 b/s), sneaking and galloping (~9.5 b/s) speeds and picked by the client from how fast he moves.
function walkCycle(len, amp, duty, bob) {
	return t => {
		const u = t / len;
		const o = base();
		frontLeg(o, 'l', u + 0.75, amp, amp * 0.92, duty);
		frontLeg(o, 'r', u + 0.25, amp, amp * 0.92, duty);
		hindLeg(o, 'l', u + 0.0, amp * 0.85, amp, duty);
		hindLeg(o, 'r', u + 0.5, amp * 0.85, amp, duty);
		const w = TAU * u;
		add(o, 'root', null, [0, -0.6 * bob + 0.6 * bob * Math.cos(2 * w), 0]);
		add(o, 'chest', [1.2 * bob * Math.cos(2 * w), 4 * Math.sin(w), 3 * Math.sin(w)]);
		add(o, 'hips', [0, -3 * Math.sin(w), -2.5 * Math.sin(w + 1.2)]);
		add(o, 'neck', [-1.5 * bob * Math.cos(2 * w), -2.5 * Math.sin(w), 0]);
		add(o, 'head', [1.0 * Math.cos(2 * w + 0.5), -2 * Math.sin(w), -2 * Math.sin(w)]);
		add(o, 'jaw', [2 + 2 * bob, 0, 0]);
		add(o, 'tail', [4, 14 * Math.sin(w + 1), 0]);
		add(o, 'quills_f', [1.5 * Math.cos(2 * w), 0, 0]);
		return o;
	};
}
clipDef('walk', 0.56, true, walkCycle(0.56, 30, 0.58, 1.2), 0.02);
// ---- sneak: the same walk, low and slow, the head down
clipDef('sneak', 1.0, true, t => {
	const o = walkCycle(1.0, 20, 0.62, 0.5)(t);
	add(o, 'root', null, [0, -2.2, 0]);
	add(o, 'neck', [10, 0, 0]);
	add(o, 'thigh_r', [-10, 0, 0]); add(o, 'thigh_l', [-10, 0, 0]); add(o, 'shin_r', [18, 0, 0]); add(o, 'shin_l', [18, 0, 0]);
	add(o, 'foot_r', [-8, 0, 0]); add(o, 'foot_l', [-8, 0, 0]);
	add(o, 'forearm_r', [16, 0, 0]); add(o, 'forearm_l', [16, 0, 0]); add(o, 'paw_r', [-16, 0, 0]); add(o, 'paw_l', [-16, 0, 0]);
	return o;
});

// ---- gallop: a transverse gallop -- the long arms reach far ahead together, the body vaults over them, the hind legs
// gather under and drive; the spine flexes and stretches each stride, one big bob per stride
clipDef('gallop', 0.42, true, t => {
	const u = t / 0.42;
	const o = base();
	frontLeg(o, 'r', u + 0.0, 52, 34, 0.38, 1.1);
	frontLeg(o, 'l', u - 0.09, 52, 34, 0.38, 1.1);
	hindLeg(o, 'r', u - 0.5, 42, 34, 0.38, 1.1);
	hindLeg(o, 'l', u - 0.59, 42, 34, 0.38, 1.1);
	const flex = -Math.cos(TAU * u); // -1 stretched (u 0), +1 gathered (u 0.5)
	add(o, 'root', [3 * Math.sin(TAU * u), 0, 0], [0, 1.2 + 1.6 * Math.cos(2 * TAU * u), 0]);
	add(o, 'hips', [-9 * flex, 0, 0]);
	add(o, 'chest', [7 * flex, 0, 0]);
	add(o, 'neck', [-8 * flex - 6, 0, 0]);
	add(o, 'head', [3 * flex, 0, 0]);
	add(o, 'jaw', [8 + 4 * Math.sin(TAU * u), 0, 0]);
	add(o, 'tail', [14 * flex + 10, 6 * Math.sin(TAU * u), 0]);
	add(o, 'quills_f', [-6 * flex, 0, 0]);
	add(o, 'quills_b', [-5 * flex, 0, 0]);
	return o;
}, 0.02);

// ---- air: arms reaching ahead for the landing, hind legs trailing, nose up
clipDef('air', 0.5, 'hold', kclip([
	[0, { arm_r: [-30, 0, 0], arm_l: [-30, 0, 0], paw_r: [30, 0, 0], paw_l: [30, 0, 0], thigh_r: [30, 0, 0], thigh_l: [30, 0, 0] }],
	[0.5, { root: { rot: [-6, 0, 0], pos: [0, 0, 0] }, arm_r: [-58, 0, -6], arm_l: [-58, 0, 6], forearm_r: [-12, 0, 0], forearm_l: [-12, 0, 0],
		paw_r: [38, 0, 0], paw_l: [38, 0, 0], thigh_r: [48, 0, 0], thigh_l: [42, 0, 0], shin_r: [-14, 0, 0], shin_l: [-10, 0, 0],
		foot_r: [26, 0, 0], foot_l: [24, 0, 0], neck: [-10, 0, 0], tail: [22, 0, 0], quills_f: [-10, 0, 0], quills_b: [-8, 0, 0], jaw: [10, 0, 0] }],
]));

// ---- leap_charge: coiled low on all fours, quivering (hold X)
const CROUCH = {
	root: { rot: [0, 0, 0], pos: [0, -3.4, 0.5] }, chest: [5, 0, 0], hips: [-4, 0, 0], neck: [8, 0, 0], head: [-2, 0, 0],
	arm_r: [-14, 0, -4], arm_l: [-14, 0, 4], forearm_r: [34, 0, 0], forearm_l: [34, 0, 0], paw_r: [-20, 0, 0], paw_l: [-20, 0, 0],
	thigh_r: [-30, 0, 0], thigh_l: [-30, 0, 0], shin_r: [42, 0, 0], shin_l: [42, 0, 0], foot_r: [-12, 0, 0], foot_l: [-12, 0, 0],
	tail: [-12, 0, 0], quills_f: { rot: [-6, 0, 0], scale: [1.1, 1.25, 1.1] }, quills_b: { rot: [-6, 0, 0], scale: [1.1, 1.25, 1.1] },
};
clipDef('leap_charge', 0.6, true, t => {
	const o = kclip([[0, CROUCH], [0.6, CROUCH]])(t);
	const q = Math.sin(TAU * t / 0.6 * 6);
	add(o, 'root', null, [0.12 * q, 0.1 * q, 0]);
	add(o, 'chest', [0.6 * q, 0, 0]);
	add(o, 'jaw', [4 + 2 * Math.sin(TAU * t / 0.6), 0, 0]);
	return o;
}, 0.02);

// ---- pounce: coil, launch with the arms thrown ahead and the jaw wide, slam down claws-first, the bite, a shake
const STRETCH = {
	root: { rot: [-14, 0, 0], pos: [0, 2, -2] }, chest: [-6, 0, 0], neck: [-12, 0, 0], head: [-6, 0, 0], jaw: [38, 0, 0],
	arm_r: [-100, 0, -10], arm_l: [-100, 0, 10], forearm_r: [-12, 0, 0], forearm_l: [-12, 0, 0], paw_r: [28, 0, 0], paw_l: [28, 0, 0],
	thigh_r: [58, 0, 0], thigh_l: [54, 0, 0], shin_r: [-12, 0, 0], shin_l: [-10, 0, 0], foot_r: [30, 0, 0], foot_l: [28, 0, 0],
	tail: [28, 0, 0], quills_f: { rot: [-14, 0, 0], scale: [1.1, 1.3, 1.1] }, quills_b: { rot: [-12, 0, 0], scale: [1.1, 1.3, 1.1] },
};
const STRIKE = {
	root: { rot: [8, 0, 0], pos: [0, -1.5, -3] }, chest: [8, 0, 0], neck: [14, 0, 0], head: [8, 0, 0], jaw: [2, 0, 0],
	arm_r: [-48, 0, -6], arm_l: [-48, 0, 6], forearm_r: [10, 0, 0], forearm_l: [10, 0, 0], paw_r: [38, 0, 0], paw_l: [38, 0, 0],
	thigh_r: [8, 0, 0], thigh_l: [12, 0, 0], shin_r: [10, 0, 0], shin_l: [8, 0, 0], foot_r: [-18, 0, 0], foot_l: [-20, 0, 0],
	tail: [10, 0, 0], quills_f: { rot: [-4, 0, 0], scale: [1.05, 1.15, 1.05] },
};
clipDef('pounce', 0.85, false, t => {
	const o = kclip([[0, {}], [0.12, CROUCH], [0.27, STRETCH], [0.42, STRIKE], [0.62, STRIKE], [0.85, {}]])(t);
	if (t > 0.42 && t < 0.66) add(o, 'head', [0, 12 * Math.sin((t - 0.42) / 0.24 * TAU * 1.5), 0]); // the bite's shake
	return o;
}, 0.02);

// ---- maul: rears up on the hind legs and rakes right, left, right, then both arms crash down
const RAISE_R = { arm_r: [-118, 0, 14], forearm_r: [-26, 0, 0], paw_r: [24, 0, 0] };
const RAISE_L = { arm_l: [-118, 0, -14], forearm_l: [-26, 0, 0], paw_l: [24, 0, 0] };
const RAKE_R = { arm_r: [-34, 0, -10], forearm_r: [22, 0, 0], paw_r: [36, 0, 0] };
const RAKE_L = { arm_l: [-34, 0, 10], forearm_l: [22, 0, 0], paw_l: [36, 0, 0] };
const REAR_LEGS = { thigh_r: [-16, 0, 0], thigh_l: [-16, 0, 0], shin_r: [22, 0, 0], shin_l: [22, 0, 0], foot_r: [20, 0, 0], foot_l: [20, 0, 0] };
const mauler = (o, deg) => rear(o, deg);
clipDef('maul', 1.05, false, t => {
	const up = (extra) => Object.assign({}, REAR_LEGS, { chest: [-4, 0, 0], neck: [-6, 0, 0], jaw: [24, 0, 0], tail: [10, 0, 0] }, extra);
	const o = kclip([
		[0, {}],
		[0.1, up(Object.assign({}, RAISE_R, RAKE_L))],
		[0.2, up(Object.assign({}, RAKE_R, RAISE_L, { chest: [-4, 10, 0] }))],
		[0.34, up(Object.assign({}, RAISE_R, RAKE_L, { chest: [-4, -10, 0] }))],
		[0.48, up(Object.assign({}, RAKE_R, RAISE_L, { chest: [-4, 10, 0] }))],
		[0.64, up(Object.assign({}, RAISE_R, RAISE_L, { chest: [-8, 0, 0], neck: [-10, 0, 0], jaw: [36, 0, 0] }))],
		[0.76, Object.assign({}, STRIKE, { arm_r: [-40, 0, -8], arm_l: [-40, 0, 8] })],
		[0.86, Object.assign({}, STRIKE, { arm_r: [-40, 0, -8], arm_l: [-40, 0, 8] })],
		[1.05, {}],
	])(t);
	mauler(o, keys([[0, 0], [0.1, 24], [0.64, 30], [0.76, 0], [1.05, 0]], t));
	return o;
}, 0.02);

// ---- quills: hunches, the back spikes bristle up, a hard full-body shake, and they fly
clipDef('quills', 0.7, false, t => {
	const H = { chest: [7, 0, 0], neck: [10, 0, 0], head: [4, 0, 0], hips: [-3, 0, 0], jaw: [12, 0, 0],
		arm_r: [-6, 0, -6], arm_l: [-6, 0, 6], paw_r: [6, 0, 0], paw_l: [6, 0, 0], tail: [18, 0, 0],
		quills_f: { rot: [-8, 0, 0], scale: [1.2, 1.5, 1.2] }, quills_b: { rot: [-8, 0, 0], scale: [1.2, 1.5, 1.2] } };
	const F = Object.assign({}, H, { quills_f: { rot: [-22, 0, 0], scale: [1.1, 1.2, 1.1] }, quills_b: { rot: [-22, 0, 0], scale: [1.1, 1.2, 1.1] },
		chest: [-4, 0, 0], neck: [-6, 0, 0] });
	const o = kclip([[0, {}], [0.16, H], [0.38, H], [0.44, F], [0.52, F], [0.7, {}]])(t);
	const shake = t > 0.14 && t < 0.42 ? Math.sin((t - 0.14) * TAU * 14) : 0;
	add(o, 'chest', [0, 0, 7 * shake]); add(o, 'hips', [0, 0, -5 * shake]); add(o, 'neck', [0, 0, -4 * shake]);
	add(o, 'root', null, [0.4 * shake, 0, 0]);
	return o;
}, 0.02);

// ---- roar: rears back, head up, the jaw thrown wide, the head shaking, arms braced wide
clipDef('roar', 1.4, false, t => {
	const R = { chest: [-10, 0, 0], neck: [-18, 0, 0], head: [-14, 0, 0], jaw: [42, 0, 0],
		arm_r: [-10, 0, 14], arm_l: [-10, 0, -14], forearm_r: [8, 0, 0], forearm_l: [8, 0, 0], paw_r: [2, 0, -14], paw_l: [2, 0, 14],
		thigh_r: [-6, 0, 0], thigh_l: [-6, 0, 0], shin_r: [10, 0, 0], shin_l: [10, 0, 0], foot_r: [6, 0, 0], foot_l: [6, 0, 0],
		tail: [26, 0, 0], quills_f: { rot: [-14, 0, 0], scale: [1.2, 1.55, 1.2] }, quills_b: { rot: [-12, 0, 0], scale: [1.2, 1.45, 1.2] } };
	const o = kclip([[0, {}], [0.12, CROUCH], [0.32, R], [1.1, R], [1.4, {}]])(t);
	rear(o, keys([[0, 0], [0.12, 0], [0.32, 14], [1.1, 12], [1.4, 0]], t));
	if (t > 0.3 && t < 1.15) {
		const k = Math.sin((t - 0.3) / 0.85 * Math.PI);
		add(o, 'head', [0, 9 * k * Math.sin((t - 0.3) * TAU * 3.2), 0]);
		add(o, 'jaw', [3 * Math.sin(t * TAU * 11), 0, 0]);
		add(o, 'chest', [0.8 * Math.sin(t * TAU * 9), 0, 0]);
	}
	return o;
}, 0.02);

// ---- slam: rears up high with both arms overhead and brings them down on the ground in front
clipDef('slam', 1.0, false, t => {
	const UP = Object.assign({}, REAR_LEGS, { chest: [-10, 0, 0], neck: [-10, 0, 0], jaw: [30, 0, 0],
		arm_r: [-160, 0, 16], arm_l: [-160, 0, -16], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], paw_r: [30, 0, 0], paw_l: [30, 0, 0],
		tail: [18, 0, 0], quills_f: { rot: [-12, 0, 0], scale: [1.15, 1.4, 1.15] } });
	const DOWN = { root: { rot: [0, 0, 0], pos: [0, -2.4, -2] }, chest: [12, 0, 0], neck: [12, 0, 0], head: [6, 0, 0], jaw: [6, 0, 0],
		arm_r: [-36, 0, -12], arm_l: [-36, 0, 12], forearm_r: [-8, 0, 0], forearm_l: [-8, 0, 0], paw_r: [44, 0, 0], paw_l: [44, 0, 0],
		thigh_r: [-8, 0, 0], thigh_l: [-8, 0, 0], shin_r: [20, 0, 0], shin_l: [20, 0, 0], foot_r: [-12, 0, 0], foot_l: [-12, 0, 0], tail: [-6, 0, 0] };
	const o = kclip([[0, {}], [0.34, UP], [0.44, DOWN], [0.7, DOWN], [1.0, {}]])(t);
	rear(o, keys([[0, 0], [0.34, 38], [0.44, 0], [1.0, 0]], t));
	return o;
}, 0.02);

// ---- roll: curled into a spiked ball, tumbling forward (Quill Roll; loops while it lasts)
const BALL = { neck: [40, 0, 0], head: [24, 0, 0], jaw: [0, 0, 0], chest: [16, 0, 0], hips: [-18, 0, 0],
	arm_r: [-70, 0, 0], arm_l: [-70, 0, 0], forearm_r: [110, 0, 0], forearm_l: [110, 0, 0], paw_r: [40, 0, 0], paw_l: [40, 0, 0],
	thigh_r: [-76, 0, 0], thigh_l: [-76, 0, 0], shin_r: [96, 0, 0], shin_l: [96, 0, 0], foot_r: [20, 0, 0], foot_l: [20, 0, 0],
	tail: [-30, 0, 0], quills_f: { rot: [-6, 0, 0], scale: [1.25, 1.6, 1.25] }, quills_b: { rot: [-6, 0, 0], scale: [1.25, 1.6, 1.25] } };
clipDef('roll', 0.4, true, t => {
	const o = kclip([[0, BALL], [0.4, BALL]])(t);
	add(o, 'spin', [360 * t / 0.4, 0, 0]);
	add(o, 'root', null, [0, 1.5, 0]);
	return o;
}, 0.02);

// ---- frenzy: the feral buff -- head thrashing, jaw snapping, hammering the ground with alternate fists
clipDef('frenzy', 1.2, false, t => {
	const o = kclip([[0, {}], [0.15, CROUCH], [0.3, { chest: [-6, 0, 0], neck: [-12, 0, 0], jaw: [36, 0, 0], arm_r: [-80, 0, 10], forearm_r: [-20, 0, 0],
		quills_f: { rot: [-12, 0, 0], scale: [1.25, 1.6, 1.25] }, quills_b: { rot: [-12, 0, 0], scale: [1.25, 1.5, 1.25] } }],
	[0.42, { chest: [6, 0, 0], neck: [4, 0, 0], jaw: [20, 0, 0], arm_r: [-20, 0, -6], paw_r: [20, 0, 0], arm_l: [-80, 0, -10], forearm_l: [-20, 0, 0],
		quills_f: { rot: [-12, 0, 0], scale: [1.25, 1.6, 1.25] }, quills_b: { rot: [-12, 0, 0], scale: [1.25, 1.5, 1.25] } }],
	[0.56, { chest: [6, 0, 0], neck: [4, 0, 0], jaw: [24, 0, 0], arm_l: [-20, 0, 6], paw_l: [20, 0, 0], arm_r: [-80, 0, 10], forearm_r: [-20, 0, 0],
		quills_f: { rot: [-12, 0, 0], scale: [1.25, 1.6, 1.25] }, quills_b: { rot: [-12, 0, 0], scale: [1.25, 1.5, 1.25] } }],
	[0.7, { chest: [6, 0, 0], neck: [4, 0, 0], jaw: [20, 0, 0], arm_r: [-20, 0, -6], paw_r: [20, 0, 0],
		quills_f: { rot: [-12, 0, 0], scale: [1.25, 1.6, 1.25] }, quills_b: { rot: [-12, 0, 0], scale: [1.25, 1.5, 1.25] } }],
	[0.86, { chest: [-10, 0, 0], neck: [-18, 0, 0], head: [-10, 0, 0], jaw: [42, 0, 0],
		quills_f: { rot: [-12, 0, 0], scale: [1.25, 1.6, 1.25] }, quills_b: { rot: [-12, 0, 0], scale: [1.25, 1.5, 1.25] } }],
	[1.2, {}]])(t);
	add(o, 'head', [0, 14 * Math.sin(t * TAU * 3.5) * Math.sin(Math.PI * clamp01(t / 1.2)), 0]);
	return o;
}, 0.02);

// ---- track: nose to the ground, sweeping side to side for the scent
clipDef('track', 1.2, false, t => {
	const D = { chest: [8, 0, 0], neck: [24, 0, 0], head: [10, 0, 0], arm_r: [-4, 0, 0], arm_l: [-4, 0, 0], forearm_r: [12, 0, 0], forearm_l: [12, 0, 0],
		paw_r: [-8, 0, 0], paw_l: [-8, 0, 0] };
	const o = kclip([[0, {}], [0.25, D], [0.95, D], [1.2, {}]])(t);
	const k = Math.sin(Math.PI * clamp01(t / 1.2));
	add(o, 'head', [-3 * Math.max(0, Math.sin(t * TAU * 5)), 26 * k * Math.sin((t - 0.25) * TAU * 1.4), 0]);
	add(o, 'neck', [0, 8 * k * Math.sin((t - 0.25) * TAU * 1.4), 0]);
	return o;
}, 0.02);

// ---- climb: the body turned up the wall (belly to it), the limbs hauling him up in a crawl (loops)
clipDef('climb', 0.8, true, t => {
	const u = t / 0.8;
	const o = base();
	frontLeg(o, 'r', u + 0.0, 40, 10, 0.5, 1.2);
	frontLeg(o, 'l', u + 0.5, 40, 10, 0.5, 1.2);
	hindLeg(o, 'r', u + 0.5, 30, 14, 0.5, 1.2);
	hindLeg(o, 'l', u + 0.0, 30, 14, 0.5, 1.2);
	add(o, 'arm_r', [-18, 0, 10]); add(o, 'arm_l', [-18, 0, -10]);
	add(o, 'thigh_r', [-24, 0, 8]); add(o, 'thigh_l', [-24, 0, -8]);
	add(o, 'root', [-78, 0, 0], [0, 16.5, -6.5 + 0.4 * Math.sin(TAU * u * 2)]);
	add(o, 'neck', [-14, 0, 0]);
	add(o, 'chest', [0, 0, 4 * Math.sin(TAU * u)]);
	add(o, 'hips', [0, 0, -4 * Math.sin(TAU * u)]);
	add(o, 'tail', [-10, 8 * Math.sin(TAU * u), 0]);
	return o;
}, 0.02);

// ---- the swing controller's claw swipes (one front arm each; Attack alternates)
for (const [n, sgn] of [['r', 1], ['l', -1]]) {
	clipDef(`swipe_${n}`, 0.4, false, t => {
		const o = {};
		const k = keys([[0, [0, 0, 0]], [0.11, [-112, 0, sgn * 16]], [0.22, [-12, 0, -sgn * 12]], [0.4, [0, 0, 0]]], t);
		const f = keys([[0, [0, 0, 0]], [0.11, [-26, 0, 0]], [0.22, [26, 0, 0]], [0.4, [0, 0, 0]]], t);
		const p = keys([[0, [0, 0, 0]], [0.11, [30, 0, 0]], [0.22, [44, 0, 0]], [0.4, [0, 0, 0]]], t);
		o[`arm_${n}`] = { rot: k }; o[`forearm_${n}`] = { rot: f }; o[`paw_${n}`] = { rot: p };
		return o;
	}, 0.02);
}
// ---- the hurt controller's flinch
clipDef('hurt', 0.4, false, t => ({ hurt: { rot: keys([[0, [0, 0, 0]], [0.06, [-9, 0, 4]], [0.18, [3, 0, -2]], [0.4, [0, 0, 0]]], t) } }), 0.02);

function sample(name, t) { return CLIPS[name].fn(Math.min(t, CLIPS[name].len)); }

function build() {
	const animations = {};
	for (const [name, c] of Object.entries(CLIPS)) {
		const bones = {};
		const n = Math.round(c.len / c.step);
		for (let i = 0; i <= n; i++) {
			const t = i === n ? c.len : i * c.step;
			const frame = c.fn(t);
			for (const [b, ch] of Object.entries(frame)) {
				bones[b] = bones[b] || {};
				for (const [k, v] of Object.entries(ch)) {
					if (!v) continue;
					const key = k === 'rot' ? 'rotation' : k === 'pos' ? 'position' : 'scale';
					bones[b][key] = bones[b][key] || {};
					bones[b][key][tkey(t)] = v.map(r3);
				}
			}
		}
		const o = { animation_length: c.len, bones };
		if (c.loop === true) o.loop = true;
		else if (c.loop === 'hold') o.loop = 'hold_on_last_frame';
		animations['animation.projecthero.wildmutt.' + name] = o;
	}
	return { format_version: '1.8.0', animations };
}

module.exports = function (M, A) {
	const file = path.join(A, 'animations', 'omnitrix', 'wildmutt.animation.json');
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(build(), null, '\t') + '\n');
	console.log(`wrote ${Object.keys(CLIPS).length} clips -> ${path.relative(path.join(A, '..', '..', '..', '..', '..'), file)}`);
};
module.exports.sample = sample;
module.exports.CLIPS = CLIPS;
