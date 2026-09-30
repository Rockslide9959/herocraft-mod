// v0.14.4: white Symbiote eye patches for infested wolves / cats / cows. Each texture matches the vanilla
// entity texture's UV layout (64x32) and is transparent (0,0,0,0) everywhere except the eye patches, so it
// can be drawn with RenderType.eyes (additive, emissive) over the black-tinted body.
// Eye pixels were located by dumping the vanilla textures' head front faces.
const path = require('path');
const png = require('./pnglib.js');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'symbiote');
require('fs').mkdirSync(OUT, { recursive: true });

// Outer corner high, inner corner low: the Venom slant.
const SETS = {
	// wolf head front face: u 4..9, v 4..9; vanilla eyes on row 6 at x 4-5 / 8-9
	symbiote_eyes_wolf: [[4, 5], [5, 5], [5, 6], [9, 5], [8, 5], [8, 6]],
	// cat (ocelot model) head front face: u 5..9, v 5..8; vanilla eyes on row 6 at x 5-6 / 8-9
	symbiote_eyes_cat: [[5, 5], [5, 6], [6, 6], [9, 5], [9, 6], [8, 6]],
	// cow head front face: u 6..13, v 6..13; vanilla eyes on row 9 at x 6-7 / 12-13
	symbiote_eyes_cow: [[6, 8], [7, 8], [7, 9], [13, 8], [12, 8], [12, 9]],
};

for (const [name, pixels] of Object.entries(SETS)) {
	const w = 64, h = 32;
	const data = Buffer.alloc(w * h * 4);
	for (const [x, y] of pixels) {
		const o = (y * w + x) * 4;
		data[o] = 245; data[o + 1] = 245; data[o + 2] = 250; data[o + 3] = 255;
	}
	png.write(path.join(OUT, name + '.png'), { w, h, data });
	console.log('wrote', name);
}
