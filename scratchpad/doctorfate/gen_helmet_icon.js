// node scratchpad/doctorfate/gen_helmet_icon.js [--preview <dir>]
// v0.17.4 round 2 (user: "make the item texture in the inventory look better please, right now it looks like an egg"): the Helmet of
// Fate's 16x16 inventory icon, hand-placed pixel art: the gold dome with a raised crest, the T-shaped dark eye slit, the flared cheek
// guards out to the sides, a dark outline and top-left light. Writes textures/item/helmet_of_fate.png; with --preview also writes a
// 1x, a 3x (a GUI-scale-3 slot) and a 16x zoom, on a slot-grey background, to the given folder.
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng } = require('../lib_png.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/item/helmet_of_fate.png');

const PAL = {
	'.': null,
	O: [72, 44, 6],      // outline
	d: [138, 88, 16],    // dark shade
	s: [188, 128, 26],   // shade
	g: [228, 172, 44],   // gold
	h: [252, 210, 88],   // light gold
	H: [255, 240, 168],  // highlight
	E: [14, 12, 26],     // the eye slit
	e: [44, 34, 70],     // the slit's inner glint (a faint blue)
	c: [206, 150, 34],   // crest
};

// 16 x 16, front view, light from the top left
const GRID = [
	'......OOOO......',
	'.....OhHhgO.....',
	'...OOOhHhgOOO...',
	'..OhHhhHhggssO..',
	'.OhHhhgcgggssdO.',
	'.OhhggggggggsdO.',
	'.OhgEEEEEEEEsdO.',
	'.OhgEeEEEEeEsdO.',
	'OOOgggsEEsggsOOO',
	'OhgOggsEEsggOsdO',
	'OhhgOgsEEsgOgsdO',
	'.OhgOgsEEsgOsdO.',
	'..OhOgssssgOdO..',
	'...OOgggsssOO...',
	'.....OOOOOO.....',
	'................',
];

function build() {
	const W = 16, H = 16;
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) {
		const row = GRID[y];
		if (row.length !== W) throw new Error('row ' + y + ' is ' + row.length + ' wide');
		for (let x = 0; x < W; x++) {
			const c = PAL[row[x]];
			if (c === undefined) throw new Error('bad char ' + row[x]);
			if (!c) continue;
			const i = (y * W + x) * 4;
			px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
		}
	}
	return { W, H, px };
}

function zoom(im, k, bg) {
	const W = im.W * k, H = im.H * k;
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const si = ((y / k | 0) * im.W + (x / k | 0)) * 4, di = (y * W + x) * 4;
		const a = im.px[si + 3] / 255;
		for (let c = 0; c < 3; c++) px[di + c] = Math.round(im.px[si + c] * a + bg[c] * (1 - a));
		px[di + 3] = 255;
	}
	return { W, H, px };
}

const icon = build();
fs.writeFileSync(OUT, encodePng(icon.W, icon.H, icon.px));
console.log('wrote', OUT);
const pi = process.argv.indexOf('--preview');
if (pi > 0) {
	const dir = process.argv[pi + 1];
	fs.mkdirSync(dir, { recursive: true });
	const slot = [139, 139, 139];
	for (const [k, name] of [[1, 'helmet_icon_1x.png'], [3, 'helmet_icon_3x.png'], [16, 'helmet_icon_16x.png']]) {
		const z = zoom(icon, k, slot);
		fs.writeFileSync(path.join(dir, name), encodePng(z.W, z.H, z.px));
	}
	console.log('previews in', dir);
}
