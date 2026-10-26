// v0.17.1: hand-painted 16x16 Cheese Wedge item icon (Mullo's favourite food). A wedge seen from the front-left:
// a pale-yellow top face sloping to the point at the right, a deeper-yellow front face with round holes, a golden rind
// along the back. Usage (repo root): node scratchpad/multimouse/gen_cheese_wedge.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('../viperion/skin_tools.js');

const W = 16;
const px = Buffer.alloc(W * W * 4);
const C = {
	o: [96, 62, 14],     // outline
	t: [255, 236, 140],  // top face
	T: [255, 246, 190],  // top highlight
	f: [246, 200, 70],   // front face
	F: [222, 170, 44],   // front shade
	h: [196, 140, 30],   // hole
	H: [168, 112, 20],   // hole dark rim
	r: [232, 160, 40],   // rind
	R: [200, 126, 28],   // rind dark
};
// rows top to bottom; '.' = clear
const ART = [
	'................',
	'................',
	'.........oo.....',
	'.......oorroo...',
	'.....oottrrRo...',
	'...oottTTttRo...',
	'.oottTTttttroo..',
	'oTTTttttttttroo.',
	'offfffffffffFFo.',
	'offhhffffffhFFo.',
	'ofHhhfffffffFFo.',
	'offffffhhfffFFo.',
	'offfffHhhfffFFo.',
	'ofhfffffffffFFo.',
	'oooooooooooooo..',
	'................',
];
for (let y = 0; y < W; y++) {
	for (let x = 0; x < W; x++) {
		const ch = ART[y][x];
		const c = C[ch];
		if (!c) continue;
		const i = (y * W + x) * 4;
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
}
const out = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'item', 'cheese_wedge.png');
fs.writeFileSync(out, T.encode(W, W, px));
fs.writeFileSync(path.join(__dirname, 'cheese_wedge_x16.png'), T.encode(W * 16, W * 16, (() => {
	const big = Buffer.alloc(W * 16 * W * 16 * 4);
	for (let y = 0; y < W * 16; y++) for (let x = 0; x < W * 16; x++) {
		const s = ((y >> 4) * W + (x >> 4)) * 4, d = (y * W * 16 + x) * 4;
		const a = px[s + 3];
		const bg = ((x >> 4) + (y >> 4)) % 2 ? 200 : 230;
		for (let k = 0; k < 3; k++) big[d + k] = a ? px[s + k] : bg;
		big[d + 3] = 255;
	}
	return big;
})()));
console.log('wrote', out);
