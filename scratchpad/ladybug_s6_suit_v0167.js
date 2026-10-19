// v0.16.7 (user): Ladybug's suit from the user's chosen skin (minecraftskins.com 23173157, "Ladybug Season 6", a SLIM
// skin), cut to the suit only so the wearer's own skin shows: the character's hair, face and every skin-tone texel go,
// the hat layer (hair, ribbons) goes, and a proper red mask with black spots is painted over the eyes (eye holes left
// open so the wearer's own eyes show). Written as suit_slim.png and, with the 3-px arms widened to 4 (the middle column
// doubled, so the stripes and spots stay whole), suit_wide.png.
// Usage: node scratchpad/ladybug_s6_suit_v0167.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('./suits/skin_tools.js');
const SRC = path.join(__dirname, 'user_skins_v0167', 'ladybug_s6.png');
const OUT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/entity/miraculous/ladybug');

const skinTone = (r, g, b) => r > 170 && g > 110 && b >= 85 && r >= g && g >= b && r - b > 18 && r - b < 130;
const img = T.decode(SRC);
const set = (x, y, c) => { const i = (y * 64 + x) * 4; img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = c[3] === undefined ? 255 : c[3]; };

// 1. the head (base + hat) clear; skin tones everywhere else clear
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
	const i = (y * 64 + x) * 4;
	if (y < 16) { img.px[i + 3] = 0; continue; }
	if (img.px[i + 3] && skinTone(img.px[i], img.px[i + 1], img.px[i + 2])) img.px[i + 3] = 0;
}

// 2. the mask: rows 12-14 of the face, red with black spots, eye holes on row 13
const R = [176, 34, 30], H = [210, 52, 44], S = [128, 22, 20], K = [18, 14, 16], E = [92, 16, 16];
const KEY = { R, H, S, K, E };
const FACE = [
	'ERHRKHRE', // row 12: brow edge
	'R..RR..R', // row 13: eye holes
	'ESRKRRSE', // row 14: under the eyes
];
FACE.forEach((row, dy) => [...row].forEach((ch, dx) => { if (ch !== '.') set(8 + dx, 12 + dy, KEY[ch]); }));
// the mask wraps one texel round each side of the head
for (let dy = 0; dy < 3; dy++) {
	set(7, 12 + dy, dy === 1 ? R : S);   // right side, front edge
	set(16, 12 + dy, dy === 1 ? R : S);  // left side, front edge
}

// 3. slim (as drawn) and wide (3 -> 4 px arms)
const slimImg = { w: 64, h: 64, px: Buffer.from(img.px) };
const wide = T.convertArms(slimImg, false);
fs.writeFileSync(path.join(OUT, 'suit_slim.png'), T.encode(64, 64, slimImg.px));
fs.writeFileSync(path.join(OUT, 'suit_wide.png'), T.encode(64, 64, wide.px));
for (const [name, im] of [['slim', slimImg], ['wide', wide]]) {
	const p = T.preview(im, 10);
	fs.writeFileSync(path.join(__dirname, 'user_skins_v0167', 'ladybug_s6_' + name + '_cut_x10.png'), T.encode(p.w, p.h, p.px));
}
console.log('ladybug s6 suit written');
