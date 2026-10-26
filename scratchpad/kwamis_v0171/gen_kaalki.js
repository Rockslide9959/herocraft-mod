// v0.17.1: KAALKI, the Kwami of Teleportation (Pegasus), remodelled on Sass's plan: a dark chocolate-brown head with long
// upright horse ears (brown outside, pale pink inside), a big WHITE swooping mane / forelock on top curling over to one
// side, half-lidded green eyes with long lashes and an aloof look, a pale white-pink muzzle at the bottom of the face,
// a pale white / light-pink body with little brown hooves, and a big fluffy white tail.
// Writes geo/miraculous/kaalki.geo.json, textures/entity/miraculous/kaalki.png, the animation.kwami.kaalki.* clips and
// scratchpad/kwamis_v0171/kaalki_sheet.png.   Usage: node scratchpad/kwamis_v0171/gen_kaalki.js
'use strict';
const K = require('./kwami_lib.js');
const { L, Model, mix, mul, clamp, fbm } = K;

const M = new Model('kaalki_kwami', { TW: 512, D: 8 });
const { head, body, H } = K.base(M, { armW: 1.05, legW: 1.15 });
const FZ = H.c[2] - H.size[2] / 2;
const TOP = H.c[1] + H.size[1] / 2;
// the muzzle: a pale rounded block on the lower face
K.blob(M, head, [0, 7.75, FZ - 0.35], [4.8, 2.7, 1.5], 0.5, 'muzzle', { seed: 500 });
// the ears: long, upright, rounded at the tip
const EARS = {}, EAR_H = 4.8, LV = 6;
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const ex = s * 2.8, ey = TOP - 0.9, ez = 0.8;
	const b = M.bone(`ear_${n}`, 'head', [ex, ey, ez], [-4, 0, -s * 12]);
	for (let i = 0; i < LV; i++) {
		const u = i / (LV - 1);
		const w = 2.3 * Math.sqrt(Math.max(0.05, 1 - u * u * 0.85)) * (1 - 0.35 * u);
		M.cbox(b, [ex, ey + 0.4 + i * (EAR_H / LV), ez], [w, EAR_H / LV + 0.05, 1.1], 'ear', { seed: 510 + i });
	}
	EARS[n] = { ex, ey };
}
// the mane: a big white swoop over the top, a forelock curling down over to its left side, and a strip down the back
const mane = M.bone('mane', 'head', [0, TOP, 0]);
K.blob(M, mane, [0.3, TOP + 0.95, 0.5], [6.6, 2.6, 8.0], 0.8, 'mane', { seed: 520 });
K.blob(M, mane, [1.6, TOP + 0.45, -3.7], [5.4, 2.9, 2.8], 0.8, 'mane', { seed: 524, rot: [0, 0, -16], pivot: [1.5, TOP + 0.35, -3.7] });
K.blob(M, mane, [3.8, TOP - 1.5, -4.35], [3.0, 3.4, 1.8], 0.65, 'mane', { seed: 528, rot: [0, 0, -28], pivot: [3.7, TOP - 1.5, -4.35] });
M.cbox(mane, [3.75, TOP - 3.25, -4.6], [1.5, 1.3, 1.0], 'mane', { seed: 532, rot: [0, 0, 20], pivot: [3.75, TOP - 3.25, -4.6] });
K.blob(M, mane, [0, TOP - 2.6, H.c[2] + H.size[2] / 2 + 0.1], [3.4, 5.6, 1.4], 0.5, 'mane', { seed: 534 });
// the big fluffy white tail
const tail = M.bone('tail', 'body', [0, 3.0, 1.5]);
[[0, 3.2, 2.4, 1.8], [0, 3.8, 3.9, 2.7], [0, 3.6, 5.3, 3.1], [0, 2.6, 6.3, 2.9], [0, 1.4, 6.8, 2.3], [0, 0.5, 6.9, 1.5]]
	.forEach(([x, y, z, r], i) => K.blob(M, tail, [x, y, z], [r * 1.0, r * 1.1, r * 0.95], r * 0.24, 'tail', { seed: 540 + i }));

// ================================================================ paint
const C = { br: [108, 64, 42], brL: [160, 104, 70], brD: [62, 34, 22], pale: [250, 236, 238], paleD: [214, 190, 198], wh: [252, 250, 252], whD: [206, 204, 216],
	pink: [240, 182, 196], hoof: [118, 72, 46], irisD: [30, 96, 50], iris: [74, 168, 88], irisL: [168, 232, 150] };
const EYE = { x: 2.15, y: 10.95, rx: 1.72, ry: 1.85, tilt: 0.04, sclera: [252, 252, 255], iris: [C.irisD, C.iris, C.irisL], irx: 0.74, iry: 0.8, ix: -0.06, iy: -0.12,
	pr: 0.4, lid: 0.36, lidTilt: -0.08, lidCol: [86, 50, 32], ow: 0.1, lashW: 0.1, lashes: [[8, 0.42, 9], [26, 0.46, 8], [46, 0.32, 7]], outline: [24, 12, 10],
	hl: [[0.3, 0.12, 0.2], [-0.3, -0.44, 0.1]] };
const brown = (p, f, info, o) => K.skin(C.br, p, f, info, Object.assign({ light: C.brL, dark: C.brD, tone: 0.3 }, o));
const white = (p, f, info) => {
	let col = K.skin(C.wh, p, f, info, { light: [255, 255, 255], dark: C.whD, tone: 0.3 });
	col = mul(col, 0.95 + 0.07 * Math.sin(p[0] * 1.7 + p[2] * 1.1 + p[1] * 0.6 + fbm(p[0] * 0.5, p[1] * 0.5, p[2] * 0.5, 2) * 4)); // soft strands
	return col;
};
function shade(c, f, s, t, p, info) {
	let col;
	switch (c.mat) {
		case 'ear': {
			const E = p[0] < 0 ? EARS.r : EARS.l;
			const rel = (p[1] - E.ey) / EAR_H;
			col = brown(p, f, info, { y0: 12, y1: 21 });
			const half = 1.15 * (1 - 0.4 * rel);
			if (f === 'north' && rel < 0.8 && Math.abs(p[0] - E.ex) < half - 0.45) col = K.skin(C.pink, p, f, info, { tone: 0.2 });
			break;
		}
		case 'mane': case 'tail': col = white(p, f, info); break;
		case 'muzzle': {
			col = K.skin(C.pale, p, f, info, { dark: C.paleD, tone: 0.2 });
			if (f === 'north') {
				for (const sx of [-1, 1]) if (K.inEll(p[0], p[1], sx * 1.05, 8.3, 0.3, 0.38) < 1) col = [120, 70, 78];
				if (K.smileLine(p[0], p[1], 0.15, 7.15, 0.9, 0.12, 0.09)) col = [130, 76, 84];
			}
			break;
		}
		case 'paw': case 'foot': col = K.skin(C.hoof, p, f, info, { light: C.brL, tone: 0.2 }); break;
		case 'head': {
			col = brown(p, f, info, { y0: 5.5, y1: 15, seed: 7 });
			if (f === 'north' && p[2] < H.c[2]) {
				const e = K.eye(p[0], p[1], EYE);
				if (e) col = e;
			}
			break;
		}
		default: col = K.skin(C.pale, p, f, info, { y0: -1, y1: 7, dark: C.paleD, tone: 0.25 });
	}
	return [col, 255, null];
}
const FLOAT = { arm_r: [-22, 0, 34], arm_l: [-34, 0, -26], tail: [-6, 0, 0] };
const REST = { tail: [-6, 0, 0] };
K.writeClips('kaalki', FLOAT, (name, t, o) => {
	if (name === 'idle') return { ear_r: { rot: [4 * Math.sin(o.w2), 0, 5 * Math.sin(o.w)] }, ear_l: { rot: [4 * Math.sin(o.w2 + 0.5), 0, -5 * Math.sin(o.w + 0.7)] },
		mane: { rot: [2 * Math.sin(o.w2 + 1), 0, 2 * Math.sin(o.w)] }, tail: { rot: [-6 + 5 * Math.sin(o.w2), 16 * Math.sin(o.w), 0] } };
	if (name === 'wave') return { ear_r: { rot: [-8 * o.k, 0, 0] }, ear_l: { rot: [-8 * o.k, 0, 0] }, mane: { rot: [-3 * o.k, 0, 0] }, tail: { rot: [-12, 26 * Math.sin(K.TAU * t * 3), 0] } };
	if (name === 'eat') return { ear_r: { rot: [3 * o.nib, 0, 4] }, ear_l: { rot: [3 * o.nib, 0, -4] }, mane: { rot: [2 * o.nib, 0, 0] }, tail: { rot: [-10, 20 * Math.sin(K.TAU * t * 2), 0] } };
	if (name === 'tired') return { ear_r: { rot: [25, 0, 24] }, ear_l: { rot: [25, 0, -24] }, mane: { rot: [6, 0, 0] }, tail: { rot: [30, 4 * o.s, 0] } };
	if (name === 'into_jewel') return { ear_r: { rot: [0, 0, 0] }, ear_l: { rot: [0, 0, 0] }, mane: { rot: [0, 0, 0] }, tail: { rot: [0, 0, 0] } };
	return {};
});
K.finish(M, 'kaalki', shade, 'KAALKI', 'KWAMI OF TELEPORTATION - PEGASUS - HORSESHOE GLASSES', FLOAT, REST,
	{ bg: { top: [168, 160, 150], bottom: [62, 56, 52] } });
