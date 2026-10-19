// v0.16.7 (user): Viperion's suit from the user's chosen skin (minecraftskins.com 20294512, a WIDE skin), cut to the SUIT
// ONLY so the wearer's own skin shows ("remove the regular skin so the player's skin can be used"):
//   * the whole head goes (the character's black + teal hair, face, ears, the hat layer's hair strands) -- except the skin's own
//     visor (rows 13-15 of the face as drawn, lens highlights and the two yellow texels under the eyes kept, user) and a
//     teal visor mask painted over the eyes, open eye holes on the eye rows (12 and 13) so the wearer's eyes show, the
//     mask wrapping one texel round each side of the head;
//   * every skin-tone texel on the body goes (the neck opening at the collar);
//   * the bodysuit, gloves, boots, the yellow collar clasp, the snake emblem on the jacket back and the sleeve cuff stay.
// Written as suit_wide.png (as drawn) and suit_slim.png (3-px arms, T.convertArms), plus x10 previews.
// Usage: node scratchpad/viperion/gen_viperion_suit.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('./skin_tools.js');
const SRC = path.join(__dirname, '..', 'user_skins_v0167', 'viperion.png');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/miraculous/viperion');
fs.mkdirSync(OUT, { recursive: true });

const skinTone = (r, g, b) => r > 170 && g > 110 && b >= 85 && r >= g && g >= b && r - b > 18 && r - b < 130;
const img = T.decode(SRC);
if (img.w !== 64 || img.h !== 64) throw new Error('want a 64x64 skin');
const set = (x, y, c) => { const i = (y * 64 + x) * 4; img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = 255; };
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255];

// 0. (user, v0.16.7) the source's own visor must survive: remember its head-front texels (rows 12-15, x 8-15) that are
// not skin -- the mask's teal / dark texels, its lens highlights, and the two bright yellow texels #fcfa2d under the eyes
const SOURCE_MASK = [];
for (let y = 12; y < 16; y++) for (let x = 8; x < 16; x++) {
	const i = (y * 64 + x) * 4;
	const r = img.px[i], g = img.px[i + 1], b = img.px[i + 2];
	if (img.px[i + 3] && !skinTone(r, g, b)) SOURCE_MASK.push([x, y, [r, g, b]]);
}
// 1. the head (base + hat) clear; skin tones everywhere else clear
let cut = 0;
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
	const i = (y * 64 + x) * 4;
	if (!img.px[i + 3]) continue;
	if (y < 16 || skinTone(img.px[i], img.px[i + 1], img.px[i + 2])) { img.px[i + 3] = 0; cut++; }
}

// 2. the visor mask, from the skin's own visor palette: rows 11-14 of the face (y 11..14), eye holes on rows 12-13
const E = hex(0x06322e), K = hex(0x0e5852), R = hex(0x107068), H = hex(0x1d9086), S = hex(0x6fa3a7), L = hex(0x2fb7b0);
const KEY = { E, K, R, H, S, L };
const FACE = [
	'EKHLLHKE', // y 11: the brow of the visor
	'R..HH..R', // y 12: eye holes
	'K..RR..K', // y 13: eye holes (two-row eyes too)
	'.ES..SE.', // y 14: the visor's lower points under the eyes
];
FACE.forEach((row, dy) => [...row].forEach((ch, dx) => { if (ch !== '.') set(8 + dx, 11 + dy, KEY[ch]); }));
// round each side of the head: the right side face's front column is x 7, the left side face's is x 16
for (let dy = 0; dy < 3; dy++) {
	set(7, 11 + dy, dy === 0 ? K : E);
	set(16, 11 + dy, dy === 0 ? K : E);
	set(6, 11 + dy, dy === 1 ? E : K); // a short strap back along the sides
	set(17, 11 + dy, dy === 1 ? E : K);
}
// a highlight glint on each lens rim
set(8, 12, S); set(15, 12, S);
// 2b. the source visor back on (its lower rows, the lens highlights and the yellow texels), except on the wearer's eye
// row (y 12, x 9-10 / 13-14): those stay open so their own eyes show
let kept = 0;
for (const [x, y, c] of SOURCE_MASK) {
	if (y === 12 && (x === 9 || x === 10 || x === 13 || x === 14)) continue;
	if (y === 12) continue; // row 12 keeps the painted bridge / rims round the holes
	set(x, y, c);
	kept++;
}
const yellow = SOURCE_MASK.filter(([, , c]) => c[0] === 0xfc && c[1] === 0xfa && c[2] === 0x2d).length;
if (yellow < 2) throw new Error('the yellow texels went missing');

// 3. wide (as drawn) and slim (4 -> 3 px arms)
const wide = { w: 64, h: 64, px: Buffer.from(img.px) };
const slim = T.convertArms(wide, true);
fs.writeFileSync(path.join(OUT, 'suit_wide.png'), T.encode(64, 64, wide.px));
fs.writeFileSync(path.join(OUT, 'suit_slim.png'), T.encode(64, 64, slim.px));
for (const [name, im] of [['wide', wide], ['slim', slim]]) {
	const p = T.preview(im, 10);
	fs.writeFileSync(path.join(__dirname, 'viperion_' + name + '_cut_x10.png'), T.encode(p.w, p.h, p.px));
}
console.log('viperion suit written (' + cut + ' texels cut)');
