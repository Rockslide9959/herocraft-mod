// v0.17.1 (user: "make all the omnitrix badges white for when they show on the player in alien form ... thats how it is in the
// show"): the alien chest badge -- a white Omnitrix symbol: a dark rim, a white ring, a black field and a white hourglass,
// softly shaded like the watch face. 64x64 -> textures/entity/omnitrix/badge.png (OmnitrixLayer; the red beep / timeout flash
// still reads the red watch face, watch_red.png).
// Usage: node scratchpad/gen_omnitrix_badge.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix', 'badge.png');
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const mixc = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * Math.max(0, Math.min(1, t))));

const W = 64, SS = 4;
const px = Buffer.alloc(W * W * 4);
const RIM = hex('#141517'), WHITE = hex('#f4f6f7'), GREY = hex('#b9bec2'), HI = hex('#ffffff'), FIELD = hex('#111214'), FIELD_EDGE = hex('#050506');

/** The colour (rgba) at a sub-pixel point (dx, dy from the centre, 32 = the edge). */
function sample(dx, dy) {
	const r = Math.hypot(dx, dy);
	if (r > 31.6) return null;
	if (r > 30.0) return RIM;
	if (r > 26.2) {
		// the white ring: lit top-left, a little grey bottom-right
		const t = (dx + dy) / 60 + 0.5;
		let c = mixc(WHITE, GREY, t * 0.8);
		if (r > 29.4 || r < 26.8) c = mixc(c, GREY, 0.6);
		return c;
	}
	if (r > 25.0) return RIM;
	const ax = Math.abs(dx), ay = Math.abs(dy);
	const w = 3.0, k = 1.12, edge = w + ay / k;
	if (ax <= edge && r < 23.0) {
		// the white hourglass, a soft gloss on its upper-left
		let c = mixc(WHITE, GREY, Math.max(0, r / 23 - 0.6) * 1.4);
		if (dx + dy * 0.6 < -8 && ay > 7 && ax < edge - 2 && r < 20) c = mixc(c, HI, 0.7);
		return c;
	}
	return mixc(FIELD, FIELD_EDGE, r / 25);
}

for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
	let rr = 0, gg = 0, bb = 0, n = 0;
	for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
		const c = sample(x + (sx + 0.5) / SS - 32, y + (sy + 0.5) / SS - 32);
		if (!c) continue;
		rr += c[0]; gg += c[1]; bb += c[2]; n++;
	}
	const i = (y * W + x) * 4;
	if (n === 0) continue;
	px[i] = Math.round(rr / n); px[i + 1] = Math.round(gg / n); px[i + 2] = Math.round(bb / n);
	px[i + 3] = Math.round(255 * n / (SS * SS));
}
PNG.write(OUT, W, W, px);
if (process.argv[2]) {
	const s = 8, P = Buffer.alloc(W * s * W * s * 4);
	for (let y = 0; y < W * s; y++) for (let x = 0; x < W * s; x++) {
		const j = (Math.floor(y / s) * W + Math.floor(x / s)) * 4, o = (y * W * s + x) * 4, a = px[j + 3] / 255;
		const chk = ((Math.floor(x / 16) + Math.floor(y / 16)) % 2) ? 90 : 60;
		for (let k = 0; k < 3; k++) P[o + k] = Math.round(px[j + k] * a + chk * (1 - a));
		P[o + 3] = 255;
	}
	fs.mkdirSync(process.argv[2], { recursive: true });
	PNG.write(path.join(process.argv[2], 'badge_preview.png'), W * s, W * s, P);
}
console.log('badge written');
