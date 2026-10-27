// v0.17.1 Dark Shadow revamp: adds the creature's new GeckoLib clips to
// src/main/resources/assets/projecthero/animations/quirk/dark_shadow.animation.json (idempotent: replaces them if present).
//   rampage -- Abyssal Rampage: two horizontal claw sweeps (right, then left) and an overhead two-handed slam, ~0.9 s;
//              contact frames at 0.15 / 0.40 / 0.65 s = server ticks 3 / 8 / 13. Weight: a wind-up before each swing, the
//              body twisting through it, the float bone lunging forward and dipping on the slam, the tail counter-swinging.
//   throw   -- Shadow Toss's release: the held body hurled forward from overhead, 0.5 s.
//   catch   -- the fall catch: wings thrown wide under its user, arms reaching down, then a settle, 0.8 s.
// Run: node scratchpad/gen_dark_shadow_v0171_clips.js
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/animations/quirk/dark_shadow.animation.json');
const d = JSON.parse(fs.readFileSync(file, 'utf8'));

function track(keys) {
	const out = {};
	for (const [t, v] of keys) {
		out[t.toFixed(3)] = { vector: v, easing: 'easeInOutSine' };
	}
	return out;
}
function rot(keys) {
	return { rotation: track(keys) };
}
const hidden = { scale: track([[0, [0.01, 0.01, 0.01]]]) };
const fingers = (side, keys) => {
	const o = {};
	for (let i = 1; i <= 3; i++) {
		o['finger_' + side + i] = rot(keys.map(([t, a]) => [t, [a, 0, 0]]));
		o['talon_' + side + i] = rot(keys.map(([t, a]) => [t, [a * 1.1, 0, 0]]));
	}
	return o;
};

const rampage = {
	animation_length: 0.9,
	bones: Object.assign({
		float: { position: track([[0, [0, 0, 0]], [0.1, [1, 0.5, 1]], [0.15, [-1.5, 0, -4]], [0.3, [-1, 0.5, 0]], [0.4, [1.5, 0, -4]],
			[0.52, [0, 3, 2]], [0.65, [0, -2, -7]], [0.78, [0, -1, -4]], [0.9, [0, 0, 0]]]) },
		spine1: rot([[0, [0, 0, 0]], [0.1, [-8, 10, 0]], [0.15, [10, -14, 0]], [0.3, [-6, -10, 0]], [0.4, [10, 14, 0]], [0.52, [-18, 0, 0]],
			[0.65, [22, 0, 0]], [0.9, [0, 0, 0]]]),
		spine3: rot([[0, [0, 0, 0]], [0.1, [-6, 16, 0]], [0.15, [8, -22, 0]], [0.3, [-4, -16, 0]], [0.4, [8, 22, 0]], [0.52, [-16, 0, 0]],
			[0.65, [18, 0, 0]], [0.9, [0, 0, 0]]]),
		chest: rot([[0, [0, 0, 0]], [0.1, [-10, 28, 0]], [0.15, [6, -30, 0]], [0.3, [-8, -26, 0]], [0.4, [6, 30, 0]], [0.52, [-24, 0, 0]],
			[0.65, [20, 0, 0]], [0.78, [10, 0, 0]], [0.9, [0, 0, 0]]]),
		// the right claw: wound out wide, then swept across
		arm_r: rot([[0, [0, 0, 0]], [0.1, [-120, 75, 55]], [0.15, [-85, -60, 5]], [0.22, [-70, -70, 0]], [0.3, [-50, -20, 0]],
			[0.4, [-60, 10, 0]], [0.52, [-175, 0, 28]], [0.65, [-40, 0, 12]], [0.78, [-30, 0, 8]], [0.9, [0, 0, 0]]]),
		forearm_r: rot([[0, [0, 0, 0]], [0.1, [-50, 0, 0]], [0.15, [-5, 0, 0]], [0.3, [-20, 0, 0]], [0.52, [-35, 0, 0]], [0.65, [-5, 0, 0]],
			[0.9, [0, 0, 0]]]),
		// the left claw: steady through the first swing, then wound out and swept back across
		arm_l: rot([[0, [0, 0, 0]], [0.1, [-60, 0, -20]], [0.15, [-55, 10, -10]], [0.3, [-120, -75, -55]], [0.4, [-85, 60, -5]],
			[0.47, [-70, 70, 0]], [0.52, [-175, 0, -28]], [0.65, [-40, 0, -12]], [0.78, [-30, 0, -8]], [0.9, [0, 0, 0]]]),
		forearm_l: rot([[0, [0, 0, 0]], [0.3, [-50, 0, 0]], [0.4, [-5, 0, 0]], [0.52, [-35, 0, 0]], [0.65, [-5, 0, 0]], [0.9, [0, 0, 0]]]),
		neck1: rot([[0, [0, 0, 0]], [0.1, [-12, 12, 0]], [0.15, [6, -14, 0]], [0.3, [-10, -12, 0]], [0.4, [6, 14, 0]], [0.52, [-22, 0, 0]],
			[0.65, [24, 0, 0]], [0.9, [0, 0, 0]]]),
		neck2: rot([[0, [0, 0, 0]], [0.52, [-12, 0, 0]], [0.65, [16, 0, 0]], [0.9, [0, 0, 0]]]),
		jaw: rot([[0, [0, 0, 0]], [0.12, [38, 0, 0]], [0.2, [12, 0, 0]], [0.37, [38, 0, 0]], [0.45, [12, 0, 0]], [0.6, [45, 0, 0]],
			[0.75, [30, 0, 0]], [0.9, [0, 0, 0]]]),
		hood: rot([[0, [0, 0, 0]], [0.15, [-15, 0, 0]], [0.4, [-15, 0, 0]], [0.65, [-28, 0, 0]], [0.9, [0, 0, 0]]]),
		// the tail counter-swings for balance (the weight of the swing)
		tail: rot([[0, [0, 0, 0]], [0.1, [10, -25, 0]], [0.15, [-8, 30, 0]], [0.3, [10, 25, 0]], [0.4, [-8, -30, 0]], [0.52, [30, 0, 0]],
			[0.65, [-28, 0, 0]], [0.9, [0, 0, 0]]]),
		wings: hidden,
		spikes: hidden,
		spikes_mid: hidden,
		head_spikes: hidden,
	}, fingers('r', [[0, 0], [0.1, -40], [0.15, 45], [0.3, 0], [0.52, -40], [0.65, 50], [0.9, 0]]),
		fingers('l', [[0, 0], [0.3, -40], [0.4, 45], [0.52, -40], [0.65, 50], [0.9, 0]])),
};

const throwClip = {
	animation_length: 0.5,
	bones: Object.assign({
		float: { position: track([[0, [0, 2, 2]], [0.12, [0, 0, -6]], [0.3, [0, -0.5, -4]], [0.5, [0, 0, 0]]]) },
		chest: rot([[0, [-22, 0, 0]], [0.12, [18, 0, 0]], [0.5, [0, 0, 0]]]),
		spine1: rot([[0, [-14, 0, 0]], [0.12, [12, 0, 0]], [0.5, [0, 0, 0]]]),
		arm_r: rot([[0, [-175, 0, 25]], [0.12, [-70, 0, 5]], [0.3, [-40, 0, 0]], [0.5, [0, 0, 0]]]),
		arm_l: rot([[0, [-175, 0, -25]], [0.12, [-70, 0, -5]], [0.3, [-40, 0, 0]], [0.5, [0, 0, 0]]]),
		neck1: rot([[0, [-20, 0, 0]], [0.12, [14, 0, 0]], [0.5, [0, 0, 0]]]),
		jaw: rot([[0, [10, 0, 0]], [0.1, [42, 0, 0]], [0.5, [0, 0, 0]]]),
		tail: rot([[0, [25, 0, 0]], [0.12, [-30, 0, 0]], [0.5, [0, 0, 0]]]),
		wings: hidden,
		spikes: hidden,
		spikes_mid: hidden,
		head_spikes: hidden,
	}, fingers('r', [[0, -40], [0.12, 50], [0.5, 0]]), fingers('l', [[0, -40], [0.12, 50], [0.5, 0]])),
};

const catchClip = {
	animation_length: 0.8,
	bones: Object.assign({
		float: { position: track([[0, [0, 3, 0]], [0.15, [0, -2, 0]], [0.35, [0, 0.5, 0]], [0.8, [0, 0, 0]]]) },
		chest: rot([[0, [30, 0, 0]], [0.15, [12, 0, 0]], [0.8, [0, 0, 0]]]),
		wing_l: rot([[0, [0, -10, 40]], [0.15, [0, 10, -12]], [0.35, [0, 5, 6]], [0.8, [0, 0, 0]]]),
		wing_r: rot([[0, [0, 10, -40]], [0.15, [0, -10, 12]], [0.35, [0, -5, -6]], [0.8, [0, 0, 0]]]),
		wing_l2: rot([[0, [0, 0, 30]], [0.15, [0, 0, -8]], [0.8, [0, 0, 0]]]),
		wing_r2: rot([[0, [0, 0, -30]], [0.15, [0, 0, 8]], [0.8, [0, 0, 0]]]),
		arm_r: rot([[0, [-40, 0, 20]], [0.15, [-20, 0, 35]], [0.8, [0, 0, 0]]]),
		arm_l: rot([[0, [-40, 0, -20]], [0.15, [-20, 0, -35]], [0.8, [0, 0, 0]]]),
		neck1: rot([[0, [25, 0, 0]], [0.15, [10, 0, 0]], [0.8, [0, 0, 0]]]),
		spikes: hidden,
		spikes_mid: hidden,
		head_spikes: hidden,
	}),
};

d.animations['animation.dark_shadow.rampage'] = rampage;
d.animations['animation.dark_shadow.throw'] = throwClip;
d.animations['animation.dark_shadow.catch'] = catchClip;
fs.writeFileSync(file, JSON.stringify(d, null, '\t') + '\n');
console.log('clips:', Object.keys(d.animations).join(', '));
