// Darken the spider emblem (chest + back, base + overlay layers) of a 64x64 skin toward near-black.
// node emblem.js <in.png> <out.png> <mode> ; mode = dark (front & back emblems drawn dark) | redback (dark front, red-on-blue back)
const P = require('./png.js');
const [, , IN, OUT, MODE, FE, BE, BS] = process.argv;
const FY = +(FE || 31), BY = +(BE || 30), BT = +(BS || 21); // last emblem row (front, back) -- below it sits the belt
const img = P.read(IN), px = img.px;
const get = (x, y) => { const i = (y * 64 + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; };
const lum = c => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
const sat = c => { const mx = Math.max(c[0], c[1], c[2]), mn = Math.min(c[0], c[1], c[2]); return mx === 0 ? 0 : (mx - mn) / mx; };
const blue = c => c[2] > c[0] + 8;
const red = c => c[0] > c[2] + 30 && c[0] > c[1] + 30;
// a pixel of a dark emblem: greyish-dark, black, or a deep maroon -- never the navy flanks
const darkEmblem = c => (sat(c) < 0.42 && lum(c) < 110) || (!blue(c) && lum(c) < 48);
const BLACK = [10, 9, 12];
const mask = [];
function pass(x0, x1, y0, y1, test) {
	for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
		const c = get(x, y);
		if (c[3] > 0 && test(c)) mask.push([x, y]);
	}
}
// front torso is x20-27, back x32-39, rows 20-31 (base) and 36-47 (overlay); the emblem sits in the middle columns
const front = (dy) => pass(21, 26, 21 + dy, FY + dy, darkEmblem);
front(0); front(16);
if (MODE === 'redback') { pass(33, 38, BT, BY, red); pass(33, 38, BT + 16, BY + 16, red); }
else { pass(33, 38, 20, 31, darkEmblem); pass(33, 38, 36, 47, darkEmblem); }
for (const [x, y] of mask) {
	const i = (y * 64 + x) * 4;
	for (let k = 0; k < 3; k++) px[i + k] = Math.round(px[i + k] * 0.22 + BLACK[k] * 0.78);
}
P.write(OUT, 64, 64, px);
console.log(IN, 'darkened', mask.length);
