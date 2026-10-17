// v0.16.6 Ladybug textures: the Yo-yo (face / band / compact screen), the spotted canopy cloth, the red light burst,
// the tiny swarm ladybug, and the five Lucky Charm item icons (vanilla shapes recoloured red with black spots, the
// parachute hand-drawn).
// node gen_ladybug_v0166.js <vanilla texture dir> <assets/projecthero dir>
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng, decodePng } = require('./lib_png');

const [vdir, adir] = process.argv.slice(2);
const ent = path.join(adir, 'textures/entity/miraculous/ladybug');
const itm = path.join(adir, 'textures/item');
fs.mkdirSync(ent, { recursive: true });
fs.mkdirSync(itm, { recursive: true });

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function set(im, x, y, r, g, b, a = 255) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const o = (y * im.w + x) * 4; im.px[o] = r; im.px[o + 1] = g; im.px[o + 2] = b; im.px[o + 3] = a; }
function get(im, x, y) { const o = (y * im.w + x) * 4; return [im.px[o], im.px[o + 1], im.px[o + 2], im.px[o + 3]]; }
function save(im, file) { fs.writeFileSync(file, encodePng(im.w, im.h, im.px)); console.log('wrote', file); }
const RED = [226, 28, 36], RED_D = [150, 14, 22], RED_L = [255, 92, 96], BLACK = [18, 14, 16];

// ---------------------------------------------------------------- the Yo-yo: 48x16 (face | band | screen)
{
	const im = img(48, 16);
	const spots = [[8, 8, 2.1], [4.6, 5.2, 1.4], [11.4, 5.2, 1.4], [4.6, 10.8, 1.4], [11.4, 10.8, 1.4]];
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy);
		if (d > 8) continue;
		let c = RED;
		const shade = 1 - 0.18 * ((dx + dy) / 11);
		c = c.map(v => Math.max(0, Math.min(255, Math.round(v * shade))));
		if (d > 7) c = BLACK; // the rim
		for (const [sx, sy, r] of spots) if (Math.hypot(x + 0.5 - sx, y + 0.5 - sy) < r) c = BLACK;
		if (d < 7 && dx < -2 && dy < -2 && d > 4.5 && d < 5.6) c = RED_L; // a highlight arc
		set(im, x, y, ...c);
	}
	for (let y = 0; y < 16; y++) for (let x = 16; x < 32; x++) {
		let c = y < 2 || y > 13 ? RED_D : RED;
		if (y === 7 || y === 8) c = BLACK; // the seam between the halves
		set(im, x, y, ...c);
	}
	for (let y = 0; y < 16; y++) for (let x = 32; x < 48; x++) {
		const dx = x - 32 + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy);
		if (d > 8) continue;
		let c = [255, 214, 226];
		if (d > 6.8) c = [40, 30, 36];
		// a little ladybug glyph on the screen + a grid of "app" dots
		if (Math.hypot(dx, dy + 1) < 2.6) c = RED;
		if (Math.abs(dx) < 0.6 && dy > -3.5 && dy < 1.6) c = BLACK;
		if (dy > 3 && dy < 4.5 && Math.abs(dx) < 4 && ((x | 0) % 2 === 0)) c = [226, 60, 90];
		set(im, x, y, ...c);
	}
	save(im, path.join(ent, 'yoyo.png'));
}

// ---------------------------------------------------------------- spotted cloth (canopy), tileable 32x32
{
	const im = img(32, 32);
	const pts = [[5, 6], [17, 3], [27, 10], [10, 17], [22, 21], [3, 27], [15, 29], [29, 28]];
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		let c = RED;
		for (const [sx, sy] of pts) {
			let dx = Math.abs(x + 0.5 - sx), dy = Math.abs(y + 0.5 - sy);
			dx = Math.min(dx, 32 - dx); dy = Math.min(dy, 32 - dy);
			if (Math.hypot(dx, dy) < 2.6) c = BLACK;
		}
		set(im, x, y, ...c);
	}
	save(im, path.join(ent, 'spots.png'));
}

// ---------------------------------------------------------------- the red light burst (radial), 64x64
{
	const im = img(64, 64);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
		if (d >= 1) continue;
		const ang = Math.atan2(y - 32, x - 32);
		const ray = 0.5 + 0.5 * Math.cos(ang * 8);
		const a = Math.pow(1 - d, 1.6) * (0.75 + 0.25 * ray);
		const w = Math.max(0, 1 - d * 2.2);
		set(im, x, y, 255, Math.round(40 + 215 * w), Math.round(50 + 205 * w), Math.round(255 * Math.min(1, a)));
	}
	save(im, path.join(ent, 'burst.png'));
}

// ---------------------------------------------------------------- the swarm ladybug, 16x16: body top (0..8) | wing (8..16)
{
	const im = img(16, 8);
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		let c = RED;
		if (y < 2) c = BLACK; // head end
		if (x === 3 || x === 4) c = y >= 2 ? [60, 10, 14] : BLACK; // wing split
		if ((x === 1 && y === 4) || (x === 6 && y === 4) || (x === 2 && y === 6) || (x === 5 && y === 6)) c = BLACK;
		set(im, x, y, ...c);
	}
	for (let y = 0; y < 8; y++) for (let x = 8; x < 16; x++) {
		const dx = x - 8 + 0.5 - 4, dy = y + 0.5 - 4;
		if (Math.hypot(dx * 0.8, dy) < 3.8) set(im, x, y, 235, 235, 255, 120);
	}
	save(im, path.join(ent, 'swarm_bug.png'));
}

// ---------------------------------------------------------------- charm icons
function lum(c) { return (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255; }
function recolour(src, spots, keep) {
	const s = decodePng(fs.readFileSync(path.join(vdir, src)));
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const o = (y * 16 + x) * 4; const c = [s.px[o], s.px[o + 1], s.px[o + 2], s.px[o + 3]];
		if (c[3] === 0) continue;
		if (keep && keep(c, x, y)) { set(im, x, y, ...c); continue; }
		const l = lum(c);
		let r;
		if (l < 0.16) r = [Math.round(20 + 40 * l), 10, 12];
		else r = [Math.min(255, Math.round(120 + 160 * l)), Math.round(10 + 70 * Math.max(0, l - 0.6)), Math.round(16 + 70 * Math.max(0, l - 0.6))];
		set(im, x, y, r[0], r[1], r[2], c[3]);
	}
	for (const [x, y] of spots) { const c = get(im, x, y); if (c[3] > 0) set(im, x, y, ...BLACK); }
	return im;
}
// apple: the golden apple's flesh red, spotted; the leaf/stem kept
save(recolour('golden_apple.png', [[5, 8], [9, 7], [7, 11], [10, 11], [5, 12]], c => c[1] > c[0] + 10), path.join(itm, 'ladybug_lucky_apple.png'));
// sword: blade red with black spots, the handle kept dark
save(recolour('diamond_sword.png', [[9, 5], [7, 7], [11, 3]], (c, x, y) => y > 10 && x < 6), path.join(itm, 'ladybug_lucky_sword.png'));
// bow: the limbs red with spots, the string kept
for (const n of ['bow', 'bow_pulling_0', 'bow_pulling_1', 'bow_pulling_2']) {
	save(recolour(n + '.png', [[3, 2], [8, 5], [12, 10]], c => c[0] > 180 && c[1] > 180 && c[2] > 180),
		path.join(itm, 'ladybug_lucky_' + n + '.png'));
}
// tnt: the block's side as an item, red with black spots, the white label kept and lettered black
{
	const im = recolour('tnt_side.png', [[2, 2], [12, 3], [4, 13], [13, 12], [8, 14]], c => c[0] > 200 && c[1] > 200 && c[2] > 200);
	save(im, path.join(itm, 'ladybug_lucky_tnt.png'));
}
// parachute: a spotted canopy over cords and a harness pack
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x + 0.5 - 8, dy = y + 0.5 - 7.5;
		if (dy < 0 && Math.hypot(dx / 7.4, dy / 6.2) < 1 && y >= 1) {
			let c = Math.hypot(dx / 7.4, dy / 6.2) > 0.82 ? RED_D : RED;
			if ([[5, 4], [10, 3], [8, 6], [3, 6], [12, 6]].some(([a, b]) => a === x && b === y)) c = BLACK;
			set(im, x, y, ...c);
		}
	}
	for (const [x0, y0] of [[1, 7], [5, 7], [10, 7], [14, 7]]) {
		for (let t = 0; t <= 1.0001; t += 0.12) { const x = Math.round(x0 + (8 - x0) * t), y = Math.round(y0 + (12 - y0) * t); set(im, x, y, 210, 200, 200); }
	}
	for (let y = 12; y < 16; y++) for (let x = 6; x < 11; x++) set(im, x, y, ...(y === 12 || x === 6 || x === 10 ? BLACK : RED_D));
	save(im, path.join(itm, 'ladybug_lucky_parachute.png'));
}
