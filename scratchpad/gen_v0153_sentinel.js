// v0.15.3: the Sentinel (the Sentinel Purge's main unit) becomes the user's Blockbench model
// (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/sentinel/Sentinel.bbmodel -- a plain 64x64 player skin on the
// standard base + layer rig, box UV, the classic magenta-and-blue Sentinel skin embedded). Supersedes the Sentinel half
// of gen_sentinel_v0151.js (the Drone and Master Mold assets are untouched).
//   geo/sentinel.geo.json              the skin rig, faithfully: one base + one layer cube per body part, the user's
//                                      inflation (hat 0.5, the rest 0.25), nothing added. Humanoid pivots (neck, shoulders,
//                                      hips). Blockbench's Bedrock export mirrors X, so the RIGHT limbs sit at -x here.
//                                      Empty locator bones mark the effect origins (chest emitter, palms, drone bay) and
//                                      keep the two names the renderer looks up (right_flame / left_flame, hidden on the
//                                      ground -- they carry no cubes now, the thruster fire is all particles).
//   textures/entity/sentinel.png       the bbmodel's embedded skin, byte for byte
//   textures/entity/sentinel_glowmask.png  only the red eyes and the orange chest gem
//   animations/sentinel.animation.json idle, walk, fly + the action clips (beam_chest, beam_hand, grab, deploy, death)
//                                      with the same names and key times SentinelEntity mirrors. Every clip keys every
//                                      bone at its first and last frame (GeckoLib leaves un-keyed bones where they were).
// The model is 2 blocks tall like the old one; the SCALE attribute (1.75) still makes it ~3.5.
// Usage (from the repo root): node scratchpad/gen_v0153_sentinel.js
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit.js');

const BBMODEL = process.argv[2] || 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/sentinel/Sentinel.bbmodel';
const A = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');

// ---------------------------------------------------------------- texture
const bb = JSON.parse(fs.readFileSync(BBMODEL, 'utf8'));
if (bb.textures.length !== 1 || bb.elements.length !== 12) throw new Error('not the plain skin rig');
const texPath = path.join(A, 'textures/entity/sentinel.png');
fs.writeFileSync(texPath, Buffer.from(bb.textures[0].source.split(',')[1], 'base64'));
const skin = decode(texPath);
if (skin.w !== 64 || skin.h !== 64) throw new Error('expected a 64x64 skin, got ' + skin.w + 'x' + skin.h);

// glowmask: the red eyes and the orange chest gem, nothing else (kept subtle)
const glow = Buffer.alloc(64 * 64 * 4);
let glowN = 0;
for (let i = 0; i < 64 * 64; i++) {
	const r = skin.px[i * 4], g = skin.px[i * 4 + 1], b = skin.px[i * 4 + 2], a = skin.px[i * 4 + 3];
	if (!a) continue;
	const redEye = r >= 200 && g < 60 && b < 90;                    // 219,16,57
	const gem = r >= 180 && g >= 90 && g <= 170 && b <= 70;         // 227,122,40 / 236,153,64 / 191,97,36
	if (redEye || gem) {
		glow[i * 4] = r; glow[i * 4 + 1] = g; glow[i * 4 + 2] = b; glow[i * 4 + 3] = 255;
		glowN++;
	}
}
fs.writeFileSync(path.join(A, 'textures/entity/sentinel_glowmask.png'), encode(64, 64, glow));

// ---------------------------------------------------------------- geometry
// bbmodel element name -> bone. The bbmodel is already in model px (32 = 2 blocks).
const PART = {
	'Head': 'head', 'Hat Layer': 'head',
	'Body': 'torso', 'Body Layer': 'torso',
	'Right Arm': 'right_arm', 'Right Arm Layer': 'right_arm',
	'Left Arm': 'left_arm', 'Left Arm Layer': 'left_arm',
	'Right Leg': 'right_leg', 'Right Leg Layer': 'right_leg',
	'Left Leg': 'left_leg', 'Left Leg Layer': 'left_leg',
};
const bones = [
	{ name: 'root', pivot: [0, 0, 0] },
	{ name: 'right_leg', parent: 'root', pivot: [-1.9, 12, 0] },
	{ name: 'right_flame', parent: 'right_leg', pivot: [-1.9, 0, 0] },
	{ name: 'left_leg', parent: 'root', pivot: [1.9, 12, 0] },
	{ name: 'left_flame', parent: 'left_leg', pivot: [1.9, 0, 0] },
	{ name: 'torso', parent: 'root', pivot: [0, 12, 0] },
	{ name: 'chest_emitter', parent: 'torso', pivot: [0, 20, -2.5] },
	{ name: 'drone_bay', parent: 'torso', pivot: [0, 19, 2.5] },
	{ name: 'head', parent: 'torso', pivot: [0, 24, 0] },
	{ name: 'right_arm', parent: 'torso', pivot: [-5, 22, 0] },
	{ name: 'right_palm', parent: 'right_arm', pivot: [-6, 12, 0] },
	{ name: 'left_arm', parent: 'torso', pivot: [5, 22, 0] },
	{ name: 'left_palm', parent: 'left_arm', pivot: [6, 12, 0] },
];
const byName = Object.fromEntries(bones.map(b => [b.name, b]));
const r4 = n => Math.round(n * 10000) / 10000;
for (const e of bb.elements) {
	const bone = byName[PART[e.name]];
	if (!bone) throw new Error('unexpected element ' + e.name);
	if (e.rotation && e.rotation.some(v => v)) throw new Error(e.name + ' is rotated');
	const size = [0, 1, 2].map(k => r4(e.to[k] - e.from[k]));
	const cube = { origin: [r4(-e.to[0]), r4(e.from[1]), r4(e.from[2])], size, uv: e.uv_offset || [0, 0] };
	if (e.inflate) cube.inflate = e.inflate;
	if (e.mirror_uv) cube.mirror = true;
	(bone.cubes = bone.cubes || []).push(cube);
}
for (const n of ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']) {
	if (!byName[n].cubes || byName[n].cubes.length !== 2) throw new Error(n + ': expected base + layer cube');
}
fs.writeFileSync(path.join(A, 'geo/sentinel.geo.json'), JSON.stringify({
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.sentinel', texture_width: 64, texture_height: 64,
			visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1.5, 0],
		},
		bones,
	}],
}, null, '\t') + '\n');

// ---------------------------------------------------------------- animations
// Conventions (the Hulk/Titan rigs): negative X swings a hanging limb forward / up, positive X pitches the torso / head
// forward-down; arm Z away from the body is + for the right arm, - for the left. root = position in model px.
const REST = {
	root: [0, 0, 0], torso: [2, 0, 0], head: [-2, 0, 0],
	right_arm: [-3, 0, 4], left_arm: [-3, 0, -4], right_leg: [0, 0, 0], left_leg: [0, 0, 0],
};
const TRACKS = Object.keys(REST);
const animations = {};
const TIMING = {};
const pose = (o = {}) => {
	const p = {};
	for (const k of TRACKS) {
		const v = o[k];
		p[k] = v === undefined ? REST[k] : typeof v === 'number' ? [v, REST[k][1], REST[k][2]] : v;
	}
	return p;
};
const R = pose();
function clip(name, length, loop, frames) {
	const out = {};
	for (const t of TRACKS) {
		const ch = t === 'root' ? 'position' : 'rotation';
		const kf = {};
		for (const [time, p, easing] of frames) {
			if (time > length + 1e-6) throw new Error(`${name}: key at ${time} past the clip length ${length}`);
			const v = p[t].map(n => Math.round(n * 1000) / 1000);
			kf[time.toFixed(2)] = easing ? { vector: v, easing } : v;
		}
		out[t] = { [ch]: kf };
	}
	if (frames[0][0] !== 0 || Math.abs(frames[frames.length - 1][0] - length) > 1e-6) throw new Error(name + ': must key 0 and the end');
	const a = { animation_length: length, bones: out };
	if (loop === true) a.loop = true;
	else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	animations['animation.sentinel.' + name] = a;
}
const T = (name, lenTicks, keyTicks) => { TIMING[name] = [lenTicks, keyTicks]; return [lenTicks / 20, keyTicks / 20]; };

// idle: a slow mechanical scan, the head sweeping left and right
clip('idle', 3.0, true, [
	[0, R],
	[0.8, pose({ head: [-2, 14, 0], torso: 3, root: [0, -0.15, 0] }), 'easeinoutsine'],
	[1.6, pose({ head: [-2, -14, 0], torso: 3, root: [0, -0.15, 0] }), 'easeinoutsine'],
	[3.0, R, 'easeinoutsine'],
]);

// walk: heavy, stiff strides
const st = 26;
clip('walk', 1.4, true, [
	[0, pose({ right_leg: -st, left_leg: st, right_arm: [18, 0, 4], left_arm: [-22, 0, -4], torso: 5, root: [0, -0.5, 0] })],
	[0.35, pose({ torso: 4, root: [0, 0.3, 0] })],
	[0.7, pose({ right_leg: st, left_leg: -st, right_arm: [-22, 0, 4], left_arm: [18, 0, -4], torso: 5, root: [0, -0.5, 0] })],
	[1.05, pose({ torso: 4, root: [0, 0.3, 0] })],
	[1.4, pose({ right_leg: -st, left_leg: st, right_arm: [18, 0, 4], left_arm: [-22, 0, -4], torso: 5, root: [0, -0.5, 0] })],
]);

// fly: leaning into the flight, arms swept back, legs trailing on the boot thrusters
const flyA = pose({ torso: 16, head: -14, right_leg: 14, left_leg: 8, right_arm: [24, 0, 10], left_arm: [24, 0, -10] });
clip('fly', 1.2, true, [
	[0, flyA],
	[0.6, pose({ ...flyA, right_leg: 8, left_leg: 14, root: [0, 0.6, 0] }), 'easeinoutsine'],
	[1.2, flyA, 'easeinoutsine'],
]);

// v0.15.3: both beams are aimed shots (SentinelAimedShot) -- the pose builds and holds through the tracking and the lock
// (SentinelEntity.LOCK_TICKS = 9 before the fire key), then recoils on the fire key
{ // chest beam: rears back, arms flung wide and back, the chest gem charges, then three pulses from CHEST_FIRE
	const [len, fire] = T('beam_chest', 36, 24);
	const rear = pose({ torso: -14, head: -10, right_arm: [30, 0, 28], left_arm: [30, 0, -28] });
	clip('beam_chest', len, false, [
		[0, R],
		[0.5, rear, 'easeoutquad'],
		[fire - 0.05, pose({ ...rear, torso: -16 })],
		[fire, pose({ ...rear, torso: -4 })],
		[fire + 0.35, pose({ ...rear, torso: -6 })],
		[len, R, 'easeinoutsine'],
	]);
}
{ // palm blast: the right arm comes up straight at the target, palm out, holds the aim, recoils on the shot
	const [len, fire] = T('beam_hand', 30, 20);
	const aim = pose({ right_arm: [-88, 0, 0], torso: [2, 12, 0], head: [-2, -10, 0] });
	clip('beam_hand', len, false, [
		[0, R],
		[0.4, aim, 'easeoutquad'],
		[fire - 0.05, pose({ ...aim, right_arm: [-89, 0, 0] })],
		[fire, pose({ ...aim, right_arm: [-80, 0, 0] })],
		[fire + 0.2, aim],
		[len, R, 'easeinoutsine'],
	]);
}
{ // backhand (the vanilla melee hit): a quick swipe of the right arm
	const [len, hit] = T('backhand', 12, 4);
	clip('backhand', len, false, [
		[0, R],
		[0.1, pose({ right_arm: [-40, -30, 30], torso: [2, -14, 0] }), 'easeoutquad'],
		[hit, pose({ right_arm: [-70, 40, -10], torso: [4, 18, 0] }), 'easeinquad'],
		[len, R, 'easeinoutsine'],
	]);
}
{ // grab and slam: both hands reach, lift the victim overhead, hold, then drive it into the ground
	const [len, grab] = T('grab', 36, 8);
	T('grab_slam', 36, 28);
	const reach = pose({ right_arm: [-82, 0, -6], left_arm: [-82, 0, 6], torso: 12, root: [0, -0.5, 0] });
	const lift = pose({ right_arm: [-165, 0, -4], left_arm: [-165, 0, 4], torso: -8, head: -18 });
	const slam = pose({ right_arm: [-40, 0, -4], left_arm: [-40, 0, 4], torso: 32, head: 6, root: [0, -1.5, 0], right_leg: -18, left_leg: 12 });
	clip('grab', len, false, [
		[0, R],
		[grab, reach, 'easeoutquad'],
		[0.8, lift, 'easeinoutsine'],
		[1.3, pose({ ...lift, torso: -10 })],
		[1.4, slam, 'easeinquart'],
		[1.55, slam],
		[len, R, 'easeinoutsine'],
	]);
}
{ // drone deploy: hunches forward and the pack on its back spits out scouts
	const [len, release] = T('deploy', 30, 16);
	const hunch = pose({ torso: 26, head: -20, right_arm: [30, 0, 14], left_arm: [30, 0, -14], root: [0, -0.75, 0] });
	clip('deploy', len, false, [
		[0, R],
		[0.6, hunch, 'easeoutquad'],
		[release, pose({ ...hunch, torso: 32 })],
		[1.1, hunch],
		[len, R, 'easeinoutsine'],
	]);
}
{ // death: sparks, buckles down onto its seat, then crashes forward over its legs
	const [len] = T('death', 60, 0);
	const down = pose({ root: [0, -7, 0], right_leg: -84, left_leg: -78, torso: 24, head: 22, right_arm: [14, 0, 8], left_arm: [18, 0, -8] });
	clip('death', len, 'hold', [
		[0, R],
		[0.4, pose({ torso: -12, head: -24, right_arm: [-30, 0, 10], left_arm: [20, 0, -10] }), 'easeoutquad'],
		[1.1, down, 'easeinquad'],
		[1.9, pose({ ...down, torso: 34, head: 30 })],
		[2.4, pose({ ...down, root: [0, -7, -2], torso: 80, head: 20, right_arm: [-60, 0, 6], left_arm: [-60, 0, -6] }), 'easeinquad'],
		[len, pose({ ...down, root: [0, -7, -2], torso: 82, head: 20, right_arm: [-62, 0, 6], left_arm: [-62, 0, -6] })],
	]);
}
fs.writeFileSync(path.join(A, 'animations/sentinel.animation.json'), JSON.stringify({ format_version: '1.8.0', animations }, null, 1) + '\n');

for (const [n, [len, at]] of Object.entries(TIMING)) console.log(`  clip ${n.padEnd(10)} ${String(len).padStart(3)} ticks, key at ${at}`);
console.log('sentinel: skin + geo (' + bones.length + ' bones) + ' + Object.keys(animations).length + ' animations written, ' + glowN + ' glow pixels');
