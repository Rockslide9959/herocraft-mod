// v0.17.1: PLAGG, the Kwami of Destruction (Cat Noir), remodelled on Sass's plan: near-black (a very dark navy-black with
// a soft blue sheen), a round head with two big pointed cat ears (a little lighter inside), big slanted lime-green eyes
// with thin slit pupils, smug and half-lidded, long thin whiskers far out from the cheeks, a small mouth with a pink
// tongue, one long thin curling strand from the top of the head, a thin black body and a thin tail that curls up.
// Writes geo/miraculous/plagg.geo.json, textures/entity/miraculous/plagg.png, the animation.kwami.plagg.* clips and
// scratchpad/kwamis_v0171/plagg_sheet.png.   Usage: node scratchpad/kwamis_v0171/gen_plagg.js
'use strict';
const K = require('./kwami_lib.js');
const { L, Model, mix, mul, clamp } = K;

const M = new Model('plagg_kwami', { TW: 512, D: 8 });
const { head, body, H } = K.base(M, { body: { size: [3.3, 4.0, 2.8] }, armW: 1.0, legW: 1.05 });
const FZ = H.c[2] - H.size[2] / 2;
const TOP = H.c[1] + H.size[1] / 2;
// the ears: big pointed triangles, a stack of shrinking slabs
const EARS = {};
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const ex = s * 3.0, ey = TOP - 0.9, ez = 0.3;
	const b = M.bone(`ear_${n}`, 'head', [ex, ey, ez], [-6, 0, -s * 14]);
	const lv = 6;
	for (let i = 0; i < lv; i++) {
		const w = 3.9 * (1 - i / lv) + 0.35, hh = 1.0;
		M.cbox(b, [ex + s * 0.12 * i, ey + 0.5 + i * 0.95, ez], [w, hh, 1.3 - 0.12 * i], 'ear', { seed: 200 + i });
	}
	EARS[n] = { ex, ey };
}
// whiskers: long and thin, far out from the cheeks
for (const s of [-1, 1]) {
	M.stick(head, [s * 3.6, 8.75, FZ + 0.5], [s, 0.16, -0.05], 4.6, [0.16, 0.16], 'whisker', { seed: 220 });
	M.stick(head, [s * 3.6, 8.25, FZ + 0.5], [s, -0.08, -0.05], 4.3, [0.16, 0.16], 'whisker', { seed: 221 });
}
// the curling strand on top of the head
M.tube(head, [[0.2, TOP - 0.4, -0.9], [0.35, TOP + 1.3, -1.1], [1.0, TOP + 2.7, -1.0], [2.1, TOP + 3.2, -0.8], [2.9, TOP + 2.6, -0.7], [2.7, TOP + 1.8, -0.7], [2.1, TOP + 1.7, -0.7]],
	[0.3, 0.27, 0.24, 0.22, 0.2, 0.18], 'whisker', { seed: 230 });
// the tail: thin, down off the back and curling up behind
const tail = M.bone('tail', 'body', [0, 2.9, 1.2]);
M.tube(tail, [[0, 2.9, 1.2], [0, 2.1, 2.6], [0, 2.3, 4.3], [0, 3.8, 5.6], [0, 6.0, 6.0], [0, 7.8, 5.4], [0, 8.6, 4.3], [0, 8.2, 3.5]],
	[0.7, 0.62, 0.56, 0.52, 0.48, 0.44, 0.4], 'tail', { seed: 240 });

// ================================================================ paint
const C = { blk: [34, 34, 48], blkL: [84, 94, 132], blkD: [14, 14, 22], inner: [78, 72, 96], tongue: [236, 112, 140] };
const EYE = { x: 2.15, y: 10.35, rx: 1.9, ry: 1.62, tilt: 0.3, sclera: null, iris: [[70, 150, 22], [150, 226, 40], [214, 252, 120]],
	irx: 1, iry: 1, pupil: 'slit', pr: 0.11, lid: 0.27, lidTilt: 0.28, lidCol: [26, 26, 38], ow: 0.1, lashW: 0.12,
	lashes: [[12, 0.22, 10]], hl: [[0.42, 0.18, 0.14], [-0.36, -0.42, 0.08]] };
const sk = (p, f, info, o) => K.skin(C.blk, p, f, info, Object.assign({ light: C.blkL, dark: C.blkD, tone: 0.3 }, o));
function shade(c, f, s, t, p, info) {
	let col;
	switch (c.mat) {
		case 'whisker': col = K.skin([22, 22, 30], p, f, info, { light: [60, 64, 84], tone: 0.2 }); break;
		case 'ear': {
			col = sk(p, f, info, { y0: 12, y1: 20 });
			const E = p[0] < 0 ? EARS.r : EARS.l;
			const rel = (p[1] - E.ey) / 5.6; // 0 at the base, 1 at the tip
			const half = (3.9 * (1 - rel) + 0.35) / 2 - 0.55;
			if (f === 'north' && rel < 0.78 && Math.abs(p[0] - E.ex - Math.sign(E.ex) * 0.6 * rel) < half) col = K.skin(C.inner, p, f, info, { tone: 0.2 });
			break;
		}
		case 'tail': col = sk(p, f, info, { y0: 0, y1: 9 }); break;
		case 'head': {
			col = sk(p, f, info, { y0: 5.5, y1: 15, seed: 4 });
			// the blue sheen: a soft lighter band over the top of the head
			const top = K.spot(p, H.c, [-0.3, 0.9, -0.3], 0.55);
			if (top) col = mix(col, C.blkL, 0.25);
			if (f === 'north' && p[2] < H.c[2]) {
				const e = K.eye(p[0], p[1], EYE);
				if (e) col = e;
				else {
					// a small open smirk with a pink tongue
					const mx = p[0] - 0.15, my = p[1];
					const q = K.inEll(mx, my, 0, 7.95, 0.62, 0.42);
					if (q < 1 && my < 7.95 + 0.06 * mx) col = my < 7.72 ? C.tongue : [60, 16, 28];
					else if (K.smileLine(mx, my, 0, 8.0, 1.0, -0.12, 0.09) && Math.abs(mx) > 0.5) col = [10, 10, 16];
				}
			}
			break;
		}
		default: col = sk(p, f, info, { y0: -1, y1: 7 });
	}
	return [col, 255, null];
}
const FLOAT = { arm_r: [-24, 0, 34], arm_l: [-40, 0, -26], tail: [-6, 0, 0] };
const REST = { tail: [-6, 0, 0] };
K.writeClips('plagg', FLOAT, (name, t, o) => {
	if (name === 'idle') return { ear_r: { rot: [4 * Math.sin(o.w2), 0, 6 * Math.sin(o.w)] }, ear_l: { rot: [4 * Math.sin(o.w2 + 0.5), 0, -6 * Math.sin(o.w + 0.7)] }, tail: { rot: [-6 + 6 * Math.sin(o.w2), 18 * Math.sin(o.w), 0] } };
	if (name === 'wave') return { ear_r: { rot: [-8 * o.k, 0, 0] }, ear_l: { rot: [-8 * o.k, 0, 0] }, tail: { rot: [-12, 30 * Math.sin(K.TAU * t * 3), 0] } };
	if (name === 'eat') return { ear_r: { rot: [3 * o.nib, 0, 4] }, ear_l: { rot: [3 * o.nib, 0, -4] }, tail: { rot: [-10, 22 * Math.sin(K.TAU * t * 2), 0] } };
	if (name === 'tired') return { ear_r: { rot: [25, 0, 24] }, ear_l: { rot: [25, 0, -24] }, tail: { rot: [34, 4 * o.s, 0] } };
	if (name === 'into_jewel') return { ear_r: { rot: [0, 0, 0] }, ear_l: { rot: [0, 0, 0] }, tail: { rot: [0, 0, 0] } };
	return {};
});
K.finish(M, 'plagg', shade, 'PLAGG', 'KWAMI OF DESTRUCTION - CAT NOIR - RING - LOVES CAMEMBERT', FLOAT, REST,
	{ bg: { top: [150, 168, 150], bottom: [52, 62, 54] } });
