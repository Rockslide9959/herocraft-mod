// v0.17.3 Omnitrix: Eye Guy textures (user: "make aliens based on their abilities and stuff they do in the
// show please, replace the pixel omnitrix on their skin with our omnitrix badge please"). Sources: docs/reference/omnitrix_new_aliens.
//   textures/entity/omnitrix/<id>.png        the skin, its painted pixel Omnitrix painted out (the mod's badge sits there)
//   textures/entity/omnitrix/<id>_glow.png   the emissive pass (lime eyes / stripes / bolt glows; Eye Guy's irises white for tinting)
// and the dial icon sources (320x320, the placeholder style the transplant reads) in scratchpad/omnitrix_new/dial_src/.
// Then: node scratchpad/gen_omnitrix_icon_transplant.js snare_oh=... eye_guy=... frankenstrike=...
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('../ben10_v0171/png_view.js');

const ROOT = path.join(__dirname, '..', '..');
const REF = path.join(ROOT, 'docs', 'reference', 'omnitrix_new_aliens');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
const at = (x, y) => (y * 64 + x) * 4;
const put = (b, x, y, hex, a = 255) => {
	const i = at(x, y);
	b[i] = (hex >> 16) & 255;
	b[i + 1] = (hex >> 8) & 255;
	b[i + 2] = hex & 255;
	b[i + 3] = a;
};
const clear = (b, x, y) => put(b, x, y, 0, 0);
const lime = (b, i) => b[i + 3] > 10 && b[i + 1] > 150 && b[i + 1] - b[i] > 40 && b[i + 1] - b[i + 2] > 60;

function glow(px, white, keep) {
	const g = Buffer.alloc(64 * 64 * 4);
	let n = 0;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (!lime(px, i) || (keep && !keep(x, y))) continue;
		if (white) {
			const m = Math.max(px[i], px[i + 1], px[i + 2]);
			g[i] = g[i + 1] = g[i + 2] = m;
		} else {
			g[i] = px[i];
			g[i + 1] = px[i + 1];
			g[i + 2] = px[i + 2];
		}
		g[i + 3] = 255;
		n++;
	}
	return { g, n };
}

// ---------------------------------------------------------------- Eye Guy
{
	const px = Buffer.from(dec(path.join(REF, 'eye_guy.png')).px);
	// the belt buckle's pixel Omnitrix (jacket 23..24 x 45..46) goes clear: the body layer's dark buckle shows, the badge sits on it
	for (const [x, y] of [[23, 45], [24, 45], [23, 46], [24, 46]]) clear(px, x, y);
	fs.writeFileSync(path.join(OUT, 'eye_guy.png'), L.encodePng(64, 64, px));
	// every iris (lime) as white, so the renderer tints them with the beam colour
	const { g, n } = glow(px, true);
	fs.writeFileSync(path.join(OUT, 'eye_guy_glow.png'), L.encodePng(64, 64, g));
	console.log('eye_guy glow texels', n);
}

