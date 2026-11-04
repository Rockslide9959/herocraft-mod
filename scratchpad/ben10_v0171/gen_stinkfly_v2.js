// v0.17.2 Stinkfly's second skin (user: "Replace our stinkfly skin with this one please:
// https://www.planetminecraft.com/skin/stinkfly-ben-10-ben-10-omniverse/ Look carefully at how the stinkfly skin is made, it got
// thin legs and double arms aswell, remove the tail from the skin and make the tail model on our stinkfly skin look better
// please, it must be a replacement for the tail on the skin") -- stinkfly_v2/stinkfly2_src.png.
//
// How that skin is made: the head is black with his four yellow-tipped eyes and the green mandible ring, the thorax white with
// black segment bands, and a long white tail painted down his back (body back 32..39 x 20..31, its jacket column 34..37 x
// 36..47) running on down the backs of both legs to a black tip. Its limbs are not blocks: both layers are cut with transparency
// -- the base arm into two thin strips per face (a "double arm" on each side), the sleeve into another thin clawed arm, the
// legs into 2..3 px stalks. Cut-out cubes look hollow in 3D, so StinkflyClient MODELS those parts instead (two thin clawed arms
// a side, thin jointed legs, the segmented tail) and this skin keeps only what a cube shows well:
//   textures/entity/omnitrix/stinkfly.png        the head and the thorax; the painted tail gone (the back takes the front's
//                                                segment bands, mirrored; its jacket column cleared then given the front's),
//                                                every arm and leg rectangle (both layers) cleared, the painted Omnitrix on the
//                                                hat (43..44 x 10..11) painted out in the face's black
//   textures/entity/omnitrix/stinkfly_body.png   the modelled parts' sheet, in the skin's own colours (layout below,
//                                                StinkflyClient reads the same rectangles)
// Run AFTER gen_skins.js (its Stinkfly blocks still write the first skin).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const write = (f, px) => fs.writeFileSync(path.join(OUT, f), L.encodePng(64, 64, px));
const at = (x, y) => (y * 64 + x) * 4;

// ------------------------------------------------------------------ the skin
const src = dec(path.join(__dirname, 'stinkfly_v2', 'stinkfly2_src.png'));
const sk = Buffer.from(src.px);
const copy = (sx, sy, dx, dy) => { for (let c = 0; c < 4; c++) sk[at(dx, dy) + c] = src.px[at(sx, sy) + c]; };
const clear = (x0, y0, x1, y1) => { for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) sk[at(x, y) + 3] = 0; };
// the back: the front's thorax bands, mirrored (base and jacket)
for (let y = 20; y < 32; y++) for (let x = 0; x < 8; x++) copy(20 + x, y, 39 - x, y);
for (let y = 36; y < 48; y++) for (let x = 0; x < 8; x++) copy(20 + x, y, 39 - x, y);
// the limbs, both layers: right leg / pants, right arm / sleeve, left leg / pants, left arm / sleeve
clear(0, 16, 16, 48);
clear(40, 16, 56, 48);
clear(0, 48, 32, 64);
clear(32, 48, 64, 64);
// the painted Omnitrix on the hat's face
for (let y = 10; y <= 11; y++) for (let x = 43; x <= 44; x++) for (let c = 0; c < 4; c++) sk[at(x, y) + c] = src.px[at(45, 10) + c];
write('stinkfly.png', sk);

// ------------------------------------------------------------------ the modelled parts' sheet
const P = {
	white: [245, 247, 252], pale: [237, 241, 250], light: [226, 232, 249], mid: [193, 199, 217], grey: [177, 184, 204],
	dusk: [128, 134, 152], slate: [70, 74, 86], dark: [25, 25, 38], black: [7, 8, 10], ink: [16, 19, 24], coal: [10, 12, 15],
	g0: [61, 78, 22], g1: [71, 92, 28], g2: [81, 105, 32], g3: [98, 122, 49], g4: [105, 130, 52], g5: [113, 140, 56],
};
const sh = Buffer.alloc(64 * 64 * 4);
const put = (x, y, c, a = 255) => { const o = at(x, y); sh[o] = c[0]; sh[o + 1] = c[1]; sh[o + 2] = c[2]; sh[o + 3] = a; };
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const fill = (x0, y0, x1, y1, f) => { for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) put(x, y, f(x - x0, y - y0, x1 - x0, y1 - y0)); };

// A tail plate (8 wide, 16 long; v runs root -> tip): a white dorsal plate, brightest along its ridge, greying to its edges, the
// front lip a little raised (one light row), a grey shading band toward its end and the dark seam where it tucks under the next
const plate = (base, edge) => (u, v, w) => {
	const across = Math.abs(u - (w - 1) / 2) / ((w - 1) / 2); // 0 on the ridge .. 1 at the edges
	let c = mix(base, edge, across * across);
	if (v === 0) c = mix(c, P.white, 0.6);
	if (v >= 11) c = mix(c, P.dusk, (v - 10) / 6);
	if (v === 14) c = mix(c, P.slate, 0.6);
	if (v === 15) c = P.dark;
	if (u === Math.floor(w / 2) && v > 0 && v < 11 && base === P.white) c = mix(c, P.white, 0.8); // the ridge's highlight
	return c;
};
fill(0, 0, 8, 16, plate(P.white, P.grey)); // TAIL_TOP
fill(8, 0, 16, 16, plate(P.light, P.dusk)); // TAIL_SIDE
fill(16, 0, 24, 16, (u, v) => { // TAIL_BELLY: softer grey segments, a fine crease down the middle
	let c = mix(P.mid, P.grey, Math.abs(u - 3.5) / 3.5);
	if (u === 3 || u === 4) c = mix(c, P.dusk, 0.35);
	if (v >= 12) c = mix(c, P.slate, (v - 11) / 5);
	if (v === 15) c = P.dark;
	return c;
});
fill(24, 0, 32, 8, (u, v) => { // TAIL_CAP: the end of a segment -- a grey rim round a dark core
	const r = Math.max(Math.abs(u - 3.5), Math.abs(v - 3.5));
	return r > 2.5 ? P.grey : r > 1.5 ? P.slate : P.dark;
});
fill(24, 8, 32, 16, (u, v) => mix(P.slate, P.dark, ((u + v) % 3) / 4)); // JOINT: the soft membrane between plates
fill(32, 0, 36, 16, (u, v) => { // STING: glossy black, a cold highlight down one side, a grey ring at its root
	let c = u === 1 ? P.slate : u === 2 ? P.dark : P.black;
	if (v <= 1) c = v === 0 ? P.grey : P.dusk;
	return c;
});
fill(36, 0, 40, 4, () => P.ink); // STING_CAP
fill(40, 0, 44, 16, (u, v) => { // LIMB: green chitin, a lit stripe and a shadowed one, a dark ring at each joint end
	let c = [P.g2, P.g4, P.g3, P.g1][u];
	if ((v * 7 + u * 3) % 5 === 0) c = mix(c, P.g0, 0.4); // the skin's mottling
	if (v === 0 || v === 15) c = P.g0;
	if (v === 1 || v === 14) c = mix(c, P.g0, 0.5);
	return c;
});
fill(44, 0, 48, 4, (u, v) => (u + v) % 2 ? P.g1 : P.g2); // LIMB_CAP
fill(44, 4, 48, 8, (u, v) => mix(P.g0, P.g1, ((u + v) % 2) * 0.6)); // KNEE: the dark joint knobs
fill(48, 0, 52, 8, (u, v) => { // CLAW: black, the skin's blue-black highlight on its upper edge, fading to the point
	let c = u === 1 ? P.ink : P.black;
	if (u === 1 && v < 4) c = P.slate;
	return c;
});
fill(52, 0, 56, 4, () => P.coal); // CLAW_CAP
fill(0, 16, 8, 24, (u, v) => mix(P.mid, P.slate, (Math.abs(u - 3.5) + Math.abs(v - 3.5)) / 7)); // MOUNT: the wing knot's chitin
write('stinkfly_body.png', sh);
console.log('stinkfly.png, stinkfly_body.png written');
