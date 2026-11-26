// v0.17.7 Cyborg's look: the Teen Titans Cyborg skin from PlanetMinecraft with Victor Stone the person taken out, so the wearer's own
// skin shows between the Cyborg parts.
//
//   node scratchpad/v0177/make_cyborg_skin.js
//
// History: the user asked "Make this the new cyborg skin please: https://www.planetminecraft.com/skin/teen-titans-cyborg-4002965/
// Remove the regular skin from this skin so that the player can see their skin with the cyborg parts". It replaces the v0.16.5
// NovaSkin 128x128 HD skin (scratchpad/gen_cyborg_v0165.js).
//
// Source (NEVER modified): scratchpad/v0177/cyborg_skin_original.png -- "CYBORG Teen Titans / Justice League" on PlanetMinecraft
// (page model: Steve = WIDE 4-pixel arms), the 64x64 file behind the page's download button:
//   static.planetminecraft.com/files/resource_media/skin/cyborg-11202576.png
// (planetminecraft.com sits behind a Cloudflare check: curl gets a 403, so it was fetched from inside a real browser tab.)
// Borrowed skin: it ships publicly like the others (CLAUDE.md), the source is noted here and in CyborgRender.
//
// Writes (all 64x64, the vanilla player-skin UV layout, both layers -- CyborgRender draws them on a PlayerModel a hair larger
// than the wearer's):
//   textures/entity/cyborg/cyborg.png             the plates, wide arms
//   textures/entity/cyborg/cyborg_slim.png        the same remapped to the slim (3-pixel arm) layout for slim players
//   textures/entity/cyborg/cyborg_glow.png        the red cybernetic eye alone (emissive pass); the eye is on the head, so one
//                                                 glow map serves both arm widths
// and the offline previews in docs/screenshots/v0177/cyborg_skin/.
//
// Texel rules (from the source's own palette -- 30 opaque colours; see the tables below):
//  * Victor's skin is four browns (110,33,32 / 129,45,40 / 147,56,44 / 158,68,49): the human half of the face, the strip over the
//    scalp from the forehead to the back of the head, the chin underside, the back of the neck, the band of bare upper arm under
//    each blue shoulder plate (both arms), and a few brown texels on the hat layer's face. All cleared, on BOTH layers.
//  * His human features drawn on the face, not in the brown set: the brow and eye (9,11) (10,11) (9,12) (10,12) and the mouth
//    (11,14). Cleared.
//  * The cybernetic eye stays: its red lens (13,12) (14,12) and dark-red housing (13,11) (14,11). It is the glow map.
//  * Everything else is Cyborg -- the blue plating and its highlights, the white / silver armour, the grey and near-black joints
//    and trims, the grey jaw plate (x 11-15 on the bottom face rows). Kept as is.
//  * Four 25%-alpha marker texels in the unused corner (62-63, 30-31) -- the skin site's signature, not on the model -- cleared.
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');
const preview = require('./skin_preview.js');

const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(__dirname, 'cyborg_skin_original.png');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/cyborg');
const SHOTS = path.join(ROOT, 'docs/screenshots/v0177/cyborg_skin');

const img = PNG.read(SRC);
const W = img.W, H = img.H;
if (W !== 64 || H !== 64) throw new Error('expected a 64x64 skin, got ' + W + 'x' + H);
const src = Buffer.from(img.px); // untouched copy to read from
const px = Buffer.from(img.px);

const key = (b, x, y) => { const i = (y * W + x) * 4; return b[i] + ',' + b[i + 1] + ',' + b[i + 2]; };
const alpha = (b, x, y) => b[(y * W + x) * 4 + 3];
const clear = (b, x, y) => { const i = (y * W + x) * 4; b[i] = b[i + 1] = b[i + 2] = b[i + 3] = 0; };

// ---- the source's palette, by what it is ----
const SKIN = new Set(['110,33,32', '129,45,40', '147,56,44', '158,68,49']);
const HUMAN_FEATURES = [[9, 11], [10, 11], [9, 12], [10, 12], [11, 14]];
const EYE = [[13, 11], [14, 11], [13, 12], [14, 12]];

let cleared = 0;
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		if (alpha(src, x, y) === 0) continue;
		if (alpha(src, x, y) < 255 || SKIN.has(key(src, x, y))) {
			clear(px, x, y);
			cleared++;
		}
	}
}
for (const [x, y] of HUMAN_FEATURES) {
	if (alpha(px, x, y)) cleared++;
	clear(px, x, y);
}
console.log('cleared ' + cleared + ' texels');

// ---- the glow: the eye alone ----
const glow = Buffer.alloc(W * H * 4);
for (const [x, y] of EYE) {
	const i = (y * W + x) * 4;
	if (!src[i + 3]) throw new Error('eye texel ' + x + ',' + y + ' is empty');
	glow.set([src[i], src[i + 1], src[i + 2], 255], i);
}

// ---- (v0.17.7 round 2: the head cover is no longer used -- Cyborg is composited into the wearer's skin at normal size) ----
const cover = Buffer.alloc(W * H * 4);
for (let y = 0; y < 16; y++) {
	for (let x = 0; x < 32; x++) {
		const b = (y * W + x) * 4, h = (y * W + x + 32) * 4;
		const top = px[h + 3] ? h : (px[b + 3] ? b : -1);
		if (top >= 0) cover.set(px.subarray(top, top + 4), b);
	}
}

// ---- slim: drop the arm column against the body on the front, back, top and bottom faces ----
// A 4-wide arm cube at (u,v), d = 4: right face u..u+4, front u+4..u+8, left u+8..u+12, back u+12..u+16, top u+4..u+8, bottom
// u+8..u+12. Slim (w = 3): right u..u+4, front u+4..u+7, left u+7..u+11, back u+11..u+14, top u+4..u+7, bottom u+7..u+10.
// Right arm (outer side at -x): the body-side column is the LAST of front / top / bottom and the FIRST of back. Left arm: mirrored.
function slimOf(buf) {
	const out = Buffer.from(buf);
	const copy = (fx, fy, tx, ty) => { const f = (fy * W + fx) * 4, t = (ty * W + tx) * 4; out.set(buf.subarray(f, f + 4), t); };
	const arms = [[40, 16, true], [40, 32, true], [32, 48, false], [48, 48, false]];
	for (const [u, v, right] of arms) {
		// wipe the cube's area first
		for (let y = v; y < v + 16; y++) for (let x = u; x < u + 16; x++) clear(out, x, y);
		const keep4 = drop => [0, 1, 2, 3].filter(i => i !== drop); // 3 of 4 source columns
		const dropFront = right ? 3 : 0, dropBack = right ? 0 : 3, dropTop = right ? 3 : 0;
		for (let y = v; y < v + 4; y++) {
			keep4(dropTop).forEach((c, i) => copy(u + 4 + c, y, u + 4 + i, y)); // top
			keep4(dropTop).forEach((c, i) => copy(u + 8 + c, y, u + 7 + i, y)); // bottom
		}
		for (let y = v + 4; y < v + 16; y++) {
			for (let i = 0; i < 4; i++) copy(u + i, y, u + i, y); // right side
			keep4(dropFront).forEach((c, i) => copy(u + 4 + c, y, u + 4 + i, y)); // front
			for (let i = 0; i < 4; i++) copy(u + 8 + i, y, u + 7 + i, y); // left side
			keep4(dropBack).forEach((c, i) => copy(u + 12 + c, y, u + 11 + i, y)); // back
		}
	}
	return out;
}
const slim = slimOf(px);

PNG.write(path.join(TEX, 'cyborg.png'), W, H, px);
PNG.write(path.join(TEX, 'cyborg_slim.png'), W, H, slim);
PNG.write(path.join(TEX, 'cyborg_glow.png'), W, H, glow);
console.log('wrote cyborg.png, cyborg_slim.png, cyborg_glow.png');

// ---- previews: original vs processed (pink checker = cleared), then over a plain wearer skin, wide and slim ----
fs.mkdirSync(SHOTS, { recursive: true });
const asImg = buf => ({ W, H, px: buf });
preview.render([asImg(src), asImg(px)], path.join(SHOTS, 'original_vs_processed.png'));
const WEARER = path.join(__dirname, 'cyborg_preview_wearer.png');
if (fs.existsSync(WEARER)) {
	preview.render([asImg(px)], path.join(SHOTS, 'processed_on_wearer_wide.png'), { underlay: WEARER });
}
const WEARER_SLIM = path.join(__dirname, 'cyborg_preview_wearer_slim.png');
preview.render([asImg(slim)], path.join(SHOTS, 'processed_slim.png'), { slim: true });
if (fs.existsSync(WEARER_SLIM)) {
	preview.render([asImg(slim)], path.join(SHOTS, 'processed_on_wearer_slim.png'), { slim: true, underlay: WEARER_SLIM });
}
console.log('previews in ' + path.relative(ROOT, SHOTS));
