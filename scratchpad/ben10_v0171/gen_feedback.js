// v0.17.2 Feedback textures (user: "build a feedback alien please ... use this skin:
// https://www.planetminecraft.com/skin/feedback-ben-10-5251468/"):
//   textures/entity/omnitrix/feedback.png        the skin, its painted chest Omnitrix painted out (the mod's badge sits there)
//   textures/entity/omnitrix/feedback_glow.png   the emissive pass (FeedbackClient draws it full-bright): his one green eye
// The skin paints the Omnitrix as a little hourglass in the middle of the white chest panel: jacket texels 23..24 x 37..39 (a dark
// top pair, then green / dark-green) over a dark pair on the body layer (23..24 x 22..23). Those take the panel's own whites
// (the brighter e1e5e9 rows above, the shaded b0b7be row the panel uses on that line). FeedbackClient's badge sits there.
// The cables, plugs and gold finger / toe caps are drawn as geometry (FeedbackCables), not painted.
// Source: feedback_dl/feedback_skin.png (downloaded through the built-in browser -- planetminecraft blocks curl).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const src = dec(path.join(__dirname, 'feedback_dl', 'feedback_skin.png'));
const px = Buffer.from(src.px);
const at = (x, y) => (y * 64 + x) * 4;
const put = (b, x, y, hex, a = 255) => {
	const i = at(x, y);
	b[i] = (hex >> 16) & 255;
	b[i + 1] = (hex >> 8) & 255;
	b[i + 2] = hex & 255;
	b[i + 3] = a;
};
// jacket layer: the hourglass -> the panel's white (rows 37, 38 bright like x21/x22 beside them, row 39 the shaded line)
for (const x of [23, 24]) {
	put(px, x, 37, 0xe1e5e9);
	put(px, x, 38, 0xe1e5e9);
	put(px, x, 39, 0xb0b7be);
	// body layer under it
	put(px, x, 22, 0xe1e5e9);
	put(px, x, 23, 0xb0b7be);
}
// v0.17.2 round 2 (user: "since hes got the ear models remove the ear plugs from his back form his skin model please. Also remove the tail
// plug from his leg from his skin model please"): the painted gold plugs on his back (body 33..38 x 21..23) and on his right leg's upper
// back / side (14..15 x 24..26, 0 x 26) take the black round them; the same spots on the jacket / trouser overlay go clear.
const GOLD = new Set(['76612f', 'ac8e3f', 'c5a75a', 'b18e36']);
const isGold = (x, y) => {
	const i = at(x, y);
	return px[i + 3] > 10 && GOLD.has(px.slice(i, i + 3).toString('hex'));
};
/** The nearest non-gold texel in the same row, searching within [x0, x1). */
const fill = (x, y, x0, x1) => {
	for (let d = 1; d < 8; d++) {
		for (const nx of [x - d, x + d]) {
			if (nx >= x0 && nx < x1 && !isGold(nx, y)) {
				const i = at(nx, y);
				return (px[i] << 16) | (px[i + 1] << 8) | px[i + 2];
			}
		}
	}
	return 0x0e0f10;
};
const paintOff = (x0, y0, x1, y1, overlay) => {
	const hits = [];
	for (let y = y0; y < y1; y++) {
		for (let x = x0; x < x1; x++) {
			if (isGold(x, y)) hits.push([x, y]);
		}
	}
	for (const [x, y] of hits) {
		if (overlay) put(px, x, y, 0, 0);
		else put(px, x, y, fill(x, y, x0, x1));
	}
	return hits.length;
};
let off = 0;
off += paintOff(32, 20, 40, 32, false); // body back
off += paintOff(32, 36, 40, 48, true); // jacket back
off += paintOff(12, 20, 16, 32, false); // right leg back
off += paintOff(0, 20, 4, 32, false); // right leg outer side
off += paintOff(12, 36, 16, 48, true); // trousers back
off += paintOff(0, 36, 4, 48, true); // trousers outer side
// the plugs' silver middles on his back (c7cbd0, rows 22..23) go too, to the row's own black either side
for (let y = 21; y <= 23; y++) {
	for (let x = 33; x <= 38; x++) {
		const i = at(x, y);
		const k = px.slice(i, i + 3).toString('hex');
		if (k === 'c7cbd0' || y === 21) {
			put(px, x, y, y === 23 ? 0x23272b : 0x33393f);
			off++;
		}
	}
}
console.log('painted-off plug texels: ' + off);
fs.writeFileSync(path.join(RES, 'entity/omnitrix/feedback.png'), L.encodePng(64, 64, px));

// ---- the glow pass: only the green eye texels, everything else transparent black
const g = Buffer.alloc(64 * 64 * 4);
let lit = 0;
for (let y = 0; y < 16; y++) { // the head and hat only
	for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (px[i + 3] < 10) continue;
		const r = px[i], gr = px[i + 1], b = px[i + 2];
		if (gr > 180 && gr - r > 40 && gr - b > 60) {
			g[i] = r;
			g[i + 1] = gr;
			g[i + 2] = b;
			g[i + 3] = 255;
			lit++;
		}
	}
}
fs.writeFileSync(path.join(RES, 'entity/omnitrix/feedback_glow.png'), L.encodePng(64, 64, g));
console.log('feedback textures written, glow texels: ' + lit);
