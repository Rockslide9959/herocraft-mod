// v0.17.1 Omnitrix: every texture the watch, the pod, the Containment Pod and the first two aliens need -- all procedural.
//   * entity/omnitrix/heatblast.png, four_arms.png    64x64 wide player skins (the alien framework's "basic model": a player
//     body with a detailed skin, plus a few extra cubes drawn from <alien>_extras.png)
//   * entity/omnitrix/heatblast_extras.png             the flame head's tongues (emissive)
//   * entity/omnitrix/watch.png                        the wrist watch model
//   * entity/omnitrix/pod.png                          the fallen pod (shell, clamshell halves, glowing core)
//   * entity/omnitrix/pod_field.png                    the Containment Pod's energy field
//   * block/omnitrix_pod_base|frame|panel.png          the Containment Pod block
//   * item/omnitrix|meteor_scanner|plumber_alloy|containment_core|omnitrix_containment_pod.png
//   * gui/omnitrix/alien_<id>.png                      a 32x32 silhouette per alien for the dial (all ten)
// Usage: node scratchpad/gen_omnitrix_textures.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');

const TEX = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const PREVIEW = process.argv[2];

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function shade(c, f) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * f)))); }
function mixc(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
function put(im, x, y, c, a = 255) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = a; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function rect(im, x, y, w, h, fn) { for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) { const c = fn(u, v, w, h, x + u, y + v); if (c) put(im, x + u, y + v, c.slice(0, 3), c[3] === undefined ? 255 : c[3]); } }
function save(im, rel) {
	const out = path.join(TEX, rel);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	PNG.write(out, im.w, im.h, im.px);
	if (PREVIEW) {
		const s = im.w <= 32 ? 12 : 8;
		const p = img(im.w * s, im.h * s);
		for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
			const c = get(im, Math.floor(x / s), Math.floor(y / s));
			const chk = ((Math.floor(x / 8) + Math.floor(y / 8)) % 2) ? 200 : 160;
			const a = c[3] / 255;
			put(p, x, y, [0, 1, 2].map(k => Math.round(c[k] * a + chk * (1 - a))));
		}
		fs.mkdirSync(PREVIEW, { recursive: true });
		PNG.write(path.join(PREVIEW, rel.replace(/[\\/]/g, '_')), p.w, p.h, p.px);
	}
}
// the standard box unwrap: [x, y, w, h] for each face of a w x h x d box at (ox, oy)
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
function paintBox(im, box, fn) { for (const [name, f] of Object.entries(box)) rect(im, f[0], f[1], f[2], f[3], (u, v, w, h, x, y) => fn(name, u, v, w, h, x, y)); }
// the wide player skin's boxes
const SKIN = {
	head: faces(0, 0, 8, 8, 8), hat: faces(32, 0, 8, 8, 8),
	body: faces(16, 16, 8, 12, 4), jacket: faces(16, 32, 8, 12, 4),
	rArm: faces(40, 16, 4, 12, 4), rSleeve: faces(40, 32, 4, 12, 4),
	lArm: faces(32, 48, 4, 12, 4), lSleeve: faces(48, 48, 4, 12, 4),
	rLeg: faces(0, 16, 4, 12, 4), rPants: faces(0, 32, 4, 12, 4),
	lLeg: faces(16, 48, 4, 12, 4), lPants: faces(0, 48, 4, 12, 4),
};

// ---------------------------------------------------------------- the Omnitrix symbol (an hourglass), drawn into a w x h face area
const OGREEN = ['#0e3a12', '#1f7a22', '#39c43a', '#7cff6a', '#d8ffcf'].map(hex);
const OBLACK = ['#0a0c0b', '#16191a', '#24292a', '#383e40'].map(hex);
function hourglass(im, x0, y0, s, glow = true, red = false) {
	// a disc of green with a black hourglass inside (s = diameter in texels)
	const G = red ? ['#3a0e0e', '#7a1f1f', '#c43a3a', '#ff6a6a', '#ffd8d8'].map(hex) : OGREEN;
	const r = s / 2;
	for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
		const dx = x + 0.5 - r, dy = y + 0.5 - r;
		const d = Math.sqrt(dx * dx + dy * dy);
		if (d > r) continue;
		let c = d > r - 1 ? OBLACK[2] : G[glow ? (d < r * 0.45 ? 4 : 3) : 2];
		// the Omnitrix hourglass: the green stands up the middle, two black wedges press in from the sides
		if (d < r - 1 && Math.abs(dy) < Math.abs(dx) * 0.85 + 0.2 && Math.abs(dx) > 0.6) c = OBLACK[0];
		put(im, x0 + x, y0 + y, c);
	}
}

// ================================================================ HEATBLAST
function heatblast() {
	const im = img(64, 64);
	const ROCK = ['#1a1310', '#2a1e18', '#3b2a20', '#4e382a', '#64483a'].map(hex);
	const LAVA = ['#7a1408', '#c8320e', '#f06a18', '#ffa63a', '#ffe08a', '#fff6d0'].map(hex);
	const FLAME = ['#c8320e', '#f06a18', '#ff9a2a', '#ffc84a', '#fff0a0', '#ffffff'].map(hex);
	// rock plates with glowing seams: a Voronoi-ish cell pattern in the face's own coordinates
	function rock(seed, u, v, w, h, heat = 0.35) {
		const cells = [];
		for (let k = 0; k < Math.max(3, Math.round(w * h / 14)); k++) cells.push([hash(k, 1, seed) * w, hash(k, 2, seed) * h]);
		let d1 = 99, d2 = 99;
		for (const [cx, cy] of cells) { const d = Math.hypot(u + 0.5 - cx, v + 0.5 - cy); if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d; }
		const edge = d2 - d1;
		const n = hash(u, v, seed + 7);
		if (edge < 0.4) return LAVA[n < heat ? 3 : 2];
		if (edge < 0.75 && n < 0.3) return LAVA[1];
		const tone = d1 < 1.0 ? 3 : d1 < 2.0 ? 2 : 1;
		return shade(ROCK[tone], 0.92 + n * 0.16);
	}
	// body: rock + the Omnitrix on the chest
	paintBox(im, SKIN.body, (f, u, v, w, h) => rock(11 + f.length, u, v, w, h, f === 'front' ? 0.5 : 0.3));
	hourglass(im, SKIN.body.front[0] + 1, SKIN.body.front[1] + 1, 6);
	// the dark belly band
	rect(im, SKIN.body.front[0], SKIN.body.front[1] + 9, 8, 1, (u) => shade(ROCK[0], 1 + hash(u, 9, 3) * 0.3));
	for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 3 && f !== 'top') return rock(33 + f.length, u, v, w, h, 0.7); // glowing hands
		return rock(21 + f.length + (k === 'lArm' ? 5 : 0), u, v, w, h);
	});
	for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => rock(41 + f.length + (k === 'lLeg' ? 5 : 0), u, v, w, h, 0.25));
	// head: a flame -- yellow-white at the face, orange to red at the edges, with dark eye slits
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		const n = hash(u, v, 61 + f.length);
		const up = 1 - v / h; // hotter towards the top
		let t = f === 'top' ? 0.7 + n * 0.12 : 0.35 + up * 0.5 + (n - 0.5) * 0.25;
		if (f === 'bottom') t = 0.2;
		if (f === 'front') {
			if (v === 4 && (u === 1 || u === 2 || u === 5 || u === 6)) return hex('#5a1404'); // the eyes
			if (v === 3 && (u === 2 || u === 5)) return hex('#8a2408');
			if (v === 6 && u >= 3 && u <= 4) return hex('#a83a10'); // the mouth
			t = 0.55 + up * 0.35 + (n - 0.5) * 0.15;
		}
		return FLAME[Math.max(0, Math.min(5, Math.floor(t * 5.99)))];
	});
	// the hat layer: flickering flame tongues around the head (partly see-through)
	paintBox(im, SKIN.hat, (f, u, v, w, h) => {
		if (f === 'bottom') return null;
		const n = hash(u, v, 91 + f.length);
		if (f === 'top') return n < 0.55 ? [...FLAME[3 + Math.floor(n * 3)], 200] : null;
		const tongue = Math.floor(hash(u, 0, 77 + f.length) * 5) + 1; // how far down from the top a tongue reaches
		if (v < tongue && n < 0.8) return [...FLAME[v < 2 ? 3 : 2], 210];
		return null;
	});
	// glowing shoulders on the sleeves (an ember crust)
	paintBox(im, SKIN.rSleeve, (f, u, v) => (v < 2 && hash(u, v, 5) < 0.35) ? [...LAVA[3], 220] : null);
	paintBox(im, SKIN.lSleeve, (f, u, v) => (v < 2 && hash(u, v, 6) < 0.35) ? [...LAVA[3], 220] : null);
	save(im, 'entity/omnitrix/heatblast.png');

	// the extra flame tongues above the head (a 16x16 sheet: each face of a small box is a flame)
	const ex = img(32, 32);
	rect(ex, 0, 0, 32, 32, (u, v) => {
		const col = u % 8;
		const lim = 1 + Math.floor(hash(Math.floor(u / 1), 3, 17) * 6);
		const vv = v % 16;
		if (vv < lim && hash(u, v, 4) < 0.35) return null;
		const t = 1 - vv / 16;
		return [...FLAME[Math.min(5, Math.max(0, Math.floor(t * 5 + hash(u, v, 9))))], 235 - col * 4];
	});
	save(ex, 'entity/omnitrix/heatblast_extras.png');
}

// ================================================================ FOUR ARMS
function fourArms() {
	const im = img(64, 64);
	const RED = ['#5a0e12', '#8a1a20', '#b8262e', '#d8423f', '#ec6a5a', '#f8a08a'].map(hex);
	const BLACK = ['#08080a', '#121216', '#1c1c22', '#2a2a32'].map(hex);
	const WHITE = ['#9aa0a8', '#c8ccd2', '#eceff2'].map(hex);
	const GOLD = ['#6a4a10', '#a8781a', '#e0b030'].map(hex);
	function skin(u, v, w, h, seed, f) {
		const n = hash(u, v, seed);
		let tone = 3;
		if (f === 'right' || f === 'left') tone = 2;
		if (f === 'back') tone = 2;
		if (f === 'bottom') tone = 1;
		if (f === 'top') tone = 4;
		// muscle shading: darker at the face edges
		if (u === 0 || u === w - 1) tone -= 1;
		return shade(RED[Math.max(0, tone)], 0.94 + n * 0.12);
	}
	// head: bald red, a ridge, FOUR yellow eyes (two pairs), a heavy jaw
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		if (f === 'front') {
			if ((v === 2 || v === 4) && (u === 1 || u === 2 || u === 5 || u === 6)) return hex(v === 2 ? '#ffd23a' : '#f0b820'); // the four eyes
			if ((v === 3 || v === 5) && (u === 1 || u === 6)) return RED[1];
			if (v === 1 && u >= 1 && u <= 6) return RED[1]; // the brow ridge
			if (v === 6 && u >= 2 && u <= 5) return RED[0]; // the mouth
			if (v === 7) return RED[2];
		}
		if (f === 'top' && (u === 3 || u === 4)) return RED[2]; // the crest
		return skin(u, v, w, h, 3 + f.length, f);
	});
	// body: a black vest with a white stripe down the middle, the Omnitrix on the chest, a gold belt
	paintBox(im, SKIN.body, (f, u, v, w, h) => {
		if (v >= 10) return v === 10 ? GOLD[2] : BLACK[1]; // belt + top of the trousers
		if (f === 'front') {
			if (u >= 3 && u <= 4 && v >= 1) return WHITE[1];
			if (v < 1 && (u < 2 || u > 5)) return skin(u, v, w, h, 9, f); // bare shoulders
			if (u === 0 || u === 7) return skin(u, v, w, h, 9, f);
			return shade(BLACK[2], 0.9 + hash(u, v, 2) * 0.2);
		}
		if (f === 'back') {
			if (u >= 3 && u <= 4) return WHITE[0];
			return shade(BLACK[2], 0.9 + hash(u, v, 3) * 0.2);
		}
		if (f === 'top') return skin(u, v, w, h, 9, f);
		return shade(BLACK[1], 0.9 + hash(u, v, 4) * 0.2);
	});
	hourglass(im, SKIN.body.front[0] + 1, SKIN.body.front[1] + 2, 6);
	// arms: bare red, black wrist bands
	for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 4 && v <= h - 3 && f !== 'top' && f !== 'bottom') return BLACK[2];
		if (f === 'bottom') return RED[2];
		return skin(u, v, w, h, 21 + (k === 'lArm' ? 7 : 0), f);
	});
	// legs: black trousers, dark boots
	for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 3) return shade(BLACK[0], 1 + hash(u, v, 8) * 0.6);
		return shade(BLACK[2], 0.9 + hash(u, v, k.length) * 0.25);
	});
	save(im, 'entity/omnitrix/four_arms.png');
}

// ================================================================ the wrist watch (the human form)
function watch() {
	const im = img(32, 32);
	// band: a 5x2x5 box at (0,0) -> 20x7 unwrap; dial: a 3x1x3 box at (0,8)
	paintBox(im, faces(0, 0, 5, 2, 5), (f, u, v) => (v === 0 && (f === 'front' || f === 'back' || f === 'left' || f === 'right'))
		? OGREEN[1] : shade(OBLACK[1 + (u % 2)], 1 + hash(u, v, 3) * 0.2));
	paintBox(im, faces(0, 8, 3, 1, 3), (f, u, v) => f === 'top' ? null : OBLACK[3]);
	hourglass(im, 3, 8, 3); // the face: the dial box's top
	// a larger face (16x16 at (16,16)) the renderer can use as a glowing overlay disc
	hourglass(im, 16, 16, 16);
	// a plain glowing disc (the face while picking: the silhouette sits on it) and four solid swatches for the renderer
	rect(im, 0, 16, 16, 12, () => null);
	for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) { const d = Math.hypot(x + 0.5 - 6, y + 0.5 - 6); if (d <= 6) put(im, x, 16 + y, d > 5.2 ? OBLACK[2] : OGREEN[d < 3 ? 4 : 3]); }
	rect(im, 0, 28, 4, 4, (u, v) => shade(OBLACK[0], 1 + hash(u, v, 1) * 0.4));
	rect(im, 4, 28, 4, 4, (u, v) => v === 1 ? OGREEN[1] : shade(OBLACK[2], 1 + hash(u, v, 2) * 0.3));
	rect(im, 8, 28, 4, 4, (u, v) => OGREEN[2]);
	rect(im, 12, 28, 4, 4, (u, v) => OGREEN[4]);
	save(im, 'entity/omnitrix/watch.png');
	// the same with a red (timing out) light
	const red = img(32, 32);
	im.px.copy(red.px);
	hourglass(red, 3, 8, 3, true, true);
	hourglass(red, 16, 16, 16, true, true);
	for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) { const d = Math.hypot(x + 0.5 - 6, y + 0.5 - 6); if (d <= 5.2) put(red, x, 16 + y, d < 3 ? hex('#ffd8d8') : hex('#ff5a4a')); }
	save(red, 'entity/omnitrix/watch_red.png');
}

// ================================================================ the fallen pod (entity) + the Containment Pod (block)
function metal(u, v, seed, base = ['#2a3034', '#3a4248', '#4e585e', '#68737a'].map(hex)) {
	const n = hash(u, v, seed);
	let c = base[1 + (n < 0.2 ? -1 : n > 0.85 ? 1 : 0)];
	return shade(c, 0.94 + hash(u, v, seed + 1) * 0.12);
}
function pod() {
	const im = img(64, 64);
	// everything metal with seams; a green glowing band at v 30..33; the core at (40,40) 16x16
	rect(im, 0, 0, 64, 64, (u, v) => {
		if (v % 8 === 0 || u % 16 === 0) return shade(hex('#1a1e22'), 1);
		if (v >= 30 && v <= 32) return OGREEN[v === 31 ? 3 : 2];
		return metal(u, v, 13);
	});
	rect(im, 40, 40, 24, 24, (u, v) => {
		const d = Math.hypot(u - 11.5, v - 11.5);
		return d < 6 ? OGREEN[4] : d < 9 ? OGREEN[3] : OGREEN[2];
	});
	hourglass(im, 46, 46, 12);
	save(im, 'entity/omnitrix/pod.png');

	const field = img(16, 16);
	rect(field, 0, 0, 16, 16, (u, v) => {
		const line = (v + Math.floor(hash(u, 0, 2) * 3)) % 5 === 0;
		return [...(line ? OGREEN[4] : OGREEN[3]), line ? 170 : 90];
	});
	save(field, 'entity/omnitrix/pod_field.png');
	// a soft white glow (tinted per use: alien projectiles, the meteor's halo)
	const glow = img(16, 16);
	rect(glow, 0, 0, 16, 16, (u, v) => { const d = Math.hypot(u + 0.5 - 8, v + 0.5 - 8) / 8; return d >= 1 ? null : [255, 255, 255, Math.round(255 * Math.pow(1 - d, 1.6))]; });
	save(glow, 'entity/omnitrix/glow.png');

	const base = img(16, 16);
	rect(base, 0, 0, 16, 16, (u, v) => {
		if (u === 0 || v === 0 || u === 15 || v === 15) return hex('#1a1e22');
		const d = Math.hypot(u - 7.5, v - 7.5);
		if (d < 6.5 && d > 5.4) return OGREEN[3];
		if (d < 5.4 && Math.abs(u - 7.5) < Math.abs(v - 7.5) * 0.85) return OBLACK[1];
		if (d < 5.4) return OGREEN[2];
		return metal(u, v, 31);
	});
	save(base, 'block/omnitrix_pod_base.png');
	const frame = img(16, 16);
	rect(frame, 0, 0, 16, 16, (u, v) => (v % 4 === 0) ? OGREEN[2] : metal(u, v, 41, ['#20262a', '#2e363c', '#3e484e', '#56626a'].map(hex)));
	save(frame, 'block/omnitrix_pod_frame.png');
	const panel = img(16, 16);
	rect(panel, 0, 0, 16, 16, (u, v) => {
		if (u === 0 || v === 0 || u === 15 || v === 15) return OBLACK[2];
		if (v > 9) return (u % 3 === 1) ? hex('#ff3a2a') : OBLACK[1];
		return OGREEN[(u + v) % 3 === 0 ? 3 : 2];
	});
	save(panel, 'block/omnitrix_pod_panel.png');
}

// ================================================================ items (16x16)
function items() {
	const omni = img(16, 16);
	// a watch seen from above: the band top to bottom, the dial in the middle
	rect(omni, 5, 0, 6, 16, (u, v) => (u === 0 || u === 5) ? OBLACK[0] : shade(OBLACK[2], 1 + ((v % 3 === 0) ? 0.3 : 0)));
	rect(omni, 3, 3, 10, 10, (u, v) => {
		const d = Math.hypot(u - 4.5, v - 4.5);
		return d <= 5 ? (d > 4 ? OBLACK[3] : null) : null;
	});
	hourglass(omni, 4, 4, 8);
	rect(omni, 2, 6, 1, 4, () => OBLACK[3]);
	rect(omni, 13, 6, 1, 4, () => OBLACK[3]);
	save(omni, 'item/omnitrix.png');

	const scan = img(16, 16);
	rect(scan, 3, 1, 10, 14, (u, v) => (u === 0 || v === 0 || u === 9 || v === 13) ? hex('#2a2e32') : hex('#5a646c'));
	rect(scan, 4, 2, 8, 7, (u, v) => {
		const d = Math.hypot(u - 3.5, v - 3.5);
		if (d < 0.8) return hex('#ffffff');
		if (Math.abs(d - 2.2) < 0.5 || Math.abs(d - 3.6) < 0.45) return OGREEN[3];
		return hex('#0a1a0c');
	});
	rect(scan, 5, 4, 1, 1, () => hex('#ff4a2a'));
	rect(scan, 4, 10, 8, 4, (u, v) => (v === 1 && u % 2 === 0) ? OGREEN[3] : (u + v) % 3 === 0 ? hex('#ff3a2a') : hex('#3a4248'));
	rect(scan, 11, 0, 1, 2, () => hex('#9aa4ac'));
	save(scan, 'item/meteor_scanner.png');

	const alloy = img(16, 16);
	// an ingot: dark gunmetal with a green sheen
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const inIngot = y >= 5 && y <= 11 && x >= 1 + (11 - y) * 0 && x >= 2 + Math.max(0, 6 - y) && x <= 13 - Math.max(0, 6 - y);
		if (!inIngot) continue;
		let c = hex('#3a4540');
		if (y === 5 || y === 6) c = hex('#6a8a74');
		if (y === 11) c = hex('#1c2420');
		if ((x + y) % 7 === 0) c = OGREEN[2];
		put(alloy, x, y, c);
	}
	save(alloy, 'item/plumber_alloy.png');

	const core = img(16, 16);
	rect(core, 2, 2, 12, 12, (u, v) => {
		const d = Math.hypot(u - 5.5, v - 5.5);
		if (d > 6) return null;
		if (d > 5) return hex('#2a3034');
		if (d > 3.6) return hex('#5a646c');
		return d < 1.6 ? OGREEN[4] : OGREEN[3];
	});
	rect(core, 7, 0, 2, 3, () => hex('#4e585e'));
	rect(core, 7, 13, 2, 3, () => hex('#4e585e'));
	save(core, 'item/containment_core.png');

	const podi = img(16, 16);
	rect(podi, 3, 14, 10, 2, () => hex('#2a3034'));
	rect(podi, 3, 0, 10, 2, () => hex('#2a3034'));
	rect(podi, 3, 2, 2, 12, () => hex('#3e484e'));
	rect(podi, 11, 2, 2, 12, () => hex('#3e484e'));
	rect(podi, 5, 2, 6, 12, (u, v) => [...OGREEN[(v % 3 === 0) ? 3 : 2], 200]);
	hourglass(podi, 6, 6, 4);
	save(podi, 'item/omnitrix_containment_pod.png');
}

// ================================================================ dial silhouettes (32x32, white on transparent)
function silhouettes() {
	const W = 32;
	function sil(id, draw) {
		const im = img(W, W);
		const set = (x, y) => put(im, Math.round(x), Math.round(y), [255, 255, 255]);
		const disc = (cx, cy, r) => { for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r + r * 0.6) set(cx + x, cy + y); };
		const box = (x, y, w, h) => { for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) set(x + u, y + v); };
		const line = (x0, y0, x1, y1, t = 1) => { const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2); for (let i = 0; i <= n; i++) { const x = x0 + (x1 - x0) * i / n, y = y0 + (y1 - y0) * i / n; for (let a = -t; a <= t; a++) for (let b = -t; b <= t; b++) if (a * a + b * b <= t * t) set(x + a, y + b); } };
		const poly = (pts) => { let minY = 99, maxY = -1; for (const p of pts) { minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); } for (let y = Math.floor(minY); y <= Math.ceil(maxY); y++) { const xs = []; for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y)) xs.push(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1])); } xs.sort((p, q) => p - q); for (let k = 0; k + 1 < xs.length; k += 2) for (let x = Math.ceil(xs[k]); x <= Math.floor(xs[k + 1]); x++) set(x, y); } };
		draw({ set, disc, box, line, poly });
		save(im, 'gui/omnitrix/alien_' + id + '.png');
	}
	const body = (g, cx, top, w, h) => { g.box(cx - w / 2, top, w, h); };
	sil('heatblast', g => {
		g.poly([[16, 1], [19, 5], [21, 3], [21, 8], [19, 11], [13, 11], [11, 8], [11, 4], [13, 6]]); // the flame head
		body(g, 16, 12, 8, 9); g.line(12, 13, 7, 20, 1.2); g.line(20, 13, 25, 20, 1.2); g.box(12, 21, 3, 9); g.box(17, 21, 3, 9);
	});
	sil('four_arms', g => {
		g.disc(16, 6, 4); body(g, 16, 10, 14, 10);
		g.line(9, 11, 3, 18, 1.6); g.line(23, 11, 29, 18, 1.6); g.line(9, 15, 4, 23, 1.6); g.line(23, 15, 28, 23, 1.6);
		g.box(10, 20, 5, 10); g.box(17, 20, 5, 10);
	});
	sil('xlr8', g => {
		g.poly([[12, 4], [20, 3], [23, 7], [19, 10], [13, 10]]); g.poly([[13, 10], [20, 10], [21, 19], [12, 19]]);
		g.line(13, 12, 7, 17, 1); g.line(20, 12, 25, 16, 1); g.line(14, 19, 10, 30, 1.3); g.line(19, 19, 22, 30, 1.3);
		g.line(13, 18, 4, 26, 1.4); g.line(4, 26, 2, 30, 1); // the tail
	});
	sil('diamondhead', g => {
		g.poly([[16, 0], [21, 6], [19, 11], [13, 11], [11, 6]]); g.poly([[11, 12], [21, 12], [19, 21], [13, 21]]);
		g.poly([[11, 12], [7, 13], [5, 20], [8, 21]]); g.poly([[21, 12], [25, 13], [27, 20], [24, 21]]);
		g.poly([[21, 12], [26, 6], [24, 12]]); g.box(13, 21, 3, 9); g.box(17, 21, 3, 9);
	});
	sil('upgrade', g => {
		g.disc(16, 7, 5); g.poly([[13, 12], [19, 12], [20, 22], [12, 22]]); g.line(13, 13, 6, 22, 1); g.line(19, 13, 26, 22, 1);
		g.line(14, 22, 12, 30, 1.2); g.line(18, 22, 20, 30, 1.2);
	});
	sil('ghostfreak', g => {
		g.disc(16, 7, 5); g.poly([[11, 11], [21, 11], [23, 22], [20, 26], [18, 22], [16, 30], [14, 22], [12, 26], [9, 22]]);
		g.line(11, 13, 4, 18, 1); g.line(21, 13, 28, 18, 1);
	});
	sil('stinkfly', g => {
		g.disc(16, 13, 4); g.poly([[13, 16], [19, 16], [18, 28], [16, 31], [14, 28]]);
		g.poly([[12, 13], [2, 7], [1, 13], [11, 17]]); g.poly([[20, 13], [30, 7], [31, 13], [21, 17]]);
		g.line(14, 10, 11, 3, 0.8); g.line(18, 10, 21, 3, 0.8); g.disc(11, 3, 2); g.disc(21, 3, 2);
	});
	sil('wildvine', g => {
		g.disc(16, 6, 4); g.poly([[13, 10], [19, 10], [18, 22], [14, 22]]);
		g.line(13, 12, 5, 16, 1); g.line(5, 16, 3, 24, 1); g.line(19, 12, 27, 14, 1); g.line(27, 14, 29, 22, 1);
		g.line(15, 22, 11, 31, 1.2); g.line(17, 22, 21, 31, 1.2); g.line(16, 2, 13, 0, 0.8); g.line(16, 2, 19, 0, 0.8);
	});
	sil('ripjaws', g => {
		g.poly([[4, 10], [14, 6], [22, 9], [26, 14], [22, 18], [12, 20], [4, 16]]); g.poly([[26, 14], [31, 8], [31, 20]]);
		g.poly([[4, 16], [12, 20], [8, 23]]); g.line(14, 20, 12, 27, 1); g.line(18, 19, 19, 26, 1);
	});
	sil('grey_matter', g => {
		g.disc(16, 17, 7); g.disc(12, 11, 3); g.disc(20, 11, 3); g.box(13, 23, 6, 4); g.line(14, 27, 11, 31, 1); g.line(18, 27, 21, 31, 1);
		g.line(11, 20, 6, 25, 1); g.line(21, 20, 26, 25, 1);
	});
}

heatblast();
fourArms();
watch();
pod();
items();
silhouettes();
console.log('omnitrix textures written');
