// v0.17.5 Symbiote Monster form (Shift+Z ultimate): the hulking, hunchbacked monster's GeckoLib model, its tint mask + keep
// texture and its animations. All original art, painted in body space. Re-runnable:
//   node scratchpad/symbiote/gen_symbiote_monster_v0175.js [--preview <dir>]
// Writes
//   geo/symbiote_monster.geo.json
//   animations/symbiote_monster.animation.json
//   textures/entity/symbiote_monster_tint.png   tint mask (R shade, G gloss, B channel 0 body / 32 mouth / 128 emblem / 255 eyes, A 255)
//   textures/entity/symbiote_monster_keep.png   texels drawn as painted on every colour (teeth): A 255 = keep, A 0 = use the mask
//   textures/entity/symbiote_monster.png        the Classic bake (fallback / icons / the preview)
// --preview <dir>: also renders a software turntable (front, 3/4, side, back, 3/4 back) + a face close-up, and Carnage colours,
// so the silhouette can be checked without a game client.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const args = process.argv.slice(2);
const PREVIEW = args.indexOf('--preview') >= 0 ? args[args.indexOf('--preview') + 1] : null;

// ------------------------------------------------------------------------------------------------ noise
function hash3(x, y, z) {
	let h = (x * 374761393 + y * 668265263 + z * 1442695041) | 0;
	h = Math.imul(h ^ (h >>> 13), 1274126177);
	return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function vnoise3(x, y, z) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
	const fx = x - xi, fy = y - yi, fz = z - zi;
	const s = (t) => t * t * (3 - 2 * t);
	const ux = s(fx), uy = s(fy), uz = s(fz);
	let r = 0;
	for (let dz = 0; dz < 2; dz++) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
		const w = (dx ? ux : 1 - ux) * (dy ? uy : 1 - uy) * (dz ? uz : 1 - uz);
		r += w * hash3(xi + dx, yi + dy, zi + dz);
	}
	return r;
}
function fbm3(x, y, z) {
	return vnoise3(x, y, z) * 0.55 + vnoise3(x * 2.03, y * 2.03, z * 2.03) * 0.28 + vnoise3(x * 4.1, y * 4.1, z * 4.1) * 0.17;
}
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const lerp = (a, b, t) => a + (b - a) * t;

// ------------------------------------------------------------------------------------------------ bedrock transforms
// Bedrock (Blockbench) space, front = -z, the model's right = -x, y up, px. GeckoLib bakes it mirrored in x with x / y rotations
// negated, so in bedrock terms (worked out from GeckoLib's rotateMatrixAroundBone, Rz * Ry * Rx, Rx applied first):
//   Rx(t): (y, z) -> (y cos + z sin, -y sin + z cos)   (+X tips an upright part forward, -X swings a hanging limb forward)
//   Ry(t): (x, z) -> (x cos + z sin, -x sin + z cos)
//   Rz(t): (x, y) -> (x cos + y sin, -x sin + y cos)   (+Z swings a hanging right arm (at -x) outward)
const D2R = Math.PI / 180;
function rot(p, r) {
	let [x, y, z] = p;
	if (r[0]) { const c = Math.cos(r[0] * D2R), s = Math.sin(r[0] * D2R); [y, z] = [y * c + z * s, -y * s + z * c]; }
	if (r[1]) { const c = Math.cos(r[1] * D2R), s = Math.sin(r[1] * D2R); [x, z] = [x * c + z * s, -x * s + z * c]; }
	if (r[2]) { const c = Math.cos(r[2] * D2R), s = Math.sin(r[2] * D2R); [x, y] = [x * c + y * s, -x * s + y * c]; }
	return [x, y, z];
}

// ------------------------------------------------------------------------------------------------ the model
const bones = [];
const B = {};
function bone(name, parent, pivot, rotation) {
	const b = { name, parent, pivot, rotation: rotation || [0, 0, 0], cubes: [] };
	bones.push(b);
	B[name] = b;
	return b;
}
function cube(b, from, size, tag, extra) {
	b.cubes.push(Object.assign({ from, size, tag: tag || 'skin' }, extra || {}));
}
/** The bone's point in model space for pose {bone name: [rx, ry, rz]} added to the rest rotation. */
function toModel(b, p, pose) {
	let q = p;
	for (let cur = b; cur; cur = cur.parent ? B[cur.parent] : null) {
		const add = pose && pose[cur.name] ? pose[cur.name] : [0, 0, 0];
		const r = [cur.rotation[0] + add[0], cur.rotation[1] + add[1], cur.rotation[2] + add[2]];
		const off = pose && pose['@' + cur.name] ? pose['@' + cur.name] : [0, 0, 0];
		const d = rot([q[0] - cur.pivot[0], q[1] - cur.pivot[1], q[2] - cur.pivot[2]], r);
		q = [d[0] + cur.pivot[0] + off[0], d[1] + cur.pivot[1] + off[1], d[2] + cur.pivot[2] + off[2]];
	}
	return q;
}

// --- the body: Titan-boss proportions (huge shoulders and forearms, knuckles near the ground, short thick legs, a big back hump)
bone('root', null, [0, 0, 0]);
bone('pelvis', 'root', [0, 38, 2]);
cube(B.pelvis, [-15, 29, -8], [30, 14, 20]);
cube(B.pelvis, [-12, 27, -6], [24, 3, 14]); // the groin underside

// legs: short, thick, slightly bowed
for (const side of [-1, 1]) {
	const s = side < 0 ? 'r' : 'l';
	const x = side * 10.5;
	bone('leg_' + s, 'pelvis', [x, 35, 1], [-8, 0, side * -4]);
	cube(B['leg_' + s], [x - 8.5, 17, -7.5], [17, 20, 17]);
	cube(B['leg_' + s], [x - 7, 22, -9.5], [14, 11, 3]); // the quad bulging forward
	bone('shin_' + s, 'leg_' + s, [x, 18.5, 1], [16, 0, 0]);
	cube(B['shin_' + s], [x - 6.5, 4, -5], [13, 16, 13]);
	cube(B['shin_' + s], [x - 6, 8, 7], [12, 10, 3.5]); // the calf
	bone('foot_' + s, 'shin_' + s, [x, 4.5, 1], [-8, 0, 0]);
	cube(B['foot_' + s], [x - 7.5, 0, -11], [15, 5, 17]);
	cube(B['foot_' + s], [x - 6, 4.5, -8], [12, 2, 12]);
	for (let k = -1; k <= 1; k++) {
		const tx = x + k * 4.6;
		bone('toe_' + s + (k + 1), 'foot_' + s, [tx, 2, -10.5], [0, 0, 0]);
		cube(B['toe_' + s + (k + 1)], [tx - 1.6, 0.2, -15], [3.2, 3.4, 5], 'skin');
		cube(B['toe_' + s + (k + 1)], [tx - 1.1, 0, -18.4], [2.2, 2.2, 3.6], 'claw');
	}
	cube(B['foot_' + s], [x - 1.2, 0.5, 6], [2.4, 2.2, 3], 'claw'); // heel spur
}

// spine: belly -> chest, leaning forward (the hunch)
bone('belly', 'pelvis', [0, 42, 2], [14, 0, 0]);
cube(B.belly, [-14, 41, -9], [28, 16, 21]);
cube(B.belly, [-10, 43, -11], [20, 12, 2.5], 'abs');
bone('chest', 'belly', [0, 55, 2], [24, 0, 0]);
cube(B.chest, [-21, 53, -12], [42, 26, 28]);               // ribcage
cube(B.chest, [-18, 63, -15.5], [36, 14, 4], 'pecs');      // raised pecs
cube(B.chest, [-17, 72, -2], [34, 18, 23]);                // THE HUMP (back, above the shoulders)
cube(B.chest, [-12, 88, 2], [24, 6, 16]);                  // hump crown
cube(B.chest, [-13, 76, -9], [26, 6, 9]);                  // traps into the neck
cube(B.chest, [-15, 56, 13], [30, 18, 4]);                 // lats / lower back bulk
// spines along the hump
for (let i = 0; i < 5; i++) {
	const z = 4 + i * 3.4, y = 94 - Math.abs(i - 1.5) * 1.6 - i * 0.6;
	bone('spine_' + i, 'chest', [0, y, z], [-30 - i * 8, 0, 0]);
	cube(B['spine_' + i], [-1.6, y, z - 1.6], [3.2, 3.6 - i * 0.25, 3.2], 'spine');
	cube(B['spine_' + i], [-0.9, y + 3.2, z - 0.9], [1.8, 3.4 - i * 0.3, 1.8], 'claw');
}
// back tendrils: three living strands off the hump (they writhe)
const TENDRILS = [['a', -9, 86, 14, [-100, 0, 38]], ['b', 9, 86, 14, [-100, 0, -38]], ['c', 0, 84, 20, [-118, 0, 0]]];
for (const [t, tx, ty, tz, r0] of TENDRILS) {
	let w = 3.4, y = ty, parent = 'chest';
	const len = [10, 9, 8, 7];
	for (let k = 0; k < 4; k++) {
		const name = 'tend_' + t + k;
		bone(name, parent, [tx, y, tz], k === 0 ? r0 : [-16, 0, 0]);
		cube(B[name], [tx - w / 2, y, tz - w / 2], [w, len[k], w], k === 3 ? 'claw' : 'skin');
		y += len[k];
		w *= 0.74;
		parent = name;
	}
}

// head: low and forward of the chest, below the hump; counter-rotated so the face looks ahead
bone('neck', 'chest', [0, 74, -2], [18, 0, 0]);          // tips the head down and forward, under the hump
bone('head', 'neck', [0, 79, -9], [-60, 0, 0]);
cube(B.head, [-11, 73, -30], [22, 17, 21], 'head');
cube(B.head, [-9, 89.5, -27], [18, 2.5, 15], 'head');     // skull dome
cube(B.head, [-12, 74, -21], [24, 9, 10], 'head');        // cheeks, wider at the back of the grin
// upper teeth: a full grin -- 12 across the front, 5 back along each side, the fangs longest
const upperFront = 12;
for (let i = 0; i < upperFront; i++) {
	const x = -10 + (20 / (upperFront - 1)) * i;
	const fang = (i === 2 || i === upperFront - 3);
	const len = fang ? 7.0 : 4.0 + 0.45 * Math.sin(i * 2.3);
	cube(B.head, [x - 0.85, 73 - len * 0.55, -29.6], [1.7, len * 0.55, 1.6], 'tooth', { tip: false });
	cube(B.head, [x - 0.5, 73 - len, -29.4], [1.0, len * 0.45, 1.2], 'tooth', { tip: true });
}
for (const side of [-1, 1]) for (let i = 0; i < 5; i++) {
	const z = -27 + i * 2.2, len = 3.0 - i * 0.25;
	const x = side * 10.4;
	cube(B.head, [x - 0.8, 73 - len * 0.55, z - 0.8], [1.6, len * 0.55, 1.6], 'tooth');
	cube(B.head, [x - 0.5, 73 - len, z - 0.5], [1.0, len * 0.45, 1.0], 'tooth', { tip: true });
}
// the jaw: wide, hinged far back, opens on +X
bone('jaw', 'head', [0, 76, -13], [22, 0, 0]);
cube(B.jaw, [-10.6, 68, -30.4], [21.2, 5.6, 18.5], 'jaw');
cube(B.jaw, [-11.6, 69, -20], [23.2, 5, 8], 'jaw');       // the jaw's back corners
cube(B.jaw, [-8, 66.4, -29.4], [16, 1.8, 11], 'jaw');     // chin
for (let i = 0; i < 11; i++) {
	const x = -9.4 + (18.8 / 10) * i;
	const fang = (i === 1 || i === 9);
	const len = fang ? 5.0 : 2.8 + 0.4 * Math.cos(i * 1.7);
	cube(B.jaw, [x - 0.8, 73.6, -30.0], [1.6, len * 0.55, 1.5], 'tooth');
	cube(B.jaw, [x - 0.45, 73.6 + len * 0.55, -29.8], [0.9, len * 0.45, 1.1], 'tooth', { tip: true });
}
for (const side of [-1, 1]) for (let i = 0; i < 4; i++) {
	const z = -27.6 + i * 2.3, len = 2.6 - i * 0.2, x = side * 9.9;
	cube(B.jaw, [x - 0.75, 73.6, z - 0.75], [1.5, len * 0.55, 1.5], 'tooth');
	cube(B.jaw, [x - 0.45, 73.6 + len * 0.55, z - 0.45], [0.9, len * 0.45, 0.9], 'tooth', { tip: true });
}
// the long tongue: a chain off the jaw floor, lolling out over the lower teeth and down the chin
{
	let parent = 'jaw', z = -15, w = 5.4;
	const segs = [[7, 0], [6, 10], [5.5, 34], [5, 28], [4.5, 22], [4, 18], [3.5, 14]];
	for (let k = 0; k < segs.length; k++) {
		const [len, r] = segs[k];
		const name = 'tongue_' + k;
		bone(name, parent, [0, 73.8, z], [r, 0, 0]);
		cube(B[name], [-w / 2, 73.0, z - len], [w, 1.7 - k * 0.08, len], 'tongue');
		z -= len;
		w *= 0.88;
		parent = name;
	}
}

// arms: massive deltoids, long arms, Popeye forearms, claws reaching near the ground
for (const side of [-1, 1]) {
	const s = side < 0 ? 'r' : 'l';
	const x = side * 27.5;
	bone('arm_' + s, 'chest', [side * 22, 74, 2], [-20, 0, side * -7]);
	cube(B['arm_' + s], [x - 9.5, 65, -10], [19, 18, 22]);                 // deltoid
	cube(B['arm_' + s], [x - 7.5, 47, -8], [15, 21, 16]);                  // upper arm
	cube(B['arm_' + s], [x - 6, 50, -10.5], [12, 13, 3]);                  // bicep
	bone('forearm_' + s, 'arm_' + s, [x, 49, 0], [-14, 0, 0]);
	cube(B['forearm_' + s], [x - 9.5, 22, -10], [19, 29, 20]);
	cube(B['forearm_' + s], [x - 10.5, 33, -8], [21, 15, 16]);            // the forearm swell
	// spurs on the outer forearm
	for (let k = 0; k < 3; k++) {
		bone('spur_' + s + k, 'forearm_' + s, [x + side * 10.5, 44 - k * 6, 4], [0, 0, side * (60 + k * 6)]);
		cube(B['spur_' + s + k], [x + side * 10.5 - 1.2, 44 - k * 6, 2.8], [2.4, 4.2 - k * 0.6, 2.4], 'claw');
	}
	bone('hand_' + s, 'forearm_' + s, [x, 23, -1], [-6, 0, 0]);
	cube(B['hand_' + s], [x - 9, 11, -9.5], [18, 12.5, 18]);
	cube(B['hand_' + s], [x - 8, 9.5, -8.5], [16, 2, 15]);                 // knuckle pad
	for (let k = 0; k < 4; k++) {
		const fx = x - side * (6.6 - k * 4.3);
		const name = 'finger_' + s + k;
		bone(name, 'hand_' + s, [fx, 11, -7], [-34 + (k - 1.5) * 3, 0, 0]);
		cube(B[name], [fx - 1.8, 3, -8.8], [3.6, 8.5, 3.6]);
		bone(name + 'c', name, [fx, 3.5, -7], [-30, 0, 0]);
		cube(B[name + 'c'], [fx - 1.3, -4.5, -8.3], [2.6, 8.5, 2.6], 'claw');
	}
	const tx = x + side * 9.2;
	bone('thumb_' + s, 'hand_' + s, [tx, 17, -5], [-30, 0, side * 25]);
	cube(B['thumb_' + s], [tx - 1.7, 9.5, -6.7], [3.4, 8, 3.4]);
	cube(B['thumb_' + s], [tx - 1.2, 3.5, -6.2], [2.4, 6.5, 2.4], 'claw');
}

// ------------------------------------------------------------------------------------------------ faces + atlas
const TS = 2; // texels per model px
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
function faceCorners(c, f) {
	const [x0, y0, z0] = c.from, x1 = x0 + c.size[0], y1 = y0 + c.size[1], z1 = z0 + c.size[2];
	switch (f) {
		case 'north': return [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]];
		case 'south': return [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]];
		case 'east': return [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]];
		case 'west': return [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]];
		case 'up': return [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]];
		default: return [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]];
	}
}
const NORMALS = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
function faceDims(c, f) {
	const [sx, sy, sz] = c.size;
	return f === 'north' || f === 'south' ? [sx, sy] : f === 'east' || f === 'west' ? [sz, sy] : [sx, sz];
}
const allFaces = [];
for (const b of bones) for (const c of b.cubes) {
	c.uv = {};
	for (const f of FACES) {
		const [w, h] = faceDims(c, f);
		const tw = Math.max(1, Math.round(w * TS)), th = Math.max(1, Math.round(h * TS));
		allFaces.push({ b, c, f, tw, th });
	}
}
const AW = 1024;
allFaces.sort((a, b) => b.th - a.th || b.tw - a.tw);
{
	let x = 0, y = 0, shelf = 0;
	for (const F of allFaces) {
		if (x + F.tw > AW) { x = 0; y += shelf; shelf = 0; }
		F.u = x; F.v = y;
		x += F.tw;
		shelf = Math.max(shelf, F.th);
		F.c.uv[F.f] = { uv: [F.u, F.v], uv_size: [F.tw, F.th] };
	}
	var AH = 1; while (AH < y + shelf) AH *= 2;
}

// ------------------------------------------------------------------------------------------------ painting
const MASK = new Float32Array(AW * AH * 4); // shade, gloss, channel, alpha (0..255)
const KEEP = new Uint8ClampedArray(AW * AH * 4);
const LIGHT = norm([-0.35, 0.85, -0.45]);
function norm(v) { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

// emblem: the spider, in chest space on the front (x, y), wrapping over the top and round the ribs
function segDist(px, py, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay;
	const t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1), 0, 1);
	return [Math.hypot(px - (ax + dx * t), py - (ay + dy * t)), t];
}
function polyDist(px, py, pts, w0, w1) { // tapered polyline: signed-ish distance (negative inside)
	let total = 0;
	const L = [];
	for (let i = 0; i < pts.length - 1; i++) { const l = Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]); L.push(l); total += l; }
	let best = 1e9, acc = 0;
	for (let i = 0; i < pts.length - 1; i++) {
		const [d, t] = segDist(px, py, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]);
		const along = (acc + t * L[i]) / total;
		const w = lerp(w0, w1, along);
		best = Math.min(best, d - w);
		acc += L[i];
	}
	return best;
}
function ellipse(px, py, cx, cy, rx, ry) { return (Math.hypot((px - cx) / rx, (py - cy) / ry) - 1) * Math.min(rx, ry); }
/** The front emblem (chest / belly front faces): distance, negative inside. */
function emblemFront(x, y) {
	const ax = Math.abs(x);
	let d = ellipse(ax, y, 0, 75.2, 2.3, 2.0);                       // head
	d = Math.min(d, ellipse(ax, y, 0, 70.6, 4.2, 3.6));               // thorax
	d = Math.min(d, polyDist(ax, y, [[0, 68], [0, 60], [0, 50], [0, 44]], 3.4, 0.4)); // the long abdomen spike
	d = Math.min(d, polyDist(ax, y, [[2.5, 73], [7, 76.5], [11, 79.5]], 2.0, 1.5));   // leg 1, over the shoulder
	d = Math.min(d, polyDist(ax, y, [[3.2, 71.5], [10, 74], [16, 73.5], [21.5, 71]], 2.0, 1.2)); // leg 2 round the ribs
	d = Math.min(d, polyDist(ax, y, [[3.4, 69.5], [10, 67.5], [16, 64.5], [21.5, 61]], 1.9, 1.1)); // leg 3
	d = Math.min(d, polyDist(ax, y, [[2.6, 67.5], [7, 62.5], [9.5, 57], [9.8, 52.5]], 1.7, 0.35)); // leg 4, a free point
	return d;
}
function emblemTop(x, z) { return polyDist(Math.abs(x), z, [[11, -15.5], [12.5, -6], [12, 6], [10, 14]], 1.5, 0.4); }
function emblemSide(z, y) { // on the ribcage's east / west faces (x = +-21)
	let d = polyDist(z, y, [[-12, 71], [-4, 70], [4, 67], [10, 62]], 1.2, 0.35);
	d = Math.min(d, polyDist(z, y, [[-12, 61], [-4, 59], [3, 56.5]], 1.1, 0.3));
	return d;
}
function emblemBack(x, y) { // the hump's back face (z = 21): a smaller spider whose legs claw round the hump
	const ax = Math.abs(x);
	let d = ellipse(ax, y, 0, 84, 1.9, 1.7);
	d = Math.min(d, ellipse(ax, y, 0, 80.2, 3.4, 3.0));
	d = Math.min(d, polyDist(ax, y, [[0, 78], [0, 72]], 2.6, 0.5));
	d = Math.min(d, polyDist(ax, y, [[2.2, 82], [8, 86.5], [14, 89.5]], 1.6, 0.8));
	d = Math.min(d, polyDist(ax, y, [[2.8, 80.5], [10, 81.5], [17, 79]], 1.6, 0.8));
	d = Math.min(d, polyDist(ax, y, [[2.8, 79], [9, 76], [14, 72.5]], 1.5, 0.6));
	return d;
}
// eyes: big angular white lenses sweeping up and round the sides of the head (head space, the front face z = -30)
const EYE = [[0.8, 78.6], [3.0, 77.3], [6.0, 77.0], [9.0, 77.8], [11.3, 79.4], [11.3, 89.1], [9.8, 89.4], [7.4, 88.5], [5.0, 86.2], [3.0, 83.0], [1.6, 80.4]];
function polySdf(px, py, poly) {
	let inside = false, best = 1e9;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
		const [xi, yi] = poly[i], [xj, yj] = poly[j];
		if ((yi > py) !== (yj > py) && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) inside = !inside;
		best = Math.min(best, segDist(px, py, xi, yi, xj, yj)[0]);
	}
	return inside ? -best : best;
}
function eyeFront(x, y) { return polySdf(Math.abs(x), y, EYE); }
function eyeSide(z, y) { // the lens wraps round the side: from the front edge (z -30) back to a point at z -23.5
	return polySdf(z, y, [[-30.5, 79.4], [-28, 81.0], [-25, 84.6], [-23.2, 88.6], [-26, 89.4], [-30.5, 89.4]]);
}

/** One texel: returns [shade, gloss, channel(B), keepRGB|null]. p = bone-space point, w = model-space point, nW = model normal. */
function paint(b, c, f, p, w, nW, fu, fv, fw, fh) {
	const tag = c.tag;
	// base body: muscle lumps as a height field, lit through a bumped normal, with a wet gloss and veins
	const sc = 0.085;
	const h = (q) => fbm3(q[0] * sc + 3.1, q[1] * sc + 7.7, q[2] * sc + 1.3);
	const eps = 0.6;
	const h0 = h(w);
	const hx = h([w[0] + eps, w[1], w[2]]) - h0, hy = h([w[0], w[1] + eps, w[2]]) - h0, hz = h([w[0], w[1], w[2] + eps]) - h0;
	const k = 9.0;
	let n = norm([nW[0] - hx * k, nW[1] - hy * k, nW[2] - hz * k]);
	const diff = clamp(dot(n, LIGHT), -1, 1);
	// edge rounding: texels near a face edge turn toward the neighbour (cheap bevel) -- darker toward the bottom edges
	const ed = Math.min(fu * fw, (1 - fu) * fw, fv * fh, (1 - fv) * fh);
	const bevel = 1 - smooth(0, 1.4, ed);
	let shade = 100 + diff * 20 + (h0 - 0.5) * 18 - bevel * 10 + (fv < 0.5 && nW[1] > -0.5 ? bevel * 6 : 0);
	shade *= 0.8 + 0.2 * smooth(0, 70, w[1]); // the legs and feet sit in the body's shadow
	// veins: zero lines of warped noise, sunk with a raised glossy rim
	const vw = [w[0] * 0.065 + fbm3(w[0] * 0.04, w[1] * 0.04, w[2] * 0.04) * 1.4, w[1] * 0.065, w[2] * 0.065];
	const vein = Math.abs(fbm3(vw[0] + 11, vw[1] + 5, vw[2] + 2) - 0.5);
	let gloss = 0;
	if (vein < 0.011) shade -= 30;
	else if (vein < 0.022) { shade += 6; gloss += 45 * (1 - (vein - 0.011) / 0.011); }
	// wet specular through the bumped normal, streaked downward like dripping goo
	const H = norm([LIGHT[0], LIGHT[1], LIGHT[2] - 1]);
	const spec = Math.pow(clamp(dot(n, H), 0, 1), 12);
	const streak = 0.4 + 0.6 * vnoise3(w[0] * 0.45, w[1] * 0.07, w[2] * 0.45);
	gloss += spec * 150 * streak;
	let ch = 0, keep = null;

	if (tag === 'claw' || tag === 'spine') {
		// glossy, darker, sharpening to a lighter wet tip along the part
		const t = c.tipFrac != null ? c.tipFrac : 0.5;
		shade = 80 + diff * 30 + fv * -10;
		gloss = 60 + spec * 160;
		if (tag === 'claw') { shade += 20; gloss += 40; }
	}
	if (tag === 'tooth') {
		const tip = c.tip ? 1 : 0;
		const along = c.tip ? fv : fv * 0.6; // fv: 0 at the face top
		const up = b.name === 'jaw'; // lower teeth point up: the root is at the bottom
		const a = up ? (c.tip ? 1 - fv : 1 - fv * 0.6) : along;
		const l = lerp(178, 246, clamp(tip * 0.5 + a * 0.5, 0, 1)) + diff * 14 - (f === 'east' || f === 'west' ? 10 : 0);
		keep = [l, l * 0.975, l * 0.9];
		if (!c.tip && ((!up && fv < 0.18) || (up && fv > 0.82))) keep = [96, 28, 36]; // the gum line
	}
	if (tag === 'tongue') {
		ch = 32;
		shade = 120 + diff * 20 + (h0 - 0.5) * 30;
		if (f === 'up') {
			const groove = Math.abs(fu - 0.5);
			if (groove < 0.07) shade -= 34;
			gloss = 40 + spec * 140;
		} else if (f === 'down') { shade = 78; gloss = 10; }
		else { shade -= 10; gloss = spec * 80; }
	}
	if (b.name === 'head' && tag === 'head') {
		// the palate (the head's underside inside the grin) is throat: dark mouth flesh with ridges
		if (f === 'down') {
			ch = 32;
			shade = 46 + 16 * Math.abs(Math.sin(p[2] * 1.3)) * smooth(-30, -18, p[2] * -1 - 0) ;
			shade = 40 + 18 * (0.5 + 0.5 * Math.sin(p[2] * 1.4)) * (p[2] < -14 ? 1 : 0.3);
			gloss = 20 + spec * 60;
		}
		// the grin's lip: a lighter ridge along the bottom of the front and the front of the sides
		const lipBand = p[1] < 74.3 && (f === 'north' || ((f === 'east' || f === 'west') && p[2] < -15));
		if (lipBand && c.from[1] <= 73.2) { shade = 132 + diff * 10; gloss = 70 + spec * 100; }
		// the corners of the grin crease up the cheeks
		if ((f === 'east' || f === 'west') && c.from[1] <= 74.2) {
			const cr = segDist(p[2], p[1], -15.5, 74.0, -19.5, 77.6)[0];
			if (cr < 0.45) { shade = 30; gloss = 0; }
			else if (cr < 0.9) { shade += 18; gloss += 50; }
		}
		// eyes
		let e = 1e9;
		if (f === 'north' && c.from[2] <= -29.9) e = eyeFront(p[0], p[1]);
		if ((f === 'east' || f === 'west') && Math.abs(Math.abs(p[0]) - 11) < 0.05) e = eyeSide(p[2], p[1]);
		if (e < 0) {
			ch = 255;
			const depth = clamp(-e / 2.4, 0, 1);
			shade = 120 + depth * 18;
			gloss = (fu < 0.5 ? 1 : 0.6) * 30 * depth + spec * 80;
		} else if (e < 1.0) { ch = 0; shade = 22; gloss = 0; }       // the dark lens rim
		else if (e < 1.8) { shade -= 26; }
		// brow furrows above the lenses
		if (f === 'north' && p[1] > 88.6 && p[1] < 89.6 && Math.abs(p[0]) < 9) shade -= 18;
	}
	if (b.name === 'jaw') {
		if (f === 'up' && c.from[1] + c.size[1] > 73.5) {          // the floor of the mouth
			ch = 32;
			shade = lerp(70, 28, smooth(-30, -14, p[2]));
			gloss = 15 + spec * 50;
		}
		if ((f === 'north' || f === 'east' || f === 'west') && p[1] > 72.4 && tag === 'jaw') { shade = 132 + diff * 10; gloss = 70 + spec * 90; }
	}
	// the emblem
	let em = 1e9;
	if (b.name === 'chest') {
		if (f === 'north') em = emblemFront(p[0], p[1]);
		else if (f === 'up' && c.from[1] < 80) em = emblemTop(p[0], p[2]);
		else if ((f === 'east' || f === 'west') && Math.abs(Math.abs(p[0]) - 21) < 0.05) em = emblemSide(p[2], p[1]);
		else if (f === 'south' && c.from[1] >= 70 && c.from[1] < 80) em = emblemBack(p[0], p[1]);
		if (f === 'north' && c.tag === 'pecs') em = emblemFront(p[0], p[1] + 0.2);
	}
	if (b.name === 'belly' && f === 'north') em = emblemFront(p[0], p[1] + 2.5);
	if (em < 1e8) {
		const rag = (vnoise3(w[0] * 0.9, w[1] * 0.9, w[2] * 0.9) - 0.5) * 0.9; // a ragged, living edge
		const d = em + rag;
		if (d < 0) { ch = 128; shade = 128 + clamp(-d, 0, 2) * 3 + (h0 - 0.5) * 20; gloss = 20 + spec * 70; }
		else if (d < 0.9) { ch = 0; shade = 20; gloss = 0; }
	}
	if (tag === 'abs') { // the abs: a grid of plates
		const gy = (p[1] - 43) % 4, gx = Math.abs(p[0]);
		if (f === 'north' && (gy < 0.5 || gx < 0.4)) shade -= 22;
	}
	return [clamp(shade, 0, 255), clamp(gloss, 0, 255), ch, keep];
}

for (const F of allFaces) {
	const { b, c, f, tw, th } = F;
	const [TL, TR, BR, BL] = faceCorners(c, f);
	const [fw, fh] = faceDims(c, f);
	const nB = NORMALS[f];
	// model-space normal at rest: rotate through the bone chain
	const o = toModel(b, [0, 0, 0]);
	const nm = toModel(b, nB);
	const nW = norm([nm[0] - o[0], nm[1] - o[1], nm[2] - o[2]]);
	for (let j = 0; j < th; j++) for (let i = 0; i < tw; i++) {
		const fu = (i + 0.5) / tw, fv = (j + 0.5) / th;
		const p = [0, 1, 2].map((a) => TL[a] + (TR[a] - TL[a]) * fu + (BL[a] - TL[a]) * fv);
		const w = toModel(b, p);
		const [shade, gloss, ch, keep] = paint(b, c, f, p, w, nW, fu, fv, fw, fh);
		const idx = ((F.v + j) * AW + F.u + i) * 4;
		MASK[idx] = shade; MASK[idx + 1] = gloss; MASK[idx + 2] = ch; MASK[idx + 3] = 255;
		if (keep) { KEEP[idx] = keep[0]; KEEP[idx + 1] = keep[1]; KEEP[idx + 2] = keep[2]; KEEP[idx + 3] = 255; }
	}
}

// ------------------------------------------------------------------------------------------------ the bake (port of SymbioteTintMath.bake)
const CLASSIC = [0x14161F, 0xF2F4F8, 0xF4F6FA, 0xB03A52];
const CARNAGE = [0xB3121B, 0x1A0A0C, 0xF6F1EA, 0xB03A52];
const MID = 128 / 255;
function bakeTexel(shade, gloss, maskBlue, colours) {
	const kind = maskBlue >= 192 ? 2 : maskBlue >= 64 ? 1 : maskBlue >= 16 ? 3 : 0;
	const base = colours[kind];
	const cc = [((base >> 16) & 255) / 255, ((base >> 8) & 255) / 255, (base & 255) / 255];
	const s = shade / 255, g = gloss / 255, m = Math.max(cc[0], cc[1], cc[2]);
	const exact = kind === 1 || kind === 2;
	const hue = m < 0.03 ? [0.82, 0.86, 1.0] : [cc[0] / m, cc[1] / m, cc[2] / m];
	const out = [];
	for (let i = 0; i < 3; i++) {
		let o;
		if (s <= MID) o = cc[i] * (s / MID);
		else if (exact) o = cc[i];
		else { const t = (s - MID) / (1 - MID); const lift = (0.5 + 0.5 * hue[i]) * 0.86; o = cc[i] + (Math.max(lift, cc[i]) - cc[i]) * t; }
		const spec = exact ? cc[i] + (1 - cc[i]) * 0.3 * m : 0.72 + 0.28 * hue[i];
		o = o + (Math.max(spec, o) - o) * g;
		out.push(Math.round(clamp(o, 0, 1) * 255));
	}
	return out;
}
function bake(colours) {
	const px = Buffer.alloc(AW * AH * 4);
	for (let i = 0; i < AW * AH; i++) {
		const k = i * 4;
		if (KEEP[k + 3]) { px[k] = KEEP[k]; px[k + 1] = KEEP[k + 1]; px[k + 2] = KEEP[k + 2]; px[k + 3] = 255; continue; }
		if (!MASK[k + 3]) continue;
		const [r, g, bb] = bakeTexel(Math.round(MASK[k]), Math.round(MASK[k + 1]), MASK[k + 2], colours);
		px[k] = r; px[k + 1] = g; px[k + 2] = bb; px[k + 3] = 255;
	}
	return px;
}

// ------------------------------------------------------------------------------------------------ write the assets
function rounded(v) { return Math.round(v * 1000) / 1000; }
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.symbiote_monster', texture_width: AW, texture_height: AH, visible_bounds_width: 8, visible_bounds_height: 8, visible_bounds_offset: [0, 3, 0] },
		bones: bones.map((b) => {
			const o = { name: b.name, pivot: b.pivot.map(rounded) };
			if (b.parent) o.parent = b.parent;
			if (b.rotation.some((r) => r)) o.rotation = b.rotation.map(rounded);
			if (b.cubes.length) o.cubes = b.cubes.map((c) => ({ origin: c.from.map(rounded), size: c.size.map(rounded), uv: c.uv }));
			return o;
		}),
	}],
};
const mask8 = Buffer.alloc(AW * AH * 4), keep8 = Buffer.alloc(AW * AH * 4);
for (let i = 0; i < AW * AH * 4; i++) { mask8[i] = Math.round(MASK[i]); keep8[i] = KEEP[i]; }
fs.writeFileSync(path.join(ROOT, 'geo', 'symbiote_monster.geo.json'), JSON.stringify(geo));
PNG.write(path.join(ROOT, 'textures', 'entity', 'symbiote_monster_tint.png'), AW, AH, mask8);
PNG.write(path.join(ROOT, 'textures', 'entity', 'symbiote_monster_keep.png'), AW, AH, keep8);
const classicTex = bake(CLASSIC);
PNG.write(path.join(ROOT, 'textures', 'entity', 'symbiote_monster.png'), AW, AH, classicTex);

// model height (rest): the top of everything
let top = -1e9, bottom = 1e9, minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
for (const b of bones) for (const c of b.cubes) for (const f of FACES) for (const q of faceCorners(c, f)) {
	const w = toModel(b, q);
	top = Math.max(top, w[1]); bottom = Math.min(bottom, w[1]);
	minX = Math.min(minX, w[0]); maxX = Math.max(maxX, w[0]); minZ = Math.min(minZ, w[2]); maxZ = Math.max(maxZ, w[2]);
}
console.log(`atlas ${AW}x${AH}, ${bones.length} bones, ${allFaces.length / 6} cubes; rest height ${rounded(bottom)}..${rounded(top)} px, x ${rounded(minX)}..${rounded(maxX)}, z ${rounded(minZ)}..${rounded(maxZ)}`);
const headTop = toModel(B.head, [0, 90, -20]);
console.log('head centre', toModel(B.head, [0, 81, -20]).map(rounded), 'hump', toModel(B.chest, [0, 94, 10]).map(rounded), 'claw tip r', toModel(B.finger_r1c, [-25, -4.5, -7]).map(rounded));

// ------------------------------------------------------------------------------------------------ animations
require('./gen_symbiote_monster_anims_v0175.js')({ ROOT, B, bones, toModel });

// ------------------------------------------------------------------------------------------------ preview (software turntable)
if (PREVIEW) {
	fs.mkdirSync(PREVIEW, { recursive: true });
	const preview = require('./preview_monster_v0175.js');
	const carnage = bake(CARNAGE);
	preview.sheet({ bones, B, toModel, faceCorners, FACES, AW, AH, tex: classicTex, out: path.join(PREVIEW, 'monster_classic.png') });
	preview.sheet({ bones, B, toModel, faceCorners, FACES, AW, AH, tex: carnage, out: path.join(PREVIEW, 'monster_carnage.png') });
	preview.face({ bones, B, toModel, faceCorners, FACES, AW, AH, tex: classicTex, out: path.join(PREVIEW, 'monster_face.png') });
	preview.face({ bones, B, toModel, faceCorners, FACES, AW, AH, tex: carnage, out: path.join(PREVIEW, 'monster_face_carnage.png') });
	const poses = require('./gen_symbiote_monster_anims_v0175.js').previewPoses;
	if (poses) preview.poses({ bones, B, toModel, faceCorners, FACES, AW, AH, tex: classicTex, poses, out: path.join(PREVIEW, 'monster_poses.png') });
	// polish pass: the emerge clip frame by frame from the front, the 3/4 and the side, so the face can be checked the whole way
	const em = require('./gen_symbiote_monster_anims_v0175.js').CLIPS.emerge;
	const times = [0, 0.15, 0.25, 0.35, 0.5, 0.7, 0.9, 1.1, 1.3, 1.6];
	for (const [tag, yaw] of [['front', 0], ['quarter', 35], ['side', 90], ['low_front', 0]]) {
		const ps = times.map((t) => [String(t), em.fn(t), yaw]);
		const ims = ps.map(([, pose]) => preview.render({ bones, B, toModel, faceCorners, FACES, AW, AH, tex: classicTex },
			{ yaw, pitch: tag === 'low_front' ? -14 : 6, w: 260, h: 330, bg: [150, 170, 190], scale: 2.4, cx: 0, cyw: 0, margin: 26, pose }));
		preview.compose(ims, 5, path.join(PREVIEW, 'monster_emerge_' + tag + '.png'));
	}
}
module.exports = { bones, B, toModel, faceCorners, FACES, AW, AH, classicTex, bake, CARNAGE };
