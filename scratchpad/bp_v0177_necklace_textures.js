// v0.17.7 Black Panther necklace: the model texture (flat swatches) and the item icon, in the golds of the necklace painted on the
// comics suit skin (black_panther.png, made by scratchpad/v0177/make_bp_suit_skin.js): highlight #E7C76E / #EACC71, mid #D19C4C /
// #DAAD59, shadow #C18039 / #CA9247, near-black #11101C in the gaps between the links. No gem.
// (Before the comics skin it was silver fangs and black beads, matching the old MCU skin's tooth band.)
// Writes necklace.png, necklace_glow.png (the Kinetic glow, violet) and the item icon.
// node scratchpad/bp_v0177_necklace_textures.js
'use strict';
const path = require('path');
const PNG = require('./pnglib.js');

const root = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');

// ---- the model texture: 64x64, 16x16 flat swatches (BlackPantherRender: HI (0,0), MID (16,0), SHADE (32,0), GAP (48,0),
// TIP (0,16), PLATE (16,16)). 16 px swatches so even the longest link's box UVs stay inside one colour.
{
	const W = 64, H = 64;
	const px = Buffer.alloc(W * H * 4);
	const fill = (x0, y0, w, h, c) => {
		for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
			const i = (y * W + x) * 4;
			px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
		}
	};
	fill(0, 0, 16, 16, [0xe7, 0xc7, 0x6e]);   // highlight: the top bevel of every link
	fill(16, 0, 16, 16, [0xd1, 0x9c, 0x4c]);  // mid gold: the link faces
	fill(32, 0, 16, 16, [0xc1, 0x80, 0x39]);  // shadow gold: the lower lip, the teeth's roots
	fill(48, 0, 16, 16, [0x11, 0x10, 0x1c]);  // the near-black cord in the gaps
	fill(0, 16, 16, 16, [0xea, 0xcc, 0x71]);  // the bright tips of the teeth and the fang
	fill(16, 16, 16, 16, [0xda, 0xad, 0x59]); // the centre plate (the skin's lighter mid #DAAD59)
	PNG.write(path.join(root, 'entity', 'black_panther', 'necklace.png'), W, H, px);
	// the Kinetic glow map, same layout: every gold swatch violet (178,102,255, the suit trims' glow colour), the gaps left dark
	const glow = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const gap = y < 16 && x >= 48;
		const used = y < 32 && (y < 16 || x < 32);
		if (used && !gap) glow.set([178, 102, 255, 255], (y * W + x) * 4);
	}
	PNG.write(path.join(root, 'entity', 'black_panther', 'necklace_glow.png'), W, H, glow);
}

// ---- the item icon: 16x16, the gold band hanging in a U, links and dark gaps in turn, the centre plate with four teeth and the
// longer centre fang below it
{
	const PAL = {
		'.': null,
		k: [0x11, 0x10, 0x1c, 255], // the gaps
		h: [0xe7, 0xc7, 0x6e, 255], // highlight
		t: [0xea, 0xcc, 0x71, 255], // bright tip
		s: [0xd1, 0x9c, 0x4c, 255], // mid
		m: [0xda, 0xad, 0x59, 255], // centre plate
		d: [0xc1, 0x80, 0x39, 255], // shadow
		e: [0x8a, 0x55, 0x22, 255], // the darkest edge
	};
	const ICON = [
		'................',
		'.hh..........hh.',
		'.sd..........ds.',
		'..k..........k..',
		'..hh........hh..',
		'..sd........ds..',
		'...k........k...',
		'...hhkhhhhkhh...',
		'...sdkmmmmkds...',
		'...eekdmmdkee...',
		'.....sdtsds.....',
		'.....d.td.d.....',
		'.......sd.......',
		'.......te.......',
		'................',
		'................',
	];
	const W = 16, H = 16;
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) {
		if (ICON[y].length !== W) throw new Error('icon row ' + y + ' is ' + ICON[y].length + ' wide');
		for (let x = 0; x < W; x++) {
			const c = PAL[ICON[y][x]];
			if (c) px.set(c, (y * W + x) * 4);
		}
	}
	PNG.write(path.join(root, 'item', 'black_panther_necklace.png'), W, H, px);
}
console.log('ok');
