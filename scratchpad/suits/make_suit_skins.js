// v0.16.7 Miraculous suit skins -- the v0.16.6 cut (main checkout scratchpad/make_suit_skins.js + gen_pegasus_v0166.js)
// with the gap fixes and Pegasus's goggles, writing straight into the assets.
//   * the cut itself is unchanged: skin-tone texels, the characters' hair and eyes go transparent so the wearer's own
//     face / hair shows (the v0.16.6 user rule);
//   * fillSuitGaps v2: a base-layer body / arm / leg face that is EMPTY in the source (the Pegasus skin left its inner
//     arm faces, its torso top / bottom and its leg tops unpainted, so the wearer's own skin showed through the inside of
//     the arms, in first person too) is now painted -- a ring face from its opposite face (mirrored), a top / bottom face
//     from the edge rows of the faces round it -- instead of being skipped; partly painted faces still take the nearest
//     painted texel of the same face;
//   * Pegasus: the skin's black civilian glasses come off the hat layer as before, and the suit's GOGGLES go on instead
//     (user, v0.16.7: "in the show Pegasus wears goggles") -- two big rounded turquoise lenses in a light cream frame on
//     the hat layer's face, a dark strap round the sides and back of the head (under the mane), and the same goggles a
//     shade darker on the base layer under them so there is no gap of bare face behind the raised frame.
// Usage: node scratchpad/suits/make_suit_skins.js [outDir]   (default: the assets folder)
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('./skin_tools.js');
const C = require('./check.js');
const SRC = path.join(__dirname, 'src');
const OUT = process.argv[2] || C.A;
const inHead = (x, y) => y < 16;
const headFront = (x, y) => y >= 8 && y < 16 && ((x >= 8 && x < 16) || (x >= 40 && x < 48));
const skinTone = (r, g, b) => r > 170 && g > 110 && b >= 92 && r >= g && g >= b && r - b > 22 && r - b < 125;
const HAIR = {
	ladybug: (r, g, b) => b > r + 6 && Math.max(r, g, b) < 150,
	cat_noir: (r, g, b) => r > 190 && g > 175 && b < 175 && r - b > 40,
	hawk_moth: () => false,
	carapace: () => false,
	pegasus: () => false,
	rena_rouge: (r, g, b) => r > 100 && g < 70 && b < 70 && r > 2 * g,
};
const EYES = {
	ladybug: (r, g, b) => (b > r + 40) || (r > 220 && g > 220 && b > 220),
	cat_noir: () => false,
	rena_rouge: (r, g, b) => (r > 225 && g > 225 && b > 225) || (b > r + 30),
	carapace: () => false,
	pegasus: (r, g, b) => r > 225 && g > 220 && b > 205,
	hawk_moth: (r, g, b) => (r > 225 && g > 225 && b > 225) || (b > r + 30) || Math.max(r, g, b) < 60,
};
// asset names: [slim, wide]
const FILES = { ladybug: ['suit_slim', 'suit_wide'], cat_noir: ['suit_slim', 'suit_wide'], hawk_moth: ['suit_slim', 'suit_wide'],
	rena_rouge: ['suit', 'suit_wide'], carapace: ['suit', 'suit_wide'], pegasus: ['suit', 'suit_wide'] };

const at = (img, x, y) => (y * img.w + x) * 4;
const get = (img, x, y) => { const i = at(img, x, y); return [img.px[i], img.px[i + 1], img.px[i + 2], img.px[i + 3]]; };
const put = (img, x, y, c) => { const i = at(img, x, y); img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = c.length > 3 ? c[3] : 255; };
const shade = (c, k) => [0, 1, 2].map(i => Math.max(0, Math.min(255, Math.round(c[i] * k)))).concat(255);
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];

function cut(hero, img) {
	let n = 0;
	for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
		const i = at(img, x, y); if (img.px[i + 3] === 0) continue;
		const r = img.px[i], g = img.px[i + 1], b = img.px[i + 2];
		const headBase = y < 16 && x < 32, hat = y < 16 && x >= 32;
		const maskBand = hero === 'pegasus' ? (r > 230 && g > 210 && b > 150 && r - b < 70)
			: x >= 8 && x < 16 && (hero === 'ladybug' ? y >= 12 && y < 15 : hero === 'carapace' ? y >= 11 && y < 15 : y >= 9 && y < (hero === 'rena_rouge' ? 15 : 14));
		const drop = (headBase && !maskBand && hero !== 'hawk_moth')
			|| (hat && hero === 'pegasus' && Math.max(r, g, b) < 80)
			|| (skinTone(r, g, b) && !(hat && hero === 'rena_rouge') && !(hero === 'pegasus' && (maskBand || hat)))
			|| (inHead(x, y) && !(maskBand && hero === 'ladybug') && HAIR[hero](r, g, b))
			|| (headFront(x, y) && y >= 10 && y < (hero === 'ladybug' ? 15 : 14) && EYES[hero](r, g, b))
			|| (hero === 'carapace' && headBase && r > 170 && g > 110 && b < 110 && r > g && g > b)
			|| (hero === 'hawk_moth' && headBase && y >= 13 && r > 180 && r > g && g > b)
			|| (hero === 'rena_rouge' && headBase && y >= 10 && y < 14 && (x === 11 || x === 12))
			|| ((hero === 'rena_rouge' || hero === 'ladybug') && y >= 16 && y < 24 && HAIR[hero](r, g, b));
		if (drop) { img.px[i + 3] = 0; n++; }
	}
	return n;
}

// ---- fillSuitGaps v2 (base-layer body / arms / legs)
function fillSuitGaps(img, slim) {
	const P = C.parts(slim);
	const log = [];
	for (const pn of ['body', 'rarm', 'larm', 'rleg', 'lleg']) {
		const f = P[pn];
		const opaque = ([fx, fy, fw, fh]) => { let k = 0; for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) if (img.px[at(img, x, y) + 3] > 0) k++; return k; };
		const ring = { right: 'left', left: 'right', front: 'back', back: 'front' };
		// 1. empty ring faces: the opposite face, mirrored left-right (a hair darker: the inner side is in shadow)
		for (const [fn, opp] of Object.entries(ring)) {
			if (opaque(f[fn]) || !opaque(f[opp])) continue;
			const [dx, dy, dw, dh] = f[fn], [sx, sy, sw] = f[opp];
			for (let y = 0; y < dh; y++) for (let x = 0; x < dw; x++) {
				const c = get(img, sx + Math.min(sw - 1, Math.round((dw - 1 - x) * (sw - 1) / Math.max(1, dw - 1))), sy + y);
				put(img, dx + x, dy + y, c[3] ? shade(c, 0.92) : c);
			}
			log.push(`${pn}.${fn} <- mirrored ${pn}.${opp}`);
		}
		// 2. empty top / bottom: each texel from the nearest edge row of the ring faces round it
		for (const fn of ['top', 'bottom']) {
			if (opaque(f[fn])) continue;
			const [dx, dy, dw, dd] = f[fn];
			const row = fn === 'top' ? 0 : f.front[3] - 1; // the ring's top row or bottom row
			for (let j = 0; j < dd; j++) for (let x = 0; x < dw; x++) {
				// in the unwrap the top face's last row (j = d-1) meets the front, its first the back; the bottom face is flipped
				const towardFront = fn === 'top' ? j >= dd / 2 : j < dd / 2;
				const src = towardFront ? get(img, f.front[0] + x, f.front[1] + row) : get(img, f.back[0] + (dw - 1 - x), f.back[1] + row);
				put(img, dx + x, dy + j, src[3] ? shade(src, fn === 'top' ? 1.0 : 0.85) : src);
			}
			log.push(`${pn}.${fn} <- ring edge rows`);
		}
		// 3. holes inside partly painted faces: the nearest painted texel of the same face (the v0.16.6 rule)
		let holes = 0;
		for (const [fx, fy, fw, fh] of Object.values(f)) {
			const op = [];
			for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) if (img.px[at(img, x, y) + 3] > 0) op.push([x, y]);
			if (!op.length) continue;
			for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) {
				const i = at(img, x, y);
				if (img.px[i + 3] === 255) continue;
				let best = null, bd = Infinity;
				for (const [qx, qy] of op) { if (img.px[at(img, qx, qy) + 3] < 255) continue; const d = (qx - x) ** 2 + (qy - y) ** 2; if (d < bd) { bd = d; best = [qx, qy]; } }
				if (!best) continue;
				img.px.copy(img.px, i, at(img, best[0], best[1]), at(img, best[0], best[1]) + 4);
				img.px[i + 3] = 255;
				holes++;
			}
		}
		if (holes) log.push(`${pn}: ${holes} holes`);
	}
	return log;
}

// ---- Pegasus's goggles
const FRAME_HI = hex(0xFBF5E2), FRAME = hex(0xEDE3C8), FRAME_LO = hex(0xCDBE98), FRAME_DK = hex(0xA8986E);
const LENS_GLINT = hex(0xC8FFF2), LENS_HI = hex(0x6FE3CF), LENS = hex(0x34B7A3), LENS_LO = hex(0x22907F), LENS_DK = hex(0x166A5D);
const STRAP = hex(0x2B2522), STRAP_HI = hex(0x3D3530), STRAP_LO = hex(0x1C1816);
// one eye, 4 x 5 (cols 0..3, rows 0..4) on the face: a rounded frame ring round a 2 x 3 lens; `outer` = the column at the
// side of the head (the strap meets it there)
function eye(img, ox, oy, mirror, k) {
	const P = (cx, cy, c) => put(img, ox + (mirror ? 3 - cx : cx), oy + cy, k === 1 ? c : shade(c, k));
	// frame: top row (rounded corners left off), sides, bottom row
	P(1, 0, FRAME_HI); P(2, 0, FRAME_HI);
	P(0, 1, FRAME); P(3, 1, FRAME); P(0, 2, FRAME); P(3, 2, FRAME_LO); P(0, 3, FRAME_LO); P(3, 3, FRAME_LO);
	P(1, 4, FRAME_DK); P(2, 4, FRAME_DK);
	// lens: light at the top-outer corner (the sky catching it), deep at the bottom-inner
	P(1, 1, LENS_GLINT); P(2, 1, LENS_HI);
	P(1, 2, LENS_HI); P(2, 2, LENS);
	P(1, 3, LENS_LO); P(2, 3, LENS_DK);
	// the rounded corners: the hat-layer corners stay clear (rounded); on the base they are the frame's shadow
	if (k !== 1) { P(0, 0, FRAME_DK); P(3, 0, FRAME_DK); P(0, 4, FRAME_DK); P(3, 4, FRAME_DK); }
}
function goggles(img) {
	// the civilian glasses off the hat layer's face (as gen_pegasus_v0166.js did)
	let glasses = 0;
	for (let y = 8; y < 16; y++) for (let x = 40; x < 48; x++) { const c = get(img, x, y); if (c[3] && Math.max(c[0], c[1], c[2]) < 110) { put(img, x, y, [0, 0, 0, 0]); glasses++; } }
	// the eyes are rows 12-13 of the face; the goggles span rows 10..14 (y 10-14): the wearer's right eye is the face's
	// left half (x 8..11 / 40..43), mirrored for the other so the glints sit at the outer corners
	const Y = 10;
	eye(img, 40, Y, false, 1); eye(img, 44, Y, true, 1); // hat layer: raised
	eye(img, 8, Y, false, 0.78); eye(img, 12, Y, true, 0.78); // base layer: the same goggles in shadow behind them
	// the bridge between the lenses sits low over the nose: the inner frame columns already meet (x 43 / 44)
	// the strap: rows 11-12 round the sides and the back -- hat layer over everything but the mane, and the base layer
	// under it so the strap never floats over bare skin
	const strapRows = [[11, STRAP_HI], [12, STRAP]];
	for (const [y, c] of strapRows) {
		for (let x = 32; x < 40; x++) put(img, x, y, x === 39 ? FRAME_LO : c);           // hat: his right side (39 meets the frame)
		for (let x = 48; x < 56; x++) put(img, x, y, x === 48 ? FRAME_LO : c);           // hat: his left side
		for (let x = 56; x < 64; x++) if (!get(img, x, y)[3]) put(img, x, y, c);           // hat: the back, under the mane
		for (let x = 0; x < 8; x++) put(img, x, y, shade(c, 0.85));                        // base: sides and back
		for (let x = 16; x < 24; x++) put(img, x, y, shade(c, 0.85));
		for (let x = 24; x < 32; x++) put(img, x, y, shade(c, 0.85));
	}
	// a buckle on the back of the strap (hat layer), between the mane's strands
	put(img, 59, 11, FRAME_LO); put(img, 60, 11, FRAME_LO); put(img, 59, 12, FRAME_DK); put(img, 60, 12, FRAME_DK);
	return glasses;
}

const report = {};
for (const hero of Object.keys(FILES)) {
	const img = T.decode(path.join(SRC, `${hero}.png`));
	const n = cut(hero, img);
	const slimSource = !img.px[at(img, 55, 24) + 3];
	const gaps = fillSuitGaps(img, slimSource);
	let extra = '';
	if (hero === 'pegasus') extra = ` glasses off: ${goggles(img)} texels, goggles on`;
	const slim = slimSource ? img : T.convertArms(img, true), wide = slimSource ? T.convertArms(img, false) : img;
	const [fs1, fw1] = FILES[hero];
	fs.mkdirSync(path.join(OUT, hero), { recursive: true });
	fs.writeFileSync(path.join(OUT, hero, fs1 + '.png'), T.encode(slim.w, slim.h, slim.px));
	fs.writeFileSync(path.join(OUT, hero, fw1 + '.png'), T.encode(wide.w, wide.h, wide.px));
	report[hero] = n;
	console.log(hero, slimSource ? 'slim source' : 'wide source', '| cut', n, '| gaps:', gaps.length ? gaps.join('; ') : 'none', extra);
}
