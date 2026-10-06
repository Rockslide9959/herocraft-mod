// v0.15.3: adds the Gladiator Hulk's move animations to hulk.animation.json (re-runnable: replaces its own clips).
// Axe in the LEFT hand, hammer in the RIGHT (the gear agent's gladiator_axe / gladiator_hammer bones ride on the arms;
// these clips only key the existing bones). Every clip keys every bone it touches at its first and last frame --
// GeckoLib leaves an un-keyed bone at whatever the last clip left there.
// Sign conventions (from the existing clips): body/head +X = lean forward, leg +X = foot back, arm -X = raised forward,
// right-arm +Z = out to the side (left arm mirrored: -Z), body +Y = right shoulder forward.
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/animations/hulk.animation.json';
const text = fs.readFileSync(file, 'utf8');
const json = JSON.parse(text);

const NEUTRAL = {
	root: [0, 0, 0], body: [8, 0, 0], head: [-8, 0, 0],
	right_arm: [-4, 0, 12], left_arm: [-4, 0, -12], right_leg: [0, 0, 0], left_leg: [0, 0, 0],
};
const BONES = Object.keys(NEUTRAL);
const k = t => (Math.round(t * 100) / 100).toFixed(2);

/**
 * frames: [{ t, root?, body?, head?, right_arm?, left_arm?, right_leg?, left_leg? }]. A bone missing from a frame is
 * not keyed there (it interpolates); every bone is keyed at the first and last frame (neutral unless given).
 */
function clip(name, length, frames, opts = {}) {
	const bones = {};
	for (const b of BONES) {
		const ch = b === 'root' ? 'position' : 'rotation';
		bones[b] = { [ch]: {} };
	}
	const first = frames[0].t;
	const last = frames[frames.length - 1].t;
	for (const f of frames) {
		for (const b of BONES) {
			let v = f[b];
			if (v === undefined && (f.t === first || f.t === last) && !opts.noNeutralEnds) v = NEUTRAL[b];
			if (v === undefined && (f.t === first || f.t === last)) v = NEUTRAL[b];
			if (v === undefined) continue;
			const ch = b === 'root' ? 'position' : 'rotation';
			bones[b][ch][k(f.t)] = v.map(x => Math.round(x * 100) / 100);
		}
	}
	const a = { animation_length: length, bones };
	if (opts.loop) a.loop = opts.loop;
	json.animations['animation.hulk.' + name] = a;
}

// ---- R Axe Cleave (impact 0.30 s = 6 t): the left arm wound out and back, swept round in front ----
clip('gladiator_axe_cleave', 0.65, [
	{ t: 0 },
	{ t: 0.15, body: [4, 45, 0], head: [-8, -30, 0], left_arm: [-70, -30, -85], right_arm: [-10, 0, 22], root: [0, -0.4, 0],
		right_leg: [-8, 0, 0], left_leg: [10, 0, 0] },
	{ t: 0.3, body: [16, -50, 0], head: [-12, 32, 0], left_arm: [-95, 40, -20], right_arm: [12, 0, 22], root: [0, -1.0, 0],
		right_leg: [16, 0, 0], left_leg: [-20, 0, 0] },
	{ t: 0.45, body: [14, -45, 0], head: [-10, 28, 0], left_arm: [-70, 50, -14], right_arm: [8, 0, 20], root: [0, -0.8, 0],
		right_leg: [14, 0, 0], left_leg: [-18, 0, 0] },
	{ t: 0.65 },
]);

// ---- Shift+R Hammer Uppercut (impact 0.30 s): crouch, the hammer low behind him, then up and over ----
clip('gladiator_hammer_uppercut', 0.65, [
	{ t: 0 },
	{ t: 0.15, body: [32, -22, 0], head: [-22, 14, 0], right_arm: [45, 0, 22], left_arm: [-30, 0, -20], root: [0, -1.8, 0],
		right_leg: [-24, 0, 0], left_leg: [-24, 0, 0] },
	{ t: 0.3, body: [-16, 26, 0], head: [-26, -16, 0], right_arm: [-165, 0, 8], left_arm: [22, 0, -26], root: [0, 1.0, 0],
		right_leg: [8, 0, 0], left_leg: [-8, 0, 0] },
	{ t: 0.45, body: [-12, 20, 0], head: [-22, -12, 0], right_arm: [-172, 0, 8], left_arm: [16, 0, -22], root: [0, 0.6, 0],
		right_leg: [4, 0, 0], left_leg: [-4, 0, 0] },
	{ t: 0.65 },
]);

// ---- G Hammer Quake (impact 0.50 s = 10 t): both hands on the hammer overhead, then down into the ground ----
clip('gladiator_hammer_quake', 1.0, [
	{ t: 0 },
	{ t: 0.3, body: [-18, 0, 0], head: [-22, 0, 0], right_arm: [-174, 0, -8], left_arm: [-174, 0, 8], root: [0, 0.8, 0],
		right_leg: [0, 0, 0], left_leg: [0, 0, 0] },
	{ t: 0.5, body: [48, 0, 0], head: [-30, 0, 0], right_arm: [-48, 0, -6], left_arm: [-48, 0, 6], root: [0, -2.6, 1.0],
		right_leg: [22, 0, 0], left_leg: [-34, 0, 0] },
	{ t: 0.75, body: [44, 0, 0], head: [-28, 0, 0], right_arm: [-42, 0, -4], left_arm: [-42, 0, 4], root: [0, -2.4, 0.8],
		right_leg: [20, 0, 0], left_leg: [-30, 0, 0] },
	{ t: 1.0 },
]);

// ---- Shift+G Earthsplitter (impact 0.50 s): the axe raised high and driven blade-first into the ground ----
clip('gladiator_earthsplitter', 1.0, [
	{ t: 0 },
	{ t: 0.3, body: [-14, 18, 0], head: [-20, -12, 0], left_arm: [-178, 0, -6], right_arm: [10, 0, 26], root: [0, 0.6, 0],
		right_leg: [0, 0, 0], left_leg: [0, 0, 0] },
	{ t: 0.5, body: [56, -14, 0], head: [-34, 10, 0], left_arm: [-34, 0, -4], right_arm: [34, 0, 34], root: [0, -3.0, 1.2],
		right_leg: [26, 0, 0], left_leg: [-40, 0, 0] },
	{ t: 0.75, body: [52, -12, 0], head: [-32, 8, 0], left_arm: [-30, 0, -4], right_arm: [30, 0, 32], root: [0, -2.8, 1.0],
		right_leg: [24, 0, 0], left_leg: [-36, 0, 0] },
	{ t: 1.0 },
]);

// ---- Z Champion's Roar (impact 0.30 s): weapons thrown up and out, chest out, head back -- a roar to the crowd ----
clip('gladiator_champions_roar', 1.5, [
	{ t: 0 },
	{ t: 0.25, body: [-20, 0, 0], head: [-36, 0, 0], right_arm: [-150, 0, 50], left_arm: [-150, 0, -50], root: [0, 0.5, 0] },
	{ t: 0.5, body: [-16, 0, 2], head: [-32, 0, 3], right_arm: [-146, 0, 54], left_arm: [-154, 0, -46] },
	{ t: 0.7, body: [-21, 0, -2], head: [-37, 0, -3], right_arm: [-154, 0, 46], left_arm: [-146, 0, -54] },
	{ t: 0.9, body: [-17, 0, 1], head: [-33, 0, 2], right_arm: [-148, 0, 52], left_arm: [-152, 0, -48] },
	{ t: 1.1, body: [-18, 0, 0], head: [-34, 0, 0], right_arm: [-150, 0, 50], left_arm: [-150, 0, -50], root: [0, 0.4, 0] },
	{ t: 1.5 },
]);

// ---- Shift+Z Weapon Clash (impact 0.40 s = 8 t): arms flung wide, then axe and hammer smashed together in front ----
clip('gladiator_weapon_clash', 0.9, [
	{ t: 0 },
	{ t: 0.2, body: [-6, 0, 0], head: [-10, 0, 0], right_arm: [-90, 0, 80], left_arm: [-90, 0, -80], root: [0, 0.3, 0] },
	{ t: 0.4, body: [14, 0, 0], head: [-6, 0, 0], right_arm: [-96, 0, -24], left_arm: [-96, 0, 24], root: [0, -0.8, 0] },
	{ t: 0.6, body: [12, 0, 0], head: [-6, 0, 0], right_arm: [-92, 0, -20], left_arm: [-92, 0, 20], root: [0, -0.6, 0] },
	{ t: 0.9 },
]);

// ---- X Arena Leap (held in the air): both weapons raised overhead, knees tucked ----
clip('gladiator_arena_leap', 0.5, [
	{ t: 0 },
	{ t: 0.5, body: [-12, 0, 0], head: [-18, 0, 0], right_arm: [-165, 0, 25], left_arm: [-165, 0, -25], root: [0, 0, 0],
		right_leg: [-50, 0, 0], left_leg: [-22, 0, 0] },
], { loop: 'hold_on_last_frame' });

// ---- Shift+X Meteor Dive (held: rising, then the dive): pitched head-first, weapons leading ----
clip('gladiator_meteor_dive', 0.6, [
	{ t: 0 },
	{ t: 0.25, body: [-20, 0, 0], head: [-30, 0, 0], right_arm: [-175, 0, 18], left_arm: [-175, 0, -18], root: [0, 0, 0],
		right_leg: [-40, 0, 0], left_leg: [-40, 0, 0] },
	{ t: 0.6, body: [62, 0, 0], head: [-50, 0, 0], right_arm: [-172, 0, 8], left_arm: [-172, 0, -8], root: [0, 0, 0],
		right_leg: [18, 0, 0], left_leg: [12, 0, 0] },
], { loop: 'hold_on_last_frame' });

// ---- the landing slam (Arena Leap, Meteor Dive, end of Whirlwind): weapons first into the ground ----
clip('gladiator_slam', 0.8, [
	{ t: 0, body: [-10, 0, 0], head: [-18, 0, 0], right_arm: [-168, 0, 18], left_arm: [-168, 0, -18], root: [0, 0, 0],
		right_leg: [-20, 0, 0], left_leg: [-20, 0, 0] },
	{ t: 0.15, body: [52, 0, 0], head: [-32, 0, 0], right_arm: [-40, 0, 10], left_arm: [-40, 0, -10], root: [0, -3.2, 1.0],
		right_leg: [-34, 0, 4], left_leg: [-34, 0, -4] },
	{ t: 0.45, body: [48, 0, 0], head: [-30, 0, 0], right_arm: [-36, 0, 10], left_arm: [-36, 0, -10], root: [0, -3.0, 0.8],
		right_leg: [-30, 0, 4], left_leg: [-30, 0, -4] },
	{ t: 0.8 },
]);

// ---- C Axe Throw (release 0.25 s = 5 t): the left arm cocked back over the shoulder, whipped forward ----
clip('gladiator_axe_throw', 0.6, [
	{ t: 0 },
	{ t: 0.12, body: [-10, 35, 0], head: [-10, -22, 0], left_arm: [-200, 0, -24], right_arm: [-20, 0, 24], root: [0, 0, 0],
		right_leg: [-6, 0, 0], left_leg: [8, 0, 0] },
	{ t: 0.25, body: [22, -32, 0], head: [-16, 22, 0], left_arm: [-80, 0, -8], right_arm: [22, 0, 26], root: [0, -0.8, 0],
		right_leg: [16, 0, 0], left_leg: [-24, 0, 0] },
	{ t: 0.4, body: [18, -26, 0], head: [-14, 18, 0], left_arm: [-38, 0, -10], right_arm: [16, 0, 22], root: [0, -0.6, 0] },
	{ t: 0.6 },
]);

// ---- Shift+C Hammer Hurl (release 0.35 s = 7 t): a big overhead wind-up with the right arm, then the hurl ----
clip('gladiator_hammer_hurl', 0.7, [
	{ t: 0 },
	{ t: 0.18, body: [-16, -35, 0], head: [-14, 22, 0], right_arm: [-210, 0, 20], left_arm: [-40, 0, -22], root: [0, 0.3, 0],
		right_leg: [10, 0, 0], left_leg: [-6, 0, 0] },
	{ t: 0.35, body: [26, 30, 0], head: [-18, -20, 0], right_arm: [-70, 0, 8], left_arm: [20, 0, -24], root: [0, -1.0, 0],
		right_leg: [-26, 0, 0], left_leg: [18, 0, 0] },
	{ t: 0.5, body: [22, 26, 0], head: [-16, -18, 0], right_arm: [-40, 0, 10], left_arm: [14, 0, -20], root: [0, -0.8, 0] },
	{ t: 0.7 },
]);

// ---- C again (hammer away): the right hand thrust out to call it home ----
clip('gladiator_hammer_recall', 0.6, [
	{ t: 0 },
	{ t: 0.2, body: [2, 16, 0], head: [-10, -10, 0], right_arm: [-96, 0, 6], left_arm: [-8, 0, -16] },
	{ t: 0.45, body: [4, 14, 0], head: [-10, -8, 0], right_arm: [-100, 0, 8], left_arm: [-8, 0, -16] },
	{ t: 0.6 },
]);

// ---- V Gladiator Whirlwind (loop, two turns a second): both arms out flat, the whole body spinning ----
clip('gladiator_whirlwind', 0.5, [
	{ t: 0, body: [10, 0, 0], head: [-8, 0, 0], right_arm: [-14, 0, 86], left_arm: [-14, 0, -86], root: [0, 0, 0],
		right_leg: [-6, 0, 4], left_leg: [6, 0, -4] },
	{ t: 0.25, body: [10, 0, 0], head: [-8, 0, 0], right_arm: [-10, 0, 84], left_arm: [-18, 0, -88], root: [0, 0, 0],
		right_leg: [6, 0, 4], left_leg: [-6, 0, -4] },
	{ t: 0.5, body: [10, 0, 0], head: [-8, 0, 0], right_arm: [-14, 0, 86], left_arm: [-14, 0, -86], root: [0, 0, 0],
		right_leg: [-6, 0, 4], left_leg: [6, 0, -4] },
], { loop: true });
// the spin itself: the root turns a full circle per loop
json.animations['animation.hulk.gladiator_whirlwind'].bones.root.rotation = {
	'0.00': [0, 0, 0], '0.12': [0, -90, 0], '0.25': [0, -180, 0], '0.38': [0, -270, 0], '0.50': [0, -360, 0],
};

// ---- Shift+V Arena Grapple (loop, one blow per 0.5 s = the 10 t blow clock): left hand pins, the hammer pounds ----
clip('gladiator_grapple', 0.5, [
	{ t: 0, body: [44, 0, 0], head: [-30, 0, 0], right_arm: [-44, 0, 8], left_arm: [-50, 0, -6], root: [0, -2.0, 0.8],
		right_leg: [-30, 0, 4], left_leg: [20, 0, -4] },
	{ t: 0.2, body: [30, -8, 0], head: [-26, 6, 0], right_arm: [-176, 0, 16], left_arm: [-52, 0, -6], root: [0, -1.6, 0.6],
		right_leg: [-30, 0, 4], left_leg: [20, 0, -4] },
	{ t: 0.4, body: [34, -6, 0], head: [-26, 4, 0], right_arm: [-170, 0, 14], left_arm: [-52, 0, -6], root: [0, -1.7, 0.6],
		right_leg: [-30, 0, 4], left_leg: [20, 0, -4] },
	{ t: 0.5, body: [44, 0, 0], head: [-30, 0, 0], right_arm: [-44, 0, 8], left_arm: [-50, 0, -6], root: [0, -2.0, 0.8],
		right_leg: [-30, 0, 4], left_leg: [20, 0, -4] },
], { loop: true });

// write back in the file's own style: 1-space indent, CRLF
const eol = text.includes('\r\n') ? '\r\n' : '\n';
fs.writeFileSync(file, JSON.stringify(json, null, 1).replace(/\n/g, eol) + eol);
console.log('gladiator clips:', Object.keys(json.animations).filter(n => n.includes('gladiator')).length);
