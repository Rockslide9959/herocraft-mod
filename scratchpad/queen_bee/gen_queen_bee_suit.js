// v0.17.2 (user): Queen Bee's suit from the user's chosen skin (minecraftskins.com 23083151, a SLIM skin), cut to the SUIT
// ONLY so the wearer's own skin and hair show ("remove the hair from the suit and the regular skin"):
//   * the whole head goes (Chloe's blonde hair, face, the hat layer's ponytail strands) except a yellow mask painted from
//     the skin's own mask palette over rows 11-14, eye holes on rows 12-13 so the wearer's eyes show, wrapping one texel
//     round each side of the head;
//   * the ponytail hanging down the back goes (base body back rows 20-24, re-drawn from the front's stripes; the jacket
//     overlay's strands cleared);
//   * the bodysuit, black gloves / boots, the belt and the stripes stay as drawn.
// Written as suit_slim.png (as drawn) and suit_wide.png (3 -> 4 px arms, T.convertArms), plus x10 previews.
// Usage: node scratchpad/queen_bee/gen_queen_bee_suit.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('../viperion/skin_tools.js');
const SRC = path.join(__dirname, 'queen_bee_src.png');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/miraculous/queen_bee');
fs.mkdirSync(OUT, { recursive: true });

const img = T.decode(SRC);
if (img.w !== 64 || img.h !== 64) throw new Error('want a 64x64 skin');
const idx = (x, y) => (y * 64 + x) * 4;
const set = (x, y, c) => { const i = idx(x, y); img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = 255; };
const clear = (x, y) => { img.px[idx(x, y) + 3] = 0; };
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255];

// 1. the head (base + hat) clear
for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) clear(x, y);

// 2. the ponytail down the back: the body's back face (x 32-39) rows 20-24 re-drawn from the front face (x 20-27),
// mirrored (the back face is seen from behind); the jacket overlay's back face strands (rows 36-40) cleared
for (let y = 20; y <= 24; y++) for (let x = 0; x < 8; x++) {
	const s = idx(20 + x, y), d = idx(39 - x, y);
	img.px.copy(img.px, d, s, s + 4);
}
for (let y = 36; y <= 40; y++) for (let x = 32; x < 40; x++) clear(x, y);

// 3. the mask, from the skin's own mask palette (yellow, black rims): face rows 11-14, eye holes on rows 12-13
const Y = hex(0xf6df47), W = hex(0xf7e35a), O = hex(0xf6dd39), K = hex(0x000000), D = hex(0x191919);
const KEY = { Y, W, O, K, D };
const FACE = [
	'KYWWWWYK', // y 11: the brow
	'O..KK..O', // y 12: eye holes, the black bridge
	'K..YY..K', // y 13: eye holes (two-row eyes too)
	'.KO..OK.', // y 14: the points under the eyes
];
FACE.forEach((row, dy) => [...row].forEach((ch, dx) => { if (ch !== '.') set(8 + dx, 11 + dy, KEY[ch]); }));
// round each side of the head: the right side face's front column is x 7, the left side face's is x 16
for (let dy = 0; dy < 3; dy++) {
	const c = dy === 1 ? Y : D;
	set(7, 11 + dy, c);
	set(16, 11 + dy, c);
}

// 4. no skin tone may survive anywhere
const skinTone = (r, g, b) => r > 200 && g > 160 && b > 150; // the suit's pale-yellow highlights stay
let leftover = 0;
for (let y = 16; y < 64; y++) for (let x = 0; x < 64; x++) {
	const i = idx(x, y);
	if (img.px[i + 3] && skinTone(img.px[i], img.px[i + 1], img.px[i + 2])) { img.px[i + 3] = 0; leftover++; }
}

// 5. slim (as drawn) and wide (3 -> 4 px arms)
const slim = { w: 64, h: 64, px: Buffer.from(img.px) };
const wide = T.convertArms(slim, false);
fs.writeFileSync(path.join(OUT, 'suit_slim.png'), T.encode(64, 64, slim.px));
fs.writeFileSync(path.join(OUT, 'suit_wide.png'), T.encode(64, 64, wide.px));
for (const [name, im] of [['wide', wide], ['slim', slim]]) {
	const p = T.preview(im, 10);
	fs.writeFileSync(path.join(__dirname, 'queen_bee_' + name + '_x10.png'), T.encode(p.w, p.h, p.px));
}
console.log('queen bee suit written (' + leftover + ' stray skin texels cut)');
