// v0.17.2 Recalibrated Omnitrix: Big Chill (Necrofriggian) -- the basic body. A 64x64 WIDE skin (deep blue, the pale-cyan
// chest and inner limbs, the big green eyes) and an extras sheet whose rectangles BigChillClient maps onto AlienParts.box cubes:
//   WING 0,0 32x32 (outer: blue-black with pale veins, see-through edge) | ANTENNA 32,0 2x8 | TIP 34,0 4x4 | EYE 40,0 4x4 (glow)
//   CLOAK 0,32 16x16 (the wing's inside, pale blue)
// Usage: node scratchpad/recal_v0172/big_chill/gen_big_chill.js [previewDir]
'use strict';
const L = require('../lib_recal_b.js');
const { img, hex, hash, shade, mixc, rect, save, paintBox, SKIN, tone } = L;
const PREVIEW = process.argv[2];

const BLUE = ['#0a1236', '#14215a', '#1f3484', '#2f4aa8', '#4a68c8'].map(hex);
const CYAN = ['#4a8ab8', '#6fb4e0', '#9ad6f6', '#d4f2ff'].map(hex);
const EYE = hex('#5aff6a');

const im = img(64, 64);
paintBox(im, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		// the big slanted green eyes
		if ((v === 2 && (u === 1 || u === 2 || u === 5 || u === 6)) || (v === 3 && (u === 2 || u === 5))) return v === 2 && (u === 1 || u === 6) ? hex('#c8ffd0') : EYE;
		if (v === 6 && u >= 3 && u <= 4) return BLUE[0]; // the small mouth
		if (v >= 4) return tone(CYAN, u, v, w, h, 3, 'side'); // the pale lower face
	}
	if (f === 'top') return tone(BLUE, u, v, w, h, 4, f);
	return tone(BLUE, u, v, w, h, 5 + f.length, f);
});
paintBox(im, SKIN.body, (f, u, v, w, h) => {
	if (f === 'front') {
		if (u >= 2 && u <= 5) return v % 3 === 2 ? CYAN[1] : tone(CYAN, u, v, w, h, 9, 'front'); // the pale ribbed chest
	}
	if (f === 'back' && (u === 3 || u === 4)) return BLUE[1];
	return tone(BLUE, u, v, w, h, 11 + f.length, f);
});
for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 2 && f !== 'top') return CYAN[0]; // the three-fingered hands, pale
	if (f === 'front') return tone(CYAN, u, v, w, h, 14, 'side');
	return tone(BLUE, u, v, w, h, 15 + k.length, f);
});
for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	// the legs fade to a ghostly pale blue at the feet
	const base = tone(BLUE, u, v, w, h, 19 + k.length, f);
	return mixc(base, CYAN[2], Math.max(0, (v - 5) / 9));
});
save(im, 'entity/omnitrix/big_chill.png', PREVIEW);

const ex = img(64, 64);
// WING: a moth's -- dark blue-black, pale cyan veins fanning from the root (u = 0, v = 0), a scalloped see-through rim
rect(ex, 0, 0, 32, 32, (u, v) => {
	const r = Math.hypot(u, v);
	const ang = Math.atan2(v, u);
	const vein = Math.abs(Math.sin(ang * 6)) < 0.12 && r > 3;
	const rim = r > 40 - 2 * Math.abs(Math.sin(ang * 5)) || u === 31 || v === 31;
	if (r > 43) return null;
	if (rim) return [...CYAN[2], 140];
	if (vein) return [...CYAN[1], 235];
	const c = shade(mixc(BLUE[2], BLUE[0], r / 40), 0.9 + hash(u, v, 61) * 0.15);
	return [...c, 245];
});
rect(ex, 32, 0, 2, 8, (u, v) => u === 0 ? BLUE[1] : BLUE[2]); // ANTENNA
rect(ex, 34, 0, 4, 4, (u, v) => mixc(CYAN[3], CYAN[1], (u + v) / 6)); // TIP
rect(ex, 40, 0, 4, 4, (u, v) => (u + v) % 3 === 0 ? hex('#d8ffe0') : EYE); // EYE glow
rect(ex, 0, 32, 16, 16, (u, v) => shade(mixc(CYAN[2], CYAN[0], v / 16), 0.92 + hash(u, v, 71) * 0.12)); // CLOAK
save(ex, 'entity/omnitrix/big_chill_extras.png', PREVIEW);
