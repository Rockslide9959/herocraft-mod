// v0.17.1 polish: the Omnitrix transformation VFX textures (OmnitrixTransformFx).
//   * textures/gui/omnitrix/fx_dot.png    128x128 white disc, a 1.5-px anti-aliased edge: the Kirby dots (tinted black / green
//                                         by vertex colour, on the screen and in the world)
//   * textures/gui/omnitrix/fx_glow.png   128x128 soft radial glow, premultiplied (black outside): the world flash (additive)
// Usage: node scratchpad/gen_omnitrix_fx_textures.js
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');
const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'gui', 'omnitrix');
fs.mkdirSync(OUT, { recursive: true });

function make(fn, premultiplied) {
	const W = 128, px = Buffer.alloc(W * W * 4);
	for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
		const r = Math.hypot(x + 0.5 - 64, y + 0.5 - 64) / 64; // 0 at the centre, 1 at the edge
		const a = Math.max(0, Math.min(1, fn(r)));
		const i = (y * W + x) * 4;
		// the glow is drawn additively (RenderType.eyes ignores alpha): its brightness lives in the colour
		px[i] = px[i + 1] = px[i + 2] = premultiplied ? Math.round(a * 255) : 255;
		px[i + 3] = premultiplied ? 255 : Math.round(a * 255);
	}
	return px;
}
// the dot: solid to r = 62/64, then a 1.5-texel ramp (crisp at every size the GUI scales it to)
PNG.write(path.join(OUT, 'fx_dot.png'), 128, 128, make(r => (62.5 - r * 64) / 1.5));
// the glow: a bright core falling off smoothly to nothing at the edge
PNG.write(path.join(OUT, 'fx_glow.png'), 128, 128, make(r => r >= 1 ? 0 : Math.pow(1 - r, 2.2) * (0.55 + 0.45 * Math.exp(-r * r * 18)), true));
console.log('wrote', OUT);
