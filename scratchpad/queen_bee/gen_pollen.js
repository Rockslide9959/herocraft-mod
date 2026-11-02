// v0.17.2: POLLEN, the Kwami of Subjection (Queen Bee, the Bee Comb, loves pollen -- honeycomb in game) -- a tiny golden
// bee Kwami on the same chibi plan and method as scratchpad/multimouse/gen_mullo.js: a big round yellow head with three
// black stripes over the crown, huge blue eyes with bright highlights, two thin curled antennae with round tips, a tiny
// striped body with little arms and legs, a small dark stinger, and two pairs of pale, see-through-looking wings.
// Bones are Nooroo's set (root, body, head, arm_r, arm_l, leg_r, leg_l, antenna_r/l, wing_r/l, wing_low_r/l), so
// Nooroo's clips (wings flutter in every clip) drive her once renamed (anim_pollen.js).
//
// Usage: node scratchpad/queen_bee/gen_pollen.js   (writes pollen.geo.json, pollen.png, pollen_sheet.png,
// bee_comb.png + bee_comb_x16.png into scratchpad/queen_bee/)
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
const HS = [9.2, 8.2, 7.8];
const BC = () => [0, 4.3 + DY, 0.1];
const EYE = () => ({ x: 2.1, y: HC()[1] - 0.1, rx: 1.6, ry: 1.9 });
const up = (v) => [v[0], v[1] + DY, v[2]];

function blob(M, b, ctr, size, k, mat, opts = {}) {
	const [w, h, d] = size;
	M.cbox(b, ctr, [w, h - 2 * k, d - 2 * k], mat, opts);
	M.cbox(b, ctr, [w - 2 * k, h, d - 2 * k], mat, opts);
	M.cbox(b, ctr, [w - 2 * k, h - 2 * k, d], mat, opts);
	M.cbox(b, ctr, [w - k * 0.9, h - k * 0.9, d - k * 0.9], mat, opts);
}

function build() {
	const M = new Model('pollen_kwami', { TW: 512, D: 8 });
	M.bone('root', null, [0, 0, 0]);
	const body = M.bone('body', 'root', up([0, 2.4, 0]));
	blob(M, body, BC(), [3.4, 3.8, 3.2], 0.8, 'body');
	// the little stinger at the back of the abdomen
	M.cbox(body, up([0, 3.0, 1.9]), [0.8, 0.8, 0.9], 'stinger', { seed: 20 });
	M.cbox(body, up([0, 2.75, 2.45]), [0.4, 0.4, 0.6], 'stinger', { seed: 21 });
	const head = M.bone('head', 'body', up([0, 6.0, 0]));
	blob(M, head, HC(), HS, 1.7, 'head');
	// a tiny round cheek-muzzle with a smile
	M.cbox(head, up([0, 7.9, -3.82]), [3.2, 1.6, 0.3], 'muzzle', { seed: 30 });
	for (const s of [-1, 1]) {
		const n = s < 0 ? 'r' : 'l';
		// thin black antennae curling out and forward, a round tip
		const a = M.bone(`antenna_${n}`, 'head', up([s * 1.4, 13.9, -0.6]));
		M.tube(a, [up([s * 1.4, 13.7, -0.6]), up([s * 1.9, 15.6, -1.0]), up([s * 2.9, 16.9, -1.3]), up([s * 3.6, 16.8, -1.2])], 0.36, 'antenna', { seed: 40 });
		M.cbox(a, up([s * 3.75, 16.85, -1.2]), [1.0, 1.0, 1.0], 'antenna_tip', { seed: 41 });
		// two pairs of wings on the back (flat plates, the shape cut out by the alpha)
		const wu = M.bone(`wing_${n}`, 'body', up([s * 0.8, 5.4, 1.8]), [0, s * 14, s * -18]);
		M.box(wu, s > 0 ? 0.6 : -7.6, 4.6 + DY, 1.7, s > 0 ? 7.6 : -0.6, 10.6 + DY, 1.85, 'wingU', { seed: 50 });
		const wl = M.bone(`wing_low_${n}`, 'body', up([s * 0.8, 4.2, 1.8]), [0, s * 16, s * 20]);
		M.box(wl, s > 0 ? 0.6 : -5.2, 1.2 + DY, 1.7, s > 0 ? 5.2 : -0.6, 4.8 + DY, 1.85, 'wingL', { seed: 51 });
		const arm = M.bone(`arm_${n}`, 'body', up([s * 1.7, 5.6, 0]), [0, 0, -s * 18]);
		M.box(arm, s * 1.7 - 0.5, 3.9 + DY, -0.5, s * 1.7 + 0.5, 5.9 + DY, 0.5, 'limb');
		M.box(arm, s * 1.7 - 0.6, 3.6 + DY, -0.6, s * 1.7 + 0.6, 4.3 + DY, 0.6, 'paw');
		const leg = M.bone(`leg_${n}`, 'body', up([s * 0.9, 2.8, 0]));
		M.box(leg, s * 0.9 - 0.55, 1.2 + DY, -0.5, s * 0.9 + 0.55, 3.0 + DY, 0.55, 'limb');
		M.box(leg, s * 0.9 - 0.65, 0.7 + DY, -0.9, s * 0.9 + 0.65, 1.35 + DY, 0.6, 'paw');
	}
	return M;
}

// ================================================================ the paint
const C = {
	gold: [250, 206, 46], goldD: [214, 160, 24], goldL: [255, 232, 120], belly: [255, 226, 110],
	stripe: [34, 26, 20], stripeL: [70, 56, 44],
	iris: [52, 104, 214], irisL: [120, 186, 250], pupil: [16, 24, 60], line: [30, 22, 18],
	mouth: [120, 64, 30], blush: [250, 150, 90],
	wing: [214, 236, 250], wingL: [246, 252, 255], wingD: [120, 150, 180],
	paw: [44, 34, 26],
};
const inEll = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
function soft(base, c, f, p, info) {
	let col = mul(base, 0.96 + 0.06 * fbm(p[0] * 0.9 + c.seed, p[1] * 0.9, p[2] * 0.9, 3));
	if (f === 'up') col = mix(col, C.goldL, 0.25);
	if (f === 'down') col = mul(col, 0.84);
	if (info.ePx < 0.18) col = mul(col, 0.95);
	return col;
}
// huge round blue eyes: a dark lid line on top, the blue paling toward the bottom, a dark pupil, two highlights
function eyes(p) {
	const E = EYE();
	for (const s of [-1, 1]) {
		const dx = p[0] - s * E.x, dy = p[1] - E.y;
		const q = inEll(dx, dy, 0, 0, E.rx, E.ry);
		if (q >= 1) continue;
		if (q > 0.8 && dy > -0.5) return C.line;
		if (q > 0.86) return mix(C.line, C.iris, 0.4);
		let col = mix(C.iris, C.irisL, clamp((-dy / E.ry) * 0.9 + 0.1, 0, 1));
		if (inEll(dx, dy, 0, -0.1, E.rx * 0.5, E.ry * 0.55) < 1) col = C.pupil;
		if (inEll(dx, dy, -0.5, 0.65, 0.55, 0.6) < 1) col = [255, 255, 255];
		if (inEll(dx, dy, 0.55, -0.8, 0.25, 0.25) < 1) col = [236, 244, 255];
		return col;
	}
	return null;
}
function shade(c, f, s, t, p, info) {
	const y = p[1] - DY;
	if (c.mat === 'wingU' || c.mat === 'wingL') {
		const side = f === 'north' || f === 'south';
		if (!side) return [C.wingD, 0, null];
		const rootLeft = (f === 'north') === (c.from[0] >= 0) ? false : true;
		const u = rootLeft ? s : 1 - s, v = t;
		const upper = c.mat === 'wingU';
		// rounded teardrops: the upper pair longer and tipped up-out, the lower a small lobe
		const cx = upper ? 0.52 : 0.5, cy = upper ? 0.45 : 0.5, rx = upper ? 0.56 : 0.52, ry = upper ? 0.5 : 0.48;
		const q = ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 - (upper ? 0.3 * u * (1 - v) : 0);
		if (q > 1 || u < 0.02) return [C.wing, 0, null];
		let col = mix(C.wing, C.wingL, 0.5 * (1 - v));
		if (q > 0.8) col = C.wingD;
		const vein = Math.abs(Math.sin(Math.atan2(v - 0.5, u) * 4));
		if (vein < 0.1 && q < 0.8) col = mix(col, C.wingD, 0.45);
		return [col, 255, null];
	}
	let col;
	switch (c.mat) {
		case 'antenna': case 'antenna_tip': case 'stinger': case 'paw': col = soft(C.stripe, c, f, p, info); if (f === 'up') col = mix(col, C.stripeL, 0.5); break;
		case 'limb': col = soft(C.gold, c, f, p, info); break;
		case 'muzzle': {
			col = soft(mix(C.gold, C.belly, 0.6), c, f, p, info);
			if (f === 'north') {
				const ax = Math.abs(p[0]);
				const sm = 7.55 + 0.28 * ax * ax;
				if (ax < 1.1 && Math.abs(y - sm) < 0.1) col = C.mouth;
				if (inEll(ax, y, 1.35, 7.9, 0.35, 0.28) < 1) col = mix(col, C.blush, 0.5);
			}
			break;
		}
		case 'body': {
			// a golden body with two black bands round it
			col = soft(C.gold, c, f, p, info);
			const by = y - 4.3;
			if (Math.abs(by + 0.4) < 0.42 || Math.abs(by - 1.15) < 0.38) col = soft(C.stripe, c, f, p, info);
			if (p[2] < -0.6 && Math.abs(p[0]) < 1.0 && Math.abs(by + 0.4) >= 0.42 && Math.abs(by - 1.15) >= 0.38) col = soft(C.belly, c, f, p, info);
			break;
		}
		default: col = soft(C.gold, c, f, p, info);
	}
	if (c.mat === 'head') {
		if (p[2] < -0.4 && y < 8.4) col = soft(mix(C.gold, C.belly, 0.5), c, f, p, info);
		// three black stripes over the crown, front to back (the top and the back of the head; not over the eyes)
		const hy = y - 10.0;
		const crown = hy > 1.6 || p[2] > 1.2;
		if (crown) {
			const band = f === 'south' ? (5.5 - hy) * 0.55 : (p[2] + 4.5) / 9.0 * 5.0 + hy * 0.12; // bands run side to side (down the back of the head)
			if (Math.floor(band) % 2 === 1 && hy > -1.5) col = soft(C.stripe, c, f, p, info);
		}
		if (f === 'north' && p[2] < -3.0) {
			const e = eyes(p);
			if (e) col = e;
			if (inEll(Math.abs(p[0]), y, 3.25, 8.55, 0.6, 0.35) < 1) col = mix(col, C.blush, 0.4);
		}
	}
	return [col, 255, null];
}

// ================================================================ build (twice: once to find the floor; wings ignored)
const notWing = c => !c.mat.startsWith('wing');
let M = build();
DY = -M.bounds(M.world({}), notWing).lo[1];
M = build();
if (Math.abs(M.bounds(M.world({}), notWing).lo[1]) > 1e-6) throw new Error('not grounded');
M.setHeight(0.62, notWing);
M.layout();
M.paint(shade);
writeJson(path.join(OUT, 'pollen.geo.json'), M.geoJson('geometry.kwami_pollen'));
fs.writeFileSync(path.join(OUT, 'pollen.png'), L.encodePng(M.TW, M.TH, M.tex));
console.log(`pollen: ${M.bones.length} bones (${M.bones.map(b => b.name).join(', ')}), ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}`);

// ================================================================ the concept sheet
const FLOAT = { body: [6, 0, 0], head: [-6, 0, 5], arm_r: [-20, 0, 40], arm_l: [-30, 0, -30], leg_r: [-14, 0, 0], leg_l: [10, 0, 0] };
const it = pose => ({ model: M, pose });
const fl = [{ x: 0, z: 0, r: 0.42 }];
L.sheet(path.join(OUT, 'pollen_sheet.png'), 'POLLEN', 'KWAMI OF SUBJECTION - QUEEN BEE - BEE COMB - LOVES POLLEN', [
	{ label: 'FRONT', items: [it(FLOAT)], cam: { yaw: 0, pitch: 6 }, floor: fl },
	{ label: 'THREE-QUARTER', items: [it(FLOAT)], cam: { yaw: 35, pitch: 12 }, floor: fl },
	{ label: 'SIDE', items: [it(FLOAT)], cam: { yaw: 90, pitch: 6 }, floor: fl },
	{ label: 'BACK', items: [it(FLOAT)], cam: { yaw: 180, pitch: 10 }, floor: fl },
], { panelH: 800, minW: 2000, bg: { top: [150, 160, 170], bottom: [60, 66, 74] } });

// ================================================================ the Bee Comb: a hand-painted 16 x 16 item icon
// (a hair comb: a gold-and-black striped spine with a little bee-stripe crest, the teeth hanging below)
{
	const KEY = {
		o: [40, 30, 18], k: [26, 22, 20], K: [70, 60, 52],
		y: [214, 160, 24], Y: [250, 206, 46], h: [255, 236, 140], w: [255, 252, 232],
	};
	const G = [
		'................',
		'................',
		'.....oooooo.....',
		'....oYhYYYYo....',
		'...okKkkkKkko...',
		'..oYYhYYYYYYyo..',
		'.okkKkkkkkKkkko.',
		'.oYwYYYYYYYYYyo.',
		'.ooooooooooooo..',
		'.oyo.oyo.oyo.oyo',
		'.oyo.oyo.oyo.oyo',
		'.oyo.oyo.oyo.oyo',
		'..o...o...o...o.',
		'................',
		'................',
		'................',
	];
	const px = Buffer.alloc(16 * 16 * 4);
	G.forEach((row, y) => { if (row.length !== 16) throw new Error('comb row ' + y + ' is ' + row.length); [...row].forEach((ch, x) => { if (ch === '.') return; const i = (y * 16 + x) * 4; const k = KEY[ch]; px[i] = k[0]; px[i + 1] = k[1]; px[i + 2] = k[2]; px[i + 3] = 255; }); });
	fs.writeFileSync(path.join(OUT, 'bee_comb.png'), L.encodePng(16, 16, px));
	const pv = T.preview({ w: 16, h: 16, px }, 16);
	fs.writeFileSync(path.join(OUT, 'bee_comb_x16.png'), T.encode(pv.w, pv.h, pv.px));
	console.log('bee comb written');
}
