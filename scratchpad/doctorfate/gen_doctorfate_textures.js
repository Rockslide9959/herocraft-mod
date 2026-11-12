// node scratchpad/doctorfate/gen_doctorfate_textures.js -- v0.17.3 Doctor Fate's textures (all procedural, no borrowed art):
//   entity/doctor_fate/suit.png, suit_slim.png  the blue-and-gold outfit (64x64 player layout; the head is a blue hood under the helmet)
//   entity/doctor_fate/helmet.png               the Helmet of Fate model (64x32: dome, T-shaped eye slit, side fins, crest, Amulet of Anubis)
//   entity/doctor_fate/cloak.png                the gold Cloak of Destiny (the Superman cape's layout, recoloured gold by luminance)
//   entity/doctor_fate/avatar.png               the Avatar of Order: the suit in pale gold (it's drawn translucent and glowing)
//   entity/tomb_guardian.png                    the mummy-wrapped husk (64x64 zombie layout)
//   item/helmet_of_fate.png, item/ankh_charm.png, block/helmet_of_fate_front.png, block/helmet_of_fate_side.png
'use strict';
const PNG = require('../pnglib.js');
const path = require('path');
const fs = require('fs');
const A = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');

function img(w, h) {
	return { W: w, H: h, px: Buffer.alloc(w * h * 4) };
}
function set(im, x, y, c) {
	if (x < 0 || y < 0 || x >= im.W || y >= im.H) return;
	const i = (y * im.W + x) * 4;
	im.px[i] = (c >> 16) & 255; im.px[i + 1] = (c >> 8) & 255; im.px[i + 2] = c & 255; im.px[i + 3] = c >>> 24 === 0 && c !== 0 ? 255 : (c >>> 24);
}
function rgb(r, g, b, a = 255) {
	return ((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255)) >>> 0;
}
function shade(c, f) {
	const r = Math.min(255, Math.round(((c >> 16) & 255) * f)), g = Math.min(255, Math.round(((c >> 8) & 255) * f)), b = Math.min(255, Math.round((c & 255) * f));
	return rgb(r, g, b, c >>> 24);
}
function hash(x, y, s) {
	let h = (x * 374761393 + y * 668265263 + s * 2147483647) >>> 0;
	h = ((h ^ (h >>> 13)) * 1274126177) >>> 0;
	return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function save(im, rel) {
	const out = path.join(A, rel);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	PNG.write(out, im.W, im.H, im.px);
	console.log('wrote', rel);
}

const GOLD = rgb(232, 178, 48), GOLD_HI = rgb(255, 222, 110), GOLD_LO = rgb(170, 118, 24), GOLD_DK = rgb(120, 78, 14);
const BLUE = rgb(36, 70, 176), BLUE_HI = rgb(64, 104, 214), BLUE_LO = rgb(22, 44, 120);

/**
 * Paints one box of the model's UV (Minecraft box UV) with fn(face, x, y, w, h) -> colour (0 = transparent). x/y are within the
 * face; side faces' y counts down from the top.
 */
function box(im, u0, v0, w, h, d, fn) {
	const faces = [
		['top', u0 + d, v0, w, d], ['bottom', u0 + d + w, v0, w, d],
		['right', u0, v0 + d, d, h], ['front', u0 + d, v0 + d, w, h], ['left', u0 + d + w, v0 + d, d, h], ['back', u0 + 2 * d + w, v0 + d, w, h],
	];
	for (const [face, fu, fv, fw, fh] of faces) {
		for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
			const c = fn(face, x, y, fw, fh);
			if (c) set(im, fu + x, fv + y, c);
		}
	}
}

function gold(x, y, s) {
	const n = hash(x, y, s);
	return n < 0.12 ? GOLD_HI : n > 0.9 ? GOLD_LO : GOLD;
}
function blue(x, y, s) {
	const n = hash(x, y, s);
	return n < 0.1 ? BLUE_HI : n > 0.88 ? BLUE_LO : BLUE;
}

// ---------------------------------------------------------------- the suit
function suit(slim, pale) {
	const im = img(64, 64);
	const tint = c => pale ? shade(rgb(255, 236, 160), 0.55 + 0.45 * (((c >> 8) & 255) / 255)) : c;
	const aw = slim ? 3 : 4;
	// head: a blue hood (hidden under the helmet)
	box(im, 0, 0, 8, 8, 8, (f, x, y) => tint(blue(x, y, 1)));
	// body: gold shoulder bib (rows 0-3, a V at the front), gold belt (rows 8-9), blue elsewhere
	box(im, 16, 16, 8, 12, 4, (f, x, y, w) => {
		if (f === 'top') return tint(gold(x, y, 2));
		if (f === 'bottom') return tint(blue(x, y, 2));
		if (y === 8 || y === 9) return tint(y === 8 ? GOLD_HI : GOLD_LO);
		if (f === 'front' || f === 'back') {
			const mid = Math.abs(x - (w - 1) / 2);
			if (y <= 3 - Math.floor(mid / 2) + (f === 'front' ? 1 : 0) && y <= 4) return tint(y === 0 ? GOLD_HI : gold(x, y, 3));
			// the Amulet of Anubis's chain on the front
			if (f === 'front' && (y === 4 || y === 5) && mid >= 1 && mid <= 2.5 && (x + y) % 2 === 0) return tint(GOLD_LO);
		} else if (y <= 3) {
			return tint(gold(x, y, 4));
		}
		return tint(blue(x, y, 5));
	});
	// arms: blue upper, gold gloves from row 6, a flared cuff at rows 6-7, gold shoulder caps
	const arm = (f, x, y) => {
		if (f === 'top') return tint(gold(x, y, 6));
		if (f === 'bottom') return tint(GOLD_LO);
		if (y <= 1) return tint(gold(x, y, 7));
		if (y >= 6) return tint(y === 6 ? GOLD_HI : y === 11 ? GOLD_LO : gold(x, y, 8));
		return tint(blue(x, y, 9));
	};
	box(im, 40, 16, aw, 12, 4, arm);
	box(im, 32, 48, aw, 12, 4, arm);
	// legs: blue, gold boots from row 7 (a cuff band at row 7)
	const leg = (f, x, y) => {
		if (f === 'top') return tint(blue(x, y, 10));
		if (f === 'bottom') return tint(GOLD_DK);
		if (y >= 7) return tint(y === 7 ? GOLD_HI : y === 11 ? GOLD_LO : gold(x, y, 11));
		return tint(blue(x, y, 12));
	};
	box(im, 0, 16, 4, 12, 4, leg);
	box(im, 16, 48, 4, 12, 4, leg);
	// overlays: the bib stands proud (jacket rows 0-2), glove cuffs flare (sleeves rows 6-7), boot cuffs (pants row 7)
	box(im, 16, 32, 8, 12, 4, (f, x, y, w) => {
		if (f === 'top') return tint(gold(x, y, 13));
		if (f === 'bottom') return 0;
		if (f === 'front' || f === 'back') {
			const mid = Math.abs(x - (w - 1) / 2);
			return y <= 2 - Math.floor(mid / 3) ? tint(y === 0 ? GOLD_HI : gold(x, y, 14)) : 0;
		}
		return y <= 2 ? tint(gold(x, y, 15)) : 0;
	});
	const cuff = (f, x, y) => (f !== 'top' && f !== 'bottom' && (y === 6 || y === 7)) ? tint(y === 6 ? GOLD_HI : GOLD) : 0;
	box(im, 40, 32, aw, 12, 4, cuff);
	box(im, 48, 48, aw, 12, 4, cuff);
	const boot = (f, x, y) => (f !== 'top' && f !== 'bottom' && y === 7) ? tint(GOLD_HI) : 0;
	box(im, 0, 32, 4, 12, 4, boot);
	box(im, 0, 48, 4, 12, 4, boot);
	return im;
}

// ---------------------------------------------------------------- the helmet (64 x 32)
function helmet() {
	const im = img(64, 32);
	const SLIT = rgb(18, 12, 6);
	// the dome: 10 x 10 x 10, front = the T-shaped slit
	box(im, 0, 0, 10, 10, 10, (f, x, y) => {
		const base = gold(x, y, 20);
		if (f === 'front') {
			if ((y === 4 || y === 5) && x >= 1 && x <= 8) return SLIT; // the eye bar
			if (y >= 5 && y <= 9 && (x === 4 || x === 5)) return SLIT; // the nose-to-chin slit
			if (y === 3 && x >= 1 && x <= 8) return GOLD_HI; // the brow ridge
			if (x === 0 || x === 9) return GOLD_LO;
			return base;
		}
		if (f === 'top') return (x === 4 || x === 5) ? GOLD_HI : base;
		if (f === 'bottom') return GOLD_DK;
		// sides and back: a raised rim at the bottom, a darker band where the fins sit
		if (y === 9) return GOLD_LO;
		if (y === 8) return GOLD_HI;
		return base;
	});
	// fins (1 x 6 x 6) and crest (1 x 2 x 9)
	box(im, 40, 0, 1, 6, 6, (f, x, y) => (y === 0 ? GOLD_HI : gold(x, y, 21)));
	box(im, 40, 12, 1, 2, 9, (f, x, y) => (f === 'top' ? GOLD_HI : gold(x, y, 22)));
	// the Amulet of Anubis (3 x 3 x 1): gold with a blue-green eye
	box(im, 0, 20, 3, 3, 1, (f, x, y) => (f === 'front' && x === 1 && y === 1) ? rgb(40, 180, 170) : (f === 'front' ? GOLD_HI : GOLD));
	return im;
}

// ---------------------------------------------------------------- the cloak
function cloak() {
	const src = PNG.read(path.join(A, 'entity/superman_cape.png'));
	const im = img(src.W, src.H);
	for (let i = 0; i < src.W * src.H; i++) {
		const r = src.px[i * 4], g = src.px[i * 4 + 1], b = src.px[i * 4 + 2], a = src.px[i * 4 + 3];
		if (a === 0) continue;
		const lum = (Math.max(r, g, b) / 255);
		// the S-shield's yellow (high green) becomes the cloak's deep orange-gold lining; everything else gold by brightness
		const yellow = g > 140 && r > 180 && b < 100;
		const c = yellow ? shade(GOLD_LO, 0.9) : shade(GOLD, 0.45 + 0.75 * lum);
		im.px[i * 4] = (c >> 16) & 255; im.px[i * 4 + 1] = (c >> 8) & 255; im.px[i * 4 + 2] = c & 255; im.px[i * 4 + 3] = a;
	}
	return im;
}

// ---------------------------------------------------------------- the Tomb Guardian (mummy)
function mummy() {
	const im = img(64, 64);
	const wrap = (x, y, s) => {
		const band = (y + (hash(0, Math.floor(y / 2), s) > 0.5 ? 1 : 0)) % 3 === 0;
		const n = hash(x, y, s);
		let c = band ? rgb(150, 136, 104) : rgb(206, 192, 156);
		if (n < 0.08) c = rgb(110, 96, 70);
		if (n > 0.95) c = rgb(228, 218, 190);
		return c;
	};
	box(im, 0, 0, 8, 8, 8, (f, x, y) => {
		if (f === 'front' && y === 3 && (x === 2 || x === 5)) return rgb(240, 200, 60); // glowing eyes in the wraps
		if (f === 'front' && y === 3 && (x === 1 || x === 6)) return rgb(30, 22, 14);
		return wrap(x, y, 30);
	});
	box(im, 16, 16, 8, 12, 4, (f, x, y) => (f === 'front' && y === 1 && x >= 2 && x <= 5) ? GOLD : wrap(x, y, 31)); // a gold collar
	box(im, 40, 16, 4, 12, 4, (f, x, y) => wrap(x, y, 32));
	box(im, 32, 48, 4, 12, 4, (f, x, y) => wrap(x, y, 33));
	box(im, 0, 16, 4, 12, 4, (f, x, y) => wrap(x, y, 34));
	box(im, 16, 48, 4, 12, 4, (f, x, y) => wrap(x, y, 35));
	// loose bandage ends on the overlay layers
	box(im, 16, 32, 8, 12, 4, (f, x, y) => (hash(x, y, 36) < 0.12 && f !== 'top' && f !== 'bottom') ? rgb(196, 182, 146) : 0);
	box(im, 40, 32, 4, 12, 4, (f, x, y) => (hash(x, y, 37) < 0.1 && f !== 'top' && f !== 'bottom') ? rgb(196, 182, 146) : 0);
	return im;
}

// ---------------------------------------------------------------- 16 x 16 icons
function icon(rows, pal) {
	const im = img(16, 16);
	rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) set(im, x, y, pal[ch]); }));
	return im;
}
const PAL = { 'G': GOLD, 'H': GOLD_HI, 'L': GOLD_LO, 'D': GOLD_DK, 'k': rgb(18, 12, 6), 'b': BLUE, 'c': rgb(40, 180, 170) };
const HELMET_ICON = [
	'................',
	'.......HH.......',
	'.....HHGGHH.....',
	'....HGGGGGGL....',
	'...HGGGGGGGGL...',
	'..HGGGGHHGGGGL..',
	'..GGHHHHHHHHGL..',
	'..GkkkkkkkkkkL..',
	'.LGkkkkkkkkkkLL.',
	'.LGGGGGkkGGGGLL.',
	'.LGGGGGkkGGGGLL.',
	'.LLGGGGkkGGGLLD.',
	'..LLGGGkkGGLLD..',
	'...DLLLkkLLLD...',
	'....DDDDDDDD....',
	'................',
];
const ANKH_ICON = [
	'................',
	'......HHHH......',
	'.....HG..GL.....',
	'....HG....GL....',
	'....G......L....',
	'....G......L....',
	'.....G....L.....',
	'..HHHHGccGLLLL..',
	'..GGGGGGGGGGLL..',
	'.......GL.......',
	'.......GL.......',
	'.......GL.......',
	'.......GL.......',
	'.......GL.......',
	'......DGLD......',
	'................',
];
const FRONT_BLOCK = [
	'HHHHHHHHHHHHHHHH',
	'HGGGGGGGGGGGGGGL',
	'GGGGGGGGGGGGGGGL',
	'GGGGGGGGGGGGGGGL',
	'GGHHHHHHHHHHHHGL',
	'GkkkkkkkkkkkkkkL',
	'GkkkkkkkkkkkkkkL',
	'GkkkkkkkkkkkkkkL',
	'GGGGGGGkkGGGGGGL',
	'GGGGGGGkkGGGGGGL',
	'GGGGGGGkkGGGGGGL',
	'GGGGGGGkkGGGGGGL',
	'GGGGGGGkkGGGGGGL',
	'GGGGGGGkkGGGGGGL',
	'LLLLLLLkkLLLLLLL',
	'DDDDDDDDDDDDDDDD',
];

function sideBlock() {
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = gold(x, y, 40);
		if (y === 0) c = GOLD_HI;
		if (y >= 14) c = y === 14 ? GOLD_HI : GOLD_LO;
		if (x === 15) c = GOLD_LO;
		set(im, x, y, c);
	}
	return im;
}

// v0.17.3: suit.png, suit_slim.png and cloak.png now come from the user's chosen DCEU skin -- build_fate_skin.js writes them
// save(suit(false, false), 'entity/doctor_fate/suit.png');
// save(suit(true, false), 'entity/doctor_fate/suit_slim.png');
save(suit(false, true), 'entity/doctor_fate/avatar.png');
save(helmet(), 'entity/doctor_fate/helmet.png');
// save(cloak(), 'entity/doctor_fate/cloak.png');
save(mummy(), 'entity/tomb_guardian.png');
save(icon(HELMET_ICON, PAL), 'item/helmet_of_fate.png');
save(icon(ANKH_ICON, PAL), 'item/ankh_charm.png');
save(icon(FRONT_BLOCK, PAL), 'block/helmet_of_fate_front.png');
save(sideBlock(), 'block/helmet_of_fate_side.png');
