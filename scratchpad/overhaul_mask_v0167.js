// v0.16.7 (user): Overhaul's own plague-doctor beak mask, painted onto the head of the cut Chisaki costume
// (textures/entity/quirk/overhaul_wide.png / _slim.png). The eyes and forehead stay the wearer's own; the mask covers
// the nose and mouth with a long bone-white beak (a lighter raised beak on the hat layer for depth) held by a dark
// purple strap round the sides and the back of the head.
// Usage: node scratchpad/overhaul_mask_v0167.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('./suits/skin_tools.js');
const DIR = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/entity/quirk');

const C = {
	edge: [70, 62, 60], shadow: [168, 160, 150], base: [214, 208, 196], light: [236, 232, 222], hi: [250, 248, 242],
	beakDark: [150, 142, 132], rivet: [92, 84, 80], strap: [52, 34, 58], strapHi: [80, 56, 88], nostril: [40, 34, 36],
};
// the front face, 8x8 (rows 0-3 left clear for the wearer's eyes / brows)
const FRONT = [
	'........',
	'........',
	'........',
	'........',
	'eSbbbbSe',
	'SbbLLbbS',
	'.SbLHbS.',
	'..sNNs..',
];
// the raised beak on the hat layer (just the middle, a shade lighter, so the beak reads as sticking out)
const HAT_FRONT = [
	'........',
	'........',
	'........',
	'........',
	'..r..r..',
	'...LH...',
	'...LH...',
	'...lh...',
];
const KEY = { e: C.edge, S: C.shadow, b: C.base, L: C.light, H: C.hi, s: C.beakDark, N: C.nostril, r: C.rivet, l: C.base, h: C.light };

function paint(img, ox, oy, rows) {
	rows.forEach((row, y) => [...row].forEach((ch, x) => {
		if (ch === '.') return;
		const c = KEY[ch], i = ((oy + y) * 64 + ox + x) * 4;
		img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = 255;
	}));
}
function set(img, x, y, c) {
	const i = (y * 64 + x) * 4;
	img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = 255;
}

for (const f of ['overhaul_wide.png', 'overhaul_slim.png']) {
	const img = T.decode(path.join(DIR, f));
	// the whole head (base + hat) clear first: nothing of the source skin's head stays
	for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) img.px[(y * 64 + x) * 4 + 3] = 0;
	paint(img, 8, 8, FRONT);
	paint(img, 40, 8, HAT_FRONT);
	// the mask's cheeks wrap a pixel round each side, then the strap runs round the sides and back (head rows 12-13)
	for (let k = 0; k < 2; k++) {
		set(img, 7, 12 + k, C.shadow);   // right side, front edge
		set(img, 16, 12 + k, C.shadow);  // left side, front edge
	}
	for (let x = 0; x < 7; x++) { set(img, x, 12, C.strap); set(img, x, 13, C.strapHi); }
	for (let x = 17; x < 24; x++) { set(img, x, 12, C.strap); set(img, x, 13, C.strapHi); }
	for (let x = 24; x < 32; x++) { set(img, x, 12, C.strap); set(img, x, 13, C.strapHi); }
	// the buckle at the back of the head
	set(img, 27, 12, C.rivet); set(img, 28, 12, C.rivet); set(img, 27, 13, C.beakDark); set(img, 28, 13, C.beakDark);
	// under the chin: the beak's underside on the head's bottom face (front row of the bottom face is its top row)
	for (let x = 18; x < 22; x++) set(img, x, 0, C.shadow);
	set(img, 19, 1, C.beakDark); set(img, 20, 1, C.beakDark);
	fs.writeFileSync(path.join(DIR, f), T.encode(64, 64, img.px));
	const p = T.preview(img, 10);
	fs.writeFileSync(path.join(__dirname, 'user_skins_v0167', f.replace('.png', '_mask_x10.png')), T.encode(p.w, p.h, p.px));
}
console.log('mask painted');
