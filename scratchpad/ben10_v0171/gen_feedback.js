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
