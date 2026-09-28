// v0.13.12 (Hulk Phase 4): the Gamma Reactor block texture and the Gamma Serum item icon, 16x16.
// Run from the repo root:  node scratchpad/gen_gamma.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit');

const A = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/');
let seed = 4242;
const rand = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
function img() { return Buffer.alloc(16 * 16 * 4); }
function put(px, x, y, r, g, b, a = 255) {
	const i = (y * 16 + x) * 4;
	px[i] = Math.max(0, Math.min(255, r)); px[i + 1] = Math.max(0, Math.min(255, g)); px[i + 2] = Math.max(0, Math.min(255, b)); px[i + 3] = a;
}

// ---- Gamma Reactor: a riveted dark-iron frame round a glowing green core with a hot white centre ----
{
	const px = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const edge = Math.min(x, y, 15 - x, 15 - y);
		const n = rand() * 18;
		if (edge <= 1) {
			const rivet = edge === 1 && (x === 1 || x === 14) && (y === 1 || y === 14);
			const base = rivet ? 120 : 52 + n;
			put(px, x, y, base, base + 6, base);
		} else if (edge === 2) {
			put(px, x, y, 30 + n * 0.5, 44 + n * 0.5, 30 + n * 0.5); // the core's dark seal
		} else {
			const d = Math.hypot(x - 7.5, y - 7.5);
			const t = Math.max(0, 1 - d / 5.5);
			const vein = ((x * 3 + y * 5) % 7 === 0) ? 25 : 0;
			put(px, x, y, 60 + t * 190 + vein, 170 + t * 85 + vein, 40 + t * 160, 255);
		}
	}
	fs.writeFileSync(A + 'block/gamma_reactor.png', encode(16, 16, px));
}

// ---- Gamma Serum: a stoppered vial of glowing green liquid ----
{
	const px = img();
	const glass = [200, 230, 220];
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		// cork (y 1-2), neck (y 3-5, x 6-9), body (y 6-14, x 4-11, rounded corners)
		if (y >= 1 && y <= 2 && x >= 6 && x <= 9) { put(px, x, y, 130 + (x + y) % 2 * 20, 92, 58); continue; }
		const neck = y >= 3 && y <= 5 && x >= 6 && x <= 9;
		const body = y >= 6 && y <= 14 && x >= 4 && x <= 11 && !((y === 6 || y === 14) && (x === 4 || x === 11));
		if (!neck && !body) continue;
		const outline = neck ? (x === 6 || x === 9) : (x === 4 || x === 11 || y === 14 || (y === 6 && (x === 5 || x === 10)));
		if (outline) { put(px, x, y, 40, 70, 50); continue; }
		if (y >= 8) {
			const t = (y - 8) / 6;
			const glint = x === 6 && y <= 11 ? 70 : 0;
			put(px, x, y, 70 + glint - t * 40, 240 - t * 60 + glint * 0.2, 60 + glint - t * 30);
		} else {
			put(px, x, y, glass[0], glass[1], glass[2], 150);
		}
	}
	fs.writeFileSync(A + 'item/gamma_serum.png', encode(16, 16, px));
}
console.log('wrote gamma_reactor.png and gamma_serum.png');
