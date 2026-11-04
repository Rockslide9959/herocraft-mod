// v0.17.2 Buzzshock textures (user: "Also build buzzshock please heres the skin:
// https://www.planetminecraft.com/skin/buzzshock-ben-10-5321154/" ... "remove the pixel omnitrix from his skin model and replace it
// with our omnitrix badge please"):
//   textures/entity/omnitrix/buzzshock.png        the skin, its painted chest Omnitrix painted out
//   textures/entity/omnitrix/buzzshock_glow.png   the emissive pass (BuzzshockClient draws it full-bright, flickering): the green
//                                                 vent on top of his head, his green eyes, the green arm / leg lights at full
//                                                 strength, his white grin and the white lightning stripe down his front and back
//                                                 at about half (his body is living electricity)
// The skin paints the classic-series Omnitrix (worn on his chest) as a 2x2 dark-blue / white mark in the middle of the jacket
// layer's white stripe (texels 23..24 x 37..38). Those four texels take the stripe's own white; BuzzshockClient's badge sits there.
// Source: buzzshock_dl/buzzshock_skin.png (downloaded through the built-in browser -- planetminecraft blocks curl).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const src = dec(path.join(__dirname, 'buzzshock_dl', 'buzzshock_skin.png'));
const px = Buffer.from(src.px);
const at = (x, y) => (y * 64 + x) * 4;
const put = (b, x, y, hex, a = 255) => {
	const i = at(x, y);
	b[i] = (hex >> 16) & 255;
	b[i + 1] = (hex >> 8) & 255;
	b[i + 2] = hex & 255;
	b[i + 3] = a;
};
// the stripe's white, as in the rows above and below (its brighter centre column x23, the softer x24)
put(px, 23, 37, 0xeaeffb);
put(px, 24, 37, 0xdae1f5);
put(px, 23, 38, 0xeaeffb);
put(px, 24, 38, 0xdae1f5);
fs.writeFileSync(path.join(RES, 'entity/omnitrix/buzzshock.png'), L.encodePng(64, 64, px));

// ---- the glow pass: only the lit texels, everything else transparent black
const g = Buffer.alloc(64 * 64 * 4);
for (let y = 0; y < 64; y++) {
	for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (px[i + 3] < 10) continue;
		const r = px[i], gr = px[i + 1], b = px[i + 2];
		let k = 0;
		if (gr > 200 && r < 200 && b < 120) k = 1.0; // the green vent, eyes and lights
		else if (r > 185 && gr > 185 && b > 210) k = 0.42; // the white stripe and grin
		if (k <= 0) continue;
		g[i] = Math.round(r * k);
		g[i + 1] = Math.round(gr * k);
		g[i + 2] = Math.round(b * k);
		g[i + 3] = 255;
	}
}
fs.writeFileSync(path.join(RES, 'entity/omnitrix/buzzshock_glow.png'), L.encodePng(64, 64, g));
console.log('buzzshock textures written');
