// v0.17.2 Cannonbolt textures (second skin -- user: "use this skin for canonbolt please:
// https://www.planetminecraft.com/skin/cannonbolt-ben-10-original-series/ The body, arms and legs should be used for the alien
// model and the head should be used for the ball form"):
//   textures/entity/omnitrix/cannonbolt.png       the skin with its head (and hat layer) cleared -- the standing body, arms and
//                                                 legs only (he has no head: his face is on his chest)
//   textures/entity/omnitrix/cannonbolt_ball.png  the skin as-is: CannonboltClient wraps its HEAD's six faces (and the hat layer
//                                                 just outside them) round the ball, the head's front face at the ball's front
// The dial icons (gui/omnitrix/dial_cannonbolt*.png, font/omnitrix_dial/cannonbolt.png) are the USER'S art now -- not generated
// here any more (scratchpad/gen_omnitrix_dial_icons.js).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const write = (f, w, h, px) => fs.writeFileSync(path.join(RES, f), L.encodePng(w, h, px));

const src = dec(path.join(__dirname, 'cannonbolt_v2', 'cannonbolt2_src.png'));
write('entity/omnitrix/cannonbolt_ball.png', 64, 64, src.px);
const body = Buffer.from(src.px);
for (let y = 0; y < 16; y++) {
	for (let x = 0; x < 64; x++) {
		body[(y * 64 + x) * 4 + 3] = 0; // the head and the hat layer: gone from the standing model
	}
}
write('entity/omnitrix/cannonbolt.png', 64, 64, body);
console.log('cannonbolt textures written');
