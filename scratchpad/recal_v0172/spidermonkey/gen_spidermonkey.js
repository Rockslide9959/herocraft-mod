// v0.17.2 Recalibrated Omnitrix: Spidermonkey's basic body -- a 64x64 WIDE player skin (dark blue fur, black stripes and hands,
// a pale blue face with four green eyes) and the extras sheet for his second pair of arms and his tail.
// extras rectangles (mirrored in SpidermonkeyClient): FUR 0,0 16x16 | DARK 16,0 16x16 | SPINNERET 32,0 8x8 | FACE 40,0 8x8
// Usage: node scratchpad/recal_v0172/spidermonkey/gen_spidermonkey.js [previewDir]
'use strict';
const L = require('../recal_c_lib.js');
const { img, hex, hash, shade, mixc, rect, paintBox, SKIN, tone, save } = L;
const PREVIEW = process.argv[2];

const FUR = ['#121a40', '#1c2860', '#26388a', '#3450b0', '#4a6ad0'].map(hex);
const BLACK = ['#07080c', '#101218', '#1a1d26', '#262a36'].map(hex);
const FACE = ['#5a6aa8', '#7888c4', '#98a8dc', '#b8c4ec'].map(hex);
const EYE = hex('#b8f040'), EYE_D = hex('#4a8a10');

function fur(ramp, u, v, w, h, seed, f) {
	const c = tone(ramp, u, v, w, h, seed, f);
	return hash(u, v, seed + 7) > 0.86 ? shade(c, 1.18) : c; // a few lighter hairs
}

const skin = img(64, 64);
paintBox(skin, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		// the pale face mask, four eyes (two big, two small above), a dark mouth
		if (v >= 2 && v <= 7 && u >= 1 && u <= 6) {
			if (v === 3 && (u === 1 || u === 2 || u === 5 || u === 6)) return u === 2 || u === 5 ? hex('#efffd0') : EYE;
			if (v === 4 && (u === 1 || u === 2 || u === 5 || u === 6)) return EYE_D;
			if (v === 2 && (u === 2 || u === 5)) return EYE;
			if (v === 6 && u >= 3 && u <= 4) return BLACK[0];
			return tone(FACE, u, v, w, h, 3, f);
		}
		return fur(FUR, u, v, w, h, 5, f);
	}
	if (f === 'top' && (u === 3 || u === 4)) return BLACK[1]; // a black crest down the head
	return fur(FUR, u, v, w, h, 7 + f.length, f);
});
paintBox(skin, SKIN.body, (f, u, v, w, h) => {
	if (f === 'front') {
		if (u >= 2 && u <= 5 && v >= 6) return tone(FACE, u, v, w, h, 9, f); // a paler belly
		if (v >= 10 || u === 0 || u === 7) return BLACK[2];
		return fur(FUR, u, v, w, h, 11, f);
	}
	if (f === 'back' && (u === 3 || u === 4 || v % 4 === 1)) return BLACK[1]; // black stripes over the back
	return fur(FUR, u, v, w, h, 13 + f.length, f);
});
for (const k of ['rArm', 'lArm']) paintBox(skin, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 4 && f !== 'top') return tone(BLACK, u, v, w, h, 17, f); // black hands
	if (v === 4 && f !== 'top' && f !== 'bottom') return BLACK[1];
	return fur(FUR, u, v, w, h, 19 + k.length, f);
});
for (const k of ['rLeg', 'lLeg']) paintBox(skin, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 3 && f !== 'top') return tone(BLACK, u, v, w, h, 23, f); // black hand-like feet
	if (v === 5 && f !== 'top' && f !== 'bottom') return BLACK[1];
	return fur(FUR, u, v, w, h, 29 + k.length, f);
});
save(skin, 'entity/omnitrix/spidermonkey.png', PREVIEW);

const ex = img(64, 64);
rect(ex, 0, 0, 16, 16, (u, v) => fur(FUR, u, v, 16, 16, 31, 'front'));
rect(ex, 16, 0, 16, 16, (u, v) => shade(BLACK[2], 0.85 + hash(u, v, 37) * 0.3));
rect(ex, 32, 0, 8, 8, (u, v) => mixc(hex('#c8ccd8'), hex('#f4f6ff'), v / 8)); // the tail's spinneret
rect(ex, 40, 0, 8, 8, (u, v) => tone(FACE, u, v, 8, 8, 41, 'front'));
save(ex, 'entity/omnitrix/spidermonkey_extras.png', PREVIEW);
