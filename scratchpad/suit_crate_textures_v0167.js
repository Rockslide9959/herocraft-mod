// v0.16.7 (user): the Suit Crate block's textures -- a heavy dark-steel Stark crate with yellow-black hazard edging,
// a red + gold Stark stripe and a small glowing arc-reactor light on the front. 16x16 each.
// Run: node scratchpad/suit_crate_textures_v0167.js   (writes into src/main/resources/assets/projecthero/textures/block/)
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'block');

function img() { return { w: 16, h: 16, px: Buffer.alloc(16 * 16 * 4) }; }
function set(im, x, y, c) {
	if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const i = (y * im.w + x) * 4;
	im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c.length > 3 ? c[3] : 255;
}
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2]]; }
function shade(c, k) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * k)))); }

// deterministic noise
let seed = 1607;
function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }

const STEEL = [52, 56, 62];
const STEEL_DARK = [34, 37, 42];
const STEEL_LIGHT = [78, 84, 92];
const YELLOW = [232, 184, 28];
const BLACK = [22, 22, 24];
const RED = [168, 22, 28];
const RED_DARK = [118, 14, 20];
const GOLD = [214, 165, 42];
const GOLD_LIGHT = [246, 210, 96];
const RIVET = [120, 126, 134];

function steel(im, x0, y0, x1, y1) {
	for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
		const n = 0.9 + rnd() * 0.16;
		set(im, x, y, shade(STEEL, n));
	}
}
// diagonal yellow/black hazard band
function hazard(im, x0, y0, x1, y1, phase) {
	for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
		const band = ((x + y + (phase || 0)) >> 1) & 1;
		set(im, x, y, band ? shade(YELLOW, 0.92 + rnd() * 0.1) : BLACK);
	}
}
function rivet(im, x, y) { set(im, x, y, RIVET); }

// ---- side: hazard lid band (rows 0-1), seam (row 2), steel body with the Stark stripe, hazard base band (14-15)
function side(withReactor) {
	const im = img();
	steel(im, 0, 0, 15, 15);
	hazard(im, 0, 0, 15, 1, 0);
	for (let x = 0; x < 16; x++) set(im, x, 2, STEEL_DARK);
	hazard(im, 0, 14, 15, 15, 1);
	for (let x = 0; x < 16; x++) set(im, x, 13, STEEL_DARK);
	// corner posts (darker, a light bevel)
	for (let y = 3; y <= 12; y++) {
		set(im, 0, y, STEEL_DARK); set(im, 15, y, STEEL_DARK);
		set(im, 1, y, STEEL_LIGHT); set(im, 14, y, shade(STEEL, 0.75));
	}
	// the Stark stripe: red (rows 7-8), gold pinstripe (row 9)
	for (let x = 2; x <= 13; x++) {
		set(im, x, 6, shade(RED, 1.1));
		set(im, x, 7, RED);
		set(im, x, 8, RED_DARK);
		set(im, x, 9, x % 3 === 0 ? GOLD_LIGHT : GOLD);
	}
	rivet(im, 2, 4); rivet(im, 13, 4); rivet(im, 2, 11); rivet(im, 13, 11);
	if (withReactor) {
		// the small arc-reactor light, centred over the stripe (x 6..9, y 6..9) with a dark bezel
		const C = [7.5, 7.5];
		for (let y = 4; y <= 11; y++) for (let x = 4; x <= 11; x++) {
			const d = Math.hypot(x - C[0], y - C[1]);
			if (d <= 0.8) set(im, x, y, [236, 255, 255]);
			else if (d <= 1.5) set(im, x, y, [150, 236, 255]);
			else if (d <= 2.1) set(im, x, y, [62, 196, 236]);
			else if (d <= 2.6) set(im, x, y, [26, 30, 36]);
			else if (d <= 3.2) set(im, x, y, d < 3.0 ? GOLD : shade(GOLD, 0.7));
		}
		// a stencil tick under it
		set(im, 7, 12, STEEL_LIGHT); set(im, 8, 12, STEEL_LIGHT);
	}
	return im;
}

// ---- top: hazard border, steel lid plate with a cross seam, rivets and a small gold Stark mark
function top() {
	const im = img();
	steel(im, 0, 0, 15, 15);
	hazard(im, 0, 0, 15, 0, 0); hazard(im, 0, 15, 15, 15, 1);
	hazard(im, 0, 0, 0, 15, 0); hazard(im, 15, 0, 15, 15, 1);
	for (let i = 1; i < 15; i++) { set(im, i, 1, STEEL_DARK); set(im, i, 14, STEEL_DARK); set(im, 1, i, STEEL_DARK); set(im, 14, i, STEEL_DARK); }
	for (let i = 2; i < 14; i++) { set(im, i, 2, STEEL_LIGHT); }
	// red + gold stripe across the lid
	for (let x = 2; x <= 13; x++) { set(im, x, 7, RED); set(im, x, 8, x % 3 === 0 ? GOLD_LIGHT : GOLD); }
	rivet(im, 3, 3); rivet(im, 12, 3); rivet(im, 3, 12); rivet(im, 12, 12);
	// a handle recess
	for (let x = 6; x <= 9; x++) { set(im, x, 11, STEEL_DARK); set(im, x, 12, shade(STEEL_DARK, 0.7)); }
	return im;
}

function bottom() {
	const im = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(im, x, y, shade(STEEL_DARK, 0.85 + rnd() * 0.15));
	for (let i = 0; i < 16; i++) { set(im, i, 0, BLACK); set(im, i, 15, BLACK); set(im, 0, i, BLACK); set(im, 15, i, BLACK); }
	return im;
}

// ---- inside (seen with the lid up): dark ribbed cavity with a cyan cradle light
function inside() {
	const im = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const rib = (x % 4 === 0) ? 0.75 : 1.0;
		set(im, x, y, shade([24, 26, 30], rib * (0.9 + rnd() * 0.15)));
	}
	for (let i = 0; i < 16; i++) { set(im, i, 0, STEEL); set(im, i, 15, STEEL); set(im, 0, i, STEEL); set(im, 15, i, STEEL); }
	for (let x = 5; x <= 10; x++) set(im, x, 8, [62, 196, 236]);
	return im;
}

const files = {
	suit_crate_front: side(true),
	suit_crate_side: side(false),
	suit_crate_top: top(),
	suit_crate_bottom: bottom(),
	suit_crate_inside: inside(),
};
for (const [name, im] of Object.entries(files)) {
	fs.writeFileSync(path.join(OUT, name + '.png'), encode(im.w, im.h, im.px));
	console.log('wrote', name);
}
