// v0.17.2 Recalibrated Omnitrix: Goop's basic body -- a 64x64 WIDE player skin of glossy acid-green slime (drawn see-through by
// bodyAlpha), darker green deep inside, pale highlights and bubbles, two small dark eyes, and the extras sheet for his
// anti-gravity projector (the silver saucer with its green light) and his slime drips.
// extras rectangles (mirrored in GoopClient): METAL 0,0 16x16 | LIGHT 16,0 8x8 | SLIME 32,0 16x16 | RIM 24,0 8x8
// Usage: node scratchpad/recal_v0172/goop/gen_goop.js [previewDir]
'use strict';
const L = require('../recal_c_lib.js');
const { img, hex, hash, shade, mixc, rect, paintBox, SKIN, tone, save } = L;
const PREVIEW = process.argv[2];

const SLIME = ['#1e6a10', '#2e9a1a', '#4cc42a', '#7ce03a', '#b0f070'].map(hex);

function goo(u, v, w, h, seed, f) {
	const c = tone(SLIME, u, v, w, h, seed, f);
	const n = hash(u, v, seed + 3);
	if (n > 0.93) return hex('#d8ffb0'); // a bubble
	if (n < 0.06) return shade(SLIME[0], 1.1); // something darker deep inside
	// a soft wavy sheen across the face
	const s = Math.sin((u + v * 0.6 + seed) * 0.9);
	return s > 0.8 ? mixc(c, SLIME[4], 0.35) : c;
}

const skin = img(64, 64);
paintBox(skin, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		if (v === 3 && (u === 2 || u === 5)) return hex('#0c2a06'); // two small dark eyes
		if (v === 4 && (u === 2 || u === 5)) return hex('#164a0c');
		if (v === 6 && u >= 3 && u <= 4) return SLIME[1];
	}
	return goo(u, v, w, h, 3 + f.length, f);
});
paintBox(skin, SKIN.body, (f, u, v, w, h) => goo(u, v, w, h, 11 + f.length, f));
for (const k of ['rArm', 'lArm', 'rLeg', 'lLeg']) paintBox(skin, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 2 && f !== 'top') return mixc(SLIME[2], SLIME[3], hash(u, v, 5)); // drippy ends
	return goo(u, v, w, h, 19 + k.length, f);
});
save(skin, 'entity/omnitrix/goop.png', PREVIEW);

const ex = img(64, 64);
// METAL: the projector's brushed silver, darker bands
rect(ex, 0, 0, 16, 16, (u, v) => {
	if (v === 0 || v === 15 || u === 0 || u === 15) return hex('#5a6068');
	if (v % 5 === 2) return hex('#7a828c');
	return shade(hex('#b8c0c8'), 0.92 + hash(u, v, 31) * 0.12);
});
// RIM: a darker trim
rect(ex, 24, 0, 8, 8, (u, v) => shade(hex('#4a5058'), 0.9 + hash(u, v, 33) * 0.2));
// LIGHT: the anti-gravity glow
rect(ex, 16, 0, 8, 8, (u, v) => {
	const d = Math.hypot(u - 3.5, v - 3.5) / 5;
	return mixc(hex('#f0ffd0'), hex('#5ae028'), Math.min(1, d));
});
// SLIME: drips
rect(ex, 32, 0, 16, 16, (u, v) => goo(u, v, 16, 16, 41, 'front'));
save(ex, 'entity/omnitrix/goop_extras.png', PREVIEW);
