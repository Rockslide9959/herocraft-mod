// v0.17.1: TRIXX, the Kwami of Illusion (Rena Rouge), remodelled on Sass's plan: a bright orange round fox head with a
// white lower face, cheeks and muzzle; TALL pointed ears with black edges and black tips that run on as long thin black
// strands, pale pink-lilac inside; big violet eyes with lashes, two little black eyebrow dashes, a small smile; an orange
// body with a white chest stripe, arms and legs turning black at the ends like socks; and a huge fluffy orange tail
// curving up behind with a white tip and white flame-like streaks.
// Writes geo/miraculous/trixx.geo.json, textures/entity/miraculous/trixx.png, the animation.kwami.trixx.* clips and
// scratchpad/kwamis_v0171/trixx_sheet.png.   Usage: node scratchpad/kwamis_v0171/gen_trixx.js
'use strict';
const K = require('./kwami_lib.js');
const { L, Model, mix, mul, clamp, fbm } = K;

const M = new Model('trixx_kwami', { TW: 512, D: 8 });
const { head, body, H } = K.base(M, {});
const TOP = H.c[1] + H.size[1] / 2;
const EARS = {};
const EAR_H = 6.2, EAR_W = 3.6, LV = 7;
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const ex = s * 2.9, ey = TOP - 1.0, ez = 0.4;
	const b = M.bone(`ear_${n}`, 'head', [ex, ey, ez], [-6, 0, -s * 12]);
	for (let i = 0; i < LV; i++) {
		const w = EAR_W * (1 - i / LV) + 0.4;
		M.cbox(b, [ex, ey + 0.45 + i * (EAR_H / LV), ez], [w, EAR_H / LV + 0.05, 1.3 - 0.1 * i], 'ear', { seed: 300 + i });
	}
	// the tip runs on as a long thin black strand
	M.stick(b, [ex, ey + EAR_H + 0.2, ez], [s * 0.12, 1, 0.05], 2.6, [0.22, 0.22], 'black', { seed: 310 });
	EARS[n] = { ex, ey };
}
// the huge fluffy tail: rounded lumps along a curve up behind
const tail = M.bone('tail', 'body', [0, 2.9, 1.3]);
const TAILP = [[0, 3.0, 2.3, 1.8], [0.3, 3.3, 3.8, 2.9], [0.8, 4.6, 5.3, 3.9], [1.4, 6.8, 6.2, 4.5], [2.0, 9.4, 6.3, 4.6], [2.6, 11.8, 5.8, 4.0], [3.0, 13.6, 4.9, 3.0], [3.2, 14.8, 4.0, 1.8]];
TAILP.forEach(([x, y, z, r], i) => K.blob(M, tail, [x, y, z], [r * 1.05, r * 1.15, r * 0.95], r * 0.24, 'tail', { seed: 330 + i }));

// ================================================================ paint
const C = { or: [246, 128, 34], orL: [255, 182, 92], orD: [196, 82, 18], wh: [252, 248, 242], whD: [214, 208, 214], blk: [30, 24, 28],
	inner: [236, 196, 222], irisD: [74, 30, 120], iris: [146, 82, 206], irisL: [212, 166, 250] };
const EYE = { x: 2.12, y: 10.6, rx: 1.72, ry: 2.05, tilt: 0.08, sclera: [252, 250, 255], iris: [C.irisD, C.iris, C.irisL], irx: 0.78, iry: 0.82, ix: -0.06,
	pr: 0.4, lashW: 0.15, lashes: [[18, 0.34, 11], [42, 0.3, 10]], brow: [-0.05, 1.32, 0.42, 0.13, -0.15], browCol: [30, 22, 26] };
const orange = (p, f, info, o) => K.skin(C.or, p, f, info, Object.assign({ light: C.orL, dark: C.orD, tone: 0.3 }, o));
const white = (p, f, info) => K.skin(C.wh, p, f, info, { light: [255, 255, 255], dark: C.whD, tone: 0.2 });
const black = (p, f, info) => K.skin(C.blk, p, f, info, { light: [72, 62, 70], tone: 0.2 });
function shade(c, f, s, t, p, info) {
	let col;
	switch (c.mat) {
		case 'black': col = black(p, f, info); break;
		case 'ear': {
			const E = p[0] < 0 ? EARS.r : EARS.l;
			const rel = (p[1] - E.ey) / EAR_H;
			const half = (EAR_W * (1 - rel) + 0.4) / 2;
			const d = Math.abs(p[0] - E.ex);
			col = orange(p, f, info, { y0: 12, y1: 22 });
			if (rel > 0.66 || d > half - 0.38) col = black(p, f, info);
			else if (f === 'north' && d < half - 0.7 && rel < 0.6) col = K.skin(C.inner, p, f, info, { tone: 0.2 });
			break;
		}
		case 'tail': {
			col = orange(p, f, info, { y0: 2, y1: 15 });
			const streak = fbm(p[0] * 0.9 + 4, p[1] * 0.35, p[2] * 0.9, 3);
			if (p[1] > 12.6 + 0.5 * Math.sin(p[0] * 2 + p[2] * 1.5) || (p[1] > 8.5 && streak > 0.62 - (p[1] - 8.5) * 0.02)) col = white(p, f, info);
			break;
		}
		case 'paw': case 'foot': col = black(p, f, info); break;
		case 'limb': col = (p[1] < 4.7 && p[1] > 3) || p[1] < 1.4 ? black(p, f, info) : orange(p, f, info, { y0: -1, y1: 7 }); break;
		case 'body': {
			col = orange(p, f, info, { y0: 1.5, y1: 7 });
			if (p[2] < -0.6 && Math.abs(p[0]) < 0.85 + 0.15 * (p[1] - 2)) col = white(p, f, info);
			break;
		}
		default: { // head
			col = orange(p, f, info, { y0: 5.5, y1: 15, seed: 5 });
			const ax = Math.abs(p[0]);
			const front = p[2] < H.c[2] - 1.2 - 1.2 * clamp((p[1] - 6.5) / 3, 0, 1);
			const line = 8.7 + 2.3 * Math.pow(ax / 5.2, 1.6);
			if (front && p[1] < line) col = white(p, f, info);
			if (f === 'north' && p[2] < H.c[2]) {
				const e = K.eye(p[0], p[1], EYE);
				if (e) col = e;
				else if (K.smileLine(p[0], p[1], 0, 8.05, 0.7, 0.34, 0.11)) col = [70, 26, 30];
				else if (K.inEll(p[0], p[1], 0, 8.5, 0.42, 0.26) < 1) col = [44, 30, 34]; // a tiny nose
			}
		}
	}
	return [col, 255, null];
}
const FLOAT = { arm_r: [-24, 0, 38], arm_l: [-36, 0, -28], tail: [-8, 0, 0] };
const REST = { tail: [-8, 0, 0] };
K.writeClips('trixx', FLOAT, (name, t, o) => {
	if (name === 'idle') return { ear_r: { rot: [4 * Math.sin(o.w2), 0, 6 * Math.sin(o.w)] }, ear_l: { rot: [4 * Math.sin(o.w2 + 0.5), 0, -6 * Math.sin(o.w + 0.7)] }, tail: { rot: [-8 + 5 * Math.sin(o.w2), 14 * Math.sin(o.w), 3 * Math.sin(o.w + 1)] } };
	if (name === 'wave') return { ear_r: { rot: [-8 * o.k, 0, 0] }, ear_l: { rot: [-8 * o.k, 0, 0] }, tail: { rot: [-12, 26 * Math.sin(K.TAU * t * 3), 0] } };
	if (name === 'eat') return { ear_r: { rot: [3 * o.nib, 0, 4] }, ear_l: { rot: [3 * o.nib, 0, -4] }, tail: { rot: [-10, 20 * Math.sin(K.TAU * t * 2), 0] } };
	if (name === 'tired') return { ear_r: { rot: [25, 0, 24] }, ear_l: { rot: [25, 0, -24] }, tail: { rot: [30, 4 * o.s, 0] } };
	if (name === 'into_jewel') return { ear_r: { rot: [0, 0, 0] }, ear_l: { rot: [0, 0, 0] }, tail: { rot: [0, 0, 0] } };
	return {};
});
K.finish(M, 'trixx', shade, 'TRIXX', 'KWAMI OF ILLUSION - RENA ROUGE - FOX NECKLACE', FLOAT, REST,
	{ bg: { top: [172, 158, 140], bottom: [66, 58, 50] } });
