// v0.13.22 mutation revamp, batch E: generates the overlay / effect-icon textures for powers 16, 17, 18, 22 and 27.
// Run from the repo root: node scratchpad/gen_v01322_e_textures.js
//   textures/entity/mutation/p16_web.png      -- web-lined palms and soles (Spider Adhesion grip), 64x64 skin layout
//   textures/entity/mutation/p17_rubber.png   -- translucent rubbery sheen (Elastic Form / Rubber Shield / glide canopy)
//   textures/entity/mutation/p18_shell.png    -- white hex-lattice density shell, tinted blue / orange at runtime
//   textures/entity/mutation/p22_bark.png     -- bark-and-leaf skin for Nature's Blessing, 64x64 skin layout
//   textures/mob_effect/crushing_density.png  -- 18x18 effect icon
//   textures/mob_effect/shrunken.png          -- 18x18 effect icon
const path = require('path');
const fs = require('fs');
const png = require('./pnglib.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures');
const OUT = path.join(ROOT, 'entity/mutation');
const FX = path.join(ROOT, 'mob_effect');
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(FX, { recursive: true });

function img(w, h) {
	return { w, h, data: Buffer.alloc(w * h * 4) };
}
function set(im, x, y, r, g, b, a) {
	if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const o = (y * im.w + x) * 4;
	im.data[o] = r; im.data[o + 1] = g; im.data[o + 2] = b; im.data[o + 3] = a;
}
function get(im, x, y) {
	const o = (y * im.w + x) * 4;
	return [im.data[o], im.data[o + 1], im.data[o + 2], im.data[o + 3]];
}
// deterministic noise
let seed = 1322;
function rnd() {
	seed = (seed * 1103515245 + 12345) & 0x7fffffff;
	return seed / 0x7fffffff;
}
function rect(x0, y0, x1, y1, fn) {
	for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) fn(x, y);
}

// ---------------------------------------------------------------- 16: web palms / soles
{
	const im = img(64, 64);
	const web = (x, y) => {
		const d1 = (x + y) % 4 === 0, d2 = ((x - y) % 4 + 4) % 4 === 0, ring = (y % 3 === 0);
		if (d1 || d2) set(im, x, y, 235, 235, 240, 210);
		else if (ring) set(im, x, y, 200, 200, 210, 120);
	};
	// right arm: lower third of the four sides + the palm (bottom face)
	rect(40, 24, 56, 32, web); rect(48, 16, 52, 20, web);
	// left arm
	rect(32, 56, 48, 64, web); rect(40, 48, 44, 52, web);
	// right leg: the foot + the sole
	rect(0, 28, 16, 32, web); rect(8, 16, 12, 20, web);
	// left leg
	rect(16, 60, 32, 64, web); rect(24, 48, 28, 52, web);
	png.write(path.join(OUT, 'p16_web.png'), im);
}

// ---------------------------------------------------------------- 17: rubber sheen
{
	const im = img(64, 64);
	rect(0, 0, 64, 64, (x, y) => {
		const streak = ((x * 2 + y) % 11) === 0 || ((x * 2 + y) % 11) === 1;
		const n = rnd() * 18;
		if (streak) set(im, x, y, 255, 236, 228, 170);
		else set(im, x, y, 232 - n, 160 - n, 150 - n, 105);
	});
	png.write(path.join(OUT, 'p17_rubber.png'), im);
}

// ---------------------------------------------------------------- 18: density hex lattice (white, tinted at runtime)
{
	const im = img(64, 64);
	rect(0, 0, 64, 64, (x, y) => {
		const row = Math.floor(y / 3);
		const off = (row % 2) * 2;
		const edge = y % 3 === 0 || ((x + off) % 4 === 0);
		if (edge) set(im, x, y, 255, 255, 255, 215);
		else set(im, x, y, 255, 255, 255, 70 + Math.floor(rnd() * 25));
	});
	png.write(path.join(OUT, 'p18_shell.png'), im);
}

// ---------------------------------------------------------------- 22: bark and leaves
{
	const im = img(64, 64);
	const bark = (x, y) => {
		const streak = (x * 7 + Math.floor(y / 3)) % 5 === 0;
		const n = Math.floor(rnd() * 22);
		if (rnd() < 0.18) return; // a few holes so the skin shows through
		if (streak) set(im, x, y, 62 + n, 42 + n, 24, 255);
		else set(im, x, y, 104 + n, 74 + n, 44 + Math.floor(n / 2), 255);
	};
	const leaf = (x, y, dense) => {
		if (rnd() > dense) return;
		const n = Math.floor(rnd() * 40);
		set(im, x, y, 46 + Math.floor(n / 2), 122 + n, 36 + Math.floor(n / 3), 255);
	};
	// body, arms, legs: bark with leafy patches
	const limbs = [[16, 16, 40, 32], [40, 16, 56, 32], [0, 16, 16, 32], [32, 48, 48, 64], [16, 48, 32, 64]];
	for (const [x0, y0, x1, y1] of limbs) rect(x0, y0, x1, y1, bark);
	for (const [x0, y0, x1, y1] of limbs) {
		for (let i = 0; i < 6; i++) {
			const cx = x0 + Math.floor(rnd() * (x1 - x0)), cy = y0 + Math.floor(rnd() * (y1 - y0));
			rect(Math.max(x0, cx - 2), Math.max(y0, cy - 1), Math.min(x1, cx + 2), Math.min(y1, cy + 2), (x, y) => leaf(x, y, 0.8));
		}
	}
	// head: a crown of leaves on the top face and the top row of each side -- the face itself stays clear
	rect(8, 0, 16, 8, (x, y) => leaf(x, y, 0.95));
	rect(0, 8, 32, 10, (x, y) => leaf(x, y, 0.85));
	rect(0, 10, 8, 16, (x, y) => leaf(x, y, 0.35)); // right side of the head
	rect(16, 10, 32, 16, (x, y) => leaf(x, y, 0.35)); // left side + back
	png.write(path.join(OUT, 'p22_bark.png'), im);
}

// ---------------------------------------------------------------- effect icons (18x18)
{
	const im = img(18, 18);
	// a heavy orange block pressing down on a flattened bar
	rect(4, 2, 14, 9, (x, y) => set(im, x, y, 255, 140 - (y - 2) * 6, 40, 255));
	rect(4, 2, 14, 3, (x, y) => set(im, x, y, 255, 200, 110, 255));
	rect(8, 9, 10, 12, (x, y) => set(im, x, y, 255, 120, 30, 255));
	rect(6, 11, 12, 12, (x, y) => set(im, x, y, 255, 120, 30, 255));
	rect(7, 12, 11, 13, (x, y) => set(im, x, y, 255, 120, 30, 255));
	rect(2, 14, 16, 16, (x, y) => set(im, x, y, 120, 70, 40, 255));
	png.write(path.join(FX, 'crushing_density.png'), im);
}
{
	const im = img(18, 18);
	// a big hollow purple square and a small solid one
	rect(1, 1, 11, 11, (x, y) => {
		if (x === 1 || y === 1 || x === 10 || y === 10) set(im, x, y, 170, 120, 255, 255);
	});
	rect(11, 11, 17, 17, (x, y) => set(im, x, y, 140, 90, 240, 255));
	rect(12, 12, 16, 13, (x, y) => set(im, x, y, 200, 170, 255, 255));
	// arrow from big to small
	for (let i = 0; i < 4; i++) set(im, 8 + i, 8 + i, 230, 220, 255, 255);
	png.write(path.join(FX, 'shrunken.png'), im);
}
console.log('batch E textures written');
