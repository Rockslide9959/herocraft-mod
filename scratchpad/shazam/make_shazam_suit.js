// v0.17.3 Shazam: builds the suit layer and the cape from the user's chosen skin
// (docs/reference/shazam/shazam_justice_league_outofspace2.png, "Shazam (Justice League)" by outofspace2 on PlanetMinecraft,
// wide/Steve model; licence to check before a public release, see docs/reference/shazam/SOURCE.txt). The source file is never
// modified. Run from the repo root:   node scratchpad/shazam/make_shazam_suit.js
//
// Writes into src/main/resources/assets/projecthero/textures/entity/shazam/:
//   shazam_suit.png / shazam_suit_slim.png               the champion's suit (wide 4-px arms / slim 3-px arms)
//   shazam_suit_family.png / shazam_suit_family_slim.png  a Shazam Family member's: the same suit with gold piping
//   shazam_cape.png                                       the cape model's cloth (64x48, FlowingCapeLayer layout)
// and an x8 preview sheet into scratchpad/shazam/preview_suit_x8.png.
//
// What it does to the skin:
//  1. The character's own skin goes: the whole head + hat layer (face, hair), the bare neck on the body's top/front/back, the
//     bare hands and palms. Those texels are transparent, so the wearer's own head and skin show through.
//  2. The painted cape goes: it is on the jacket layer (top, back, the back edges of both sides) and wraps the inner back edges
//     of both sleeves. The jacket back is left clear so the suit's own back (red, the gold belt) shows, like the sides; the two
//     sleeve band texels the cape's hem sat on are repainted in the band's red. The cape is a real model now.
//  3. The odd blue jacket-bottom face (seen from below as a blue patch at the waist) is cleared.
//  4. Slim arms: each 4-wide face (front, back, top, bottom) drops one middle column, the one most like its neighbour, so the
//     cuffs' diagonal, the bands and the shoulder seams still line up; the 4-deep sides are kept whole and re-laid.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const SRC = path.join(ROOT, 'docs', 'reference', 'shazam', 'shazam_justice_league_outofspace2.png');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'shazam');

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
const key = (c) => c.slice(0, 3).join(',');

// ------------------------------------------------------------------ 1. the character's own skin
// the skin tones the skin uses for the neck and hands (the head's are cleared with the whole head)
const SKIN = new Set(['244,170,137', '250,202,172', '244,170,136', '227,125,122', '227,126,122', '249,201,171', '252,216,194']);
const wide = base.copy();
let cleared = { head: 0, skin: 0, cape: 0 };
for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) if (wide.get(x, y)[3]) { cleared.head++; wide.clear(x, y); }
for (let y = 16; y < 64; y++) for (let x = 0; x < 64; x++) {
	const c = wide.get(x, y);
	if (c[3] && SKIN.has(key(c))) { cleared.skin++; wide.clear(x, y); }
}

// ------------------------------------------------------------------ 2. the painted cape
const capeTexels = [];
const rect = (x0, y0, w, h) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) capeTexels.push([x, y]); };
rect(20, 32, 8, 4);  // jacket top: the cape over the shoulders
rect(32, 36, 8, 12); // jacket back: the cape itself
rect(16, 36, 1, 6);  // jacket right side, back edge
rect(31, 36, 1, 6);  // jacket left side, back edge
rect(16, 36, 16, 1); // the cape's collar band round the top of the jacket's sides and front
rect(27, 37, 1, 1);  // ...and where it tucks over the left shoulder
// right sleeve (u40 v32): the cape on its inner back edge
rect(47, 32, 1, 4); rect(47, 36, 1, 1); rect(48, 36, 4, 1); rect(51, 37, 1, 2); rect(52, 36, 1, 5);
// left sleeve (u48 v48): the same on the other side
rect(52, 48, 1, 4); rect(48, 52, 5, 1); rect(48, 53, 1, 2); rect(63, 52, 1, 5);
for (const [x, y] of capeTexels) if (wide.get(x, y)[3]) { cleared.cape++; wide.clear(x, y); }
// the sleeve bands the cape's hem sat on: carry the band's red round to the back edge
wide.set(52, 40, wide.get(55, 40)); // right sleeve back, band row
wide.set(63, 56, wide.get(60, 56)); // left sleeve back, band row
// 3. the jacket's bottom face (blue)
wide.clearRect(28, 32, 8, 4);

// ------------------------------------------------------------------ family: gold piping (the old family suit's distinction)
const GOLD = [255, 196, 48];
const GOLD_DARK = [214, 146, 30];
function family(img, armW) {
	const out = img.copy();
	const pipe = (x, y, c = GOLD) => { if (out.get(x, y)[3]) out.set(x, y, c); };
	// body (u16 v16, 8x12x4): the shoulder line (row 0 of every side face) and a stripe down each side
	for (let x = 16; x < 40; x++) pipe(x, 20);
	for (let y = 21; y < 28; y++) { pipe(17, y); pipe(30, y); } // down the sides, stopping at the belt
	// arms: a gold shoulder band and the tops
	for (const [u, v] of [[40, 16], [32, 48]]) {
		for (let x = u; x < u + 2 * 4 + 2 * armW; x++) pipe(x, v + 4);
		for (let x = u + 4; x < u + 4 + armW; x++) for (let y = v; y < v + 4; y++) pipe(x, y, (y === v || x === u + 4) ? GOLD_DARK : GOLD);
	}
	// legs: a stripe down the outer and inner sides, down to the boots
	for (const [u, v] of [[0, 16], [16, 48]]) for (let y = v + 4; y < v + 4 + 7; y++) { pipe(u + 1, y); pipe(u + 4 + 4 + 2, y); }
	return out;
}

// ------------------------------------------------------------------ 4. slim arms
function rgbDiff(a, b) { if (!a[3] && !b[3]) return 0; if (!a[3] || !b[3]) return 400; return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]); }
/** Which column (1 or 2) of a 4-wide face to drop: the one that differs least from the neighbour it merges into. */
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
	// wide faces: top, bottom, right, front, left, back
	const W = { top: [u + d, v, 4, d], bottom: [u + d + 4, v, 4, d], right: [u, v + d, d, h], front: [u + d, v + d, 4, h], left: [u + d + 4, v + d, d, h], back: [u + d + 4 + d, v + d, 4, h] };
	const S = { top: [u + d, v, 3, d], bottom: [u + d + 3, v, 3, d], right: [u, v + d, d, h], front: [u + d, v + d, 3, h], left: [u + d + 3, v + d, d, h], back: [u + d + 3 + d, v + d, 3, h] };
	const faces = {};
	for (const f of Object.keys(W)) {
		const [fx, fy, fw, fh] = W[f];
		const cols = [];
		for (let x = 0; x < fw; x++) { const col = []; for (let y = 0; y < fh; y++) col.push(from.get(fx + x, fy + y)); cols.push(col); }
		// the 4-deep sides (right / left) are kept whole; only the 4-wide faces lose a column
		faces[f] = (f === 'right' || f === 'left') ? { cols, drop: -1 } : { cols, drop: dropColumn(from, fx, fy, fh) };
	}
	// the top and bottom drop the same column as the front so the shoulder and wrist edges meet the front's columns
	faces.top.drop = faces.front.drop;
	faces.bottom.drop = faces.front.drop;
	to.clearRect(u, v, 16, 16);
	for (const f of Object.keys(S)) {
		const [sx, sy] = S[f];
		const kept = faces[f].cols.filter((_, i) => i !== faces[f].drop);
		kept.forEach((col, x) => col.forEach((c, y) => to.set(sx + x, sy + y, c)));
	}
	return { front: faces.front.drop, back: faces.back.drop };
}
function slim(img) {
	const out = img.copy();
	const drops = {};
	for (const [u, v, n] of [[40, 16, 'right arm'], [40, 32, 'right sleeve'], [32, 48, 'left arm'], [48, 48, 'left sleeve']]) drops[n] = slimArm(img, out, u, v);
	return { img: out, drops };
}

// ------------------------------------------------------------------ the cape (64x48: left half = outside, right half = lining)
// Matches the painted cape: off-white cloth, a soft lavender-grey shadow down the middle (the painted centre is darker, the
// edges whiter), a gold hem with pale-gold glints at the bottom, the lining a shade darker.
function cape() {
	const img = new Img(64, 48);
	const WHITE = [240, 236, 244], MID = [206, 199, 210], SHADOW = [180, 171, 188], DEEP = [153, 143, 166];
	const HEM = [250, 175, 51], HEM_LIGHT = [254, 213, 123], HEM_GLINT = [255, 239, 175], HEM_DARK = [204, 128, 20];
	const mix = (a, b, t) => [0, 1, 2].map((k) => Math.round(a[k] + (b[k] - a[k]) * t));
	const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) & 0xffff) / 0xffff; };
	for (let y = 0; y < 48; y++) for (let x = 0; x < 64; x++) {
		const lining = x >= 32;
		const lx = lining ? x - 32 : x;
		const t = Math.abs(lx - 15.5) / 15.5; // 0 at the centre line, 1 at the edges
		let c;
		if (t < 0.25) c = mix(DEEP, SHADOW, t / 0.25);
		else if (t < 0.6) c = mix(SHADOW, MID, (t - 0.25) / 0.35);
		else c = mix(MID, WHITE, (t - 0.6) / 0.4);
		c = mix(c, WHITE, Math.min(1, y / 60) * 0.35); // it catches more light lower down, away from the shoulders' shadow
		c = c.map((v) => v * (0.97 + hash(x, y) * 0.05));
		if (y <= 1) c = mix(c, DEEP, 0.4); // the collar's fold
		if (lining) c = c.map((v) => v * 0.9);
		// the gold hem
		if (y >= 43) {
			c = y === 43 ? HEM_LIGHT : y === 47 ? HEM_DARK : HEM;
			if ((y === 44 || y === 45) && lx % 5 === 1) c = HEM_GLINT;
			if (lining) c = c.map((v) => v * 0.9);
		} else if (lx === 0 || lx === 31) {
			c = mix(c, HEM, 0.85); // a thin gold edge down each side
		}
		img.set(x, y, c.map((v) => Math.max(0, Math.min(255, Math.round(v)))));
	}
	return img;
}

// ------------------------------------------------------------------ write
const fam = family(wide, 4);
const s1 = slim(wide), s2 = slim(fam);
// the family piping is laid on the wide suit and carried through the same slim conversion, so both match
wide.save('shazam_suit.png');
s1.img.save('shazam_suit_slim.png');
fam.save('shazam_suit_family.png');
s2.img.save('shazam_suit_family_slim.png');
const c = cape();
c.save('shazam_cape.png');

// preview: source | wide | slim | family | family slim, x8, on a checkerboard; the cape below
const imgs = [base, wide, s1.img, fam, s2.img];
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
for (let Y = 0; Y < 48 * SC; Y++) for (let X = 0; X < 64 * SC; X++) put(X, 64 * SC + GAP + Y, c.get(Math.floor(X / SC), Math.floor(Y / SC)));
PNG.write(path.join(__dirname, 'preview_suit_x8.png'), PW, PH, prev);

console.log('cleared texels:', cleared, '| slim dropped columns (0-3):', JSON.stringify(s1.drops));
