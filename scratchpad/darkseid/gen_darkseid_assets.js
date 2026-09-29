// v0.13.18 Darkseid Raid assets. Run from the repo root:  node scratchpad/darkseid/gen_darkseid_assets.js
//
// Writes:
//   geo/darkseid.geo.json, animations/darkseid.animation.json, textures/entity/darkseid(_glowmask).png
//   geo/parademon.geo.json, animations/parademon.animation.json, textures/entity/parademon[_ranged|_elite|_brute](_glowmask).png
//   textures/entity/boom_tube.png
//   textures/item/{boom_tube_beacon,omega_core,omega_shard,omega_relic,mother_box}.png + models/item/*.json
//
// Every Darkseid clip length and keyed impact frame is checked against src/.../darkseid/entity/DarkseidAnims.java
// -- the server lands its blows on those ticks, so a drift here fails the script instead of shipping a punch that
// hits before the fist arrives.
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const REPO = path.join(__dirname, '..', '..');
const ASSETS = path.join(REPO, 'src/main/resources/assets/projecthero/');
const ANIMS_JAVA = path.join(REPO, 'src/main/java/com/projecthero/mod/darkseid/entity/DarkseidAnims.java');
for (const d of ['geo', 'animations', 'textures/entity', 'textures/item', 'models/item']) fs.mkdirSync(ASSETS + d, { recursive: true });

// ------------------------------------------------------------------ tiny image lib
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function set(im, x, y, c, a = 255) {
	x = Math.floor(x); y = Math.floor(y);
	if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const i = (y * im.w + x) * 4;
	im.px[i] = clamp(c[0]); im.px[i + 1] = clamp(c[1]); im.px[i + 2] = clamp(c[2]); im.px[i + 3] = a;
}
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255];
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
const shade = (c, k) => [c[0] * k, c[1] * k, c[2] * k];
let seed = 1337;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >> 13)) * 1274126177; return ((h ^ (h >> 16)) >>> 0) / 4294967295; }
function save(im, rel) { fs.writeFileSync(ASSETS + rel, encode(im.w, im.h, im.px)); }

// ------------------------------------------------------------------ geo builder (box UV, shelf-packed)
function buildGeo(identifier, texW, texH, bones) {
	// pack every cube's box-UV footprint (2d+2w) x (d+h)
	const cubes = [];
	for (const b of bones) for (const c of (b.cubes || [])) cubes.push(c);
	const order = [...cubes].sort((a, b) => (b.size[2] + b.size[1]) - (a.size[2] + a.size[1]));
	let x = 0, y = 0, rowH = 0;
	for (const c of order) {
		const fw = Math.ceil(2 * c.size[2] + 2 * c.size[0]), fh = Math.ceil(c.size[2] + c.size[1]);
		if (x + fw > texW) { x = 0; y += rowH; rowH = 0; }
		if (y + fh > texH) throw new Error(identifier + ': texture ' + texW + 'x' + texH + ' too small');
		c.uv = [x, y];
		x += fw; rowH = Math.max(rowH, fh);
	}
	const out = bones.map(b => {
		const o = { name: b.name, pivot: b.pivot };
		if (b.parent) o.parent = b.parent;
		if (b.rotation) o.rotation = b.rotation;
		if (b.cubes) o.cubes = b.cubes.map(c => { const cc = { origin: c.origin, size: c.size, uv: c.uv }; if (c.inflate) cc.inflate = c.inflate; return cc; });
		return o;
	});
	return {
		json: {
			format_version: '1.12.0',
			'minecraft:geometry': [{ description: { identifier, texture_width: texW, texture_height: texH, visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1.5, 0] }, bones: out }],
		}, cubes,
	};
}
// face rects of a box-UV cube
function faces(c) {
	const [u, v] = c.uv, [w, h, d] = c.size.map(Math.ceil);
	return {
		up: [u + d, v, w, d], down: [u + d + w, v, w, d],
		east: [u, v + d, d, h], north: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h],
	};
}
function paintRect(im, r, fn) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = fn(x, y, w, h, x0 + x, y0 + y); if (c) set(im, x0 + x, y0 + y, c, c[3] === undefined ? 255 : c[3]); } }
function paintCube(im, c, mat, front) {
	const f = faces(c);
	for (const [name, r] of Object.entries(f)) {
		paintRect(im, r, (x, y, w, h, ax, ay) => mat(x, y, w, h, ax, ay, name));
		if (name === 'north' && front) paintRect(im, r, (x, y, w, h, ax, ay) => front(x, y, w, h, ax, ay));
	}
}

// ------------------------------------------------------------------ animation builder
function animSet(prefix, rest) {
	const A = {};
	return {
		A,
		/** frames: { bone: { rotation: [[t,[x,y,z]],...], position: [...] } }; every bone in `rest` is keyed at 0 and the end */
		anim(name, length, loop, frames) {
			const bones = {};
			const all = new Set([...Object.keys(rest), ...Object.keys(frames)]);
			for (const bn of all) {
				const f = frames[bn] || {};
				const r = rest[bn] || { rotation: [0, 0, 0], position: [0, 0, 0] };
				const ob = {};
				for (const ch of ['rotation', 'position']) {
					let keys = f[ch] ? [...f[ch]] : [];
					const restV = (r[ch] || [0, 0, 0]);
					if (!keys.length) keys = [[0, restV], [length, restV]];
					if (keys[0][0] > 0) keys.unshift([0, keys[0][1]]);
					if (keys[keys.length - 1][0] < length) keys.push([length, keys[keys.length - 1][1]]);
					const o = {};
					for (const [t, v] of keys) o[(+t).toFixed(3)] = v.map(n => +(+n).toFixed(2));
					ob[ch] = o;
				}
				bones[bn] = ob;
			}
			const a = { animation_length: length, bones };
			if (loop === true) a.loop = true; else if (loop === 'hold') a.loop = 'hold_on_last_frame';
			A['animation.' + prefix + '.' + name] = a;
			return a;
		},
	};
}

// ==================================================================== DARKSEID
// The user's model: C:/Users/ethan/OneDrive/Desktop/3d minecraft models/darkseid/darkseid.bbmodel -- a player-skin rig
// (base cube + second-layer cube per part) with a 128x128 HD skin in the standard 64x64 layout. Its cubes and UVs are
// used exactly; each arm and leg is split at the elbow/knee along the skin's own UV rows (a 12-tall limb becomes two
// 6-tall cubes whose box-UV side strips are the upper and lower halves of the original) so the animations can bend
// them. 32 px tall = DarkseidEntity.MODEL_HEIGHT 2.0; the renderer stretches it to the 4.2-block hit-box.
// Front = -Z, the entity's right = -X (Blockbench's geo export mirrors X).
const BB_DIR = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/darkseid/';
const skinOf = f => Buffer.from(JSON.parse(fs.readFileSync(BB_DIR + f, 'utf8')).textures[0].source.split(',')[1], 'base64');
const cube = (origin, size, uv, extra) => Object.assign({ origin, size, uv }, extra || {});
/** Two halves (upper, lower) of a 4x12x4 skin limb standing on y0, base + second layer each. */
function split(x, y0, uvBase, uvLayer) {
	const half = (y, dv) => [cube([x, y, -2], [4, 6, 4], [uvBase[0], uvBase[1] + dv]),
		cube([x, y, -2], [4, 6, 4], [uvLayer[0], uvLayer[1] + dv], { inflate: 0.25 })];
	return [half(y0 + 6, 0), half(y0, 6)];
}
/** A standard skin rig split at elbows/knees, bones named for the animation tables below. */
function skinRig(extraBones) {
	const [ruA, rlA] = split(-8, 12, [40, 16], [40, 32]);
	const [luA, llA] = split(4, 12, [32, 48], [48, 48]);
	const [ruL, rlL] = split(-3.9, 0, [0, 16], [0, 32]);
	const [luL, llL] = split(-0.1, 0, [16, 48], [0, 48]);
	return [
		{ name: 'root', pivot: [0, 0, 0] },
		{ name: 'body', parent: 'root', pivot: [0, 12, 0] },
		{ name: 'torso', parent: 'body', pivot: [0, 12, 0], cubes: [cube([-4, 12, -2], [8, 12, 4], [16, 16]), cube([-4, 12, -2], [8, 12, 4], [16, 32], { inflate: 0.25 })] },
		{ name: 'head', parent: 'torso', pivot: [0, 24, 0], cubes: [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], { inflate: 0.5 })] },
		{ name: 'right_arm', parent: 'torso', pivot: [-5, 22, 0], cubes: ruA },
		{ name: 'right_forearm', parent: 'right_arm', pivot: [-6, 18, 0], cubes: rlA },
		{ name: 'right_hand', parent: 'right_forearm', pivot: [-6, 12, 0] },
		{ name: 'left_arm', parent: 'torso', pivot: [5, 22, 0], cubes: luA },
		{ name: 'left_forearm', parent: 'left_arm', pivot: [6, 18, 0], cubes: llA },
		{ name: 'left_hand', parent: 'left_forearm', pivot: [6, 12, 0] },
		{ name: 'right_leg', parent: 'root', pivot: [-1.9, 12, 0], cubes: ruL },
		{ name: 'right_shin', parent: 'right_leg', pivot: [-1.9, 6, 0], cubes: rlL },
		{ name: 'left_leg', parent: 'root', pivot: [1.9, 12, 0], cubes: luL },
		{ name: 'left_shin', parent: 'left_leg', pivot: [1.9, 6, 0], cubes: llL },
		...extraBones,
	];
}
/** Geo writer for pre-assigned UVs. The UV space is the skin's 64x64 layout whatever the image resolution. */
function writeGeo(file, identifier, bones) {
	const out = bones.map(b => {
		const o = { name: b.name, pivot: b.pivot };
		if (b.parent) o.parent = b.parent;
		if (b.cubes) o.cubes = b.cubes.map(c => { const cc = { origin: c.origin, size: c.size, uv: c.uv }; if (c.inflate) cc.inflate = c.inflate; return cc; });
		return o;
	});
	fs.writeFileSync(ASSETS + 'geo/' + file, JSON.stringify({ format_version: '1.12.0', 'minecraft:geometry': [{
		description: { identifier, texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0] },
		bones: out }] }, null, 1));
}
writeGeo('darkseid.geo.json', 'geometry.darkseid', skinRig([
	// no geometry of their own: kept so the animation tables' tunic-flap keys resolve
	{ name: 'skirt_front', parent: 'body', pivot: [0, 12, -2] },
	{ name: 'skirt_back', parent: 'body', pivot: [0, 12, 2] },
]));
const { decode } = require('../pngkit');
/** Copy the skin byte for byte and build a glowmask of its eye pixels (face + hat-face regions only). */
function skinAndGlow(bbFile, outName, isEye) {
	const bytes = skinOf(bbFile);
	fs.writeFileSync(ASSETS + 'textures/entity/' + outName + '.png', bytes);
	const tmp = path.join(__dirname, 'src', outName + '_tmp.png');
	fs.mkdirSync(path.dirname(tmp), { recursive: true });
	fs.writeFileSync(tmp, bytes);
	const im = decode(tmp);
	const k = im.w / 64;
	const glow = img(im.w, im.h);
	let n = 0;
	for (const [fx, fy] of [[8, 8], [40, 8]]) {
		for (let y = fy * k; y < (fy + 8) * k; y++) for (let x = fx * k; x < (fx + 8) * k; x++) {
			const c = get(im, x, y);
			if (c[3] > 0 && isEye(c)) { set(glow, x, y, c); n++; }
		}
	}
	save(glow, 'textures/entity/' + outName + '_glowmask.png');
	return { im, glowPixels: n };
}
const dsSkin = skinAndGlow('darkseid.bbmodel', 'darkseid', c => c[0] > 180 && c[1] > 60 && c[1] < 200 && c[2] < 90);
console.log('darkseid: your skin ' + dsSkin.im.w + 'x' + dsSkin.im.h + ', ' + dsSkin.glowPixels + ' glowing eye pixels');

// ---- Darkseid animations (degrees; X negative = limb forward/up, positive X = head/body pitches down)
const DREST = {
	root: { rotation: [0, 0, 0], position: [0, 0, 0] },
	body: { rotation: [0, 0, 0], position: [0, 0, 0] },
	torso: { rotation: [0, 0, 0], position: [0, 0, 0] },
	head: { rotation: [0, 0, 0], position: [0, 0, 0] },
	right_arm: { rotation: [0, 0, 6], position: [0, 0, 0] },
	left_arm: { rotation: [0, 0, -6], position: [0, 0, 0] },
	right_forearm: { rotation: [-8, 0, 0], position: [0, 0, 0] },
	left_forearm: { rotation: [-8, 0, 0], position: [0, 0, 0] },
	right_hand: { rotation: [0, 0, 0], position: [0, 0, 0] },
	left_hand: { rotation: [0, 0, 0], position: [0, 0, 0] },
	skirt_front: { rotation: [0, 0, 0], position: [0, 0, 0] },
	skirt_back: { rotation: [0, 0, 0], position: [0, 0, 0] },
	right_leg: { rotation: [0, 0, 0], position: [0, 0, 0] },
	left_leg: { rotation: [0, 0, 0], position: [0, 0, 0] },
	right_shin: { rotation: [0, 0, 0], position: [0, 0, 0] },
	left_shin: { rotation: [0, 0, 0], position: [0, 0, 0] },
};
const D = animSet('darkseid', DREST);
const R = f => ({ rotation: f });
const P = f => ({ position: f });
const RP = (r, p) => ({ rotation: r, position: p });
const RA = DREST.right_arm.rotation, LA = DREST.left_arm.rotation, FA = DREST.right_forearm.rotation;
const Z3 = [0, 0, 0];

D.anim('idle', 3.0, true, {
	root: P([[0, Z3], [1.5, [0, -0.25, 0]], [3, Z3]]),
	torso: R([[0, [0, 0, 0]], [1.5, [-2, 0, 0]], [3, [0, 0, 0]]]),
	head: R([[0, [2, 0, 0]], [1.5, [3, 2, 0]], [3, [2, 0, 0]]]),
	right_arm: R([[0, RA], [1.5, [2, 0, 8]], [3, RA]]),
	left_arm: R([[0, LA], [1.5, [2, 0, -8]], [3, LA]]),
});
function gait(name, len, leg, knee, arm, lean, bob) {
	const h = len / 2, q = len / 4;
	D.anim(name, len, true, {
		root: P([[0, [0, -bob, 0]], [q, [0, bob * 0.3, 0]], [h, [0, -bob, 0]], [h + q, [0, bob * 0.3, 0]], [len, [0, -bob, 0]]]),
		body: R([[0, [lean, 0, 2]], [h, [lean, 0, -2]], [len, [lean, 0, 2]]]),
		head: R([[0, [-lean * 0.6, 0, 0]], [len, [-lean * 0.6, 0, 0]]]),
		right_leg: R([[0, [-leg, 0, 0]], [h, [leg, 0, 0]], [len, [-leg, 0, 0]]]),
		left_leg: R([[0, [leg, 0, 0]], [h, [-leg, 0, 0]], [len, [leg, 0, 0]]]),
		right_shin: R([[0, [0, 0, 0]], [q, [knee, 0, 0]], [h, [knee * 0.6, 0, 0]], [h + q, [0, 0, 0]], [len, [0, 0, 0]]]),
		left_shin: R([[0, [knee * 0.6, 0, 0]], [q, [0, 0, 0]], [h, [0, 0, 0]], [h + q, [knee, 0, 0]], [len, [knee * 0.6, 0, 0]]]),
		right_arm: R([[0, [arm, 0, 7]], [h, [-arm, 0, 7]], [len, [arm, 0, 7]]]),
		left_arm: R([[0, [-arm, 0, -7]], [h, [arm, 0, -7]], [len, [-arm, 0, -7]]]),
		right_forearm: R([[0, [-12, 0, 0]], [h, [-28, 0, 0]], [len, [-12, 0, 0]]]),
		left_forearm: R([[0, [-28, 0, 0]], [h, [-12, 0, 0]], [len, [-28, 0, 0]]]),
		skirt_front: R([[0, [-6, 0, 0]], [q, [-2, 0, 0]], [h, [-6, 0, 0]], [len, [-6, 0, 0]]]),
	});
}
gait('walk', 1.4, 22, 28, 16, 3, 0.35);
gait('run', 0.8, 38, 55, 34, 12, 0.7);

const ENTRANCE = D.anim('entrance', 4.0, false, {
	root: P([[0, [0, -5, 0]], [1.2, [0, -4, -1]], [2.4, [0, -1.5, 0]], [3.2, [0, 0, 0]], [4.0, [0, 0, 0]]]),
	body: R([[0, [28, 0, 0]], [1.2, [24, 0, 0]], [2.4, [10, 0, 0]], [3.2, [0, 0, 0]], [4.0, [0, 0, 0]]]),
	head: R([[0, [25, 0, 0]], [2.4, [18, 0, 0]], [3.0, [0, 0, 0]], [3.3, [-12, 0, 0]], [4.0, [0, 0, 0]]]),
	right_leg: R([[0, [-40, 0, 0]], [1.2, [-36, 0, 0]], [2.4, [-15, 0, 0]], [3.2, Z3]]),
	left_leg: R([[0, [-40, 0, 0]], [1.2, [-30, 0, 0]], [2.4, [-10, 0, 0]], [3.2, Z3]]),
	right_shin: R([[0, [60, 0, 0]], [1.2, [55, 0, 0]], [2.4, [22, 0, 0]], [3.2, Z3]]),
	left_shin: R([[0, [60, 0, 0]], [1.2, [50, 0, 0]], [2.4, [18, 0, 0]], [3.2, Z3]]),
	right_arm: R([[0, [-20, 0, 12]], [2.4, [-8, 0, 10]], [3.3, [0, 0, 22]], [4.0, RA]]),
	left_arm: R([[0, [-20, 0, -12]], [2.4, [-8, 0, -10]], [3.3, [0, 0, -22]], [4.0, LA]]),
});

// melee 1: right hook (impact 0.55 s = 11 t)
D.anim('melee_attack_1', 1.2, false, {
	body: R([[0, Z3], [0.4, [0, -22, 0]], [0.55, [4, 24, 0]], [0.75, [2, 18, 0]], [1.2, Z3]]),
	right_arm: R([[0, RA], [0.4, [35, 0, 28]], [0.55, [-88, 10, 6]], [0.75, [-80, 8, 6]], [1.2, RA]]),
	right_forearm: R([[0, FA], [0.4, [-70, 0, 0]], [0.55, [-6, 0, 0]], [0.75, [-10, 0, 0]], [1.2, FA]]),
	left_arm: R([[0, LA], [0.4, [-20, 0, -12]], [0.55, [20, 0, -10]], [1.2, LA]]),
	right_leg: R([[0, Z3], [0.55, [12, 0, 0]], [1.2, Z3]]),
	left_leg: R([[0, Z3], [0.55, [-16, 0, 0]], [1.2, Z3]]),
});
// melee 2: left backhand (impact 0.6 s = 12 t)
D.anim('melee_attack_2', 1.3, false, {
	body: R([[0, Z3], [0.45, [0, 28, 0]], [0.6, [2, -30, 0]], [0.85, [0, -24, 0]], [1.3, Z3]]),
	left_arm: R([[0, LA], [0.45, [-95, 0, 45]], [0.6, [-80, 0, -75]], [0.85, [-70, 0, -70]], [1.3, LA]]),
	left_forearm: R([[0, FA], [0.45, [-50, 0, 0]], [0.6, [-5, 0, 0]], [1.3, FA]]),
	right_arm: R([[0, RA], [0.6, [15, 0, 14]], [1.3, RA]]),
	head: R([[0, Z3], [0.6, [0, -15, 0]], [1.3, Z3]]),
});
// melee combo: jab (0.5 = 10 t), jab (1.0 = 20 t), two-fisted hammer (1.7 = 34 t)
D.anim('melee_combo', 2.2, false, {
	root: P([[0, Z3], [1.4, [0, 0.6, 0]], [1.7, [0, -2.5, -1]], [1.95, [0, -2.2, -1]], [2.2, Z3]]),
	body: R([[0, Z3], [0.35, [0, -18, 0]], [0.5, [6, 20, 0]], [0.8, [0, 16, 0]], [1.0, [6, -20, 0]], [1.4, [-12, 0, 0]], [1.7, [38, 0, 0]], [1.95, [34, 0, 0]], [2.2, Z3]]),
	right_arm: R([[0, RA], [0.35, [25, 0, 20]], [0.5, [-90, 0, 4]], [0.8, [-20, 0, 10]], [1.0, [10, 0, 12]], [1.4, [-172, 0, 10]], [1.7, [-45, 0, 6]], [1.95, [-40, 0, 6]], [2.2, RA]]),
	left_arm: R([[0, LA], [0.5, [15, 0, -12]], [0.8, [25, 0, -20]], [1.0, [-90, 0, -4]], [1.2, [-40, 0, -10]], [1.4, [-172, 0, -10]], [1.7, [-45, 0, -6]], [1.95, [-40, 0, -6]], [2.2, LA]]),
	right_forearm: R([[0, FA], [0.35, [-60, 0, 0]], [0.5, [-4, 0, 0]], [1.4, [-20, 0, 0]], [1.7, [-4, 0, 0]], [2.2, FA]]),
	left_forearm: R([[0, FA], [0.8, [-60, 0, 0]], [1.0, [-4, 0, 0]], [1.4, [-20, 0, 0]], [1.7, [-4, 0, 0]], [2.2, FA]]),
	right_leg: R([[0, Z3], [1.7, [-28, 0, 0]], [2.2, Z3]]),
	left_leg: R([[0, Z3], [1.7, [14, 0, 0]], [2.2, Z3]]),
	right_shin: R([[0, Z3], [1.7, [30, 0, 0]], [2.2, Z3]]),
});
// ground slam (impact 1.3 = 26 t)
D.anim('ground_slam', 2.5, false, {
	root: P([[0, Z3], [0.6, [0, -1.5, 0]], [0.95, [0, 3.5, 0]], [1.1, [0, 3.0, 0]], [1.3, [0, -4, -1]], [1.8, [0, -3.6, -1]], [2.5, Z3]]),
	body: R([[0, Z3], [0.6, [8, 0, 0]], [0.95, [-18, 0, 0]], [1.3, [48, 0, 0]], [1.8, [44, 0, 0]], [2.5, Z3]]),
	head: R([[0, Z3], [0.95, [-20, 0, 0]], [1.3, [-35, 0, 0]], [2.5, Z3]]),
	right_arm: R([[0, RA], [0.6, [-60, 0, 20]], [0.95, [-175, 0, 12]], [1.3, [-40, 0, 8]], [1.8, [-38, 0, 8]], [2.5, RA]]),
	left_arm: R([[0, LA], [0.6, [-60, 0, -20]], [0.95, [-175, 0, -12]], [1.3, [-40, 0, -8]], [1.8, [-38, 0, -8]], [2.5, LA]]),
	right_forearm: R([[0, FA], [0.95, [-15, 0, 0]], [1.3, [-2, 0, 0]], [2.5, FA]]),
	left_forearm: R([[0, FA], [0.95, [-15, 0, 0]], [1.3, [-2, 0, 0]], [2.5, FA]]),
	right_leg: R([[0, Z3], [0.6, [-30, 0, 0]], [0.95, [10, 0, 0]], [1.3, [-55, 0, 6]], [1.8, [-55, 0, 6]], [2.5, Z3]]),
	left_leg: R([[0, Z3], [0.6, [-30, 0, 0]], [0.95, [10, 0, 0]], [1.3, [-20, 0, -6]], [1.8, [-20, 0, -6]], [2.5, Z3]]),
	right_shin: R([[0, Z3], [0.6, [50, 0, 0]], [0.95, [5, 0, 0]], [1.3, [70, 0, 0]], [1.8, [70, 0, 0]], [2.5, Z3]]),
	left_shin: R([[0, Z3], [0.6, [50, 0, 0]], [0.95, [5, 0, 0]], [1.3, [55, 0, 0]], [1.8, [55, 0, 0]], [2.5, Z3]]),
	skirt_front: R([[0, Z3], [0.95, [10, 0, 0]], [1.3, [-35, 0, 0]], [2.5, Z3]]),
});
// Omega Beams: the glare (held) and the release
D.anim('omega_beam_charge', 1.6, 'hold', {
	root: P([[0, Z3], [1.6, [0, -0.5, 0]]]),
	body: R([[0, Z3], [0.8, [-6, 0, 0]], [1.6, [-8, 0, 0]]]),
	head: R([[0, Z3], [0.6, [12, 0, 0]], [1.6, [16, 0, 0]]]),
	right_arm: R([[0, RA], [0.8, [8, 0, 28]], [1.6, [10, 0, 30]]]),
	left_arm: R([[0, LA], [0.8, [8, 0, -28]], [1.6, [10, 0, -30]]]),
	right_forearm: R([[0, FA], [1.6, [-35, 0, 0]]]),
	left_forearm: R([[0, FA], [1.6, [-35, 0, 0]]]),
});
D.anim('omega_beam_fire', 1.2, false, {
	root: P([[0, [0, -0.5, 0]], [0.1, [0, -0.5, 0.8]], [0.5, [0, -0.3, 0.4]], [1.2, Z3]]),
	body: R([[0, [-8, 0, 0]], [0.1, [-14, 0, 0]], [0.5, [-6, 0, 0]], [1.2, Z3]]),
	head: R([[0, [16, 0, 0]], [0.1, [4, 0, 0]], [0.6, [10, 0, 0]], [1.2, Z3]]),
	right_arm: R([[0, [10, 0, 30]], [0.1, [25, 0, 36]], [1.2, RA]]),
	left_arm: R([[0, [10, 0, -30]], [0.1, [25, 0, -36]], [1.2, LA]]),
	right_forearm: R([[0, [-35, 0, 0]], [1.2, FA]]),
	left_forearm: R([[0, [-35, 0, 0]], [1.2, FA]]),
});
// Omega Barrage (release 1.0 = 20 t)
D.anim('omega_barrage', 1.8, false, {
	body: R([[0, Z3], [0.8, [-14, 0, 0]], [1.0, [12, 0, 0]], [1.3, [8, 0, 0]], [1.8, Z3]]),
	head: R([[0, Z3], [0.8, [-25, 0, 0]], [1.0, [5, 0, 0]], [1.8, Z3]]),
	right_arm: R([[0, RA], [0.8, [-40, 0, 80]], [1.0, [-92, 0, 12]], [1.3, [-88, 0, 12]], [1.8, RA]]),
	left_arm: R([[0, LA], [0.8, [-40, 0, -80]], [1.0, [-92, 0, -12]], [1.3, [-88, 0, -12]], [1.8, LA]]),
	right_hand: R([[0, Z3], [1.0, [-30, 0, 0]], [1.8, Z3]]),
	left_hand: R([[0, Z3], [1.0, [-30, 0, 0]], [1.8, Z3]]),
});
// Grip: reach and hold; then the throw (release 0.4 = 8 t)
D.anim('grip', 1.0, 'hold', {
	body: R([[0, Z3], [1.0, [-6, -10, 0]]]),
	head: R([[0, Z3], [1.0, [-15, 0, 0]]]),
	right_arm: R([[0, RA], [0.5, [-110, 0, 14]], [1.0, [-130, 0, 10]]]),
	right_forearm: R([[0, FA], [0.5, [-30, 0, 0]], [1.0, [-8, 0, 0]]]),
	right_hand: R([[0, Z3], [1.0, [-40, 0, 0]]]),
	left_arm: R([[0, LA], [1.0, [18, 0, -22]]]),
	right_leg: R([[0, Z3], [1.0, [-10, 0, 0]]]),
	left_leg: R([[0, Z3], [1.0, [12, 0, 0]]]),
});
D.anim('grip_throw', 0.8, false, {
	body: R([[0, [-6, -10, 0]], [0.25, [-8, -28, 0]], [0.4, [10, 32, 0]], [0.8, Z3]]),
	right_arm: R([[0, [-130, 0, 10]], [0.25, [-150, -20, 20]], [0.4, [-50, 20, -10]], [0.55, [-30, 20, -10]], [0.8, RA]]),
	right_forearm: R([[0, [-8, 0, 0]], [0.8, FA]]),
	right_hand: R([[0, [-40, 0, 0]], [0.4, Z3]]),
	head: R([[0, [-15, 0, 0]], [0.8, Z3]]),
});
// Apokoliptian Charge: the crouch (held), then the rush (loop)
D.anim('charge', 1.2, 'hold', {
	root: P([[0, Z3], [1.2, [0, -3, 0]]]),
	body: R([[0, Z3], [1.2, [34, 0, 0]]]),
	head: R([[0, Z3], [1.2, [-26, 0, 0]]]),
	right_arm: R([[0, RA], [1.2, [45, 0, 16]]]),
	left_arm: R([[0, LA], [1.2, [45, 0, -16]]]),
	right_leg: R([[0, Z3], [1.2, [-42, 0, 0]]]),
	left_leg: R([[0, Z3], [1.2, [22, 0, 0]]]),
	right_shin: R([[0, Z3], [1.2, [55, 0, 0]]]),
	left_shin: R([[0, Z3], [1.2, [30, 0, 0]]]),
});
D.anim('charge_rush', 0.5, true, {
	root: P([[0, [0, -1.5, 0]], [0.125, [0, -0.5, 0]], [0.25, [0, -1.5, 0]], [0.375, [0, -0.5, 0]], [0.5, [0, -1.5, 0]]]),
	body: R([[0, [40, 0, 0]], [0.5, [40, 0, 0]]]),
	head: R([[0, [-32, 0, 0]], [0.5, [-32, 0, 0]]]),
	right_arm: R([[0, [30, 0, 22]], [0.25, [10, 0, 22]], [0.5, [30, 0, 22]]]),
	left_arm: R([[0, [10, 0, -22]], [0.25, [30, 0, -22]], [0.5, [10, 0, -22]]]),
	right_forearm: R([[0, [-60, 0, 0]], [0.5, [-60, 0, 0]]]),
	left_forearm: R([[0, [-60, 0, 0]], [0.5, [-60, 0, 0]]]),
	right_leg: R([[0, [-55, 0, 0]], [0.25, [45, 0, 0]], [0.5, [-55, 0, 0]]]),
	left_leg: R([[0, [45, 0, 0]], [0.25, [-55, 0, 0]], [0.5, [45, 0, 0]]]),
	right_shin: R([[0, [20, 0, 0]], [0.125, [70, 0, 0]], [0.25, [10, 0, 0]], [0.5, [20, 0, 0]]]),
	left_shin: R([[0, [10, 0, 0]], [0.25, [20, 0, 0]], [0.375, [70, 0, 0]], [0.5, [10, 0, 0]]]),
});
// Omega Teleport
D.anim('teleport', 0.6, false, {
	root: P([[0, Z3], [0.6, [0, -3, 0]]]),
	body: R([[0, Z3], [0.6, [20, 0, 0]]]),
	right_arm: R([[0, RA], [0.6, [-75, 0, -30]]]),
	left_arm: R([[0, LA], [0.6, [-75, 0, 30]]]),
	right_leg: R([[0, Z3], [0.6, [-30, 0, 0]]]),
	left_leg: R([[0, Z3], [0.6, [-30, 0, 0]]]),
	right_shin: R([[0, Z3], [0.6, [45, 0, 0]]]),
	left_shin: R([[0, Z3], [0.6, [45, 0, 0]]]),
});
D.anim('teleport_arrive', 0.6, false, {
	root: P([[0, [0, -3, 0]], [0.3, [0, -0.5, 0]], [0.6, Z3]]),
	body: R([[0, [20, 0, 0]], [0.3, [10, 0, 0]], [0.6, Z3]]),
	right_arm: R([[0, [-75, 0, -30]], [0.3, [-110, 0, 20]], [0.45, [-60, 0, 10]], [0.6, RA]]),
	left_arm: R([[0, [-75, 0, 30]], [0.3, [20, 0, -40]], [0.6, LA]]),
	right_leg: R([[0, [-30, 0, 0]], [0.6, Z3]]),
	left_leg: R([[0, [-30, 0, 0]], [0.6, Z3]]),
	right_shin: R([[0, [45, 0, 0]], [0.6, Z3]]),
	left_shin: R([[0, [45, 0, 0]], [0.6, Z3]]),
});
// phase-change roar (roar 1.5 = 30 t)
D.anim('rage_transition', 3.0, false, {
	root: P([[0, Z3], [1.0, [0, -2, 0]], [1.5, [0, 0.8, 0]], [2.4, [0, 0.5, 0]], [3.0, Z3]]),
	body: R([[0, Z3], [1.0, [24, 0, 0]], [1.5, [-16, 0, 0]], [2.4, [-14, 0, 0]], [3.0, Z3]]),
	head: R([[0, Z3], [1.0, [20, 0, 0]], [1.5, [-32, 0, 0]], [2.4, [-30, 0, 0]], [3.0, Z3]]),
	right_arm: R([[0, RA], [1.0, [-50, 0, -20]], [1.5, [-25, 0, 95]], [2.4, [-25, 0, 92]], [3.0, RA]]),
	left_arm: R([[0, LA], [1.0, [-50, 0, 20]], [1.5, [-25, 0, -95]], [2.4, [-25, 0, -92]], [3.0, LA]]),
	right_forearm: R([[0, FA], [1.0, [-100, 0, 0]], [1.5, [-15, 0, 0]], [3.0, FA]]),
	left_forearm: R([[0, FA], [1.0, [-100, 0, 0]], [1.5, [-15, 0, 0]], [3.0, FA]]),
	right_leg: R([[0, Z3], [1.0, [-20, 0, 0]], [1.5, [0, 0, 8]], [3.0, Z3]]),
	left_leg: R([[0, Z3], [1.0, [-20, 0, 0]], [1.5, [0, 0, -8]], [3.0, Z3]]),
	right_shin: R([[0, Z3], [1.0, [30, 0, 0]], [1.5, Z3]]),
	left_shin: R([[0, Z3], [1.0, [30, 0, 0]], [1.5, Z3]]),
});
// Omega Annihilation: a slow, trembling rise (held) and the release (blast 0.3 = 6 t)
{
	const rArm = [[0, RA], [1.0, [-10, 0, 90]]], lArm = [[0, LA], [1.0, [-10, 0, -90]]], body = [[0, Z3], [1.0, [-6, 0, 0]]], head = [[0, Z3], [1.0, [-18, 0, 0]]];
	for (let t = 1.25, i = 0; t <= 5.0001; t += 0.25, i++) {
		const k = (t - 1.0) / 4.0, j = (i % 2 ? 1 : -1) * (1 + 2 * k);
		rArm.push([t, [-10 - 20 * k, j, 90 + 60 * k]]);
		lArm.push([t, [-10 - 20 * k, -j, -90 - 60 * k]]);
		body.push([t, [-6 - 10 * k + j * 0.4, 0, j * 0.5]]);
		head.push([t, [-18 - 12 * k, j * 0.8, 0]]);
	}
	D.anim('omega_annihilation_charge', 5.0, 'hold', {
		root: P([[0, Z3], [1.0, [0, 0.5, 0]], [5.0, [0, 1.2, 0]]]),
		body: R(body), head: R(head), right_arm: R(rArm), left_arm: R(lArm),
		right_forearm: R([[0, FA], [1.0, [-20, 0, 0]], [5.0, [-10, 0, 0]]]),
		left_forearm: R([[0, FA], [1.0, [-20, 0, 0]], [5.0, [-10, 0, 0]]]),
		right_leg: R([[0, Z3], [1.0, [0, 0, 10]], [5.0, [0, 0, 12]]]),
		left_leg: R([[0, Z3], [1.0, [0, 0, -10]], [5.0, [0, 0, -12]]]),
	});
}
D.anim('omega_annihilation_release', 1.2, false, {
	root: P([[0, [0, 1.2, 0]], [0.3, [0, -1.5, -1]], [0.7, [0, -1.2, -1]], [1.2, Z3]]),
	body: R([[0, [-16, 0, 0]], [0.3, [26, 0, 0]], [0.7, [22, 0, 0]], [1.2, Z3]]),
	head: R([[0, [-30, 0, 0]], [0.3, [8, 0, 0]], [1.2, Z3]]),
	right_arm: R([[0, [-30, 0, 150]], [0.15, [-150, 0, 40]], [0.3, [-85, 0, 10]], [0.7, [-80, 0, 10]], [1.2, RA]]),
	left_arm: R([[0, [-30, 0, -150]], [0.15, [-150, 0, -40]], [0.3, [-85, 0, -10]], [0.7, [-80, 0, -10]], [1.2, LA]]),
	right_hand: R([[0, Z3], [0.3, [-40, 0, 0]], [1.2, Z3]]),
	left_hand: R([[0, Z3], [0.3, [-40, 0, 0]], [1.2, Z3]]),
	right_leg: R([[0, [0, 0, 12]], [0.3, [-24, 0, 6]], [1.2, Z3]]),
	left_leg: R([[0, [0, 0, -12]], [0.3, [16, 0, -6]], [1.2, Z3]]),
});
D.anim('omega_sweep', 1.0, true, {
	body: R([[0, [10, 0, 0]], [1.0, [10, 0, 0]]]),
	head: R([[0, [28, 0, 0]], [0.25, [29, 1.5, 0]], [0.5, [28, 0, 0]], [0.75, [29, -1.5, 0]], [1.0, [28, 0, 0]]]),
	right_arm: R([[0, [6, 0, 18]], [1.0, [6, 0, 18]]]),
	left_arm: R([[0, [6, 0, -18]], [1.0, [6, 0, -18]]]),
	right_forearm: R([[0, [-40, 0, 0]], [1.0, [-40, 0, 0]]]),
	left_forearm: R([[0, [-40, 0, 0]], [1.0, [-40, 0, 0]]]),
	right_leg: R([[0, [-8, 0, 6]], [1.0, [-8, 0, 6]]]),
	left_leg: R([[0, [8, 0, -6]], [1.0, [8, 0, -6]]]),
});
// Boom Tube Reinforcements (open 0.8 = 16 t)
D.anim('summon', 1.2, false, {
	body: R([[0, Z3], [0.6, [-10, 0, 0]], [0.8, [8, 0, 0]], [1.2, Z3]]),
	head: R([[0, Z3], [0.6, [-20, 0, 0]], [0.8, [0, 0, 0]], [1.2, Z3]]),
	right_arm: R([[0, RA], [0.6, [-172, 0, 8]], [0.8, [-92, 0, 8]], [0.95, [-90, 0, 8]], [1.2, RA]]),
	right_hand: R([[0, Z3], [0.8, [-35, 0, 0]], [1.2, Z3]]),
	left_arm: R([[0, LA], [0.6, [10, 0, -20]], [1.2, LA]]),
});
D.anim('hurt', 0.5, false, {
	body: R([[0, Z3], [0.1, [-10, 0, 3]], [0.5, Z3]]),
	head: R([[0, Z3], [0.1, [-12, 0, 0]], [0.5, Z3]]),
	right_arm: R([[0, RA], [0.1, [10, 0, 18]], [0.5, RA]]),
	left_arm: R([[0, LA], [0.1, [10, 0, -18]], [0.5, LA]]),
});
// kneel pose shared by stagger and death: right knee up in front, left knee on the ground
const KNEEL = {
	root: [0, -7, 0], body: [18, 0, 0], head: [22, 0, 0],
	right_leg: [-80, 0, 6], right_shin: [80, 0, 0], left_leg: [-6, 0, -4], left_shin: [88, 0, 0],
	right_arm: [-30, 0, 10], right_forearm: [-40, 0, 0], left_arm: [10, 0, -10], left_forearm: [-10, 0, 0],
};
D.anim('stagger', 1.0, 'hold', {
	root: P([[0, Z3], [0.3, [0, -1, 1]], [1.0, KNEEL.root]]),
	body: R([[0, Z3], [0.3, [-14, 0, 5]], [1.0, KNEEL.body]]),
	head: R([[0, Z3], [0.3, [-18, 0, 0]], [1.0, KNEEL.head]]),
	right_leg: R([[0, Z3], [0.5, [-40, 0, 4]], [1.0, KNEEL.right_leg]]),
	right_shin: R([[0, Z3], [0.5, [40, 0, 0]], [1.0, KNEEL.right_shin]]),
	left_leg: R([[0, Z3], [0.5, [10, 0, -4]], [1.0, KNEEL.left_leg]]),
	left_shin: R([[0, Z3], [0.5, [40, 0, 0]], [1.0, KNEEL.left_shin]]),
	right_arm: R([[0, RA], [0.3, [20, 0, 30]], [1.0, KNEEL.right_arm]]),
	right_forearm: R([[0, FA], [1.0, KNEEL.right_forearm]]),
	left_arm: R([[0, LA], [0.3, [20, 0, -30]], [1.0, KNEEL.left_arm]]),
	left_forearm: R([[0, FA], [1.0, KNEEL.left_forearm]]),
});
D.anim('stagger_recover', 0.7, false, Object.fromEntries(Object.entries(KNEEL).map(([bn, v]) =>
	[bn, bn === 'root' ? P([[0, v], [0.7, Z3]]) : R([[0, v], [0.7, DREST[bn].rotation]])])));
// death (5.5 s): stagger back, down on one knee, the energy tears loose, head snaps up and arms fling wide at the
// burst (3.6 s = 72 t), then held as he fades into the Boom Tube
{
	const tremble = (base, amp, from, to) => { const out = []; for (let t = from, i = 0; t <= to + 1e-6; t += 0.15, i++) out.push([+t.toFixed(3), base.map((b, k) => b + (k === 2 ? (i % 2 ? amp : -amp) : 0))]); return out; };
	D.anim('death', 5.5, 'hold', {
		root: P([[0, Z3], [0.6, [0, -0.5, 1.5]], [1.4, KNEEL.root], [3.4, KNEEL.root], [3.6, [0, -6, 0]], [5.5, [0, -6, 0]]]),
		body: R([[0, Z3], [0.6, [-16, 0, 4]], [1.4, KNEEL.body], ...tremble([26, 0, 0], 2.5, 1.55, 3.3), [3.6, [-24, 0, 0]], [5.5, [-26, 0, 0]]]),
		head: R([[0, Z3], [0.6, [-20, 0, 0]], [1.4, [30, 0, 0]], [3.3, [34, 0, 0]], [3.6, [-40, 0, 0]], [5.5, [-42, 0, 0]]]),
		right_leg: R([[0, Z3], [0.6, [10, 0, 4]], [1.4, KNEEL.right_leg], [5.5, KNEEL.right_leg]]),
		right_shin: R([[0, Z3], [1.4, KNEEL.right_shin], [5.5, KNEEL.right_shin]]),
		left_leg: R([[0, Z3], [0.6, [-10, 0, -4]], [1.4, KNEEL.left_leg], [5.5, KNEEL.left_leg]]),
		left_shin: R([[0, Z3], [1.4, KNEEL.left_shin], [5.5, KNEEL.left_shin]]),
		right_arm: R([[0, RA], [0.6, [25, 0, 30]], [1.4, [-40, 0, -25]], [3.3, [-45, 0, -28]], [3.6, [-30, 0, 105]], [5.5, [-32, 0, 110]]]),
		left_arm: R([[0, LA], [0.6, [25, 0, -30]], [1.4, [-40, 0, 25]], [3.3, [-45, 0, 28]], [3.6, [-30, 0, -105]], [5.5, [-32, 0, -110]]]),
		right_forearm: R([[0, FA], [1.4, [-100, 0, 0]], [3.3, [-105, 0, 0]], [3.6, [-10, 0, 0]], [5.5, [-10, 0, 0]]]),
		left_forearm: R([[0, FA], [1.4, [-100, 0, 0]], [3.3, [-105, 0, 0]], [3.6, [-10, 0, 0]], [5.5, [-10, 0, 0]]]),
	});
}
// the tables were written for a 40 px body; the user's rig is 32 px (12 px legs): scale every position key
for (const a of Object.values(D.A)) for (const b of Object.values(a.bones)) if (b.position) for (const t of Object.keys(b.position)) b.position[t] = b.position[t].map(v => +(v * 0.75).toFixed(2));
fs.writeFileSync(ASSETS + 'animations/darkseid.animation.json', JSON.stringify({ format_version: '1.8.0', animations: D.A }, null, 1));

// ---- verify against DarkseidAnims.java
{
	const java = fs.readFileSync(ANIMS_JAVA, 'utf8');
	const C = {};
	for (const m of java.matchAll(/static final int (\w+) = (\d+);/g)) C[m[1]] = +m[2];
	const arr = {};
	for (const m of java.matchAll(/static final int\[\] (\w+) = \{([^}]*)\}/g)) arr[m[1]] = m[2].split(',').map(s => +s.trim());
	const names = {};
	for (const m of java.matchAll(/static final String (\w+) = "(\w+)";/g)) names[m[1]] = m[2];
	const lenOf = n => { const a = D.A['animation.darkseid.' + n]; if (!a) throw new Error('missing clip ' + n); return Math.round(a.animation_length * 20); };
	const keyed = (n, tick) => { const a = D.A['animation.darkseid.' + n]; const t = (tick / 20).toFixed(3); return Object.values(a.bones).some(b => Object.keys(b.rotation || {}).includes(t)); };
	const checks = [
		['ENTRANCE', 'ENTRANCE_TICKS'], ['MELEE_1', 'MELEE_1_TICKS', 'MELEE_1_IMPACT'], ['MELEE_2', 'MELEE_2_TICKS', 'MELEE_2_IMPACT'],
		['MELEE_COMBO', 'MELEE_COMBO_TICKS'], ['GROUND_SLAM', 'GROUND_SLAM_TICKS', 'GROUND_SLAM_IMPACT'], ['BEAM_FIRE', 'BEAM_FIRE_TICKS'],
		['BARRAGE', 'BARRAGE_TICKS', 'BARRAGE_RELEASE'], ['GRIP_THROW', 'GRIP_THROW_TICKS', 'GRIP_THROW_RELEASE'], ['TELEPORT', 'TELEPORT_TICKS'],
		['TELEPORT_ARRIVE', 'TELEPORT_ARRIVE_TICKS'], ['RAGE', 'RAGE_TICKS', 'RAGE_ROAR'], ['ANNIHILATION_RELEASE', 'ANNIHILATION_RELEASE_TICKS', 'ANNIHILATION_BLAST'],
		['SUMMON', 'SUMMON_TICKS', 'SUMMON_OPEN'], ['HURT', 'HURT_TICKS'], ['STAGGER_RECOVER', 'STAGGER_RECOVER_TICKS'], ['DEATH', 'DEATH_TICKS', 'DEATH_BURST'],
	];
	let bad = 0;
	for (const [n, lenKey, impKey] of checks) {
		const clip = names[n];
		if (lenOf(clip) !== C[lenKey]) { console.error('LENGTH MISMATCH', clip, lenOf(clip), 'vs', lenKey, C[lenKey]); bad++; }
		if (impKey && !keyed(clip, C[impKey])) { console.error('NO KEYFRAME AT IMPACT', clip, impKey, C[impKey]); bad++; }
	}
	for (const tick of arr.MELEE_COMBO_IMPACTS) if (!keyed('melee_combo', tick)) { console.error('NO KEYFRAME AT combo impact', tick); bad++; }
	if (lenOf('grip') !== C.GRIP_REACH_TICKS) { console.error('grip reach', lenOf('grip'), C.GRIP_REACH_TICKS); bad++; }
	if (lenOf('charge') !== C.CHARGE_WINDUP_TICKS) { console.error('charge windup', lenOf('charge'), C.CHARGE_WINDUP_TICKS); bad++; }
	for (const n of Object.values(names)) if (!D.A['animation.darkseid.' + n]) { console.error('clip named in Java but not generated:', n); bad++; }
	if (bad) { console.error(bad + ' Darkseid animation/Java mismatches'); process.exit(1); }
	console.log('darkseid: ' + Object.keys(D.A).length + ' clips, all timings match DarkseidAnims.java');
}

// ==================================================================== PARADEMON (the user's parademon.bbmodel, 32 px = 2 blocks)
writeGeo('parademon.geo.json', 'geometry.parademon', skinRig([]));
const pdSkin = skinAndGlow('parademon.bbmodel', 'parademon', c => c[0] > 170 && c[1] < 90 && c[2] < 90);
// the other three variants are recolours of the same skin, sharing its eye glowmask
function recolour(outName, fn) {
	const im = pdSkin.im;
	const out = img(im.w, im.h);
	for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) {
		const c = get(im, x, y);
		if (c[3] === 0) continue;
		set(out, x, y, fn(c), c[3]);
	}
	save(out, 'textures/entity/' + outName + '.png');
	fs.copyFileSync(ASSETS + 'textures/entity/parademon_glowmask.png', ASSETS + 'textures/entity/' + outName + '_glowmask.png');
}
const isEyeRed = c => c[0] > 170 && c[1] < 90 && c[2] < 90;
const isOrange = c => c[0] > 180 && c[1] > 80 && c[1] < 190 && c[2] < 80;
const lum = c => (c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11);
// Ranged gunners: a bronze cast to the armour, the emblem burning brighter
recolour('parademon_ranged', c => isEyeRed(c) ? c : isOrange(c) ? [255, 150, 40] : mix(c, [lum(c) * 1.1, lum(c) * 0.85, lum(c) * 0.6], 0.55));
// Elites: near-black armour, gold emblem
recolour('parademon_elite', c => isEyeRed(c) ? c : isOrange(c) ? [230, 180, 50] : shade(c, 0.5));
// Brutes: scarred red-brown
recolour('parademon_brute', c => isEyeRed(c) ? c : isOrange(c) ? [255, 90, 30] : mix(c, [lum(c) * 1.2, lum(c) * 0.7, lum(c) * 0.55], 0.6));
console.log('parademon: your skin + 3 recoloured variants, ' + pdSkin.glowPixels + ' glowing eye pixels');

// ---- Parademon animations (no wings on this model: flight is a forward-leaning dive)
const PREST = {
	root: { rotation: Z3, position: Z3 }, body: { rotation: Z3, position: Z3 }, torso: { rotation: Z3, position: Z3 }, head: { rotation: Z3, position: Z3 },
	right_arm: { rotation: [0, 0, 4], position: Z3 }, left_arm: { rotation: [0, 0, -4], position: Z3 },
	right_forearm: { rotation: [-6, 0, 0], position: Z3 }, left_forearm: { rotation: [-6, 0, 0], position: Z3 },
	right_leg: { rotation: Z3, position: Z3 }, left_leg: { rotation: Z3, position: Z3 },
	right_shin: { rotation: Z3, position: Z3 }, left_shin: { rotation: Z3, position: Z3 },
};
const PA = animSet('parademon', PREST);
PA.anim('idle', 2.0, true, {
	torso: R([[0, [4, 0, 0]], [1.0, [6, 0, 0]], [2.0, [4, 0, 0]]]),
	head: R([[0, [-4, 0, 0]], [0.7, [-4, 12, 0]], [1.4, [-4, -8, 0]], [2.0, [-4, 0, 0]]]),
	right_arm: R([[0, [0, 0, 4]], [1.0, [-4, 0, 7]], [2.0, [0, 0, 4]]]),
	left_arm: R([[0, [0, 0, -4]], [1.0, [-4, 0, -7]], [2.0, [0, 0, -4]]]),
	right_forearm: R([[0, [-6, 0, 0]], [1.0, [-14, 0, 0]], [2.0, [-6, 0, 0]]]),
	left_forearm: R([[0, [-6, 0, 0]], [1.0, [-14, 0, 0]], [2.0, [-6, 0, 0]]]),
});
PA.anim('walk', 0.9, true, {
	root: P([[0, Z3], [0.225, [0, 0.3, 0]], [0.45, Z3], [0.675, [0, 0.3, 0]], [0.9, Z3]]),
	torso: R([[0, [10, 0, 0]], [0.9, [10, 0, 0]]]),
	head: R([[0, [-8, 0, 0]], [0.9, [-8, 0, 0]]]),
	right_leg: R([[0, [-30, 0, 0]], [0.45, [30, 0, 0]], [0.9, [-30, 0, 0]]]),
	left_leg: R([[0, [30, 0, 0]], [0.45, [-30, 0, 0]], [0.9, [30, 0, 0]]]),
	right_shin: R([[0, [0, 0, 0]], [0.225, [35, 0, 0]], [0.45, [15, 0, 0]], [0.9, [0, 0, 0]]]),
	left_shin: R([[0, [15, 0, 0]], [0.45, [0, 0, 0]], [0.675, [35, 0, 0]], [0.9, [15, 0, 0]]]),
	right_arm: R([[0, [25, 0, 6]], [0.45, [-25, 0, 6]], [0.9, [25, 0, 6]]]),
	left_arm: R([[0, [-25, 0, -6]], [0.45, [25, 0, -6]], [0.9, [-25, 0, -6]]]),
	right_forearm: R([[0, [-25, 0, 0]], [0.9, [-25, 0, 0]]]),
	left_forearm: R([[0, [-25, 0, 0]], [0.9, [-25, 0, 0]]]),
});
PA.anim('fly', 0.6, true, {
	root: P([[0, [0, 0.6, 0]], [0.3, [0, -0.6, 0]], [0.6, [0, 0.6, 0]]]),
	body: R([[0, [55, 0, 0]], [0.3, [58, 0, 0]], [0.6, [55, 0, 0]]]),
	head: R([[0, [-45, 0, 0]], [0.6, [-45, 0, 0]]]),
	right_arm: R([[0, [40, 0, 22]], [0.3, [48, 0, 26]], [0.6, [40, 0, 22]]]),
	left_arm: R([[0, [40, 0, -22]], [0.3, [48, 0, -26]], [0.6, [40, 0, -22]]]),
	right_forearm: R([[0, [-20, 0, 0]], [0.6, [-20, 0, 0]]]),
	left_forearm: R([[0, [-20, 0, 0]], [0.6, [-20, 0, 0]]]),
	right_leg: R([[0, [12, 0, 4]], [0.3, [20, 0, 4]], [0.6, [12, 0, 4]]]),
	left_leg: R([[0, [20, 0, -4]], [0.3, [12, 0, -4]], [0.6, [20, 0, -4]]]),
	right_shin: R([[0, [25, 0, 0]], [0.6, [25, 0, 0]]]),
	left_shin: R([[0, [25, 0, 0]], [0.6, [25, 0, 0]]]),
});
PA.anim('attack', 0.6, false, {
	torso: R([[0, Z3], [0.18, [-6, -20, 0]], [0.3, [14, 20, 0]], [0.6, Z3]]),
	right_arm: R([[0, [0, 0, 4]], [0.18, [-150, 0, 10]], [0.3, [-50, 0, 0]], [0.6, [0, 0, 4]]]),
	right_forearm: R([[0, [-6, 0, 0]], [0.18, [-40, 0, 0]], [0.3, [-5, 0, 0]], [0.6, [-6, 0, 0]]]),
	left_arm: R([[0, [0, 0, -4]], [0.3, [-60, 0, -10]], [0.6, [0, 0, -4]]]),
});
PA.anim('shoot', 0.6, false, {
	right_arm: R([[0, [0, 0, 4]], [0.2, [-88, 0, 0]], [0.3, [-100, 0, 0]], [0.45, [-88, 0, 0]], [0.6, [0, 0, 4]]]),
	right_forearm: R([[0, [-6, 0, 0]], [0.2, [0, 0, 0]], [0.6, [-6, 0, 0]]]),
	torso: R([[0, Z3], [0.3, [-5, 0, 0]], [0.6, Z3]]),
	head: R([[0, Z3], [0.2, [0, 10, 0]], [0.6, Z3]]),
});
fs.writeFileSync(ASSETS + 'animations/parademon.animation.json', JSON.stringify({ format_version: '1.8.0', animations: PA.A }, null, 1));
console.log('parademon: ' + Object.keys(PA.A).length + ' clips');


// ==================================================================== BOOM TUBE (64x64 radial light)
{
	const t = img(64, 64);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const dx = (x - 31.5) / 31.5, dy = (y - 31.5) / 31.5;
		const r = Math.sqrt(dx * dx + dy * dy);
		if (r > 1) { set(t, x, y, [0, 0, 0], 0); continue; }
		const ang = Math.atan2(dy, dx);
		const streak = 0.5 + 0.5 * Math.sin(ang * 9 + r * 12);
		const core = Math.max(0, 1 - r / 0.35);
		const ring = Math.exp(-Math.pow((r - 0.82) / 0.1, 2));
		const a = Math.min(1, core + ring * 0.9 + (1 - r) * 0.55 * streak);
		const c = mix([120, 170, 255], [255, 255, 255], Math.min(1, core + ring * 0.5));
		set(t, x, y, c, clamp(a * 255));
	}
	save(t, 'textures/entity/boom_tube.png');
}

// ==================================================================== ITEMS (16x16)
function item16(name, draw) { const t = img(16, 16); draw(t); save(t, 'textures/item/' + name + '.png'); }
const px = (t, pts, c) => pts.forEach(([x, y]) => set(t, x, y, c));
// Boom Tube Beacon: a dark Apokoliptian cylinder with a blue-white portal eye
item16('boom_tube_beacon', t => {
	for (let y = 3; y < 15; y++) for (let x = 4; x < 12; x++) set(t, x, y, shade([40, 38, 46], 0.8 + hash(x, y, 30) * 0.4));
	for (let x = 3; x < 13; x++) { set(t, x, 3, [70, 66, 80]); set(t, x, 14, [26, 24, 30]); }
	for (let x = 5; x < 11; x++) set(t, x, 2, [90, 30, 30]);
	for (let y = 6; y < 12; y++) for (let x = 6; x < 10; x++) { const d = Math.hypot(x - 7.5, y - 8.5); set(t, x, y, d < 1.3 ? [255, 255, 255] : [120, 180, 255]); }
	px(t, [[4, 7], [11, 7], [4, 10], [11, 10]], [200, 40, 30]);
});
// Omega Core: a pulsing red-black sphere with the Omega sign
item16('omega_core', t => {
	for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d > 6.6) continue;
		const k = 1 - d / 6.6;
		set(t, x, y, mix([30, 6, 8], [255, 60, 30], Math.pow(k, 1.4)));
	}
	px(t, [[5, 5], [6, 4], [7, 4], [8, 4], [9, 4], [10, 5], [10, 6], [10, 7], [9, 8], [6, 8], [5, 7], [5, 6], [5, 9], [6, 9], [9, 9], [10, 9]], [255, 230, 190]);
});
// Omega Shard: a jagged red crystal
item16('omega_shard', t => {
	const shape = [[7, 1], [8, 2], [6, 3], [7, 3], [8, 3], [9, 3]];
	for (let y = 2; y < 15; y++) { const w = y < 8 ? (y - 1) * 0.6 : (15 - y) * 0.55; for (let x = Math.round(7.5 - w); x <= Math.round(7.5 + w * 0.8); x++) set(t, x, y, mix([120, 10, 10], [255, 80, 50], hash(x, y, 31))); }
	px(t, shape, [255, 200, 180]);
	px(t, [[7, 5], [7, 6], [8, 7], [8, 8]], [255, 220, 200]);
});
// Omega Relic: a dark stone tablet with glowing red Omega eyes
item16('omega_relic', t => {
	for (let y = 2; y < 15; y++) for (let x = 3; x < 13; x++) {
		if ((y === 2 || y === 14) && (x === 3 || x === 12)) continue;
		set(t, x, y, shade([70, 66, 62], 0.7 + hash(x, y, 32) * 0.45));
	}
	px(t, [[5, 6], [6, 6], [9, 6], [10, 6]], [255, 40, 20]);
	px(t, [[5, 5], [6, 5], [9, 5], [10, 5]], [40, 38, 36]);
	for (let y = 9; y < 13; y++) px(t, [[7, y], [8, y]], [180, 30, 20]);
	px(t, [[6, 9], [9, 9], [6, 12], [9, 12]], [180, 30, 20]);
});
// Mother Box texture (16x16 atlas face): grey metal with glowing blue circuitry
item16('mother_box', t => {
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(t, x, y, shade([74, 74, 84], 0.8 + hash(x, y, 33) * 0.3));
	for (let i = 0; i < 16; i++) { set(t, i, 0, [40, 40, 48]); set(t, i, 15, [40, 40, 48]); set(t, 0, i, [40, 40, 48]); set(t, 15, i, [40, 40, 48]); }
	const CY = [90, 220, 255];
	px(t, [[3, 3], [4, 3], [5, 3], [5, 4], [5, 5], [6, 5], [7, 5], [8, 5], [8, 6], [8, 7], [8, 8], [9, 8], [10, 8], [11, 8], [12, 8], [12, 9], [12, 10], [12, 11], [3, 12], [4, 12], [5, 12], [5, 11], [5, 10], [4, 7], [3, 7], [3, 8], [3, 9], [10, 3], [11, 3], [12, 3], [12, 4], [12, 5]], CY);
	px(t, [[7, 7], [7, 8], [8, 7], [8, 8]], [230, 255, 255]);
});
// item models
const flat = n => ({ parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + n } });
for (const n of ['boom_tube_beacon', 'omega_core', 'omega_shard', 'omega_relic']) fs.writeFileSync(ASSETS + 'models/item/' + n + '.json', JSON.stringify(flat(n), null, 2) + '\n');
for (const n of ['darkseid_spawn_egg', 'parademon_spawn_egg']) fs.writeFileSync(ASSETS + 'models/item/' + n + '.json', JSON.stringify({ parent: 'minecraft:item/template_spawn_egg' }, null, 2) + '\n');
// the Mother Box is a real little 3D block-ish model (also what the raid's floating boxes render with)
const allFaces = uv => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, { uv, texture: '#box' }]));
fs.writeFileSync(ASSETS + 'models/item/mother_box.json', JSON.stringify({
	textures: { box: 'projecthero:item/mother_box', particle: 'projecthero:item/mother_box' },
	elements: [
		{ from: [3, 0, 4], to: [13, 7, 12], faces: allFaces([0, 0, 16, 16]) },
		{ from: [4, 7, 5], to: [12, 8, 11], faces: allFaces([2, 2, 14, 14]) },
		{ from: [7, 8, 7], to: [9, 9, 9], faces: allFaces([7, 7, 9, 9]) },
	],
	display: {
		gui: { rotation: [30, 225, 0], translation: [0, 2, 0], scale: [1.0, 1.0, 1.0] },
		ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
		fixed: { rotation: [0, 0, 0], translation: [0, -2, 0], scale: [0.8, 0.8, 0.8] },
		thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.4, 0.4, 0.4] },
		firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
	},
}, null, 2) + '\n');
console.log('items: 5 textures + 7 models; boom tube texture');
