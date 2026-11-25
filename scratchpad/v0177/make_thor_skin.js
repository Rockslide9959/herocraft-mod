// v0.17.7 Thor's Armour look: the helmet from a PlanetMinecraft skin + the red cape texture.
//
//   node scratchpad/v0177/make_thor_skin.js
//
// History: the user first asked "Use this skin: https://www.planetminecraft.com/skin/thor-avengers-6321094/ Remove the cape from the
// back of the skin and add a red cape model", then changed it: "for the thor armour remove the skin from it so players can see their
// skin underneath and keep the helmet please". So the armour now draws ONLY the skin's winged helmet; the body, arms and legs show
// the wearer's own skin, and the cape is the 3D cloth one (client ThorCapeLayer).
//
// Source (NEVER modified): scratchpad/v0177/thor_skin_original.png -- "Thor | Avengers" on PlanetMinecraft, the 64x64 wide-arm skin
// file behind the page's download button (static.planetminecraft.com/files/resource_media/skin/steve-planetminecraft-com-19528531.png).
//
// Writes:
//   src/main/resources/assets/projecthero/textures/armor/thor.png      Thor's Armour texture (player-skin UV layout, geo/thor.geo.json):
//                                                                      only the helmet, everything else transparent
//   src/main/resources/assets/projecthero/textures/entity/thor_cape.png the 3D cape (FlowingCapeLayer 64x48 layout), in the skin's reds
//
// The helmet: in the head rows (0-15) of both layers, every texel in one of the helmet's colours is kept -- the silver crown and brow
// band (base layer, rows 0-10 across the head), the silver cheek guards down the sides (rows 11-14), and on the hat layer the two white
// wings and the silver crest strip. Every other texel (the skin's face, eyes, blond hair, neck) is cleared, so the wearer's own face
// and hair show under and between the plates. Rows 16-63 (body, arms, legs) are cleared entirely.
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(__dirname, 'thor_skin_original.png');
const OUT_SUIT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/armor/thor.png');
const OUT_CAPE = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/thor_cape.png');

const img = PNG.read(SRC);
const W = img.W, H = img.H, px = img.px;
if (W !== 64 || H !== 64) throw new Error('expected a 64x64 skin, got ' + W + 'x' + H);

// the helmet's colours in the source: silvers / steel blues, and the wings' whites
const HELMET = new Set([
	'203,212,219', '179,193,203', '222,227,231', '186,177,194', '147,171,180', '115,147,161', // silver, steel
	'255,255,255', '237,233,239', '212,205,216', // wings
]);
let kept = 0, cleared = 0;
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const i = (y * W + x) * 4;
	if (px[i + 3] === 0) continue;
	const key = px[i] + ',' + px[i + 1] + ',' + px[i + 2];
	// the head's bottom face (u 16..23, v 0..7) is the neck opening: never part of the helmet
	const neck = y < 8 && x >= 16 && x < 24;
	// the face below the brow band (front face, rows 11-15) is the skin's eyes: white like the wings, but never helmet
	const face = x >= 8 && x < 16 && y >= 11 && y < 16;
	if (y < 16 && HELMET.has(key) && !neck && !face) {
		kept++;
	} else {
		px[i] = px[i + 1] = px[i + 2] = px[i + 3] = 0;
		cleared++;
	}
}
PNG.write(OUT_SUIT, W, H, px);
console.log('suit (helmet only):', OUT_SUIT, '| helmet texels', kept, '| cleared', cleared);

// ---- the cape texture (FlowingCapeLayer layout, 64x48: u 0..31 the outside, u 32..63 the lining) ----
// The skin's own cape reds: J 244,16,61  K 219,16,57  L 190,15,52  M 158,14,41  V 144,10,39. Soft vertical folds, a darker hem,
// a slightly darker band under the collar; the lining is the shadowed M / V reds.
const CW = 64, CH = 48;
const cape = Buffer.alloc(CW * CH * 4);
const lerp = (a, b, t) => a + (b - a) * t;
const mix = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const J = [244, 16, 61], K = [219, 16, 57], L = [190, 15, 52], M = [158, 14, 41], V = [144, 10, 39], D = [104, 8, 28];
let seed = 7;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
for (let y = 0; y < CH; y++) for (let x = 0; x < CW; x++) {
	const lining = x >= 32;
	const u = (x % 32) / 32;
	// folds: a few soft vertical ridges, lit centre-left
	const fold = 0.5 + 0.5 * Math.sin(u * Math.PI * 6 + 0.6) * (0.55 + 0.45 * (y / CH));
	let c = lining ? mix(V, M, fold * 0.7) : mix(L, mix(K, J, 0.45), fold);
	if (y <= 1) c = mix(c, lining ? D : L, 0.6); // under the collar
	if (y >= CH - 2) c = mix(c, lining ? D : M, y === CH - 1 ? 0.75 : 0.45); // hem
	const n = (rnd() - 0.5) * 8; // a little cloth grain
	const i = (y * CW + x) * 4;
	cape[i] = Math.max(0, Math.min(255, Math.round(c[0] + n)));
	cape[i + 1] = Math.max(0, Math.min(255, Math.round(c[1] + n * 0.1)));
	cape[i + 2] = Math.max(0, Math.min(255, Math.round(c[2] + n * 0.3)));
	cape[i + 3] = 255;
}
PNG.write(OUT_CAPE, CW, CH, cape);
console.log('cape:', OUT_CAPE);
