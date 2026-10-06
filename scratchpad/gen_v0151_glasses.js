// v0.15.1: the Stark Glasses item icon (16x16) -- gold aviator frame, red-tinted lenses with a glint.
// Run from the repo root:  node scratchpad/gen_v0151_glasses.js
const fs = require('fs');
const path = require('path');
const { encode, upscale } = require('./pngkit.js');

const grid = [
	'................',
	'................',
	'................',
	'................',
	'................',
	'dHHHHHHHHHHHHHHd',
	'dGrrrrGkkGrrrrGd',
	'oGwrrrG..GwrrrGo',
	'.GrrrrG..GrrrrG.',
	'.GRrrRG..GRrrRG.',
	'.GRRRRG..GRRRRG.',
	'.kGRRGk..kGRRGk.',
	'..kGGk....kGGk..',
	'................',
	'................',
	'................',
];
const C = {
	d: [0x7a, 0x54, 0x18, 255], // temple arm, going back
	o: [0x5a, 0x3c, 0x10, 255],
	H: [0xf5, 0xd3, 0x72, 255], // top bar highlight
	G: [0xc9, 0x96, 0x2e, 255], // gold frame
	k: [0x6e, 0x4a, 0x14, 255], // frame shadow
	R: [0x7c, 0x10, 0x1e, 255], // deep tint
	r: [0xc2, 0x34, 0x3a, 255], // lighter tint (top of the lens)
	w: [0xff, 0xe0, 0xd6, 255], // glint
};
const w = 16, h = 16;
const px = Buffer.alloc(w * h * 4);
for (let y = 0; y < h; y++) {
	for (let x = 0; x < w; x++) {
		const c = C[grid[y][x]];
		if (c) {
			px.set(c, (y * w + x) * 4);
		}
	}
}
const out = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/item/stark_glasses.png');
fs.writeFileSync(out, encode(w, h, px));
const big = upscale({ w, h, px }, 16, true);
fs.mkdirSync(path.join(__dirname, 'shots_v0151'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'shots_v0151', 'G_icon_preview.png'), encode(big.w, big.h, big.px));
console.log('wrote', out);
