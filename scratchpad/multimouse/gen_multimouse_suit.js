// PREVIEW ONLY (not wired into the game): MULTIMOUSE, the Mouse Miraculous suit, painted from scratch as a 64x64 skin
// overlay. Follows the suit conventions of scratchpad/viperion/gen_viperion_suit.js:
//   * the head's base layer is clear except the mask (rows y 11-14 of the face, eye holes on rows 12-13, wrapping one
//     texel round each side of the head) -- the wearer's own face and hair show;
//   * the hood is on the HAT layer (top, sides, back), its rim framing the open face;
//   * the bodysuit is on the base layer; raised bits (cuffs, boot cuffs, belt knot, pendant) are repeated on the overlay
//     layer so they stand proud.
// Written wide (4-px arms, as drawn) and slim (3-px arms via T.convertArms), plus x10 flat previews.
// Usage: node scratchpad/multimouse/gen_multimouse_suit.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('../viperion/skin_tools.js');
const OUT = __dirname;

// ---------------------------------------------------------------- palette (dark -> light ramps)
const GREY = [[52, 54, 66], [78, 81, 96], [108, 112, 128], [140, 144, 160], [174, 178, 192], [208, 211, 222], [236, 238, 245]];
const RIM = [[128, 132, 148], [162, 166, 180], [196, 199, 211], [222, 224, 233], [244, 245, 250]];
const LIL = [[170, 118, 160], [198, 148, 190], [222, 178, 214], [240, 204, 232], [252, 230, 248]];
const PINK = [[128, 30, 84], [170, 48, 116], [210, 74, 148], [236, 114, 178], [250, 166, 210]];
const SILVER = [[70, 74, 86], [126, 132, 146], [186, 190, 202], [226, 230, 238], [252, 252, 255]];
const MASK = [[92, 96, 114], [128, 132, 150], [160, 164, 180], [194, 197, 210], [228, 230, 240]];
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const tone = (ramp, v) => ramp[clamp(Math.round(v * (ramp.length - 1)), 0, ramp.length - 1)];

const img = { w: 64, h: 64, px: Buffer.alloc(64 * 64 * 4) };
const set = (x, y, c) => { const i = (y * 64 + x) * 4; img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = 255; };
const clear = (x, y) => { const i = (y * 64 + x) * 4; img.px.fill(0, i, i + 4); };

// the standard skin box unwrap
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
// paint every face of a box: fn(face, x, y, fw, fh) -> [r,g,b] | null
function paintBox(ox, oy, w, h, d, fn) {
	const F = faces(ox, oy, w, h, d);
	for (const [f, [fx, fy, fw, fh]] of Object.entries(F)) for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
		const c = fn(f, x, y, fw, fh);
		if (c) set(fx + x, fy + y, c); else clear(fx + x, fy + y);
	}
}

// ---------------------------------------------------------------- the satin shading
// a light value from the face (top bright, front, sides, back darker), a cylinder roll across the face, a slight
// fall-off downwards, and a soft diagonal gloss streak (the satin sheen)
const FACE_K = { top: 0.16, front: 0.06, right: -0.04, left: -0.02, back: -0.08, bottom: -0.3 };
function lightV(f, x, y, fw, fh, opts = {}) {
	let v = 0.5 + (FACE_K[f] || 0);
	const u = fw > 1 ? (x + 0.5) / fw * 2 - 1 : 0;
	if (f !== 'top' && f !== 'bottom') v += 0.14 * (1 - u * u) - 0.07;
	v += 0.05 - 0.1 * (y / Math.max(1, fh - 1));
	const g = (opts.gloss == null ? 0.3 : opts.gloss) * 0.85;
	const c = (opts.gc == null ? fw * 0.32 : opts.gc) + y * (opts.gs == null ? 0.06 : opts.gs * 0.4);
	v += g * Math.exp(-((x - c) ** 2) / 1.5) * (opts.gmask ? opts.gmask(y) : 1);
	return v;
}
const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const jit = (x, y, k = 0.05) => (hash(x, y) - 0.5) * k * 0.2;

// ================================================================ HEAD: base layer = the mask only
// the mask's front (head front x 8-15, rows y 11-14): K = edge (lighter), M = mask, H = sheen, . = clear
const MASK_ROWS = [
	'lHHHHHHl', // y 11 brow
	'M..HH..M', // y 12 eye holes
	'm..MM..m', // y 13 eye holes
	'.lm..ml.', // y 14 lower points
];
const MK = { l: MASK[4], H: MASK[3], M: MASK[2], m: MASK[1], d: MASK[0] };
MASK_ROWS.forEach((row, dy) => [...row].forEach((ch, dx) => { if (ch !== '.') set(8 + dx, 11 + dy, MK[ch]); }));
for (let dy = 0; dy < 3; dy++) { // round each side: x 7 (right side's front column) and x 16 (left side's), a short strap behind
	set(7, 11 + dy, dy === 0 ? MASK[3] : MASK[1]); set(16, 11 + dy, dy === 0 ? MASK[3] : MASK[1]);
	set(6, 11 + dy, dy === 1 ? MASK[0] : MASK[1]); set(17, 11 + dy, dy === 1 ? MASK[0] : MASK[1]);
}

// ================================================================ HEAD: hat layer = the hood
paintBox(32, 0, 8, 8, 8, (f, x, y, fw, fh) => {
	if (f === 'front') {
		// the hood's rim framing the face: a full brow row, two-texel corners, one-texel sides, rounded under the chin
		const rim = y === 0 || ((y === 1 || y >= 6) && (x <= 1 || x >= 6)) || x === 0 || x === 7;
		if (!rim) return null;
		const inner = (y === 0 && x > 0 && x < 7) || (y === 1 && (x === 1 || x === 6)) || (y >= 6 && (x === 1 || x === 6));
		let v = inner ? 0.86 : 0.55;
		if (y === 0) v += 0.08 - 0.12 * Math.abs((x + 0.5) / 8 * 2 - 1);
		v -= 0.05 * (y / 7);
		return tone(RIM, v + jit(x, y));
	}
	if (f === 'bottom') { // only a dark lip round the edge (the neck opening is clear)
		const edge = x === 0 || x === 7 || y === 7;
		return edge ? tone(GREY, 0.18) : null;
	}
	let v = lightV(f, x, y, fw, fh, { gloss: 0.32, gc: f === 'top' ? 2.2 : fw * 0.3, gs: f === 'top' ? 0.35 : 0.18 });
	if (f === 'top') {
		if (x === 3 || x === 4) v -= 0.12; // the seam front-to-back along the crown
		if (x === 4) v += 0.06;
		if (y === 7) v += 0.12; // the brow fold catches the light
		if (y === 0) v -= 0.08;
	}
	if (f === 'back') {
		if (x === 3) v -= 0.14; // centre seam
		if (x === 4) v += 0.05;
		if (y === 7) v -= 0.14; // the hood's lower hem shadow
		if (y === 6) v += 0.08;
	}
	if (f === 'right' || f === 'left') {
		const front = f === 'right' ? x === 7 : x === 0; // the column next to the face rim
		if (front) v += 0.14;
		if (y === 7) v -= 0.14;
		if ((f === 'right' ? x === 2 : x === 5)) v -= 0.08; // side panel seam
	}
	return tone(GREY, v + jit(x, y, 0.06));
});

// ================================================================ BODY (16,16): the jacket, the lilac front panel, the belt
const lapelL = y => (y <= 1 ? 3 : y <= 4 ? 2 : 1);
function bodyFn(f, x, y, fw, fh) {
	if (f === 'top') { // the high collar round the neck: grey, a lighter lip on the front edge
		const v = 0.62 + (y === 3 ? 0.2 : 0) - (y === 0 ? 0.12 : 0);
		return tone(GREY, v + jit(x, y));
	}
	if (f === 'bottom') return tone(GREY, 0.2 + jit(x, y));
	// the rope belt on row 8: a twisted pink rope, three tones on a diagonal
	if (y === 8) {
		const k = (x + (f === 'back' ? 1 : 0)) % 3;
		if (f === 'back' && (x === 3 || x === 4)) return PINK[4]; // the knot the tail rope hangs from
		return PINK[k === 0 ? 1 : k === 1 ? 3 : 2];
	}
	if (f === 'front') {
		if (y <= 7) {
			// pendant at the collar (rows 0-1, x 3-4): a round silver charm
			if (y === 0 && (x === 3 || x === 4)) return SILVER[x === 3 ? 4 : 3];
			if (y === 1 && (x === 3 || x === 4)) return SILVER[x === 3 ? 2 : 1];
			if (y === 0 && (x === 2 || x === 5)) return GREY[1]; // the charm's dark outline
			if (y === 0) return tone(GREY, 0.66 - 0.2 * Math.abs((x + 0.5) / 8 * 2 - 1) + jit(x, y)); // the collar band
			const L = lapelL(y), R = 7 - L;
			if (x === L || x === R) { // the lighter jacket edges / lapels
				const v = 0.78 + (x === L ? 0.2 : 0) - 0.04 * y / 7;
				return tone(RIM, v + jit(x, y));
			}
			if (x > L && x < R) { // the lilac panel, a soft vertical sheen, darker under the chest
				let v = 0.62 + 0.18 * Math.exp(-((x - 3.2) ** 2) / 1.2) - (y === 4 ? 0.14 : 0) - 0.06 * (y / 7);
				if (y === 7) v -= 0.12;
				if (y === 5 || y === 6) v += (x === 3 ? 0.08 : 0);
				return tone(LIL, v + jit(x, y));
			}
			// the grey jacket either side
			let v = lightV(f, x, y, fw, fh, { gloss: 0.26, gc: 0.6, gs: 0.05 });
			if (x === L - 1 || x === R + 1) v -= 0.14; // the shadow the lapel throws
			if (y === 3 && (x === 0 || x === 7)) v -= 0.08; // the pec crease
			return tone(GREY, v + jit(x, y, 0.06));
		}
		// rows 9-11: the hips. A pink diagonal panel each side, sloping in towards the crotch; grey centre
		const b = y - 9; // 0..2
		const sx = x < 4 ? x : 7 - x; // distance from the outer edge
		if (sx === b || sx === b + 1) return tone(PINK, (sx === b ? 0.75 : 0.55) - 0.08 * b + jit(x, y));
		if (sx < b) return tone(GREY, 0.42 + jit(x, y));
		let v = 0.4 - 0.1 * b + (sx === 3 ? -0.06 : 0.04);
		return tone(GREY, v + jit(x, y));
	}
	// sides and back: the grey suit
	let v = lightV(f, x, y, fw, fh, { gloss: f === 'back' ? 0.24 : 0.2, gc: f === 'back' ? 1.4 : 1.2, gs: 0.08 });
	if (f === 'back') {
		if (x === 3) v -= 0.13; if (x === 4) v += 0.05; // the zip seam
		if (y === 3 && x !== 3) v -= 0.06; // shoulder-blade line
		if (y === 7) v -= 0.1; // the shadow over the belt
		if (y === 9) v -= 0.1;
	}
	if (f === 'right' || f === 'left') {
		if (y === 7) v -= 0.1;
		if (y >= 9) { // the hip panel wraps round the side
			const b = y - 9; const fx = f === 'right' ? 3 - x : x; // fx: 0 at the front edge
			if (fx === b || fx === b + 1) return tone(PINK, 0.6 - 0.08 * b + jit(x, y));
		}
	}
	return tone(GREY, v + jit(x, y, 0.06));
}
paintBox(16, 16, 8, 12, 4, bodyFn);

// ================================================================ ARMS: grey sleeves, elbow crease, pink wrist cuff, grey glove
function armFn(outer) {
	return (f, x, y, fw, fh) => {
		if (f === 'top') return tone(GREY, 0.7 + (x === 1 ? 0.1 : 0) + jit(x, y));
		if (f === 'bottom') return tone(GREY, 0.22 + jit(x, y));
		if (y === 9 || y === 10) { // the cuff: a bright top edge, a darker lower edge
			const v = y === 9 ? 0.82 : 0.5;
			return tone(PINK, v + (f === outer ? -0.1 : 0) + (f === 'back' ? -0.12 : 0) + jit(x, y));
		}
		let v = lightV(f, x, y, fw, fh, { gloss: 0.3, gc: f === 'front' ? 1.1 : 1.4, gs: 0.06 });
		if (y === 0) v += 0.1; // the shoulder's round top
		if (y === 3) v -= 0.06; // the shoulder seam
		if (y === 5 && (f === 'front' || f === 'back')) v -= 0.12; // the elbow crease
		if (y === 4 && f === 'front') v += 0.06;
		if (y === 8) v -= 0.08; // the shadow over the cuff
		if (y >= 11) v -= 0.14; // the glove, a touch darker
		if (y === 11 && f === 'front' && x === 1) v += 0.18; // a knuckle glint
		return tone(GREY, v + jit(x, y, 0.06));
	};
}
paintBox(40, 16, 4, 12, 4, armFn('right'));
paintBox(32, 48, 4, 12, 4, armFn('left'));

// ================================================================ LEGS: pink hip band, lilac thighs (grey outer), knee, pink boot cuff,
// lilac boots, pink soles
function legFn(outerFace, outerX) { // outerX: the front/back column on the outside of the leg
	return (f, x, y, fw, fh) => {
		if (f === 'top') return tone(GREY, 0.4 + jit(x, y));
		if (f === 'bottom') return tone(PINK, 0.25 + ((x + y) % 2 ? 0.08 : 0) + jit(x, y)); // the sole
		const isOuterSide = f === outerFace;
		const isInnerSide = (f === 'right' || f === 'left') && !isOuterSide;
		// how far a front/back column sits from the outer edge (0 = outermost)
		const fromOuter = f === 'front' ? (outerX === 0 ? x : 3 - x) : f === 'back' ? (outerX === 0 ? 3 - x : x) : null;
		if (y === 11) return tone(PINK, 0.42 + (f === 'front' ? 0.12 : 0) + jit(x, y)); // sole edge
		if (y === 7) return tone(PINK, 0.82 - (f === 'back' ? 0.14 : 0) - (isInnerSide ? 0.1 : 0) + jit(x, y)); // boot cuff
		if (y >= 8) { // the boot: light pink-lilac, glossy toe, darker heel
			let v = lightV(f, x, y - 8, fw, 3, { gloss: 0.28, gc: 1.2, gs: 0.3 });
			if (y === 8) v -= 0.08;
			if (f === 'front' && y === 10) v += 0.08; // the toe cap
			if (f === 'back') v -= 0.08;
			return tone(LIL, v + 0.04 + jit(x, y));
		}
		if (y === 6) { // the knee: grey, a highlight on the cap
			const v = 0.46 + (f === 'front' && fromOuter >= 1 && fromOuter <= 2 ? 0.2 : 0) - (f === 'back' ? 0.12 : 0);
			return tone(GREY, v + jit(x, y, 0.06));
		}
		// rows 0-5: the thigh. The pink diagonal band crosses from the outer hip (y 0) to the inner thigh (y 1); below it
		// the thigh is lilac, with grey on the outer side and the back of the leg
		if (f === 'front') {
			if (y === 0) return fromOuter >= 2 ? tone(PINK, 0.62 - 0.06 * (fromOuter - 2) + jit(x, y)) : tone(GREY, 0.46 + jit(x, y));
			if (y === 1) return fromOuter === 1 ? tone(PINK, 0.7 + jit(x, y)) : fromOuter === 0 ? tone(GREY, 0.42 + jit(x, y)) : tone(LIL, 0.62 + jit(x, y));
			if (fromOuter === 0) return tone(GREY, 0.42 + 0.1 * (y === 2) + jit(x, y, 0.06));
			let v = 0.58 + 0.2 * Math.exp(-((fromOuter - 1.6) ** 2) / 0.6) - 0.06 * (y / 5);
			if (y === 5) v -= 0.12;
			return tone(LIL, v + jit(x, y));
		}
		if (isInnerSide) { // the inner thigh: lilac, in shade
			if (y === 0) return tone(PINK, 0.45 + jit(x, y));
			return tone(LIL, 0.42 - 0.05 * (y / 5) + (x === (f === 'left' ? 0 : 3) ? 0.1 : 0) + jit(x, y));
		}
		// the outer side and the back: grey satin, the pink band wrapping the outer hip on row 0
		let v = lightV(f, x, y, fw, 6, { gloss: 0.24, gc: isOuterSide ? 1.6 : 1.0, gs: 0.12 });
		if (isOuterSide && y === 0) return tone(PINK, 0.5 + jit(x, y));
		if (f === 'back' && y === 0) v -= 0.1; // under the seat
		if (f === 'back' && fromOuter === 3) v -= 0.08; // the inner seam
		return tone(GREY, v + jit(x, y, 0.06));
	};
}
// right leg (0,16): its outer side is the 'right' face, the outer front column x 0; left leg (16,48): 'left', x 3
paintBox(0, 16, 4, 12, 4, legFn('right', 0));
paintBox(16, 48, 4, 12, 4, legFn('left', 3));

// ================================================================ the overlay layer: raised cuffs, boot cuffs, pendant, belt knot
function overlayRows(ox, oy, w, h, d, rows, fn) {
	paintBox(ox, oy, w, h, d, (f, x, y, fw, fh) => (f !== 'top' && f !== 'bottom' && rows.includes(y)) ? fn(f, x, y) : null);
}
const cuffFn = (f, x, y) => tone(PINK, (y === 9 ? 0.95 : 0.62) - (f === 'back' ? 0.14 : 0) + (f === 'front' && x === 1 ? 0.06 : 0) + jit(x, y));
overlayRows(40, 32, 4, 12, 4, [9, 10], cuffFn);  // right sleeve
overlayRows(48, 48, 4, 12, 4, [9, 10], cuffFn);  // left sleeve
const bootCuff = (f, x, y) => tone(PINK, 0.95 - (f === 'back' ? 0.14 : 0) + jit(x, y));
overlayRows(0, 32, 4, 12, 4, [7], bootCuff);    // right pants
overlayRows(0, 48, 4, 12, 4, [7], bootCuff);    // left pants
// jacket layer: the pendant stands proud of the collar (a round charm: bright rim, pink centre texel)
paintBox(16, 32, 8, 12, 4, (f, x, y) => {
	if (f !== 'front') return null;
	if (y === 0 && x === 3) return SILVER[4];
	if (y === 0 && x === 4) return SILVER[3];
	if (y === 1 && x === 3) return PINK[3];
	if (y === 1 && x === 4) return SILVER[1];
	return null;
});

// ================================================================ write wide + slim + previews
const wide = { w: 64, h: 64, px: Buffer.from(img.px) };
const slim = T.convertArms(wide, true);
fs.writeFileSync(path.join(OUT, 'multimouse_suit_wide.png'), T.encode(64, 64, wide.px));
fs.writeFileSync(path.join(OUT, 'multimouse_suit_slim.png'), T.encode(64, 64, slim.px));
for (const [name, im] of [['wide', wide], ['slim', slim]]) {
	const p = T.preview(im, 10);
	fs.writeFileSync(path.join(OUT, 'multimouse_suit_' + name + '_x10.png'), T.encode(p.w, p.h, p.px));
}
console.log('multimouse suit written');
