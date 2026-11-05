// v0.17.2 Stinkfly's third skin (user: "also for the stinkfly skin model can you rather go back to the old one? i think it was this :
// https://www.planetminecraft.com/skin/stinkfly-ben-10-4949986/") -- stinkfly_v3/stinkfly3_src.png. (Not the same file as the
// v0.17.1 skin, stinkfly_src.png, but the one the user linked.) Standard full-block limbs, drawn as the skin lays them out.
//   textures/entity/omnitrix/stinkfly.png   the skin with what the modelled parts replace taken off:
//     - the painted wings and tail on the jacket's back and sides (the pale wing colours anywhere on the jacket, and the jacket's
//       whole back): the base back's thorax shows under them; StinkflyClient draws the wings, StinkflyBody the tail
//     - the tail where it runs down the legs: the whites on the legs' backs and inner sides repainted in the leg's green beside
//       them; the pants layer's whites and its black tail tip cleared
//     - the painted Omnitrix on the forehead (base 11..12 x 11..12 white diagonal -> the face's dark; hat 43..44 x 11..12 cleared);
//       StinkflyClient.badge() sits there
// Run AFTER gen_stinkfly_v2.js (which still writes stinkfly_body.png, the tail's sheet; its stinkfly.png is replaced here).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const at = (x, y) => (y * 64 + x) * 4;
const src = dec(path.join(__dirname, 'stinkfly_v3', 'stinkfly3_src.png'));
const sk = Buffer.from(src.px);
const rgb = o => [sk[o], sk[o + 1], sk[o + 2]];
const white = o => sk[o + 3] && sk[o] > 180 && Math.abs(sk[o] - sk[o + 1]) < 15 && Math.abs(sk[o + 1] - sk[o + 2]) < 15;
const pale = o => sk[o + 3] && sk[o] > 185 && sk[o + 1] > 190 && sk[o + 2] < 205 && sk[o + 2] > 120 && sk[o + 1] - sk[o + 2] > 20; // the wings' cream
const green = o => sk[o + 3] && sk[o + 1] > sk[o] + 10 && sk[o + 1] > sk[o + 2] + 20;

// the jacket: its whole back, and the wing colours wherever else they reach
for (let y = 32; y < 48; y++) for (let x = 16; x < 40; x++) {
	const o = at(x, y);
	if ((x >= 32 && y >= 36) || pale(o)) sk[o + 3] = 0;
}
// the sleeves: the wings' cream painted across the backs of the arms
for (const [ox, oy] of [[40, 32], [48, 48]]) {
	for (let y = oy; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) if (pale(at(x, y))) sk[at(x, y) + 3] = 0;
}
// the legs' base: whites (the tail) on the backs and inner sides -> the nearest green in the same row of that leg
for (const [ox, oy] of [[0, 16], [16, 48]]) {
	for (let y = oy + 4; y < oy + 16; y++) {
		for (let x = ox; x < ox + 16; x++) {
			const o = at(x, y);
			if (!white(o)) continue;
			let best = null;
			for (let d = 1; d < 16 && !best; d++) for (const xx of [x - d, x + d]) {
				if (xx >= ox && xx < ox + 16 && green(at(xx, y))) { best = rgb(at(xx, y)); break; }
			}
			if (best) { sk[o] = best[0]; sk[o + 1] = best[1]; sk[o + 2] = best[2]; }
		}
	}
}
// the pants: anything not green (the tail's whites and its black tip) cleared -- the base leg shows
for (const [ox, oy] of [[0, 32], [0, 48]]) {
	for (let y = oy + 4; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) if (!green(at(x, y))) sk[at(x, y) + 3] = 0;
}
// the Omnitrix on the forehead
const face = rgb(at(10, 11));
for (let y = 11; y <= 12; y++) for (let x = 11; x <= 12; x++) { const o = at(x, y); sk[o] = face[0]; sk[o + 1] = face[1]; sk[o + 2] = face[2]; sk[o + 3] = 255; }
for (let y = 11; y <= 12; y++) for (let x = 43; x <= 44; x++) sk[at(x, y) + 3] = 0;
fs.writeFileSync(path.join(OUT, 'stinkfly.png'), L.encodePng(64, 64, sk));
console.log('stinkfly.png written (third skin)');
