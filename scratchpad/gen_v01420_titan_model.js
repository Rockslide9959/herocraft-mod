// v0.14.20 Titan world boss: a brand-new GeckoLib model, texture, glowmask and every animation.
//   - assets/projecthero/geo/titan.geo.json              (hunched, massively muscled giant zombie)
//   - assets/projecthero/textures/entity/titan.png        (rotting green-grey skin, exposed ribs/spine, rags, shackles)
//   - assets/projecthero/textures/entity/titan_glowmask.png (eyes, mouth, rib cavity embers)
//   - assets/projecthero/animations/titan.animation.json  (idle/walk/run + every attack, roar, intro, death)
// Usage (from the repo root):  node scratchpad/gen_v01420_titan_model.js [previewDir]
// With a previewDir it also software-renders posed previews (front / side / 3-quarter) of chosen frames into it.
//
// Conventions (Bedrock geo / Blockbench, which GeckoLib follows): +Y up, the model faces -Z, the entity's RIGHT
// side is -X. Rotation X > 0 tips a bone's top forward (so a hanging limb's free end swings BACK); rotation
// Z > 0 swings a hanging limb toward -X (the right). Animation rotations are ADDED to a bone's rest rotation.
// Every number here is model pixels; the renderer scales the model by MODEL_SCALE (printed below) so the posed
// rest model is exactly the Titan's 18-block hit-box height.
//
// TIMING: every telegraphed attack strikes on the SERVER at tick 40 after its animation is set. The client starts
// the clip on receipt, and the GeckoLib "main" controller then spends TRANSITION ticks blending in before the
// clip's own time starts -- so a strike keyframe sits at (40 - TRANSITION) clip ticks. The swat runs on the
// "upper" controller (UPPER_TRANSITION). These constants are mirrored in TitanEntity (TitanAnims) and checked here.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const PREVIEW = process.argv[2] || null;
const RES = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');
const TITAN_HEIGHT_BLOCKS = 18.0;
const TRANSITION = 4;          // main controller blend-in, ticks
const UPPER_TRANSITION = 2;    // upper-body (swat) controller blend-in, ticks
const STRIKE = 40 - TRANSITION; // clip tick of every telegraphed strike
const D = 3;                    // texels per model pixel
const TW = 512;

// ================================================================ PNG
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePng(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ================================================================ noise
let seed = 20141020;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
function hash3(x, y, z) {
	let h = (x * 374761393 + y * 668265263 + z * 2147483647) | 0;
	h = (h ^ (h >>> 13)) * 1274126177 | 0;
	h = h ^ (h >>> 16);
	return (h >>> 0) / 4294967296;
}
const sm = t => t * t * (3 - 2 * t);
function vnoise(x, y, z) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
	const xf = sm(x - xi), yf = sm(y - yi), zf = sm(z - zi);
	let r = 0;
	for (let dz = 0; dz < 2; dz++) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
		const w = (dx ? xf : 1 - xf) * (dy ? yf : 1 - yf) * (dz ? zf : 1 - zf);
		r += w * hash3(xi + dx, yi + dy, zi + dz);
	}
	return r;
}
function fbm(x, y, z, oct = 4) {
	let a = 0.5, f = 1, s = 0, n = 0;
	for (let i = 0; i < oct; i++) { s += a * vnoise(x * f, y * f, z * f); n += a; a *= 0.5; f *= 2.03; }
	return s / n;
}
const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, v));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * clamp(t, 0, 1));
const mul = (a, k) => a.map(v => v * k);

// ================================================================ model
const bones = [];
const B = {};
function bone(name, parent, pivot, rot = [0, 0, 0]) {
	const b = { name, parent, pivot: pivot.slice(), rot: rot.slice(), cubes: [] };
	bones.push(b); B[name] = b; return b;
}
// a cube from min to max corner; opts: { rot:[x,y,z], pivot:[x,y,z], paint: 'tag' }
function box(b, x0, y0, z0, x1, y1, z1, mat, opts = {}) {
	if (x1 < x0) [x0, x1] = [x1, x0];
	const c = { from: [x0, y0, z0], to: [x1, y1, z1], mat, rot: opts.rot || null, pivot: opts.pivot || null, paint: opts.paint || mat, bone: b.name };
	b.cubes.push(c); return c;
}
// side s: -1 = right (json -X), +1 = left. Outward x-range [a, b] -> real x-range.
const sx = (s, a, b) => s > 0 ? [a, b] : [-b, -a];

const root = bone('root', null, [0, 0, 0]);
const pelvis = bone('pelvis', 'root', [0, 20, 0]);
box(pelvis, -8, 17, -5.5, 8, 23, 5.5, 'cloth');
box(pelvis, -8.4, 21.6, -5.9, 8.4, 23.1, 5.9, 'rope');
const flapF = bone('flap_f', 'pelvis', [0, 22, -5.9]);
box(flapF, -4.5, 13.5, -6.3, 4.5, 22, -5.9, 'rag');
const flapB = bone('flap_b', 'pelvis', [0, 22, 5.9]);
box(flapB, -5.5, 12, 5.9, 5.5, 22, 6.3, 'rag');

for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const lx = s * 4.6;
	const leg = bone(`leg_${n}`, 'pelvis', [lx, 19, 0], [-10, 0, -s * 5]);
	box(leg, sx(s, -0.2, 9.4)[0], 10.5, -4.8, sx(s, -0.2, 9.4)[1], 20, 4.8, 'skin');
	box(leg, sx(s, -0.7, 9.9)[0], 9.8, -5.3, sx(s, -0.7, 9.9)[1], 20.5, 5.3, 'pants');
	const shin = bone(`shin_${n}`, `leg_${n}`, [lx, 11, 0], [20, 0, 0]);
	box(shin, sx(s, 0.6, 8.2)[0], 3, -3.9, sx(s, 0.6, 8.2)[1], 11.5, 3.9, 'skin');
	box(shin, sx(s, 1.1, 7.7)[0], 5.5, 3.8, sx(s, 1.1, 7.7)[1], 10.5, 5.6, 'skin');           // calf
	box(shin, sx(s, 1.8, 7.0)[0], 9.0, -4.8, sx(s, 1.8, 7.0)[1], 12.0, -3.7, 'skin', { paint: 'knee' });
	if (s > 0) {
		box(shin, sx(s, 0.1, 8.7)[0], 3.2, -4.5, sx(s, 0.1, 8.7)[1], 5.6, 4.5, 'iron');      // ankle shackle
		const ch = bone('chain_ankle', 'shin_l', [s * 8.7, 3.8, 0], [75, 0, 0]);
		chainLinks(ch, s * 8.9, 3.8, 3, 1);
	}
	const foot = bone(`foot_${n}`, `shin_${n}`, [lx, 3, 0], [-10, 0, s * 5]);
	box(foot, sx(s, 0.3, 8.7)[0], 0, -8.5, sx(s, 0.3, 8.7)[1], 3.2, 3.8, 'skin', { paint: 'foot' });
	for (const c of [1.9, 4.5, 7.1]) {
		box(foot, sx(s, c - 0.85, c + 0.85)[0], 0, -10.8, sx(s, c - 0.85, c + 0.85)[1], 1.6, -8.3, 'claw', { paint: 'toeclaw' });
	}
}

function chainLinks(b, x, yTop, count, s) {
	let y = yTop;
	for (let i = 0; i < count; i++) {
		const h = i === count - 1 ? 1.6 : 2.6; // the last link is broken off
		if (i % 2 === 0) box(b, x - 0.45, y - h, -0.9, x + 0.45, y, 0.9, 'chain');
		else box(b, x - 0.9, y - h, -0.45, x + 0.9, y, 0.45, 'chain');
		y -= h - 0.5;
	}
}

const waist = bone('waist', 'pelvis', [0, 22, 0], [10, 0, 0]);
box(waist, -8.8, 22, -6.2, 8.8, 30.5, 6.2, 'skin', { paint: 'belly' });
for (const y of [23.2, 26.0, 28.8]) box(waist, -1.3, y, 6.0, 1.3, y + 1.7, 7.4, 'bone', { paint: 'vert' });

const flinch = bone('flinch', 'waist', [0, 29, 0]);
const chest = bone('chest', 'flinch', [0, 29, 0], [20, 0, 0]);
box(chest, -10.5, 29, -7, 10.5, 43.5, 6.5, 'skin', { paint: 'chest' });
box(chest, -9.5, 34.5, -8.4, 9.5, 41.5, -7, 'skin', { paint: 'pecs' });
box(chest, -8.5, 41.5, -2, 8.5, 49, 7, 'skin', { paint: 'hump' });
box(chest, -4.6, 36.5, -10.6, 4.6, 42.5, -3, 'skin', { paint: 'neck' });
// exposed ribs: lower-left front of the ribcage, torn open
for (const [y, len, droop, back] of [[30.0, 8.8, 8, 2.0], [31.9, 7.8, 11, 0.6], [33.8, 6.2, 14, -0.8]]) {
	// curved ribs, drooping toward the sternum, broken off at different lengths
	box(chest, 11.4 - len, y, -7.5, 11.4, y + 0.8, -6.8, 'bone', { rot: [0, 0, -droop], pivot: [11.0, y + 0.4, -7.1], paint: 'rib' });
	box(chest, 10.8, y, -6.9, 11.5, y + 0.8, back, 'bone', { paint: 'rib' });
}
// spine spikes: lean back out of the spine and hump, biggest at the top
const spikes = [[31.0, 2.6, 1.3, 6.4], [34.0, 3.6, 1.6, 6.4], [37.0, 4.6, 1.9, 6.5], [40.5, 6.0, 2.3, 6.8], [44.5, 7.5, 2.7, 6.8]];
for (const [y, len, w, z] of spikes) {
	box(chest, -w / 2, y, z - w / 2, w / 2, y + len, z + w / 2, 'bone', { rot: [-55, 0, 0], pivot: [0, y, z], paint: 'spike' });
}
for (const s of [-1, 1]) {
	box(chest, s * 4.8 - 0.85, 47.5, 3.6, s * 4.8 + 0.85, 52.0, 5.3, 'bone', { rot: [-45, 0, -s * 24], pivot: [s * 4.8, 47.5, 4.5], paint: 'spike' });
	box(chest, s * 7.6 - 0.6, 44.0, 5.2, s * 7.6 + 0.6, 47.5, 6.4, 'bone', { rot: [-45, 0, -s * 38], pivot: [s * 7.6, 44.0, 5.8], paint: 'spike' });
}

// head: hangs forward and low, in front of the hump
const HY = -3.0, HZ = -2.5;
const hb = (b, x0, y0, z0, x1, y1, z1, mat, o) => box(b, x0, y0 + HY, z0 + HZ, x1, y1 + HY, z1 + HZ, mat, o && o.pivot ? Object.assign({}, o, { pivot: [o.pivot[0], o.pivot[1] + HY, o.pivot[2] + HZ] }) : o);
const neck = bone('neck', 'chest', [0, 43 + HY, -6.5 + HZ]);
const head = bone('head', 'neck', [0, 43 + HY, -6.5 + HZ], [-24, 0, 0]);
hb(head, -5.6, 43, -15.5, 5.6, 52.5, -4.5, 'skin', { paint: 'head' });
hb(head, -6.0, 48.6, -16.4, 6.0, 50.4, -14.9, 'skin', { paint: 'brow' });
hb(head, -4.0, 41.6, -15.45, 4.0, 43.2, -14.95, 'tooth', { paint: 'teeth_up' });
for (const s of [-1, 1]) {
	hb(head, s * 2.7 - 0.45, 40.2, -15.55, s * 2.7 + 0.45, 43.0, -14.95, 'tooth', { paint: 'fang' });
	hb(head, s * 5.0 - 1.0, 45.0, -15.9, s * 5.0 + 1.0, 47.0, -12.0, 'skin', { paint: 'cheek' });
	hb(head, s * 5.6 - (s > 0 ? 0 : 0.7), 45.5, -10.5, s * 5.6 + (s > 0 ? 0.7 : 0), 48.8, -8.2, 'skin', { paint: 'ear' });
}
for (const x of [-3.4, -1.1, 1.4, 3.6]) {
	hb(head, x - 0.45, 40.5 + Math.abs(x) * 0.6, -4.9, x + 0.45, 52.0, -4.4, 'hair');
}
const jaw = bone('jaw', 'head', [0, 44 + HY, -6.5 + HZ]);
hb(jaw, -5.2, 39.6, -16.0, 5.2, 43.2, -6.5, 'skin', { paint: 'jaw' });
hb(jaw, -4.2, 43.2, -15.95, 4.2, 44.5, -15.45, 'tooth', { paint: 'teeth_low' });
for (const s of [-1, 1]) hb(jaw, s * 3.45 - 0.5, 43.2, -16.15, s * 3.45 + 0.5, 46.8, -15.5, 'tooth', { paint: 'tusk' });

// arms: long, hanging to the knees, with massive forearms and oversized clawed hands
const ELBOW = 30.0, WRIST = 20.5, KNUCK = 15.5;
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const c = 13.6; // arm centre, outward
	const cx = s * c;
	const arm = bone(`arm_${n}`, 'chest', [s * 11.0, 40, 0], [-31, 0, -s * 6]);
	box(arm, sx(s, 9.6, 18.8)[0], 34, -4.8, sx(s, 9.6, 18.8)[1], 43.5, 4.8, 'skin', { paint: 'delt' });
	box(arm, sx(s, 10.6, 17.8)[0], ELBOW - 0.5, -3.7, sx(s, 10.6, 17.8)[1], 36, 3.7, 'skin');
	box(arm, cx - 0.7, 42.5, -1.2, cx + 0.7, 47.0, 0.2, 'bone', { rot: [-10, 0, -s * 28], pivot: [cx, 42.5, -0.5], paint: 'spike' });
	box(arm, cx + s * 2.2 - 0.55, 41.5, 1.6, cx + s * 2.2 + 0.55, 44.8, 2.7, 'bone', { rot: [-25, 0, -s * 40], pivot: [cx + s * 2.2, 41.5, 2.1], paint: 'spike' });
	const fore = bone(`forearm_${n}`, `arm_${n}`, [cx, ELBOW, 0], [-16, 0, 0]);
	box(fore, sx(s, 9.8, 18.6)[0], WRIST, -4.3, sx(s, 9.8, 18.6)[1], ELBOW + 0.8, 4.3, 'skin', { paint: 'forearm' });
	box(fore, sx(s, 9.1, 19.3)[0], WRIST + 0.4, -5.0, sx(s, 9.1, 19.3)[1], WRIST + 4.0, 5.0, 'iron', { paint: 'cuff' });
	const ch = bone(`chain_${n}`, `forearm_${n}`, [s * 19.3, WRIST + 1.4, 0]);
	chainLinks(ch, s * 19.6, WRIST + 1.4, 5, s);
	const hand = bone(`hand_${n}`, `forearm_${n}`, [cx, WRIST, 0], [-6, 0, 0]);
	box(hand, sx(s, 9.8, 18.6)[0], KNUCK, -4.4, sx(s, 9.8, 18.6)[1], WRIST, 4.4, 'skin', { paint: 'hand' });
	box(hand, sx(s, 10.0, 18.4)[0], KNUCK + 0.1, -5.0, sx(s, 10.0, 18.4)[1], KNUCK + 2.1, -3.8, 'skin', { paint: 'knuckle' });
	box(hand, sx(s, 8.4, 10.4)[0], KNUCK + 0.3, -3.6, sx(s, 8.4, 10.4)[1], KNUCK + 4.7, -1.4, 'skin', { rot: [0, 0, s * 18], pivot: [s * 10.2, KNUCK + 4.5, -2.5], paint: 'finger' });
	box(hand, sx(s, 8.6, 10.0)[0], KNUCK - 1.9, -3.4, sx(s, 8.6, 10.0)[1], KNUCK + 0.4, -1.8, 'claw', { rot: [0, 0, s * 18], pivot: [s * 10.2, KNUCK + 4.5, -2.5] });
	const fing = bone(`fingers_${n}`, `hand_${n}`, [cx, KNUCK + 0.5, -1.5], [-14, 0, 0]);
	for (const f of [11.1, 13.3, 15.5, 17.4]) {
		const fx = sx(s, f - 1.05, f + 1.05);
		const len = f === 17.4 ? 4.0 : f === 13.3 || f === 15.5 ? 5.0 : 4.5;
		const top = KNUCK + 0.5;
		box(fing, fx[0], top - len, -4.2, fx[1], top, -1.8, 'skin', { paint: 'finger' });
		const cxw = sx(s, f - 0.7, f + 0.7);
		box(fing, cxw[0], top - len - 2.8, -4.0, cxw[1], top - len + 0.2, -2.2, 'claw', { rot: [-22, 0, 0], pivot: [s * f, top - len, -3.1] });
	}
}

// ---------------------------------------------------------------- 3D math (json space)
const DEG = Math.PI / 180;
const I3 = () => [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
const mm3 = (a, b) => a.map((r, i) => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const Rx = a => { const c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; };
const Ry = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; };
const Rz = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; };
const MIR = [[-1, 0, 0], [0, 1, 0], [0, 0, 1]];
// GeckoLib: baked x = -json x, baked rotation = (-rx, -ry, rz), applied as Rz * Ry * Rx.
function rotJ(r) {
	return mm3(MIR, mm3(Rz(r[2] * DEG), mm3(Ry(-r[1] * DEG), mm3(Rx(-r[0] * DEG), MIR))));
}
// affine = { m: 3x3, t: [3] }; apply: m*p + t
const aff = (m, t) => ({ m, t });
const affMul = (a, b) => ({ m: mm3(a.m, b.m), t: [0, 1, 2].map(i => a.m[i][0] * b.t[0] + a.m[i][1] * b.t[1] + a.m[i][2] * b.t[2] + a.t[i]) });
const affApply = (a, p) => [0, 1, 2].map(i => a.m[i][0] * p[0] + a.m[i][1] * p[1] + a.m[i][2] * p[2] + a.t[i]);
function pivotRot(piv, r) { // T(piv) R T(-piv)
	const m = rotJ(r);
	const mp = [0, 1, 2].map(i => m[i][0] * piv[0] + m[i][1] * piv[1] + m[i][2] * piv[2]);
	return aff(m, [piv[0] - mp[0], piv[1] - mp[1], piv[2] - mp[2]]);
}
// pose: { bone: [rx,ry,rz] } offsets; rootPos: [x,y,z]
function worldMatrices(pose, rootPos = [0, 0, 0]) {
	const W = {};
	for (const b of bones) {
		const off = pose[b.name] || [0, 0, 0];
		const r = [b.rot[0] + off[0], b.rot[1] + off[1], b.rot[2] + off[2]];
		let local = pivotRot(b.pivot, r);
		if (b.name === 'root') local = affMul(aff(I3(), rootPos), local);
		W[b.name] = b.parent ? affMul(W[b.parent], local) : local;
	}
	return W;
}
function cubeCorners(c) {
	const out = [];
	for (const x of [c.from[0], c.to[0]]) for (const y of [c.from[1], c.to[1]]) for (const z of [c.from[2], c.to[2]]) out.push([x, y, z]);
	return out;
}
function cubeWorld(c, W) {
	let a = W[c.bone];
	if (c.rot) a = affMul(a, pivotRot(c.pivot, c.rot));
	return a;
}
const allCubes = () => bones.flatMap(b => b.cubes);
function minY(W, filter) {
	let m = Infinity;
	for (const c of allCubes()) {
		if (filter && !filter(c)) continue;
		const a = cubeWorld(c, W);
		for (const p of cubeCorners(c)) m = Math.min(m, affApply(a, p)[1]);
	}
	return m;
}
function bounds(W, filter) {
	const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
	for (const c of allCubes()) {
		if (filter && !filter(c)) continue;
		const a = cubeWorld(c, W);
		for (const p of cubeCorners(c)) { const q = affApply(a, p); for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], q[i]); hi[i] = Math.max(hi[i], q[i]); } }
	}
	return { lo, hi };
}
const FEET = c => c.bone === 'foot_r' || c.bone === 'foot_l';
const KNEES = c => FEET(c) || c.bone === 'shin_r' || c.bone === 'shin_l';

// Ground the rest pose: shift everything so the planted feet stand on y = 0.
{
	const W = worldMatrices({});
	const m = minY(W, FEET);
	for (const b of bones) b.pivot[1] -= m;
	for (const c of allCubes()) { c.from[1] -= m; c.to[1] -= m; if (c.pivot) c.pivot[1] -= m; }
}
const REST_W = worldMatrices({});
const REST_B = bounds(REST_W);
const MODEL_HEIGHT = bounds(REST_W, c => c.paint !== 'spike' && c.mat !== 'hair').hi[1];
const MODEL_SCALE = TITAN_HEIGHT_BLOCKS * 16 / MODEL_HEIGHT;
const PX2BLK = MODEL_SCALE / 16;
console.log(`rest bounds x ${REST_B.lo[0].toFixed(1)}..${REST_B.hi[0].toFixed(1)}  y ${REST_B.lo[1].toFixed(1)}..${REST_B.hi[1].toFixed(1)}  z ${REST_B.lo[2].toFixed(1)}..${REST_B.hi[2].toFixed(1)} px`);
console.log(`MODEL_SCALE = ${MODEL_SCALE.toFixed(4)}f  (rest height ${MODEL_HEIGHT.toFixed(2)} px -> ${TITAN_HEIGHT_BLOCKS} blocks; width ${((REST_B.hi[0] - REST_B.lo[0]) * PX2BLK).toFixed(1)} blocks)`);

// key points (json space, in the bone's own rest coordinates)
const handPoint = n => { const h = B[`hand_${n}`]; return [h.pivot[0], h.pivot[1] - 3.5, h.pivot[2]]; };
function pointWorld(W, boneName, p) { return affApply(W[boneName], p); }

// ================================================================ texture
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
const rects = [];
for (const c of allCubes()) {
	c.uv = {};
	const sz = [c.to[0] - c.from[0], c.to[1] - c.from[1], c.to[2] - c.from[2]];
	for (const f of FACES) {
		const [fw, fh] = (f === 'north' || f === 'south') ? [sz[0], sz[1]] : (f === 'east' || f === 'west') ? [sz[2], sz[1]] : [sz[0], sz[2]];
		const r = { c, f, w: Math.max(1, Math.round(fw * D)), h: Math.max(1, Math.round(fh * D)), fw, fh };
		c.uv[f] = r; rects.push(r);
	}
}
let TH;
{
	const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
	let x = 0, y = 0, rowH = 0;
	for (const r of order) {
		if (x + r.w > TW) { x = 0; y += rowH; rowH = 0; }
		r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
	}
	TH = Math.ceil((y + rowH) / 16) * 16;
}
const tex = Buffer.alloc(TW * TH * 4);
const glow = Buffer.alloc(TW * TH * 4);
const put = (buf, x, y, c, a = 255) => { const i = (y * TW + x) * 4; buf[i] = clamp(Math.round(c[0])); buf[i + 1] = clamp(Math.round(c[1])); buf[i + 2] = clamp(Math.round(c[2])); buf[i + 3] = a; };

// 3D rest-space point of face texel (s, t in 0..1, t = 0 at the top for side faces). Verified in game: GeckoLib maps
// a north face's u from -X to +X (json), i.e. mirrored from a plain right-handed view -- every face follows suit.
function facePoint(c, f, s, t) {
	const [x0, y0, z0] = c.from, [x1, y1, z1] = c.to;
	const X = (a) => x0 + a * (x1 - x0), Y = (a) => y1 - a * (y1 - y0), Z = (a) => z0 + a * (z1 - z0);
	switch (f) {
		case 'north': return [X(s), Y(t), z0];
		case 'south': return [x1 - s * (x1 - x0), Y(t), z1];
		case 'east': return [x1, Y(t), Z(s)];
		case 'west': return [x0, Y(t), z1 - s * (z1 - z0)];
		case 'up': return [x1 - s * (x1 - x0), y1, Z(t)];
		default: return [x1 - s * (x1 - x0), y0, z1 - t * (z1 - z0)];
	}
}

const SKIN_L = [140, 152, 112], SKIN_M = [96, 110, 84], SKIN_D = [48, 58, 46];
const ROT = [92, 80, 50], BRUISE = [82, 60, 82], NECRO = [40, 34, 38], SICK = [158, 154, 100];
const FLESH = [140, 52, 50], FLESH_D = [70, 16, 20], PUS = [168, 162, 86], BLOOD = [86, 22, 20];
const BONE_C = [226, 214, 178], BONE_D = [160, 144, 108];
const MOUTH = [74, 16, 20];
const EYE_HOT = [255, 244, 170], EYE_RING = [255, 160, 40];

// wounds: [x, y, z, r] in rest space (after grounding they shift with the model; positions given pre-shift)
const GROUND_SHIFT = (() => { return 0; })();
// gashes: [x, y, z, size, tilt] -- long, thin, torn wounds (stretched sideways, tilted in the face plane)
const wounds = [
	[-6.0, 37.5, -8.3, 2.6, 0.5], [7.0, 25.5, -6.2, 2.2, -0.4], [-8.8, 26.0, 2.0, 2.0, 0.2], [5.5, 44.0, 5.0, 2.2, -0.6],
	[-13.5, 23.0, -4.3, 1.8, 0.3], [14.0, 31.0, 3.6, 1.6, -0.2], [-5.0, 14.0, -5.3, 1.8, 0.4], [-2.0, 46.5, 7.0, 2.0, 0.1],
	[-10.5, 33.0, 6.5, 2.0, -0.5], [6.0, 16.0, 5.3, 1.6, 0.6],
];
// they were authored before the ground shift: apply the same shift
const SHIFT = B.root ? (B.pelvis.pivot[1] - 20) : 0;
for (const w of wounds) w[1] += SHIFT;
const RIB_REGION = { x0: 0.8, x1: 11.6, y0: 29.2 + SHIFT, y1: 36.2 + SHIFT, zFront: -7.0, zBack: 1.4 };

function skinBase(p, f, s, t, c) {
	// large mottling between grave-grey green and dark olive, plus fine grain
	const n1 = fbm(p[0] * 0.16 + 3, p[1] * 0.16, p[2] * 0.16, 4);
	const n2 = fbm(p[0] * 1.2 + 50, p[1] * 1.2, p[2] * 1.2, 2);
	let col = mix(SKIN_D, SKIN_L, 0.05 + 0.95 * Math.pow(n1, 1.15));
	col = mul(col, 0.88 + 0.22 * n2);
	// sickly pale patches, rot, bruising and black necrotic flesh
	const pale = fbm(p[0] * 0.2 + 130, p[1] * 0.2, p[2] * 0.2, 3);
	if (pale > 0.6) col = mix(col, SICK, (pale - 0.6) * 2.2);
	const r = fbm(p[0] * 0.12 + 70, p[1] * 0.12, p[2] * 0.12, 3);
	if (r > 0.56) col = mix(col, fbm(p[0] * 0.3, p[1] * 0.3 + 9, p[2] * 0.3, 2) > 0.5 ? ROT : BRUISE, (r - 0.56) * 3.0);
	const nec = fbm(p[0] * 0.35 + 410, p[1] * 0.35, p[2] * 0.35, 3);
	if (nec > 0.68) col = mix(col, NECRO, (nec - 0.68) * 4.5);
	// a few dark swollen veins, only in patches
	if (fbm(p[0] * 0.1 + 300, p[1] * 0.1, p[2] * 0.1, 2) > 0.64) {
		const v = vnoise(p[0] * 0.5 + 200, p[1] * 0.28, p[2] * 0.5);
		if (Math.abs(v - 0.5) < 0.02) col = mix(col, [44, 36, 58], 0.6);
	}
	// grime run-off: faint vertical streaks
	const streak = fbm(p[0] * 2.4 + 17, p[1] * 0.18, p[2] * 2.4, 2);
	if (streak > 0.64 && f !== 'up' && f !== 'down') col = mix(col, [52, 46, 34], (streak - 0.64) * 2.0);
	// shading: side faces lit from above and falling off hard toward the bottom (heavy, sagging mass)
	if (f === 'up') col = mul(col, 1.1);
	else if (f === 'down') col = mul(col, 0.55);
	else col = mul(col, 1.1 - 0.36 * Math.pow(t, 1.3));
	// dark seams where the slabs of muscle meet
	const ed = Math.min(s, 1 - s, t, 1 - t);
	const fw = (f === 'north' || f === 'south' || f === 'up' || f === 'down') ? c.to[0] - c.from[0] : c.to[2] - c.from[2];
	const fh = (f === 'up' || f === 'down') ? c.to[2] - c.from[2] : c.to[1] - c.from[1];
	const edPx = Math.min(Math.min(s, 1 - s) * fw, Math.min(t, 1 - t) * fh);
	if (edPx < 0.34) col = mul(col, 0.74);
	else if (edPx < 0.67) col = mul(col, 0.9);
	return col;
}
function applyWounds(p, col, f) {
	for (const [x, y, z, r, tilt] of wounds) {
		// a gash: long across, thin vertically, tilted
		const dx0 = p[0] - x, dz0 = p[2] - z, dy0 = p[1] - y;
		const along = Math.hypot(dx0, dz0);
		const sideSign = Math.sign(dx0 + dz0) || 1;
		const u = along * sideSign, v = dy0 - tilt * u;
		const d = Math.hypot(u / r, v / (r * 0.32), 0);
		const wob = d + (fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2) - 0.5) * 0.5;
		if (wob < 0.45) col = mix([40, 8, 10], FLESH_D, wob * 2.2);
		else if (wob < 0.72) col = mix(FLESH_D, FLESH, (wob - 0.45) * 3.7);
		else if (wob < 0.86) col = mix(FLESH, [184, 104, 92], (wob - 0.72) * 7);
		else if (wob < 1.05) col = mix(col, PUS, (1.05 - wob) * 3.5);
		// old blood running down from it
		if (f !== 'up' && f !== 'down' && d > 0.9 && p[1] < y && y - p[1] < r * 3.2) {
			const lane = vnoise(p[0] * 2.2 + p[2] * 2.2, 5, 9);
			const dx = Math.hypot(p[0] - x, p[2] - z);
			if (dx < r * 0.7 && lane > 0.62 && (y - p[1]) < r * 3.2 * lane) col = mix(col, BLOOD, 0.75);
		}
	}
	return col;
}
function boneCol(p, t, tipUp = true) {
	const n = fbm(p[0] * 1.3, p[1] * 1.3, p[2] * 1.3, 3);
	let col = mix(BONE_D, BONE_C, 0.3 + 0.7 * n);
	if (vnoise(p[0] * 3, p[1] * 3, p[2] * 3) > 0.82) col = mix(col, [90, 76, 58], 0.6);
	return col;
}
function inRibRegion(p, f) {
	const R = RIB_REGION;
	const wob = (fbm(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9, 3) - 0.5) * 1.6;
	if (f === 'north') return p[0] > R.x0 + wob && p[0] < R.x1 && p[1] > R.y0 + wob * 0.5 && p[1] < R.y1 + wob;
	if (f === 'east') return p[2] < R.zBack + wob && p[1] > R.y0 + wob * 0.5 && p[1] < R.y1 + wob;
	return false;
}

function shade(c, f, s, t, p, out) {
	// returns [col, alpha, glowCol|null]
	const P = c.paint;
	let col, a = 255, g = null;
	switch (c.mat) {
		case 'skin': {
			col = skinBase(p, f, s, t, c);
			col = applyWounds(p, col, f);
			if (P === 'chest' || P === 'pecs' || P === 'belly') {
				// exposed, torn-open ribcage on the lower-left front
				if (P === 'chest' && inRibRegion(p, f)) {
					const edge = !inRibRegion([p[0] + 0.35, p[1] + 0.35, p[2]], f) || !inRibRegion([p[0] - 0.35, p[1] - 0.35, p[2]], f);
					col = edge ? mix(FLESH, [180, 96, 88], 0.4) : mix([22, 8, 10], [58, 14, 16], fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
					if (!edge && hash3(Math.floor(p[0] * D), Math.floor(p[1] * D), Math.floor(p[2] * D) + 7) > 0.93) { col = [255, 150, 40]; g = [200, 110, 20]; }
					else if (!edge) g = [26, 8, 0];
				}
				if (P === 'belly' && f === 'north') { // abdominal plates, sagging and split
					const gx = Math.abs(p[0]);
					if (gx < 0.25 || (Math.abs(((p[1] - SHIFT) - 22.6) % 2.6) < 0.22 && gx < 5.5)) col = mul(col, 0.72);
					if (gx > 5.8) col = mul(col, 0.9);
				}
				if (P === 'pecs' && f === 'north' && Math.abs(p[0]) < 0.3) col = mul(col, 0.7);
				if (P === 'pecs' && t > 0.85) col = mul(col, 0.78);
			}
			if (f === 'south' && (P === 'chest' || P === 'belly' || P === 'hump') && Math.abs(p[0]) < 1.1) {
				// the spine ridge showing through the back
				const k = ((p[1] - SHIFT) * 1.1) % 1.6;
				col = k < 1.1 ? mix(boneCol(p, t), col, 0.25) : mul(col, 0.6);
			}
			if (P === 'head') {
				col = headPaint(c, f, s, t, p, col, (gc) => { g = gc; });
			}
			if (P === 'brow') col = mul(mix(col, SKIN_D, 0.3), f === 'down' ? 0.5 : 0.95);
			if (P === 'jaw') {
				if (f === 'up') { col = mix(MOUTH, [120, 40, 44], fbm(p[0] * 1.5, 0, p[2] * 1.5, 2)); g = [30, 6, 0]; }
				if (f === 'north' && t < 0.18) col = mix([110, 40, 44], MOUTH, t * 5); // torn lower lip
				if (f === 'north' && Math.abs(p[0]) < 1.2 && t > 0.55) col = mul(col, 0.85); // chin cleft
			}
			if (P === 'neck' && (f === 'north' || f === 'east' || f === 'west')) { // sinew cords
				if (Math.abs(((p[0] + p[2]) * 1.4) % 1.5) < 0.25) col = mul(col, 0.78);
			}
			if (P === 'ear') col = mul(col, 0.85);
			if (P === 'cheek') col = mul(col, f === 'down' ? 0.6 : 0.92);
			if (P === 'knee') col = mix(col, BONE_D, f === 'north' ? 0.35 : 0.1);
			if (P === 'knuckle') col = mix(col, [80, 70, 64], 0.25);
			if (P === 'hand' && f === 'down') col = mul(col, 1.3);
			if (P === 'finger') col = mul(col, 0.95 - 0.12 * t);
			if (P === 'delt' && f === 'up') col = mul(col, 1.05);
			if (P === 'foot' && f === 'down') col = [40, 38, 34];
			break;
		}
		case 'bone': {
			col = boneCol(p, t);
			if (P === 'spike') {
				// tip pale, root bloody where it tears out of the flesh
				if (f !== 'up' && f !== 'down') col = t > 0.72 ? mix(col, [110, 30, 28], (t - 0.72) * 3.5) : mix(col, [244, 238, 214], (0.3 - t) * 1.5);
				if (f === 'down') col = [110, 30, 28];
			}
			if (P === 'rib') col = mul(col, f === 'down' ? 0.6 : 1.0);
			if (P === 'vert') col = mul(col, f === 'up' ? 1.05 : 0.9);
			break;
		}
		case 'tooth': {
			col = mix([226, 210, 160], [150, 124, 80], t * 0.9 + (vnoise(p[0] * 4, p[1] * 4, 0) - 0.5) * 0.3);
			if (P === 'teeth_up' || P === 'teeth_low') {
				const tw = 0.62; // tooth pitch in model px
				const k = ((p[0] + 10) / tw) % 1;
				const id = Math.floor((p[0] + 10) / tw);
				const len = 0.55 + 0.45 * hash3(id, 3, P === 'teeth_up' ? 1 : 2);
				if (k > 0.78) a = 0;                                   // gaps between teeth
				const tipT = P === 'teeth_up' ? t : 1 - t;             // tips point into the mouth
				if (tipT > len) a = 0;
				if (hash3(id, 9, 4) > 0.9) col = mix(col, [60, 52, 40], 0.7); // a rotten one
				if (P === 'teeth_up' ? t < 0.15 : t > 0.85) col = [116, 40, 46]; // gum line
			}
			if (P === 'fang' || P === 'tusk') {
				const tipT = P === 'tusk' ? 1 - t : t; // upper fangs point down, the lower tusks up
				const half = Math.abs(s - 0.5);
				if (tipT > 0.6 && half > (1 - tipT) * 1.2 && f !== 'up' && f !== 'down') a = 0;
				col = mix([236, 222, 176], [120, 96, 60], Math.pow(1 - tipT, 1.4));
				if (tipT < 0.12) col = [96, 30, 34]; // bloody gum at the root
			}
			break;
		}
		case 'claw': {
			// black horn, cracked, a dirty yellow only at the very tip
			const tip = c.paint === 'toeclaw' ? (f === 'north' ? 1 : f === 'south' ? 0 : s) : t;
			col = mix([30, 26, 24], [64, 56, 46], vnoise(p[0] * 5, p[1] * 5, p[2] * 5));
			if (tip > 0.72) col = mix(col, [176, 156, 108], (tip - 0.72) * 3.2);
			if (f === 'up' && c.paint !== 'toeclaw') col = [70, 26, 24];
			break;
		}
		case 'iron': {
			const n = fbm(p[0] * 1.6, p[1] * 1.6, p[2] * 1.6, 3);
			col = mix([46, 44, 50], [104, 100, 108], n);
			const rust = fbm(p[0] * 0.9 + 30, p[1] * 0.9, p[2] * 0.9, 3);
			if (rust > 0.55) col = mix(col, [130, 72, 36], (rust - 0.55) * 3);
			if (f !== 'up' && f !== 'down') {
				if (t < 0.12 || t > 0.88) col = mul(col, t < 0.12 ? 1.3 : 0.65);      // rolled rims
				const along = f === 'north' || f === 'south' ? s * (c.to[0] - c.from[0]) : s * (c.to[2] - c.from[2]);
				const rv = Math.abs(((along + 0.8) % 2.4) - 1.2);
				if (Math.abs(t - 0.5) < 0.12 && rv < 0.22) col = [178, 172, 168];  // rivets
				else if (Math.abs(t - 0.62) < 0.1 && rv < 0.3) col = mul(col, 0.6);
			}
			if (f === 'up' || f === 'down') col = mul(col, 0.8);
			break;
		}
		case 'chain': {
			const n = vnoise(p[0] * 4, p[1] * 4, p[2] * 4);
			col = mix([52, 50, 54], [126, 120, 124], n);
			if (Math.abs(s - 0.5) < 0.2) col = mul(col, 1.25);
			if (n > 0.7) col = mix(col, [124, 70, 36], 0.6);
			break;
		}
		case 'pants': case 'cloth': case 'rag': {
			const n = fbm(p[0] * 0.8 + 11, p[1] * 0.8, p[2] * 0.8, 3);
			col = mix([40, 36, 34], [92, 82, 66], 0.2 + 0.8 * n);
			if (Math.floor(p[1] * D * 1.0) % 2 === 0) col = mul(col, 0.94);          // weave
			if (fbm(p[0] * 0.4 + 5, p[1] * 0.4, p[2] * 0.4, 2) > 0.62) col = mix(col, [58, 40, 26], 0.5); // stains
			if (c.mat !== 'cloth' && (f === 'up' || f === 'down')) { a = 0; break; }
			if (c.mat === 'pants' || c.mat === 'rag') {
				// ragged hem + tears
				const fh = c.mat === 'rag' ? 3.2 : 2.0;
				const fromBottom = (1 - t) * (c.to[1] - c.from[1]);
				const jag = fbm(p[0] * 1.8 + 2, 0, p[2] * 1.8, 3) * fh * 1.7;
				if (fromBottom < jag) a = 0;
				else if (fromBottom < jag + 0.4) col = mul(col, 0.6);
				const hole = fbm(p[0] * 0.7 + 90, p[1] * 0.7, p[2] * 0.7, 3);
				if (hole > (c.mat === 'rag' ? 0.66 : 0.69)) a = 0;
				else if (hole > (c.mat === 'rag' ? 0.62 : 0.65)) col = mul(col, 0.55);
			}
			if (c.mat === 'cloth' && f === 'down') col = mul(col, 0.6);
			break;
		}
		case 'rope': {
			const k = ((p[1] * 3 + (p[0] + p[2]) * 1.2) % 1 + 1) % 1;
			col = k < 0.5 ? [124, 100, 64] : [80, 62, 38];
			col = mul(col, 0.85 + 0.3 * vnoise(p[0] * 3, p[1] * 3, p[2] * 3));
			break;
		}
		case 'hair': {
			const strand = Math.floor((p[0] + 10) * D * 1.0);
			col = mix([28, 28, 26], [70, 66, 58], hash3(strand, 1, 1));
			if (hash3(strand, 2, 2) > 0.55) a = 0;
			const len = 0.55 + 0.45 * hash3(strand, 5, 5);
			if (t > len) a = 0;
			if (f === 'up' || f === 'down') a = 0;
			break;
		}
		default: col = [255, 0, 255];
	}
	return [col, a, g];
}

function headPaint(c, f, s, t, p, col, setGlow) {
	const y = p[1] - SHIFT - HY, x = p[0], z = p[2] - HZ;
	// a patch of skull where the scalp has rotted away
	const sk = Math.hypot(x - 1.8, (y - 51.5) * 1.2, z + 11.5) / 3.3 + (fbm(x * 1.5, y * 1.5, z * 1.5, 2) - 0.5) * 0.6;
	if ((f === 'up' || f === 'north' || f === 'east') && sk < 1) {
		col = sk > 0.85 ? mix(FLESH, col, 0.4) : boneCol(p, t);
		if (sk < 0.85 && Math.abs(((x * 0.7 + z * 0.4) * 2.3) % 1) < 0.06) col = [92, 78, 60]; // cracks
	}
	if (f === 'up' && sk >= 1) { // thin, matted hair
		if (hash3(Math.floor(x * D), 1, Math.floor(z * D * 0.3)) > 0.6) col = mix([30, 30, 28], col, 0.3);
	}
	if (f === 'down' && z < -6.3) { col = mix(MOUTH, [126, 44, 48], (Math.abs(((x * 2.2) % 1)) < 0.2) ? 0.6 : 0.15); setGlow([30, 6, 0]); }
	if (f === 'north') {
		// deep eye sockets under the brow, burning eyes
		for (const ex of [-2.5, 2.5]) {
			const dx = (x - ex) / 1.55, dy = (y - 47.6) / 1.05;
			const d = Math.hypot(dx, dy);
			if (d < 1) col = mix([18, 12, 12], [52, 40, 36], d * d);
			const de = Math.hypot((x - ex) / 0.9, (y - 47.55) / 0.7);
			if (de < 0.55) { col = EYE_HOT; setGlow(EYE_HOT); }
			else if (de < 1.0) { col = mix(EYE_RING, [140, 40, 10], (de - 0.55) * 2.2); setGlow(mix(EYE_RING, [120, 30, 0], (de - 0.55) * 2.2)); }
		}
		// nose: rotted away to a cavity
		if (y > 44.9 && y < 46.7 && Math.abs(x) < 0.45 + (46.7 - y) * 0.35) col = [26, 14, 14];
		// sunken cheeks
		if (Math.abs(Math.abs(x) - 3.4) < 0.9 && y > 43.8 && y < 46.4) col = mul(col, 0.8);
		// the upper lip is torn back to the gums
		if (y < 44.0) col = mix([120, 40, 46], MOUTH, (44.0 - y) * 1.2);
		// a scar across the face
		if (Math.abs((y - 44.5) - (x + 4) * 0.55) < 0.18 && x < 1.5 && x > -4.8) col = [92, 42, 40];
	}
	return col;
}

for (const r of rects) {
	const { c, f, w, h, u, v } = r;
	for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
		const s = (i + 0.5) / w, t = (j + 0.5) / h;
		const p = facePoint(c, f, s, t);
		const [col, a, g] = shade(c, f, s, t, p);
		put(tex, u + i, v + j, col, a);
		if (g && a) put(glow, u + i, v + j, g);
	}
}

// ---------------------------------------------------------------- geo json
const R3 = n => Math.round(n * 1000) / 1000;
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.titan', texture_width: TW, texture_height: TH,
			visible_bounds_width: 8, visible_bounds_height: 6, visible_bounds_offset: [0, 2.5, 0],
		},
		bones: bones.map(b => {
			const o = { name: b.name };
			if (b.parent) o.parent = b.parent;
			o.pivot = b.pivot.map(R3);
			if (b.rot.some(v => v !== 0)) o.rotation = b.rot.map(R3);
			if (b.cubes.length) o.cubes = b.cubes.map(c => {
				const cc = { origin: c.from.map(R3), size: [0, 1, 2].map(i => R3(c.to[i] - c.from[i])) };
				if (c.rot) { cc.pivot = c.pivot.map(R3); cc.rotation = c.rot.map(R3); }
				cc.uv = {};
				for (const f of FACES) { const r = c.uv[f]; cc.uv[f] = { uv: [r.u, r.v], uv_size: [r.w, r.h] }; }
				return cc;
			});
			return o;
		}),
	}],
};
const writeJson = (p, obj) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, '\t') + '\n'); };
writeJson(path.join(RES, 'geo/titan.geo.json'), geo);
fs.writeFileSync(path.join(RES, 'textures/entity/titan.png'), encodePng(TW, TH, tex));
fs.writeFileSync(path.join(RES, 'textures/entity/titan_glowmask.png'), encodePng(TW, TH, glow));
console.log(`geo: ${bones.length} bones, ${allCubes().length} cubes; texture ${TW}x${TH} (${D} texels/px)`);

// ================================================================ animations
// A frame: [tick, pose, easing?]. pose = { bone: [x,y,z] | number(x only), $ground: 'feet'|'knees'|'all'|false, $y: extra root y }
const ANIM_BONES = bones.map(b => b.name).filter(n => n !== 'neck' && n !== 'flinch');
const UPPER_BONES = ['waist', 'chest', 'head', 'jaw', 'arm_l', 'forearm_l', 'hand_l', 'fingers_l', 'chain_l', 'arm_r', 'forearm_r', 'hand_r', 'fingers_r', 'chain_r'];
function norm(pose) {
	const o = {};
	for (const k of Object.keys(pose)) {
		if (k.startsWith('$')) continue;
		const v = pose[k];
		o[k] = typeof v === 'number' ? [v, 0, 0] : v;
	}
	return o;
}
// left/right mirror of a pose (swap _r/_l, negate y and z rotations)
function mirror(pose) {
	const o = {};
	for (const k of Object.keys(pose)) {
		if (k.startsWith('$')) { o[k] = pose[k]; continue; }
		const v = typeof pose[k] === 'number' ? [pose[k], 0, 0] : pose[k];
		const nk = k.endsWith('_r') ? k.slice(0, -2) + '_l' : k.endsWith('_l') ? k.slice(0, -2) + '_r' : k;
		o[nk] = [v[0], -v[1], -v[2]];
	}
	return o;
}
const merge = (...ps) => Object.assign({}, ...ps);
function rootFor(pose) {
	const g = pose.$ground === undefined ? 'feet' : pose.$ground;
	const extra = pose.$y || 0;
	const z = pose.$z || 0;
	if (!g) return [0, extra, z];
	const W = worldMatrices(norm(pose), [0, 0, z]);
	const TORSO = c => c.bone === 'pelvis' || c.bone === 'waist' || c.bone === 'chest';
	const filt = g === 'feet' ? FEET : g === 'knees' ? KNEES : g === 'torso' ? TORSO : null;
	return [0, -minY(W, filt) + extra, z];
}
const animations = {};
const clipInfo = {};
function kfVal(v, easing) { const r = v.map(n => Math.round(n * 1000) / 1000); return easing ? { vector: r, easing } : r; }
function clip(name, lengthTicks, loop, frames, opts = {}) {
	const boneSet = opts.bones || ANIM_BONES;
	const out = {};
	const poses = frames.map(([t, p, e]) => ({ t, p: norm(p), root: boneSet.includes('root') ? rootFor(p) : [0, 0, 0], e }));
	for (const bn of boneSet) {
		const vals = poses.map(fr => fr.p[bn] || [0, 0, 0]);
		const any = vals.some(v => v.some(x => Math.abs(x) > 1e-6));
		if (any || opts.keyAll) {
			out[bn] = out[bn] || {};
			const k = {};
			poses.forEach((fr, i) => { k[(fr.t / 20).toFixed(4)] = kfVal(vals[i], fr.e); });
			out[bn].rotation = k;
		}
		if (bn === 'root') {
			const rv = poses.map(fr => fr.root);
			if (rv.some(v => v.some(x => Math.abs(x) > 1e-4))) {
				out.root = out.root || {};
				const k = {};
				poses.forEach((fr, i) => { k[(fr.t / 20).toFixed(4)] = kfVal(rv[i], fr.e); });
				out.root.position = k;
			}
		}
	}
	const a = { animation_length: lengthTicks / 20, bones: out };
	if (loop === true) a.loop = true;
	else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	animations[`animation.titan.${name}`] = a;
	clipInfo[name] = { length: lengthTicks, frames: poses };
	return poses;
}

// ---- shared poses
const REST = {};
const FIST_R = { fingers_r: -95 }, FIST_L = { fingers_l: -95 };
const OPEN_R = { fingers_r: 30 }, OPEN_L = { fingers_l: 30 };
const chainsSwing = (a) => ({ chain_r: [a, 0, -a * 0.6], chain_l: [a, 0, a * 0.6], chain_ankle: [a * 0.8, 0, 0] });

// ---- idle: heavy, wet breathing, swaying, the jaw working on each exhale
clip('idle', 80, true, [
	[0, merge({ chest: -1, head: [2, 0, 0], jaw: 4, pelvis: [0, -1.5, 0] }, chainsSwing(2))],
	[30, merge({ chest: -5.5, waist: -1.5, head: [-4, 4, 2], jaw: 2, arm_r: [-3, 0, 3], arm_l: [-3, 0, -3], fingers_r: -8, fingers_l: -6, pelvis: [0, 1.5, 0] }, chainsSwing(-3)), 'easeinoutsine'],
	[55, merge({ chest: 2.5, waist: 1, head: [5, -3, -2], jaw: 16, arm_r: [2, 0, -1], arm_l: [2, 0, 1], fingers_r: 4, pelvis: [0, 0, 0] }, chainsSwing(4)), 'easeinoutsine'],
	[80, merge({ chest: -1, head: [2, 0, 0], jaw: 4, pelvis: [0, -1.5, 0] }, chainsSwing(2)), 'easeinoutsine'],
]);

// ---- locomotion. Contact pose = right leg forward. Cycle lengths are derived from the stride so the feet
// don't skate at the speed the Titan actually moves (WALK_SPEED / RUN_SPEED, blocks per tick, measured in-game).
const WALK_SPEED = 0.194, RUN_SPEED = 0.487; // measured with the harness (v0.14.20)
function gait(name, A, speed) {
	const contact = merge({
		leg_r: -A.th, shin_r: A.kc, foot_r: A.th - A.kc + 6,
		leg_l: A.th * 0.85, shin_l: A.kb, foot_l: -A.th * 0.85 - A.kb + 14,
		pelvis: [0, -A.yaw, 2.5], waist: [A.lean, A.yaw * 0.6, 0], chest: [A.lean2 + 3, A.yaw * 0.9, -2],
		head: [A.head - 3, -A.yaw * 1.3, 2], jaw: A.jaw,
		arm_r: [A.arm, 0, 4], forearm_r: A.fa, arm_l: [-A.arm * 1.1, 0, -2], forearm_l: -A.fa,
		fingers_r: A.fing, fingers_l: A.fing, flap_f: -6, flap_b: 5,
	}, chainsSwing(-6));
	const passing = merge({
		leg_r: 0, shin_r: 0, foot_r: 0,
		leg_l: -A.lift, shin_l: A.lift * 1.9, foot_l: -A.lift * 0.6,
		pelvis: [0, 0, -1], waist: [A.lean, 0, 0], chest: [A.lean2 - 1.5, 0, 0],
		head: [A.head + 2, 0, 0], jaw: A.jaw * 0.4,
		arm_r: [A.arm * 0.2, 0, 0], arm_l: [-A.arm * 0.2, 0, 0], forearm_r: A.fa * 0.5, forearm_l: A.fa * 0.5,
		fingers_r: A.fing, fingers_l: A.fing, flap_f: 3, flap_b: -3, $y: A.rise,
	}, chainsSwing(5));
	// stride: how far the planted foot travels from contact to the next contact (both ways), in blocks
	const Wc = worldMatrices(norm(contact));
	const fr = pointWorld(Wc, 'foot_r', [B.foot_r.pivot[0], 0, -2]), fl = pointWorld(Wc, 'foot_l', [B.foot_l.pivot[0], 0, -2]);
	const stepBlocks = Math.abs(fr[2] - fl[2]) * PX2BLK;
	const cycle = Math.max(10, Math.round(2 * stepBlocks / speed / 2) * 2);
	const q = cycle / 4;
	clip(name, cycle, true, [
		[0, contact],
		[q, passing, 'easeinoutsine'],
		[2 * q, mirror(contact), 'easeinoutsine'],
		[3 * q, mirror(passing), 'easeinoutsine'],
		[4 * q, contact, 'easeinoutsine'],
	]);
	console.log(`${name}: step ${stepBlocks.toFixed(2)} blocks, cycle ${cycle} ticks for ${speed} b/t`);
	return cycle;
}
const WALK_CYCLE = gait('walk', { th: 24, kc: -12, kb: 16, lift: 22, yaw: 5, lean: 2, lean2: 2, head: 0, jaw: 8, arm: 16, fa: -6, fing: -10, rise: 0.6 }, WALK_SPEED);
const RUN_CYCLE = gait('run', { th: 32, kc: -14, kb: 24, lift: 32, yaw: 7, lean: 10, lean2: 8, head: -14, jaw: 24, arm: 30, fa: -24, fing: -25, rise: 1.2 }, RUN_SPEED);

// ---- PUNCH: a right haymaker. Strike at STRIKE.
{
	const cock = { waist: [-2, 30, 0], chest: [-8, 22, 0], head: [6, -40, 0], jaw: 14, arm_r: [40, 30, 30], forearm_r: -75, hand_r: 10, ...FIST_R, arm_l: [-45, 0, -8], forearm_l: -30, leg_r: 10, shin_r: 6, foot_r: -16, leg_l: -16, shin_l: 10, foot_l: 6 };
	const hit = { waist: [10, -12, 0], chest: [14, -10, 0], head: [-14, 18, 0], jaw: 30, arm_r: [-92, -4, 0], forearm_r: 22, hand_r: -5, ...FIST_R, arm_l: [25, 0, -18], forearm_l: -40, leg_r: 24, shin_r: 4, foot_r: -28, leg_l: -30, shin_l: 18, foot_l: 12 };
	clip('punch', 60, 'hold', [
		[0, REST],
		[14, cock, 'easeoutquad'],
		[STRIKE - 4, merge(cock, { waist: [-3, 38, 0], chest: [-10, 26, 0], arm_r: [48, 34, 34], forearm_r: -82, jaw: 20 }), 'easeinoutsine'],
		[STRIKE, hit, 'easeinquart'],
		[STRIKE + 10, merge(hit, { chest: [16, -26, 0], jaw: 22 }), 'easeoutquad'],
		[60, REST, 'easeinoutsine'],
	]);
}
// ---- SWEEP: a wide right backhand -- wound across the body to the left, ripped out to the right.
{
	const wind = { waist: [4, -32, 0], chest: [8, -26, -4], head: [-4, 36, 0], jaw: 12, arm_r: [-70, -55, -30], forearm_r: -50, hand_r: [0, -20, 0], arm_l: [20, 0, -30], forearm_l: -20, ...OPEN_R, leg_r: -12, shin_r: 10, foot_r: 2, leg_l: 10, shin_l: 8, foot_l: -18 };
	const thru = { waist: [12, 36, 0], chest: [18, 28, 6], head: [-14, -30, 0], jaw: 30, arm_r: [-78, 70, 32], forearm_r: -10, hand_r: [0, 25, 0], arm_l: [-30, 0, -40], forearm_l: -20, ...OPEN_R, leg_r: 12, shin_r: 14, foot_r: -26, leg_l: -18, shin_l: 18, foot_l: 0 };
	clip('sweep', 60, 'hold', [
		[0, REST],
		[16, wind, 'easeoutquad'],
		[STRIKE - 4, merge(wind, { waist: [5, -40, 0], chest: [9, -32, -6], arm_r: [-72, -62, -34] }), 'easeinoutsine'],
		[STRIKE, merge(thru, { waist: [10, 5, 0], chest: [14, 2, 2], arm_r: [-80, 10, 6], hand_r: 0 }), 'easeinquad'],
		[STRIKE + 5, thru, 'easeoutquad'],
		[STRIKE + 12, thru],
		[60, REST, 'easeinoutsine'],
	]);
}
// ---- STOMP: right knee high, arms out for balance, then the foot comes down.
{
	const up = { leg_r: -80, shin_r: 96, foot_r: -4, leg_l: 4, shin_l: 8, foot_l: -12, waist: -8, chest: [-10, 0, 6], head: [-6, 0, 0], jaw: 18, arm_r: [18, 0, 42], arm_l: [18, 0, -42], forearm_r: -20, forearm_l: -20, pelvis: [0, 0, -6], ...OPEN_R, ...OPEN_L };
	const down = { leg_r: -18, shin_r: 6, foot_r: 12, leg_l: 10, shin_l: 18, foot_l: -28, waist: 12, chest: [16, 0, -2], head: [-18, 0, 0], jaw: 34, arm_r: [10, 0, 20], arm_l: [10, 0, -20], forearm_r: -30, forearm_l: -30, ...FIST_R, ...FIST_L, chain_r: [30, 0, 0], chain_l: [30, 0, 0] };
	clip('stomp', 60, 'hold', [
		[0, REST],
		[16, up, 'easeoutquad'],
		[STRIKE - 3, merge(up, { leg_r: -86, shin_r: 70, chest: [-14, 0, 8], jaw: 24, $ground: 'feet' }), 'easeinoutsine'],
		[STRIKE, down, 'easeinquart'],
		[STRIKE + 10, merge(down, { chest: [12, 0, 0], jaw: 26 })],
		[60, REST, 'easeinoutsine'],
	]);
}
// ---- SLAM: both fists overhead, leaning back -- then a double hammer-fist into the ground.
{
	const up = { waist: -10, chest: -22, head: [-12, 0, 0], jaw: 16, arm_r: [-150, 0, -14], arm_l: [-150, 0, 14], forearm_r: -35, forearm_l: -35, ...FIST_R, ...FIST_L, leg_r: -4, leg_l: -4, shin_r: 4, shin_l: 4 };
	const down = { waist: 22, chest: 36, head: [-34, 0, 0], jaw: 34, arm_r: [-55, 0, -8], arm_l: [-55, 0, 8], forearm_r: -2, forearm_l: -2, ...FIST_R, ...FIST_L, leg_r: -26, shin_r: 42, foot_r: -16, leg_l: -26, shin_l: 42, foot_l: -16, chain_r: 40, chain_l: 40 };
	clip('slam', 60, 'hold', [
		[0, REST],
		[16, up, 'easeoutquad'],
		[STRIKE - 4, merge(up, { waist: -14, chest: -28, arm_r: [-168, 0, -14], arm_l: [-168, 0, 14], jaw: 26, chain_r: -40, chain_l: -40 }), 'easeinoutsine'],
		[STRIKE, down, 'easeinquart'],
		[STRIKE + 10, merge(down, { chest: 33, waist: 20 })],
		[60, REST, 'easeinoutsine'],
	]);
}
// ---- SHOCKWAVE: arms spread wide and raised (gathering), then a deep squat driving both fists down.
{
	const gather = { waist: -10, chest: -16, head: [-28, 0, 0], jaw: 36, arm_r: [26, 0, 100], arm_l: [26, 0, -100], forearm_r: -25, forearm_l: -25, ...OPEN_R, ...OPEN_L, leg_r: [0, 0, 6], leg_l: [0, 0, -6], foot_r: [0, 0, -6], foot_l: [0, 0, 6] };
	const blast = { waist: 22, chest: 30, head: [-28, 0, 0], jaw: 40, arm_r: [-30, 0, 24], arm_l: [-30, 0, -24], forearm_r: -10, forearm_l: -10, ...FIST_R, ...FIST_L, leg_r: [-34, 0, 12], shin_r: 62, foot_r: [-28, 0, -12], leg_l: [-34, 0, -12], shin_l: 62, foot_l: [-28, 0, 12] };
	clip('shockwave', 62, 'hold', [
		[0, REST],
		[18, gather, 'easeoutquad'],
		[STRIKE - 4, merge(gather, { arm_r: [26, 0, 125], arm_l: [26, 0, -125], chest: -20, jaw: 42 }), 'easeinoutsine'],
		[STRIKE, blast, 'easeinquart'],
		[STRIKE + 12, merge(blast, { chest: 26 })],
		[62, REST, 'easeinoutsine'],
	]);
}
// ---- GRAB: right claw raised and drawn back, open -- then a lunging downward snatch.
// Move one hand's palm onto a target given in blocks (up, forward, right of the Titan's feet) by coordinate descent
// over the listed [bone, axis] angles -- used to put the grab fist exactly where the server holds the victim.
function solveHand(pose, n, target, vars, mask = [1, 1, 1]) {
	const tgt = [-target[2] / PX2BLK, target[0] / PX2BLK, -target[1] / PX2BLK];
	const p = norm(pose);
	const root = rootFor(pose);
	const err = () => {
		const h = pointWorld(worldMatrices(p, root), `hand_${n}`, handPoint(n));
		return Math.hypot((h[0] - tgt[0]) * mask[2], (h[1] - tgt[1]) * mask[0], (h[2] - tgt[2]) * mask[1]);
	};
	let step = 8;
	while (step > 0.05) {
		let improved = false;
		for (const [bn, ax] of vars) for (const d of [step, -step]) {
			const e0 = err();
			p[bn] = (p[bn] || [0, 0, 0]).slice();
			p[bn][ax] += d;
			if (err() < e0 - 1e-9) improved = true; else p[bn][ax] -= d;
		}
		if (!improved) step /= 2;
	}
	console.log(`solveHand ${n}: residual ${(err() * PX2BLK).toFixed(2)} blocks -> ${vars.map(([b, a]) => b + '[' + a + ']=' + p[b][a].toFixed(1)).join(' ')}`);
	return merge(pose, Object.fromEntries(vars.map(([bn]) => [bn, p[bn]])));
}
// the server pins the victim's feet at (up 0.62 h, forward 1.05 w, right 0.5 w); the fist closes round their middle
const GRAB_HOLD = solveHand({ waist: [4, 10, 0], chest: [2, 8, 0], head: [-4, 24, 4], jaw: 34, arm_r: [-60, 0, -6], forearm_r: -70, hand_r: [-20, 0, 0], fingers_r: -70, arm_l: [5, 0, -22], forearm_l: -25, leg_r: 14, shin_r: 10, foot_r: -24, leg_l: -18, shin_l: 14, foot_l: 4 },
	'r', [0.62 * 18 + 0.9, 1.05 * 0.6 * 18 / 1.95, 0.5 * 0.6 * 18 / 1.95], [['arm_r', 0], ['arm_r', 1], ['forearm_r', 0], ['arm_r', 2]]);
{
	const reach = { waist: [-2, 22, 0], chest: [-6, 16, 0], head: [8, -24, 0], jaw: 16, arm_r: [-128, -18, 22], forearm_r: -22, ...OPEN_R, arm_l: [-30, 0, -15], forearm_l: -30, leg_r: 6, leg_l: -6 };
	const snatch = { waist: [14, -18, 0], chest: [20, -12, 0], head: [-18, 22, 0], jaw: 30, arm_r: [-70, -8, 2], forearm_r: -12, fingers_r: -80, arm_l: [10, 0, -24], forearm_l: -24, leg_r: 18, shin_r: 10, foot_r: -28, leg_l: -28, shin_l: 18, foot_l: 10 };
	clip('grab', 60, 'hold', [
		[0, REST],
		[16, reach, 'easeoutquad'],
		[STRIKE - 4, merge(reach, { arm_r: [-140, -22, 26], fingers_r: 40, jaw: 22 }), 'easeinoutsine'],
		[STRIKE, snatch, 'easeinquart'],
		[STRIKE + 10, snatch],
		[60, REST, 'easeinoutsine'],
	]);
}
// ---- GRAB_THROW: the grab landed. Hold the victim up at the face and roar (server holds them there for 30
// ticks), a short wind back, then hurl them (server throw at tick 30 -> clip tick 26).
{
	const THROW = 30 - TRANSITION;
	const windBack = merge(GRAB_HOLD, { waist: [-6, 26, 0], chest: [-14, 22, 0], arm_r: [-150, 20, 10], forearm_r: -40, head: [4, -10, 0], jaw: 20 });
	const hurl = { waist: [16, -26, 0], chest: [22, -20, 0], head: [-16, 18, 0], jaw: 36, arm_r: [-60, -34, -4], forearm_r: 0, fingers_r: 35, arm_l: [20, 0, -28], forearm_l: -30, leg_r: 22, shin_r: 6, foot_r: -28, leg_l: -30, shin_l: 18, foot_l: 12 };
	clip('grab_throw', 48, 'hold', [
		[0, GRAB_HOLD],
		[5, merge(GRAB_HOLD, { head: [-4, 30, -6], jaw: 42, chest: [6, -10, 2] })],
		[10, merge(GRAB_HOLD, { head: [-10, 22, 6], jaw: 26, arm_r: [-64, -12, -5] })],
		[15, merge(GRAB_HOLD, { head: [-4, 30, -6], jaw: 44, chest: [6, -10, -2] })],
		[THROW - 6, merge(GRAB_HOLD, { jaw: 30 })],
		[THROW - 2, windBack, 'easeoutquad'],
		[THROW, hurl, 'easeinquart'],
		[THROW + 8, hurl],
		[48, REST, 'easeinoutsine'],
	]);
}
// ---- BOULDER: claws dig into the ground, rip a rock out, heave it overhead, throw (release at STRIKE).
const BOULDER_SHOW = [14, STRIKE]; // clip ticks the rock is drawn in the hands (renderer adds TRANSITION)
{
	// both palms closed on a ~4.5-block rock: the right hand is solved onto its side of the rock, the left mirrors it
	const solveBoth = (pose, up, fwd, halfGap) => {
		const s = solveHand(pose, 'r', [up, fwd, halfGap], [['arm_r', up > 15 ? 2 : 1]], [0, 0, 1]);
		const ar = s.arm_r, fr = typeof s.forearm_r === 'number' ? [s.forearm_r, 0, 0] : s.forearm_r;
		return merge(s, { arm_l: [ar[0], -ar[1], -ar[2]], forearm_l: [fr[0], -fr[1], -fr[2]] });
	};
	const GAP = 2.5;
	const dig = solveBoth({ waist: 32, chest: 30, head: [-50, 0, 0], jaw: 10, arm_r: [-32, 0, -12], forearm_r: -10, fingers_r: -20, fingers_l: -20, leg_r: -30, shin_r: 56, foot_r: -26, leg_l: -30, shin_l: 56, foot_l: -26 }, 1.6, 7.0, GAP);
	const lift = solveBoth({ waist: 6, chest: -4, head: [-8, 0, 0], jaw: 18, arm_r: [-72, 0, 0], forearm_r: -35, fingers_r: -60, fingers_l: -60, leg_r: -8, shin_r: 12, leg_l: -8, shin_l: 12 }, 10.5, 5.5, GAP);
	const over = solveBoth({ waist: -10, chest: -20, head: [-22, 0, 0], jaw: 26, arm_r: [-150, 0, -20], forearm_r: -22, fingers_r: -60, fingers_l: -60, leg_r: 6, shin_r: 6, foot_r: -12, leg_l: -14, shin_l: 10, foot_l: 4 }, 18.5, -1.0, GAP);
	// where the rock's centre sits in the right hand's own rest space (TitanRenderer.HeldBoulderLayer ROCK_*)
	for (const [nm, ps] of [['dig', dig], ['lift', lift], ['over', over]]) {
		const W = worldMatrices(norm(ps), rootFor(ps));
		const a = pointWorld(W, 'hand_r', handPoint('r')), b = pointWorld(W, 'hand_l', handPoint('l'));
		const mid = [0, 1, 2].map(i => (a[i] + b[i]) / 2);
		const H = W.hand_r, d = [0, 1, 2].map(i => mid[i] - H.t[i]);
		const loc = [0, 1, 2].map(j => H.m[0][j] * d[0] + H.m[1][j] * d[1] + H.m[2][j] * d[2]);
		console.log(`boulder ${nm}: rock centre in hand_r space ${loc.map(v => v.toFixed(2)).join(', ')} (palm gap ${(Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]) * PX2BLK).toFixed(2)} blocks)`);
	}
	const thrown = { waist: 16, chest: 26, head: [-26, 0, 0], jaw: 36, arm_r: [-78, 0, -10], arm_l: [-78, 0, 10], forearm_r: 0, forearm_l: 0, ...OPEN_R, ...OPEN_L, leg_r: 22, shin_r: 6, foot_r: -28, leg_l: -30, shin_l: 20, foot_l: 10 };
	clip('boulder', 60, 'hold', [
		[0, REST],
		[10, dig, 'easeoutquad'],
		[14, merge(dig, { fingers_r: -95, fingers_l: -95, chest: 26, jaw: 24 })],
		[22, lift, 'easeinoutsine'],
		[STRIKE - 4, over, 'easeinoutsine'],
		[STRIKE, thrown, 'easeinquart'],
		[STRIKE + 10, thrown],
		[60, REST, 'easeinoutsine'],
	]);
}
// ---- CHARGE: head down, arms back, pawing the ground twice; then (charge_run) a knuckle-dragging gallop.
{
	const ready = { waist: 16, chest: 22, head: [-38, 0, 0], jaw: 32, arm_r: [60, 0, 18], arm_l: [60, 0, -18], forearm_r: -20, forearm_l: -20, ...FIST_R, ...FIST_L, leg_r: -14, shin_r: 26, foot_r: -12, leg_l: -14, shin_l: 26, foot_l: -12 };
	const paw = (up) => merge(ready, up ? { leg_r: -40, shin_r: 50, foot_r: 0, jaw: 22 } : { leg_r: 18, shin_r: 18, foot_r: -36, jaw: 36 });
	clip('charge', STRIKE, 'hold', [
		[0, REST],
		[8, paw(true), 'easeoutquad'],
		[14, paw(false), 'easeinquad'],
		[21, paw(true), 'easeoutquad'],
		[27, paw(false), 'easeinquad'],
		[STRIKE, ready, 'easeinoutsine'],
	]);
	const g1 = { waist: 18, chest: 30, head: [-46, 0, 0], jaw: 34, arm_r: [-75, 0, 10], forearm_r: -10, arm_l: [45, 0, -12], forearm_l: -30, ...FIST_R, ...FIST_L, leg_r: 30, shin_r: 28, foot_r: -30, leg_l: -40, shin_l: 30, foot_l: 8, chain_r: 30, chain_l: -30 };
	const mid = { waist: 18, chest: 28, head: [-42, 0, 0], jaw: 30, arm_r: [-10, 0, 10], arm_l: [-10, 0, -10], forearm_r: -20, forearm_l: -20, ...FIST_R, ...FIST_L, leg_r: -10, shin_r: 60, foot_r: -10, leg_l: 0, shin_l: 10, foot_l: -6, $y: 1.6 };
	clip('charge_run', 16, true, [
		[0, g1],
		[4, mid, 'easeinoutsine'],
		[8, mirror(g1), 'easeinoutsine'],
		[12, mirror(mid), 'easeinoutsine'],
		[16, g1, 'easeinoutsine'],
	]);
}
// ---- LEAP: a deep crouch (held), the airborne tuck (held), the landing.
const CROUCH = { waist: 26, chest: 24, head: [-46, 0, 0], jaw: 18, arm_r: [70, 0, 18], arm_l: [70, 0, -18], forearm_r: -20, forearm_l: -20, ...FIST_R, ...FIST_L, leg_r: -48, shin_r: 88, foot_r: -40, leg_l: -48, shin_l: 88, foot_l: -40 };
clip('leap', STRIKE, 'hold', [
	[0, REST],
	[20, merge(CROUCH, { waist: 18, chest: 18, leg_r: -34, shin_r: 66, foot_r: -32, leg_l: -34, shin_l: 66, foot_l: -32, arm_r: [50, 0, 14], arm_l: [50, 0, -14] }), 'easeoutquad'],
	[STRIKE, CROUCH, 'easeinoutsine'],
]);
{
	const tuck = { waist: 10, chest: 8, head: [-20, 0, 0], jaw: 36, arm_r: [-150, 0, -20], arm_l: [-150, 0, 20], forearm_r: -60, forearm_l: -60, ...FIST_R, ...FIST_L, leg_r: -62, shin_r: 82, foot_r: -20, leg_l: -50, shin_l: 76, foot_l: -20, chain_r: -60, chain_l: -60, $ground: false, $y: 0 };
	clip('leap_air', 14, 'hold', [
		[0, merge(CROUCH, { $ground: 'feet' })],
		[4, { waist: -8, chest: -16, head: [-18, 0, 0], jaw: 40, arm_r: [-165, 0, -10], arm_l: [-165, 0, 10], forearm_r: -10, forearm_l: -10, leg_r: 18, shin_r: -6, foot_r: 26, leg_l: 18, shin_l: -6, foot_l: 26, $ground: false, $y: 0 }, 'easeoutquad'],
		[14, tuck, 'easeinoutsine'],
	]);
}
{
	const impact = { waist: 30, chest: 40, head: [-40, 0, 0], jaw: 40, arm_r: [-40, 0, 26], arm_l: [-40, 0, -26], forearm_r: -10, forearm_l: -10, ...FIST_R, ...FIST_L, leg_r: [-46, 0, 14], shin_r: 86, foot_r: [-40, 0, -14], leg_l: [-46, 0, -14], shin_l: 86, foot_l: [-40, 0, 14], chain_r: 50, chain_l: 50 };
	clip('leap_land', 30, 'hold', [
		[0, impact],
		[10, merge(impact, { chest: 34, jaw: 30, head: [-36, 0, 0] })],
		[30, REST, 'easeinoutsine'],
	]);
}
// ---- ROAR: rears back inhaling, arms spread; then a roaring lunge, jaw wide, the whole body shuddering.
const ROAR_PEAK = { waist: 2, chest: 6, head: [-46, 0, 0], jaw: 48, arm_r: [24, 0, 84], arm_l: [24, 0, -84], forearm_r: -35, forearm_l: -35, hand_r: [0, 0, 20], hand_l: [0, 0, -20], ...OPEN_R, ...OPEN_L, leg_r: [-14, 0, 6], shin_r: 24, foot_r: [-10, 0, -6], leg_l: [-14, 0, -6], shin_l: 24, foot_l: [-10, 0, 6], chain_r: -30, chain_l: -30 };
function shudder(base, from, to, step, amp) {
	const out = [];
	let i = 0;
	for (let t = from; t <= to; t += step, i++) {
		const sgn = i % 2 ? 1 : -1;
		out.push([t, merge(base, {
			head: [base.head[0] + sgn * amp * 0.4, sgn * amp, -sgn * amp * 0.8],
			chest: [(Array.isArray(base.chest) ? base.chest[0] : base.chest) + sgn * amp * 0.25, 0, sgn * amp * 0.35],
			jaw: base.jaw - (i % 3 === 0 ? 4 : 0),
		})]);
	}
	return out;
}
{
	const inhale = { waist: -14, chest: -22, head: [-24, 0, 0], jaw: 8, arm_r: [18, 0, 58], arm_l: [18, 0, -58], forearm_r: -40, forearm_l: -40, ...OPEN_R, ...OPEN_L, leg_r: 4, leg_l: 4 };
	clip('roar', 82, 'hold', [
		[0, REST],
		[20, inhale, 'easeoutquad'],
		[STRIKE - 3, merge(inhale, { waist: -18, chest: -27, head: [-34, 0, 0], arm_r: [14, 0, 100], arm_l: [14, 0, -100], jaw: 12 }), 'easeinoutsine'],
		[STRIKE, ROAR_PEAK, 'easeinquart'],
		...shudder(ROAR_PEAK, STRIKE + 3, STRIKE + 30, 3, 5),
		[STRIKE + 34, merge(ROAR_PEAK, { jaw: 28, arm_r: [24, 0, 50], arm_l: [24, 0, -50] })],
		[82, REST, 'easeinoutsine'],
	]);
	// INTRO: just transformed -- rises from all fours and roars.
	const fours = { waist: 40, chest: 34, head: [-56, 0, 0], jaw: 6, arm_r: [-40, 0, 8], arm_l: [-40, 0, -8], forearm_r: -10, forearm_l: -10, fingers_r: -30, fingers_l: -30, leg_r: -40, shin_r: 90, foot_r: -50, leg_l: -40, shin_l: 90, foot_l: -50, $ground: 'knees' };
	clip('intro', 52, 'hold', [
		[0, fours],
		[8, merge(fours, { jaw: 20, head: [-50, 8, 0] })],
		[20, merge(inhale, { waist: -6, chest: -16, leg_r: -8, shin_r: 14, leg_l: -8, shin_l: 14 }), 'easeinoutsine'],
		[24, ROAR_PEAK, 'easeinquart'],
		...shudder(ROAR_PEAK, 27, 42, 3, 5),
		[52, REST, 'easeinoutsine'],
	]);
}
// ---- SWAT (upper body only, on the "upper" controller): a quick left backhand chop. Server hits at tick 10.
{
	const SWAT_HIT = 10 - UPPER_TRANSITION;
	const raise = { waist: [-2, -18, 0], chest: [-8, -16, 0], head: [6, 14, 0], jaw: 14, arm_l: [-150, 14, -18], forearm_l: -40, fingers_l: 30, arm_r: [-10, 0, 10] };
	const chop = { waist: [8, 18, 0], chest: [16, 20, 0], head: [-14, -16, 0], jaw: 30, arm_l: [-55, -32, 12], forearm_l: -5, fingers_l: 35, arm_r: [10, 0, 14] };
	clip('swat', 22, false, [
		[0, REST],
		[SWAT_HIT - 2, raise, 'easeoutquad'],
		[SWAT_HIT, chop, 'easeinquart'],
		[SWAT_HIT + 5, chop],
		[22, REST, 'easeinoutsine'],
	], { bones: UPPER_BONES });
}
// ---- DEATH: staggers back roaring, drops to its knees, sways, then topples face-first into the ground.
const DEATH_IMPACT = 62; // clip tick the body hits the ground (the server's dust burst = this + TRANSITION)
{
	const recoil = { waist: -16, chest: -26, head: [-30, 0, 12], jaw: 48, arm_r: [10, 0, 55], arm_l: [10, 0, -55], forearm_r: -30, forearm_l: -30, ...OPEN_R, ...OPEN_L, leg_r: 10, shin_r: 6, foot_r: -10, leg_l: -14, shin_l: 30, foot_l: -8, chain_r: -50, chain_l: -50 };
	const kneel = { waist: -6, chest: -8, head: [24, 14, 8], jaw: 26, arm_r: [-6, 0, -4], arm_l: [-6, 0, 4], forearm_r: 10, forearm_l: 10, fingers_r: 10, fingers_l: 10, leg_r: [10, 0, 6], shin_r: 92, foot_r: 34, leg_l: [10, 0, -6], shin_l: 92, foot_l: 34, $ground: 'knees' };
	const sway = merge(kneel, { chest: [12, 0, 10], head: [34, 22, 18], jaw: 20 });
	const fallen = merge(kneel, { root: [78, 0, 0], waist: -4, chest: -6, head: [-34, 32, 12], jaw: 34, arm_r: [-120, 0, 40], arm_l: [-95, 0, -55], forearm_r: 10, forearm_l: -20, fingers_r: 20, fingers_l: 20, leg_r: [14, 0, 6], shin_r: -12, foot_r: 40, leg_l: [8, 0, -10], shin_l: -6, foot_l: 40, $ground: 'torso' });
	clip('death', 84, 'hold', [
		[0, REST],
		[6, recoil, 'easeoutquad'],
		[14, merge(recoil, { leg_r: -30, shin_r: 62, foot_r: -24, chest: -6, waist: -4, jaw: 36, head: [-10, 10, 10] }), 'easeinoutsine'],
		[26, kneel, 'easeinquad'],
		[30, merge(kneel, { chest: 10, head: [30, 16, 10] }), 'easeoutquad'],
		[44, sway, 'easeinoutsine'],
		[50, merge(kneel, { root: [16, 0, 0], chest: 8, head: [20, 10, 6], $ground: 'knees' }), 'easeinoutsine'],
		[DEATH_IMPACT, fallen, 'easeinquad'],
		[DEATH_IMPACT + 4, merge(fallen, { root: [74, 0, 0], $ground: 'torso', $y: 1.5 }), 'easeoutquad'],
		[DEATH_IMPACT + 9, fallen, 'easeinquad'],
		[78, merge(fallen, { fingers_r: -30, jaw: 20 })],
		[84, fallen],
	]);
}

writeJson(path.join(RES, 'animations/titan.animation.json'), { format_version: '1.8.0', animations });

// ---------------------------------------------------------------- checks + key numbers for the Java side
function frameAt(name, t) { return clipInfo[name].frames.find(f => f.t === t); }
const checks = [];
for (const n of ['punch', 'sweep', 'stomp', 'slam', 'shockwave', 'grab', 'boulder']) {
	if (!frameAt(n, STRIKE)) checks.push(`${n}: no keyframe on the strike tick ${STRIKE}`);
	if (clipInfo[n].length + TRANSITION > 64 + 2) checks.push(`${n}: clip outlasts the server's 64-tick animation`);
}
if (!frameAt('grab_throw', 30 - TRANSITION)) checks.push('grab_throw: no keyframe on the throw tick');
if (!frameAt('swat', 10 - UPPER_TRANSITION)) checks.push('swat: no keyframe on the hit tick');
if (clipInfo.roar.length + TRANSITION > 40 + 30 + 16 + 2) checks.push('roar outlasts the server animation');
if (clipInfo.intro.length + TRANSITION > 56 + 2) checks.push('intro outlasts the server animation');
if (checks.length) { console.error(checks.join('\n')); process.exit(1); }

function report(name, t) {
	const f = frameAt(name, t);
	const W = worldMatrices(f.p, f.root);
	const hr = pointWorld(W, 'hand_r', handPoint('r'));
	const hl = pointWorld(W, 'hand_l', handPoint('l'));
	// json -> entity frame: forward = -z, right = -x
	const fmt = p => `up ${(p[1] * PX2BLK).toFixed(1)} fwd ${(-p[2] * PX2BLK).toFixed(1)} right ${(-p[0] * PX2BLK).toFixed(1)}`;
	console.log(`${name}@${t}: hand_r ${fmt(hr)} | hand_l ${fmt(hl)} | min y ${(minY(W) * PX2BLK).toFixed(2)}`);
}
clipInfo.rest = { length: 1, frames: [{ t: 0, p: {}, root: [0, 0, 0] }] };
report('rest', 0);
report('grab_throw', 0);
report('grab_throw', 15);
report('grab', STRIKE);
report('punch', STRIKE);
report('boulder', STRIKE - 4);
report('slam', STRIKE);
{
	const fc = pointWorld(REST_W, 'head', [0, 47 + HY + SHIFT, -15.5 + HZ]);
	console.log(`face (rest): up ${(fc[1] * PX2BLK).toFixed(1)} fwd ${(-fc[2] * PX2BLK).toFixed(1)} blocks`);
}
for (const t of [26, 50, DEATH_IMPACT]) {
	const f = frameAt('death', t);
	const bb = bounds(worldMatrices(f.p, f.root));
	console.log(`death@${t}: root pos ${f.root.map(v => v.toFixed(2))} rot ${JSON.stringify(f.p.root || null)} -> y ${bb.lo[1].toFixed(2)}..${bb.hi[1].toFixed(2)} z ${bb.lo[2].toFixed(1)}..${bb.hi[2].toFixed(1)}`);
}
console.log('server grab hold point: up 11.2 fwd 5.8 right 2.8 (blocks)');
console.log(`TIMING: transition ${TRANSITION}, strike clip tick ${STRIKE}, boulder rock shown clip ticks ${BOULDER_SHOW}, death impact clip tick ${DEATH_IMPACT}`);
console.log(`clips: ${Object.keys(animations).map(k => k.replace('animation.titan.', '') + '(' + clipInfo[k.replace('animation.titan.', '')].length + ')').join(' ')}`);

// ================================================================ preview (software renderer)
if (PREVIEW) {
	fs.mkdirSync(PREVIEW, { recursive: true });
	const texAt = (r, s, t) => {
		const x = Math.min(r.w - 1, Math.floor(s * r.w)), y = Math.min(r.h - 1, Math.floor(t * r.h));
		const i = ((r.v + y) * TW + r.u + x) * 4;
		return [tex[i], tex[i + 1], tex[i + 2], tex[i + 3], glow[i + 3] ? [glow[i], glow[i + 1], glow[i + 2]] : null];
	};
	function render(pose, root, yawDeg, size = 360) {
		const W = worldMatrices(pose, root);
		const img = Buffer.alloc(size * size * 4);
		const zb = new Float32Array(size * size).fill(-Infinity);
		for (let i = 0; i < size * size; i++) { img[i * 4] = 70; img[i * 4 + 1] = 78; img[i * 4 + 2] = 92; img[i * 4 + 3] = 255; }
		const scale = size / 64, ox = size / 2, oy = size - 6;
		const cy = Math.cos(yawDeg * DEG), syw = Math.sin(yawDeg * DEG);
		// camera: looks at the model's front (from -Z) rotated by yaw around Y. screen x = right, screen y = up.
		const view = p => { const x = p[0] * cy - p[2] * syw, z = p[0] * syw + p[2] * cy; return [x, p[1], -z]; };
		// ground line
		for (let x = 0; x < size; x++) for (let y = oy; y < size; y++) { const i = (y * size + x) * 4; img[i] = 52; img[i + 1] = 58; img[i + 2] = 48; }
		for (const c of allCubes()) {
			const a = cubeWorld(c, W);
			for (const f of FACES) {
				const r = c.uv[f];
				const P = (s, t) => view(affApply(a, facePoint(c, f, s, t)));
				const p00 = P(0, 0), p10 = P(1, 0), p01 = P(0, 1), p11 = P(1, 1);
				// normal (towards viewer = +z in view space)
				const e1 = p10.map((v, i) => v - p00[i]), e2 = p01.map((v, i) => v - p00[i]);
				const nz = e1[0] * e2[1] - e1[1] * e2[0];
				const nrm = Math.hypot(e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], nz) || 1;
				const light = 0.55 + 0.45 * Math.abs(nz / nrm);
				const scr = p => [ox + p[0] * scale, oy - p[1] * scale, p[2]];
				const S = [scr(p00), scr(p10), scr(p01), scr(p11)];
				const tris = [[[0, 0, S[0]], [1, 0, S[1]], [0, 1, S[2]]], [[1, 0, S[1]], [1, 1, S[3]], [0, 1, S[2]]]];
				for (const tri of tris) {
					const xs = tri.map(v => v[2][0]), ys = tri.map(v => v[2][1]);
					const x0 = Math.max(0, Math.floor(Math.min(...xs))), x1 = Math.min(size - 1, Math.ceil(Math.max(...xs)));
					const y0 = Math.max(0, Math.floor(Math.min(...ys))), y1 = Math.min(size - 1, Math.ceil(Math.max(...ys)));
					const [A, Bq, C] = tri.map(v => v[2]);
					const den = (Bq[1] - C[1]) * (A[0] - C[0]) + (C[0] - Bq[0]) * (A[1] - C[1]);
					if (Math.abs(den) < 1e-9) continue;
					for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
						const px = x + 0.5, py = y + 0.5;
						const l1 = ((Bq[1] - C[1]) * (px - C[0]) + (C[0] - Bq[0]) * (py - C[1])) / den;
						const l2 = ((C[1] - A[1]) * (px - C[0]) + (A[0] - C[0]) * (py - C[1])) / den;
						const l3 = 1 - l1 - l2;
						if (l1 < 0 || l2 < 0 || l3 < 0) continue;
						const z = l1 * A[2] + l2 * Bq[2] + l3 * C[2];
						const idx = y * size + x;
						if (z <= zb[idx]) continue;
						const s = l1 * tri[0][0] + l2 * tri[1][0] + l3 * tri[2][0];
						const t = l1 * tri[0][1] + l2 * tri[1][1] + l3 * tri[2][1];
						const [rr, gg, bb, aa, gl] = texAt(r, s, t);
						if (aa < 128) continue;
						zb[idx] = z;
						const k = gl ? 1 : light;
						img[idx * 4] = clamp(rr * k); img[idx * 4 + 1] = clamp(gg * k); img[idx * 4 + 2] = clamp(bb * k);
					}
				}
			}
		}
		return img;
	}
	function sheet(file, frames) { // frames: [[clipName, tick]] -> rows of front / side / 3-quarter
		const size = 300, views = [0, 90, 35];
		const Wd = size * views.length, Hd = size * frames.length;
		const out = Buffer.alloc(Wd * Hd * 4);
		frames.forEach(([n, t], row) => {
			const f = n === 'rest' ? { p: {}, root: [0, 0, 0] } : frameAt(n, t);
			if (!f) throw new Error(`no frame ${n}@${t}`);
			views.forEach((yaw, col) => {
				const img = render(f.p, f.root, yaw, size);
				for (let y = 0; y < size; y++) img.copy(out, ((row * size + y) * Wd + col * size) * 4, y * size * 4, (y + 1) * size * 4);
			});
		});
		fs.writeFileSync(path.join(PREVIEW, file), encodePng(Wd, Hd, out));
	}
	sheet('p_rest.png', [['rest', 0], ['idle', 55]]);
	sheet('p_walk.png', [['walk', 0], ['walk', WALK_CYCLE / 4], ['run', 0]]);
	sheet('p_punch.png', [['punch', 14], ['punch', STRIKE - 4], ['punch', STRIKE]]);
	sheet('p_sweep_stomp.png', [['sweep', STRIKE - 4], ['sweep', STRIKE + 5], ['stomp', STRIKE - 3], ['stomp', STRIKE]]);
	sheet('p_slam_shock.png', [['slam', STRIKE - 4], ['slam', STRIKE], ['shockwave', STRIKE - 4], ['shockwave', STRIKE]]);
	sheet('p_grab.png', [['grab', STRIKE - 4], ['grab', STRIKE], ['grab_throw', 0], ['grab_throw', 26]]);
	sheet('p_boulder.png', [['boulder', 10], ['boulder', 22], ['boulder', STRIKE - 4], ['boulder', STRIKE]]);
	sheet('p_charge_leap.png', [['charge', 8], ['charge_run', 0], ['leap', STRIKE], ['leap_air', 14], ['leap_land', 0]]);
	sheet('p_roar.png', [['roar', STRIKE - 3], ['roar', STRIKE], ['intro', 0], ['swat', 8]]);
	sheet('p_death.png', [['death', 6], ['death', 26], ['death', 44], ['death', DEATH_IMPACT]]);
	// texture preview, 2x
	{
		const k = 2, out = Buffer.alloc(TW * k * TH * k * 4);
		for (let y = 0; y < TH * k; y++) for (let x = 0; x < TW * k; x++) {
			const si = ((Math.floor(y / k)) * TW + Math.floor(x / k)) * 4, di = (y * TW * k + x) * 4;
			const a = tex[si + 3] / 255, ch = ((x >> 3) + (y >> 3)) & 1 ? 200 : 160;
			for (let q = 0; q < 3; q++) out[di + q] = Math.round(tex[si + q] * a + ch * (1 - a));
			out[di + 3] = 255;
		}
		fs.writeFileSync(path.join(PREVIEW, 'p_texture.png'), encodePng(TW * k, TH * k, out));
	}
	console.log(`previews written to ${PREVIEW}`);
}
