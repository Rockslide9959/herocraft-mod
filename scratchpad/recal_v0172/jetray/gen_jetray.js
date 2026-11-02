// v0.17.2 Recalibrated Omnitrix: Jetray (Aerophibian) -- the basic body. A 64x64 WIDE skin (crimson back, black underside and
// face plate, the yellow-green eyes) and an extras sheet whose rectangles JetrayClient maps onto AlienParts.box cubes:
//   WING 0,0 32x16 | TAIL 32,0 4x16 | STINGER 36,0 4x4 | FIN 40,0 8x8 | EYE 48,0 4x4 (glow) | UNDER 0,16 16x8
// Usage: node scratchpad/recal_v0172/jetray/gen_jetray.js [previewDir]
'use strict';
const L = require('../lib_recal_b.js');
const { img, hex, hash, shade, mixc, rect, save, paintBox, SKIN, tone } = L;
const PREVIEW = process.argv[2];

const RED = ['#5a0d18', '#8e1a2a', '#c42a3e', '#e04a5a', '#f27a86'].map(hex);
const BLK = ['#08090b', '#121418', '#1c2026', '#2a3038'].map(hex);
const EYE = hex('#d8ff3a');
const EYE_D = hex('#7ac81a');

const im = img(64, 64);
paintBox(im, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		if (v >= 2 && v <= 3 && (u === 1 || u === 2)) return v === 2 && u === 1 ? hex('#f6ffc0') : EYE; // the eyes, wide set
		if (v >= 2 && v <= 3 && (u === 5 || u === 6)) return v === 2 && u === 6 ? hex('#f6ffc0') : EYE;
		if (v === 4 && (u === 1 || u === 2 || u === 5 || u === 6)) return EYE_D;
		if (v >= 5) return v === 6 && u >= 2 && u <= 5 ? BLK[0] : tone(BLK, u, v, w, h, 3, 'front'); // the black jaw plate
		if (u === 3 || u === 4) return RED[3]; // the brow ridge
	}
	if (f === 'top') return (u === 3 || u === 4) ? RED[1] : tone(RED, u, v, w, h, 4, f);
	if (f === 'bottom') return tone(BLK, u, v, w, h, 5, 'front');
	if ((f === 'right' || f === 'left') && v >= 5) return tone(BLK, u, v, w, h, 6, 'front');
	return tone(RED, u, v, w, h, 7 + f.length, f);
});
// the hat layer: blank (the head fin is an extras cube)
paintBox(im, SKIN.body, (f, u, v, w, h) => {
	if (f === 'front') {
		if (u >= 2 && u <= 5 && v >= 2) return tone(BLK, u, v, w, h, 9, 'front'); // the black belly plate
		if ((u === 1 || u === 6) && v >= 1) return RED[1];
	}
	if (f === 'back') {
		if (u === 3 || u === 4) return v % 3 === 0 ? BLK[2] : RED[1]; // the spine ridge
	}
	if (f === 'bottom') return BLK[1];
	return tone(RED, u, v, w, h, 11 + f.length, f);
});
for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 3 && f !== 'top') return tone(BLK, u, v, w, h, 13, 'front'); // black hands
	if (f === 'front' && v >= 4) return tone(BLK, u, v, w, h, 14, 'side'); // dark inner forearm
	if (f === 'bottom') return BLK[1];
	return tone(RED, u, v, w, h, 15 + k.length, f);
});
for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 2 && f !== 'top') return BLK[1]; // feet
	if (f === 'front' && (u === 1 || u === 2) && v >= 2) return tone(BLK, u, v, w, h, 17, 'front'); // the black stripe
	if (f === 'bottom') return BLK[1];
	return tone(RED, u, v, w, h, 19 + k.length, f);
});
save(im, 'entity/omnitrix/jetray.png', PREVIEW);

const ex = img(64, 64);
// WING: crimson with a black leading edge (v small), dark tips (u large) and two pale-yellow spots
rect(ex, 0, 0, 32, 16, (u, v) => {
	if (v <= 1) return BLK[1 + (u % 3 === 0 ? 1 : 0)];
	if (u >= 27) return mixc(RED[1], BLK[0], (u - 26) / 5);
	const spot = Math.hypot(u - 12, v - 7) < 1.6 || Math.hypot(u - 19, v - 9) < 1.2;
	if (spot) return hex('#f0e070');
	if (v >= 14) return RED[1];
	return shade(mixc(RED[3], RED[2], u / 32), 0.92 + hash(u, v, 31) * 0.14);
});
rect(ex, 32, 0, 4, 16, (u, v) => (v % 4 === 3) ? BLK[2] : u === 0 || u === 3 ? RED[1] : RED[2]); // TAIL
rect(ex, 36, 0, 4, 4, (u, v) => mixc(EYE, EYE_D, v / 4)); // STINGER (lit)
rect(ex, 40, 0, 8, 8, (u, v) => v === 0 ? BLK[2] : shade(RED[2], 0.9 + hash(u, v, 41) * 0.15)); // FIN
rect(ex, 48, 0, 4, 4, (u, v) => (u + v) % 3 === 0 ? hex('#f8ffd0') : EYE); // EYE glow
rect(ex, 0, 16, 16, 8, (u, v) => tone(BLK, u, v, 16, 8, 51, 'front')); // UNDER
save(ex, 'entity/omnitrix/jetray_extras.png', PREVIEW);
