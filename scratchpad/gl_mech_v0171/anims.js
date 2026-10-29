// v0.17.1 Hard-Light Mech clips (GeckoLib): idle, walk, punch_r / punch_l, rocket_fist, ground_slam, beam, shield, dash,
// missiles, jump, assemble, dissolve. Each clip is a function t -> { bone: { rot, pos, scale } } sampled into keyframes.
// Rotations use the geometry convention (X > 0 tips a bone's top forward: a hanging arm swings BACK; X < 0 swings it
// forward); positions are model pixels in json space (forward = -Z).
'use strict';
const fs = require('fs');
const path = require('path');

const TAU = Math.PI * 2;
const r3 = v => Math.round(v * 1000) / 1000;
const ease = t => { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); };
const easeOut = t => { t = Math.max(0, Math.min(1, t)); return 1 - (1 - t) * (1 - t); };
const tkey = t => { const s = String(Math.round(t * 1000) / 1000); return s.includes(".") ? s : s + ".0"; }; // "1.0", not "1": JS lists integer keys first and GeckoLib reads them in order
const lerp = (a, b, k) => a + (b - a) * k;
const lerp3 = (a, b, k) => a.map((v, i) => lerp(v, b[i], k));
// piecewise pose keys: [[t, value], ...] -> value at time (smooth between keys)
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

// the stance every clip starts from: slightly crouched, fists a little forward, shoulders squared
// (v0.17.1 Gurren rework: a prouder stance -- the chest face up and out, the head lifted, the feet planted wider)
const STANCE = {
	pelvis: [0, 0, 0], torso: [1, 0, 0], head: [-3, 0, 0],
	arm_r: [-6, 0, 6], arm_l: [-6, 0, -6], forearm_r: [-14, 0, 0], forearm_l: [-14, 0, 0],
	leg_r: [-6, 0, 4], leg_l: [-6, 0, -4], shin_r: [10, 0, 0], shin_l: [10, 0, 0], foot_r: [-4, 0, 0], foot_l: [-4, 0, 0],
};
const base = () => { const o = {}; for (const [b, r] of Object.entries(STANCE)) o[b] = { rot: r.slice() }; return o; };
const add = (o, b, rot, pos, scale) => {
	o[b] = o[b] || { rot: [0, 0, 0] };
	if (rot) o[b].rot = (o[b].rot || [0, 0, 0]).map((v, i) => v + rot[i]);
	if (pos) o[b].pos = (o[b].pos || [0, 0, 0]).map((v, i) => v + pos[i]);
	if (scale) o[b].scale = scale;
	return o;
};

const CLIPS = {};
const clipDef = (name, len, loop, fn, step = 0.05) => { CLIPS[name] = { len, loop, fn, step }; };

// ---- idle: a breathing hum -- the chest rises, the shoulders lift, the fists flex
clipDef('idle', 3.2, true, t => {
	const w = TAU * t / 3.2;
	const o = base();
	add(o, 'root', null, [0, -0.35 + 0.35 * Math.sin(w), 0]);
	add(o, 'torso', [1.6 * Math.sin(w), 0, 0]);
	add(o, 'head', [-1.5 * Math.sin(w + 0.6), 3 * Math.sin(w * 0.5), 0]);
	add(o, 'shoulder_r', [0, 0, -1.5 * Math.sin(w)]);
	add(o, 'shoulder_l', [0, 0, 1.5 * Math.sin(w)]);
	add(o, 'arm_r', [1.5 * Math.sin(w + 0.4), 0, 1.2 * Math.sin(w)]);
	add(o, 'arm_l', [1.5 * Math.sin(w + 0.4), 0, -1.2 * Math.sin(w)]);
	add(o, 'forearm_r', [-2 * Math.sin(w + 1), 0, 0]);
	add(o, 'forearm_l', [-2 * Math.sin(w + 1), 0, 0]);
	add(o, 'thrusters', [1.2 * Math.sin(w + 2), 0, 0]);
	add(o, 'leg_r', [-0.8 * Math.sin(w), 0, 0]); add(o, 'leg_l', [-0.8 * Math.sin(w), 0, 0]);
	add(o, 'shin_r', [1.6 * Math.sin(w), 0, 0]); add(o, 'shin_l', [1.6 * Math.sin(w), 0, 0]);
	add(o, 'foot_r', [-0.8 * Math.sin(w), 0, 0]); add(o, 'foot_l', [-0.8 * Math.sin(w), 0, 0]);
	return o;
});

// ---- walk: heavy stomps. 0.6 s cycle = two steps; the body drops and the torso twists on each footfall
clipDef('walk', 0.6, true, t => {
	const w = TAU * t / 0.6;
	const o = base();
	const sw = Math.sin(w), cw = Math.cos(w);
	// legs: thigh swing, the knee bends on the lift (the leg moving forward), the foot stays level
	const leg = (n, ph) => {
		const s = Math.sin(w + ph), c = Math.cos(w + ph);
		const lift = Math.max(0, c); // forward-moving half
		add(o, `leg_${n}`, [-26 * s, 0, 0]);
		add(o, `shin_${n}`, [10 + 38 * lift * lift, 0, 0]);
		add(o, `foot_${n}`, [26 * s - 30 * lift * lift * 0.6, 0, 0]);
	};
	leg('r', 0); leg('l', Math.PI);
	// the body bobs down twice per cycle (each footfall), the torso counter-twists
	add(o, 'root', null, [0, -2.2 * Math.abs(cw) + 0.4, 0]);
	add(o, 'pelvis', [0, 7 * sw, 2.5 * cw]);
	add(o, 'torso', [4, -11 * sw, -2 * cw]);
	add(o, 'head', [0, 6 * sw, 0]);
	add(o, 'arm_r', [22 * sw, 0, 2]);
	add(o, 'arm_l', [-22 * sw, 0, -2]);
	add(o, 'forearm_r', [-10 - 8 * Math.max(0, -sw), 0, 0]);
	add(o, 'forearm_l', [-10 - 8 * Math.max(0, sw), 0, 0]);
	add(o, 'thrusters', [3 * Math.abs(cw), 0, 0]);
	return o;
}, 0.025);

// ---- punches: wind back, drive the fist forward along the look, recover
const punch = (n, other, dirZ) => t => {
	const o = base();
	const T = [[0, 0], [0.12, -1], [0.22, 1], [0.32, 1], [0.5, 0]];
	const k = keys(T, t); // -1 wound up, +1 extended
	const wind = Math.max(0, -k), ext = Math.max(0, k);
	add(o, 'torso', [6 * ext, dirZ * (22 * wind - 30 * ext), 0]);
	add(o, 'pelvis', [0, dirZ * (-6 * wind + 8 * ext), 0]);
	add(o, `arm_${n}`, [30 * wind - 82 * ext, dirZ * -6 * ext, -dirZ * (8 * wind + 6 * ext)]);
	add(o, `forearm_${n}`, [-60 * wind + 6 * ext, 0, 0]);
	add(o, `fist_${n}`, null, [0, 0, -3 * ext]);
	add(o, `arm_${other}`, [-30 * ext + 10 * wind, 0, 0]);
	add(o, `forearm_${other}`, [-55 * ext, 0, 0]);
	add(o, 'head', [0, dirZ * (-14 * wind + 20 * ext), 0]);
	add(o, 'root', null, [0, -1.2 * ext, -1.5 * ext]);
	return o;
};
// right arm is on -X: twisting the torso to drive the right fist forward is +Y in this convention (checked on the sheet)
clipDef('punch_r', 0.5, false, punch('r', 'l', 1));
clipDef('punch_l', 0.5, false, punch('l', 'r', -1));

// ---- rocket fist: arm raised forward, the forearm fires off (scale 0 while the hard-light fist flies), comes back
clipDef('rocket_fist', 1.5, false, t => {
	const o = base();
	const aim = keys([[0, 0], [0.18, 1], [1.3, 1], [1.5, 0]], t);
	const kick = keys([[0.18, 0], [0.24, 1], [0.45, 0]], t);
	add(o, 'arm_r', [-88 * aim, 0, 4 * aim]);
	add(o, 'forearm_r', [12 * aim, 0, 0]);
	add(o, 'torso', [-4 * kick, 14 * aim, 0]);
	add(o, 'root', null, [0, 0, 2.5 * kick]);
	add(o, 'arm_l', [-24 * aim, 0, -10 * aim]);
	add(o, 'forearm_l', [-50 * aim, 0, 0]);
	const gone = t > 0.22 && t < 1.18;
	add(o, 'forearm_r', null, null, gone ? [0, 0, 0] : [1, 1, 1]);
	return o;
}, 0.02);

// ---- ground slam: both fists overhead, jump-crouch, hammer the ground
clipDef('ground_slam', 1.2, false, t => {
	const o = base();
	const up = keys([[0, 0], [0.35, 1], [0.45, 1], [0.55, -1], [0.85, -1], [1.2, 0]], t);
	const raise = Math.max(0, up), slam = Math.max(0, -up);
	for (const [n, s] of [['r', 1], ['l', -1]]) {
		add(o, `arm_${n}`, [-165 * raise - 70 * slam, 0, s * (10 * raise - 14 * slam)]);
		add(o, `forearm_${n}`, [-40 * raise + 10 * slam, 0, 0]);
		add(o, `leg_${n}`, [-38 * slam - 6 * raise, 0, s * 4 * slam]);
		add(o, `shin_${n}`, [62 * slam + 8 * raise, 0, 0]);
		add(o, `foot_${n}`, [-26 * slam, 0, 0]);
	}
	add(o, 'torso', [-12 * raise + 40 * slam, 0, 0]);
	add(o, 'head', [8 * raise - 20 * slam, 0, 0]);
	add(o, 'root', null, [0, 3 * raise - 14 * slam, -3 * slam]);
	add(o, 'thrusters', [-10 * raise, 0, 0]);
	return o;
}, 0.025);

// ---- beam: the chest pushed out, shoulders back, arms braced wide (loop with a strain tremble)
clipDef('beam', 0.8, true, t => {
	const o = base();
	const tr = Math.sin(TAU * t / 0.8 * 3) * 1.2;
	add(o, 'torso', [-12 + tr * 0.5, 0, 0]);
	add(o, 'head', [10, 0, 0]);
	add(o, 'jaw', [26 + tr * 1.5, 0, 0]); // v0.17.1 Gurren rework: the chest face roars the beam out of its open jaw
	add(o, 'arm_r', [24, 0, 26 + tr]);
	add(o, 'arm_l', [24, 0, -26 - tr]);
	add(o, 'forearm_r', [-50, 0, 0]);
	add(o, 'forearm_l', [-50, 0, 0]);
	add(o, 'shoulder_r', [0, 0, 6]); add(o, 'shoulder_l', [0, 0, -6]);
	add(o, 'leg_r', [-22, 0, 4]); add(o, 'leg_l', [16, 0, -4]);
	add(o, 'shin_r', [20, 0, 0]); add(o, 'shin_l', [10, 0, 0]);
	add(o, 'foot_r', [2, 0, 0]); add(o, 'foot_l', [-24, 0, 0]);
	add(o, 'root', null, [0, -1.5 + 0.2 * tr, 0]);
	return o;
});

// ---- shield: the left forearm raised across the front as the wall forms (loop)
clipDef('shield', 1.0, true, t => {
	const o = base();
	const b = Math.sin(TAU * t) * 0.8;
	add(o, 'arm_l', [-62, 0, -30 + b]);
	add(o, 'forearm_l', [-78, 0, 0]);
	add(o, 'arm_r', [-20, 0, 8]);
	add(o, 'forearm_r', [-70, 0, 0]);
	add(o, 'torso', [8, -10, 0]);
	add(o, 'head', [-4, 6, 0]);
	add(o, 'leg_r', [12, 0, 2]); add(o, 'leg_l', [-22, 0, -2]);
	add(o, 'shin_l', [16, 0, 0]);
	add(o, 'root', null, [0, -1.5 + 0.3 * b, 0]);
	return o;
});

// ---- dash: a hard forward lean, arms swept back, thrusters angled down-back
clipDef('dash', 0.6, false, t => {
	const o = base();
	const k = keys([[0, 0], [0.08, 1], [0.42, 1], [0.6, 0]], t);
	add(o, 'pelvis', [16 * k, 0, 0]);
	add(o, 'torso', [12 * k, 0, 0]);
	add(o, 'head', [-28 * k, 0, 0]);
	add(o, 'arm_r', [55 * k, 0, 18 * k]); add(o, 'arm_l', [55 * k, 0, -18 * k]);
	add(o, 'forearm_r', [-20 * k, 0, 0]); add(o, 'forearm_l', [-20 * k, 0, 0]);
	add(o, 'leg_r', [-36 * k, 0, 0]); add(o, 'leg_l', [-4 * k, 0, 0]);
	add(o, 'shin_r', [30 * k, 0, 0]); add(o, 'shin_l', [40 * k, 0, 0]);
	add(o, 'thrusters', [-30 * k, 0, 0]);
	return o;
}, 0.02);

// ---- missiles: the pods flip up and recoil twice, the shoulders brace
clipDef('missiles', 1.0, false, t => {
	const o = base();
	const open = keys([[0, 0], [0.15, 1], [0.8, 1], [1.0, 0]], t);
	const kick = Math.max(0, Math.sin(Math.min(1, Math.max(0, (t - 0.2) / 0.5)) * Math.PI * 3)) * open;
	for (const [n, s] of [['r', 1], ['l', -1]]) {
		add(o, `pod_${n}`, [-38 * open + 6 * kick, 0, 0], [0, 1.5 * open, 0]);
		add(o, `shoulder_${n}`, [3 * kick, 0, 0]);
		add(o, `arm_${n}`, [-16 * open, 0, s * 14 * open]);
		add(o, `forearm_${n}`, [-36 * open, 0, 0]);
	}
	add(o, 'torso', [-6 * open + 3 * kick, 0, 0]);
	add(o, 'root', null, [0, -0.5 * kick, 0.8 * kick]);
	return o;
}, 0.025);

// ---- jump: crouch, then spring up with the thrusters
clipDef('jump', 0.7, false, t => {
	const o = base();
	const c = keys([[0, 0], [0.12, 1], [0.25, -0.6], [0.55, -0.4], [0.7, 0]], t);
	const crouch = Math.max(0, c), ext = Math.max(0, -c);
	for (const n of ['r', 'l']) {
		add(o, `leg_${n}`, [-34 * crouch + 8 * ext, 0, 0]);
		add(o, `shin_${n}`, [56 * crouch + 20 * ext, 0, 0]);
		add(o, `foot_${n}`, [-22 * crouch + 18 * ext, 0, 0]);
		add(o, `arm_${n}`, [30 * crouch - 40 * ext, 0, 0]);
	}
	add(o, 'torso', [18 * crouch - 8 * ext, 0, 0]);
	add(o, 'root', null, [0, -9 * crouch, 0]);
	add(o, 'thrusters', [-14 * ext, 0, 0]);
	return o;
}, 0.025);

// ---- assemble (2.0 s): the core forms first, then every plate snaps out of it into place (overshoot, settle),
// root to tip, with the pieces flying in from outward
const ORDER = [
	['pelvis', 0.0, [0, 0, 0]], ['torso', 0.18, [0, 8, 0]], ['leg_r', 0.32, [-6, -4, 0]], ['leg_l', 0.36, [6, -4, 0]],
	['shin_r', 0.5, [-4, -8, -4]], ['shin_l', 0.54, [4, -8, -4]], ['foot_r', 0.66, [0, -6, -6]], ['foot_l', 0.7, [0, -6, -6]],
	['thrusters', 0.62, [0, 0, 12]], ['shoulder_r', 0.78, [-14, 8, 0]], ['shoulder_l', 0.82, [14, 8, 0]],
	['arm_r', 0.9, [-10, 0, 0]], ['arm_l', 0.94, [10, 0, 0]], ['forearm_r', 1.04, [-6, -6, -6]], ['forearm_l', 1.08, [6, -6, -6]],
	['fist_r', 1.18, [0, -4, -8]], ['fist_l', 1.22, [0, -4, -8]], ['pod_r', 1.3, [0, 10, 0]], ['pod_l', 1.34, [0, 10, 0]],
	['shades', 1.38, [0, 8, -6]], ['head', 1.42, [0, 14, -4]], // v0.17.1 Gurren rework: + the shades
];
const SNAP = 0.22;
// v0.17.1 (user: "make it build pixel by pixel piece by piece"): the parts no longer fly in and scale up -- they stay in
// place while the renderer forms them texel by texel, legs to torso to arms to head (HardLightMechRenderer), so this
// clip only holds the mech in its build crouch, the head bowed, and then has it straighten up once whole.
clipDef('assemble', 2.0, 'hold', t => {
	const o = base();
	const bow = 1 - ease((t - 1.6) / 0.4);
	add(o, 'head', [10 * bow, 0, 0]);
	add(o, 'arm_r', [6 * bow, 0, 4 * bow]);
	add(o, 'arm_l', [6 * bow, 0, -4 * bow]);
	// once whole, the mech straightens up from a crouch and the thrusters flare
	const stand = ease((t - 1.6) / 0.4);
	add(o, 'root', null, [0, -6 * (1 - stand), 0]);
	add(o, 'torso', [14 * (1 - stand), 0, 0]);
	return o;
}, 0.02);

// ---- dissolve (1.0 s, held): the reverse -- tips first, each part swelling slightly and sinking into light
clipDef('dissolve', 1.0, 'hold', t => {
	const o = base();
	const n = ORDER.length;
	ORDER.slice().reverse().forEach(([b], i) => {
		const t0 = (i / n) * 0.7;
		const u = Math.max(0, Math.min(1, (t - t0) / 0.25));
		const sc = u <= 0 ? 1 : u >= 1 ? 0.001 : 1 + 0.15 * Math.sin(u * Math.PI) - u * 0.999 * ease(u);
		add(o, b, null, [0, 4 * u, 0], [Math.max(0.001, sc), Math.max(0.001, sc), Math.max(0.001, sc)]);
	});
	add(o, 'root', null, [0, -4 * ease(t), 0]);
	return o;
}, 0.02);

// ================================================================ v0.17.1 construct combos: Giga Drill + Hard-Light Katana
// Keyframed pose clips: each key is a full pose (the stance with the key's bones overridden, absolute rotations /
// positions), eased between keys. The katana sits in fist_r along the fist's -Z (forward when the arm hangs), edge
// down; fist_r is the wrist that sets the blade's angle. The drill replaces the right fist (the renderer hides it) and
// points down the forearm (-Y), so arm_r -90 aims it straight ahead; the model adds the pilot's aim pitch on top.
const BONES = ['root', 'pelvis', 'torso', 'head', 'thrusters', 'shoulder_r', 'shoulder_l', 'pod_r', 'pod_l', 'arm_r', 'arm_l',
	'forearm_r', 'forearm_l', 'fist_r', 'fist_l', 'leg_r', 'leg_l', 'shin_r', 'shin_l', 'foot_r', 'foot_l'];
function fullPose(stance, over) {
	const P = {};
	for (const b of BONES) P[b] = { rot: (stance[b] || [0, 0, 0]).slice(), pos: [0, 0, 0] };
	for (const [b, v] of Object.entries(over || {})) {
		if (Array.isArray(v)) P[b].rot = v.slice();
		else { if (v.rot) P[b].rot = v.rot.slice(); if (v.pos) P[b].pos = v.pos.slice(); }
	}
	return P;
}
function kclip(stance, keyList, extra) {
	const ks = keyList.map(([t, over]) => [t, fullPose(stance, over)]);
	return t => {
		let a = ks[0], b = ks[0];
		for (let i = 0; i < ks.length - 1; i++) if (t >= ks[i][0]) { a = ks[i]; b = ks[i + 1]; }
		if (t >= ks[ks.length - 1][0]) a = b = ks[ks.length - 1];
		const k = a === b ? 0 : ease((t - a[0]) / Math.max(1e-6, b[0] - a[0]));
		const o = {};
		for (const bn of BONES) {
			const rot = lerp3(a[1][bn].rot, b[1][bn].rot, k), pos = lerp3(a[1][bn].pos, b[1][bn].pos, k);
			o[bn] = { rot };
			if (pos.some(v => Math.abs(v) > 1e-4)) o[bn].pos = pos;
		}
		if (extra) extra(o, t);
		return o;
	};
}

// the katana stance (chudan-ish): right fist forward at the hip line, blade angled up and forward, left arm on guard,
// feet apart with the left foot forward
const KSTANCE = Object.assign({}, STANCE, {
	pelvis: [0, -8, 0], torso: [6, 12, 0], head: [-6, -10, 0],
	arm_r: [-42, 0, -14], forearm_r: [-38, 0, 0], fist_r: [36, 0, 0],
	arm_l: [-34, 0, -4], forearm_l: [-62, 0, 0],
	leg_r: [-2, 0, 6], leg_l: [-22, 0, -6], shin_r: [16, 0, 0], shin_l: [22, 0, 0], foot_r: [-12, 0, 0], foot_l: [0, 0, 0],
});
const kbase = () => { const o = {}; for (const [b, r] of Object.entries(KSTANCE)) o[b] = { rot: r.slice() }; return o; };
// the hand at the left hip, blade back along the hip (where the draw starts and the sheathe ends)
const HIP = { arm_r: [-22, 0, -40], forearm_r: [-78, 0, 0], fist_r: [-70, 0, 0], torso: [8, -22, 0], head: [-4, 12, 0] };

clipDef('katana_idle', 3.2, true, t => {
	const w = TAU * t / 3.2;
	const o = kbase();
	add(o, 'root', null, [0, -0.6 + 0.35 * Math.sin(w), 0]);
	add(o, 'torso', [1.4 * Math.sin(w), 0, 0]);
	add(o, 'head', [-1.2 * Math.sin(w + 0.6), 2 * Math.sin(w * 0.5), 0]);
	add(o, 'arm_r', [1.2 * Math.sin(w + 0.4), 0, 0]);
	add(o, 'fist_r', [-1.5 * Math.sin(w + 1.2), 0, 0]);
	add(o, 'arm_l', [1.2 * Math.sin(w + 0.4), 0, 0]);
	add(o, 'shin_r', [1.2 * Math.sin(w), 0, 0]); add(o, 'shin_l', [1.2 * Math.sin(w), 0, 0]);
	return o;
});

clipDef('katana_walk', 0.6, true, t => {
	const w = TAU * t / 0.6;
	const o = kbase();
	const sw = Math.sin(w), cw = Math.cos(w);
	const leg = (n, ph) => {
		const s = Math.sin(w + ph), c = Math.cos(w + ph);
		const lift = Math.max(0, c);
		o[`leg_${n}`] = { rot: [-24 * s, 0, n === 'r' ? 3 : -3] };
		o[`shin_${n}`] = { rot: [10 + 36 * lift * lift, 0, 0] };
		o[`foot_${n}`] = { rot: [24 * s - 18 * lift * lift, 0, 0] };
	};
	leg('r', 0); leg('l', Math.PI);
	add(o, 'root', null, [0, -2.0 * Math.abs(cw) + 0.2, 0]);
	o.pelvis = { rot: [0, 6 * sw - 4, 2 * cw] };
	add(o, 'torso', [0, -6 * sw, 0]);
	add(o, 'arm_r', [3 * Math.abs(cw), 0, 0]);
	add(o, 'fist_r', [-3 * Math.abs(cw), 0, 0]);
	add(o, 'arm_l', [-10 * sw, 0, 0]);
	return o;
}, 0.025);

// draw (0.6 s = the 12-tick KATANA_DRAW_TICKS): reach across to the left hip, pull the blade out in a rising sweep, settle
clipDef('katana_draw', 0.6, false, kclip(STANCE, [
	[0, {}],
	[0.16, Object.assign({}, HIP, { leg_l: [-14, 0, -4], shin_l: [18, 0, 0], leg_r: [-8, 0, 4], shin_r: [14, 0, 0] })],
	[0.38, { arm_r: [-118, 0, 30], forearm_r: [-12, 0, 0], fist_r: [-4, 0, 0], torso: [-4, 26, 0], head: [-8, -8, 0], arm_l: [-20, 0, -18], forearm_l: [-40, 0, 0] }],
	[0.6, KSTANCE],
]), 0.02);

// sheathe (0.5 s): a flick of the blade out to the side (chiburi), then back to the hip -- the renderer slides it into light
clipDef('katana_sheath', 0.5, false, kclip(KSTANCE, [
	[0, {}],
	[0.14, { arm_r: [-40, 0, 46], forearm_r: [-18, 0, 0], fist_r: [10, 0, 0], torso: [4, 20, 0] }],
	[0.36, Object.assign({}, HIP)],
	[0.5, STANCE],
]), 0.02);

// R 1 (0.45 s, the edge lands at 0.2): kesa -- from high over the left shoulder down across to the low right
clipDef('katana_slash_1', 0.45, false, kclip(KSTANCE, [
	[0, {}],
	[0.1, { arm_r: [-168, 0, 30], forearm_r: [-10, 0, 0], fist_r: [28, 0, 0], torso: [-6, -26, 0], head: [-6, 14, 0], pelvis: [0, -12, 0] }],
	[0.24, { arm_r: [-34, 0, 40], forearm_r: [-8, 0, 0], fist_r: [22, 0, 0], torso: [16, 30, 0], head: [-10, -18, 0], pelvis: [0, 10, 0],
		root: { rot: [0, 0, 0], pos: [0, -2.5, -3] }, leg_l: [-32, 0, -6], shin_l: [34, 0, 0], leg_r: [8, 0, 6], shin_r: [18, 0, 0] }],
	[0.3, { arm_r: [-30, 0, 44], forearm_r: [-6, 0, 0], fist_r: [26, 0, 0], torso: [17, 32, 0], head: [-10, -18, 0], pelvis: [0, 10, 0],
		root: { rot: [0, 0, 0], pos: [0, -2.5, -3] }, leg_l: [-32, 0, -6], shin_l: [34, 0, 0], leg_r: [8, 0, 6], shin_r: [18, 0, 0] }],
	[0.45, {}],
]), 0.02);

// R 2 (0.45 s, lands at 0.2): the rising reverse cut -- low right up across to high left
clipDef('katana_slash_2', 0.45, false, kclip(KSTANCE, [
	[0, {}],
	[0.1, { arm_r: [-18, 0, 44], forearm_r: [-10, 0, 0], fist_r: [44, 0, 0], torso: [12, 28, 0], head: [-8, -14, 0], pelvis: [0, 8, 0] }],
	[0.24, { arm_r: [-150, 0, 34], forearm_r: [-20, 0, 0], fist_r: [-30, 0, 0], torso: [-10, -30, 0], head: [-2, 16, 0], pelvis: [0, -12, 0],
		root: { rot: [0, 0, 0], pos: [0, 0.5, -2] }, leg_r: [-26, 0, 6], shin_r: [28, 0, 0] }],
	[0.3, { arm_r: [-154, 0, 36], forearm_r: [-22, 0, 0], fist_r: [-32, 0, 0], torso: [-10, -31, 0], head: [-2, 16, 0], pelvis: [0, -12, 0],
		root: { rot: [0, 0, 0], pos: [0, 0.5, -2] }, leg_r: [-26, 0, 6], shin_r: [28, 0, 0] }],
	[0.45, {}],
]), 0.02);

// R 3 (0.7 s, lands at 0.3): the wide horizontal finisher -- the blade turned flat (wrist Y), wound far back right,
// swept right across the front to the left with a deep step-in
clipDef('katana_slash_3', 0.7, false, kclip(KSTANCE, [
	[0, {}],
	[0.16, { arm_r: [-8, 0, 86], forearm_r: [0, 0, 0], fist_r: [72, 0, 0], torso: [2, -40, 0], head: [-4, 26, 0], pelvis: [0, -14, 0],
		arm_l: [-50, 0, -30], forearm_l: [-50, 0, 0], root: { rot: [0, 0, 0], pos: [0, -1.5, 1] } }],
	[0.3, { arm_r: [-14, 0, 86], forearm_r: [0, 0, 0], fist_r: [-64, 0, 0], torso: [12, 44, 0], head: [-6, -26, 0], pelvis: [0, 16, 0],
		arm_l: [10, 0, -24], forearm_l: [-30, 0, 0], root: { rot: [0, 0, 0], pos: [0, -4, -5] },
		leg_l: [-40, 0, -8], shin_l: [44, 0, 0], foot_l: [-4, 0, 0], leg_r: [20, 0, 6], shin_r: [24, 0, 0] }],
	[0.42, { arm_r: [-14, 0, 86], forearm_r: [0, 0, 0], fist_r: [-70, 0, 0], torso: [12, 46, 0], head: [-6, -26, 0], pelvis: [0, 16, 0],
		arm_l: [10, 0, -24], forearm_l: [-30, 0, 0], root: { rot: [0, 0, 0], pos: [0, -4, -5] },
		leg_l: [-40, 0, -8], shin_l: [44, 0, 0], foot_l: [-4, 0, 0], leg_r: [20, 0, 6], shin_r: [24, 0, 0] }],
	[0.7, {}],
]), 0.02);

// Shift+R (0.8 s, the crescent leaves at 0.3): iaido -- crouch, hand to the hip, an explosive flat draw out to the right,
// hold the follow-through (zanshin), back to the stance
clipDef('katana_iaido', 0.8, false, kclip(KSTANCE, [
	[0, {}],
	[0.14, Object.assign({}, HIP, { root: { rot: [0, 0, 0], pos: [0, -5, 0] }, leg_l: [-34, 0, -6], shin_l: [52, 0, 0], foot_l: [-16, 0, 0],
		leg_r: [6, 0, 6], shin_r: [40, 0, 0], foot_r: [-30, 0, 0] })],
	[0.28, { arm_r: [-12, 0, 86], forearm_r: [0, 0, 0], fist_r: [34, 0, 0], torso: [8, 30, 0], head: [-6, -12, 0], pelvis: [0, 12, 0],
		arm_l: [12, 0, -30], forearm_l: [-20, 0, 0], root: { rot: [0, 0, 0], pos: [0, -4, -6] },
		leg_l: [-44, 0, -8], shin_l: [46, 0, 0], foot_l: [-2, 0, 0], leg_r: [26, 0, 6], shin_r: [22, 0, 0], foot_r: [-12, 0, 0] }],
	[0.56, { arm_r: [-12, 0, 87], forearm_r: [0, 0, 0], fist_r: [36, 0, 0], torso: [9, 31, 0], head: [-6, -12, 0], pelvis: [0, 12, 0],
		arm_l: [12, 0, -30], forearm_l: [-20, 0, 0], root: { rot: [0, 0, 0], pos: [0, -4, -6] },
		leg_l: [-44, 0, -8], shin_l: [46, 0, 0], foot_l: [-2, 0, 0], leg_r: [26, 0, 6], shin_r: [22, 0, 0], foot_r: [-12, 0, 0] }],
	[0.8, {}],
]), 0.02);

// G (0.9 s, the sweep lands at 0.35): the whole mech spins once on the spot, the blade held straight out to the right
clipDef('katana_spin', 0.9, false, kclip(KSTANCE, [
	[0, {}],
	[0.1, { arm_r: [-14, 0, 84], forearm_r: [0, 0, 0], fist_r: [6, 0, 0], torso: [6, -24, 0], arm_l: [-40, 0, -40], forearm_l: [-70, 0, 0],
		root: { rot: [0, 30, 0], pos: [0, -3, 0] }, leg_l: [-22, 0, -14], leg_r: [-4, 0, 14], shin_l: [26, 0, 0], shin_r: [26, 0, 0] }],
	[0.5, { arm_r: [-12, 0, 86], forearm_r: [0, 0, 0], fist_r: [6, 0, 0], torso: [6, 10, 0], arm_l: [-40, 0, -40], forearm_l: [-70, 0, 0],
		root: { rot: [0, -330, 0], pos: [0, -3, 0] }, leg_l: [-22, 0, -14], leg_r: [-4, 0, 14], shin_l: [26, 0, 0], shin_r: [26, 0, 0] }],
	[0.62, { arm_r: [-20, 0, 70], forearm_r: [-10, 0, 0], fist_r: [10, 0, 0], torso: [6, 8, 0], arm_l: [-40, 0, -30], forearm_l: [-70, 0, 0],
		root: { rot: [0, -360, 0], pos: [0, -2, 0] }, leg_l: [-22, 0, -10], leg_r: [-4, 0, 10], shin_l: [22, 0, 0], shin_r: [22, 0, 0] }],
	[0.9, { root: { rot: [0, -360, 0], pos: [0, 0, 0] } }],
]), 0.02);

// ---- Giga Drill: spin-up (loop, braced, the drill arm straight out), the charge (loop, a hard forward lean behind the
// drill), and the GIGA DRILL BREAK thrust (an action clip over the charge pose)
const DRILL_SPIN = Object.assign({}, STANCE, {
	pelvis: [0, 10, 0], torso: [6, 18, 0], head: [-8, -16, 0],
	arm_r: [-90, 0, 6], forearm_r: [0, 0, 0], fist_r: [0, 0, 0],
	arm_l: [14, 0, -26], forearm_l: [-46, 0, 0],
	leg_r: [-26, 0, 6], leg_l: [20, 0, -8], shin_r: [30, 0, 0], shin_l: [16, 0, 0], foot_r: [-4, 0, 0], foot_l: [-34, 0, 0],
});
clipDef('drill_spin', 0.4, true, t => {
	const o = {};
	for (const [b, r] of Object.entries(DRILL_SPIN)) o[b] = { rot: r.slice() };
	const tr = Math.sin(TAU * t / 0.4 * 3);
	add(o, 'arm_r', [0.8 * tr, 0, 0.8 * tr]);
	add(o, 'torso', [0.5 * tr, 0, 0]);
	add(o, 'root', null, [0.25 * tr, -2 + 0.2 * tr, 0]);
	return o;
}, 0.02);
const DRILL_CHARGE = Object.assign({}, STANCE, {
	pelvis: [24, 8, 0], torso: [12, 14, 0], head: [-34, -10, 0],
	arm_r: [-126, 0, 4], forearm_r: [0, 0, 0], fist_r: [0, 0, 0],
	arm_l: [46, 0, -18], forearm_l: [-30, 0, 0],
	leg_r: [-54, 0, 4], shin_r: [62, 0, 0], foot_r: [-14, 0, 0],
	leg_l: [26, 0, -4], shin_l: [22, 0, 0], foot_l: [10, 0, 0], thrusters: [-34, 0, 0],
});
clipDef('drill_charge', 0.3, true, t => {
	const o = {};
	for (const [b, r] of Object.entries(DRILL_CHARGE)) o[b] = { rot: r.slice() };
	const tr = Math.sin(TAU * t / 0.3 * 2);
	add(o, 'root', null, [0, -4 + 0.6 * tr, -2]);
	add(o, 'arm_r', [0.6 * tr, 0, 0]);
	add(o, 'head', [0.8 * tr, 0, 0]);
	return o;
}, 0.02);
clipDef('drill_break', 0.9, false, kclip(DRILL_CHARGE, [
	[0, { root: { rot: [0, 0, 0], pos: [0, -4, -2] } }],
	[0.08, { arm_r: [-112, 0, 4], torso: [6, 8, 0], root: { rot: [0, 0, 0], pos: [0, -3, 2] } }],
	[0.18, { arm_r: [-130, 0, 2], torso: [18, 22, 0], pelvis: [30, 10, 0], root: { rot: [0, 0, 0], pos: [0, -6, -8] },
		leg_r: [-66, 0, 4], shin_r: [70, 0, 0], leg_l: [34, 0, -4], shin_l: [14, 0, 0] }],
	[0.55, { arm_r: [-130, 0, 2], torso: [18, 22, 0], pelvis: [30, 10, 0], root: { rot: [0, 0, 0], pos: [0, -6, -8] },
		leg_r: [-66, 0, 4], shin_r: [70, 0, 0], leg_l: [34, 0, -4], shin_l: [14, 0, 0] }],
	[0.9, Object.assign({}, STANCE, { pelvis: [0, 0, 0], thrusters: [0, 0, 0] })],
]), 0.02);

// ================================================================ v0.17.1 round 2 mech combos (user: "Give the mech other mech
// construct combinations too, at least 3 more ... make the animations good aswell and make it cool")

// METEOR MACE (1.5 s, the head lands at 0.75 = MACE_IMPACT_TICKS): the mace forms low in the right fist, both hands take
// it up high overhead with a lean back and a rise onto the toes, then the whole body drives it down in front -- a deep
// crouch, the torso folded over the impact -- holds the follow-through a beat and recovers. The mace runs down fist_r's
// -Y (it extends the arm), so overhead it points up behind the head and in the smash forward and down.
const MACE_UP = { arm_r: [-172, 0, 14], forearm_r: [-24, 0, 0], fist_r: [-10, 0, 0], arm_l: [-168, 0, -16], forearm_l: [-30, 0, 0],
	fist_l: [0, 0, 0], torso: [-16, 0, 0], head: [-12, 0, 0], pelvis: [-4, 0, 0], root: { rot: [0, 0, 0], pos: [0, 1.5, 1] },
	leg_r: [4, 0, 6], leg_l: [-10, 0, -6], shin_r: [6, 0, 0], shin_l: [10, 0, 0], foot_r: [-8, 0, 0], foot_l: [0, 0, 0],
	thrusters: [10, 0, 0] };
const MACE_DOWN = { arm_r: [-46, 0, 8], forearm_r: [-6, 0, 0], fist_r: [-8, 0, 0], arm_l: [-48, 0, -10], forearm_l: [-10, 0, 0],
	fist_l: [0, 0, 0], torso: [34, 0, 0], head: [-26, 0, 0], pelvis: [10, 0, 0], root: { rot: [0, 0, 0], pos: [0, -8, -4] },
	leg_r: [-40, 0, 8], shin_r: [52, 0, 0], foot_r: [-12, 0, 0], leg_l: [-28, 0, -8], shin_l: [44, 0, 0], foot_l: [-16, 0, 0],
	thrusters: [-24, 0, 0] };
clipDef('mace_smash', 1.5, false, kclip(STANCE, [
	[0, {}],
	[0.16, { arm_r: [-34, 0, 10], forearm_r: [-40, 0, 0], fist_r: [10, 0, 0], torso: [6, -10, 0], root: { rot: [0, 0, 0], pos: [0, -1.5, 0] } }],
	[0.5, MACE_UP],
	[0.6, Object.assign({}, MACE_UP, { arm_r: [-180, 0, 14], arm_l: [-176, 0, -16], torso: [-20, 0, 0] })],
	[0.75, MACE_DOWN],
	[0.98, Object.assign({}, MACE_DOWN, { torso: [36, 0, 0], root: { rot: [0, 0, 0], pos: [0, -8.5, -4] } })],
	[1.5, {}],
], (o, t) => {
	// the impact jolt
	const j = t > 0.75 && t < 0.9 ? Math.sin((t - 0.75) / 0.15 * Math.PI) : 0;
	add(o, 'root', null, [0, -1.2 * j, 0]);
}), 0.02);

// WRECKING WHIRL (2.0 s): the right arm swings out wide to the side as the ball forms on its chain, a twist and a wide
// stance, then the whole mech spins two full turns on the spot (0.4-1.7 s, the hits), leaning back against the ball's
// pull, and unwinds.
const WHIRL = { arm_r: [-16, 0, 82], forearm_r: [-4, 0, 0], fist_r: [0, 0, 0], arm_l: [-24, 0, -58], forearm_l: [-40, 0, 0],
	torso: [-8, 0, 0], head: [-6, 0, 0], pelvis: [0, 0, 0], leg_r: [-6, 0, 16], leg_l: [-6, 0, -16], shin_r: [20, 0, 0], shin_l: [20, 0, 0],
	foot_r: [-12, 0, 0], foot_l: [-12, 0, 0] };
clipDef('wrecking_whirl', 2.0, false, kclip(STANCE, [
	[0, {}],
	[0.24, Object.assign({}, WHIRL, { torso: [4, 34, 0], arm_r: [-10, 0, 70], root: { rot: [0, 0, 0], pos: [0, -3, 0] } })],
	[0.4, Object.assign({}, WHIRL, { root: { rot: [0, 0, 0], pos: [0, -3.5, 0] } })],
	[1.7, Object.assign({}, WHIRL, { root: { rot: [0, 0, 0], pos: [0, -3.5, 0] } })],
	[2.0, {}],
], (o, t) => {
	// the spin itself, linear (so the ball keeps its speed): two turns between 0.4 s and 1.7 s
	const k = Math.max(0, Math.min(1, (t - 0.4) / 1.3));
	const ang = -720 * k;
	o.root = o.root || { rot: [0, 0, 0] };
	o.root.rot = [o.root.rot[0], o.root.rot[1] + ang, o.root.rot[2]];
	// a bob with each turn
	add(o, 'root', null, [0, -0.6 * Math.abs(Math.sin(k * TAU * 2)), 0]);
}), 0.02);

// LANTERN CANNON (1.6 s, fires at 0.9 = CANNON_FIRE_TICKS): the left arm comes up level along the aim as the cannon forms
// round it, the body turned side-on behind it and the right hand bracing the barrel, a tremble while it charges, a
// big recoil that throws the arm up and rocks the mech back, then back down.
const CANNON_AIM = { arm_l: [-90, 0, -2], forearm_l: [0, 0, 0], fist_l: [0, 0, 0], arm_r: [-74, 0, -34], forearm_r: [-46, 0, 0],
	fist_r: [0, 0, 0], torso: [2, 20, 0], head: [-4, -16, 0], pelvis: [0, 10, 0], leg_l: [-22, 0, -6], shin_l: [22, 0, 0],
	foot_l: [0, 0, 0], leg_r: [14, 0, 8], shin_r: [16, 0, 0], foot_r: [-14, 0, 0], root: { rot: [0, 0, 0], pos: [0, -2, 0] } };
clipDef('lantern_cannon', 1.6, false, kclip(STANCE, [
	[0, {}],
	[0.3, CANNON_AIM],
	[0.88, CANNON_AIM],
	[0.96, Object.assign({}, CANNON_AIM, { arm_l: [-118, 0, -2], forearm_l: [-12, 0, 0], arm_r: [-60, 0, -26], torso: [-10, 26, 0],
		head: [-12, -18, 0], root: { rot: [0, 0, 0], pos: [0, -1, 4] }, thrusters: [12, 0, 0] })],
	[1.2, Object.assign({}, CANNON_AIM, { arm_l: [-96, 0, -2] })],
	[1.6, {}],
], (o, t) => {
	// the charge tremble, growing
	if (t > 0.3 && t < 0.88) {
		const k = (t - 0.3) / 0.58;
		const tr = Math.sin(TAU * t * 14) * 0.7 * k;
		add(o, 'arm_l', [tr, 0, tr * 0.5]);
		add(o, 'root', null, [0.2 * tr, 0, 0]);
	}
}), 0.02);

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
					const key = k === 'rot' ? 'rotation' : k === 'pos' ? 'position' : 'scale';
					bones[b][key] = bones[b][key] || {};
					bones[b][key][tkey(t)] = v.map(r3);
				}
			}
		}
		// every bone a clip touches keeps a full channel set, so nothing pops when clips change
		const o = { animation_length: c.len, bones };
		if (c.loop === true) o.loop = true;
		else if (c.loop === 'hold') o.loop = 'hold_on_last_frame';
		animations['animation.green_lantern_mech.' + name] = o;
	}
	return { format_version: '1.8.0', animations };
}

module.exports = function (M, A, ROOT) {
	const file = path.join(A, 'animations', 'green_lantern_mech.animation.json');
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(build(), null, '\t') + '\n');
	console.log(`wrote ${Object.keys(CLIPS).length} clips`);
};
module.exports.sample = sample;
module.exports.CLIPS = CLIPS;
