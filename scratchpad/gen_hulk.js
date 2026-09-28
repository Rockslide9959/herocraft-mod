// v0.13.12 (Hulk Phase 3): the Hulk's GeckoLib body from the user's Blockbench model
// (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/hulk/hulk.bbmodel -- a 64x64 player skin on a skin rig).
//   geo/hulk.geo.json                 player rig: root > body > head / right_arm / left_arm, root > right_leg / left_leg
//   animations/hulk.animation.json    idle, walk, run, clap, smash, leap_charge, leap, transform, punch
//   textures/entity/hulk.png          the model's embedded skin, byte for byte
// Drawn in place of the player while he is the Hulk (client/hulk/HulkRenderer); the player's own 1.8x scale
// attribute sizes it, so the geo stays player-sized. Replace any of these three files with a Blockbench export
// that keeps the same bone and animation names (docs/HULK_REFERENCE.md lists them).
//
// Run from the repo root:  node scratchpad/gen_hulk.js [path/to/hulk.bbmodel]
const fs = require('fs');
const path = require('path');
const { decode } = require('./pngkit');

const ROOT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/');
const BBMODEL = process.argv[2] || 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/hulk/hulk.bbmodel';

// ---------------- texture ----------------
const bb = JSON.parse(fs.readFileSync(BBMODEL, 'utf8'));
const skinBytes = Buffer.from(bb.textures[0].source.split(',')[1], 'base64');
fs.mkdirSync(ROOT + 'textures/entity', { recursive: true });
const skinPath = ROOT + 'textures/entity/hulk.png';
fs.writeFileSync(skinPath, skinBytes);
const skin = decode(skinPath);
if (skin.w !== 64 || skin.h !== 64) throw new Error('expected a 64x64 skin, got ' + skin.w + 'x' + skin.h);

// ---------------- geometry ----------------
// Same layout as the bbmodel (base cube + second-layer cube per part, standard skin UVs). Blockbench's Bedrock
// export mirrors X, so the RIGHT limbs sit at -x here. The bbmodel's baked "natural pose" rotations are dropped.
const cube = (origin, size, uv, inflate) => { const c = { origin, size, uv }; if (inflate) c.inflate = inflate; return c; };
const bones = [
	{ name: 'root', pivot: [0, 0, 0] },
	{ name: 'body', parent: 'root', pivot: [0, 12, 0], cubes: [cube([-4, 12, -2], [8, 12, 4], [16, 16]), cube([-4, 12, -2], [8, 12, 4], [16, 32], 0.25)] },
	{ name: 'head', parent: 'body', pivot: [0, 24, 0], cubes: [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)] },
	{ name: 'right_arm', parent: 'body', pivot: [-5, 22, 0], cubes: [cube([-8, 12, -2], [4, 12, 4], [40, 16]), cube([-8, 12, -2], [4, 12, 4], [40, 32], 0.25)] },
	{ name: 'left_arm', parent: 'body', pivot: [5, 22, 0], cubes: [cube([4, 12, -2], [4, 12, 4], [32, 48]), cube([4, 12, -2], [4, 12, 4], [48, 48], 0.25)] },
	{ name: 'right_leg', parent: 'root', pivot: [-2, 12, 0], cubes: [cube([-3.9, 0, -2], [4, 12, 4], [0, 16]), cube([-3.9, 0, -2], [4, 12, 4], [0, 32], 0.25)] },
	{ name: 'left_leg', parent: 'root', pivot: [2, 12, 0], cubes: [cube([-0.1, 0, -2], [4, 12, 4], [16, 48]), cube([-0.1, 0, -2], [4, 12, 4], [0, 48], 0.25)] },
];
fs.mkdirSync(ROOT + 'geo', { recursive: true });
fs.writeFileSync(ROOT + 'geo/hulk.geo.json', JSON.stringify({
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.hulk', texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0] },
		bones,
	}],
}, null, 1));

// ---------------- animations ----------------
// Convention (verified on the Titan): negative X swings a hanging limb FORWARD / up, positive X swings it back or
// pitches the head down. Arm Z: the value that swings each arm AWAY from the body (right +, left -) is set per
// call below. Positions are model px (1/16 block before the Hulk's 1.8x).
const A = {};
function anim(name, length, loop, boneFrames) {
	const out = {};
	for (const [bn, chans] of Object.entries(boneFrames)) {
		out[bn] = {};
		for (const [ch, frames] of Object.entries(chans)) {
			const o = {};
			for (const [t, v] of frames) o[t.toFixed(2)] = v;
			out[bn][ch] = o;
		}
	}
	const a = { animation_length: length, bones: out };
	if (loop === true) a.loop = true; else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	A['animation.hulk.' + name] = a;
}
const R = (frames) => ({ rotation: frames });
const Pn = (frames) => ({ position: frames });
const OUT = 12; // the Hulk's arms never hang straight -- his shoulders are too big

// idle: a hunched, heaving breath; fists hang away from the body
anim('idle', 3, true, {
	root: Pn([[0, [0, 0, 0]], [1.5, [0, -0.2, 0]], [3, [0, 0, 0]]]),
	body: { rotation: [[0, [8, 0, 0]], [1.5, [10, 0, 0]], [3, [8, 0, 0]]], scale: [[0, [1, 1, 1]], [1.5, [1.04, 1.02, 1.04]], [3, [1, 1, 1]]] },
	head: R([[0, [-8, 0, 0]], [1.5, [-10, 0, 0]], [3, [-8, 0, 0]]]),
	right_arm: R([[0, [-4, 0, OUT]], [1.5, [-2, 0, OUT + 3]], [3, [-4, 0, OUT]]]),
	left_arm: R([[0, [-4, 0, -OUT]], [1.5, [-2, 0, -OUT - 3]], [3, [-4, 0, -OUT]]]),
});

function gait(name, len, legSwing, armSwing, lean, bob, twist) {
	const h = len / 2, q = len / 4;
	anim(name, len, true, {
		root: Pn([[0, [0, -bob, 0]], [q, [0, bob * 0.5, 0]], [h, [0, -bob, 0]], [h + q, [0, bob * 0.5, 0]], [len, [0, -bob, 0]]]),
		right_leg: R([[0, [-legSwing, 0, 0]], [h, [legSwing, 0, 0]], [len, [-legSwing, 0, 0]]]),
		left_leg: R([[0, [legSwing, 0, 0]], [h, [-legSwing, 0, 0]], [len, [legSwing, 0, 0]]]),
		right_arm: R([[0, [armSwing, 0, OUT]], [h, [-armSwing, 0, OUT]], [len, [armSwing, 0, OUT]]]),
		left_arm: R([[0, [-armSwing, 0, -OUT]], [h, [armSwing, 0, -OUT]], [len, [-armSwing, 0, -OUT]]]),
		body: R([[0, [lean, twist, 0]], [h, [lean, -twist, 0]], [len, [lean, twist, 0]]]),
		head: R([[0, [-lean * 0.7, -twist * 0.6, 0]], [h, [-lean * 0.7, twist * 0.6, 0]], [len, [-lean * 0.7, -twist * 0.6, 0]]]),
	});
}
gait('walk', 1.1, 28, 24, 10, 0.4, 6);
gait('run', 0.6, 50, 55, 22, 0.8, 9);

// clap (the clap lands at 0.3 s = 6 ticks, HulkAbilities.CLAP_IMPACT_TICKS): arms fly out wide, then slam together
anim('clap', 0.7, false, {
	right_arm: R([[0, [-4, 0, OUT]], [0.15, [-85, -55, 0]], [0.3, [-88, 12, 0]], [0.45, [-80, 8, 0]], [0.7, [-4, 0, OUT]]]),
	left_arm: R([[0, [-4, 0, -OUT]], [0.15, [-85, 55, 0]], [0.3, [-88, -12, 0]], [0.45, [-80, -8, 0]], [0.7, [-4, 0, -OUT]]]),
	body: R([[0, [8, 0, 0]], [0.15, [-6, 0, 0]], [0.3, [16, 0, 0]], [0.7, [8, 0, 0]]]),
	head: R([[0, [-8, 0, 0]], [0.3, [-14, 0, 0]], [0.7, [-8, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.3, [0, -0.5, -0.4]], [0.7, [0, 0, 0]]]),
});

// smash (the fists land at 0.5 s = 10 ticks, HulkAbilities.SMASH_IMPACT_TICKS): both fists overhead, then down
anim('smash', 1.0, false, {
	right_arm: R([[0, [-4, 0, OUT]], [0.3, [-172, 0, 8]], [0.5, [-40, 0, 6]], [0.7, [-30, 0, 6]], [1.0, [-4, 0, OUT]]]),
	left_arm: R([[0, [-4, 0, -OUT]], [0.3, [-172, 0, -8]], [0.5, [-40, 0, -6]], [0.7, [-30, 0, -6]], [1.0, [-4, 0, -OUT]]]),
	body: R([[0, [8, 0, 0]], [0.3, [-14, 0, 0]], [0.5, [42, 0, 0]], [0.7, [38, 0, 0]], [1.0, [8, 0, 0]]]),
	head: R([[0, [-8, 0, 0]], [0.3, [-18, 0, 0]], [0.5, [-30, 0, 0]], [1.0, [-8, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [0.5, [-22, 0, 4]], [1.0, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.5, [-22, 0, -4]], [1.0, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.3, [0, 0.6, 0]], [0.5, [0, -2.4, 0.8]], [0.7, [0, -2.2, 0.6]], [1.0, [0, 0, 0]]]),
});

// leap_charge (held while X is down): a deep crouch, arms swept back
anim('leap_charge', 0.6, 'hold', {
	root: Pn([[0, [0, 0, 0]], [0.6, [0, -3.2, 0]]]),
	body: R([[0, [8, 0, 0]], [0.6, [34, 0, 0]]]),
	head: R([[0, [-8, 0, 0]], [0.6, [-30, 0, 0]]]),
	right_arm: R([[0, [-4, 0, OUT]], [0.6, [42, 0, OUT + 6]]]),
	left_arm: R([[0, [-4, 0, -OUT]], [0.6, [42, 0, -OUT - 6]]]),
	right_leg: R([[0, [0, 0, 0]], [0.6, [-44, 0, 6]]]),
	left_leg: R([[0, [0, 0, 0]], [0.6, [-44, 0, -6]]]),
});

// leap (held in the air): arms thrown up, legs trailing
anim('leap', 0.5, 'hold', {
	root: Pn([[0, [0, -3.2, 0]], [0.25, [0, 0, 0]]]),
	body: R([[0, [34, 0, 0]], [0.25, [-8, 0, 0]], [0.5, [-4, 0, 0]]]),
	head: R([[0, [-30, 0, 0]], [0.5, [-6, 0, 0]]]),
	right_arm: R([[0, [42, 0, OUT]], [0.25, [-160, 0, 18]], [0.5, [-150, 0, 22]]]),
	left_arm: R([[0, [42, 0, -OUT]], [0.25, [-160, 0, -18]], [0.5, [-150, 0, -22]]]),
	right_leg: R([[0, [-44, 0, 0]], [0.25, [24, 0, 4]], [0.5, [18, 0, 6]]]),
	left_leg: R([[0, [-44, 0, 0]], [0.25, [-10, 0, -4]], [0.5, [-14, 0, -6]]]),
});

// transform (1.5 s = HulkConfig.GROWTH_TICKS): doubled over in pain, then he straightens with a roar, arms flung out
anim('transform', 1.5, false, {
	root: Pn([[0, [0, -2.5, 0]], [0.7, [0, -1.5, 0]], [1.0, [0, 0.4, 0]], [1.5, [0, 0, 0]]]),
	body: R([[0, [42, 0, 0]], [0.7, [34, 0, 0]], [1.0, [-12, 0, 0]], [1.2, [-10, 0, 0]], [1.5, [8, 0, 0]]]),
	head: R([[0, [30, 0, 0]], [0.7, [22, 0, 0]], [1.0, [-38, 0, 0]], [1.25, [-34, 0, 0]], [1.5, [-8, 0, 0]]]),
	right_arm: R([[0, [-20, 0, 6]], [0.7, [-26, 0, 10]], [1.0, [-40, 0, 72]], [1.25, [-44, 0, 76]], [1.5, [-4, 0, OUT]]]),
	left_arm: R([[0, [-20, 0, -6]], [0.7, [-26, 0, -10]], [1.0, [-40, 0, -72]], [1.25, [-44, 0, -76]], [1.5, [-4, 0, -OUT]]]),
	right_leg: R([[0, [-24, 0, 0]], [0.7, [-18, 0, 0]], [1.0, [0, 0, 6]], [1.5, [0, 0, 0]]]),
	left_leg: R([[0, [-24, 0, 0]], [0.7, [-18, 0, 0]], [1.0, [0, 0, -6]], [1.5, [0, 0, 0]]]),
});

// punch (a melee swing, 0.3 s = the vanilla swing): only the right arm is keyed, so it layers over walk / run
anim('punch', 0.3, false, {
	right_arm: R([[0, [-4, 0, OUT]], [0.08, [30, 0, OUT]], [0.16, [-100, 0, 4]], [0.3, [-4, 0, OUT]]]),
});

fs.mkdirSync(ROOT + 'animations', { recursive: true });
fs.writeFileSync(ROOT + 'animations/hulk.animation.json', JSON.stringify({ format_version: '1.8.0', animations: A }, null, 1));
console.log('wrote hulk geo, skin and', Object.keys(A).length, 'animations');
