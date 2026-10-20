// v0.16.7 Iron Man Era I (core): the Suit Crate item sprite (16x16, hand-painted) + its item model. Idempotent.
// Run from the repo root:   node scratchpad/gen_v0167_suit_crate.js
// A dark steel shipping crate seen slightly from above: riveted frame, two steel bands, a red-and-gold Stark stripe and
// a small cyan arc-reactor status light on the front.
'use strict';
const { Canvas, writePng, writeJson, hex, clamp, fbm, written } = require('./v0161_asset_lib.js');

const OUT = hex('#141820');
const FRAME_HI = hex('#9aa4b4'), FRAME = hex('#6a7484'), FRAME_LO = hex('#3a4250');
const PANEL = hex('#4a5262'), PANEL_LO = hex('#363c48');
const TOP = hex('#5c6676'), TOP_HI = hex('#7c8696');
const RED = hex('#b02a22'), RED_LO = hex('#7a1a14'), GOLD = hex('#e0a830');
const CYAN = hex('#7fe9ff'), CYAN_HI = hex('#e0fbff');

const c = new Canvas(16, 16);
const noisy = (col, x, y, s) => col.map(v => clamp(v + (fbm(x * 0.6, y * 0.6, s) - 0.5) * 18));

// lid (top face, rows 2..4) and front face (rows 5..14), columns 1..14
for (let y = 2; y <= 14; y++) {
	for (let x = 1; x <= 14; x++) {
		let col;
		if (y <= 4) {
			col = noisy(y === 2 ? TOP_HI : TOP, x, y, 3);
		} else {
			col = noisy(y >= 12 ? PANEL_LO : PANEL, x, y, 7);
		}
		c.set(x, y, col);
	}
}
// outline
for (let x = 1; x <= 14; x++) { c.set(x, 1, OUT); c.set(x, 15, OUT); }
for (let y = 1; y <= 15; y++) { c.set(0, y, OUT); c.set(15, y, OUT); }
// lid edge + frame
for (let x = 1; x <= 14; x++) { c.set(x, 5, FRAME_HI); c.set(x, 14, FRAME_LO); }
for (let y = 5; y <= 14; y++) { c.set(1, y, FRAME); c.set(14, y, FRAME_LO); }
// two steel bands down the front
for (const bx of [4, 11]) {
	for (let y = 2; y <= 14; y++) c.set(bx, y, y <= 4 ? FRAME_HI : FRAME);
}
// rivets on the frame corners
for (const [x, y] of [[2, 6], [13, 6], [2, 13], [13, 13]]) c.set(x, y, FRAME_HI);
// the Stark stripe: red with a gold rule above, across the front between the bands
for (let x = 5; x <= 10; x++) {
	c.set(x, 8, GOLD);
	c.set(x, 9, RED);
	c.set(x, 10, RED_LO);
}
// the arc-reactor status light, centre front under the stripe
c.set(7, 12, CYAN); c.set(8, 12, CYAN_HI); c.set(7, 13, CYAN_HI); c.set(8, 13, CYAN);
// a carry handle on the lid
c.set(6, 3, FRAME_LO); c.set(7, 3, FRAME_HI); c.set(8, 3, FRAME_HI); c.set(9, 3, FRAME_LO);

writePng('textures/item/suit_crate.png', c);
writeJson('models/item/suit_crate.json', { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/suit_crate' } });
console.log(written.join('\n'));
