// Moon Knight Phase 7: original 16x16 textures for the Scarab of Khonshu and the Altar of Khonshu (+ spent variants).
// Run from the repo root: node scratchpad/build_khonshu_textures.js [--preview]
const path = require('path');
const png = require('./pnglib.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures');
const hex = (h) => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 255];

function fromMap(rows, pal) {
	const data = Buffer.alloc(16 * 16 * 4);
	rows.forEach((r, y) => {
		if (r.length !== 16) throw new Error('row ' + y + ' is ' + r.length + ': ' + r);
		for (let x = 0; x < 16; x++) {
			const ch = r[x];
			const c = ch === '.' ? [0, 0, 0, 0] : pal[ch];
			if (!c) throw new Error('no colour for ' + ch);
			data.set(c, (y * 16 + x) * 4);
		}
	});
	return { w: 16, h: 16, data };
}

// stable per-pixel noise
const rnd = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };

// ------------------------------------------------------------------ the scarab
const SCARAB = [
	'..o.........o...',
	'...o..ooo..o....',
	'....ooylyoo.....',
	'.....odldo......',
	'..o.oggggGo.o...',
	'...ogglllggo....',
	'..odgllhllgdo...',
	'..odgglllggdo...',
	'o..odddddddo..o.',
	'.oodgssgglgdoo..',
	'..odsSggllhgdo..',
	'o.odsSgggllgdo.o',
	'..odsSggglggdo..',
	'.oodgssggggdoo..',
	'o..oddgggggddo.o',
	'....ooddddddoo..',
];
const SCARAB_PAL = {
	o: hex('4a2a06'), d: hex('a86a10'), g: hex('e0a526'), l: hex('f8d65a'), h: hex('fff4b8'), y: hex('c98a18'),
	s: hex('eef2fa'), S: hex('9aa4b8'), G: hex('e0a526'),
};

// ------------------------------------------------------------------ the altar
const SAND = ['e3d8ab', 'dccf9f', 'd6c794', 'cfbf8a', 'c6b47e'].map(hex);
const DARK = hex('a8935c');
const DARKER = hex('8c7847');
const QUARTZ = hex('f2eee6');
const QUARTZ_SH = hex('cfc9bf');
const DULL = hex('b7b0a2');
const DULL_SH = hex('948c7e');
const CRACK = hex('5e4f2c');

function sandPixel(x, y, s) {
	const n = rnd(x, y, s);
	return SAND[n < 0.08 ? 4 : n < 0.3 ? 3 : n < 0.62 ? 2 : n < 0.88 ? 1 : 0];
}

const CRESCENT_TOP = [ // 10x10 crescent for the top face, centred
	'...XXXX...',
	'..XXS.....',
	'.XXS......',
	'XXS.......',
	'XXS.......',
	'XXS.......',
	'XXS.......',
	'.XXS......',
	'..XXS.....',
	'...XXXX...',
];
const CRESCENT_SIDE = [ // 6x6 carved crescent for the sides
	'.XXX..',
	'XXS...',
	'XS....',
	'XS....',
	'XXS...',
	'.XXX..',
];

function altarTop(spent) {
	const img = { w: 16, h: 16, data: Buffer.alloc(1024) };
	const set = (x, y, c) => img.data.set(c, (y * 16 + x) * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = sandPixel(x, y, 11);
		if (x === 0 || y === 0 || x === 15 || y === 15) c = DARK;
		else if (x === 1 || y === 1 || x === 14 || y === 14) c = SAND[0];
		else if (x === 2 || y === 2 || x === 13 || y === 13) c = DARK;
		set(x, y, c);
	}
	// four "stars" in the corners of the inner field
	for (const [x, y] of [[4, 4], [11, 4], [4, 11], [11, 11]]) set(x, y, spent ? DULL_SH : QUARTZ);
	// the crescent, shifted right a touch so its hollow sits centred
	CRESCENT_TOP.forEach((r, y) => [...r].forEach((ch, x) => {
		if (ch === 'X') set(x + 4, y + 3, spent ? DULL : QUARTZ);
		else if (ch === 'S') set(x + 4, y + 3, spent ? DULL_SH : QUARTZ_SH);
	}));
	if (spent) crack(set, [[1, 5], [3, 6], [5, 6], [6, 8], [8, 9], [9, 8], [11, 9], [12, 11], [14, 12]], [[6, 8], [6, 10], [5, 12], [5, 14]]);
	return img;
}

function altarSide(spent) {
	const img = { w: 16, h: 16, data: Buffer.alloc(1024) };
	const set = (x, y, c) => img.data.set(c, (y * 16 + x) * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = sandPixel(x, y, 23);
		if (y === 0) c = SAND[0];
		else if (y === 3 || y === 12) c = DARK;
		else if (y === 15) c = DARKER;
		else if (y <= 2 || y >= 13) c = rnd(x, y, 5) < 0.5 ? SAND[1] : SAND[2]; // trims
		else if (x === 0 || x === 15) c = DARK; // the panel's edges
		set(x, y, c);
	}
	// small notches on the trims
	for (const x of [2, 5, 10, 13]) { set(x, 1, DARK); set(x, 14, DARK); }
	// the carved crescent in the middle panel
	CRESCENT_SIDE.forEach((r, y) => [...r].forEach((ch, x) => {
		if (ch === 'X') set(x + 6, y + 5, spent ? DULL : QUARTZ);
		else if (ch === 'S') set(x + 6, y + 5, spent ? DULL_SH : QUARTZ_SH);
	}));
	if (spent) crack(set, [[0, 6], [2, 7], [4, 7], [5, 9], [7, 10], [9, 10], [10, 8], [12, 7], [15, 8]], [[5, 9], [4, 11], [4, 13]]);
	return img;
}

// jagged cracks through the given waypoints (main path, then a branch)
function crack(set, main, branch) {
	for (const pts of [main, branch]) {
		for (let i = 0; i + 1 < pts.length; i++) {
			let [x0, y0] = pts[i];
			const [x1, y1] = pts[i + 1];
			const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
			for (let s = 0; s <= steps; s++) {
				const x = Math.round(x0 + (x1 - x0) * s / steps);
				const y = Math.round(y0 + (y1 - y0) * s / steps);
				if (x >= 0 && x < 16 && y >= 0 && y < 16) set(x, y, CRACK);
			}
		}
	}
}

const out = {
	'item/scarab_of_khonshu.png': fromMap(SCARAB, SCARAB_PAL),
	'block/khonshu_altar_top.png': altarTop(false),
	'block/khonshu_altar_side.png': altarSide(false),
	'block/khonshu_altar_top_spent.png': altarTop(true),
	'block/khonshu_altar_side_spent.png': altarSide(true),
};
for (const [f, img] of Object.entries(out)) {
	png.write(path.join(ROOT, f), img);
	if (process.argv.includes('--preview')) {
		png.write(path.join(__dirname, 'preview_' + path.basename(f)), png.upscale(img, 16));
	}
}
console.log('wrote', Object.keys(out).join(', '));
