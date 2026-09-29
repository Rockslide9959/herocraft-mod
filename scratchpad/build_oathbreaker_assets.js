// Builds the Oathbreaker's GeckoLib assets: texture (extends the user-supplied deathknight skin with
// a sword swatch), geo.json (named bones + explicit per-face UV, box-uv algorithm applied by hand
// since the exported geo format wants explicit rects, not an implicit box_uv origin), and
// animation.json (idle/walk/hit/death + the two named attacks' windup/strike beats).
const fs = require('fs');
const path = require('path');

// v0.13.19: outputs go to the checkout this script sits in (so it also works from a git worktree); the source
// skin and pnglib (untracked scratchpad files) are always read from the main checkout.
const MAIN_ROOT = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod';
const ROOT = path.resolve(__dirname, '..');
const png = require(path.join(MAIN_ROOT, 'scratchpad/pnglib.js'));
// v0.14.0: moved out of a session temp dir into the project scratchpad so it can't vanish with a cleanup.
const SRC_TEXTURE = path.join(MAIN_ROOT, 'scratchpad/oathbreaker/deathknight_source.png');
const OUT_TEXTURE = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/oathbreaker.png');
const OUT_GEO = path.join(ROOT, 'src/main/resources/assets/projecthero/geo/oathbreaker.geo.json');
const OUT_ANIM = path.join(ROOT, 'src/main/resources/assets/projecthero/animations/oathbreaker.animation.json');

// ---------------------------------------------------------------- texture

const base = png.read(SRC_TEXTURE); // 64x64, vanilla player skin layout (base + second layer)
// v0.14.0: back to the plain 64x64 skin. The old 64x80 canvas only existed to hold three flat-colour
// sword swatches; the sword is now the real vanilla netherite sword item, rendered at the `sword` bone
// by OathbreakerRenderer (GeckoLib BlockAndItemGeoLayer), so the model has no sword geometry to texture.
const TW = 64, TH = 64;
const out = Buffer.from(base.data);

// v0.13.9 "his right arm is invisible": it wasn't missing -- harness-verified with the arms painted green/blue,
// both render in every pose -- but the source skin paints each arm's banded plate only on its OUTER face.
// The front face is ~95% pure black (0x0d), exactly like the chest behind it, so seen from the front
// (where every attack is watched from) the sword arm vanished into the torso and the blade looked like it
// floated. Wrap the outer face's plate pattern around the front and back faces wherever the skin left them
// near-black; the inner face (against the body) stays dark. Box-UV arms are 4x12x4: outer face at u+0,
// front u+4, inner u+8, back u+12, rows v+4..v+15. Both arms (the model is mirrored, so "right arm" in
// the player's eyes could be either).
function wrapArmPlates(u, v) {
	for (let row = 0; row < 12; row++) {
		const y = v + 4 + row;
		for (const faceU of [u + 4, u + 12]) {
			for (let c = 0; c < 4; c++) {
				const o = (y * TW + faceU + c) * 4;
				const lum = Math.max(out[o], out[o + 1], out[o + 2]);
				if (out[o + 3] === 0 || lum > 0x22) continue; // keep any detail the skin did paint there
				// front mirrors the outer face so the pattern meets at the shared edge; back continues it
				const src = ((y * TW) + u + (faceU === u + 4 ? 3 - c : c)) * 4;
				out[o] = out[src]; out[o + 1] = out[src + 1]; out[o + 2] = out[src + 2]; out[o + 3] = out[src + 3];
			}
		}
	}
}
wrapArmPlates(40, 16); // right_arm bone (sword arm)
wrapArmPlates(32, 48); // left_arm bone (off arm)

fs.mkdirSync(path.dirname(OUT_TEXTURE), { recursive: true });
fs.writeFileSync(OUT_TEXTURE, png.encode(TW, TH, out));
console.log('wrote texture', OUT_TEXTURE, TW, TH);

// ---------------------------------------------------------------- v0.14.0 phase textures + glowmasks
// GeckoLib's AutoGlowingGeoLayer renders `<texture>_glowmask.png` full-bright over the model, so every
// texture gets a twin where only the pixels that should glow are opaque. Phase 1: just the two red eye
// pixels (found by scanning the source skin's head-front face -- the other ~89 red pixels are the armour's
// painted streaks and deliberately stay unlit). Phase 2 ("Forsworn") paints jagged soul-fire cracks across
// the chest, back, arms and helmet; phase 3 ("Oathless") re-walks the same cracks (same seed) wider and
// brighter with extra branches. Crack cores go into that phase's glowmask; their darker rims don't.
// head north (front) face row (8,8)+(x,4): bright inner pixels at x=2,5, dim outer ones at x=1,6. The
// helmet layer's visor has transparent slits cut exactly over these, so they glow through it.
const EYE_PIXELS = [[9, 12], [10, 12], [13, 12], [14, 12]];
const TEX_DIR = path.dirname(OUT_TEXTURE);

function mulberry32(seed) {
	return function () {
		seed |= 0; seed = seed + 0x6D2B79F5 | 0;
		let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
		t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
		return ((t ^ t >>> 14) >>> 0) / 4294967296;
	};
}
function setPx(buf, x, y, rgb) {
	const o = (y * TW + x) * 4;
	buf[o] = rgb[0]; buf[o + 1] = rgb[1]; buf[o + 2] = rgb[2]; buf[o + 3] = 255;
}
function getPx(buf, x, y) {
	const o = (y * TW + x) * 4;
	return [buf[o], buf[o + 1], buf[o + 2], buf[o + 3]];
}
// Skin faces the cracks run across, as [u, v, w, h] rects in the 64x64 box-UV layout.
const CRACK_FACES = [
	[20, 20, 8, 12], // chest (body north)
	[32, 20, 8, 12], // back (body south)
	[44, 20, 4, 12], // sword arm, front
	[40, 20, 4, 12], // sword arm, outer side
	[36, 52, 4, 12], // off arm, front
	[32, 52, 4, 12], // off arm, outer side
	[8, 8, 8, 8], // helmet front
	[0, 8, 8, 8], // helmet side
	[16, 8, 8, 8], // helmet other side
	[8, 0, 8, 8], // helmet top
	// second-layer faces (5th element true): cracks only where the layer is already opaque armour, so
	// they run through the plates without adding pixels to (or changing) the layer's silhouette
	[40, 8, 8, 8, true], // helm visor front
	[32, 8, 8, 8, true], // helm side
	[48, 8, 8, 8, true], // helm other side
	[40, 0, 8, 8, true], // helm crest/top
	[20, 36, 8, 12, true], // chest plate
	[32, 36, 8, 12, true], // back plate
	[44, 36, 4, 12, true], // sword-arm pauldron/vambrace
	[52, 52, 4, 12, true], // off-arm pauldron/vambrace
];
const isLayerFace = face => face[4] === true;
const baseOpaque = (x, y) => out[(y * TW + x) * 4 + 3] > 0;
const isEye = (x, y) => EYE_PIXELS.some(([ex, ey]) => ex === x && ey === y);

// A jagged, mostly-downward random walk from the face's top edge, occasionally forking. Returns the list
// of core pixels. `wide` also claims a sideways neighbour per step; `extraBranches` forks more often.
function crackPaths(rand, face, wide, extraBranches, lengthScale = 1.0, startsOverride = 0) {
	const [u, v, w, h] = face;
	const core = [];
	const walk = (x, y, len, branchChance) => {
		for (let i = 0; i < len && y < v + h; i++) {
			if (x >= u && x < u + w) {
				core.push([x, y]);
				if (wide) {
					const nx = x + (rand() < 0.5 ? -1 : 1);
					if (nx >= u && nx < u + w) core.push([nx, y]);
				}
			}
			const r = rand();
			if (r < 0.3) x -= 1; else if (r < 0.6) x += 1;
			x = Math.max(u, Math.min(u + w - 1, x));
			y += rand() < 0.8 ? 1 : 0;
			if (rand() < branchChance) {
				walk(x + (rand() < 0.5 ? -1 : 1), y + 1, Math.floor(len / 2), 0);
			}
		}
	};
	const starts = startsOverride || (w >= 8 ? 2 : 1);
	for (let s = 0; s < starts; s++) {
		const x0 = u + Math.floor(rand() * w);
		const y0 = v + Math.floor(rand() * (lengthScale < 1 ? h / 2 : 2));
		walk(x0, y0, Math.floor(h * (0.7 + rand() * 0.3) * lengthScale), extraBranches ? 0.25 : 0.1);
	}
	return core;
}

// Phase 2's cracks, generated once. Phase 3 is built FROM this set (widened + extra forks from a second RNG
// stream), so its cracks visibly grow out of the ones the player already saw in phase 2.
const BASE_CRACKS = (() => {
	const rand = mulberry32(0x0A7B + 17);
	return CRACK_FACES.map(face => crackPaths(rand, face, false, false));
})();
function phase3Cracks() {
	const rand = mulberry32(0x5EED + 3);
	return CRACK_FACES.map((face, i) => {
		const [u, v, w, h] = face;
		const core = BASE_CRACKS[i].slice();
		// widen about half of the existing crack, then one short new fork per face -- the damage is
		// spreading, not replacing the armour
		for (const [x, y] of BASE_CRACKS[i]) {
			if (rand() < 0.5) continue;
			const nx = x + (rand() < 0.5 ? -1 : 1);
			if (nx >= u && nx < u + w && y >= v && y < v + h) core.push([nx, y]);
		}
		return core.concat(crackPaths(rand, face, false, false, 0.45, 1));
	});
}

function crackedTexture(phase) {
	const buf = Buffer.from(out);
	const glow = Buffer.alloc(TW * TH * 4, 0);
	const coreRgb = phase === 3 ? [215, 255, 255] : [110, 235, 250];
	const rimRgb = phase === 3 ? [70, 200, 225] : [25, 120, 150];
	const cracks = phase === 3 ? phase3Cracks() : BASE_CRACKS;
	for (let fi = 0; fi < CRACK_FACES.length; fi++) {
		const face = CRACK_FACES[fi];
		const core = cracks[fi];
		const coreSet = new Set(core.map(([x, y]) => x + ',' + y));
		// darker rim first, around every core pixel, inside the same face only
		for (const [x, y] of core) {
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
				const nx = x + dx, ny = y + dy;
				if (nx < face[0] || nx >= face[0] + face[2] || ny < face[1] || ny >= face[1] + face[3]) continue;
				if (coreSet.has(nx + ',' + ny) || isEye(nx, ny)) continue;
				if (isLayerFace(face) && !baseOpaque(nx, ny)) continue;
				setPx(buf, nx, ny, rimRgb);
			}
		}
		for (const [x, y] of core) {
			if (isEye(x, y)) continue;
			if (isLayerFace(face) && !baseOpaque(x, y)) continue;
			setPx(buf, x, y, coreRgb);
			setPx(glow, x, y, coreRgb);
		}
	}
	return { buf, glow };
}

function eyeGlow(target, src) {
	for (const [x, y] of EYE_PIXELS) {
		const c = getPx(src, x, y);
		setPx(target, x, y, [c[0], c[1], c[2]]);
	}
}

// v0.13.9: "don't make him glow" -- the glow layer is gone from OathbreakerRenderer, so the _glowmask
// twins are no longer written (the crack/eye maths above still builds them; they're simply unused).
for (const phase of [2, 3]) {
	const { buf } = crackedTexture(phase);
	fs.writeFileSync(path.join(TEX_DIR, `oathbreaker_phase${phase}.png`), png.encode(TW, TH, buf));
}
console.log('wrote phase textures (phase2, phase3)');

// ---------------------------------------------------------------- v0.14.0 item: Broken Oath (16x16)
// A knight's oath-seal: a netherite disc with a dark-gold rim and a red gem, split in two by a jagged
// soul-fire crack, the halves pushed a pixel apart. Same palette as the boss (netherite, the eyes' red,
// the phase-2 cracks' cyan), so it reads as "his" drop.
{
	const S = 16;
	const buf = Buffer.alloc(S * S * 4, 0);
	const put = (x, y, rgb) => {
		if (x < 0 || y < 0 || x >= S || y >= S) return;
		const o = (y * S + x) * 4;
		buf[o] = rgb[0]; buf[o + 1] = rgb[1]; buf[o + 2] = rgb[2]; buf[o + 3] = 255;
	};
	const METAL = [64, 58, 66], METAL_LIGHT = [96, 88, 99], METAL_DARK = [38, 33, 40];
	const GOLD = [150, 118, 42], GOLD_DARK = [92, 70, 24];
	const RED = [216, 24, 12], RED_DARK = [120, 13, 6];
	const CYAN = [110, 235, 250], CYAN_DARK = [25, 120, 150];
	// the crack: a jagged line from top-right to bottom-left; x-offset of the crack at each row
	const crackX = [11, 10, 10, 9, 9, 8, 8, 7, 8, 7, 6, 6, 5, 5, 4, 4];
	const cx = 7.5, cy = 7.5;
	for (let y = 0; y < S; y++) {
		for (let x = 0; x < S; x++) {
			const d = Math.hypot(x - cx, y - cy);
			if (d > 6.9) continue;
			const leftHalf = x < crackX[y];
			// push the halves apart: left half up-left one pixel, right half down-right one pixel
			const tx = x + (leftHalf ? -1 : 1) * (y % 2 === 0 ? 1 : 0);
			const ty = y + (leftHalf ? -1 : 1) * (y > 3 && y < 13 ? 0 : 0);
			let c;
			if (d > 5.6) c = (x + y) % 3 === 0 ? GOLD_DARK : GOLD; // rim
			else if (d < 1.9) c = (x + y) % 2 === 0 ? RED : RED_DARK; // gem
			else if (x - y > 3) c = METAL_LIGHT; // a little top-right sheen
			else if (y - x > 4) c = METAL_DARK; // bottom-left shade
			else c = METAL;
			put(tx, ty, c);
		}
	}
	// the crack itself, drawn over the gap: bright core with a darker rim
	for (let y = 1; y < S - 1; y++) {
		const x = crackX[y];
		if (Math.hypot(x - cx, y - cy) > 7.2) continue;
		put(x, y, CYAN);
		put(x - 1, y, CYAN_DARK);
		if (y % 3 === 0) put(x + 1, y, CYAN_DARK);
	}
	const OUT_ITEM = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/item/broken_oath.png');
	fs.writeFileSync(OUT_ITEM, png.encode(S, S, buf));
	console.log('wrote item texture', OUT_ITEM);
}

// ---------------------------------------------------------------- geo.json

// Standard Minecraft/Blockbench box-UV algorithm: for a box of size (w,h,d) with uv origin (u,v),
// the six faces pack into a cross layout. Returns explicit {uv,uv_size} per face (the exported geo
// format wants these spelled out rather than an implicit single origin).
function boxUv(u, v, w, h, d) {
	return {
		up: { uv: [u + d, v], uv_size: [w, d] },
		down: { uv: [u + d + w, v], uv_size: [w, d] },
		west: { uv: [u, v + d], uv_size: [d, h] },
		north: { uv: [u + d, v + d], uv_size: [w, h] },
		east: { uv: [u + d + w, v + d], uv_size: [d, h] },
		south: { uv: [u + d + w + d, v + d], uv_size: [w, h] },
	};
}
function cube(origin, size, uv, inflate) {
	const c = { origin, size, uv };
	if (inflate) c.inflate = inflate;
	return c;
}

// v0.14.0: the second skin layer (hat/jacket/sleeves/trousers) is back -- done the way the source
// Blockbench file itself does it: each layer cube lives INSIDE the same bone as its base cube, inflated
// (hat 0.5, the rest 0.25, vanilla's own values), with the layer's box-UV origin. The v0.13.7 attempt
// that corrupted the model built the layer as separate CHILD BONES instead; no new bones here at all.
// Layer pixels are mostly transparent (armour trim, the visor frame with eye slits, plates), and
// GeckoLib's default entityCutoutNoCull render type discards them, so it reads as armour on top.
const bones = [
	{ name: 'root', pivot: [0, 0, 0] },
	{
		name: 'body', parent: 'root', pivot: [0, 24, 0],
		cubes: [
			cube([-4, 12, -2], [8, 12, 4], boxUv(16, 16, 8, 12, 4)),
			cube([-4, 12, -2], [8, 12, 4], boxUv(16, 32, 8, 12, 4), 0.25), // jacket
		],
	},
	{
		name: 'head', parent: 'body', pivot: [0, 24, 0],
		cubes: [
			cube([-4, 24, -4], [8, 8, 8], boxUv(0, 0, 8, 8, 8)),
			cube([-4, 24, -4], [8, 8, 8], boxUv(32, 0, 8, 8, 8), 0.5), // hat / helm
		],
	},
	{
		name: 'right_arm', parent: 'body', pivot: [4, 24, 0],
		cubes: [
			cube([4, 12, -2], [4, 12, 4], boxUv(40, 16, 4, 12, 4)),
			cube([4, 12, -2], [4, 12, 4], boxUv(40, 32, 4, 12, 4), 0.25), // sleeve
		],
	},
	{
		name: 'left_arm', parent: 'body', pivot: [-4, 24, 0],
		cubes: [
			cube([-8, 12, -2], [4, 12, 4], boxUv(32, 48, 4, 12, 4)),
			cube([-8, 12, -2], [4, 12, 4], boxUv(48, 48, 4, 12, 4), 0.25), // sleeve
		],
	},
	{
		name: 'right_leg', parent: 'body', pivot: [2, 12, 0],
		cubes: [
			cube([0.05, 0, -2], [3.9, 12, 4], boxUv(0, 16, 4, 12, 4)),
			cube([0.05, 0, -2], [3.9, 12, 4], boxUv(0, 32, 4, 12, 4), 0.25), // trouser leg
		],
	},
	{
		name: 'left_leg', parent: 'body', pivot: [-2, 12, 0],
		cubes: [
			cube([-3.95, 0, -2], [3.9, 12, 4], boxUv(16, 48, 4, 12, 4)),
			cube([-3.95, 0, -2], [3.9, 12, 4], boxUv(0, 48, 4, 12, 4), 0.25), // trouser leg
		],
	},
	{
		// The sword mount: pivots at the wrist (y=12, the bottom of the arm cube). v0.14.0: no cubes --
		// OathbreakerRenderer draws the real vanilla netherite sword item here (GeckoLib
		// BlockAndItemGeoLayer), oriented blade-down along the arm at rest. Every clip's `sword` track
		// still drives it exactly as it drove the old three-cuboid blade.
		name: 'sword', parent: 'right_arm', pivot: [6, 12, 0],
	},
];

const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [
		{
			description: {
				identifier: 'geometry.oathbreaker',
				texture_width: TW,
				texture_height: TH,
				visible_bounds_width: 3,
				visible_bounds_height: 3,
				visible_bounds_offset: [0, 1, 0],
			},
			bones,
		},
	],
};
fs.mkdirSync(path.dirname(OUT_GEO), { recursive: true });
fs.writeFileSync(OUT_GEO, JSON.stringify(geo, null, 1));
console.log('wrote geo', OUT_GEO);

// ---------------------------------------------------------------- animation.json

function kf(entries) {
	// entries: [[time, [x,y,z]], ...] or [[time, [x,y,z], 'easeoutquad'], ...] -- turns into the keyframe
	// map GeckoLib expects. v0.14.0: an optional third element is a GeckoLib easing name (lowercase, as
	// EasingType registers them), applied to the segment that ENDS at that keyframe -- e.g.
	// 'easeinquad' on a wind-up's peak, 'easeoutback' on a strike's contact frame.
	const out = {};
	for (const [t, v, easing] of entries) {
		const r = v.map(n => Math.round(n * 100) / 100);
		out[t.toFixed(2)] = easing ? { vector: r, easing } : r;
	}
	return out;
}

// v0.14.0: kneeling/staggering without a waist bone (hard rule: no new bones unless isolated and
// screenshotted -- and none is needed). Legs are children of `body`, whose pivot is at the SHOULDERS
// (y=24), so leaning the torso swings the hips backward and up and drags both legs along with it. This
// solves the pose in world terms instead: give it the torso lean and the WORLD angle each leg/arm/sword
// should end up at, and it returns the local rotations (world minus parent) plus the `root` offset that
// puts the lower foot back on the ground. Angle convention matches every existing clip: positive X
// tips a bone's top forward, so for anything hanging down from its pivot (legs, arms, sword) a NEGATIVE
// world angle swings the free end forward.
// v0.14.0 poses are written with these defaults: standing, sword arm angled a touch forward so the blade
// hangs dead vertical with its point planted on the ground (arm -8, sword world 0 -> tip lands at y~0).
const REST = { lean: 0, frontLeg: 0, rearLeg: 0, armWorld: -8, swordWorld: 0, leftArmWorld: 0, head: 0 };
function kneelPose(p) {
	p = Object.assign({}, REST, p);
	const rad = d => d * Math.PI / 180;
	const lean = p.lean; // torso, positive = slumped forward
	// hip, relative to the model origin, after rotating the body about its shoulder pivot (y=24)
	const hipY = 24 - 12 * Math.cos(rad(lean));
	const hipZ = -12 * Math.sin(rad(lean));
	// lowest foot below the hip for the requested world leg angles
	const footDrop = Math.max(12 * Math.cos(rad(p.frontLeg)), 12 * Math.cos(rad(p.rearLeg)));
	// airborne poses don't plant a foot -- only the torso-lean hip correction and any explicit lift apply
	const rootY = (p.air ? 12 - hipY : footDrop - hipY) + (p.lift || 0);
	const rootZ = -hipZ + (p.shiftZ || 0);
	return {
		root: [0, rootY, rootZ],
		body: [lean, p.twist || 0, p.roll || 0],
		head: [p.head, p.headYaw || 0, 0], // head is a child of body: its value is already relative
		right_leg: [p.frontLeg - lean, 0, 0],
		left_leg: [p.rearLeg - lean, 0, 0],
		right_arm: [p.armWorld - lean, p.armYaw || 0, p.armRoll || 0],
		// sword is a child of right_arm: world = arm world + local
		sword: [p.swordWorld - p.armWorld, p.swordYaw || 0, p.swordRoll || 0],
		left_arm: [p.leftArmWorld - lean, p.leftArmYaw || 0, p.leftArmRoll || 0],
	};
}
const pose = kneelPose;

const ZERO = [0, 0, 0];
// A clip made of named poses held/moved between keyframe times: frames = [[t, pose, easing?], ...],
// where pose is a kneelPose()-shaped object (missing bones default to zero). Emits position for root
// and rotation for everything, so every bone a clip touches always has a full, explicit track.
function poseClip(length, loop, frames) {
	const boneNames = ['root', 'body', 'head', 'right_leg', 'left_leg', 'right_arm', 'sword', 'left_arm'];
	const bones = {};
	const rest = kneelPose({}); // a null frame means "back to the rest pose"
	for (const b of boneNames) {
		const track = frames.map(([t, p, easing]) => [t, (p || rest)[b] || ZERO, easing]);
		if (b === 'root') {
			bones.root = { position: kf(track) };
		} else {
			bones[b] = { rotation: kf(track) };
		}
	}
	return anim(length, loop, bones);
}
function anim(length, loop, bones) {
	const a = { animation_length: length, bones };
	if (loop) a.loop = true;
	return a;
}

const animations = {};

// ---------------------------------------------------------------- locomotion / reactions (v0.14.0 rework)

// Idle: a heavy, slow breath. The sword point stays planted on the ground the whole time (the arm counter-
// rotates against the chest heave so the tip doesn't skate), the head lifts on the inhale and sinks on the
// exhale, and the free arm sways a hair.
const IDLE_EXHALE = pose({});
const IDLE_INHALE = pose({ lean: -2, armWorld: -8, head: -4, leftArmWorld: -3, leftArmRoll: -3, lift: 0.3 });
animations['animation.oathbreaker.idle'] = poseClip(3.0, true, [
	[0, IDLE_EXHALE],
	[1.3, IDLE_INHALE, 'easeinoutsine'],
	[3.0, IDLE_EXHALE, 'easeinoutsine'],
]);

// Walk: a heavy, deliberate stride. Real foot plant: at full stride both rigid legs are angled, which would
// lift both feet off the floor, so the hips drop (root y) by exactly the amount that keeps the planted foot
// on the ground -- that drop is also the "weight" in the step.
// v0.13.9 rework ("looks kinda goofy"). Three things were wrong: the free arm swung WITH the same-side leg
// (a camel's pacing gait -- it must swing with the opposite leg), the torso roll splayed both legs (they
// hang off the body), and a 1.0s cycle of +-30 degree strides covers ~3 blocks while he walked ~1 block/s,
// so his feet skated. Now: +-24 degree strides on a 1.4s cycle = 2.4 blocks per cycle, ~1.7 blocks/s, which
// matches his phase-1 speed (OathbreakerEntity plays it 1.25x in phase 2); a heel-strike "load" beat just
// after each contact where the hips sink under the weight; a slight forward lean; counter-rotating
// shoulders; the sword arm swinging a little against its leg with the blade trailing.
const WALK_STRIDE = 24;
function walkPose(side) {
	// side = +1: right (front) leg forward -> left arm forward, sword (right) arm back
	return pose({
		lean: 4, frontLeg: -WALK_STRIDE * side, rearLeg: WALK_STRIDE * side, twist: -4 * side,
		armWorld: -12 + 7 * side, swordWorld: 10 + 3 * side, leftArmWorld: -22 * side, leftArmRoll: -4, head: -3,
	});
}
function walkLoad(side) {
	// a beat after heel strike: legs already gathering under him, hips sunk a little deeper -- the weight
	return pose({
		lean: 5, frontLeg: -17 * side, rearLeg: 17 * side, twist: -3 * side,
		armWorld: -12 + 5 * side, swordWorld: 10 + 2 * side, leftArmWorld: -16 * side, leftArmRoll: -4, head: -1, lift: -0.7,
	});
}
const WALK_PASS = pose({ lean: 4, armWorld: -12, swordWorld: 10, leftArmWorld: 0, leftArmRoll: -4, head: -2, lift: 0.4 });
animations['animation.oathbreaker.walk'] = poseClip(1.4, true, [
	[0, walkPose(1)],
	[0.16, walkLoad(1), 'easeoutquad'],
	[0.42, WALK_PASS, 'easeinoutsine'],
	[0.7, walkPose(-1), 'easeinquad'],
	[0.86, walkLoad(-1), 'easeoutquad'],
	[1.12, WALK_PASS, 'easeinoutsine'],
	[1.4, walkPose(1), 'easeinquad'],
]);

// Flinch: a quick recoil back from the hit, head snapping, then a heavy settle. Only ever played while he
// isn't attacking or staggered (see OathbreakerEntity#actuallyHurt), so it never fights another clip.
animations['animation.oathbreaker.hit'] = poseClip(0.3, false, [
	[0, null],
	[0.07, pose({ lean: -8, head: -12, leftArmWorld: -18, leftArmRoll: -12, armWorld: -14, rearLeg: 6 }), 'easeoutquad'],
	[0.3, null, 'easeinoutquad'],
]);

// Spawn: coiled on one knee, head bowed, sword planted -- the same kneel as the stagger, deeper -- held for
// most of the clip, then he rises to the idle rest pose exactly as the clip (== SPAWN_TICKS) ends, so the
// hand-off to idle has no pop and AI wakes up the moment he's upright.
const SPAWN_KNEEL = pose({ lean: 28, frontLeg: -55, rearLeg: 58, armWorld: -45, swordWorld: 0, leftArmWorld: -30, head: 34 });
animations['animation.oathbreaker.spawn'] = poseClip(1.25, false, [
	[0, SPAWN_KNEEL],
	[0.8, SPAWN_KNEEL],
	[0.95, pose({ lean: 20, frontLeg: -45, rearLeg: 45, armWorld: -30, swordWorld: 0, leftArmWorld: -20, head: 10 }), 'easeinquad'],
	[1.25, null, 'easeoutquad'],
]);

// v0.14.0 death (3s == DEATH_TICKS): no collapse -- he sinks to BOTH knees (rigid legs folded back flat
// under him, hips dropped almost to the floor), plants the sword point-down in front of him with the off
// hand on the pommel, and bows his head over it. He holds there while the renderer fades him into the soul
// fire streaming off him (from DEATH_FADE_START_TICKS). "The oath... is fulfilled."
const DEATH_SAG = pose({ lean: 18, frontLeg: -30, rearLeg: 40, armWorld: -30, swordWorld: 5, leftArmWorld: -20, head: 15 });
const DEATH_KNEEL = pose({ lean: 10, frontLeg: 84, rearLeg: 86, armWorld: -38, swordWorld: 0, leftArmWorld: -42, leftArmYaw: -25, head: 20 });
const DEATH_BOW = pose({ lean: 16, frontLeg: 84, rearLeg: 86, armWorld: -36, swordWorld: 0, leftArmWorld: -40, leftArmYaw: -25, head: 38 });
const DEATH_BOW_DEEP = pose({ lean: 20, frontLeg: 84, rearLeg: 86, armWorld: -35, swordWorld: 0, leftArmWorld: -39, leftArmYaw: -25, head: 42 });
animations['animation.oathbreaker.death'] = poseClip(3.0, false, [
	[0, null],
	[0.35, DEATH_SAG, 'easeoutquad'],
	[0.75, DEATH_KNEEL, 'easeinquad'],
	[0.85, DEATH_KNEEL, 'easeoutquad'],
	[1.4, DEATH_BOW, 'easeinoutsine'],
	[3.0, DEATH_BOW_DEEP, 'easeinoutsine'],
]);

// v0.14.0: stagger -- poise broken. He drops to one knee with the sword planted point-down in front of
// him (the punish window), heaves for breath, then pushes back up to standing right as the clip (==
// OathbreakerTuning.STAGGER_TICKS) ends and AI resumes.
const STAGGER_KNEEL = kneelPose({
	lean: 18, frontLeg: -50, rearLeg: 55, armWorld: -40, swordWorld: 0, leftArmWorld: -25, head: 22,
});
const STAGGER_HEAVE = kneelPose({
	lean: 22, frontLeg: -50, rearLeg: 55, armWorld: -40, swordWorld: 0, leftArmWorld: -28, head: 28,
});
animations['animation.oathbreaker.stagger'] = poseClip(2.5, false, [
	[0, null],
	[0.25, STAGGER_KNEEL, 'easeoutquad'],
	[0.9, STAGGER_HEAVE, 'easeinoutsine'],
	[1.5, STAGGER_KNEEL, 'easeinoutsine'],
	[2.1, STAGGER_HEAVE, 'easeinoutsine'],
	[2.5, null, 'easeinoutquad'],
]);

// ---------------------------------------------------------------- attacks (v0.14.0 rework)
// Every strike follows the same beat: the wind-up pulls OPPOSITE the coming swing (anticipation, eased in
// with easeinquad), the strike accelerates into a CONTACT keyframe at a fixed time that the Java side
// resolves damage on (checked below against the *_CONTACT_TICKS constants), overshoots past it
// (follow-through), then settles heavily.

// ---- "Oath Shattered" (phase 1 -> 2, 3s, == TRANSITION_TICKS). He staggers back, sinks to one knee
// and raises the sword overhead in a reverse grip (both hands on the hilt, blade hanging point-down in
// front of his face), trembles, then DRIVES it into the ground at exactly TRANSITION_PLUNGE_TICKS -- the
// shockwave and the texture swap fire on that frame -- bows over it, and finally rips it out and stands.
const OS_REEL = pose({ lean: -14, frontLeg: 10, rearLeg: 28, armWorld: 10, swordWorld: 20, leftArmWorld: -30, leftArmRoll: -25, head: -18 });
const OS_RAISE = pose({ lean: 10, frontLeg: -50, rearLeg: 55, armWorld: -155, swordWorld: 5, leftArmWorld: -150, leftArmYaw: -25, head: -10 });
const OS_RAISE_HIGH = pose({ lean: 8, frontLeg: -50, rearLeg: 55, armWorld: -162, swordWorld: 3, leftArmWorld: -158, leftArmYaw: -25, head: -14 });
const OS_PLUNGE = pose({ lean: 25, frontLeg: -50, rearLeg: 55, armWorld: -40, swordWorld: 0, leftArmWorld: -45, leftArmYaw: -25, head: 20, lift: -1.5 });
const OS_BOWED = pose({ lean: 30, frontLeg: -50, rearLeg: 55, armWorld: -36, swordWorld: 0, leftArmWorld: -40, leftArmYaw: -25, head: 28, lift: -2.0 });
const OS_RIP = pose({ lean: -5, frontLeg: -30, rearLeg: 25, armWorld: -130, swordWorld: -160, leftArmWorld: -20, leftArmRoll: -20, head: -6 });
animations['animation.oathbreaker.phase_transition'] = poseClip(3.0, false, [
	[0, null],
	[0.2, OS_REEL, 'easeoutquad'],
	[0.55, OS_RAISE, 'easeinoutquad'],
	[1.3, OS_RAISE_HIGH, 'easeinoutsine'],
	[1.5, OS_PLUNGE, 'easeinquad'],
	[1.62, OS_BOWED, 'easeoutquad'],
	[2.25, OS_BOWED, 'easeinoutsine'],
	[2.55, OS_RIP, 'easeoutback'],
	[3.0, null, 'easeinoutquad'],
]);

// ---- Enrage (phase 2 -> 3, 1.5s == ENRAGE_TICKS): gathers himself, then at ENRAGE_ROAR_TICKS arches back
// and roars with his arms flung wide (the shake + brighter texture fire on that frame), then drops into a
// low, forward, hungrier ready stance. ----
const ENRAGE_GATHER = pose({ lean: 22, frontLeg: -30, rearLeg: 28, armWorld: -20, armRoll: 10, swordWorld: 10, leftArmWorld: -20, leftArmRoll: 10, head: 25 });
const ENRAGE_ROAR = pose({ lean: -18, frontLeg: -24, rearLeg: 22, armWorld: -95, armRoll: 55, swordWorld: -100, leftArmWorld: -95, leftArmRoll: -55, head: -32 });
const ENRAGE_ROAR_B = pose({ lean: -20, frontLeg: -24, rearLeg: 22, armWorld: -100, armRoll: 58, swordWorld: -104, leftArmWorld: -100, leftArmRoll: -58, head: -35 });
const ENRAGE_READY = pose({ lean: 14, frontLeg: -26, rearLeg: 24, armWorld: 10, armRoll: 8, swordWorld: 40, leftArmWorld: -25, leftArmRoll: -15, head: 6 });
animations['animation.oathbreaker.enrage'] = poseClip(1.5, false, [
	[0, null],
	[0.3, ENRAGE_GATHER, 'easeinquad'],
	[0.5, ENRAGE_ROAR, 'easeoutback'],
	[0.8, ENRAGE_ROAR_B, 'easeinoutsine'],
	[1.1, ENRAGE_ROAR, 'easeinoutsine'],
	[1.5, ENRAGE_READY, 'easeinoutquad'],
]);

// ---- Run (phase 3 chase, loop): leaning hard into it, long strides with a real foot plant, the off arm
// pumping, the sword trailing behind with its point dragging low. ----
// v0.13.9: the off arm pumps with the OPPOSITE leg now (it was pacing, like the old walk), and the cycle
// is 1.0s at +-32 degrees = ~3.2 blocks/s, matching his phase-3 speed so the feet don't skate.
function runPose(side) {
	return pose({
		lean: 16, frontLeg: -32 * side, rearLeg: 32 * side, twist: -6 * side,
		armWorld: 34 + 6 * side, swordWorld: 80, leftArmWorld: -38 * side, leftArmRoll: -8, head: -9,
	});
}
const RUN_PASS = pose({ lean: 18, frontLeg: 0, rearLeg: 0, armWorld: 34, swordWorld: 80, leftArmWorld: 0, leftArmRoll: -8, head: -9, lift: 0.8 });
animations['animation.oathbreaker.run'] = poseClip(1.0, true, [
	[0, runPose(1)],
	[0.25, RUN_PASS, 'easeoutquad'],
	[0.5, runPose(-1), 'easeinquad'],
	[0.75, RUN_PASS, 'easeoutquad'],
	[1.0, runPose(1), 'easeinquad'],
]);

// ---- Judgement (phase 3): springs straight up with both hands on the hilt overhead in a reverse grip
// (blade hanging point-down), hangs there over his target, then drives the blade down into the ground on
// landing at JUDGEMENT_FALL_TICKS and stays bowed over it -- the punish window. ----
const JDG_CROUCH = pose({ lean: 26, frontLeg: -40, rearLeg: 38, armWorld: -30, swordWorld: -10, leftArmWorld: -30, head: 10 });
const JDG_UP = pose({ lean: -4, frontLeg: -20, rearLeg: 20, armWorld: -168, swordWorld: 5, leftArmWorld: -162, leftArmYaw: -25, head: -20, air: true });
const JDG_HANG = pose({ lean: 6, frontLeg: -48, rearLeg: 22, armWorld: -165, swordWorld: 3, leftArmWorld: -160, leftArmYaw: -25, head: 18, air: true });
const JDG_HANG_B = pose({ lean: 8, frontLeg: -50, rearLeg: 24, armWorld: -168, swordWorld: 2, leftArmWorld: -163, leftArmYaw: -25, head: 22, air: true });
const JDG_IMPACT = pose({ lean: 28, frontLeg: -52, rearLeg: 56, armWorld: -38, swordWorld: 0, leftArmWorld: -42, leftArmYaw: -25, head: 22, lift: -1.5 });
const JDG_BOWED = pose({ lean: 34, frontLeg: -52, rearLeg: 58, armWorld: -34, swordWorld: 0, leftArmWorld: -38, leftArmYaw: -25, head: 30, lift: -2.2 });
animations['animation.oathbreaker.judgement_rise'] = poseClip(0.5, false, [
	[0, null],
	[0.1, JDG_CROUCH, 'easeoutquad'],
	[0.5, JDG_UP, 'easeoutquad'],
]);
animations['animation.oathbreaker.judgement_hang'] = poseClip(1.0, false, [
	[0, JDG_UP],
	[0.25, JDG_HANG, 'easeoutquad'],
	[0.6, JDG_HANG_B, 'easeinoutsine'],
	[1.0, JDG_HANG, 'easeinoutsine'],
]);
animations['animation.oathbreaker.judgement_slam'] = poseClip(1.2, false, [
	[0, JDG_HANG],
	[0.25, JDG_IMPACT, 'easeinquad'],
	[0.35, JDG_BOWED, 'easeoutquad'],
	[0.9, JDG_BOWED, 'easeinoutsine'],
	[1.2, null, 'easeinoutquad'],
]);

// ---- Execution (phase 3, the unblockable grab): the OFF hand does the grabbing, the sword stays cocked
// back the whole time. Coil -> lunge -> hold the victim up in front -> drive the blade through (contact at
// EXECUTION_IMPALE_CONTACT_TICKS) and fling them off. A whiff overextends him badly. ----
const EX_COIL = pose({ lean: 12, twist: 18, frontLeg: -30, rearLeg: 30, armWorld: 30, swordWorld: -80, leftArmWorld: 25, leftArmRoll: -20, head: 4 });
const EX_COIL_B = pose({ lean: 15, twist: 20, frontLeg: -32, rearLeg: 32, armWorld: 32, swordWorld: -82, leftArmWorld: 30, leftArmRoll: -22, head: 6 });
const EX_REACH = pose({ lean: 18, twist: -10, frontLeg: -46, rearLeg: 38, armWorld: 30, swordWorld: -80, leftArmWorld: -95, leftArmRoll: 5, head: 2, shiftZ: 3 });
const EX_HOLD = pose({ lean: -4, twist: 6, frontLeg: -24, rearLeg: 22, armWorld: 28, swordWorld: -86, leftArmWorld: -125, leftArmRoll: 8, head: -12 });
const EX_HOLD_B = pose({ lean: -5, twist: 7, frontLeg: -24, rearLeg: 22, armWorld: 31, swordWorld: -88, leftArmWorld: -128, leftArmRoll: 8, head: -14 });
const EX_IMPALE = pose({ lean: 12, twist: -12, frontLeg: -40, rearLeg: 32, armWorld: -85, swordWorld: -92, leftArmWorld: -120, leftArmRoll: 8, head: -4, shiftZ: 2 });
const EX_FLING = pose({ lean: 6, twist: -20, frontLeg: -34, rearLeg: 28, armWorld: -80, swordWorld: -95, leftArmWorld: -40, leftArmRoll: -45, head: 0 });
animations['animation.oathbreaker.execution_windup'] = poseClip(0.8, false, [
	[0, null],
	[0.45, EX_COIL, 'easeinquad'],
	[0.8, EX_COIL_B, 'easeinoutsine'],
]);
animations['animation.oathbreaker.execution_lunge'] = poseClip(0.2, false, [
	[0, EX_COIL_B],
	[0.2, EX_REACH, 'easeoutquad'],
]);
animations['animation.oathbreaker.execution_hold'] = poseClip(1.5, false, [
	[0, EX_REACH],
	[0.2, EX_HOLD, 'easeoutquad'],
	[0.8, EX_HOLD_B, 'easeinoutsine'],
	[1.5, EX_HOLD, 'easeinoutsine'],
]);
animations['animation.oathbreaker.execution_impale'] = poseClip(0.8, false, [
	[0, EX_HOLD],
	[0.15, pose({ lean: -8, twist: 14, frontLeg: -24, rearLeg: 22, armWorld: 45, swordWorld: -85, leftArmWorld: -125, leftArmRoll: 8, head: -12 }), 'easeinquad'],
	[0.3, EX_IMPALE, 'easeoutback'],
	[0.5, EX_FLING, 'easeoutquad'],
	[0.8, null, 'easeinoutquad'],
]);
animations['animation.oathbreaker.execution_whiff'] = poseClip(1.5, false, [
	[0, EX_REACH],
	[0.25, pose({ lean: 30, twist: -14, frontLeg: -52, rearLeg: 44, armWorld: 20, swordWorld: -40, leftArmWorld: -55, leftArmRoll: 10, head: 16, lift: -1.0 }), 'easeoutquad'],
	[1.0, pose({ lean: 26, twist: -10, frontLeg: -48, rearLeg: 40, armWorld: 10, swordWorld: -20, leftArmWorld: -40, leftArmRoll: 10, head: 12, lift: -0.8 }), 'easeinoutsine'],
	[1.5, null, 'easeinoutquad'],
]);

// ---- Stance Dash (30 dmg): draw back, lunge, one big forward-down diagonal cut, hold the stance. ----
const DASH_WINDUP = pose({
	lean: -8, twist: -16, frontLeg: -28, rearLeg: 24, armWorld: -150, armRoll: -12, swordWorld: -195,
	leftArmWorld: -40, leftArmRoll: -20, head: 6,
});
const DASH_WINDUP_DEEP = pose({
	lean: -10, twist: -20, frontLeg: -30, rearLeg: 26, armWorld: -156, armRoll: -14, swordWorld: -202,
	leftArmWorld: -44, leftArmRoll: -22, head: 8,
});
const DASH_CONTACT = pose({
	lean: 14, twist: 12, frontLeg: -42, rearLeg: 34, armWorld: -78, armRoll: 8, swordWorld: -80,
	leftArmWorld: 20, leftArmRoll: -10, head: 4,
});
const DASH_FOLLOW = pose({
	lean: 20, twist: 24, frontLeg: -44, rearLeg: 38, armWorld: 18, armRoll: 16, swordWorld: 40,
	leftArmWorld: 30, leftArmRoll: -14, head: 6,
});
const DASH_SETTLE = pose({
	lean: 17, twist: 20, frontLeg: -42, rearLeg: 36, armWorld: 10, armRoll: 14, swordWorld: 32,
	leftArmWorld: 22, leftArmRoll: -12, head: 4,
});
animations['animation.oathbreaker.windup_dash'] = poseClip(1.5, false, [
	[0, null],
	[0.35, DASH_WINDUP, 'easeinquad'],
	[0.9, DASH_WINDUP_DEEP, 'easeinoutsine'],
	[1.2, DASH_WINDUP, 'easeinoutsine'],
	[1.5, DASH_WINDUP_DEEP, 'easeinoutsine'],
]);
animations['animation.oathbreaker.dash_attack'] = poseClip(0.3, false, [
	[0, DASH_WINDUP_DEEP],
	[0.1, DASH_CONTACT, 'easeinquad'],
	[0.2, DASH_FOLLOW, 'easeoutquad'],
	[0.3, DASH_SETTLE, 'easeinoutsine'],
]);
// Held low and committed for 2s -- the punish window -- then he recovers to rest.
animations['animation.oathbreaker.post_dash'] = poseClip(2.0, false, [
	[0, DASH_SETTLE],
	[0.9, pose({ lean: 19, twist: 20, frontLeg: -42, rearLeg: 36, armWorld: 12, armRoll: 14, swordWorld: 34, leftArmWorld: 24, leftArmRoll: -12, head: 8 }), 'easeinoutsine'],
	[1.55, DASH_SETTLE, 'easeinoutsine'],
	[2.0, null, 'easeinoutquad'],
]);

// ---- Combo (10 dmg per hit): four directions. [windup, contact, follow-through] per hit. ----
// Directions are carried mostly by the torso twist and the sword arm's roll (the arms are children of the
// body, so twisting the torso swings the blade), with the arm's pitch deciding high vs low.
const COMBO = [
	{ // 1: diagonal from high on the sword side, across and down
		windup: pose({ twist: -32, lean: -4, frontLeg: -18, rearLeg: 16, armWorld: -150, armRoll: -30, swordWorld: -190, leftArmWorld: -20, leftArmRoll: -15, head: 4 }),
		contact: pose({ twist: 10, lean: 8, frontLeg: -24, rearLeg: 20, armWorld: -85, armRoll: 10, swordWorld: -95, leftArmWorld: 15, leftArmRoll: -10, head: 4 }),
		follow: pose({ twist: 30, lean: 12, frontLeg: -24, rearLeg: 20, armWorld: -20, armRoll: 28, swordWorld: -10, leftArmWorld: 22, leftArmRoll: -12, head: 6 }),
	},
	{ // 2: the mirror diagonal, back the other way
		windup: pose({ twist: 34, lean: -4, frontLeg: -18, rearLeg: 16, armWorld: -145, armRoll: 30, swordWorld: -185, leftArmWorld: 10, leftArmRoll: -10, head: 4 }),
		contact: pose({ twist: -8, lean: 8, frontLeg: -24, rearLeg: 20, armWorld: -85, armRoll: -10, swordWorld: -95, leftArmWorld: -10, leftArmRoll: -8, head: 4 }),
		follow: pose({ twist: -30, lean: 12, frontLeg: -24, rearLeg: 20, armWorld: -25, armRoll: -30, swordWorld: -15, leftArmWorld: -20, leftArmRoll: -12, head: 6 }),
	},
	{ // 3: flat horizontal sweep at chest height
		windup: pose({ twist: 45, lean: 0, frontLeg: -16, rearLeg: 18, armWorld: -90, armRoll: 35, swordWorld: -90, leftArmWorld: 15, leftArmRoll: -20, head: 0 }),
		contact: pose({ twist: 0, lean: 6, frontLeg: -22, rearLeg: 22, armWorld: -90, armRoll: 0, swordWorld: -90, leftArmWorld: 0, leftArmRoll: -15, head: 2 }),
		follow: pose({ twist: -48, lean: 8, frontLeg: -22, rearLeg: 22, armWorld: -85, armRoll: -30, swordWorld: -80, leftArmWorld: -10, leftArmRoll: -20, head: 4 }),
	},
	{ // 4: overhead slam -- rises onto the balls of his feet, then crashes down and hunches over it
		windup: pose({ twist: 0, lean: -10, frontLeg: -14, rearLeg: 12, armWorld: -178, armRoll: -6, swordWorld: -200, leftArmWorld: -165, leftArmRoll: 10, head: -10, lift: 0.8 }),
		contact: pose({ twist: 0, lean: 20, frontLeg: -32, rearLeg: 28, armWorld: -60, armRoll: -4, swordWorld: -55, leftArmWorld: -55, leftArmRoll: 6, head: 14 }),
		follow: pose({ twist: 0, lean: 28, frontLeg: -36, rearLeg: 32, armWorld: -30, armRoll: -4, swordWorld: -8, leftArmWorld: -30, leftArmRoll: 6, head: 20, lift: -1.2 }),
	},
	{ // 5 (phase 2+): a straight thrust -- blade drawn back level at the hip, then driven forward in a lunge
		windup: pose({ twist: -25, lean: -5, frontLeg: -20, rearLeg: 22, armWorld: 25, swordWorld: -90, leftArmWorld: -60, leftArmRoll: -20, head: 6 }),
		contact: pose({ twist: 10, lean: 16, frontLeg: -46, rearLeg: 36, armWorld: -90, swordWorld: -90, leftArmWorld: 25, leftArmRoll: -15, head: 4, shiftZ: 3 }),
		follow: pose({ twist: 14, lean: 20, frontLeg: -48, rearLeg: 38, armWorld: -94, swordWorld: -94, leftArmWorld: 30, leftArmRoll: -15, head: 4, shiftZ: 4 }),
	},
];
COMBO.forEach((hit, i) => {
	const n = i + 1;
	// Each wind-up starts from where the previous strike settled, so the chain flows instead of snapping
	// back to rest between hits.
	const from = i === 0 ? null : COMBO[i - 1].follow;
	animations[`animation.oathbreaker.combo_windup_${n}`] = poseClip(0.5, false, [
		[0, from],
		[0.4, hit.windup, 'easeinquad'],
		[0.5, hit.windup, 'easeinoutsine'],
	]);
	animations[`animation.oathbreaker.combo_strike_${n}`] = poseClip(0.35, false, [
		[0, hit.windup],
		[0.1, hit.contact, 'easeinquad'],
		[0.18, hit.follow, 'easeoutquad'],
		[0.35, hit.follow, 'easeinoutsine'],
	]);
	// Phase 2+ "Elden Ring delay": a looping hold of this wind-up's peak (a slow, straining tremble) for the
	// random 0-0.6s extra wait before the strike. Looping, so it holds however long Java needs.
	const strain = Object.fromEntries(Object.entries(hit.windup).map(([b, v]) =>
		[b, b === 'right_arm' ? [v[0] - 3, v[1], v[2]] : b === 'head' ? [v[0] + 2, v[1], v[2]] : v]));
	animations[`animation.oathbreaker.combo_hold_${n}`] = poseClip(0.5, true, [
		[0, hit.windup],
		[0.25, strain, 'easeinoutsine'],
		[0.5, hit.windup, 'easeinoutsine'],
	]);
});

// ---- Stance Dash feint (phase 2+): the weight rocks forward as if to go -- then settles back into the
// wind-up, and the real dash fires the moment this 0.5s clip ends. ----
animations['animation.oathbreaker.dash_feint'] = poseClip(0.5, false, [
	[0, DASH_WINDUP_DEEP],
	[0.14, pose({ lean: 8, twist: -8, frontLeg: -38, rearLeg: 30, armWorld: -135, armRoll: -8, swordWorld: -180, leftArmWorld: -20, leftArmRoll: -15, head: 4 }), 'easeoutquad'],
	[0.5, DASH_WINDUP_DEEP, 'easeinoutquad'],
]);

// ---- Soul Rend (phase 2+): sword dragged along the ground behind him, then a rising slash that sends
// the eruption line out (contact 0.10s == SOUL_REND_CONTACT_TICKS). ----
const REND_DRAG = pose({ lean: 22, twist: -30, frontLeg: -40, rearLeg: 34, armWorld: 45, armRoll: -10, swordWorld: 95, leftArmWorld: -45, leftArmRoll: -15, head: -6 });
const REND_DRAG_DEEP = pose({ lean: 26, twist: -38, frontLeg: -44, rearLeg: 38, armWorld: 50, armRoll: -12, swordWorld: 100, leftArmWorld: -50, leftArmRoll: -18, head: -8 });
animations['animation.oathbreaker.soul_rend_windup'] = poseClip(0.8, false, [
	[0, null],
	[0.35, REND_DRAG, 'easeinquad'],
	[0.8, REND_DRAG_DEEP, 'easeinoutsine'],
]);
animations['animation.oathbreaker.soul_rend_strike'] = poseClip(0.6, false, [
	[0, REND_DRAG_DEEP],
	[0.1, pose({ lean: 10, twist: 0, frontLeg: -40, rearLeg: 34, armWorld: -60, swordWorld: -80, leftArmWorld: -20, head: -4 }), 'easeinquad'],
	[0.25, pose({ lean: -8, twist: 12, frontLeg: -30, rearLeg: 26, armWorld: -170, swordWorld: -195, leftArmWorld: 10, head: -12 }), 'easeoutquad'],
	[0.6, null, 'easeinoutquad'],
]);

// ---- Chains of the Forsworn (phase 2+): thrown with the OFF hand (the sword stays in the other). ----
const CHAIN_COCK = pose({ twist: 25, lean: -4, frontLeg: -20, rearLeg: 18, leftArmWorld: -170, leftArmRoll: -10, armWorld: -10, swordWorld: 5, head: 0 });
const CHAIN_OUT = pose({ twist: -18, lean: 10, frontLeg: -32, rearLeg: 26, leftArmWorld: -92, leftArmRoll: 0, armWorld: -10, swordWorld: 5, head: 2 });
const CHAIN_OUT_B = pose({ twist: -18, lean: 11, frontLeg: -32, rearLeg: 26, leftArmWorld: -89, leftArmRoll: 2, armWorld: -12, swordWorld: 5, head: 3 });
animations['animation.oathbreaker.chain_throw'] = poseClip(0.5, false, [
	[0, null],
	[0.32, CHAIN_COCK, 'easeinquad'],
	[0.5, CHAIN_OUT, 'easeoutback'],
]);
// held while the chain is in flight (looping -- the flight time depends on the distance)
animations['animation.oathbreaker.chain_hold'] = poseClip(0.4, true, [
	[0, CHAIN_OUT],
	[0.2, CHAIN_OUT_B, 'easeinoutsine'],
	[0.4, CHAIN_OUT, 'easeinoutsine'],
]);
// caught: the off hand hauls back while the sword comes up into combo wind-up 1, so strike 1 flows on
animations['animation.oathbreaker.chain_pull'] = poseClip(0.5, false, [
	[0, CHAIN_OUT],
	[0.2, pose({ twist: 10, lean: -10, frontLeg: -20, rearLeg: 24, leftArmWorld: 20, leftArmRoll: -20, armWorld: -90, swordWorld: -120, head: -2 }), 'easeoutquad'],
	[0.5, COMBO[0].windup, 'easeinoutquad'],
]);
// missed: two reeling hauls on the empty chain, hunched and open -- the punish window
animations['animation.oathbreaker.chain_recover'] = poseClip(1.0, false, [
	[0, CHAIN_OUT],
	[0.2, pose({ twist: -5, lean: 14, frontLeg: -26, rearLeg: 22, leftArmWorld: -20, leftArmRoll: -15, armWorld: -10, swordWorld: 5, head: 10 }), 'easeoutquad'],
	[0.4, pose({ twist: -12, lean: 12, frontLeg: -26, rearLeg: 22, leftArmWorld: -80, armWorld: -10, swordWorld: 5, head: 8 }), 'easeinoutsine'],
	[0.6, pose({ twist: -5, lean: 14, frontLeg: -26, rearLeg: 22, leftArmWorld: 10, leftArmRoll: -15, armWorld: -10, swordWorld: 5, head: 10 }), 'easeoutquad'],
	[1.0, null, 'easeinoutquad'],
]);

// ---- Oath Guard: sword raised vertically in front of the face, blade up, off hand braced on it. ----
const GUARD = pose({
	lean: 4, frontLeg: -20, rearLeg: 16, armWorld: -100, armYaw: 28, swordWorld: -185,
	leftArmWorld: -80, leftArmYaw: -30, head: 10,
});
const GUARD_BREATH = pose({
	lean: 6, frontLeg: -20, rearLeg: 16, armWorld: -98, armYaw: 28, swordWorld: -183,
	leftArmWorld: -78, leftArmYaw: -30, head: 12,
});
animations['animation.oathbreaker.guard_stance'] = poseClip(1.5, false, [
	[0, null],
	[0.18, GUARD, 'easeoutback'],
	[0.8, GUARD_BREATH, 'easeinoutsine'],
	[1.3, GUARD, 'easeinoutsine'],
	[1.5, null, 'easeinoutquad'],
]);

// ---- Riposte: from the guard, a tiny draw back, then a fast straight thrust (contact 0.15s). ----
const RIPOSTE_DRAW = pose({
	lean: -2, twist: -10, frontLeg: -20, rearLeg: 18, armWorld: -60, swordWorld: -90, leftArmWorld: -40, head: 6,
});
const RIPOSTE_THRUST = pose({
	lean: 14, twist: 8, frontLeg: -44, rearLeg: 34, armWorld: -92, swordWorld: -92, leftArmWorld: 20, leftArmRoll: -15,
	head: 4, shiftZ: 3,
});
animations['animation.oathbreaker.riposte'] = poseClip(0.4, false, [
	[0, GUARD],
	[0.08, RIPOSTE_DRAW, 'easeinquad'],
	[0.15, RIPOSTE_THRUST, 'easeoutback'],
	[0.3, RIPOSTE_THRUST, 'easeinoutsine'],
	[0.4, null, 'easeinoutquad'],
]);

// ---- Backstep: dip, hop back (the entity moves; this is the body language), land and absorb. ----
animations['animation.oathbreaker.backstep'] = poseClip(0.5, false, [
	[0, null],
	[0.07, pose({ lean: 12, frontLeg: -20, rearLeg: 20, armWorld: -20, swordWorld: 5, leftArmWorld: -10, head: 4 }), 'easeoutquad'],
	[0.2, pose({ lean: -12, frontLeg: -28, rearLeg: 8, armWorld: -40, swordWorld: -20, leftArmWorld: -30, leftArmRoll: -20, head: -4, air: true }), 'easeoutquad'],
	[0.34, pose({ lean: 8, frontLeg: -22, rearLeg: 26, armWorld: -15, swordWorld: 5, leftArmWorld: -15, head: 4, lift: -1.2 }), 'easeinquad'],
	[0.5, null, 'easeinoutquad'],
]);

// ---- Leaping Cleave: crouch and coil, launch with the sword cocked over the shoulder, arch back at the
// apex, then chop straight down on landing and sink into a heavy settle (the punish window). ----
const LEAP_COIL = pose({
	lean: 28, frontLeg: -40, rearLeg: 38, armWorld: -165, armRoll: -10, swordWorld: -200, leftArmWorld: 25, head: -8,
});
const LEAP_COIL_DEEP = pose({
	lean: 32, frontLeg: -46, rearLeg: 44, armWorld: -168, armRoll: -10, swordWorld: -205, leftArmWorld: 30, head: -10,
});
const LEAP_LAUNCH = pose({
	lean: -8, frontLeg: -30, rearLeg: 22, armWorld: -175, swordWorld: -215, leftArmWorld: -30, leftArmRoll: -30, head: -8, air: true,
});
const LEAP_APEX = pose({
	lean: -16, frontLeg: -40, rearLeg: 10, armWorld: -185, swordWorld: -225, leftArmWorld: -60, leftArmRoll: -30, head: -12, air: true,
});
const LEAP_CHOP = pose({
	lean: 36, frontLeg: -50, rearLeg: 50, armWorld: -32, swordWorld: -8, leftArmWorld: -25, head: 20,
});
const LEAP_SETTLE = pose({
	lean: 40, frontLeg: -52, rearLeg: 54, armWorld: -28, swordWorld: -2, leftArmWorld: -22, head: 24, lift: -0.8,
});
animations['animation.oathbreaker.leap_windup'] = poseClip(0.6, false, [
	[0, null],
	[0.45, LEAP_COIL, 'easeinquad'],
	[0.6, LEAP_COIL_DEEP, 'easeinoutsine'],
]);
animations['animation.oathbreaker.leap_air'] = poseClip(0.8, false, [
	[0, LEAP_COIL_DEEP],
	[0.12, LEAP_LAUNCH, 'easeoutquad'],
	[0.55, LEAP_APEX, 'easeinoutsine'],
	[0.8, LEAP_APEX, 'easeinoutsine'],
]);
animations['animation.oathbreaker.leap_land'] = poseClip(0.8, false, [
	[0, LEAP_APEX],
	[0.08, LEAP_CHOP, 'easeinquad'],
	[0.16, LEAP_SETTLE, 'easeoutquad'],
	[0.55, LEAP_SETTLE, 'easeinoutsine'],
	[0.8, null, 'easeinoutquad'],
]);

// ---- v0.13.19 Oathbound Whirlwind (all phases): the sword drawn low behind him, weight sunk (0.7s wind-up),
// then TWO full spins -- the root bone turns 0 -> 719 degrees over WHIRLWIND_SPIN_TICKS with the sword arm held
// out level -- hitting everything round him on the two contact frames (one per spin), then a dizzy, swaying
// recovery with the blade dragging (the punish window). Spins + recovery are ONE clip on purpose: GeckoLib's
// bone reset snaps a "suspected completed rotation" (just under a whole number of turns) straight back to 0 once
// no clip animates the bone -- but a following clip keyed at 0 would instead lerp him 719 degrees backwards
// over its 1-tick transition. So the root holds 719 until the clip ends, and nothing else keys root rotation.
// (719, not 720: GeckoLib's check only catches values at or just BELOW a whole turn.)
const WW_DRAW = pose({ lean: 18, twist: -40, frontLeg: -38, rearLeg: 32, armWorld: 50, armRoll: -20, swordWorld: 85, leftArmWorld: -40, leftArmRoll: -25, head: -4 });
const WW_DRAW_DEEP = pose({ lean: 22, twist: -46, frontLeg: -42, rearLeg: 36, armWorld: 55, armRoll: -22, swordWorld: 90, leftArmWorld: -45, leftArmRoll: -28, head: -6 });
const WW_SPIN = pose({ lean: 8, twist: 25, frontLeg: -26, rearLeg: 24, armWorld: -90, armRoll: 40, swordWorld: -92, leftArmWorld: -70, leftArmRoll: -50, head: 4 });
const WW_SPIN_B = pose({ lean: 10, twist: 28, frontLeg: -28, rearLeg: 26, armWorld: -86, armRoll: 44, swordWorld: -88, leftArmWorld: -74, leftArmRoll: -54, head: 6, lift: -0.3 });
const WW_DIZZY_A = pose({ lean: 24, roll: 8, twist: 6, frontLeg: -30, rearLeg: 28, armWorld: 10, armRoll: 6, swordWorld: 35, leftArmWorld: -10, leftArmRoll: -18, head: 18, headYaw: 12, lift: -0.6 });
const WW_DIZZY_B = pose({ lean: 26, roll: -8, twist: -6, frontLeg: -28, rearLeg: 30, armWorld: 14, armRoll: 4, swordWorld: 38, leftArmWorld: -14, leftArmRoll: -14, head: 22, headYaw: -12, lift: -0.7 });
animations['animation.oathbreaker.whirlwind_windup'] = poseClip(0.7, false, [
	[0, null],
	[0.4, WW_DRAW, 'easeinquad'],
	[0.7, WW_DRAW_DEEP, 'easeinoutsine'],
]);
animations['animation.oathbreaker.whirlwind_strike'] = poseClip(2.3, false, [
	[0, WW_DRAW_DEEP],
	[0.1, WW_SPIN, 'easeoutquad'],
	[0.2, WW_SPIN_B, 'easeinoutsine'], // contact 1 (WHIRLWIND_HIT1_TICKS)
	[0.4, WW_SPIN, 'easeinoutsine'],
	[0.6, WW_SPIN_B, 'easeinoutsine'], // contact 2 (WHIRLWIND_HIT2_TICKS)
	[0.8, WW_SPIN, 'easeinoutsine'], // spins end (WHIRLWIND_SPIN_TICKS)
	[1.0, WW_DIZZY_A, 'easeoutquad'],
	[1.35, WW_DIZZY_B, 'easeinoutsine'],
	[1.7, WW_DIZZY_A, 'easeinoutsine'],
	[2.0, WW_DIZZY_B, 'easeinoutsine'],
	[2.3, null, 'easeinoutquad'],
]);
{
	// the spin itself: constant 900 deg/s (linear keys every 0.1s), then held at 719 to the end of the clip
	const spin = [];
	for (let i = 0; i <= 8; i++) spin.push([i * 0.1, [0, Math.min(719, i * 90), 0]]);
	spin.push([2.3, [0, 719, 0]]);
	animations['animation.oathbreaker.whirlwind_strike'].bones.root.rotation = kf(spin);
}

// ---- v0.13.19 Grave Geysers (phase 2+): both hands on the hilt overhead in a reverse grip (blade hanging point-
// down in front of his face, like Oath Shattered), then he drives it into the ground and drops to one knee --
// the blade goes in on GEYSER_PLUNGE_CONTACT_TICKS, when the telegraph rings appear under every player -- and
// stays bowed over it while the geysers come (the punish window), then rips it out and stands. ----
const GY_RAISE = pose({ lean: -4, frontLeg: -22, rearLeg: 20, armWorld: -160, swordWorld: 5, leftArmWorld: -155, leftArmYaw: -25, head: -14 });
const GY_RAISE_HIGH = pose({ lean: -6, frontLeg: -22, rearLeg: 20, armWorld: -168, swordWorld: 3, leftArmWorld: -163, leftArmYaw: -25, head: -18, lift: 0.3 });
const GY_BOWED_B = pose({ lean: 33, frontLeg: -50, rearLeg: 55, armWorld: -35, swordWorld: 0, leftArmWorld: -39, leftArmYaw: -25, head: 32, lift: -2.1 });
animations['animation.oathbreaker.geyser_windup'] = poseClip(0.8, false, [
	[0, null],
	[0.35, GY_RAISE, 'easeoutquad'],
	[0.8, GY_RAISE_HIGH, 'easeinoutsine'],
]);
animations['animation.oathbreaker.geyser_plunge'] = poseClip(0.5, false, [
	[0, GY_RAISE_HIGH],
	[0.15, OS_PLUNGE, 'easeinquad'], // the blade goes in (GEYSER_PLUNGE_CONTACT_TICKS)
	[0.5, OS_BOWED, 'easeoutquad'],
]);
animations['animation.oathbreaker.geyser_bowed'] = poseClip(2.2, false, [
	[0, OS_BOWED],
	[0.8, GY_BOWED_B, 'easeinoutsine'],
	[1.5, OS_BOWED, 'easeinoutsine'],
	[1.95, OS_RIP, 'easeoutback'],
	[2.2, null, 'easeinoutquad'],
]);

const animFile = { format_version: '1.8.0', animations };
fs.mkdirSync(path.dirname(OUT_ANIM), { recursive: true });
fs.writeFileSync(OUT_ANIM, JSON.stringify(animFile, null, 1));
console.log('wrote animations', OUT_ANIM, Object.keys(animations).length, 'clips');

// ---------------------------------------------------------------- clip length <-> Java timing check
// v0.14.0 hard rule: every timing constant in Java must exactly match its clip's length. This prints
// every clip's length in ticks and cross-checks the ones Java times against OathbreakerTuning.java
// (read straight from source, so the two can't silently drift). Loops and purely cosmetic one-shots
// (flinch) have no Java timer and are listed as such. Exits non-zero on any mismatch.
const TUNING_JAVA = path.join(ROOT, 'src/main/java/com/projecthero/mod/oathbreaker/OathbreakerTuning.java');
const tuning = {};
for (const m of fs.readFileSync(TUNING_JAVA, 'utf8').matchAll(/public static final int (\w+) = (\d+);/g)) {
	tuning[m[1]] = parseInt(m[2], 10);
}
// clip-name regex -> OathbreakerTuning constant it must equal (in ticks)
const CLIP_TIMERS = [
	[/^spawn$/, 'SPAWN_TICKS'],
	[/^hit$/, 'FLINCH_TICKS'],
	[/^stagger$/, 'STAGGER_TICKS'],
	[/^windup_dash$/, 'STANCE_WINDUP_TICKS'],
	[/^dash_attack$/, 'STANCE_DASH_TICKS'],
	[/^post_dash$/, 'STANCE_POST_TICKS'],
	[/^combo_windup_\d$/, 'COMBO_WINDUP_TICKS'],
	[/^combo_strike_\d$/, 'COMBO_STRIKE_HOLD_TICKS'],
	[/^death$/, 'DEATH_TICKS'],
	[/^guard_stance$/, 'GUARD_STANCE_TICKS'],
	[/^riposte$/, 'RIPOSTE_TICKS'],
	[/^backstep$/, 'BACKSTEP_TICKS'],
	[/^leap_windup$/, 'LEAP_WINDUP_TICKS'],
	[/^leap_air$/, 'LEAP_AIR_TICKS'],
	[/^leap_land$/, 'LEAP_LAND_TICKS'],
	[/^phase_transition$/, 'TRANSITION_TICKS'],
	[/^dash_feint$/, 'STANCE_FEINT_TICKS'],
	[/^soul_rend_windup$/, 'SOUL_REND_WINDUP_TICKS'],
	[/^soul_rend_strike$/, 'SOUL_REND_STRIKE_TICKS'],
	[/^chain_throw$/, 'CHAIN_THROW_TICKS'],
	[/^chain_pull$/, 'CHAIN_PULL_TICKS'],
	[/^chain_recover$/, 'CHAIN_RECOVER_TICKS'],
	[/^enrage$/, 'ENRAGE_TICKS'],
	[/^judgement_rise$/, 'JUDGEMENT_RISE_TICKS'],
	[/^judgement_hang$/, 'JUDGEMENT_HANG_TICKS'],
	[/^judgement_slam$/, 'JUDGEMENT_SLAM_TICKS'],
	[/^execution_windup$/, 'EXECUTION_WINDUP_TICKS'],
	[/^execution_lunge$/, 'EXECUTION_LUNGE_TICKS'],
	[/^execution_hold$/, 'EXECUTION_HOLD_TICKS'],
	[/^execution_impale$/, 'EXECUTION_IMPALE_TICKS'],
	[/^execution_whiff$/, 'EXECUTION_WHIFF_TICKS'],
	[/^whirlwind_windup$/, 'WHIRLWIND_WINDUP_TICKS'],
	[/^whirlwind_strike$/, 'WHIRLWIND_STRIKE_TICKS'],
	[/^geyser_windup$/, 'GEYSER_WINDUP_TICKS'],
	[/^geyser_plunge$/, 'GEYSER_PLUNGE_TICKS'],
	[/^geyser_bowed$/, 'GEYSER_BOWED_TICKS'],
];
// Strike clips whose damage Java resolves a fixed number of ticks in: the clip must have a sword-arm
// keyframe exactly there, or the hit lands off the visible contact.
const CONTACT_FRAMES = [
	[/^dash_attack$/, 'STANCE_DASH_CONTACT_TICKS'],
	[/^combo_strike_\d$/, 'COMBO_STRIKE_CONTACT_TICKS'],
	[/^riposte$/, 'RIPOSTE_CONTACT_TICKS'],
	[/^phase_transition$/, 'TRANSITION_PLUNGE_TICKS'],
	[/^soul_rend_strike$/, 'SOUL_REND_CONTACT_TICKS'],
	[/^enrage$/, 'ENRAGE_ROAR_TICKS'],
	[/^judgement_slam$/, 'JUDGEMENT_FALL_TICKS'],
	[/^execution_impale$/, 'EXECUTION_IMPALE_CONTACT_TICKS'],
	// v0.13.19: a clip may have several (every matching entry is checked)
	[/^whirlwind_strike$/, 'WHIRLWIND_HIT1_TICKS'],
	[/^whirlwind_strike$/, 'WHIRLWIND_HIT2_TICKS'],
	[/^whirlwind_strike$/, 'WHIRLWIND_SPIN_TICKS'],
	[/^geyser_plunge$/, 'GEYSER_PLUNGE_CONTACT_TICKS'],
];
let mismatches = 0;
console.log('\nclip                          sec    ticks  java');
for (const [full, clip] of Object.entries(animations)) {
	const name = full.replace('animation.oathbreaker.', '');
	const ticks = clip.animation_length * 20;
	const rule = CLIP_TIMERS.find(([re]) => re.test(name));
	let java;
	if (clip.loop) {
		java = '(loop)';
	} else if (!rule) {
		java = '(no Java timer)';
	} else if (!(rule[1] in tuning)) {
		java = `${rule[1]} MISSING from OathbreakerTuning  <-- MISMATCH`;
		mismatches++;
	} else if (Math.abs(tuning[rule[1]] - ticks) > 1e-6) {
		java = `${rule[1]}=${tuning[rule[1]]}  <-- MISMATCH`;
		mismatches++;
	} else {
		java = `${rule[1]}=${tuning[rule[1]]} ok`;
	}
	for (const contact of CONTACT_FRAMES.filter(([re]) => re.test(name))) {
		const t = tuning[contact[1]];
		const key = t === undefined ? '?' : (t / 20).toFixed(2);
		const armKeys = Object.keys((clip.bones.right_arm || {}).rotation || {});
		if (t === undefined || !armKeys.includes(key)) {
			java += `  | contact ${contact[1]}=${t} -> no right_arm keyframe at ${key}s  <-- MISMATCH`;
			mismatches++;
		} else {
			java += `  | contact @${key}s ok`;
		}
	}
	console.log(`${name.padEnd(29)} ${clip.animation_length.toFixed(2).padStart(5)} ${String(Math.round(ticks * 100) / 100).padStart(7)}  ${java}`);
}
// v0.13.19: the Whirlwind's single strike clip is the spins + the dizzy recovery back to back (Java plays it
// across two steps), and its root must sit at 719 degrees from the end of the spins to the end of the clip (the
// GeckoLib reset-snap trick -- see the clip's comment). No other clip may key root rotation at all.
{
	const spinT = tuning.WHIRLWIND_SPIN_TICKS, recT = tuning.WHIRLWIND_RECOVER_TICKS, strikeT = tuning.WHIRLWIND_STRIKE_TICKS;
	if (spinT + recT !== strikeT) {
		console.error(`WHIRLWIND_SPIN_TICKS (${spinT}) + WHIRLWIND_RECOVER_TICKS (${recT}) != WHIRLWIND_STRIKE_TICKS (${strikeT})  <-- MISMATCH`);
		mismatches++;
	}
	const rot = animations['animation.oathbreaker.whirlwind_strike'].bones.root.rotation;
	const keys = Object.keys(rot).map(Number);
	const held = keys.filter(k => k >= spinT / 20 - 1e-6).every(k => rot[k.toFixed(2)][1] === 719);
	if (!held || !((spinT / 20).toFixed(2) in rot)) {
		console.error('whirlwind_strike root rotation must be 719 from the end of the spins to the end of the clip  <-- MISMATCH');
		mismatches++;
	}
	for (const [full, clip] of Object.entries(animations)) {
		if (full !== 'animation.oathbreaker.whirlwind_strike' && clip.bones.root && clip.bones.root.rotation) {
			console.error(`${full} keys root rotation -- only whirlwind_strike may  <-- MISMATCH`);
			mismatches++;
		}
	}
	console.log(`\nwhirlwind: spin ${spinT} + recover ${recT} = strike ${strikeT}; root held at 719 after the spins: ${held}`);
}
if (mismatches) {
	console.error(`\n${mismatches} clip/timer MISMATCH(ES) -- fix the clip or OathbreakerTuning before building.`);
	process.exitCode = 1;
} else {
	console.log('\nall timed clips match OathbreakerTuning.');
}
