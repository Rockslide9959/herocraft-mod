// v0.14.16 Spider Horde: every generated asset of the rebuilt Brood Queen and the brood spider variants.
//   - geo/brood_queen.geo.json            (8 jointed legs: hip / femur / tibia / foot; thorax, head, 10 eyes, fangs,
//                                           palps, chitin crown, bloated abdomen with egg sacs and spinnerets)
//   - animations/brood_queen.animation.json (idle, walk gait, attack clips, shriek, death, intro)
//   - textures/entity/brood_queen.png + brood_queen_glowmask.png (box-UV atlas painted per material)
//   - textures/entity/brood_spider/<variant>.png (vanilla spider recoloured, one per variant)
// Usage (from the repo root): node scratchpad/gen_brood_queen_v01416.js <dir holding assets/minecraft/textures/entity/...>
//
// Model space is Blockbench/Bedrock: y up, the queen faces -Z, +X is her right. The model is authored at HALF size
// (8 units = 1 block in game); BroodQueenRenderer scales it by 2.
// Rotation conventions (checked against GeckoLib's loader and the existing Darkseid/Oathbreaker clips):
//   +rx pitches the front (-Z) down; +rz lowers the +X end of a bone; +ry swings the +X end forward (-Z).
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit');

const VANILLA = process.argv[2];
if (!VANILLA) throw new Error('usage: node scratchpad/gen_brood_queen_v01416.js <extracted client jar dir>');
const A = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');
const json = (p, obj) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, 1) + '\n'); };

// ------------------------------------------------------------------ geometry
const bones = [];
const boneByName = {};
function bone(name, parent, pivot, rotation) {
	const b = { name, pivot: pivot.slice() };
	if (parent) b.parent = parent;
	if (rotation && rotation.some(v => v !== 0)) b.rotation = rotation.slice();
	b.cubes = [];
	bones.push(b);
	boneByName[name] = b;
	return b;
}
/** A cube in model space; {@code mat} picks how the texture region is painted. Sizes are whole units (clean box UV). */
function cube(b, origin, size, mat) {
	b.cubes.push({ origin: origin.slice(), size: size.slice(), mat });
}

const LEG_Z = [-7, -2.5, 2.5, 7];
const LEG_SPREAD = [40, 14, -14, -40]; // degrees forward
const FEMUR_LIFT = 40, TIBIA_BEND = 105, FOOT_BEND = 15;
const FEMUR_LEN = 15, TIBIA_LEN = 17, FOOT_LEN = 8;
const ATTACH_X = 8.5, BODY_Y = 15;

bone('root', null, [0, 0, 0]);
const body = bone('body', 'root', [0, BODY_Y, 0]);
cube(body, [-9, 11, -10], [18, 9, 20], 'chitin');
cube(body, [-7, 20, -8], [14, 2, 16], 'plate');
cube(body, [-6, 9, -6], [12, 2, 12], 'chitin_dark');

const head = bone('head', 'body', [0, 16, -10]);
cube(head, [-6, 11, -19], [12, 10, 9], 'chitin');
cube(head, [-7, 19, -20], [14, 3, 4], 'plate');
// ten eyes: a big pair, a small pair under them, two on the brow, two behind those, one each side
for (const [o, s] of [
	[[-4, 15, -20], [3, 3, 1]], [[1, 15, -20], [3, 3, 1]],
	[[-5.5, 13, -20], [2, 2, 1]], [[3.5, 13, -20], [2, 2, 1]],
	[[-5, 22, -19], [2, 1, 2]], [[3, 22, -19], [2, 1, 2]],
	[[-2.5, 22, -16], [2, 1, 2]], [[0.5, 22, -16], [2, 1, 2]],
	[[-7, 16, -17], [1, 2, 2]], [[6, 16, -17], [1, 2, 2]],
]) cube(head, o, s, 'eye');

const crown = bone('crown', 'head', [0, 22, -14]);
cube(crown, [-7, 21.5, -17], [14, 1, 4], 'plate');
[[-6, 5], [-3.5, 7], [-1, 9], [1.5, 7], [4, 5]].forEach(([x, h]) => cube(crown, [x, 22.5, -16], [2, h, 2], 'spike'));

for (const [side, sx] of [['left', -1], ['right', 1]]) {
	const fang = bone(`fang_${side}`, 'head', [sx * 3, 12, -18]);
	cube(fang, [sx > 0 ? 1.5 : -4.5, 4, -20], [3, 8, 3], 'fang');
	cube(fang, [sx > 0 ? 2 : -4, 1, -19.5], [2, 3, 2], 'venom');
	const palp = bone(`palp_${side}`, 'head', [sx * 5.5, 12, -18]);
	cube(palp, [sx > 0 ? 4.5 : -7, 10, -25], [2, 2, 7], 'leg');
	cube(palp, [sx > 0 ? 4.5 : -7, 9, -27], [2, 3, 2], 'leg_tip');
}

const abdomen = bone('abdomen', 'body', [0, 18, 9]);
cube(abdomen, [-13, 13, 8], [26, 22, 28], 'abdomen');
cube(abdomen, [-10, 35, 12], [20, 4, 20], 'abdomen_top');
cube(abdomen, [-10, 10, 12], [20, 3, 20], 'abdomen_dark');
cube(abdomen, [-3, 15, 36], [6, 6, 3], 'spinneret');
const eggs = bone('eggs', 'abdomen', [0, 36, 22]);
for (const [o, s] of [
	[[-9, 38, 14], [6, 5, 6]], [[3, 38, 17], [6, 5, 6]], [[-3, 39, 24], [7, 5, 7]],
	[[-8, 37, 28], [5, 4, 5]], [[4, 37, 28], [5, 4, 5]],
	[[-15, 22, 15], [3, 7, 7]], [[12, 24, 20], [3, 7, 7]], [[-15, 17, 26], [3, 6, 6]], [[12, 16, 28], [3, 5, 5]],
]) cube(eggs, o, s, 'egg');

const legNames = [];
for (const [side, sx] of [['l', -1], ['r', 1]]) {
	for (let i = 0; i < 4; i++) {
		const n = `leg_${side}${i + 1}`;
		legNames.push({ n, sx, i });
		const z = LEG_Z[i];
		const ax = sx * ATTACH_X;
		bone(n, 'body', [ax, BODY_Y, z], [0, sx * LEG_SPREAD[i], 0]);
		const femur = bone(`${n}_femur`, n, [ax, BODY_Y, z], [0, 0, -sx * FEMUR_LIFT]);
		cube(femur, [sx > 0 ? ax : ax - FEMUR_LEN, BODY_Y - 2, z - 2], [FEMUR_LEN, 4, 4], 'leg');
		const kx = ax + sx * FEMUR_LEN;
		const tibia = bone(`${n}_tibia`, `${n}_femur`, [kx, BODY_Y, z], [0, 0, sx * TIBIA_BEND]);
		cube(tibia, [sx > 0 ? kx : kx - TIBIA_LEN, BODY_Y - 1.5, z - 1.5], [TIBIA_LEN, 3, 3], 'leg');
		const fx = kx + sx * TIBIA_LEN;
		const foot = bone(`${n}_foot`, `${n}_tibia`, [fx, BODY_Y, z], [0, 0, sx * FOOT_BEND]);
		cube(foot, [sx > 0 ? fx : fx - FOOT_LEN, BODY_Y - 1, z - 1], [FOOT_LEN, 2, 2], 'leg_tip');
		const cx = fx + sx * FOOT_LEN;
		cube(foot, [sx > 0 ? cx : cx - 2, BODY_Y - 0.5, z - 0.5], [2, 1, 1], 'claw');
	}
}

// ---- forward kinematics (rest pose) to sit the feet on the ground
const rad = d => d * Math.PI / 180;
const mul = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const RX = t => { const c = Math.cos(t), s = Math.sin(t); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const RY = t => { const c = Math.cos(t), s = Math.sin(t); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const RZ = t => { const c = Math.cos(t), s = Math.sin(t); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const apply = (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]);
function worldOf(b, cache = {}) {
	if (cache[b.name]) return cache[b.name];
	const [rx, ry, rz] = b.rotation || [0, 0, 0];
	const p = b.pivot;
	let local = mul(T(p[0], p[1], p[2]), mul(RZ(rad(-rz)), mul(RY(rad(ry)), mul(RX(rad(-rx)), T(-p[0], -p[1], -p[2])))));
	const m = b.parent ? mul(worldOf(boneByName[b.parent], cache), local) : local;
	cache[b.name] = m;
	return m;
}
function bounds() {
	let lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
	for (const b of bones) for (const c of b.cubes) {
		const m = worldOf(b);
		for (let i = 0; i < 8; i++) {
			const v = apply(m, [c.origin[0] + (i & 1 ? c.size[0] : 0), c.origin[1] + (i & 2 ? c.size[1] : 0), c.origin[2] + (i & 4 ? c.size[2] : 0)]);
			for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], v[k]); hi[k] = Math.max(hi[k], v[k]); }
		}
	}
	return { lo, hi };
}
{
	const { lo } = bounds();
	const dy = -lo[1];
	for (const b of bones) {
		if (b.name === 'root') continue;
		b.pivot[1] += dy;
		for (const c of b.cubes) c.origin[1] += dy;
	}
}
const B = bounds();
console.log('queen bounds (model units, 8 = 1 block in game):', B.lo.map(v => v.toFixed(1)), B.hi.map(v => v.toFixed(1)));
console.log(`  in game: ${((B.hi[1] - B.lo[1]) / 8).toFixed(2)} tall, ${((B.hi[0] - B.lo[0]) / 8).toFixed(2)} wide, ${((B.hi[2] - B.lo[2]) / 8).toFixed(2)} long`);

// ---- box-UV packing (shelf packer)
const TEX_W = 256;
const allCubes = [];
for (const b of bones) for (const c of b.cubes) allCubes.push(c);
// identical sizes+materials share one region (the legs: one paint job for all eight)
const regions = new Map();
const order = [...allCubes].sort((a, b) => (b.size[2] + b.size[1]) - (a.size[2] + a.size[1]));
let sx0 = 0, sy0 = 0, shelfH = 0;
for (const c of order) {
	const key = c.size.join('x') + c.mat;
	if (regions.has(key)) { c.uv = regions.get(key).uv; continue; }
	const [w, h, d] = c.size;
	const rw = Math.ceil(2 * (w + d)), rh = Math.ceil(d + h);
	if (sx0 + rw > TEX_W) { sx0 = 0; sy0 += shelfH; shelfH = 0; }
	const r = { uv: [sx0, sy0], w, h, d, mat: c.mat };
	regions.set(key, r);
	c.uv = r.uv;
	sx0 += rw;
	shelfH = Math.max(shelfH, rh);
}
const TEX_H = Math.max(64, Math.ceil((sy0 + shelfH) / 16) * 16);
console.log(`queen texture ${TEX_W}x${TEX_H}, ${regions.size} regions for ${allCubes.length} cubes`);

const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.brood_queen',
			texture_width: TEX_W, texture_height: TEX_H,
			visible_bounds_width: 10, visible_bounds_height: 7, visible_bounds_offset: [0, 2.5, 0],
		},
		bones: bones.map(b => {
			const o = { name: b.name };
			if (b.parent) o.parent = b.parent;
			o.pivot = b.pivot.map(v => +v.toFixed(4));
			if (b.rotation) o.rotation = b.rotation;
			if (b.cubes.length) o.cubes = b.cubes.map(c => ({ origin: c.origin.map(v => +v.toFixed(4)), size: c.size, uv: c.uv }));
			return o;
		}),
	}],
};
json(path.join(A, 'geo/brood_queen.geo.json'), geo);

// ---- texture
let seed = 90210;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const tex = Buffer.alloc(TEX_W * TEX_H * 4);
const glow = Buffer.alloc(TEX_W * TEX_H * 4);
const put = (buf, x, y, c, a = 255) => { if (x < 0 || y < 0 || x >= TEX_W || y >= TEX_H) return; const i = (y * TEX_W + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = a; };
const PAL = {
	chitin: [[34, 16, 26], [12, 6, 10], [120, 18, 30]],
	chitin_dark: [[20, 10, 16], [8, 4, 8], [70, 12, 20]],
	plate: [[58, 20, 32], [22, 8, 14], [170, 30, 40]],
	spike: [[60, 22, 34], [20, 8, 14], [200, 40, 50]],
	leg: [[28, 14, 22], [8, 4, 8], [150, 20, 32]],
	leg_tip: [[90, 16, 26], [30, 8, 12], [210, 40, 50]],
	claw: [[16, 10, 12], [4, 2, 4], [60, 20, 20]],
	abdomen: [[118, 18, 36], [44, 8, 16], [196, 34, 52]],
	abdomen_top: [[128, 20, 40], [48, 8, 18], [210, 40, 58]],
	abdomen_dark: [[30, 8, 14], [12, 4, 8], [70, 12, 20]],
	spinneret: [[90, 80, 84], [40, 34, 38], [140, 130, 130]],
	egg: [[196, 204, 150], [120, 130, 80], [235, 240, 200]],
	fang: [[214, 204, 176], [60, 50, 40], [240, 236, 220]],
	venom: [[120, 255, 80], [40, 160, 30], [200, 255, 160]],
	eye: [[255, 36, 30], [150, 0, 0], [255, 190, 160]],
};
for (const r of regions.values()) {
	const [u, v] = r.uv;
	const { w, h, d, mat } = r;
	const [base, dark, hi] = PAL[mat];
	const rw = 2 * (w + d), rh = d + h;
	for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
		// skip the two unused corners of a box-UV layout
		if (y < d && (x < d || x >= d + 2 * w)) continue;
		let c = mix(base, dark, rnd() * 0.4);
		if (rnd() < 0.06) c = mix(c, hi, 0.5);
		const onTop = y < d;
		const faceY = onTop ? -1 : y - d; // 0 at the top edge of a side face
		if (mat === 'leg') {
			// red bands at both joints of every segment, segment runs along x on the side faces
			const lx = x < d ? x : x < d + w ? x - d : x < 2 * d + w ? x - d - w : x - 2 * d - w;
			const len = (x < d || (x >= d + w && x < 2 * d + w)) ? d : w;
			if (len === w && (lx < 2 || lx >= w - 2)) c = mix(hi, c, 0.25);
			if (!onTop && faceY === h - 1) c = mix(c, dark, 0.5);
		}
		if (mat === 'abdomen' || mat === 'abdomen_top') {
			// black chevrons down the back
			if (!onTop && ((faceY + Math.floor(x / 3)) % 7 === 0)) c = mix(c, dark, 0.8);
			if (onTop && ((y + Math.abs((x % (w + d)) - (d + w / 2)) / 2) | 0) % 6 === 0) c = mix(c, dark, 0.7);
		}
		if (mat === 'egg' && rnd() < 0.12) c = mix(c, [150, 200, 90], 0.7);
		if (mat === 'eye') {
			c = (x + y) % 3 === 0 ? hi : base;
			put(glow, u + x, v + y, c);
		}
		if (mat === 'venom') put(glow, u + x, v + y, c);
		if (mat === 'egg' && ((x * 7 + y * 13) % 11 === 0)) {
			const g = [170, 255, 110];
			c = g;
			put(glow, u + x, v + y, g);
		}
		if (mat === 'spike' && !onTop && faceY < 2) { c = hi; }
		put(tex, u + x, v + y, c);
	}
	// the hourglass on the top of the abdomen (the up face of the biggest cube)
	if (mat === 'abdomen_top') {
		for (let yy = 0; yy < d; yy++) {
			const t = Math.abs(yy - d / 2) / (d / 2);
			const half = Math.round(1 + t * (w / 4));
			for (let xx = -half; xx <= half; xx++) {
				const px = u + d + Math.round(w / 2) + xx, py = v + yy;
				put(tex, px, py, [220, 24, 30]);
				put(glow, px, py, [255, 40, 40], 200);
			}
		}
	}
}
fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
fs.writeFileSync(path.join(A, 'textures/entity/brood_queen.png'), encode(TEX_W, TEX_H, tex));
fs.writeFileSync(path.join(A, 'textures/entity/brood_queen_glowmask.png'), encode(TEX_W, TEX_H, glow));

// ------------------------------------------------------------------ animations
const r2 = v => Math.round(v * 1000) / 1000;
const key = t => r2(t).toFixed(2);
/** tracks: {bone: {rotation: [[t,[x,y,z]],...], position: ..., scale: ...}} */
function clip(length, loop, tracks) {
	const out = { animation_length: r2(length) };
	if (loop) out.loop = true;
	out.bones = {};
	for (const [b, chans] of Object.entries(tracks)) {
		if (!boneByName[b]) throw new Error('animation names a missing bone: ' + b);
		out.bones[b] = {};
		for (const [ch, frames] of Object.entries(chans)) {
			const o = {};
			for (const [t, v] of frames) o[key(t)] = v.map(r2);
			out.bones[b][ch] = o;
		}
	}
	return out;
}
// per-leg helpers: deltas on top of the rest pose
const legs = () => legNames;
const Z3 = [0, 0, 0];
const swingRot = (sx, deg) => [0, sx * deg, 0];          // + = forward
const liftRot = (sx, deg) => [0, 0, -sx * deg];          // + = raise
const curlRot = (sx, deg) => [0, 0, sx * deg];           // + = bend the knee further down/in
function add(tracks, b, ch, frames) {
	tracks[b] = tracks[b] || {};
	tracks[b][ch] = frames;
}
const anims = {};

// idle: breathing abdomen, pulsing eggs, twitching palps and fangs
{
	const t = {};
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [2, [1.035, 1.04, 1.035]], [4, [1, 1, 1]]]);
	add(t, 'eggs', 'scale', [[0, [1, 1, 1]], [1, [1.08, 1.08, 1.08]], [2.2, [0.98, 0.98, 0.98]], [3.2, [1.06, 1.06, 1.06]], [4, [1, 1, 1]]]);
	add(t, 'head', 'rotation', [[0, Z3], [1.5, [-3, 5, 0]], [3, [2, -4, 0]], [4, Z3]]);
	add(t, 'palp_left', 'rotation', [[0, Z3], [0.6, [-10, 0, 0]], [1.2, Z3], [2.6, [-6, 4, 0]], [4, Z3]]);
	add(t, 'palp_right', 'rotation', [[0, Z3], [1.0, [-8, 0, 0]], [1.8, Z3], [3.0, [-10, -4, 0]], [4, Z3]]);
	add(t, 'fang_left', 'rotation', [[0, Z3], [2, [0, 0, 6]], [4, Z3]]);
	add(t, 'fang_right', 'rotation', [[0, Z3], [2, [0, 0, -6]], [4, Z3]]);
	add(t, 'body', 'position', [[0, Z3], [2, [0, 0.4, 0]], [4, Z3]]);
	for (const { n, sx, i } of legs()) {
		const ph = (i % 2) * 2;
		add(t, `${n}_tibia`, 'rotation', [[0, Z3], [ph === 0 ? 2 : 1, curlRot(sx, 3)], [4, Z3]]);
	}
	anims.idle = clip(4, true, t);
}

// walk: an alternating tetrapod gait -- l1 r2 l3 r4 step together, then r1 l2 r3 l4
function gait(len, stride, lift) {
	const t = {};
	const steps = 8;
	for (const { n, sx, i } of legs()) {
		const groupA = (n.startsWith('leg_l') && i % 2 === 0) || (n.startsWith('leg_r') && i % 2 === 1);
		const off = groupA ? 0 : 0.5;
		const hip = [], fem = [], tib = [];
		for (let k = 0; k <= steps; k++) {
			const tt = k / steps;
			const p = (tt + off) % 1;
			let fwd, up;
			if (p < 0.5) { const q = p / 0.5; fwd = -stride + 2 * stride * (0.5 - 0.5 * Math.cos(Math.PI * q)); up = lift * Math.sin(Math.PI * q); }
			else { const q = (p - 0.5) / 0.5; fwd = stride - 2 * stride * q; up = 0; }
			hip.push([tt * len, swingRot(sx, fwd)]);
			fem.push([tt * len, liftRot(sx, up)]);
			tib.push([tt * len, curlRot(sx, -up * 0.6)]);
		}
		add(t, n, 'rotation', hip);
		add(t, `${n}_femur`, 'rotation', fem);
		add(t, `${n}_tibia`, 'rotation', tib);
	}
	add(t, 'body', 'position', [[0, Z3], [len / 4, [0, 0.6, 0]], [len / 2, Z3], [3 * len / 4, [0, 0.6, 0]], [len, Z3]]);
	add(t, 'abdomen', 'rotation', [[0, [0, 3, 0]], [len / 2, [0, -3, 0]], [len, [0, 3, 0]]]);
	add(t, 'palp_left', 'rotation', [[0, [-6, 0, 0]], [len / 2, [4, 0, 0]], [len, [-6, 0, 0]]]);
	add(t, 'palp_right', 'rotation', [[0, [4, 0, 0]], [len / 2, [-6, 0, 0]], [len, [4, 0, 0]]]);
	return t;
}
anims.walk = clip(1.0, true, gait(1.0, 14, 24));

// helpers for the one-shot clips: every leg / the front pair / everything but the front pair
const allLegs = (t, f) => legs().forEach(l => f(t, l));
function legKeys(t, filter, keys) {
	// keys: [[time, swing, lift, curl], ...]
	for (const l of legs()) {
		if (!filter(l)) continue;
		add(t, l.n, 'rotation', keys.map(([tm, s]) => [tm, swingRot(l.sx, s)]));
		add(t, `${l.n}_femur`, 'rotation', keys.map(([tm, , li]) => [tm, liftRot(l.sx, li)]));
		add(t, `${l.n}_tibia`, 'rotation', keys.map(([tm, , , cu]) => [tm, curlRot(l.sx, cu)]));
	}
}
const FRONT = l => l.i === 0;
const FRONT2 = l => l.i <= 1;
const REST_OF = l => l.i > 0;
const ANY = () => true;
const fangs = (t, keys) => { // keys: [[time, spread]] -- + = fangs apart
	add(t, 'fang_left', 'rotation', keys.map(([tm, s]) => [tm, [0, 0, s]]));
	add(t, 'fang_right', 'rotation', keys.map(([tm, s]) => [tm, [0, 0, -s]]));
};

// fang_lunge (32 ticks): rear up, fangs wide (0-0.7), snap down and forward (0.7-0.9), recover
function lunge(len, wind, strike) {
	const t = {};
	add(t, 'body', 'rotation', [[0, Z3], [wind, [-18, 0, 0]], [strike, [14, 0, 0]], [strike + 0.15, [10, 0, 0]], [len, Z3]]);
	add(t, 'body', 'position', [[0, Z3], [wind, [0, 2, 0]], [strike, [0, -1.5, 0]], [len, Z3]]);
	add(t, 'head', 'rotation', [[0, Z3], [wind, [-10, 0, 0]], [strike, [12, 0, 0]], [len, Z3]]);
	fangs(t, [[0, 0], [wind, 30], [strike, -12], [strike + 0.2, -8], [len, 0]]);
	legKeys(t, FRONT, [[0, 0, 0, 0], [wind, 18, 45, -20], [strike, 22, -5, 10], [len, 0, 0, 0]]);
	legKeys(t, REST_OF, [[0, 0, 0, 0], [wind, -4, -6, 6], [strike, 6, 0, -4], [len, 0, 0, 0]]);
	return t;
}
anims.fang_lunge = clip(1.6, false, lunge(1.6, 0.7, 0.9));
anims.frenzy_lunge = clip(0.7, false, lunge(0.7, 0.35, 0.5));

// leg_sweep (36 ticks): twist and crouch (0-0.8), spin the whole body round (0.8-1.1), settle
{
	const t = {};
	add(t, 'root', 'rotation', [[0, Z3], [0.8, [0, 40, 0]], [0.95, [0, -150, 0]], [1.1, [0, -320, 0]], [1.4, [0, -355, 0]], [1.8, [0, -360, 0]]]);
	add(t, 'body', 'position', [[0, Z3], [0.8, [0, -3, 0]], [1.1, [0, 1, 0]], [1.8, Z3]]);
	legKeys(t, ANY, [[0, 0, 0, 0], [0.8, 0, -10, 15], [0.95, 0, 25, -45], [1.1, 0, 25, -45], [1.4, 0, 5, -5], [1.8, 0, 0, 0]]);
	fangs(t, [[0, 0], [0.8, 20], [1.1, 25], [1.8, 0]]);
	anims.leg_sweep = clip(1.8, false, t);
}

// venom_spray (52 ticks): head up, fangs wide (0-0.8); spray sweeping side to side (0.8-2.0); settle
{
	const t = {};
	const sweep = [[0, Z3], [0.8, [-20, 0, 0]]];
	for (let k = 1; k <= 8; k++) sweep.push([0.8 + k * 0.15, [-14, [25, 0, -25, 0][(k - 1) % 4], 0]]);
	sweep.push([2.6, Z3]);
	add(t, 'head', 'rotation', sweep);
	add(t, 'body', 'rotation', [[0, Z3], [0.8, [-10, 0, 0]], [2.0, [-10, 0, 0]], [2.6, Z3]]);
	fangs(t, [[0, 0], [0.8, 35], [1.0, 28], [1.2, 35], [1.4, 28], [1.6, 35], [1.8, 28], [2.0, 35], [2.6, 0]]);
	legKeys(t, FRONT, [[0, 0, 0, 0], [0.8, 10, 20, -10], [2.0, 10, 20, -10], [2.6, 0, 0, 0]]);
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [0.8, [1.08, 1.06, 1.08]], [2.0, [0.96, 0.96, 0.96]], [2.6, [1, 1, 1]]]);
	anims.venom_spray = clip(2.6, false, t);
}

// web_volley (44 ticks): abdomen swings up over her back (0-0.7), three pumps (0.7, 1.0, 1.3), lower
{
	const t = {};
	add(t, 'abdomen', 'rotation', [[0, Z3], [0.7, [38, 0, 0]], [1.5, [38, 0, 0]], [2.2, Z3]]);
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [0.65, [1, 1, 1]], [0.7, [1.1, 1.1, 1.1]], [0.85, [1, 1, 1]], [1.0, [1.1, 1.1, 1.1]],
		[1.15, [1, 1, 1]], [1.3, [1.1, 1.1, 1.1]], [1.45, [1, 1, 1]], [2.2, [1, 1, 1]]]);
	add(t, 'body', 'rotation', [[0, Z3], [0.7, [8, 0, 0]], [1.5, [8, 0, 0]], [2.2, Z3]]);
	legKeys(t, l => l.i === 3, [[0, 0, 0, 0], [0.7, -10, -10, 10], [1.5, -10, -10, 10], [2.2, 0, 0, 0]]);
	anims.web_volley = clip(2.2, false, t);
}

// egg_burst (44 ticks): the abdomen and its sacs swell (0-1.2), burst (1.2-1.3), the sacs grow back
{
	const t = {};
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [0.6, [1.12, 1.12, 1.12]], [0.9, [1.18, 1.2, 1.18]], [1.0, [1.15, 1.17, 1.15]], [1.2, [1.27, 1.27, 1.27]],
		[1.3, [0.92, 0.92, 0.92]], [1.6, [1, 1, 1]], [2.2, [1, 1, 1]]]);
	add(t, 'eggs', 'scale', [[0, [1, 1, 1]], [1.2, [1.4, 1.4, 1.4]], [1.3, [0.2, 0.2, 0.2]], [1.5, [0.2, 0.2, 0.2]], [2.2, [1, 1, 1]]]);
	add(t, 'abdomen', 'rotation', [[0, Z3], [1.2, [16, 0, 0]], [1.3, [-6, 0, 0]], [2.2, Z3]]);
	add(t, 'body', 'position', [[0, Z3], [1.2, [0, -2.5, 0]], [1.3, [0, 1, 0]], [2.2, Z3]]);
	legKeys(t, ANY, [[0, 0, 0, 0], [1.2, 0, -12, 14], [1.3, 0, 10, -8], [2.2, 0, 0, 0]]);
	anims.egg_burst = clip(2.2, false, t);
}

// leap: windup crouch (16 ticks), airborne loop, landing squash (14 ticks)
{
	const t = {};
	add(t, 'body', 'position', [[0, Z3], [0.6, [0, -4, 0]], [0.8, [0, -4.5, 0]]]);
	add(t, 'body', 'rotation', [[0, Z3], [0.8, [-6, 0, 0]]]);
	legKeys(t, ANY, [[0, 0, 0, 0], [0.6, 0, -18, 24], [0.8, 0, -20, 26]]);
	fangs(t, [[0, 0], [0.8, 18]]);
	anims.leap_windup = clip(0.8, false, t);
}
{
	const t = {};
	legKeys(t, FRONT2, [[0, 20, 30, -40], [0.25, 24, 34, -44], [0.5, 20, 30, -40]]);
	legKeys(t, l => l.i > 1, [[0, -20, 10, -30], [0.25, -24, 14, -34], [0.5, -20, 10, -30]]);
	add(t, 'body', 'rotation', [[0, [-12, 0, 0]], [0.5, [-12, 0, 0]]]);
	fangs(t, [[0, 30], [0.5, 30]]);
	anims.leap_air = clip(0.5, true, t);
}
{
	const t = {};
	add(t, 'body', 'position', [[0, [0, -5, 0]], [0.15, [0, -3, 0]], [0.7, Z3]]);
	add(t, 'body', 'rotation', [[0, [6, 0, 0]], [0.7, Z3]]);
	legKeys(t, ANY, [[0, 0, -15, 25], [0.15, 0, -8, 12], [0.7, 0, 0, 0]]);
	add(t, 'abdomen', 'scale', [[0, [1.1, 0.9, 1.1]], [0.2, [1, 1, 1]]]);
	fangs(t, [[0, 10], [0.7, 0]]);
	anims.slam = clip(0.7, false, t);
}

// web ascent: rear up to the sky (14 ticks), gathered on the line (loop), dropping (loop)
{
	const t = {};
	add(t, 'body', 'rotation', [[0, Z3], [0.5, [-40, 0, 0]], [0.7, [-45, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z3], [0.7, [-20, 0, 0]]]);
	legKeys(t, FRONT2, [[0, 0, 0, 0], [0.7, 30, 50, -30]]);
	legKeys(t, l => l.i > 1, [[0, 0, 0, 0], [0.7, -10, -10, 10]]);
	add(t, 'abdomen', 'rotation', [[0, Z3], [0.7, [-20, 0, 0]]]);
	anims.ascend = clip(0.7, false, t);
}
{
	const t = {};
	add(t, 'root', 'rotation', [[0, [0, 0, -4]], [0.6, [0, 0, 4]], [1.2, [0, 0, -4]]]);
	legKeys(t, ANY, [[0, 0, 50, 40], [0.6, 0, 54, 44], [1.2, 0, 50, 40]]);
	add(t, 'abdomen', 'rotation', [[0, [10, 0, 0]], [1.2, [10, 0, 0]]]);
	anims.hang = clip(1.2, true, t);
}
{
	const t = {};
	legKeys(t, ANY, [[0, 0, -25, -30], [0.2, 0, -28, -34], [0.4, 0, -25, -30]]);
	add(t, 'body', 'rotation', [[0, [10, 0, 0]], [0.4, [10, 0, 0]]]);
	fangs(t, [[0, 35], [0.4, 35]]);
	anims.drop = clip(0.4, true, t);
}

// acid_rain (38 ticks): abdomen up and pumping (0-0.9), a big thrust at 0.9, lower
{
	const t = {};
	add(t, 'abdomen', 'rotation', [[0, Z3], [0.4, [45, 0, 0]], [0.85, [45, 0, 0]], [0.95, [58, 0, 0]], [1.1, [35, 0, 0]], [1.9, Z3]]);
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [0.4, [1.1, 1.1, 1.1]], [0.5, [1, 1, 1]], [0.6, [1.12, 1.12, 1.12]], [0.7, [1, 1, 1]],
		[0.85, [1.16, 1.16, 1.16]], [0.95, [0.94, 0.94, 0.94]], [1.3, [1, 1, 1]], [1.9, [1, 1, 1]]]);
	add(t, 'head', 'rotation', [[0, Z3], [0.4, [12, 0, 0]], [1.3, [12, 0, 0]], [1.9, Z3]]);
	add(t, 'eggs', 'scale', [[0, [1, 1, 1]], [0.85, [1.15, 1.15, 1.15]], [0.95, [0.9, 0.9, 0.9]], [1.9, [1, 1, 1]]]);
	anims.acid_rain = clip(1.9, false, t);
}

// shriek (50 ticks): rear up high, front legs raised and trembling, fangs wide, head thrown back
{
	const t = {};
	add(t, 'body', 'rotation', [[0, Z3], [0.6, [-35, 0, 0]], [2.0, [-35, 0, 0]], [2.5, Z3]]);
	add(t, 'body', 'position', [[0, Z3], [0.6, [0, 3, 0]], [2.0, [0, 3, 0]], [2.5, Z3]]);
	const head = [[0, Z3], [0.6, [-25, 0, 0]]];
	for (let k = 1; k <= 13; k++) head.push([0.6 + k * 0.1, [-25 + (k % 2 ? -4 : 4), k % 2 ? 3 : -3, 0]]);
	head.push([2.5, Z3]);
	add(t, 'head', 'rotation', head);
	const frontKeys = [[0, 0, 0, 0], [0.6, 25, 60, -40]];
	for (let k = 1; k <= 13; k++) frontKeys.push([0.6 + k * 0.1, 25, 60 + (k % 2 ? 6 : -6), -40]);
	frontKeys.push([2.5, 0, 0, 0]);
	legKeys(t, FRONT2, frontKeys);
	legKeys(t, l => l.i > 1, [[0, 0, 0, 0], [0.6, -8, -12, 14], [2.0, -8, -12, 14], [2.5, 0, 0, 0]]);
	fangs(t, [[0, 0], [0.6, 40], [2.0, 40], [2.5, 0]]);
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [0.6, [1.08, 1.08, 1.08]], [2.0, [1.08, 1.08, 1.08]], [2.5, [1, 1, 1]]]);
	anims.shriek = clip(2.5, false, t);
}

// death (60 ticks): the legs curl up under her, the body sinks, the abdomen deflates; held
{
	const t = {};
	add(t, 'body', 'position', [[0, Z3], [0.4, [0, 2, 0]], [1.4, [0, -5, 0]], [3.0, [0, -6, 0]]]);
	add(t, 'body', 'rotation', [[0, Z3], [0.4, [-15, 0, 0]], [1.4, [6, 0, 8]], [3.0, [8, 0, 10]]]);
	legKeys(t, ANY, [[0, 0, 0, 0], [0.4, 0, 30, -20], [1.4, 0, 55, 70], [3.0, 0, 60, 80]]);
	add(t, 'abdomen', 'scale', [[0, [1, 1, 1]], [1.4, [0.92, 0.85, 0.92]], [3.0, [0.88, 0.78, 0.88]]]);
	add(t, 'eggs', 'scale', [[0, [1, 1, 1]], [3.0, [0.6, 0.6, 0.6]]]);
	fangs(t, [[0, 0], [0.4, 40], [1.4, -10], [3.0, -10]]);
	add(t, 'head', 'rotation', [[0, Z3], [0.4, [-30, 0, 0]], [1.4, [15, 0, 0]], [3.0, [18, 0, 0]]]);
	anims.death = clip(3.0, false, t);
}

// intro (40 ticks): curled up, then unfolds and rises
{
	const t = {};
	add(t, 'body', 'position', [[0, [0, -6, 0]], [1.0, [0, -4, 0]], [1.6, [0, 1, 0]], [2.0, Z3]]);
	legKeys(t, ANY, [[0, 0, 55, 70], [1.0, 0, 40, 40], [1.6, 0, -5, 5], [2.0, 0, 0, 0]]);
	add(t, 'head', 'rotation', [[0, [20, 0, 0]], [1.6, [-25, 0, 0]], [2.0, Z3]]);
	fangs(t, [[0, -10], [1.6, 35], [2.0, 0]]);
	anims.intro = clip(2.0, false, t);
}

const animFile = { format_version: '1.8.0', animations: {} };
for (const [n, a] of Object.entries(anims)) animFile.animations['animation.brood_queen.' + n] = a;
json(path.join(A, 'animations/brood_queen.animation.json'), animFile);
console.log('queen clips:', Object.keys(anims).join(', '));

// ------------------------------------------------------------------ brood spider variants (vanilla spider recoloured)
// Each palette maps the vanilla body's luminance onto [shadow, mid, highlight]; the red eyes are kept (or recoloured).
const VARIANTS = {
	hunter: { pal: [[40, 8, 4], [150, 50, 20], [235, 120, 50]], eye: [255, 220, 60] },        // ember-orange, fast
	brute: { pal: [[24, 26, 30], [88, 92, 100], [170, 176, 186]], eye: [255, 60, 40], plates: true }, // iron-grey armour plates
	venom: { pal: [[10, 30, 10], [50, 120, 40], [150, 230, 90]], eye: [210, 255, 80] },      // venom green
	burster: { pal: [[60, 60, 10], [170, 170, 40], [240, 240, 120]], eye: [120, 255, 60], blotch: true }, // bloated acid yellow
	leaper: { pal: [[30, 14, 40], [96, 50, 120], [180, 120, 210]], eye: [255, 80, 200] },    // trapdoor purple
	broodmother: { pal: [[34, 10, 22], [110, 30, 60], [200, 140, 160]], eye: [255, 40, 40], eggs: true }, // with egg spots
	stalker: { pal: [[4, 4, 8], [26, 26, 34], [70, 70, 90]], eye: [180, 220, 255] },         // near-black
	spiderling: { pal: [[120, 110, 100], [190, 180, 160], [245, 240, 225]], eye: [255, 40, 40] }, // pale newborn
};
const src = decode(path.join(VANILLA, 'assets/minecraft/textures/entity/spider/spider.png'));
fs.mkdirSync(path.join(A, 'textures/entity/brood_spider'), { recursive: true });
for (const [name, v] of Object.entries(VARIANTS)) {
	seed = name.length * 4099 + name.charCodeAt(0);
	const px = Buffer.from(src.px);
	for (let i = 0; i < src.w * src.h; i++) {
		const o = i * 4;
		if (!px[o + 3]) continue;
		const r = px[o], g = px[o + 1], b = px[o + 2];
		const x = i % src.w, y = Math.floor(i / src.w);
		if (r > 150 && g < 80) { // the eyes
			px[o] = v.eye[0]; px[o + 1] = v.eye[1]; px[o + 2] = v.eye[2];
			continue;
		}
		const lum = Math.min(1, ((r + g + b) / 3) / 110);
		let c = lum < 0.5 ? mix(v.pal[0], v.pal[1], lum * 2) : mix(v.pal[1], v.pal[2], (lum - 0.5) * 2);
		// the abdomen of the vanilla texture sits at (0..48, 12..24)
		const onAbdomen = y >= 12 && x < 48;
		if (v.plates && onAbdomen && (x % 6 === 0 || y % 4 === 0)) c = mix(c, [210, 214, 222], 0.55);
		if (v.blotch && onAbdomen && rnd() < 0.25) c = mix(c, [200, 255, 80], 0.6);
		if (v.eggs && onAbdomen && ((x * 5 + y * 3) % 9 === 0)) c = [228, 220, 190];
		px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2];
	}
	fs.writeFileSync(path.join(A, `textures/entity/brood_spider/${name}.png`), encode(src.w, src.h, px));
}
console.log('brood spider textures:', Object.keys(VARIANTS).join(', '));
