// v0.17.1 (user: "make it look like an actual gadget that scans meteors with like a keypad and a screen that has a map kind of
// thing almost like sonar"): the Meteor Scanner's item model (a chunky handheld: a wide screen head, a grip with a 3x3 keypad and
// two function keys, an antenna with a lit tip, a sensor pod, grip ridges) and its textures -- the case, the keys, and the
// animated sonar screen (a sweep turning over a contour map, the blip of the meteor lighting as the sweep passes).
// Run: node scratchpad/omnitrix_acquire_v0171/gen_scanner.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng, fbm, clamp, mix, smooth } = require('../viperion/lib_concept_models.js');

const RES = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const TEX = path.join(RES, 'textures', 'item');

function canvas(w, h) {
	const px = Buffer.alloc(w * h * 4);
	return {
		w, h, px,
		set(x, y, c, a = 255) {
			if (x < 0 || y < 0 || x >= w || y >= h) return;
			const i = (y * w + x) * 4;
			px[i] = clamp(Math.round(c[0])); px[i + 1] = clamp(Math.round(c[1])); px[i + 2] = clamp(Math.round(c[2])); px[i + 3] = clamp(Math.round(a));
		},
		get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2]]; },
		save(name) { fs.writeFileSync(path.join(TEX, name), encodePng(w, h, px)); console.log('wrote', name, w + 'x' + h); },
	};
}

// ---------------------------------------------------------------- the case (32 x 32: a 16-unit uv maps 2 texels per unit)
// Rows of the sheet (uv units): 0-8 the head's front (around the screen), 8-16 the grip's front (behind the keys),
// x 0-8 front, 8-16 sides / back / top (plain plates with a seam).
const CASE_DARK = [52, 58, 62], CASE = [74, 82, 86], CASE_HI = [104, 114, 118], TRIM = [60, 150, 70], BLACK = [22, 24, 26];
const C = canvas(32, 32);
for (let y = 0; y < 32; y++) {
	for (let x = 0; x < 32; x++) {
		const n = fbm(x * 0.4, y * 0.4, 3, 2);
		let c = CASE.map(v => v + (n - 0.5) * 10);
		// plate edges: a lit top / left edge and a dark bottom / right edge on every 16x16 quadrant
		const qx = x % 16, qy = y % 16;
		if (qy === 0 || qx === 0) c = CASE_HI;
		if (qy === 15 || qx === 15) c = CASE_DARK;
		C.set(x, y, c);
	}
}
// the head's front (0..16 x 0..16 texels): a black bezel round the screen opening, a green trim stripe under it, two screws
for (let y = 0; y < 16; y++) {
	for (let x = 0; x < 16; x++) {
		let c = C.get(x, y);
		if (x >= 2 && x <= 13 && y >= 2 && y <= 13) c = BLACK;
		if (y === 14 && x >= 2 && x <= 13) c = TRIM;
		C.set(x, y, c);
	}
}
for (const [sx, sy] of [[1, 1], [14, 1]]) C.set(sx, sy, [140, 146, 150]);
// the grip's front (0..16 x 16..32): a recessed key well, the brand stripe
for (let y = 16; y < 32; y++) {
	for (let x = 0; x < 16; x++) {
		let c = C.get(x, y);
		if (x >= 2 && x <= 13 && y >= 18 && y <= 29) c = [40, 44, 48].map(v => v + (fbm(x, y, 1, 2) - 0.5) * 6);
		if (x >= 2 && x <= 13 && (y === 17)) c = TRIM;
		C.set(x, y, c);
	}
}
// the sides (16..32 x 0..16): grip ridges
for (let y = 0; y < 16; y++) {
	for (let x = 16; x < 32; x++) {
		let c = C.get(x, y);
		if (y % 3 === 1 && x > 17 && x < 30) c = CASE_DARK;
		C.set(x, y, c);
	}
}
C.save('meteor_scanner_case.png');

// ---------------------------------------------------------------- the keys (16 x 16): grey keys, a green key, a red key, the lit antenna tip
const K = canvas(16, 16);
function keyBlock(x0, y0, base) {
	for (let y = 0; y < 4; y++) {
		for (let x = 0; x < 4; x++) {
			let c = base;
			if (y === 0 || x === 0) c = mix(base, [255, 255, 255], 0.35);
			if (y === 3 || x === 3) c = mix(base, [0, 0, 0], 0.4);
			K.set(x0 + x, y0 + y, c);
		}
	}
}
keyBlock(0, 0, [150, 156, 160]);   // a number key (uv 0-4)
keyBlock(4, 0, [70, 200, 80]);     // the green SCAN key (uv 4-8)
keyBlock(8, 0, [210, 60, 50]);     // the red key (uv 8-12)
keyBlock(12, 0, [120, 255, 130]);  // the antenna's lit tip (uv 12-16)
// a key's face with a tiny glyph dot (row 4-8), the antenna rod (row 8-12), the sensor grille (row 12-16)
for (let x = 0; x < 16; x++) {
	for (let y = 4; y < 8; y++) K.set(x, y, (x % 4 === 1 || x % 4 === 2) && y === 5 ? [60, 64, 68] : [150, 156, 160]);
	for (let y = 8; y < 12; y++) K.set(x, y, [120, 126, 132].map(v => v - (y - 8) * 14));
	for (let y = 12; y < 16; y++) K.set(x, y, (x + y) % 2 === 0 ? [30, 34, 36] : [90, 96, 100]);
}
K.save('meteor_scanner_keys.png');

// ---------------------------------------------------------------- the sonar screen: 32 x 32 frames, animated
const N = 32, S = 32;
const SCR = canvas(S, S * N);
const BG = [6, 26, 12], GRID = [20, 70, 32], CONTOUR = [16, 58, 28], SWEEP = [140, 255, 150];
const BLIP = { x: 21.5, y: 10.5 };
const blipAng = Math.atan2(BLIP.y - 16, BLIP.x - 16);
for (let f = 0; f < N; f++) {
	const sweep = f / N * Math.PI * 2 - Math.PI / 2;
	for (let y = 0; y < S; y++) {
		for (let x = 0; x < S; x++) {
			const dx = x + 0.5 - 16, dy = y + 0.5 - 16;
			const r = Math.hypot(dx, dy);
			let c = BG.slice();
			if (r > 15.2) { SCR.set(x, f * S + y, [4, 10, 6]); continue; }
			// the map: contour lines of a little noise terrain
			const h = fbm(x * 0.06 + 3, y * 0.06 + 7, 2, 3);
			const band = Math.abs(((h * 5) % 1) - 0.5);
			if (band < 0.035 && r < 14) c = CONTOUR;
			// range rings and the cross
			if (Math.abs(r - 5) < 0.45 || Math.abs(r - 10) < 0.45 || Math.abs(r - 14.6) < 0.5) c = GRID.map(v => v + 15);
			if (Math.abs(dx) < 0.5 || Math.abs(dy) < 0.5) c = mix(c, GRID, 0.8);
			// the sweep and its fading wake
			let a = Math.atan2(dy, dx) - sweep;
			a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI); // 0 just behind the beam, growing backward
			const behind = 2 * Math.PI - a; // how far behind the beam (radians)
			const wake = behind < 2.2 ? Math.pow(1 - behind / 2.2, 2.2) : 0;
			c = mix(c, [40, 200, 70], wake * 0.55);
			if (behind < 0.12 || behind > 2 * Math.PI - 0.02) c = mix(c, SWEEP, 0.95);
			// you, at the centre
			if (r < 1.1) c = [210, 255, 210];
			SCR.set(x, f * S + y, c);
		}
	}
	// the blip: lights as the sweep crosses it, fading over the rest of the turn; pulses ring out
	let since = sweep - blipAng;
	since = ((since % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
	const glow = Math.pow(1 - since / (2 * Math.PI), 1.6);
	for (let y = 0; y < S; y++) {
		for (let x = 0; x < S; x++) {
			const d = Math.hypot(x + 0.5 - BLIP.x, y + 0.5 - BLIP.y);
			const cur = SCR.get(x, f * S + y);
			if (d < 1.6) SCR.set(x, f * S + y, mix(cur, [255, 255, 200], 0.35 + glow * 0.65));
			else if (d < 2.6) SCR.set(x, f * S + y, mix(cur, [120, 255, 140], glow * 0.7));
			const ring = Math.abs(d - (2 + since * 1.4));
			if (ring < 0.5 && since < 3.0) SCR.set(x, f * S + y, mix(SCR.get(x, f * S + y), [120, 255, 140], (1 - since / 3.0) * 0.8));
		}
	}
}
SCR.save('meteor_scanner_screen.png');
fs.writeFileSync(path.join(TEX, 'meteor_scanner_screen.png.mcmeta'), JSON.stringify({ animation: { frametime: 2, interpolate: false } }, null, 2) + '\n');

// ---------------------------------------------------------------- the model
const els = [];
function box(name, from, to, faces, extra = {}) {
	els.push(Object.assign({ name, from, to, faces }, extra));
}
const all = (tex, uv) => ({ north: { texture: tex, uv }, south: { texture: tex, uv }, east: { texture: tex, uv }, west: { texture: tex, uv }, up: { texture: tex, uv }, down: { texture: tex, uv } });
// the grip (lower body), the head (the screen housing), the bottom cap
box('grip', [4.5, 0.6, 6.5], [11.5, 8.2, 9.5], Object.assign(all('#case', [8, 8, 16, 16]), { south: { texture: '#case', uv: [0, 8, 8, 16] } }));
box('cap', [4.2, 0, 6.2], [11.8, 0.8, 9.8], all('#case', [8, 14, 16, 16]));
box('head', [3.5, 8, 5.5], [12.5, 15.5, 10.5], Object.assign(all('#case', [8, 0, 16, 8]), { south: { texture: '#case', uv: [0, 0, 8, 8] } }));
// the screen, a hair proud of the bezel
box('screen', [4.6, 8.9, 10.5], [11.4, 14.6, 10.55], { south: { texture: '#screen', uv: [0, 0, 16, 16] } });
// the side ridges on the grip
for (const x of [[3.9, 4.5], [11.5, 12.1]]) {
	box('ridge', [x[0], 1.6, 6.9], [x[1], 7.4, 9.1], all('#case', [8, 0, 16, 8]));
}
// the keypad: three rows of three, and the green SCAN and red keys above it
const cols = [5.15, 7.3, 9.45];
const rows = [5.4, 3.75, 2.1];
for (const y of rows) {
	for (const x of cols) {
		box('key', [x, y, 9.5], [x + 1.4, y + 1.1, 10.05], Object.assign(all('#keys', [0, 0, 4, 4]), { south: { texture: '#keys', uv: [0, 4, 4, 8] } }));
	}
}
box('key_scan', [5.15, 6.9, 9.5], [7.85, 7.8, 10.1], all('#keys', [4, 0, 8, 4]));
box('key_red', [8.75, 6.9, 9.5], [10.85, 7.8, 10.1], all('#keys', [8, 0, 12, 4]));
// the antenna and its lit tip, the sensor pod on the head's top
box('antenna', [10.3, 15.5, 7.4], [11.1, 20.5, 8.2], all('#keys', [0, 8, 16, 12]));
box('antenna_tip', [10.05, 20.5, 7.15], [11.35, 21.8, 8.45], all('#keys', [12, 0, 16, 4]));
box('sensor', [4.6, 15.5, 6.4], [8.4, 16.6, 9.6], Object.assign(all('#case', [8, 8, 16, 16]), { up: { texture: '#keys', uv: [0, 12, 16, 16] } }));

const model = {
	credit: 'Project Hero v0.17.1 -- scratchpad/omnitrix_acquire_v0171/gen_scanner.js',
	textures: {
		particle: 'projecthero:item/meteor_scanner_case',
		case: 'projecthero:item/meteor_scanner_case',
		keys: 'projecthero:item/meteor_scanner_keys',
		screen: 'projecthero:item/meteor_scanner_screen',
	},
	elements: els,
	display: {
		gui: { rotation: [12, -24, 0], translation: [0, -1.25, 0], scale: [0.82, 0.82, 0.82] },
		ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
		fixed: { rotation: [0, 180, 0], translation: [0, -1, 0], scale: [0.85, 0.85, 0.85] },
		thirdperson_righthand: { rotation: [-20, 180, 0], translation: [0, 1.5, -1.5], scale: [0.55, 0.55, 0.55] },
		thirdperson_lefthand: { rotation: [-20, 180, 0], translation: [0, 1.5, -1.5], scale: [0.55, 0.55, 0.55] },
		firstperson_righthand: { rotation: [14, -12, 0], translation: [-1.5, 4.6, -0.5], scale: [0.5, 0.5, 0.5] },
		firstperson_lefthand: { rotation: [14, 12, 0], translation: [-1.5, 4.6, -0.5], scale: [0.5, 0.5, 0.5] },
		head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
	},
};
fs.writeFileSync(path.join(RES, 'models', 'item', 'meteor_scanner.json'), JSON.stringify(model, null, 2) + '\n');
console.log('wrote models/item/meteor_scanner.json', els.length, 'elements');
