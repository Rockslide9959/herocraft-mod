// v0.17.2: the Recalibrated Omnitrix's textures (user's reference: "a chunky grey bezel round a green-and-black hourglass face, two
// side buttons, a green band with black stripes"), remapped from the prototype's so the UV layout is identical:
//   * entity/omnitrix/watch_recal.png, watch_recal_red.png   the swatches recoloured -- the cuff a green band, the prongs black
//     stripes over it, the plate / grey / ring a lighter chunky grey; the face quadrant's grey side wedges turned black (the
//     green-and-black hourglass)
//   * entity/omnitrix/face_morph_recal.png, face_morph_recal_red.png   every morph frame with the same grey -> black remap
//   * item/recalibrated_omnitrix.png, item/recalibration_chip.png   16x16 icons
// Usage: node scratchpad/recal_v0172/gen_recal_watch.js
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');
const TEX = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const E = path.join(TEX, 'entity', 'omnitrix');
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
const shade = (c, f) => c.map(v => Math.max(0, Math.min(255, Math.round(v * f))));
const mixc = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

function load(name) {
	const im = PNG.read(path.join(E, name));
	return { w: im.W || im.width || im.w, h: im.H || im.height || im.h, px: Buffer.from(im.px || im.data) };
}
function put(im, x, y, c, a = 255) { const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = a; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }

/** A grey pixel of the face (the side wedges / the dial disc) -- low saturation, not the near-black rim and dividers. */
function greyToBlack(im, x0, y0, w, h) {
	for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
		const [r, g, b, a] = get(im, x, y);
		if (!a) continue;
		const mx = Math.max(r, g, b), mn = Math.min(r, g, b);
		if (mx - mn < 26 && mx > 48) {
			// keep a faint gloss so the black reads as a surface: 70 -> 30, 200 -> 52
			const l = 24 + (mx - 48) * 0.18;
			put(im, x, y, [l, l + 1, l + 3], a);
		}
	}
}

/** Re-paints one 16x16 nine-slice swatch (same outline / bevel scheme as gen_omnitrix_watch_v2.js). */
function swatch(im, ox, oy, base, line, bevel, noise, seed) {
	base = hex(base); line = hex(line); bevel = hex(bevel);
	for (let v = 0; v < 16; v++) for (let u = 0; u < 16; u++) {
		const e = Math.min(u, v, 15 - u, 15 - v);
		let c;
		if (e === 0) c = line;
		else if (e === 1) c = (u <= 1 || v <= 1) ? bevel : mixc(base, line, 0.35);
		else c = shade(base, 1 - noise + hash(u, v, seed) * noise * 2);
		put(im, ox + u, oy + v, c);
	}
}

function watch(red) {
	const im = load(red ? 'watch_red.png' : 'watch.png');
	swatch(im, 0, 0, '#3e9a3a', '#123a14', '#6cc75a', 0.05, 1);    // cuff: the green band
	swatch(im, 16, 0, '#8e959c', '#2a2d31', '#b8bfc5', 0.04, 2);   // plate: chunky grey
	swatch(im, 48, 0, '#9aa1a8', '#2c2f33', '#c9cfd4', 0.03, 3);   // grey: the bezel (the recal model draws it grey)
	swatch(im, 0, 16, '#17191c', '#050607', '#34383d', 0.03, 4);   // white -> black: the stripes over the band
	swatch(im, 48, 16, '#a9b0b6', '#2c2f33', '#d6dbdf', 0.02, 5);  // ring: the buttons' grey collars
	greyToBlack(im, 64, 64, 64, 64);                                // the face: green-and-black hourglass
	return im;
}

function morph(red) {
	const im = load(red ? 'face_morph_red.png' : 'face_morph.png');
	greyToBlack(im, 0, 0, im.w, im.h);
	return im;
}

function write(rel, im) {
	const out = path.join(TEX, rel);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	PNG.write(out, im.w, im.h, im.px);
}

// ---------------------------------------------------------------- 16x16 item icons
function blank() { return { w: 16, h: 16, px: Buffer.alloc(16 * 16 * 4) }; }
function itemWatch() {
	const im = blank();
	const G = hex('#3e9a3a'), Gd = hex('#1f5a1f'), K = hex('#141517'), Gr = hex('#9aa1a8'), Grd = hex('#5d636a'), L = hex('#8cf03a');
	for (let y = 3; y < 13; y++) for (let x = 0; x < 16; x++) {
		let c = (y === 3 || y === 12) ? Gd : G;
		if (x % 4 === 1) c = K; // the black stripes across the band
		put(im, x, y, c);
	}
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x + 0.5 - 8, dy = y + 0.5 - 8, r = Math.hypot(dx, dy);
		if (r > 7.6) continue;
		let c;
		if (r > 6.0) c = r > 7.0 ? Grd : Gr;                    // the chunky grey bezel
		else if (r > 5.2) c = K;
		else c = Math.abs(dx) <= 1.0 + Math.abs(dy) * 0.75 ? L : K; // the green-and-black hourglass
		put(im, x, y, c);
	}
	put(im, 0, 7, Gr); put(im, 0, 8, Gr); put(im, 15, 7, Gr); put(im, 15, 8, Gr); // the two side buttons
	write('item/recalibrated_omnitrix.png', im);
}
function itemChip() {
	const im = blank();
	const B = hex('#1c2a22'), Bl = hex('#2f4a3a'), T = hex('#8cf03a'), Td = hex('#3c7d22'), P = hex('#c9cfd4'), K = hex('#0b0f0c');
	for (let y = 3; y < 13; y++) for (let x = 2; x < 14; x++) {
		let c = (x === 2 || x === 13 || y === 3 || y === 12) ? K : (x + y) % 7 === 0 ? Bl : B;
		put(im, x, y, c);
	}
	for (const x of [4, 7, 10]) { put(im, x, 1, P); put(im, x, 2, P); put(im, x, 13, P); put(im, x, 14, P); } // pins
	// the hourglass etched in lime traces
	for (let y = 5; y < 11; y++) {
		const half = Math.abs(y - 7.5) * 0.8 + 0.5;
		for (let x = 4; x < 12; x++) if (Math.abs(x + 0.5 - 8) <= half) put(im, x, y, Math.abs(x + 0.5 - 8) > half - 1 ? Td : T);
	}
	write('item/recalibration_chip.png', im);
}

write('entity/omnitrix/watch_recal.png', watch(false));
write('entity/omnitrix/watch_recal_red.png', watch(true));
write('entity/omnitrix/face_morph_recal.png', morph(false));
write('entity/omnitrix/face_morph_recal_red.png', morph(true));
itemWatch();
itemChip();
console.log('recalibrated watch textures written');
