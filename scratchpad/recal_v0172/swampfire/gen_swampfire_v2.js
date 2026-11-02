// v0.17.2 Swampfire revamp (user gave the skin: planetminecraft.com/skin/swampfire-ben-10-alien-force-5291545): copies the
// downloaded 64x64 skin in as swampfire.png (re-encoded as plain RGBA) and repaints swampfire_extras.png in that skin's palette --
// red bloom petals with dark veins, an orange-yellow head flame, olive seed pods with red spots, an olive vine.
// Usage: node gen_swampfire_v2.js <downloaded skin.png>
'use strict';
const path = require('path');
const PNG = require('../../pnglib.js');
const { img, hex, hash, mixc, rect, save, put } = require('./skin_helpers.js');

const src = process.argv[2];
if (!src) throw new Error('usage: node gen_swampfire_v2.js <skin.png>');
const s = PNG.read(src);
if (s.W !== 64 || s.H !== 64) throw new Error("expected a 64x64 skin, got " + s.W + "x" + s.H);
const skin = img(64, 64);
s.px.copy(skin.px);
save(skin, 'entity/omnitrix/swampfire.png');

// the skin's own colours
const RED = ['#780810', '#990f19', '#c11320', '#df1625'].map(hex);
const ORANGE = ['#eca30f', '#f0b710', '#eec91c'].map(hex);
const OLIVE = ['#2f3914', '#3f4c1a', '#4c5c1f', '#556723'].map(hex);

const ex = img(64, 64);
// 0,0 8x8: a petal -- deep red at the base, bright at the tip, a dark vein down the middle
rect(ex, 0, 0, 8, 8, (u, v) => {
	if (u === 3 || u === 4) return v > 1 ? RED[0] : RED[1];
	const t = 1 - v / 7;
	const c = mixc(RED[1], RED[3], t);
	return hash(u, v, 5) > 0.85 ? RED[2] : c;
});
// 8,0 4x8: the vine down his back
rect(ex, 8, 0, 4, 8, (u, v) => (u + v) % 3 === 0 ? OLIVE[0] : OLIVE[(v % 2) + 2]);
// 12,0 4x4: the head flame -- yellow core, orange edge
rect(ex, 12, 0, 4, 4, (u, v) => (u === 1 || u === 2) && v >= 1 ? ORANGE[2] : ORANGE[0]);
// 16,0 8x8: a seed pod -- olive with the skin's red spots
rect(ex, 16, 0, 8, 8, (u, v) => {
	const n = hash(u, v, 21);
	if (n > 0.86) return RED[2];
	return mixc(OLIVE[1], OLIVE[3], v / 7);
});
put(ex, 0, 63, [0, 0, 0], 0);
save(ex, 'entity/omnitrix/swampfire_extras.png');
console.log('swampfire v2 done from', path.basename(src));
