// Generates the Titan Shifter's v0.12.32 body: a regular Minecraft player model (the supplied
// aot/titanshifter.bbmodel skin) scaled up to 11 blocks tall by the renderer (11 / 2.0 = 5.5x).
//   geo/titan_form.geo.json                        player rig: root > body > head / right_arm / left_arm, root > right_leg / left_leg
//   animations/titan_form.animation.json           every animation redone for the new body
//   textures/entity/titan_form.png                 the supplied skin, byte-for-byte
//   textures/entity/titan_form_hardened.png        a crystal-blue recolour of it (Titan Hardening)
// The serum icon is unchanged (gen_titan.js still owns it).
//
// Run from the repo root:  node scratchpad/gen_titan_v2.js [path/to/titanshifter.bbmodel]
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');

const ROOT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/');
const BBMODEL = process.argv[2] || 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/aot/titanshifter.bbmodel';

// ---------------- texture ----------------
const bb = JSON.parse(fs.readFileSync(BBMODEL, 'utf8'));
const skinBytes = Buffer.from(bb.textures[0].source.split(',')[1], 'base64');
const skin = png.decode(skinBytes);
if (skin.w !== 64 || skin.h !== 64) throw new Error('expected a 64x64 skin');
fs.writeFileSync(ROOT + 'textures/entity/titan_form.png', skinBytes);

// Hardened: keep the skin's own light/dark structure but recolour it into faceted blue crystal.
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2246822519) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
const hardened = { w: 64, h: 64, data: Buffer.alloc(64 * 64 * 4) };
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
	const i = (y * 64 + x) * 4;
	const a = skin.data[i + 3];
	if (a === 0) continue;
	const lum = (0.3 * skin.data[i] + 0.59 * skin.data[i + 1] + 0.11 * skin.data[i + 2]) / 255;
	const t = Math.max(0, Math.min(1, (lum - 0.15) / 0.75));
	let r = 40 + t * 170, g = 84 + t * 158, b = 140 + t * 115;
	// crystal facets: light diagonal seams and dark fault lines
	if ((x + y * 2) % 7 === 0) { r += 40; g += 30; b += 20; }
	if ((x * 2 - y + 64) % 11 === 0) { r -= 35; g -= 30; b -= 15; }
	const n = (hash(x, y, 3) - 0.5) * 14;
	hardened.data[i] = Math.max(0, Math.min(255, r + n));
	hardened.data[i + 1] = Math.max(0, Math.min(255, g + n));
	hardened.data[i + 2] = Math.max(0, Math.min(255, b + n));
	hardened.data[i + 3] = a;
}
png.write(ROOT + 'textures/entity/titan_form_hardened.png', hardened);

// ---------------- geometry ----------------
// bbmodel x is mirrored relative to the geo file (Blockbench's Bedrock export flips X), so the model's RIGHT limbs sit at -x.
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
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.titan_form', texture_width: 64, texture_height: 64, visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1, 0] },
		bones,
	}],
};

// ---------------- animations ----------------
// Convention (verified in-game in v0.12.31): negative X swings a hanging limb FORWARD/up, positive X swings it back / pitches
// the head down. Positions are in model px -- the renderer multiplies everything by 5.5, so 1 px here is ~0.34 blocks.
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
	A['animation.titan.' + name] = a;
}
const R = (frames) => ({ rotation: frames });
const Pn = (frames) => ({ position: frames });

// idle -- slow, heavy breathing; the arms hang a little away from the body
anim('idle', 4, true, {
	root: Pn([[0, [0, 0, 0]], [2, [0, -0.25, 0]], [4, [0, 0, 0]]]),
	body: { rotation: [[0, [0, 0, 0]], [2, [2.2, 0, 0]], [4, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [2, [1.02, 1.02, 1.02]], [4, [1, 1, 1]]] },
	head: R([[0, [0, 0, 0]], [2, [-1.5, 2, 0]], [4, [0, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [2, [7, 0, 0]], [4, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [2, [7, 0, 0]], [4, [3, 0, 0]]]),
});

function gait(name, len, legSwing, armSwing, lean, bob, twist) {
	const h = len / 2, q = len / 4;
	anim(name, len, true, {
		root: Pn([[0, [0, -bob, 0]], [q, [0, bob * 0.5, 0]], [h, [0, -bob, 0]], [h + q, [0, bob * 0.5, 0]], [len, [0, -bob, 0]]]),
		right_leg: R([[0, [-legSwing, 0, 0]], [h, [legSwing, 0, 0]], [len, [-legSwing, 0, 0]]]),
		left_leg: R([[0, [legSwing, 0, 0]], [h, [-legSwing, 0, 0]], [len, [legSwing, 0, 0]]]),
		right_arm: R([[0, [armSwing, 0, 0]], [h, [-armSwing, 0, 0]], [len, [armSwing, 0, 0]]]),
		left_arm: R([[0, [-armSwing, 0, 0]], [h, [armSwing, 0, 0]], [len, [-armSwing, 0, 0]]]),
		body: R([[0, [lean, twist, 0]], [h, [lean, -twist, 0]], [len, [lean, twist, 0]]]),
		head: R([[0, [-lean * 0.6, -twist * 0.6, 0]], [h, [-lean * 0.6, twist * 0.6, 0]], [len, [-lean * 0.6, -twist * 0.6, 0]]]),
	});
}
gait('walk', 1.2, 26, 20, 4, 0.3, 4);
gait('run', 0.8, 44, 48, 14, 0.6, 7);

// punch (the hit lands at 0.3 s): wind the right shoulder back, drive the fist straight out, recover
anim('punch', 0.6, false, {
	right_arm: R([[0, [3, 0, 0]], [0.15, [58, 0, 0]], [0.3, [-92, 0, 0]], [0.45, [-70, 0, 0]], [0.6, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.15, [-32, 0, 0]], [0.3, [30, 0, 0]], [0.6, [3, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.15, [-5, -22, 0]], [0.3, [12, 26, 0]], [0.6, [0, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.3, [-4, 10, 0]], [0.6, [0, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [0.3, [14, 0, 0]], [0.6, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.3, [-18, 0, 0]], [0.6, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.3, [0, -0.4, -0.5]], [0.6, [0, 0, 0]]]),
});
// kick (the hit lands at 0.35 s): lean back, whip the right leg out straight
anim('kick', 0.7, false, {
	right_leg: R([[0, [0, 0, 0]], [0.2, [40, 0, 0]], [0.35, [-88, 0, 0]], [0.5, [-62, 0, 0]], [0.7, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.35, [8, 0, 0]], [0.7, [0, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.2, [10, 0, 0]], [0.35, [-16, 0, 0]], [0.7, [0, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.35, [-38, 0, 0]], [0.7, [3, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [0.35, [34, 0, 0]], [0.7, [3, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.35, [0, -0.2, 0.3]], [0.7, [0, 0, 0]]]),
});
// heavy punch -- the combo finisher (the hit lands at 0.45 s): a full overhead haymaker
anim('heavy_punch', 0.9, false, {
	right_arm: R([[0, [3, 0, 0]], [0.25, [-168, 0, 0]], [0.45, [-92, 0, 0]], [0.6, [-70, 0, 0]], [0.9, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.25, [-44, 0, 0]], [0.45, [26, 0, 0]], [0.9, [3, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.25, [-16, -12, 0]], [0.45, [26, 16, 0]], [0.9, [0, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.45, [10, 0, 0]], [0.9, [0, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [0.45, [16, 0, 0]], [0.9, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.45, [-26, 0, 0]], [0.9, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.25, [0, -0.3, 0.5]], [0.45, [0, -0.9, -0.9]], [0.9, [0, 0, 0]]]),
});
// heavy smash (the slam lands at 1.0 s): both fists overhead, then everything comes down
anim('smash', 1.5, false, {
	right_arm: R([[0, [3, 0, 0]], [0.5, [-122, 0, 0]], [0.85, [-174, 0, 0]], [1.0, [-96, 0, 0]], [1.15, [-42, 0, 0]], [1.5, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.5, [-122, 0, 0]], [0.85, [-174, 0, 0]], [1.0, [-96, 0, 0]], [1.15, [-42, 0, 0]], [1.5, [3, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.85, [-18, 0, 0]], [1.0, [34, 0, 0]], [1.5, [0, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.85, [-14, 0, 0]], [1.0, [16, 0, 0]], [1.5, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.85, [0, -0.9, 0.7]], [1.0, [0, -1.8, -1.0]], [1.5, [0, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [0.85, [-16, 0, 0]], [1.0, [-30, 0, 0]], [1.5, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.85, [-16, 0, 0]], [1.0, [-30, 0, 0]], [1.5, [0, 0, 0]]]),
});
// stomp (the foot lands at 0.5 s): a knee-high lift with the arms thrown up, then a hard plant
anim('stomp', 0.9, false, {
	right_leg: R([[0, [0, 0, 0]], [0.3, [-74, 0, 0]], [0.5, [2, 0, 0]], [0.9, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.3, [6, 0, 0]], [0.5, [-4, 0, 0]], [0.9, [0, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.3, [-10, 0, 0]], [0.5, [12, 0, 0]], [0.9, [0, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.3, [-40, 0, 0]], [0.5, [14, 0, 0]], [0.9, [3, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [0.3, [-40, 0, 0]], [0.5, [14, 0, 0]], [0.9, [3, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.3, [-6, 0, 0]], [0.5, [10, 0, 0]], [0.9, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.3, [0, 0.2, 0]], [0.5, [0, -1.1, 0]], [0.9, [0, 0, 0]]]),
});
// leap (the launch is at 0.3 s): a deep forward crouch, then everything explodes upward
anim('leap', 1.4, false, {
	root: Pn([[0, [0, 0, 0]], [0.3, [0, -2.4, 0.4]], [0.5, [0, 0.4, 0]], [1.4, [0, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [0.3, [-46, 0, 0]], [0.5, [14, 0, 0]], [0.9, [-20, 0, 0]], [1.4, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.3, [-46, 0, 0]], [0.5, [14, 0, 0]], [0.9, [-8, 0, 0]], [1.4, [0, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [0.3, [46, 0, 0]], [0.55, [-128, 0, 0]], [1.1, [-72, 0, 0]], [1.4, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.3, [46, 0, 0]], [0.55, [-128, 0, 0]], [1.1, [-72, 0, 0]], [1.4, [3, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.3, [28, 0, 0]], [0.55, [-8, 0, 0]], [1.4, [0, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.3, [12, 0, 0]], [0.55, [-14, 0, 0]], [1.4, [0, 0, 0]]]),
});
// landing: absorb the impact
anim('landing', 0.7, false, {
	root: Pn([[0, [0, -2.2, 0]], [0.15, [0, -2.9, 0]], [0.7, [0, 0, 0]]]),
	right_leg: R([[0, [-40, 0, 0]], [0.15, [-46, 0, 0]], [0.7, [0, 0, 0]]]),
	left_leg: R([[0, [-40, 0, 0]], [0.15, [-46, 0, 0]], [0.7, [0, 0, 0]]]),
	body: R([[0, [26, 0, 0]], [0.15, [32, 0, 0]], [0.7, [0, 0, 0]]]),
	right_arm: R([[0, [-24, 0, 0]], [0.15, [28, 0, 0]], [0.7, [3, 0, 0]]]),
	left_arm: R([[0, [-24, 0, 0]], [0.15, [28, 0, 0]], [0.7, [3, 0, 0]]]),
	head: R([[0, [8, 0, 0]], [0.15, [12, 0, 0]], [0.7, [0, 0, 0]]]),
});
// roar (the sound is at 0.5 s): head thrown back and shaking, chest out, arms flared
const roarShake = []; for (let i = 0; i <= 12; i++) roarShake.push([0.5 + i * 0.05, [-34, (i % 2 ? 4 : -4), 0]]);
anim('roar', 1.6, false, {
	head: R([[0, [0, 0, 0]], [0.4, [-34, 0, 0]], ...roarShake, [1.6, [0, 0, 0]]]),
	body: { rotation: [[0, [0, 0, 0]], [0.4, [-16, 0, 0]], [1.15, [-13, 0, 0]], [1.6, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [0.5, [1.05, 1.05, 1.05]], [1.1, [1.03, 1.03, 1.03]], [1.6, [1, 1, 1]]] },
	right_arm: R([[0, [3, 0, 0]], [0.4, [26, 0, 0]], [1.15, [26, 0, 0]], [1.6, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.4, [26, 0, 0]], [1.15, [26, 0, 0]], [1.6, [3, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.4, [0, -0.3, 0.3]], [1.6, [0, 0, 0]]]),
});
// hurt: a short flinch
anim('hurt', 0.45, false, {
	body: R([[0, [0, 0, 0]], [0.1, [-10, 0, 0]], [0.45, [0, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.1, [-14, 0, 0]], [0.45, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.1, [0, 0, 0.6]], [0.45, [0, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [0.1, [16, 0, 0]], [0.45, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.1, [16, 0, 0]], [0.45, [3, 0, 0]]]),
});
// regeneration: hunched, both fists pulled tight into the chest while the whole body pulses
anim('regeneration', 2.0, false, {
	right_arm: R([[0, [3, 0, 0]], [0.3, [-62, 0, 0]], [1.7, [-62, 0, 0]], [2.0, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.3, [-62, 0, 0]], [1.7, [-62, 0, 0]], [2.0, [3, 0, 0]]]),
	body: { rotation: [[0, [0, 0, 0]], [0.3, [12, 0, 0]], [1.7, [12, 0, 0]], [2.0, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [0.6, [1.05, 1.05, 1.05]], [0.9, [1, 1, 1]], [1.2, [1.05, 1.05, 1.05]], [1.5, [1, 1, 1]], [1.8, [1.05, 1.05, 1.05]], [2.0, [1, 1, 1]]] },
	head: R([[0, [0, 0, 0]], [0.3, [16, 0, 0]], [1.7, [16, 0, 0]], [2.0, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.3, [0, -0.4, 0]], [1.7, [0, -0.4, 0]], [2.0, [0, 0, 0]]]),
});
// hardening: brace, arms forward and locked, chest swelling as the crystal forms
anim('hardening', 1.1, false, {
	right_arm: R([[0, [3, 0, 0]], [0.35, [-48, 0, 0]], [0.85, [-48, 0, 0]], [1.1, [3, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [0.35, [-48, 0, 0]], [0.85, [-48, 0, 0]], [1.1, [3, 0, 0]]]),
	body: { rotation: [[0, [0, 0, 0]], [0.4, [-7, 0, 0]], [1.1, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [0.4, [1.07, 1.07, 1.07]], [0.85, [1.05, 1.05, 1.05]], [1.1, [1, 1, 1]]] },
	head: R([[0, [0, 0, 0]], [0.4, [-10, 0, 0]], [1.1, [0, 0, 0]]]),
	root: Pn([[0, [0, 0, 0]], [0.4, [0, -0.5, 0]], [1.1, [0, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [0.4, [-8, 0, 0]], [1.1, [0, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.4, [8, 0, 0]], [1.1, [0, 0, 0]]]),
});
// transformation (held): a hunched shape swells up out of the flash and straightens (root scale mirrors TitanFormEntity#visualScale)
anim('transformation', 3.0, 'hold', {
	root: { scale: [[0, [0.25, 0.25, 0.25]], [1.0, [0.5, 0.5, 0.5]], [2.2, [1.05, 1.05, 1.05]], [3.0, [1, 1, 1]]] },
	body: R([[0, [36, 0, 0]], [1.4, [28, 0, 0]], [2.4, [-6, 0, 0]], [3.0, [0, 0, 0]]]),
	head: R([[0, [24, 0, 0]], [1.6, [18, 0, 0]], [2.4, [-14, 0, 0]], [3.0, [0, 0, 0]]]),
	right_arm: R([[0, [-46, 0, 0]], [1.6, [-30, 0, 0]], [2.4, [24, 0, 0]], [3.0, [3, 0, 0]]]),
	left_arm: R([[0, [-46, 0, 0]], [1.6, [-30, 0, 0]], [2.4, [24, 0, 0]], [3.0, [3, 0, 0]]]),
	right_leg: R([[0, [-34, 0, 0]], [1.6, [-22, 0, 0]], [3.0, [0, 0, 0]]]),
	left_leg: R([[0, [-34, 0, 0]], [1.6, [-22, 0, 0]], [3.0, [0, 0, 0]]]),
});
// reversion (held): folds back down into a crouch as it shrinks in a cloud of steam
anim('reversion', 2.0, 'hold', {
	root: { scale: [[0, [1, 1, 1]], [1.0, [0.8, 0.8, 0.8]], [2.0, [0.25, 0.25, 0.25]]] },
	body: R([[0, [0, 0, 0]], [1.2, [18, 0, 0]], [2.0, [36, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [1.2, [12, 0, 0]], [2.0, [24, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [2.0, [-46, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [2.0, [-46, 0, 0]]]),
	right_leg: R([[0, [0, 0, 0]], [2.0, [-34, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [2.0, [-34, 0, 0]]]),
});
// death (held): the legs give out, it sinks to a sit and slumps forward, head hanging
anim('death', 3.0, 'hold', {
	root: Pn([[0, [0, 0, 0]], [0.5, [0, -1.2, 0.3]], [1.4, [0, -6.0, -0.6]], [2.4, [0, -11.4, -1.2]], [3.0, [0, -12, -1.2]]]),
	right_leg: R([[0, [0, 0, 0]], [0.5, [-22, 0, 0]], [1.4, [-56, 0, 0]], [2.4, [-84, 0, 0]], [3.0, [-88, 0, 0]]]),
	left_leg: R([[0, [0, 0, 0]], [0.5, [-16, 0, 0]], [1.4, [-50, 0, 0]], [2.4, [-80, 0, 0]], [3.0, [-86, 0, 0]]]),
	body: R([[0, [0, 0, 0]], [0.5, [-8, 0, 0]], [1.4, [24, 0, 0]], [2.4, [46, 0, 0]], [3.0, [52, 0, 0]]]),
	head: R([[0, [0, 0, 0]], [0.5, [-12, 0, 0]], [1.4, [26, 0, 0]], [3.0, [44, 0, 0]]]),
	right_arm: R([[0, [3, 0, 0]], [1.4, [14, 0, 0]], [3.0, [30, 0, 0]]]),
	left_arm: R([[0, [3, 0, 0]], [1.4, [14, 0, 0]], [3.0, [30, 0, 0]]]),
});

fs.writeFileSync(ROOT + 'geo/titan_form.geo.json', JSON.stringify(geo, null, 1));
fs.writeFileSync(ROOT + 'animations/titan_form.animation.json', JSON.stringify({ format_version: '1.8.0', animations: A }, null, 1));
console.log('done; animations:', Object.keys(A).length);
