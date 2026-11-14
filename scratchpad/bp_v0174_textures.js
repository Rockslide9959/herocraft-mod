// v0.17.4 Black Panther: the necklace/claw texture (32x32 swatches) and the Panther Habit item sprite (16x16).
// node bp_textures.js <pnglib.js> <necklace.png out> <item png out>
const [, , lib, neckOut, itemOut] = process.argv;
const p = require(lib);

function img(W, H) { return { W, H, px: Buffer.alloc(W * H * 4) }; }
function set(im, x, y, c) { if (x < 0 || y < 0 || x >= im.W || y >= im.H) return; const i = (y * im.W + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c.length > 3 ? c[3] : 255; }
function hex(h) { return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)]; }
function mix(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }

// ---- the necklace swatches: 8x8 blocks, lit from the top (top rows lighter, bottom darker)
const neck = img(32, 32);
const sw = [
	[0, 0, '#DDE1EA', '#8F95A6'], // silver bead
	[8, 0, '#5A5E6E', '#2C2F3A'], // gunmetal spacer
	[16, 0, '#F1EDE2', '#B9B2A0'], // fang (ivory silver)
	[24, 0, '#C08CFF', '#6A22D8'], // vibranium gem
	[0, 8, '#AEB3C2', '#5F6475'], // dark silver (links)
	[8, 8, '#FFFFFF', '#D8DCF0'], // highlight
	[16, 8, '#4A4F63', '#1E2130'], // claw body (dark vibranium)
	[24, 8, '#E6E9F2', '#9CA2B4'], // claw edge (bright silver)
];
for (const [ox, oy, top, bot] of sw) {
	const a = hex(top), b = hex(bot);
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		let c = mix(a, b, y / 7);
		if ((x + y) % 5 === 0) c = mix(c, a, 0.35); // a little sparkle
		set(neck, ox + x, oy + y, c);
	}
}
p.write(neckOut, neck.W, neck.H, neck.px);

// ---- the item: the folded habit (black weave, silver triangle pattern, purple seams) with the fang necklace over it
const it = img(16, 16);
const K = hex('#15151C'), K2 = hex('#262634'), K3 = hex('#3A3A4E'), PU = hex('#9A4CFF'), PL = hex('#D2A8FF'), S = hex('#DDE1EA'), S2 = hex('#8F95A6'), F = hex('#F1EDE2'), O = hex('#07070A');
// the folded suit: a rounded square, rows 3..14
for (let y = 3; y <= 14; y++) for (let x = 2; x <= 13; x++) {
	const corner = (y === 3 || y === 14) && (x === 2 || x === 13);
	if (corner) continue;
	let c = (x + y) % 4 === 0 ? K3 : (x * 3 + y) % 7 === 0 ? K2 : K;
	if (y === 3 || x === 2) c = K2; // a lit top / left edge
	set(it, x, y, c);
}
// purple seams: a V down the chest and the fold line
for (let i = 0; i < 5; i++) { set(it, 4 + i, 6 + i, PU); set(it, 11 - i, 6 + i, PU); }
for (let x = 3; x <= 12; x++) if (x < 5 || x > 10) set(it, x, 12, mix(PU, K, 0.4));
// outline
for (let y = 2; y <= 15; y++) for (let x = 1; x <= 14; x++) {
	const i = (y * 16 + x) * 4;
	if (it.px[i + 3]) continue;
	const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => { const xx = x + dx, yy = y + dy; return xx >= 0 && yy >= 0 && xx < 16 && yy < 16 && it.px[(yy * 16 + xx) * 4 + 3] && !(it.px[(yy * 16 + xx) * 4] === O[0] && it.px[(yy * 16 + xx) * 4 + 1] === O[1]); });
	if (n) set(it, x, y, O);
}
// the necklace: silver beads from the top corners down to the pendant
const beads = [[3, 2], [4, 3], [5, 4], [6, 5], [12, 2], [11, 3], [10, 4], [9, 5]];
for (const [x, y] of beads) { set(it, x, y, (x + y) % 2 ? S : S2); }
set(it, 7, 6, S); set(it, 8, 6, S);
// the gem and the fang
set(it, 7, 7, PL); set(it, 8, 7, PU);
set(it, 7, 8, F); set(it, 8, 8, F); set(it, 7, 9, F); set(it, 8, 9, mix(F, S2, 0.5)); set(it, 7, 10, mix(F, S2, 0.3));
p.write(itemOut, it.W, it.H, it.px);
console.log('written');
