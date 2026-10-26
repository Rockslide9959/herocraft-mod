// v0.17.1: TIKKI, the Kwami of Creation (Ladybug), remodelled on Sass's plan: a round bright pink-red blob head with one
// big black spot on the top/back and small spots round the sides and back (none on the body), huge blue-violet eyes with
// big highlights and long black upper lashes, a small happy smile, two very thin antennae curving up and out, and a
// tiny red body with the arms folded at the chest, short legs and little insect leg segments at the hips.
// Writes geo/miraculous/tikki.geo.json, textures/entity/miraculous/tikki.png, the animation.kwami.tikki.* clips and
// scratchpad/kwamis_v0171/tikki_sheet.png.   Usage: node scratchpad/kwamis_v0171/gen_tikki.js
'use strict';
const K = require('./kwami_lib.js');
const { L, Model, mix, mul, clamp } = K;

const M = new Model('tikki_kwami', { TW: 512, D: 8 });
const { head, body, H } = K.base(M, { mats: { foot: 'limb', paw: 'paw' } });
const FZ = H.c[2] - H.size[2] / 2; // the face plane
const TOP = H.c[1] + H.size[1] / 2;
// the antennae: very thin, curving up and out, a tiny round tip
for (const s of [-1, 1]) {
	const n = s < 0 ? 'r' : 'l';
	const b = M.bone(`antenna_${n}`, 'head', [s * 1.5, TOP - 0.4, -1.0]);
	const pts = [[s * 1.5, TOP - 0.4, -1.0], [s * 1.8, TOP + 1.2, -1.1], [s * 2.5, TOP + 2.6, -1.0], [s * 3.5, TOP + 3.6, -0.8], [s * 4.6, TOP + 4.1, -0.6]];
	M.tube(b, pts, [0.32, 0.3, 0.28, 0.26], 'antenna', { seed: 90 });
	M.cbox(b, [s * 4.75, TOP + 4.15, -0.6], [0.62, 0.62, 0.62], 'antenna', { seed: 95 });
}
// insect leg segments at the hips: two little dark stubs a side
for (const s of [-1, 1]) {
	M.stick(body, [s * 1.6, 3.4, -0.5], [s * 1.0, -0.5, -0.3], 0.9, 0.34, 'seg', { seed: 110 });
	M.stick(body, [s * 1.6, 2.9, 0.4], [s * 1.0, -0.8, 0.25], 0.85, 0.32, 'seg', { seed: 112 });
}

// ================================================================ paint
const C = { red: [236, 50, 66], redL: [255, 120, 128], redD: [168, 22, 40], black: [26, 20, 26], blackL: [64, 56, 70],
	irisD: [36, 44, 140], iris: [76, 104, 214], irisL: [150, 178, 250], seg: [120, 20, 34] };
const SPOTS = [[[0, 0.86, 0.5], 0.62]];
for (const s of [-1, 1]) {
	SPOTS.push([[s, 0.42, 0.12], 0.22], [[s * 0.78, -0.1, 0.62], 0.2], [[s * 0.95, -0.3, -0.05], 0.15], [[s * 0.55, 0.62, -0.2], 0.13]);
}
SPOTS.push([[0, -0.08, 1], 0.2]);
const EYE = { x: 2.12, y: 10.55, rx: 1.76, ry: 2.15, tilt: 0.05, sclera: [250, 250, 255], iris: [C.irisD, C.iris, C.irisL], irx: 0.8, iry: 0.84, ix: -0.08, iy: -0.04,
	pr: 0.44, lashW: 0.16, lashes: [[16, 0.34, 12], [36, 0.38, 11], [56, 0.26, 9]], hl: [[0.22, 0.36, 0.3], [-0.32, -0.38, 0.12], [0.4, -0.1, 0.07]] };
function shade(c, f, s, t, p, info) {
	let col;
	switch (c.mat) {
		case 'antenna': col = K.skin(C.black, p, f, info, { light: C.blackL, tone: 0.2 }); break;
		case 'seg': col = K.skin(C.seg, p, f, info, { tone: 0.2 }); break;
		case 'paw': col = K.skin(C.red, p, f, info, { y0: 2, y1: 7 }); break;
		case 'limb': col = K.skin(C.red, p, f, info, { y0: -1, y1: 7 }); break;
		case 'body': col = K.skin(C.red, p, f, info, { y0: 1.5, y1: 7, light: C.redL, dark: C.redD }); break;
		default: { // head
			col = K.skin(C.red, p, f, info, { y0: 5.5, y1: 15, light: C.redL, dark: C.redD, seed: 3 });
			for (const [d, a] of SPOTS) if (K.spot(p, H.c, d, a)) { col = K.skin(C.black, p, f, info, { light: C.blackL, tone: 0.25 }); break; }
			if (f === 'north' && p[2] < H.c[2]) {
				const e = K.eye(p[0], p[1], EYE);
				if (e) col = e;
				else if (K.smileLine(p[0], p[1], 0, 7.95, 0.75, 0.38, 0.12)) col = [70, 10, 22];
				else if (K.inEll(p[0], p[1], -3.7, 8.25, 0.8, 0.45) < 1 || K.inEll(p[0], p[1], 3.7, 8.25, 0.8, 0.45) < 1) col = mix(col, [255, 140, 150], 0.35);
			}
		}
	}
	return [col, 255, null];
}
const FLOAT = { arm_r: [-75, -50, 0], arm_l: [-75, 50, 0] };
const REST = { antenna_r: [0, 0, 0], antenna_l: [0, 0, 0] };
K.writeClips('tikki', FLOAT, (name, t, o) => {
	if (name === 'idle') return { antenna_r: { rot: [6 * Math.sin(o.w2 + 0.3), 0, 8 * Math.sin(o.w)] }, antenna_l: { rot: [6 * Math.sin(o.w2 + 1), 0, -8 * Math.sin(o.w + 0.5)] } };
	if (name === 'wave') return { antenna_r: { rot: [-10 * o.k + 6 * o.wv, 0, 0] }, antenna_l: { rot: [-10 * o.k + 6 * o.wv, 0, 0] } };
	if (name === 'eat') return { antenna_r: { rot: [8 * o.nib, 0, 0] }, antenna_l: { rot: [8 * o.nib, 0, 0] } };
	if (name === 'tired') return { antenna_r: { rot: [38 + 3 * o.s, 0, 14] }, antenna_l: { rot: [38 + 3 * o.s, 0, -14] } };
	if (name === 'into_jewel') return { antenna_r: { rot: [0, 0, 0] }, antenna_l: { rot: [0, 0, 0] } };
	return {};
});
K.finish(M, 'tikki', shade, 'TIKKI', 'KWAMI OF CREATION - LADYBUG - EARRINGS - LOVES MACARONS', FLOAT, REST,
	{ bg: { top: [176, 150, 156], bottom: [70, 56, 62] } });
