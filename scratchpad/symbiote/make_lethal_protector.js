// v0.17.5 Symbiote "Lethal Protector" suit style (user: "add another suit model and make it this skin, keep the spider logo and the
// eyes but make the mouth animated, the tongue moves and stuff").
//
//   node scratchpad/symbiote/make_lethal_protector.js
//
// Reads docs/reference/symbiote/venom_lethal_protector.png -- the user's PlanetMinecraft skin "Venom: Lethal Protector"
// (https://www.planetminecraft.com/skin/venom-lethal-protector-7075992/, author not shown, no licence stated: see SOURCE.txt next to
// it). It is an HD skin, 128 x 128 in the 64 x 64 player-skin layout at 2x, which is exactly how the Classic suit's texture is laid out
// (geo declared 64 wide), so it drops onto the same rig.
//
// Writes (src/main/resources/assets/projecthero/):
//   textures/armor/symbiote_host_lethal.png       the skin, ready to wear: spider logo + eyes kept, the painted grin replaced by a dark
//        throat where the 3D jaw / teeth / tongue go, the goo patches the spine nodes and the suit-up tendrils sample copied in from the
//        Classic texture, and a small atlas for the mouth parts (jaw, teeth, gum, tongue) in unused skin texels
//   textures/armor/symbiote_host_lethal_tint.png  its TINT MASK (same format as symbiote_host_tint.png: R shade, G gloss, B channel
//        0 body / 128 emblem / 255 eyes, A coverage) so the style takes the player's colours. A = 0 marks texels that keep the skin's own
//        colour whatever the player picks: the inside of the mouth (throat, teeth, tongue, floor) and the saliva (v0.17.5 round 2: was green
//        drool, repainted). Round 3: the lips and the gum strip are body-coloured lip shades (lipShade), the jaw's outside takes the suit colour
//   textures/armor/symbiote_host_lethal_fx.png    the sheen map (R sweep coordinate, G sheen strength, B = 0: this style has no veins)
//   geo/symbiote_host_lethal.geo.json             the Classic rig (read from geo/symbiote_host.geo.json) + the mouth: lp_jaw (hinged
//        lower jaw with its teeth), lp_upper (upper teeth + gum), lp_tongue_0..5 (a chain of six tapering segments in the jaw)
//
// The three textures are made from a borrowed skin whose licence is unchecked, so they are .gitignored (local builds only, like the
// Gyro and Sentry comics skins); without them the game silently falls back to the mod's own Classic suit art (since round 3 the
// Lethal Protector is the only suit -- the Classic art is the public-build fallback, not a choice). The geo is original and committed.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const ASSETS = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero');
const SKIN = path.join(ROOT, 'docs', 'reference', 'symbiote', 'venom_lethal_protector.png');
const W = 128, H = 128, S = 2;

if (!fs.existsSync(SKIN)) {
	console.error('missing ' + SKIN + ' -- save the PlanetMinecraft skin there first');
	process.exit(1);
}
const skin = PNG.read(SKIN);
if (skin.W !== W || skin.H !== H) {
	console.error('expected a 128 x 128 HD skin, got ' + skin.W + ' x ' + skin.H);
	process.exit(1);
}
const classic = PNG.read(path.join(ASSETS, 'textures/armor/symbiote_host.png'));
const classicMask = PNG.read(path.join(ASSETS, 'textures/armor/symbiote_host_tint.png'));
const classicFx = PNG.read(path.join(ASSETS, 'textures/armor/symbiote_host_fx.png'));

const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const mix = (c0, c1, t) => [0, 1, 2].map((i) => Math.round(lerp(c0[i], c1[i], clamp(t))));
const lum = (c) => (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255;
function hash(x, y) { let h = (x * 374761393 + y * 668265263) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }

// 0) v0.17.5 round 2 (user: "remove the green from the mouth"): the skin paints slime-green drool on the hat layer round the grin and
// down the cheeks (22 texels, half-transparent green). Repainted as glistening saliva -- the dark skin beneath lifted toward a cool
// pale highlight, brightest near the top of each run -- fully opaque, and kept in its own colour whatever suit colours are picked.
const isGreen = (r, g, b) => g > r + 12 && g > b + 5;
const saliva = new Uint8Array(W * H);
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const i = (y * W + x) * 4;
	if (!skin.px[i + 3] || !isGreen(skin.px[i], skin.px[i + 1], skin.px[i + 2])) continue;
	const bx = x >= 64 && y < 32 ? x - 64 : x, bi = (y * W + bx) * 4; // the base-layer texel under a hat texel
	const under = [skin.px[bi], skin.px[bi + 1], skin.px[bi + 2]];
	const above = y > 0 && isGreen(skin.px[i - W * 4], skin.px[i - W * 4 + 1], skin.px[i - W * 4 + 2]);
	const t = (above ? 0.42 : 0.62) + hash(x, y) * 0.16;
	const c = [0, 1, 2].map((k) => Math.round(under[k] + ([196, 208, 224][k] - under[k]) * t));
	skin.px[i] = c[0]; skin.px[i + 1] = c[1]; skin.px[i + 2] = c[2]; skin.px[i + 3] = 255;
	saliva[y * W + x] = 1;
}

const out = Buffer.from(skin.px);                 // the wearable texture
const keep = new Uint8Array(W * H);               // 1 = keeps its own colour (mask A = 0)
for (let i = 0; i < W * H; i++) keep[i] = saliva[i]; // the saliva stays pale on any colour
const get = (img, x, y) => { const i = (y * W + x) * 4; return [img[i], img[i + 1], img[i + 2], img[i + 3]]; };
const put = (x, y, c, a = 255) => { const i = (y * W + x) * 4; out[i] = c[0]; out[i + 1] = c[1]; out[i + 2] = c[2]; out[i + 3] = a; };

// ---------------------------------------------------------------- texel <-> head point (texel coords, 128 space)
// base head faces: west (wearer's right) u 0..16, front 16..32, east 32..48, rows v 16..32; the hat layer is the same at u + 64.
const yOf = (v) => 32 - (v - 16 + 0.5) / S;                 // model y of a side-face row
const xOfFront = (u) => -4 + (u - 16 + 0.5) / S;            // front face: fu 0 -> minX
const zOfWest = (u) => -4 + (16 - u - 0.5) / S;             // west: fu 0 -> back
const zOfEast = (u) => -4 + (u - 32 + 0.5) / S;             // east: fu 0 -> front
function composite(x, y) { // the skin as seen: hat over base
	const b = get(skin.px, x, y), h = get(skin.px, x + 64, y);
	const a = h[3] / 255;
	return [0, 1, 2].map((i) => Math.round(h[i] * a + b[i] * (1 - a)));
}

// ---------------------------------------------------------------- the mouth: geometry shared by the texture and the geo
const JAW = { x0: -4.74, x1: 4.74, y0: 23.32, y1: 25.1, z0: -4.76, z1: -0.8 };
// v0.17.5 round 3 (user: "the top of the mouth is kinda going into the eyes of the suit"): the mouth's top edge was 27.55, level with
// the bottom of the lenses (they reach down to y 27.0 at the sides, face rows 8-9). Now 26.0 (the top of face row 12): the skin's black
// between the lenses and its painted top teeth (rows 8-11) are painted over as suit, so a full pixel of suit (rows 10-11) always separates
// the eyes from the mouth, and both rows of teeth are TEETH_SCALE as long so they still interlock in the smaller closed gap (jaw top 25.1).
// SymbioteMouth.UPPER_TIP_Y / LOWER_TIP_Y follow.
const MOUTH_TOP = 26.0;       // the upper teeth hang from here (the top of the open mouth)
const UPPER_SCALE = 0.6, LOWER_SCALE = 0.6;
const MOUTH_ROW = 12;         // the first face row of the open mouth (y 26.0 .. 25.5)
// v0.17.5 round 3 (user: "make the lip colour match the colour of the symbiote"): lip / gum texels take the body colour, shaded.
// 0 = not a lip; else the tint-mask shade (body channel) + 1. Throat, teeth and tongue keep their own colours (keep = 1).
const lipShade = new Uint8Array(W * H);
const lipOf = (c) => 1 + Math.round(clamp(0.36 + (lum(c) - 0.25) * 1.3, 0.28, 0.7) * 255);
const THROAT_IN = [56, 11, 24], THROAT_OUT = [15, 6, 12], THROAT_SIDE = [24, 8, 16];
const LIP = [187, 85, 102], LIP_DARK = [134, 46, 66];
const isLip = (c) => c[0] > 140 && c[0] > c[1] * 1.6;
const isTooth = (c) => c[0] > 180 && c[1] > 120 && !isLip(c);
const isDark = (c) => lum(c) < 0.16;
function throat(x, y, side) {
	if (side) return mix(THROAT_SIDE, THROAT_OUT, hash(x, y) * 0.5);
	const d = Math.hypot(x / 4.2, (y - 26.2) / 2.2);
	const c = mix(THROAT_IN, THROAT_OUT, d);
	const n = (hash(Math.round(x * 2), Math.round(y * 2)) - 0.5) * 10;
	return c.map((v) => clamp(Math.round(v + n), 0, 255));
}

// 1) the head: where the jaw sits and where the mouth opens, paint a throat on the base layer and clear the hat layer
const SUIT_REF = composite(16, 16 + 10).slice(0, 3); // round 3: a plain suit texel (face edge, row 10) for painting over the old mouth top
for (let v = 16; v < 32; v++) {
	const y = yOf(v);
	// front
	for (let u = 16; u < 32; u++) {
		const x = xOfFront(u);
		const base = get(skin.px, u, v), hat = get(skin.px, u + 64, v);
		const row = v - 16;     // 0..15 down the face
		let mouth = false;
		if (y <= JAW.y1 + 0.05) mouth = true;                                         // behind the jaw
		else if (row >= MOUTH_ROW && Math.abs(x) <= 3.3) mouth = true;              // the skin's teeth rows (top edge = MOUTH_TOP)
		else if (row >= 8 && Math.abs(x) <= 4 && (isDark(composite(u, v)) || (row === MOUTH_ROW - 1 && composite(u, v)[3] !== 0
				&& (isTooth(composite(u, v)) || isLip(composite(u, v)) || lum(composite(u, v)) > 0.3)))) {
			// round 3: the black top of the painted open mouth between the lenses, and its painted top teeth -> suit
			put(u, v, mix(SUIT_REF, [0, 0, 0], 0.1 + hash(u, v) * 0.25));
			put(u + 64, v, [0, 0, 0], 0);
			continue;
		}
		if (!mouth) continue;
		put(u, v, throat(x, y, false));
		keep[v * W + u] = 1;
		if (hat[3] && (isDark(hat) || !isTooth(hat) || row >= MOUTH_ROW)) {
			// the hat's mouth bits (black, pink tongue, drool) go; its eye whites above stay
			if (!(row <= 9 && lum(hat) > 0.6)) put(u + 64, v, [0, 0, 0], 0);
		}
	}
	// sides: the inner cheek behind the jaw
	for (const [u0, u1, zOf, hatOff] of [[0, 16, zOfWest, 64], [32, 48, zOfEast, 64]]) {
		for (let u = u0; u < u1; u++) {
			const z = zOf(u);
			if (y > JAW.y1 + 0.05 || z > JAW.z1 + 0.1) continue;
			put(u, v, throat(u, v, true));
			keep[v * W + u] = 1;
			put(u + hatOff, v, [0, 0, 0], 0);
		}
	}
}
// the skin's lower-lip corners that stay on the head: round 3 -- they take the body colour (shaded as lips); the saliva was marked above
for (let v = 16; v < 32; v++) for (let u = 0; u < 48; u++) {
	for (const uu of [u, u + 64]) {
		const c = get(out, uu, v);
		if (!c[3] || keep[v * W + uu]) continue;
		if (isLip(c)) lipShade[v * W + uu] = lipOf(c);
	}
}

// 2) the goo patches the Classic rig's caps / drips / spine nodes sample (skin (24,0)-(40,8) and (56,16)-(64,48)), from the Classic texture
const PATCHES = [[48, 0, 80, 16], [112, 32, 128, 96]];
for (const [x0, y0, x1, y1] of PATCHES) for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
	const c = get(classic.px, x, y);
	put(x, y, c, c[3]);
}

// 3) the mouth atlas. Block J = skin (0,0)-(8,8): the jaw. Block M = skin (56,0)-(64,8): teeth, gum, tongue. (Both unused by skins.)
const J = { u: 0, v: 0 }, M = { u: 56, v: 0 };
function fillRect(u, v, w, h, fn) { // skin-px rect, fn(tx, ty, tw, th) -> [r,g,b] (texel indices inside the rect)
	const X0 = Math.round(u * S), Y0 = Math.round(v * S), TW = Math.round(w * S), TH = Math.round(h * S);
	for (let ty = 0; ty < TH; ty++) for (let tx = 0; tx < TW; tx++) {
		const c = fn(tx, ty, TW, TH);
		put(X0 + tx, Y0 + ty, c.slice(0, 3), c[3] == null ? 255 : c[3]);
		keep[(Y0 + ty) * W + X0 + tx] = 1;
	}
}
// the jaw's outside is the skin that was there (hat over base), with the painted teeth turned into lip (the 3D teeth stand on it)
function skinAt(face, a, y) { // face 'front' a = x; 'west' / 'east' a = z
	let u;
	if (face === 'front') {
		if (a < -4) u = 15; else if (a > 4) u = 32; else u = Math.min(31, 16 + Math.floor((a + 4) * S));
	} else if (face === 'west') {
		u = clamp(Math.floor(16 - (a + 4) * S), 0, 15);
	} else {
		u = clamp(32 + Math.floor((a + 4) * S), 32, 47);
	}
	const v = clamp(16 + Math.floor((32 - y) * S), 16, 31);
	let c = composite(u, v);
	if (isTooth(c)) c = LIP;
	return c;
}
const BODY_DARK = [21, 21, 42];
const JW = JAW.x1 - JAW.x0, JH = JAW.y1 - JAW.y0, JD = JAW.z1 - JAW.z0;
fillRect(J.u, J.v, 8, 2, (tx, ty, tw, th) => {                       // north (front): fu 0 -> minX
	const x = JAW.x0 + JW * (tx + 0.5) / tw, y = JAW.y1 - JH * (ty + 0.5) / th;
	const c = skinAt('front', x, y);
	return ty === 0 ? mix(c, LIP, isLip(c) ? 0.25 : 0) : c;
});
fillRect(J.u, J.v + 2, 8, 2, () => BODY_DARK);                        // south (inside the head)
fillRect(J.u, J.v + 4, 4, 2, (tx, ty, tw, th) => {                   // east: fu 0 -> front
	const z = JAW.z0 + JD * (tx + 0.5) / tw, y = JAW.y1 - JH * (ty + 0.5) / th;
	return skinAt('east', z, y);
});
fillRect(J.u + 4, J.v + 4, 4, 2, (tx, ty, tw, th) => {               // west: fu 0 -> back
	const z = JAW.z1 - JD * (tx + 0.5) / tw, y = JAW.y1 - JH * (ty + 0.5) / th;
	return skinAt('west', z, y);
});
fillRect(J.u, J.v + 6, 8, 1, (tx, ty, tw) => {                        // up: the floor of the mouth (wet gum, a darker tongue bed)
	const x = (tx + 0.5) / tw - 0.5;
	return mix([120, 32, 52], [64, 14, 28], Math.abs(x) * 1.6 + (ty ? 0.25 : 0));
});
fillRect(J.u, J.v + 7, 8, 1, () => BODY_DARK);                        // down (under the chin)
// round 3: the jaw's outside (front, inside, sides, under the chin) is suit -- it takes the player's colours like the head it continues
// (it used to keep the skin's colours, so a coloured suit had a black chin and pink lips); its lip texels become body-coloured lips.
// Only the floor of the mouth (J.v + 6) keeps its own colour.
for (let ty = 0; ty < 16; ty++) for (let tx = 0; tx < 16; tx++) {
	if (ty === 12 || ty === 13) continue; // the floor
	const i = (J.v * S + ty) * W + J.u * S + tx;
	keep[i] = 0;
	const c = get(out, J.u * S + tx, J.v * S + ty);
	if (isLip(c)) lipShade[i] = lipOf(c);
}
// teeth: a root-to-tip ramp (upper teeth hang root-up, lower stand root-down)
const TOOTH_ROOT = [190, 168, 138], TOOTH_MID = [236, 228, 206], TOOTH_TIP = [252, 250, 242];
const toothRamp = (t) => t < 0.5 ? mix(TOOTH_ROOT, TOOTH_MID, t * 2) : mix(TOOTH_MID, TOOTH_TIP, (t - 0.5) * 2);
fillRect(M.u, M.v, 0.5, 2, (tx, ty, tw, th) => toothRamp((ty + 0.5) / th));          // upper tooth (root at the top)
fillRect(M.u + 0.5, M.v, 0.5, 2, (tx, ty, tw, th) => toothRamp(1 - (ty + 0.5) / th)); // lower tooth (root at the bottom)
fillRect(M.u + 1, M.v, 1, 1, (tx, ty) => ty ? [92, 20, 38] : [128, 36, 56]);         // gum
// round 3: the gum strip is the upper lip band seen from the front -> body colour, a little darker than the body
for (let ty = 0; ty < 2; ty++) for (let tx = 0; tx < 2; tx++) {
	const i = (M.v * S + ty) * W + (M.u + 1) * S + tx;
	keep[i] = 0;
	lipShade[i] = 1 + Math.round((ty ? 0.3 : 0.4) * 255);
}
fillRect(M.u + 2, M.v, 2, 2, (tx, ty, tw) => {                                        // tongue top: pink, a groove down the middle
	const e = Math.abs((tx + 0.5) / tw - 0.5) * 2;
	if (e < 0.3) return [158, 44, 66];
	return mix([214, 92, 114], [176, 58, 82], (e - 0.3) / 0.7 + hash(tx, ty) * 0.15);
});
fillRect(M.u + 4, M.v, 2, 1, (tx) => mix([170, 54, 78], [150, 42, 66], hash(tx, 3)));  // tongue sides
fillRect(M.u + 4, M.v + 1, 2, 1, () => [112, 28, 48]);                                // tongue underside
fillRect(M.u + 6, M.v, 1, 1, () => [222, 104, 124]);                                  // tongue tip / segment ends
fillRect(M.u + 6, M.v + 1, 1, 1, () => [56, 10, 22]);                                 // spare: the darkest throat

// ---------------------------------------------------------------- the tint mask (so the style takes the player's colours)
// Classic colours, as in SymbioteColours.CLASSIC: shade is solved so the Classic bake of each texel comes back close to the skin.
const CLASSIC = { 0: [0x14, 0x16, 0x1F], 128: [0xF2, 0xF4, 0xF8], 255: [0xF4, 0xF6, 0xFA] };
function solve(c, ch) {
	const base = CLASSIC[ch].map((v) => v / 255);
	const m = Math.max(...base);
	const hue = m < 0.03 ? [0.82, 0.86, 1.0] : base.map((v) => v / m);
	const Lc = lum(CLASSIC[ch]), Lp = lum(c);
	const liftC = base.map((v, i) => Math.max((0.5 + 0.5 * hue[i]) * 0.86, v));
	const Ll = 0.299 * liftC[0] + 0.587 * liftC[1] + 0.114 * liftC[2];
	const specC = liftC.map((v, i) => Math.max(0.72 + 0.28 * hue[i], v));
	const Ls = 0.299 * specC[0] + 0.587 * specC[1] + 0.114 * specC[2];
	let s, g = 0;
	if (Lp <= Lc) s = 0.5 * Lp / Math.max(Lc, 1e-3);
	else if (Lp <= Ll || Ll <= Lc) s = 0.5 + 0.5 * clamp((Lp - Lc) / Math.max(Ll - Lc, 1e-3));
	else { s = 1; g = clamp((Lp - Ll) / Math.max(Ls - Ll, 1e-3)); }
	return [Math.round(clamp(s) * 255), Math.round(g * 255)];
}
const tint = Buffer.alloc(W * H * 4);
const fx = Buffer.alloc(W * H * 4);
const inHead = (x, y) => y < 32 && x < 128;           // head base (0..64) and hat (64..128), rows 0..32
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const i = (y * W + x) * 4;
	const c = get(out, x, y);
	if (!c[3]) continue;
	const patch = PATCHES.some(([x0, y0, x1, y1]) => x >= x0 && x < x1 && y >= y0 && y < y1);
	if (patch) { // the goo patches: the Classic mask / fx as they are
		for (let k = 0; k < 4; k++) { tint[i + k] = classicMask.px[i + k]; fx[i + k] = classicFx.px[i + k]; }
		continue;
	}
	if (lipShade[y * W + x]) { // round 3: lips / gum -> the body colour, shaded as lips, a little wet
		const s = lipShade[y * W + x] - 1;
		tint[i] = s; tint[i + 1] = s > 140 ? 40 : 0; tint[i + 2] = 0; tint[i + 3] = 255;
		continue;
	}
	if (keep[y * W + x]) continue; // mask A = 0: keeps its own colour; no sheen on the mouth
	const mx = Math.max(c[0], c[1], c[2]), mn = Math.min(c[0], c[1], c[2]);
	const sat = mx ? (mx - mn) / mx : 0;
	const L = lum(c);
	if (sat > 0.45 && L > 0.18) continue; // the odd saturated texel (the blue soles): left as painted
	let ch = 0;
	if (L > 0.5 && sat < 0.3 && !(x < 16 && y < 16)) ch = inHead(x, y) ? 255 : 128; // (the jaw block J is all body)
	const [s, g] = solve(c, ch);
	tint[i] = s; tint[i + 1] = g; tint[i + 2] = ch; tint[i + 3] = c[3];
	fx[i + 1] = ch ? 50 : Math.round(clamp(0.38 + 0.62 * (L - 0.08) / 0.2) * 255);
	fx[i + 3] = c[3];
}

// the sheen sweep coordinate from each texel's place on the body (same formula as the Classic map)
const PARTS = {
	head: { o: [-4, 24, -4], s: [8, 8, 8] }, body: { o: [-4, 12, -2], s: [8, 12, 4] },
	rarm: { o: [-8, 12, -2], s: [4, 12, 4] }, larm: { o: [4, 12, -2], s: [4, 12, 4] },
	rleg: { o: [-4, 0, -2], s: [4, 12, 4] }, lleg: { o: [0, 0, -2], s: [4, 12, 4] },
};
const BOXES = [['head', 0, 0], ['head', 32, 0], ['body', 16, 16], ['body', 16, 32], ['rarm', 40, 16], ['rarm', 40, 32],
	['larm', 32, 48], ['larm', 48, 48], ['rleg', 0, 16], ['rleg', 0, 32], ['lleg', 16, 48], ['lleg', 0, 48]];
function facesOf(u, v, sx, sy, sz) {
	return [
		{ id: 'top', u0: u + sz, v0: v, du: sx, dv: sz }, { id: 'bottom', u0: u + sz + sx, v0: v, du: sx, dv: sz },
		{ id: 'west', u0: u, v0: v + sz, du: sz, dv: sy }, { id: 'front', u0: u + sz, v0: v + sz, du: sx, dv: sy },
		{ id: 'east', u0: u + sz + sx, v0: v + sz, du: sz, dv: sy }, { id: 'back', u0: u + 2 * sz + sx, v0: v + sz, du: sx, dv: sy },
	];
}
function pointOn(face, fu, fv, g) {
	const [ox, oy, oz] = g.o, [sx, sy, sz] = g.s;
	switch (face) {
		case 'top': return [ox + sx * fu, oy + sy, oz + sz * (1 - fv)];
		case 'bottom': return [ox + sx * fu, oy, oz + sz * (1 - fv)];
		case 'west': return [ox, oy + sy * (1 - fv), oz + sz * (1 - fu)];
		case 'front': return [ox + sx * fu, oy + sy * (1 - fv), oz];
		case 'east': return [ox + sx, oy + sy * (1 - fv), oz + sz * fu];
		default: return [ox + sx * (1 - fu), oy + sy * (1 - fv), oz + sz];
	}
}
for (const [part, u, v] of BOXES) {
	const g = PARTS[part];
	for (const f of facesOf(u, v, ...g.s)) {
		for (let ty = 0; ty < f.dv * S; ty++) for (let tx = 0; tx < f.du * S; tx++) {
			const X = f.u0 * S + tx, Y = f.v0 * S + ty, i = (Y * W + X) * 4;
			if (!fx[i + 3]) continue;
			const p = pointOn(f.id, (tx + 0.5) / (f.du * S), (ty + 0.5) / (f.dv * S), g);
			fx[i] = Math.round(clamp((p[1] + 0.45 * p[0] + 0.25 * p[2] + 5) / 42) * 255);
		}
	}
}

// ---------------------------------------------------------------- the geo: the Classic rig + the mouth
const classicGeo = JSON.parse(fs.readFileSync(path.join(ASSETS, 'geo', 'symbiote_host.geo.json'), 'utf8'));
const geo = JSON.parse(JSON.stringify(classicGeo));
const g0 = geo['minecraft:geometry'][0];
g0.description.identifier = 'geometry.symbiote_host_lethal';
g0.bones = g0.bones.filter((b) => !b.name.startsWith('lp_'));
const r3 = (v) => Math.round(v * 1000) / 1000;
const uvAll = (u, v, w, h) => ({ north: { uv: [u, v], uv_size: [w, h] }, south: { uv: [u, v], uv_size: [w, h] },
	east: { uv: [u, v], uv_size: [w, h] }, west: { uv: [u, v], uv_size: [w, h] }, up: { uv: [u, v], uv_size: [w, h] }, down: { uv: [u, v], uv_size: [w, h] } });
const box = (name, o, s, uv) => ({ name, origin: o.map(r3), size: s.map(r3), uv });
const bones = [];
// lp_upper: the upper teeth, hanging from the top of the mouth (fixed to the head; lifts a little in a snarl)
{
	const cubes = [box('upper_gum', [-3.8, MOUTH_TOP - 0.3, -4.86], [7.6, 0.3, 0.26], uvAll(M.u + 1, M.v, 1, 0.5))];
	const L = [1.0, 1.6, 0.85, 1.05, 0.8, 0.95, 0.8, 1.05, 0.85, 1.6, 1.0].map((l) => l * UPPER_SCALE);
	for (let k = 0; k < L.length; k++) {
		const x = -3.5 + 0.7 * k, l = L[k];
		cubes.push(box('ut' + k, [x - 0.26, MOUTH_TOP - l * 0.6, -4.97], [0.52, l * 0.6, 0.34], uvAll(M.u, M.v, 0.5, 1.2)));
		cubes.push(box('ut' + k + '_tip', [x - 0.13, MOUTH_TOP - l, -4.92], [0.26, l * 0.4, 0.24], uvAll(M.u, M.v + 1.2, 0.5, 0.8)));
	}
	for (const sg of [-1, 1]) { // round the corners of the grin, along the cheeks
		[[-4.3, 0.95], [-3.6, 0.75], [-2.95, 0.55]].map(([z, l]) => [z, l * UPPER_SCALE]).forEach(([z, l], k) => {
			const x0 = sg > 0 ? 4.64 : -4.98;
			cubes.push(box('uts' + (sg > 0 ? 'l' : 'r') + k, [x0, MOUTH_TOP - 0.15 - l * 0.6, z - 0.26], [0.34, l * 0.6, 0.52], uvAll(M.u, M.v, 0.5, 1.2)));
			cubes.push(box('uts' + (sg > 0 ? 'l' : 'r') + k + '_tip', [x0 + 0.05, MOUTH_TOP - 0.15 - l, z - 0.13], [0.24, l * 0.4, 0.26], uvAll(M.u, M.v + 1.2, 0.5, 0.8)));
		});
	}
	bones.push({ name: 'lp_upper', parent: 'armorHead', pivot: [0, MOUTH_TOP, -4.8], cubes });
}
// lp_jaw: the lower jaw, hinged back inside the head; carries the lower teeth and the tongue
const JAW_PIVOT = [0, 26.6, 0.2];
{
	const cubes = [box('jaw', [JAW.x0, JAW.y0, JAW.z0], [JW, JH, JD], {
		north: { uv: [J.u, J.v], uv_size: [8, 2] }, south: { uv: [J.u, J.v + 2], uv_size: [8, 2] },
		east: { uv: [J.u, J.v + 4], uv_size: [4, 2] }, west: { uv: [J.u + 4, J.v + 4], uv_size: [4, 2] },
		up: { uv: [J.u, J.v + 6], uv_size: [8, 1] }, down: { uv: [J.u, J.v + 7], uv_size: [8, 1] } })];
	const L = [0.85, 1.05, 0.8, 0.95, 0.72, 0.72, 0.95, 0.8, 1.05, 0.85].map((l) => l * LOWER_SCALE);
	for (let k = 0; k < L.length; k++) {
		const x = -3.15 + 0.7 * k, l = L[k];
		cubes.push(box('lt' + k, [x - 0.25, JAW.y1, -4.94], [0.5, l * 0.6, 0.33], uvAll(M.u + 0.5, M.v + 0.8, 0.5, 1.2)));
		cubes.push(box('lt' + k + '_tip', [x - 0.12, JAW.y1 + l * 0.6, -4.9], [0.24, l * 0.4, 0.24], uvAll(M.u + 0.5, M.v, 0.5, 0.8)));
	}
	for (const sg of [-1, 1]) {
		[[-4.25, 0.8], [-3.55, 0.65], [-2.85, 0.5]].map(([z, l]) => [z, l * LOWER_SCALE]).forEach(([z, l], k) => {
			const x0 = sg > 0 ? 4.66 : -4.98;
			cubes.push(box('lts' + (sg > 0 ? 'l' : 'r') + k, [x0, JAW.y1, z - 0.25], [0.32, l * 0.6, 0.5], uvAll(M.u + 0.5, M.v + 0.8, 0.5, 1.2)));
			cubes.push(box('lts' + (sg > 0 ? 'l' : 'r') + k + '_tip', [x0 + 0.04, JAW.y1 + l * 0.6, z - 0.12], [0.24, l * 0.4, 0.24], uvAll(M.u + 0.5, M.v, 0.5, 0.8)));
		});
	}
	bones.push({ name: 'lp_jaw', parent: 'armorHead', pivot: JAW_PIVOT, cubes });
}
// lp_tongue_0..5: six tapering segments, each hinged at its back end on the one before (the renderer slides the chain out of the
// mouth and bends every joint)
{
	const lens = [1.5, 1.35, 1.25, 1.15, 1.1, 1.05], ws = [2.0, 1.85, 1.65, 1.45, 1.2, 0.9], hs = [0.7, 0.65, 0.6, 0.55, 0.5, 0.45];
	let z = -0.6;
	for (let k = 0; k < lens.length; k++) {
		const w = ws[k], h = hs[k], l = lens[k], y0 = JAW.y1 + 0.05;
		const faces = { up: { uv: [M.u + 2, M.v], uv_size: [2, 2] }, down: { uv: [M.u + 4, M.v + 1], uv_size: [2, 1] },
			east: { uv: [M.u + 4, M.v], uv_size: [2, 1] }, west: { uv: [M.u + 4, M.v], uv_size: [2, 1] },
			north: { uv: [M.u + 6, M.v], uv_size: [1, 1] }, south: { uv: [M.u + 6, M.v], uv_size: [1, 1] } };
		bones.push({ name: 'lp_tongue_' + k, parent: k === 0 ? 'lp_jaw' : 'lp_tongue_' + (k - 1), pivot: [0, r3(y0 + h / 2), r3(z)],
			cubes: [box('tongue_' + k, [-w / 2, y0 + (hs[0] - h) * 0.3, z - l - (k < lens.length - 1 ? 0.06 : 0)], [w, h, l + (k < lens.length - 1 ? 0.06 : 0)], faces)] });
		z -= l;
	}
	console.log('tongue length', r3(-0.6 - z));
}
// keep the mouth bones right after armorHead (render order does not matter; tidy in the file)
const at = g0.bones.findIndex((b) => b.name === 'armorHead');
g0.bones.splice(at + 1, 0, ...bones);

// ---------------------------------------------------------------- write
function write(rel, buf) { const f = path.join(ASSETS, rel); PNG.write(f, W, H, buf); console.log('wrote', rel); }
write('textures/armor/symbiote_host_lethal.png', out);
write('textures/armor/symbiote_host_lethal_tint.png', tint);
write('textures/armor/symbiote_host_lethal_fx.png', fx);
fs.writeFileSync(path.join(ASSETS, 'geo', 'symbiote_host_lethal.geo.json'), JSON.stringify(geo, null, '\t').replace(/\n/g, '\r\n') + '\r\n');
console.log('wrote geo/symbiote_host_lethal.geo.json (' + bones.length + ' mouth bones)');

// a zoomed debug view of the processed texture next to the skin (scratch, not committed)
if (process.argv.includes('--debug')) {
	const Z = 5, dbg = Buffer.alloc(W * 2 * Z * H * Z * 4);
	const DW = W * 2 * Z;
	for (let y = 0; y < H * Z; y++) for (let x = 0; x < W * 2 * Z; x++) {
		const src = x < W * Z ? skin.px : out, sx = Math.floor((x % (W * Z)) / Z), sy = Math.floor(y / Z), si = (sy * W + sx) * 4;
		const chk = ((x >> 3) + (y >> 3)) & 1 ? 170 : 120, a = src[si + 3] / 255, oi = (y * DW + x) * 4;
		for (let k = 0; k < 3; k++) dbg[oi + k] = Math.round(src[si + k] * a + chk * (1 - a));
		dbg[oi + 3] = 255;
	}
	const f = process.argv[process.argv.indexOf('--debug') + 1];
	PNG.write(f, DW, H * Z, dbg);
	console.log('debug', f);
}
