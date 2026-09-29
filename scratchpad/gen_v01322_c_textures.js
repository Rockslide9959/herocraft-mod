// v0.13.22 batch C overlay textures (Electrokinesis charged shell, Energy Absorption shell, Shockwave ripple shell,
// Wind air shell, Wind tornado funnel). All original, procedurally generated. Run from the repo root:
//   node scratchpad/gen_v01322_c_textures.js
const path = require('path');
const png = require('./pnglib.js');
const OUT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures/entity/mutation');

function rng(seed) {
	return function () {
		seed |= 0; seed = seed + 0x6D2B79F5 | 0;
		let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
		t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
		return ((t ^ t >>> 14) >>> 0) / 4294967296;
	};
}

// base-layer regions of the 64x64 player skin (outer layers are hidden on mutation shells)
const REGIONS = [
	[0, 0, 32, 16],   // head
	[0, 16, 16, 32],  // right leg
	[16, 16, 40, 32], // body
	[40, 16, 56, 32], // right arm
	[16, 48, 32, 64], // left leg
	[32, 48, 48, 64], // left arm
];
const inSkin = (x, y) => REGIONS.some(([x0, y0, x1, y1]) => x >= x0 && x < x1 && y >= y0 && y < y1);

function canvas(w, h) {
	const data = Buffer.alloc(w * h * 4);
	return {
		w, h, data,
		get(x, y) { const i = (y * w + x) * 4; return [data[i], data[i + 1], data[i + 2], data[i + 3]]; },
		set(x, y, r, g, b, a) {
			if (x < 0 || y < 0 || x >= w || y >= h) return;
			const i = (y * w + x) * 4;
			if (a < data[i + 3]) return; // keep the brightest
			data[i] = r; data[i + 1] = g; data[i + 2] = b; data[i + 3] = a;
		},
	};
}

// ---- 07: crackling lightning, two frames ----
function crackle(seed) {
	const c = canvas(64, 64);
	const r = rng(seed);
	for (let n = 0; n < 70; n++) {
		const reg = REGIONS[Math.floor(r() * REGIONS.length)];
		let x = reg[0] + Math.floor(r() * (reg[2] - reg[0]));
		let y = reg[1] + Math.floor(r() * (reg[3] - reg[1]));
		const len = 5 + Math.floor(r() * 10);
		let dx = r() < 0.5 ? 1 : -1;
		let dy = r() < 0.5 ? 1 : -1;
		for (let s = 0; s < len; s++) {
			if (!inSkin(x, y)) break;
			c.set(x, y, 220, 245, 255, 255);
			for (const [ox, oy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
				if (inSkin(x + ox, y + oy)) c.set(x + ox, y + oy, 70, 160, 255, 150);
			}
			if (r() < 0.55) x += dx; else y += dy;
			if (r() < 0.2) dx = -dx;
			if (r() < 0.2) dy = -dy;
		}
	}
	return c;
}

// ---- 20: an energy lattice (white; tinted by element at render time) ----
function lattice() {
	const c = canvas(64, 64);
	const r = rng(2020);
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			if (!inSkin(x, y)) continue;
			const d1 = (x + y) % 5 === 0;
			const d2 = (x - y + 64) % 5 === 0;
			if (d1 && d2) c.set(x, y, 255, 255, 255, 235);
			else if (d1 || d2) c.set(x, y, 255, 255, 255, 120);
			else if (r() < 0.05) c.set(x, y, 255, 255, 255, 80);
		}
	}
	return c;
}

// ---- 21: kinetic ripples (pale warm distortion lines) ----
function ripple() {
	const c = canvas(64, 64);
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			if (!inSkin(x, y)) continue;
			const wave = Math.round(Math.sin(x / 2.6) * 1.4);
			const k = (y + wave + 64) % 5;
			if (k === 0) c.set(x, y, 255, 236, 205, 170);
			else if (k === 1) c.set(x, y, 255, 214, 160, 60);
		}
	}
	return c;
}

// ---- 24: air streaks for the thin shell ----
function airStreaks() {
	const c = canvas(64, 64);
	const r = rng(2424);
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			if (!inSkin(x, y)) continue;
			const k = (x * 2 + y + 128) % 11;
			if (k === 0 && r() < 0.8) c.set(x, y, 245, 252, 255, 170);
			else if (k === 1 && r() < 0.6) c.set(x, y, 225, 240, 255, 90);
			else c.set(x, y, 235, 246, 255, 18);
		}
	}
	return c;
}

// ---- 24: tornado funnel (tileable in x) ----
function funnel() {
	const c = canvas(64, 64);
	const r = rng(99);
	const noise = [];
	for (let i = 0; i < 64; i++) noise.push(r());
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const band = (x + Math.floor(y * 0.5) + 64) % 16;
			let a = band < 5 ? 150 + Math.floor(noise[(x + y) % 64] * 80) : 35 + Math.floor(noise[(x * 3 + y) % 64] * 40);
			if ((y % 8) === 0) a = Math.min(255, a + 40);
			const g = 225 + Math.floor(noise[y % 64] * 25);
			c.set(x, y, g, g + 5 > 255 ? 255 : g + 5, 255, a);
		}
	}
	return c;
}

const out = {
	'p07_charged_a.png': crackle(707),
	'p07_charged_b.png': crackle(7070),
	'p20_shell.png': lattice(),
	'p21_ripple.png': ripple(),
	'p24_air.png': airStreaks(),
	'p24_tornado.png': funnel(),
};
for (const [name, c] of Object.entries(out)) {
	png.write(path.join(OUT, name), { w: c.w, h: c.h, data: c.data });
	console.log('wrote', name);
}
