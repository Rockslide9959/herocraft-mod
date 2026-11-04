// v0.17.2 Upchuck textures (user: "heres upchucks skin please build him: https://www.planetminecraft.com/skin/upchuck/ ... remove the
// pixel omnitrix from his skin model and replace it with our omnitrix badge please"):
//   textures/entity/omnitrix/upchuck.png   the skin, its painted Omnitrix painted out
//   upchuck_dl/dial_src/upchuck.png        a 222x222 placeholder dial source (grey disc, black rim, lime hourglass, Upchuck's black
//                                          silhouette: a squat round body, a big wide head with its sprouts, stubby legs, a tail) --
//                                          run scratchpad/gen_omnitrix_dial_icons.js on it with IDS=upchuck until the user's art arrives
// The skin (downloaded as upchuck_dl/upchuck_src.png) paints the Omnitrix as a 2x2 grey hourglass on the outside of his LEFT WRIST --
// the original series' Upchuck wears the symbol on his left hand -- on both layers: the arm (40..44 is the left arm's outer face,
// texels 41..42 x 60..61) and the sleeve (56..60, texels 57..58 x 60..61). Each texel takes its wrist band's own colour (the white
// band on row 60, the black cuff on row 61); UpchuckClient's badge sits there instead.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const src = dec(path.join(__dirname, 'upchuck_dl', 'upchuck_src.png'));
const px = Buffer.from(src.px);
const at = (x, y) => (y * 64 + x) * 4;
const put = (x, y, rgb) => {
	px[at(x, y)] = (rgb >> 16) & 255;
	px[at(x, y) + 1] = (rgb >> 8) & 255;
	px[at(x, y) + 2] = rgb & 255;
	px[at(x, y) + 3] = 255;
};
const WHITE = 0xdce2f1, CUFF = 0x0b0b14;
for (const x of [41, 42, 57, 58]) {
	put(x, 60, WHITE);
	put(x, 61, CUFF);
}
fs.writeFileSync(path.join(RES, 'entity/omnitrix/upchuck.png'), L.encodePng(64, 64, px));

// ---- placeholder dial source, 222x222
const N = 222, C = N / 2;
const d = Buffer.alloc(N * N * 4, 255);
const set = (x, y, r, g, b) => {
	const i = (y * N + x) * 4;
	d[i] = r; d[i + 1] = g; d[i + 2] = b; d[i + 3] = 255;
};
// the silhouette in a 0..1 box: a big wide head with three sprouts, a round pot belly, stubby arms and legs, a curled tail
const inFigure = (u, v) => {
	const head = ((u - 0.5) / 0.26) ** 2 + ((v - 0.3) / 0.17) ** 2 < 1;
	const sprouts = [0.36, 0.5, 0.64].some((sx) => Math.abs(u - sx) < 0.03 && v > 0.07 && v < 0.18);
	const belly = ((u - 0.5) / 0.24) ** 2 + ((v - 0.6) / 0.2) ** 2 < 1;
	const arms = v > 0.5 && v < 0.58 && Math.abs(u - 0.5) < 0.34;
	const legs = v >= 0.74 && v < 0.92 && Math.abs(u - 0.5) > 0.05 && Math.abs(u - 0.5) < 0.16;
	const tail = Math.hypot(u - 0.8, v - 0.8) < 0.08 && Math.hypot(u - 0.8, v - 0.8) > 0.04;
	return head || sprouts || belly || arms || legs || tail;
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
fs.mkdirSync(path.join(__dirname, 'upchuck_dl', 'dial_src'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'upchuck_dl', 'dial_src', 'upchuck.png'), L.encodePng(N, N, d));
console.log('upchuck textures written');
