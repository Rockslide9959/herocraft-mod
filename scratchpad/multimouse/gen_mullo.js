// PREVIEW ONLY: MULLO, the Kwami of Multiplication (Multimouse, the Mouse Pendant, loves cheese) -- a tiny pale
// lilac-white mouse Kwami on the same chibi plan and method as scratchpad/viperion/gen_sass.js: a big round head, two HUGE
// round ears with soft pink insides, big plum eyes with lashes and bright highlights, a pink nose, two buck teeth in a
// smile, long thin whiskers, a tiny body with little arms and legs, and a long thin tail curling below.
// Bones are the shared Kwami set (root, body, head, arm_r, arm_l, leg_r, leg_l, tail) so the shared clips still drive it.
//
// Writes ONLY into scratchpad/multimouse/ (Usage: node scratchpad/multimouse/gen_mullo.js):
//   mullo.geo.json, mullo.png (the GeckoLib geo + texture), mullo_sheet.png (the concept sheet),
//   mouse_pendant.png (16 x 16 item icon) + mouse_pendant_x16.png.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const T = require('../viperion/skin_tools.js');
const { Model, mix, mul, fbm, clamp } = L;
const OUT = __dirname;
const writeJson = (f, o) => fs.writeFileSync(f, JSON.stringify(o, null, '\t') + '\n');

// ================================================================ the rig
let DY = 0;
const HC = () => [0, 10.0 + DY, 0];
const HS = [9.0, 7.8, 7.6];
const BC = () => [0, 4.3 + DY, 0.1];
const EYE = () => ({ x: 2.05, y: HC()[1] + 0.15, rx: 1.45, ry: 1.75 });
const up = (v) => [v[0], v[1] + DY, v[2]];
const DISC = [[1, 0.44], [0.93, 0.72], [0.72, 0.93], [0.44, 1]];
const EAR_R = 3.9, EAR_RI = 2.75;
const earC = s => up([s * 4.35, 14.7, 0.9]);

function blob(M, b, ctr, size, k, mat, opts = {}) {
	const [w, h, d] = size;
	M.cbox(b, ctr, [w, h - 2 * k, d - 2 * k], mat, opts);
	M.cbox(b, ctr, [w - 2 * k, h, d - 2 * k], mat, opts);
	M.cbox(b, ctr, [w - 2 * k, h - 2 * k, d], mat, opts);
	M.cbox(b, ctr, [w - k * 0.9, h - k * 0.9, d - k * 0.9], mat, opts);
}

// the tail: out from the back of the body, down, and a loose curl forward under him, thin and tapering
function tailPath() {
	const pts = [[0, 2.6, 1.3], [0.2, 1.8, 2.6], [0.5, 0.7, 3.4]];
	const c = [1.4, -1.6, 2.8];
	const a0 = Math.atan2(0.7 - c[1], 0.5 - c[0]);
	const n = 14;
	for (let i = 1; i <= n; i++) {
		const t = i / n, a = a0 - t * Math.PI * 1.75, r = 2.4 - 1.0 * t;
		pts.push([c[0] + Math.cos(a) * r, c[1] + Math.sin(a) * r, c[2] - 1.6 * t]);
	}
	const widths = [];
	for (let i = 0; i < pts.length - 1; i++) { const t = i / (pts.length - 2); const w = 0.75 - 0.42 * t; widths.push([w, w]); }
	return { pts: pts.map(up), widths };
}

function build() {
	const M = new Model('mullo_kwami', { TW: 512, D: 8 });
	M.bone('root', null, [0, 0, 0]);
	const body = M.bone('body', 'root', up([0, 2.4, 0]));
	blob(M, body, BC(), [3.2, 3.6, 2.9], 0.8, 'body');
	const head = M.bone('head', 'body', up([0, 6.0, 0]));
	blob(M, head, HC(), HS, 1.6, 'head');
	// the little muzzle low on the face: the nose on its tip, the smile and the buck teeth under it
	M.cbox(head, up([0, 7.95, -3.95]), [3.6, 1.9, 0.7], 'muzzle', { seed: 50 });
	M.cbox(head, up([0, 8.65, -4.45]), [1.1, 0.75, 0.5], 'nose', { seed: 51 });
	for (const s of [-1, 1]) M.cbox(head, up([s * 0.3, 6.95, -4.32]), [0.5, 0.62, 0.16], 'tooth', { seed: 52 });
	// long thin whiskers: three a side, fanning out from the muzzle
	for (const s of [-1, 1]) for (const [dy, ang] of [[0.45, 10], [0, 0], [-0.45, -10]]) {
		const from = up([s * 1.6, 8.1 + dy, -4.05]);
		const dir = [s * Math.cos(ang * Math.PI / 180), Math.sin(ang * Math.PI / 180), -0.12];
		M.stick(head, from, dir, 4.2, [0.14, 0.14], 'whisker', { seed: 53 });
	}
	// three tiny lashes flicking up off the outer corner of each eye (the rest are painted)
	for (const s of [-1, 1]) {
		const E = EYE();
		for (const [k, ang] of [[0, 35], [1, 60], [2, 82]]) {
			const a0 = Math.PI / 2 - (k * 0.28 + 0.38);
			const from = [s * (E.x + Math.cos(a0) * E.rx * 0.98), E.y + Math.sin(a0) * E.ry * 0.98, -3.82];
			const dir = [s * Math.cos(ang * Math.PI / 180), Math.sin(ang * Math.PI / 180), -0.25];
			M.stick(head, from, dir, 0.75, [0.18, 0.12], 'lash', { seed: 54 });
		}
	}
	// the two HUGE round ears: rounded-octagon discs, pale outside, a soft pink inner disc on the front
	for (const s of [-1, 1]) {
		const C = earC(s);
		const rot = { rot: [-10, 0, s * 16], pivot: up([s * 3.2, 12.6, 0.9]) };
		for (const [kw, kh] of DISC) M.cbox(head, C, [2 * EAR_R * kw, 2 * EAR_R * kh, 0.9], 'ear', Object.assign({ seed: 60 }, rot));
		const Ci = [C[0], C[1] - 0.2, C[2] - 0.5];
		for (const [kw, kh] of DISC) M.cbox(head, Ci, [2 * EAR_RI * kw, 2 * EAR_RI * kh, 0.18], 'ear_in', Object.assign({ seed: 61 }, rot));
	}
	for (const s of [-1, 1]) {
		const n = s < 0 ? 'r' : 'l';
		const arm = M.bone(`arm_${n}`, 'body', up([s * 1.6, 5.6, 0]), [0, 0, -s * 18]);
		M.box(arm, s * 1.6 - 0.5, 3.9 + DY, -0.5, s * 1.6 + 0.5, 5.9 + DY, 0.5, 'limb');
		M.box(arm, s * 1.6 - 0.6, 3.6 + DY, -0.6, s * 1.6 + 0.6, 4.3 + DY, 0.6, 'paw');
		const leg = M.bone(`leg_${n}`, 'body', up([s * 0.85, 2.8, 0]));
		M.box(leg, s * 0.85 - 0.55, 1.2 + DY, -0.5, s * 0.85 + 0.55, 3.0 + DY, 0.55, 'limb');
		M.box(leg, s * 0.85 - 0.65, 0.7 + DY, -1.25, s * 0.85 + 0.65, 1.35 + DY, 0.6, 'paw'); // a long little mouse foot
	}
	const tail = M.bone('tail', 'body', up([0, 2.6, 1.3]));
	const TP = tailPath();
	M.tube(tail, TP.pts, TP.widths, 'tail', { seed: 100 });
	return M;
}

// ================================================================ the paint
const C = {
	fur: [236, 222, 244], furD: [200, 180, 214], furL: [250, 244, 253], belly: [250, 240, 248],
	ear: [244, 176, 202], earD: [222, 136, 172], earL: [252, 206, 222],
	iris: [96, 30, 58], irisL: [170, 64, 84], pupil: [36, 10, 24], line: [44, 20, 34],
	nose: [238, 118, 152], noseL: [252, 176, 198], mouth: [150, 70, 96], tooth: [252, 252, 246],
	whisker: [236, 232, 240], tail: [232, 196, 214], paw: [246, 210, 222],
};
const inEll = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
function soft(base, c, f, p, info) { // soft fur: low-frequency mottling, a lit top, a shaded underside, soft edges
	let col = mul(base, 0.96 + 0.06 * fbm(p[0] * 0.9 + c.seed, p[1] * 0.9, p[2] * 0.9, 3));
	if (f === 'up') col = mix(col, C.furL, 0.35);
	if (f === 'down') col = mul(col, 0.84);
	if (info.ePx < 0.18) col = mul(col, 0.95);
	return col;
}
function discDepth(dx, dy, R) { let b = -1; for (const [kw, kh] of DISC) b = Math.max(b, Math.min(R * kw - Math.abs(dx), R * kh - Math.abs(dy))); return b; }
// big round plum eyes: a dark lid line on top, a plum iris that warms to red-brown at the bottom, a dark pupil,
// one big and one small highlight
function eyes(p) {
	const E = EYE();
	for (const s of [-1, 1]) {
		const ex = s * E.x, dx = p[0] - ex, dy = p[1] - E.y;
		const q = inEll(dx, dy, 0, 0, E.rx, E.ry);
		if (q >= 1) continue;
		if (q > 0.8 && dy > -0.5) return C.line;           // the upper lid line (thick, a lash line)
		if (q > 0.86) return mix(C.line, C.iris, 0.4);
		let col = mix(C.iris, C.irisL, clamp((-dy / E.ry) * 0.9 + 0.1, 0, 1));
		if (inEll(dx, dy, 0, -0.1, E.rx * 0.5, E.ry * 0.55) < 1) col = C.pupil;
		if (inEll(dx, dy, -s * 0.0 - 0.45, 0.6, 0.52, 0.56) < 1) col = [255, 255, 255];
		if (inEll(dx, dy, 0.5, -0.75, 0.24, 0.24) < 1) col = [250, 236, 244];
		return col;
	}
	return null;
}
// painted lashes: two dark flicks above the outer corner of each eye, on the face itself
function paintedLash(p) {
	const E = EYE();
	for (const s of [-1, 1]) {
		const dx = s * (p[0] - s * E.x), dy = p[1] - E.y;
		const q = inEll(dx, dy, 0, 0, E.rx * 1.18, E.ry * 1.12);
		if (q < 1.0 && q > 0.82 && dy > E.ry * 0.25 && dx > E.rx * 0.2) return true;
	}
	return false;
}
function shade(c, f, s, t, p, info) {
	let col;
	const y = p[1] - DY;
	switch (c.mat) {
		case 'paw': col = soft(C.paw, c, f, p, info); break;
		case 'limb': col = soft(C.fur, c, f, p, info); break;
		case 'tail': col = soft(f === 'down' ? mul(C.tail, 0.9) : C.tail, c, f, p, info); if (Math.floor((p[0] + p[1] + p[2]) * 2.2) % 2) col = mul(col, 0.95); break;
		case 'whisker': col = C.whisker; break;
		case 'lash': col = C.line; break;
		case 'tooth': col = info.ePx < 0.05 ? [210, 206, 200] : C.tooth; break;
		case 'nose': {
			col = soft(C.nose, c, f, p, info);
			if (f === 'north' && s < 0.45 && t < 0.5) col = C.noseL; // a shiny spot
			if (f === 'up') col = mix(C.nose, C.noseL, 0.5);
			break;
		}
		case 'muzzle': {
			col = soft(mix(C.fur, C.belly, 0.6), c, f, p, info);
			if (f === 'north') {
				// a sweet little "w" smile under the nose
				const ax = Math.abs(p[0]);
				const sm = 7.3 + 0.32 * Math.abs(Math.sin(ax * 2.4)) + 0.16 * ax;
				if (ax < 1.25 && Math.abs(y - sm) < 0.1) col = C.mouth;
				if (ax < 0.08 && y > 7.35 && y < 8.3) col = C.mouth; // the philtrum line down from the nose
				// pink cheeks
				if (inEll(ax, y, 1.45, 7.7, 0.4, 0.3) < 1) col = mix(col, C.ear, 0.5);
			}
			break;
		}
		case 'ear': {
			const s0 = p[0] < 0 ? -1 : 1, E = earC(s0);
			const dx = p[0] - E[0], dy = p[1] - E[1];
			col = soft(C.fur, c, f, p, info);
			if (discDepth(dx, dy, EAR_R) < 0.35 && (f === 'north' || f === 'south')) col = mix(col, C.furL, 0.6);
			if (f === 'south') col = mix(col, C.furD, 0.25 + 0.2 * clamp(-dy / EAR_R, 0, 1));
			break;
		}
		case 'ear_in': {
			const s0 = p[0] < 0 ? -1 : 1, E = earC(s0);
			const dx = p[0] - E[0], dy = p[1] - E[1] + 0.2;
			const d = discDepth(dx, dy, EAR_RI);
			col = mix(C.ear, C.earL, clamp(d / EAR_RI * 1.4, 0, 1)); // soft pink, paler in the middle
			if (d < 0.3) col = mix(col, C.earD, 0.5);
			break;
		}
		case 'body': {
			const belly = p[2] < -0.4 && Math.abs(p[0]) < 1.1;
			col = soft(belly ? C.belly : C.fur, c, f, p, info);
			break;
		}
		default: col = soft(C.fur, c, f, p, info);
	}
	if (c.mat === 'head') {
		if (p[2] < -0.4 && y < 8.4) col = soft(mix(C.fur, C.belly, 0.5), c, f, p, info); // a paler lower face
		if (f === 'up') col = mix(col, C.furL, 0.2);
		if (f === 'north' && p[2] < -3.0) {
			if (paintedLash(p)) col = C.line;
			const e = eyes(p);
			if (e) col = e;
			// soft pink blush under the eyes
			if (inEll(Math.abs(p[0]), y, 3.15, 8.75, 0.6, 0.35) < 1) col = mix(col, C.ear, 0.45);
		}
	}
	return [col, 255, null];
}

// ================================================================ build (twice: once to find the floor)
let M = build();
DY = -M.bounds(M.world({})).lo[1];
M = build();
if (Math.abs(M.bounds(M.world({})).lo[1]) > 1e-6) throw new Error('not grounded');
M.setHeight(0.62);
M.layout();
M.paint(shade);
writeJson(path.join(OUT, 'mullo.geo.json'), M.geoJson('geometry.kwami_mullo'));
fs.writeFileSync(path.join(OUT, 'mullo.png'), L.encodePng(M.TW, M.TH, M.tex));
console.log(`mullo: ${M.bones.length} bones (${M.bones.map(b => b.name).join(', ')}), ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}`);

// ================================================================ the concept sheet
const FLOAT = { body: [6, 0, 0], head: [-6, 0, 5], arm_r: [-20, 0, 40], arm_l: [-30, 0, -30], leg_r: [-14, 0, 0], leg_l: [10, 0, 0], tail: [-4, 0, 0] };
const it = pose => ({ model: M, pose });
const fl = [{ x: 0, z: 0, r: 0.42 }];
L.sheet(path.join(OUT, 'mullo_sheet.png'), 'MULLO', 'KWAMI OF MULTIPLICATION - MULTIMOUSE - MOUSE PENDANT - LOVES CHEESE', [
	{ label: 'FRONT', items: [it(FLOAT)], cam: { yaw: 0, pitch: 6 }, floor: fl },
	{ label: 'THREE-QUARTER', items: [it(FLOAT)], cam: { yaw: 35, pitch: 12 }, floor: fl },
	{ label: 'SIDE', items: [it(FLOAT)], cam: { yaw: 90, pitch: 6 }, floor: fl },
	{ label: 'BACK', items: [it(FLOAT)], cam: { yaw: 180, pitch: 10 }, floor: fl },
], { panelH: 800, minW: 2000, bg: { top: [150, 160, 170], bottom: [60, 66, 74] } });

// ================================================================ the Mouse Pendant: a hand-painted 16 x 16 item icon
{
	const KEY = {
		c: [64, 56, 74], C: [122, 110, 134],                           // the cord, dark + its lit strand
		o: [44, 46, 58], d: [104, 110, 126], m: [156, 162, 178], l: [206, 210, 222], w: [250, 252, 255], // silver
		p: [170, 38, 108], P: [226, 84, 156], h: [250, 170, 210],     // the pink centre
	};
	const G = [
		'.C............C.',
		'.cC..........Cc.',
		'..cC........Cc..',
		'...cC......Cc...',
		'....cC....Cc....',
		'.....cCooCc.....',
		'......olwo......',
		'.....oooooo.....',
		'....olllllmo....',
		'...olwlmmmmdo...',
		'...olmmhPmmdo...',
		'...olmhPPpmdo...',
		'...ommPPppddo...',
		'...ommdppdddo...',
		'....oddmmmdo....',
		'.....oooooo.....',
	];
	const px = Buffer.alloc(16 * 16 * 4);
	G.forEach((row, y) => { if (row.length !== 16) throw new Error('pendant row ' + y + ' is ' + row.length); [...row].forEach((ch, x) => { if (ch === '.') return; const i = (y * 16 + x) * 4; const k = KEY[ch]; px[i] = k[0]; px[i + 1] = k[1]; px[i + 2] = k[2]; px[i + 3] = 255; }); });
	fs.writeFileSync(path.join(OUT, 'mouse_pendant.png'), L.encodePng(16, 16, px));
	const pv = T.preview({ w: 16, h: 16, px }, 16);
	fs.writeFileSync(path.join(OUT, 'mouse_pendant_x16.png'), T.encode(pv.w, pv.h, pv.px));
	console.log('mouse pendant written');
}
