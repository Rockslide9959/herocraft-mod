// v0.17.5 Omnitrix Master Control round 3 (user: "during the master control unlock the omnitrix symbol shouldnt be these 4 squares
// it should be an alternating black and white regular looking omnitrix symbol"): the flash face's masks.
//   entity/omnitrix/master_flash_face.png  128x64: two 64x64 cells, white on transparent (drawn cutout with a vertex tint)
//     cell 0 (u 0..0.5): the face disc (the background colour)
//     cell 1 (u 0.5..1): the hourglass, the same shape as the plain face's (face_morph.png frame 0): the two wedges meeting at a
//       narrow waist, inside the bezel ring, with the face's dark outline gap between the hourglass and its sides
// Usage: node scratchpad/gen_omnitrix_master_flash_v0175.js
'use strict';
const path = require('path');
const PNG = require('./pnglib.js');
const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix', 'master_flash_face.png');

const C = 64, W = C * 2, H = C;
const px = Buffer.alloc(W * H * 4);
const R = C / 2;
for (let y = 0; y < C; y++) {
	for (let x = 0; x < C; x++) {
		const dx = x + 0.5 - R, dy = y + 0.5 - R;
		const r = Math.hypot(dx, dy) / R;
		// the disc: the whole round face
		if (r <= 0.98) {
			const i = (y * W + x) * 4;
			px[i] = px[i + 1] = px[i + 2] = px[i + 3] = 255;
		}
		// the hourglass (measured off face_morph.png frame 0, in face radii): half-width 0.9 |dy| + 0.125, inside the bezel (r 0.84)
		const hw = 0.9 * Math.abs(dy) / R + 0.125;
		if (r <= 0.84 && Math.abs(dx) / R <= hw) {
			const i = (y * W + C + x) * 4;
			px[i] = px[i + 1] = px[i + 2] = px[i + 3] = 255;
		}
	}
}
PNG.write(OUT, W, H, px);
console.log('wrote', OUT);
