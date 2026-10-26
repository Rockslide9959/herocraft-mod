// v0.17.1: WAYZZ, the Kwami of Protection (Carapace), remodelled on Sass's plan: a light green round head with a single
// short antenna on top ending in a small ball, big round eyes with yellow-amber sclera and green irises (calm and kind,
// with lashes), a gentle smile, a small domed turtle SHELL on the back (darker green plates, a lighter rim), tiny arms
// folded and short legs.
// Writes geo/miraculous/wayzz.geo.json, textures/entity/miraculous/wayzz.png, the animation.kwami.wayzz.* clips and
// scratchpad/kwamis_v0171/wayzz_sheet.png.   Usage: node scratchpad/kwamis_v0171/gen_wayzz.js
'use strict';
const K = require('./kwami_lib.js');
const { L, Model, mix, mul, clamp, fbm } = K;

const M = new Model('wayzz_kwami', { TW: 512, D: 8 });
const { head, body, H } = K.base(M, { head: { c: [0, 10.2, -0.2], size: [10.0, 8.6, 8.4], r: [1.4, 2.3, 1.9] }, body: { size: [4.0, 4.0, 3.2] } });
const TOP = H.c[1] + H.size[1] / 2;
// the antenna: short, a small ball on top
M.tube(head, [[0, TOP - 0.3, -0.4], [0.1, TOP + 1.0, -0.5], [0.35, TOP + 2.0, -0.4]], [0.36, 0.32], 'antenna', { seed: 600 });
K.blob(M, head, [0.4, TOP + 2.4, -0.4], [1.1, 1.1, 1.1], 0.25, 'ball', { seed: 605 });
// the shell: a small dome on the back with a lighter rim
const shell = M.bone('shell', 'body', [0, 4.0, 2.2]);
K.roundBox(M, shell, [0, 4.3, 2.75], [5.0, 5.4, 2.4], [1.3, 1.4, 0.8], 'shell', { seed: 610 });
K.blob(M, shell, [0, 4.3, 2.05], [5.5, 5.9, 0.8], 0.6, 'rim', { seed: 620 });

// ================================================================ paint
const C = { gr: [150, 212, 116], grL: [202, 240, 160], grD: [86, 148, 66], belly: [222, 236, 156], shell: [60, 126, 66], shellL: [96, 166, 86], shellD: [32, 82, 40],
	rim: [184, 210, 110], scl: [250, 200, 76], irisD: [26, 96, 44], iris: [64, 156, 74], irisL: [150, 222, 120] };
const EYE = { x: 2.15, y: 10.35, rx: 1.75, ry: 1.95, tilt: -0.04, sclera: [250, 202, 84], iris: [C.irisD, C.iris, C.irisL], irx: 0.64, iry: 0.68, ix: -0.06, iy: -0.08,
	pr: 0.42, lid: 0.12, lidCurve: -0.1, lidCol: [120, 186, 96], ow: 0.1, lashW: 0.12, outline: [26, 40, 20], lashes: [[20, 0.26, 10], [40, 0.22, 8]],
	hl: [[0.2, 0.26, 0.24], [-0.3, -0.36, 0.1]] };
const green = (p, f, info, o) => K.skin(C.gr, p, f, info, Object.assign({ light: C.grL, dark: C.grD, tone: 0.3 }, o));
// hexagonal plates on the shell's back
function plate(x, y) {
	const s = 1.35, h = s * Math.sqrt(3) / 2;
	let best = 1e9, second = 1e9;
	for (let j = -4; j <= 4; j++) for (let i = -4; i <= 4; i++) {
		const cx = i * s * 1.5, cy = j * h * 2 + (i & 1 ? h : 0) + 4.3;
		const d = Math.hypot(x - cx, y - cy);
		if (d < best) { second = best; best = d; } else if (d < second) second = d;
	}
	return second - best; // small near the plate seams
}
function shade(c, f, s, t, p, info) {
	let col;
	switch (c.mat) {
		case 'antenna': col = green(p, f, info, { y0: 13, y1: 17 }); break;
		case 'ball': col = K.skin(C.grL, p, f, info, { tone: 0.2 }); break;
		case 'shell': {
			const seam = plate(p[0], p[1]);
			col = K.skin(C.shell, p, f, info, { light: C.shellL, dark: C.shellD, tone: 0.35, y0: 1, y1: 7.5 });
			if (f === 'south' || f === 'up' || f === 'east' || f === 'west') {
				if (seam < 0.14) col = mul(C.shellD, 0.9);
				else col = mix(col, C.shellL, clamp(0.5 - seam * 0.6, 0, 0.4));
			}
			break;
		}
		case 'rim': col = K.skin(C.rim, p, f, info, { tone: 0.2, y0: 1, y1: 8 }); break;
		case 'body': {
			col = green(p, f, info, { y0: 1.5, y1: 7 });
			if (p[2] < -0.5 && Math.abs(p[0]) < 1.4) {
				col = K.skin(C.belly, p, f, info, { tone: 0.15 });
				if (f === 'north' && (Math.abs(p[0]) < 0.06 || Math.abs(((p[1] - 2.2) % 1.1) - 0.55) > 0.5)) col = mul(col, 0.86);
			}
			break;
		}
		case 'head': {
			col = green(p, f, info, { y0: 5.5, y1: 15, seed: 8 });
			if (f === 'north' && p[2] < H.c[2]) {
				const e = K.eye(p[0], p[1], EYE);
				if (e) col = e;
				else if (K.smileLine(p[0], p[1], 0, 7.95, 0.95, 0.32, 0.11)) col = [40, 76, 30];
			}
			break;
		}
		default: col = green(p, f, info, { y0: -1, y1: 7 });
	}
	return [col, 255, null];
}
const FLOAT = { arm_r: [-75, -50, 0], arm_l: [-75, 50, 0] };
K.writeClips('wayzz', FLOAT, (name, t, o) => {
	if (name === 'idle') return { shell: { rot: [1.5 * Math.sin(o.w2 + 1), 0, 0] } };
	return {};
});
K.finish(M, 'wayzz', shade, 'WAYZZ', 'KWAMI OF PROTECTION - CARAPACE - TURTLE BRACELET', FLOAT, {},
	{ bg: { top: [150, 168, 150], bottom: [52, 62, 54] } });
