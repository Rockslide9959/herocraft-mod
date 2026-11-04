// v0.17.2 Wildvine textures (user: "Use this skin: https://www.planetminecraft.com/skin/wildvine-ben-10-4704618/ remove the pixel
// omnitrix from his skin model and replace it with our omnitrix badge please"):
//   textures/entity/omnitrix/wildvine.png   the skin with its painted Omnitrix painted out
//   wildvine/icon_src/wildvine.png          a PLACEHOLDER 222x222 dial icon source (grey disc, black rim, lime diamond, his black
//                                           silhouette) -- fed to scratchpad/gen_omnitrix_dial_icons.js (IDS=wildvine) for the
//                                           dial / HUD / font icons. Swap in the user's own art when they send it.
// Usage: node scratchpad/ben10_v0171/gen_wildvine.js   (then: IDS=wildvine node scratchpad/gen_omnitrix_dial_icons.js scratchpad/ben10_v0171/wildvine/icon_src)
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const src = dec(path.join(__dirname, 'wildvine', 'wildvine_src.png'));
const body = Buffer.from(src.px);
const set = (x, y, rgb) => {
	const o = (y * 64 + x) * 4;
	body[o] = (rgb >> 16) & 255;
	body[o + 1] = (rgb >> 8) & 255;
	body[o + 2] = rgb & 255;
	body[o + 3] = 255;
};
// The skin's Omnitrix: a 2x2 grey block in the middle of the top of his chest (base layer 23..24 x 21..22) under a grey / white
// diagonal on the outer layer (23..24 x 37..38). Both take the light green of the chest stripe they sit in, row by row (the pixels
// either side: 0x589D46 on the upper row, 0x599E47 on the lower); the mod's badge sits there instead.
for (const x of [23, 24]) {
	set(x, 21, 0x589d46);
	set(x, 22, 0x599e47);
	set(x, 37, 0x589d46);
	set(x, 38, 0x599e47);
}
// v0.17.2 (user: "can you add little seed bomb models to wildvines back please"): the flat dark pods the skin paints on his back
// (base layer, back face x32..39: 33..34 / 37..38 on rows 23..24, 34 / 37 on rows 25, 27..29 and 31; the jacket layer repeats them
// at 33..34 / 37..38 rows 39..40 and 34 / 37 rows 43..44, 46) go -- WildvineFx.backPods draws 3D pods there instead. Base pixels
// take the green beside them in the same row (x35); the jacket's pod pixels turn clear so the base shows through.
const dark = (x, y) => { const o = (y * 64 + x) * 4; return body[o + 3] > 10 && body[o] < 20 && body[o + 1] < 20 && body[o + 2] < 30; };
for (let y = 20; y <= 31; y++) {
	for (let x = 32; x <= 39; x++) {
		if (dark(x, y)) {
			const o = (y * 64 + 35) * 4;
			set(x, y, (body[o] << 16) | (body[o + 1] << 8) | body[o + 2]);
		}
	}
}
for (let y = 36; y <= 47; y++) {
	for (let x = 32; x <= 39; x++) {
		if (dark(x, y)) {
			body[(y * 64 + x) * 4 + 3] = 0;
		}
	}
}
fs.writeFileSync(path.join(RES, 'entity/omnitrix/wildvine.png'), L.encodePng(64, 64, body));

// ---------------------------------------------------------------- the placeholder dial icon source (222 x 222)
const N = 222, C = 111;
const px = Buffer.alloc(N * N * 4, 255);
const put = (x, y, r, g, b) => {
	const o = (y * N + x) * 4;
	px[o] = r; px[o + 1] = g; px[o + 2] = b; px[o + 3] = 255;
};
const BLACK = [0, 0, 0], GREY = [82, 82, 82], LIME = [140, 240, 58], RIM = [24, 24, 24];
// the silhouette: a bulb head with one eye, a thin stalk of a body, long curling vine arms, root legs
function inSilhouette(x, y) {
	const dx = x - C, dy = y - C;
	// the bulb head (a teardrop leaning back), centre (C, C-42)
	const hx = dx, hy = dy + 42;
	if ((hx * hx) / (20 * 20) + (hy * hy) / (24 * 24) <= 1) return true;
	if (hy < -16 && Math.abs(hx + (hy + 16) * 0.5) < 6 && hy > -40) return true; // the stalk-tip at the top
	// the body: a thin stalk widening at the chest
	if (dy > -20 && dy < 34 && Math.abs(dx) < 10 - (dy > 10 ? (dy - 10) * 0.15 : 0)) return true;
	// the legs: two roots splaying out
	for (const s of [-1, 1]) {
		const t = (dy - 30) / 44;
		if (t >= 0 && t <= 1 && Math.abs(dx - s * (6 + t * 26)) < 6 - t * 2) return true;
	}
	// the arms: vines from the shoulders arcing out and curling down
	for (const s of [-1, 1]) {
		for (let k = 0; k <= 60; k++) {
			const a = k / 60;
			const ax = s * (8 + a * 58), ay = -14 - Math.sin(a * Math.PI) * 18 + a * a * 40;
			if ((dx - ax) ** 2 + (dy - ay) ** 2 < (5.5 - a * 2.5) ** 2) return true;
		}
		// the curl at the end
		const cx = s * 62, cy = 22;
		const d = Math.hypot(dx - cx, dy - cy);
		if (d > 6 && d < 10 && (dy - cy) * 1 + (dx - cx) * s * 0.2 > -6) return true;
	}
	return false;
}
function inEye(x, y) {
	return Math.hypot(x - C, y - (C - 44)) < 7;
}
for (let y = 0; y < N; y++) {
	for (let x = 0; x < N; x++) {
		const d = Math.hypot(x + 0.5 - C, y + 0.5 - C);
		if (d > 108) continue; // white outside: the dial script cuts it away
		if (d > 100) { put(x, y, ...RIM); continue; }
		const dia = Math.abs(x + 0.5 - C) + Math.abs(y + 0.5 - C);
		let c = GREY;
		if (dia < 97) c = dia > 91 ? BLACK : LIME;
		if (inSilhouette(x, y)) c = inEye(x, y) ? LIME : BLACK;
		put(x, y, ...c);
	}
}
const out = path.join(__dirname, 'wildvine', 'icon_src');
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'wildvine.png'), L.encodePng(N, N, px));
console.log('wildvine textures written');
