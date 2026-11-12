// v0.17.3 Vision: builds the suit layer and the cape from the user's chosen skin
// (docs/reference/vision/vision_doctor_aurus.png, "Vision" by Doctor_Aurus on PlanetMinecraft, wide/Steve model; no licence stated,
// check with the author before a public release, see docs/reference/vision/SOURCE.txt). The source file is never modified.
// Run from the repo root:   node scratchpad/vision/make_vision_suit.js [-v]
//
// Writes into src/main/resources/assets/projecthero/textures/entity/vision/:
//   suit.png       the suit, wide 4-px arms (head included: Vision's face is part of the synthezoid)
//   suit_slim.png  the same with slim 3-px arms
//   suit_glow.png / suit_glow_slim.png  the Mind Stone and the eyes, drawn full bright
//   cape.png       the cape model's cloth (64x48, FlowingCapeLayer layout: outside on the left half, lining on the right)
// and an x8 preview sheet into scratchpad/vision/preview_suit_x8.png.
//
// What it does to the skin:
//  1. The painted cape goes. It is only on the OUTER layer: the jacket's back (gold with a darker fold down the middle), the back
//     edges of both jacket sides, the jacket's top (over the shoulders) and bottom faces, and on both trouser legs their backs, the
//     back edges of their outer sides and their tops (the cape reached the ankles). Those texels are cleared, so the suit's own
//     green-and-maroon back on the base layer shows (the base layer has no cape painted on it; checked below).
//     The gold on the jacket's FRONT (the clasps at the shoulders, the chest line) is kept.
//  2. Slim arms: each 4-wide face (front, back, top, bottom) drops one middle column, the one most like its neighbour (the Shazam
//     suit's method), so the bands and seams still line up; the 4-deep sides are kept whole.
//  3. The cape: the painted cape's mustard gold, darker at the shoulders and lighter toward the hem, its darker fold down the
//     middle, a deeper gold edge; the lining a shade darker.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const SRC = path.join(ROOT, 'docs', 'reference', 'vision', 'vision_doctor_aurus.png');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'vision');
const VERBOSE = process.argv.includes('-v');

class Img {
	constructor(w, h, px) { this.w = w; this.h = h; this.d = px ? Buffer.from(px) : Buffer.alloc(w * h * 4); }
	get(x, y) { const i = (y * this.w + x) * 4; return [this.d[i], this.d[i + 1], this.d[i + 2], this.d[i + 3]]; }
	set(x, y, c) { const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c[3] === undefined ? 255 : c[3]; }
	clear(x, y) { this.set(x, y, [0, 0, 0, 0]); }
	clearRect(x0, y0, w, h) { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) this.clear(x, y); }
	copy() { return new Img(this.w, this.h, this.d); }
	save(name) { PNG.write(path.join(OUT, name), this.w, this.h, this.d); }
}

const src = PNG.read(SRC);
if (src.W !== 64 || src.H !== 64) throw new Error('expected a 64x64 skin');
const base = new Img(64, 64, src.px);
const hex = (c) => c[3] ? c.slice(0, 3).map((v) => v.toString(16).padStart(2, '0')).join('') : 'clear';

/** The cape's mustard gold (7c6a20 .. c2a948): red over green over blue, little blue. The suit is green and maroon. */
function isCapeGold(c) {
	if (!c[3]) return false;
	const [r, g, b] = c;
	return r >= 0x70 && r > g && g > b && b < r * 0.6 && r - b > 0x30;
}

// ------------------------------------------------------------------ 1. the painted cape
const suit = base.copy();
const counts = { gold: 0, all: 0 };
function clearIf(x0, y0, w, h, why, all) {
	for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
		const c = suit.get(x, y);
		if (!c[3]) continue;
		if (all || isCapeGold(c)) {
			if (VERBOSE) console.log(`  ${why}: (${x},${y}) ${hex(c)} -> clear`);
			suit.clear(x, y);
			counts[all ? 'all' : 'gold']++;
		}
	}
}
// jacket (u16 v32, 8x12x4): top 20..27, bottom 28..35 (rows 32..35); right side 16..19, front 20..27, left side 28..31, back 32..39
clearIf(20, 32, 8, 4, 'jacket top (the cape over the shoulders)', false);
clearIf(28, 32, 8, 4, 'jacket bottom (the cape seen from below)', true);
clearIf(16, 36, 4, 12, 'jacket right side', false);
clearIf(28, 36, 4, 12, 'jacket left side', false);
clearIf(32, 36, 8, 12, 'jacket back (the cape)', true);
// trousers: right (u0 v32), left (u0 v48), 4x12x4: top 4..7, sides 0..3 / 8..11, front 4..7, back 12..15
clearIf(4, 32, 4, 4, 'right trousers top', false);
clearIf(0, 36, 4, 12, 'right trousers outer side', false);
clearIf(12, 36, 4, 12, 'right trousers back (the cape)', true);
clearIf(4, 48, 4, 4, 'left trousers top', false);
clearIf(8, 52, 4, 12, 'left trousers outer side', false);
clearIf(12, 52, 4, 12, 'left trousers back (the cape)', true);

// check: no cape gold left on any back face, base or outer, and the base back is solid (nothing see-through where the cape was)
const leftovers = [];
const check = (x0, y0, w, h, name, solid) => {
	for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
		const c = suit.get(x, y);
		if (isCapeGold(c)) leftovers.push(`${name} (${x},${y}) ${hex(c)}`);
		if (solid && c[3] !== 255) leftovers.push(`${name} (${x},${y}) not solid`);
	}
};
check(32, 20, 8, 12, 'body back', true); check(12, 20, 4, 12, 'right leg back', true); check(28, 52, 4, 12, 'left leg back', true);
check(32, 36, 8, 12, 'jacket back', false); check(12, 36, 4, 12, 'right trousers back', false); check(12, 52, 4, 12, 'left trousers back', false);
if (leftovers.length) throw new Error('cape left on a back face:\n' + leftovers.join('\n'));

// ------------------------------------------------------------------ 2. slim arms
function rgbDiff(a, b) { if (!a[3] && !b[3]) return 0; if (!a[3] || !b[3]) return 400; return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]); }
function dropColumn(img, fx, fy, fh) {
	let best = 1, bestCost = Infinity;
	for (const c of [1, 2]) {
		let cost = 0;
		for (let y = fy; y < fy + fh; y++) cost += Math.min(rgbDiff(img.get(fx + c, y), img.get(fx + c - 1, y)), rgbDiff(img.get(fx + c, y), img.get(fx + c + 1, y)));
		if (cost < bestCost) { best = c; bestCost = cost; }
	}
	return best;
}
function slimArm(from, to, u, v) {
	const d = 4, h = 12;
	const W = { top: [u + d, v, 4, d], bottom: [u + d + 4, v, 4, d], right: [u, v + d, d, h], front: [u + d, v + d, 4, h], left: [u + d + 4, v + d, d, h], back: [u + d + 4 + d, v + d, 4, h] };
	const S = { top: [u + d, v], bottom: [u + d + 3, v], right: [u, v + d], front: [u + d, v + d], left: [u + d + 3, v + d], back: [u + d + 3 + d, v + d] };
	const faces = {};
	for (const f of Object.keys(W)) {
		const [fx, fy, fw, fh] = W[f];
		const cols = [];
		for (let x = 0; x < fw; x++) { const col = []; for (let y = 0; y < fh; y++) col.push(from.get(fx + x, fy + y)); cols.push(col); }
		faces[f] = (f === 'right' || f === 'left') ? { cols, drop: -1 } : { cols, drop: dropColumn(from, fx, fy, fh) };
	}
	faces.top.drop = faces.front.drop;
	faces.bottom.drop = faces.front.drop;
	to.clearRect(u, v, 16, 16);
	for (const f of Object.keys(S)) {
		const [sx, sy] = S[f];
		faces[f].cols.filter((_, i) => i !== faces[f].drop).forEach((col, x) => col.forEach((c, y) => to.set(sx + x, sy + y, c)));
	}
	return faces.front.drop;
}
const slim = suit.copy();
const drops = {};
for (const [u, v, n] of [[40, 16, 'right arm'], [40, 32, 'right sleeve'], [32, 48, 'left arm'], [48, 48, 'left sleeve']]) drops[n] = slimArm(suit, slim, u, v);
// the skin has a few stray texels in the unused corner right of the right arm (56..63, 16..31): not part of any cube, cleared
suit.clearRect(56, 16, 8, 16);
slim.clearRect(56, 16, 8, 16);

// ------------------------------------------------------------------ 3. the cape (64x48)
function cape() {
	const img = new Img(64, 48);
	// the painted cape's own golds, dark to light (7c6a20 .. c2a948)
	const RAMP = [[0x6a, 0x58, 0x1a], [0x7c, 0x6a, 0x20], [0x8a, 0x77, 0x2c], [0x93, 0x7f, 0x2f], [0xa9, 0x94, 0x3b], [0xb4, 0x9c, 0x3e],
		[0xbc, 0xa1, 0x38], [0xc1, 0xa8, 0x44], [0xcc, 0xb3, 0x52]];
	const FOLD = [0x5c, 0x48, 0x1a]; // the painted cape's middle fold (a deep brown-gold where the skin had its dark seam)
	const EDGE = [0x7a, 0x62, 0x18], HEM = [0x6a, 0x52, 0x14];
	const mix = (a, b, t) => [0, 1, 2].map((k) => Math.round(a[k] + (b[k] - a[k]) * t));
	const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) & 0xffff) / 0xffff; };
	for (let y = 0; y < 48; y++) for (let x = 0; x < 64; x++) {
		const lining = x >= 32;
		const lx = lining ? x - 32 : x;
		const fromMid = Math.abs(lx - 15.5) / 15.5; // 0 at the fold, 1 at the edges
		// soft vertical folds: the painted cape alternated light and mid gold every couple of texels
		const ripple = Math.sin(lx * 1.3) * 0.5 + Math.sin(lx * 0.45 + 1.0) * 0.35;
		let lvl = 2.6 + (y / 47) * 4.2 + ripple + (hash(x, y) - 0.5) * 0.7 - (1 - fromMid) * 0.6;
		if (y <= 1) lvl -= 1.4; // the collar's shadow
		let c = RAMP[Math.max(0, Math.min(RAMP.length - 1, Math.round(lvl)))];
		if (fromMid < 0.07) c = mix(c, FOLD, 0.75); // the dark fold down the middle
		else if (fromMid < 0.14) c = mix(c, FOLD, 0.3);
		if (lx === 0 || lx === 31) c = EDGE; // a deeper gold edge down each side
		if (y >= 46) c = y === 47 ? HEM : EDGE; // and along the hem
		if (lining) c = c.map((v) => Math.round(v * 0.82));
		img.set(x, y, c);
	}
	return img;
}

// ------------------------------------------------------------------ 4. the glow: the Mind Stone on the brow (bright yellow, base and
// hat layer) and the white eyes (the face's row 12), drawn full bright over the suit
function glow(img) {
	const out = new Img(64, 64);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) {
		const c = img.get(x, y);
		if (!c[3]) continue;
		const yellow = c[0] > 0xE0 && c[1] > 0xD0 && c[2] < 0x90;
		const white = c[0] > 0xE8 && c[1] > 0xE8 && c[2] > 0xE8 && y === 12;
		if (yellow || white) out.set(x, y, c);
	}
	return out;
}

fs.mkdirSync(OUT, { recursive: true });
suit.save('suit.png');
slim.save('suit_slim.png');
glow(suit).save('suit_glow.png');
glow(slim).save('suit_glow_slim.png');
const capeImg = cape();
capeImg.save('cape.png');

// preview: source | wide | slim, x8 on a checkerboard; the cape below
const imgs = [base, suit, slim];
const SC = 8, GAP = 8;
const PW = imgs.length * (64 * SC + GAP), PH = 64 * SC + GAP + 48 * SC;
const prev = Buffer.alloc(PW * PH * 4);
const put = (X, Y, rgba) => { const i = (Y * PW + X) * 4; prev[i] = rgba[0]; prev[i + 1] = rgba[1]; prev[i + 2] = rgba[2]; prev[i + 3] = 255; };
for (let Y = 0; Y < PH; Y++) for (let X = 0; X < PW; X++) put(X, Y, [40, 40, 40]);
imgs.forEach((im, k) => {
	const ox = k * (64 * SC + GAP);
	for (let Y = 0; Y < 64 * SC; Y++) for (let X = 0; X < 64 * SC; X++) {
		const p = im.get(Math.floor(X / SC), Math.floor(Y / SC));
		const chk = ((Math.floor(X / SC) + Math.floor(Y / SC)) & 1) ? 120 : 90;
		const a = p[3] / 255;
		let col = [0, 1, 2].map((i) => Math.round(p[i] * a + chk * (1 - a)));
		if (X % (SC * 8) === 0 || Y % (SC * 8) === 0) col = [20, 20, 20];
		put(ox + X, Y, col);
	}
});
for (let Y = 0; Y < 48 * SC; Y++) for (let X = 0; X < 64 * SC; X++) put(X, 64 * SC + GAP + Y, capeImg.get(Math.floor(X / SC), Math.floor(Y / SC)));
PNG.write(path.join(__dirname, 'preview_suit_x8.png'), PW, PH, prev);

console.log('cape texels cleared:', counts, '| slim dropped front columns (0-3):', JSON.stringify(drops));
