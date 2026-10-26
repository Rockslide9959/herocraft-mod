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
const tkey = t => String(Math.round(t * 1000) / 1000);
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
const STANCE = {
	pelvis: [0, 0, 0], torso: [4, 0, 0], head: [-4, 0, 0],
	arm_r: [-6, 0, 6], arm_l: [-6, 0, -6], forearm_r: [-14, 0, 0], forearm_l: [-14, 0, 0],
	leg_r: [-6, 0, 2], leg_l: [-6, 0, -2], shin_r: [10, 0, 0], shin_l: [10, 0, 0], foot_r: [-4, 0, 0], foot_l: [-4, 0, 0],
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
	['head', 1.42, [0, 14, -4]],
];
const SNAP = 0.22;
clipDef('assemble', 2.0, 'hold', t => {
	const o = base();
	for (const [b, t0, from] of ORDER) {
		const u = (t - t0) / SNAP;
		let sc, off;
		if (u <= 0) { sc = 0.001; off = from; }
		else if (u < 1) { const e = easeOut(u); sc = 0.25 + 0.95 * e; off = from.map(v => v * (1 - e)); }
		else { const v = Math.min(1, (u - 1) / 0.6); sc = 1.2 - 0.2 * ease(v); off = [0, 0, 0]; }
		add(o, b, null, off, [sc, sc, sc]);
	}
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
