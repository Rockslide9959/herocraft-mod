// v0.17.2 Recalibrated Omnitrix: Swampfire's basic body (user: "basic versions ... that we can revamp later").
//   * entity/omnitrix/swampfire.png          64x64 WIDE player skin: mossy green plant body streaked with black bark, the chest
//                                             a darker ribbed husk, glowing yellow-orange eyes, a maroon bloom crown, root-like feet
//   * entity/omnitrix/swampfire_extras.png   petal 0,0 8x8 (maroon, yellow seeds) | vine 8,0 4x8 | pod 12,0 4x4 | bloom 16,0 8x8
// Usage: node scratchpad/recal_v0172/swampfire/gen_swampfire.js [previewDir]
'use strict';
const H = require('./skin_helpers.js');
const { img, hex, hash, shade, mixc, rect, save, paintBox, SKIN, tone } = H;
const PREVIEW = process.argv[2];

const GREEN = ['#1c4a20', '#2a6a2e', '#3c8a3e', '#55a84f', '#7cc466'].map(hex);
const BARK = ['#0c100c', '#161c15', '#232b20'].map(hex);
const MAROON = ['#4a0f14', '#6e1a1c', '#922a22'].map(hex);
const YEL = ['#d89a1a', '#f2c23a', '#ffe680'].map(hex);

// black bark streaks running down the limbs and body
function bark(u, v, seed) {
	const n = hash(Math.floor(u / 2), Math.floor(v / 3), seed);
	return n > 0.78 || ((u + Math.floor(v / 4)) % 5 === 0 && hash(u, v, seed + 7) > 0.55);
}

const im = img(64, 64);
paintBox(im, SKIN.head, (f, u, v, w, h) => {
	if (f === 'top') {
		// the bloom crown: a maroon petal ring round yellow seeds
		const d = Math.hypot(u - 3.5, v - 3.5);
		if (d < 1.6) return hash(u, v, 3) > 0.5 ? YEL[1] : YEL[0];
		if (d < 3.6) return MAROON[(u + v) % 2 ? 1 : 2];
		return GREEN[2];
	}
	if (f === 'front') {
		if (v <= 1) return MAROON[1]; // the bloom's rim over the brow
		if (v === 3 && (u === 1 || u === 2 || u === 5 || u === 6)) return (u === 2 || u === 5) ? YEL[2] : YEL[1]; // glowing eyes
		if (v === 2 && (u === 1 || u === 6)) return GREEN[0]; // a heavy brow
		if (v === 6 && u >= 2 && u <= 5) return BARK[0]; // the mouth
		if (v === 5 && (u === 2 || u === 5)) return BARK[1];
	}
	if ((f === 'left' || f === 'right' || f === 'back') && v <= 1) return MAROON[v === 0 ? 2 : 1];
	if (bark(u, v, 11) && f !== 'front') return BARK[2];
	return tone(GREEN, u, v, w, h, 5 + f.length, f);
});
paintBox(im, SKIN.hat, (f, u, v) => {
	// the bloom's petals stand off the head a little (second layer): only on the upper rim
	if (f === 'top') {
		const d = Math.hypot(u - 3.5, v - 3.5);
		if (d >= 2.6 && d < 4.2 && (Math.floor(Math.atan2(v - 3.5, u - 3.5) * 2.5) & 1)) return MAROON[2];
		return null;
	}
	if (f !== 'bottom' && v === 0 && u % 3 !== 1) return MAROON[2];
	return null;
});
paintBox(im, SKIN.body, (f, u, v, w, h) => {
	if (f === 'front') {
		// the darker husk down the chest, ribbed
		if (u >= 2 && u <= 5 && v >= 2) return (v % 3 === 0) ? BARK[1] : shade(GREEN[1], 0.85 + hash(u, v, 2) * 0.1);
		if ((u === 1 || u === 6) && v >= 1) return BARK[0];
	}
	if (f === 'back' && (u === 3 || u === 4)) return BARK[1]; // the spine ridge
	if (bark(u, v, 21)) return BARK[2];
	return tone(GREEN, u, v, w, h, 13 + f.length, f);
});
for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 3 && f !== 'top') return v === h - 1 ? BARK[0] : GREEN[1]; // dark, knotted hands
	if (v === 3 && f !== 'top' && f !== 'bottom') return BARK[1]; // a bark band at the shoulder
	if (bark(u, v, 31 + k.length)) return BARK[2];
	return tone(GREEN, u, v, w, h, 23 + k.length, f);
});
for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 2 && f !== 'top') return v === h - 1 ? BARK[0] : BARK[2]; // root-like feet
	if (bark(u, v, 41 + k.length)) return BARK[2];
	return tone(GREEN, u, v, w, h, 33 + k.length, f);
});
save(im, 'entity/omnitrix/swampfire.png', PREVIEW);

const ex = img(64, 64);
rect(ex, 0, 0, 8, 8, (u, v) => {
	// a petal: maroon, paler at the tip, yellow seed speckles
	if (hash(u, v, 9) > 0.86) return YEL[1];
	return mixc(MAROON[0], MAROON[2], v / 7);
});
rect(ex, 8, 0, 4, 8, (u, v) => (u + v) % 3 === 0 ? BARK[1] : GREEN[(v % 2) + 1]);
rect(ex, 12, 0, 4, 4, (u, v) => (u === 1 && v === 1) ? YEL[2] : YEL[0]);
rect(ex, 16, 0, 8, 8, (u, v) => {
	const d = Math.hypot(u - 3.5, v - 3.5);
	return d < 2 ? YEL[1] : MAROON[1];
});
save(ex, 'entity/omnitrix/swampfire_extras.png', PREVIEW);
console.log('swampfire done');
