// v0.17.1 Stinkfly clips (GeckoLib). Each clip is a function t -> { bone: { rot, pos, scale } } sampled into keyframes.
// Rotations use the geometry convention (X > 0 tips a bone's top forward: a hanging limb swings BACK, a segment pointing back
// (+Z) LIFTS; X < 0 reaches a limb forward); positions are model pixels in json space (forward = -Z).
//
// Controllers (StinkflyAnimatable), later ones win on the bones they key:
//   "main"  -- the body: ground idle / walk, hover / fly, and the whole-body moves (dash, dive, take-off, splash-down, lift);
//   "move"  -- the upper-body moves (spit, barrage, net, gas, stab, gust, storm, the attack's tail flick) on the head, jaw, chest,
//              arms and tail only, so they play over whatever the body is doing (on the ground or in the air); never the neck,
//              which holds the flight's head-up;
//   "wings" -- the four wings only: folded, a flutter, the hover buzz, the flight beat, the dive sweep, the gust's great flap;
//   "swing" -- the Attack claw jab on one arm, alternating; "hurt" -- the flinch on its own bone.
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

const ARMS = ['arm_r', 'forearm_r', 'hand_r', 'arm_l', 'forearm_l', 'hand_l'];
const LEGS = ['thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l'];
const TAILB = ['tail1', 'tail2', 'tail3', 'tail4', 'fin'];
const STALKS = ['stalk_r1', 'stalk_l1', 'stalk_r2', 'stalk_l2'];
const WINGS = ['wing_ur', 'wing_ul', 'wing_dr', 'wing_dl'];
// the bones the main controller owns (every main clip keys all of them, so nothing pops between clips)
const MAIN = ['root', 'hips', 'chest', 'neck', 'head', 'jaw', ...STALKS, ...ARMS, ...LEGS, ...TAILB];
// the move controller's bones (a move keys the ones it uses)
const MOVE = ['chest', 'head', 'jaw', ...ARMS, ...TAILB];
// the wings' rest angles (gen_stinkfly.js WINGS): degrees swept back, degrees raised
const WREST = { u: [22, 34], d: [30, -12] };

const base = (set = MAIN) => { const o = {}; for (const b of set) o[b] = { rot: [0, 0, 0] }; if (o.root) o.root.pos = [0, 0, 0]; return o; };
const add = (o, b, rot, pos, scale) => {
	o[b] = o[b] || { rot: [0, 0, 0] };
	if (rot) o[b].rot = (o[b].rot || [0, 0, 0]).map((v, i) => v + rot[i]);
	if (pos) o[b].pos = (o[b].pos || [0, 0, 0]).map((v, i) => v + pos[i]);
	if (scale) o[b].scale = scale;
	return o;
};
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
// a pose-keyed clip over `set`: frames [[t, { bone: rot | {rot,pos} }]], every bone missing from a frame is at rest
function kclip(frames, set = MAIN) {
	const norm = frames.map(([t, f]) => {
		const o = base(set);
		for (const [b, v] of Object.entries(f)) {
			if (!o[b]) continue;
			if (Array.isArray(v)) o[b].rot = v.slice();
			else o[b] = Object.assign({ rot: [0, 0, 0] }, o[b], v);
		}
		return [t, o];
	});
	return t => {
		const out = base(set);
		for (const b of Object.keys(out)) {
			for (const ch of ['rot', 'pos']) {
				if (out[b][ch] === undefined) continue;
				out[b][ch] = keys(norm.map(([tt, o]) => [tt, o[b][ch] || out[b][ch]]), t);
			}
		}
		return out;
	};
}
// the body tipped forward by `deg` about a point `cy` px up (so he leans in place, not about his feet)
function tilt(o, deg, cy = 14) {
	const a = deg * Math.PI / 180;
	add(o, 'root', [deg, 0, 0], [0, cy * (1 - Math.cos(a)), cy * Math.sin(a)]);
}
// the eye-stalks' constant small wobble (each its own phase)
function stalks(o, t, amp = 1) {
	STALKS.forEach((b, i) => add(o, b, [amp * 5 * Math.sin(t * 5.1 + i * 1.7), 0, amp * 4 * Math.sin(t * 3.7 + i * 2.3)]));
}
// the tail's lazy sway
function tailSway(o, t, amp = 1, rate = 1.6) {
	TAILB.forEach((b, i) => add(o, b, [amp * 2.5 * Math.sin(t * rate * TAU * 0.5 - i * 0.7), amp * 4 * Math.sin(t * rate * TAU * 0.35 - i * 0.6), 0]));
}
// the tail's rest curve undone (one straight line) and the whole of it lifted `lift` degrees (- swings it down)
function tailStraight(o, lift = 0, k = 1) {
	add(o, 'tail1', [lift, 0, 0]);
	add(o, 'tail2', [k * 10, 0, 0]); add(o, 'tail3', [k * -6, 0, 0]); add(o, 'tail4', [k * -16, 0, 0]);
}
// the mantis arms held up and forward, claws ready
function armsReady(o, k = 1) {
	add(o, 'arm_r', [-22 * k, 0, 6 * k]); add(o, 'arm_l', [-22 * k, 0, -6 * k]);
	add(o, 'forearm_r', [-18 * k, 0, 0]); add(o, 'forearm_l', [-18 * k, 0, 0]);
	add(o, 'hand_r', [10 * k, 0, 0]); add(o, 'hand_l', [10 * k, 0, 0]);
}
// both wings of a pair at absolute angles (sweep back, raise; + a pitch of the wing plane)
function wing(o, k, sweep, elev, pitch = 0) {
	const [rs, re] = WREST[k];
	for (const [n, s] of [['r', -1], ['l', 1]]) {
		o[`wing_${k}${n}`] = { rot: [pitch, -s * (sweep - rs), -s * (elev - re)] };
	}
	return o;
}

const CLIPS = {};
const clipDef = (name, len, loop, fn, step = 0.04) => { CLIPS[name] = { len, loop, fn, step }; };

// ================================================================ main: on the ground
// ---- idle: standing on his two legs, breathing, the arms held ready and twitching, the stalks and the tail never still
clipDef('idle', 3.0, true, t => {
	const w = TAU * t / 3.0;
	const o = base();
	const br = Math.sin(w * 2);
	add(o, 'root', null, [0, 0.2 * br - 0.2, 0]);
	add(o, 'chest', [-1.5 * br, 0, 0]);
	add(o, 'head', [1.5 * Math.sin(w * 2 + 0.8), 6 * Math.sin(w), 0]);
	add(o, 'jaw', [2 + 2 * Math.max(0, Math.sin(w * 3 + 1)), 0, 0]);
	armsReady(o, 0.7);
	add(o, 'forearm_r', [4 * Math.sin(w * 2), 0, 0]); add(o, 'forearm_l', [4 * Math.sin(w * 2 + 1.1), 0, 0]);
	// a twitch of a claw now and then
	const tw = Math.exp(-Math.pow((fr(t / 1.5) - 0.6) / 0.03, 2));
	add(o, 'hand_r', [12 * tw, 0, 0]);
	add(o, 'thigh_r', [-4, 0, 0]); add(o, 'thigh_l', [-4, 0, 0]); add(o, 'shin_r', [6, 0, 0]); add(o, 'shin_l', [6, 0, 0]);
	add(o, 'foot_r', [-2, 0, 0]); add(o, 'foot_l', [-2, 0, 0]);
	stalks(o, t * 1.3, 1);
	tailSway(o, t, 1, 0.66);
	return o;
});

// ---- walk: a quick insect scuttle on the two thin legs, the body bobbing, arms held ready, the tail swinging (folded wings)
function leg(o, n, u, A, B, duty) {
	u = fr(u);
	let a, lift;
	if (u < duty) { a = lerp(-A, B, u / duty); lift = 0; } else { const k = (u - duty) / (1 - duty); a = lerp(B, -A, ease(k)); lift = Math.sin(Math.PI * k); }
	add(o, `thigh_${n}`, [a - 26 * lift, 0, 0]);
	add(o, `shin_${n}`, [40 * lift, 0, 0]);
	add(o, `foot_${n}`, [-a - 14 * lift, 0, 0]);
	return lift;
}
clipDef('walk', 0.5, true, t => {
	const u = t / 0.5;
	const o = base();
	leg(o, 'r', u, 26, 22, 0.55);
	leg(o, 'l', u + 0.5, 26, 22, 0.55);
	const w = TAU * u;
	add(o, 'root', null, [0, -0.5 + 0.5 * Math.cos(2 * w), 0]);
	add(o, 'hips', [0, 6 * Math.sin(w), 2 * Math.sin(w)]);
	add(o, 'chest', [5 + 1.5 * Math.cos(2 * w), -5 * Math.sin(w), -2 * Math.sin(w)]);
	add(o, 'head', [-4, 3 * Math.sin(w), 0]);
	add(o, 'jaw', [2, 0, 0]);
	armsReady(o, 1);
	add(o, 'arm_r', [5 * Math.sin(w), 0, 0]); add(o, 'arm_l', [-5 * Math.sin(w), 0, 0]);
	stalks(o, t * 2, 1.2);
	TAILB.forEach((b, i) => add(o, b, [3 * Math.cos(2 * w - i * 0.8), 8 * Math.sin(w - i * 0.7), 0]));
	return o;
}, 0.02);

// ================================================================ main: in the air
// ---- hover: hanging in the air on the buzzing wings, a slow bob, the legs dangling and twitching, the arms held ready
clipDef('hover', 1.6, true, t => {
	const w = TAU * t / 1.6;
	const o = base();
	tilt(o, 10);
	add(o, 'root', null, [0, 0.8 * Math.sin(w), 0]);
	add(o, 'neck', [-6, 0, 0]);
	add(o, 'head', [-2 + 2 * Math.sin(w + 1), 5 * Math.sin(w * 0.5), 0]);
	add(o, 'jaw', [2 + 2 * Math.sin(w * 2), 0, 0]);
	armsReady(o, 1.1);
	add(o, 'forearm_r', [5 * Math.sin(w + 0.4), 0, 0]); add(o, 'forearm_l', [5 * Math.sin(w + 1.6), 0, 0]);
	// the legs hang, a little back, toes pointed, each twitching on its own
	for (const [n, ph] of [['r', 0], ['l', 2.1]]) {
		const tw = Math.exp(-Math.pow((fr(t / 1.6 + ph / TAU) - 0.5) / 0.04, 2));
		add(o, `thigh_${n}`, [20 + 4 * Math.sin(w + ph), 0, n === 'r' ? 4 : -4]);
		add(o, `shin_${n}`, [-6 + 10 * tw, 0, 0]);
		add(o, `foot_${n}`, [34 + 6 * Math.sin(w + ph + 1), 0, 0]);
	}
	stalks(o, t * 1.4, 1);
	tailSway(o, t, 1.3, 0.62);
	add(o, 'tail1', [-6, 0, 0]);
	return o;
}, 0.02);

// ---- fly: tipped well forward into the flight, head up to see ahead, the legs trailing, the tail streaming out behind
function flyPose(o, t, deg, len) {
	const w = TAU * t / len;
	tilt(o, deg);
	add(o, 'root', null, [0, 0.6 * Math.sin(w * 2), 0]);
	add(o, 'neck', [-deg * 0.55, 0, 0]);
	add(o, 'head', [-deg * 0.25, 0, 0]);
	add(o, 'jaw', [3, 0, 0]);
	add(o, 'arm_r', [-95, 0, 8]); add(o, 'arm_l', [-95, 0, -8]); // (the body's tip swings them back: this holds them out ahead)
	add(o, 'forearm_r', [30, 0, 0]); add(o, 'forearm_l', [30, 0, 0]);
	add(o, 'hand_r', [12, 0, 0]); add(o, 'hand_l', [12, 0, 0]);
	for (const [n, ph] of [['r', 0], ['l', 1.3]]) {
		add(o, `thigh_${n}`, [40 + 4 * Math.sin(w + ph), 0, n === 'r' ? 3 : -3]);
		add(o, `shin_${n}`, [-24 + 6 * Math.sin(w + ph + 0.8), 0, 0]);
		add(o, `foot_${n}`, [30, 0, 0]);
	}
	tailStraight(o, 28 - deg); // 6 degrees under the horizontal
	TAILB.forEach((b, i) => add(o, b, [4 * Math.sin(w * 2 - i * 0.9), 3 * Math.sin(w - i * 0.8), 0]));
	STALKS.forEach((b, i) => add(o, b, [-16 + 3 * Math.sin(t * 9 + i), 0, 0])); // swept back by the wind
	return o;
}
clipDef('fly', 0.8, true, t => flyPose(base(), t, 62, 0.8), 0.02);

// ---- dash (Shift+X): flat out, everything tucked
clipDef('dash', 0.7, false, t => {
	const k = keys([[0, 0], [0.1, 1], [0.55, 1], [0.7, 0]], t);
	const o = flyPose(base(), t, 62 + 22 * k, 0.4);
	add(o, 'arm_r', [-30 * k, 0, -4 * k]); add(o, 'arm_l', [-30 * k, 0, 4 * k]);
	add(o, 'forearm_r', [40 * k, 0, 0]); add(o, 'forearm_l', [40 * k, 0, 0]);
	add(o, 'thigh_r', [16 * k, 0, 0]); add(o, 'thigh_l', [16 * k, 0, 0]);
	return o;
}, 0.02);

// ---- dive (Z): head-first plunge, the arms and claws reaching ahead, legs and tail straight behind (held while he dives)
clipDef('dive', 0.5, 'hold', t => {
	const k = keys([[0, 0], [0.25, 1], [0.5, 1]], t);
	const o = base();
	tilt(o, 130 * k);
	add(o, 'neck', [-40 * k, 0, 0]);
	add(o, 'head', [-12 * k, 0, 0]);
	add(o, 'jaw', [14 * k, 0, 0]);
	add(o, 'arm_r', [-168 * k, 0, 10 * k]); add(o, 'arm_l', [-168 * k, 0, -10 * k]);
	add(o, 'forearm_r', [50 * k, 0, 0]); add(o, 'forearm_l', [50 * k, 0, 0]);
	add(o, 'hand_r', [10 * k, 0, 0]); add(o, 'hand_l', [10 * k, 0, 0]);
	add(o, 'thigh_r', [30 * k, 0, 4 * k]); add(o, 'thigh_l', [30 * k, 0, -4 * k]);
	add(o, 'shin_r', [-14 * k, 0, 0]); add(o, 'shin_l', [-14 * k, 0, 0]);
	add(o, 'foot_r', [40 * k, 0, 0]); add(o, 'foot_l', [40 * k, 0, 0]);
	tailStraight(o, -56 * k, k); // straight out behind along the body
	STALKS.forEach(b => add(o, b, [-30 * k, 0, 0]));
	return o;
}, 0.02);

// ---- takeoff (X, and the Dive Bomb's hop): a crouch, then a spring up off the legs, the arms thrown up
clipDef('takeoff', 0.7, false, t => {
	const crouch = { root: { rot: [0, 0, 0], pos: [0, -2.4, 0] }, chest: [10, 0, 0], neck: [6, 0, 0],
		thigh_r: [-40, 0, 0], thigh_l: [-40, 0, 0], shin_r: [62, 0, 0], shin_l: [62, 0, 0], foot_r: [-22, 0, 0], foot_l: [-22, 0, 0],
		arm_r: [-10, 0, 20], arm_l: [-10, 0, -20], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0], tail1: [-10, 0, 0] };
	const spring = { root: { rot: [0, 0, 0], pos: [0, 1.5, 0] }, chest: [-8, 0, 0], neck: [-10, 0, 0], head: [-8, 0, 0], jaw: [16, 0, 0],
		thigh_r: [24, 0, 0], thigh_l: [24, 0, 0], shin_r: [4, 0, 0], shin_l: [4, 0, 0], foot_r: [40, 0, 0], foot_l: [40, 0, 0],
		arm_r: [-120, 0, 24], arm_l: [-120, 0, -24], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], tail1: [-18, 0, 0], tail2: [-8, 0, 0] };
	const o = kclip([[0, {}], [0.16, crouch], [0.3, spring], [0.48, spring], [0.7, {}]])(t);
	stalks(o, t * 3, 1.5);
	return o;
}, 0.02);

// ---- splash (the Dive Bomb's landing): slammed down into a crouch, arms braced, then back up
clipDef('splash', 0.7, false, t => {
	const hit = { root: { rot: [0, 0, 0], pos: [0, -3.2, -0.5] }, chest: [22, 0, 0], neck: [-10, 0, 0], head: [-8, 0, 0], jaw: [24, 0, 0],
		thigh_r: [-46, 0, 10], thigh_l: [-46, 0, -10], shin_r: [70, 0, 0], shin_l: [70, 0, 0], foot_r: [-24, 0, 0], foot_l: [-24, 0, 0],
		arm_r: [-40, 0, 38], arm_l: [-40, 0, -38], forearm_r: [10, 0, 0], forearm_l: [10, 0, 0], hand_r: [30, 0, 0], hand_l: [30, 0, 0],
		tail1: [-20, 0, 0], tail2: [-10, 0, 0], tail3: [-6, 0, 0] };
	const o = kclip([[0, hit], [0.08, hit], [0.32, hit], [0.7, {}]])(t);
	const shake = t < 0.3 ? Math.sin(t * TAU * 12) * (1 - t / 0.3) : 0;
	add(o, 'chest', [2 * shake, 0, 0]);
	stalks(o, t * 4, 2);
	return o;
}, 0.02);

// ---- lift (V): the legs and claws reach down for the creature below / in front; lift_hold: clutching it, legs curled under
const CLUTCH = { thigh_r: [-58, 0, 12], thigh_l: [-58, 0, -12], shin_r: [78, 0, 0], shin_l: [78, 0, 0], foot_r: [26, 0, 0], foot_l: [26, 0, 0],
	arm_r: [-6, 0, 14], arm_l: [-6, 0, -14], forearm_r: [30, 0, 0], forearm_l: [30, 0, 0], hand_r: [40, 0, 0], hand_l: [40, 0, 0],
	chest: [10, 0, 0], neck: [-12, 0, 0], head: [-4, 0, 0], tail1: [8, 0, 0] };
clipDef('lift', 0.6, false, t => {
	const reach = Object.assign({}, CLUTCH, { thigh_r: [-70, 0, 20], thigh_l: [-70, 0, -20], shin_r: [30, 0, 0], shin_l: [30, 0, 0],
		arm_r: [-20, 0, 26], arm_l: [-20, 0, -26], chest: [24, 0, 0], neck: [-6, 0, 0], jaw: [18, 0, 0] });
	const o = kclip([[0, {}], [0.2, reach], [0.4, CLUTCH], [0.6, CLUTCH]])(t);
	tilt(o, 10 * keys([[0, 0], [0.2, 1], [0.6, 1]], t));
	return o;
}, 0.02);
clipDef('lift_hold', 1.6, true, t => {
	const w = TAU * t / 1.6;
	const o = kclip([[0, CLUTCH], [1.6, CLUTCH]])(t);
	tilt(o, 10);
	add(o, 'root', null, [0, 0.8 * Math.sin(w), 0]);
	add(o, 'shin_r', [4 * Math.sin(w * 2), 0, 0]); add(o, 'shin_l', [4 * Math.sin(w * 2 + 1), 0, 0]);
	stalks(o, t, 1);
	tailSway(o, t, 0.8, 0.62);
	return o;
}, 0.02);

// ================================================================ move: the upper-body moves
const MV = MOVE;
// ---- shot (R, and the Use goo spit): the head snaps forward, the jaw flies open, the goo is spat; the arms pull back
clipDef('shot', 0.45, false, kclip([
	[0, {}],
	[0.08, { head: [-12, 0, 0], jaw: [6, 0, 0], chest: [-4, 0, 0], arm_r: [-10, 0, 12], arm_l: [-10, 0, -12] }],
	[0.16, { head: [16, 0, 0], jaw: [34, 0, 0], chest: [6, 0, 0], arm_r: [8, 0, 18], arm_l: [8, 0, -18], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0] }],
	[0.26, { head: [10, 0, 0], jaw: [22, 0, 0], chest: [4, 0, 0], arm_r: [4, 0, 14], arm_l: [4, 0, -14] }],
	[0.45, {}],
], MV), 0.02);
// ---- barrage (Shift+C): five quick spits, the head sweeping across the fan
clipDef('barrage', 0.5, false, t => {
	const o = kclip([[0, {}], [0.06, { chest: [4, 0, 0], arm_r: [-6, 0, 16], arm_l: [-6, 0, -16] }], [0.42, { chest: [4, 0, 0], arm_r: [-6, 0, 16], arm_l: [-6, 0, -16] }], [0.5, {}]], MV)(t);
	const k = Math.sin(Math.PI * clamp01(t / 0.5));
	add(o, 'head', [8 * k, 26 * Math.sin((t / 0.5) * TAU * 0.5 - Math.PI / 2) * k, 0]);
	add(o, 'jaw', [k * (8 + 22 * Math.max(0, Math.sin(t * TAU * 10))), 0, 0]);
	return o;
}, 0.02);
// ---- net (Shift+R): rears back, the jaw thrown wide, and heaves a great glob forward, the arms flung out
clipDef('net', 0.75, false, kclip([
	[0, {}],
	[0.18, { chest: [-16, 0, 0], head: [-22, 0, 0], jaw: [12, 0, 0], arm_r: [-60, 0, 36], arm_l: [-60, 0, -36], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0],
		tail1: [10, 0, 0] }],
	[0.3, { chest: [16, 0, 0], head: [20, 0, 0], jaw: [42, 0, 0], arm_r: [-10, 0, 50], arm_l: [-10, 0, -50], forearm_r: [20, 0, 0], forearm_l: [20, 0, 0],
		hand_r: [30, 0, 0], hand_l: [30, 0, 0], tail1: [-14, 0, 0] }],
	[0.46, { chest: [12, 0, 0], head: [14, 0, 0], jaw: [30, 0, 0], arm_r: [-10, 0, 40], arm_l: [-10, 0, -40], forearm_r: [14, 0, 0], forearm_l: [14, 0, 0] }],
	[0.75, {}],
], MV), 0.02);
// ---- gas (G): hunched, the tail swung down and forward under him, the abdomen pumping out the stink cloud
const GAS = { chest: [14, 0, 0], head: [8, 0, 0], jaw: [10, 0, 0], arm_r: [-14, 0, 30], arm_l: [-14, 0, -30], forearm_r: [10, 0, 0], forearm_l: [10, 0, 0],
	tail1: [-62, 0, 0], tail2: [-30, 0, 0], tail3: [-14, 0, 0], tail4: [10, 0, 0], fin: [20, 0, 0] };
clipDef('gas', 1.2, false, t => {
	const o = kclip([[0, {}], [0.2, GAS], [1.0, GAS], [1.2, {}]], MV)(t);
	const k = t > 0.2 && t < 1.0 ? Math.sin((t - 0.2) * TAU * 4) : 0;
	add(o, 'tail1', [3 * k, 0, 0]); add(o, 'tail2', [4 * k, 0, 0]); add(o, 'tail3', [5 * k, 0, 0]);
	add(o, 'chest', [1.5 * k, 0, 0]);
	return o;
}, 0.02);
// ---- stab (Shift+G): the tail cocks up behind him, then whips down and forward under the body to drive the stinger in
clipDef('stab', 0.6, false, kclip([
	[0, {}],
	[0.12, { chest: [-8, 0, 0], tail1: [26, 0, 0], tail2: [16, 0, 0], tail3: [10, 0, 0], tail4: [6, 0, 0], arm_r: [-30, 0, 20], arm_l: [-30, 0, -20] }],
	[0.22, { chest: [14, 0, 0], head: [10, 0, 0], jaw: [20, 0, 0], tail1: [-96, 0, 0], tail2: [-24, 0, 0], tail3: [-8, 0, 0], tail4: [-4, 0, 0], fin: [-10, 0, 0],
		arm_r: [-10, 0, 30], arm_l: [-10, 0, -30] }],
	[0.36, { chest: [12, 0, 0], head: [8, 0, 0], jaw: [12, 0, 0], tail1: [-92, 0, 0], tail2: [-22, 0, 0], tail3: [-8, 0, 0], tail4: [-4, 0, 0], fin: [-10, 0, 0],
		arm_r: [-10, 0, 26], arm_l: [-10, 0, -26] }],
	[0.6, {}],
], MV), 0.02);
// ---- jab (Attack on a creature): a quick flick of the stinger forward under him
clipDef('jab', 0.35, false, kclip([
	[0, {}],
	[0.08, { tail1: [-56, 0, 0], tail2: [-14, 0, 0], fin: [-6, 0, 0] }],
	[0.16, { tail1: [-50, 0, 0], tail2: [-12, 0, 0] }],
	[0.35, {}],
], ['tail1', 'tail2', 'tail3', 'tail4', 'fin']), 0.02);
// ---- gust (Shift+Z): rears back with the arms flung wide while the wings give one great beat
clipDef('gust', 0.7, false, kclip([
	[0, {}],
	[0.16, { chest: [-14, 0, 0], head: [-12, 0, 0], jaw: [10, 0, 0], arm_r: [-70, 0, 64], arm_l: [-70, 0, -64], forearm_r: [-14, 0, 0], forearm_l: [-14, 0, 0] }],
	[0.3, { chest: [10, 0, 0], head: [6, 0, 0], jaw: [24, 0, 0], arm_r: [-80, 0, 20], arm_l: [-80, 0, -20], forearm_r: [24, 0, 0], forearm_l: [24, 0, 0],
		hand_r: [20, 0, 0], hand_l: [20, 0, 0] }],
	[0.45, { chest: [8, 0, 0], head: [4, 0, 0], jaw: [16, 0, 0], arm_r: [-74, 0, 22], arm_l: [-74, 0, -22], forearm_r: [20, 0, 0], forearm_l: [20, 0, 0] }],
	[0.7, {}],
], MV), 0.02);
// ---- storm (C): arms thrown up, head back, the jaw chattering as he sprays goo high into the air to rain down
clipDef('storm', 1.3, false, t => {
	const UP = { chest: [-14, 0, 0], head: [-34, 0, 0], jaw: [30, 0, 0], arm_r: [-150, 0, 22], arm_l: [-150, 0, -22], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0],
		hand_r: [-10, 0, 0], hand_l: [-10, 0, 0], tail1: [-10, 0, 0] };
	const o = kclip([[0, {}], [0.22, UP], [1.05, UP], [1.3, {}]], MV)(t);
	if (t > 0.2 && t < 1.08) {
		add(o, 'jaw', [10 * Math.sin(t * TAU * 8), 0, 0]);
		add(o, 'head', [3 * Math.sin(t * TAU * 8 + 1), 6 * Math.sin(t * TAU * 1.6), 0]);
		add(o, 'arm_r', [6 * Math.sin(t * TAU * 3), 0, 0]); add(o, 'arm_l', [6 * Math.sin(t * TAU * 3 + 2), 0, 0]);
	}
	return o;
}, 0.02);

// ================================================================ wings
const WK = (fn, ...a) => t => fn({}, t, ...a);
// ---- folded: swept back along his back over the tail, the lower pair tucked under; now and then a quick flutter
const FOLD = { u: [78, 4], d: [80, -20] };
function folded(o, t, flutterEvery, amp = 1) {
	const ph = fr(t / flutterEvery);
	const fl = ph > 0.75 ? Math.sin((ph - 0.75) / 0.25 * TAU * 3) * Math.sin((ph - 0.75) / 0.25 * Math.PI) : 0;
	wing(o, 'u', FOLD.u[0] - 8 * amp * Math.abs(fl), FOLD.u[1] + 10 * amp * fl, 4);
	wing(o, 'd', FOLD.d[0] - 6 * amp * Math.abs(fl), FOLD.d[1] + 8 * amp * fl, 4);
	return o;
}
clipDef('wings_fold', 2.4, true, WK(folded, 2.4, 1), 0.02);
clipDef('wings_walk', 0.6, true, t => {
	const o = folded({}, t, 1.2, 0.6);
	const v = Math.sin(t / 0.6 * TAU * 4);
	add(o, 'wing_ur', [0, 0, 3 * v]); add(o, 'wing_ul', [0, 0, -3 * v]);
	return o;
}, 0.02);
// ---- the hover buzz: a fast, blurry beat (the upper and lower pairs out of step, like a dragonfly)
function beat(o, t, len, ampE, ampS, liftE = 0, sweepAdd = 0) {
	const w = TAU * t / len;
	wing(o, 'u', WREST.u[0] + sweepAdd + ampS * Math.sin(w + 0.8), WREST.u[1] + liftE + ampE * Math.sin(w), 0);
	wing(o, 'd', WREST.d[0] + sweepAdd + ampS * Math.sin(w + Math.PI + 0.8), WREST.d[1] + liftE * 0.6 + ampE * 0.8 * Math.sin(w + Math.PI), 0);
	return o;
}
clipDef('wings_hover', 0.15, true, WK(beat, 0.15, 30, 12), 0.025);
// ---- the flight beat: bigger, a touch slower, the wings lowered to spread flat out to the sides of the tipped body (a
// dragonfly's) and swept a little further back
clipDef('wings_fly', 0.2, true, t => {
	const w = TAU * t / 0.2;
	const o = {};
	wing(o, 'u', 30 + 12 * Math.sin(w + 0.8), 6 + 36 * Math.sin(w), 0);
	wing(o, 'd', 40 + 12 * Math.sin(w + Math.PI + 0.8), -16 + 30 * Math.sin(w + Math.PI), 0);
	return o;
}, 0.025);
// ---- the dive: swept back tight, shivering in the wind
clipDef('wings_dive', 0.2, true, t => {
	const v = Math.sin(TAU * t / 0.1);
	const o = {};
	wing(o, 'u', 72 + 2 * v, 14 + 3 * v, 6);
	wing(o, 'd', 76 - 2 * v, -6 - 3 * v, 6);
	return o;
}, 0.025);
// ---- the gust's great flap: raised high and back, then one huge downstroke forward
clipDef('wings_gust', 0.7, false, t => {
	const o = {};
	const e = keys([[0, 34], [0.16, 78], [0.3, -40], [0.46, -30], [0.7, 34]], t);
	const sw = keys([[0, 22], [0.16, 40], [0.3, -10], [0.46, 0], [0.7, 22]], t);
	wing(o, 'u', sw, e, 0);
	wing(o, 'd', sw + 8, e * 0.7 - 14, 0);
	return o;
}, 0.02);

// ================================================================ swing: the Attack claw jab, one arm
for (const [n, sgn] of [['r', 1], ['l', -1]]) {
	clipDef(`jab_${n}`, 0.35, false, t => {
		const o = {};
		o[`arm_${n}`] = { rot: keys([[0, [0, 0, 0]], [0.08, [-30, 0, sgn * 18]], [0.16, [-68, 0, -sgn * 4]], [0.35, [0, 0, 0]]], t) };
		o[`forearm_${n}`] = { rot: keys([[0, [0, 0, 0]], [0.08, [-30, 0, 0]], [0.16, [46, 0, 0]], [0.35, [0, 0, 0]]], t) };
		o[`hand_${n}`] = { rot: keys([[0, [0, 0, 0]], [0.08, [-10, 0, 0]], [0.16, [14, 0, 0]], [0.35, [0, 0, 0]]], t) };
		return o;
	}, 0.02);
}
// ---- the hurt controller's flinch
clipDef('hurt', 0.4, false, t => ({ hurt: { rot: keys([[0, [0, 0, 0]], [0.06, [-9, 0, 4]], [0.18, [3, 0, -2]], [0.4, [0, 0, 0]]], t) } }), 0.02);

function sample(name, t) { return CLIPS[name].fn(Math.min(t, CLIPS[name].len)); }

function build(M) {
	const animations = {};
	for (const [name, c] of Object.entries(CLIPS)) {
		const bones = {};
		const n = Math.round(c.len / c.step);
		for (let i = 0; i <= n; i++) {
			const t = i === n ? c.len : i * c.step;
			const frame = c.fn(t);
			for (const [b, ch] of Object.entries(frame)) {
				if (M && !M.B[b]) throw new Error(`clip ${name} keys a missing bone ${b}`);
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
		animations['animation.projecthero.stinkfly.' + name] = o;
	}
	return { format_version: '1.8.0', animations };
}

module.exports = function (M, A) {
	const file = path.join(A, 'animations', 'omnitrix', 'stinkfly.animation.json');
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(build(M), null, '\t') + '\n');
	console.log(`wrote ${Object.keys(CLIPS).length} clips -> animations/omnitrix/stinkfly.animation.json`);
};
module.exports.sample = sample;
module.exports.CLIPS = CLIPS;
module.exports.WINGS = WINGS;
