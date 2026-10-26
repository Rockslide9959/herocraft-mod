// v0.17.1: NOOROO, the Kwami of Transmission (Hawk Moth), remodelled on Sass's plan: a pale lavender-lilac rounded,
// slightly tall head with a darker purple SPIRAL on the forehead, big outlined eyes with white sclera and purple irises,
// a small worried mouth, two pairs of rounded butterfly wings on the back (the upper pair bigger, pale and light), two
// very long thin antennae that trail DOWN below the body with small tips, and a tiny body and legs.
// Writes geo/miraculous/nooroo.geo.json, textures/entity/miraculous/nooroo.png, the animation.kwami.nooroo.* clips
// (with the wing flutter) and scratchpad/kwamis_v0171/nooroo_sheet.png.   Usage: node scratchpad/kwamis_v0171/gen_nooroo.js
'use strict';
const K = require('./kwami_lib.js');
const { L, Model, mix, mul, clamp, fbm } = K;

const M = new Model('nooroo_kwami', { TW: 512, D: 8 });
const { head, body, H } = K.base(M, { head: { c: [0, 10.75, -0.2], size: [10.0, 9.8, 8.4], r: [1.4, 2.6, 1.9] } });
const TOP = H.c[1] + H.size[1] / 2;
// the antennae: up off the top, out to the sides and trailing down past the body, a small teardrop tip
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const b = M.bone(`antenna_${n}`, 'head', [s * 1.4, TOP - 0.4, -0.8]);
	const pts = [[s * 1.4, TOP - 0.4, -0.8], [s * 1.9, TOP + 1.1, -1.0], [s * 3.4, TOP + 2.0, -1.1], [s * 5.2, TOP + 1.3, -1.0], [s * 6.2, TOP - 1.2, -0.8],
		[s * 6.6, TOP - 5.0, -0.6], [s * 6.6, TOP - 9.4, -0.4], [s * 6.3, TOP - 13.6, -0.2], [s * 5.9, TOP - 15.8, 0]];
	M.tube(b, pts, [0.32, 0.3, 0.28, 0.27, 0.26, 0.25, 0.24, 0.24], 'antenna', { seed: 400 });
	const tip = [s * 5.85, TOP - 16.4, 0];
	M.cbox(b, tip, [0.75, 1.1, 0.5], 'tip', { seed: 410 });
	M.cbox(b, [tip[0], tip[1] - 0.6, 0], [0.4, 0.5, 0.36], 'tip', { seed: 411 });
}
// the wings: thin planes behind the shoulders, cut to rounded wing shapes by the texture's alpha
// (user: "nooroos wings are in his body"): the upper pair sits behind the HEAD (its back is at z 4.0), the lower pair
// clear of the body
const WINGS = [];
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const up = M.bone(`wing_${n}`, 'body', [s * 0.8, 5.4, 4.4], [0, s * 14, -s * 16]);
	M.box(up, s * 0.5, 4.4, 4.45, s * 9.8, 12.8, 4.65, 'wing', { seed: 420 });
	WINGS.push({ s, cx: s * 5.4, cy: 8.8, rx: 4.3, ry: 3.9, root: [s * 0.8, 5.4], lo: false });
	const lo = M.bone(`wing_low_${n}`, 'body', [s * 0.8, 4.0, 2.2], [0, s * 16, s * 22]);
	M.box(lo, s * 0.5, 0.2, 2.25, s * 6.4, 4.9, 2.45, 'wing_lo', { seed: 430 });
	WINGS.push({ s, cx: s * 3.6, cy: 2.6, rx: 2.8, ry: 2.2, root: [s * 0.8, 4.0], lo: true });
}

// ================================================================ paint
const C = { lav: [206, 182, 236], lavL: [236, 222, 252], lavD: [150, 120, 196], body: [176, 146, 220], spiral: [104, 52, 158],
	irisD: [70, 26, 112], iris: [140, 72, 196], irisL: [206, 160, 246], wing: [218, 192, 250], wingL: [246, 236, 255], wingRim: [150, 108, 210], antenna: [116, 74, 170] };
const EYE = { x: 2.1, y: 10.25, rx: 1.75, ry: 2.05, tilt: -0.06, sclera: [252, 252, 255], iris: [C.irisD, C.iris, C.irisL], irx: 0.66, iry: 0.7, ix: -0.08, iy: -0.08,
	pr: 0.42, ow: 0.12, lashW: 0.12, outline: [56, 24, 84], lashes: [[24, 0.22, 10]], brow: [-0.1, 1.28, 0.46, 0.13, 0.42], browCol: [84, 40, 128],
	hl: [[0.2, 0.3, 0.26], [-0.3, -0.36, 0.11]] };
// the spiral on the forehead (Archimedean, two and a half turns)
function spiral(x, y) {
	const cx = 0, cy = 13.15, dx = x - cx, dy = y - cy;
	const r = Math.hypot(dx, dy);
	if (r > 1.25) return false;
	let th = Math.atan2(dy, dx); if (th < 0) th += Math.PI * 2;
	const pitch = 0.46;
	for (let n = 0; n < 3; n++) { const rr = pitch * (th / (Math.PI * 2) + n); if (rr < 1.2 && Math.abs(r - rr) < 0.11) return true; }
	return false;
}
function wingAlpha(p, W) {
	const q = K.inEll(p[0], p[1], W.cx, W.cy, W.rx, W.ry);
	// a narrow stalk back to the root so the wing joins the body
	const t = clamp((p[0] - W.root[0]) / (W.cx - W.root[0]), 0, 1);
	const sy = W.root[1] + (W.cy - W.root[1]) * t;
	const stalk = t < 0.6 && Math.abs(p[1] - sy) < 0.6 + 1.6 * t;
	return { inside: q < 1 || stalk, q };
}
function shade(c, f, s, t, p, info) {
	let col;
	switch (c.mat) {
		case 'antenna': col = K.skin(C.antenna, p, f, info, { tone: 0.2 }); break;
		case 'tip': col = K.skin(C.lavD, p, f, info, { tone: 0.2 }); break;
		case 'wing': case 'wing_lo': {
			const W = WINGS.find(w => w.lo === (c.mat === 'wing_lo') && Math.sign(w.cx) === Math.sign(p[0]));
			const { inside, q } = wingAlpha(p, W);
			if (!inside) return [[0, 0, 0], 0, null];
			col = mix(C.wingL, C.wing, clamp(q * 1.2, 0, 1));
			if (q > 0.78 && q < 1) col = mix(col, C.wingRim, 0.75);
			// faint veins fanning out from the root
			const a = Math.atan2(p[1] - W.root[1], Math.abs(p[0] - W.root[0]));
			if (Math.abs(Math.sin(a * 7)) < 0.09 && q < 0.8) col = mix(col, C.wingRim, 0.35);
			col = mul(col, 0.97 + 0.05 * fbm(p[0] * 1.4, p[1] * 1.4, 2, 2));
			break;
		}
		case 'head': {
			col = K.skin(C.lav, p, f, info, { y0: 5.5, y1: 16, light: C.lavL, dark: C.lavD, seed: 6 });
			if (f === 'north' && p[2] < H.c[2]) {
				const e = K.eye(p[0], p[1], EYE);
				if (e) col = e;
				else if (spiral(p[0], p[1])) col = C.spiral;
				// a small worried mouth: a little wobbly line, higher in the middle
				else if (Math.abs(p[0]) < 0.65 && Math.abs(p[1] - (7.85 + 0.16 * (1 - (p[0] / 0.65) ** 2) + 0.05 * Math.sin(p[0] * 9))) < 0.1) col = [70, 30, 90];
			}
			break;
		}
		default: col = K.skin(C.body, p, f, info, { y0: -1, y1: 7, light: C.lavL, dark: C.lavD });
	}
	return [col, 255, null];
}
const FLOAT = { arm_r: [-24, 0, 30], arm_l: [-30, 0, -24] };
const REST = {};
K.writeClips('nooroo', FLOAT, (name, t, o) => {
	if (name === 'idle') return { antenna_r: { rot: [4 * Math.sin(o.w2 + 0.3), 0, 4 * Math.sin(o.w)] }, antenna_l: { rot: [4 * Math.sin(o.w2 + 1), 0, -4 * Math.sin(o.w + 0.5)] } };
	if (name === 'wave') return { antenna_r: { rot: [-6 * o.k + 3 * o.wv, 0, 0] }, antenna_l: { rot: [-6 * o.k + 3 * o.wv, 0, 0] } };
	if (name === 'eat') return { antenna_r: { rot: [4 * o.nib, 0, 0] }, antenna_l: { rot: [4 * o.nib, 0, 0] } };
	if (name === 'tired') return { antenna_r: { rot: [14 + 3 * o.s, 0, 6] }, antenna_l: { rot: [14 + 3 * o.s, 0, -6] } };
	if (name === 'into_jewel') return { antenna_r: { rot: [0, 0, 0] }, antenna_l: { rot: [0, 0, 0] } };
	return {};
});
// the wings flutter in every clip (whole beats per clip so the loops stay seamless): [amplitude deg, beats/s, droop deg]
{
	const fs = require('fs'), path = require('path');
	const ANIM = path.join(K.A, 'animations', 'miraculous', 'kwami.animation.json');
	const file = JSON.parse(fs.readFileSync(ANIM, 'utf8'));
	const r3 = v => Math.round(v * 1000) / 1000, tkey = t => String(Math.round(t * 100) / 100);
	const WING = { idle: [14, 2.5, 0], wave: [22, 3.3, 0], eat: [10, 2, 0], tired: [5, 0.67, 14], into_jewel: [30, 6, -10], pop_out: [26, 4, 0] };
	for (const [name, [amp, hz, droop]] of Object.entries(WING)) {
		const c = file.animations['animation.kwami.nooroo.' + name];
		const len = c.animation_length, n = Math.max(2, Math.round(len / 0.05));
		const cycles = Math.max(1, Math.round(hz * len));
		for (const [b, side, k, ph] of [['wing_r', -1, 1, 0], ['wing_l', 1, 1, 0], ['wing_low_r', -1, 0.7, 0.6], ['wing_low_l', 1, 0.7, 0.6]]) {
			const rot = {};
			for (let i = 0; i <= n; i++) {
				const tt = i === n ? len : i * len / n;
				const v = amp * k * Math.sin(Math.PI * 2 * cycles * tt / len - ph);
				rot[tkey(tt)] = [0, side * v, side * -droop].map(r3);
			}
			c.bones[b] = { rotation: rot };
		}
	}
	fs.writeFileSync(ANIM, JSON.stringify(file, null, '\t') + '\n');
}
K.finish(M, 'nooroo', shade, 'NOOROO', 'KWAMI OF TRANSMISSION - HAWK MOTH - BUTTERFLY BROOCH', FLOAT, REST,
	{ bg: { top: [160, 150, 176], bottom: [58, 52, 70] } });
