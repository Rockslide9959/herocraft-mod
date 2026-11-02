// v0.17.2: the Recalibrated Omnitrix's dial icons -- the Alien Force ten. Same frame as the prototype's (gen_omnitrix_dial_icons.js:
// a disc, a dark rim, the lit lime diamond with the alien's black silhouette) but on the recalibrated watch's BLACK disc (its face
// morph ends on black, face_morph_recal.png). Silhouettes are simple vector shapes (basic versions -- the user may supply art later).
//   textures/gui/omnitrix/dial_<id>.png, dial_<id>_red.png   64x64 (+ .mcmeta blur)
//   textures/font/omnitrix_dial/<id>.png                      32x32 (the inline text icon)
// Usage: node scratchpad/recal_v0172/gen_recal_dial_icons.js [previewPng]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');
const RES = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const GUI = path.join(RES, 'textures', 'gui', 'omnitrix');
const FONT = path.join(RES, 'textures', 'font', 'omnitrix_dial');
const BLUR = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n';

// shapes in a 100-unit box centred on the icon (x right, y down); the diamond spans |x|/30 + |y|/42 <= 1
const E = (cx, cy, rx, ry, rot = 0) => ({ t: 'e', cx, cy, rx, ry, rot });
const P = (...pts) => ({ t: 'p', pts });
const L = (x0, y0, x1, y1, w) => ({ t: 'l', x0, y0, x1, y1, w });
const SIL = {
	// plant body, broad shoulders, the flame-lit crest of a head
	swampfire: [E(0, -22, 7, 8), P([-4, -31], [-1, -38], [1, -31], [4, -37], [5, -28]), P([-15, -13], [15, -13], [10, 12], [-10, 12]),
		L(-13, -11, -21, 8, 5), L(13, -11, 21, 8, 5), L(-6, 10, -8, 32, 6), L(6, 10, 8, 32, 6)],
	// a small body under the big round speaker head
	echo_echo: [E(0, -16, 12, 11), P([-8, -6], [8, -6], [6, 14], [-6, 14]), L(-7, -3, -14, 10, 3.5), L(7, -3, 14, 10, 3.5),
		L(-4, 13, -5, 28, 4), L(4, 13, 5, 28, 4)],
	// the huge brawler: a small head on a massive torso, thick legs, a tail
	humungousaur: [E(0, -27, 5, 5), P([-18, -21], [18, -21], [14, 10], [-14, 10]), L(-17, -18, -24, 6, 8), L(17, -18, 24, 6, 8),
		L(-8, 8, -10, 31, 9), L(8, 8, 10, 31, 9), L(12, 8, 22, 22, 4)],
	// manta wings spread wide, the head and the long tail
	jetray: [P([0, -22], [26, -2], [18, 2], [6, 0], [0, 6], [-6, 0], [-18, 2], [-26, -2]), E(0, -20, 5, 6), L(0, 4, 0, 34, 2.5),
		L(-4, 2, -6, 14, 3), L(4, 2, 6, 14, 3)],
	// the moth: wings folded down round the body like a hooded cloak, two antennae
	big_chill: [P([0, -28], [12, -18], [16, 18], [8, 30], [0, 22], [-8, 30], [-16, 18], [-12, -18]), L(-3, -27, -9, -38, 2), L(3, -27, 9, -38, 2)],
	// crystal man: spikes off the head and shoulders
	chromastone: [P([-6, -20], [-4, -34], [0, -24], [4, -34], [6, -20], [0, -15]), P([-14, -13], [14, -13], [9, 12], [-9, 12]),
		P([-14, -13], [-24, -22], [-17, -6]), P([14, -13], [24, -22], [17, -6]), L(-12, -10, -18, 8, 5), L(12, -10, 18, 8, 5),
		L(-5, 10, -7, 31, 5.5), L(5, 10, 7, 31, 5.5)],
	// the crab: a domed shell / brain, wide body, claws up
	brainstorm: [E(0, -16, 14, 10), P([-14, -10], [14, -10], [12, 6], [-12, 6]), L(-12, -6, -22, -16, 4), E(-23, -20, 5, 4, -0.6),
		L(12, -6, 22, -16, 4), E(23, -20, 5, 4, 0.6), L(-8, 5, -14, 26, 3.5), L(-3, 6, -5, 28, 3.5), L(3, 6, 5, 28, 3.5), L(8, 5, 14, 26, 3.5)],
	// four arms out, a round head, the tail
	spidermonkey: [E(0, -22, 7, 7), P([-8, -14], [8, -14], [6, 8], [-6, 8]), L(-7, -12, -22, -20, 3.5), L(7, -12, 22, -20, 3.5),
		L(-7, -6, -22, 2, 3.5), L(7, -6, 22, 2, 3.5), L(-4, 7, -10, 28, 4), L(4, 7, 10, 28, 4), L(4, 6, 16, 18, 2.5)],
	// a slime blob with the anti-gravity projector disc hovering over it
	goop: [E(0, -28, 11, 3), E(0, 10, 13, 16), E(0, -10, 8, 9), L(-10, 10, -18, 24, 5), L(10, 10, 18, 24, 5)],
	// tall, the two horns
	alien_x: [E(0, -20, 6, 7), P([-5, -24], [-14, -40], [-3, -27]), P([5, -24], [14, -40], [3, -27]), P([-12, -12], [12, -12], [8, 14], [-8, 14]),
		L(-11, -9, -16, 12, 4.5), L(11, -9, 16, 12, 4.5), L(-4, 12, -5, 33, 5), L(4, 12, 5, 33, 5)],
};
const IDS = Object.keys(SIL);

function inside(s, x, y) {
	if (s.t === 'e') {
		const c = Math.cos(-s.rot), sn = Math.sin(-s.rot), dx = x - s.cx, dy = y - s.cy;
		const u = dx * c - dy * sn, v = dx * sn + dy * c;
		return (u * u) / (s.rx * s.rx) + (v * v) / (s.ry * s.ry) <= 1;
	}
	if (s.t === 'l') {
		const vx = s.x1 - s.x0, vy = s.y1 - s.y0, t = Math.max(0, Math.min(1, ((x - s.x0) * vx + (y - s.y0) * vy) / (vx * vx + vy * vy)));
		return Math.hypot(x - s.x0 - vx * t, y - s.y0 - vy * t) <= s.w / 2;
	}
	let c = false;
	for (let i = 0, j = s.pts.length - 1; i < s.pts.length; j = i++) {
		const [xi, yi] = s.pts[i], [xj, yj] = s.pts[j];
		if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
	}
	return c;
}

const LIME = [169, 240, 39], LIME_HI = [214, 255, 140], RED = [240, 42, 39], RED_HI = [255, 150, 130];
const DISC = [26, 28, 31], RIM = [12, 13, 14], SILC = [8, 9, 10];
function sample(id, x, y, red) {
	const r = Math.hypot(x, y);
	if (r > 48) return null;
	if (r > 44.5) return RIM;
	const d = Math.abs(x) / 30 + Math.abs(y) / 42;
	for (const s of SIL[id]) if (inside(s, x, y)) return SILC;
	if (d <= 1) {
		const k = Math.max(0, -(x + y) / 60);
		const base = red ? RED : LIME, hi = red ? RED_HI : LIME_HI;
		return base.map((v, i) => v + (hi[i] - v) * k * 0.6);
	}
	if (d <= 1.12) return RIM; // the diamond's dark outline
	return DISC.map(v => v + (r < 40 && y < -10 ? 8 : 0));
}

function render(id, n, red) {
	const px = Buffer.alloc(n * n * 4), SS = 6;
	for (let py = 0; py < n; py++) for (let qx = 0; qx < n; qx++) {
		let a = 0, c = [0, 0, 0];
		for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
			const x = ((qx + (sx + 0.5) / SS) / n - 0.5) * 100, y = ((py + (sy + 0.5) / SS) / n - 0.5) * 100;
			const s = sample(id, x, y, red);
			if (s) { a++; c = c.map((v, i) => v + s[i]); }
		}
		const i = (py * n + qx) * 4;
		if (a) { px[i] = c[0] / a; px[i + 1] = c[1] / a; px[i + 2] = c[2] / a; px[i + 3] = (255 * a) / (SS * SS); }
	}
	return px;
}

fs.mkdirSync(FONT, { recursive: true });
for (const id of IDS) {
	PNG.write(path.join(GUI, `dial_${id}.png`), 64, 64, render(id, 64, false));
	PNG.write(path.join(GUI, `dial_${id}_red.png`), 64, 64, render(id, 64, true));
	fs.writeFileSync(path.join(GUI, `dial_${id}.png.mcmeta`), BLUR);
	fs.writeFileSync(path.join(GUI, `dial_${id}_red.png.mcmeta`), BLUR);
	PNG.write(path.join(FONT, `${id}.png`), 32, 32, render(id, 32, false));
}
if (process.argv[2]) {
	const S = 128, W = S * IDS.length, o = Buffer.alloc(W * S * 4);
	IDS.forEach((id, k) => {
		const im = render(id, S, false);
		for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
			const i = (y * S + x) * 4, j = (y * W + k * S + x) * 4, al = im[i + 3] / 255;
			for (let c = 0; c < 3; c++) o[j + c] = im[i + c] * al + 110 * (1 - al);
			o[j + 3] = 255;
		}
	});
	PNG.write(process.argv[2], W, S, o);
}
console.log('recalibrated dial icons written:', IDS.join(', '));
