// v0.17.3 Shock Rock textures (user: "replace the pixel omnitrix on their skin with our omnitrix badge please"):
//   textures/entity/omnitrix/shock_rock.png       the user's skin, the little hourglass on the jacket over the chest's rock band (23..24 x 38..39)
//                                                 painted over with the band's own dark rock (the base layer's 0x1a1921), so the badge sits there
//   textures/entity/omnitrix/shock_rock_glow.png  the emissive pass: every bright cyan energy texel and the green eyes
// Source: docs/reference/omnitrix_new_aliens/shock_rock.png.   node scratchpad/omnitrix_new/gen_shock_rock.js
'use strict';
const path = require('path');
const PNG = require('../pnglib.js');

const REF = path.join(__dirname, '../../docs/reference/omnitrix_new_aliens/shock_rock.png');
const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const at = (x, y) => (y * 64 + x) * 4;
function put(px, x, y, hex, a = 255) {
	const i = at(x, y);
	px[i] = (hex >> 16) & 255;
	px[i + 1] = (hex >> 8) & 255;
	px[i + 2] = hex & 255;
	px[i + 3] = a;
}

const im = PNG.read(REF);
if (im.W !== 64 || im.H !== 64) throw new Error('shock_rock is ' + im.W + 'x' + im.H);
const px = Buffer.from(im.px);
for (const x of [23, 24]) for (const y of [38, 39]) put(px, x, y, 0x1a1921);
PNG.write(path.join(OUT, 'shock_rock.png'), 64, 64, px);

const g = Buffer.alloc(64 * 64 * 4);
let lit = 0;
for (let y = 0; y < 64; y++) {
	for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (px[i + 3] < 10) continue;
		const r = px[i], gr = px[i + 1], b = px[i + 2];
		const energy = gr >= 150 && b >= 150 && r <= 215;
		const eye = gr > 200 && r < 190 && b < 100;
		if (energy || eye) {
			g[i] = r; g[i + 1] = gr; g[i + 2] = b; g[i + 3] = 255;
			lit++;
		}
	}
}
PNG.write(path.join(OUT, 'shock_rock_glow.png'), 64, 64, g);
console.log('shock rock written, glow texels: ' + lit);
