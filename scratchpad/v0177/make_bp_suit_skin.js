// v0.17.7 Black Panther's suit look: the comics Black Panther skin from PlanetMinecraft with its painted cape and high collar taken
// off (they are real models now: BlackPantherCapeLayer + BlackPantherCollarLayer) and the back of the suit painted in.
//
//   node scratchpad/v0177/make_bp_suit_skin.js
//
// History: the user asked "make this the new black panther suit: https://www.planetminecraft.com/skin/black-panther-marvel-comics-6828688/
// Remove the cape and and high collar from the skin and make it a model that reacts with the players movement, make it look cool
// ... fill in the gaps". It replaces the v0.17.3 "The Black Panther (MCU)" suit skin (PMC file 11967850).
//
// Source (NEVER modified): scratchpad/v0177/bp_skin_original.png -- "Black Panther [Marvel Comics]" on PlanetMinecraft (page model:
// Steve = WIDE 4-pixel arms), the 64x64 file behind the page's download button (PMC file 19479117):
//   https://www.planetminecraft.com/skin/black-panther-marvel-comics-6828688/download/file/19479117/
// (planetminecraft.com sits behind a Cloudflare check: curl gets a 403, so it was fetched from inside a real browser tab.)
// Borrowed skin: it ships publicly like the others (CLAUDE.md); the source is noted here and in BlackPantherRender.
//
// Writes (textures/entity/black_panther/):
//   black_panther.png       the suit, 64x64 vanilla player-skin layout, both layers, wide arms (the suit model is always wide:
//                           BlueBeetleRender.model()), so one texture serves every wearer. The texel build (BlackPantherRender.build)
//                           makes its frames from this file at runtime, so the suit-up covers the new texture by itself.
//   black_panther_glow.png  the Kinetic glow map: every gold trim texel (eyes, necklace, belt, bracers) and the new vibranium seams
//                           on the back, in violet (178,102,255) -- drawn emissive over the suit, alpha = the Kinetic bar
//   black_panther_cape.png  the cape model's cloth, 64x48, FlowingCapeLayer layout (left half outside, right half lining)
//   black_panther_collar.png the high collar model's texture, 32x32 (BlackPantherCollarLayer: outside, inside, rim, yoke bands)
// and the offline previews in docs/screenshots/v0177/bp_suit/.
//
// What the source paints, and what this does with it (texel coordinates are the 64x64 skin's):
//  * The CAPE is a purple-grey (#2a-#5c) cloth with vertical fold stripes: the whole body back (32-39, 20-31), the back half of the
//    body's top (rows 16-17) and its bottom, the two back columns of each body side (16-17, 30-31), the back of both legs and the
//    two back columns of each leg side down to the boot (rows 20-27 / 52-59), and sparse fold highlights on the second layer over
//    all of those. The base layer is repainted as suit, the second-layer cape texels are cleared.
//  * The HIGH COLLAR rises behind the head: the light texels of the head's sides and back on rows 11-15 (it stops a texel short of
//    the face, so it is open at the front), the outer ring of the head's underside, and copies of it pushed out on the hat layer.
//    Head texels become the dark hood of the mask; the hat-layer collar texels are cleared (the dark hat texels -- brows, eye rims,
//    the ear tips -- stay).
//  * FILLING THE GAPS: the suit's back is the suit's front seen from behind (mirrored) without the necklace's hanging teeth: the gold
//    neck band and the gold V belt now run all the way round (the cape used to cut them off at the side seams), the body sides and
//    leg sides take their own front half mirrored, the legs' backs mirror their fronts (boots included). New detail in the suit's
//    own style: a vibranium spine seam and shoulder-blade seams (a Y) on the back, a shade lighter than the suit, which glow violet
//    with the Kinetic bar. The gold bracers had a gap on the inner back of each forearm; the band is closed.
//  * Every base-layer texel of the head, body, arms and legs is opaque (checked below), so the wearer never shows through.
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');
const preview = require('./skin_preview.js');

const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(__dirname, 'bp_skin_original.png');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/black_panther');
const SHOTS = path.join(ROOT, 'docs/screenshots/v0177/bp_suit');
fs.mkdirSync(SHOTS, { recursive: true });

const img = PNG.read(SRC);
if (img.W !== 64 || img.H !== 64) throw new Error('expected a 64x64 skin, got ' + img.W + 'x' + img.H);
const src = Buffer.from(img.px);
const px = Buffer.from(img.px);
const W = 64;

const get = (b, x, y) => { const i = (y * W + x) * 4; return [b[i], b[i + 1], b[i + 2], b[i + 3]]; };
const set = (b, x, y, c) => { const i = (y * W + x) * 4; b[i] = c[0]; b[i + 1] = c[1]; b[i + 2] = c[2]; b[i + 3] = c.length > 3 ? c[3] : 255; };
const clear = (b, x, y) => set(b, x, y, [0, 0, 0, 0]);
const lum = c => (c[0] + c[1] + c[2]) / 3;
const isGold = c => c[3] > 0 && c[0] > 150 && c[2] < 140;
const scale = (c, k) => [Math.round(c[0] * k), Math.round(c[1] * k), Math.round(c[2] * k), 255];
const add = (c, d) => [0, 1, 2].map(i => Math.max(0, Math.min(255, c[i] + d[i]))).concat(255);
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const mix = (a, b, t) => [0, 1, 2].map(i => Math.round(a[i] + (b[i] - a[i]) * t)).concat(255);

// cube faces: [x0, y0, w, h] of each face of a box textured at (u, v) with size w x h x d
function faces(u, v, w, h, d) {
	return {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
		right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h],
	};
}
const BASE = {
	head: faces(0, 0, 8, 8, 8), body: faces(16, 16, 8, 12, 4), rarm: faces(40, 16, 4, 12, 4), larm: faces(32, 48, 4, 12, 4),
	rleg: faces(0, 16, 4, 12, 4), lleg: faces(16, 48, 4, 12, 4),
};
const counts = { collarHead: 0, collarHat: 0, capeBase: 0, capeOverlay: 0, bracer: 0, filledClear: 0 };

// ------------------------------------------------------------------ 1. the high collar
// head sides / back, rows 11-15: the collar is every texel lighter than the mask (avg >= 46); it becomes the mask's dark hood, the
// colour of that face's row 10 (the hood just above the collar), a touch darker each row down to the jaw.
const COLLAR_LUM = 46;
for (const f of ['right', 'left', 'back']) {
	const [x0, y0, w] = BASE.head[f];
	for (let y = y0 + 3; y < y0 + 8; y++) {
		const k = [1, 1, 0.96, 0.92, 0.88][y - y0 - 3];
		for (let x = x0; x < x0 + w; x++) {
			if (lum(get(src, x, y)) < COLLAR_LUM) continue;
			let hood = get(src, x, y0 + 2);
			if (lum(hood) >= COLLAR_LUM) hood = get(src, x, y0 + 1);
			set(px, x, y, scale(hood, k));
			counts.collarHead++;
		}
	}
}
// the head's underside: the collar's ring round the neck. Every column takes the neck's colour (its row 7).
{
	const [x0, y0, w, h] = BASE.head.bottom;
	for (let x = x0; x < x0 + w; x++) {
		const neck = get(src, x, y0 + h - 1);
		for (let y = y0; y < y0 + h; y++) {
			if (lum(get(src, x, y)) >= COLLAR_LUM) counts.collarHead++;
			set(px, x, y, neck);
		}
	}
}
// the hat layer's collar: on its sides and back every light texel goes (the dark ones are the mask's ear tips)
for (const [x0, w] of [[32, 8], [48, 8], [56, 8]]) {
	for (let y = 8; y < 16; y++) for (let x = x0; x < x0 + w; x++) {
		const c = get(src, x, y);
		if (c[3] && lum(c) >= 40) { clear(px, x, y); counts.collarHat++; }
	}
}

// ------------------------------------------------------------------ 2. the painted cape -> the suit's back
const B = BASE.body;
// the body's back: the front seen from behind (mirrored), rows 21-22 without the necklace's hanging teeth (rows 23 / 24 instead)
{
	const [fx, fy, w, h] = B.front, [bx] = B.back;
	for (let r = 0; r < h; r++) {
		const fromRow = r === 1 ? 3 : r === 2 ? 4 : r;
		for (let k = 0; k < w; k++) {
			set(px, bx + k, fy + r, get(src, fx + w - 1 - k, fy + fromRow));
			counts.capeBase++;
		}
	}
}
// the vibranium seams on the back: a spine line and a seam out over each shoulder blade, joining it like a Y; a shade lighter than
// the suit with a violet cast. They glow with the Kinetic bar.
const SEAMS = [];
{
	const [bx, by] = B.back;
	const pts = [[1, 1], [2, 2], [2, 3], [3, 4]]; // the left half (x from the back's left edge, y from its top); mirrored below
	for (const [x, y] of pts) { SEAMS.push([bx + x, by + y]); SEAMS.push([bx + 7 - x, by + y]); }
	for (let y = 5; y <= 8; y++) { SEAMS.push([bx + 3, by + y]); SEAMS.push([bx + 4, by + y]); }
	for (const [x, y] of SEAMS) set(px, x, y, add(get(px, x, y), [28, 25, 48]));
}
// the body's top (the back half was the cape over the shoulders) and its underside: suit black from the front's darkest rows
{
	const [tx, ty, w] = B.top, [fx, fy] = B.front;
	for (let y = ty; y < ty + 2; y++) for (let k = 0; k < w; k++) { set(px, tx + k, y, get(src, fx + k, fy + 4)); counts.capeBase++; }
	const [ux, uy, uw, uh] = B.bottom;
	for (let y = uy; y < uy + uh; y++) for (let k = 0; k < uw; k++) { set(px, ux + k, y, get(src, fx + k, fy + 5)); counts.capeBase++; }
}
// a side face's back half takes its own front half mirrored. backAtLow: the face's first column touches the back (a right face).
function mirrorSide(face, backAtLow, rows) {
	const [x0, y0, w] = face;
	for (const r of rows) {
		for (let k = 0; k < w / 2; k++) {
			const dst = backAtLow ? x0 + k : x0 + w - 1 - k;
			const from = backAtLow ? x0 + w - 1 - k : x0 + k;
			set(px, dst, y0 + r, get(src, from, y0 + r));
			counts.capeBase++;
		}
	}
}
const range = (a, b) => Array.from({ length: b - a }, (_, i) => a + i);
mirrorSide(B.right, true, range(0, 12)); // body: the gold neck band and the belt now meet round the back
mirrorSide(B.left, false, range(0, 12));
// the legs: backs mirror their fronts (gold-free), sides mirror down to the boot (rows 0-7 of the face)
for (const leg of [BASE.rleg, BASE.lleg]) {
	const [fx, fy, w, h] = leg.front, [bx] = leg.back;
	for (let r = 0; r < h; r++) {
		const row = [];
		for (let k = 0; k < w; k++) row.push(get(src, fx + w - 1 - k, fy + r));
		const dark = row.filter(c => !isGold(c));
		for (let k = 0; k < w; k++) {
			let c = row[k];
			if (isGold(c)) c = dark.length ? dark[(k + 1) % dark.length] : [20, 18, 30, 255];
			set(px, bx + k, fy + r, c);
			counts.capeBase++;
		}
	}
	mirrorSide(leg.right, true, range(0, 8));
	mirrorSide(leg.left, false, range(0, 8));
}
// the second layer's cape: the jacket's back, top, bottom and the back columns of its sides; both trouser legs' backs and the back
// columns of their sides down to the boot
function clearRect(x0, y0, w, h) {
	for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) if (get(px, x, y)[3]) { clear(px, x, y); counts.capeOverlay++; }
}
clearRect(32, 36, 8, 12); // jacket back
clearRect(20, 32, 8, 4); // jacket top
clearRect(28, 32, 8, 4); // jacket bottom
clearRect(16, 36, 2, 12); clearRect(30, 36, 2, 12); // jacket sides' back columns
for (const v of [32, 48]) { // right trousers (0,32), left trousers (0,48)
	clearRect(12, v + 4, 4, 12);
	clearRect(0, v + 4, 2, 8);
	clearRect(10, v + 4, 2, 8);
}

// ------------------------------------------------------------------ 3. closing the bracers' gap (inner back of each forearm)
const BRACER = [[[50, 27, '#d29d4c'], [51, 27, '#d6a452'], [52, 27, '#dcae58'], [53, 27, '#e2b75c']],
	[[46, 59, '#e2b75c'], [47, 59, '#dcae58'], [32, 59, '#d6a452'], [33, 59, '#d29d4c']]];
for (const band of BRACER) for (const [x, y, c] of band) { set(px, x, y, hex(c)); counts.bracer++; }

// ------------------------------------------------------------------ 4. a complete, opaque base layer
for (const part of Object.values(BASE)) {
	for (const [x0, y0, w, h] of Object.values(part)) {
		for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
			if (get(px, x, y)[3] === 255) continue;
			// nearest opaque texel on the same face
			let best = null, bd = 1e9;
			for (let yy = y0; yy < y0 + h; yy++) for (let xx = x0; xx < x0 + w; xx++) {
				const c = get(px, xx, yy);
				const d = (xx - x) ** 2 + (yy - y) ** 2;
				if (c[3] === 255 && d < bd) { bd = d; best = c; }
			}
			set(px, x, y, best || [16, 15, 26, 255]);
			counts.filledClear++;
		}
	}
}

PNG.write(path.join(TEX, 'black_panther.png'), 64, 64, px);

// ------------------------------------------------------------------ 5. the Kinetic glow map
const glow = Buffer.alloc(64 * 64 * 4);
let glowN = 0;
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (isGold(get(px, x, y))) { set(glow, x, y, [178, 102, 255, 255]); glowN++; }
for (const [x, y] of SEAMS) { set(glow, x, y, [178, 102, 255, 255]); glowN++; }
PNG.write(path.join(TEX, 'black_panther_glow.png'), 64, 64, glow);

// ------------------------------------------------------------------ 6. the cape (64x48: left half outside, right half lining)
// The painted cape's own cloth: purple-grey with soft vertical folds (its stripes ran #353350 / #454262 / #5c597b), darker at the
// edges and lower down, a thin dark-gold hem like the suit's trims, and a violet-black vibranium lining.
const CAPE_W = 64, CAPE_H = 48;
const cape = Buffer.alloc(CAPE_W * CAPE_H * 4);
const cset = (x, y, c) => { const i = (y * CAPE_W + x) * 4; cape[i] = c[0]; cape[i + 1] = c[1]; cape[i + 2] = c[2]; cape[i + 3] = 255; };
const CLOTH_DARK = hex('#26243a'), CLOTH_MID = hex('#3c3a58'), CLOTH_LIGHT = hex('#5c597b');
const LINING_DARK = hex('#0e0d17'), LINING_LIGHT = hex('#2a2342');
const GOLD_HI = hex('#e7c76e'), GOLD = hex('#d19c4c'), GOLD_LO = hex('#a8702e');
function fold(u, v) {
	// u, v 0..1: folds widen and deepen toward the hem
	const n = 4.5;
	const f = Math.sin((u * n + 0.15 * Math.sin(v * 3.1)) * Math.PI * 2);
	return f * (0.35 + 0.65 * v);
}
for (let y = 0; y < CAPE_H; y++) {
	const v = y / (CAPE_H - 1);
	for (let x = 0; x < 32; x++) {
		const u = x / 31;
		// outside: base tone darkens downward a little and toward the edges, folds on top
		const edge = Math.min(u, 1 - u);
		let t = 0.5 - 0.22 * v + 0.3 * fold(u, v);
		if (edge < 0.07) t -= 0.25;
		else if (edge < 0.13) t -= 0.1;
		if (y <= 1) t += 0.12; // the top edge catches the light over the shoulders
		t = Math.max(0, Math.min(1, t));
		let c = t < 0.5 ? mix(CLOTH_DARK, CLOTH_MID, t / 0.5) : mix(CLOTH_MID, CLOTH_LIGHT, (t - 0.5) / 0.5);
		// the hem: a dark-gold trim line two texels up from the bottom edge, the edge itself near-black
		if (y === CAPE_H - 1) c = mix(CLOTH_DARK, LINING_DARK, 0.5);
		else if (y === CAPE_H - 3) c = (x % 4 === 1) ? GOLD_HI : (x % 2 ? GOLD : GOLD_LO);
		else if (y === CAPE_H - 2) c = mix(c, CLOTH_DARK, 0.5);
		cset(x, y, c);
		// lining: violet-black, faint folds (the same folds, so the two sides agree)
		let l = 0.35 + 0.25 * fold(u, v) - 0.15 * v;
		l = Math.max(0, Math.min(1, l));
		let lc = mix(LINING_DARK, LINING_LIGHT, l);
		if (y === CAPE_H - 1) lc = LINING_DARK;
		cset(32 + x, y, lc);
	}
}
PNG.write(path.join(TEX, 'black_panther_cape.png'), CAPE_W, CAPE_H, cape);

// ------------------------------------------------------------------ 7. the high collar (32x32)
// Bands (v): 0-11 the outside (top edge at v 0, the base at 11), 12-23 the inside (top at 12), 24-27 the rim along the top edge,
// 28-31 the yoke on the shoulders. u runs round the collar, front-right end (u 0) to front-left end (u 31). Same cloth as the
// painted collar (#3d3a59 / #504d6d ribs, a #666385 light rim) and the cape, a thin gold edge along the top like the suit's trims.
const COL = 32;
const collar = Buffer.alloc(COL * COL * 4);
const kset = (x, y, c) => { const i = (y * COL + x) * 4; collar[i] = c[0]; collar[i + 1] = c[1]; collar[i + 2] = c[2]; collar[i + 3] = 255; };
for (let x = 0; x < COL; x++) {
	const u = x / (COL - 1);
	const rib = (x % 3 === 0) ? -0.16 : (x % 3 === 1 ? 0.1 : 0.0); // stiff vertical ribs, like the painted collar's stripes
	const endShade = Math.min(u, 1 - u) < 0.06 ? -0.2 : 0; // the open front ends a little darker
	for (let y = 0; y < 12; y++) {
		const v = y / 11;
		let t = 0.75 - 0.4 * v + rib + endShade; // lighter at the top, into shadow at the base
		t = Math.max(0, Math.min(1, t));
		let c = t < 0.5 ? mix(CLOTH_DARK, CLOTH_MID, t / 0.5) : mix(CLOTH_MID, hex('#666385'), (t - 0.5) / 0.5);
		if (y === 0) c = (x % 2) ? GOLD : GOLD_HI;
		else if (y === 1) c = mix(c, GOLD_LO, 0.35);
		kset(x, y, c);
		// inside: the lining, lit at the top edge and dark down at the neck
		let l = 0.6 - 0.5 * v + rib * 0.5;
		l = Math.max(0, Math.min(1, l));
		kset(x, 12 + y, y === 0 ? GOLD_LO : mix(LINING_DARK, LINING_LIGHT, l));
	}
	for (let y = 24; y < 28; y++) kset(x, y, y < 26 ? GOLD_HI : GOLD); // the rim: gold
	for (let y = 28; y < 32; y++) kset(x, y, mix(CLOTH_MID, CLOTH_DARK, (y - 28) / 3 + rib)); // the yoke
}
PNG.write(path.join(TEX, 'black_panther_collar.png'), COL, COL, collar);

// ------------------------------------------------------------------ previews
const asImg = b => ({ W: 64, H: 64, px: b });
preview.render([asImg(src), asImg(px)], path.join(SHOTS, 'original_vs_processed.png'));
preview.render([asImg(glow)], path.join(SHOTS, 'glow_map.png'));
// the raw textures at x8 (cape and collar) side by side
function upscale(buf, w, h, s) {
	const o = Buffer.alloc(w * s * h * s * 4);
	for (let y = 0; y < h * s; y++) for (let x = 0; x < w * s; x++) buf.copy(o, (y * w * s + x) * 4, ((y / s | 0) * w + (x / s | 0)) * 4, ((y / s | 0) * w + (x / s | 0)) * 4 + 4);
	return o;
}
PNG.write(path.join(SHOTS, 'cape_texture_x8.png'), CAPE_W * 8, CAPE_H * 8, upscale(cape, CAPE_W, CAPE_H, 8));
PNG.write(path.join(SHOTS, 'collar_texture_x8.png'), COL * 8, COL * 8, upscale(collar, COL, COL, 8));
console.log(JSON.stringify({ ...counts, glow: glowN }));
