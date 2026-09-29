// v0.13.22 revamp batch D: generates the overlay / entity / item textures for Telekinesis, Light, Shadow, Gravity and
// Magnetism. Run from the repo root: node scratchpad/build_batch_d_textures.js
const path = require('path');
const png = require('./pnglib.js');
const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures');

function canvas(w, h) {
	return { w, h, data: Buffer.alloc(w * h * 4) };
}
function set(c, x, y, r, g, b, a) {
	if (x < 0 || y < 0 || x >= c.w || y >= c.h) return;
	const i = (y * c.w + x) * 4;
	c.data[i] = r; c.data[i + 1] = g; c.data[i + 2] = b; c.data[i + 3] = a;
}
function rect(c, x0, y0, w, h, fn) {
	for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) fn(x, y);
}
// deterministic noise
let seed = 1337;
function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }

// Base-layer UV boxes of the 64x64 player skin (head, body, arms, legs) -- the shells only use these.
const BASE = [
	[0, 0, 32, 16],   // head
	[16, 16, 24, 16], // body
	[40, 16, 16, 16], // right arm
	[0, 16, 16, 16],  // right leg
	[32, 48, 16, 16], // left arm
	[16, 48, 16, 16], // left leg
];
function eachBase(fn) { for (const [x, y, w, h] of BASE) rect(null, x, y, w, h, fn); }

// ---- 19: Shadow Form shell -- near-black living shadow with faint violet veins ----
{
	const c = canvas(64, 64);
	seed = 19;
	eachBase((x, y) => {
		const n = rnd();
		const vein = (Math.sin(x * 0.9 + y * 0.35) + Math.sin(y * 0.7 - x * 0.2)) > 1.55;
		if (vein) set(c, x, y, 58, 22, 92, 240);
		else set(c, x, y, 6 + Math.floor(n * 8), 4 + Math.floor(n * 5), 12 + Math.floor(n * 10), 238);
	});
	png.write(path.join(ROOT, 'entity/mutation/p19_shadow_form.png'), c);
}

// ---- 23: Gravity Field shell -- translucent violet with rippling bands ----
{
	const c = canvas(64, 64);
	eachBase((x, y) => {
		const band = 0.5 + 0.5 * Math.sin(y * 0.8 + Math.sin(x * 0.5) * 1.3);
		const a = Math.floor(45 + band * 120);
		set(c, x, y, 120 + Math.floor(band * 40), 60, 210, a);
	});
	png.write(path.join(ROOT, 'entity/mutation/p23_gravity_field.png'), c);
}

// ---- 26: Magnetic field lines -- mostly clear, steel-blue vertical lines with a few arcs ----
{
	const c = canvas(64, 64);
	eachBase((x, y) => {
		const line = (x % 4 === 1) || ((x + y) % 9 === 0 && y % 3 !== 0);
		if (line) set(c, x, y, 160, 200, 255, 200);
	});
	png.write(path.join(ROOT, 'entity/mutation/p26_field.png'), c);
}

// ---- 19: Shadow Servant skin (64x64 player layout) + its emissive eye layer ----
{
	const c = canvas(64, 64);
	const eyes = canvas(64, 64);
	seed = 91;
	eachBase((x, y) => {
		const n = rnd();
		const vein = (Math.sin(x * 1.1 - y * 0.4) + Math.cos(y * 0.9)) > 1.6;
		if (vein) set(c, x, y, 70, 30, 110, 255);
		else set(c, x, y, 10 + Math.floor(n * 10), 8 + Math.floor(n * 6), 18 + Math.floor(n * 12), 255);
	});
	// eyes on the face (head front = x 8..15, y 8..15): two 2x1 slits
	for (const [x, y] of [[9, 12], [10, 12], [13, 12], [14, 12]]) {
		set(c, x, y, 200, 120, 255, 255);
		set(eyes, x, y, 210, 130, 255, 255);
	}
	png.write(path.join(ROOT, 'entity/mutation/p19_shadow_servant.png'), c);
	png.write(path.join(ROOT, 'entity/mutation/p19_shadow_servant_eyes.png'), eyes);
}

// ---- 15: Hard-Light Blade item (16x16, diagonal like vanilla swords: tip top-right, pommel bottom-left) ----
{
	const c = canvas(16, 16);
	// blade: from (4,11) to (14,1)
	for (let i = 0; i <= 10; i++) {
		const x = 4 + i, y = 11 - i;
		set(c, x, y, 255, 255, 240, 255);          // bright core
		set(c, x + 1, y, 255, 226, 120, 255);      // gold edge
		set(c, x, y - 1, 255, 236, 150, 230);      // gold edge
		if (i > 1 && i < 10) set(c, x + 1, y - 1, 255, 250, 200, 140); // shimmer
	}
	set(c, 14, 1, 255, 255, 255, 255);
	set(c, 15, 0, 255, 245, 190, 200);
	// crossguard
	for (const [x, y] of [[2, 10], [3, 11], [5, 13], [6, 14], [3, 12], [4, 13], [2, 11], [5, 14]]) set(c, x, y, 255, 205, 70, 255);
	// grip + pommel
	for (const [x, y] of [[3, 12], [2, 13], [1, 14]]) set(c, x, y, 250, 190, 90, 255);
	set(c, 0, 15, 255, 255, 220, 255);
	set(c, 1, 15, 255, 220, 120, 200);
	set(c, 0, 14, 255, 220, 120, 200);
	png.write(path.join(ROOT, 'item/hard_light_blade.png'), c);
}

console.log('batch D textures written');
