// v0.17.7 Black Panther necklace: the model texture (flat swatches) and the item icon, both in the colours of the tooth band
// painted on the suit skin (silver #6E6E76 teeth, black beads between them).
// node scratchpad/bp_v0177_necklace_textures.js
'use strict';
const path = require('path');
const PNG = require('./pnglib.js');

const root = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');

// ---- the model texture: 32x32, 8x8 flat swatches (BlackPantherRender: SILVER_U 0, TIP_U 8, BEAD_U 16)
{
	const W = 32, H = 32;
	const px = Buffer.alloc(W * H * 4);
	const fill = (x0, y0, w, h, c) => {
		for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
			const i = (y * W + x) * 4;
			px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
		}
	};
	fill(0, 0, 8, 8, [0x80, 0x80, 0x8a]);   // silver: the skin's #6E6E76, a shade lighter so it reads as metal in the shade
	fill(8, 0, 8, 8, [0xa6, 0xa6, 0xb2]);   // the fang tips, polished
	fill(16, 0, 8, 8, [0x0c, 0x0c, 0x10]);  // the black beads (the skin's black texels)
	PNG.write(path.join(root, 'entity', 'black_panther', 'necklace.png'), W, H, px);
}

// ---- the item icon: 16x16, the band hanging in a U, silver fangs and black beads in turn
{
	const PAL = {
		'.': null,
		k: [12, 12, 16, 255],      // black bead
		o: [26, 26, 32, 255],      // cord / outline
		s: [110, 110, 118, 255],   // silver (the skin's #6E6E76)
		h: [168, 168, 180, 255],   // silver highlight
		d: [70, 70, 78, 255],      // silver shade
		p: [150, 84, 255, 255],    // a glint of vibranium violet
	};
	const ICON = [
		'................',
		'.o............o.',
		'.k............k.',
		'.hs..........sh.',
		'.sd..........ds.',
		'..k..........k..',
		'..hs........sh..',
		'..sd........ds..',
		'...k........k...',
		'...ok......ko...',
		'....hs.kk.sh....',
		'....sd.pp.ds....',
		'.....d.kk.d.....',
		'................',
		'................',
		'................',
	];
	const W = 16, H = 16;
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const c = PAL[ICON[y][x]];
		if (c) px.set(c, (y * W + x) * 4);
	}
	PNG.write(path.join(root, 'item', 'black_panther_necklace.png'), W, H, px);
}
console.log('ok');
