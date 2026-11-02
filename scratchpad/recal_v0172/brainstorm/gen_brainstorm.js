// v0.17.2 Recalibrated Omnitrix: Brainstorm's basic body -- a 64x64 WIDE player skin (orange-brown carapace, tan underside,
// green eyes, brown legs) and the extras sheet the client draws his shell, brain, claws and small side legs from.
// extras rectangles (mirrored in BrainstormClient): SHELL 0,0 16x16 | BRAIN 16,0 16x16 | CLAW 32,0 16x16 | LEG 48,0 8x8 |
//                                                   EYE 56,0 8x8 | SHELL_IN 0,16 16x16
// Usage: node scratchpad/recal_v0172/brainstorm/gen_brainstorm.js [previewDir]
'use strict';
const L = require('../recal_c_lib.js');
const { img, hex, hash, shade, mixc, rect, paintBox, SKIN, tone, save } = L;
const PREVIEW = process.argv[2];

const SHELL = ['#5a2410', '#8a3a18', '#b8582a', '#d8783a', '#eca060'].map(hex);
const STRIPE = hex('#4a1c0c');
const TAN = ['#8a6a48', '#b8946a', '#dcbc90', '#f0d8b0'].map(hex);
const BROWN = ['#3a2010', '#5a3418', '#7a4a24', '#9a6434'].map(hex);
const GREEN = hex('#7cf04a'), GREEN_D = hex('#2e8a1a');

const skin = img(64, 64);
// head: a tan face with big green eyes, a mandible mouth; the top / back / sides are carapace
paintBox(skin, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		if (v <= 1) return shade(SHELL[2], 0.95 + hash(u, v, 1) * 0.1); // the shell's brow
		if (v >= 2 && v <= 4 && (u === 1 || u === 2 || u === 5 || u === 6)) return v === 2 && (u === 2 || u === 5) ? hex('#d8ffcf') : (v === 4 ? GREEN_D : GREEN);
		if (v === 6 && u >= 2 && u <= 5) return hex('#3a1a0c'); // mandible slit
		if (v === 7 && (u === 2 || u === 5)) return TAN[0];
		return tone(TAN, u, v, w, h, 3, f);
	}
	if (f === 'bottom') return TAN[1];
	if ((f === 'top' && (u === 3 || u === 4)) || (f === 'back' && (u === 3 || u === 4))) return STRIPE; // the seam where the shell opens
	return tone(SHELL, u, v, w, h, 5 + f.length, f);
});
// body: carapace back and shoulders, a tan segmented belly
paintBox(skin, SKIN.body, (f, u, v, w, h) => {
	if (f === 'front') {
		if (u >= 1 && u <= 6 && v >= 2) return v % 3 === 0 ? TAN[1] : tone(TAN, u, v, w, h, 9, f);
		return tone(SHELL, u, v, w, h, 11, f);
	}
	if (f === 'back' && (v % 4 === 0 || u === 3 || u === 4)) return STRIPE;
	return tone(SHELL, u, v, w, h, 13 + f.length, f);
});
// arms: carapace with a darker joint and the claw's root at the hand
for (const k of ['rArm', 'lArm']) paintBox(skin, SKIN[k], (f, u, v, w, h) => {
	if (v === 5 && f !== 'top' && f !== 'bottom') return STRIPE;
	if (v >= 9 && f !== 'top') return tone(SHELL, u, v, w, h, 17, f);
	return tone(SHELL, u, v, w, h, 19 + k.length, f);
});
// legs: thin brown crab legs with dark joints and pale tips
for (const k of ['rLeg', 'lLeg']) paintBox(skin, SKIN[k], (f, u, v, w, h) => {
	if (v % 4 === 3 && f !== 'top' && f !== 'bottom') return BROWN[0];
	if (v >= h - 2 && f !== 'top') return TAN[2];
	return tone(BROWN, u, v, w, h, 23 + k.length, f);
});
save(skin, 'entity/omnitrix/brainstorm.png', PREVIEW);

const ex = img(64, 64);
// SHELL: the carapace -- orange with dark stripes and pale speckles
rect(ex, 0, 0, 16, 16, (u, v) => {
	if (v % 5 === 2 || u === 7 || u === 8) return STRIPE;
	const n = hash(u, v, 31);
	return n > 0.92 ? SHELL[4] : shade(SHELL[2 + (v < 3 ? 1 : 0)], 0.9 + n * 0.2);
});
// SHELL_IN: the inside of the shell -- dark, wet
rect(ex, 0, 16, 16, 16, (u, v) => shade(hex('#5a1e28'), 0.8 + hash(u, v, 37) * 0.3));
// BRAIN: pink folds
rect(ex, 16, 0, 16, 16, (u, v) => {
	const fold = Math.sin(u * 1.3 + Math.sin(v * 0.9) * 2.2) + Math.sin(v * 1.1 + u * 0.4);
	const base = fold > 0.6 ? hex('#f8b8d0') : fold < -0.6 ? hex('#c05a84') : hex('#e88ab0');
	return shade(base, 0.92 + hash(u, v, 41) * 0.12);
});
// CLAW: big pincers, the shell's orange to a pale tip
rect(ex, 32, 0, 16, 16, (u, v) => {
	if (v >= 12) return mixc(SHELL[3], TAN[3], (v - 12) / 3);
	if (u === 0 || u === 15) return SHELL[1];
	return shade(SHELL[2], 0.9 + hash(u, v, 43) * 0.18);
});
// LEG: brown
rect(ex, 48, 0, 8, 8, (u, v) => (v % 4 === 3 ? BROWN[0] : shade(BROWN[2], 0.9 + hash(u, v, 47) * 0.2)));
// EYE: green
rect(ex, 56, 0, 8, 8, (u, v) => ((u === 2 && v === 2) ? hex('#e8ffe0') : mixc(GREEN, GREEN_D, v / 8)));
save(ex, 'entity/omnitrix/brainstorm_extras.png', PREVIEW);
