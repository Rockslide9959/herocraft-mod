// v0.17.2 Ghostfreak's second skin (user: "replace our ghost freak model with this one please:
// https://www.planetminecraft.com/skin/ghostfreak-ben-10-4702366/ ... try to colour match the tail to the skin please") --
// ghostfreak_v2/ghostfreak2_src.png. A WIDE skin (the first one was slim and remapped in gen_skins.js; this one needs none).
//   textures/entity/omnitrix/ghostfreak.png        the skin, its painted Omnitrix painted out: the 2x2 white/grey symbol on the
//                                                  jacket (23..24 x 39..40) cleared, the grey ring under it on the base
//                                                  (23..24 x 23..24) given his body's grey; GhostfreakClient.badge() sits there
//   textures/entity/omnitrix/ghostfreak_tail.png   the modelled tail's sheet (GhostfreakTail), from the tail the skin paints on
//                                                  its legs (both legs together taper into one wisp): each of the 12 rows of the
//                                                  legs' faces, base with the pants layer over it, its opaque texels stretched
//                                                  across the full width (the model tapers the boxes; the sheet keeps the
//                                                  colours and the black cracks)
//                                                    FRONT   0..8  x 0..12  right leg front + left leg front
//                                                    BACK    8..16 x 0..12  left leg back + right leg back (seen from behind)
//                                                    SIDE_R 16..20 x 0..12  right leg outer side
//                                                    SIDE_L 20..24 x 0..12  left leg outer side
// Run AFTER gen_skins.js (its Ghostfreak blocks still write the first skin).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const write = (f, px) => fs.writeFileSync(path.join(OUT, f), L.encodePng(64, 64, px));
const at = (x, y) => (y * 64 + x) * 4;

const src = dec(path.join(__dirname, 'ghostfreak_v2', 'ghostfreak2_src.png'));
const sk = Buffer.from(src.px);
for (let y = 39; y <= 40; y++) for (let x = 23; x <= 24; x++) sk[at(x, y) + 3] = 0;
const GREY = [180, 178, 190];
for (let y = 23; y <= 24; y++) for (let x = 23; x <= 24; x++) { const o = at(x, y); sk[o] = GREY[0]; sk[o + 1] = GREY[1]; sk[o + 2] = GREY[2]; sk[o + 3] = 255; }
write('ghostfreak.png', sk);

// a texel of a leg face: the pants layer if it has one there, else the base
const tex = (bx, by, ox, oy) => {
	const o = at(ox, oy);
	if (sk[o + 3]) return [...sk.slice(o, o + 3)];
	const b = at(bx, by);
	return sk[b + 3] ? [...sk.slice(b, b + 3)] : null;
};
// faces: [base x, base y, pants x, pants y] of each 4-wide column strip, left to right as seen
const RL = { front: [4, 20, 4, 36], back: [12, 20, 12, 36], outer: [0, 20, 0, 36] };
const LL = { front: [20, 52, 4, 52], back: [28, 52, 12, 52], outer: [24, 52, 8, 52] };
const sheet = Buffer.alloc(64 * 64 * 4);
const strip = (dx, faces) => {
	let last = null;
	const w = faces.length * 4;
	for (let r = 0; r < 12; r++) {
		const row = [];
		for (const f of faces) for (let c = 0; c < 4; c++) {
			const t = tex(f[0] + c, f[1] + r, f[2] + c, f[3] + r);
			if (t) row.push(t);
		}
		const use = row.length ? row : last;
		last = use;
		for (let x = 0; x < w; x++) {
			const c = use ? use[Math.min(use.length - 1, Math.floor(x * use.length / w))] : GREY;
			const o = at(dx + x, r);
			sheet[o] = c[0]; sheet[o + 1] = c[1]; sheet[o + 2] = c[2]; sheet[o + 3] = 255;
		}
	}
};
strip(0, [RL.front, LL.front]);
strip(8, [LL.back, RL.back]);
strip(16, [RL.outer]);
strip(20, [LL.outer]);
write('ghostfreak_tail.png', sheet);
console.log('ghostfreak.png, ghostfreak_tail.png written');
