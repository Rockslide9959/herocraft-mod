// v0.17.2 Ditto textures (user: "also create a moveset for ditto please. Use this skin:
// https://www.planetminecraft.com/skin/ditto-ben-10-5303343/ remove the pixel omnitrix from his skin model and replace it with our
// omnitrix badge please"):
//   textures/entity/omnitrix/ditto.png   the skin, its painted Omnitrix painted out
//   ditto_dl/dial_src/ditto.png          a 222x222 placeholder dial source (grey disc, black rim, lime hourglass, Ditto's black
//                                        silhouette with his head fin) -- run scratchpad/gen_omnitrix_dial_icons.js on it with
//                                        IDS=ditto until the user sends real art
// The skin paints the Omnitrix as a 2x2 white-grey mark on his forehead fin (the hat layer's front, 43..44 x 9..10), like the show
// (Ditto wears the hourglass on his forehead). Those four texels take the fin's own dark colour; DittoClient's badge sits there.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const src = dec(path.join(__dirname, 'ditto_dl', 'ditto_src.png'));
const px = Buffer.from(src.px);
const at = (x, y) => (y * 64 + x) * 4;
// the fin's dark centre, as on its rows above and below (0e121b)
for (const [x, y] of [[43, 9], [44, 9], [43, 10], [44, 10]]) {
	px[at(x, y)] = 0x0e;
	px[at(x, y) + 1] = 0x12;
	px[at(x, y) + 2] = 0x1b;
	px[at(x, y) + 3] = 255;
}
fs.writeFileSync(path.join(RES, 'entity/omnitrix/ditto.png'), L.encodePng(64, 64, px));

// ---- placeholder dial source, 222x222
const N = 222, C = N / 2;
const d = Buffer.alloc(N * N * 4, 255);
const set = (x, y, r, g, b) => {
	const i = (y * N + x) * 4;
	d[i] = r; d[i + 1] = g; d[i + 2] = b; d[i + 3] = 255;
};
// the silhouette: a small round-headed figure with a fin, stubby arms out, short legs (in a 0..1 box)
const inFigure = (u, v) => {
	const head = Math.hypot(u - 0.5, v - 0.3) < 0.17;
	const fin = Math.abs(u - 0.5) < 0.05 && v > 0.06 && v < 0.3;
	const body = Math.abs(u - 0.5) < 0.14 && v > 0.42 && v < 0.72;
	const arms = v > 0.46 && v < 0.54 && Math.abs(u - 0.5) < 0.3;
	const legs = v >= 0.72 && v < 0.9 && Math.abs(u - 0.5) > 0.03 && Math.abs(u - 0.5) < 0.13;
	return head || fin || body || arms || legs;
};
for (let y = 0; y < N; y++) {
	for (let x = 0; x < N; x++) {
		const r = Math.hypot(x + 0.5 - C, y + 0.5 - C);
		if (r > 105) continue; // white outside (the icon script cuts it away)
		if (r > 96) { set(x, y, 30, 30, 30); continue; }
		set(x, y, 150, 152, 156);
		// the lime hourglass: two triangles meeting in the middle
		const dx = Math.abs(x + 0.5 - C), dy = Math.abs(y + 0.5 - C);
		if (dy < 78 && dx < dy * 0.85 + 4) set(x, y, 120, 220, 40);
		const u = (x - 40) / 142, v = (y - 30) / 170;
		if (u >= 0 && u <= 1 && v >= 0 && v <= 1 && inFigure(u, v)) set(x, y, 12, 14, 18);
	}
}
fs.mkdirSync(path.join(__dirname, 'ditto_dl', 'dial_src'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'ditto_dl', 'dial_src', 'ditto.png'), L.encodePng(N, N, d));
console.log('ditto textures written');
