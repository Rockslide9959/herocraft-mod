// v0.17.7 Thor's Armour look: the full suit from a PlanetMinecraft skin with Thor himself taken out, + the red cape texture.
//
//   node scratchpad/v0177/make_thor_skin.js
//
// History: the user asked "Use this skin: https://www.planetminecraft.com/skin/thor-avengers-6321094/ Remove the cape from the back of
// the skin and add a red cape model". A later "remove the skin from it so players can see their skin underneath and keep the helmet"
// was first misread as "helmet only"; the user then corrected it: "no i still want the suit and armour i was just saying remove thors
// actual skin form the suit and keep the helmet". So: every piece of armour stays (tunic, shoulder plates, bracers, belt, greaves,
// boots, the winged helmet); Thor the person (face, eyes, beard, hair, bare arms, hands, neck) is cleared to transparent so the
// wearer's own face and skin show through; the painted cape is gone and the back is repainted as armour; the cape is the 3D cloth one
// (client ThorCapeLayer).
//
// Source (NEVER modified): scratchpad/v0177/thor_skin_original.png -- "Thor | Avengers" on PlanetMinecraft, the 64x64 wide-arm skin
// file behind the page's download button (static.planetminecraft.com/files/resource_media/skin/steve-planetminecraft-com-19528531.png).
//
// Writes:
//   src/main/resources/assets/projecthero/textures/armor/thor.png      Thor's Armour texture (player-skin UV layout, geo/thor.geo.json)
//   src/main/resources/assets/projecthero/textures/entity/thor_cape.png the 3D cape (FlowingCapeLayer 64x48 layout), in the skin's reds
//
// Texel rules (decided from the source's own palette, see the tables below):
//  * Head (rows 0-15, both layers): only the helmet -- the silver crown and brow band, the steel cheek guards and the grey wing roots on
//    the base layer, the white wings and the silver crest on the hat layer. Face, eyes, brows, blond hair/beard and the neck opening are
//    cleared.
//  * Body / arms / legs (rows 16-63): skin tones (bare upper arms, hands, the neck) are cleared. Hair (Thor's long blond hair lies over
//    the shoulders and down the back of the tunic on the base layer) is repainted as tunic: dark steel on the sides and shoulders, and
//    on the back the tunic is rebuilt -- collar, the four blue discs and the centre seam, mirroring the front -- down to the original
//    belt and skirt rows.
//  * The painted cape (all on the second layer: the jacket back/sides/bottom, the backs of the legs and arms, the shoulders): cleared
//    from the body and legs; on the shoulders the cape's red becomes dark-steel shoulder plates; the red band at the bracer row stays
//    (it is the bracer's own trim). The jacket back gets the front's disc highlights and belt studs, mirrored.
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
const src = Buffer.from(px); // untouched copy to read from

const key = (x, y) => { const i = (y * W + x) * 4; return src[i + 3] === 0 ? null : src[i] + ',' + src[i + 1] + ',' + src[i + 2]; };
const set = (x, y, rgb) => { const i = (y * W + x) * 4; px[i] = rgb[0]; px[i + 1] = rgb[1]; px[i + 2] = rgb[2]; px[i + 3] = 255; };
const clear = (x, y) => { const i = (y * W + x) * 4; px[i] = px[i + 1] = px[i + 2] = px[i + 3] = 0; };
const rgbOf = k => k.split(',').map(Number);
const inRect = (x, y, x0, y0, x1, y1) => x >= x0 && x < x1 && y >= y0 && y < y1;

// ---- the source's palette, by what it is ----
// the helmet: silvers / steel blues, the grey wing roots, and the wings' whites
const HELMET = new Set([
	'203,212,219', '179,193,203', '222,227,231', '186,177,194', '147,171,180', '115,147,161', // silver, steel, wing roots
	'255,255,255', '237,233,239', '212,205,216', // wings
]);
// Thor's skin: the face / neck / bare arms / hands, highlights and shadows
const SKIN = new Set(['243,169,136', '243,169,135', '243,169,137', '243,169,134', '249,201,171', '252,216,194', '255,229,214',
	'229,123,122', '229,124,122']);
// Thor's hair and beard (blond, and the darker gold under it)
const HAIR = new Set(['209,162,95', '241,222,170', '246,233,189', '226,199,134']);
// the painted cape's reds (the same reds also trim the bracers on the base layer, which stay)
const RED = new Set(['244,16,61', '219,16,57', '190,15,52', '158,14,41', '144,10,39']);
// the tunic's own colours, used for the repaint
const TUNIC = [16, 18, 28], EDGE = [25, 28, 44], SEAM = [34, 38, 60], DISC_DARK = [43, 48, 76], DISC = [105, 185, 243];
const PLATE = { '244,16,61': DISC_DARK, '219,16,57': SEAM, '190,15,52': EDGE, '158,14,41': EDGE, '144,10,39': EDGE };

const counts = { helmet: 0, skin: 0, hair: 0, cape: 0, plate: 0, back: 0, kept: 0 };

// ---- 1. the head: only the helmet ----
for (let y = 0; y < 16; y++) for (let x = 0; x < W; x++) {
	const k = key(x, y);
	if (k === null) continue;
	const neck = y < 8 && x >= 16 && x < 24; // the head's bottom face is the neck opening
	const face = x >= 8 && x < 16 && y >= 11; // the face below the brow band: the eyes are as white as the wings
	if (HELMET.has(k) && !neck && !face) {
		counts.helmet++;
	} else {
		clear(x, y);
		counts[SKIN.has(k) ? 'skin' : 'hair']++;
	}
}

// the second-layer boxes of the body, arms and legs (where the cape was painted)
const OVER_BODY = [16, 32, 40, 48], OVER_RLEG = [0, 32, 16, 48], OVER_LLEG = [0, 48, 16, 64];
const OVER_RARM = [40, 32, 56, 48], OVER_LARM = [48, 48, 64, 64];
const BODY_BACK = [32, 20, 40, 32]; // base layer, back face of the body

// ---- 2. body, arms and legs ----
for (let y = 16; y < H; y++) for (let x = 0; x < W; x++) {
	const k = key(x, y);
	if (k === null) continue;
	if (inRect(x, y, ...BODY_BACK)) continue; // rebuilt below
	if (SKIN.has(k)) { clear(x, y); counts.skin++; continue; }
	if (HAIR.has(k)) {
		// the long hair over the shoulders and down the sides of the tunic: tunic there (the body's top face and front edges too)
		if (inRect(x, y, 16, 16, 40, 32)) {
			const frontEdge = y >= 20 && (x === 20 || x === 27);
			set(x, y, frontEdge ? EDGE : TUNIC);
			counts.hair++;
		} else {
			clear(x, y);
			counts.hair++;
		}
		continue;
	}
	if (RED.has(k)) {
		const overArm = inRect(x, y, ...OVER_RARM) || inRect(x, y, ...OVER_LARM);
		const armTop = inRect(x, y, ...OVER_RARM) ? y - 32 : y - 48; // rows into the arm's second-layer box
		if (overArm && armTop < 6) { set(x, y, PLATE[k]); counts.plate++; continue; } // the cape over the shoulder -> shoulder plate
		if (overArm && armTop === 11) { counts.kept++; continue; } // the bracer's red trim
		if (inRect(x, y, ...OVER_BODY) || inRect(x, y, ...OVER_RLEG) || inRect(x, y, ...OVER_LLEG) || overArm) {
			clear(x, y); counts.cape++; continue; // the painted cape
		}
		counts.kept++; // base-layer red: the bracers
		continue;
	}
	counts.kept++;
}

// ---- 3. the back of the tunic, rebuilt (base layer, u 32..39, v 20..31) ----
// Rows 20-25 were Thor's hair; rows 26-31 (centre seam, belt, skirt) are the skin's own and stay. The new rows mirror the front
// (u 20..27): the collar, the four blue discs and the dark seam between them.
{
	const T = TUNIC, E = EDGE, S = SEAM, F = DISC_DARK, B = DISC;
	const BACK_ROWS = {
		20: [T, E, S, S, S, S, E, T],
		21: [E, S, S, S, S, S, S, E],
		22: [E, B, B, S, S, B, B, E],
		23: [E, B, B, F, F, B, B, E],
		24: [E, S, F, S, S, F, S, E],
		25: [T, B, B, E, E, B, B, T],
	};
	for (let y = 20; y < 32; y++) for (let i = 0; i < 8; i++) {
		if (BACK_ROWS[y]) { set(32 + i, y, BACK_ROWS[y][i]); counts.back++; }
		else if (key(32 + i, y) !== null && (HAIR.has(key(32 + i, y)) || SKIN.has(key(32 + i, y)))) { set(32 + i, y, T); counts.back++; }
	}
}
// the jacket back (second layer, u 32..39, v 36..47) gets the front's disc highlights and belt studs (u 20..27), mirrored
for (const y of [38, 39, 40, 41, 42, 44]) for (let fx = 20; fx < 28; fx++) {
	const k = key(fx, y);
	if (k === null || RED.has(k)) continue;
	set(32 + (27 - fx), y, rgbOf(k));
	counts.back++;
}

PNG.write(OUT_SUIT, W, H, px);
console.log('suit:', OUT_SUIT, JSON.stringify(counts));

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
