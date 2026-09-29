// v0.13.22 revamp batch B: generates the overlay shells (64x64 player-skin layout) for Geokinesis, Crystalkinesis,
// Pyrokinesis, Cryokinesis and Water Manipulation, plus the Ice Blade item sprite. Deterministic (seeded RNG), so
// re-running reproduces the same art. Run from the repo root: node scratchpad/gen_v01322_b_textures.js
const path = require('path');
const png = require('./pnglib.js');

const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const MUT = path.join(ROOT, 'entity', 'mutation');
const ITEM = path.join(ROOT, 'item');

let seed = 1322;
function rnd() {
	seed = (seed * 1103515245 + 12345) & 0x7fffffff;
	return seed / 0x7fffffff;
}
function reseed(s) { seed = s; }

// smooth value noise on a wrap-free grid
function makeNoise(cell) {
	const g = {};
	const at = (x, y) => { const k = x + ',' + y; if (g[k] === undefined) g[k] = rnd(); return g[k]; };
	const sm = (t) => t * t * (3 - 2 * t);
	return (x, y) => {
		const gx = Math.floor(x / cell), gy = Math.floor(y / cell);
		const fx = sm(x / cell - gx), fy = sm(y / cell - gy);
		const a = at(gx, gy), b = at(gx + 1, gy), c = at(gx, gy + 1), d = at(gx + 1, gy + 1);
		return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
	};
}

function blank(w, h) { return { w, h, data: Buffer.alloc(w * h * 4) }; }
function put(img, x, y, r, g, b, a) {
	if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
	const o = (y * img.w + x) * 4;
	img.data[o] = Math.max(0, Math.min(255, Math.round(r)));
	img.data[o + 1] = Math.max(0, Math.min(255, Math.round(g)));
	img.data[o + 2] = Math.max(0, Math.min(255, Math.round(b)));
	img.data[o + 3] = Math.max(0, Math.min(255, Math.round(a)));
}
function get(img, x, y) { const o = (y * img.w + x) * 4; return [img.data[o], img.data[o + 1], img.data[o + 2], img.data[o + 3]]; }
// the two eye pixels on the head's front face (u 8..16, v 8..16): left clear so the skin's eyes show through
const EYES = [[9, 12], [10, 12], [13, 12], [14, 12]];
function clearEyes(img) { for (const [x, y] of EYES) put(img, x, y, 0, 0, 0, 0); }

// ---- 05 stone skin: cracked grey rock (tinted per ground in the renderer) ----
function stoneSkin() {
	reseed(501);
	const img = blank(64, 64);
	const n1 = makeNoise(4), n2 = makeNoise(2);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const v = 112 + n1(x, y) * 40 + n2(x, y) * 18 - 14;
		put(img, x, y, v, v, v * 0.97, 255);
	}
	// cracks: short random walks of dark pixels
	for (let c = 0; c < 70; c++) {
		let x = Math.floor(rnd() * 64), y = Math.floor(rnd() * 64);
		const len = 3 + Math.floor(rnd() * 6);
		for (let i = 0; i < len; i++) {
			put(img, x, y, 58, 56, 54, 255);
			if (rnd() < 0.5) x += rnd() < 0.5 ? -1 : 1; else y += rnd() < 0.5 ? -1 : 1;
		}
	}
	// light pebbles
	for (let c = 0; c < 90; c++) {
		const x = Math.floor(rnd() * 64), y = Math.floor(rnd() * 64);
		const [r] = get(img, x, y);
		put(img, x, y, r + 30, r + 30, r + 28, 255);
	}
	clearEyes(img);
	return img;
}

// ---- 06 crystal armour: faceted amethyst plates ----
function crystalArmor() {
	reseed(601);
	const img = blank(64, 64);
	// Voronoi facets: each cell one flat amethyst shade with a lit upper-left rim and a dark lower-right seam
	const pts = [];
	for (let i = 0; i < 110; i++) pts.push([rnd() * 64, rnd() * 64, rnd()]);
	const shades = [[112, 66, 184], [138, 90, 212], [164, 118, 236], [192, 152, 252]];
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		let best = 1e9, second = 1e9, bi = 0;
		for (let i = 0; i < pts.length; i++) {
			const dx = pts[i][0] - x, dy = pts[i][1] - y, d = dx * dx + dy * dy;
			if (d < best) { second = best; best = d; bi = i; } else if (d < second) second = d;
		}
		const edge = Math.sqrt(second) - Math.sqrt(best);
		const base = shades[Math.floor(pts[bi][2] * shades.length)];
		if (edge < 0.9) {
			const lit = (pts[bi][0] - x) + (pts[bi][1] - y) > 0;
			if (lit) put(img, x, y, 232, 208, 255, 240); else put(img, x, y, base[0] * 0.55, base[1] * 0.5, base[2] * 0.7, 225);
			continue;
		}
		put(img, x, y, base[0], base[1], base[2], 208);
	}
	for (let c = 0; c < 40; c++) {
		const x = Math.floor(rnd() * 64), y = Math.floor(rnd() * 64);
		put(img, x, y, 250, 240, 255, 250); // glints
	}
	clearEyes(img);
	return img;
}

// ---- 08 flame body: near-white flame tongues (the renderer tints them orange, or blue when hot) ----
function flameBody() {
	reseed(801);
	const img = blank(64, 64);
	const n1 = makeNoise(3), n2 = makeNoise(6);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		// tongues rise toward smaller v within each 8-16 px face band
		const band = (y % 16) / 16;
		const tongue = 0.5 + 0.5 * Math.sin(x * 0.9 + n2(x, y) * 6);
		let v = n1(x, y) * 0.55 + band * 0.45 + tongue * 0.25 - 0.25;
		v = Math.max(0, Math.min(1, v));
		const a = v < 0.22 ? 0 : Math.min(235, (v - 0.22) * 330);
		const core = 170 + v * 85;
		put(img, x, y, core, core * 0.96, core * 0.9, a);
	}
	return img;
}

// ---- 09 frost armour: pale ice plating with white rime ----
function frostArmor() {
	reseed(901);
	const img = blank(64, 64);
	const n = makeNoise(3);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const k = n(x, y);
		put(img, x, y, 150 + k * 50, 205 + k * 35, 245, 185);
	}
	// rime: short white diagonal streaks
	for (let c = 0; c < 80; c++) {
		let x = Math.floor(rnd() * 64), y = Math.floor(rnd() * 64);
		const len = 2 + Math.floor(rnd() * 4);
		const dx = rnd() < 0.5 ? 1 : -1;
		for (let i = 0; i < len; i++) { put(img, x, y, 245, 252, 255, 235); x += dx; y += 1; }
	}
	// deep-blue plate seams every 8 px
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		if (y % 8 === 7 && rnd() < 0.8) put(img, x, y, 70, 130, 200, 210);
	}
	clearEyes(img);
	return img;
}

// ---- 09 ice shell (frozen solid): a clear block of ice with white fracture lines ----
function iceShell() {
	reseed(902);
	const img = blank(64, 64);
	const n = makeNoise(5);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const k = n(x, y);
		put(img, x, y, 190 + k * 40, 225 + k * 25, 255, 140 + k * 30);
	}
	for (let c = 0; c < 30; c++) {
		let x = Math.floor(rnd() * 64), y = Math.floor(rnd() * 64);
		const len = 4 + Math.floor(rnd() * 8);
		for (let i = 0; i < len; i++) {
			put(img, x, y, 255, 255, 255, 220);
			x += rnd() < 0.6 ? 1 : 0; y += rnd() < 0.6 ? 1 : -1;
		}
	}
	return img;
}

// ---- 25 aquatic form: a thin, shimmering film of water ----
function aquatic() {
	reseed(2501);
	const img = blank(64, 64);
	const n = makeNoise(4);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const wave = Math.sin(x * 0.55 + y * 0.35 + n(x, y) * 4);
		if (wave > 0.82) {
			put(img, x, y, 200, 235, 255, 170); // highlight ripple
		} else {
			put(img, x, y, 60, 140, 245, 60 + n(x, y) * 40);
		}
	}
	// droplets
	for (let c = 0; c < 50; c++) {
		const x = Math.floor(rnd() * 64), y = Math.floor(rnd() * 64);
		put(img, x, y, 230, 248, 255, 200);
	}
	clearEyes(img);
	return img;
}

// ---- Ice Blade item (16x16, drawn like a vanilla sword: handle bottom-left, tip top-right) ----
function iceBlade() {
	const img = blank(16, 16);
	// blade: diagonal from (5,10) to (14,1), 2 px wide with a white edge
	for (let i = 0; i <= 9; i++) {
		const x = 5 + i, y = 10 - i;
		put(img, x, y, 150, 215, 250, 255);
		put(img, x, y - 1, 225, 248, 255, 255); // upper edge
		put(img, x - 1, y, 110, 180, 235, 255); // shaded lower edge
	}
	put(img, 14, 1, 255, 255, 255, 255);
	put(img, 15, 0, 235, 250, 255, 255);
	// frosty guard
	for (const [x, y] of [[3, 10], [4, 11], [5, 12], [6, 11], [7, 12], [4, 9], [3, 12]]) put(img, x, y, 90, 150, 215, 255);
	put(img, 5, 11, 200, 240, 255, 255);
	// grip wrapped in packed ice
	for (let i = 0; i < 3; i++) put(img, 3 - i, 12 + i, 60 + i * 10, 95 + i * 10, 150, 255);
	put(img, 0, 15, 180, 225, 250, 255); // pommel
	// sparkle
	put(img, 9, 4, 255, 255, 255, 255);
	put(img, 11, 6, 240, 252, 255, 255);
	return img;
}

png.write(path.join(MUT, 'p05_stone_skin.png'), stoneSkin());
png.write(path.join(MUT, 'p06_crystal_armor.png'), crystalArmor());
png.write(path.join(MUT, 'p08_flame_body.png'), flameBody());
png.write(path.join(MUT, 'p09_frozen_armor.png'), frostArmor());
png.write(path.join(MUT, 'p09_ice_shell.png'), iceShell());
png.write(path.join(MUT, 'p25_aquatic.png'), aquatic());
png.write(path.join(ITEM, 'ice_blade.png'), iceBlade());
console.log('batch B textures written');
